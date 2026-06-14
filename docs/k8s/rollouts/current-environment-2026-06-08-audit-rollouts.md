# 2026-06-08 audit rollout log

来源：`current-environment-2026-06-08-rollouts.md` 拆分。

## 2026-06-08 Behavior Parity 028-12 Ops Query Rollout

`028-12` 已完成 worker/job audit ops 查询 API rollout。该切片复用既有
`ircs-ops-service` Deployment/Service，不新增 Service、PVC、中间件或公共 HTTPRoute。
为应用 `worker_job_audit_events` live schema，临时运行一次 `ircs-migrator` Job；
Job 完成后已删除，namespace 不保留 migrator Pod。

首次按旧 migrator dev 资源运行时被 ResourceQuota 阻止：

```text
exceeded quota: ircs-dev-quota, requested: limits.cpu=500m, used: limits.cpu=7650m, limited: limits.cpu=8
```

已将 `deploy/k8s/dev/migrator-job-dev.yaml` 调整为：

```text
requests.cpu=25m
limits.cpu=250m
```

Rollout evidence：

```text
deployment/ircs-ops-service Ready 1/1

ircs-ops-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-ops-service@sha256:34a42fe11f08a78ba2ae1b042b6cbe2b32bf04e42e6b9711affc1cc57600d01a

ircs-migrator imageID:
  harbor.mnnu.eu.org/ircs/ircs-migrator@sha256:aaaf00ef4567fb47daeda23d85eaf92f0bc5bde702a8bdac43cf1b2a38ddace0

worker_job_audit_events table:
  exists

ResourceQuota:
  pods=28/30, services=24/26
  requests.cpu=880m/4, requests.memory=4480Mi/8Gi
  limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
HTTPRoute in ircs-dev:
  No resources found in ircs-dev namespace.
Migrator cleanup:
  No resources found for app=ircs-migrator
```

Live smoke summary：

```text
trace: 02812-ops-query-20260608094520
pageTotalElements: 1
firstJobSource: ircs-smoke-worker
firstStatus: failed
summaryTotalLast24h: 1
residue: 0

MARK 1/4 worker_job_audit_events live table accepts fixture event
MARK 2/4 ops worker-job audit page query filters live fixture
MARK 3/4 ops worker-job audit summary returns live totals
MARK 4/4 live fixture cleanup leaves no worker_job_audit_events residue
```


## 2026-06-08 Behavior Parity 028-12 Notification Worker Live Audit Smoke

`028-12` 已完成 notification worker/job audit live queue smoke。该切片复用既有
`ircs-notification-worker` Deployment，不新增 Pod、Service、PVC、中间件或公共
HTTPRoute。dev 邮件仍保持 `APP_MAIL_ENABLED=false`，不会触发真实 SMTP。

Rollout evidence：

```text
deployment/ircs-notification-worker Ready 1/1

ircs-notification-worker imageID:
  harbor.mnnu.eu.org/ircs/ircs-notification-worker@sha256:742fe58a97b29e57eeae201da03ec63e331d4759284582d6d2351654bb7df6d4

ResourceQuota:
  pods=28/30, services=24/26
  requests.cpu=880m/4, requests.memory=4480Mi/8Gi
  limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
HTTPRoute in ircs-dev:
  No resources found in ircs-dev namespace.
RabbitMQ:
  q.notification.mail messages=0, consumers=1, ready=0, unacked=0
  q.notification.mail.dlq messages=0, consumers=0, ready=0, unacked=0
Fixture cleanup:
  worker_job_audit_events 02812 notification residue=0
```

Smoke summary：

```text
.\scripts\smoke\notification-worker-audit-02812-smoke.ps1
MARK 1/5 notification worker has q.notification.mail consumer
MARK 2/5 live smoke publishes MailMessageDTO fixture with Rabbit message_id
MARK 3/5 notification worker writes succeeded worker_job_audit_events with correlation_id
MARK 4/5 ops worker-job audit query exposes notification worker event
MARK 5/5 live smoke cleanup leaves no DB residue and mail queues empty
```

Implementation note：

```text
NotificationWorkerRabbitConfiguration registers a SimpleMessageConverter
allowed-list for com.prodigalgal.ircs.contracts.notification.*. This keeps
Java serialized MailMessageDTO consumption working without enabling global
SPRING_AMQP_DESERIALIZATION_TRUST_ALL.
```


## 2026-06-08 Behavior Parity 028-12 Content Service Handler Audit Live Smoke

