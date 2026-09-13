# 简历项目描述备选

本文用于制作正式中文简历。所有表述只覆盖当前已完成的 Java + Python 工单系统；Java 负责可信业务边界，Python 负责受控 AI 能力，生产部署仍不属于本项目证据。

## 1. 项目名称建议

### 默认推荐：AI 工单平台后端

名称保留后续智能化方向，同时明确当前交付物是工单后端。

### 稳妥 Java 后端名称：工单管理与认证授权平台

适合 Java 后端岗位，突出已经实现的核心业务和安全边界。

### 权限与一致性方向：工单权限、并发与操作审计系统

适合希望重点讲对象级授权、条件更新和事务日志的场景。

## 2. 一句话项目说明

基于 Spring Boot 构建工单业务后端，实现 JWT 认证授权、条件更新与事务审计，并以 Redis Lua 为登录和创建链路提供有限窗口保护。

## 3. 默认四条简历要点

- 设计用户、工单与操作日志三表模型，基于 MyBatis-Plus 实现创建、详情、条件分页、状态流转和指派，以 Flyway V1～V6 管理数据库演进及身份外键。
- 基于 BCrypt、HS256 JWT 和 Spring Security 构建 USER、AGENT、ADMIN 三角色认证授权，分离请求级权限与 Service 对象级所有权，可信身份统一来自 JWT `sub`。
- 通过旧状态/旧处理人条件 UPDATE 识别并发冲突，将业务更新与操作日志写入同一事务，并使用真实 MySQL 外键故障验证两次写入一起回滚。
- 基于 Redis Lua 实现登录 IP/规范化用户名双维度固定窗口限流，并设计用户作用域 `Idempotency-Key` 状态机，支持处理中冲突识别和成功响应重放；以62个测试类、463项测试覆盖关键边界。
- 通过 Java 配置化 HTTP 客户端调用同级 FastAPI AI 服务，使用结构化 schema 返回工单分析建议、人工审核回复草稿和受控只读 Agent；Python 不持有 Java 数据库凭证，也不自动修改工单。

## 4. 技术栈行

```text
Java 21 / Spring Boot 4.1 / Spring Security / MyBatis-Plus / MySQL 8.4 / Flyway / JWT / Redis 7.4 / Spring Data Redis / Lettuce / Lua / Docker Compose / Maven；Python 3.12 / FastAPI / Pydantic / pytest / MCP SDK
```

补充测试技术可单独写为：

```text
JUnit Jupiter / Mockito / MockMvc / Spring Boot Test / 真实 MySQL 集成测试
```

## 5. 精简版

### 项目说明

基于 Spring Boot 的工单业务后端，覆盖 JWT 认证授权、工单流转、事务审计，以及 Redis 登录限流和创建幂等。

### 三条要点

- 基于 MyBatis-Plus、MySQL 和 Flyway V1～V6 实现用户、工单、条件分页、状态流转及处理人指派，维护三张核心表的可追踪演进。
- 使用 BCrypt、HS256 JWT 与 Spring Security 构建 USER、AGENT、ADMIN 三角色权限，并在 Service 中实现 USER 工单详情所有权过滤。
- 采用条件 UPDATE 与同事务日志保证业务一致性，并使用 Redis Lua 实现登录固定窗口限流和用户作用域创建幂等；通过463项 Java 分层测试和 Python pytest 验证核心链路、AI HTTP 故障边界与结构化输出。

## 6. 详细版

### 项目说明

基于 Spring Boot 构建可持续演进的工单后端基础平台，为后续分类、优先级建议和回复草稿等智能能力提供身份、权限、业务状态与审计基础。

### 五条要点

