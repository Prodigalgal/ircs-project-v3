# 028-07-I storage-service R2 avatar path 真实链路 smoke task spec

状态：已完成

父任务：

- `tasks/in-progress/028-07-r2-live-watchdog-parity.md`

上游来源：

- `tasks/done/028-07-g-real-r2-explicit-v1-bucket-write-smoke.md`
- `tasks/done/028-07-h-r2-cover-explicit-v1-bucket-chain-smoke.md`
- `docs/reports/090-02807-storage-service-r2-avatar-path-smoke.md`
- `docs/reports/084-02807-avatar-r2-live-smoke.md`

## 背景

`028-07-H` 已用 V1 parity bucket override 完成 cover path 真实链路：bucket `ircs`、
URL `https://img.mnnu.eu.org/covers/...`、DB/R2/local/Secret/Deployment cleanup 全部通过。
当前需要覆盖 avatar path，避免 storage-service R2 parity 只停留在 cover。

仓库已有 `028-07-D` avatar R2 live smoke，本任务不是重写业务链路，而是以
`028-07-E/G/H` 之后的显式 V1 bucket 策略复验 avatar path，并补齐 cleanup evidence 输出。

## 目标

- 显式使用 `-UseV1ParityBucketOverride` 走 V1 bucket `ircs`。
- 通过 portal/identity 上传临时 avatar，触发 `q.storage.avatar_sync`。
- 验证 storage-service avatar consumer 上传 R2、回写 `members.avatar_url`、删除本地文件。
- 验证 public URL 为 `https://img.mnnu.eu.org/avatars/...`。
- cleanup 后确认 R2 HEAD notFound、member residual=0、local residual=0、queue/DLQ 无新增残留。
- 复查临时 Secret、Deployment env、HTTPRoute、ResourceQuota 和 port-forward 残留。

## 非目标

- 不修改 config-service、content-service、Douban provider、notification/ops、portal/admin/BFF。
- 不永久启用 R2 或修改常驻 dev bucket。
- 不执行批量历史 avatar 修复。
- 不覆盖 cover path。

## 输入

```text
scripts/smoke/storage-avatar-r2-live-02807-smoke.ps1
ircs-dev credential-service R2 lease
ircs-dev portal-bff / identity-service / storage-service / RabbitMQ / PostgreSQL
V1 parity bucket ircs
```

## 输出

```text
scripts/smoke/storage-avatar-r2-live-02807-smoke.ps1
docs/reports/090-02807-storage-service-r2-avatar-path-smoke.md
tasks/in-progress/028-07-i-storage-service-r2-avatar-path-smoke.md
```

任务完成后移动到：

```text
tasks/done/028-07-i-storage-service-r2-avatar-path-smoke.md
```

## 影响范围

允许写入：

```text
scripts/smoke/storage-avatar-r2-live-02807-smoke.ps1
docs/reports/090-02807-storage-service-r2-avatar-path-smoke.md
tasks/in-progress/028-07-i-storage-service-r2-avatar-path-smoke.md
tasks/done/028-07-i-storage-service-r2-avatar-path-smoke.md
tasks/in-progress/028-07-r2-live-watchdog-parity.md
```

如 avatar 代码真实缺失，仅允许最小修复：

