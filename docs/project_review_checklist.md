# 项目复习与掌握检查表

使用方法：只有在不照着文档也能口述、定位或动手时再勾选。遇到不会的项，先回到相关类和测试，而不是只背参考答案。

## 1. 第一轮：能说明功能

### 注册与登录

- [ ] 我能说明注册请求的用户名、密码和显示名规则。
- [ ] 我能说明注册为什么固定创建 USER。
- [ ] 我能说明密码如何保存、登录如何比较。
- [ ] 我能说出登录成功响应的 Token 类型和有效期。
- [ ] 我能说明 `/api/auth/me` 的数据来自哪里。

### 工单创建与查询

- [ ] 我能说明创建工单的请求和响应字段。
- [ ] 我能区分 `creatorName` 与 `creatorUserId`。
- [ ] 我能说明全局分页和 `/mine` 的角色及数据范围差异。
- [ ] 我能说明 USER、AGENT、ADMIN 查看详情的差异。
- [ ] 我能解释为什么 USER 查看他人工单返回404。

### 状态、指派和日志

- [ ] 我能完整说出四种状态和三条允许迁移边。
- [ ] 我能说明非法状态、状态冲突使用不同错误码的原因。
- [ ] 我能说明只有 ADMIN 可以指派，目标必须为 AGENT。
- [ ] 我能区分首次指派、重新指派和重复指派。
- [ ] 我能说明目前不支持取消指派或主动领取。
- [ ] 我能说出两种日志类型及其前后值。
- [ ] 我能区分指派操作者与目标处理人。
- [ ] 我能说明当前没有日志查询 API。

## 2. 第二轮：能说明设计原因

- [ ] 我能解释 BCrypt 为什么使用 `matches` 而不是重新 encode 比较。
- [ ] 我能解释 strength 10 是成本参数而不是密码长度。
- [ ] 我能解释 JWT 是签名而不是加密。
- [ ] 我能说明为什么 JWT `sub` 使用用户 ID。
- [ ] 我能说出 issuer、sub、iat、exp、jti、username、role。
- [ ] 我能区分 Decoder、Validator 和 Converter。
- [ ] 我能区分 AuthenticationEntryPoint 与 AccessDeniedHandler。
- [ ] 我能解释请求级授权与对象级授权的区别。
- [ ] 我能解释为什么 Service 不直接读取 SecurityContext。
- [ ] 我能解释为什么请求中的 creatorName 不能建立所有权。
- [ ] 我能说明 V4 的 creator_user_id 为什么允许 NULL。
- [ ] 我能说明 assignee_user_id 为什么允许 NULL。
- [ ] 我能写出状态旧值条件 UPDATE。
- [ ] 我能写出首次指派和重新指派的不同条件。
- [ ] 我能说明 affectedRows 为0、1和异常值分别意味着什么。
- [ ] 我能区分条件更新和事务原子性。
- [ ] 我能解释为什么先业务 UPDATE 再日志 INSERT。
- [ ] 我能解释为什么不能异步或用 REQUIRES_NEW 写强一致日志。
- [ ] 我能说明日志为何只有 created_at，没有 updated_at。
- [ ] 我能承认日志不是数据库物理不可篡改。

## 3. 第三轮：能定位代码

在不搜索本文档的情况下，完成下列定位：

- [ ] 找到 `AuthController` 的注册、登录和 `/me`。
- [ ] 找到 `TicketController` 的6个工单端点。
- [ ] 找到 `AuthServiceImpl` 的用户名规范化、BCrypt 和登录错误分支。
- [ ] 找到 `TicketServiceImpl` 的所有权查询。
- [ ] 找到 `TicketStatus.canTransitionTo`。
- [ ] 找到状态 `LambdaUpdateWrapper`。
- [ ] 找到指派 `IS NULL` 与旧处理人等值条件。
- [ ] 找到 `appendOperationLog` 及行数、ID 检查。
- [ ] 找到 `SecurityConfig` 的公开、认证和角色规则。
- [ ] 找到 JWT Encoder、Decoder、Validator、Converter。
- [ ] 找到 `ErrorCode` 和 `GlobalExceptionHandler` 的 HTTP 映射。
- [ ] 找到 V1～V6 每个迁移的职责。
- [ ] 找到 Service Mockito 测试。
- [ ] 找到 standalone MockMvc 测试。
- [ ] 找到真实 JWT Security 测试。
- [ ] 找到事务原子性外键故障测试。

### 关键相对路径

```text
src/main/java/com/xiaoyang/aiticketplatform/controller
src/main/java/com/xiaoyang/aiticketplatform/service/impl
src/main/java/com/xiaoyang/aiticketplatform/config/SecurityConfig.java
src/main/java/com/xiaoyang/aiticketplatform/security
src/main/java/com/xiaoyang/aiticketplatform/common/ErrorCode.java
src/main/resources/db/migration
src/test/java/com/xiaoyang/aiticketplatform
```

## 4. 第四轮：能现场修改

这些练习只给影响层次和验收点，不提供完整代码。练习前创建临时分支，完成后运行目标测试和全量测试。

