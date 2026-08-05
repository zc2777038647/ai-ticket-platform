# 项目面试问题与参考回答

本文的问题和答案只针对当前 AI 工单平台后端。回答建议遵循“先给结论，再结合项目实现，最后说明边界或演进”的顺序。

## A. 项目整体

### A1. 为什么做这个项目？【高频】

**答：** 我希望用一条完整业务链练习 Java 后端，而不只是实现孤立接口。项目从工单创建逐步扩展到认证、所有权、状态并发、指派和审计，能够验证分层、数据库演进、安全与事务。当前 AI 能力尚未实现，先完成的是可靠业务底座。

### A2. 项目当前解决了什么问题？【高频】

**答：** 当前解决用户注册登录、三角色授权、工单创建查询分页、顺序状态流转、ADMIN 指派和操作留痕。身份来自 JWT，状态和指派使用条件更新，业务修改与日志同事务。通知、评论、日志查询和 AI 分类仍未实现。

### A3. 为什么选择单体架构？【高频】

**答：** 当前业务规模和学习目标不需要微服务，单体可以让用户、工单和日志共享一个数据库事务，降低部署与分布式一致性复杂度。若未来模块和团队边界明确，再依据调用量和一致性要求拆分，而不是提前引入远程调用。

### A4. 项目最难的部分是什么？【追问】

**答：** 难点不是 CRUD，而是把身份、对象所有权、并发条件和日志原子性串起来。例如指派时要区分 ADMIN 操作者与目标 AGENT，并保证旧处理人未变化、日志失败时业务回滚。真实 MySQL 故障测试用于证明这一点。

### A5. 当前项目有哪些核心数据？

**答：** 核心是三张表：`users` 保存账户和角色，`tickets` 保存工单、可信创建者和可空处理人，`ticket_operation_logs` 保存状态或指派的操作者与前后值。结构通过 Flyway V1～V6 演进，不包含评论、附件或知识库数据。

### A6. 为什么项目名中保留 AI？

**答：** AI 表示后续方向，而不是当前完成度。现在先完成可靠工单业务、权限、状态和审计，以便未来模型建议仍经过人工确认和 Service 规则。简历和面试中会明确说明尚未实现分类、RAG 或 Agent。

### A7. 当前最有说服力的项目证据是什么？

**答：** 除了可定位的生产代码和 V1～V6，项目有45个 Surefire 测试类、329项测试实例，并通过真实 MySQL 验证外键、条件更新、JWT 安全链和日志失败回滚。但这些不是压力测试，也不等于生产运行证据。

### A8. 如果继续开发，优先做什么？【追问】

**答：** 我会先收紧 `anyRequest().permitAll()` 的兜底规则，再补操作日志只读查询和“分配给我”。之后考虑用户停用、Token 撤销、限流与幂等，最后才接入分类、优先级建议和回复草稿，避免 AI 绕过基础业务边界。

**代码定位：** `README.md`、`docs/p7_project_stage_review.md`、`src/main/java/.../controller`、`src/main/java/.../service/impl`。

## B. Spring 分层与设计

### B1. Controller 和 Service 如何划分职责？【高频】

**答：** Controller 处理路径、请求绑定、Validation、认证主体和 HTTP 状态；Service 处理状态机、所有权、指派校验、条件更新和事务。比如 Controller 从 JWT 取 operator ID，Service 决定能否流转并写日志。这样业务逻辑不依赖 HTTP。

### B2. 为什么 Service 不直接读取 SecurityContext？【高频】

**答：** Controller 从已经认证的 `JwtAuthenticationToken` 提取最小身份数据，再显式传入 Service，使方法契约清晰且容易做纯单元测试。若 Service 直接读线程上下文，会增加隐式依赖，也不利于未来从非 HTTP 入口复用业务。

### B3. 为什么不直接返回 Entity？【高频】

**答：** Entity 服务于数据库映射，DTO 服务于外部契约。项目返回 `TicketResponse`、`UserResponse` 等 record，避免泄露密码哈希、外键和内部时间字段，也防止数据库字段调整直接改变 API。当前需要更多字段时应显式演进 DTO。

### B4. 为什么请求 DTO 使用 record，而 Entity 使用普通类？

**答：** 请求和响应 DTO 是有限字段的不可变数据载体，record 简洁且契合值语义。MyBatis-Plus Entity 需要无参构造、setter、自增 ID 回填和查询结果填充，所以使用普通 Java 类。项目没有用 Lombok，映射行为更显式。

