# 025-03 interaction-service 门户写接口 MVP

状态：已完成

完成时间：2026-06-05

父任务：`tasks/in-progress/025-v1-route-coverage-closeout-parent.md`

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/027-interaction-service-portal-write-boundary-analysis.md`
- `docs/migrations/021-interaction-service-feedback-history-extraction.md`
- `docs/migrations/024-interaction-service-portal-write-api-extraction.md`

## 背景

`ircs-interaction-service` 已迁移门户反馈墙、观看历史只读和收藏只读，但 V1
门户交互写接口仍由单体承担。本任务在不新增 K8S 资源的前提下，复用现有 service
关闭用户侧轻写缺口。

## 目标

已实现并验证以下 API：

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

## 非目标

- 不迁移后台 `/api/v1/messages/**`。
- 不接入 `q.interaction.watch_progress` consumer。
- 不新增 Redis traffic policy。
- 不新增 Deployment、Service、Ingress 或 HTTPRoute。
- 不做物理 DB 拆分。

## 输出

- `InteractionCommandService` 和 `ProgressReportRequest`。
- `FeedbackCommandService`、`FeedbackSubmitRequest` 和 `JdbcFeedbackRepository`。
- `PortalInteractionController` 新增收藏、历史写接口。
- `FeedbackController` 新增反馈提交、个人列表和删除接口。
- 新增 command/controller 单元测试。
- ARM64 镜像推送并完成 `ircs-dev` rollout。
- ClusterIP smoke 完成并清理临时数据。

## 行为选择

- 收藏 toggle 与 V1 保持 `favorited` 响应字段。
- 历史上报本轮采用同步 DB `upsert`，以
  `(member_id, unified_video_id)` 保持幂等。
- 反馈删除按当前会员归属删除，未命中时 no-op 204，避免暴露留言归属。
- 反馈清洗使用轻量 HTML tag/script/style/comment 移除，不新增大依赖。

## 验收结果

自动测试：

```text
.\gradlew.bat :services:ircs-interaction-service:test
BUILD SUCCESSFUL

.\gradlew.bat build
BUILD SUCCESSFUL
```

K8S：

```text
image: harbor.mnnu.eu.org/ircs/ircs-interaction-service:dev
imageID: harbor.mnnu.eu.org/ircs/ircs-interaction-service@sha256:7dbaaef893c0465da52fbe94bd5a3d94973a6e3cf757bcf02f60c79ea75e84e4
rollout: successfully rolled out
HTTPRoute: none
ResourceQuota: pods=23/24, services=19/20
```

Smoke：

```text
favorite toggle/status: 200 true -> 200 false
history report/list/delete/clear: 202, 200, 204, 204
feedback submit/list/delete: 200, 200, 204
cleanup_remaining members,unified,user_messages,history,favorites=0,0,0,0,0
```

## 剩余事项

`interaction` 模块剩余后台留言管理 4 条路由：

```text
GET    /api/v1/messages
POST   /api/v1/messages/{id}/reply
PUT    /api/v1/messages/{id}/visibility
DELETE /api/v1/messages/{id}
```

后续已完成 `tasks/done/025-08-interaction-service-admin-message-api-mvp.md`。
