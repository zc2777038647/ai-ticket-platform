# P1 创建工单模块学习与面试复盘

## 文档说明与事实边界

本文只复盘当前项目已经完成的“创建工单”能力，内容来自实际源码、Flyway 迁移和测试报告，不把尚未实现的能力写成项目成果。

当前已经完成的范围是：接收创建工单请求、校验请求参数、由 Service 执行业务规则、通过 MyBatis-Plus 写入 MySQL、返回统一成功结构，以及对常见请求错误和系统异常返回统一错误结构。当前未实现工单查询、更新、删除、状态流转接口、登录鉴权、Redis、消息队列、AI 或 Agent 能力。

测试结果部分记录的是仓库当前 `target/surefire-reports` 中最近一次测试产物；本次 P1-R 文档整理没有重新运行 Maven 测试，因此不会把历史报告描述成本轮重新执行的结果。

## 一、模块目标与最终结果

P1 的目标是建立第一条完整、真实、可验证的后端写入链路：

```text
POST /api/tickets
→ TicketController
→ TicketService
→ TicketServiceImpl
→ TicketMapper
→ MyBatis-Plus
→ JDBC
→ MySQL
```

最终实现具备以下行为：

1. 客户端提交 `title`、`description`、`creatorName` 和 `priority`。
2. Spring MVC 将 JSON 反序列化为 `CreateTicketRequest`。
3. Jakarta Validation 校验必填项和长度限制。
4. Service 显式设置新工单状态为 `TicketStatus.OPEN`。
5. MyBatis-Plus 执行插入，由 MySQL 生成自增主键和时间字段。
6. 自增 ID 通过 JDBC generated keys 回填到 `Ticket` 实体。
7. Controller 返回 HTTP 201，响应体为 `ApiResponse<TicketResponse>`。
8. 参数校验失败或 JSON 无法解析时返回 HTTP 400；未处理异常返回 HTTP 500。

成功响应的业务数据当前包含：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": 100,
    "title": "登录页无法提交",
    "description": "点击提交按钮后页面没有响应",
    "creatorName": "xiaoyang",
    "priority": "HIGH",
    "status": "OPEN"
  }
}
```

这里的 `100` 只是示例。真实 ID 由 MySQL 自增生成，不保证连续，也不应该在测试中断言连续。

## 二、项目目录与文件职责

与 P1 创建工单模块直接相关的实际结构如下：

```text
src/main/java/com/xiaoyang/aiticketplatform
├─ AiTicketPlatformApplication.java
├─ common
│  ├─ ApiResponse.java
│  └─ ErrorCode.java
├─ controller
│  └─ TicketController.java
├─ dto
│  ├─ request
│  │  └─ CreateTicketRequest.java
│  └─ response
│     └─ TicketResponse.java
├─ entity
│  └─ Ticket.java
├─ enums
│  ├─ TicketPriority.java
│  └─ TicketStatus.java
├─ exception
│  └─ GlobalExceptionHandler.java
├─ mapper
│  └─ TicketMapper.java
└─ service
   ├─ TicketService.java
   └─ impl
      └─ TicketServiceImpl.java

src/main/resources/db/migration
├─ V1__create_ticket_table.sql
└─ V2__align_ticket_enum_values.sql

