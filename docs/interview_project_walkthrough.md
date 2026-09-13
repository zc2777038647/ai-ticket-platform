# 项目面试讲解稿

本文用于口头练习。讲解时先给结论，再结合代码和测试，最后主动说明边界；不需要逐字背诵。

## 1. 30 秒版本

我做的是一个 Java + Python 工单平台，完成了注册登录、JWT 三角色授权、工单分页和状态流转、管理员指派及事务操作日志。状态和指派使用旧值条件更新，权限同时覆盖接口角色和 USER 对象所有权。P8 又基于 Redis Lua 增加登录双维度固定窗口限流和用户作用域创建幂等，P9 通过同级 FastAPI 服务提供结构化分析建议、人工审核回复草稿、只读 Agent、RAG 与 MCP。项目目前有62个 Java 测试类、463项测试，并用真实 MySQL 和 Redis 验证关键链路。

## 2. 1 分钟版本

这是一个 Java + Python 工单后端，Java 先建立可靠业务底座，再通过受控 HTTP 边界提供 AI 能力。项目使用 Spring Boot、MyBatis-Plus、MySQL 和 Flyway，实现注册登录、创建查询分页、顺序状态流转、ADMIN 指派和追加式日志；BCrypt、HS256 JWT 与 Spring Security 建立 USER、AGENT、ADMIN 三角色权限，Service 再依据 `creator_user_id` 做对象级所有权。

并发方面，状态和处理人更新都把旧值放进 SQL 条件，业务 UPDATE 与日志 INSERT 在同一 MySQL 事务中提交。P8 接入 Spring Data Redis 和 Lettuce：登录用单 Key Lua 对 IP 和规范化用户名做固定窗口限流；创建工单用 JWT `sub` 隔离 `Idempotency-Key`，结合请求指纹、ownerToken 和 `PROCESSING/SUCCEEDED` 状态重放第一次成功响应。这个幂等只有有限 TTL，Redis 和 MySQL 不在同一事务，因此不宣称 exactly-once。AI 客户端还覆盖连接拒绝、超时、4xx、5xx 和非法响应；Java 有62个测试类、463项测试。

## 3. 3～5 分钟完整版本

### 第一段：项目目标

这是一个 Java 业务后端与同级 Python AI 服务组成的工单平台。Java 负责账户、权限、状态、指派、审计以及入口可靠性保护；Python 只输出建议、草稿或受控只读回答，不能绕过可信身份和业务规则，也不能直接修改工单。

### 第二段：分层与数据模型

请求先经过 Spring Security Filter Chain，再进入 Controller；Controller 处理 HTTP、Validation 和认证主体，Service 处理业务规则与 MySQL 事务，MyBatis-Plus Mapper 访问数据库。核心有 users、tickets、ticket_operation_logs 三张表，Flyway V1～V6 保留演进历史。

Redis 由 Spring Boot 自动配置 LettuceConnectionFactory 和 StringRedisTemplate。项目没有 Redisson、通用 Object 序列化或已经完成的工单缓存；Redis 当前只承载登录限流和创建幂等临时状态。Python 默认使用 deterministic fake provider，真实 provider 通过环境变量配置。

### 第三段：JWT 与权限

注册使用 BCrypt strength 10，只保存哈希。登录成功签发两小时 HS256 Access Token，包含 sub、username、role 和 jti。Resource Server 校验签名、issuer、时间和必要 claims，并把角色转换为 Spring Authority。

授权分两层：SecurityFilterChain 判断哪种角色可以调用某类接口；TicketService 再判断 USER 是否拥有具体工单。USER 查询他人工单返回404，减少资源存在性泄露。`creatorName` 只是展示文本，可信创建者和日志操作者都来自 JWT `sub`。

### 第四段：条件更新与事务审计

状态只允许 `OPEN → IN_PROGRESS → RESOLVED → CLOSED`。状态更新 SQL 同时匹配 ID 和旧状态；首次指派要求旧处理人为 NULL，重新指派匹配旧处理人 ID。若另一个请求已经修改，affectedRows 为0并返回明确冲突，不会静默覆盖。

条件 UPDATE 成功后同步 INSERT 操作日志，两次写入位于同一 `@Transactional`。真实 MySQL 测试用不存在的 operator ID 触发日志外键失败，验证状态或处理人回滚且没有日志残留。条件更新解决并发前置条件，事务解决业务写和日志写的原子性，两者不能混为一谈。

### 第五段：Redis 登录限流

登录请求通过 JSON 和 Validation 后进入 Controller，先按 `remoteAddr` 检查 IP 桶，再按 trim + `Locale.ROOT` 小写后的用户名检查第二个桶。默认 IP 60秒20次、用户名60秒10次，成功和失败登录都计数。

固定窗口 Lua 在首次请求时原子写入计数和 TTL，后续正常请求不刷新窗口；超限返回 HTTP 429 / 42900 和正数 `Retry-After`。未建立可信代理边界，所以不信任 `X-Forwarded-For`。Redis 故障时 fail-closed，不继续执行 AuthService。它不是账户锁定、滑动窗口或令牌桶，窗口边界仍可能产生突发。

