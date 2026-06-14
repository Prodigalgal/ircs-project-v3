# 028-07-C storage-service R2 path smoke task spec

状态：已完成

父任务：

- `tasks/in-progress/028-07-r2-live-watchdog-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/083-02807-storage-service-r2-path-smoke.md`
- `docs/migrations/083-storage-service-r2-path-smoke.md`

## 背景

R2 credential-only smoke 已证明 V1 bucket `ircs` 可用，但 V3 dev ConfigMap 仍是
`ircs-dev`，storage-service 也未启用 R2 runtime。需要把 storage-service path smoke
拆成独立任务，避免直接永久打开真实 R2。

## 目标

- 解决或显式覆盖 bucket 漂移。
- 临时启用 storage-service R2 runtime，禁用 watchdog 扫描。
- 通过最小 fixture 验证对象写入、URL/DB side effect 和 cleanup。
- 结束后恢复 dev 默认关闭。

## 非目标

- 不永久开启 R2。
- 不扫描全量本地媒体或历史 cover/avatar。
- 不新增 HTTPRoute、Service、Pod、中间件。
- 不打印 secret。

## 输入

```text
scripts/smoke/storage-r2-live-object-02807-smoke.ps1
deploy/k8s/dev/storage-service-dev.yaml
ircs-dev-app-config R2_BUCKET_NAME/R2_PUBLIC_DOMAIN
V1 ircs-config R2_BUCKET_NAME/R2_PUBLIC_DOMAIN
sys_credentials R2 lease
```

## 输出

```text
storage-service path smoke script/config adjustments
live smoke evidence
docs/reports/083-02807-storage-service-r2-path-smoke.md
docs/migrations/083-storage-service-r2-path-smoke.md
```

## 影响范围

```text
deploy/k8s/dev/storage-service-dev.yaml
scripts/smoke/*02807*.ps1
docs/reports/083-02807-storage-service-r2-path-smoke.md
docs/migrations/083-storage-service-r2-path-smoke.md
tasks/done/028-07-c-storage-service-r2-path-smoke.md
docs/k8s/current-environment-2026-06-02.md
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
```

Live smoke 只能由主线程执行，并在 finally 中恢复 dev 环境。

## 实施结果

- 新增 `scripts/smoke/storage-service-r2-path-02807-smoke.ps1`。
- 通过 credential-service R2 lease 获取 `access_key`、`secret_key`、`account_id`，不打印
  secret。
- live smoke 临时注入 storage-service R2 env，显式使用 V1 bucket `ircs`，并禁用 watchdog。
- 通过 storage-service cover upload + `sync-r2` path 验证对象进入 R2、DB row 更新为
  `R2/REMOTE_STORED`、URL 使用 `img.mnnu.eu.org/{storagePath}`。
- cleanup 删除 R2 object、本地文件、DB fixture、临时 Secret，并恢复 storage-service
  dev 默认关闭。
- 已修复 smoke restore 函数，恢复前删除临时 `APP_STORAGE_R2_ENABLED`、
  `APP_STORAGE_R2_BUCKET_NAME`、`APP_STORAGE_R2_PUBLIC_DOMAIN` 等 env，避免后续 apply
  manifest 时出现 `value`/`valueFrom` 冲突。

## 验收结果

```text
MARK 1/6 storage-service R2 path smoke resolves V1 bucket or explicit override：通过
MARK 2/6 storage-service temporary R2 runtime is enabled without watchdog scan：通过
MARK 3/6 fixture object is uploaded through storage-service path：通过
MARK 4/6 DB/media URL side effect matches V1 public domain semantics：通过
MARK 5/6 R2 object and DB fixture are cleaned：通过
MARK 6/6 dev env restored with no HTTPRoute/quota/port-forward residue：通过
```

## 测试记录

```powershell
.\gradlew.bat :services:ircs-magnet-service:test :services:ircs-notification-worker:test :services:ircs-credential-service:test :services:ircs-ops-service:test :services:ircs-storage-service:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL in 44s`，28 个 Gradle task executed。

```text
PARSER_OK storage-service-r2-path-02807-smoke.ps1
```

```powershell
.\scripts\smoke\storage-service-r2-path-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken
```

结果：`SKIPPED/LIVE_STORAGE_SERVICE_R2_WRITE_NOT_CONFIRMED`，只完成 diagnostic，不修改
Deployment。

```powershell
.\scripts\smoke\storage-service-r2-path-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -ConfirmStorageServiceR2Write
```

结果：`SUCCESS/STORAGE_SERVICE_R2_PATH_SMOKE_OK`。

```text
traceId=02807-storage-r2-path-20260608194526
coverImageId=16f8e4c9-fca3-4cc0-8021-2fda51e8d7fc
storagePath=covers/3220fc7838ccdab0a3a21b5d8498275c7abe6930abbb059e9fdb4939bba8c2fd.png
configMapBucket=ircs-dev
effectiveBucket=ircs
publicDomain=img.mnnu.eu.org
```

## 后置状态

```text
storage-service APP_STORAGE_R2_ENABLED=false
APP_STORAGE_R2_BUCKET_NAME restored to ircs-dev-app-config.R2_BUCKET_NAME valueFrom
APP_STORAGE_R2_PUBLIC_DOMAIN restored to ircs-dev-app-config.R2_PUBLIC_DOMAIN valueFrom
temporary secret ircs-r2-smoke-02807 absent
cover_images fixture residue=0
HTTPRoute absent
ResourceQuota unchanged: pods=28/30, services=24/26
local kubectl port-forward residue=none
```

## 剩余边界

本子任务完成时，storage-service cover R2 path smoke 已闭环；`028-07` 父任务当时仍保持进行中，剩余项为
avatar R2 live 上传/URL 回写/本地清理，以及是否将 dev ConfigMap bucket 从 `ircs-dev`
调整到 V1 `ircs` 的配置决策。

2026-06-09 收敛更新：上述剩余边界已由 `028-07-D/E/F/G/H/I/J` 继续推进并闭环；
`028-07` 父任务当前已完成，见 `tasks/done/028-07-r2-live-watchdog-parity.md` 和
`docs/reports/093-v1-v3-parity-progress-ledger.md`。
