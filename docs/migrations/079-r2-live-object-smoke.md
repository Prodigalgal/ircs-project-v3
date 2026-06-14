# 079 R2 live object smoke 迁移记录

状态：完成第一刀

日期：2026-06-08

关联报告：

- `docs/reports/079-02807-r2-live-object-smoke.md`

关联任务：

- `tasks/in-progress/028-07-r2-live-watchdog-parity.md`

## 迁移内容

新增 `028-07` credential-only R2 live object smoke，用于在不打开 storage-service R2 runtime 的前提下，
验证 V1 导入的 R2 credential 能对 dev bucket 执行最小 object lifecycle。

## 代码范围

```text
scripts/smoke/storage-r2-live-object-02807-smoke.ps1
docs/reports/079-02807-r2-live-object-smoke.md
docs/migrations/079-r2-live-object-smoke.md
tasks/in-progress/028-07-r2-live-watchdog-parity.md
```

## 行为边界

- 默认 dry-run/diagnostic，不执行 R2 put/delete。
- 只有显式传入 `-ConfirmLiveR2Write` 才写入临时 object。
- object key 固定在 `codex-smoke/02807/` 前缀下。
- 脚本输出 credential id、payload key 集合、bucket、object key 和 cleanup 状态，不输出 secret。
- 不修改 Kubernetes Deployment，不新增 HTTPRoute，不触发 storage-service watchdog。

## 回滚策略

删除 smoke 脚本和本报告/迁移/任务回写即可；没有数据库结构变更，没有 Kubernetes 资源变更。

## 验证

已执行：

```powershell
.\scripts\smoke\storage-r2-live-object-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken
.\scripts\smoke\storage-r2-live-object-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -ConfirmLiveR2Write
```

结果：

```text
dry-run: MARK 1/5, MARK 2/5, SKIPPED/LIVE_R2_WRITE_NOT_CONFIRMED
dev ConfigMap bucket ircs-dev: FAILED/R2_PUT_FAILED statusCode=404
V1 bucket ircs override: MARK 1/5 through MARK 5/5
```

迁移结论：

```text
R2 credential is valid.
V3 dev ConfigMap R2_BUCKET_NAME=ircs-dev drifts from V1 R2_BUCKET_NAME=ircs.
storage-service runtime remains APP_STORAGE_R2_ENABLED=false.
No HTTPRoute or Kubernetes resource was added.
```
