# 083 028-07 storage-service R2 path smoke 报告

状态：已完成

日期：2026-06-08

## 背景

`028-07-B` 已证明 V1 导入的 R2 credential 可对 V1 bucket `ircs` 完成 put/head/delete。
当前 `ircs-dev-app-config.R2_BUCKET_NAME=ircs-dev` 与 V1 `R2_BUCKET_NAME=ircs` 漂移，
导致默认 bucket live PUT 返回 404。storage-service 运行时仍为
`APP_STORAGE_R2_ENABLED=false`，且未注入 R2 access/secret/account。

下一刀目标不是扩大 R2 外呼，而是先固定 storage-service path smoke 的配置修正边界：
修正或显式覆盖 bucket，临时启用最小 R2 runtime，验证 storage-service path 能完成对象写入、
URL 回写和 cleanup，然后恢复 dev 默认关闭。

## 目标

- 确认 dev ConfigMap bucket 漂移处理策略。
- 为 storage-service path smoke 固定临时 R2 env、watchdog gate 和 cleanup。
- 默认不打开 watchdog，不扫描非 fixture 数据。
- smoke 后恢复 `APP_STORAGE_R2_ENABLED=false`，无 HTTPRoute、无 queue residue。

## 非目标

- 不永久打开 dev R2 runtime。
- 不处理全量 avatar/cover 历史数据。
- 不新增 HTTPRoute、Service、Pod 或中间件。
- 不打印 R2 secret。

## 建议写入范围

```text
deploy/k8s/dev/storage-service-dev.yaml
scripts/smoke/*02807*.ps1
docs/reports/083-02807-storage-service-r2-path-smoke.md
docs/migrations/083-storage-service-r2-path-smoke.md
tasks/done/028-07-c-storage-service-r2-path-smoke.md
```

## 验收标准

```text
MARK 1/6 storage-service R2 path smoke resolves V1 bucket or explicit override
MARK 2/6 storage-service temporary R2 runtime is enabled without watchdog scan
MARK 3/6 fixture object is uploaded through storage-service path
MARK 4/6 DB/media URL side effect matches V1 public domain semantics
MARK 5/6 R2 object and DB fixture are cleaned
MARK 6/6 dev env restored with no HTTPRoute/quota/port-forward residue
```

## 测试边界

```powershell
.\gradlew.bat :services:ircs-storage-service:test --rerun-tasks
.\scripts\smoke\storage-service-r2-path-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken
.\scripts\smoke\storage-service-r2-path-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -ConfirmStorageServiceR2Write
```

storage-service path smoke 需要主线程根据当前 `ircs-dev` 实态执行，不委派给 subagent。

## 实施结果

- 新增 `scripts/smoke/storage-service-r2-path-02807-smoke.ps1`。
- smoke 默认只完成 credential/config diagnostic，未显式确认时不修改 storage-service。
- live 路径通过 credential-service 租用 R2 credential，临时创建 K8S Secret 并注入
  `ircs-storage-service`，显式覆盖 `APP_STORAGE_R2_BUCKET_NAME=ircs`，同时设置
  `APP_STORAGE_R2_WATCHDOG_ENABLED=false`。
- live 路径通过 storage-service HTTP 上传最小 cover fixture，再调用
  `POST /api/v1/cover-images/{id}/sync-r2`，验证 DB row 进入 `R2/REMOTE_STORED` 且 URL
  使用 V1 public domain `img.mnnu.eu.org`。
- cleanup 会删除 R2 临时对象、本地 storage fixture、DB row、临时 Secret，并恢复
  `APP_STORAGE_R2_ENABLED=false` 与原始 ConfigMap valueFrom 绑定。
- 已修正恢复函数：恢复时先删除 `APP_STORAGE_R2_ENABLED`、
  `APP_STORAGE_R2_BUCKET_NAME`、`APP_STORAGE_R2_PUBLIC_DOMAIN` 等临时 env，再重新 apply
  `deploy/k8s/dev/storage-service-dev.yaml`，避免 `value` 与 `valueFrom` 冲突。

## 验收结果

```text
MARK 1/6 storage-service R2 path smoke resolves V1 bucket or explicit override：通过
MARK 2/6 storage-service temporary R2 runtime is enabled without watchdog scan：通过
MARK 3/6 fixture object is uploaded through storage-service path：通过
MARK 4/6 DB/media URL side effect matches V1 public domain semantics：通过
MARK 5/6 R2 object and DB fixture are cleaned：通过
MARK 6/6 dev env restored with no HTTPRoute/quota/port-forward residue：通过
```

## 验证记录

主线程聚合测试：

```powershell
.\gradlew.bat :services:ircs-magnet-service:test :services:ircs-notification-worker:test :services:ircs-credential-service:test :services:ircs-ops-service:test :services:ircs-storage-service:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL in 44s`，28 个 Gradle task executed。

PowerShell parse check：

```text
PARSER_OK storage-service-r2-path-02807-smoke.ps1
```

Diagnostic dry-run：

```powershell
.\scripts\smoke\storage-service-r2-path-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken
```

结果：`MARK 1/6` 后按预期返回
`SKIPPED/LIVE_STORAGE_SERVICE_R2_WRITE_NOT_CONFIRMED`，不修改 storage-service。

Live smoke：

```powershell
.\scripts\smoke\storage-service-r2-path-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -ConfirmStorageServiceR2Write
```

结果：`SUCCESS/STORAGE_SERVICE_R2_PATH_SMOKE_OK`。

关键证据：

```text
traceId=02807-storage-r2-path-20260608194526
credentialId=133e890a-fe1c-40c4-89c6-65d7f757bd57
configMapBucket=ircs-dev
effectiveBucket=ircs
publicDomain=img.mnnu.eu.org
coverImageId=16f8e4c9-fca3-4cc0-8021-2fda51e8d7fc
storagePath=covers/3220fc7838ccdab0a3a21b5d8498275c7abe6930abbb059e9fdb4939bba8c2fd.png
HTTPRoute=""
ResourceQuota: pods=28/30, services=24/26
```

后置核查：

```text
storage-service APP_STORAGE_R2_ENABLED=false
APP_STORAGE_R2_BUCKET_NAME  restored to ircs-dev-app-config.R2_BUCKET_NAME valueFrom
APP_STORAGE_R2_PUBLIC_DOMAIN restored to ircs-dev-app-config.R2_PUBLIC_DOMAIN valueFrom
temporary secret ircs-r2-smoke-02807 absent
cover_images fixture residue=0
kubectl port-forward residue=none
ResourceQuota unchanged: pods=28/30, services=24/26
```
