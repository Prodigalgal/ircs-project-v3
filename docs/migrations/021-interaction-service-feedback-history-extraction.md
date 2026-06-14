# 021 interaction-service feedback/history MVP 迁移记录

状态：已完成

更新时间：2026-06-04

## 背景

本迁移从 V1 interaction 边界中抽取只读优先 MVP，先接入公开留言墙和会员态
history/favorites 只读查询。写接口、watch progress consumer、admin message 管理和
DLQ 处理均后置。

## 实现范围

```text
services/ircs-interaction-service
deploy/k8s/dev/interaction-service-dev.yaml
```

已接入路由：

```text
GET /api/portal/feedback/wall
GET /api/portal/interaction/history
GET /api/portal/interaction/favorites
```

authenticated 查询复用 identity-service member JWT 语义，校验 `role=ROLE_MEMBER`、
JWT secret、iat-floor 和 `members.status`。

## K8S 结果

```text
deployment:    ircs-interaction-service Ready 1/1
service:       ircs-interaction-service ClusterIP 10.105.151.72
HTTPRoute:     none
resources:     request 25m/128Mi, limit 250m/512Mi
image:         harbor.mnnu.eu.org/ircs/ircs-interaction-service:dev
digest:        sha256:540c97ad07c292e866a0f30523ff5b20c5f6bf5a6ae702db353c85f8fd019bd4
```

## 验证结果

```text
.\gradlew.bat :services:ircs-interaction-service:test
.\gradlew.bat :services:ircs-interaction-service:bootJar
.\gradlew.bat build
kubectl apply --dry-run=server -f deploy\k8s\dev\interaction-service-dev.yaml
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-interaction-service' -ImageName ircs-interaction-service -Tag dev -Architecture arm64 -Os linux
kubectl rollout status deployment/ircs-interaction-service -n ircs-dev --timeout=240s
```

ClusterIP smoke：

```text
GET /api/portal/feedback/wall -> returned temporary public message
memberEmail leak check        -> false
GET /api/portal/interaction/history with ROLE_MEMBER JWT   -> 200 empty page
GET /api/portal/interaction/favorites with ROLE_MEMBER JWT -> 200 empty page
```

清理结果：

```text
members where email like codex-interaction-smoke-%: 0
user_messages where content like CODEX_INTERACTION_SMOKE_%: 0
RabbitMQ queues and DLQs: 0 messages
HTTPRoute in ircs-dev: none
```

## 遗留问题

- `POST /api/portal/feedback`、favorites toggle、history report/delete 仍后置。
- `q.interaction.watch_progress` consumer 尚未迁移。
- admin `/api/v1/messages/**` 写接口尚未迁移。

## 关联任务

- `tasks/done/017-02-interaction-feedback-history-mvp.md`
- `tasks/in-progress/011-mvp-service-onboarding-parent.md`
