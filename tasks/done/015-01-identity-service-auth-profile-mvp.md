# 015-01 identity-service 会员认证与 profile MVP 接入 task spec

当前状态：已完成

父任务：

- `tasks/in-progress/011-mvp-service-onboarding-parent.md`

## 背景

V3 已包含 `services:ircs-identity-service` skeleton，当前主线要求继续把 V1 单体拆成可独立接入的微服务。V1 会员身份能力分散在 `modules.portal`、`modules.interaction` 和 `infrastructure.security` 中，其中 `members` 表既承载认证凭证，也承载 profile、状态、积分和签到信息。

本任务用于固定 identity-service 会员认证与基础 profile MVP 的实现边界。当前
subagent 已完成本地代码和 dev manifest 草案，主 agent 已完成代码复核、ARM64 镜像推送、
K8S rollout 和 auth/profile MVP smoke。本任务完成后移动到 `tasks/done/`。

## 目标

- 在后续实现阶段，将 V1 会员自助认证能力接入 `services/ircs-identity-service`。
- 保持 `/api/portal/auth/**` 和基础 `/api/portal/profile/**` HTTP 语义兼容。
- 由 identity-service 直接读写 `members` 表。
- 保留 JWT 语义：member token 的 `subject` 为 member UUID，claim `role` 为 `ROLE_MEMBER`。
- 保留 Redis 中 PoW、激活码、重置密码令牌和 member status cache 的功能语义。

## 非目标

- 不修改 task-service 相关文件。
- 不实现 `/api/v1/auth/**` 管理员登录与改密。
- 不实现 `/api/v1/members/**` 后台会员管理。
- 不实现 `/api/portal/profile/avatar` 头像上传。
- 不迁移 interaction-service 的收藏、历史、留言业务。
- 不做物理数据库拆分。

## 上游来源

