# 090 028-07-I storage-service R2 avatar path 真实链路 smoke 报告

状态：已完成

日期：2026-06-09

## 背景

`028-07-G` 已通过显式 V1 parity bucket override 验证 R2 单对象
`PUT -> HEAD -> DELETE -> HEAD 404`，bucket 为 `ircs`，常驻 dev bucket 仍保持
`ircs-dev`。

`028-07-H` 已完成 cover path 真实业务链路 smoke：通过 storage-service 上传 cover fixture，
再触发 `sync-r2` 写入 `https://img.mnnu.eu.org/covers/...`，并完成 R2、DB、本地文件、
临时 Secret 和 Deployment env 清理。

本轮 `028-07-I` 覆盖 avatar path，避免 `028-07` 的 storage parity 只验证 cover。仓库已有
`028-07-D` avatar R2 live smoke，本轮在其基础上复验显式 V1 bucket 语义，并把 cleanup evidence
补到可审计字段。

## 目标

- 使用 `scripts/smoke/storage-avatar-r2-live-02807-smoke.ps1` 执行 avatar path 真实链路。
- 显式使用 `-UseV1ParityBucketOverride`，effective bucket 必须为 V1 bucket `ircs`。
- 通过 portal/identity 上传一个临时 1x1 PNG avatar，触发 `q.storage.avatar_sync`。
- 验证 storage-service avatar consumer 将 `members.avatar_url` 回写为
  `https://img.mnnu.eu.org/avatars/...`。
- 验证 R2 object HEAD 成功、本地 `/app/storage/avatars/...` 文件被删除。
- cleanup 后验证 R2 HEAD notFound、member fixture residual=0、queue/DLQ 无新增残留、临时
  Secret 和 Deployment env 恢复。
- 复查 `ircs-dev` 无 HTTPRoute 污染，ResourceQuota 仍保持低资源状态。

## 非目标

- 不修改 config-service、content-service、Douban provider、notification/ops、portal/admin/BFF。
- 不永久启用 storage-service R2 runtime。
- 不修改 dev 常驻 ConfigMap bucket。
- 不执行批量 avatar 或历史 avatar 迁移。
- 不覆盖 cover path，cover path 已由 `028-07-H` 完成。

## 影响范围

允许写入：

```text
scripts/smoke/storage-avatar-r2-live-02807-smoke.ps1
docs/reports/090-02807-storage-service-r2-avatar-path-smoke.md
tasks/in-progress/028-07-i-storage-service-r2-avatar-path-smoke.md
tasks/done/028-07-i-storage-service-r2-avatar-path-smoke.md
tasks/in-progress/028-07-r2-live-watchdog-parity.md
```

如发现 avatar 业务代码缺失，仅允许在 `services/ircs-storage-service/**` 做最小修复。本轮预期只补
smoke evidence 输出。

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

## 验收标准

```text
MARK 1/7 avatar R2 credential/config resolves V1 bucket override
MARK 2/7 storage-service temporary R2 runtime is enabled without watchdog scan
MARK 3/7 portal register/login fixture is active for avatar upload
MARK 4/7 portal/identity avatar upload returns local media URL and publishes SYNC_AVATAR
MARK 5/7 q.storage.avatar_sync drains and members.avatar_url becomes R2 public URL
MARK 6/7 R2 object exists and local avatar file is cleaned
MARK 7/7 R2 object/member/queue cleanup and dev env restore complete
```

补充 evidence 必须包含：

