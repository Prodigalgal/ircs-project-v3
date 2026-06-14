# 083 storage-service R2 path smoke 迁移记录

状态：已完成

日期：2026-06-08

## 迁移内容

在 `028-07-B` credential-only R2 live object smoke 之后，推进 storage-service path
smoke。该迁移先处理 V1/V3 bucket 漂移，并确保 dev 环境临时启用、验证、清理、恢复。

## 范围

```text
deploy/k8s/dev/storage-service-dev.yaml
scripts/smoke/*02807*.ps1
docs/reports/083-02807-storage-service-r2-path-smoke.md
tasks/done/028-07-c-storage-service-r2-path-smoke.md
```

## 期望结果

```text
MARK 1/6 storage-service R2 path smoke resolves V1 bucket or explicit override
MARK 2/6 storage-service temporary R2 runtime is enabled without watchdog scan
MARK 3/6 fixture object is uploaded through storage-service path
MARK 4/6 DB/media URL side effect matches V1 public domain semantics
MARK 5/6 R2 object and DB fixture are cleaned
MARK 6/6 dev env restored with no HTTPRoute/quota/port-forward residue
```

## 回滚边界

如 live path 出现异常，立即恢复 `APP_STORAGE_R2_ENABLED=false`，停止本地 port-forward，
删除 fixture DB 行和临时 R2 object；不保留 dev 真实 R2 runtime。

## 实施记录

- 新增 `scripts/smoke/storage-service-r2-path-02807-smoke.ps1`，默认 diagnostic，不带
  `-ConfirmStorageServiceR2Write` 时只验证 R2 credential/config，不修改 Deployment。
- live 路径临时创建 `ircs-r2-smoke-02807` Secret，并注入 storage-service R2 env；
  bucket 显式覆盖为 V1 `ircs`，public domain 使用 `img.mnnu.eu.org`。
- live 路径禁用 `APP_STORAGE_R2_WATCHDOG_ENABLED`，只处理本轮 cover fixture。
- 已修正恢复逻辑：恢复前删除临时 `APP_STORAGE_R2_*` value，再 apply
  `deploy/k8s/dev/storage-service-dev.yaml`，确保 bucket/publicDomain 回到 ConfigMap
  `valueFrom`，避免 `value`/`valueFrom` 冲突。

## 验证记录

```powershell
.\gradlew.bat :services:ircs-magnet-service:test :services:ircs-notification-worker:test :services:ircs-credential-service:test :services:ircs-ops-service:test :services:ircs-storage-service:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL in 44s`。

```powershell
.\scripts\smoke\storage-service-r2-path-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken
```

结果：`SKIPPED/LIVE_STORAGE_SERVICE_R2_WRITE_NOT_CONFIRMED`，只完成 `MARK 1/6` diagnostic。

```powershell
.\scripts\smoke\storage-service-r2-path-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -ConfirmStorageServiceR2Write
```

结果：

```text
MARK 1/6 storage-service R2 path smoke resolves V1 bucket or explicit override
MARK 2/6 storage-service temporary R2 runtime is enabled without watchdog scan
MARK 3/6 fixture object is uploaded through storage-service path
MARK 4/6 DB/media URL side effect matches V1 public domain semantics
MARK 5/6 R2 object and DB fixture are cleaned
MARK 6/6 dev env restored with no HTTPRoute/quota/port-forward residue
SUCCESS/STORAGE_SERVICE_R2_PATH_SMOKE_OK
```

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
