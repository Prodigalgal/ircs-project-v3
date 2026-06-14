# 028-07-H R2 cover explicit V1 bucket chain smoke task spec

状态：已完成

父任务：

- `tasks/in-progress/028-07-r2-live-watchdog-parity.md`

上游来源：

- `tasks/done/028-07-g-real-r2-explicit-v1-bucket-write-smoke.md`
- `tasks/in-progress/028-07-r2-live-watchdog-parity.md`
- `docs/reports/084-02807-avatar-r2-live-smoke.md`

## 背景

`028-07-G` 已验证 V1 bucket `ircs` 在显式 override 下可完成单对象
`PUT -> HEAD -> DELETE -> HEAD 404`，且不修改常驻 dev bucket。
本轮需要在真实业务链路中选择 cover path 或 avatar path 之一继续做最小 smoke。

本任务选择 cover path。原因是 cover path 只需要 storage-service 的
`/api/v1/cover-images/upload` 与 `/api/v1/cover-images/{id}/sync-r2`，
副作用集中在一个 `cover_images` fixture row、一个 `covers/*` R2 object、
临时 Secret 与 storage-service Deployment runtime env；相比 avatar path 不需要创建
portal member、登录态或等待 `q.storage.avatar_sync` 队列消费，cleanup 边界更窄。

## 目标

- 使用 `scripts/smoke/storage-service-r2-path-02807-smoke.ps1` 执行一条真实 cover path smoke。
- 显式使用 V1 bucket override：`-UseV1ParityBucketOverride`，实际 bucket 应为 `ircs`。
- 只上传一个 1x1 PNG fixture，经 storage-service cover upload 和 `sync-r2` 写入 R2。
- 记录 object key、`cover_images` row id、URL、副作用和 cleanup evidence。
- smoke 结束后确认 R2 object、DB row、临时 Secret、port-forward 和 Deployment patch 无残留。
- 常驻 `ircs-dev-app-config.R2_BUCKET_NAME` 保持 `ircs-dev`，不修改 dev bucket。

## 非目标

- 不执行 avatar path。
- 不执行批量 cover/image 样本。
- 不修改 `services/**` 业务代码。
- 不修改 `deploy/**` 常驻 manifest。
- 不触碰 Rabbit、Douban、SMTP、normalization。
- 不更新 `docs/index.md`、`tasks/index.md`、`docs/reports/089`、`docs/migrations/089`。

## 输入

```text
scripts/smoke/storage-service-r2-path-02807-smoke.ps1
tasks/done/028-07-g-real-r2-explicit-v1-bucket-write-smoke.md
tasks/in-progress/028-07-r2-live-watchdog-parity.md
docs/reports/084-02807-avatar-r2-live-smoke.md
```

## 输出

```text
tasks/in-progress/028-07-h-r2-cover-explicit-v1-bucket-chain-smoke.md
```

任务完成后移动到：

```text
tasks/done/028-07-h-r2-cover-explicit-v1-bucket-chain-smoke.md
```

## 影响范围

允许写入：

```text
tasks/in-progress/028-07-h-r2-cover-explicit-v1-bucket-chain-smoke.md
tasks/done/028-07-h-r2-cover-explicit-v1-bucket-chain-smoke.md
scripts/smoke/storage*
```

本轮预计不需要修改 smoke 脚本；若真实 smoke 暴露输出缺口，只允许最小补强
`scripts/smoke/storage-service-r2-path-02807-smoke.ps1` 的 smoke evidence 输出。

不包含：

```text
services/**
deploy/**
docs/index.md
tasks/index.md
docs/reports/089*
docs/migrations/089*
Rabbit/Douban/SMTP/normalization
```

## 依赖

- `kubectl` 可访问 `ircs-dev` namespace。
- `ircs-credential-service` 可通过 port-forward 获取 R2 credential lease。
- `ircs-dev-secrets.INTERNAL_CREDENTIAL_TOKEN` 可供 smoke 读取。
- `ircs-storage-service` 可临时启用 R2 runtime，并在 smoke 后恢复。
- V1 R2 credential 可对 bucket `ircs` 执行 cover object HEAD/DELETE。

## 风险

- 真实 R2 写入会短暂创建一个 `covers/*` object；失败时必须记录 key 和 cleanup 状态。
- smoke 会临时 patch storage-service Deployment env；必须恢复到 manifest/ConfigMap 绑定状态。
- 若 storage-service rollout、credential lease 或 R2 cleanup 任一步失败，需要记录残留风险。
- 本任务只覆盖 cover path，不证明 avatar path 或 watchdog 长周期恢复。

## 验收标准

```text
命令显式包含 -UseV1ParityBucketOverride。
输出包含 effectiveBucket=ircs 且 configMapBucket=ircs-dev。
输出包含 coverImageId、storagePath、publicDomain/URL 语义。
MARK 1/6 到 MARK 6/6 全部通过。
R2 object DELETE 后 HEAD 404/notFound 已确认。
cover_images fixture residue=0。
临时 Secret ircs-r2-smoke-02807 不存在。
storage-service Deployment 恢复 APP_STORAGE_R2_ENABLED=false。
APP_STORAGE_R2_BUCKET_NAME 与 APP_STORAGE_R2_PUBLIC_DOMAIN 恢复为 ConfigMap valueFrom。
无 HTTPRoute 残留，port-forward 进程已结束或记录残留。
```

## 测试边界

真实 smoke 命令：

```powershell
.\scripts\smoke\storage-service-r2-path-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -UseV1ParityBucketOverride -ConfirmStorageServiceR2Write
```