### B5. 为什么使用统一 ApiResponse？

**答：** 统一 `code/message/data` 让成功和业务错误协议稳定，HTTP 状态表达传输语义，五位应用码区分具体错误。当前没有虚构 traceId 或 timestamp；Actuator 端点也不套业务响应，因为它由框架管理。

### B6. 为什么 Mapper 没写大量自定义 SQL？

**答：** 当前 CRUD、分页和条件更新可以由 `BaseMapper`、`LambdaQueryWrapper`、`LambdaUpdateWrapper` 表达，类型安全且避免 XML 重复。复杂报表或经过分析的性能 SQL 出现时才值得自定义，不能为了展示技术而增加无需求 SQL。

### B7. 为什么对象转换直接显式映射？

**答：** 当前字段很少，显式构造 DTO 能直接看到哪些字段对外暴露，也避免 BeanUtils 反射和额外 MapStruct 依赖。规模扩大后可以引入编译期映射，但仍要审查敏感字段，不能把转换工具当成自动安全边界。

### B8. `@Transactional(readOnly=true)` 有什么作用？【追问】

**答：** 它表达查询事务意图，并可能给事务管理器或驱动优化提示。项目用于详情、分页和登录查询，但它不是数据库层绝对禁止写入的安全机制。纯 Mockito 测试不能证明代理生效，真实 Spring 集成测试负责事务链路。

**代码定位：** `TicketController`、`AuthController`、`TicketServiceImpl`、`AuthServiceImpl`、`ApiResponse`、各 DTO。

## C. 数据库与 Flyway

### C1. 为什么使用 Flyway？【高频】

**答：** Flyway 用有序、可审计脚本描述 schema 演进，使本地和测试数据库按同一历史升级。项目从 V1 tickets 演进到 V6 操作日志，Spring 启动会校验 checksum 和版本。它不能替代备份、评审或生产发布流程。

### C2. 为什么不能修改已经执行的迁移？【高频】

**答：** 已执行脚本的 checksum 已记录，直接修改会让不同环境的“同一版本”产生不同结构并触发校验失败。项目用 V2 对齐旧状态，而不是回改 V1。若发现历史问题，应新增后续迁移明确修正。

### C3. 为什么 V2 先 UPDATE 再 ALTER？

**答：** V1 的旧值是 PENDING、PROCESSING、COMPLETED，Java 新枚举是 OPEN、IN_PROGRESS、RESOLVED。V2 先只转换三个已知旧值，再调整列默认和注释，避免未知状态被静默改成 OPEN。迁移没有删除数据。

### C4. 为什么 creator_user_id 初始允许 NULL？【高频】

**答：** V4 是在已有工单上增加身份外键，历史记录没有可信用户 ID。允许 NULL 可以保留真实历史，而不是虚构创建者。新建工单会从 JWT `sub` 写入非空 ID；NULL 历史工单只允许 AGENT、ADMIN 查看。

### C5. 为什么 assignee_user_id 允许 NULL？

**答：** 未指派是工单合法初始状态，所以数据库必须能表达 NULL。首次指派条件使用 `IS NULL`，重新指派匹配旧 ID。当前不支持取消指派，因此 Service 不会把已指派工单主动设回 NULL。

### C6. 外键为什么使用 RESTRICT？【追问】

**答：** 创建者、处理人和操作者是业务与审计引用，RESTRICT 可以避免删除用户或工单时级联抹掉历史。当前没有用户删除功能。若未来要停用用户，更适合增加状态；若必须删除，需要单独设计匿名化与审计保留策略。

### C7. RESTRICT、CASCADE、SET NULL 如何取舍？

**答：** RESTRICT 保留引用完整性，CASCADE 适合生命周期完全从属的数据，SET NULL 适合允许失去引用但保留主体的场景。本项目审计日志不能随工单或操作者消失，所以选择 RESTRICT；不能机械地把一种策略用于所有关系。

### C8. 为什么日志表没有 updated_at？

**答：** 操作日志表达已经发生的事件，生产代码只插入，不提供更新或删除，因此只有 `created_at`。这体现追加式意图，但不代表数据库物理不可篡改；拥有足够数据库权限的人仍可能修改记录。

### C9. 为什么日志复合索引包含 id？【追问】

