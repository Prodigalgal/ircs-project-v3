# 017-02 interaction-service feedback/history MVP 子任务

状态：已完成

父任务：

- `tasks/in-progress/011-mvp-service-onboarding-parent.md`

关联文档：

- `docs/reports/017-portal-interaction-magnet-boundary-analysis.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`
- `docs/requirements/011-mvp-service-onboarding-acceleration.md`

## 背景

`ircs-interaction-service` 尚未接入 `ircs-dev`。V1 interaction 边界覆盖
收藏、观看历史、留言墙和后台留言管理，来源包括
`PortalInteractionController`、`PortalMessageController`、
`AdminMessageController` 与 `WatchHistoryConsumer`。该服务既有用户态 HTTP
写入，也有 `q.interaction.watch_progress` 队列消费；在 `identity-service`
尚未接入前，完整端到端验收存在 auth 依赖。本子任务先固定 feedback/history
MVP 边界，实施时再决定是否仅做 public wall + 只读 authenticated 查询。

## 目标

- 为 `ircs-interaction-service` 接入 feedback/history 只读优先 MVP。
- 优先保留 `GET /api/portal/feedback/wall` 公开留言墙查询。
- 在具备测试 token 或 identity-service MVP 后，保留
  `GET /api/portal/interaction/history`。
- 在具备测试 token 或 identity-service MVP 后，保留
  `GET /api/portal/interaction/favorites`。
- 使用当前 PostgreSQL `user_messages`、`member_watch_histories`、
  `member_favorites` 作为迁移桥。
- 后续实施时完成本地测试、server dry-run、ARM64 镜像、K8S rollout 和
  ClusterIP smoke。

## 非目标

- 不接入 `POST /api/portal/interaction/history`。
- 不迁移 `q.interaction.watch_progress` consumer。
- 不接入 `POST /api/portal/interaction/favorites/{unifiedVideoId}`。
- 不接入 history/favorites 删除接口。
- 不接入 `POST /api/portal/feedback` 和 `DELETE /api/portal/feedback/{id}`。
- 不接入 `/api/v1/messages/**` admin 写接口。
- 不做 interaction 数据合并迁移。
- 不新增公网 HTTPRoute。
- 不拆分独立数据库。

## 上游来源

- `docs/reports/017-portal-interaction-magnet-boundary-analysis.md`
- V1 `PortalInteractionController`
- V1 `PortalMessageController`
- V1 `AdminMessageController`
- V1 `WatchHistoryConsumer`
- V1 `MemberFavoriteServiceImpl`
- V1 `WatchHistoryServiceImpl`
- V1 `UserMessageServiceImpl`
- V1 `huawai/src/lib/api/interaction.ts`
- V1 `huawai/src/lib/types/index.ts`

## 输入

- `services/ircs-interaction-service`
- `GET /api/portal/feedback/wall`
- `GET /api/portal/interaction/history`
- `GET /api/portal/interaction/favorites`
- `UserMessageDTO`
- `HistoryRecordDTO`
- `user_messages`
- `member_watch_histories`
- `member_favorites`
- `members`
- `unified_videos`
- `cover_images`
- `videos`
- `episodes`

## 输出

- `ircs-interaction-service` feedback/history MVP 实现。
- `GET /api/portal/feedback/wall` 兼容响应。
- 可验证条件满足后提供 history/favorites 只读响应。
- dev Deployment/Service。
- 迁移文档、K8S 快照和任务状态回写。

## 影响范围

```text
services/ircs-interaction-service
deploy/k8s/dev/interaction-service-dev.yaml
docs/migrations
docs/k8s
deploy/k8s/README.md
tasks
```

## 依赖

- 当前 V3 shared PostgreSQL。
- history/favorites 查询需要会员身份来源；真实 portal smoke 依赖
  `identity-service` 或明确的内部测试 token 策略。
- 观看进度写入后续依赖 RabbitMQ `x.interaction` /
  `q.interaction.watch_progress`。
- favorites/history card 投影依赖 `unified_videos` 和封面 URL 解析。
- feedback submit 每日限额后续依赖 `config-service` 或共享 DB 读取
  `system_configs`。

## 风险

- 当前 identity-service auth/profile MVP 已收口，interaction-service 可复用
  identity member token 做 authenticated smoke；但仍必须验证 interaction 自身的权限、
  写入幂等和 cleanup。
- V1 history 写路径是 HTTP publish + consumer batch upsert，若一起迁移会扩大到
  RabbitMQ、幂等 upsert 和 DLQ 处理。
- `user_messages` 写路径涉及 HTML 清洗、每日限额、删除权限和 admin 可见性，MVP
  不应混入。
- `members` 当前属于 identity-service 目标边界，interaction-service 只应引用读取
  必要展示字段，不应写会员资料。

## 验收标准

- `GET /api/portal/feedback/wall` 返回公开留言分页，未公开留言不出现在响应中。
- public wall 响应不泄露 `memberEmail`。
- history/favorites 如进入本 MVP，响应字段可被 V1 `InteractionRecord` 消费。
- 只读 smoke 不新增 `member_watch_histories`、`member_favorites` 或
  `user_messages` 业务数据。
- 服务在 `ircs-dev` Ready 1/1。
- 未新增公网 HTTPRoute。

## 测试边界

```text
.\gradlew.bat :services:ircs-interaction-service:test
.\gradlew.bat build
kubectl apply --dry-run=server -f deploy\k8s\dev\interaction-service-dev.yaml
kubectl rollout status deployment/ircs-interaction-service -n ircs-dev --timeout=240s
集群内 GET /api/portal/feedback/wall smoke
具备测试 token 后执行 GET /api/portal/interaction/history smoke
具备测试 token 后执行 GET /api/portal/interaction/favorites smoke
```

## 关联任务

- 父任务：`tasks/in-progress/011-mvp-service-onboarding-parent.md`
- 同批边界分析：`tasks/in-progress/017-01-portal-read-composition-mvp.md`
- 同批边界分析：`tasks/done/017-03-magnet-provider-cache-mvp.md`

## 验证结果

已完成：

```text
.\gradlew.bat :services:ircs-interaction-service:test
.\gradlew.bat :services:ircs-interaction-service:bootJar
.\gradlew.bat build
kubectl apply --dry-run=server -f deploy\k8s\dev\interaction-service-dev.yaml
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-interaction-service' -ImageName ircs-interaction-service -Tag dev -Architecture arm64 -Os linux
kubectl rollout status deployment/ircs-interaction-service -n ircs-dev --timeout=240s
ClusterIP GET /api/portal/feedback/wall smoke
ClusterIP GET /api/portal/interaction/history smoke with ROLE_MEMBER JWT
ClusterIP GET /api/portal/interaction/favorites smoke with ROLE_MEMBER JWT
```

smoke 结果：

```text
GET /api/portal/feedback/wall 返回临时公开留言，不泄露 memberEmail
GET /api/portal/interaction/history 返回 authenticated 空分页
GET /api/portal/interaction/favorites 返回 authenticated 空分页
临时 members/user_messages 清理后均为 0
Deployment Ready 1/1
Service ClusterIP 10.105.151.72
HTTPRoute none
image digest sha256:540c97ad07c292e866a0f30523ff5b20c5f6bf5a6ae702db353c85f8fd019bd4
```

## 当前状态

已完成。`ircs-interaction-service` 已在 `ircs-dev` Ready 1/1；写接口和
`q.interaction.watch_progress` consumer 仍后置。
