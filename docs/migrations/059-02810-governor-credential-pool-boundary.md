# 059 028-10 governor / credential pool 边界迁移记录

状态：已完成第一刀，后续迁移继续拆分

日期：2026-06-07

关联任务：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`

关联报告：

- `docs/reports/059-02810-production-governor-credential-pool-first-slice.md`

## 迁移背景

V3 已有 ops maintenance session/SSE contract、dev-safe unified reindex runner、mail disabled/failure
语义和 credential-service provider lease。`028-10` 需要把这些能力推进到 production governor
和 SMTP credential pool parity，但不能在 dev 环境打开破坏性 runner 或真实 SMTP。

## 迁移原则

- production runner 默认关闭，必须经过 environment gate 和 runner registry。
- dev 环境只允许 dry-run、fixture 或 capped message runner。
- credential-service 是 SMTP secret 的唯一租约来源。
- notification-worker 不持久化 password，不在日志输出 secret。
- send history 记录状态和失败分类，不替代 RabbitMQ retry/DLQ。
- 真实 SMTP smoke 只能在 staging/production-like 环境单独确认。

## 第一刀迁移

本轮只做本地可测试的 credential pool guard：

```text
CredentialService.leaseProviderCredentials()
  -> service-level limit cap
  -> limit <= 0 时返回空租约

CredentialService.leaseMailCredentials()
  -> provider 固定为 MAIL
  -> 必须包含 username 和 password
  -> 复用同一 limit cap
```

该迁移不新增 DB 表、不新增 HTTPRoute、不执行 SMTP。

## 后续迁移拆分

### 028-10-B production maintenance governor registry

范围：

```text
services/ircs-ops-service/**
docs/reports/*02810*governor*.md
docs/migrations/*02810*governor*.md
tasks/in-progress/028-10-production-governor-credential-pool-parity.md
```

验收：

- runner registry 至少记录 `riskLevel`、`devAllowed`、`supportsDryRun`、`maxLimit`。
- unsupported/high-risk runner 在 dev 输出稳定 refused/guarded 事件。
- 不新增 HTTPRoute，不 rollout。

### 028-10-C SMTP credential adapter

范围：

```text
services/ircs-notification-worker/**
services/ircs-credential-service/**
docs/reports/*02810*credential*.md
docs/migrations/*02810*credential*.md
```

验收：

- `APP_NOTIFICATION_MAIL_ENABLED=false` 仍然 no-op。
- enabled 时从 credential-service 获取 MAIL lease。
- 无 credential、credential-service 不可用、SMTP 失败分别有稳定 failure code。
- 本地测试使用 mock，不执行真实 SMTP。

### 028-10-D send history

范围待独立 task spec 固化。

验收：

- 记录 message status、template、recipient hash 或脱敏 recipient、credential id、failure code。
- 不记录 password、token 或完整 secret。
- retry/DLQ 与 send history 状态一致。

### 028-10-E staging SMTP smoke

范围待人工确认 staging/production-like 环境。

验收：

- 使用隔离 SMTP 账号。
- 低频限额和明确收件目标。
- smoke 能输出 `MARK` 或 `SKIPPED`，不伪造 success。

## 回滚策略

第一刀只改 service-level guard 和单元测试；如出现回归，可在 credential-service 内回退该小块，不需要
DB rollback、K8S rollback 或队列清理。

## 第一刀实现结果

- 已在 `CredentialService.leaseProviderCredentials()` 增加 service-level limit guard。
- 已新增 `CredentialService.leaseMailCredentials(int limit)`，用于固定 SMTP credential pool
  的最小选择规则。
- 已新增本地单元测试覆盖 generic lease cap、zero limit skip 和 MAIL lease 必填 secret。

## 验证结果

```powershell
.\gradlew.bat :services:ircs-credential-service:test --rerun-tasks
BUILD SUCCESSFUL in 8s
```

```text
CredentialServiceTest tests=10, skipped=0, failures=0, errors=0
```

## 未执行项

- 未执行真实 SMTP。
- 未新增 HTTPRoute。
- 未 rollout。
- 未执行 destructive maintenance runner。

## 当前状态

2026-06-07：第一刀迁移已完成；production governor registry、notification SMTP adapter、
send history 和 staging SMTP smoke 继续拆分。
