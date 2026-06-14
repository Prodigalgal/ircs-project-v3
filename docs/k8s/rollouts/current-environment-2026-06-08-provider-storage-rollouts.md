# 2026-06-08 credential/provider/storage rollout log

来源：`current-environment-2026-06-08-rollouts.md` 拆分。

## 2026-06-08 Behavior Parity 028-14 V1 Credential Import And 028-06 TMDB Live Queue

`028-14` 已从 V1 `ircs-config` 安全导入真实凭据到 `ircs-dev.sys_credentials`，未在脚本输出、
文档或任务中记录明文 secret。导入后 provider pool：

```text
MAIL   total=1 enabled=1
OPENAI total=1 enabled=1
R2     total=1 enabled=1
TMDB   total=8 enabled=8
```

`028-06` 随后完成 TMDB direct diagnostic 与 live queue success。live queue smoke 复用既有
`ircs-metadata-worker`、`ircs-credential-service`、`ircs-search-service`、RabbitMQ、PostgreSQL
和 Elasticsearch，不新增 Pod、Service、PVC、中间件或公共 HTTPRoute。smoke 完成后已恢复
`APP_METADATA_TMDB_ENABLED=false`，继续避免 dev 自动 provider fan-out。

Live smoke summary：

```text
.\scripts\smoke\metadata-provider-live-parity-02806-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken
SUCCESS TMDB_LIVE_SEARCH_OK
firstResultId=603
title=黑客帝国
credential provider=TMDB

.\scripts\smoke\metadata-provider-live-queue-02806-smoke.ps1 -SkipPreflightTmdbDiagnostic -WaitAttempts 45 -WaitSeconds 2 -PurgeSmokeQueuesOnCleanup
MARK 1/6 credential lease succeeded; TMDB upstream diagnostic skipped for direct queue verification
MARK 2/6 metadata/search consumers are ready and HTTPRoute remains absent
MARK 3/6 TMDB provider queue task is published and q.fetch.metadata.tmdb drains
MARK 4/6 provider result updates raw metadata to SUCCESS with tmdb_id
MARK 5/6 raw search sync refreshes ES document with TMDB enrichment fields
MARK 6/6 provider/result/search queues and DLQs are empty; smoke fixture is cleaned
```

Post-check：

```text
APP_METADATA_TMDB_ENABLED=false
APP_METADATA_TMDB_WORKER_ENABLED=true
APP_METADATA_LISTENER_ENABLED=true
DB fixture residue=0
q.fetch.metadata.tmdb messages=0
q.enrich.metadata.result messages=0
q.search.sync.raw messages=0
local port-forward residue=none
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
```


## 2026-06-08 Behavior Parity 028-07 R2 Credential-Only Live Object Smoke

`028-07` 已完成 credential-only R2 live object smoke。该切片只通过 credential-service 租用
V1 导入的 R2 credential，并直接验证 Cloudflare R2 临时对象 lifecycle；没有打开
`ircs-storage-service` 的 R2 runtime，没有触发 watchdog，没有新增 Pod、Service、PVC、中间件或
公共 HTTPRoute。

环境状态：

```text
sys_credentials: R2 total=1 enabled=1
ircs-dev-app-config: R2_BUCKET_NAME=ircs-dev, R2_PUBLIC_DOMAIN=img.mnnu.eu.org
V1 ircs-config: R2_BUCKET_NAME=ircs, R2_PUBLIC_DOMAIN=img.mnnu.eu.org
storage-service: APP_STORAGE_R2_ENABLED=false
storage-service R2 access/secret/account env: not injected
```

Smoke summary：

```text
.\scripts\smoke\storage-r2-live-object-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken
MARK 1/5 R2 credential lease has required payload keys without printing secrets
MARK 2/5 R2 bucket and temporary object key are resolved
SKIPPED/LIVE_R2_WRITE_NOT_CONFIRMED

.\scripts\smoke\storage-r2-live-object-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -ConfirmLiveR2Write
FAILED/R2_PUT_FAILED statusCode=404 bucket=ircs-dev

.\scripts\smoke\storage-r2-live-object-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -ConfirmLiveR2Write -BucketName ircs
MARK 1/5 R2 credential lease has required payload keys without printing secrets
MARK 2/5 R2 bucket and temporary object key are resolved
MARK 3/5 R2 putObject succeeds for temporary smoke object
MARK 4/5 R2 headObject confirms object exists
MARK 5/5 R2 deleteObject succeeds and headObject confirms cleanup
```

结论：

```text
R2 credential 可用。
V3 dev ConfigMap bucket=ircs-dev 与 V1 bucket=ircs 漂移；当前 storage-service path smoke
需要先修正 bucket 或显式覆盖。
HTTPRoute remains absent.
ResourceQuota unchanged: pods=28/30, services=24/26.
local credential-service port-forward residue=none.
```


## 2026-06-08 Behavior Parity 028-07 Storage Service R2 Path Live Smoke

`028-07-C` 已完成 storage-service R2 path live smoke。该切片复用既有
`ircs-storage-service` Deployment 和 ClusterIP Service，不新增 Pod、Service、PVC、中间件或
公共 HTTPRoute。smoke 使用 credential-service 租用 V1 导入的 R2 credential，临时创建
`ircs-r2-smoke-02807` Secret 注入 storage-service，并显式覆盖 bucket 为 V1 `ircs`；同时设置
`APP_STORAGE_R2_WATCHDOG_ENABLED=false`，避免扫描非本轮 fixture。

Smoke summary：

```text
.\scripts\smoke\storage-service-r2-path-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken
MARK 1/6 storage-service R2 path smoke resolves V1 bucket or explicit override
SKIPPED/LIVE_STORAGE_SERVICE_R2_WRITE_NOT_CONFIRMED

.\scripts\smoke\storage-service-r2-path-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -ConfirmStorageServiceR2Write
MARK 1/6 storage-service R2 path smoke resolves V1 bucket or explicit override
MARK 2/6 storage-service temporary R2 runtime is enabled without watchdog scan
MARK 3/6 fixture object is uploaded through storage-service path
MARK 4/6 DB/media URL side effect matches V1 public domain semantics
MARK 5/6 R2 object and DB fixture are cleaned
MARK 6/6 dev env restored with no HTTPRoute/quota/port-forward residue
SUCCESS/STORAGE_SERVICE_R2_PATH_SMOKE_OK
```

Live evidence：

```text
traceId=02807-storage-r2-path-20260608194526
credentialId=133e890a-fe1c-40c4-89c6-65d7f757bd57
configMapBucket=ircs-dev
effectiveBucket=ircs
publicDomain=img.mnnu.eu.org
coverImageId=16f8e4c9-fca3-4cc0-8021-2fda51e8d7fc
storagePath=covers/3220fc7838ccdab0a3a21b5d8498275c7abe6930abbb059e9fdb4939bba8c2fd.png
```

Post-check：

```text
APP_STORAGE_R2_ENABLED=false
APP_STORAGE_R2_BUCKET_NAME valueFrom=ircs-dev-app-config.R2_BUCKET_NAME
APP_STORAGE_R2_PUBLIC_DOMAIN valueFrom=ircs-dev-app-config.R2_PUBLIC_DOMAIN
temporary secret ircs-r2-smoke-02807 absent
cover_images fixture residue=0
HTTPRoute in ircs-dev: No resources found
ResourceQuota unchanged: pods=28/30, services=24/26
local kubectl port-forward residue=none
```

