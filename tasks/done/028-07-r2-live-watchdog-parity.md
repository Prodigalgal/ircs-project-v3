# 028-07 R2 live and watchdog parity 后续子任务

状态：已完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/041-v1-v3-behavior-parity-gap-audit.md`
- `docs/reports/047-storage-avatar-sync-consumer-parity-analysis.md`
- `docs/reports/053-storage-image-download-parity-analysis.md`

## 背景

V3 已完成 avatar 本地落盘、`/media/avatars/*` 服务、`SYNC_AVATAR` dev-safe consumer、
R2 active unit path，以及 `DOWNLOAD_IMAGE` 本地下载和 live smoke。真实 R2 live、
cover image R2 pending upload、`AvatarSyncScheduler` watchdog 和 image upload watchdog
仍为后置能力。

## 目标

- 在 dev 明确配置 R2 credential 后验证 avatar R2 live 上传、URL 回写和本地清理。
- 为 cover image 下载后的 R2 pending upload 设计并实现最小 parity。
- 补 `AvatarSyncScheduler` / image upload watchdog 的恢复和重试语义。
- 确保 R2 disabled/inactive 时仍保持 ack/no-op 语义。

## 非目标

- 不要求生产 bucket。
- 不做大批量迁移或压测。
- 不把 R2 credential 写入仓库。

## 输入

- `q.storage.avatar_sync`
- `q.storage.image`
- storage local object
- R2 dev credential/config

## 输出

- R2 live smoke 脚本。
- watchdog 实现或 task spec。
- 迁移记录和任务状态回写。

## 影响范围

```text
services/ircs-storage-service/**
services/ircs-identity-service/**
scripts/smoke/**
docs/reports/**
docs/migrations/**
tasks/**
```

## 依赖

- dev R2 credential 可用。
- storage-service Deployment 低资源运行正常。

## 风险

- 外部对象存储写入需要严格清理。
- credential 缺失时必须明确 skip，不能产生假阳性。

## 验收标准

- avatar R2 live、cover R2 pending upload 和 watchdog 至少各有一个可复现 MARK。
- R2 对象、DB fixture、queue/DLQ 均能清理。
- `ircs-dev` 不新增公共 HTTPRoute。

## 测试边界

- 本地 mock R2 单元测试。
- dev live smoke 只使用临时对象 key。

## 当前状态

第一刀 `028-07-A storage R2 watchdog recovery parity` 已完成并部署到 `ircs-dev`。
2026-06-09 已完成 `028-07-E dev R2 bucket policy guard`：dev ConfigMap 不切到 V1
production bucket，V1 bucket parity 只允许显式临时覆盖，并已用只读 guard 固定。
2026-06-09 已完成 `028-07-J storage-service R2 watchdog 长周期/恢复验证`，`028-07`
父任务剩余 watchdog 长周期恢复边界已闭环。

## 子任务 028-07-A：storage R2 watchdog recovery parity

### 背景

`028-05-B` 已完成 `SYNC_AVATAR` consumer，`028-05-H` 已完成 `DOWNLOAD_IMAGE` 本地下载。
但如果上传后本地头像仍滞留，或 cover image 下载完成后因 R2 暂不可用而停留在
`LOCAL_STORED + next_retry_time due`，当前 V3 缺少 V1 风格 watchdog 来重新触发同步。

### 目标

- 在 R2 active 时扫描本地头像 URL，重投 `SYNC_AVATAR`。
- 在 R2 active 时扫描 due 的 `LOCAL_STORED` cover image，执行 R2 upload retry。
- R2 inactive 时 watchdog 只跳过，不写 DB、不发消息。
- cover R2 upload 失败时写回 retry/backoff，不让异常中断整批扫描。

### 非目标

- 不打开真实 R2 credential。
- 不执行真实 R2 live smoke。
- 不新增 HTTPRoute、Service、Pod 或中间件。
- 不实现 avatar R2 live smoke，也不覆盖真实 R2 对象清理。

### 输入

- `members.avatar_url`
- `cover_images.storage_type/status/next_retry_time/storage_path`
- `q.storage.avatar_sync`
- `R2ObjectStorage.isActive()`

### 输出

- storage watchdog 代码和本地测试。
- `docs/reports/057-storage-r2-watchdog-02807-analysis.md`
- `docs/migrations/058-storage-r2-watchdog-recovery.md`

### 影响范围

```text
services/ircs-storage-service/**
docs/reports/**
docs/migrations/**
tasks/done/028-07-r2-live-watchdog-parity.md
```

### 依赖

- storage-service 已启用 Spring scheduling。
- 现有 `StorageCommandPublisher`、`AvatarSyncMemberRepository` 和 `CoverImageAdminRepository`。

### 风险

- 扫描批量不能过大，默认小批量。
- R2 inactive 时必须 no-op，避免 dev 环境制造无意义队列消息。
- cover upload 失败不能清理本地文件，后续重试仍需要本地副本。

### 验收标准

```text
MARK 1/4 R2 inactive watchdog skips avatar and cover recovery
MARK 2/4 R2 active watchdog republishes local avatar members to q.storage.avatar_sync
MARK 3/4 R2 active watchdog uploads due LOCAL_STORED cover image to R2
MARK 4/4 R2 upload failure records retry/backoff without aborting watchdog batch
```

### 测试边界

```text
.\gradlew.bat :services:ircs-storage-service:test --rerun-tasks
```

### 当前状态

已完成第一刀代码、测试、ARM64 image 推送和 `ircs-dev` rollout。

### 实现结果

- 新增 `StorageR2Watchdog` 周期扫描：
  - R2 inactive 时 skip/no-op。
  - R2 active 时扫描本地头像 URL 并发布 `q.storage.avatar_sync`。
  - R2 active 时扫描 due `LOCAL_STORED` cover image 并调用 R2 upload retry。
- 新增 `CoverImageR2SyncService`：
  - 成功路径 finalize cover image 为远端存储状态。
  - 失败路径写回 retry/backoff，不中断 watchdog batch。
- 扩展 repository/publisher：
  - `AvatarSyncMemberRepository.findLocalAvatarMemberIds(...)`
  - `CoverImageAdminRepository.findDueLocalR2UploadIds(...)`
  - `StorageCommandPublisher.publishAvatarSync(...)`
- storage-service 配置增加 watchdog 开关、延迟和 batch size。

### 验证结果

本地测试：

```text
.\gradlew.bat :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL in 14s
```

MARK：

```text
MARK 1/4 R2 inactive watchdog skips avatar and cover recovery
MARK 2/4 R2 active watchdog republishes local avatar members to q.storage.avatar_sync
MARK 3/4 R2 active watchdog uploads due LOCAL_STORED cover image to R2
MARK 4/4 R2 upload failure records retry/backoff without aborting watchdog batch
```

ARM64 image 与 rollout：

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-storage-service' -ImageName ircs-storage-service -Tag dev -Architecture arm64 -Os linux
kubectl -n ircs-dev rollout restart deployment/ircs-storage-service
kubectl -n ircs-dev rollout status deployment/ircs-storage-service --timeout=240s
```

结果：

```text
deployment/ircs-storage-service successfully rolled out
pod: ircs-storage-service-d77d864c7-9tb78 Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-storage-service@sha256:1355d90561d00e412d333bcf95566987697cd93f4861f27d4bdd17a9a57b7172
APP_STORAGE_R2_ENABLED=false
ResourceQuota: pods=28/30, services=24/26
HTTPRoute in ircs-dev: No resources found
```

### 剩余项

- cover image 后续真实 R2 smoke 深化。
- dev bucket decision 已由 `028-07-E` 闭环：`ircs-dev-app-config.R2_BUCKET_NAME` 常驻保持
  `ircs-dev`，V1 bucket `ircs` 只允许显式临时覆盖。
- watchdog 长周期恢复策略和 batch size 仍需在更多真实样本下验证。

## 子任务 028-07-B：credential-only R2 live object smoke

### 背景

`028-14` 已将 V1 R2 credential 导入 V3 dev credential pool，当前 `sys_credentials` 中
`R2 total=1 enabled=1`。但 `ircs-storage-service` 运行态仍保持
`APP_STORAGE_R2_ENABLED=false`，Deployment 也未注入 `APP_STORAGE_R2_ACCESS_KEY`、
`APP_STORAGE_R2_SECRET_KEY` 或 `APP_STORAGE_R2_ACCOUNT_ID`。直接打开 storage-service R2
runtime 会同时引入 watchdog 扫描和业务对象副作用，不适合作为第一步 live 验证。

### 目标

- 新增 credential-only R2 live object smoke。
- 从 credential-service R2 lease 获取 payload key，不打印 secret。
- 从 `ircs-dev-app-config` 读取 bucket/public domain。
- 默认只做 diagnostic；显式确认后只写入 `codex-smoke/02807/**` 临时对象。
- 验证 put/head/delete/head-missing，并确保 cleanup。

### 非目标

- 不修改 storage-service Deployment。
- 不打开 `APP_STORAGE_R2_ENABLED=true`。
- 不触发 `/api/v1/cover-images/{id}/sync-r2`。
- 不触发 R2 watchdog。
- 不处理真实 avatar/cover 业务对象。

### 上游来源

- `docs/reports/079-02807-r2-live-object-smoke.md`
- `docs/migrations/079-r2-live-object-smoke.md`
- `docs/reports/078-02814-v1-credential-import-for-live-parity.md`

### 输出

```text
scripts/smoke/storage-r2-live-object-02807-smoke.ps1
docs/reports/079-02807-r2-live-object-smoke.md
docs/migrations/079-r2-live-object-smoke.md
```

### 验收标准

```text
MARK 1/5 R2 credential lease has required payload keys without printing secrets
MARK 2/5 R2 bucket and temporary object key are resolved
MARK 3/5 R2 putObject succeeds for temporary smoke object
MARK 4/5 R2 headObject confirms object exists
MARK 5/5 R2 deleteObject succeeds and headObject confirms cleanup
```

### 测试边界

```powershell
.\scripts\smoke\storage-r2-live-object-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken
.\scripts\smoke\storage-r2-live-object-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -ConfirmLiveR2Write
```

### 当前状态

完成第一刀。credential-only R2 live object smoke 已实现并验证，发现 V3 dev bucket 配置与 V1 漂移。

### 实现结果

- 新增 `scripts/smoke/storage-r2-live-object-02807-smoke.ps1`。
- 默认 dry-run/diagnostic，不执行 R2 写入。
- 显式 `-ConfirmLiveR2Write` 才执行 put/head/delete/head-missing。
- 新增 `-BucketName` / `-PublicDomain` 覆盖参数，用于区分 dev ConfigMap 漂移和 credential 失效。

### 验证结果

```text
.\scripts\smoke\storage-r2-live-object-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken
MARK 1/5 R2 credential lease has required payload keys without printing secrets
MARK 2/5 R2 bucket and temporary object key are resolved
SKIPPED/LIVE_R2_WRITE_NOT_CONFIRMED
```

```text
.\scripts\smoke\storage-r2-live-object-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -ConfirmLiveR2Write
MARK 1/5 R2 credential lease has required payload keys without printing secrets
MARK 2/5 R2 bucket and temporary object key are resolved
FAILED/R2_PUT_FAILED statusCode=404 bucket=ircs-dev
```

```text
.\scripts\smoke\storage-r2-live-object-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -ConfirmLiveR2Write -BucketName ircs
MARK 1/5 R2 credential lease has required payload keys without printing secrets
MARK 2/5 R2 bucket and temporary object key are resolved
MARK 3/5 R2 putObject succeeds for temporary smoke object
MARK 4/5 R2 headObject confirms object exists
MARK 5/5 R2 deleteObject succeeds and headObject confirms cleanup
```

### 结论

```text
R2 credential 可用。
V1 R2_BUCKET_NAME=ircs。
V3 ircs-dev-app-config 当前 R2_BUCKET_NAME=ircs-dev，导致 live PUT 返回 404。
storage-service 仍保持 APP_STORAGE_R2_ENABLED=false，且未注入 R2 access/secret/account env。
```

### 剩余项

- dev bucket decision 后续由 `028-07-E` 闭环：不把常驻 dev ConfigMap 改成 V1 bucket
  `ircs`，live parity 继续使用显式临时覆盖。
- storage-service path smoke 需要先设计 R2 credential-service lease adapter 或临时 Secret env。
- 若临时打开 storage-service R2 runtime，必须同时禁用 watchdog，避免扫描非本轮 fixture。

## 子任务 028-07-C：storage-service R2 path smoke

### 当前状态

已完成并移动到 `tasks/done/028-07-c-storage-service-r2-path-smoke.md`。

### 实现结果

- 新增 `scripts/smoke/storage-service-r2-path-02807-smoke.ps1`。
- smoke 默认 diagnostic，不带 `-ConfirmStorageServiceR2Write` 时不修改 Deployment。
- live smoke 通过 credential-service 租用 R2 credential，临时注入 storage-service R2 env，
  显式覆盖 bucket 为 V1 `ircs`，并设置 `APP_STORAGE_R2_WATCHDOG_ENABLED=false`。
- 已验证 storage-service cover upload + `sync-r2` path 能写入 R2、更新 DB URL，并完成
  R2 object、local file、DB row、Secret 和 Deployment env cleanup。

### 验收结果

```text
MARK 1/6 storage-service R2 path smoke resolves V1 bucket or explicit override：通过
MARK 2/6 storage-service temporary R2 runtime is enabled without watchdog scan：通过
MARK 3/6 fixture object is uploaded through storage-service path：通过
MARK 4/6 DB/media URL side effect matches V1 public domain semantics：通过
MARK 5/6 R2 object and DB fixture are cleaned：通过
MARK 6/6 dev env restored with no HTTPRoute/quota/port-forward residue：通过
```

### 验证记录

```powershell
.\scripts\smoke\storage-service-r2-path-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -ConfirmStorageServiceR2Write
```

结果：`SUCCESS/STORAGE_SERVICE_R2_PATH_SMOKE_OK`。

后置状态：

```text
APP_STORAGE_R2_ENABLED=false
APP_STORAGE_R2_BUCKET_NAME valueFrom=ircs-dev-app-config.R2_BUCKET_NAME
APP_STORAGE_R2_PUBLIC_DOMAIN valueFrom=ircs-dev-app-config.R2_PUBLIC_DOMAIN
temporary secret absent
cover_images fixture residue=0
HTTPRoute absent
ResourceQuota unchanged: pods=28/30, services=24/26
```

### 剩余项

- dev bucket decision 已由 `028-07-E` 闭环：常驻 `ircs-dev-app-config.R2_BUCKET_NAME` 保持
  `ircs-dev`，不切到 V1 bucket `ircs`。

## 子任务 028-07-D：avatar R2 live smoke

状态：已完成

关联任务：`tasks/done/028-07-d-avatar-r2-live-smoke.md`

目标：验证 identity avatar upload -> `q.storage.avatar_sync` -> storage-service R2 upload ->
`members.avatar_url` R2 URL 回写 -> local avatar cleanup 的端到端 live path。

### 实现结果

- 新增 `scripts/smoke/storage-avatar-r2-live-02807-smoke.ps1`。
- smoke 默认 diagnostic，不带 `-ConfirmAvatarR2Write` 时不执行 R2 写入。
- live smoke 通过 credential-service 租用 R2 credential，临时启用 storage-service R2
  runtime，显式使用 V1 bucket `ircs`，并禁用 watchdog 扫描。
- 已验证 portal 注册/登录/上传头像、`q.storage.avatar_sync` 消费、R2 URL 回写、
  本地头像清理、R2 object cleanup、member cleanup、临时 Secret cleanup 和 Deployment
  env 恢复。

### 验收结果

```text
MARK 1/7 avatar R2 live smoke resolves credential lease without printing secrets：通过
MARK 2/7 storage-service temporary R2 runtime is enabled without watchdog scan：通过
MARK 3/7 portal member registers and uploads avatar through identity-service：通过
MARK 4/7 q.storage.avatar_sync is consumed by storage-service：通过
MARK 5/7 members.avatar_url is rewritten to V1 public R2 domain：通过
MARK 6/7 R2 object exists and local avatar file is cleaned：通过
MARK 7/7 smoke cleanup restores dev env and leaves no DB/queue/R2 residue：通过
```

### 验证记录

```powershell
.\scripts\smoke\storage-avatar-r2-live-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -ConfirmAvatarR2Write
```

结果：`SUCCESS/AVATAR_R2_LIVE_SMOKE_OK`。

## 子任务 028-07-E：dev R2 bucket policy guard

状态：已完成

关联任务：`tasks/done/028-07-e-dev-r2-bucket-policy-guard.md`

### 决策结论

- `ircs-dev-app-config.R2_BUCKET_NAME` 常驻保持 `ircs-dev`，不直接切换成 V1 production bucket
  `ircs`。
- V1 bucket `ircs` 只用于 credential-only 或 storage/avatar parity smoke 的显式临时覆盖；
  smoke 完成后必须恢复 storage-service 为 `APP_STORAGE_R2_ENABLED=false` 和 ConfigMap
  `valueFrom`。
- 现有 storage/avatar live smoke 需要去掉隐式默认 `ircs`，后续必须通过
  `-UseV1ParityBucketOverride` 或 `-BucketName ircs` 明确声明。

### 验收边界

```text
MARK 1/4 dev app ConfigMap manifest keeps R2_BUCKET_NAME away from V1 production bucket
MARK 2/4 storage-service dev manifest keeps R2 disabled and bucket bound by ConfigMap valueFrom
MARK 3/4 storage live smoke requires explicit V1 parity bucket override before temporary R2 writes
MARK 4/4 local parser/guard verification passes without writing R2 objects
```

### 实施结果

- 新增 `scripts/smoke/storage-dev-r2-bucket-policy-02807-guard.ps1`。
- `storage-r2-live-object-02807-smoke.ps1`、`storage-service-r2-path-02807-smoke.ps1` 和
  `storage-avatar-r2-live-02807-smoke.ps1` 新增 `-UseV1ParityBucketOverride`。
- storage-service path/avatar live smoke 不再隐式默认 V1 bucket `ircs`；默认读取
  `ircs-dev-app-config.R2_BUCKET_NAME`，需要 V1 parity 时必须显式声明
  `-UseV1ParityBucketOverride` 或 `-BucketName ircs`。
- 若 dev 常驻 ConfigMap 未来误设为 `ircs`，confirm 写入路径会返回
  `SKIPPED/PRODUCTION_BUCKET_AS_DEV_CONFIG_BLOCKED`。

### 验证结果

```text
PowerShell parser check: 4/4 scripts PARSER_OK
.\scripts\smoke\storage-dev-r2-bucket-policy-02807-guard.ps1
SUCCESS/DEV_R2_BUCKET_POLICY_GUARD_OK
kubectl apply --dry-run=server -f deploy/k8s/dev/storage-service-dev.yaml
deployment.apps/ircs-storage-service configured (server dry run)
service/ircs-storage-service unchanged (server dry run)
```

本刀未执行 `-ConfirmLiveR2Write`、`-ConfirmStorageServiceR2Write` 或
`-ConfirmAvatarR2Write`，未写入任何 R2 对象。

## 子任务 028-07-I：storage-service R2 avatar path 真实链路 smoke

状态：已完成

关联任务：`tasks/done/028-07-i-storage-service-r2-avatar-path-smoke.md`

关联报告：`docs/reports/090-02807-storage-service-r2-avatar-path-smoke.md`

目标：在 `028-07-G` 单对象 R2 write/delete 与 `028-07-H` cover path 真实链路之后，
用显式 V1 parity bucket override 复验 avatar path，确认 `q.storage.avatar_sync` 到
storage-service R2 upload、`members.avatar_url` 回写、local cleanup、R2 cleanup 和 dev
环境恢复均有可审计 evidence。

### 验收边界

```text
MARK 1/7 avatar R2 credential/config resolves V1 bucket override
MARK 2/7 storage-service temporary R2 runtime is enabled without watchdog scan
MARK 3/7 portal register/login fixture is active for avatar upload
MARK 4/7 portal/identity avatar upload returns local media URL and publishes SYNC_AVATAR
MARK 5/7 q.storage.avatar_sync drains and members.avatar_url becomes R2 public URL
MARK 6/7 R2 object exists and local avatar file is cleaned
MARK 7/7 R2 object/member/queue cleanup and dev env restore complete
```

### 测试边界

```powershell
.\scripts\smoke\storage-avatar-r2-live-02807-smoke.ps1 -TaskId 028-07-I -StartCredentialPortForward -UseKubernetesSecretToken -UseV1ParityBucketOverride -ConfirmAvatarR2Write
```

### 实施结果

- 已补强 `storage-avatar-r2-live-02807-smoke.ps1` 成功 JSON 的 cleanup evidence 输出。
- 已执行显式 V1 bucket avatar path live smoke，结果 `SUCCESS/AVATAR_R2_LIVE_SMOKE_OK`。
- 已确认 `effectiveBucket=ircs`、`configMapBucket=ircs-dev`、
  `r2Url=https://img.mnnu.eu.org/avatars/...`。
- 已确认 R2 DELETE `204` 后 HEAD `404/notFound`、member residual=0、本地 avatar
  residual=0、queue/DLQ 无新增残留。
- 已确认临时 Secret 不存在、Deployment 恢复 R2 disabled 与 ConfigMap valueFrom、无 HTTPRoute。

### 验收结果

```text
MARK 1/7 avatar R2 credential/config resolves V1 bucket override：通过
MARK 2/7 storage-service temporary R2 runtime is enabled without watchdog scan：通过
MARK 3/7 portal register/login fixture is active for avatar upload：通过
MARK 4/7 portal/identity avatar upload returns local media URL and publishes SYNC_AVATAR：通过
MARK 5/7 q.storage.avatar_sync drains and members.avatar_url becomes R2 public URL：通过
MARK 6/7 R2 object exists and local avatar file is cleaned：通过
MARK 7/7 R2 object/member/queue cleanup and dev env restore complete：通过
```

## 子任务 028-07-J：storage-service R2 watchdog 长周期/恢复验证

状态：已完成

关联任务：`tasks/done/028-07-j-storage-r2-watchdog-recovery-smoke.md`

关联报告：`docs/reports/091-02807-storage-r2-watchdog-long-run-recovery-smoke.md`

目标：在 `028-07-G/H/I` 分别完成单对象、cover path、avatar path 真实链路之后，使用短周期
临时 env 验证 storage-service R2 watchdog 在 R2/env 恢复后能处理 due cover backlog 与
local avatar backlog，并记录 retry/ack/DLQ/cleanup 证据。

### 实施结果

- 新增 `scripts/smoke/storage-r2-watchdog-02807j-smoke.ps1`。
- 真实 smoke 显式使用 `-UseV1ParityBucketOverride -ConfirmWatchdogR2Write`，effective bucket
  为 V1 bucket `ircs`，常驻 ConfigMap bucket 仍为 `ircs-dev`。
- 通过 `LOCAL_STORED + next_retry_time due` 构造 cover backlog。
- 通过临时 member 本地 avatar URL 构造 avatar backlog，由 watchdog 重投
  `q.storage.avatar_sync` 并由 consumer 上传 R2、回写 URL。
- smoke 将 watchdog interval 缩短为 initial delay 30s、fixed delay 5s、batch size 1。
- 第一次真实 smoke 捕获 dev Pod 重建导致本地临时文件丢失，cover 进入
  `LOCAL|FAILED|Local file missing for R2 upload`；脚本已补 dev-only fixture rehydrate，
  避免把 dev 临时存储差异误判为 watchdog 业务失败。
- 未修改 storage-service 业务代码。

### 验收边界

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

### 验证记录

```powershell
.\scripts\smoke\storage-r2-watchdog-02807j-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -UseV1ParityBucketOverride -ConfirmWatchdogR2Write
```

结果：`SUCCESS/STORAGE_R2_WATCHDOG_RECOVERY_SMOKE_OK`。

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
q.storage.avatar_sync messages=0 ready=0 unacked=0 consumers=1
q.storage.avatar_sync.dlq messages=0 ready=0 unacked=0 consumers=0
```

### 后置状态

```text
temporary secret absent
APP_STORAGE_R2_ENABLED=false
APP_STORAGE_R2_BUCKET_NAME valueFrom=ircs-dev-app-config/R2_BUCKET_NAME
APP_STORAGE_R2_PUBLIC_DOMAIN valueFrom=ircs-dev-app-config/R2_PUBLIC_DOMAIN
APP_STORAGE_R2_* credential env absent
APP_STORAGE_R2_WATCHDOG_* temporary env absent
cover fixture residue=0
member fixture residue=0
local cover/avatar files absent
HTTPRoute absent
ResourceQuota pods=28/30, services=24/26
port-forward ports 18090/18093 no listener
```

## 父任务完成结论

`028-07` 已完成：

- R2 credential-only object smoke：已完成。
- storage-service cover path R2 smoke：已完成。
- avatar path R2 smoke：已完成。
- dev bucket policy guard：已完成。
- explicit V1 bucket preflight/no-write guard：已完成。
- real R2 explicit V1 bucket PUT/HEAD/DELETE：已完成。
- cover/avatar explicit V1 bucket chain smoke：已完成。
- storage-service R2 watchdog 长周期/恢复验证：已完成。
