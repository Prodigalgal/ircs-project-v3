# 028-19 统一测试批次与 production-like smoke orchestrator prep

状态：prep 完成，统一测试已开始；UTB-02816-P1P2-02 no-real-cost/dry-run 子集已通过，UTB-02816-P1P2-03 已在 migrator/notification 修复后重跑通过

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- `tasks/in-progress/028-16-v1-v3-deep-parity-gap-audit-parent.md`
- `tasks/backlog/028-16-f8-production-like-smoke-golden-p1p2-backlog.md`

## 背景

近期多个切片采用“实现优先，统一测试后置”策略推进，包括 normalization A9/A10、LLM
cleaning、C1 seed/cache/resolver、C3 outbound adoption、worker/scheduler audit，以及
resource/rollback verifier。`093` 总账已经给出 `UTB-02816-P1P2-01/02/03` 批次口径，
但缺少一个可执行、默认 dry-run 的统一入口。

## 目标

- 固化 `UTB-02816-P1P2-01/02/03` 的命令入口、前置条件、风险和清理检查。
- 将 resource verifier、portal/admin/BFF smoke、provider/SMTP/R2 smoke 纳入同一批次计划。
- 新增默认 plan-only 的 orchestrator，真实执行必须显式传入 `-Execute`。
- 记录每个批次预计外部请求、SMTP 邮件、R2 对象和 DB fixture 数。
- 回写 `docs/reports/093-v1-v3-parity-progress-ledger.md`，作为后续统一测试阶段入口。

## 非目标

- 不改业务代码。
- 不执行大规模 live smoke。
- 不默认访问 K8S、外网、SMTP、R2 或真实 LLM。
- 不替代每个 smoke 脚本自身的安全门禁和清理逻辑。

## 输入

- `tasks/backlog/028-16-f1..f8` 的 P1/P2 backlog。
- 既有 `scripts/smoke/*.ps1` live/fake smoke 入口。
- `scripts/ops/verify-k8s-dev-resource-rollback-guard.ps1` resource/rollback verifier。
- `docs/reports/093-v1-v3-parity-progress-ledger.md` 当前总账口径。

## 输出

- `scripts/smoke/production-like-smoke-orchestrator-02819.ps1`
- `docs/reports/106-02819-production-like-smoke-orchestrator-prep.md`
- 本 task spec 与 `093` 总账入口更新。

## 影响范围

```text
scripts/smoke/production-like-smoke-orchestrator-02819.ps1
docs/reports/106-02819-production-like-smoke-orchestrator-prep.md
docs/reports/093-v1-v3-parity-progress-ledger.md
tasks/index.md
tasks/in-progress/028-19-production-like-smoke-orchestrator-prep.md
```

## 风险

- 批次命令入口如果默认执行 live 脚本，可能造成重复 rollout、队列污染、SMTP 真发或 R2 写入。
- 既有 smoke 脚本门禁不完全一致，orchestrator 只能作为上层编排，不能绕过下层参数要求。
- 后续统一测试时，外部 provider/SMTP/R2 波动需要区分环境阻塞和代码回归。

## 验收标准

```text
MARK 1/6 task spec and production-like plan documented
MARK 2/6 orchestrator defaults to dry-run/plan-only
MARK 3/6 UTB-02816-P1P2-01/02/03 command entries listed
MARK 4/6 resource verifier, portal/admin/BFF, provider/SMTP/R2 entries listed
MARK 5/6 preconditions, risk, fixture/external estimates and cleanup checks recorded
MARK 6/6 093 ledger and task index updated
```

## 测试边界

- 本轮只做脚本语法/静态检查和默认 dry-run 输出检查。
- 不传 `-Execute`，不运行真实 K8S/外网/SMTP/R2 大规模测试。
- 后续统一测试窗口再按 orchestrator 输出分批执行，并逐批记录 MARK。

## 当前状态

2026-06-10：plan-only 入口已准备完成。账本只记录
`UTB-02816-P1P2-01/02/03`、resource verifier、portal/admin/BFF、provider/SMTP/R2 的统一入口和
安全门禁；不得把 028-19 写成 production-like smoke 已通过。后续任何真实执行必须显式传
`-Execute`，外部 provider、SMTP 和 R2 仍需各自 allow flag。

2026-06-11：已执行 `UTB-02816-P1P2-02` 的 no-real-cost / dry-run 安全子集，并追加只读
`RESOURCE-02818` 后置检查。真实 LLM、resolver 外网 live、C3 dev-safe live/K8S circuit matrix
仍未执行，继续保留在统一测试窗口。

执行命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-02 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\utb-02816-p1p2-02-20260611.json
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch RESOURCE-02818 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\resource-02818-after-utb02-20260611.json
```

结果摘要：

```text
UTB-02816-P1P2-02:
  LLM cleaning F4/E fake/no-cost focused regression: PASSED
  C1 gate health focused regression: PASSED
  C1 resolver/resource prep dry-run: PASSED
  C2 live matrix read-only/dry-run guard: PASSED
  C3 BFF proxy/admin target validation: MANUAL, live/full transport deferred

RESOURCE-02818:
  status: PASSED
  ircs-dev HTTPRoute count: 0
  ResourceQuota used/hard: pods 28/30, services 24/26, requests.cpu 890m/4, requests.memory 4544Mi/8Gi
  dangerous gates: dev-safe defaults retained