`028-12` 已完成 content-service handler audit 与 live smoke。该切片复用既有
`ircs-content-service` Deployment 和 ClusterIP Service，只 rollout 已存在的 Deployment，
不新增 Pod、Service、PVC、中间件或公共 HTTPRoute。smoke 使用唯一
`X-Trace-Id=02812-content-audit-20260608113559` 调用只读
`/api/v1/actors/all` handler，验证 `request_audit_logs.request_source=ircs-content-service`
可查询并完成 fixture 清理。

Rollout evidence：

```text
deployment/ircs-content-service successfully rolled out
pod: ircs-content-service-58d98958c8-pbr6v Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-content-service@sha256:5557e950adbce042a9d51a15fe551ae9cefe86227be50b9e52ef976a68ade42a

ResourceQuota:
  pods=28/30, services=24/26
  requests.cpu=880m/4, requests.memory=4480Mi/8Gi
  limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
HTTPRoute in ircs-dev:
  No resources found in ircs-dev namespace.
Fixture cleanup:
  request_audit_logs content-service 02812 residue=0
  local content-service port-forward residue=none
```

Smoke summary：

```text
.\scripts\smoke\content-service-audit-02812-smoke.ps1
MARK 1/4 content-service pod is Ready
MARK 2/4 content-service live handler request returns success
MARK 3/4 content-service live audit row is queryable by trace_id
MARK 4/4 content-service live smoke cleanup leaves no DB residue
```


## 2026-06-08 Behavior Parity 028-12 Credential Service Handler Audit Live Smoke

`028-12` 已完成 credential-service handler audit、敏感字段边界测试与 live smoke。该切片
复用既有 `ircs-credential-service` Deployment 和 ClusterIP Service，只 rollout 已存在的
Deployment，不新增 Pod、Service、PVC、中间件或公共 HTTPRoute。smoke 使用唯一
`X-Trace-Id=02812-credential-audit-20260608115419` 调用安全只读
`/api/v1/credentials/templates?provider=TMDB` endpoint，避免读取或写入真实 credential
secret，并验证 `request_audit_logs.request_source=ircs-credential-service` 可查询和清理。

Rollout evidence：

```text
deployment/ircs-credential-service successfully rolled out
pod: ircs-credential-service-84449db48c-s57wq Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-credential-service@sha256:634f635076842a4a0b4518ef1ea5ca0beaee8980e80544f15c5eebb3ff750e96

ResourceQuota:
  pods=28/30, services=24/26
  requests.cpu=880m/4, requests.memory=4480Mi/8Gi
  limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
HTTPRoute in ircs-dev:
  No resources found in ircs-dev namespace.
Fixture cleanup:
  request_audit_logs credential-service 02812 residue=0
  local credential-service port-forward residue=none
```

Smoke summary：

```text
.\scripts\smoke\credential-service-audit-02812-smoke.ps1
MARK 1/4 credential-service pod is Ready
MARK 2/4 credential-service safe template request returns success
MARK 3/4 credential-service live audit row is queryable by trace_id
MARK 4/4 credential-service live smoke cleanup leaves no DB residue
```


## 2026-06-08 Behavior Parity 028-12 Identity Service Handler Audit Live Smoke

`028-12` 已完成 identity-service handler audit、query token 脱敏与 live smoke。该切片
复用既有 `ircs-identity-service` Deployment 和 ClusterIP Service，只 rollout 已存在的
Deployment，不新增 Pod、Service、PVC、中间件或公共 HTTPRoute。smoke 使用唯一
`X-Trace-Id=02812-identity-audit-20260608144010` 调用 public
`/api/v1/auth/pow?access_token=query-secret-token&redirect=/portal` endpoint，避免执行登录、
注册、改密或头像上传，并验证 `request_audit_logs.request_source=ircs-identity-service`
可查询、`access_token` 被写入为 `***`、fixture 可清理。

Rollout evidence：

```text
deployment/ircs-identity-service successfully rolled out
pod: ircs-identity-service-7f8c65d969-f9fbt Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-identity-service@sha256:09d4574336c51d54c7716e8d6697fad8a063e0f770c0caa74f1afa49ef9481c7

ResourceQuota:
  pods=28/30, services=24/26
  requests.cpu=880m/4, requests.memory=4480Mi/8Gi
  limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
HTTPRoute in ircs-dev:
  No resources found in ircs-dev namespace.
Fixture cleanup:
  request_audit_logs identity-service 02812 residue=0
  local identity-service port-forward residue=none
```

