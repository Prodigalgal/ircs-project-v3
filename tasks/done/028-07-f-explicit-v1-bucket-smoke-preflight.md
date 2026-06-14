# 028-07-F explicit V1 bucket smoke preflight task spec

状态：已完成

父任务：

- `tasks/done/028-07-r2-live-watchdog-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `tasks/done/028-07-e-dev-r2-bucket-policy-guard.md`
- `tasks/done/028-07-r2-live-watchdog-parity.md`

## 背景

`028-07-E` 已明确 dev 常驻 bucket 保持 `ircs-dev`，V1 bucket `ircs` 只能用于显式临时覆盖。
现有 credential-only、cover path 和 avatar path R2 smoke 都已经要求确认开关后才写入，但下一步要推进
explicit V1 bucket smoke 前，需要先增加更清晰的预检/只读防护，避免误把真实 V1 bucket 写入当成默认动作。

## 目标

- 为现有 R2 smoke 增加 `-PreflightOnly` 与 `-NoWrite` 防护。
- 让默认行为保持只读，不写 R2 对象、不 patch storage-service Deployment。
- 显式 V1 bucket smoke 必须通过 `-UseV1ParityBucketOverride` 或 `-BucketName ircs` 声明 bucket 来源。
- 输出中明确记录 `willWriteR2Objects`、`willPatchDeployment`、`bucketSource` 和确认开关状态。
- 本轮只执行 parser check 与 no-write/preflight 命令，不执行真实写入。

## 非目标

- 不执行 `-ConfirmLiveR2Write`、`-ConfirmStorageServiceR2Write` 或 `-ConfirmAvatarR2Write` 的真实写入路径。
- 不修改 `deploy/**` 常驻 manifest。
- 不修改 `services/**` 业务代码、BFF、normalization 或 SMTP。
- 不更新 `docs/index.md`、`tasks/index.md`、`docs/reports/089`、`docs/migrations/089`。

## 输入

```text
scripts/smoke/storage-r2-live-object-02807-smoke.ps1
scripts/smoke/storage-service-r2-path-02807-smoke.ps1
scripts/smoke/storage-avatar-r2-live-02807-smoke.ps1
tasks/done/028-07-e-dev-r2-bucket-policy-guard.md
tasks/done/028-07-r2-live-watchdog-parity.md
```

## 输出

```text
scripts/smoke/storage-r2-live-object-02807-smoke.ps1
scripts/smoke/storage-service-r2-path-02807-smoke.ps1
scripts/smoke/storage-avatar-r2-live-02807-smoke.ps1
tasks/done/028-07-f-explicit-v1-bucket-smoke-preflight.md
```

## 影响范围

```text
scripts/smoke/storage*.ps1
tasks/in-progress/028-07-f-explicit-v1-bucket-smoke-preflight.md
```

不包含：

```text
services/**
deploy/**
docs/index.md
tasks/index.md
docs/reports/089*
docs/migrations/089*
```

## 依赖

- dev ConfigMap 继续提供 `R2_BUCKET_NAME=ircs-dev` 与 `R2_PUBLIC_DOMAIN`。
- V1 bucket parity 继续通过显式临时覆盖使用 `ircs`。
- 真实 cover/avatar V1 bucket write smoke 需要人工确认后另行执行。

## 风险

- `-PreflightOnly` 不访问 Kubernetes、credential-service 或 queue 状态；`-NoWrite` 防护已实现，
  但本轮收缩后不继续执行 live 只读探测。
- 如果调用方同时传入确认写入开关与 `-NoWrite` / `-PreflightOnly`，脚本必须以 no-write 为准。
- 本轮不证明 V1 bucket 的真实 cover/avatar 写入链路当前仍可用，只证明写入前安全边界可复现。

## 验收标准

```text
PowerShell parser check passes for changed storage smoke scripts.
No-write/preflight command succeeds and reports willWriteR2Objects=false.
Explicit V1 bucket source is visible in output.
No K8S manifest is modified.
```

## 测试边界

```powershell
powershell -NoProfile -Command "PowerShell parser check for changed storage smoke scripts"
.\scripts\smoke\storage-r2-live-object-02807-smoke.ps1 -UseV1ParityBucketOverride -PreflightOnly
.\scripts\smoke\storage-service-r2-path-02807-smoke.ps1 -UseV1ParityBucketOverride -PreflightOnly
.\scripts\smoke\storage-avatar-r2-live-02807-smoke.ps1 -UseV1ParityBucketOverride -PreflightOnly
```

不执行真实 R2 写入、不 patch Deployment、不创建临时 Secret。

## 实施结果

- `storage-r2-live-object-02807-smoke.ps1` 新增 `-PreflightOnly` / `-NoWrite`。
  - `-PreflightOnly` 要求 `-UseV1ParityBucketOverride` 或显式 `-BucketName`，然后直接返回
    `R2_LIVE_OBJECT_PREFLIGHT_OK`。
  - `-NoWrite` 在 credential/config 只读解析后返回 `R2_LIVE_OBJECT_NO_WRITE_OK`，
    不进入 PUT/HEAD/DELETE 写入段。
- `storage-service-r2-path-02807-smoke.ps1` 新增 `-PreflightOnly` / `-NoWrite`。
  - `-PreflightOnly` 不创建 Secret、不 patch Deployment、不 port-forward storage-service。
  - `-NoWrite` 在 credential/config 只读解析后返回
    `STORAGE_SERVICE_R2_PATH_NO_WRITE_OK`，不创建 Secret、不 patch Deployment、不上传 cover。
- `storage-avatar-r2-live-02807-smoke.ps1` 新增 `-PreflightOnly` / `-NoWrite`。
  - `-PreflightOnly` 不访问 portal、不创建用户、不上传 avatar。
  - `-NoWrite` 已避免 portal port-forward，并在写入链路前返回，不创建 Secret、不 patch Deployment。
- 三个脚本输出均包含：
  - `bucketSource`
  - `willWriteR2Objects`
  - `willPatchDeployment`
  - 对应 `confirm...Write` 状态
  - `confirmIgnoredByNoWrite`
- confirm 写入前新增显式 bucket override gate：未传 `-UseV1ParityBucketOverride` 或 `-BucketName`
  时不会进入真实 R2 写入路径。

## 验收结果

PowerShell parser check：

```text
PARSER_OK scripts/smoke/storage-r2-live-object-02807-smoke.ps1
PARSER_OK scripts/smoke/storage-service-r2-path-02807-smoke.ps1
PARSER_OK scripts/smoke/storage-avatar-r2-live-02807-smoke.ps1
PARSER_OK scripts/smoke/storage-dev-r2-bucket-policy-02807-guard.ps1
```

`-PreflightOnly`：

```powershell
.\scripts\smoke\storage-r2-live-object-02807-smoke.ps1 -UseV1ParityBucketOverride -PreflightOnly
.\scripts\smoke\storage-service-r2-path-02807-smoke.ps1 -UseV1ParityBucketOverride -PreflightOnly
.\scripts\smoke\storage-avatar-r2-live-02807-smoke.ps1 -UseV1ParityBucketOverride -PreflightOnly
```

结果：

```text
SUCCESS/R2_LIVE_OBJECT_PREFLIGHT_OK bucket=ircs bucketSource=UseV1ParityBucketOverride willWriteR2Objects=false willPatchDeployment=false
SUCCESS/STORAGE_SERVICE_R2_PATH_PREFLIGHT_OK effectiveBucket=ircs bucketSource=UseV1ParityBucketOverride willWriteR2Objects=false willPatchDeployment=false
SUCCESS/AVATAR_R2_PREFLIGHT_OK effectiveBucket=ircs bucketSource=UseV1ParityBucketOverride willWriteR2Objects=false willPatchDeployment=false
```

## 未执行项

- 未执行 `-ConfirmLiveR2Write`。
- 未执行 `-ConfirmStorageServiceR2Write`。
- 未执行 `-ConfirmAvatarR2Write`。
- 未执行真实 R2 PUT/DELETE。
- 未创建 storage-service 临时 Secret。
- 未 patch storage-service Deployment。
- 未修改任何 K8S manifest。

## 剩余 live 风险

- 真正 cover/avatar V1 bucket write smoke 仍待人工确认后执行。
- 本轮未证明 V1 bucket 当前仍可接收 cover/avatar 真实对象写入。
- watchdog 长周期恢复策略、batch size 与真实样本恢复效果后续已由 `028-07-J` 覆盖。

## 当前状态

已完成预检/只读防护、parser check 和 task spec 回写。真实写入 smoke 留待人工确认。
