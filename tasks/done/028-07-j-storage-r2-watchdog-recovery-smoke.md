# 028-07-J storage-service R2 watchdog 长周期/恢复验证 task spec

状态：已完成

父任务：

- `tasks/done/028-07-r2-live-watchdog-parity.md`

上游来源：

- `docs/reports/041-v1-v3-behavior-parity-gap-audit.md`
- `docs/reports/091-02807-storage-r2-watchdog-long-run-recovery-smoke.md`
- `tasks/done/028-07-g-real-r2-explicit-v1-bucket-write-smoke.md`
- `tasks/done/028-07-h-r2-cover-explicit-v1-bucket-chain-smoke.md`
- `tasks/done/028-07-i-storage-service-r2-avatar-path-smoke.md`

## 背景

`028-07-G/H/I` 已分别完成 R2 单对象、cover path 与 avatar path 的真实链路验证。
`028-07` 剩余边界是 watchdog 长周期/恢复语义：当 R2 或 worker 临时不可用后，V3 是否能像
V1 一样通过周期扫描恢复本地 avatar 与 due cover R2 上传。

## 目标

- 对照 V1 `AvatarSyncScheduler`、`AvatarSyncConsumer` 与 cover image retry 语义，固定 V3
  watchdog smoke 验收口径。
- 新增 `scripts/smoke/storage-r2-watchdog-02807j-smoke.ps1`。
- 设计短周期可控场景：在常驻 R2 disabled 状态下制造 avatar/cover 本地积压，再临时启用
  R2 runtime 与 R2 watchdog；cover 通过 `LOCAL_STORED + next_retry_time due` 制造恢复样本。
- 使用临时 env 缩短 watchdog interval，测试完成后恢复 Deployment 原值。
- 低频写入真实 R2，最多创建一个 `covers/*` 与一个 `avatars/*` 对象，结束后 DELETE 并确认
  HEAD 404/notFound。

## 非目标

- 不修改 config-service、content-service、Douban provider、notification/ops、portal/admin/BFF。
- 不永久启用 R2 或修改常驻 dev bucket。
- 不做批量历史对象迁移。
- 不做生产 bucket 策略调整。

## 输入

```text
ircs-dev Kubernetes / RabbitMQ / PostgreSQL
ircs-credential-service R2 lease
V1 parity bucket ircs
storage-service watchdog env:
APP_STORAGE_R2_WATCHDOG_ENABLED
APP_STORAGE_R2_WATCHDOG_INITIAL_DELAY_MS
APP_STORAGE_R2_WATCHDOG_FIXED_DELAY_MS
APP_STORAGE_R2_WATCHDOG_BATCH_SIZE
```

## 输出

```text
scripts/smoke/storage-r2-watchdog-02807j-smoke.ps1
docs/reports/091-02807-storage-r2-watchdog-long-run-recovery-smoke.md
tasks/done/028-07-j-storage-r2-watchdog-recovery-smoke.md
```

完成后移动到：

```text
tasks/done/028-07-j-storage-r2-watchdog-recovery-smoke.md
```

## 影响范围

允许写入：

```text
scripts/smoke/storage-r2-watchdog-02807j-smoke.ps1
services/ircs-storage-service/**
docs/reports/091-02807-storage-r2-watchdog-long-run-recovery-smoke.md
tasks/done/028-07-j-storage-r2-watchdog-recovery-smoke.md
tasks/done/028-07-r2-live-watchdog-parity.md
tasks/index.md
```

明确不包含：

```text
services/ircs-config-service/**
services/ircs-content-service/**
services/ircs-notification-*/**
services/ircs-ops-service/**
services/ircs-portal-bff/**
services/ircs-admin-bff/**
deploy/k8s/dev/* 常驻 bucket 改动
```

## 依赖

- `kubectl` 可访问 `ircs-dev`。
- `ircs-credential-service` 可租用 R2 credential。
- `ircs-dev-secrets.INTERNAL_CREDENTIAL_TOKEN` 可读取。
- `ircs-portal-bff` 可 port-forward 完成注册、登录、avatar upload。
- `ircs-storage-service` 可临时 scale down/up 与临时 patch R2/watchdog env。
- RabbitMQ 可查询并可清理本任务 trace 相关 ready 消息。

## 风险

- 临时 scale down storage-service 会短时间影响并行 smoke，因此必须窗口短、结束恢复。
- 真实 R2 对象必须 finally cleanup。
- Rabbit ready 消息清理必须只处理本任务 memberId，不能 purge 整队列。
- Deployment patch 必须恢复 `APP_STORAGE_R2_ENABLED=false` 与 ConfigMap `valueFrom`。

## 验收标准

```text
MARK 1/8 V1/V3 watchdog parity preflight resolves R2 credential and explicit bucket
MARK 2/8 storage-service is paused so avatar upload creates a recoverable local backlog
MARK 3/8 cover and avatar backlog fixtures are created without immediate R2 sync
MARK 4/8 temporary R2 runtime enables watchdog with shortened interval and small batch
MARK 5/8 watchdog recovers due cover image to R2 REMOTE_STORED
MARK 6/8 watchdog republishes local avatar and consumer rewrites avatar_url to R2
MARK 7/8 R2 objects, DB fixtures, local files, queue/DLQ are cleaned
MARK 8/8 dev env restored with no Secret/port-forward/HTTPRoute/quota residue
```

