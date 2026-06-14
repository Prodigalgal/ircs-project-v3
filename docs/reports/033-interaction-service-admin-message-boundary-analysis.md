# 033 interaction-service admin message API 边界分析报告

状态：已完成

更新时间：2026-06-06

## 背景

`025` 路由覆盖闭环已完成 `025-01` 到 `025-07`，当前 V3 Controller route
推进口径为：

```text
V3 service Controller routes before this slice: 104
Direct missing routes before this slice:        110
V3 service Controller routes after this slice:  108
Direct missing routes after this slice:         106
```

V1 `interaction` 模块剩余缺口主要是后台留言管理接口。V3
`ircs-interaction-service` 已经拥有门户反馈、观看历史、收藏等接口，并且已经具备
`user_messages` 表的 JDBC 读写能力。本报告用于固定 `025-08` 的后台留言管理迁移边界。

## 上游来源

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `tasks/in-progress/025-v1-route-coverage-closeout-parent.md`
- `docs/migrations/024-interaction-service-portal-write-api-extraction.md`
- V1 `AdminMessageController`
- V1 `UserMessageServiceImpl`
- V1 `UserMessage`
- V1 `UserMessageDTO`
- V1 `AdminReplyCmd`
- V3 `FeedbackController`
- V3 `JdbcFeedbackRepository`
- V3 `UserMessageResponse`

## 目标接口

本切片覆盖 V1 后台留言管理的 4 个接口：

```text
GET    /api/v1/messages
POST   /api/v1/messages/{id}/reply
PUT    /api/v1/messages/{id}/visibility
DELETE /api/v1/messages/{id}
```

验收时按接口粒度计数。只有某个接口完成 V3 实现、部署到 `ircs-dev`，并通过真实
ClusterIP HTTP 调用复现后，才标记一次：

```text
MARK 1/4 ...
MARK 2/4 ...
MARK 3/4 ...
MARK 4/4 ...
```

准备数据、清理数据、健康检查、quota 检查不计入接口 MARK。

## V1 行为证据

### Controller

V1 `AdminMessageController`：

```text
GET    /api/v1/messages?keyword=&status=&page=&size=&sort=
POST   /api/v1/messages/{id}/reply
PUT    /api/v1/messages/{id}/visibility
DELETE /api/v1/messages/{id}
```

列表查询：

- 默认按 `createdAt DESC` 分页。
- `keyword` 匹配留言内容、会员昵称和会员邮箱。
- `status` 使用 `MessageStatus` 枚举：`PENDING`、`REPLIED`、`CLOSED`。

回复：

- 请求体字段：`reply`。
- 回复后写入 `user_messages.reply`。
- 状态置为 `REPLIED`。
- V1 当前传入 `adminId=null`，因此本轮保持 `admin_id` 不强制写入。

可见性：

- 请求体字段：`public`。
- 缺少 `public` 时返回 bad request。
- 写入 `user_messages.is_public`。

删除：

- 按 `id` 物理删除留言。

### 数据字段

V1 `UserMessageDTO` 字段：

```text
id
memberId
memberNickname
memberEmail
memberAvatarUrl
content
reply
status
isPublic
createdAt
updatedAt
```

V3 `UserMessageResponse` 已覆盖这些字段，且通过 `@JsonProperty("isPublic")`
保持 JSON 字段名兼容。本轮同时补充 `public` JSON 字段，兼容 admin 门户
`row.public` 开关读取。

## V3 目标边界

本切片复用已部署的 `ircs-interaction-service`：

```text
                +---------------------------+
                | ircs-interaction-service  |
                | ClusterIP only            |
                +-------------+-------------+
                              |
          +-------------------+-------------------+
          |                                       |
          v                                       v
+--------------------+                 +--------------------+
| user_messages      |                 | members            |
| content/reply/...  |                 | nickname/email/... |
+--------------------+                 +--------------------+
          |
          v
+-------------------------------+
| /api/v1/messages admin API    |
+-------------------------------+
```

实现策略：

- 沿用当前 service 的 Spring JDBC 风格，不引入 JPA。
- 返回 V3 现有 `PageResponse<UserMessageResponse>`，字段语义与 V1 DTO 对齐。
- 列表排序先支持 V1 默认 `createdAt DESC`；请求的 `sort` 若后续需要完全兼容再统一扩展。
- 本轮不新增 HTTPRoute、不新增 Pod、不新增 Service。
- 不触碰门户反馈接口的既有行为。

## 非目标

- 不改门户 `/api/portal/feedback/**` 行为。
- 不新增后台认证体系；沿用当前 dev ClusterIP smoke 口径。
- 不做消息软删除或审计表。
- 不做物理数据库拆分。

## 风险

- 后台列表按关键字 join `members`，需要保留 member email 字段；门户公开墙仍不得泄漏 email。
- V1 使用 Spring Data `Page`，V3 当前多数轻量服务使用自定义 `PageResponse`；前端若强依赖
  Spring Page 的 `sort/pageable` 元数据，后续需要统一 page DTO 兼容层。
- 物理删除会删除 smoke 数据；live smoke 必须使用临时 member/message，并在后置检查中确认无残留。

## 验收标准

- 完成本报告与 `025-08` task spec。
- `ircs-interaction-service` 新增 4 个后台留言接口。
- 单元测试覆盖列表参数透传、回复、可见性缺字段、删除。
- 本地 `:services:ircs-interaction-service:test` 通过。
- 全仓 `build` 通过。
- ARM64 Jib 镜像推送到 Harbor。
- `ircs-dev` rollout 成功。
- ClusterIP smoke 输出 `MARK 1/4` 到 `MARK 4/4`。
- smoke 后无 HTTPRoute，Pod/Service quota 不新增消耗，临时 `members` 和 `user_messages`
  数据清理为 0。

## 完成结果

```text
local:
  .\gradlew.bat :services:ircs-interaction-service:test -> PASS
  .\gradlew.bat build -> PASS

image:
  harbor.mnnu.eu.org/ircs/ircs-interaction-service:dev
  sha256:6b272d19acf269781317b9c59028eb67220601b79f977bbd40efc50fc4cbccd0

rollout:
  ircs-interaction-service Ready 1/1

smoke through frontend-gateway -> admin-bff -> interaction-service:
  MARK 1/4 GET /api/v1/messages -> HTTP 200
  MARK 2/4 POST /api/v1/messages/{id}/reply -> HTTP 200
  MARK 3/4 PUT /api/v1/messages/{id}/visibility -> HTTP 200
  MARK 4/4 DELETE /api/v1/messages/{id} -> HTTP 204

cleanup:
  remaining user_messages|members = 0|0

resource:
  no HTTPRoute
  pods 28/30
  services 24/26
```