Smoke summary：

```text
.\scripts\smoke\identity-service-audit-02812-smoke.ps1
MARK 1/4 identity-service pod is Ready
MARK 2/4 identity-service PoW request returns success
MARK 3/4 identity-service live audit row redacts query token
MARK 4/4 identity-service live smoke cleanup leaves no DB residue
```


## 2026-06-08 Behavior Parity 028-12 Interaction Service Handler Audit Live Smoke

`028-12` 已完成 interaction-service handler audit、query token 脱敏与 live smoke。该切片
复用既有 `ircs-interaction-service` Deployment 和 ClusterIP Service，只 rollout 已存在的
Deployment，不新增 Pod、Service、PVC、中间件或公共 HTTPRoute。smoke 使用唯一
`X-Trace-Id=02812-interaction-audit-20260608145509` 调用 public
`/api/portal/feedback/wall?token=query-secret-token&page=0&size=1` endpoint，避免登录、
收藏、历史、留言或 admin reply 写入，并验证
`request_audit_logs.request_source=ircs-interaction-service` 可查询、`token` 被写入为
`***`、fixture 可清理。

Rollout evidence：

```text
deployment/ircs-interaction-service successfully rolled out
pod: ircs-interaction-service-5ff44bdf48-g2ggz Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-interaction-service@sha256:2cd5b1086233d618ac0354be4a1c0f982a5c75793670acfcd14ca451c0492cb0

ResourceQuota:
  pods=28/30, services=24/26
  requests.cpu=880m/4, requests.memory=4480Mi/8Gi
  limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
HTTPRoute in ircs-dev:
  No resources found in ircs-dev namespace.
Fixture cleanup:
  request_audit_logs interaction-service 02812 residue=0
  local interaction-service port-forward residue=none
```

Smoke summary：

```text
.\scripts\smoke\interaction-service-audit-02812-smoke.ps1
MARK 1/4 interaction-service pod is Ready
MARK 2/4 interaction-service feedback wall request returns success
MARK 3/4 interaction-service live audit row redacts query token
MARK 4/4 interaction-service live smoke cleanup leaves no DB residue
```


## 2026-06-08 Behavior Parity 028-12 Magnet Service Handler Audit Live Smoke

`028-12` 已完成 magnet-service handler audit、query token 脱敏与 live smoke。该切片复用
既有 `ircs-magnet-service` Deployment 和 ClusterIP Service，只 rollout 已存在的 Deployment，
不新增 Pod、Service、PVC、中间件或公共 HTTPRoute。smoke 使用唯一
`X-Trace-Id=02812-magnet-audit-20260608150827` 调用只读
`/api/v1/magnet-providers?token=query-secret-token&provider=all` endpoint，避免 provider
create/update、provider search 或 magnet link 写入，并验证
`request_audit_logs.request_source=ircs-magnet-service` 可查询、`token` 被写入为 `***`、
fixture 可清理。

Rollout evidence：

```text
deployment/ircs-magnet-service successfully rolled out
pod: ircs-magnet-service-569d445dd9-pmc7f Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-magnet-service@sha256:8adacd816cd0e71008924b554a9c444ace1878e6d1ed2197ff6edc2ec5ff1ad1

ResourceQuota:
  pods=28/30, services=24/26
  requests.cpu=880m/4, requests.memory=4480Mi/8Gi
  limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
HTTPRoute in ircs-dev:
  No resources found in ircs-dev namespace.
Fixture cleanup:
  request_audit_logs magnet-service 02812 residue=0
  local magnet-service port-forward residue=none
```

Smoke summary：

```text
.\scripts\smoke\magnet-service-audit-02812-smoke.ps1
MARK 1/4 magnet-service pod is Ready
MARK 2/4 magnet-service provider list request returns success
MARK 3/4 magnet-service live audit row redacts query token
MARK 4/4 magnet-service live smoke cleanup leaves no DB residue
```


## 2026-06-08 Behavior Parity 028-12 Storage Service Handler Audit Live Smoke

`028-12` 已完成 storage-service handler audit、query token 脱敏与 live smoke。该切片复用
既有 `ircs-storage-service` Deployment 和 ClusterIP Service，只 rollout 已存在的 Deployment，
不新增 Pod、Service、PVC、中间件或公共 HTTPRoute。smoke 使用唯一
`X-Trace-Id=02812-storage-audit-20260608152804` 调用只读
`/api/v1/cover-images?token=query-secret-token&size=1` endpoint，避免 upload、download、
delete、retry、sync-r2、avatar upload 或 queue publish，并验证
`request_audit_logs.request_source=ircs-storage-service` 可查询、`token` 被写入为 `***`、
fixture 可清理。

