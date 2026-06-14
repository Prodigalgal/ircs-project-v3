# 027 interaction-service 门户写接口边界分析

状态：已完成

更新时间：2026-06-05

## 背景

`025` 路由覆盖审计显示，V1 `interaction` 模块仍有 12 个 HTTP 路由没有在 V3
服务中覆盖。`ircs-interaction-service` 已完成 MVP 只读切片：

- `GET /api/portal/feedback/wall`
- `GET /api/portal/interaction/history`
- `GET /api/portal/interaction/favorites`

下一步优先关闭前端用户态的轻写接口，复用现有 `ircs-interaction-service`
Deployment，不新增 Pod、Service 或 HTTPRoute。

## 目标

本切片覆盖以下 V1 门户 API：

```text
POST   /api/portal/interaction/favorites/{unifiedVideoId}
GET    /api/portal/interaction/favorites/{unifiedVideoId}/status
POST   /api/portal/interaction/history
DELETE /api/portal/interaction/history
DELETE /api/portal/interaction/history/{id}
POST   /api/portal/feedback
GET    /api/portal/feedback
DELETE /api/portal/feedback/{id}
```

预期完成后，`interaction` 模块缺口由 12 条降至 4 条，剩余为后台留言管理
`/api/v1/messages/**`。

## 非目标

- 不迁移 `/api/v1/messages/**` 后台留言审核、回复、公开状态切换和管理员删除。
- 不新增 `q.interaction.watch_progress` RabbitMQ consumer。
- 不引入 Redis traffic policy；本轮先保持 JWT 认证和数据库约束。
- 不做物理 DB 拆分，仍以共享 PostgreSQL 作为迁移桥。
- 不新增公网 HTTPRoute。

## V1 行为摘要

### 收藏

V1 `MemberFavoriteServiceImpl` 行为：

- `toggleFavorite(memberId, unifiedVideoId)` 先确认 `unified_videos` 存在。
- 已收藏则删除并返回 `favorited=false`。
- 未收藏则插入并返回 `favorited=true`。
- `checkFavorite` 返回 `favorited` 状态。

### 观看历史

V1 `PortalInteractionController` 行为：

- `POST /history` 接收 `unifiedVideoId`、`videoId`、`episodeId`、`episodeName`、
  `progress`、`duration`，返回 `202 Accepted`。
- V1 通过 `q.interaction.watch_progress` 异步写入；V3 本轮采用同库
  `upsert` 完成 MVP 等价，队列 consumer 后置。
- `DELETE /history` 清空当前会员历史，返回 `204 No Content`。
- `DELETE /history/{id}` 删除当前会员的单条历史，返回 `204 No Content`。

### 用户反馈

V1 `UserMessageServiceImpl` 行为：

- `POST /api/portal/feedback` 校验内容非空、最长 500 字。
- 使用 `member.message.daily-limit` 控制会员每日留言次数，默认 5。
- 使用 HTML 清洗后写入 `user_messages`，默认 `status=PENDING`、`is_public=false`。
- `GET /api/portal/feedback` 返回当前会员自己的留言。
- `DELETE /api/portal/feedback/{id}` 只允许删除自己的留言。

## V3 目标实现

```text
client/member token
        |
        v
+--------------------------+
| ircs-interaction-service |
|                          |
|  /api/portal/interaction |
|  /api/portal/feedback    |
+------------+-------------+
             |
             v
+--------------------------+
| shared PostgreSQL bridge |
|                          |
| member_favorites         |
| member_watch_histories   |
| user_messages            |
| unified_videos           |
| members                  |
+--------------------------+
```

实现边界：

- Controller 继续使用 `MemberTokenService` 解析 `Authorization: Bearer` 或
  `token` 查询参数。
- 收藏和历史写入集中到 command service，读接口继续保留在
  `InteractionQueryService`。
- 反馈写入需要复用 `SystemConfigRepository` 读取 `member.message.daily-limit`。
- 返回模型复用已有 `InteractionRecordResponse`、`UserMessageResponse` 和
  `PageResponse`。

## 数据表和约束

依赖现有 migration：

- `member_favorites`：主键 `id`，唯一约束
  `(member_id, unified_video_id)`。
- `member_watch_histories`：主键 `id`，唯一约束
  `(member_id, unified_video_id)`。
- `user_messages`：主键 `id`，按 `(member_id, created_at desc)` 查询。
- `unified_videos`：收藏和历史的内容锚点。
- `members`：JWT subject 对应会员账号。

## 风险

- V1 历史上报是异步队列，本轮改为同步 `upsert`，可能与未来 consumer 需要去重。
- 删除反馈时如果返回 404，可能暴露“不是自己的留言或不存在”的差异；本轮按安全口径统一
  不暴露归属。
- HTML 清洗依赖实现方式，需要避免额外大依赖影响镜像大小。
- 当前 `ircs-dev` ResourceQuota 紧张，本轮不能新增 Deployment 或 Service。

## 验收标准

- 单元测试覆盖新增 Controller 和 command service 分支。
- `.\gradlew.bat :services:ircs-interaction-service:test` 通过。
- `.\gradlew.bat build` 通过。
- ARM64 镜像推送到 dev Harbor tag，并 rollout 当前 Deployment。
- ClusterIP smoke 覆盖收藏 toggle/status、历史 upsert/delete、反馈 submit/list/delete。
- smoke 后临时 `members`、`unified_videos`、`member_favorites`、
  `member_watch_histories`、`user_messages` 数据清理到 0。

## 验收结果

已完成：

- `.\gradlew.bat :services:ircs-interaction-service:test` 通过。
- `.\gradlew.bat build` 通过。
- ARM64 镜像推送到
  `harbor.mnnu.eu.org/ircs/ircs-interaction-service:dev`。
- `ircs-dev` 中 `ircs-interaction-service` rollout 成功。
- 运行镜像 digest：
  `harbor.mnnu.eu.org/ircs/ircs-interaction-service@sha256:7dbaaef893c0465da52fbe94bd5a3d94973a6e3cf757bcf02f60c79ea75e84e4`。
- ClusterIP smoke 覆盖收藏 toggle/status、历史 upsert/list/delete/clear、反馈
  submit/list/delete。
- 临时数据清理结果：
  `members,unified,user_messages,history,favorites=0,0,0,0,0`。
- `ircs-dev` 未新增 HTTPRoute，ResourceQuota 仍为 `pods=23/24`、`services=19/20`。

本切片完成后，`interaction` 模块剩余缺口为后台留言管理 4 条
`/api/v1/messages/**` 路由。