- `AGENTS.md`
- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/reports/015-identity-service-boundary-analysis.md`
- V1 `MemberAuthController`
- V1 `MemberProfileController`
- V1 `Member` / `MemberRepository`
- V1 `SecurityConfig` / `JwtAuthenticationFilter`
- V1 member 相关 Liquibase changelog

## 输入

V1 来源：

```text
D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-backend\src\main\java\com\prodigalgal\ircs\modules\portal\controller\MemberAuthController.java
D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-backend\src\main\java\com\prodigalgal\ircs\modules\portal\controller\MemberProfileController.java
D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-backend\src\main\java\com\prodigalgal\ircs\modules\portal\service\impl\MemberAuthServiceImpl.java
D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-backend\src\main\java\com\prodigalgal\ircs\modules\portal\service\impl\MemberProfileServiceImpl.java
D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-backend\src\main\java\com\prodigalgal\ircs\modules\interaction\model\domain\Member.java
D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-backend\src\main\java\com\prodigalgal\ircs\modules\interaction\repository\MemberRepository.java
D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-backend\src\main\resources\db\changelog\2026\01\22-01-add-members.sql
D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-backend\src\main\resources\db\changelog\2026\01\25-01-add-member-gamification.sql
```

V3 目标：

```text
services/ircs-identity-service
shared/ircs-common
shared/ircs-contracts
shared/ircs-messaging
platform/ircs-migrator
```

## 输出

目标输出：

- identity-service 中的会员 auth/profile Controller、DTO、Entity、Repository、Service。
- 与 V1 兼容的 HTTP 响应 shape。
- `members` 表读写逻辑。
- Redis key TTL 与状态缓存逻辑。
- 邮件发送队列 publish 逻辑。
- 本地单元测试或 slice test。
- 独立迁移记录与 K8S smoke 结果。

当前实际输出：

- `docs/reports/015-identity-service-boundary-analysis.md`
- `tasks/done/015-01-identity-service-auth-profile-mvp.md`
- `services/ircs-identity-service/build.gradle`
- `services/ircs-identity-service/src/main/resources/application.yaml`
- `services/ircs-identity-service/src/main/java/com/prodigalgal/ircs/identity/*.java`
- `services/ircs-identity-service/src/test/java/com/prodigalgal/ircs/identity/MemberAuthServiceTest.java`
- `services/ircs-identity-service/src/test/java/com/prodigalgal/ircs/identity/MemberProfileServiceTest.java`
- `deploy/k8s/dev/identity-service-dev.yaml`
- `deployment/ircs-identity-service` Ready 1/1
- `service/ircs-identity-service` ClusterIP `10.97.88.143`
- ARM64 image digest `sha256:51b2dbc5dffdd5affceed62762597f9e200ea494dd9f322ce52b5b85ee41d55c`
- `docs/migrations/018-identity-service-auth-profile-extraction.md`

## 影响范围

后续实现可影响：

```text
services/ircs-identity-service/**
shared/ircs-common/**
shared/ircs-contracts/**
shared/ircs-messaging/**
platform/ircs-migrator/src/main/resources/db/changelog/**
deploy/k8s/dev/identity-service-dev.yaml
```

当前集成阶段不应影响：

```text
services/ircs-task-service/**
```

## 依赖

- PostgreSQL 中的 `members` 表及既有 changelog。
- Redis / Valkey，用于 PoW、激活码、重置密码令牌和状态缓存。
- `system_configs` 或 config-service 内部 API，用于读取 JWT、member auth、mail、frontend URL 配置。
- RabbitMQ，用于发布 `q.notification.mail`。
- notification-worker，用于实际发送激活和重置密码邮件。
- Spring Security、BCrypt、JJWT。

## 风险

- V1 `Member` 位于 interaction 模块，迁移时需要重新划分 identity ownership，避免 interaction-service 与 identity-service 双写。
- 邮箱验证开启但邮件关闭时，注册流程会产生无法激活的待验证用户；测试配置必须明确。
- Redis key 与 V1 共用前缀时可能发生状态串扰；dev smoke 应使用临时账号并清理。
- profile 的认证保护不能只依赖全局 route matcher，必须确保方法或路径级认证生效。
- 后台 `/api/v1/members/**` 与 interaction 表耦合强，混入本任务会扩大验收范围。
- 头像上传跨 storage-service，当前后置；前端完整 profile 页可能需要临时隐藏或保留旧路由。

## 验收标准

已完成草案验收：

- 已创建边界分析报告。
- 已创建本 task spec。
- subagent 本地测试通过。
- dev manifest server dry-run accepted。
- bootJar 通过。
- 主 agent 于 2026-06-04 复跑当前工作树：
  `.\gradlew.bat :services:ircs-identity-service:test` 通过。
- 主 agent 于 2026-06-04 复跑当前工作树：
  `kubectl apply --dry-run=server -f deploy\k8s\dev\identity-service-dev.yaml`
  通过。

已完成主 agent 验收：

- `GET /api/portal/auth/pow` 可返回 PoW challenge。
- `POST /api/portal/auth/register` 可创建临时 member，并按配置决定 `PENDING_VERIFICATION` 或 `ACTIVE`。
- `POST /api/portal/auth/login` 可校验 PoW、密码和状态，并返回 `MemberTokenVO`。
- ARM64 镜像推送。
- `ircs-dev` rollout Ready 1/1。
- 无 HTTPRoute。
- `GET /api/portal/profile`、`POST /api/portal/profile/password`、`POST /api/portal/profile/check-in` 可通过 member token 调用。
- smoke 后清理临时 member；成功路径临时 member 剩余为 0。
- `PUT /api/portal/profile` 可通过 member token 调用，并确认 nickname 等 profile 字段更新。
- `POST /api/portal/auth/forgot-password` 在 dev 邮件关闭时返回 HTTP 400，不产生邮件队列残留。
- `POST /api/portal/auth/reset-password` 可消费临时 Valkey reset token，消费后 token 不存在。
- BANNED member 被拒绝访问。
- smoke 后清理临时 member、Valkey key 和测试队列残留。

后置验收：

- `POST /api/portal/auth/activate` 和 `resend-code` 需要在 dev 开启
  `APP_IDENTITY_EMAIL_VERIFY_ENABLED=true` 与邮件发送链路后单独 smoke。

## 测试边界

当前已执行：

```text
.\gradlew.bat :services:ircs-identity-service:test
kubectl apply --dry-run=server -f deploy\k8s\dev\identity-service-dev.yaml
.\gradlew.bat :services:ircs-identity-service:bootJar
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-identity-service' -ImageName ircs-identity-service -Tag dev -Architecture arm64 -Os linux
kubectl apply -f deploy\k8s\dev\identity-service-dev.yaml
kubectl rollout status deployment/ircs-identity-service -n ircs-dev --timeout=240s
集群内 GET /api/portal/auth/pow smoke
集群内 POST /api/portal/auth/register smoke
集群内 POST /api/portal/auth/login smoke
集群内 GET /api/portal/profile smoke
集群内 POST /api/portal/profile/check-in smoke
集群内 POST /api/portal/profile/password smoke
集群内 PUT /api/portal/profile smoke
集群内 relogin smoke
集群内 forgot-password mail disabled smoke
集群内 reset-password temp Valkey token smoke
集群内 BANNED login rejection smoke
临时 member 清理检查
Valkey auth/reset/pow key 清理检查
RabbitMQ queue/DLQ 消息数检查
```

后续全仓验证：

```text
.\gradlew.bat build
```

建议覆盖：

- register 成功与邮箱重复。
- login 成功、密码错误、BANNED、PENDING_VERIFICATION。
- activate code 正确、错误、过期。
- reset password token 正确、过期。
- profile get/update/change-password/check-in。
- JWT claim 与 `SECURITY_JWT_IAT_FLOOR`。

K8S rollout、ARM64 镜像和集群内 smoke 已完成。邮件验证开启后的
`activate`/`resend-code` 作为后续增强单独处理。

## 关联文档

- `docs/reports/015-identity-service-boundary-analysis.md`
- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/migrations/018-identity-service-auth-profile-extraction.md`

## 关联任务

- 父任务：`tasks/in-progress/011-mvp-service-onboarding-parent.md`
- 本任务：`tasks/done/015-01-identity-service-auth-profile-mvp.md`
- 后续建议拆分：`identity-service-admin-auth-mvp`
- 后续建议拆分：`identity-service-member-admin-management`
- 后续建议拆分：`identity-service-profile-avatar-storage-contract`

## 当前状态

已完成。主 agent 已完成代码复核、ARM64 镜像推送、K8S rollout、auth/profile MVP
smoke、临时数据清理和迁移记录回写。本任务移动到 `tasks/done/`。
