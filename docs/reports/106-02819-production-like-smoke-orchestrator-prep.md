# 106 028-19 production-like smoke orchestrator prep

状态：orchestrator prep 完成，`UTB-02816-P1P2-02` no-real-cost/dry-run 子集已执行，`UTB-02816-P1P2-03` 已在 migrator/notification 修复后重跑通过

任务：`tasks/in-progress/028-19-production-like-smoke-orchestrator-prep.md`

## 结论

本轮将后置统一测试从口头计划固化为可执行入口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1
```

默认行为是 dry-run / plan-only：只输出批次、前置条件、风险、预计副作用、清理检查和命令入口。
真实执行必须显式传入 `-Execute`。provider、SMTP、R2 等外部副作用批次即使在 `-Execute`
模式下，也保留独立 allow flag 作为二次门禁。

## 批次入口

| 批次 | 范围 | 默认动作 | 真实执行门禁 |
| --- | --- | --- | --- |
| `UTB-02816-P1P2-01` | normalization、aggregation、Tika/scraper D3-D4 fixture/live smoke | plan-only | `-Execute` |
| `UTB-02816-P1P2-02` | LLM fake/no-cost、C1 readiness、C3 outbound adoption | plan-only | `-Execute`；真实 LLM 另需后续专门 allow |
| `UTB-02816-P1P2-03` | worker/scheduler audit、production-like cleanup/resource checks | plan-only | `-Execute` |
| `RESOURCE-02818` | K8S resource/rollback verifier | plan-only | `-Execute` |
| `PORTAL-ADMIN-BFF` | portal/admin/BFF smoke | plan-only | `-Execute` |
| `EXTERNAL-PROVIDER-SMTP-R2` | provider、SMTP、R2 smoke | plan-only | `-Execute` + `-AllowProviderExternal` / `-AllowSmtpSend` / `-AllowR2Write` |

## 预计副作用预算

| 批次 | 外部请求 | SMTP | R2 对象 | DB fixture | 清理检查 |
| --- | ---: | ---: | ---: | ---: | --- |
| `UTB-02816-P1P2-01` | 0 默认；provider charset live 单条需另开外网窗口 | 0 | 0 | 约 10-20 | queue/DLQ、ES、raw/unified fixture、Tika Big5/Shift_JIS/BOM fixture、port-forward、quota |
| `UTB-02816-P1P2-02` | 0 默认；resolver live 单条需另开 | 0 | 0 | 约 5-10 | readiness gate、resolver preset、outbound fake/circuit、audit residue |
| `UTB-02816-P1P2-03` | 0 | 0 | 0 | 约 5-10 | worker_job_audit_events、Job/Pod、HTTPRoute、ResourceQuota、smoke residue |
| `RESOURCE-02818` | 0 | 0 | 0 | 0 | quota、resources、HTTPRoute、危险 gate、smoke residue |
| `PORTAL-ADMIN-BFF` | 0 外网 | 0 | 0 | 约 5-15 | BFF fixture、ops/debug residue、port-forward |
| `EXTERNAL-PROVIDER-SMTP-R2` | 每 provider 约 1 次 | 0 默认；允许后最多 1 封 | 0 默认；允许后每 smoke 1 个对象 | 约 5-15 | provider queue/DLQ、send history、R2 HEAD 404、deployment env restore |

## MARK 状态

```text
MARK 1/6 task spec and production-like plan documented
MARK 2/6 orchestrator defaults to dry-run/plan-only
MARK 3/6 UTB-02816-P1P2-01/02/03 command entries listed
MARK 4/6 resource verifier, portal/admin/BFF, provider/SMTP/R2 entries listed
MARK 5/6 preconditions, risk, fixture/external estimates and cleanup checks recorded
MARK 6/6 093 ledger and task index updated
```

## 未执行项

2026-06-10 D4/F3 补充：`UTB-02816-P1P2-01` 的 Tika step 已承接 D3-D4 local fixture smoke；
`scripts/smoke/tika-file-parsing-02816d-smoke.ps1` 会准备 Big5 detail、Shift_JIS list 和
UTF-8 BOM pagination fixture。真实 provider 低资源 charset smoke 仍不在默认执行内，需单独外网窗口。

本轮未执行 live K8S、外网 provider、SMTP、R2 或大规模 smoke。只准备入口并做静态/默认
dry-run/fixture prep 验证。

## 2026-06-10 reboot reconciliation

系统重启后重新核对 orchestrator：`-ListBatches` 可正常列出 6 个批次：

```text
UTB-02816-P1P2-01
UTB-02816-P1P2-02
UTB-02816-P1P2-03
RESOURCE-02818
PORTAL-ADMIN-BFF
EXTERNAL-PROVIDER-SMTP-R2
```

本次只做 plan/list 验证，未执行 `-Execute`、未触发外网 provider、SMTP 或 R2 写入。

## 2026-06-11 UTB-02816-P1P2-02 Execute Evidence

本次开始执行统一测试批次中的安全子集，仍不触发真实 LLM、外网 provider、SMTP 或 R2 写入。

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-02 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\utb-02816-p1p2-02-20260611.json
```

