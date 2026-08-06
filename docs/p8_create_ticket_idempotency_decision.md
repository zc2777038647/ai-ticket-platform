# P8 创建工单幂等架构决策记录

## 1. 决策信息

- 决策编号：P8-3-3
- 日期：2026-08-06
- 状态：已接受
- 决策范围：`POST /api/tickets` 的创建工单幂等
- 当前实现：纯 Redis 有限窗口幂等
- 当前未实现：MySQL 持久化幂等、Outbox、MQ、分布式事务

## 2. 背景

客户端可能因为网络超时、响应丢失或主动重试，多次提交语义相同的创建工单请求。
如果每次请求都直接调用创建 Service，就可能插入多张内容相同的工单。

项目当前希望学习并验证以下能力：

- 按认证用户隔离幂等作用域；
- 使用请求指纹判断同一个 Key 是否对应同一个请求；
- 使用 Redis Lua 原子协调并发请求；
- 在有限 TTL 内重放第一次成功创建的响应；
- 清晰处理业务失败、Redis 失败和并发冲突；
- 准确说明 Redis 与 MySQL 之间无法消除的一致性窗口。

这是 Java 后端学习项目中的有限窗口幂等，不是支付级或订单核心系统的严格防重复方案。

## 3. 当前实现事实

### 3.1 完整调用顺序

当前开启幂等后的实际调用顺序是：

```text
客户端携带 Idempotency-Key
→ SecurityFilterChain 验证 JWT
→ TicketController 从 JWT sub 取得 creatorUserId
→ CreateTicketIdempotencyCoordinator
→ 使用 CreateTicketRequest 计算请求指纹
→ 生成本次处理尝试的随机 ownerToken
→ Redis Lua acquire
→ TicketService.createTicket
→ MySQL 事务提交并返回 TicketResponse
→ ObjectMapper 序列化 TicketResponse
→ Redis Lua markSucceeded
→ HTTP 201
```

协调器没有 `@Transactional`。
当前 Controller 调用协调器时不存在外层数据库事务，
因此 Spring 代理上的 `TicketService.createTicket` 事务在 Service 调用正常返回前完成提交。

### 3.2 Redis Key 作用域

Redis Key 由以下输入生成：

```text
creatorUserId
+ trim 后的客户端 Idempotency-Key
→ 明确的长度编码
→ SHA-256
→ <keyPrefix>:<64位十六进制摘要>
```

因此：

- 相同用户和相同客户端 Key 对应同一个 Redis Key；
- 不同用户即使使用相同客户端 Key，也对应不同 Redis Key；
- Redis Key 不直接暴露用户 ID 或客户端 Key 原文。

SHA-256 在这里用于减少 Redis 中的明文标识暴露，
不是加密、数字签名或强匿名机制。

### 3.3 请求指纹

请求指纹严格覆盖：

- `title`；
- `description`；
- `creatorName`；
- `priority.name()`。

字段按“字段名、UTF-8 字节长度、UTF-8 字节内容”编码后计算 SHA-256。
该结构避免冒号、竖线、换行等字符造成简单拼接歧义。

### 3.4 Redis 状态

当前只有两个持久状态：

```text
PROCESSING
SUCCEEDED
```

不存在单独的 `FAILED` 或 `EXPIRED` 状态。
业务失败时由当前 owner 删除 PROCESSING；TTL 到期表现为 Redis Key 不存在。

PROCESSING Hash 保存：

```text
state
fingerprint
ownerToken
```

SUCCEEDED Hash 保存：

```text
state
fingerprint
response
```

转为 SUCCEEDED 时删除 `ownerToken`。

### 3.5 acquire 分支

Lua acquire 是单 Redis Key 原子操作：

- Key 不存在：写入 PROCESSING、fingerprint、ownerToken 和 processing TTL，返回 `ACQUIRED`；
- fingerprint 不同：返回 `PAYLOAD_MISMATCH`，不修改记录或 TTL；
- 已是 PROCESSING：返回 `IN_PROGRESS` 和当前正数 TTL，不替换 owner；
- 已是 SUCCEEDED：返回 `SUCCEEDED` 和原响应字符串，不刷新 TTL；
- 记录损坏或状态未知：由 Java 层视为非法结果，不伪造成功。

### 3.6 重复请求行为

```text
PROCESSING
→ HTTP 409
→ code 40906
→ Retry-After: 当前正数TTL
```

