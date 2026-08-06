# 测试证据

本文数据来自 2026-08-06 本轮实际执行的 `mvn test`、60 份 Surefire XML 和对应测试代码。每个 testsuite 只归入一个类别，不按文件名猜测实例数量。

## 1. 测试总览

| 项目 | 本轮实际值 |
| --- | --- |
| Java | 21.0.9；项目目标版本 21 |
| Maven | 3.9.11 |
| MySQL | Compose 镜像 `mysql:8.4.11`，容器健康 |
| Redis | Compose 镜像 `redis:7.4-alpine`，容器健康 |
| Spring Data Redis | 4.1.0 |
| Lettuce | 7.5.2.RELEASE |
| Flyway | 12.4.0 |
| Flyway schema version | 6 |
| Surefire testsuite | 60 |
| Build | `BUILD SUCCESS` |

实际全量结果：

```text
Tests run: 448
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

## 2. 不重复计数的测试分层

| 类别 | 测试类 | 测试实例 | Mock | Spring | MySQL | Redis |
| --- | ---: | ---: | --- | --- | --- | --- |
| DTO Validation | 6 | 44 | 否 | 否 | 否 | 否 |
| 核心、配置、Security 与 Service 单元 | 13 | 114 | 部分 | 否 | 否 | 否 |
| standalone MVC 与异常协议 | 3 | 79 | 是 | MVC 组件 | 否 | 否 |
| Mapper 与迁移集成 | 7 | 21 | 否 | 是 | 是 | 否 |
| Service / MySQL 集成 | 4 | 16 | 否 | 是 | 是 | 否 |
| 普通 HTTP 全链路 | 6 | 21 | 否 | 是 | 是 | 否 |
| P0～P7 Security HTTP 集成 | 5 | 45 | 否 | 是 | 是 | 否 |
| ApplicationContext 冒烟 | 1 | 1 | 否 | 是 | 是 | 是 |
| Redis 基础设施 | 1 | 5 | 否 | 是 | 是 | 是 |
| 登录限流单元、Redis 与 HTTP | 5 | 21 | 部分 | 部分 | HTTP 场景是 | 是 |
| 创建幂等单元、Redis 状态机与 HTTP | 9 | 81 | 部分 | 部分 | HTTP 场景是 | 是 |
| **合计** | **60** | **448** |  |  |  |  |

“连接 MySQL/Redis”按测试实际启动上下文和访问资源判断；ApplicationContext 冒烟会建立完整应用上下文。448 是测试实例总数，不是 448 个端到端场景。

## 3. 原有业务证据

### 3.1 Validation、MVC 与统一错误协议

DTO 测试直接使用 Jakarta Validator。standalone MockMvc 覆盖 JSON 绑定、Validation、Principal 参数、HTTP 状态、19 个应用错误码涉及的主要分支以及响应安全，不经过真实 Security Filter Chain。

### 3.2 MyBatis-Plus、MySQL 与 Flyway

真实 Mapper 和 Service 测试验证：

- 三张核心表的实体映射、自增 ID、数据库时间和外键；
- MyBatis-Plus 分页插件和动态条件；
- Flyway V1～V6 校验，schema version 为 6；
- 状态和旧处理人条件 UPDATE；
- 业务 UPDATE 与操作日志 INSERT 同事务提交或回滚。

### 3.3 JWT 与授权

真实 HTTP 测试覆盖 BCrypt 登录、JWT 签发、签名与必要 claims 验证、401/403、角色授权、USER 对象所有权以及真实 JWT `sub` 写入创建者和日志操作者。

### 3.4 事务故障注入

`TicketOperationAtomicityIntegrationTest` 使用不存在的正数 operator ID 触发 MySQL 外键异常，验证状态仍为 OPEN、处理人仍为 NULL、日志数量为 0。该测试证明 MySQL 本地事务原子性，不证明 Redis 与 MySQL 跨存储原子性。

## 4. P8 Redis 基础证据

`RedisInfrastructureIntegrationTest` 使用真实 `RedisConnectionFactory` 和 `StringRedisTemplate` 验证：

- `PING` 返回 `PONG`；
- String 写入与读取；
- 带 TTL 的 String 且剩余 TTL 为正；
- 连续 `INCR` 得到 1 和 2；
- 带 TTL 的 `SETNX` 首次成功、第二次失败；
- 每个测试只记录自己生成的精确 Key，结束后逐一删除并断言不存在。

它证明本轮环境的自动配置和基础命令可用，不证明 Sentinel、Cluster、持久化恢复或生产网络安全。

## 5. P8 登录限流证据

### 5.1 Key 与固定窗口

- Key 生成单元测试验证用户名 trim + `Locale.ROOT` 小写、IP/用户名命名空间隔离、原始标识不出现在 Key 中以及 64 位 SHA-256 十六进制摘要；
- 真实 Redis 测试验证 IP 第 4 次在测试阈值 3 后拒绝；
- 同一规范化用户名跨不同 IP 在测试阈值 2 后拒绝；
- 大小写和首尾空格变体进入同一用户名桶；
- 允许请求不会刷新固定窗口 TTL；
- 关闭配置时不生成 Key、不访问 Redis。

### 5.2 HTTP 行为

`LoginRateLimitHttpIntegrationTest` 使用真实 Spring、Redis、MySQL 和 JWT 链路验证：

- 不存在账户的失败登录也被计数，但不会泄露账户是否存在；
- 同一 `remoteAddr` 跨不同用户名受 IP 阈值约束；
- 成功登录同样计数并最终返回 429；
- 攻击者改变 `X-Forwarded-For` 不能绕过当前 `remoteAddr` 桶；
- Validation 失败不创建 IP 或用户名 Key；
- 429 返回 42900、正数 `Retry-After`，且不包含用户名、IP、计数、Redis/Lua 或堆栈。

`AuthControllerTest` 还验证先调用限流器再调用 `AuthService`；超限或 Redis 异常时不调用 `AuthService.login`，即 fail-closed。

## 6. P8 创建幂等证据

### 6.1 Key、配置与请求指纹

- 默认配置、TTL 和 8～128 字符范围经过 Validation 测试；
- 相同用户和客户端 Key 生成稳定 Redis Key，不同用户隔离；
- Key 原文和用户 ID 不直接进入 Redis Key；
- 空值、短 Key、超长 Key和控制字符被拒绝；
- 请求指纹覆盖四个创建字段，任一字段变化都会改变摘要；
- 使用字段名和 UTF-8 长度编码，验证分隔符和换行不会产生简单拼接歧义。

### 6.2 Redis 状态机

真实 Redis 测试覆盖：

- Key 不存在时首次 `ACQUIRED` 并写入 `PROCESSING`；
- 重复请求返回 `IN_PROGRESS`，不替换 owner、不刷新 TTL；
- 不同指纹返回 `PAYLOAD_MISMATCH`，不修改记录；
- 正确 owner 完成后转为 `SUCCEEDED`，删除 ownerToken 并保存响应；
- 相同指纹取回成功响应，不刷新 success TTL；
- 不同指纹不能重放成功响应；
- stale owner 无法完成；
- 正确 owner 可以释放失败记录并重新获取；
- 错误 owner 不能释放；
- 已成功记录不能被 release 删除。

### 6.3 协调器故障边界

Mockito 单元测试验证：

- `ACQUIRED` 才调用 `TicketService`，并用同一 fingerprint 和 owner 完成；
- `IN_PROGRESS`、`SUCCEEDED`、`PAYLOAD_MISMATCH` 的分支和 Service 交互；
- acquire Redis 异常时 fail-closed；
- Service 业务异常时尝试 release 并继续抛原异常；
- release 自身失败作为 suppressed exception，不覆盖原业务异常；
- Service 已成功后的响应序列化失败或 `markSucceeded` 失败不会 release。

最后两项是对 Redis/MySQL 一致性边界的单元验证。测试没有通过杀进程、断网、停止容器或破坏数据卷来模拟真实崩溃。

### 6.4 HTTP 成功重放

`CreateTicketIdempotencyHttpIntegrationTest` 使用真实 JWT、Spring Security、MySQL 和 Redis 验证：

- 首次创建返回 201，数据库写入一张工单且 Redis 为 `SUCCEEDED`；
- 相同 Key、相同请求重放相同响应，数据库仍只有一张工单；
- 相同 Key、不同请求返回 40907，不覆盖已有响应；
- `PROCESSING` 返回 40906 和正数 `Retry-After`，不创建工单；
- 不同用户使用相同客户端 Key 互相隔离；
- 缺失、空白或过短 Key 返回 40003；
- Validation 失败和未认证请求不会创建幂等 Key；
- 成功响应重放不刷新 success TTL；
- 测试事务回滚 MySQL 数据，`@AfterEach` 精确清理 Redis Key。

## 7. 未验证内容

以下内容没有测试证据，不能从 448 项测试推导：

- 真实高并发或性能压测、QPS 和延迟；
- 固定窗口边界突发的容量表现；
- 多实例实际部署与竞争测试；
- Redis Sentinel、Cluster、故障转移或持久化恢复；
- Redis 故障期间的高可用降级；
- MySQL 提交与 Redis `complete` 之间的真实进程崩溃、断网恢复；
- exactly-once、永久防重或 MySQL 持久化幂等；
- 大数据量分页、生产代理和网络配置；
- 备份恢复、容灾、安全渗透或代码覆盖率；
- AI 模型、RAG、Tool Calling 或 Agent 行为。

## 8. 复现命令与解读边界

```powershell
$env:REDIS_PORT = "6380"
docker compose ps
mvn test
```

- 单元测试不证明 Spring 代理、真实 SQL 或 Lua；
- Redis 集成测试不证明 MySQL 事务，MySQL 集成测试也不证明 Redis 状态；
- 单 Key Lua 原子性不能推导为 Redis 与 MySQL 共同事务；
- 测试事务回滚不会回收 MySQL 自增值；
- `BUILD SUCCESS` 只说明本轮环境和覆盖场景通过，不等于系统不存在未覆盖缺陷。
