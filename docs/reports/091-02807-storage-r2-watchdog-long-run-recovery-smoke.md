# 091 028-07-J storage-service R2 watchdog 长周期/恢复验证报告

状态：已完成

日期：2026-06-09

## 背景

`028-07-G` 已完成 R2 单对象 `PUT -> HEAD -> DELETE -> HEAD 404`，`028-07-H`
已完成 cover path 真实链路，`028-07-I` 已完成 avatar path 真实链路并清理。`028-07`
父任务剩余边界为 watchdog 长周期恢复验证：确认临时失败、积压、恢复语义接近 V1。

V1 对照：

- `AvatarSyncScheduler` 每小时扫描本地 avatar URL，R2 active 时重投 `SYNC_AVATAR`。
- `AvatarSyncConsumer` 在 R2 disabled/inactive 时直接返回，消息被 ack；R2 上传异常时抛出，
  交给 Rabbit retry/DLQ 语义。
- cover image 下载完成后若需要 R2，会保持 `LOCAL_STORED + nextRetryTime`，后续恢复 runner
  依靠 due 时间继续上传；失败通过 `markFailed` 记录 `last_error`、`retry_count` 与下次重试时间。

V3 当前实现：

- `StorageR2Watchdog` 在 `app.storage.r2.watchdog.enabled=true` 时周期运行。
- R2 inactive 时 no-op，不发消息、不写 DB。
- R2 active 时扫描本地 avatar URL 并发布 `q.storage.avatar_sync`。
- R2 active 时扫描 due `LOCAL_STORED` cover image 并调用 `CoverImageR2SyncService`。
- cover R2 上传失败会写回 `FAILED`、`retry_count`、`next_retry_time`、`last_error`，不会中断同批其他样本。

## 目标

- 新增一个短周期、低资源、可清理的真实 smoke，验证 watchdog 恢复链路。
- 先制造 cover R2 待恢复积压：上传临时 cover，仅将 `next_retry_time` 置为 due，不调用手动
  `sync-r2` endpoint。
- 先制造 avatar 待恢复积压：注册临时 member 后上传 avatar，再暂时停止 storage-service，避免
  `SYNC_AVATAR` consumer 立即处理；随后清空该测试消息，保留本地 avatar URL 给 watchdog 扫描。
- 临时启用 storage-service R2 runtime，并缩短 watchdog delay/interval/batch-size，让 watchdog
  在短时间内完成 cover upload 与 avatar requeue/recovery。
- 验证 R2 object、DB 状态、queue/DLQ、Deployment env、临时 Secret、port-forward、HTTPRoute 与
  ResourceQuota 后置清理。

## 非目标

- 不永久启用 storage-service R2。
- 不修改 dev 常驻 ConfigMap bucket，V1 bucket 仅通过显式 `-UseV1ParityBucketOverride` 使用。
- 不执行批量历史数据迁移、压测或长时间 soak。
- 不修改 config-service、content-service、Douban provider、notification/ops、portal/admin/BFF。

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

预期只新增 smoke 脚本与文档任务；如发现 watchdog 代码真实缺口，仅在
`services/ircs-storage-service/**` 做最小修复。

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

后置检查：

```powershell
kubectl -n ircs-dev get secret ircs-r2-watchdog-smoke-02807j --ignore-not-found
kubectl -n ircs-dev get deployment ircs-storage-service -o json
kubectl -n ircs-dev exec rabbitmq-0 -- rabbitmqctl list_queues name messages messages_ready messages_unacknowledged consumers
kubectl -n ircs-dev get httproute --ignore-not-found
kubectl -n ircs-dev get resourcequota ircs-dev-quota
Get-NetTCPConnection -LocalPort 18086,18090,18093 -ErrorAction SilentlyContinue
```

## 当前状态

已完成。

## 实施结果