结果：

```text
mode=execute
selectedBatches=UTB-02816-P1P2-02
LLM cleaning F4/E fake/no-cost focused regression: PASSED
C1 gate health focused regression: PASSED
C1 resolver/resource prep dry-run: PASSED
C2 live matrix read-only/dry-run guard: PASSED
C3 BFF proxy/admin target validation: MANUAL, live/full transport deferred
allowFlags: providerExternal=false, smtpSend=false, r2Write=false
```

执行覆盖：

- `:services:ircs-normalization-worker:test --tests *LlmCleaningServiceTest --tests *NormalizationConfigValuesTest --rerun-tasks`
- `:shared:ircs-common:test --tests *AutoStartGateInspectorTest`
- `:services:ircs-catalog-service:test --tests *CatalogAutoStartGateHealthConfigurationTest`
- `:services:ircs-content-service:test --tests *ContentAutoStartGateHealthConfigurationTest`
- `:services:ircs-task-service:test --tests *TaskAutoStartGateHealthConfigurationTest`
- `scripts/smoke/resolver-resource-live-02816c1-prep.ps1`
- `scripts/smoke/config-event-live-matrix-02816c2-smoke.ps1 -DryRun`

边界：

- 真实 LLM 单条 smoke 未执行。
- resolver/resource 外网 live 未执行。
- C3 dev-safe BFF/ClusterIP live、K8S circuit override/open-circuit matrix 未执行。
- 本批次没有外部请求、SMTP、R2 写入或真实 provider 调用。

## 2026-06-11 RESOURCE-02818 Post-check

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch RESOURCE-02818 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\resource-02818-after-utb02-20260611.json
```

结果：

```text
RESOURCE-02818 status=PASSED
ircs-dev HTTPRoute count=0
ResourceQuota used/hard:
  pods 28/30
  services 24/26
  requests.cpu 890m/4
  requests.memory 4544Mi/8Gi
  limits.cpu 7750m/8
  limits.memory 14464Mi/16Gi
dangerous gates: dev-safe defaults retained
stale smoke Job/Pod residue: none
```

## 2026-06-11 Orchestrator Exit-Code Fix

执行 `UTB-02816-P1P2-03` 时发现一个证据质量问题：child PowerShell/native command
返回 `exitCode=2` 时，`Invoke-Expression` 不一定抛异常，原脚本会把 step 标成 `PASSED`。
这会把 `FailOnSkip` 类安全跳过误写为通过。

已修复 `scripts/smoke/production-like-smoke-orchestrator-02819.ps1`：

- 每个 step 执行前重置 `$LASTEXITCODE=0`。
- 执行后读取 `$LASTEXITCODE`。
- `exitCode != 0` 时标记 `FAILED`，并保留 `error="Command exited with code X"`。
- `-ContinueOnFailure` 只继续收集后续步骤，不把失败改写为通过。

验证：

```powershell
$tokens=$null; $errors=$null; [System.Management.Automation.Language.Parser]::ParseFile('scripts\smoke\production-like-smoke-orchestrator-02819.ps1',[ref]$tokens,[ref]$errors) | Out-Null; if ($errors.Count) { $errors | Format-List; exit 1 }; 'PARSER_OK'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -ListBatches
```

## 2026-06-11 UTB-02816-P1P2-03 Partial Evidence

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-03 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\utb-02816-p1p2-03-20260611.json
```

结果：

```text
notification worker audit smoke: FAILED, exitCode=2
task service audit smoke: PASSED
search worker audit focused regression: PASSED
scraper worker audit focused regression: PASSED
search service audit smoke: PASSED
scraper service audit smoke: PASSED dev-safe dry-run
resource rollback verifier: PASSED
```

notification 阻塞证据：

```powershell
kubectl -n ircs-dev exec rabbitmq-0 -- rabbitmqctl list_queues name messages consumers | Select-String -Pattern 'q.notification.mail|q.notification.mail.dlq'
```

结果：

```text
q.notification.mail.dlq 0 0
q.notification.mail     1 0
```

结论：

- `UTB-02816-P1P2-03` 是 partial，不可标记完成。
- notification worker audit 阻塞于 mail queue 非空且没有 consumer；本轮未 purge 队列，避免误删真实待发邮件。
- task/search/scraper/resource 子项已提供通过证据。

## 2026-06-11 DB-Migrator Drift Fix And UTB-03 Closure

用户要求避免对数据库做任何假设；本轮按现场证据处理：

```text
initial live schema check:
  request_audit_logs.audit_class: missing
  worker_job_audit_events.audit_class: missing
  add-audit-archive-retention-governance.sql in databasechangelog: 0

first ircs-migrator:dev job:
  Complete, Run=0
  conclusion: live dev image did not contain current local changelog

after ARM64 Jib push:
  first failure: checksum changed for already executed add-notification-mail-send-history.sql
  fix: restore applied changeset immutability; keep audit_class/outbox/archive in later governance changeset

next failure:
  add-unified-video-content-visibility.sql DO $$ split by Liquibase
  fix: split pending changeset and mark DO block splitStatements:false
```

