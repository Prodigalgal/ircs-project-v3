# 015 identity-service 边界分析报告

状态：完成（边界分析完成；auth/profile MVP 后续已部署并验收）

更新时间：2026-06-03

## 背景

当前主线目标是将 `D:\WorkSpace\Project\ircs\ircs-project-v1` 完整拆分迁移为 V3 微服务。本报告形成时 `ircs-dev` 已接入 10/18；identity-service smoke 收口时已部署并 Ready 的业务服务为 14/18；截至 2026-06-04 scraper-service 接入后，主线已部署并 Ready 的业务服务为 15/18。`services:ircs-identity-service` 已完成 ARM64 镜像推送、K8S rollout 和 auth/profile MVP smoke。

本报告主体仍是 V1 中 auth、member、profile 相关边界分析；后续实现、部署和 smoke 状态以
`tasks/done/015-01-identity-service-auth-profile-mvp.md` 以及
`docs/reports/021-documentation-state-sync-after-identity-rollout.md` 为准。

## V1 来源清单

### Controller

```text
ircs-backend/src/main/java/com/prodigalgal/ircs/modules/portal/controller/MemberAuthController.java
ircs-backend/src/main/java/com/prodigalgal/ircs/modules/portal/controller/MemberProfileController.java
ircs-backend/src/main/java/com/prodigalgal/ircs/modules/portal/controller/MemberAdminController.java
ircs-backend/src/main/java/com/prodigalgal/ircs/modules/system/controller/AuthController.java
```

### DTO / VO / command / enum

```text
modules/portal/model/dto/MemberRegisterReq.java
modules/portal/model/dto/MemberLoginReq.java
modules/portal/model/dto/AccountActivateReq.java
modules/portal/model/dto/ForgotPasswordReq.java
modules/portal/model/dto/ResetPasswordReq.java
modules/portal/model/dto/ProfileUpdateReq.java
modules/portal/model/dto/PasswordChangeReq.java
modules/portal/model/dto/MemberDTO.java
modules/portal/model/dto/CheckInResult.java
modules/portal/model/vo/MemberTokenVO.java
modules/portal/model/command/MemberAdminUpdateCmd.java
modules/portal/model/command/MemberStatusUpdateCmd.java
modules/portal/model/enums/MemberStatus.java
modules/portal/model/enums/MemberTitle.java
```

### Entity / Repository / Service

```text
modules/interaction/model/domain/Member.java
modules/interaction/model/domain/MemberFavorite.java
modules/interaction/model/domain/MemberWatchHistory.java
modules/interaction/model/domain/UserMessage.java
modules/interaction/repository/MemberRepository.java
modules/portal/service/impl/MemberAuthServiceImpl.java
modules/portal/service/impl/MemberProfileServiceImpl.java
modules/portal/service/impl/MemberStatusCacheServiceImpl.java
modules/portal/service/impl/MemberAdminServiceImpl.java
```

### 安全、配置与前端入口

```text
infrastructure/config/SecurityConfig.java
infrastructure/security/filter/JwtAuthenticationFilter.java
common/enums/SystemConfigKey.java
common/enums/RedisKeyPrefix.java
huawai/src/lib/api/auth.ts
ircs-frontend/src/features/auth/authApi.js
ircs-frontend/src/features/members/membersApi.js
```

## HTTP 边界

### 本 MVP 建议实现

本 MVP 建议先实现会员自助身份和基础 profile，不接后台会员管理、不接头像上传：

```text
GET  /api/portal/auth/pow
POST /api/portal/auth/register
POST /api/portal/auth/activate
POST /api/portal/auth/login
POST /api/portal/auth/resend-code
POST /api/portal/auth/forgot-password
POST /api/portal/auth/reset-password

GET  /api/portal/profile
PUT  /api/portal/profile
POST /api/portal/profile/password
POST /api/portal/profile/check-in
```

关键兼容点：

- `register` 和 `login` 请求体都包含 `PoWVerification`。
- `login` 成功返回 `MemberTokenVO`，JWT `subject` 为 member UUID，claim `role` 为 `ROLE_MEMBER`。
- `profile` 通过 `Authorization: Bearer <token>` 解析 `@AuthenticationPrincipal` 中的 member id。
- `check-in` 使用 `Asia/Shanghai` 日期，更新 `points`、`experience`、`last_check_in_date`、`check_in_streak`。

### 目标 ownership 但本 MVP 后置

```text
POST /api/portal/profile/avatar
```

V1 中该接口属于 `MemberProfileController`，但实现依赖图片安全校验、文件归一化、本地存储、`SystemConfigKey.STORAGE_*`、`R2_ENABLED` 和 `QueueTopic.SYNC_AVATAR`。V3 已有 storage-service，本接口应在补齐 storage contract 后再接入，避免 identity-service 复制存储职责。

```text
GET    /api/v1/members
GET    /api/v1/members/{id}/favorites
GET    /api/v1/members/{id}/history
PUT    /api/v1/members/{id}
PUT    /api/v1/members/{id}/status
DELETE /api/v1/members/{id}
```

这些是后台管理面接口，需要 `ROLE_ADMIN`，并跨 `member_favorites`、`member_watch_histories`、`user_messages` 与内容表。建议作为 admin member management 后续任务处理。

```text
GET  /api/v1/auth/pow
POST /api/v1/auth/login
POST /api/v1/auth/change-password
```