**答：** `(ticket_id, created_at, id)` 和操作者索引可以按主体过滤并按时间读取；毫秒时间仍可能相同，追加自增 ID 可形成稳定顺序。它服务于未来只读查询和测试排序，但当前没有生产日志查询 API。

### C10. created_at 和 updated_at 为什么交给数据库？

**答：** 数据库通过 `CURRENT_TIMESTAMP(3)` 生成创建时间，并用 `ON UPDATE` 管理工单和用户更新时间，能覆盖所有写入口。插入后的 Java 对象不一定自动获得数据库时间，所以测试会重新查询验证；自增 ID 则由 MyBatis-Plus 回填。

**代码定位：** `src/main/resources/db/migration/V1__...sql` 至 `V6__...sql`、`Ticket`、`UserAccount`、`TicketOperationLog`。

## D. 密码、JWT 与 Spring Security

### D1. 为什么 BCrypt 不能重新 encode 后直接比较？【高频】

**答：** BCrypt 每次编码使用随机盐，相同密码生成的哈希通常不同，所以不能比较两个 encode 结果。项目登录使用 `passwordEncoder.matches(原始密码, 数据库哈希)`，由算法读取哈希中的参数和盐进行验证。

### D2. BCrypt strength 10 表示什么？【高频】

**答：** strength 是成本因子，10对应约 `2^10` 级别的核心计算工作量，提升它会同时增加攻击成本和登录注册耗时。项目显式配置10，但没有做目标硬件上的性能基准，不能声称它对所有部署都是最佳值。

### D3. JWT 签名和加密有什么区别？【高频】

**答：** 签名保证来源和完整性，不隐藏 payload；加密才提供机密性。项目使用 HS256 签名，所以 Token claims 可被客户端解码查看，不能放密码或敏感信息。安全依赖密钥保密、验证器和 HTTPS 等部署边界。

### D4. 为什么 sub 使用用户 ID？

**答：** 用户 ID 稳定、唯一，适合作为业务身份主键；username 可能有规范化或未来修改需求。项目把 `sub` 转为正数 Long，用于 `creator_user_id`、对象所有权和日志操作者，username 只作为辅助 claim。

### D5. HS256 有什么边界？【追问】

**答：** HS256 的签发和验证共享同一密钥，配置简单但所有验证方都能获得签发能力，不适合密钥分发复杂的多服务场景。当前是单体应用且密钥从环境变量注入；未来多服务可评估非对称算法、`kid` 和轮换机制。

### D6. JwtEncoder 负责什么？

**答：** `NimbusJwtEncoder` 使用 HS256 密钥把 header 和 claims 编码并签名。项目的 `JwtTokenServiceImpl` 组装 issuer、sub、iat、exp、jti、username、role。Encoder 不负责请求认证，也不决定接口权限。

### D7. JwtDecoder 负责什么？

**答：** Decoder 解析 Bearer Token、校验 HS256 签名，并执行配置的 Validator。项目使用同一 SecretKey 构建 `NimbusJwtDecoder`。签名合法仍不代表业务 claims 合法，所以还要标准和应用验证器。

### D8. Validator 负责什么？【高频】

**答：** 标准 Validator 检查 issuer 和时间，`ApplicationJwtValidator` 进一步要求正数 Long 的 sub、非空 username、有效 UserRole 和非空 jti。任一失败都会进入401。它不查询用户表，所以不能识别账户停用或实时角色变化。

### D9. Converter 负责什么？

**答：** Converter 把验证后的 role 转为 `ROLE_<role>` Authority，并让 `Authentication.getName()` 等于 JWT sub。SecurityConfig 据此匹配 `hasRole`，Controller 据此读取用户 ID。Converter 不验证签名，也不执行业务所有权。

### D10. AuthenticationEntryPoint 与 AccessDeniedHandler 有什么区别？【高频】

**答：** EntryPoint 处理未建立有效认证的请求，返回 HTTP 401 / 40101 和 `WWW-Authenticate: Bearer`；AccessDeniedHandler 处理已认证但权限不足，返回 HTTP 403 / 40300。两者都输出统一 JSON，但触发阶段不同。

### D11. 为什么无效 Token 返回401，越权返回403？

**答：** 无效 Token 无法证明调用者身份，所以是401；有效 Token 已建立身份，但角色不满足规则，所以是403。项目真实安全测试覆盖两种情况，不能把所有安全失败统一为500或200业务错误。