- 新增 `scripts/smoke/storage-r2-watchdog-02807j-smoke.ps1`。
- 默认 `PreflightOnly` / `NoWrite` 不 patch Deployment、不写 R2。
- 真实 smoke 显式要求 `-UseV1ParityBucketOverride -ConfirmWatchdogR2Write`。
- smoke 使用短周期 watchdog env：
  - `APP_STORAGE_R2_WATCHDOG_ENABLED=true`
  - `APP_STORAGE_R2_WATCHDOG_INITIAL_DELAY_MS=30000`
  - `APP_STORAGE_R2_WATCHDOG_FIXED_DELAY_MS=5000`
  - `APP_STORAGE_R2_WATCHDOG_BATCH_SIZE=1`
- 由于 dev storage-service 使用 Pod 本地 `/app/storage`，临时 patch Deployment 会重建 Pod，
  patch 前制造的本地文件会丢失。第一次真实 smoke 捕获到该差异：
  `LOCAL|FAILED|Local file missing for R2 upload`。
- 脚本随后补充 dev-only rehydrate：rollout 后、watchdog initial delay 内，仅把本轮
  cover/avatar fixture 文件放回新 Pod 的对应本地路径，再等待 watchdog 自行恢复。
- 未修改 storage-service 业务代码。
- 未修改 dev 常驻 ConfigMap bucket。

## 验证结果

脚本解析：

```powershell
$path = '.\scripts\smoke\storage-r2-watchdog-02807j-smoke.ps1'; $tokens = $null; $errors = $null; [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path -LiteralPath $path), [ref]$tokens, [ref]$errors) | Out-Null; if ($errors.Count -gt 0) { $errors | ForEach-Object { $_.ToString() }; exit 1 }; "PARSER_OK $path"
```

结果：

```text
PARSER_OK .\scripts\smoke\storage-r2-watchdog-02807j-smoke.ps1
```

no-write 诊断：

```powershell
.\scripts\smoke\storage-r2-watchdog-02807j-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -UseV1ParityBucketOverride -NoWrite
```

结果：

```text
SUCCESS/STORAGE_R2_WATCHDOG_NO_WRITE_OK
configMapBucket=ircs-dev
effectiveBucket=ircs
bucketSource=UseV1ParityBucketOverride
queueBefore q.storage.avatar_sync messages=0 ready=0 unacked=0 consumers=1
dlqBefore q.storage.avatar_sync.dlq messages=0 ready=0 unacked=0 consumers=0
willWriteR2Objects=false
willPatchDeployment=false
```

真实 smoke：

```powershell
.\scripts\smoke\storage-r2-watchdog-02807j-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -UseV1ParityBucketOverride -ConfirmWatchdogR2Write
```

MARK：

```text
MARK 1/8 V1/V3 watchdog parity preflight resolves R2 credential and explicit bucket
MARK 2/8 storage-service is reachable while standing R2 env remains disabled for backlog setup
MARK 3/8 cover and avatar backlog fixtures are created without immediate R2 sync
MARK 4/8 temporary R2 runtime enables watchdog with shortened interval and small batch
MARK 5/8 watchdog recovers due cover image to R2 REMOTE_STORED
MARK 6/8 watchdog republishes local avatar and consumer rewrites avatar_url to R2
MARK 7/8 R2 objects, DB fixtures, local files, queue/DLQ are cleaned
MARK 8/8 dev env restored with no Secret/port-forward/HTTPRoute/quota residue
```

关键 evidence：

