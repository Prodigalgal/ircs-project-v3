# 028-10-I notification SMTP staging-only one-shot live smoke 需求

状态：已完成

日期：2026-06-09

## 背景

`028-10-H` 已完成 dev DB migration 与 ops-service 查询闭环：
`notification_mail_send_history` 表存在，`/api/v1/ops/notification-mail-send-history`
和 `/summary` 可查。SMTP 真实发送仍未执行。

本轮用户允许真实外网测试，但邮件发送必须满足严格 staging-only 门禁。任何门禁不满足，
只能停在 preflight 并记录阻塞证据，不得发送邮件。

## 目标

- 读取当前 dev/K8S/credential 配置，确认 SMTP host、port、from、recipient、staging 标识，
  但不输出 password、token、recipient 或 secret 全量。
- 只有在 staging-only recipient 明确存在、不会发往真实用户或批量列表、临时开关可恢复、
  send history 可查且输出已脱敏时，才允许发送最多 1 封测试邮件。
- 临时开启必要开关后必须恢复原值；如不能安全发送，记录阻塞证据。
- 验证 `notification_mail_send_history` 审计写入，关注 provider/delivery mode、status、
  error 或 null、timestamp、recipient 脱敏与 ops endpoint 查询结果。
- 清理 port-forward 等临时资源；send history 审计记录如产生则按业务审计预期保留。

## 范围

可写入：

```text
scripts/smoke/notification-mail-smtp-02810f-preflight.ps1
deploy/k8s/dev/notification-worker*
docs/**
tasks/**
```

不写入：

```text
services/ircs-config-service/**
services/ircs-content-service/**
Douban provider
services/ircs-storage-service/**
services/ircs-ops-service/** schema/migrator
```

## 风险

- 当前 dev SMTP host/from 可能指向真实邮箱服务，不能仅凭 `AllowSend` 放行。
- 临时打开 `APP_MAIL_ENABLED`、listener 或 send history 后如果未恢复，会造成后续误发风险。
- Rabbit 投递失败或消费者未启动时，可能出现队列残留，需要记录并按脚本清理边界处理。
- `SENT` 只表示 notification-worker delivery 层接受发送意图，不等同于收件箱已收到。
- 并行 worker 可能同时更新 dev deployment，执行前后必须读取 live 状态，不得 revert 他人改动。

## 验收标准

```text
MARK 1/6 028-10-I docs/task spec 已在实现或 live smoke 前创建
MARK 2/6 preflight 已读取 K8S/credential/ops/send history 状态，未输出 secret 或 recipient 全量
MARK 3/6 staging-only、safe recipient、one-shot、history 可查、开关可恢复门禁已执行
MARK 4/6 若门禁通过，最多发送 1 封测试邮件；若门禁不通过，停止在 preflight 并记录原因
MARK 5/6 send history 审计通过 DB 或 ops endpoint 查询到，或明确说明因未发送无法产生
MARK 6/6 临时 APP_MAIL_ENABLED 等开关和 port-forward 已恢复/清理，审计记录保留原因已说明
```

## 关联任务

- 父任务：`tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- 子任务：`tasks/done/028-10-i-notification-smtp-staging-one-shot-live-smoke.md`

## 验收结果

```text
MARK 1/6 PASS 028-10-I docs/task spec 已在 live smoke 前创建
MARK 2/6 PASS preflight 已读取 K8S/credential/ops/send history 状态，未输出 secret 或 recipient 全量
MARK 3/6 PASS staging-only、safe recipient、one-shot、history 可查、开关可恢复门禁已执行
MARK 4/6 PASS 门禁不通过，停止在 preflight，未发送 SMTP
MARK 5/6 PASS send history 表和 ops endpoint 可查；因未发送，未产生新增审计 row
MARK 6/6 PASS 未执行 runtime patch，本轮端口转发已清理，APP_MAIL_ENABLED 仍为 false
```

## 完成说明

- 真实 SMTP：未发送。带 `-AllowSend` 和安全测试收件人形态时，仍因
  `SMTP_CONFIG_NOT_STAGING_ONLY` 阻断。
- live 配置摘要：`host=smtp.gmail.com`、`port=465`、from 已脱敏为
  `HuaWai System <hu***@gmail.com>`，`APP_MAIL_ENABLED=false`。
- MAIL credential：credential-service lease 可用，payload keys 为 `password, username`，
  输出仅包含 username 脱敏摘要，`passwordPrinted=false`。
- send history：`notification_mail_send_history` 存在，ops summary 可查，
  `totalLast24h=0`；因为没有发送，未产生新增 row。
- 恢复/清理：未进行 runtime patch；本轮唯一使用的临时 port-forward 端口
  `18092/18093/18094/18132/18133/18134` 已无残留进程。
