# 024 interaction-service 门户写接口迁移记录

状态：已完成

完成时间：2026-06-05

## 范围

本次迁移在现有 `ircs-interaction-service` 中补齐 8 条 V1 门户用户态写接口：

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

不迁移后台 `/api/v1/messages/**`，不新增 `q.interaction.watch_progress`
consumer，不新增 K8S Service、HTTPRoute 或 Deployment。

## 实现结果

新增 command service 和 request DTO：

```text
services/ircs-interaction-service/src/main/java/com/prodigalgal/ircs/interaction/InteractionCommandService.java
services/ircs-interaction-service/src/main/java/com/prodigalgal/ircs/interaction/ProgressReportRequest.java
services/ircs-interaction-service/src/main/java/com/prodigalgal/ircs/interaction/FeedbackCommandService.java
services/ircs-interaction-service/src/main/java/com/prodigalgal/ircs/interaction/FeedbackSubmitRequest.java
```

新增/更新 JDBC repository 和 Controller：

```text
services/ircs-interaction-service/src/main/java/com/prodigalgal/ircs/interaction/JdbcInteractionRepository.java
services/ircs-interaction-service/src/main/java/com/prodigalgal/ircs/interaction/JdbcFeedbackRepository.java
services/ircs-interaction-service/src/main/java/com/prodigalgal/ircs/interaction/PortalInteractionController.java
services/ircs-interaction-service/src/main/java/com/prodigalgal/ircs/interaction/FeedbackController.java
```

行为说明：

- 收藏 toggle 返回 V1 兼容字段 `favorited`。
- 观看历史本轮使用同步 PostgreSQL `upsert`，未来如恢复
  `q.interaction.watch_progress` consumer，需要复用 `(member_id, unified_video_id)`
  幂等边界。
- 反馈提交读取 `member.message.daily-limit`，缺失或非法值默认 5，`<=0` 表示不限额。
- 反馈内容做轻量 HTML 清洗，默认写入 `status=PENDING`、`is_public=false`。
- 反馈删除按 `member_id + id` 执行；不存在或不属于当前会员时 no-op，统一返回 204。

## 验证

本地验证：

```text
.\gradlew.bat :services:ircs-interaction-service:test
BUILD SUCCESSFUL

.\gradlew.bat build
BUILD SUCCESSFUL
```

ARM64 镜像：

```text
.\scripts\docker\push-project-image-jib.ps1 `
  -ProjectPath ':services:ircs-interaction-service' `
  -ImageName ircs-interaction-service `
  -Tag dev `
  -Architecture arm64 `
  -Os linux

Pushed harbor.mnnu.eu.org/ircs/ircs-interaction-service:dev for linux/arm64
```

K8S rollout：

```text
kubectl rollout restart deployment/ircs-interaction-service -n ircs-dev
kubectl rollout status deployment/ircs-interaction-service -n ircs-dev --timeout=240s

deployment "ircs-interaction-service" successfully rolled out
```

运行镜像：

```text
harbor.mnnu.eu.org/ircs/ircs-interaction-service@sha256:7dbaaef893c0465da52fbe94bd5a3d94973a6e3cf757bcf02f60c79ea75e84e4
```

ClusterIP smoke：

```text
POST /api/portal/interaction/favorites/{unifiedVideoId}        -> 200 {"favorited":true}
GET  /api/portal/interaction/favorites/{unifiedVideoId}/status -> 200 {"favorited":true}
POST /api/portal/interaction/favorites/{unifiedVideoId}        -> 200 {"favorited":false}
GET  /api/portal/interaction/favorites/{unifiedVideoId}/status -> 200 {"favorited":false}

POST   /api/portal/interaction/history      -> 202
GET    /api/portal/interaction/history      -> 200，返回 1 条 CODEX_02503_EP
DELETE /api/portal/interaction/history/{id} -> 204
POST   /api/portal/interaction/history      -> 202
DELETE /api/portal/interaction/history      -> 204

POST   /api/portal/feedback      -> 200，content 清洗为 CODEX_02503_feedback & ok
GET    /api/portal/feedback      -> 200，返回 1 条 PENDING 留言
DELETE /api/portal/feedback/{id} -> 204
```

清理结果：

```text
cleanup_remaining members,unified,user_messages,history,favorites=0,0,0,0,0
```

K8S 资源状态：

```text
No HTTPRoute in ircs-dev namespace.
pods=23/24
services=19/20
requests.cpu=795m/4
requests.memory=3968Mi/8Gi
limits.cpu=6700m/8
limits.memory=12672Mi/16Gi
```

最近 60 秒 interaction-service 日志未发现 `ERROR`、`Exception`、constraint 或
`HttpMessageNotReadable`。

## 剩余缺口

`interaction` 模块剩余未迁移路由为后台留言管理：

```text
GET    /api/v1/messages
POST   /api/v1/messages/{id}/reply
PUT    /api/v1/messages/{id}/visibility
DELETE /api/v1/messages/{id}
```

这些接口涉及管理员权限、审核/回复流程和后台运营面，建议作为单独 admin
interaction slice 处理。