```text
SUCCEEDED 且指纹相同
→ 反序列化原 TicketResponse
→ HTTP 201
→ 不调用 TicketService
→ 不重新查询数据库
```

```text
相同用户、相同Key、指纹不同
→ HTTP 409
→ code 40907
→ 不调用 TicketService
```

### 3.7 业务失败

如果 `TicketService.createTicket` 抛出运行时异常：

```text
当前 owner 调用 releaseProcessing
→ Lua 校验 state、fingerprint 和 ownerToken
→ 校验全部通过才删除 PROCESSING
→ 原业务异常继续抛出
```

release 返回 false 时仍保留原业务异常。
release 自身抛异常时，释放异常作为 suppressed exception 附加，原业务异常仍是主异常。

### 3.8 数据库成功后的失败

如果 TicketService 已正常返回，说明当前生产 HTTP 调用链中的数据库事务已经提交。
此后发生以下错误时不会释放 PROCESSING：

- TicketResponse 序列化失败；
- Redis 连接失败；
- complete Lua 失败；
- complete 检测到 owner、指纹或状态不一致。

客户端最终收到统一的服务器内部错误，Redis PROCESSING 保留至 TTL。

## 4. 当前方案能够保证什么

当前方案保证的是 Redis 有效状态窗口内的请求协调、冲突识别和成功响应重放：

1. Redis Key 存在期间，相同用户和相同客户端 Key 只有一个 PROCESSING owner；
2. 正在处理的重复请求不会并发调用 TicketService；
3. 相同 Key 对应不同请求体时能够通过 fingerprint 识别；
4. SUCCEEDED 存在期间能够重放第一次保存的 TicketResponse；
5. 不同用户使用相同客户端 Key 时互不影响；
6. stale owner 不能完成或释放当前新 owner 的状态；
7. 正常的重复 acquire 和成功响应重放不会刷新 TTL；
8. Redis acquire 失败时采用 fail-closed，不继续创建工单；
9. Service 失败时当前 owner 可以安全释放 PROCESSING；
10. acquire、complete、release 各自在单 Redis Key Lua 中原子执行。

这些保证不能扩展解释为 Redis 与 MySQL 的跨存储原子保证。

## 5. 当前方案没有保证什么

当前实现没有：

- exactly-once；
- Redis 与 MySQL 分布式事务；
- 数据库持久化幂等记录；
- 数据库唯一约束层面的最终防重复；
- Redis 数据丢失后的成功响应重放能力；
- 进程崩溃后的自动恢复；
- 无限期成功响应重放；
- Outbox、MQ 或分布式工作流；
- 对外部不可逆副作用的事务协调。

Redis Key 丢失或 TTL 到期后，数据库当前没有信息可以证明某个 Idempotency-Key 已使用过。

## 6. Redis 与 MySQL 一致性窗口

### 6.1 场景一：MySQL 提交后进程崩溃

```text
Redis = PROCESSING
→ MySQL 创建工单并提交
→ 进程在 markSucceeded 前崩溃
→ Redis 未保存成功响应
→ PROCESSING 最终过期
→ 客户端再次请求
→ 可能再次创建工单
```

数据库没有幂等唯一约束，因此无法在最后一步持久阻止第二次插入。

### 6.2 场景二：响应序列化失败

```text
MySQL 已提交
→ TicketResponse 序列化失败
→ HTTP 返回 50000
→ PROCESSING 保留至 TTL
→ TTL 到期后重试可能再次创建
```

虽然当前 TicketResponse 结构简单，但序列化仍是数据库提交后的独立步骤，理论上可能失败。

### 6.3 场景三：Redis complete 失败

```text
MySQL 已提交
→ Redis 连接失败或 complete Lua 失败
→ HTTP 返回 50000
→ Redis 可能继续保持 PROCESSING
→ TTL 到期后重试可能再次创建
```

如果故障导致 Redis 数据本身丢失，重复创建窗口还可能提前出现。

### 6.4 为什么此时不能释放 PROCESSING

Service 成功返回后，工单可能已经持久化。
如果序列化或 markSucceeded 失败时删除 PROCESSING，客户端可以立即重新 acquire 并再次创建工单。

保留 PROCESSING 不能彻底消除重复创建，但可以把窗口从“立即发生”推迟到 processing TTL 到期后，
为短暂故障恢复和人工排查保留时间。

