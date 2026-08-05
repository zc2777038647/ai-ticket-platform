# API 契约

本文从当前 `AuthController`、`TicketController`、DTO、`SecurityConfig`、`ErrorCode` 和 `GlobalExceptionHandler` 提取。示例均使用占位符或明显测试值。

## 1. 通用约定

### 1.1 Base URL

本地默认地址：

```text
http://localhost:8080
```

### 1.2 Bearer 认证

需要认证的接口使用：

```http
Authorization: Bearer <access-token>
```

当前 Access Token 类型为 `Bearer`，没有 Refresh Token。

### 1.3 统一响应

业务接口成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

无附加错误数据时：

```json
{
  "code": 40400,
  "message": "工单不存在",
  "data": null
}
```

字段校验失败时，`data` 可以是字段到中文提示的对象，例如：

```json
{
  "code": 40000,
  "message": "请求参数校验失败",
  "data": {
    "title": "标题不能为空"
  }
}
```

响应没有 `traceId`、`timestamp` 或 `path` 字段。Actuator 健康检查由 Spring Boot Actuator 输出，不使用 `ApiResponse`。

## 2. 认证接口

### 2.1 注册

```text
POST /api/auth/register
```

| 项目 | 契约 |
| --- | --- |
| 认证 | 不需要 |
| 允许角色 | 公开 |
| Content-Type | `application/json` |
| 成功状态 | HTTP 201 |

请求：

```json
{
  "username": "demo_user",
  "password": "ExamplePass123",
  "displayName": "演示用户"
}
```

约束：

- `username`：非空，4～64 字符，只允许字母、数字和下划线；保存前转小写；
- `password`：非空，8～64 字符；不会 trim；
- `displayName`：非空，最多 64 字符；保存前 trim。

成功 `data`：

```json
{
  "id": 1,
  "username": "demo_user",
  "displayName": "演示用户",
  "role": "USER"
}
```

主要错误：HTTP 400 / 40000、40001、40002；HTTP 409 / 40902；HTTP 500 / 50000。

业务边界：客户端不能指定角色；注册始终创建 USER；响应不包含密码或密码哈希。

### 2.2 登录

```text
POST /api/auth/login
```

| 项目 | 契约 |
| --- | --- |
| 认证 | 不需要 |
| 允许角色 | 公开 |
| 成功状态 | HTTP 200 |

请求：

```json
{
  "username": "demo_user",
  "password": "ExamplePass123"
}
```

`username` 非空、最多 64 字符并按小写查询；`password` 非空、最多 64 字符且不 trim。

成功 `data`：

```json
{
  "accessToken": "<access-token>",
  "tokenType": "Bearer",
  "expiresIn": 7200,
  "user": {
    "id": 1,
    "username": "demo_user",
    "displayName": "演示用户",
    "role": "USER"
  }
}
```

主要错误：HTTP 400 / 40000、40001、40002；HTTP 401 / 40100；HTTP 500 / 50000。

业务边界：用户名不存在和密码错误统一返回“用户名或密码错误”。

### 2.3 当前用户

```text
GET /api/auth/me
```

| 项目 | 契约 |
| --- | --- |
| 认证 | 必须提供有效 Bearer Token |
| 允许角色 | USER、AGENT、ADMIN |
| 成功状态 | HTTP 200 |

成功 `data` 来自已验证 JWT claims：

```json
{
  "id": 1,
  "username": "demo_user",
  "role": "USER"
}
```

主要错误：HTTP 401 / 40101。该接口不实时查询 users 表。

## 3. 工单接口

### 3.1 创建工单

```text
POST /api/tickets
```

| 项目 | 契约 |
| --- | --- |
| 认证 | 必须 |
| 允许角色 | USER、AGENT、ADMIN |
| 成功状态 | HTTP 201 |

请求：

```json
{
  "title": "无法登录系统",
  "description": "输入正确凭据后仍然无法登录",
  "creatorName": "演示创建人",
  "priority": "HIGH"
}
```

