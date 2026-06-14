# 2026-06-07 IRCS dev rollout log

来源：`docs/k8s/current-environment-2026-06-02.md` 拆分。

## 2026-06-07 Route Closeout Rollout

`025-18` 到 `025-20` 已完成 ARM64 image、rollout 和 BFF live smoke。相关 Deployment
仍复用既有 Pod/Service，未新增 HTTPRoute：

```text
deployment/ircs-magnet-service     Ready 1/1
deployment/ircs-identity-service   Ready 1/1
deployment/ircs-ops-service        Ready 1/1
deployment/ircs-portal-bff         Ready 1/1
deployment/ircs-admin-bff          Ready 1/1

ircs-magnet-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-magnet-service@sha256:31c72a41f0d2d70967df00931b8037fa74188d7689b7556f8b508142d763b4ea
ircs-identity-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-identity-service@sha256:24edd3d5c562b1bcd383ff19e9e111eb05ba8a55fb097179ab2709f35af4453a
ircs-ops-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-ops-service@sha256:c3fc3dff2092be4695cd999da3f50025ed23bbb7b652fa3fb8733ff68934d7bd
ircs-portal-bff imageID:
  harbor.mnnu.eu.org/ircs/ircs-portal-bff@sha256:4a5aa6b1431666f9b5ac86077f16a20e120b9aa1b4000d75131262c245a2e25e
ircs-admin-bff imageID:
  harbor.mnnu.eu.org/ircs/ircs-admin-bff@sha256:9dbac6b9532eb8c2a216a0994ed9d490dbd2657cb367959bea4ad8c2e58e3a50

ResourceQuota:
  pods=28/30, services=24/26
HTTPRoute in ircs-dev:
  none
```

Smoke summary:

```text
025-18 admin-bff magnet provider/search: MARK 3/3
025-19 portal-bff avatar upload:          MARK 1/1
025-20 admin-bff ops guarded contract:    MARK 19/19
```

BFF fix:

```text
services/ircs-portal-bff/src/main/resources/application.yaml
services/ircs-admin-bff/src/main/resources/application.yaml
spring.servlet.multipart.enabled=false
```

Reason: BFF should preserve raw multipart request bodies and let downstream services parse them.

## 2026-06-07 Behavior Parity 028-01 Rollout

`028-01` 已完成 portal/admin BFF request audit collection。该切片不新增 Pod、Service
或 HTTPRoute，只在既有 BFF Pod 中增加轻量 JDBC 写入能力，并复用已有
`request_audit_logs` 表和 `ops-service` 查询接口。

```text
deployment/ircs-portal-bff         Ready 1/1
deployment/ircs-admin-bff          Ready 1/1
deployment/ircs-ops-service        Ready 1/1

ircs-portal-bff imageID:
  harbor.mnnu.eu.org/ircs/ircs-portal-bff@sha256:d051d3f2d2391de6a7ff2f01408ba4bbc79d63ac1f87931029e69c7e0d22fa52
ircs-admin-bff imageID:
  harbor.mnnu.eu.org/ircs/ircs-admin-bff@sha256:4e80119902baef1cb59f50f5a53d05d42aac3e39b3ff8f26c99764f21d0c6854

ResourceQuota:
  pods=28/30, services=24/26
HTTPRoute in ircs-dev:
  none
```

Smoke summary:

```text
028-01 portal-bff request audit collection: MARK 1/2
028-01 admin-bff request audit collection:  MARK 2/2
request_audit_logs smoke rows after cleanup: 0
```

## 2026-06-07 Behavior Parity 028-02 To 028-04 Rollout

`028-02`、`028-03`、`028-04` 已完成 ARM64 image、K8S rollout 和统一 ClusterIP
smoke。该批次复用既有 Deployment/Service，不新增 Pod、Service、PVC、中间件或
HTTPRoute。

```text
deployment/ircs-storage-service    Ready 1/1
deployment/ircs-identity-service   Ready 1/1
deployment/ircs-magnet-service     Ready 1/1
deployment/ircs-ops-service        Ready 1/1
deployment/ircs-search-service     Ready 1/1
deployment/ircs-portal-bff         Ready 1/1
deployment/ircs-admin-bff          Ready 1/1

ircs-storage-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-storage-service@sha256:f73fd025c806bd09768b1336647b6806105896fbb3ece4ad19ca2ce689b26e02
ircs-identity-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-identity-service@sha256:58381fc9c9acc25f82d0ac01a14d7e52623af5ca73e6bcce349e231a862f4456
ircs-magnet-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-magnet-service@sha256:5fb8ae6bb463ab202c971d2ca8cffc1fb21491bf7d78de0510b2715ada1984e6
ircs-ops-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-ops-service@sha256:77736bf364124fe8e582f71ce72cee7edf065c2812b16bbcfe1101963046253d

ResourceQuota:
  pods=28/30, services=24/26
HTTPRoute in ircs-dev:
  none
```

Smoke summary:

```text
.\scripts\smoke\behavior-02802-02804-parity-smoke.ps1
MARK 1/8 identity avatar upload persists media through storage-service
MARK 2/8 storage-service serves uploaded avatar through /media/avatars/*
MARK 3/8 POST /api/v1/magnets/search/unified/{id} creates non-skipped search job
MARK 4/8 provider fixture run writes magnet_provider_runs
MARK 5/8 provider fixture candidate upserts magnet_links
MARK 6/8 ops search-reindex-unified runner publishes dev-safe progress
MARK 7/8 search-service consumes reindex message and indexes unified video
MARK 8/8 smoke cleanup leaves no temporary DB/ES residue
```

Implementation note:

```text
identity-service rollout first exposed a Spring constructor selection issue in
RestClientAvatarStorageClient. The production constructor is now explicitly
annotated with @Autowired, and a lightweight ApplicationContextRunner test
guards the bean creation path.
```

