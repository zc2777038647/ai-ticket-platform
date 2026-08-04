# P2 按 ID 查询工单复盘

## 文档边界

本文只复盘 P2 已完成的按 ID 查询工单能力，默认读者已经阅读 P1 创建模块复盘。

DTO、Entity、MyBatis-Plus 基础映射、Flyway 和创建链路不再重复展开。

文中的测试数字来自当前 `target/surefire-reports`，本轮只整理文档，没有重新运行 Maven。

## 一、P2 实现结果

P2 新增接口：

```text
GET /api/tickets/{id}
```

接口存在四种已实现结果：

| 请求情况 | HTTP 状态 | 应用码 | 消息 |
|---|---:|---:|---|
| 查询成功 | 200 | 0 | success |
| 工单不存在 | 404 | 40400 | 工单不存在 |
| ID 不为正数 | 400 | 40000 | 请求参数校验失败 |
| ID 无法转换为 Long | 400 | 40002 | 请求参数格式错误 |

成功响应沿用 P1 的 `ApiResponse<TicketResponse>`，数据包含：

```text
id, title, description, creatorName, priority, status
```

当前测试报告记录：

```text
Tests run: 44
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

其中 BUILD SUCCESS 来自此前实际 Maven 全量测试结果；本轮仅重新读取现有 Surefire XML 并核对 44/0/0/0。

## 二、相对于 P1 新增的调用链

查询成功的主要链路为：

```text
GET /api/tickets/{id}
→ @PathVariable 将路径文本转换为 Long
→ @Positive 校验 ID 为正数
→ TicketController.getTicketById
→ TicketService.getTicketById
→ TicketServiceImpl.getTicketById
→ TicketMapper.selectById
→ MyBatis-Plus / MySQL
→ Ticket
→ TicketResponse
→ ApiResponse.success
→ HTTP 200
```

查询成功时，Service 只执行一次 `selectById(id)`，随后显式复制六个字段到 `TicketResponse`。

查询不存在的分支为：

```text
TicketMapper.selectById(id) 返回 null
→ TicketServiceImpl 抛出 BusinessException(TICKET_NOT_FOUND)
→ GlobalExceptionHandler 捕获
→ ApiResponse.failure(TICKET_NOT_FOUND)
→ HTTP 404 / code 40400
```

Controller 不直接调用 Mapper，也不捕获 `BusinessException`。

## 三、路径参数转换与校验顺序

### 3.1 `/100`

Spring MVC 从 URL 中取得字符串 `"100"`，通过类型转换机制得到 `Long 100L`。

随后 Spring Framework 7 的 MVC 方法校验检查 `@Positive`，100 大于 0，因此进入 Controller 并调用 Service。

### 3.2 `/abc`

`"abc"` 无法转换为 `Long`，因此在进入 Controller 方法和 Service 之前失败。

当前实际异常类型是：

```text
MethodArgumentTypeMismatchException
```

全局异常处理器将其映射为 HTTP 400 / code 40002。

它是路径参数格式错误，不是 JSON 请求体错误，因此不能使用 40001。

### 3.3 `/0`

`"0"` 可以成功转换为 `Long 0L`，但不满足 `@Positive`。

Spring Framework 7.0.8 的原生 MVC 方法校验实际抛出：

```text
HandlerMethodValidationException
```

全局异常处理器将其映射为 HTTP 400 / code 40000。

### 3.4 为什么都是 HTTP 400，却使用不同应用码

HTTP 400 只说明客户端请求不合法。

应用码进一步区分原因：

- 40000：值能够进入校验流程，但违反约束。
- 40002：值连目标 Java 类型都无法转换。

两条失败路径都发生在 Service 调用前，相关 MVC 测试也验证了 `TicketService` 完全没有交互。

当前 Controller 没有添加类级 `@Validated`。Spring 7 原生 MVC 方法校验可以识别参数上的直接约束；无需机械复制旧教程配置。

## 四、业务异常

### 4.1 BusinessException 的职责

`BusinessException` 继承 `RuntimeException`，持有不可变的 `ErrorCode`。

它表达的是“业务操作为什么失败”，而不是“HTTP 应该怎样返回”。

使用 RuntimeException 后，异常可以自然向上传播；在事务方法中，未捕获的运行时异常默认也具备触发回滚的语义。

### 4.2 为什么持有 ErrorCode

`ErrorCode` 同时提供稳定应用码和公开消息。

异常处理器可以使用：

```text
exception.getErrorCode()
```

创建统一失败响应，而不用根据任意异常文本猜测错误类型。

构造器还通过 `Objects.requireNonNull` 防止产生没有错误码的业务异常。

### 4.3 为什么 Service 不返回 HTTP 404

Service 属于业务层，不应依赖 `ResponseEntity` 或 `HttpStatus`。

它只抛出：

```text
BusinessException(ErrorCode.TICKET_NOT_FOUND)
```

`GlobalExceptionHandler` 再将它转换为 HTTP 404 / code 40400。

这样 Service 仍可被其他入口复用，HTTP 协议决策集中在 Web 层。

### 4.4 为什么查询不到不返回 null

Service 返回 null 会把“资源不存在”变成隐含约定，调用方容易漏判并产生新的空指针异常。

明确业务异常能够稳定表达失败原因，也能统一生成 404 响应。

### 4.5 日志级别

工单不存在是可预期业务结果，当前处理器只用 warn 记录错误码，不用 error 打印完整堆栈。

未预期系统异常仍由通用 `Exception` handler 使用 error 记录完整异常。

### 4.6 当前局限

当前只有一种 `BusinessException`，所以处理器把全部业务异常明确映射为 HTTP 404。

如果以后增加“状态冲突”“无权限”“重复操作”等错误，全部返回 404 就会失真。

后续可以建立明确的 ErrorCode 到 HTTP 状态映射策略，或按业务异常类别划分处理逻辑；本阶段没有实现这些扩展。

## 五、只读事务

查询实现使用：

```java
@Transactional(readOnly = true)
```

它表达该业务用例只读取数据，并把事务边界放在 Service 层。

事务管理器、连接池或数据库驱动可能据此采用只读相关优化，例如减少不必要的状态维护。

但 `readOnly = true` 通常是语义和优化提示，不是数据库安全机制，不能断言执行写 SQL 一定报错。

真正的写保护仍可能需要数据库权限、只读连接或其他基础设施约束。

`TicketServiceImplTest` 通过 Mockito 直接调用实现对象，没有 Spring 代理，因此不能证明 `@Transactional` 实际生效。

单元测试负责验证：查询一次、映射正确、未找到时抛出正确异常。

真实 `@SpringBootTest` 负责验证：Spring Bean 代理、真实 Controller、Mapper 和 MySQL 能组合运行。

在查询集成测试中，Service 的只读事务加入外层测试事务；这同样不意味着连接绝对禁止测试准备阶段的 INSERT。

## 六、查询接口测试体系

| 测试层 | Mock | 启动 Spring | MySQL | 主要验证 |
|---|---|---:|---:|---|
| Service 单元测试 | Mock TicketMapper | 否 | 否 | 存在/不存在分支、DTO 映射、Mapper 交互 |
| Controller standalone MVC | Mock TicketService | 最小 MVC，不是完整 Boot | 否 | 路由、参数校验、HTTP 和统一响应 |
| GlobalExceptionHandler 测试 | 无业务层 Mock | 最小 MVC | 否 | 具体 MVC 异常到应用响应的映射 |
| GET 全链路测试 | 不 Mock业务组件 | 是 | 是 | HTTP 到真实 MySQL 的完整查询链路 |

### 6.1 Service 单元测试

存在场景让 `selectById(100L)` 返回 Ticket，验证六个响应字段，确认只查询一次且不调用 insert。

不存在场景让 `selectById(999L)` 返回 null，精确断言 `BusinessException`、`TICKET_NOT_FOUND` 和“工单不存在”。

### 6.2 Controller MVC 测试

使用真实 `TicketController`、真实 Validator、真实 `GlobalExceptionHandler` 和 Mock Service。

四个 GET 场景分别验证 200、404、非正数 400 和类型转换 400。

非法路径测试还确认 Service 完全没有被调用。

### 6.3 异常处理测试

嵌套测试 Controller 提供带 `@Positive` 的路径参数端点。

实际请求证明 `/0` 命中 `HandlerMethodValidationException` 处理器，`/abc` 命中 `MethodArgumentTypeMismatchException` 处理器，且响应不泄露框架异常类名。

### 6.4 真实 MySQL 全链路测试

存在场景必须先插入真实工单，否则只能证明 Mock 数据返回，不能证明 `selectById`、字段映射和数据库连接有效。

测试确认 insert 影响一行、自增 ID 回填，然后使用实际 ID 发起 GET，并核对响应与真实记录。

不存在场景使用 `Long.MAX_VALUE`，但仍先调用 `selectById` 明确确认不存在，没有假设某个自增 ID 必然空缺。

所有 GET 前后都检查记录数量：准备数据会增加一条，但 GET 本身不应继续增加、删除或修改记录数量。

测试类使用 `@Transactional` 回滚准备数据，并通过唯一 creatorName 在 `@AfterTransaction` 中确认回滚后记录数为 0。

## 七、常见错误

1. Controller 直接调用 Mapper，导致 HTTP 层承担业务和持久化职责。
2. Service 返回 Ticket Entity，使数据库模型泄露为接口契约。
3. 查询不到时返回 null，把明确业务结果变成隐含约定。
4. 把 `/abc` 当成请求体格式错误 40001，而不是路径参数格式错误 40002。
5. 手写 `if (id <= 0)` 代替 `@Positive`，让输入约束分散在流程代码中。
6. 对所有业务异常返回 HTTP 500，把预期业务结果误报为系统故障。
7. 查询接口意外执行 insert、update 或 delete，却只验证响应内容。
8. Service Mock 单元测试通过后，没有真实 MySQL 查询测试。
9. 假设“最大 ID + 1”或某个普通自增 ID 一定不存在，忽略并发和数据变化。
10. 把 `readOnly = true` 理解为数据库绝对禁止写入。
11. 在 Controller 捕获 `BusinessException`，造成重复且分散的响应拼装。
12. 给 Controller 添加不必要的 `@Validated`，改变 Spring 7 原生 MVC 方法校验路径。

## 八、三级面试问题

### 第一层：基础概念

1. **问：`@PathVariable` 有什么作用？答：** 它把 URL 模板中的 `{id}` 绑定到 Controller 方法参数，并配合转换服务转成声明的 `Long`。
2. **问：`@Positive` 有什么作用？答：** 它要求数值严格大于 0；项目用它阻止 0 和负数 ID 进入 Service。
3. **问：HTTP 404 表示什么？答：** 表示请求指向的资源不存在；本项目查不到工单时返回 HTTP 404。
4. **问：`selectById` 来自哪里？答：** `TicketMapper` 继承 MyBatis-Plus 的 `BaseMapper<Ticket>`，无需自定义 SQL 就具备该方法。
5. **问：什么是业务异常？答：** 它表达业务操作无法完成的预期原因，例如工单不存在；不同于数据库断连等系统异常。

### 第二层：实现原理

1. **问：`/abc` 和 `/0` 为什么走不同异常？答：** `abc` 无法转成 Long；0 能转换，但随后违反 `@Positive`。
2. **问：为什么 Service 返回 DTO？答：** DTO 稳定接口边界，避免 Entity 的数据库字段和未来变化直接暴露给 Web 层。
3. **问：为什么查询不到抛异常而不是返回 null？答：** 异常能明确表达原因并统一转换成 404；null 容易被漏判。
4. **问：HTTP 404 与应用码 40400 有什么区别？答：** 404 是通用传输层语义；40400 是应用内部“工单不存在”的错误类型。
5. **问：查询方法为什么使用只读事务？答：** 它表达只读事务边界并可能提供优化提示，但不是绝对写保护。

### 第三层：工程边界

1. **问：数据库中存在非法枚举字符串会怎样？答：** 按名称映射无法匹配 Java 常量时查询会失败，需要通过数据治理和迁移保证一致。
2. **问：查询后还要写访问日志，readOnly 事务怎样调整？答：** 先确认日志是否需同事务；若需写库，应重新设计事务边界。
3. **问：全部 BusinessException 映射为 404 有什么问题？答：** 冲突、权限等失败需要不同 HTTP 语义，全部 404 会误导客户端和监控。
4. **问：如何避免通过 ID 查询到其他用户的工单？答：** 建立认证授权，并在查询条件中加入可信的资源归属校验；当前尚未实现。
5. **问：多租户系统按 ID 查询还需要什么条件？答：** 通常同时按 `id` 和可信 `tenantId` 查询，不能只依赖客户端传入租户 ID。

## 九、1～2 分钟口头练习

在项目 P2 阶段，我实现了 `GET /api/tickets/{id}`。路径中的 ID 先由 Spring MVC 转换为 Long，再通过 Jakarta Validation 的 `@Positive` 校验必须大于 0。像 `/abc` 会在类型转换阶段失败，返回 HTTP 400 和应用码 40002；`/0` 能完成转换，但会触发 Spring 7 的 `HandlerMethodValidationException`，返回 HTTP 400 和应用码 40000。这两种情况都不会进入 Service。

校验通过后，Controller 调用 `TicketService.getTicketById`。Service 方法使用 `@Transactional(readOnly = true)` 表达只读事务，通过 MyBatis-Plus `BaseMapper` 提供的 `selectById` 查询。查询成功后显式把 Ticket 转成 TicketResponse；查不到时抛出带 `TICKET_NOT_FOUND` 的 BusinessException。Service 不依赖 HTTP，最终由全局异常处理器把业务异常转换为 HTTP 404、应用码 40400 和统一响应结构。

测试分为多层：Service 使用 Mockito 验证存在和不存在分支；Controller 与异常处理器使用 standalone MockMvc 验证路由、参数校验和响应；最后通过 Spring Boot、真实 Mapper 和真实 MySQL 完成四个 GET 场景的全链路测试。集成测试检查 GET 前后数据库记录数不变，准备数据通过事务回滚，并在事务结束后确认没有永久保留。

## 十、独立重写练习

### 练习一：GET Controller

任务：独立写出按 ID 查询的 Controller 方法，不查看现有文件。

验收点：路径准确；使用 `@PathVariable` 和 `@Positive`；调用一次 Service；返回 HTTP 200 和统一成功响应。

常见错误：手写正数判断；直接调用 Mapper；捕获业务异常；返回 Entity；添加不必要的 `@Validated`。

### 练习二：Service 查询方法

任务：独立写出存在和不存在两个分支，并显式转换响应 DTO。

验收点：只调用一次 `selectById`；使用只读事务；不存在时抛出正确 BusinessException；映射六个字段；不返回 null。

常见错误：返回 Entity；二次查询；吞掉数据库异常；把 HTTP 404 写进 Service；修改查询到的实体。

### 练习三：不存在的 Mock 测试

任务：使用 Mockito 验证 Mapper 返回 null 时的 Service 行为。

验收点：精确断言 `BusinessException`；验证 `TICKET_NOT_FOUND` 和消息；查询一次；不调用 insert；没有其他 Mapper 交互。

常见错误：只断言 RuntimeException；不检查 ErrorCode；返回一个空 Ticket 代替 null；遗漏交互次数验证；启动完整 Spring。

## 十一、掌握检查表

- [ ] 能完整说出 GET 从路径到 MySQL 再到响应的调用链。
- [ ] 能解释 `/abc` 和 `/0` 的处理顺序与异常差异。
- [ ] 能解释 `MethodArgumentTypeMismatchException` 的触发条件。
- [ ] 能解释 `HandlerMethodValidationException` 的触发条件。
- [ ] 能解释为什么非法路径参数不会调用 Service。
- [ ] 能解释 BusinessException 的字段和职责。
- [ ] 能说明为什么 Service 不直接依赖 HTTP 404。
- [ ] 能解释 HTTP 404 与应用码 40400 的区别。
- [ ] 能说明查询不到为什么不返回 null。
- [ ] 能解释 `@Transactional(readOnly = true)` 的作用。
- [ ] 能说明 readOnly 不能保证绝对禁止写入。
- [ ] 能区分 Service、Controller、异常处理和全链路四类测试职责。
- [ ] 能说明为什么真实查询测试要先准备一条数据。
- [ ] 能说明为什么 GET 前后数据库记录数应保持不变。
- [ ] 能说明测试数据为什么必须回滚。
- [ ] 能独立重写 GET Controller。
- [ ] 能独立重写 Service 查询方法。
- [ ] 能独立编写查询不存在的 Mockito 测试。
- [ ] 能指出“全部 BusinessException 返回 404”的当前局限。
- [ ] 能明确当前尚未实现分页、更新、删除、鉴权和多租户查询。

## 结语

P2 的核心不是增加一个 `selectById`，而是把路径输入边界、业务不存在语义、只读事务和真实查询测试连接成一条可解释、可验证的读取链路。