### 练习1：新增一个 TicketPriority 值

涉及层次：枚举、数据库列注释的新 Flyway 迁移、Validation/JSON 契约、相关测试和文档。

验收点：普通枚举仍按名称写入 VARCHAR；不修改已执行迁移；非法旧值行为明确；全量测试通过。

### 练习2：新增分页筛选条件

涉及层次：`TicketPageQuery`、Service wrapper、Controller绑定测试、Service 单元与 MySQL 分页集成测试、API 文档。

验收点：空参数不加条件；`/mine` 的所有权条件不能被新 OR 条件绕过；排序保持稳定。

### 练习3：修改 JWT TTL

涉及层次：外部配置、`JwtProperties`、`JwtTokenServiceImplTest`、配置测试和文档。

验收点：TTL 必须为正；`expiresIn` 与 exp-iat 一致；不硬编码真实密钥；旧 Token 行为被准确说明。

### 练习4：增加只允许 ADMIN 的只读接口

涉及层次：Controller、Service、DTO、SecurityConfig、401/403测试、对象级规则判断和 API 文档。

验收点：先明确资源与数据范围；Security matcher 不依赖兜底 permitAll；USER/AGENT 均有真实安全拒绝测试。

### 练习5：为日志增加查询 Service，暂不实现 Controller

涉及层次：查询请求/响应 DTO、Service 接口与实现、Mapper现有分页能力、Service 单元和 MySQL 集成测试。

验收点：只读事务；按 ticket/order 稳定分页；不暴露 Entity；不增加修改或删除日志能力；无生产 HTTP 端点。

### 练习6：将重复指派改成幂等成功

涉及层次：Service 业务语义、返回 DTO、日志策略、40904是否保留、单元/HTTP/安全测试和 API 文档。

验收点：明确重复请求是否新增日志；并发冲突仍可区分；不能只删除异常分支而不更新契约和测试。

## 5. 第五轮：能排查问题

### 场景1：POST `/api/tickets` 返回403

排查方向：Token 是否有效、Security matcher 是否命中、Authority 是否带 `ROLE_`、是否误用不允许的角色。相关类：`SecurityConfig`、`ApplicationJwtAuthenticationConverter`、安全集成测试。

### 场景2：JWT 始终验证失败

排查方向：Base64 密钥是否合法且解码后至少32字节、issuer是否一致、算法是否HS256、Token时间和必要 claims 是否有效。相关类：`JwtConfig`、`JwtProperties`、`ApplicationJwtValidator`。

### 场景3：USER 能看到其他人的详情

排查方向：Service 是否按角色分支，USER 查询是否同时包含 ID 与 `creator_user_id`，Controller 是否正确传入 JWT sub 和 role。相关类：`TicketController`、`TicketServiceImpl`、`TicketOwnershipIntegrationTest`。

### 场景4：keyword 搜索泄露其他用户工单

排查方向：所有权条件与 keyword 的 AND/OR 分组是否正确，OR 是否逃逸到 creator 条件外。相关类：`TicketServiceImpl.queryTickets`、分页 Service/MySQL 与所有权测试。

### 场景5：状态更新 affectedRows=0

排查方向：确认工单查询时状态、目标状态机、UPDATE 前数据库状态是否被其他请求改变；查看条件 wrapper 是否包含旧状态。相关类：`TicketStatus`、`TicketServiceImpl.updateTicketStatus`。

### 场景6：指派更新被覆盖

排查方向：首次指派是否使用 `IS NULL`，重新指派是否匹配读取到的旧处理人，是否存在无条件 updateById 写路径。相关类：`TicketServiceImpl.assignTicket`、`TicketAssignmentIntegrationTest`。

### 场景7：日志插入失败但工单仍更新

排查方向：Service 是否经 Spring 代理调用、方法是否有 `@Transactional`、是否吞掉运行时异常、日志是否异步或 REQUIRES_NEW、是否同一数据源。相关类：`TicketServiceImpl`、`TicketOperationAtomicityIntegrationTest`。

### 场景8：Flyway checksum mismatch

排查方向：检查已执行迁移是否被修改、数据库 history 与 Git 版本是否一致。不要直接 clean 或改 history；恢复原脚本并通过新迁移修正。相关位置：`db/migration`、`flyway_schema_history`。

### 场景9：Mapper 枚举映射失败

排查方向：数据库 VARCHAR 是否与 Java 枚举名称完全一致、是否出现未知值、列长度是否足够、是否误加 EnumValue 或 TypeHandler。相关类：枚举、Entity、V2、Mapper集成测试。

### 场景10：MySQL 外键异常

排查方向：creator、assignee、operator ID 是否真实存在，插入顺序是否先有父记录，测试事务之间是否可见；查看具体约束名称但不要向客户端返回 SQL。相关迁移：V4、V5、V6。

### 场景11：测试事务没有回滚

排查方向：测试是否由 Spring 管理、`@Transactional` 是否在测试方法/类、是否手动提交或使用独立事务、AfterTransaction 查询条件是否唯一。相关测试：各 IntegrationTest、原子性测试。

