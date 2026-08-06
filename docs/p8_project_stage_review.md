# P8 Redis、登录限流与创建幂等阶段复盘

本文用于复习和面试准备，只总结 P8 已实现事实。Redis 当前承担基础连接、登录固定窗口限流和创建工单有限窗口幂等，不是已经完成的通用缓存体系、高可用集群或跨存储事务方案。

## 1. P8 目标

P8 的目标是在已有认证、工单、权限和事务审计基础上，引入一个真实 Redis 运行环境，并选择两个边界清晰的入口验证它的价值：

1. 登录请求需要在调用 BCrypt 和数据库前限制频率；
2. 创建工单需要识别有限时间内的重复请求，并重放第一次成功结果。

阶段重点不是堆叠 Redis API，而是明确 Key、TTL、Lua 原子性、HTTP 协议、测试隔离，以及 Redis 与 MySQL 不能共同原子提交的事实。

## 2. P8-1 Redis 基础

### 2.1 自动配置和客户端

项目引入 Spring Data Redis starter，由 Spring Boot 4.1.0 根据 `spring.data.redis` 自动配置 `LettuceConnectionFactory` 和 `StringRedisTemplate`。实际依赖为 Spring Data Redis 4.1.0、Lettuce 7.5.2.RELEASE；Compose 使用 Redis 7.4 Alpine 镜像。

当前没有：

- 自定义通用 Object 序列化；
- Redisson；
- 手写连接池参数；
- Sentinel 或 Cluster；
- Redis 工单缓存或 JWT 缓存。

### 2.2 配置和生命周期

host、port、database、连接超时和命令超时来自外部配置。开发 Compose 将无密码 Redis 绑定到宿主 `127.0.0.1`，这不是生产认证方案。Redis 数据使用命名卷，但项目没有验证持久化恢复。

### 2.3 真实连接测试

基础集成测试覆盖 PING、String、TTL、INCR、带TTL的SETNX，并为每个测试生成唯一Key。清理阶段逐一删除精确Key并断言不存在，避免使用通配扫描或清空数据库。

## 3. P8-2 登录固定窗口限流

### 3.1 算法和调用顺序

```text
HTTP JSON与Validation
→ AuthController
→ remoteAddr IP桶
→ 规范化username桶
→ AuthService.login
→ BCrypt / MySQL
→ JWT
```

默认规则：

- IP：60秒最多20次；
- 规范化用户名：60秒最多10次；
- 先检查IP，再检查用户名；
- 成功和凭据失败都计数；
- JSON或Validation失败不计数。

### 3.2 Key和隐私

IP和用户名分别拥有不同命名空间。用户名先trim，再用`Locale.ROOT`转小写。原始IP和用户名通过SHA-256转换为64位摘要后进入Key，减少明文暴露。

摘要不是加密或匿名化。IP和常见用户名属于低熵输入，仍可能被枚举；生产安全仍依赖Redis认证、网络隔离和最小权限。

### 3.3 Lua和TTL

固定窗口Lua只操作一个Key：不存在时用带EX的SET同时建立计数和TTL；未达到阈值时INCR；达到阈值时返回当前正数TTL。正常请求不刷新窗口。

单Key Lua解决的是Redis内部多步骤原子性，避免INCR成功而EXPIRE未执行。IP桶和用户名桶不是共同事务，Redis与MySQL更不是共同事务。

### 3.4 HTTP和故障策略

超限返回HTTP 429、应用码42900和正数`Retry-After`。响应不返回IP、用户名、计数、Key或Lua细节。

当前只使用`remoteAddr`，因为尚未配置可信代理边界；不信任`X-Forwarded-For`。Redis故障采用fail-closed，不继续调用AuthService。它保护登录请求频率，但不是账户失败锁定、验证码、滑动窗口或令牌桶。

## 4. P8-3 创建工单有限窗口幂等

### 4.1 用户作用域Key

幂等开启时，`POST /api/tickets`要求trim后8～128字符且不含控制字符的`Idempotency-Key`。JWT `sub`和客户端Key通过明确长度编码后计算SHA-256，因此相同客户端Key在不同用户之间隔离。

### 4.2 请求指纹

请求指纹覆盖title、description、creatorName、priority。编码包含字段名、UTF-8字节长度和字节内容，避免使用冒号、竖线或换行等简单分隔符造成字段边界歧义。

### 4.3 ownerToken和状态机

每次尝试生成随机ownerToken。Redis Hash有两个状态：