```

2026-06-11：修复 orchestrator 执行结果判定。此前 `Invoke-Expression` 对 native command
`$LASTEXITCODE != 0` 不一定抛异常，导致 JSON 中可能出现 `status=PASSED` 但 `exitCode=2` 的
假绿结果。现在每个可执行 step 会在执行前重置 `$LASTEXITCODE`，并在命令结束后把非 0 exit code
标记为 `FAILED`。

验证：

```powershell
$tokens=$null; $errors=$null; [System.Management.Automation.Language.Parser]::ParseFile('scripts\smoke\production-like-smoke-orchestrator-02819.ps1',[ref]$tokens,[ref]$errors) | Out-Null; if ($errors.Count) { $errors | Format-List; exit 1 }; 'PARSER_OK'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -ListBatches
```

2026-06-11：已执行 `UTB-02816-P1P2-03`，结果为 partial：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-03 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\utb-02816-p1p2-03-20260611.json
```

结果摘要：

```text
notification worker audit smoke: FAILED, exitCode=2
  reason observed from child script: MAIL_QUEUE_NOT_EMPTY
  Rabbit snapshot: q.notification.mail messages=1 consumers=0, q.notification.mail.dlq messages=0 consumers=0
task service audit smoke: PASSED
search worker audit focused regression: PASSED
scraper worker audit focused regression: PASSED
search service audit smoke: PASSED
scraper service audit smoke: PASSED dev-safe dry-run
resource rollback verifier: PASSED
```

边界：

- `UTB-02816-P1P2-03` 不能记为完成，因为 notification worker audit 未通过。
- 本轮未 purge `q.notification.mail`，避免误删真实待发邮件。
- 后续需要先识别/处理该队列残留，并确认 notification listener gate 策略，再重跑 notification worker audit。

2026-06-11：已完成 DB 现场取证、migrator 正式链路修复与 `UTB-02816-P1P2-03` 重跑。

关键修复：

- `ircs-migrator:dev` 镜像落后于当前源码，首次真实 Job 显示 `Run: 0`，不能假设 DB 已应用本地 changelog。
- 恢复已执行 `add-notification-mail-send-history.sql` 的不可变历史 schema，新增 audit governance 仍由独立 changeset 负责。
- 修复未执行的 `add-unified-video-content-visibility.sql`，将 `DO $$` constraint 拆到 `splitStatements:false` changeset，避免 Liquibase 分号误切。
- 新增 `notification-worker-audit-with-listener-02812-smoke.ps1`，在 dev smoke 前显式打开 listener gate，结束后恢复。
- `notification-worker-audit-02812-smoke.ps1` 的 queue cleanup 改为等待 Rabbit ack 后再判定 residue，避免异步时序假失败。

验证命令：

```powershell
.\gradlew.bat --no-daemon :platform:ircs-migrator:test :platform:ircs-migrator:bootJar
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\docker\push-project-image-jib.ps1 -ProjectPath ':platform:ircs-migrator' -ImageName ircs-migrator -Tag dev -Architecture arm64 -Os linux
kubectl apply -f deploy\k8s\dev\migrator-job-dev.yaml
kubectl -n ircs-dev wait --for=condition=complete job/ircs-migrator --timeout=300s
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-03 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\utb-02816-p1p2-03-after-db-migration-20260611.json
```

结果摘要：

```text
ircs-migrator imageDigest: sha256:8e6305b43ca6f1ae0911a333f8e412307cda099c571e94a72cf6038a04b41da0
migrator update summary: Run=10, Previously run=127, Total change sets=137
request_audit_logs.audit_class: NOT NULL default BEHAVIOR
worker_job_audit_events.audit_class: NOT NULL default SYSTEM
notification_mail_send_history.audit_class: NOT NULL default SYSTEM
audit_es_replication_outbox: exists
audit_archive_entries: exists
ops worker-job-audit endpoint: HTTP 200 after schema migration

UTB-02816-P1P2-03:
  notification worker audit smoke: PASSED
  task service audit smoke: PASSED
  search worker audit focused regression: PASSED
  scraper worker audit focused regression: PASSED
  search service audit smoke: PASSED
  scraper service audit smoke: PASSED dev-safe dry-run
  resource rollback verifier: PASSED
```

后置状态：

```text
APP_NOTIFICATION_LISTENER_ENABLED restored/absent
q.notification.mail: messages_ready=0, messages_unacknowledged=0, messages=0, consumers=0
q.notification.mail.dlq: messages_ready=0, messages_unacknowledged=0, messages=0, consumers=0
ircs-migrator Job/Pod residue: none
HTTPRoute count: 0
ResourceQuota: pods 28/30, services 24/26, requests.cpu 890m/4, requests.memory 4544Mi/8Gi
```

2026-06-12：已执行并关闭 `UTB-02816-P1P2-01`。

本批次先暴露两个 harness/test fixture 问题并已修复：

- A9 smoke 对 `kubectl` stderr 诊断和单行 SQL scalar 读取不稳，导致业务已 READY 但脚本误判；已补 native wrapper 和数组化 scalar。
- storage cover upload 单测未注入 `maxUploadBytes`，与新增上传大小限制冲突；已补测试注入和 oversized guard。

验证命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\normalization-a9-title-location-genre-closure-smoke.ps1 -FailOnSkip
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\tika-file-parsing-02816d-smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-01 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\utb-02816-p1p2-01-rerun-20260612085514.json
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\ops\verify-k8s-dev-resource-rollback-guard.ps1
```

结果摘要：

```text
UTB-02816-P1P2-01:
  normalization A9 title/location/genre closure: PASSED
  normalization A12 title/raw dictionary focused regression: PASSED
  aggregation pipeline runtime B15: PASSED
  Tika/scraper D3-D4 local fixture smoke: PASSED

RESOURCE-02818 post-check:
  status: PASSED
  HTTPRoute count: 0
  ResourceQuota pods 28/30, services 24/26
  target queues/DLQs empty
  port-forward residue absent
```

边界：真实 provider charset 外网 smoke 不在 UTB-01 默认批次内，仍需独立 live window。
