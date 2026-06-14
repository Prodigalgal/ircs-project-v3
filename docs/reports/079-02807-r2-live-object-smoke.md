# 079 028-07 R2 live object smoke 报告

状态：完成第一刀

日期：2026-06-08

关联任务：

- `tasks/in-progress/028-07-r2-live-watchdog-parity.md`

## 背景

`028-07-A` 已完成 storage-service R2 watchdog/recovery 第一刀，验证了 R2 inactive no-op、
R2 active 下本地头像重投、cover image due upload retry 和失败 backoff。V1 真实 R2 credential
已通过 `028-14` 导入 V3 dev credential pool，当前需要先确认 credential pool 中的 R2 凭据能对
dev bucket 完成最小 put/head/delete，而不是直接打开 storage-service R2 runtime。

## 目标

- 新增 credential-only R2 live object smoke。
- 默认 dry-run/diagnostic，不执行真实 R2 写入。
- 显式传入确认参数后，只写入临时 `codex-smoke/02807/**` object key。
- 验证 put、head、delete、head-missing 和 cleanup。
- 不修改 storage-service Deployment，不新增 HTTPRoute，不启动额外 Pod/Service。

## 非目标

- 不打开 `APP_STORAGE_R2_ENABLED=true`。
- 不执行 storage-service `/sync-r2`。
- 不触发 R2 watchdog。
- 不把 R2 secret 明文写入仓库、日志或文档。
- 不做 bucket 列表、批量迁移或真实业务对象处理。

## 当前环境判断

```text
sys_credentials: R2 total=1 enabled=1
required payload keys: access_key, account_id, secret_key
ircs-dev-app-config: R2_BUCKET_NAME=ircs-dev, R2_PUBLIC_DOMAIN=img.mnnu.eu.org
V1 ircs-config: R2_BUCKET_NAME=ircs, R2_PUBLIC_DOMAIN=img.mnnu.eu.org
storage-service runtime: APP_STORAGE_R2_ENABLED=false
```

因此本切片先验证 credential 和 bucket 的最小可用性。首次使用 dev ConfigMap bucket
`ircs-dev` 时，R2 PUT 返回 HTTP 404；使用 V1 bucket `ircs` 后 live object lifecycle 通过。
这说明 R2 credential 有效，当前 V3 dev bucket 配置与 V1 存在漂移。

## 验收标准

```text
MARK 1/5 R2 credential lease has required payload keys without printing secrets
MARK 2/5 R2 bucket and temporary object key are resolved
MARK 3/5 R2 putObject succeeds for temporary smoke object
MARK 4/5 R2 headObject confirms object exists
MARK 5/5 R2 deleteObject succeeds and headObject confirms cleanup
```

## 测试边界

```powershell
.\scripts\smoke\storage-r2-live-object-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken
.\scripts\smoke\storage-r2-live-object-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -ConfirmLiveR2Write
```

第一条只做 diagnostic；第二条才写入并删除临时 R2 object。

## 验证结果

dry-run/diagnostic：

```text
MARK 1/5 R2 credential lease has required payload keys without printing secrets
MARK 2/5 R2 bucket and temporary object key are resolved
SKIPPED/LIVE_R2_WRITE_NOT_CONFIRMED
bucket=ircs-dev
publicDomain=img.mnnu.eu.org
payloadKeys=access_key,account_id,secret_key
```

dev ConfigMap bucket 写入失败：

```text
.\scripts\smoke\storage-r2-live-object-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -ConfirmLiveR2Write
MARK 1/5 R2 credential lease has required payload keys without printing secrets
MARK 2/5 R2 bucket and temporary object key are resolved
FAILED/R2_PUT_FAILED
statusCode=404
bucket=ircs-dev
```

V1 bucket 覆盖后通过：

```text
.\scripts\smoke\storage-r2-live-object-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -ConfirmLiveR2Write -BucketName ircs
MARK 1/5 R2 credential lease has required payload keys without printing secrets
MARK 2/5 R2 bucket and temporary object key are resolved
MARK 3/5 R2 putObject succeeds for temporary smoke object
MARK 4/5 R2 headObject confirms object exists
MARK 5/5 R2 deleteObject succeeds and headObject confirms cleanup
```

后置检查：

```text
APP_STORAGE_R2_ENABLED=false
storage-service Deployment still has no R2 access/secret/account env
local credential-service port-forward residue=none
HTTPRoute absent
ResourceQuota unchanged: pods 28/30, services 24/26
```

## 剩余项

- 决定是否将 `ircs-dev-app-config.R2_BUCKET_NAME` 从 `ircs-dev` 修正为 V1 的 `ircs`。
- storage-service path smoke 仍需先设计 credential-service R2 lease adapter 或临时 Secret env，并禁用 watchdog。