```text
PROCESSING: state + fingerprint + ownerToken
SUCCEEDED:  state + fingerprint + response
```

三个Lua脚本分别负责：

- acquire：首次占用、处理中、成功重放和payload mismatch；
- complete：校验状态、指纹和owner，写入成功响应并删除ownerToken；
- release：仅正确owner能删除PROCESSING。

ownerToken防止Key过期并被新请求占用后，旧处理者错误完成或释放新记录。

### 4.4 响应重放和HTTP

- 首次成功：HTTP 201 / code 0；
- 相同Key和请求：反序列化第一次`TicketResponse`，仍返回201；
- PROCESSING：HTTP 409 / 40906 / `Retry-After`；
- 相同Key不同请求：HTTP 409 / 40907；
- Key缺失或非法：HTTP 400 / 40003。

成功重放不调用TicketService，也不重新查询已经可能发生变化的工单。Redis保存TicketResponse JSON，不保存完整HTTP响应。重复请求不会刷新TTL。

### 4.5 业务失败和跨存储窗口

Service抛出业务异常时，当前owner尝试release，原业务异常继续传播。Service正常返回后说明MySQL事务已经提交；此后响应序列化或Redis complete失败时不能release，否则客户端可立即再次创建。

当前有三个一致性窗口：

1. MySQL提交后进程崩溃；
2. MySQL提交后响应序列化失败；
3. MySQL提交后Redis complete失败。

因此当前方案只保证Redis状态TTL内的协调、冲突识别和成功响应重放，不保证exactly-once、永久防重或Redis/MySQL强一致。详细取舍见 [创建工单幂等架构决策](p8_create_ticket_idempotency_decision.md)。

## 5. 五个最重要的设计

1. **Lua保证Redis内部原子操作。** 首次计数与TTL、幂等状态校验与转换不会暴露客户端分步中间状态。
2. **限流Key不保存明文标识。** IP和用户名使用不同命名空间及SHA-256摘要，但不把摘要夸大为匿名化。
3. **幂等Key按认证用户隔离。** JWT `sub`参与Key作用域，客户端不能通过请求体伪造用户身份。
4. **Service成功后Redis失败不释放PROCESSING。** 数据库可能已提交，释放会主动扩大立即重复写入风险。
5. **不把纯Redis方案宣传为exactly-once。** TTL、Redis数据丢失和MySQL提交后的故障窗口均被明确记录。

## 6. 3～5分钟P8讲解结构

### 第一分钟：为什么引入Redis

说明不是为了技术堆叠，而是登录在昂贵认证前需要频率保护，创建工单需要有限窗口重复请求协调。

### 第二分钟：登录限流

说明双维度、先IP后用户名、固定窗口Lua、TTL不刷新、429与Retry-After、remoteAddr和fail-closed。

### 第三分钟：创建幂等

说明JWT sub用户作用域、8～128字符Key、请求指纹、ownerToken、PROCESSING/SUCCEEDED和响应重放。

### 第四分钟：一致性边界

说明Service的MySQL事务先提交，随后Redis complete；列举三个失败窗口，解释为什么成功后不能release，以及为什么不是exactly-once。

### 第五分钟：测试和演进

说明真实Redis状态机、真实JWT/MySQL/Redis HTTP重放、数据库只有一张工单和故障单元测试；最后主动说明未做多实例、崩溃恢复和性能压测。

## 7. 当前不足

- 固定窗口存在边界突发；
- 登录限流与AuthService对用户名首尾空格的规范化存在差异；
- 未建立可信代理边界；
- Redis故障采用fail-closed，尚无高可用降级；
- 无Sentinel、Cluster、多实例和持久化恢复测试；
- 创建幂等没有MySQL持久化记录或唯一约束；
- MySQL提交后到Redis完成前仍有重复创建窗口；
- Redis响应快照存在DTO版本兼容问题；
- 没有真实并发压测、QPS或延迟数据；
- Redis缓存和AI能力均未实现。

## 8. 下一阶段建议

以下只是规划，不代表已经实现：

1. 先收紧未来接口的默认安全规则，建立可信代理IP解析；
2. 在有真实读热点后设计Redis缓存和缓存一致性，而不是盲目缓存；
3. 严格防重或长期重放需求出现时，引入独立MySQL幂等记录表并与工单创建同事务；
4. 增加用户停用、Refresh Token和Token撤销；
5. 创建工单出现消息或外部副作用后再评估Outbox；
6. 最后接入AI分类、优先级建议和回复草稿，并保留人工确认与审计。