### D12. 为什么 USER 查他人工单返回404？【高频】

**答：** USER 的 Service 查询同时带 ID 和 `creator_user_id`，未命中统一为工单不存在。这样不向非所有者确认某个 ID 是否存在。请求级仍允许 USER 调用详情接口，对象级数据条件负责资源隔离。

### D13. 为什么 `/me` 不查询数据库？【追问】

**答：** 当前 `/me` 直接返回已验证 Token 的 sub、username 和 role，避免一次数据库查询，也符合无状态设计。但它无法反映签发后的账户停用或角色变化。未来有停用功能时，应结合短 TTL、版本 claim、黑名单或实时查询重新设计。

### D14. 当前如何撤销 Token？

**答：** 当前尚未实现 Token 撤销、Refresh Token 或服务端登出。Access Token 在两小时 TTL 内只要签名和 claims 合法就可使用。演进方案可以是短 Access Token、Refresh Token 轮换、jti 黑名单或用户 Token 版本。

### D15. 为什么不 trim 密码？

**答：** 密码中的空格可能是用户选择的一部分，trim 会静默改变凭据。项目只通过 Validation 限制非空和长度，注册 encode 与登录 matches 都使用原字符串。用户名和显示名则根据业务含义做小写或 trim 规范化。

**代码定位：** `PasswordConfig`、`JwtConfig`、`JwtProperties`、`JwtTokenServiceImpl`、`ApplicationJwtValidator`、`ApplicationJwtAuthenticationConverter`、`SecurityConfig`、两个 REST 安全处理器。

## E. 状态流转与并发

### E1. 为什么需要状态机？【高频】

**答：** 枚举本身只能限制值集合，不能限制转换方向。项目把规则放在 `TicketStatus.canTransitionTo`，确保状态只能按处理流程推进。以后若增加重开或取消，需要明确更新状态机和测试，而不是在 Controller 分散 if。

### E2. 当前允许哪些状态流转？【高频】

**答：** 只允许 `OPEN → IN_PROGRESS`、`IN_PROGRESS → RESOLVED`、`RESOLVED → CLOSED` 三条边。相同状态、跳跃、反向和 CLOSED 后更新都返回 40900。数据库 VARCHAR 只保存枚举名称。

### E3. 为什么不能直接 updateById？【高频】

**答：** 无条件 `updateById` 只按 ID 写入，两个请求都读到旧状态时，后写请求可能覆盖先写结果。项目把旧状态放进 WHERE，只有数据库仍处于读取值时才更新，从而把竞态转换为 affectedRows=0 的显式冲突。

### E4. 条件 UPDATE 如何防止覆盖？

**答：** Service 先读取 currentStatus，再执行 `WHERE id=? AND status=currentStatus`。若另一事务已经改变状态，条件不再成立，本次不会写入。它防止的是基于过期快照覆盖，不保证所有业务并发问题都自动解决。

### E5. affectedRows=0 表示什么？【高频】

**答：** 在已经查到工单并通过流转校验的前提下，0行通常表示从查询到更新之间旧状态发生变化，项目映射为40901。对于指派则映射40905。不能把0行继续当成功，否则客户端会收到与数据库不一致的响应。

### E6. affectedRows 大于1为什么是内部异常？

**答：** 更新条件包含主键 ID，理论上最多影响一行。大于1说明数据或执行假设被破坏，不能归类为普通冲突，所以项目抛 `IllegalStateException`，由统一兜底返回50000并触发事务回滚。

### E7. 条件更新与 version 乐观锁有什么区别？【追问】

**答：** 当前条件更新只针对状态或处理人这一业务旧值，SQL直观且无需 version 列。通用 version 可以覆盖实体更多修改，但所有写路径都要维护版本。若未来多字段并发更新增多，可评估统一版本机制。

### E8. `IS NULL` 和 `= NULL` 有什么区别？【高频】

**答：** SQL 中 NULL 表示未知，`column = NULL` 不会得到 true，必须使用 `IS NULL`。项目首次指派明确用 `isNull(Ticket::getAssigneeUserId)`；重新指派才使用 `eq` 匹配旧处理人 ID。

### E9. `@Transactional` 能阻止两个请求同时读到旧值吗？【追问】