约束：`title` 非空且最多 120 字符；`description` 非空且最多 2000 字符；`creatorName` 非空且最多 64 字符；`priority` 必须是 `LOW`、`MEDIUM`、`HIGH`、`URGENT`。

成功 `data`：

```json
{
  "id": 100,
  "title": "无法登录系统",
  "description": "输入正确凭据后仍然无法登录",
  "creatorName": "演示创建人",
  "priority": "HIGH",
  "status": "OPEN"
}
```

主要错误：HTTP 400 / 40000、40001、40002；HTTP 401 / 40101；HTTP 500 / 50000。

业务边界：`creator_user_id` 来自 JWT `sub`，不来自请求；`creatorName` 不用于授权。当前响应不包含创建者用户 ID、处理人和时间字段。

### 3.2 全局条件分页

```text
GET /api/tickets
```

| 项目 | 契约 |
| --- | --- |
| 认证 | 必须 |
| 允许角色 | AGENT、ADMIN |
| 成功状态 | HTTP 200 |

查询参数：

| 参数 | 类型 | 默认/约束 | 查询语义 |
| --- | --- | --- | --- |
| `page` | Integer | 默认 1，最小 1 | 页码 |
| `size` | Integer | 默认 20，1～100 | 每页数量 |
| `status` | TicketStatus | 可选 | 精确匹配 |
| `priority` | TicketPriority | 可选 | 精确匹配 |
| `creatorName` | String | 最多 64，查询前 trim | 精确匹配 |
| `keyword` | String | 最多 100，查询前 trim | title 或 description 模糊匹配 |

排序固定为 `created_at DESC, id DESC`。成功 `data`：

```json
{
  "records": [],
  "total": 0,
  "pages": 0,
  "current": 1,
  "size": 20
}
```

主要错误：HTTP 400 / 40000、40002；HTTP 401 / 40101；HTTP 403 / 40300。

### 3.3 我的工单分页

```text
GET /api/tickets/mine
```

| 项目 | 契约 |
| --- | --- |
| 认证 | 必须 |
| 允许角色 | USER、AGENT、ADMIN |
| 成功状态 | HTTP 200 |

查询参数、排序和响应与全局分页相同，但 Service 强制追加：

```text
creator_user_id = 当前 JWT sub
```

AGENT、ADMIN 使用该端点时也只查看自己创建的工单。`creatorName` 过滤不能扩大所有权范围。

### 3.4 按 ID 查询工单

```text
GET /api/tickets/{id}
```

| 项目 | 契约 |
| --- | --- |
| 认证 | 必须 |
| 允许角色 | USER、AGENT、ADMIN |
| 路径参数 | `id` 必须能转换为 Long 且为正数 |
| 成功状态 | HTTP 200 |

成功 `data` 使用 `TicketResponse`，字段与创建成功响应相同。

主要错误：

- `/abc`：HTTP 400 / 40002；
- `/0`：HTTP 400 / 40000；
- 工单不存在：HTTP 404 / 40400；
- USER 查询他人工单：HTTP 404 / 40400；
- 无效 Token：HTTP 401 / 40101。

AGENT、ADMIN 可查看任意现有工单，包括 `creator_user_id=NULL` 的历史工单。

### 3.5 更新工单状态

```text
PATCH /api/tickets/{id}/status
```

| 项目 | 契约 |
| --- | --- |
| 认证 | 必须 |
| 允许角色 | AGENT、ADMIN |
| 路径参数 | 正数 Long |
| 成功状态 | HTTP 200 |

请求：

```json
{
  "status": "IN_PROGRESS"
}
```

成功 `data` 为更新状态后的 `TicketResponse`。

主要错误：HTTP 400 / 40000、40001、40002；HTTP 401 / 40101；HTTP 403 / 40300；HTTP 404 / 40400；HTTP 409 / 40900、40901；HTTP 500 / 50000。

