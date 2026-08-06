# 安全、并发与事务设计

本文记录当前实现提供的安全与一致性边界，不将其描述为完整的生产安全体系。

## 1. 密码安全

### 1.1 BCrypt 配置

`PasswordConfig` 创建：

```text
BCryptPasswordEncoder(strength = 10)
```

BCrypt 每次编码包含随机盐信息，因此相同明文通常生成不同哈希。`users.password_hash` 长度为 100，只保存哈希，不保存明文。

### 1.2 注册和登录

- 注册使用 `passwordEncoder.encode(request.password())`；
- 登录使用 `passwordEncoder.matches(rawPassword, storedHash)`；
- 密码不会 trim，避免静默改变用户输入；
- 用户名保存和查询前转为小写；
- 用户不存在与密码错误统一返回 40100；
- `UserResponse`、`CurrentUserResponse`、`LoginResponse` 均不包含 `passwordHash`。

当前没有密码修改、找回、复杂度黑名单、泄露密码检测或账户锁定功能。

## 2. JWT Access Token

### 2.1 签名与配置

| 项目 | 当前值 |
| --- | --- |
| 算法 | HS256 |
| JCA Key 类型 | HmacSHA256 |
| issuer | `ai-ticket-platform` |
| Access Token TTL | `PT2H`，即 2 小时 |
| 密钥来源 | 环境变量 `JWT_SECRET_BASE64` |
| 最小密钥长度 | Base64 解码后至少 32 字节 |
| Encoder / Decoder | NimbusJwtEncoder / NimbusJwtDecoder |

真实密钥不应写入仓库或文档。本地和部署环境需要分别注入。

### 2.2 Claims

`JwtTokenServiceImpl` 实际签发：

| Claim | 含义 |
| --- | --- |
| `iss` | 配置的 issuer |
| `sub` | 用户自增 ID 字符串 |
| `iat` | 签发时间 |
| `exp` | 过期时间 |
| `jti` | 每次签发生成的 UUID |
| `username` | 规范化后的用户名 |
| `role` | USER、AGENT 或 ADMIN |

JWT header 使用 `alg=HS256`、`typ=JWT`。

### 2.3 验证器

`JwtValidators.createDefaultWithIssuer` 负责标准时间与 issuer 校验；`ApplicationJwtValidator` 额外要求：

- `sub` 可以解析为正数 Long；
- `username` 非空；
- `role` 必须是 `UserRole` 枚举值；
- `jti` 非空。

任何必要 claim 缺失或非法都会导致 Token 认证失败。

### 2.4 Authentication 转换

`ApplicationJwtAuthenticationConverter` 将：

```text
role = AGENT
```

转换为：

```text
GrantedAuthority = ROLE_AGENT
```

同时令：

```text
Authentication.getName() = JWT sub
```

Controller 由此取得可信用户 ID，并只将最小业务身份参数传给 Service。

## 3. 401 与 403

### 3.1 401 AuthenticationEntryPoint

缺少、过期、签名错误或 claims 无效的 Token 由 `RestAuthenticationEntryPoint` 处理：

```text
HTTP 401
应用码 40101
WWW-Authenticate: Bearer
```

登录凭据错误也是 HTTP 401，但应用码为 40100。

### 3.2 403 AccessDeniedHandler

身份已认证但角色不允许访问时，由 `RestAccessDeniedHandler` 返回：

```text
HTTP 403
应用码 40300
```

401 表示尚未建立有效认证，403 表示身份有效但权限不足。

## 4. 请求级与对象级授权

### 4.1 SecurityFilterChain

当前请求级规则：

| 路径 | 规则 |
| --- | --- |
| `POST /api/auth/register` | 公开 |
| `POST /api/auth/login` | 公开 |
| `GET /actuator/health` | 公开 |
| `GET /api/auth/me` | 已认证 |
| `POST /api/tickets` | 已认证 |
| `GET /api/tickets/mine` | 已认证 |
| `GET /api/tickets` | AGENT、ADMIN |
| `GET /api/tickets/*` | 已认证 |
| `PATCH /api/tickets/*/status` | AGENT、ADMIN |
| `PATCH /api/tickets/*/assignee` | ADMIN |

应用使用无状态 Session，关闭 CSRF、form login、HTTP Basic 和 logout。

### 4.2 对象级授权

SecurityFilterChain 只能确认 USER 可以调用详情接口，不能判断其是否拥有某张工单。`TicketService.getTicketById` 对 USER 使用：

```text
id = 请求ID
AND creator_user_id = JWT sub
```

未命中统一返回 40400。AGENT、ADMIN 直接按 ID 查询任意工单。

### 4.3 creatorName 不是身份

`creatorName` 由创建请求提供，只是业务展示字段。授权只使用 JWT `sub` 和数据库 `creator_user_id`，防止客户端伪造显示名称获得所有权。

