# 084 avatar R2 live smoke 迁移记录

状态：已完成

日期：2026-06-08

## 迁移内容

为 `028-07` 增加 avatar R2 live smoke，验证 V3 identity avatar upload ->
`q.storage.avatar_sync` -> storage-service R2 upload -> `members.avatar_url` R2 URL 回写 ->
本地 avatar cleanup 的完整路径。

## 范围

```text
scripts/smoke/storage-avatar-r2-live-02807-smoke.ps1
docs/reports/084-02807-avatar-r2-live-smoke.md
tasks/done/028-07-d-avatar-r2-live-smoke.md
```

## 期望结果

```text
MARK 1/7 avatar R2 credential/config resolves V1 bucket override
MARK 2/7 storage-service temporary R2 runtime is enabled without watchdog scan
MARK 3/7 portal avatar upload persists local media and publishes avatar sync
MARK 4/7 SYNC_AVATAR drains and member avatar_url becomes R2 public URL
MARK 5/7 R2 object exists and local avatar object is removed
MARK 6/7 R2 object, member fixture and queues are cleaned
MARK 7/7 dev env restored with no HTTPRoute/quota/port-forward residue
```

## 回滚边界

如 live path 出现异常，恢复 `APP_STORAGE_R2_ENABLED=false`，删除临时 Secret、fixture
member、local avatar object 和 R2 object；不保留 dev 真实 R2 runtime。

## 实施结果

- 新增 `scripts/smoke/storage-avatar-r2-live-02807-smoke.ps1`。
- 完成 identity avatar upload -> `q.storage.avatar_sync` -> storage-service R2 upload ->
  `members.avatar_url` R2 URL 回写 -> 本地 avatar cleanup 的端到端 live 验证。
- smoke 显式使用 V1 bucket `ircs`，不永久修改 dev ConfigMap。

## 验证结果

```powershell
.\gradlew.bat :services:ircs-identity-service:test :services:ircs-storage-service:test --rerun-tasks
.\scripts\smoke\storage-avatar-r2-live-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken
.\scripts\smoke\storage-avatar-r2-live-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -ConfirmAvatarR2Write
```

结果：

```text
identity/storage tests: BUILD SUCCESSFUL
dry-run: SKIPPED/LIVE_AVATAR_R2_WRITE_NOT_CONFIRMED
live smoke: SUCCESS/AVATAR_R2_LIVE_SMOKE_OK
MARK 1/7 through MARK 7/7 all passed
```