- 采用 Controller、Service、MyBatis-Plus Mapper 分层实现注册登录、工单创建、详情、我的工单、全局条件分页、顺序状态流转和 ADMIN 指派。
- 设计 `users`、`tickets`、`ticket_operation_logs` 三张核心表，使用 Flyway V1～V6 处理状态值对齐、创建者/处理人外键及追加式日志演进。
- 使用 BCrypt strength 10 保存密码哈希，以 HS256 JWT 承载用户 ID、用户名和角色，并通过 Resource Server Validator、Converter、401/403 Handler 建立无状态认证链。
- 将 SecurityFilterChain 的请求级角色规则与 TicketService 的对象级所有权分离；USER 查询他人工单统一返回404，`creatorName` 不参与授权判断。
- 使用旧状态和旧处理人条件 UPDATE 防止静默覆盖，并将业务 UPDATE 与操作日志 INSERT 纳入同一 MySQL 事务。
- 基于单 Key Redis Lua 实现登录双维度固定窗口计数，以及 `PROCESSING/SUCCEEDED` 创建幂等状态机；62个测试类、463项 Java 测试覆盖单元、MVC、真实 MySQL、真实 Redis、HTTP 与 AI 客户端故障场景。

## 7. 不推荐写法

| 不推荐表述 | 原因 |
| --- | --- |
| 生产级高并发工单系统 | 没有生产部署、压力测试或容量数据 |
| 支持百万级工单数据 | 没有百万级数据准备或查询证据 |
| 达到高 QPS、低延迟 | 没有性能基准、响应时间和 QPS 数据 |
| 大幅提升系统性能 | 没有对照实验或提升百分比 |
| 实现自动业务决策或自动发送回复 | 当前 AI 只返回建议、草稿和受控只读回答，不能替代人工确认或 Java Service 规则 |
| 构建生产级 RAG 平台 | 当前是小型本地知识库和词法检索示例，没有大型向量基础设施 |
| 保证系统绝对安全 | 仍无 Token 撤销、限流、渗透测试和密钥轮换 |
| 操作日志不可篡改 | 目前只是应用层追加式，数据库权限持有者仍可能修改 |
| 完整解决分布式并发 | 当前是单体、单数据库条件更新，没有多节点一致性验证 |
| Redis 与 MySQL 强一致 | 两个存储不在同一事务，存在提交后的失败窗口 |
| exactly-once 或彻底防重复 | 当前仅在 Redis TTL 内协调和重放，没有 MySQL 持久化幂等 |
| 分布式锁或支付级幂等 | 当前是请求状态机，不是通用锁，也没有支付级恢复证据 |
| 高并发性能提升 | 没有压测、QPS、延迟或对照数据 |
| 463个端到端测试 | 463是多个测试层次的 Java 实例总数，不全是端到端 |
| 精通 Spring Security | 单个项目只能证明具体实现与理解，不能证明“精通” |

## 8. 可量化事实清单

| 指标 | 当前有证据的数值 |
| --- | ---: |
| 用户角色 | 3：USER、AGENT、ADMIN |
| Flyway 迁移 | 6：V1～V6 |
| Java 自动化测试实例 | 463 |
| Surefire 测试类 | 62 |
| 核心业务表 | 3：users、tickets、ticket_operation_logs |
| 业务与健康类 HTTP 端点 | 11：9个业务端点、2个 Actuator 端点 |
| 工单状态 | 4 |
| 实际允许状态迁移边 | 3 |
| 操作日志类型 | 2：STATUS_CHANGED、ASSIGNEE_CHANGED |
| 应用错误码 | 19 |
| Redis Lua 脚本 | 4：登录限流1个、创建幂等3个 |

当前没有可用于简历的 QPS、响应时间、并发用户数、性能提升百分比或代码覆盖率数据。

## 9. 使用建议

- Java 后端岗位优先采用默认四条或精简版；
- 项目篇幅较大时采用详细版，但不要把全部五条写成同一长度；
- 面试时明确区分 Java 业务真相与 Python AI 建议，并主动说明默认 fake provider、外部 LLM 凭证和生产部署边界；
- 根据个人实际贡献调整“设计并实现”等动词，确保能够现场定位代码并解释测试证据。