## 5. 并发控制

### 5.1 状态条件更新

Service 先读取当前状态并校验状态机，再执行：

```text
UPDATE tickets
SET status = target
WHERE id = ticketId
  AND status = previouslyReadStatus
```

- 1 行：当前请求赢得条件更新；
- 0 行：旧状态已变化，返回 40901；
- 其他行数：视为内部异常。

### 5.2 指派条件更新

重新指派使用：

```text
WHERE id = ticketId
  AND assignee_user_id = previouslyReadAssigneeId
```

首次指派使用：

```text
WHERE id = ticketId
  AND assignee_user_id IS NULL
```

0 行返回 40905。重复指派在 UPDATE 前返回 40904。

### 5.3 能解决和不能解决的问题

条件更新可以防止两个请求基于同一旧值时静默覆盖，但当前没有：

- 通用 version 列；
- 悲观锁或 `SELECT ... FOR UPDATE`；
- 分布式锁；
- 调度队列；
- 完整的真实多线程 HTTP 竞态基准。

因此不应将当前实现描述为完整高并发调度系统。

## 6. 事务原子性

### 6.1 状态更新

`TicketServiceImpl.updateTicketStatus` 使用一个 `@Transactional`：

```text
状态条件 UPDATE
→ STATUS_CHANGED 日志 INSERT
→ 两次写入都成功才提交
```

### 6.2 指派

`TicketServiceImpl.assignTicket` 同样使用一个 `@Transactional`：

```text
处理人条件 UPDATE
→ ASSIGNEE_CHANGED 日志 INSERT
→ 两次写入都成功才提交
```

日志写入不是异步任务，也没有使用 `REQUIRES_NEW`。操作日志 Mapper 抛出运行时异常时，Spring 将当前事务回滚，工单保持旧值。

### 6.3 为什么先业务 UPDATE

先写日志再做条件更新，可能在 UPDATE 为 0 行时产生“实际没有成功”的虚假日志。当前顺序只记录已经通过业务规则和并发条件的操作，同时仍依赖事务在日志失败时撤销业务 UPDATE。

### 6.4 日志字段边界

- 状态日志：旧状态、新状态；
- 指派日志：旧处理人 ID、新处理人 ID；
- `operator_user_id` 始终来自 JWT `sub`；
- 指派操作者是 ADMIN，目标 AGENT 只是 after value；
- 当前不记录请求体、Token、密码、IP、User-Agent 或备注。

### 6.5 真实回滚证据

`TicketOperationAtomicityIntegrationTest` 使用真实 MySQL 外键故障注入：

- 不存在的正数 operator ID 使日志 INSERT 违反外键；
- Spring 抛出 `DataIntegrityViolationException`；
- 状态 UPDATE 回滚后仍为 OPEN；
- 指派 UPDATE 回滚后仍为 NULL；
- 两种场景日志数量均为 0。

测试使用外层 Spring 测试事务和嵌套保存点验证失败后的持久化状态，最终再回滚全部准备数据。

## 7. Redis 连接安全边界

Spring Boot 根据 `spring.data.redis` 自动配置 Lettuce、连接工厂和 `StringRedisTemplate`。本地 Compose Redis 无密码，但只将宿主端口绑定到 `127.0.0.1`；这只是开发环境暴露范围控制，不等于生产安全配置。

生产环境至少需要 Redis 认证、网络隔离、传输保护和 Secret 管理，并限制运维访问权限。Actuator 显示 Redis 健康为 UP，只能证明探测时连接和基础命令可用，不能证明 Lua 业务语义、TTL、数据保留或跨存储一致性一定正确。

## 8. 登录固定窗口限流

### 8.1 算法与原子性

登录接口按 IP 和规范化用户名维护两个固定窗口桶，默认分别是 60 秒 20 次和 60 秒 10 次。Controller 先检查 IP，再检查用户名。用户名维度被拒绝时，本次请求已经占用 IP 桶额度。

单 Key Lua 在首次请求时用一条 Redis 执行路径同时写入计数 1 和 TTL；后续请求只 `INCR`，正常情况下不刷新 TTL。这样避免客户端分步执行 `INCR` 与 `EXPIRE` 时在进程或网络故障下留下无过期计数。Lua 的原子性只覆盖脚本访问的单个 Redis Key，两个维度并不是一个共同原子事务。

固定窗口在相邻窗口边界可能允许短时间突发：上一个窗口末尾和下一个窗口开头都可使用完整额度。当前没有滑动窗口、令牌桶或真实压力测试。

### 8.2 身份、代理和隐私边界