后置检查：

```powershell
kubectl -n ircs-dev get secret ircs-r2-smoke-02807 --ignore-not-found
kubectl -n ircs-dev get deployment ircs-storage-service -o json
kubectl -n ircs-dev get httproute --ignore-not-found
kubectl -n ircs-dev get resourcequota ircs-dev-quota
```

不执行：

```text
storage-avatar-r2-live-02807-smoke.ps1
批量 cover/avatar smoke
watchdog 长周期 smoke
任何业务代码测试或构建
```

## 实施记录

选择链路：cover path。

执行命令：

```powershell
.\scripts\smoke\storage-service-r2-path-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -UseV1ParityBucketOverride -ConfirmStorageServiceR2Write
```

命令结果：成功。关键 MARK：

```text
MARK 1/6 storage-service R2 path smoke resolves V1 bucket or explicit override
MARK 2/6 storage-service temporary R2 runtime is enabled without watchdog scan
MARK 3/6 fixture object is uploaded through storage-service path
MARK 4/6 DB/media URL side effect matches V1 public domain semantics
MARK 5/6 R2 object and DB fixture are cleaned
MARK 6/6 dev env restored with no HTTPRoute/quota/port-forward residue
```

JSON 摘要：

```text
status=SUCCESS
reason=STORAGE_SERVICE_R2_PATH_SMOKE_OK
checkedAt=2026-06-09T09:34:33.6663432Z
namespace=ircs-dev
traceId=02807-storage-r2-path-20260609173145
credentialId=133e890a-fe1c-40c4-89c6-65d7f757bd57
configMapBucket=ircs-dev
effectiveBucket=ircs
bucketSource=UseV1ParityBucketOverride
publicDomain=img.mnnu.eu.org
coverImageId=d2412baf-2c04-48a5-b7fa-b9a7ae6dbafa
storagePath=covers/3220fc7838ccdab0a3a21b5d8498275c7abe6930abbb059e9fdb4939bba8c2fd.png
publicUrl=https://img.mnnu.eu.org/covers/3220fc7838ccdab0a3a21b5d8498275c7abe6930abbb059e9fdb4939bba8c2fd.png
httpRoute=<empty>
resourceQuota=pods 28/30, services 24/26
```

脚本成功路径在 `MARK 5/6` 前执行：

```text
R2 DELETE covers/3220fc7838ccdab0a3a21b5d8498275c7abe6930abbb059e9fdb4939bba8c2fd.png
R2 HEAD after DELETE with AllowNotFound
delete from cover_images where id='d2412baf-2c04-48a5-b7fa-b9a7ae6dbafa'
assert R2 missing.notFound=true and dbResidue=0
```

## 验收结果

通过。

后置检查：

```powershell
kubectl -n ircs-dev get secret ircs-r2-smoke-02807 --ignore-not-found
kubectl -n ircs-dev exec postgres-0 -- psql -U postgres -d ircs -tAc "select count(*) from cover_images where id = 'd2412baf-2c04-48a5-b7fa-b9a7ae6dbafa';"
kubectl -n ircs-dev exec deploy/ircs-storage-service -- sh -c "test ! -f '/app/storage/covers/3220fc7838ccdab0a3a21b5d8498275c7abe6930abbb059e9fdb4939bba8c2fd.png' && echo LOCAL_FILE_ABSENT"
kubectl -n ircs-dev get httproute --ignore-not-found
kubectl -n ircs-dev get deployment ircs-storage-service -o json
kubectl -n ircs-dev get configmap ircs-dev-app-config -o jsonpath="{.data.R2_BUCKET_NAME}{'\n'}{.data.R2_PUBLIC_DOMAIN}{'\n'}"
Get-NetTCPConnection -LocalPort 18090,18093 -ErrorAction SilentlyContinue
kubectl -n ircs-dev get resourcequota ircs-dev-quota
```

结果：

```text
temporary secret ircs-r2-smoke-02807: absent
cover_images fixture residue: 0
local file: LOCAL_FILE_ABSENT
HTTPRoute: absent
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
port-forward ports 18090/18093: no owning process; 18090 only had TIME_WAIT owningProcess=0
ResourceQuota: pods=28/30, services=24/26
```

## 残留状态

无已知残留。

- R2 object：脚本成功路径已确认 DELETE 后 HEAD notFound。
- DB row：`cover_images.id=d2412baf-2c04-48a5-b7fa-b9a7ae6dbafa` 残留为 0。
- 本地文件：`/app/storage/covers/3220fc7838ccdab0a3a21b5d8498275c7abe6930abbb059e9fdb4939bba8c2fd.png` 不存在。
- 临时 Secret：`ircs-r2-smoke-02807` 不存在。
- Deployment patch：storage-service 已恢复到 R2 disabled 与 ConfigMap valueFrom。
- dev bucket：`ircs-dev-app-config.R2_BUCKET_NAME` 仍为 `ircs-dev`。
- HTTPRoute：无残留。
- port-forward：无存活 owning process。

## 剩余 R2 风险

- 本轮只覆盖 cover path，不覆盖 avatar path；avatar 已有上游 `084` smoke，但本任务未重复执行。
- 本轮只覆盖一个最小 PNG fixture，不覆盖批量 cover download 或真实外部图片下载后的 R2 upload。
- 本轮不覆盖 watchdog 长周期恢复、batch size、失败重试退避在真实样本下的长时间行为。
- 常驻 dev bucket 仍保持 `ircs-dev`；V1 bucket `ircs` 只能继续通过显式临时 override 参与 parity smoke。