Rollout evidence：

```text
deployment/ircs-storage-service successfully rolled out
pod: ircs-storage-service-6c8788f87-jhg57 Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-storage-service@sha256:89408df252a2fb53ad6a939fae66427f78bbd8808791fa4afd941e1a6c487ff2

ResourceQuota:
  pods=28/30, services=24/26
  requests.cpu=880m/4, requests.memory=4480Mi/8Gi
  limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
HTTPRoute in ircs-dev:
  No resources found in ircs-dev namespace.
Fixture cleanup:
  request_audit_logs storage-service 02812 residue=0
  local storage-service port-forward residue=none
```

Smoke summary：

```text
.\scripts\smoke\storage-service-audit-02812-smoke.ps1
MARK 1/4 storage-service pod is Ready
MARK 2/4 storage-service cover image list request returns success
MARK 3/4 storage-service live audit row redacts query token
MARK 4/4 storage-service live smoke cleanup leaves no DB residue
```


## 2026-06-08 Behavior Parity 028-12 Search Service Handler Audit Live Smoke

`028-12` 已完成 search-service handler audit、query token 脱敏与 live smoke。该切片复用
既有 `ircs-search-service` Deployment 和 ClusterIP Service，只 rollout 已存在的 Deployment，
不新增 Pod、Service、PVC、中间件或公共 HTTPRoute。smoke 使用唯一
`X-Trace-Id=02812-search-audit-20260608154116` 调用只读
`/api/portal/search/suggest?token=query-secret-token&keyword=codex` endpoint，避免
recommendations fixture、search sync queue 投递或 ES 文档写入，并验证
`request_audit_logs.request_source=ircs-search-service` 可查询、`token` 被写入为 `***`、
fixture 可清理。

Rollout evidence：

```text
deployment/ircs-search-service successfully rolled out
pod: ircs-search-service-5477cf5d99-8wnhm Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-search-service@sha256:228bad735e35e0ac36d2303ac0bce67fe438a3598bbd4d460fd59f0ded5faefe

ResourceQuota:
  pods=28/30, services=24/26
  requests.cpu=880m/4, requests.memory=4480Mi/8Gi
  limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
HTTPRoute in ircs-dev:
  No resources found in ircs-dev namespace.
Fixture cleanup:
  request_audit_logs search-service 02812 residue=0
  local search-service port-forward residue=none
```

Smoke summary：

```text
.\scripts\smoke\search-service-audit-02812-smoke.ps1
MARK 1/4 search-service pod is Ready
MARK 2/4 search-service suggest request returns success
MARK 3/4 search-service live audit row redacts query token
MARK 4/4 search-service live smoke cleanup leaves no DB residue
```


## 2026-06-08 Behavior Parity 028-12 Portal Service Handler Audit Live Smoke

`028-12` 已完成 portal-service handler audit、query token 脱敏与 live smoke。该切片复用
既有 `ircs-portal-service` Deployment 和 ClusterIP Service，只 rollout 已存在的 Deployment，
不新增 Pod、Service、PVC、中间件或公共 HTTPRoute。smoke 使用唯一
`X-Trace-Id=02812-portal-audit-20260608155128` 调用只读
`/api/portal/metadata?token=query-secret-token&view=metadata` endpoint，避免 home/explore/detail
fixture、写入、缓存刷新或 queue publish，并验证
`request_audit_logs.request_source=ircs-portal-service` 可查询、`token` 被写入为 `***`、
fixture 可清理。

Rollout evidence：

```text
deployment/ircs-portal-service successfully rolled out
pod: ircs-portal-service-84978475cc-smxwg Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-portal-service@sha256:6d10a946fce61b6930806ee4958c8e51ccee7243c55af90f71a6521b99bc4dc2

ResourceQuota:
  pods=28/30, services=24/26
  requests.cpu=880m/4, requests.memory=4480Mi/8Gi
  limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
HTTPRoute in ircs-dev:
  No resources found in ircs-dev namespace.
Fixture cleanup:
  request_audit_logs portal-service 02812 residue=0
  local portal-service port-forward residue=none
```

Smoke summary：