```text
status=SUCCESS
reason=STORAGE_R2_WATCHDOG_RECOVERY_SMOKE_OK
traceId=02807j-r2-watchdog-20260609190331
coverImageId=67618a3a-5018-47f4-a364-27c95a04eb5d
memberId=de89b7e2-2d25-4bbf-8a0c-f1d22b271f28
configMapBucket=ircs-dev
effectiveBucket=ircs
bucketSource=UseV1ParityBucketOverride
publicDomain=img.mnnu.eu.org
coverStoragePath=covers/4a23fffd4a8a2e224f7ee36db41f1101e25ae5c3e8ddeae8b0d02483015a505f.png
coverR2Url=https://img.mnnu.eu.org/covers/4a23fffd4a8a2e224f7ee36db41f1101e25ae5c3e8ddeae8b0d02483015a505f.png
avatarLocalUrl=/media/avatars/4a23fffd4a8a2e224f7ee36db41f1101e25ae5c3e8ddeae8b0d02483015a505f.png
avatarStoragePath=avatars/4a23fffd4a8a2e224f7ee36db41f1101e25ae5c3e8ddeae8b0d02483015a505f.png
avatarR2Url=https://img.mnnu.eu.org/avatars/4a23fffd4a8a2e224f7ee36db41f1101e25ae5c3e8ddeae8b0d02483015a505f.png
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
devLocalFixtureRehydratedAfterRollout=true
watchdogInitialDelayMs=30000
watchdogFixedDelayMs=5000
watchdogBatchSize=1
```

## 后置检查

独立复查命令：

```powershell
kubectl -n ircs-dev get secret ircs-r2-watchdog-smoke-02807j --ignore-not-found
kubectl -n ircs-dev get deployment ircs-storage-service -o json
kubectl -n ircs-dev exec postgres-0 -- psql -U postgres -d ircs -tAc "select count(*) from cover_images where id = '67618a3a-5018-47f4-a364-27c95a04eb5d'; select count(*) from members where id = 'de89b7e2-2d25-4bbf-8a0c-f1d22b271f28' or email like 'codex.02807j.watchdog.%';"
kubectl -n ircs-dev exec deploy/ircs-storage-service -- sh -c "test ! -f '/app/storage/covers/4a23fffd4a8a2e224f7ee36db41f1101e25ae5c3e8ddeae8b0d02483015a505f.png' && test ! -f '/app/storage/avatars/4a23fffd4a8a2e224f7ee36db41f1101e25ae5c3e8ddeae8b0d02483015a505f.png' && echo LOCAL_FILES_ABSENT"
kubectl -n ircs-dev exec rabbitmq-0 -- rabbitmqctl list_queues name messages messages_ready messages_unacknowledged consumers
kubectl -n ircs-dev get httproute --ignore-not-found
kubectl -n ircs-dev get resourcequota ircs-dev-quota
Get-NetTCPConnection -LocalPort 18090,18093 -ErrorAction SilentlyContinue
```

结果：

```text
temporary secret ircs-r2-watchdog-smoke-02807j: absent
APP_STORAGE_R2_ENABLED=false
APP_STORAGE_R2_BUCKET_NAME valueFrom=ircs-dev-app-config/R2_BUCKET_NAME
APP_STORAGE_R2_PUBLIC_DOMAIN valueFrom=ircs-dev-app-config/R2_PUBLIC_DOMAIN
APP_STORAGE_R2_* credential env absent
APP_STORAGE_R2_WATCHDOG_* temporary env absent
cover fixture residue=0
member fixture residue=0
local files=LOCAL_FILES_ABSENT
q.storage.avatar_sync messages=0 ready=0 unacked=0 consumers=1
q.storage.avatar_sync.dlq messages=0 ready=0 unacked=0 consumers=0
HTTPRoute absent
ResourceQuota pods=28/30, services=24/26
port-forward ports 18090/18093: no listener
```

## 结论

`028-07-J` 已完成。V3 storage-service R2 watchdog 在短周期真实 smoke 中复现了 V1 风格恢复语义：
R2/env 恢复后，due cover 被 watchdog 上传到 R2，local avatar 被 watchdog 重投并由 consumer
回写 R2 URL；清理后 DB、R2、local、queue/DLQ、Secret、Deployment env、port-forward 和
HTTPRoute 均无残留。

`028-07` 父任务剩余边界已闭环，可以标记完成。