```text
effectiveBucket=ircs
configMapBucket=ircs-dev
storagePath=avatars/...
r2Url=https://img.mnnu.eu.org/avatars/...
r2HeadAfterDeleteNotFound=true
memberResidue=0
localAvatarMissing=true
temporary secret absent
APP_STORAGE_R2_ENABLED=false
APP_STORAGE_R2_BUCKET_NAME valueFrom=ircs-dev-app-config.R2_BUCKET_NAME
APP_STORAGE_R2_PUBLIC_DOMAIN valueFrom=ircs-dev-app-config.R2_PUBLIC_DOMAIN
HTTPRoute absent
ResourceQuota low-resource state recorded
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

## 实施结果

- 最小补强 `scripts/smoke/storage-avatar-r2-live-02807-smoke.ps1` 成功 JSON 输出：
  - 新增 `-TaskId`，本轮输出 `task=028-07-I`。
  - 新增 `r2HeadAfterPutStatusCode`、`r2DeleteStatusCode`、
    `r2HeadAfterDeleteStatusCode`、`r2HeadAfterDeleteNotFound`。
  - 新增 `memberResidue`、`localAvatarMissing`、`publicUrl`。
- 未修改 storage-service 业务代码。
- 未修改 deploy 常驻 manifest 或 dev ConfigMap bucket。

## 验证结果

脚本解析：

```powershell
$path = '.\scripts\smoke\storage-avatar-r2-live-02807-smoke.ps1'; $tokens = $null; $errors = $null; [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path -LiteralPath $path), [ref]$tokens, [ref]$errors) | Out-Null; if ($errors.Count -gt 0) { $errors | ForEach-Object { $_.ToString() }; exit 1 }; "PARSER_OK $path"
```

结果：

```text
PARSER_OK .\scripts\smoke\storage-avatar-r2-live-02807-smoke.ps1
```

真实 smoke 命令：

```powershell
.\scripts\smoke\storage-avatar-r2-live-02807-smoke.ps1 -TaskId '028-07-I' -StartCredentialPortForward -UseKubernetesSecretToken -UseV1ParityBucketOverride -ConfirmAvatarR2Write
```

关键 MARK：

```text
MARK 1/7 avatar R2 credential/config resolves V1 bucket override
MARK 2/7 storage-service temporary R2 runtime is enabled without watchdog scan
MARK 3/7 portal register/login fixture is active for avatar upload
MARK 4/7 portal/identity avatar upload returns local media URL and publishes SYNC_AVATAR
MARK 5/7 q.storage.avatar_sync drains and members.avatar_url becomes R2 public URL
MARK 6/7 R2 object exists and local avatar file is cleaned
MARK 7/7 R2 object/member/queue cleanup and dev env restore complete
```

JSON 摘要：

```text
status=SUCCESS
reason=AVATAR_R2_LIVE_SMOKE_OK
checkedAt=2026-06-09T10:35:28.5416255Z
namespace=ircs-dev
traceId=02807-avatar-r2-live-20260609183215
credentialId=133e890a-fe1c-40c4-89c6-65d7f757bd57
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

## 后置检查

```powershell
kubectl -n ircs-dev get secret ircs-r2-avatar-smoke-02807 --ignore-not-found
kubectl -n ircs-dev exec postgres-0 -- psql -U postgres -d ircs -tAc "select count(*) from members where email = 'codex.02807.avatar.20260609183215@example.invalid';"
kubectl -n ircs-dev exec deploy/ircs-storage-service -- sh -c "test ! -f '/app/storage/avatars/3220fc7838ccdab0a3a21b5d8498275c7abe6930abbb059e9fdb4939bba8c2fd.png' && echo LOCAL_FILE_ABSENT"
kubectl -n ircs-dev exec rabbitmq-0 -- rabbitmqctl list_queues name messages messages_ready messages_unacknowledged consumers
kubectl -n ircs-dev get deployment ircs-storage-service -o json
kubectl -n ircs-dev get httproute --ignore-not-found
kubectl -n ircs-dev get resourcequota ircs-dev-quota
Get-NetTCPConnection -LocalPort 18086,18090 -ErrorAction SilentlyContinue
```

结果：

```text
temporary secret ircs-r2-avatar-smoke-02807: absent
member fixture residue: 0
local file: LOCAL_FILE_ABSENT
q.storage.avatar_sync: messages=0 ready=0 unacked=0 consumers=1
q.storage.avatar_sync.dlq: messages=0 ready=0 unacked=0 consumers=0
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

- R2 object：`avatars/3220fc7838ccdab0a3a21b5d8498275c7abe6930abbb059e9fdb4939bba8c2fd.png`
  DELETE 返回 `204`，DELETE 后 HEAD 返回 `404`。
- DB：测试 member 邮箱 `codex.02807.avatar.20260609183215@example.invalid` residual=0。
- 本地文件：`/app/storage/avatars/3220fc7838ccdab0a3a21b5d8498275c7abe6930abbb059e9fdb4939bba8c2fd.png`
  不存在。
- Queue：`q.storage.avatar_sync` 和 DLQ 无消息残留。
- K8S：临时 Secret 不存在，storage-service Deployment 已恢复，HTTPRoute 无残留。

## 遗留边界

- 本轮只覆盖单个 avatar fixture，不覆盖历史 avatar 批量迁移。
- 本轮不覆盖 watchdog 长周期恢复、batch size 或失败退避在真实样本下的长时间行为。
