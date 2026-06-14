# 028-10-H notification mail send history dev migration / ops query 需求

状态：已完成

日期：2026-06-09

## 背景

`028-10-E` 已完成 `notification_mail_send_history` schema contract 和 ops-service
只读查询接口。`028-10-G` 已把该 schema 接入 `platform/ircs-migrator`，并通过本地测试
和 K8S server dry-run，但 dev DB 仍未实际落表，live ops-service 仍对
`/api/v1/ops/notification-mail-send-history` 返回 404。

本轮用户已允许真实测试，目标是把前序 dry-run 推进到 dev 环境真实 DB migration 和
ops-service 查询闭环。SMTP staging live preflight 仍被严格隔离，本轮不得发送真实 SMTP。

## 目标

- 构建并推送 ARM 可运行的 dev `ircs-migrator` image。
- 运行一次 dev `ircs-migrator` Job，证明 `notification_mail_send_history` 表已存在于 dev DB。
- 如 live `ircs-ops-service:dev` 仍缺 send history endpoint，则构建、推送并 rollout
  ops-service dev image。
- 验证 `/api/v1/ops/notification-mail-send-history` 和 `/summary` 不再返回 404。
- 保持 `APP_MAIL_ENABLED=false` 或不触发任何发送路径，明确记录 SMTP 未发送证据。
- 保持 dev 低资源 request/limit、ClusterIP-only，不新增 HTTPRoute 或公网入口。
- Job 完成后清理可清理资源；如保留残留资源，记录原因和下一步。

## 范围

可写入：

```text
platform/ircs-migrator/**
services/ircs-ops-service/**
deploy/k8s/dev/*migrator*
deploy/k8s/dev/*ops-service*
scripts/smoke/*notification*/*mail*
scripts/smoke/*notification*
scripts/smoke/*mail*
docs/**
tasks/**
```

不写入：

```text
config-service Rabbit publisher
content-service listener
Douban provider
storage-service
portal/admin/BFF
```

## 风险

- registry push 可能失败或推送到非 ARM manifest，导致 dev Job `ImagePullBackOff` 或运行失败。
- dev namespace 资源紧张，migrator Job 和 ops-service rollout 必须保持低 request/limit。
- ops endpoint 404 可能来自 live image 旧代码，也可能来自 service/port-forward 指向错误。
- send history 表存在只证明 schema 与 ops 查询闭环，不证明真实 SMTP 投递。
- 并行 worker 可能同时 rollout dev 服务，执行前后需要读取 live 状态并避免 revert 他人变更。

## 验收标准

```text
MARK 1/6 本地需求、父任务/子任务/task spec 已在实现前创建
MARK 2/6 dev migrator image 已构建推送并记录 digest
MARK 3/6 dev migrator Job 成功完成，或失败时有日志、清理和下一步
MARK 4/6 dev DB 存在 notification_mail_send_history 表及索引证据
MARK 5/6 ops-service send history page 与 summary endpoint live smoke 不再 404
MARK 6/6 SMTP 未发送、APP_MAIL_ENABLED 未打开、Job/port-forward 等临时资源已清理或说明保留原因
```

## 关联任务

- 父任务：`tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- 子任务：`tasks/done/028-10-h-notification-mail-send-history-dev-migration-ops-query.md`

## 验收结果

```text
MARK 1/6 PASS 本地需求、父任务/子任务/task spec 已在实现前创建
MARK 2/6 PASS dev migrator image 已构建推送并记录 digest
MARK 3/6 PASS dev migrator Job 成功完成并已清理
MARK 4/6 PASS dev DB 存在 notification_mail_send_history 表及索引证据
MARK 5/6 PASS ops-service send history page 与 summary endpoint live smoke 不再 404
MARK 6/6 PASS SMTP 未发送、APP_MAIL_ENABLED=false、临时 Job/port-forward 已清理
```

## 完成说明

- `ircs-migrator:dev` 已推送并由 dev Job 实际运行，image digest 为
  `sha256:f6b8be42100136c204800cb6ad88c082a1aad56f04e10337c89ca22ca829c578`。
- `notification_mail_send_history` 已存在于 dev DB，列数 `13`，业务索引数 `4`。
- `ircs-ops-service:dev` 已 rollout 到 digest
  `sha256:feca779a5bf0c8157a2a2fc8424aee12f266deb9df525ecc05da5f12e4f8b10c`。
- live smoke 返回 `PASS`，summary/page endpoint 可查询，`pageTotalElements=0`，
  `totalLast24h=0`。
- `APP_MAIL_ENABLED=false`，smoke 记录 `sent=false`、`passwordPrinted=false`、
  `recipientPrinted=false`，本轮未发送真实 SMTP。