- 用户名规范化为 trim 后用 `Locale.ROOT` 小写；当前 `AuthService` 登录查询只做小写，首尾空格的语义与限流桶存在已知差异，本阶段只记录、不修改；
- IP 来源仅为 `HttpServletRequest.getRemoteAddr()`；未配置可信代理名单，因此故意不信任 `X-Forwarded-For`；
- Key 只保存 SHA-256 摘要，不保存 IP 或用户名原文；无盐 SHA-256 对低熵输入仍可被枚举，不等于强匿名化；
- IP 桶和用户名桶限制请求频率，不记录失败次数，也不是账户锁定；
- JSON 或 Validation 失败在进入 Controller 前被拒绝，不消耗额度；成功与凭据失败登录都会计数。

### 8.3 fail-closed 取舍

Redis 连接、脚本执行或返回值异常时，限流器抛出异常且不会继续调用 `AuthService.login`，最终使用 HTTP 500 / 50000。fail-closed 优先避免 Redis 故障被利用来绕过登录保护，代价是 Redis 不可用时正常用户也无法登录。当前没有高可用降级、Sentinel 或 Cluster 验证。

## 9. 创建工单幂等

### 9.1 状态与输入边界

- 客户端 Key 与 JWT `sub` 共同形成用户作用域 Redis Key；
- 请求指纹覆盖 title、description、creatorName 和 priority，并以字段名及 UTF-8 长度编码避免简单分隔符拼接歧义；
- 随机 ownerToken 标识本次 `PROCESSING` 所有者，旧 owner 不能完成或释放新 owner 的记录；
- `PROCESSING` 保存 state、fingerprint、ownerToken；`SUCCEEDED` 保存 state、fingerprint、response；
- acquire、complete、release 分别由单 Key Lua 原子执行；
- 相同 Key 的正常重复请求、payload mismatch 和成功重放都不会刷新 TTL。

### 9.2 成功、失败与重放

业务 Service 抛出运行时异常时，当前 owner 尝试通过 release Lua 删除 `PROCESSING`，随后继续抛出原异常；释放失败作为 suppressed exception 保留。Service 正常返回后，协调器序列化并保存 `TicketResponse`，后续相同请求直接反序列化该快照，不重新查询数据库，也不重新执行 Service。

Service 已成功后若响应序列化或 Redis `complete` 失败，协调器不会释放 `PROCESSING`。此时 MySQL 工单可能已经提交，释放会让重试立即再次取得执行权并插入第二张工单。保留到 TTL 只能延后风险，不能提供永久防重。

## 10. Redis 与 MySQL 一致性

当前有三个明确窗口：

1. MySQL 提交后，进程在 Redis `complete` 前崩溃；
2. MySQL 提交后，`TicketResponse` 序列化失败；
3. MySQL 提交后，Redis 连接或 `complete` Lua 失败。

三个场景都可能表现为数据库已有工单而 Redis 仍为 `PROCESSING`；该 Key 到期或 Redis 数据丢失后，再次请求可能创建第二张工单。

`@Transactional` 当前管理 MySQL 数据源事务，不会自动让 Redis Lua 与 MySQL 共同提交或回滚。协调器故意不增加外层数据库事务，避免先把 Redis 标记成功、随后 MySQL 最终提交失败。详细方案比较和升级触发条件见 [P8 创建工单幂等架构决策](p8_create_ticket_idempotency_decision.md)。

## 11. 当前安全与可靠性边界

- HS256 使用单个共享密钥，没有密钥轮换或 `kid`；
- 没有 Refresh Token、Token 撤销、服务端登出或会话名单；
- `/api/auth/me` 使用 Token claims，不实时检查用户数据库状态；
- 没有用户停用、账户锁定或密码修改；
- 登录限流只覆盖 `/api/auth/login`，没有滑动窗口、令牌桶、账户锁定、验证码或其他接口限流；
- Redis 故障期间登录和启用幂等的工单创建均 fail-closed；
- 未建立可信反向代理边界，限流 IP 只使用 `remoteAddr`；
- Redis Key 摘要降低明文暴露，但不是匿名化；
- 创建工单幂等是有限窗口协调和响应重放，不是 exactly-once；
- 没有 MySQL 持久化幂等记录，Redis 数据丢失后不能长期防重或重放；
- 用户不存在与密码错误使用相同协议，但不保证严格认证耗时一致；
- 测试密钥只存在测试 classpath，不能用于其他环境；
- 操作日志在应用层只追加，但数据库没有禁止拥有数据库权限者 UPDATE/DELETE 的物理防篡改机制；
- 没有多租户隔离；
- `anyRequest().permitAll()` 使未来新增路径必须同步补充安全 matcher；
- 未执行 Redis Sentinel/Cluster、持久化恢复、MySQL/Redis 崩溃恢复、多实例部署、安全渗透、密钥托管或生产网络边界验证。