## 7. 方案对比

| 维度 | 方案A：纯Redis有限窗口 | 方案B：tickets表增加幂等字段 | 方案C：独立MySQL幂等表 | 方案D：Outbox/事务消息/工作流 |
|---|---|---|---|---|
| 实现复杂度 | 低到中 | 中 | 中到高 | 高 |
| 正常请求性能 | Redis Lua 快，之后写MySQL | 直接依赖MySQL索引与事务 | 需要幂等记录和工单同事务 | 通常包含异步组件和消息处理 |
| 并发协调 | Redis单Key Lua | MySQL唯一约束 | MySQL唯一约束，可叠加Redis | 依赖消息/工作流状态机 |
| 成功响应重放 | TTL内保存响应 | 不自然，需要额外字段或重建响应 | 可持久保存原始响应 | 可保存流程结果，但设计更重 |
| Redis丢失影响 | 状态和重放能力丢失 | 数据库唯一约束仍可防重复 | MySQL作为持久兜底 | 由持久消息和工作流恢复 |
| MySQL提交后崩溃窗口 | 存在 | 唯一约束可阻止相同Key再次插入 | 可通过同库事务显著收敛 | 可恢复外部副作用，但仍需幂等消费者 |
| 元数据归属 | Redis临时数据 | 污染tickets业务表 | 与业务表分离 | 独立消息/流程模型 |
| 适用范围 | 可容忍有限窗口的普通创建接口 | 幂等与单一业务行强绑定 | 需要持久防重复和长期重放 | 涉及多服务或不可恢复外部副作用 |

## 8. 方案A：纯 Redis 有限窗口

这是当前采用的方案。

### 优点

- Lua 在单 Key 内完成原子状态转换；
- 正常路径延迟低；
- PROCESSING 能协调并发请求；
- SUCCEEDED 能在 TTL 内直接重放原响应；
- 不修改现有 tickets 表；
- 实现和测试边界适合当前学习项目。

### 局限

- Redis 和 MySQL 不是同一事务；
- Redis 重启、淘汰、误删或数据丢失会失去幂等状态；
- TTL 到期后不再阻止相同 Key；
- MySQL 提交后、Redis 成功标记前存在重复创建窗口；
- 不能提供长期审计和永久响应重放。

### 适用范围

适用于重复创建影响可控、重试窗口有限、允许清晰披露边界的普通业务接口。

共享 Redis 可以协调多个应用实例，
因此“多实例”并不意味着当前方案必然失效；
但实例和服务数量增加后，持久化恢复、故障排查与一致性要求通常会提高。

## 9. 方案B：在 tickets 表增加幂等字段和唯一索引

概念上可以增加：

```text
creator_user_id
idempotency_key_hash
request_fingerprint
```

并建立用户作用域唯一约束。

### 优点

- MySQL 可以持久阻止同一用户和 Key 重复插入；
- 唯一约束不依赖应用进程中的“先查后写”；
- Redis 丢失后仍有数据库防重复兜底；
- 幂等占用可以和工单插入位于同一个数据库事务中。

### 缺点和未决问题

- 幂等技术字段进入工单业务表，职责耦合；
- 历史无幂等工单需要兼容；
- nullable 唯一索引语义必须结合 MySQL 行为谨慎设计；
- 只加唯一索引不能自然重放第一次 HTTP 响应；
- 工单状态以后可能改变，查询当前工单不等于重放第一次创建响应；
- 唯一键冲突后的事务状态、异常转换和已有记录查询需要专门设计；
- Key 和响应保留策略与工单生命周期被绑定。

因此该方案不是“加三个字段和一个索引”就完整结束。

## 10. 方案C：独立 MySQL 幂等记录表

概念字段可以包括：

```text
creator_user_id
idempotency_key_hash
request_fingerprint
status
ticket_id
response_payload
created_at
expires_at
```

这只是架构概念，本项目当前没有创建该表或对应迁移。

### 优点

- 幂等元数据与 tickets 业务字段分离；
- 用户作用域唯一约束能够持久占用 Key；
- 可以保存第一次成功响应的快照；
- Redis 可以退化为快速 PROCESSING 协调层和响应缓存；
- Redis 数据丢失后仍可从 MySQL 判断 Key 是否使用过；
- 保留策略、审计和清理可以独立设计。

### 复杂点

