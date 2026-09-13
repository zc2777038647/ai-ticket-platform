# AI Ticket Platform

基于 Spring Boot 的 AI 工单平台后端。当前版本完成了工单基础业务、认证授权、身份关联、指派与操作日志，并以 Redis Lua 为登录和创建链路增加有限范围的流量与重复请求保护；AI 能力由同级独立的 FastAPI 服务提供，Java 保持可信业务边界。

## 目录

- [项目能力](#项目能力)
- [技术栈](#技术栈)
- [架构概览](#架构概览)
- [角色权限](#角色权限)
- [核心业务规则](#核心业务规则)
- [本地运行](#本地运行)
- [API 快速索引](#api-快速索引)
- [测试概览](#测试概览)
- [详细文档](#详细文档)
- [项目边界与-roadmap](#项目边界与-roadmap)

## 项目能力

- 用户注册，用户名统一按小写保存；
- BCrypt 密码哈希，数据库不保存明文密码；
- 用户登录并签发 HS256 JWT Access Token；
- Spring Security OAuth2 Resource Server Bearer 认证；
- USER、AGENT、ADMIN 请求级授权；
- 创建工单并将可信 JWT `sub` 绑定到 `creator_user_id`；
- 我的工单条件分页与 AGENT、ADMIN 全局条件分页；
- USER 工单详情对象级所有权校验；
- `OPEN → IN_PROGRESS → RESOLVED → CLOSED` 状态流转；
- ADMIN 首次指派或重新指派工单给 AGENT；
- 状态和处理人旧值条件更新，识别并发冲突；
- 状态变更、处理人变更追加式操作日志；
- 业务 UPDATE 与日志 INSERT 同事务原子提交；
- Spring Boot 自动配置 Lettuce、Redis 连接工厂和 `StringRedisTemplate`；
- 登录接口采用 Redis Lua 固定窗口限流，分别约束请求 IP 和规范化用户名；
- 限流计数与 TTL 在单 Key Lua 中原子处理，拒绝时返回 HTTP 429 和 `Retry-After`；
- 创建工单支持 `Idempotency-Key`，Redis Key 按 JWT `sub` 用户作用域隔离；
- 请求指纹识别同 Key 不同请求，`PROCESSING` / `SUCCEEDED` 状态机支持处理中冲突与成功 `TicketResponse` 重放；
- `ownerToken` 防止过期处理者完成或释放新的处理记录；
- Flyway V1～V6 数据库演进；
- Validation、Mockito、MockMvc、真实 MySQL 与真实 Redis 分层测试。

## 技术栈

| 技术 | 当前版本或来源 |
| --- | --- |
| Java | 21；本轮测试运行时为 21.0.9 |
| Spring Boot | 4.1.0 |
| Spring MVC | Spring Framework 7.0.8 |
| Spring Security / OAuth2 Resource Server / Crypto | 7.1.0 |
| Spring Data Redis | 4.1.0 |
| Lettuce | 7.5.2.RELEASE |
| Redis | Compose 镜像 `redis:7.4-alpine` |
| Redis Lua | 4 个脚本：1 个登录限流、3 个创建幂等状态转换 |
| MyBatis-Plus | 3.5.17 |
| MySQL | Compose 镜像 `mysql:8.4.11` |
| MySQL Connector/J | 9.7.0 |
| Flyway | 12.4.0；schema version 6 |
| Jakarta Validation / Hibernate Validator | 3.1.1 / 9.1.0.Final |
| JUnit Jupiter | 6.0.3 |
| Mockito | 5.23.0 |
| Maven | 项目使用 Maven；本轮系统 Maven 为 3.9.11 |
| Docker Compose | 仓库提供 `compose.yaml`；本轮环境为 v5.1.4 |

版本来自 `pom.xml`、Maven dependency tree、Compose 镜像和本轮执行环境，不将本机工具版本视为仓库锁定版本。

## 架构概览

```mermaid
flowchart LR
    Client["HTTP Client"] --> Security["Spring Security Filter Chain"]
    Security --> Controller["Controller"]
    Controller --> Service["Service / Transaction"]
    Service --> Mapper["MyBatis-Plus Mapper"]
    Mapper --> MySQL[("MySQL")]
    JWT["JWT Encoder / Decoder"] --> Security
    Flyway["Flyway V1-V6"] --> MySQL
    Service --> Log["Operation Log"]
    Log --> Mapper
    Auth["AuthController.login"] --> RateLimiter["RedisLoginRateLimiter"]
    RateLimiter --> RateLua["固定窗口 Lua"]
    RateLua --> Redis[("Redis")]
    RateLimiter --> AuthService["AuthService / MySQL / JWT"]
    Create["TicketController.create"] --> Coordinator["Idempotency Coordinator"]
    Coordinator --> Store["Redis Store / Lua"]
    Store --> Redis
    Coordinator --> TicketService["TicketService / MySQL Transaction"]
    TicketService --> MySQL
    TicketService --> Coordinator
    Coordinator --> Store
    TicketService --> AiClient["AI Service Client"]
    AiClient --> Python["FastAPI AI Service"]
    Python --> Knowledge["Structured Provider / RAG / Agent / MCP"]
```

生产接口只暴露 DTO，不直接返回 Entity。Controller 负责 HTTP 边界，Service 负责业务规则、对象所有权和 MySQL 事务，Mapper 负责 MyBatis-Plus 数据访问。AI Service 只产生建议和草稿，不拥有用户授权或工单写权限。Redis Lua 的单 Key 原子性不等于 Redis 与 MySQL 处于同一事务。

## 角色权限

| 能力 | USER | AGENT | ADMIN |
| --- | ---: | ---: | ---: |
| 创建工单 | 是 | 是 | 是 |
| 查询我的工单 | 是 | 是 | 是 |
| 查看自己的工单详情 | 是 | 是 | 是 |
| 查看任意工单详情 | 否 | 是 | 是 |
| 全局分页 | 否 | 是 | 是 |
| 更新状态 | 否 | 是 | 是 |
| 指派处理人 | 否 | 否 | 是 |

补充边界：

- USER 查询他人工单返回 404，而不是 403，避免暴露资源是否存在；
- AGENT、ADMIN 调用 `/api/tickets/mine` 时仍只返回自己创建的工单；
- `creator_user_id IS NULL` 的历史工单不属于任何 USER，只允许 AGENT、ADMIN 通过全局权限查看；
- `creatorName` 是展示文本，不参与授权判断；授权依据是 JWT `sub` 与 `creator_user_id`。

## 核心业务规则

### 状态流转

```text
OPEN → IN_PROGRESS → RESOLVED → CLOSED
```

不允许跳跃、反向、相同状态更新，`CLOSED` 没有后继状态。

### 指派

- 只有 ADMIN 可以指派；
- 目标用户必须存在且角色为 AGENT；
- 支持首次指派和从一个 AGENT 重新指派给另一个 AGENT；
- 重复指派给当前处理人返回 HTTP 409 / 应用码 40904；
- 当前不支持取消指派，也不支持 AGENT 主动领取。

### 并发与事务

状态更新通过旧状态条件防止覆盖：

```sql
WHERE id = ? AND status = ?
```

指派通过旧处理人条件防止覆盖：

```sql
WHERE id = ? AND assignee_user_id = ?
```

首次指派使用 `assignee_user_id IS NULL`。项目没有 version 字段、悲观锁或分布式锁。

状态或指派条件 UPDATE 成功后，同一个 `@Transactional` 方法同步 INSERT 操作日志；任一写入失败都会回滚本次业务操作。

### 登录限流

- IP 维度：固定 60 秒窗口最多 20 次；
- 规范化用户名维度：固定 60 秒窗口最多 10 次；
- Controller 先检查 IP，再检查用户名；用户名被拒绝前已经消耗本次 IP 额度；
- 成功和失败登录都计数；JSON 解析或 Validation 在进入 Controller 前失败，因此不计数；
- 当前是请求频率限制，不是账户失败锁定、滑动窗口、令牌桶或验证码；
- IP 只取 Servlet `remoteAddr`。尚未建立可信代理边界，所以不信任 `X-Forwarded-For`；
- Redis 故障采用 fail-closed：不继续调用登录 Service，由统一异常处理返回服务器错误。

### 创建工单幂等

- 幂等功能开启时，`POST /api/tickets` 必须携带合法 `Idempotency-Key`；
- Redis Key 由 JWT `sub` 与 trim 后的客户端 Key 生成摘要，不同用户使用相同客户端 Key 互不影响；
- 同 Key、同请求在成功 TTL 内返回第一次保存的 `TicketResponse`；同 Key、不同请求返回 HTTP 409 / 40907；
- 请求仍在处理时返回 HTTP 409 / 40906，并携带正数 `Retry-After`；
- `PROCESSING` 和 `SUCCEEDED` 都有有限 TTL，正常重复请求不会刷新 TTL；
- 当前是有限窗口幂等，不保证 exactly-once、永久防重或 Redis 与 MySQL 强一致。

## 本地运行

### 前置要求

- JDK 21；
- 可用的 Maven；
- Docker 与 Docker Compose；
- 本地 3307 端口可用；Redis 端口可通过 `REDIS_PORT` 指定。

### 1. 准备数据库环境变量

复制 `.env.example` 为 `.env`，在本机设置数据库普通用户密码和 root 密码。`.env` 已被 Git 忽略。

### 2. 启动 MySQL 和 Redis

```powershell
$env:REDIS_PORT = "6380"
docker compose up -d mysql redis
docker compose ps
```

等待 `ai-ticket-mysql` 和 `ai-ticket-redis` 均显示为 `healthy`。示例使用 6380 避免本机默认端口冲突，应用测试时应使用同一 `REDIS_PORT`。

### 3. 准备本地 Spring 配置

复制：

```text
src/main/resources/application-local.example.yml
```

为：

```text
src/main/resources/application-local.yml
```

只在本地文件中填写与 `.env` 对应的数据库密码。该文件已被 Git 忽略。

### 4. 配置 JWT 密钥

设置 `JWT_SECRET_BASE64` 环境变量。其 Base64 解码结果至少为 32 字节，不要提交真实值。

```powershell
$env:JWT_SECRET_BASE64 = "<your-base64-secret>"
```

### 5. 启动应用

```powershell
mvn spring-boot:run
```

默认端口为 8080，Flyway 会在启动时校验并迁移到当前 schema version 6。

### 6. 健康检查与测试

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
mvn test
```

当前 PowerShell 环境使用系统 Maven；仓库中的 Maven Wrapper 不作为本文档验证命令。

## API 快速索引

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/register` | 注册 USER |
| POST | `/api/auth/login` | 登录并获取 Access Token；受 IP/用户名固定窗口限流 |
| GET | `/api/auth/me` | 读取当前 JWT 身份 |
| POST | `/api/tickets` | 创建工单；启用时要求 `Idempotency-Key` |
| GET | `/api/tickets` | AGENT、ADMIN 全局条件分页 |
| GET | `/api/tickets/mine` | 查询当前用户创建的工单 |
| GET | `/api/tickets/{id}` | 按 ID 查询并执行对象级授权 |
| PATCH | `/api/tickets/{id}/status` | AGENT、ADMIN 更新状态 |
| PATCH | `/api/tickets/{id}/assignee` | ADMIN 指派 AGENT |
| POST | `/api/tickets/{id}/ai-analysis` | AGENT、ADMIN 获取 AI 分类与优先级建议（不写工单） |
| POST | `/api/tickets/{id}/ai-reply-draft` | AGENT、ADMIN 获取人工审核回复草稿 |
| POST | `/api/tickets/{id}/ai-agent` | AGENT、ADMIN 调用受控只读 Agent |
| GET | `/actuator/health` | 健康检查 |
| GET | `/actuator/info` | ADMIN 可访问的 Actuator 应用信息 |

除注册、登录和健康检查外，接口使用：

```http
Authorization: Bearer <access-token>
```

详细请求、响应和错误码见 [API 契约](docs/api_contract.md)。

## 测试概览

本轮在健康的真实 MySQL 8.4.11 与 Redis 7.4 容器上执行：

```powershell
mvn test
```

结果：

```text
Tests run: 463
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

61 个 Surefire 测试类、463 个测试实例覆盖 DTO Validation、Service 单元测试、standalone MockMvc、Mapper/MySQL 持久化、HTTP 全链路、Spring Security、Redis 基础设施、Lua 固定窗口限流、创建幂等状态机、Redis/MySQL 故障边界和 Java→Python AI HTTP 客户端错误边界；它们并不全部是端到端测试。Python 服务另有独立 `pytest` 测试。

## 详细文档

- [项目架构](docs/project_architecture.md)
- [API 契约](docs/api_contract.md)
- [安全、并发与事务设计](docs/security_and_transaction_design.md)
- [测试证据](docs/testing_evidence.md)
- [P0～P7 阶段复盘](docs/p7_project_stage_review.md)
- [P8 Redis、登录限流与创建幂等复盘](docs/p8_project_stage_review.md)
- [创建工单幂等架构决策](docs/p8_create_ticket_idempotency_decision.md)
- [Java/Python AI 服务架构](docs/ai_service_architecture.md)
- `docs/p1_create_ticket_module_review.md` 至 `docs/p4_ticket_status_transition_review.md`：历史阶段学习复盘。

## 项目边界与 Roadmap

当前尚未实现：

- 操作日志查询接口；
- 分配给我的工单列表；
- 取消指派和主动领取；
- 用户停用；
- Refresh Token、登出、Token 撤销和密钥轮换；
- Redis 工单缓存与缓存一致性；
- 除登录以外其他高成本接口的限流；
- MySQL 持久化幂等记录与长期响应重放；
- Redis Sentinel、Cluster 与故障恢复验证；
- 可信反向代理后的客户端 IP 解析；
- 消息队列与通知；
- 多租户隔离；
- Outbox 与外部副作用的一致性处理；
- 压力测试、生产部署与容灾验证。

这些内容是规划，不属于当前已完成功能。
