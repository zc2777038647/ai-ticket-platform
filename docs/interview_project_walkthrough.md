# 项目面试讲解稿

本文用于口头练习。讲解时应根据面试官关注点取舍，不需要逐字背诵。

## 1. 30秒版本

我做的是一个基于 Spring Boot 的工单平台后端，目前完成注册登录、JWT认证、三角色授权、工单分页、状态流转、管理员指派和操作日志。两个重点设计是请求级权限与对象级所有权分离，以及通过条件更新和同事务日志保证并发冲突可识别、业务与审计一起成功或回滚。项目有45个测试类、329项分层测试，并使用真实 MySQL 验证关键链路。

## 2. 1分钟版本

这个项目是一个单体 Java 工单后端，目标是先建立可靠的业务、身份和审计基础，再为后续智能分类等能力预留演进空间。它使用 Spring Boot、MyBatis-Plus、MySQL 和 Flyway，实现注册登录、工单创建、详情、我的工单、条件分页、状态流转和管理员指派。安全方面用 BCrypt 保存密码哈希，以 HS256 JWT 建立 USER、AGENT、ADMIN 三角色授权；SecurityFilterChain 处理接口级权限，Service 再按 `creator_user_id` 处理 USER 的对象所有权。状态和指派都使用旧值条件 UPDATE，避免并发请求静默覆盖；成功后同步写操作日志，并在同一事务中提交。当前通过45个测试类、329项自动化测试验证 Validation、Service、MVC、安全链路和真实 MySQL 回滚场景。

## 3. 3～5分钟完整版本

### 第一段：项目目标

我做的是一个单体 Java 工单平台后端。当前重点不是直接接 AI 模型，而是先把用户身份、权限、工单状态、处理人指派和操作审计这些基础能力做好。因为后续无论接工单分类、优先级建议还是回复草稿，都必须建立在可信身份和稳定业务状态上。

当前系统完成了注册、登录、当前用户、创建工单、详情查询、我的工单、全局条件分页、状态更新和管理员指派。AI 分类、RAG、Agent、Redis、消息队列都还没有实现，我会把它们放在 Roadmap，而不是当成当前成果。

### 第二段：架构和数据模型

项目采用常规分层：请求先经过 Spring Security Filter Chain，再进入 Controller；Controller 负责 HTTP 参数和认证主体，Service 负责业务规则、所有权和事务，Mapper 基于 MyBatis-Plus 访问 MySQL。

核心有三张表。`users` 保存用户名、BCrypt 哈希、显示名和角色；`tickets` 保存工单内容、可信创建者 ID、可空处理人 ID、优先级和状态；`ticket_operation_logs` 保存状态或指派变更的操作者、前值、后值和时间。数据库通过 Flyway V1～V6 演进，没有直接修改已经执行的旧迁移。

### 第三段：认证授权

注册时密码通过 BCrypt strength 10 编码，数据库只保存哈希；登录时使用 `matches` 校验，然后签发有效期两小时的 HS256 Access Token。Token 的 `sub` 是用户 ID，另外包含 username、role 和 jti。Resource Server 会验证签名、issuer、时间以及这些必要 claims，再把 role 转为 `ROLE_USER`、`ROLE_AGENT` 或 `ROLE_ADMIN`。

授权分成两层。SecurityFilterChain 处理请求级规则，比如 USER 不能全局分页或更新状态，只有 ADMIN 能指派。对象级规则放在 TicketService：USER 查询详情时使用工单 ID 和 `creator_user_id` 一起查询，所以不能读取他人的工单。未命中统一返回404而不是403，避免确认资源是否存在。请求中的 `creatorName` 只是展示文本，不能作为授权依据，可信创建者来自 JWT `sub`。

### 第四段：业务难点和并发

工单状态不是任意修改，而是明确状态机：`OPEN → IN_PROGRESS → RESOLVED → CLOSED`，不允许跳跃、逆向或相同状态更新。

只在 Java 中先查再判断还不够，因为两个请求可能同时读到同一个旧状态。所以状态更新的 SQL 条件同时包含 ID 和旧状态。如果另一个请求已经更新，当前 UPDATE 影响行数就是0，Service 返回状态冲突，而不是覆盖新状态。

指派使用相同思想。首次指派要求 `assignee_user_id IS NULL`，重新指派要求数据库处理人仍等于刚才读取的旧处理人。这样可以区分正常成功、重复指派和并发变化。我没有直接使用无条件 `updateById`，因为它无法表达“只有旧值没有变化时才写入”的前置条件。

### 第五段：事务与操作审计

状态或指派条件 UPDATE 成功后，Service 会同步插入一条操作日志。状态日志记录旧状态和新状态；指派日志记录旧处理人 ID 和新处理人 ID。操作者来自 JWT `sub`，所以指派日志的操作者是执行操作的 ADMIN，目标 AGENT 只是 after value。