## 2026-06-07 Behavior Parity 028-05-A Rollout

`028-05-A` 已完成 `q.interaction.watch_progress` 消费者、ARM64 image、K8S rollout 和
RabbitMQ/DB smoke。该切片复用既有 `ircs-interaction-service` Deployment/Service，不新增
Pod、Service、PVC、中间件或 HTTPRoute。

```text
deployment/ircs-interaction-service Ready 1/1

ircs-interaction-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-interaction-service@sha256:a70f46ed265944ac06f180e68d8930268542c7c53519f624273b52b7cb296ab4

ResourceQuota:
  pods=28/30, services=24/26
HTTPRoute in ircs-dev:
  none
RabbitMQ:
  q.interaction.watch_progress=0 messages, 1 consumer
  q.interaction.watch_progress.dlq=0 messages
```

Smoke summary:

```text
.\scripts\smoke\interaction-watch-progress-02805a-smoke.ps1
MARK 1/4 WATCH_PROGRESS consumer listens on q.interaction.watch_progress
MARK 2/4 consumer upserts member_watch_histories from fixture payload
MARK 3/4 duplicate messages keep latest timestamp per member/unified pair
MARK 4/4 smoke cleanup leaves no DB residue and WATCH_PROGRESS queues empty
```

## 2026-06-07 Behavior Parity 028-05-H Rollout

`028-05-H` 已完成 `q.storage.image` DOWNLOAD_IMAGE live smoke。该切片复用既有
`ircs-storage-service` Deployment/Service；smoke 期间短暂创建 in-cluster fixture
Pod/Service/ConfigMap，并临时设置 storage-service dev 环境变量，结束后全部恢复和清理。
未新增公共 HTTPRoute。

```text
deployment/ircs-storage-service Ready 1/1

ircs-storage-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-storage-service@sha256:2fdc14e5fc39f1939ebde89c8d06963a4cc39d720a085469fb22ba678d498df4

ResourceQuota:
  pods=28/30, services=24/26
HTTPRoute in ircs-dev:
  No resources found in ircs-dev namespace.
RabbitMQ:
  q.storage.image=0 messages, 1 consumer
  q.storage.image.dlq=0 messages
Fixture cleanup:
  pod/service/configmap deleted
  source_domains residue=0
  cover_images residue=0
```

Smoke summary:

```text
.\scripts\smoke\storage-image-download-02805h-smoke.ps1
MARK 1/5 DOWNLOAD_IMAGE consumer listens on q.storage.image
MARK 2/5 DOWNLOAD_IMAGE fixture finalizes cover_images as LOCAL_STORED
MARK 3/5 downloaded cover object exists under storage-service local storage
MARK 4/5 q.storage.image and DLQ are empty after live download
MARK 5/5 live smoke cleanup leaves no DB/object residue, q.storage.image and DLQ empty
```

## 2026-06-07 Behavior Parity 028-11 Rollout

`028-11` 已完成首个 service handler audit 切片。该切片复用既有 `ircs-config-service`
和 `ircs-ops-service` Deployment/Service，不新增 Pod、Service、PVC、中间件或公共
HTTPRoute。dev PostgreSQL 已应用 `request_audit_logs.request_source` 字段和索引。

```text
deployment/ircs-config-service Ready 1/1
deployment/ircs-ops-service    Ready 1/1

ircs-config-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-config-service@sha256:dea99906f21224ba835b7beffa47eb63e5c6e17cf513e9b0c39a678665c3986c

ircs-ops-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-ops-service@sha256:a41ea0eed59902f975b9ef2a38bb4c29b72534a6c98d98a89f64d80ae92820de

ResourceQuota:
  pods=28/30, services=24/26
  requests.cpu=880m/4, requests.memory=4480Mi/8Gi
  limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
HTTPRoute in ircs-dev:
  No resources found in ircs-dev namespace.
Fixture cleanup:
  request_audit_logs 02811 trace residue=0
```

Smoke summary:

```text
.\scripts\smoke\service-audit-02811-smoke.ps1
MARK 1/3 request_audit_logs schema can store request_source
MARK 2/3 config-service handler request writes service audit row
MARK 3/3 ops-service request audit response exposes requestSource
```

剩余 service/worker audit 铺开不计入 `028-11` 完成范围，已拆入
`tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`。

## 2026-06-07 Behavior Parity 028-07-A Rollout

`028-07-A` 已完成 storage R2 watchdog/recovery 第一刀。该切片复用既有
`ircs-storage-service` Deployment/Service，不新增 Pod、Service、PVC、中间件或公共
HTTPRoute。当前 dev 清单保持 `APP_STORAGE_R2_ENABLED=false`，所以 watchdog 在 R2 inactive
路径下安全 no-op；真实 R2 live credential smoke 仍留给 `028-07` 后续切片。

```text
deployment/ircs-storage-service Ready 1/1

ircs-storage-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-storage-service@sha256:1355d90561d00e412d333bcf95566987697cd93f4861f27d4bdd17a9a57b7172

ResourceQuota:
  pods=28/30, services=24/26
  requests.cpu=880m/4, requests.memory=4480Mi/8Gi
  limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
HTTPRoute in ircs-dev:
  No resources found in ircs-dev namespace.
```

Local test summary:

```text
.\gradlew.bat :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL in 14s

MARK 1/4 R2 inactive watchdog skips avatar and cover recovery
MARK 2/4 R2 active watchdog republishes local avatar members to q.storage.avatar_sync
MARK 3/4 R2 active watchdog uploads due LOCAL_STORED cover image to R2
MARK 4/4 R2 upload failure records retry/backoff without aborting watchdog batch
```