业务边界：只允许 `OPEN → IN_PROGRESS → RESOLVED → CLOSED`；条件 UPDATE 使用旧状态；成功后写入 `STATUS_CHANGED`；日志失败则状态回滚。

### 3.6 指派处理人

```text
PATCH /api/tickets/{id}/assignee
```

| 项目 | 契约 |
| --- | --- |
| 认证 | 必须 |
| 允许角色 | ADMIN |
| 路径参数 | 正数 Long |
| 成功状态 | HTTP 200 |

请求：

```json
{
  "assigneeUserId": 200
}
```

`assigneeUserId` 必须非空且为正数。成功 `data`：

```json
{
  "ticketId": 100,
  "assigneeUserId": 200,
  "assigneeUsername": "agent_demo",
  "assigneeDisplayName": "演示处理人"
}
```

主要错误：HTTP 400 / 40000、40001、40002；HTTP 401 / 40101；HTTP 403 / 40300；HTTP 404 / 40400、40401；HTTP 409 / 40903、40904、40905；HTTP 500 / 50000。

业务边界：目标必须为 AGENT；允许首次指派和重新指派；不支持取消指派或主动领取；成功后写入 `ASSIGNEE_CHANGED`，日志操作者是 ADMIN 而不是目标 AGENT。

## 4. 健康检查

```text
GET /actuator/health
```

无需认证，成功通常为 HTTP 200。响应结构由 Spring Boot Actuator 管理，不使用统一业务响应。

当前 `management.endpoints.web.exposure.include` 同时包含 `health,info`，因此还存在：

```text
GET /actuator/info
```

该端点同样由 Actuator 管理。`SecurityConfig` 只显式匹配了 health，但当前兜底规则是 `anyRequest().permitAll()`，所以 info 实际也无需认证。这一行为应在未来收紧兜底规则时重新核对。

## 5. 完整错误码表

| 应用码 | HTTP | 对外消息 | 典型来源 |
| ---: | ---: | --- | --- |
| 40000 | 400 | 请求参数校验失败 | Bean Validation、路径正数校验 |
| 40001 | 400 | 请求体格式错误 | JSON 语法、请求体枚举或类型反序列化失败 |
| 40002 | 400 | 请求参数格式错误 | 路径/查询参数类型转换或绑定失败 |
| 40100 | 401 | 用户名或密码错误 | 登录凭据失败 |
| 40101 | 401 | 请先登录或提供有效访问令牌 | 缺少、无效或过期 Bearer Token |
| 40300 | 403 | 权限不足，无法执行此操作 | 已认证但角色不允许 |
| 40400 | 404 | 工单不存在 | 资源不存在或 USER 无对象所有权 |
| 40401 | 404 | 处理人不存在 | 指派目标用户不存在 |
| 40900 | 409 | 工单状态流转不合法 | 跳跃、反向、相同状态或 CLOSED 后更新 |
| 40901 | 409 | 工单状态已发生变化，请刷新后重试 | 状态条件 UPDATE 为 0 行 |
| 40902 | 409 | 用户名已存在 | 注册用户名冲突 |
| 40903 | 409 | 目标用户不是可指派的处理人 | 目标角色不是 AGENT |
| 40904 | 409 | 工单已指派给该处理人 | 重复指派 |
| 40905 | 409 | 工单指派状态已发生变化，请刷新后重试 | 处理人条件 UPDATE 为 0 行 |
| 50000 | 500 | 服务器内部错误 | 未预期异常、异常影响行数、日志写入失败等 |

HTTP 状态码表达传输层结果；五位应用码区分项目内部错误类型，两者不能互相替代。

## 6. 当前 API 边界

- 没有用户列表、用户停用、角色修改接口；
- 没有 Refresh Token、登出或 Token 撤销接口；
- 没有分配给我的工单接口；
- 没有取消指派、主动领取、通知或评论接口；
- 没有操作日志查询、修改或删除接口；
- 没有 AI 分类、RAG 或 Agent 接口。