## 测试边界

脚本解析：

```powershell
$path = '.\scripts\smoke\storage-r2-watchdog-02807j-smoke.ps1'; $tokens = $null; $errors = $null; [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path -LiteralPath $path), [ref]$tokens, [ref]$errors) | Out-Null; if ($errors.Count -gt 0) { $errors | ForEach-Object { $_.ToString() }; exit 1 }; "PARSER_OK $path"
```

真实 smoke：

```powershell
.\scripts\smoke\storage-r2-watchdog-02807j-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -UseV1ParityBucketOverride -ConfirmWatchdogR2Write
```

## 实施记录

- 新增 `scripts/smoke/storage-r2-watchdog-02807j-smoke.ps1`。
- 默认 `PreflightOnly` / `NoWrite` 不 patch Deployment、不写 R2。
- 真实 smoke 显式使用 `-UseV1ParityBucketOverride -ConfirmWatchdogR2Write`。
- 使用短周期 watchdog env 验证恢复：
  - `APP_STORAGE_R2_WATCHDOG_INITIAL_DELAY_MS=30000`
  - `APP_STORAGE_R2_WATCHDOG_FIXED_DELAY_MS=5000`
  - `APP_STORAGE_R2_WATCHDOG_BATCH_SIZE=1`
- 第一次真实 smoke 发现 dev Pod 重建会丢失 `/app/storage` 本地临时文件，cover 进入
  `LOCAL|FAILED|Local file missing for R2 upload`。脚本已增加
  `devLocalFixtureRehydratedAfterRollout=true` 的 dev-only fixture rehydrate，避免把 dev
  临时存储差异误判为 watchdog 业务失败。
- 未修改 storage-service 业务代码。

## 验收结果

脚本解析：

```powershell
$path = '.\scripts\smoke\storage-r2-watchdog-02807j-smoke.ps1'; $tokens = $null; $errors = $null; [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path -LiteralPath $path), [ref]$tokens, [ref]$errors) | Out-Null; if ($errors.Count -gt 0) { $errors | ForEach-Object { $_.ToString() }; exit 1 }; "PARSER_OK $path"
```

结果：

```text
PARSER_OK .\scripts\smoke\storage-r2-watchdog-02807j-smoke.ps1
```

真实 smoke：

```powershell
.\scripts\smoke\storage-r2-watchdog-02807j-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -UseV1ParityBucketOverride -ConfirmWatchdogR2Write
```

结果：

```text
MARK 1/8 V1/V3 watchdog parity preflight resolves R2 credential and explicit bucket
MARK 2/8 storage-service is reachable while standing R2 env remains disabled for backlog setup
MARK 3/8 cover and avatar backlog fixtures are created without immediate R2 sync
MARK 4/8 temporary R2 runtime enables watchdog with shortened interval and small batch
MARK 5/8 watchdog recovers due cover image to R2 REMOTE_STORED
MARK 6/8 watchdog republishes local avatar and consumer rewrites avatar_url to R2
MARK 7/8 R2 objects, DB fixtures, local files, queue/DLQ are cleaned
MARK 8/8 dev env restored with no Secret/port-forward/HTTPRoute/quota residue
SUCCESS/STORAGE_R2_WATCHDOG_RECOVERY_SMOKE_OK
```

关键 evidence：

```text
traceId=02807j-r2-watchdog-20260609190331
coverImageId=67618a3a-5018-47f4-a364-27c95a04eb5d
memberId=de89b7e2-2d25-4bbf-8a0c-f1d22b271f28
configMapBucket=ircs-dev
effectiveBucket=ircs
coverStoragePath=covers/4a23fffd4a8a2e224f7ee36db41f1101e25ae5c3e8ddeae8b0d02483015a505f.png
avatarStoragePath=avatars/4a23fffd4a8a2e224f7ee36db41f1101e25ae5c3e8ddeae8b0d02483015a505f.png
r2CoverHeadStatusCode=200
r2AvatarHeadStatusCode=200
r2CoverDeleteStatusCode=204
r2AvatarDeleteStatusCode=204
coverCleanupNotFound=true
avatarCleanupNotFound=true
coverResidue=0
memberResidue=0
queueAfterCleanup q.storage.avatar_sync messages=0 ready=0 unacked=0 consumers=1
dlqAfterCleanup q.storage.avatar_sync.dlq messages=0 ready=0 unacked=0 consumers=0
temporarySecret=<empty>
HTTPRoute=<empty>
ResourceQuota pods=28/30, services=24/26
```

后置检查：

```text
temporary secret absent
APP_STORAGE_R2_ENABLED=false
APP_STORAGE_R2_BUCKET_NAME valueFrom=ircs-dev-app-config/R2_BUCKET_NAME
APP_STORAGE_R2_PUBLIC_DOMAIN valueFrom=ircs-dev-app-config/R2_PUBLIC_DOMAIN
APP_STORAGE_R2_* credential env absent
APP_STORAGE_R2_WATCHDOG_* temporary env absent
cover fixture residue=0
member fixture residue=0
local files absent
q.storage.avatar_sync and q.storage.avatar_sync.dlq no residue
port-forward ports 18090/18093 no listener
```

## 当前状态

已完成，移动到 `tasks/done/028-07-j-storage-r2-watchdog-recovery-smoke.md`。