**答：** 不能。普通事务边界不等于串行执行，两个事务仍可能读到同一状态。项目依赖条件 UPDATE 决定谁成功，另一个得到0行。事务在这里主要保证业务更新和日志插入一起提交或回滚。

### E10. 为什么没有使用悲观锁？

**答：** 当前状态和指派操作可用短条件 UPDATE 识别冲突，不需要提前持有行锁等待，逻辑和测试更简单。若未来一次操作需要长事务内读取多个强关联资源，才应评估 `SELECT FOR UPDATE` 的吞吐和死锁代价。

### E11. 先查询再更新是否多了一次 SQL？

**答：** 是，但查询用于确认资源存在、读取当前状态并执行明确状态机校验，随后条件 UPDATE 负责并发安全。可以设计单 SQL 状态转换，但错误分类和可读性会变化，需要依据性能证据权衡；当前没有性能瓶颈数据。

### E12. 为什么冲突返回409？

**答：** 请求格式和身份都可能合法，但它与当前资源状态冲突，HTTP 409 比400或500更贴近语义。项目再用40900、40901、40904、40905区分非法流转、状态竞态、重复指派和指派竞态。

**代码定位：** `TicketStatus`、`UpdateTicketStatusRequest`、`TicketServiceImpl.updateTicketStatus`、`TicketStatusUpdateIntegrationTest`、`TicketStatusUpdateHttpIntegrationTest`。

## F. 指派业务

### F1. 为什么只有 ADMIN 能指派？【高频】

**答：** 当前业务将人员调度视为管理权限，SecurityConfig 只允许 ADMIN 调用 assignee PATCH。Service 仍负责目标合法性和并发条件。若未来支持 AGENT 领取，需要新增独立规则和接口，而不是直接放宽现有指派端点。

### F2. 为什么目标必须是 AGENT？【高频】

**答：** USER 是提交方，ADMIN 是调度方，AGENT 才是处理角色。Service 真实查询目标 UserAccount 并检查角色，不信任请求中的角色文本。目标不存在返回40401，角色不合法返回40903。

### F3. 为什么旧处理人不能由客户端提交？【高频】

**答：** 旧值是服务器并发控制依据，若由客户端决定容易被伪造或过期。Service 查询数据库获得 currentAssignee，再放进条件 UPDATE。请求 DTO 只接收目标 `assigneeUserId`，保持外部契约最小。

### F4. 为什么重复指派返回冲突？

**答：** 当前将“已经指派给同一人”视为无状态变化的业务冲突，返回40904，也不写第二条日志。另一种设计可以做幂等成功，但必须同步修改响应语义、日志策略和测试，不能只改一个 if。

### F5. 首次指派与重新指派的条件有什么不同？

**答：** 首次指派要求数据库旧值仍为 NULL，因此用 `IS NULL`；重新指派要求旧值仍等于读取到的 AGENT_A ID，因此用等值条件。两者都同时匹配 ticket ID，并在成功后写 ASSIGNEE_CHANGED。

### F6. 指派操作者和目标处理人有什么区别？【高频】

**答：** 操作者是发起 PATCH 的 ADMIN，来自 JWT `sub`，写入 `operator_user_id`；目标处理人是请求中的 AGENT ID，写入 tickets.assignee 和日志 after value。把目标 AGENT 当操作者会丢失真正责任主体。

### F7. 为什么不支持取消指派？

**答：** 当前需求只定义首次指派和重新指派，没有取消语义、权限或日志约定，所以 Service 不提供设 NULL 方法。未来增加时需要明确谁能取消、状态限制、before/after 日志以及并发条件，不能直接暴露通用更新。

### F8. 角色变化的并发边界是什么？【追问】

**答：** Service 在 UPDATE 前查询目标并验证其当时为 AGENT，但条件 UPDATE 没有关联 users.role。如果未来存在实时改角色功能，校验后角色可能变化。当前没有角色修改接口；未来需用事务锁、数据库约束或业务状态重新设计。

### F9. 为什么不让 AGENT 主动领取？

**答：** 主动领取是另一种业务用例，涉及可领取状态、队列、公平性和并发竞争。当前由 ADMIN 调度更简单且与权限矩阵一致。未来可以新增独立 claim 方法，用 `assignee IS NULL` 条件竞争，但不能声称已经实现。

### F10. 指派失败为什么不写日志？

