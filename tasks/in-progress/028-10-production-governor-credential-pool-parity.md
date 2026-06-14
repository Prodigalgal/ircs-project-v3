# 028-10 production governor and credential pool parity 后续子任务

状态：当前 governor/runner 基座实现闭环；destructive / high-write V1 runner 副作用独立迁移

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/041-v1-v3-behavior-parity-gap-audit.md`
- `docs/reports/044-ops-maintenance-runner-parity-analysis.md`
- `docs/reports/054-mail-notification-disabled-failure-parity-analysis.md`
- `docs/reports/059-02810-production-governor-credential-pool-first-slice.md`
- `docs/migrations/059-02810-governor-credential-pool-boundary.md`

## 背景

当前 ops maintenance 已完成 `search-reindex-unified` dev-safe runner，mail notification
已完成 disabled/failure 语义。V1 中更完整的生产级 runner、traffic governor、SMTP
credential pool、发送历史和部分高风险维护任务仍未迁移到 V3。

本轮只启动 `028-10` 并完成第一刀边界固化：先把 production governor、SMTP credential
pool、发送历史和高风险 runner 的执行门槛写清楚，再做一个不外呼 SMTP、不触碰 K8S、
不新增 HTTPRoute 的 credential pool 服务级最小可测试切片。

## 目标

- 设计生产级 maintenance runner 的 traffic governor、dev-safe guard 和环境门禁。
- 补 raw reindex、hard reset、trend sync 等高风险 runner 的任务边界。
- 设计 SMTP credential pool、发送历史和真实 SMTP smoke 的分阶段验收方式。
- 固化 credential lease 的服务级上限和 MAIL pool 基础选择规则，提供本地单元测试证据。
- 明确哪些能力只能在 staging/production-like 环境验证。

## 非目标

- 不在当前 dev 环境直接执行破坏性全量任务。
- 不绕过 guarded operation。
- 不开启公网生产流量。
- 不执行真实 SMTP。
- 不做 K8S rollout、不新增 HTTPRoute、不修改 K8S 快照。
- 不修改父任务、索引文件或 028-06/028-07/028-08/028-09/028-12 任务文件。

## 输入

- ops maintenance APIs
- `services/ircs-ops-service` runner/session/traffic 代码
- `services/ircs-notification-worker` mail disabled/failure 代码
- `services/ircs-credential-service` credential lease/config 代码

## 输出

- production governor 边界设计和后续 task spec。
- SMTP credential pool 边界设计和第一刀服务级测试。
- 高风险 runner task specs。
- 本任务、报告、迁移记录的状态回写。

## 影响范围

本轮授权范围：

```text
services/ircs-credential-service/**
docs/reports/*02810*governor*credential*.md
docs/migrations/*02810*governor*credential*.md
tasks/in-progress/028-10-production-governor-credential-pool-parity.md
```

后续任务可能涉及但本轮不修改：

```text
services/ircs-ops-service/**
services/ircs-notification-worker/**
services/ircs-task-service/**
scripts/smoke/**
```

## 依赖

- 明确 dev/staging/production-like 环境边界。
- 维护任务必须可中止、可观察、可清理。
- SMTP credential 必须由 credential-service 管理，不在 notification-worker 中明文固化。
- 真实 SMTP smoke 需要隔离账号、低频限额和人工确认。

## 风险

- 高风险 runner 可能破坏数据或制造大量队列消息。
- 真实 SMTP smoke 可能触发外部发送成本或风控。
- credential pool 若缺少服务级上限，内部调用方可能一次拉取过多 secret。
- send history 如果与 retry/DLQ 语义混杂，可能误把失败邮件记录成成功投递。

## 验收标准

- 每个生产级 runner 都有 guard、限流、状态和清理策略。
- SMTP credential pool 和发送历史有测试证据或明确后续 task spec。
- dev 环境不执行不可逆全量任务。
- 本轮第一刀必须提供本地文档、任务、迁移记录和低风险测试证据。

## 测试边界

- dev 仅做 guarded dry-run、纯单元测试或极小 fixture。
- staging/production-like 验证需另行确认。
- 本轮不执行真实 SMTP、不投递 mail queue、不做 rollout。

## 子任务 028-10-A：production governor / SMTP credential pool 边界设计 + credential lease guard

### 背景

`044` 已证明 ops maintenance 只接入了 `search-reindex-unified` dev-safe message runner，
其他 destructive runner 仍需要 guard。`054` 已证明 notification-worker mail disabled/failure
语义，但 SMTP credential pool、发送历史和真实 SMTP smoke 仍未实现。credential-service
已有 provider credential lease，且 provider catalog 已包含 `MAIL` 模板，但 lease 服务级边界
还需要在当前任务中先固定。

### 目标

- 输出 production governor / SMTP credential pool 第一刀报告。
- 输出 governor 与 credential pool 迁移记录。
- 将 credential lease limit 在 service 层做上限保护，避免绕过 controller validation。
- 增加 MAIL credential pool 的服务级选择单元测试，验证只选择含 `username` 和 `password`
  的 MAIL lease。

### 非目标

- 不把 notification-worker 改成真实 credential-service SMTP 发送。
- 不新增 controller route、BFF route 或 HTTPRoute。
- 不执行真实 SMTP、不投递 RabbitMQ mail 消息。
- 不实现 send history 表和迁移 SQL。
- 不接 raw reindex hard reset、trend sync 或 production runner。

### 上游来源

- `docs/reports/044-ops-maintenance-runner-parity-analysis.md`
- `docs/reports/054-mail-notification-disabled-failure-parity-analysis.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

### 输入

- `services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/CredentialService.java`
- `services/ircs-credential-service/src/test/java/com/prodigalgal/ircs/credential/CredentialServiceTest.java`
- `services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/CredentialRepository.java`
- `services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/CredentialProviderCatalog.java`

### 输出

- `docs/reports/059-02810-production-governor-credential-pool-first-slice.md`
- `docs/migrations/059-02810-governor-credential-pool-boundary.md`
- credential-service 服务级 guard 和单元测试。

### 影响范围

```text
services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/CredentialService.java
services/ircs-credential-service/src/test/java/com/prodigalgal/ircs/credential/CredentialServiceTest.java
docs/reports/059-02810-production-governor-credential-pool-first-slice.md
docs/migrations/059-02810-governor-credential-pool-boundary.md
tasks/in-progress/028-10-production-governor-credential-pool-parity.md
```

### 依赖

- 本地 Gradle/JDK 能运行 `:services:ircs-credential-service:test`。
- 代码实现仅使用 mock repository，不依赖 DB、RabbitMQ、SMTP 或 K8S。

### 风险

- generic credential lease 的 limit cap 不能破坏现有 internal controller 默认 `20` 和最大 `100`
  行为。
- MAIL pool 只做 credential 选择规则，不代表 SMTP 发送成功。
- 后续 notification-worker 接入时必须继续保留 `APP_NOTIFICATION_MAIL_ENABLED` 和 SMTP smoke gate。

### 验收标准

```text
MARK 1/3 028-10 task moved to in-progress with task spec
MARK 2/3 production governor and SMTP credential pool boundary docs recorded
MARK 3/3 credential-service MAIL pool guard unit tests pass without SMTP
```

### 测试边界

必跑：

```powershell
.\gradlew.bat :services:ircs-credential-service:test --rerun-tasks
```

不跑：

```text
真实 SMTP
K8S rollout
HTTPRoute 检查/变更
maintenance destructive runner
```

### 关联文档

- `docs/reports/059-02810-production-governor-credential-pool-first-slice.md`
- `docs/migrations/059-02810-governor-credential-pool-boundary.md`

### 关联任务

- 父任务：`tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- 本任务：`tasks/in-progress/028-10-production-governor-credential-pool-parity.md`

### 当前状态

已完成第一刀；`028-10` 总任务保持进行中，后续 production governor、SMTP adapter、
send history、ops 查询/retention 和 staging smoke 继续拆分。

### 实现结果

- 已移动本任务：`tasks/backlog/028-10-production-governor-credential-pool-parity.md`
  -> `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`。
- 已新增并回写：
  - `docs/reports/059-02810-production-governor-credential-pool-first-slice.md`
  - `docs/migrations/059-02810-governor-credential-pool-boundary.md`
- 已在 `CredentialService` 增加 credential lease service-level limit guard：
  - `limit > 100` 时 cap 到 `100`。
  - `limit <= 0` 时返回空租约，不查询 repository。
- 已新增 `CredentialService.leaseMailCredentials(int limit)`：
  - provider 固定为 `MAIL`。
  - 只返回同时包含非空 `username` 和 `password` 的 lease。
  - 不执行 SMTP，不投递消息，不记录 secret。
- 已在 `CredentialServiceTest` 增加本地单元测试：
  - `capsProviderCredentialLeaseLimitAtServiceBoundary()`
  - `zeroProviderCredentialLeaseLimitSkipsRepository()`
  - `leasesMailCredentialsOnlyWhenUsernameAndPasswordExist()`

### 验证结果

通过：

```powershell
.\gradlew.bat :services:ircs-credential-service:test --rerun-tasks
BUILD SUCCESSFUL in 8s
```

测试证据：

```text
CredentialServiceTest tests=10, skipped=0, failures=0, errors=0
```

MARK：

```text
MARK 1/3 028-10 task moved to in-progress with task spec
MARK 2/3 production governor and SMTP credential pool boundary docs recorded
MARK 3/3 credential-service MAIL pool guard unit tests pass without SMTP
```

SKIPPED：

```text
SKIPPED real SMTP: 本轮禁止真实 SMTP，且第一刀只做 credential-service 单元测试
SKIPPED K8S rollout: 本轮不 rollout
SKIPPED HTTPRoute: 本轮不新增、不修改 HTTPRoute
SKIPPED destructive maintenance runner: 本轮不执行 raw reindex/hard reset/trend sync
```

### 剩余项

- production governor runner registry 已由 `028-10-C` 完成 dev gate/metadata/audit 第一刀。
- notification-worker SMTP credential adapter、send history、retention cleanup、V1 SMTP one-shot 和
  send-history cleanup runner safety gate 已由 `028-10-B` 到 `028-10-N` 完成。
- `028-10-O / 028-12-E` 已将 `search-reindex-raw` 升级为 capped dev-safe owner runner，并让
  manual maintenance session 下传 `sessionId` 作为 runner audit correlationId。
- 当前 runner/governor 基座功能面闭环；`unified-recalculate` 已由 `028-22-A` 迁为 capped
  owner runner，`aggregation-reset` 已由 `028-22-B` 迁为多 owner destructive runner，`sanitize`
  已由 `028-22-C` 迁为 normalization-worker owner runner，`trend-sync` 已由 `028-22-D`
  迁为 scraper/content owner runner，V1 staggered background discovery 和 TMDB
  credential-service lease 已由 `028-22-E` 收敛。当前 runner/governor 基座已无已知占位拒绝缺口。

## 子任务 028-10-H：notification mail send history dev migration / ops query

### 当前状态

已完成并移动到 `tasks/done/028-10-h-notification-mail-send-history-dev-migration-ops-query.md`。

### 验收结果

```text
MARK 1/6 PASS 028-10-H docs/task spec created before live changes
MARK 2/6 PASS migrator dev image built and pushed with digest
MARK 3/6 PASS migrator Job completed in ircs-dev and was cleaned
MARK 4/6 PASS notification_mail_send_history exists in dev DB with expected indexes
MARK 5/6 PASS ops-service send history page and summary endpoints return non-404 live responses
MARK 6/6 PASS SMTP was not sent and APP_MAIL_ENABLED stayed false
```

### 关键证据

```text
ircs-migrator digest: sha256:f6b8be42100136c204800cb6ad88c082a1aad56f04e10337c89ca22ca829c578
ircs-ops-service digest: sha256:feca779a5bf0c8157a2a2fc8424aee12f266deb9df525ecc05da5f12e4f8b10c
dev table: notification_mail_send_history, columns=13, business indexes=4
ops smoke: status=PASS, summaryStatus=OK, pageTotalElements=0
SMTP: sent=false, APP_MAIL_ENABLED=false
cleanup: job/pod app=ircs-migrator No resources found
```

## 子任务 028-10-I：notification SMTP staging-only one-shot live smoke

### 当前状态

已完成并移动到 `tasks/done/028-10-i-notification-smtp-staging-one-shot-live-smoke.md`。
当前 dev SMTP 配置不是 staging-only，真实 SMTP 被阻断，未发送邮件。

- `docs/requirements/028-10-i-notification-smtp-staging-one-shot-live-smoke.md`
- `docs/reports/092-02810i-notification-smtp-staging-one-shot-live-smoke.md`
- `tasks/done/028-10-i-notification-smtp-staging-one-shot-live-smoke.md`

### 验收边界

```text
MARK 1/6 docs/task spec before live smoke
MARK 2/6 K8S/credential/ops/send history preflight without secret or full recipient output
MARK 3/6 staging-only/safe recipient/one-shot/history/restoration gates
MARK 4/6 at most one SMTP send if gates pass, otherwise blocked preflight
MARK 5/6 send history row via DB or ops endpoint, or no-send reason
MARK 6/6 switches and port-forward restored/cleaned
```

### 验收结果

```text
MARK 1/6 PASS docs/task spec before live smoke
MARK 2/6 PASS K8S/credential/ops/send history preflight without secret or full recipient output
MARK 3/6 PASS staging-only/safe recipient/one-shot/history/restoration gates executed
MARK 4/6 PASS SMTP blocked at preflight because config is not staging-only
MARK 5/6 PASS send history table and ops endpoint available; no new row because no SMTP was sent
MARK 6/6 PASS no runtime patch applied; APP_MAIL_ENABLED remains false; helper port-forward processes cleaned
```

### 关键证据

```text
AllowSend=true and safe recipient shape passed, but blockReasons=SMTP_CONFIG_NOT_STAGING_ONLY
host=smtp.gmail.com, fromMasked=HuaWai System <hu***@gmail.com>
sent=false, notification_mail_send_history recent24hCount=0, ops summary available
```

2026-06-09 docs/tasks 收敛补充：

- `028-10-H` 已完成 dev DB migration 与 ops-service query live 闭环，migrator/ops-service ARM64
  image rollout、`notification_mail_send_history` table/index 和 endpoint 可查均已入账。
- `028-10-I` 已完成安全门禁验证；当时以 `SMTP_CONFIG_NOT_STAGING_ONLY` 阻断真实 SMTP，
  未发送邮件。
- 用户已修正后续 SMTP one-shot provider 口径：允许 Gmail 等标准 SMTP 协议邮箱，不再把
  Gmail provider 本身视为不合格；但必须使用专用凭据、单一测试收件人 allowlist、防批量误发、
  发送后 send history 可审计，且 secret 不输出。
- `028-10-J/K` 已完成 send history retention cleanup dev DB smoke，Harbor push 阻塞解除。
- 父任务继续保持进行中；真实 SMTP one-shot 已由 `028-10-L` 关闭，send-history cleanup
  runner safety gate 已由 `028-10-N` 关闭。当前剩余边界只保留其他高风险 runner /
  production-like governor 扩展。

## 子任务 028-10-J：notification mail send history retention / cleanup

状态：已完成

任务规格：

- `docs/requirements/028-10-j-notification-mail-send-history-retention-cleanup.md`
- `tasks/done/028-10-j-notification-mail-send-history-retention-cleanup.md`

目标：默认关闭的低资源 cleanup runner，按 `created_at` cutoff 和可选状态白名单批量清理
`notification_mail_send_history`，避免审计表无限增长；本任务不发送真实 SMTP。

当前结果：

```text
MARK 1/5 PASS docs/task spec created
MARK 2/5 PASS cleanup service/runner default-off and bounded
MARK 3/5 PASS notification-worker focused tests
MARK 4/5 PASS dev DB cleanup smoke deletes old fixture and keeps fresh fixture
MARK 5/5 PASS ops summary after cleanup queryable; smoke fixture cleaned
```

关键证据：

```text
imageDigest=sha256:79462463933647277e6069c61d0081e75ed241ccdff94f264d311d6ee164027b
cleanup Job=notification-mail-history-cleanup-02810j-814314ff5c9c
deletedRows=1
oldRemainingAfterCleanup=0
freshRetainedAfterCleanup=true
finalSmokeResidue=0
ops summary available=true
SMTP sent=false
```

真实 SMTP one-shot 已由 `028-10-L` 独立关闭，不属于 028-10-J。

## 子任务 028-10-K：notification-worker Harbor push / retention smoke unblock

状态：已完成

任务规格：

- `docs/requirements/028-10-k-notification-worker-harbor-push-retention-smoke.md`
- `tasks/done/028-10-k-notification-worker-harbor-push-retention-smoke.md`

目标：诊断 `ircs-notification-worker:dev` Harbor push unauthorized，优先复用现有安全凭据或
提供可验证替代路径，并完成 `028-10-J` dev DB cleanup smoke。本任务不发送真实 SMTP，
不输出 secret。

验收结果：

```text
MARK 1/5 PASS Harbor unauthorized root cause or verified alternative path recorded
MARK 2/5 PASS notification-worker dev cleanup image is available to K8S without printing secrets
MARK 3/5 PASS cleanup smoke inserts old/fresh fixture and runs one-shot cleanup
MARK 4/5 PASS old fixture deleted, fresh fixture retained, then all smoke fixture removed
MARK 5/5 PASS ops summary queryable and SMTP remains unsent
```

报告：

- `docs/reports/098-02810k-notification-worker-harbor-push-retention-smoke.md`

## 子任务 028-10-M：notification-worker CrashLoop Ready recovery

状态：已完成

任务规格：

- `docs/requirements/028-10-m-notification-worker-crashloop-ready-recovery.md`
- `tasks/done/028-10-m-notification-worker-crashloop-ready-recovery.md`

目标：修复 `CredentialServiceMailCredentialRepository: No default constructor found`
导致的 notification-worker CrashLoop，恢复 `ircs-dev` Ready；本任务不发送 SMTP、
不投递业务 mail queue、不输出 secret。

验收结果：

```text
MARK 1/6 PASS CrashLoop root cause identified without exposing secrets
MARK 2/6 PASS focused constructor/DI startup test added
MARK 3/6 PASS notification-worker tests pass locally
MARK 4/6 PASS ARM64 dev image pushed with digest recorded
MARK 5/6 PASS ircs-dev notification-worker Ready and logs no longer contain constructor error
MARK 6/6 PASS APP_MAIL_ENABLED/listener test switches restored; SMTP sent count remains 0
```

关键证据：

```text
imageDigest=sha256:5271750ff0740e1f2aaf520642cb821fd845215f846bffbf8c0008571c99895e
pod=ircs-notification-worker-7684c86d5c-7ptxd ready=True restartCount=0
APP_MAIL_ENABLED=false
APP_NOTIFICATION_LISTENER_ENABLED absent from live Deployment env
recent 20 minutes notification_mail_send_history status=SENT count=0
```

报告：

- `docs/reports/100-02810m-notification-worker-crashloop-ready-recovery.md`

`028-10-L` SMTP one-shot 已在 Ready 恢复后重跑并完成；真实发送 1 封，send history/ops
审计和用户收件箱确认均已入账。

## 子任务 028-10-L：notification SMTP V1 credential one-shot live smoke

状态：已完成

任务规格：

- `docs/requirements/028-10-l-notification-smtp-v1-live-smoke.md`
- `tasks/done/028-10-l-notification-smtp-v1-live-smoke.md`

目标：使用 V1 真实 SMTP 凭据执行最多 1 封 one-shot live smoke，验证
`notification_mail_send_history` 与 ops endpoint，并恢复 runtime 开关。

验收结果：

```text
MARK 1/6 PASS V1/V3 SMTP config and MAIL credential evidence checked without printing secrets
MARK 2/6 PASS standard SMTP one-shot gates pass only with explicit AllowSend and one safe recipient
MARK 3/6 PASS notification-worker runtime switches are temporarily enabled and restored
MARK 4/6 PASS exactly one notification.mail test message is published
MARK 5/6 PASS send history DB row and ops endpoint expose status/error/timestamp/recipient evidence safely
MARK 6/6 PASS temporary port-forward/helper resources are cleaned; audit row retention is documented
```

关键证据：

```text
trace=02810l-smtp-smoke-20260609222338
sent=true
publishResult.routed=true
sendHistory.status=sent
ops.status=SENT
credential_id=f0a0a267-f124-40c0-82e1-1ad323370cc7
recipientMasked=HuaWai System <hu***@gmail.com>
mailboxReceiptConfirmedByUser=true
runtimeRestored=true
no 028-10-L job/pod residue
no smoke port-forward process residue
```

报告：

- `docs/reports/101-02810l-notification-smtp-v1-live-smoke.md`

## 子任务 028-10-N：notification maintenance runner safety gate

状态：已完成

任务规格：

- `docs/requirements/028-10-n-notification-maintenance-runner-safety-gate.md`
- `tasks/done/028-10-n-notification-maintenance-runner-safety-gate.md`

目标：补齐 notification-worker send-history cleanup runner 的高风险 maintenance runner gate：
默认关闭、enabled 后默认 dry-run、真实删除双开关、batch/rate limit、worker-job audit、
one-shot 失败语义和 dev smoke 清理证据。

验收结果：

```text
MARK 1/6 PASS 028-10-N docs/task spec created before implementation
MARK 2/6 PASS cleanup runner default-off and real execute requires explicit double gate
MARK 3/6 PASS dry-run does not modify fixture rows and limit still bounds candidate work
MARK 4/6 PASS worker job audit records dry-run/execute/refusal without secrets
MARK 5/6 PASS focused notification-worker tests pass
MARK 6/6 PASS dev smoke records dry-run + execute + ops/audit evidence
```

关键证据：

```text
imageDigest=sha256:4cff0405ff615adfd29fe9847518208a9320b0f3522afa0021ceaeef55ada6cc
dryRun candidateRows=1 deletedRows=0
execute deletedRows=1
oldAfterExecute=0
freshAfterExecute=1
dryRunAudit=1
executeAudit=1
opsWorkerJobAudit.available=true
finalSmokeResidue=0
sent=false
mailQueuePublished=false
passwordPrinted=false
recipientPrinted=false
APP_MAIL_SEND_HISTORY_CLEANUP_ENABLED=false
APP_MAIL_SEND_HISTORY_CLEANUP_DRY_RUN=true
APP_MAIL_SEND_HISTORY_CLEANUP_EXECUTE_ENABLED=false
```

报告：

- `docs/reports/102-02810n-notification-maintenance-runner-safety-gate.md`

## 子任务 028-10-O：raw reindex runner / session correlation closeout

状态：已完成

报告：

- `docs/reports/117-02810-02812-runner-audit-closeout.md`
- `tasks/done/028-10-o-02812-runner-audit-closeout.md`

目标：将 `search-reindex-raw` 从 high-risk refusal 升级为 capped dev-safe owner runner，并让
manual maintenance session 的 `sessionId` 进入 runner audit correlation 链路。

验收结果：

```text
MARK 1/3 PASS raw reindex runner is LOW risk, devAllowed and supportsDryRun
MARK 2/3 PASS raw reindex only enqueues RAW_VIDEO INDEX sync tasks through search-service owner API
MARK 3/3 PASS manual maintenance session passes sessionId as correlationId
```

## 子任务 028-10-B：MAIL credential adapter and fake sink

### 当前状态

已完成并移动到 `tasks/done/028-10-b-mail-credential-adapter-fake-sink.md`。

### 验收结果

```text
MARK 1/5 PASS notification-worker can lease MAIL credentials without printing secrets
MARK 2/5 PASS disabled mail path still skips send and acks/no-ops
MARK 3/5 PASS fake sink records intended mail without SMTP network
MARK 4/5 PASS missing credential and fake failure keep retry/DLQ error semantics
MARK 5/5 PASS notification-worker and credential-service tests pass without SMTP
```

## 子任务 028-10-C：ops runner registry and dev gate

### 当前状态

已完成并移动到 `tasks/done/028-10-c-ops-runner-registry-dev-gate.md`。

### 验收结果

```text
MARK 1/5 runner registry exposes risk/dev/dry-run/limit metadata
MARK 2/5 search-reindex-unified remains dev-safe and capped
MARK 3/5 high-risk runner is refused in dev with stable reason
MARK 4/5 runner/refusal emits worker_job_audit_events-compatible evidence
MARK 5/5 ops-service tests pass without destructive maintenance execution
```

### 主线程复验

```powershell
.\gradlew.bat :services:ircs-magnet-service:test :services:ircs-notification-worker:test :services:ircs-credential-service:test :services:ircs-ops-service:test :services:ircs-storage-service:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL in 44s`。