src/test/java/com/xiaoyang/aiticketplatform
├─ AiTicketPlatformApplicationTests.java
├─ common/ApiResponseTest.java
├─ controller/TicketControllerTest.java
├─ dto/request/CreateTicketRequestValidationTest.java
├─ exception/GlobalExceptionHandlerTest.java
├─ integration/TicketCreationIntegrationTest.java
├─ mapper/TicketMapperIntegrationTest.java
├─ migration/TicketStatusMigrationIntegrationTest.java
└─ service/impl/TicketServiceImplTest.java
```

各文件的核心职责：

| 文件 | 职责 |
|---|---|
| `CreateTicketRequest` | 表达创建请求输入并声明输入校验规则 |
| `TicketResponse` | 表达当前接口对外返回的工单字段 |
| `TicketPriority` | 约束优先级为 `LOW`、`MEDIUM`、`HIGH`、`URGENT` |
| `TicketStatus` | 约束状态为 `OPEN`、`IN_PROGRESS`、`RESOLVED`、`CLOSED` |
| `Ticket` | MyBatis-Plus 持久化实体，对应 `tickets` 表 |
| `TicketMapper` | 继承 `BaseMapper<Ticket>`，提供基础数据库操作 |
| `TicketService` | 定义创建工单用例，不暴露持久化实体 |
| `TicketServiceImpl` | 执行业务规则、事务控制、对象映射和插入结果检查 |
| `TicketController` | 接收 HTTP 请求、触发校验、调用 Service、确定 HTTP 201 |
| `ApiResponse` | 统一成功与失败响应的外层结构 |
| `ErrorCode` | 集中定义当前错误码和对外消息 |
| `GlobalExceptionHandler` | 将 MVC 校验、JSON 解析和未处理异常转换为 HTTP 响应 |
| V1 | 初始创建 `tickets` 表和索引 |
| V2 | 把旧状态值迁移到 Java 枚举体系，并更新列默认值和注释 |

基础包为 `com.xiaoyang.aiticketplatform`。启动类位于该包根部，因此 Spring Boot 默认组件扫描覆盖其所有子包，能够发现 Controller、Service、Mapper 和异常处理器。

## 三、完整请求调用链

以下按真实类和方法拆解一次合法请求的执行过程。

1. 客户端向 `POST /api/tickets` 发送 JSON。
2. `DispatcherServlet` 根据 `TicketController` 上的 `@RequestMapping("/api/tickets")` 和方法上的 `@PostMapping` 找到创建方法。
3. Jackson 根据 `@RequestBody` 将 JSON 转换为 `CreateTicketRequest` record。
4. `@Valid` 触发 Jakarta Bean Validation，对 record 组件上的约束执行校验。
5. 校验通过后，`TicketController` 调用 `ticketService.createTicket(request)`。
6. Spring 实际调用的是 `TicketServiceImpl` 的代理对象；`@Transactional` 在进入方法前开启或加入事务。
7. `TicketServiceImpl.createTicket` 创建普通 `Ticket` 对象。
8. Service 将请求中的四个字段逐一映射到实体，并显式设置 `TicketStatus.OPEN`。
9. Service 不设置 `id`、`createdAt`、`updatedAt`，调用 `ticketMapper.insert(ticket)`。
10. `TicketMapper` 继承的 MyBatis-Plus `BaseMapper.insert` 生成并执行 INSERT SQL。
11. MySQL 生成自增 ID；`created_at` 和 `updated_at` 使用数据库默认时间。
12. JDBC/MyBatis 将生成的 ID 回填到当前 `Ticket` 对象。
13. Service 检查影响行数必须为 1、ID 必须已回填，然后直接组装 `TicketResponse`，不再查询数据库。
14. Service 正常返回后事务提交；Controller 用 `ApiResponse.success(response)` 包装数据，并通过 `ResponseEntity.status(HttpStatus.CREATED)` 返回 HTTP 201。

这条链路中每一层只承担一种主要责任：Controller 处理 HTTP，Service 处理业务用例和事务，Mapper 处理持久化入口，数据库负责实际数据约束、默认值和持久化。

## 四、请求反序列化与参数校验

### 4.1 JSON 如何变成请求对象

`CreateTicketRequest` 是 Java record：

```text
CreateTicketRequest(
    String title,
    String description,
    String creatorName,
    TicketPriority priority
)
```

Spring MVC 使用 Jackson 调用 record 的规范构造器完成反序列化。枚举字段默认按枚举常量名解析，因此请求值应为 `LOW`、`MEDIUM`、`HIGH` 或 `URGENT`。例如传入 `"HIGH"` 可以解析，传入不认识的枚举值或结构不合法的 JSON 会触发 `HttpMessageNotReadableException`。

### 4.2 实际校验规则

| 字段 | 注解 | 作用 |
|---|---|---|
| `title` | `@NotBlank` | 拒绝 `null`、空字符串和仅包含空白字符的值 |
| `title` | `@Size(max = 120)` | 限制 Java 字符串长度不超过 120 |
| `description` | `@NotBlank` | 描述必须存在且不能只包含空白字符 |
| `description` | `@Size(max = 2000)` | 接口层将描述限制为最多 2000 个字符 |
| `creatorName` | `@NotBlank` | 创建人名称必须存在且非纯空白 |
| `creatorName` | `@Size(max = 64)` | 与数据库 `VARCHAR(64)` 的长度边界一致 |
| `priority` | `@NotNull` | 优先级必须明确提供 |

`@NotBlank` 比 `@NotNull` 更适合字符串，因为 `@NotNull` 会允许 `""` 和 `"   "`。`@Size` 负责上限，两类注解组合后才能同时表达“必填”和“最大长度”。

### 4.3 校验失败如何返回

校验失败时，Controller 方法不会被调用。Spring 抛出 `MethodArgumentNotValidException`，由 `GlobalExceptionHandler.handleMethodArgumentNotValidException` 转换为 HTTP 400。

处理器使用 `LinkedHashMap<String, String>` 收集字段错误，并通过 `putIfAbsent` 保留每个字段的第一条错误消息。错误响应使用 `ErrorCode.VALIDATION_ERROR`，当前错误码为 `40000`。

JSON 语法错误、枚举值无法反序列化等问题不是 Bean Validation 错误，而由 `handleHttpMessageNotReadableException` 处理，返回 HTTP 400 和错误码 `40001`，当前不返回详细内部解析信息。

### 4.4 边界认识

- Validation 保护的是 API 输入，不能代替数据库 `NOT NULL` 和列长度限制。
- 数据库约束保护所有写入入口，包括未来的批处理、脚本或其他服务。
- DTO 限制 `description` 最大 2000，但数据库列为 `TEXT`，这是应用规则比物理容量更严格，并非类型冲突。
- 当前没有手动进行 `trim`，所以含有首尾空格但并非全空白的内容会通过 `@NotBlank`，并按原值保存。

## 五、DTO、Entity 与响应对象

### 5.1 为什么请求和响应使用 record

`CreateTicketRequest` 和 `TicketResponse` 都是不可变的数据载体，字段固定、没有生命周期变化，也不需要无参构造器和 setter。Java 21 record 自动提供构造器、访问器、`equals`、`hashCode` 和 `toString`，可以减少样板代码，并突出 DTO 只是边界数据结构。

请求 record 的访问方式是 `request.title()`，而不是 JavaBean 风格的 `getTitle()`。

### 5.2 为什么 Entity 不使用 record

`Ticket` 是普通可变 Java 类。MyBatis-Plus 插入后需要把数据库生成的 ID 写回实体，查询映射时也通常依赖无参构造和属性写入。实体还可能在持久化生命周期内逐步获得 ID、时间戳或状态，因此普通类更符合当前 ORM/Mapper 使用方式。

这并不表示任何 ORM 都绝对不能支持 record，而是当前 MyBatis-Plus 插入回填与项目风格使用可变实体最直接、风险最低。

### 5.3 请求 DTO 到 Entity 的映射

| 请求字段 | 实体字段 | 映射方式 |
|---|---|---|
| `request.title()` | `ticket.title` | Service 显式 setter |
| `request.description()` | `ticket.description` | Service 显式 setter |
| `request.creatorName()` | `ticket.creatorName` | Service 显式 setter |
| `request.priority()` | `ticket.priority` | Service 显式 setter |
| 请求中不存在 | `ticket.status` | Service 显式设置 `OPEN` |
| 请求中不存在 | `ticket.id` | 不设置，由 MySQL 生成 |
| 请求中不存在 | `ticket.createdAt` | 不设置，由 MySQL 默认值生成 |
| 请求中不存在 | `ticket.updatedAt` | 不设置，由 MySQL默认值生成 |

项目没有使用 `BeanUtils`、反射复制、MapStruct 或转换工具类。当前字段少，显式映射更容易审查，也能清楚展示哪些字段来自用户、哪些字段由业务规则控制。

### 5.4 Entity 到响应 DTO 的映射

插入完成后，Service 从当前实体读取 `id`、`title`、`description`、`creatorName`、`priority` 和 `status`，构造 `TicketResponse`。当前响应不包含 `createdAt` 和 `updatedAt`，所以 Service 不执行额外的 `selectById`。

这种做法少一次数据库查询，但也意味着响应只包含应用已经持有或已经回填的字段。若未来响应必须返回数据库生成的时间值，需要重新评估：可以查询一次、使用数据库返回能力，或改变时间字段的生成策略。

### 5.5 为什么不直接返回 Entity

不直接返回 Entity 可以避免把数据库结构变成外部 API 契约，也避免未来实体新增内部字段时意外泄露。DTO 允许 API 和数据库模型分别演进，并让接口明确控制可接收与可返回的字段。

## 六、Service 与事务

### 6.1 Service 接口的边界

`TicketService` 只定义：

```text
TicketResponse createTicket(CreateTicketRequest request)
```

它没有继承 MyBatis-Plus `IService`，没有向上层暴露 `Ticket`，也没有加入尚未需要的查询、更新或删除方法。这使接口直接表达“创建工单”业务用例，而不是数据库 CRUD 集合。

### 6.2 创建流程

`TicketServiceImpl.createTicket` 的真实处理顺序是：

1. 新建 `Ticket`。
2. 显式复制四个请求字段。
3. 显式设置 `TicketStatus.OPEN`。
4. 调用 `ticketMapper.insert(ticket)`。
5. 若影响行数不为 1，抛出带明确信息的 `IllegalStateException`。
6. 若影响行数为 1 但 `ticket.getId()` 仍为 `null`，抛出 `IllegalStateException`。
7. 从实体组装并返回 `TicketResponse`。

Service 不捕获并吞掉数据库异常。数据库异常会继续向上抛出，使事务能够回滚，并最终由全局异常处理器返回通用 500 响应。

### 6.3 为什么显式设置 OPEN

“新建工单状态为 OPEN”属于创建用例的业务规则，因此由 Service 明确表达。数据库 `DEFAULT 'OPEN'` 是防御性兜底，保护省略该列的其他写入路径，但不能代替应用业务语义。

显式赋值的好处包括：

- 单元测试无需数据库也能验证业务规则。
- 阅读 Service 即可知道创建状态，不必查表定义。
- 数据库默认值未来变更时，应用规则不会悄悄改变。
- 返回 DTO 可以直接使用实体中的 `OPEN`，无需回查。

### 6.4 为什么事务放在 Service

事务边界应围绕业务用例，而不是围绕 HTTP 或单个 Mapper 方法。当前只有一次 INSERT，数据库语句本身具备原子性，因此从最低限度看，不加 Service 事务也可能完成插入；但 `@Transactional` 仍有实际价值：

- INSERT 后的结果检查仍处于同一事务内。
- 如果影响行数或 ID 回填检查失败并抛出运行时异常，事务可回滚。
- 未来创建用例增加日志表、标签或事件记录时，可以保持一个业务事务。
- Controller 不需要了解数据一致性细节。

Spring 默认对未捕获的 `RuntimeException` 和 `Error` 回滚。当前抛出的 `IllegalStateException` 属于运行时异常，因此不需要写多余的 `rollbackFor = Exception.class`。

### 6.5 事务代理和自调用

`@Transactional` 依赖 Spring 代理。外部 Bean 通过注入的 `TicketService` 调用 `createTicket` 时会经过代理，事务生效。如果同一个对象内部用 `this.createTicket(...)` 自调用，通常不会经过代理，事务增强可能失效。这是 Spring AOP 面试中常见的边界问题。

### 6.6 为什么使用构造器注入

`TicketServiceImpl` 和 `TicketController` 都通过构造器注入依赖。依赖在对象创建时必须完整提供，字段可以保持不可变，也便于在单元测试中直接构造真实对象或由 Mockito 注入 Mock。它比字段注入更显式，且不依赖反射才能完成基本对象初始化。

## 七、MyBatis-Plus 与数据库插入

### 7.1 表和主键映射

`Ticket` 使用 `@TableName("tickets")` 显式映射表名。主键配置为：

```text
@TableId(value = "id", type = IdType.AUTO)
```

`IdType.AUTO` 告诉 MyBatis-Plus 主键由数据库自增产生，插入前应用不生成 ID。V1 中 `id` 的实际定义为 `BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY`，与此策略一致。

插入成功后，MyBatis/JDBC 获取 MySQL generated key 并写回同一个 `Ticket` 对象，所以 Service 能检查 `ticket.getId()` 并把它放入响应。

### 7.2 字段映射

项目启用了下划线转驼峰，因此无需给每个普通字段添加 `@TableField`：

| Java 字段 | 数据库列 | 数据库定义 |
|---|---|---|
| `id` | `id` | `BIGINT UNSIGNED`，自增主键 |
| `title` | `title` | `VARCHAR(120) NOT NULL` |
| `description` | `description` | `TEXT NOT NULL` |
| `creatorName` | `creator_name` | `VARCHAR(64) NOT NULL` |
| `priority` | `priority` | `VARCHAR(16) NOT NULL` |
| `status` | `status` | `VARCHAR(16) NOT NULL DEFAULT 'OPEN'`（V2 后） |
| `createdAt` | `created_at` | `DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)` |
| `updatedAt` | `updated_at` | `DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)` |

表还包含两个实际索引：`idx_tickets_status_created_at(status, created_at)` 和 `idx_tickets_creator_name(creator_name)`。

### 7.3 普通枚举如何写入 VARCHAR

`TicketPriority` 和 `TicketStatus` 只是普通 Java enum，没有实现 `IEnum`，没有 `@EnumValue`，也没有自定义 TypeHandler。MyBatis-Plus/MyBatis 按枚举名称与 `VARCHAR` 交互，因此 `TicketPriority.HIGH` 写入 `HIGH`，`TicketStatus.OPEN` 写入 `OPEN`；查询时相同字符串被还原为对应枚举。

这种方案简单直观，但数据库中的值必须和 Java 枚举常量完全一致。未知字符串无法正常转换为当前枚举，新增或重命名枚举值时必须考虑数据库兼容和迁移。

### 7.4 Mapper 如何注册

`TicketMapper` 同时满足：

- 使用 `@Mapper`，由 MyBatis/Spring 注册为 Bean；
- 继承 `BaseMapper<Ticket>`，获得 `insert`、`selectById` 等通用方法；
- 没有 XML Mapper；
- 没有自定义 SQL；
- 没有额外 `@MapperScan` 配置。

### 7.5 数据库默认字段和回填边界

插入时 Service 不设置 `createdAt` 和 `updatedAt`。MyBatis-Plus 默认字段策略会省略值为 `null` 的普通字段，MySQL 因而使用列默认值。

数据库生成时间并不会像自增主键那样自动回填到原始实体。Mapper 集成测试和迁移集成测试都是插入后再调用 `selectById`，从查询结果验证时间字段非空。Service 当前不需要返回时间，因此不额外查询。

V2 的数据库默认 `OPEN` 只在 INSERT 省略 `status` 列时生效；如果 SQL 显式写入 `NULL`，会触发 `NOT NULL` 约束，而不是使用默认值。迁移集成测试验证了当前 MyBatis-Plus 策略会在实体 status 为 null 时省略该列。

## 八、Flyway 与数据库演进

### 8.1 V1 的初始结构

`V1__create_ticket_table.sql` 创建 `tickets` 表、主键和两个索引。它最初使用的状态体系为：

```text
PENDING, PROCESSING, COMPLETED, CLOSED
```

初始 `status` 默认值为 `PENDING`；初始 priority 注释只列出 `LOW, MEDIUM, HIGH`。这与后来确定的 Java 枚举并不完全一致。

### 8.2 V2 如何对齐状态体系

`V2__align_ticket_enum_values.sql` 执行三类动作：

1. 用带 `CASE` 的 UPDATE 转换已有旧状态：
   - `PENDING` → `OPEN`
   - `PROCESSING` → `IN_PROGRESS`
   - `COMPLETED` → `RESOLVED`
2. 把 `priority` 保持为 `VARCHAR(16) NOT NULL`，并把列注释更新为 `LOW, MEDIUM, HIGH, URGENT`。
3. 把 `status` 保持为 `VARCHAR(16) NOT NULL`，默认值改为 `OPEN`，注释更新为 `OPEN, IN_PROGRESS, RESOLVED, CLOSED`。

UPDATE 的 WHERE 只包含三个明确旧值。`CLOSED` 不需要转换，未知状态也不会被静默改成 `OPEN`，从而避免掩盖脏数据。

### 8.3 为什么不能直接修改 V1

已经在环境中执行过的 Flyway 迁移是数据库演进历史。直接修改 V1 会改变校验和，导致已有数据库出现 migration checksum mismatch；即使强行修复，也会让“新建数据库执行的历史”和“旧数据库曾经执行的历史”不一致。

正确做法是保留 V1 原样，用只前进的新版本 V2 显式描述数据和表结构变化。这样既能升级已有数据库，也能让空数据库按 V1 → V2 得到相同最终结构。

### 8.4 Flyway 与测试事务的关系

在 `@SpringBootTest` 中，Flyway 通常在 Spring 测试上下文启动、测试方法事务开始之前完成迁移。测试方法上的 `@Transactional` 只回滚测试方法产生的业务数据，不会回滚已经完成的 Flyway 迁移记录。因此：

- 集成测试插入的工单可以回滚；
- `flyway_schema_history` 中的 V2 成功记录会正常保留；
- 测试回滚仍可能消耗 MySQL 自增 ID，这是正常行为。

## 九、统一响应与异常处理

### 9.1 统一响应结构

`ApiResponse<T>` 是 record，字段为：

```text
int code
String message
T data
```

成功由 `ApiResponse.success(data)` 创建，当前业务码为 `0`、消息为 `success`。失败由 `ApiResponse.failure(ErrorCode)` 或带详细 data 的重载创建。

HTTP 状态码和业务码承担不同职责：HTTP 201/400/500 表达协议层结果，`code` 表达应用层结果。客户端不应只看 HTTP 200，因为创建成功实际返回 HTTP 201。

### 9.2 当前错误码

| 枚举 | code | message | HTTP 状态 |
|---|---:|---|---:|
| `VALIDATION_ERROR` | 40000 | 请求参数校验失败 | 400 |
| `MESSAGE_NOT_READABLE` | 40001 | 请求体格式错误或字段类型不匹配 | 400 |
| `INTERNAL_ERROR` | 50000 | 服务器内部错误 | 500 |

### 9.3 全局异常处理

`GlobalExceptionHandler` 使用 `@RestControllerAdvice`，目前有三条处理路径：

1. `MethodArgumentNotValidException`：返回字段级校验消息 Map，HTTP 400。
2. `HttpMessageNotReadableException`：不暴露 Jackson 的内部解析细节，HTTP 400。
3. `Exception`：记录完整服务端错误日志，对客户端只返回通用内部错误，HTTP 500。

当前 Service 抛出的两个 `IllegalStateException` 没有专用处理器，因此会落入通用 `Exception` 分支，返回 500。这与“持久化未达到预期属于服务端失败”的当前设计一致，但项目目前没有自定义业务异常层次。

### 9.4 安全与可观测性

处理器把详细异常保留在服务端日志中，同时避免将堆栈、SQL、数据库连接信息或内部异常消息返回给客户端。这降低了信息泄露风险。参数类错误使用 warn 日志，未预期异常使用 error 日志并携带异常对象。

### 9.5 数据库成功但响应失败的边界

`@Transactional` 只覆盖 Service 方法。Service 正常返回后事务提交，随后 Controller 包装响应、Jackson 序列化并通过网络发送。如果数据库已经提交，但响应序列化或网络发送失败，客户端可能没有收到成功响应，而数据已经存在。

当前接口没有幂等键，客户端盲目重试可能产生重复工单。P1 尚未实现幂等机制；面试时应如实说明这是写接口的后续工程问题，不能声称当前已经解决。

## 十、测试体系

当前测试不是单一的“跑通接口”，而是按层验证不同责任。最近一次现有 Surefire 报告合计：`Tests run: 28, Failures: 0, Errors: 0, Skipped: 0`。

### 10.1 测试分层总览

| 测试类别 | 实际测试类 | 是否启动 Spring | 是否使用 Mock | 是否连接真实 MySQL | 主要证明 |
|---|---|---:|---:|---:|---|
| DTO 校验测试 | `CreateTicketRequestValidationTest` | 否 | 否 | 否 | Validation 注解的边界规则 |
| 统一响应单元测试 | `ApiResponseTest` | 否 | 否 | 否 | 成功/失败工厂方法的响应结构 |
| Service 单元测试 | `TicketServiceImplTest` | 否 | 是，Mapper | 否 | 业务映射、OPEN 规则、异常分支、ID 回填检查 |
| Mapper 集成测试 | `TicketMapperIntegrationTest` | 是 | 否 | 是 | Mapper → MP → JDBC → MySQL 的真实链路 |
| Flyway/默认值集成测试 | `TicketStatusMigrationIntegrationTest` | 是 | 否 | 是 | status 被省略时数据库默认 OPEN 及查询映射 |
| 异常处理 MockMvc 测试 | `GlobalExceptionHandlerTest` | Standalone MVC | 否 | 否 | 400/500 响应结构与异常路由 |
| Controller MockMvc 测试 | `TicketControllerTest` | Standalone MVC | 是，Service | 否 | HTTP 路由、201、校验及 Service 协作 |
| 全链路集成测试 | `TicketCreationIntegrationTest` | 是 | 否 | 是 | 真实 HTTP 层到数据库及回滚 |
| 上下文加载测试 | `AiTicketPlatformApplicationTests` | 是 | 否 | 是（当前配置） | 应用上下文能够启动 |

### 10.2 DTO 校验测试

`CreateTicketRequestValidationTest` 直接通过 Jakarta `Validator` 校验 DTO，不启动 Spring，也不连接数据库。它覆盖合法请求，以及 title 空字符串、纯空格、超长，description 为空、超长，creatorName 为空、超长，priority 为 null，共 9 个测试。

它能快速、精确地证明注解规则，但不能证明 Spring MVC 确实触发了 `@Valid`，也不能证明错误会被转换为正确 HTTP 响应。

### 10.3 Service 单元测试

`TicketServiceImplTest` 使用 JUnit 5、MockitoExtension、Mock `TicketMapper` 和真实 `TicketServiceImpl`，不启动 Spring、不连接数据库。

三个场景分别验证：

1. 成功：在 Mockito Answer 中先确认 Service 没有预生成 ID，再模拟数据库回填 `100L`；捕获实体并验证字段映射和 OPEN 状态；验证响应字段。
2. insert 返回 0：验证抛出插入失败的 `IllegalStateException`，Mapper 只调用一次。
3. insert 返回 1 但不回填 ID：验证抛出“自增 ID 未回填”的 `IllegalStateException`。

它不能证明 `@Transactional` 代理实际生效，不能证明 SQL 正确，也不能证明 MySQL 真能回填 ID。

### 10.4 Mapper 集成测试

`TicketMapperIntegrationTest` 使用 `@SpringBootTest`、`@Transactional` 和真实 MySQL。它插入显式 `HIGH`、`OPEN` 的实体，验证影响行数、ID 回填，再按 ID 查询并验证所有业务字段、枚举以及数据库生成的两个时间字段。

它证明真实持久化映射，但绕过 Controller 和 Service，不能证明 HTTP 契约或 Service 业务规则。

### 10.5 迁移默认值集成测试

`TicketStatusMigrationIntegrationTest` 故意不设置 Java status，通过真实插入验证：

```text
Java status 为 null
→ MyBatis-Plus 省略 status 列
→ MySQL 使用 DEFAULT 'OPEN'
→ selectById 映射为 TicketStatus.OPEN
```

测试同时验证 ID 回填和时间字段。它证明数据库兜底机制，但正常创建 Service 仍显式设置 OPEN，不能用这个测试替代 Service 业务规则测试。

### 10.6 异常处理 MockMvc 测试

`GlobalExceptionHandlerTest` 使用 standalone MockMvc、真实 Validator 和 Jackson 消息转换器，不启动完整 Spring Boot、不连接数据库。它覆盖字段校验失败、非法枚举或无法读取的请求体、以及通用异常等 HTTP 错误响应。

它能够精确验证 handler 的响应格式，但不能证明完整应用中的 Bean 扫描、事务或数据库行为。

### 10.7 Controller MockMvc 测试

`TicketControllerTest` 使用真实 Controller、Mock Service、真实异常处理器、Validator 和 Jackson 转换器。它覆盖合法创建返回 HTTP 201 和统一响应、校验失败不调用 Service、请求体不可读返回 400 等场景。

它验证 Web 层契约和层间协作，但 Mock Service 意味着它无法证明 Service、Mapper 或数据库链路。

### 10.8 全链路集成测试

`TicketCreationIntegrationTest` 使用完整 Spring Boot WebApplicationContext、MockMvc、真实 Controller、Service、Mapper、Flyway 后的 MySQL，并通过 `@Transactional` 回滚测试数据。它覆盖成功请求落库、响应 ID 与数据库 ID 对应，以及非法请求不会新增记录等场景。

这是最接近真实调用的测试，但运行较慢、依赖本地 MySQL 健康状态。它不能替代快速单元测试，因为失败时定位范围更大，也不适合穷举所有校验边界。

### 10.9 为什么各层测试不能互相替代

- 单元测试快且定位准，但 Mock 可能与真实框架行为不一致。
- Mapper 集成测试能发现 SQL、字段、枚举和 ID 回填问题，但不经过 HTTP 和业务层。
- MockMvc 切片式测试能验证协议细节，但 Mock 掉的下层不会暴露数据库问题。
- 全链路测试提供最高整体信心，但成本高、定位慢、环境依赖强。

合理的测试金字塔是让大量快速测试覆盖细节，再用少量真实集成测试确认层与层之间的连接。

## 十一、常见错误和边界情况

### 11.1 把数据库默认值当成业务规则

错误做法是 Service 不设置 status，只依赖数据库 `DEFAULT 'OPEN'`。这样业务意图隐藏在 DDL 中，单元测试无法直接确认，且响应若不回查数据库便拿不到状态。当前实现由 Service 显式设置 OPEN，数据库默认只做兜底。

### 11.2 手动生成自增 ID

实体配置 `IdType.AUTO` 后，不应在 Service 用时间戳、随机数或自增变量生成 ID。应用生成值可能与 MySQL 策略冲突，也绕开 generated keys 回填链路。

### 11.3 误以为所有数据库默认字段都会回填实体

MySQL 自增主键可通过 generated keys 回填，但 `created_at`、`updated_at` 的数据库默认值不会自动出现在原实体中。要验证时间字段，需要重新查询；当前 Service 因响应不需要时间而不查询。

### 11.4 枚举字符串和数据库值不一致

普通枚举映射依赖名称一致。数据库遗留 `PENDING` 而 Java 只有 `OPEN` 时，查询会失败。V2 的价值正是迁移历史数据并统一最终列语义。

### 11.5 直接修改已执行的 V1

这会造成 Flyway 校验和不一致，并破坏环境间迁移历史。数据库演进必须新增版本迁移。

### 11.6 以为 `@Transactional` 在任何调用方式下都有效

注解依赖代理。对象内部自调用、手动 `new TicketServiceImpl(...)` 或方法不经过 Spring Bean 代理时，不会获得相同事务增强。Mockito 单元测试也不验证事务代理。

### 11.7 捕获并吞掉异常

若 Service 捕获数据库异常后只记录日志并正常返回，事务可能提交或上层误判成功。当前实现不吞异常，让异常向上抛出并触发事务回滚。

### 11.8 只验证 HTTP 结果，不验证数据库

Mock Service 的 Controller 测试即使返回 201，也不能证明真实数据已写入。全链路集成测试需要同时检查响应和数据库记录。

### 11.9 只做全链路测试

只依赖完整集成测试会导致运行慢、边界覆盖不足、失败难定位。DTO、Service、Controller、Mapper 各层测试仍然必要。

### 11.10 在事务测试中断言自增 ID 连续

MySQL 自增值即使事务回滚也可能被消耗。测试只应断言 ID 非 null 且大于 0，不应断言连续或固定。

### 11.11 请求枚举大小写错误

当前 Jackson 按枚举常量名解析。`"HIGH"` 合法，`"high"` 在没有额外配置时不能按当前 enum 正常解析，会进入请求体不可读处理，而不是 `@NotNull` 校验分支。

### 11.12 空白字符串和未规范化输入

纯空格会被 `@NotBlank` 拒绝，但 `"  标题  "` 会通过并保留空格。当前没有输入规范化策略；不能声称项目已经自动 trim。

### 11.13 数据库插入成功但客户端未收到响应

事务提交与网络交付不是一个原子操作。客户端超时重试可能重复创建。目前没有幂等机制，后续可以根据业务引入客户端请求号、唯一约束或幂等记录，但这不属于当前 P1 已完成范围。

### 11.14 通用异常返回内部细节

把数据库异常消息、SQL 或堆栈直接返回会泄露内部结构。当前 handler 对外返回通用 500，对内记录详细日志，是更安全的默认做法。

## 十二、面试追问与参考回答

以下回答以当前项目为依据，目标是能讲清“为什么这样设计”，而不是背概念。

### 12.1 基础层问题

#### 1. 这个接口接收和返回哪些字段？

请求接收 title、description、creatorName、priority。id、status 和时间字段不允许客户端控制。Service 将 status 设置为 OPEN，MySQL 生成 id 和时间。响应当前返回 id、四个请求相关字段和 status，不返回时间字段。

#### 2. 为什么 DTO 使用 record？

DTO 只是固定的数据载体，不需要修改状态。record 减少构造器、getter、equals 等样板代码，同时表达不可变性。当前项目使用 Java 21，Jackson 和 Validation 都能支持 record。

#### 3. `@NotBlank` 和 `@NotNull` 有什么区别？

`@NotNull` 只拒绝 null，空字符串仍合法；`@NotBlank` 还拒绝空字符串和纯空白。项目中的三个 String 字段使用 NotBlank，枚举 priority 使用 NotNull。

#### 4. 为什么成功返回 HTTP 201？

因为请求成功创建了新的服务端资源。201 Created 比通用 200 更准确表达结果；当前资源查询接口尚未实现，所以没有额外声称返回 Location 头。

#### 5. 新工单状态为什么是 OPEN？

这是创建工单用例的业务规则。Service 显式赋值，数据库默认 OPEN 只是其他写入路径省略 status 时的兜底。

#### 6. Mapper 为什么不写 XML？

当前只有标准 insert 和 selectById，`BaseMapper<Ticket>` 已提供实现，不需要自定义 SQL。等出现复杂查询时再评估注解 SQL 或 XML。

#### 7. Entity 为什么需要 setter？

Service 逐步组装实体，MyBatis 查询时需要写入属性，插入后还要把生成 ID 回填到对象。普通可变类与当前持久化方式最匹配。

#### 8. 为什么 Controller 不直接调用 Mapper？

Controller 应处理 HTTP，创建状态、事务和插入结果判断是业务职责。通过 Service 隔离后，业务逻辑可单元测试，也不会与 Web 层绑定。

#### 9. 请求 priority 传 `high` 会怎样？

当前按枚举名称解析，没有大小写兼容配置，因此反序列化失败，进入 `HttpMessageNotReadableException` 处理，返回 HTTP 400 和业务码 40001。

#### 10. 创建接口为什么不能接收 id？

id 是 MySQL 自增主键，应由服务端持久化策略控制。允许客户端提供会引入冲突、越权修改语义和主键生成不一致。

### 12.2 原理层问题

#### 1. MySQL 自增 ID 如何回到 Java 对象？

实体主键使用 `IdType.AUTO`。MyBatis-Plus 执行 INSERT 时使用 JDBC generated keys，数据库返回生成主键后，框架把值写入同一个 Ticket 实体。因此 Service 在 insert 后可以读取 `ticket.getId()`。

#### 2. 为什么 createdAt 没有同时回填？

自增主键有专门的 generated key 返回机制，而时间字段只是数据库 default 表达式的结果，当前 INSERT 不会把它们一起返回并映射。项目通过 selectById 验证时间，Service 因响应不需要时间而不回查。

#### 3. 普通 enum 如何保存到 VARCHAR？

当前没有 IEnum、EnumValue 或自定义处理器，MyBatis 按 enum 的名称进行字符串映射，所以 HIGH 和 OPEN 分别写成同名 VARCHAR。数据库数据必须能匹配 Java 常量。

#### 4. 为什么还需要数据库 NOT NULL，Validation 不够吗？

Validation 只覆盖走这个 API 且触发校验的请求。数据库可能被测试、脚本或未来其他服务访问，NOT NULL 是最后一致性防线。两层约束负责不同边界。

#### 5. `@Transactional` 为什么通常放 Service？

事务应覆盖完整业务用例。Controller 是协议层，Mapper 是单条持久化操作；Service 最清楚哪些写操作必须一起成功或失败。当前也能让 insert 后的结果检查异常触发回滚。

#### 6. `@Transactional` 为什么可能失效？

Spring 通常通过代理拦截外部方法调用。类内部 `this` 自调用不会经过代理；手动 new 出来的对象也不是 Spring Bean。当前真实 Controller 注入 Service 会经过代理，而 Mockito 单元测试只验证方法逻辑。

#### 7. MyBatis-Plus 为什么会让数据库默认 OPEN 生效？

迁移测试中实体 status 为 null，当前字段插入策略省略 null 字段，生成的 INSERT 不包含 status，MySQL 才使用 DEFAULT OPEN。如果 SQL 明确写 status = NULL，NOT NULL 会报错。

#### 8. Flyway 为什么使用 V2 而不是改 V1？

V1 已经可能在数据库执行并记录校验和。修改它会让迁移历史不可信并触发校验失败。V2 能安全把已有数据从旧枚举迁移到新枚举，也让新数据库按顺序获得相同最终结构。

#### 9. `updated_at ON UPDATE` 什么时候变化？

插入时使用默认当前时间；后续某次 UPDATE 真正修改行时，MySQL 自动更新它。当前项目尚未实现更新接口，所以只验证插入后的值非空，没有声称已验证业务更新链路。

#### 10. 为什么 Service 插入后不 selectById？

当前响应字段除 ID 外都已在实体中，ID 又会自动回填，因此回查只会增加一次数据库往返。时间字段虽由数据库生成，但响应暂不包含它们。

### 12.3 工程实践层问题

#### 1. 你如何证明不是只写了“看起来能工作”的代码？

项目按层测试：DTO 有直接 Validation 测试，Service 用 Mockito 验证规则和异常，Mapper 用真实 MySQL 验证 SQL、枚举和 ID，Controller/异常处理用 MockMvc，最后用完整 Spring 上下文和真实 MySQL 验证 POST 到落库。现有报告为 28 个测试全部通过。

#### 2. 为什么既有 Mapper 集成测试又有全链路测试？

Mapper 测试专注持久化映射，失败时容易定位 SQL、字段和 type mapping；全链路测试证明 Web、Validation、事务、Service、Mapper 能共同工作。两者关注范围不同。

#### 3. 如何避免测试污染数据库？

真实数据库集成测试使用 `@Transactional`，测试方法结束后回滚业务数据。Flyway 迁移在测试事务之前执行并正常保留。自增 ID 被消耗不等于数据污染。

#### 4. 如果 insert 返回 1 但 ID 没回填，为什么要失败？

当前 API 必须返回新资源 ID。没有 ID 表示主键策略、驱动或映射可能配置错误，继续返回会产生不完整成功结果，所以 Service 抛出运行时异常并回滚。

#### 5. 为什么不捕获数据库异常转换成固定成功或 null？

吞异常会隐藏失败并破坏事务语义。当前让异常上抛，由事务回滚和全局处理器统一返回 500，同时服务端日志保留根因。

#### 6. 如果请求超时，客户端重试会怎样？

当前没有幂等键，第一次可能已经提交，重试可能产生重复工单。这是当前边界。后续可设计 requestId 和唯一约束等机制，但不能把它说成已实现。

#### 7. 为什么全局异常处理不能把 exception.message 原样返回？

异常消息可能包含 SQL、表名、连接信息或堆栈线索。项目对外返回稳定通用错误码，对内记录异常，兼顾安全、API 稳定性和排查能力。

#### 8. 数据库 enum 和 VARCHAR 你会如何选择？

当前选 VARCHAR 加 Java enum，迁移和扩展相对直接，但数据库不会自动限制所有合法值。MySQL ENUM 约束更强，却会把业务枚举演进紧密绑定 DDL。当前项目不添加 CHECK，靠应用枚举、迁移和测试保持一致。

#### 9. 当前设计最值得后续改进的点是什么？

可以根据下一阶段需求考虑幂等、可观测性、专用业务异常、查询接口及状态流转规则。但应按真实需求小步加入；当前只实现创建链路，不能提前声称这些能力存在。

#### 10. 如何确认 V2 没有错误转换未知状态？

迁移 UPDATE 的 WHERE 只匹配 PENDING、PROCESSING、COMPLETED，CASE 也只转换这三个值。CLOSED 保持不变，未知值不在更新范围，不会被静默设为 OPEN。

## 十三、可独立重写练习

以下练习不提供完整答案，目的是验证能否脱离现有代码重新建立关键能力。

### 练习一：重写请求 DTO 和校验测试

任务：从空文件开始写一个 Java 21 `CreateTicketRequest` record，并用直接创建的 Jakarta Validator 覆盖四个字段的合法与非法边界。

验收标准：

- 字段和枚举类型准确。
- 三个字符串同时具备必填和长度上限。
- priority 拒绝 null。
- 不启动 Spring、不连接数据库。
- 至少覆盖最大长度刚好合法与超出 1 个字符的边界。

常见错误：只用 `@NotNull` 校验字符串；把注解写到无关 getter；使用 `@SpringBootTest` 让简单测试变慢；忘记关闭 ValidatorFactory。

### 练习二：重写 Service 与 Mockito 测试

任务：实现显式 DTO → Entity → Response 映射，调用一个 Mock Mapper，并处理影响行数异常和 ID 未回填异常。

验收标准：

- Service 显式设置 OPEN，不依赖数据库默认。
- 插入前 id 和时间为空。
- Mockito Answer 在设置 ID 前验证 id 为 null。
- Mapper 每个场景只调用一次。
- 测试不启动 Spring、不连接 MySQL。

常见错误：在 Answer 回填后再断言 captured id 为 null；返回 Entity；调用 selectById；捕获异常后返回空响应；手动生成主键。

### 练习三：重写完整创建接口的测试设计

任务：只写测试方案和关键断言，设计 Controller MockMvc 测试、Mapper 真实 MySQL 测试和全链路集成测试三层，不复制现有测试代码。

验收标准：

- 能说明每层 Mock 什么、真实使用什么。
- 成功场景验证 HTTP 201、统一响应和数据库记录。
- 非法请求验证 HTTP 400、Service 不被调用或数据库无新增。
- 真实数据库测试使用事务回滚，但不假设自增 ID 连续。
- 能解释为什么三层测试不能互相替代。

常见错误：所有测试都用 `@SpringBootTest`；Controller 测试连接真实数据库；只断言状态码；依赖固定数据库行数；为清理数据执行删库或 Flyway clean。

## 十四、2–3 分钟口述稿

我在这个项目的 P1 阶段完成了一条真实的创建工单链路，接口是 `POST /api/tickets`。客户端提交标题、描述、创建人和优先级，Spring MVC 先把 JSON 反序列化成 Java 21 record，并通过 Jakarta Validation 校验非空和长度边界。校验失败会由全局异常处理器返回统一的 400 响应；非法 JSON 或枚举值也有单独的请求体错误码。

业务层没有直接暴露 Entity，而是由 `TicketService` 表达创建用例。实现类通过构造器注入 Mapper，并在事务中显式把请求字段映射到 `Ticket`。新工单状态由 Service 设置为 `OPEN`，因为这是业务规则；数据库虽然也有默认 OPEN，但它只是兜底。插入后我检查影响行数和自增 ID 回填，任何异常都会继续抛出并让事务回滚。响应当前不包含时间字段，因此不会为了获取数据库时间再多查一次。

持久化层使用 MyBatis-Plus。`TicketMapper` 继承 `BaseMapper`，实体通过 `@TableName` 映射 tickets 表，主键用 `IdType.AUTO` 对齐 MySQL 自增。普通枚举按名称写入 VARCHAR。数据库最初的状态值和 Java 枚举不一致，我没有改已经执行的 V1，而是增加 V2，把 PENDING、PROCESSING、COMPLETED 迁移成 OPEN、IN_PROGRESS、RESOLVED，同时把默认状态改成 OPEN，保留未知状态不动。

测试方面我做了分层验证：DTO 校验直接用 Validator，Service 用 Mockito，Controller 和异常处理用 standalone MockMvc，Mapper 和迁移测试连接真实 MySQL，最后还有完整 Spring 上下文的创建链路测试。现有 Surefire 报告合计 28 个测试全部通过。这样既能快速定位单层问题，也能证明从 HTTP 到真实数据库的组合链路。当前阶段只完成创建能力，还没有把查询、更新、登录、Redis 或 AI 功能包装成已完成成果。

## 十五、保守的简历表述

可选表述一：

> 基于 Spring Boot、MyBatis-Plus、MySQL 与 Flyway 完成工单创建链路，设计 DTO 校验、Service 事务边界、自增主键回填及统一异常响应，并通过分层单元测试和真实 MySQL 集成测试验证。

可选表述二：

> 为学习型工单平台实现 `POST /api/tickets` 模块，使用增量 Flyway 迁移统一历史状态枚举，建立 DTO、Service、Mapper、MockMvc 与端到端数据库测试；当前项目仍处于 P1 创建模块阶段。

简历中不应写“完整工单系统”“高并发平台”“AI Agent 平台已上线”等超出当前事实的描述。

## 十六、P1 完成检查清单

- [x] 基础包统一为 `com.xiaoyang.aiticketplatform`。
- [x] 创建请求只接收 title、description、creatorName、priority。
- [x] 请求 DTO 使用 Java 21 record。
- [x] title 使用 `@NotBlank` 和最大 120 校验。
- [x] description 使用 `@NotBlank` 和最大 2000 校验。
- [x] creatorName 使用 `@NotBlank` 和最大 64 校验。
- [x] priority 使用 `@NotNull`。
- [x] 优先级枚举包含 LOW、MEDIUM、HIGH、URGENT。
- [x] 状态枚举包含 OPEN、IN_PROGRESS、RESOLVED、CLOSED。
- [x] Ticket 使用普通 Java 类而非 record 或 Lombok。
- [x] Ticket 显式映射 `tickets` 表。
- [x] 主键使用 `IdType.AUTO` 对齐 MySQL 自增。
- [x] creator_name、created_at、updated_at 使用下划线转驼峰映射。
- [x] 普通枚举按名称与 VARCHAR 映射，无自定义 TypeHandler。
- [x] TicketMapper 使用 `@Mapper` 并继承 `BaseMapper<Ticket>`。
- [x] Service 接口不继承 MyBatis-Plus `IService`，不暴露 Entity。
- [x] Service 使用构造器注入和 `@Transactional`。
- [x] Service 显式设置新工单状态为 OPEN。
- [x] Service 检查 insert 影响行数和自增 ID 回填。
- [x] Controller 暴露 `POST /api/tickets` 并返回 HTTP 201。
- [x] 成功响应使用 `ApiResponse<TicketResponse>`。
- [x] Validation、请求体不可读和通用异常有统一处理。
- [x] V1 保持历史不变，通过 V2 对齐旧状态和默认 OPEN。
- [x] DTO 校验测试不启动 Spring、不连接数据库。
- [x] Service 单元测试使用 Mockito，不连接数据库。
- [x] Mapper 和迁移测试验证真实 MySQL 行为并使用事务回滚测试数据。
- [x] Controller 和异常处理使用 MockMvc 验证 HTTP 契约。
- [x] 全链路测试覆盖真实 Controller → Service → Mapper → MySQL。
- [x] 现有 Surefire 报告为 28 个测试，0 失败、0 错误、0 跳过。
- [ ] 工单查询、更新、状态流转等后续能力尚未实现，不计入 P1 创建模块成果。
- [ ] 登录鉴权、Redis、AI 和 Agent 能力尚未实现，不计入当前项目成果。

## 结语

P1 的主要价值不是文件数量，而是建立了一条职责清楚且能被多层测试证明的真实写入链路。复盘时应重点讲清三个边界：业务默认值与数据库默认值的区别、单元测试与真实数据库测试的区别、当前已完成创建能力与未来功能设想的区别。