```text
.\scripts\smoke\portal-service-audit-02812-smoke.ps1
MARK 1/4 portal-service pod is Ready
MARK 2/4 portal-service metadata request returns success
MARK 3/4 portal-service live audit row redacts query token
MARK 4/4 portal-service live smoke cleanup leaves no DB residue
```


## 2026-06-08 Behavior Parity 028-12 Task Service Handler Audit Live Smoke

`028-12` 已完成 task-service handler audit、query token 脱敏与 live smoke。该切片复用
既有 `ircs-task-service` Deployment 和 ClusterIP Service，只 rollout 已存在的 Deployment，
不新增 Pod、Service、PVC、中间件或公共 HTTPRoute。smoke 使用唯一
`X-Trace-Id=02812-task-audit-20260608160734` 调用只读
`/api/v1/collection-tasks?token=query-secret-token&page=0&size=1` endpoint，避免任务创建、
生命周期命令、cron scheduler、runner、scraper-service 调用或 task log 写入，并验证
`request_audit_logs.request_source=ircs-task-service` 可查询、`token` 被写入为 `***`、fixture
可清理。

Rollout evidence：

```text
deployment/ircs-task-service successfully rolled out
pod: ircs-task-service-84f784487c-rmn2q Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-task-service@sha256:e5d8f0d50a921e6d0f5ade027c089b877669d177977596a815aac5f4701eefb0

ResourceQuota:
  pods=28/30, services=24/26
  requests.cpu=880m/4, requests.memory=4480Mi/8Gi
  limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
HTTPRoute in ircs-dev:
  No resources found in ircs-dev namespace.
Fixture cleanup:
  request_audit_logs task-service 02812 residue=0
  local task-service port-forward residue=none
```

Smoke summary：

```text
.\scripts\smoke\task-service-audit-02812-smoke.ps1
MARK 1/4 task-service pod is Ready
MARK 2/4 task-service collection task list request returns success
MARK 3/4 task-service live audit row redacts query token
MARK 4/4 task-service live smoke cleanup leaves no DB residue
```


## 2026-06-08 Behavior Parity 028-12 Ops And Scraper Handler Audit Live Smoke

`028-12` 已完成 ops-service 与 scraper-service handler audit live smoke。两个切片均复用既有
Deployment 和 ClusterIP Service，只 rollout 已存在的 Deployment，不新增 Pod、Service、PVC、
中间件或公共 HTTPRoute。

Ops-service smoke 使用唯一 `X-Trace-Id=02812-ops-audit-20260608174902` 调用只读
`GET /api/v1/ops/worker-job-audit/summary?token=query-secret-token`，验证
`request_audit_logs.request_source=ircs-ops-service` 写入、query token 脱敏和 cleanup。
Scraper-service smoke 使用唯一 `X-Trace-Id=02812-scraper-audit-20260608175041` 调用
`GET /api/v1/scraper/manual/stream/{invalid-session}`，期望业务失败但仍写入 audit；该路径不创建
manual session，不触发 refetch、task execution、真实抓取或 RabbitMQ publish。

Rollout evidence：

```text
ircs-ops-service Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-ops-service@sha256:7ec8a32ef43fb6c6bcd4ad165deddfc5490ef3b3d6a01b11ff2d022c81e1c39a

ircs-scraper-service Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-scraper-service@sha256:d33617061c9e3deda010d2db1fba40ca1e6323da2bd113ca4bb19bf1b91e0c55

HTTPRoute in ircs-dev:
  No resources found in ircs-dev namespace.
ResourceQuota:
  pods=28/30, services=24/26
  requests.cpu=880m/4, requests.memory=4480Mi/8Gi
  limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
```

Smoke summary：

```text
.\scripts\smoke\ops-service-audit-02812-smoke.ps1
MARK 1/4 pod Ready
MARK 2/4 worker job audit summary request success
MARK 3/4 audit row redacts query token
MARK 4/4 cleanup leaves no DB residue

.\scripts\smoke\scraper-service-audit-02812-smoke.ps1 -ExecuteLive
MARK 1/4 scraper-service pod is Ready
MARK 2/4 invalid stream request fails without scraper execution
MARK 3/4 audit row redacts query token/password
MARK 4/4 cleanup leaves no DB residue
```

Post-check：

```text
request_audit_logs ops-service trace residue=0
request_audit_logs scraper-service trace residue=0
q.ingest.video/q.process.playlist_sync unchanged
local ops-service/scraper-service port-forward residue=none
```