业务 UPDATE 和日志 INSERT 在同一个 `@Transactional` 中：两次写入都成功才提交。日志放在业务条件 UPDATE 之后，是为了避免条件更新失败时留下虚假日志；日志失败又必须让业务 UPDATE 回滚，所以没有使用异步或 `REQUIRES_NEW`。

我用真实 MySQL 做了故障注入：传入一个确认不存在但为正数的操作者 ID，让日志 INSERT 触发外键异常。测试验证状态仍为 OPEN、处理人仍为 NULL，并且没有日志残留。这证明的是业务与日志原子性。条件 UPDATE 解决的是并发覆盖，两者不是同一个问题。

### 第六段：测试和边界

项目当前有45个测试类、329项测试实例。它们不是329个端到端测试，而是分层覆盖 DTO Validation、Service/Mockito、standalone MockMvc、Mapper 与真实 MySQL、HTTP 全链路、Security Filter Chain，以及事务和条件更新场景。

当前不足主要有两个方面。第一，只有 HS256 Access Token，没有 Refresh Token、撤销、用户停用和登录限流；第二，还没有真实多线程 HTTP 竞态、压力测试、操作日志查询和 AI 能力。下一步我会先收紧未来端点的安全默认规则，再评估日志查询、分配给我的工单和智能分类等功能。

## 4. 面试官打断时的跳转句

- “这部分最关键的是，请求级权限和对象级所有权是两层机制。”
- “这个问题我当时主要从业务合法性和数据库并发条件两个层面处理。”
- “这里的条件更新和事务不是同一件事，我分别说明一下。”
- “如果您更关注数据库，我可以展开讲 V1～V6 和外键取舍。”
- “如果您更关注安全，我可以从 JWT 验证链和401/403边界展开。”
- “这个场景我不只做了 Mock 测试，还用真实 MySQL 验证了最终状态。”
- “当前版本没有实现这一点，我可以说明现有边界和下一步演进方案。”
- “这项数据目前没有性能测试证据，所以我不会给出 QPS 或提升百分比。”

## 5. 不同岗位的讲解重点

### 5.1 Java 后端岗位

重点顺序：

1. Controller、Service、Mapper 分层；
2. DTO 与 Entity 隔离；
3. Flyway 数据演进和外键；
4. 条件 UPDATE、affectedRows 和业务异常；
5. `@Transactional` 原子性；
6. 单元测试与真实数据库测试如何互补。

建议深入回答为什么不用无条件 `updateById`、为什么 Service 不直接读 `SecurityContext`、为什么已执行迁移不能修改。

### 5.2 AI 应用后端岗位

先说明当前完成的是可靠工单业务底座：可信身份、权限、状态机、处理人和审计日志。后续计划接入工单分类、优先级建议和回复草稿，但必须保留人工确认、权限校验和操作可追踪性。

不要说已经实现模型调用、RAG 或 Agent。可以重点讲为什么业务状态和审计是接入 AI 前的必要边界，以及未来模型输出不能直接绕过 Service 业务规则。

### 5.3 测试开发岗位

重点顺序：

1. 329项测试如何不重复分层；
2. Mockito 验证业务交互与内存时序；
3. standalone MockMvc 与真实 Security Filter Chain 的差异；
4. 唯一测试前缀和事务回滚隔离；
5. 外键故障注入证明事务回滚；
6. 当前没有压力、容灾和真实多线程竞态测试。

## 6. 本地项目演示顺序

### 准备

确认 MySQL 容器健康，启动应用。准备一个 USER、一个 AGENT 和一个 ADMIN 测试账户，不展示密码哈希或完整 Token。

### 演示流程

1. 调用 `POST /api/auth/register` 注册 USER；
2. 调用 `POST /api/auth/login` 登录三种角色，后续只展示 `<access-token>`；
3. 使用 USER Token 调用 `POST /api/tickets` 创建工单；
4. 使用 USER Token 调用 `GET /api/tickets/mine`，说明按 JWT `sub` 过滤；
5. 使用另一个 USER Token 查询该工单，展示 HTTP 404 / 40400；
6. 使用 ADMIN Token 调用 `PATCH /api/tickets/{id}/assignee` 指派给 AGENT；
7. 使用 AGENT Token 调用 `PATCH /api/tickets/{id}/status` 更新为 IN_PROGRESS；
8. 通过只读 SQL或相关集成测试查看 `ticket_operation_logs`，说明当前没有日志查询 API；
9. 展示 `mvn test` 汇总：329项、0失败、BUILD SUCCESS。

演示不依赖前端，也不应现场打印真实 Secret、完整 JWT 或数据库密码。
