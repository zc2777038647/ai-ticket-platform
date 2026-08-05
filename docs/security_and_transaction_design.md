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

## 7. 当前安全边界

- HS256 使用单个共享密钥，没有密钥轮换或 `kid`；
- 没有 Refresh Token、Token 撤销、服务端登出或会话名单；
- `/api/auth/me` 使用 Token claims，不实时检查用户数据库状态；
- 没有用户停用、账户锁定或密码修改；
- 没有登录限流、验证码或统一接口限流；
- 用户不存在与密码错误使用相同协议，但不保证严格认证耗时一致；
- 测试密钥只存在测试 classpath，不能用于其他环境；
- 操作日志在应用层只追加，但数据库没有禁止拥有数据库权限者 UPDATE/DELETE 的物理防篡改机制；
- 没有多租户隔离；
- `anyRequest().permitAll()` 使未来新增路径必须同步补充安全 matcher；
- 未执行安全渗透测试、密钥托管验证或生产网络边界验证。
