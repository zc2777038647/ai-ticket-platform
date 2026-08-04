# P3 工单条件分页查询复盘

## 文档边界与证据

本文只复盘 P3 已完成的条件分页查询，默认已经掌握 P1 的创建链路和 P2 的按 ID 查询；DTO、Entity、统一响应、业务异常等基础概念不再重复展开。

结论来自当前生产代码、测试代码、V1/V2 迁移以及 `target/surefire-reports`；本轮只编写文档，没有重新运行 Maven，也没有执行 EXPLAIN 或性能压测。

Surefire XML 当前汇总为 76/0/0/0；`BUILD SUCCESS` 来自此前实际全量测试。

## 一、P3 实现结果

最终接口：

```http
GET /api/tickets
```

支持六个可选查询参数：

```text
page, size, status, priority, creatorName, keyword
```

分页默认值与限制：

```text
page = 1
size = 20
page >= 1
1 <= size <= 100
```

筛选语义：status、priority 和 creatorName 精确匹配，keyword 匹配 title 或 description。

固定排序：

```sql
ORDER BY created_at DESC, id DESC
```

成功响应为 HTTP 200，使用 `ApiResponse<PageResponse<TicketResponse>>`。

当前真实测试结果：

```text
Tests run: 76
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

## 二、完整分页调用链

```text
HTTP 查询参数
→ @ModelAttribute TicketPageQuery
→ record 紧凑构造器应用默认值
→ Jakarta Validation
→ TicketController.pageTickets
→ TicketService.pageTickets
→ TicketServiceImpl.pageTickets
→ Page<Ticket>
→ LambdaQueryWrapper<Ticket>
→ TicketMapper.selectPage
→ MybatisPlusInterceptor
→ PaginationInnerInterceptor
→ MySQL COUNT 与分页查询
→ Page<Ticket>
→ List<TicketResponse>
→ PageResponse<TicketResponse>
→ ApiResponse.success
→ HTTP 200
```

Service 源码只调用一次 `ticketMapper.selectPage(page, wrapper)`。

一次 Mapper 方法调用不等于只执行一条 SQL；分页插件可能执行总数统计和分页数据查询。

Controller 不接触 Mapper，Service 不暴露 MyBatis-Plus 的分页类型给 Web 层。

## 三、分页插件

项目使用 MyBatis-Plus 3.5.17，并显式依赖：

```text
com.baomidou:mybatis-plus-jsqlparser:3.5.17
```

该模块为当前版本分页 SQL 改写提供解析能力；缺失时分页内部插件不能按预期工作。

`MybatisPlusConfig` 注册一个 `MybatisPlusInterceptor` Bean。

它是 MyBatis-Plus 内部插件链的入口，目前只包含一个分页内部插件。

配置代码创建：

```text
PaginationInnerInterceptor(DbType.MYSQL)
```

显式指定 MySQL，使插件按目标数据库方言生成分页 SQL，而不是依赖运行时猜测。

`overflow=false` 表示请求页超过总页数时，不自动跳回第一页。

`maxLimit=100` 是数据访问层兜底，限制单次分页允许返回的最大记录数。

它不是 QPS 限流、用户配额、权限控制或防刷机制。

DTO 同时使用 `@Max(100)`，让非法 size 在进入 Service 前得到清晰的 40000 响应。

插件兜底不能替代输入 Validation，因为两者所处层次和错误反馈不同。

项目没有引入 PageHelper，避免同时维护两套分页机制和潜在插件冲突。

Service 只传入 page 与 size，不手算 offset，也不拼接 LIMIT SQL。

## 四、TicketPageQuery

`TicketPageQuery` 是 Java 21 record，字段顺序为：

```text
Integer page
Integer size
TicketStatus status
TicketPriority priority
String creatorName
String keyword
```

Spring MVC 通过显式 `@ModelAttribute` 将查询字符串转换并绑定到 record 构造参数。

紧凑构造器只处理 null：page 默认为 1，size 默认为 20。

只处理 null 很重要：缺失参数需要默认值，显式传入 0 则应暴露为错误。

如果把 page=0 静默改为 1，客户端错误会被隐藏，接口契约也会变得模糊。

record 构造完成后，`@Valid` 触发 Bean Validation。

`@Min`、`@Max` 和 `@Size` 检查最终构造出来的对象。

status 和 priority 由 Spring 转换为枚举，输入必须对应实际枚举常量名称。

creatorName 与 keyword 是可选筛选，因此没有 `@NotBlank`。

null、空字符串和纯空格都允许到达 Service，再由统一的筛选语义判断是否使用。

trim 放在 Service，确保所有调用入口复用相同的业务查询规则。

DTO 不接收动态排序字段；当前排序是服务端固定契约，避免不必要的复杂度和注入面。

## 五、查询参数错误分类

Spring Framework 7.0.8 的实际行为由 `GlobalExceptionHandlerTest` 检查了 resolved exception。

`page=0` 的路径：

```text
字符串 "0" 成功转换为 Integer
→ TicketPageQuery 构造成功
→ @Min 校验失败
→ MethodArgumentNotValidException
→ FieldError.isBindingFailure() = false
→ HTTP 400 / code 40000
```

`page=abc` 的路径：

```text
字符串 "abc" 无法绑定为 Integer
→ MethodArgumentNotValidException
→ page 字段 bindingFailure = true
→ HTTP 400 / code 40002
```

`status=UNKNOWN` 的路径：

```text
UNKNOWN 无法绑定为 TicketStatus
→ MethodArgumentNotValidException
→ status 字段 bindingFailure = true
→ HTTP 400 / code 40002
```

这两个 ModelAttribute 转换错误在当前版本并不是 `MethodArgumentTypeMismatchException`。

GlobalExceptionHandler 先检查是否存在 binding failure；有则返回格式错误且 data 为 null。

没有 binding failure 时才收集字段 Validation 消息并返回 40000 和错误 Map。

因此不能仅按异常类名把全部 `MethodArgumentNotValidException` 映射成同一个应用码。

POST 请求体的非法枚举走 Jackson 反序列化：

```text
HttpMessageNotReadableException
→ HTTP 400 / code 40001 / 请求体格式错误
```

查询参数格式错误与 JSON 请求体错误属于不同输入通道，应用码应保持区分。

## 六、动态查询条件

Service 使用 `LambdaQueryWrapper<Ticket>`，通过方法引用表达数据库列。

status 非 null 时使用 `eq(Ticket::getStatus, query.status())`。

priority 非 null 时使用 `eq(Ticket::getPriority, query.priority())`。

creatorName 使用 `StringUtils.hasText` 判断，有文本时 trim 后精确匹配。

creatorName 表示明确创建人身份，本阶段不用 LIKE，避免扩大结果范围。

keyword 使用 `StringUtils.hasText` 判断，有文本时 trim 后匹配标题或描述。

`hasText` 不仅检查 null 和空串，还会把纯空白字符串识别为“无实际文本”。

所以 null、`""`、`"   "` 都不会生成 creatorName 或 keyword 条件。

关键词条件使用嵌套 wrapper，正确逻辑为：

```sql
status = ?
AND priority = ?
AND creator_name = ?
AND (
    title LIKE ?
    OR description LIKE ?
)
```

错误写法：

```sql
status = ? AND title LIKE ?
OR description LIKE ?
```

由于 AND 优先级高于 OR，错误写法可能让 description 命中的其他状态记录进入结果。

集成测试专门放入 RESOLVED 但 description 命中的记录，证明其被正确排除。

Wrapper 通过 MyBatis 参数绑定传值，不把用户输入直接拼接成 SQL 片段。

这降低了值参数的 SQL 注入风险；动态列名仍不能直接接受客户端字符串。

## 七、稳定排序与 OFFSET 分页

分页查询必须显式排序，否则数据库可以按未承诺的物理顺序返回记录。

只按 createdAt 排序仍不稳定，因为 `DATETIME(3)` 下多条记录可能具有相同时间。

增加 `id DESC` 后，相同 createdAt 的记录获得确定的第二排序键。

当前顺序是最新时间优先，同时间下较大 ID 优先。

测试显式设置时间，验证顺序为：较晚记录 → 同时间较大 ID → 同时间较小 ID。

OFFSET 分页的概念换算为：

```text
offset = (page - 1) × size
```

page=1、size=20 时 offset=0；page=2 时 offset=20。

实际 offset 和 LIMIT 由 MyBatis-Plus 分页插件处理，Service 不重复实现。

事务回滚仍可能消耗 MySQL 自增 ID，所以测试只比较大小和唯一性，不断言 ID 连续。

双字段排序能稳定同一数据快照内的顺序，但不能消除持续写入或删除带来的 OFFSET 漂移。

翻页期间新增记录可能导致重复，删除记录可能导致遗漏；大 offset 还可能增加扫描成本。

游标分页会携带上一页末尾的排序键，例如 `(createdAt, id)`，继续向后查询。

游标分页只是后续可选演进，当前项目没有实现。

## 八、PageResponse

`PageResponse<T>` 是应用自己的响应 record，不直接返回 MyBatis-Plus `Page` 或 `IPage`。

这样 Web 契约不受持久化框架字段和版本变化影响。

五个字段分别是：

```text
records：当前页数据
total：符合条件的总记录数
pages：总页数
current：当前页码
size：每页数量
```

`records` 必须非 null，并通过 `List.copyOf` 建立不可修改副本。

防御性复制避免调用方修改原 List 后悄悄改变已经构造好的响应。

它也阻止调用方通过 `response.records().add(...)` 修改分页结果。

空分页合法表示为 records 空、total=0、pages=0，同时 current 和 size 仍大于等于 1。

PageResponse 只描述分页数据，不包含 HTTP 状态或应用错误码；外层 ApiResponse 负责统一协议。

当前没有加入 hasNext、排序描述等字段，因为现有客户端契约暂不需要。

## 九、七类测试的职责

| 测试 | Spring | Mock | MySQL | 主要证明 |
|---|---:|---|---:|---|
| 分页插件集成测试 | 是 | 无 | 是 | selectPage、total、pages、两页结果和插件生效 |
| TicketPageQuery Validation | 否 | 无 | 否 | 默认值、上下限、长度、空白允许 |
| PageResponse 单元测试 | 否 | 无 | 否 | 元数据保护、空页、防御性复制 |
| Service 分页单元测试 | 否 | Mock Mapper | 否 | Page 参数、DTO 映射、单次 Mapper 协作 |
| Service 条件集成测试 | 是 | 无 | 是 | 条件组合、trim、排序、分页、回滚 |
| Controller standalone MVC | 最小 MVC | Mock Service | 否 | 参数绑定、默认值、错误码、Service 阻断 |
| HTTP 全链路分页测试 | 是 | 无 | 是 | DispatcherServlet 到真实 MySQL 的完整链路 |

- 单元测试不断言完整内部 SQL，因为框架参数名和生成细节不稳定。
- 真实数据库测试用 UUID 隔离，准备 3 条匹配与 4 条排除记录。
- 四条排除记录分别破坏 status、priority、creatorName、keyword，以 ID 集合证明 AND 生效。
- RESOLVED 且 description 命中的记录用于证明 OR 被正确分组。
- 相同较早时间、不同 ID 的记录用于验证第二排序键。
- 两页 ID 不重复，用于检查分页边界。
- GET 前后唯一数据数量保持 7，证明查询没有插入或删除记录。
- `@Transactional` 回滚数据，`@AfterTransaction` 确认 UUID 残留为 0。
- Mock 不能替代真实 SQL，真实 SQL 也不能替代 HTTP 绑定测试。

## 十、常见错误与边界

1. 没注册分页插件却调用 selectPage，分页元数据和 SQL 行为可能不符合预期。
2. 使用新版分页插件却忘记 `mybatis-plus-jsqlparser`，运行时缺少必要解析能力。
3. Service 直接返回 IPage，把持久化框架泄漏到接口契约。
4. 先 selectList 查询全部记录，再由 Java 截取，浪费数据库和网络资源。
5. 把 page=0 静默改为默认页，掩盖客户端错误。
6. keyword 的 OR 没有括号，使其他筛选条件被绕过。
7. creatorName 错用模糊查询，返回名称相近但不相同的创建人记录。
8. 纯空格直接进入 SQL，生成 `creator_name = ''` 或无意义 LIKE。
9. 动态拼接客户端排序字段，造成注入和不可控查询风险。
10. 只按 createdAt 排序，时间相同时分页顺序不稳定。
11. 把 maxLimit 当成限流；它只限制单页记录数。
12. 把查询枚举绑定失败误归为请求体错误 40001。
13. 全量测试假设开发库为空，导致 total 断言受已有数据影响。
14. 假设回滚后的自增 ID 可复用或 ID 必然连续。
15. GET 查询意外修改数据库，却只验证 HTTP 响应。

## 十一、三级面试追问

### 第一层：基础

1. **什么是分页？** 把完整结果按页码和每页数量分段返回，降低单次传输和处理量。
2. **MyBatis-Plus Page 是什么？** 它承载分页请求参数、记录和 total/pages 等结果。
3. **LambdaQueryWrapper 是什么？** 使用实体方法引用构造类型相对安全的动态查询条件。
4. **`@ModelAttribute` 有什么作用？** 将 URL 查询参数绑定到 Java 对象，本项目绑定到 record。
5. **`@Min` 和 `@Max` 如何生效？** `@Valid` 在对象绑定完成后触发 Bean Validation。
6. **total 与 pages 分别是什么？** total 是总记录数，pages 是按 size 计算出的总页数。

### 第二层：实现

1. **完整调用链是什么？** MVC 绑定 DTO，经 Controller、Service、selectPage、插件和 MySQL，再转为 PageResponse。
2. **为什么 PageResponse 不依赖 IPage？** 避免框架类型成为外部 API 契约。
3. **为什么关键词 OR 必须分组？** 保证 OR 只连接 title/description，不绕过外层 AND 条件。
4. **为什么使用 createdAt、id 双排序？** createdAt 可能相同，id 提供确定的第二排序键。
5. **为什么默认值只处理 null？** null 表示缺失；0 是显式非法输入，应返回校验错误。
6. **page=abc 与 page=0 有何区别？** 前者绑定失败返回 40002，后者转换成功但 Validation 失败返回 40000。

### 第三层：工程边界

1. **OFFSET 分页持续写入时有什么问题？** 页边界可能移动，造成重复或遗漏。
2. **大页码为什么可能变慢？** 数据库可能需要扫描并跳过大量前置行。
3. **如何改成游标分页？** 用上一页末尾的 `(createdAt,id)` 作为下一页查询边界。
4. **当前筛选与排序如何设计索引？** 应基于高频组合、选择性和 EXPLAIN 评估联合索引，不能凭字段堆叠。
5. **`LIKE '%keyword%'` 为什么难用普通索引？** 前导通配符无法利用普通 B+ 树的前缀有序性。
6. **动态排序如何防 SQL 注入？** 只允许服务端白名单映射，不能直接拼接客户端列名。
7. **COUNT 很慢如何优化？** 可评估覆盖索引、近似计数、延迟统计或改用不要求 total 的游标方案。
8. **多租户分页还需什么条件？** 必须加入来自可信上下文的 tenantId 隔离条件。

## 十二、MySQL 索引初步分析

V1 实际创建两个二级索引，V2 只调整枚举值与列定义，没有修改索引：

```text
idx_tickets_status_created_at(status, created_at)
idx_tickets_creator_name(creator_name)
```

- status 与 createdAt 的联合索引可能帮助过滤和部分排序。
- 实际排序还有 id，查询也可能加入 priority、creatorName、keyword。
- 索引选择、回表和额外排序会随数据分布与选择性变化。
- creatorName 精确匹配可能受益于单列索引，组合条件仍需看计划。
- title/description 的 `%keyword%` 通常难利用普通前缀 B+ 树索引。
- 当前未执行 EXPLAIN，不能声称索引命中、扫描行数或耗时。
- 后续应以接近真实规模的数据执行 EXPLAIN/EXPLAIN ANALYZE。
- 全文检索或搜索引擎只是潜在方向，当前没有实现。

## 十三、1～2 分钟口头讲解

项目 P3 实现了 `GET /api/tickets` 条件分页查询。首先引入 MyBatis-Plus 的 jsqlparser 模块，并注册分页拦截器，指定 MySQL 方言、overflow=false 和单页最大 100 条。查询参数使用 Java record `TicketPageQuery` 接收，缺失 page 和 size 时由紧凑构造器设置为 1 和 20，再通过 Validation 校验范围。
Spring MVC 用 ModelAttribute 绑定查询参数。实际测试发现，page=0 是普通 Validation 失败，而 page=abc 和非法枚举都是 MethodArgumentNotValidException 中的 binding failure，所以异常处理器进一步检查字段错误，分别返回 40000 和 40002；POST 请求体非法枚举仍返回 40001。
Service 使用 LambdaQueryWrapper 动态添加 status、priority 和 creatorName 精确条件。creatorName 与 keyword 先用 hasText 判断并 trim；关键词通过嵌套条件形成 `AND (title LIKE ? OR description LIKE ?)`，不会绕过外层筛选。排序固定为 createdAt DESC、id DESC，保证同时间记录顺序稳定。Service 调用一次 selectPage，由分页插件处理 COUNT 和分页 SQL，再把 Ticket 转成 TicketResponse，最终包装成与 IPage 解耦的 PageResponse。
测试包含 DTO、响应对象、Service 和 MVC 单元测试，也包含分页插件、Service 组合条件以及 HTTP 到真实 MySQL 的全链路测试。真实测试用 UUID 隔离 3 条匹配和 4 条排除数据，验证 AND/OR、双字段排序、两页 ID 不重复、GET 不改数据，并在事务结束后确认无残留。

## 十四、独立重写练习

### 练习一：TicketPageQuery

- 任务：独立写出六字段 record、默认值和 Validation，不查看现有实现。
- 验收点：只对 null 设置 1/20；范围与长度约束准确；可选字段不做多余处理。
- 常见错误：把 0 改成默认值；给可选筛选加 NotBlank；在 DTO 中 trim 或加入排序字段。

### 练习二：Service Wrapper

- 任务：根据六个字段构造条件、固定排序并调用一次 selectPage。
- 验收点：hasText、trim、精确匹配、关键词嵌套 OR、双字段 DESC、DTO 转换。
- 常见错误：OR 无括号；selectList 后截取；手算 offset；返回 Ticket 或 IPage。

### 练习三：Controller 分页方法

- 任务：独立写出 GET 方法和返回类型。
- 验收点：显式 `@Valid @ModelAttribute`；调用一次 Service；返回统一成功响应。
- 常见错误：Controller 构造 Wrapper；手写默认值和 if 校验；捕获绑定异常。

### 练习四：组合条件测试数据

- 任务：设计 3 条匹配与 4 条排除数据，并写出期望的两页 ID 顺序。
- 验收点：title/description 分别命中；四类排除样本；同时间不同 ID。
- 常见错误：数据不唯一；靠执行速度制造时间差；只看 total；未检查回滚。

## 十五、掌握检查表

- [ ] 能完整说出查询参数到 MySQL 再到响应的分页调用链。
- [ ] 能解释为什么分页插件需要 jsqlparser。
- [ ] 能说明 MybatisPlusInterceptor 与 PaginationInnerInterceptor 的关系。
- [ ] 能解释默认值、record 构造和 Validation 的顺序。
- [ ] 能解释 bindingFailure 以及 40000、40001、40002 的边界。
- [ ] 能独立写出正确分组的 AND/OR LambdaQueryWrapper。
- [ ] 能解释 hasText、trim 和 creatorName 精确匹配。
- [ ] 能解释 createdAt DESC、id DESC 的稳定排序意义。
- [ ] 能解释 PageResponse 为什么与 IPage 解耦。
- [ ] 能区分七类分页测试各自证明什么。
- [ ] 能独立重写分页 Controller。
- [ ] 能独立重写 Service 分页条件和结果转换。
- [ ] 能说明 OFFSET 分页在持续写入和大页码下的局限。
- [ ] 能基于现有两个索引做保守分析，不虚构 EXPLAIN 结果。

## 结语

P3 的重点不只是“返回一页数据”，而是把输入绑定、错误分类、动态条件、稳定排序、框架解耦和真实数据库验证连接成一条可解释、可复现的查询链路。
