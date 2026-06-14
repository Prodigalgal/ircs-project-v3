# 095 DB Migrator Drift And Notification Audit Smoke

日期：2026-06-11

## 背景

`UTB-02816-P1P2-03` 首次执行时，notification worker audit 被 `q.notification.mail`
残留和 listener gate 阻塞。后续排查发现更深层问题：live dev DB 缺少 `audit_class`，
而本地 changelog 已经包含修复。按“避免数据库做任何假设”的原则，本轮以 live schema、
`databasechangelog` 和 migrator Job 日志为准。

## 修复

- 重新推送 `platform:ircs-migrator` ARM64 `dev` 镜像。
- 恢复已执行 `add-notification-mail-send-history.sql` changeset 的不可变历史内容。
- 将 `add-unified-video-content-visibility.sql` 的 `DO $$` block 拆为 `splitStatements:false`
  changeset。
- 新增 notification audit wrapper，dev smoke 时临时打开 listener gate 并恢复。
- notification audit cleanup 等待 Rabbit ack 归零后再判定 residue。

## 证据

```text
ircs-migrator imageDigest:
  sha256:8e6305b43ca6f1ae0911a333f8e412307cda099c571e94a72cf6038a04b41da0

migrator update:
  Run=10
  Previously run=127
  Total change sets=137
  Rows affected=22231

live schema:
  request_audit_logs.audit_class NOT NULL default BEHAVIOR
  worker_job_audit_events.audit_class NOT NULL default SYSTEM
  notification_mail_send_history.audit_class NOT NULL default SYSTEM
  audit_es_replication_outbox exists
  audit_archive_entries exists
```

## Smoke

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-03 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\utb-02816-p1p2-03-after-db-migration-20260611.json
```

结果：

```text
notification worker audit smoke: PASSED
task service audit smoke: PASSED
search worker audit focused regression: PASSED
scraper worker audit focused regression: PASSED
search service audit smoke: PASSED
scraper service audit smoke: PASSED
resource rollback verifier: PASSED
```

后置状态：

```text
APP_NOTIFICATION_LISTENER_ENABLED restored/absent
q.notification.mail 0
q.notification.mail.dlq 0
ircs-migrator Job/Pod residue none
HTTPRoute count 0
```

## 决策

- live DB 状态必须用现场查询确认，不以源码、旧任务记录或旧 smoke 结论代替。
- 已执行 Liquibase changeset 不可修改；任何新字段、索引、表必须走新增 changeset。
- `ircs-dev` 可临时打开 V1 已验证的 enable/gate 做真实调测，但必须通过脚本恢复开关并记录 residue。
