# 测试证据

本文数据来自本轮实际执行命令、45 份 Surefire XML 和对应测试代码，不按文件名猜测测试数量。

## 1. 测试总览

### 1.1 执行环境

| 项目 | 本轮实际值 |
| --- | --- |
| 执行日期 | 2026-08-05 |
| Java | 21.0.9 |
| Maven | 3.9.11 |
| MySQL | Docker 镜像 8.4.11，容器健康 |
| Flyway | 12.4.0 |
| Flyway schema version | 6 |
| Surefire 测试类 | 45 |

本机工具版本用于复现实验环境；仓库硬性要求是 Java 21、Maven 可用和 Compose 支持。

### 1.2 全量结果

执行：

```powershell
mvn test
```

实际结果：

```text
Tests run: 329
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

Spring 启动日志同时显示：

```text
Successfully validated 6 migrations
Current version of schema `ai_ticket`: 6
```

## 2. 不重复计数的测试分层

以下分类以每个 Surefire testsuite 只归入一类为原则。

| 层次 | 测试类数 | 测试实例数 | 是否 Mock | 是否启动 Spring | 是否连接 MySQL |
| --- | ---: | ---: | --- | --- | --- |
| DTO Validation | 6 | 44 | 否 | 否 | 否 |
| 核心与 Service 单元测试 | 13 | 112 | 部分使用 Mockito | 否 | 否 |
| standalone MVC / 异常协议 | 3 | 69 | Mock Service 或测试 Controller | 仅 MVC 组件 | 否 |
| Mapper 与迁移集成 | 7 | 21 | 否 | 是 | 是 |
| Service / MySQL 集成 | 4 | 16 | 否 | 是 | 是 |
| 普通 HTTP 全链路集成 | 6 | 21 | 否 | 是 | 是 |
| Security HTTP 集成 | 5 | 45 | 否 | 是 | 是 |
| ApplicationContext 冒烟 | 1 | 1 | 否 | 是 | 是 |
| **合计** | **45** | **329** |  |  |  |

### 2.1 DTO Validation：44

- `AssignTicketRequestValidationTest`：4；
- `CreateTicketRequestValidationTest`：9；
- `LoginRequestValidationTest`：9；
- `RegisterRequestValidationTest`：12；
- `TicketPageQueryValidationTest`：8；
- `UpdateTicketStatusRequestValidationTest`：2。

直接使用 Jakarta `Validator`，验证字段边界，不启动 Spring。

### 2.2 核心与 Service 单元测试：112

包括统一响应、JWT/密码配置、分页响应、状态机、业务异常、安全转换器与处理器，以及：

- `AuthServiceImplTest`：9；
- `JwtTokenServiceImplTest`：3；
- `TicketServiceImplTest`：50。

Service 单元测试 Mock Mapper，验证业务分支、映射、交互次数、日志时序和异常传播，不证明 Spring 事务代理或真实 SQL。

### 2.3 standalone MVC 与异常协议：69

- `AuthControllerTest`：11；
- `TicketControllerTest`：36；
- `GlobalExceptionHandlerTest`：22。

验证请求绑定、Validation、Principal 到 Service 参数传递、HTTP 状态、应用码及安全错误响应，不连接数据库。

### 2.4 Mapper 与迁移集成：21

覆盖：

- Ticket 基础持久化；
- creator 与 assignee 外键映射；
- UserAccount；
- MyBatis-Plus 分页插件；
- V2 默认状态迁移；
- 操作日志字段、枚举、自增 ID、时间和外键。

这些测试注入真实 Mapper 并连接真实 MySQL。

### 2.5 Service / MySQL 集成：16

- `TicketAssignmentIntegrationTest`：5；
- `TicketOperationAtomicityIntegrationTest`：5；
- `TicketPaginationServiceIntegrationTest`：2；
- `TicketStatusUpdateIntegrationTest`：4。

用于验证真实 MyBatis-Plus 条件 UPDATE、业务事务、分页与日志原子性。

### 2.6 普通 HTTP 全链路：21

覆盖注册、登录、创建、查询详情、分页和状态更新，从 HTTP 进入 Controller、Service、Mapper 和 MySQL。

### 2.7 Security HTTP 集成：45

- `BearerAuthenticationIntegrationTest`：10；
- `TicketAssignmentAuthorizationIntegrationTest`：11；
- `TicketAuthorizationIntegrationTest`：14；
- `TicketOperationLoggingHttpIntegrationTest`：7；
- `TicketOwnershipIntegrationTest`：3。

真实经过 BCrypt 登录、JWT 签发、Resource Server Filter Chain、角色授权、对象所有权与数据库。

## 3. 关键证据

### 3.1 BCrypt 数据库存储

真实注册测试读取数据库，验证 `password_hash` 已写入、不是原始密码且 `PasswordEncoder.matches` 成功。响应没有哈希字段。

### 3.2 JWT 签名与 Claims

单元测试覆盖 HS256 配置、最小密钥长度、issuer、TTL、`sub`、`username`、`role`、`jti`、签发与过期时间。Bearer 集成测试使用真实登录 Token 访问受保护端点。

### 3.3 无效 Token 返回 401

安全集成测试覆盖缺失、损坏、过期和必要 claims 非法的 Token，验证 HTTP 401 / 40101 和 `WWW-Authenticate: Bearer`。

### 3.4 权限不足返回 403

USER 更新状态、USER/AGENT 指派等场景通过真实 Filter Chain 返回 HTTP 403 / 40300，Service 不应被执行。

### 3.5 USER 对象所有权

测试准备多个真实用户和工单，验证 USER 只能查看自己的详情和 `/mine` 数据；访问他人工单返回 404。AGENT、ADMIN 可查看任意工单。

### 3.6 状态条件更新冲突

真实 MySQL 测试证明：第一次基于旧状态的 UPDATE 影响 1 行，第二次使用过期旧状态影响 0 行，最终状态不被覆盖。Service 将 0 行映射为 40901。

### 3.7 指派条件和目标角色

测试覆盖目标不存在、目标为 USER/ADMIN、首次指派、重新指派、重复指派，以及 NULL/非 NULL 旧处理人条件冲突。

### 3.8 操作日志

Mapper 测试验证 `STATUS_CHANGED`、`ASSIGNEE_CHANGED`、前后值、自增 ID、数据库时间和外键。HTTP 测试进一步证明日志操作者来自真实 Token 的 `sub`。

### 3.9 日志失败回滚业务

原子性测试让不存在的正数 operator ID 触发真实 MySQL 外键异常：

- 状态条件 UPDATE 已执行，但最终状态回滚为 OPEN；
- 指派条件 UPDATE 已执行，但最终 assignee 仍为 NULL；
- 两种情况均无日志残留。

### 3.10 Flyway V1～V6

每次 Spring/MySQL 集成测试启动都会校验 6 个迁移，日志确认 schema version 6 且无需新迁移。

### 3.11 测试事务零残留

真实数据库测试主要使用 `@Transactional` 回滚，部分测试在 `@AfterTransaction` 使用唯一前缀或已记录 ID 断言 users、tickets、ticket_operation_logs 数量为 0。自增 ID 仍可能被消耗，测试不依赖 ID 连续。

## 4. 当前未验证内容

以下内容没有测试证据，因此不能从 329 项测试推导出来：

- 压力测试或性能基准；
- 大数据量分页性能；
- 真实多线程 HTTP 竞态；
- 多节点或分布式一致性；
- 生产环境部署与升级；
- 数据库主从、备份恢复和容灾；
- 网络故障、连接池耗尽或部分可用性；
- 安全渗透测试；
- 密钥托管、轮换和泄露响应；
- 多租户隔离；
- AI 模型质量、RAG 检索质量或 Agent 安全性。

## 5. 复现命令

### 5.1 环境检查

```powershell
java -version
mvn -version
docker compose version
docker compose ps
```

### 5.2 全量测试

```powershell
mvn test
```

### 5.3 示例目标测试

```powershell
mvn "-Dtest=TicketServiceImplTest,TicketControllerTest" test
mvn -Dtest=TicketOperationLogMapperIntegrationTest test
mvn -Dtest=TicketOperationAtomicityIntegrationTest test
mvn -Dtest=TicketOperationLoggingHttpIntegrationTest test
```

当前文档没有使用 Maven Wrapper 作为验证命令。

## 6. 证据解读边界

- 单元测试通过不代表真实 SQL 正确，因此另有 MySQL 集成测试；
- Mapper 测试通过不代表 HTTP 与安全规则正确，因此另有 MockMvc 和安全全链路测试；
- `@Transactional` 测试回滚可保持开发库整洁，但不会回收 MySQL 自增值；
- 329 是测试实例数，不是“329 个端到端场景”；
- 测试成功说明当前受测环境和覆盖场景通过，不等于不存在未覆盖缺陷。