修复后 migrator 证据：

```text
imageDigest=sha256:8e6305b43ca6f1ae0911a333f8e412307cda099c571e94a72cf6038a04b41da0
Run=10
Previously run=127
Total change sets=137
Rows affected=22231
audit governance changeset executed at 2026-06-11 15:28:23 UTC
```

live schema 证据：

```text
notification_mail_send_history.audit_class: NOT NULL default SYSTEM
request_audit_logs.audit_class: NOT NULL default BEHAVIOR
worker_job_audit_events.audit_class: NOT NULL default SYSTEM
audit_es_replication_outbox: exists
audit_archive_entries: exists
```

ops endpoint recovery:

```text
GET /api/v1/ops/worker-job-audit?size=5&jobSource=ircs-notification-worker&status=succeeded
status=200
body contains auditClass=SYSTEM
```

notification worker audit 修复：

- 新增 `scripts/smoke/notification-worker-audit-with-listener-02812-smoke.ps1`，dev smoke 前临时打开 `APP_NOTIFICATION_LISTENER_ENABLED=true`，结束后恢复。
- `notification-worker-audit-02812-smoke.ps1` 对 Rabbit queue cleanup 改成轮询等待 ack 归零，再判定 residue。

UTB-03 重跑命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-03 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\utb-02816-p1p2-03-after-db-migration-20260611.json
```

UTB-03 重跑结果：

```text
notification worker audit smoke: PASSED
task service audit smoke: PASSED
search worker audit focused regression: PASSED
scraper worker audit focused regression: PASSED
search service audit smoke: PASSED
scraper service audit smoke: PASSED dev-safe dry-run
resource rollback verifier: PASSED
allowFlags: providerExternal=false, smtpSend=false, r2Write=false
```

后置检查：

```text
APP_NOTIFICATION_LISTENER_ENABLED restored/absent
q.notification.mail 0/0/0/0
q.notification.mail.dlq 0/0/0/0
ircs-migrator Job/Pod residue: none
HTTPRoute count: 0
ResourceQuota pods 28/30, services 24/26
```

剩余边界：

- `UTB-02816-P1P2-02` 的 C3 live/full transport、真实 LLM 单条仍后置。
- 外部 provider/SMTP/R2 批次仍需要对应 allow flag；本轮未触发真实外部副作用。

## 2026-06-12 UTB-02816-P1P2-01 Closure

本轮执行 `UTB-02816-P1P2-01`，先发现并修复两个 smoke/test harness 问题：

- `normalization-a9-title-location-genre-closure-smoke.ps1` 对 `kubectl` stderr 诊断过于敏感，`Unknown stream id` 会被 PowerShell 当作 terminating error；已改为 native command wrapper 捕获 exit code/output。
- A9 SQL scalar helper 对单行中文输出使用 `$rows[-1]`，实际取到最后一个字符；已改为强制数组化。
- `CoverImageAdminServiceTest` 手工构造 service 时未注入 `maxUploadBytes`，导致 1 byte fixture 被默认 `0` 上限拒绝；已补测试注入，并新增 oversized upload guard。

局部验证：

```powershell
$tokens=$null; $errors=$null; [System.Management.Automation.Language.Parser]::ParseFile('scripts\smoke\normalization-a9-title-location-genre-closure-smoke.ps1',[ref]$tokens,[ref]$errors) | Out-Null; if($errors.Count){ $errors | Format-List; exit 1 }; 'PARSER_OK'
.\gradlew.bat --no-daemon :services:ircs-storage-service:test --tests *CoverImageAdminServiceTest --tests *ImageSecurityValidatorTest --tests *FileNormalizationServiceTest --rerun-tasks
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\normalization-a9-title-location-genre-closure-smoke.ps1 -FailOnSkip
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\tika-file-parsing-02816d-smoke.ps1
```

UTB-01 重跑命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-01 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\utb-02816-p1p2-01-rerun-20260612085514.json
```

结果：

```text
normalization A9 title/location/genre closure: PASSED
normalization A12 title/raw dictionary focused regression: PASSED
aggregation pipeline runtime B15: PASSED
Tika/scraper D3-D4 local fixture smoke: PASSED
providerExternal=false
smtpSend=false
r2Write=false
```

后置检查：

```text
RESOURCE-02818: PASSED
HTTPRoute count: 0
ResourceQuota: pods 28/30, services 24/26, requests.cpu 890m/4, requests.memory 4544Mi/8Gi
target queues/DLQs: empty
smoke Job/Pod residue: none
port-forward residue: none
dangerous gates: dev-safe defaults retained
```

仍未关闭：

- 真实 provider charset smoke 仍需独立外网窗口和单条 datasource 输入。
- `UTB-02816-P1P2-02` 的 C3 live/full transport、K8S circuit matrix 与可选真实 LLM 单条仍后置。
- `EXTERNAL-PROVIDER-SMTP-R2` 仍需对应 allow flag。