**答：** 操作日志只记录实际成功的业务变化。工单不存在、目标不存在、角色错误、重复指派和条件更新0行都没有成功变更，所以日志 Mapper 不应被调用；单元和 HTTP 测试都验证了这一点。

**代码定位：** `AssignTicketRequest`、`TicketController.assignTicket`、`SecurityConfig`、`TicketServiceImpl.assignTicket`、`TicketAssignmentIntegrationTest`、`TicketAssignmentAuthorizationIntegrationTest`。

## G. 操作日志与事务

### G1. 为什么日志必须与业务 UPDATE 同事务？【高频】

**答：** 当前日志是业务一致性要求，不是可丢失的统计事件。如果工单更新提交但日志失败，就无法追踪操作者；如果日志成功但业务失败，又会产生虚假记录。因此两个写操作在同一个 Service `@Transactional` 中一起提交或回滚。

### G2. 为什么业务 UPDATE 在日志 INSERT 之前？【高频】

**答：** 只有条件 UPDATE 影响1行才说明业务动作真正成功获得写入资格。若先插日志，随后发现旧状态或旧处理人已经变化，会记录未发生的动作。当前顺序是业务条件成功、写日志、共同提交。

### G3. 为什么不能使用 REQUIRES_NEW？【高频】

**答：** `REQUIRES_NEW` 会让日志在独立事务提交，即使外层业务事务后来回滚，日志也可能保留；反过来日志失败也不必然撤销业务。它适用于不同一致性要求的场景，但不符合本项目的强原子性目标。

### G4. 为什么不能异步写日志？

**答：** 异步执行脱离当前事务，业务响应成功时日志可能尚未写入或最终失败。若采用异步，应接受最终一致性并设计可靠消息、重试和死信。当前需求要求同步强一致，因此直接调用日志 Mapper。

### G5. 日志失败如何证明业务回滚？【高频】

**答：** `TicketOperationAtomicityIntegrationTest` 使用真实 MySQL，并传入不存在的正数 operator ID，让日志 INSERT 违反外键。测试捕获 `DataIntegrityViolationException` 后验证状态仍为 OPEN、处理人仍为 NULL且日志为0，不是只用 Mockito 推断。

### G6. 为什么日志 insert 返回行数和 ID 都要检查？

**答：** 影响行数必须为1才能确认落库，自增 ID 必须回填才能确认 Mapper 主键策略符合预期。任一异常都抛 `IllegalStateException`，不返回成功。createdAt 由数据库生成，不要求原始对象自动回填。

### G7. 为什么内存 Ticket 要在日志成功后修改？【追问】

**答：** 即使数据库事务会回滚，过早修改内存对象也会让单元测试或后续代码看到“成功后的状态”。项目在日志成功后才 setStatus 或 setAssignee，使内存语义和方法是否成功保持一致。

### G8. 当前日志记录哪些内容？

**答：** 只有 STATUS_CHANGED 和 ASSIGNEE_CHANGED。字段包括 ticket ID、JWT 操作者 ID、操作类型、前值、后值和数据库创建时间。不记录完整请求体、Token、密码、IP、User-Agent、评论或备注。

### G9. 当前日志是否物理不可篡改？【追问】

**答：** 不是。生产代码只插入且没有修改接口，体现应用层追加式设计；但数据库高权限用户仍可修改或删除。若需要合规审计，应评估权限隔离、独立存储、签名链、WORM 或外部审计系统。

### G10. 为什么日志表保留外键？

**答：** 外键保证 ticket 和 operator 必须真实存在，并使用 RESTRICT 防止删除主体时静默抹掉审计引用。它也被原子性测试用于真实故障注入。代价是归档和删除策略更复杂，需要未来明确生命周期。

### G11. 日志量大后如何演进？

**答：** 先根据真实查询模式使用现有 ticket/operator 时间索引并增加只读分页，再评估归档、冷热分层或独立审计存储。若改为异步，需要可靠消息和最终一致性补偿。当前没有数据量和性能证据，不提前拆库。

### G12. 事务回滚能回收自增 ID 吗？

**答：** 通常不能。MySQL 自增值在事务回滚后仍可能被消耗，所以项目测试只断言 ID 为正数，不断言连续。ID 的职责是唯一标识，不是无间隙序号；需要连续业务编号应单独设计。

**代码定位：** `TicketOperationLog`、`TicketOperationType`、`TicketOperationLogMapper`、`TicketServiceImpl.appendOperationLog`、V6、`TicketOperationAtomicityIntegrationTest`、`TicketOperationLoggingHttpIntegrationTest`。

