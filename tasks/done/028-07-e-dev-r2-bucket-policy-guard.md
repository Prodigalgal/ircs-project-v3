# 028-07-E dev R2 bucket policy guard task spec

状态：已完成

父任务：

- `tasks/in-progress/028-07-r2-live-watchdog-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/079-02807-r2-live-object-smoke.md`
- `docs/reports/083-02807-storage-service-r2-path-smoke.md`
- `docs/reports/084-02807-avatar-r2-live-smoke.md`
- `docs/reports/089-v1-golden-behavior-inventory.md`

## 背景

`028-07-B/C/D` 已证明导入的 V1 R2 credential 可写入 V1 bucket `ircs`，且
storage-service cover path 与 avatar end-to-end path 都可通过临时 runtime override 完成
live parity。与此同时，`ircs-dev-app-config.R2_BUCKET_NAME` 仍是 `ircs-dev`，与 V1
bucket `ircs` 存在漂移。

本刀不把 dev 常驻 ConfigMap 切到 V1 production bucket。需要把策略写清楚，并补一个可复现
guard，避免后续误把 `ircs` 当成 `ircs-dev` 的常驻配置。

## 目标

- 明确 dev bucket policy：`ircs-dev-app-config.R2_BUCKET_NAME` 常驻保持 `ircs-dev`。
- 明确 V1 bucket parity smoke 只能通过显式临时覆盖或 credential-only path 使用 `ircs`。
- 调整现有 live smoke，移除默认隐式 V1 bucket override。
- 新增只读 guard，检查 dev ConfigMap 与 storage-service manifest 未把 `ircs` 作为常驻 bucket。
- 保持低资源、无新增 HTTPRoute、无 namespace 污染。

## 非目标

- 不永久启用 storage-service R2 runtime。
- 不把 `ircs-dev-app-config.R2_BUCKET_NAME` 改成 `ircs`。
- 不执行真实 R2 写入或重复 live smoke。
- 不修改全局索引、V1 golden behavior 汇总、normalization-worker、content-service C2、
  shared outbound C3、notification/ops SMTP。

## 输入

```text
deploy/k8s/dev/notification-worker-dev.yaml
deploy/k8s/dev/storage-service-dev.yaml
scripts/smoke/storage-r2-live-object-02807-smoke.ps1
scripts/smoke/storage-service-r2-path-02807-smoke.ps1
scripts/smoke/storage-avatar-r2-live-02807-smoke.ps1
tasks/in-progress/028-07-r2-live-watchdog-parity.md
```

## 输出

```text
scripts/smoke/storage-dev-r2-bucket-policy-02807-guard.ps1
smoke script guard changes
task spec and parent task status update
```

## 影响范围

```text
scripts/smoke/storage*.ps1
tasks/in-progress/028-07-e-dev-r2-bucket-policy-guard.md
tasks/in-progress/028-07-r2-live-watchdog-parity.md
```

## 依赖

- 当前 dev manifest 中 `ircs-dev-app-config` 由 `deploy/k8s/dev/notification-worker-dev.yaml`
  定义。
- storage-service dev manifest 继续通过 ConfigMap `valueFrom` 读取 R2 bucket/public domain。
- 若后续需要 V1 bucket live parity，调用方必须显式传入 `-UseV1ParityBucketOverride`
  或 `-BucketName ircs`。

## 风险

- 改变 smoke 默认 bucket 解析后，旧命令若直接加 confirm 但不声明 override，会按预期 skip，
  不再隐式写入 V1 bucket。
- 本刀只做 guard 与文档决策，不证明长期 watchdog 在真实 R2 上的恢复效果。
- 若未来真的创建 dev bucket `ircs-dev`，可通过显式 `-BucketName ircs-dev` 做独立 smoke。

## 验收标准