- 如何在高并发下原子占用唯一 Key；
- 并发插入冲突如何避免让需要继续查询的事务进入 rollback-only；
- PROCESSING 幂等记录和工单创建如何纳入同一 MySQL 事务；
- 业务失败后记录删除、失败标记或超时回收如何选择；
- 成功记录保存多久以及如何清理；
- response JSON 的 DTO 版本兼容如何处理；
- Redis 与 MySQL 双层状态谁是事实来源；
- Redis 缓存回填、失效和并发竞争如何处理；
- 表数据和唯一索引持续增长如何治理。

与方案B相比，方案C职责更清晰、扩展性更好，但实现和运维复杂度也更高。

## 11. 方案D：事务消息、Outbox 或工作流

该方案适合创建工单之后还会发生以下行为：

- 向外部系统发送消息；
- 发送通知；
- 调用第三方服务；
- 触发计费、库存或其他不可逆副作用；
- 跨多个服务完成最终一致的业务流程。

Outbox 可以让“写业务数据”和“记录待发送事件”处于同一数据库事务，
再由异步发布器可靠投递；消费者仍需实现自身幂等。

当前项目是单体应用，创建工单目前主要是一次 MySQL 插入，
没有必要仅为了这个接口引入 MQ 或分布式工作流。

## 12. 为什么不选择 Redis 与 MySQL 强行双写

以下两种顺序都存在崩溃窗口：

```text
先写Redis
→ 进程崩溃
→ MySQL未写
```

```text
先写MySQL
→ 进程崩溃
→ Redis未写
```

调整顺序只能改变失败表现，不能让两个独立存储自动原子提交。

Spring 本地 `@Transactional` 当前管理 MySQL 数据源事务，
不会自动把 `StringRedisTemplate` 的命令纳入同一个 MySQL 原子事务，
也不能保证其中一个存储失败时另一个存储自动回滚。

即使额外使用 Redis 事务，它也不能与 MySQL 本地事务自动组成统一原子事务。

## 13. 当前正式决策

现阶段保留纯 Redis 有限窗口幂等，不增加 MySQL 持久化幂等表。

### 决策理由

1. 当前目标是 Java 后端实习学习项目，不是支付或订单核心系统；
2. 已覆盖 Redis、Lua、请求指纹、ownerToken、用户作用域和响应重放；
3. 当前能力和一致性窗口已有生产代码、单元测试与真实 Redis/MySQL HTTP 测试支撑；
4. 引入持久化层会显著扩大数据库事务、并发占用、记录清理和版本兼容复杂度；
5. 当前没有长期重放或严格防重复的明确业务需求；
6. 在项目和面试中如实说明有限窗口，比虚构 exactly-once 更可靠；
7. 当前方案足以展示架构边界判断，而不是单纯堆叠组件。

## 14. 未来升级触发条件

出现以下任一明确需求时，应优先评估独立 MySQL 幂等记录表：

- 重复创建会造成严重业务损失；
- 必须跨 Redis 重启、数据丢失或缓存淘汰继续防重复；
- 必须长期重放第一次成功响应；
- 客户端可能在 success TTL 之后继续重试；
- 创建工单会触发计费、库存或其他不可逆副作用；
- 系统进入多实例或多服务部署，且持久恢复与审计要求提高；
- 出现明确审计、合规或问题追踪要求。

共享 Redis 本身能够协调多实例；
升级触发点是多实例场景下更高的持久化、故障恢复和审计要求，
而不是“多实例会让 Redis 协调必然失效”。

## 15. 推荐演进顺序

### 第一步：独立 MySQL 幂等记录表

建立用户作用域唯一约束，明确状态、保留期限和清理策略。

### 第二步：与工单创建使用同一 MySQL 事务

幂等占用、工单创建、ticket_id 和成功状态更新在同一数据库事务中完成，
让 MySQL 成为持久事实来源。

### 第三步：Redis 退化为快速协调和缓存

Redis 继续快速处理 PROCESSING 竞争和成功响应缓存，
但 Redis 丢失后由 MySQL 幂等记录兜底。

### 第四步：必要时引入 Outbox

只有创建工单开始触发消息、通知或第三方副作用时，
再通过 Outbox 和幂等消费者处理跨系统最终一致性。

该演进顺序不代表当前已经实现任何数据库迁移或生产代码。

## 16. 测试证据

当前测试体系覆盖：

