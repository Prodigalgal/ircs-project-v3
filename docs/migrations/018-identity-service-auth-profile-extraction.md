# 018 identity-service auth/profile MVP 迁移记录

状态：已完成

完成日期：2026-06-04

关联任务：

- `tasks/done/015-01-identity-service-auth-profile-mvp.md`

上游文档：

- `docs/reports/015-identity-service-boundary-analysis.md`
- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`

## 背景

V1 会员身份能力分散在 `modules.portal`、`modules.interaction` 和
`infrastructure.security` 中。V3 目标边界将会员自助认证、member token 签发、基础
profile、签到和密码重置归入 `ircs-identity-service`。

本迁移抽取 auth/profile MVP，并保持无公网 HTTPRoute，只在 `ircs-dev` 内通过
ClusterIP smoke。

## 本轮范围

本轮接入以下路径：

```text
GET  /api/portal/auth/pow
POST /api/portal/auth/register
POST /api/portal/auth/login
POST /api/portal/auth/forgot-password
POST /api/portal/auth/reset-password

GET  /api/portal/profile
PUT  /api/portal/profile
POST /api/portal/profile/password
POST /api/portal/profile/check-in
```

本轮验证了 `resend-code` 与 `activate` 在 dev 配置下不作为完成门槛：当前
`APP_IDENTITY_EMAIL_VERIFY_ENABLED=false` 且 `APP_MAIL_ENABLED=false`，注册直接进入
`ACTIVE`，激活码发送链路后置到邮件验证 slice。

## 非目标

- 不接入 `/api/v1/auth/**` 管理员登录与改密。
- 不接入 `/api/v1/members/**` 后台会员管理。
- 不接入 `/api/portal/profile/avatar`。
- 不迁移 interaction-service 的收藏、历史、留言业务。
- 不新增公网 HTTPRoute。
- 不拆分物理数据库。

## 实现输出

```text
services/ircs-identity-service/build.gradle
services/ircs-identity-service/src/main/resources/application.yaml
services/ircs-identity-service/src/main/java/com/prodigalgal/ircs/identity/*
services/ircs-identity-service/src/test/java/com/prodigalgal/ircs/identity/MemberAuthServiceTest.java
services/ircs-identity-service/src/test/java/com/prodigalgal/ircs/identity/MemberProfileServiceTest.java
deploy/k8s/dev/identity-service-dev.yaml
```

## K8S 部署

```text
namespace:     ircs-dev
deployment:    ircs-identity-service Ready 1/1
service:       ircs-identity-service ClusterIP 10.97.88.143
HTTPRoute:     none
image:         harbor.mnnu.eu.org/ircs/ircs-identity-service:dev
image digest:  sha256:51b2dbc5dffdd5affceed62762597f9e200ea494dd9f322ce52b5b85ee41d55c
resources:     request 25m/128Mi, limit 250m/512Mi
```

## 验证

已执行：

```text
.\gradlew.bat :services:ircs-identity-service:test
.\gradlew.bat :services:ircs-identity-service:bootJar
kubectl apply --dry-run=server -f deploy\k8s\dev\identity-service-dev.yaml
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-identity-service' -ImageName ircs-identity-service -Tag dev -Architecture arm64 -Os linux
kubectl apply -f deploy\k8s\dev\identity-service-dev.yaml
kubectl rollout status deployment/ircs-identity-service -n ircs-dev --timeout=240s
```

Smoke 结果：

```text
email: codex-identity-smoke-20260604123051@example.invalid
memberId: 143dfd36-8aab-488f-b928-8972a5f2f247
register verifyRequired: false
PUT /api/portal/profile nickname: Codex Put 20260604123051
GET /api/portal/profile nickname: Codex Put 20260604123051
forgot-password with mail disabled: HTTP 400
manual reset token consumed after reset-password: 0
relogin after reset-password: ACTIVE
BANNED login rejected: HTTP 403
remaining temporary members: 0
auth status key after cleanup: 0
```

残留检查：

```text
PostgreSQL codex identity smoke members: 0
Valkey auth smoke keys: 0
Valkey pow challenge keys after cleanup: 0
RabbitMQ all queues and DLQs messages: 0
HTTPRoute in ircs-dev: none
```

## 当前配额

identity-service 接入后：

```text
pods:            19 / 20
services:        20 / 20
requests.cpu:    700m / 4
requests.memory: 3456Mi / 8Gi
limits.cpu:      5750m / 8
limits.memory:   10752Mi / 16Gi
```

identity-service 接入后当时最紧限制为 `services=20/20`。随后
`docs/reports/022-dev-service-quota-worker-service-pruning.md` 已通过裁剪 worker-only
Service 将 Service 使用量降到 `15/20`。

## 后续增强

- `identity-service-admin-auth-mvp`：接入 `/api/v1/auth/**` 管理员登录与改密。
- `identity-service-member-admin-management`：接入 `/api/v1/members/**` 后台会员管理。
- `identity-service-profile-avatar-storage-contract`：与 storage-service 明确头像上传 contract。
- `identity-service-email-verification-slice`：在 dev 开启邮件验证后单独 smoke
  `activate`、`resend-code` 和真实邮件队列。