### 场景12：Security 集成测试意外绕过 Filter Chain

排查方向：是否使用 standalone MockMvc、是否手动构造 Controller、是否缺少 `@AutoConfigureMockMvc`。相关测试：对比 `TicketControllerTest` 与 `TicketAuthorizationIntegrationTest`。

### 场景13：注册并发出现重复用户名

排查方向：应用层 selectCount 只能优化提示，最终要依赖数据库唯一索引；确认 DuplicateKeyException 被转换为40902。相关类：`AuthServiceImpl`、V3、注册集成测试。

### 场景14：`/api/auth/me` 显示旧角色

排查方向：当前 `/me` 只读 Token claims，不查询数据库；确认 Token 签发时间和 TTL。当前尚无角色修改和撤销功能，演进需加入停用/版本或实时校验策略。

## 6. 一周复习计划

每天约60～90分钟，建议结构为“20分钟阅读、20分钟口述、20～40分钟动手”。

### 第1天：项目结构和接口

- 阅读：README、`AuthController`、`TicketController`、DTO 和 ErrorCode。
- 口述：30秒项目介绍；9个业务端点；三角色权限矩阵。
- 动手：不看文档画 Controller→Service→Mapper→MySQL 调用图。
- 自测：能在3分钟内找到任意端点的请求、响应、主要错误码。

### 第2天：数据库和 Flyway

- 阅读：V1～V6、三个 Entity、Mapper集成测试。
- 口述：每个迁移的目的；两个可空外键；RESTRICT取舍。
- 动手：手写三表核心 ER 关系，并设计一个不修改旧迁移的新字段变更。
- 自测：能解释 V2 为什么先迁移数据再改列定义。

### 第3天：密码和 JWT

- 阅读：PasswordConfig、AuthServiceImpl、JwtConfig、JwtTokenServiceImpl、Validator。
- 口述：BCrypt matches；JWT claims；Encoder/Decoder/Validator职责。
- 动手：手写一份不含真实值的 claims 表和认证链。
- 自测：能清楚回答签名不等于加密、当前为何不能撤销Token。

### 第4天：权限和所有权

- 阅读：SecurityConfig、Converter、401/403 Handler、所有权 Service 和安全测试。
- 口述：401与403；请求级与对象级；为什么他人工单返回404。
- 动手：为一个虚构 ADMIN 只读端点列出需要修改的层和测试，不写完整代码。
- 自测：能指出 `anyRequest().permitAll()` 的后续风险。

### 第5天：条件更新与并发

- 阅读：TicketStatus、状态和指派 Service、对应单元/集成测试。
- 口述：三条状态边；两个条件 UPDATE；affectedRows语义。
- 动手：手写首次指派和重新指派的伪 SQL，分析两个并发请求结果。
- 自测：能说明事务为什么不能替代条件更新。

### 第6天：事务日志与测试

- 阅读：TicketOperationLog、V6、appendOperationLog、Atomicity测试、testing_evidence。
- 口述：业务UPDATE→日志INSERT；为什么不用异步/REQUIRES_NEW。
- 动手：画出外键故障注入和回滚验证时序。
- 自测：能准确说出45类、329项的分层，且不都称端到端。

### 第7天：完整模拟讲解

- 阅读：简历描述、讲解稿和本问答中的高频/追问题。
- 口述：30秒、1分钟、3～5分钟各一次并录音复盘。
- 动手：按本地演示顺序准备测试账户和请求，不打印完整Token。
- 自测：随机抽10题，8题能先给结论；能主动说出两个当前不足。

## 7. 面试前最终检查

### 表达

- [ ] 我能在30秒内说明做了什么、两个重点设计和测试结果。
- [ ] 我能在1分钟内覆盖功能、安全、并发、事务和测试。
- [ ] 我能在3～5分钟内完整讲解且不过度展开。
- [ ] 我不会把 AI、RAG、Redis、微服务或生产部署说成已实现。
- [ ] 我不会虚构 QPS、响应时间、覆盖率或性能提升。

### 深度

- [ ] 我能解释项目最难的设计，而不是只列技术栈。
- [ ] 我能区分401、403、404和应用错误码。
- [ ] 我能区分条件更新、乐观锁和事务。
- [ ] 我能解释真实数据库回滚证据。
- [ ] 我能承认 Token 撤销、用户停用、压力测试等边界。

### 代码与演示

- [ ] 我能现场定位 Controller、Service、SecurityConfig、ErrorCode 和迁移。
- [ ] 我能找到一个单元测试、一个 MVC 测试和一个真实 MySQL 测试。
- [ ] MySQL 容器能健康启动，应用本地配置与密钥已安全准备。
- [ ] 演示账户均为测试数据，不暴露密码、哈希、Secret或完整Token。
- [ ] 我能在日志查询 API 不存在的前提下，用只读 SQL或测试展示日志。
- [ ] 我已运行 `mvn test` 并确认实际结果，而不是引用预计值。
