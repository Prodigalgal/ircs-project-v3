# 028-07-G real R2 explicit V1 bucket write smoke task spec

状态：已完成

父任务：

- `tasks/in-progress/028-07-r2-live-watchdog-parity.md`

上游来源：

- `tasks/in-progress/028-07-f-explicit-v1-bucket-smoke-preflight.md`
- `tasks/done/028-07-e-dev-r2-bucket-policy-guard.md`
- `tasks/in-progress/028-07-r2-live-watchdog-parity.md`

## 背景

`028-07-E` 已固定 dev 常驻 bucket policy：`ircs-dev-app-config.R2_BUCKET_NAME`
保持 `ircs-dev`，V1 bucket `ircs` 只能通过显式临时覆盖参与 parity smoke。
`028-07-F` 已完成 `-PreflightOnly` / `-NoWrite` 防护，本轮用户已允许真实测试，
需要执行一条最小的真实 R2 write/read/delete smoke，验证显式 V1 bucket 路径仍可写且能清理。

## 目标

- 使用已有 `scripts/smoke/storage-r2-live-object-02807-smoke.ps1`。
- 显式传入 `-UseV1ParityBucketOverride` 或 `-BucketName ircs`。
- 只创建一个 `codex-smoke/02807/...` 临时对象。
- 记录 bucket、object key、R2 PUT/HEAD/DELETE/HEAD 404 结果。
- 结束后确认对象已删除，不留下 R2 残留。
- 不 patch storage-service Deployment，不修改常驻 dev ConfigMap bucket。

## 非目标

- 不执行 cover image 批量上传。
- 不执行 avatar end-to-end 上传。
- 不修改 `services/**` 业务代码。
- 不修改 `deploy/**` 常驻 manifest。
- 不修改 provider、SMTP、RabbitMQ 或其他配置。
- 不更新 `docs/index.md`、`tasks/index.md`、`docs/reports/089`、`docs/migrations/089`。

## 输入

```text
scripts/smoke/storage-r2-live-object-02807-smoke.ps1
tasks/in-progress/028-07-f-explicit-v1-bucket-smoke-preflight.md
tasks/done/028-07-e-dev-r2-bucket-policy-guard.md
tasks/in-progress/028-07-r2-live-watchdog-parity.md
```

## 输出

```text
scripts/smoke/storage-r2-live-object-02807-smoke.ps1
tasks/done/028-07-g-real-r2-explicit-v1-bucket-write-smoke.md
```

## 影响范围

允许写入：

```text
scripts/smoke/storage-r2-live-object-02807-smoke.ps1
tasks/done/028-07-g-real-r2-explicit-v1-bucket-write-smoke.md
```

不包含：

```text
services/**
deploy/**
scripts/smoke/storage-service-r2-path-02807-smoke.ps1
scripts/smoke/storage-avatar-r2-live-02807-smoke.ps1
docs/index.md
tasks/index.md
docs/reports/089*
docs/migrations/089*
```

## 依赖

- `kubectl` 可访问 `ircs-dev` namespace。
- `ircs-credential-service` 可通过 port-forward 获取 R2 credential lease。
- `ircs-dev-secrets.INTERNAL_CREDENTIAL_TOKEN` 可供 smoke 读取。
- V1 R2 credential 仍可对 bucket `ircs` 执行 PUT、HEAD、DELETE。

## 风险

- 真实 R2 操作会短暂创建一个临时对象；失败时必须记录 cleanup 状态。
- 如果 DELETE 或 HEAD 404 确认失败，需要在结果中明确记录 object key 和残留风险。
- 本轮只覆盖 credential-only object path，不证明 cover/avatar 业务链路。

## 验收标准

```text
命令显式使用 -UseV1ParityBucketOverride 或 -BucketName ircs。
输出包含 objectKey。
R2 PUT 成功。
R2 HEAD after PUT 成功。
R2 DELETE 成功。
DELETE 后 HEAD 返回 404/notFound。
结果明确 willPatchDeployment=false 或脚本路径不 patch Deployment。
任务文件回写命令、bucket、object key、write/read/delete/cleanup 状态和残留结论。
```

## 测试边界

执行一条最小真实 smoke：

```powershell
.\scripts\smoke\storage-r2-live-object-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -UseV1ParityBucketOverride -ConfirmLiveR2Write
```

不执行：

```text
storage-service cover path smoke
avatar live smoke
任何批量 cover/avatar smoke
任何常驻 ConfigMap patch
任何 deploy manifest 修改
```

## 实施记录

- 第一次真实执行命令已通过 5 个 MARK，但成功路径没有输出 JSON details，无法从命令输出中记录
  object key。
- 为满足本任务验收，最小补强 `scripts/smoke/storage-r2-live-object-02807-smoke.ps1`
  成功路径：在 `PUT -> HEAD -> DELETE -> HEAD 404` 全部通过后输出
  `R2_LIVE_OBJECT_SMOKE_OK` JSON。
- 补强只改变成功结果输出，不改变 R2 写入、读取、删除逻辑，不 patch Deployment。

## 验收结果

PowerShell parser check：

```powershell
$path = '.\scripts\smoke\storage-r2-live-object-02807-smoke.ps1'; $tokens = $null; $errors = $null; [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path -LiteralPath $path), [ref]$tokens, [ref]$errors) | Out-Null; if ($errors.Count -gt 0) { $errors | ForEach-Object { $_.ToString() }; exit 1 }; "PARSER_OK $path"
```

结果：

```text
PARSER_OK .\scripts\smoke\storage-r2-live-object-02807-smoke.ps1
```

真实 smoke 命令：

```powershell
.\scripts\smoke\storage-r2-live-object-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -UseV1ParityBucketOverride -ConfirmLiveR2Write
```

关键输出：

```text
MARK 1/5 R2 credential lease has required payload keys without printing secrets
MARK 2/5 R2 bucket and temporary object key are resolved
MARK 3/5 R2 putObject succeeds for temporary smoke object
MARK 4/5 R2 headObject confirms object exists
MARK 5/5 R2 deleteObject succeeds and headObject confirms cleanup
```

JSON 摘要：

```text
status=SUCCESS
reason=R2_LIVE_OBJECT_SMOKE_OK
checkedAt=2026-06-09T08:59:25.0800986Z
namespace=ircs-dev
bucket=ircs
bucketSource=UseV1ParityBucketOverride
configMapBucket=ircs-dev
objectKey=codex-smoke/02807/20260609165913-r2-live-object.txt
publicDomain=img.mnnu.eu.org
publicUrl=img.mnnu.eu.org/codex-smoke/02807/20260609165913-r2-live-object.txt
putStatusCode=200
headAfterPutStatusCode=200
deleteStatusCode=204
headAfterDeleteStatusCode=404
cleanupConfirmed=true
willWriteR2Objects=true
willPatchDeployment=false
confirmLiveR2Write=true
requiresExplicitBucketOverride=true
```

## 残留状态

- 第二次记录完整 evidence 的对象：
  `codex-smoke/02807/20260609165913-r2-live-object.txt`。
- DELETE 返回 `204`。
- DELETE 后 HEAD 返回 `404`。
- `cleanupConfirmed=true`。
- 未发现 R2 对象残留。
- 未 patch storage-service Deployment。
- 未修改 dev 常驻 ConfigMap bucket，运行时仍记录 `configMapBucket=ircs-dev`。

## 剩余范围

- 本轮未覆盖 cover path 或 avatar path。
- watchdog 长周期恢复策略和批量样本恢复效果仍归属 `028-07-r2-live-watchdog-parity` 后续子任务。