这是后台管理员登录与改密入口，使用 `system_configs` 中的 `ADMIN_USERNAME`、`ADMIN_PASSWORD`、`JWT_SECRET`、`SECURITY_JWT_IAT_FLOOR`。它属于 identity-service 的目标候选 ownership，但本 MVP 可后置，避免同时引入 admin auth 与 member auth 两套验收口径。

## 数据表边界

### identity-service MVP 直接读写

```text
members
```

当前字段来自 V1 `Member` Entity 与 changelog：

```text
id
created_at
updated_at
version
email
password_hash
nickname
avatar_url
role
status
experience
points
last_check_in_date
check_in_streak
```

约束与索引：

```text
pk_members
uc_members_email
idx_members_email
idx_members_points
```

### MVP 读取依赖

```text
system_configs
```

identity-service MVP 需要读取以下配置键或等价配置 contract：

```text
security.jwt.secret
security.jwt.iat-floor
member.auth.code.validity-seconds
member.auth.code.rate-limit-seconds
member.register.email-verify.enabled
member.register.time-window.enabled
member.register.time-window.start
member.register.time-window.end
member.register.timezone
app.mail.enabled
app.frontend.url
```

### Redis 状态边界

V1 使用的 Redis key 前缀：

```text
auth:status:{memberId}
auth:activate:{email}
auth:reset_token:{token}
pow:challenge:{challengeId}
traffic:limit:{key}
```

MVP 至少需要保留 PoW challenge、激活码、重置密码令牌与 member status cache；头像和更复杂流量控制可随后续 profile/avatar 增强处理。

### 外部引用但不由 MVP 写入

```text
member_favorites
member_watch_histories
user_messages
```

这些表通过 `member_id` 依赖 `members(id)`，但业务 ownership 更接近 interaction-service。当前 Entity 形态中：

- `member_favorites` 使用 `member_id + unified_video_id` 唯一约束。
- `member_watch_histories` 使用 `member_id + unified_video_id` 唯一约束，并带 `last_video_id`、`last_episode_id`、`episode_name`。
- `user_messages` 使用 `member_id`、`status`、`is_public` 查询索引。

注意：V1 changelog 早期先以 `video_id` 建立收藏/历史，后续 `2026/01/26-02-refactor-interactions-to-unified.sql` 迁移到 `unified_video_id`。实际实现前需要以目标环境 Liquibase 状态为准。

## 队列边界

MVP auth/password reset 可能发布邮件：

```text
QueueTopic.SEND_MAIL -> q.notification.mail
MailMessageDTO
```

后续 avatar 增强可能发布：

```text
QueueTopic.SYNC_AVATAR -> q.storage.avatar_sync
payload: memberId UUID
```

identity-service MVP 只应发布 notification mail，不消费队列。

## 非目标

- 不实现任何代码。
- 不修改 K8S、HTTPRoute、Deployment、Service 或镜像。
- 不修改 task-service 相关文件。
- 不修改 `docs/index.md`、`tasks/index.md`、011 父任务或 011 需求文档。
- 不做物理数据库拆分。
- 不实现后台 `/api/v1/members/**` 管理面。
- 不实现 `/api/portal/profile/avatar` 头像上传。
- 不把 interaction-service 的收藏、历史、留言业务迁入 identity-service。

## 风险

- V1 `Member` Entity 位于 `modules.interaction`，但数据 ownership 应迁到 identity-service；其他服务仍有 `member_id` 外键和只读引用。
- JWT 验证逻辑依赖统一 `security.jwt.secret` 和 `security.jwt.iat-floor`，服务拆分时必须保证 token 签发与各服务验证一致。
- V1 `SecurityConfig` 对 `/api/portal/**` 是 permitAll，`MemberProfileController` 依赖 method-level `@PreAuthorize("isAuthenticated()")`；V3 实现时不能遗漏 profile 的认证保护。
- 注册开启邮箱验证但 `app.mail.enabled=false` 时，V1 会创建待激活用户但无法发送验证码；MVP smoke 需要显式选择配置状态。
- 激活码、重置密码令牌、状态缓存依赖 Redis TTL，迁移时要避免与 V1 或其他服务 key namespace 冲突。
- `MemberAdminController.delete` 会受 interaction 表外键影响；后台会员管理不应混入本 MVP。
- 头像上传跨 storage-service、R2 和本地静态资源路径，过早接入会扩大验收面。

## 验收建议

文档阶段验收：

- 本报告列出 V1 auth/member/profile 的 Controller、DTO、Entity、Repository、配置和表名。
- task spec 明确本 MVP 的输入、输出、非目标、风险和测试边界；当前任务已移动到
  `tasks/done/015-01-identity-service-auth-profile-mvp.md`，当前已部署 Ready，并已完成
  auth/profile MVP smoke 收口。

后续实现阶段验收：

- 本地测试覆盖注册、登录、JWT claim、profile 读取、profile 更新、改密、签到。
- smoke 使用临时 member email，结束后清理 `members`、Redis 激活码/重置令牌/状态缓存以及可能产生的邮件队列测试消息。
- 不新增 dev HTTPRoute。
- 不修改 task-service 或 011 文档。

## 后续增强

- 接入 `/api/v1/auth/**` 管理员登录与改密，并明确 admin token 与 member token 的兼容策略。
- 接入 `/api/v1/members/**` 后台会员管理，但历史/收藏查询需与 interaction-service 定义边界。
- 为 profile avatar 补 storage-service contract，再接 `/api/portal/profile/avatar`。
- 为其他服务提供内部 member lookup/status contract，减少跨服务直接读 `members`。
- 后续逐步从直接读 `system_configs` 迁到 config-service 内部 API。