## H. 测试

### H1. 为什么需要多个测试层次？【高频】

**答：** 每层失败定位和可信度不同：单元测试快速验证业务分支，MVC 验证协议，Mapper/Service 集成验证真实 SQL和事务，Security HTTP 测试验证完整认证链。只保留一种会留下明显盲区。

### H2. standalone MockMvc 是否经过 Security Filter Chain？【高频】

**答：** 当前 Controller standalone 测试不会自动经过真实安全过滤链，所以需要显式 principal，并只证明参数绑定、Controller 协作和错误协议。角色授权由 `@AutoConfigureMockMvc` 的安全集成测试覆盖。

### H3. 如何验证真实 JWT？【高频】

**答：** 安全集成测试先向真实 MySQL 插入 BCrypt 用户，再调用登录获得 JWT，随后携带 Bearer Token 通过真实 Resource Server 访问接口。另有 JwtConfig、Validator、Converter 单元测试分别覆盖签名配置和 claims 规则。

### H4. 如何验证事务回滚？

**答：** 普通集成测试用 `@Transactional` 并在 `@AfterTransaction` 检查唯一前缀数据为0。业务原子性则主动制造日志外键失败，在嵌套保存点回滚后读取工单旧值，最终外层测试事务再清理全部数据。

### H5. 为什么回滚后自增 ID 仍增长？

**答：** MySQL 自增分配不保证事务回滚后复用，这是并发和实现语义的一部分。测试不依赖 ID 连续，也不通过清空表恢复编号。业务如果需要连续号码，不能直接把数据库主键当业务序列。

### H6. 为什么329项不能都称为端到端测试？【高频】

**答：** 329包含 DTO Validation、Mockito 单元、standalone MVC、Mapper、Service/MySQL、HTTP Security 和上下文冒烟等45个测试类。只有部分经过完整 HTTP、安全和数据库链路，全部称为端到端会夸大证据。

### H7. Mockito 测试能证明事务生效吗？【追问】

**答：** 不能。直接 new 或 `@InjectMocks` 的 Service 没有 Spring 代理，Mockito 只能验证调用顺序、参数、异常和内存对象。事务代理与真实回滚必须由 `@SpringBootTest` 和真实数据库测试证明。

### H8. 为什么数据库集成测试使用唯一前缀？

**答：** 唯一前缀让查询只命中本次准备数据，避免开发库其他记录影响 total、权限或残留断言。配合事务回滚和 AfterTransaction 检查可以验证零残留，但仍不回收已消耗的自增值。

### H9. 如何测试条件更新冲突？

**答：** 单元测试让 Mapper update 返回0验证业务映射；真实 MySQL 测试先改变状态或处理人，再用过期旧值执行相同条件 UPDATE，断言影响0行和最终值未覆盖。两层分别验证 Service 分支和数据库语义。

### H10. 如何验证没有越权数据泄露？

**答：** 所有权集成测试准备多个真实 USER 与各自工单，使用真实 Token 请求详情和 `/mine`，验证他人工单返回404且分页结果只含当前 `creator_user_id`。还要特别测试 keyword 条件被 AND 在所有权范围内。

### H11. 当前没有验证哪些内容？【追问】

**答：** 当前尚未执行压力测试、性能基准、真实多线程 HTTP 竞态、多节点一致性、生产部署、容灾、备份恢复和安全渗透测试，也没有代码覆盖率数据。因此不能给出 QPS、响应时间或“完全安全”结论。

### H12. 测试通过是否说明没有缺陷？

**答：** 不能。测试只说明当前环境和覆盖场景符合预期。项目已记录 `anyRequest().permitAll()` 的未来漏配风险、无 Token 撤销、无用户停用等边界。后续功能需要先补测试，再修改实现和文档。

**代码定位：** `src/test/java/...`、`TicketServiceImplTest`、`TicketControllerTest`、`BearerAuthenticationIntegrationTest`、`TicketOwnershipIntegrationTest`、`TicketOperationAtomicityIntegrationTest`、`docs/testing_evidence.md`。

## 标记说明

- **高频**：建议能在30～60秒内直接回答；
- **追问**：通常出现在基础回答之后，重点考察边界和权衡；
- 回答中的未来方案是演进方向，不代表当前已经实现。