```text
services/ircs-storage-service/**
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
- `ircs-credential-service` 可通过 port-forward 租用 R2 credential。
- `ircs-dev-secrets.INTERNAL_CREDENTIAL_TOKEN` 可读取。
- `ircs-portal-bff` 可临时 port-forward 完成注册、PoW、登录与头像上传。
- `ircs-storage-service` 可临时注入 R2 env，并在完成后恢复。
- `q.storage.avatar_sync` 至少有一个 storage-service consumer。

## 风险

- 真实 R2 写入会短暂创建一个 `avatars/*` object，必须 DELETE 并确认 HEAD 404。
- avatar path 跨 portal/identity/storage/Rabbit/PostgreSQL/R2，失败时必须保留 traceId、
  memberId、storagePath 和 cleanup 状态。
- 临时 patch storage-service Deployment 必须恢复到 `APP_STORAGE_R2_ENABLED=false` 和
  ConfigMap `valueFrom`。
- 并行 worker 可能同时使用 dev 环境，smoke 只能写入独立邮箱、独立 object key 和固定临时
  Secret，并在 finally 中清理。

## 验收标准

```text
命令显式包含 -UseV1ParityBucketOverride。
输出包含 effectiveBucket=ircs 与 configMapBucket=ircs-dev。
输出包含 memberId、localAvatarUrl、storagePath、r2Url。
MARK 1/7 到 MARK 7/7 全部通过。
R2 object DELETE 后 HEAD 404/notFound 已确认。
members fixture residual=0。
本地 /app/storage/avatars/... residual=0。
q.storage.avatar_sync 和 q.storage.avatar_sync.dlq 无新增消息残留。
临时 Secret ircs-r2-avatar-smoke-02807 不存在。
storage-service Deployment 恢复 APP_STORAGE_R2_ENABLED=false。
APP_STORAGE_R2_BUCKET_NAME 与 APP_STORAGE_R2_PUBLIC_DOMAIN 恢复为 ConfigMap valueFrom。
无 HTTPRoute 残留，ResourceQuota 和 port-forward 状态记录清楚。
```

## 测试边界

脚本解析：

```powershell
$path = '.\scripts\smoke\storage-avatar-r2-live-02807-smoke.ps1'; $tokens = $null; $errors = $null; [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path -LiteralPath $path), [ref]$tokens, [ref]$errors) | Out-Null; if ($errors.Count -gt 0) { $errors | ForEach-Object { $_.ToString() }; exit 1 }; "PARSER_OK $path"
```

真实 smoke：

```powershell
.\scripts\smoke\storage-avatar-r2-live-02807-smoke.ps1 -TaskId 028-07-I -StartCredentialPortForward -UseKubernetesSecretToken -UseV1ParityBucketOverride -ConfirmAvatarR2Write
```

后置检查：

```powershell
kubectl -n ircs-dev get secret ircs-r2-avatar-smoke-02807 --ignore-not-found
kubectl -n ircs-dev get deployment ircs-storage-service -o json
kubectl -n ircs-dev get httproute --ignore-not-found
kubectl -n ircs-dev get resourcequota ircs-dev-quota
Get-NetTCPConnection -LocalPort 18086,18090 -ErrorAction SilentlyContinue
```

不执行：

```text
storage-service cover path smoke
credential-only R2 object smoke
watchdog 长周期 smoke
批量 avatar/cover smoke
全量 Gradle 测试
```

## 实施记录

- 最小补强 `scripts/smoke/storage-avatar-r2-live-02807-smoke.ps1`：
  - 新增 `-TaskId`，本轮输出 `task=028-07-I`。
  - 成功 JSON 增加 R2 cleanup、member residual、本地文件 residual 和 public URL evidence。
- 未修改 `services/ircs-storage-service/**` 业务代码。
- 未修改 dev 常驻 ConfigMap bucket 或 deploy manifest。

## 验收结果

脚本解析：

```powershell
$path = '.\scripts\smoke\storage-avatar-r2-live-02807-smoke.ps1'; $tokens = $null; $errors = $null; [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path -LiteralPath $path), [ref]$tokens, [ref]$errors) | Out-Null; if ($errors.Count -gt 0) { $errors | ForEach-Object { $_.ToString() }; exit 1 }; "PARSER_OK $path"
```

结果：

```text
PARSER_OK .\scripts\smoke\storage-avatar-r2-live-02807-smoke.ps1
```

真实 smoke：

```powershell
.\scripts\smoke\storage-avatar-r2-live-02807-smoke.ps1 -TaskId '028-07-I' -StartCredentialPortForward -UseKubernetesSecretToken -UseV1ParityBucketOverride -ConfirmAvatarR2Write
```

结果：

```text
MARK 1/7 avatar R2 credential/config resolves V1 bucket override
MARK 2/7 storage-service temporary R2 runtime is enabled without watchdog scan
MARK 3/7 portal register/login fixture is active for avatar upload
MARK 4/7 portal/identity avatar upload returns local media URL and publishes SYNC_AVATAR
MARK 5/7 q.storage.avatar_sync drains and members.avatar_url becomes R2 public URL
MARK 6/7 R2 object exists and local avatar file is cleaned
MARK 7/7 R2 object/member/queue cleanup and dev env restore complete
SUCCESS/AVATAR_R2_LIVE_SMOKE_OK
```

关键 evidence：

```text
traceId=02807-avatar-r2-live-20260609183215
memberId=d1037ba8-47fd-4c10-a290-5cf8853906ad
configMapBucket=ircs-dev
effectiveBucket=ircs
bucketSource=UseV1ParityBucketOverride
publicDomain=img.mnnu.eu.org
localAvatarUrl=/media/avatars/3220fc7838ccdab0a3a21b5d8498275c7abe6930abbb059e9fdb4939bba8c2fd.png
storagePath=avatars/3220fc7838ccdab0a3a21b5d8498275c7abe6930abbb059e9fdb4939bba8c2fd.png
r2Url=https://img.mnnu.eu.org/avatars/3220fc7838ccdab0a3a21b5d8498275c7abe6930abbb059e9fdb4939bba8c2fd.png
r2HeadAfterPutStatusCode=200
r2DeleteStatusCode=204
r2HeadAfterDeleteStatusCode=404
r2HeadAfterDeleteNotFound=true
memberResidue=0
localAvatarMissing=true
queueAfterCleanup=q.storage.avatar_sync messages=0 ready=0 unacked=0 consumers=1
dlqAfterCleanup=q.storage.avatar_sync.dlq messages=0 ready=0 unacked=0 consumers=0
HTTPRoute=<empty>
ResourceQuota=pods 28/30, services 24/26
```

后置检查：

```text
temporary secret ircs-r2-avatar-smoke-02807: absent
member fixture residue: 0
local file: LOCAL_FILE_ABSENT
APP_STORAGE_R2_ENABLED=value:false
APP_STORAGE_R2_BUCKET_NAME=valueFrom:ircs-dev-app-config.R2_BUCKET_NAME
APP_STORAGE_R2_PUBLIC_DOMAIN=valueFrom:ircs-dev-app-config.R2_PUBLIC_DOMAIN
APP_STORAGE_R2_ACCOUNT_ID=<absent>
APP_STORAGE_R2_ACCESS_KEY=<absent>
APP_STORAGE_R2_SECRET_KEY=<absent>
APP_STORAGE_R2_ENDPOINT=<absent>
APP_STORAGE_R2_WATCHDOG_ENABLED=<absent>
ConfigMap R2_BUCKET_NAME=ircs-dev
ConfigMap R2_PUBLIC_DOMAIN=img.mnnu.eu.org
HTTPRoute: absent
ResourceQuota: pods=28/30, services=24/26
port-forward ports 18086/18090: no owning process; 18090 only had TIME_WAIT owningProcess=0
```

## 残留状态

无已知残留。

- R2 object：DELETE 后 HEAD 404/notFound 已确认。
- DB：测试 member residual=0。
- local：avatar file residual=0。
- queue：avatar sync queue 和 DLQ 无新增残留。
- K8S：临时 Secret 不存在，storage-service Deployment 已恢复，无 HTTPRoute。

## 当前状态

已完成，移动到 `tasks/done/028-07-i-storage-service-r2-avatar-path-smoke.md`。