```text
MARK 1/4 dev app ConfigMap manifest keeps R2_BUCKET_NAME away from V1 production bucket
MARK 2/4 storage-service dev manifest keeps R2 disabled and bucket bound by ConfigMap valueFrom
MARK 3/4 storage live smoke requires explicit V1 parity bucket override before temporary R2 writes
MARK 4/4 local parser/guard verification passes without writing R2 objects
```

## 测试边界

```powershell
.\scripts\smoke\storage-dev-r2-bucket-policy-02807-guard.ps1
powershell -NoProfile -Command "PowerShell parser check for changed storage smoke scripts"
```

不执行 `-ConfirmLiveR2Write`、`-ConfirmStorageServiceR2Write` 或 `-ConfirmAvatarR2Write`。

## 实施结果

- 新增 `scripts/smoke/storage-dev-r2-bucket-policy-02807-guard.ps1`。
- guard 只读检查 dev app ConfigMap manifest 中 `R2_BUCKET_NAME=ircs-dev`，并阻止把 V1
  production bucket `ircs` 作为 dev 常驻配置。
- guard 只读检查 storage-service dev manifest：
  - `APP_STORAGE_R2_ENABLED=false`。
  - `APP_STORAGE_R2_BUCKET_NAME` 继续通过 `ircs-dev-app-config.R2_BUCKET_NAME` valueFrom 注入。
  - `APP_STORAGE_R2_PUBLIC_DOMAIN` 继续通过 `ircs-dev-app-config.R2_PUBLIC_DOMAIN` valueFrom 注入。
  - 常驻 manifest 不包含 R2 account/access/secret/endpoint env。
- `storage-r2-live-object-02807-smoke.ps1`、`storage-service-r2-path-02807-smoke.ps1` 和
  `storage-avatar-r2-live-02807-smoke.ps1` 已新增 `-UseV1ParityBucketOverride`。
- storage-service path 与 avatar live smoke 不再默认隐式使用 V1 bucket `ircs`；不传
  `-BucketName` 且不传 `-UseV1ParityBucketOverride` 时使用 dev ConfigMap bucket。
- 若 dev 常驻 ConfigMap 未来被误设为 `ircs`，confirm 写入路径会返回
  `SKIPPED/PRODUCTION_BUCKET_AS_DEV_CONFIG_BLOCKED`，提示只能显式临时覆盖。

## 验收结果

```text
MARK 1/4 dev app ConfigMap manifest keeps R2_BUCKET_NAME away from V1 production bucket
MARK 2/4 storage-service dev manifest keeps R2 disabled and bucket bound by ConfigMap valueFrom
MARK 3/4 storage live smoke requires explicit V1 parity bucket override before temporary R2 writes
MARK 4/4 local parser/guard verification passes without writing R2 objects
```

## 验证记录

PowerShell parser check：

```text
PARSER_OK scripts/smoke/storage-r2-live-object-02807-smoke.ps1
PARSER_OK scripts/smoke/storage-service-r2-path-02807-smoke.ps1
PARSER_OK scripts/smoke/storage-avatar-r2-live-02807-smoke.ps1
PARSER_OK scripts/smoke/storage-dev-r2-bucket-policy-02807-guard.ps1
```

只读 guard：

```powershell
.\scripts\smoke\storage-dev-r2-bucket-policy-02807-guard.ps1
```

结果：

```text
SUCCESS/DEV_R2_BUCKET_POLICY_GUARD_OK
devBucket=ircs-dev
v1ProductionBucket=ircs
storageR2Enabled=false
writesR2Objects=false
```

K8S server dry-run：

```powershell
kubectl apply --dry-run=server -f deploy/k8s/dev/storage-service-dev.yaml
```

结果：

```text
deployment.apps/ircs-storage-service configured (server dry run)
service/ircs-storage-service unchanged (server dry run)
```

## 剩余边界

- 本刀未重复执行真实 R2 live smoke，也未写任何 R2 对象。
- cover/avatar live parity 仍依赖显式临时覆盖到 V1 bucket `ircs` 的 smoke 证据。
- watchdog 长周期恢复策略、batch size 与真实样本下的恢复效果仍未完全闭环。