- Key 作用域、规范化和隐私边界；
- 请求指纹稳定性和防拼接歧义；
- Store 返回值和异常边界；
- 真实 Redis acquire、complete、release 与 TTL；
- 协调器所有状态分支；
- Service 失败后的释放和 suppressed exception；
- Service 成功后 complete 失败不释放；
- 真实 JWT、HTTP、MySQL 与 Redis 完整链路；
- 相同请求成功重放且数据库只有一张工单；
- 相同 Key 不同请求、PROCESSING 和用户隔离；
- Redis Key 精确清理和数据库事务回滚。

本次决策记录创建前实际执行：

```text
mvn test
Tests run: 448
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

测试证明当前代码符合已声明的有限窗口语义，
不能据此证明 Redis 与 MySQL exactly-once。

## 17. 面试问题与参考回答

### 17.1 为什么使用 Redis 实现幂等？

当前接口需要低延迟地协调重复请求，并在短期内重放第一次成功响应。
Redis 单 Key Lua 可以原子完成 owner 占用、指纹比较和状态转换，
适合当前可容忍有限窗口的创建接口。

### 17.2 Redis 幂等是否能保证 exactly-once？

不能。当前 Redis 状态和 MySQL 工单不在同一事务中。
MySQL 提交后到 Redis markSucceeded 之间仍有崩溃窗口，
TTL 到期或 Redis 数据丢失后仍可能再次创建。

### 17.3 为什么 Service 成功后 Redis 失败不能释放 PROCESSING？

当前 Service 正常返回时数据库事务已经提交。
如果此时删除 PROCESSING，客户端可以立即 acquire 并再插入一张工单。
保留到 TTL 虽不能彻底消除风险，但不会把重复窗口提前到立即发生。

### 17.4 为什么协调器不能开启外层数据库事务？

如果协调器开启外层事务，Service 会加入该事务，
协调器可能先把 Redis 标记为 SUCCEEDED，方法结束时 MySQL 才真正提交。
一旦最终提交失败，就会出现 Redis 保存成功响应但数据库没有工单的更危险状态。

### 17.5 数据库唯一约束和 Redis 分别解决什么问题？

数据库唯一约束提供持久、最终的重复插入兜底；
Redis 擅长低延迟并发协调、PROCESSING 提示和成功响应缓存。
两者可以互补，但不会因为同时存在就自动成为原子事务。

### 17.6 为什么只在 tickets 表加唯一索引仍不完整？

唯一索引能阻止重复插入，但不能自动保存第一次 HTTP 响应，
也没有解决历史 nullable 数据、冲突后的事务处理和响应版本问题。
工单以后状态变化时，查询当前行也不等于重放第一次创建响应。

### 17.7 什么情况下会增加独立幂等记录表？

当重复创建损失严重、必须跨 Redis 故障防重复、需要长期重放、
重试时间超过 TTL，或创建开始触发不可逆副作用时，
会优先采用独立 MySQL 幂等记录表。

### 17.8 幂等和分布式锁有什么区别？

锁主要限制同一时刻谁能执行；幂等还要定义重复请求在完成后返回什么结果。
当前实现不仅通过 owner 协调 PROCESSING，还比较请求指纹并缓存成功响应，
因此不是简单的分布式锁。

### 17.9 ownerToken 解决什么问题？

ownerToken 证明谁取得了当前 PROCESSING。
Key 过期后可能由新请求重新占用，旧请求的 stale owner 不能完成或删除新 owner 的记录，
避免误释放和错误覆盖。

### 17.10 TTL 过长或过短分别有什么影响？

processing TTL 过短会让仍在执行的请求提前失去占用，增加并发重复创建风险；
过长会让崩溃后的客户端等待更久。
success TTL 过短会缩短响应重放和冲突识别窗口；
过长会增加 Redis 内存占用，并延长响应 JSON 的版本兼容周期。

## 18. 结论

当前正式定位是：

> Redis 有效状态窗口内的请求协调、冲突识别和成功响应重放。

它不是 exactly-once，不是 Redis/MySQL 分布式事务，
也不是已经完成的 MySQL 持久化幂等系统。

在当前项目需求下继续保留方案A；
当持久防重复、长期重放、不可逆副作用或审计需求出现时，
按“独立 MySQL 幂等表 → 同库事务 → Redis 缓存协调 → 必要时 Outbox”的顺序演进。