### 第六段：创建工单幂等

启用时 `POST /api/tickets` 需要 8～128 字符的 `Idempotency-Key`。JWT `sub` 与 trim 后的 Key 经过长度编码和 SHA-256 生成用户作用域 Redis Key；创建请求四个字段生成请求指纹；随机 ownerToken 标识本次处理者。

acquire Lua 首次写 `PROCESSING`。相同 Key 和指纹仍在处理时返回40906与 `Retry-After`；不同指纹返回40907；成功后 Hash 转为 `SUCCEEDED`，保存序列化的 `TicketResponse`。相同请求再次到来直接重放第一次 DTO，不查询数据库，不刷新成功 TTL。ownerToken 防止旧请求完成或删除新 owner 的记录。

协调器没有数据库事务，TicketService 的 MySQL 事务先提交，之后 Redis 才 `complete`。如果 Service 业务失败，当前 owner 可以 release；但 Service 已成功后若序列化或 Redis 完成失败，不能释放 PROCESSING，否则重试可能立即插入第二张工单。Redis 与 MySQL 之间仍有崩溃窗口，所以当前只叫有限窗口幂等，不是 exactly-once，也没有 MySQL 持久化幂等表。

### 第七段：测试证据和边界

项目有62个 Surefire 测试类、463项测试。除了 Validation、Mockito、standalone MockMvc、Security 和真实 MySQL，还用真实 Redis验证 PING、TTL、INCR、SETNX、固定窗口和幂等 Lua状态机；HTTP 测试证明相同请求重放时数据库只有一张工单，不同用户同 Key 隔离；Python 另有12项 pytest 测试。

协调器单元测试验证 Redis 获取失败 fail-closed、业务失败释放，以及 Service 成功后 complete 失败不释放。但没有通过杀进程或断网做破坏性崩溃测试，也没有高并发压测、多实例、Sentinel/Cluster、MySQL 持久化幂等或性能数据。

## 4. 面试官打断时的跳转句

- “这一段最关键的是 Redis 单 Key 原子性不等于 Redis 与 MySQL 强一致。”
- “如果您关注 Java，我可以展开讲 Lua、事务和条件 UPDATE 的职责边界。”
- “如果您关注安全，我可以说明 remoteAddr、代理信任和 fail-closed 取舍。”
- “幂等不是简单加锁，我还比较请求指纹并重放第一次成功响应。”
- “当前版本没有实现这一点，我可以说明现有边界和演进触发条件。”
- “这项数据没有压测证据，所以我不会给出 QPS 或性能提升百分比。”

## 5. 不同岗位的讲解重点

### 5.1 Java 后端岗位

重点说明：

1. Controller、Service、Mapper 和 Redis 协调层的职责；
2. 单 Key Lua 为什么能避免多命令中间状态；
3. 条件 UPDATE 与 MySQL 事务分别解决什么问题；
4. 为什么协调器不能用外层 `@Transactional` 包住 Redis 与 MySQL；
5. Service 成功后 Redis 失败为何不释放 PROCESSING；
6. 请求级授权、对象级所有权和用户作用域幂等。

### 5.2 AI 应用后端岗位

重点说明 Java 先建立可靠业务底座：可信身份、权限、状态机、审计、登录限流和创建幂等，再通过内部 HTTP 调用 Python。结构化分析是建议，回复是人工审核草稿，Agent 只读；默认 fake provider 不应冒充真实 LLM 效果，任何模型输出都不能直接写数据库。

### 5.3 测试开发岗位

重点说明真实 Redis 与 Mock 的互补、Lua 状态机分支、HTTP 成功响应重放、不同用户 Key 隔离、TTL 不刷新、故障注入和精确 Key 清理。主动指出 Redis/MySQL 崩溃窗口目前只做了部分单元故障注入，没有进行停止容器或杀进程的破坏性测试。

## 6. 本地演示顺序

1. 确认 MySQL 和 Redis 容器均 healthy；
2. 注册测试 USER，并登录获取占位展示的 Access Token；
3. 重复失败登录展示 HTTP 429、42900 和 `Retry-After`；
4. 使用合法 `Idempotency-Key` 创建工单；
5. 相同用户、相同 Key 和请求再次调用，展示相同 TicketResponse 且数据库只有一条记录；
6. 相同 Key 改变请求字段，展示 HTTP 409 / 40907；
7. 使用 ADMIN 指派、AGENT 更新状态，并通过只读 SQL或测试展示操作日志；
8. 展示 `mvn test` 的62类、463项、0失败结果，并展示 Python `pytest` 的12项通过结果；
9. 最后主动说明 MySQL 提交后 Redis complete 前的三个一致性窗口。

演示不依赖不存在的前端或日志查询 API，也不展示密码哈希、完整 JWT、客户端 Key、ownerToken、请求指纹、原始 Redis Key 或 Secret。
