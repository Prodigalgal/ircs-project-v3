# 028 V1 到 V3 行为级 1:1 迁移父任务

状态：进行中

上游来源：

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/041-v1-v3-behavior-parity-gap-audit.md`
- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`

## 背景

`025` 已关闭 HTTP Controller 路由缺口，但 V3 仍存在多处 guarded/MVP 行为。父任务负责
继续推进行为级 1:1：每个已迁移接口不仅要能访问，还要和 V1 在权限、数据写入、异步
副作用、错误语义、审计可见性和运维可观察性上对齐。

## 目标

- 建立行为 parity 的任务队列和验收口径。
- 优先补齐 portal/admin 验证链路依赖的可观察性与媒体/外部集成行为。
- 每完成一个完整复现的接口或内部流程，记录 `MARK` 并回写迁移文档。
- 保持 `ircs-dev` 低资源和无公网 HTTPRoute。

## 非目标

- 不重新计算 HTTP route missing。
- 不强制本阶段完成物理数据库拆分。
- 不进行生产流量切换和压测。
- 不无条件执行破坏性维护任务。

## 输入

- V1 `ircs-project-v1` 已验证功能和内部实现。
- V3 当前微服务代码、BFF、K8S dev 清单。
- 现有 `request_audit_logs`、`failed_messages`、magnet、member、queue schema。
- portal/admin 现有验证入口。

## 输出

- 行为 parity 子任务。
- 对应代码实现、测试和 smoke 脚本。
- 迁移记录、报告、需求和任务状态回写。
- 每个行为完成项的 `MARK n/m`。

## 影响范围

包含：

- `services/ircs-portal-bff`
- `services/ircs-admin-bff`
- `services/ircs-ops-service`
- 后续子任务涉及的业务服务或 worker。
- `deploy/k8s/dev`
- `scripts/smoke`
- `docs`
- `tasks`

不包含：

- 生产命名空间。
- 公共 HTTPRoute。
- 物理数据库拆分强制落地。

## 依赖

- 当前 `ircs-dev` 业务服务 Ready。
- PostgreSQL、RabbitMQ、Valkey dev 中间件可用。
- Harbor ARM64 镜像推送可用。
- portal/admin BFF 可以通过 ClusterIP smoke 验证。

## 风险

- 行为 parity 比 HTTP route coverage 更容易出现隐性差异，需要按数据副作用验证。
- BFF audit 写入会增加少量数据库连接，需要将 Hikari pool 控制在低资源范围。
- 外部 provider、maintenance runner 和 queue consumer 可能触发真实副作用，必须分任务
  单独做 dev-safe 限制。

## 验收标准

- 所有子任务均有独立 task spec。
- 已完成子任务移动到 `tasks/done/`。
- 未完成项保留在本父任务或 backlog，不写成已完成。
- 本父任务最终关闭前，行为缺口表中所有强制项均有当前证据证明完成。

## 测试边界

每个子任务至少包含：

```text
本地 Gradle 测试
必要的全仓 build 或相关模块 build
K8S rollout status
ClusterIP smoke
数据副作用检查
HTTPRoute 和 ResourceQuota 后置检查
```

## 当前子任务

进行中：

```text
tasks/in-progress/028-10-production-governor-credential-pool-parity.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
tasks/in-progress/028-16-v1-v3-deep-parity-gap-audit-parent.md
```

已完成：

```text
tasks/done/028-01-bff-request-audit-collection-parity.md
tasks/done/028-02-identity-avatar-media-sync-parity.md
tasks/done/028-03-magnet-provider-search-parity.md
tasks/done/028-04-ops-maintenance-runner-parity.md
tasks/done/028-05-a-interaction-watch-progress-consumer-parity.md
tasks/done/028-05-b-storage-avatar-sync-consumer-parity.md
tasks/done/028-05-c-metadata-provider-fanout-parity.md
tasks/done/028-05-d-search-producer-matrix-parity.md
tasks/done/028-05-e-metadata-provider-worker-enrichment-parity.md
tasks/done/028-05-f-normalization-raw-relation-parity.md
tasks/done/028-05-g-metadata-pending-gather-parity.md
tasks/done/028-05-h-storage-image-download-parity.md
tasks/done/028-05-i-mail-notification-disabled-failure-parity.md
tasks/done/028-05-queue-consumer-deep-side-effect-parity-audit.md
tasks/done/028-06-real-provider-live-parity.md
tasks/done/028-08-normalization-deep-mapping-parity.md
tasks/done/028-09-search-producer-live-smoke-parity.md
tasks/done/028-11-per-service-audit-collection-parity.md
tasks/done/028-06-c-magnet-yts-bz-runner-contract.md
tasks/done/028-06-i-rt-provider-arm64-live-rollout-queue-parity.md
tasks/done/028-07-c-storage-service-r2-path-smoke.md
tasks/done/028-07-d-avatar-r2-live-smoke.md
tasks/done/028-07-e-dev-r2-bucket-policy-guard.md
tasks/done/028-07-f-explicit-v1-bucket-smoke-preflight.md
tasks/done/028-07-g-real-r2-explicit-v1-bucket-write-smoke.md
tasks/done/028-07-h-r2-cover-explicit-v1-bucket-chain-smoke.md
tasks/done/028-07-i-storage-service-r2-avatar-path-smoke.md
tasks/done/028-07-j-storage-r2-watchdog-recovery-smoke.md
tasks/done/028-07-r2-live-watchdog-parity.md
tasks/done/028-10-b-mail-credential-adapter-fake-sink.md
tasks/done/028-10-c-ops-runner-registry-dev-gate.md
tasks/done/028-10-d-notification-send-history-fake-sink.md
tasks/done/028-10-e-notification-send-history-schema-ops-query.md
tasks/done/028-10-f-smtp-staging-live-preflight.md
tasks/done/028-10-g-send-history-migrator-dev-deploy-smoke.md
tasks/done/028-10-h-notification-mail-send-history-dev-migration-ops-query.md
tasks/done/028-10-i-notification-smtp-staging-one-shot-live-smoke.md
tasks/done/028-10-j-notification-mail-send-history-retention-cleanup.md
tasks/done/028-10-k-notification-worker-harbor-push-retention-smoke.md
tasks/done/028-10-l-notification-smtp-v1-live-smoke.md
tasks/done/028-10-m-notification-worker-crashloop-ready-recovery.md
tasks/done/028-12-a-task-service-runner-audit.md
tasks/done/028-12-b-aggregation-scheduler-audit.md
tasks/done/028-15-jdk25-mockito-test-runtime.md
tasks/done/028-16-c2-system-config-cache-event-parity.md
```

后续 backlog：

```text
```

## 进度记录

2026-06-07：

- `028-01` 已完成 BFF request audit 采集行为 parity。
- portal/admin BFF 请求可写入 `request_audit_logs`，并可通过 `ops-service`
  `GET /api/v1/ops/request-audit` 查询验证。
- `MARK 2/2` 已记录，未新增 Pod、Service 或 HTTPRoute。
- `028-05` 已开始队列消费者深层副作用审计，当前只写文档和任务 spec，不与
  `028-02`、`028-03`、`028-04` 的服务代码写入重叠。
- `028-02` 已完成 identity avatar media persistence/sync parity，记录
  `MARK 2/2`，并在 K8S smoke 中验证 `/media/avatars/*` 可由 storage-service 服务。
- `028-03` 已完成 magnet provider search dev-safe parity，记录 `MARK 3/3`，
  已验证 non-skipped job、provider run 和 magnet link upsert。
- `028-04` 已完成 ops `search-reindex-unified` dev-safe runner parity，记录
  `MARK 3/3`，已验证 SSE runner 进度、search-service 消费和 Elasticsearch index。
- 集成 smoke `.\scripts\smoke\behavior-02802-02804-parity-smoke.ps1` 返回
  `MARK 8/8`，临时 DB/ES 数据清理为 0。
- `028-05-A` 已完成 interaction watch-progress consumer parity，记录 `MARK 4/4`，
  已验证 `q.interaction.watch_progress` 消费、`member_watch_histories` upsert、
  旧消息晚到保护和队列/DLQ 清理。
- `028-05-B` 已完成 storage avatar sync consumer parity，记录 `MARK 4/4`，
  已验证 `q.storage.avatar_sync` consumer、R2 disabled dev ack/no-op、R2 active unit path
  和 identity upload producer。
- `028-05-C` 已完成 metadata provider fan-out parity，已验证 Douban/TMDB/RT routing 和
  pending set 注册。
- `028-05-D` 已完成 raw/unified search producer matrix parity，记录 `MARK 2/2`，
  已验证 content image unlink 会通过 `DELETE -> INDEX` refresh 清空 ES `coverImageUrl`。
- `028-05-E` 已完成 metadata provider worker/result dev-safe parity，记录 `MARK 4/4`，
  已验证 Douban/RT listener 和 provider missing terminal result。
- `028-05-F` 已完成 normalization raw relation parity，记录 `MARK 4/4`，
  已验证 raw genre/language/area 提取、raw relation upsert、join relation 替换和 raw
  search sync 保持。
- `028-05-G` 已完成 metadata pending gather parity，记录 `MARK 4/4`，
  已验证 provider complete remaining、pending 未归零不 finalize、不发布 raw search sync、
  pending 归零清理 key 并结算，以及 missing video 清理 pending key。
- `028-05-H` 已完成 storage image download 本地实现、单元测试和 live smoke，记录
  `MARK 5/5`，已验证 `q.storage.image`、DLQ、DB/object residue cleanup。
- `028-05-I` 已完成 mail notification disabled/failure parity，记录 `MARK 4/4`，
  已验证 disabled ack/no-op、template failure 和 SMTP failure 均按 retry/DLQ 语义传播。
- `028-05` queue consumer deep side-effect parity 审计已完成并归档；第一阶段强制项
  已闭环，剩余生产级/外部集成/深层映射能力拆入 backlog。
- 集成测试：
  `.\gradlew.bat :services:ircs-metadata-worker:test :services:ircs-storage-service:test :services:ircs-notification-worker:test --rerun-tasks`
  返回 `BUILD SUCCESSFUL in 18s`。
- 后置检查：`kubectl -n ircs-dev get httproute` 无资源，ResourceQuota 仍为
  `pods=28/30`、`services=24/26`。
- `028-06` 已完成 provider live parity：TMDB direct/live queue、magnet YTS_BZ runner
  contract、Douban first slice/full queue parity、RT first slice/full queue parity 均已闭环。
  RT live queue smoke 记录 `MARK 6/6`，live metadata-worker digest 为
  `sha256:93db92be56a2a7dc0b85d84c7a43ecb8c8f854f5947f1bbd7a6ae2dafc34291a`，
  `externalRequestCount=1`，`APP_METADATA_RT_ENABLED=false` 已恢复，queue/DB/ES/port-forward
  清理完成。
- `028-08` 已进入进行中，完成 actor/director person relation 第一刀并记录 `MARK 4/4`；
  raw category、standard mapping 和 FAILED/backoff/watchdog 已逐项推进，当前剩余
  `q.normalize.video` live smoke、queue/DLQ cleanup 和 ES residue cleanup。
- `028-09` 已完成 normalization -> raw search sync 第一刀 live smoke，记录
  `MARK 5/5`。
- `028-09` 已完成 metadata result -> raw search sync 第二刀 live smoke，记录
  `MARK 6/6`，已验证 DB/ES fixture cleanup、`q.enrich.metadata.result`、
  `q.search.sync.raw`、DLQ 检查和 HTTPRoute 只读检查。
- `028-09` 已完成 aggregation -> unified search sync 第三刀 live smoke，记录
  `MARK 6/6`，已验证 aggregation scheduler 将 raw 推进到 `BOUND`、创建
  `unified_videos`/`raw_video_unified_video`，并通过 `q.search.sync.raw` 与
  `q.search.sync.unified` 刷新 `ircs_raw_video`/`ircs_unified_video`；DB residue=0|0，
  raw/unified search 队列和 DLQ 均为空，`ircs-dev` 仍无 HTTPRoute，ResourceQuota 仍为
  `pods=28/30`、`services=24/26`。
- `028-09` 已完成 old unified dirty lifecycle 第四刀 live smoke，记录 `MARK 6/6`；
  已通过 content-service HTTP `PUT /api/v1/raw-videos/{id}` 验证 raw critical update 将
  old unified 标记 `DIRTY`、删除 raw/unified 绑定、raw 回到 `PENDING` 并刷新
  `ircs_raw_video`；随后通过 `POST /api/v1/unified-videos/{id}/recalculate` 验证 old unified
  回到 `SYNCED` 并刷新 `ircs_unified_video sourceCount=0`。最终 DB residue=0|0，
  raw/unified search 队列和 DLQ 均为空，`ircs-dev` 仍无 HTTPRoute，ResourceQuota 仍为
  `pods=28/30`、`services=24/26`。`028-09` 已移动到 `tasks/done/`。
- `028-11` 已完成 config-service handler audit 第一刀，记录 `MARK 3/3`，已验证
  `request_source` schema、config-service 审计写入、ops-service `requestSource` 查询和
  `ircs-dev` 无 residue；全服务铺开与 worker/job audit 已拆入 `028-12`。
- `028-07` 已进入进行中，完成 storage R2 watchdog/recovery 第一刀，记录
  `MARK 4/4`，并完成 credential-only R2 live object smoke，记录 `MARK 5/5`；已验证
  V1 导入的 R2 credential 可对 `R2_BUCKET_NAME=ircs` 执行 put/head/delete cleanup。
  当前 dev ConfigMap `R2_BUCKET_NAME=ircs-dev` 与 V1 `ircs` 漂移，导致默认 bucket live PUT
  返回 404；storage-service ARM64 镜像已 rollout 到 `ircs-dev`，当前
  `APP_STORAGE_R2_ENABLED=false`，无公共 HTTPRoute，ResourceQuota 仍为
  `pods=28/30`、`services=24/26`。storage-service path smoke 仍需先修正 bucket 或显式覆盖。
- `028-12` 已进入进行中，完成 catalog-service handler audit 第一刀，记录
  `MARK 2/2`，已通过本地 H2 单测验证 `request_source=ircs-catalog-service` 和 actuator
  噪音排除；尚未 rollout，worker/job audit 仍未闭环。
- `028-08` 已完成 raw category / `data_source_category_id` 第二刀，记录 `MARK 4/4`，
  已验证 raw metadata 提取、data source fallback、`raw_category` upsert 不覆盖
  `category_id`，以及 `raw_videos.data_source_category_id` 更新。
- `028-08` 已完成 standard mapping fill 第三刀，记录 `MARK 4/4`，已验证
  `raw_genres/raw_languages/raw_areas/raw_category` 在精确命中已有标准字典时回填标准外键，
  且不覆盖已有人工映射；FAILED/backoff、watchdog 和 `q.normalize.video` live smoke
  仍未闭环。
- `028-08` 已完成 normalization retry watchdog 第四刀，记录 `MARK 4/4`，已验证
  due `FAILED` 和 stale `PENDING` 会小批量重投 `q.normalize.video`，并跳过
  `READY`、`PERMANENT_FAILURE` 与未来 retry 记录；`q.normalize.video` live smoke、
  queue/DLQ cleanup 和 ES residue cleanup 仍未闭环。
- `028-08` 已完成 normalization deep mapping live smoke，记录 `MARK 6/6`；已验证
  `q.normalize.video` 真实消费后写入 actor/director、raw genre/language/area、standard
  mapping、raw category 和 `raw_videos.data_source_category_id`，并由 search-service
  刷新 `ircs_raw_video`。fixture trace `02808-normalization-deep-20260608103455`
  清理后 DB/ES residue=0，队列/DLQ 均为空；`ircs-normalization-worker` ARM64 imageID 为
  `harbor.mnnu.eu.org/ircs/ircs-normalization-worker@sha256:d0e35af46a0bd8aa0ca60e4db2c973a90824b7e84783cd2f5c6db501d0f043fe`。
- `028-10` 已进入进行中，完成 production governor / MAIL credential pool 第一刀，
  记录 `MARK 3/3`；本轮仅固化边界和 credential-service MAIL pool guard，不执行真实 SMTP、
  不 rollout、不新增 HTTPRoute。后续已完成 MAIL fake sink、ops runner registry、send history
  schema/dev DB/ops query、retention cleanup、notification-worker CrashLoop Ready 恢复和
  V1 SMTP one-shot 真实发送；当前仅剩高风险 maintenance runner 仍未闭环。
- `028-12` 已完成 worker/job audit 第一刀，记录 `MARK 3/3`，新增独立
  `worker_job_audit_events` schema 和 common writer，notification-worker `MailConsumer`
  已在成功/失败路径写入 queue consumer audit 事件，失败路径继续抛原异常给 RabbitMQ retry；
  notification worker live queue smoke 和更多 worker/scheduler/runner 接入仍未闭环。
- `028-12` 已完成 worker/job audit ops 查询 API，记录 `MARK 3/3`，新增
  `GET /api/v1/ops/worker-job-audit` 与 `/summary`，已通过本地 H2 SQL 测试验证
  source/status/correlation/time 过滤和 24 小时摘要；live smoke 和更多
  worker/scheduler/runner 接入仍未闭环。
- `028-12` worker/job audit ops 查询 API 已 rollout 到 `ircs-dev` 并完成 live query
  smoke，记录 `MARK 4/4`；migrator 已应用 `worker_job_audit_events` live schema，
  fixture trace `02812-ops-query-20260608094520` 通过 endpoint 查到 1 条并清理为
  residue=0；后置检查仍无 HTTPRoute，ResourceQuota 为 `pods=28/30`、`services=24/26`。
- `028-12` notification worker/job audit live queue smoke 已完成，记录 `MARK 5/5`；
  已验证 `q.notification.mail` 真实消费 Java-serialized `MailMessageDTO`，disabled-mail
  dev path ack 后写入 `worker_job_audit_events`，并由 ops 查询 API 读出。最终
  `trace=02812-notification-audit-20260608101907` residue=0，mail queue/DLQ 均为空；
  `ircs-notification-worker` ARM64 imageID 为
  `harbor.mnnu.eu.org/ircs/ircs-notification-worker@sha256:742fe58a97b29e57eeae201da03ec63e331d4759284582d6d2351654bb7df6d4`。
- `028-12` content-service handler audit 已完成，记录本地 `MARK 2/2` 与 live `MARK 4/4`；
  `ircs-content-service` 已接入 `ServiceRequestAuditFilter` 并 rollout 到 `ircs-dev`，
  通过 `trace=02812-content-audit-20260608113559` 验证 live handler 请求写入
  `request_audit_logs.request_source=ircs-content-service`，fixture residue=0；
  `ircs-content-service` ARM64 imageID 为
  `harbor.mnnu.eu.org/ircs/ircs-content-service@sha256:5557e950adbce042a9d51a15fe551ae9cefe86227be50b9e52ef976a68ade42a`，
  后置检查仍无 HTTPRoute，ResourceQuota 为 `pods=28/30`、`services=24/26`。
- `028-12` credential-service handler audit 已完成，记录本地 `MARK 3/3` 与 live
  `MARK 4/4`；`ircs-credential-service` 已接入 `ServiceRequestAuditFilter` 并 rollout
  到 `ircs-dev`，本地测试证明 request body、Authorization、Cookie 和 internal token
  不进入 audit 表；通过 `trace=02812-credential-audit-20260608115419` 验证安全 template
  endpoint live 请求写入 `request_audit_logs.request_source=ircs-credential-service`，
  fixture residue=0；`ircs-credential-service` ARM64 imageID 为
  `harbor.mnnu.eu.org/ircs/ircs-credential-service@sha256:634f635076842a4a0b4518ef1ea5ca0beaee8980e80544f15c5eebb3ff750e96`，
  后置检查仍无 HTTPRoute，ResourceQuota 为 `pods=28/30`、`services=24/26`。
- `028-12` identity-service handler audit 已完成，记录本地 `MARK 4/4` 与 live
  `MARK 4/4`；`ircs-identity-service` 已接入 `ServiceRequestAuditFilter` 并 rollout
  到 `ircs-dev`，shared service/BFF audit 已统一脱敏敏感 query 参数，本地测试证明
  request body、Authorization、Cookie、query token 和 response token 不进入 audit 表；
  通过 `trace=02812-identity-audit-20260608144010` 验证 public PoW endpoint live 请求写入
  `request_audit_logs.request_source=ircs-identity-service` 且 `access_token` 被脱敏为
  `***`，fixture residue=0；`ircs-identity-service` ARM64 imageID 为
  `harbor.mnnu.eu.org/ircs/ircs-identity-service@sha256:09d4574336c51d54c7716e8d6697fad8a063e0f770c0caa74f1afa49ef9481c7`，
  后置检查仍无 HTTPRoute，ResourceQuota 为 `pods=28/30`、`services=24/26`。
- `028-12` interaction-service handler audit 已完成，记录本地 `MARK 4/4` 与 live
  `MARK 4/4`；`ircs-interaction-service` 已接入 `ServiceRequestAuditFilter` 并 rollout
  到 `ircs-dev`，本地测试证明 feedback body、Authorization、Cookie、query token 和
  response content 不进入 audit 表；通过 `trace=02812-interaction-audit-20260608145509`
  验证 public feedback wall live 请求写入
  `request_audit_logs.request_source=ircs-interaction-service` 且 `token` 被脱敏为 `***`，
  fixture residue=0；`ircs-interaction-service` ARM64 imageID 为
  `harbor.mnnu.eu.org/ircs/ircs-interaction-service@sha256:2cd5b1086233d618ac0354be4a1c0f982a5c75793670acfcd14ca451c0492cb0`，
  后置检查仍无 HTTPRoute，ResourceQuota 为 `pods=28/30`、`services=24/26`。
- `028-12` magnet-service handler audit 已完成，记录本地 `MARK 4/4` 与 live
  `MARK 4/4`；`ircs-magnet-service` 已接入 `ServiceRequestAuditFilter` 并 rollout 到
  `ircs-dev`，本地测试证明 provider write body、Authorization、Cookie、query token 和
  response content 不进入 audit 表；通过 `trace=02812-magnet-audit-20260608150827`
  验证只读 provider list live 请求写入
  `request_audit_logs.request_source=ircs-magnet-service` 且 `token` 被脱敏为 `***`，
  fixture residue=0；`ircs-magnet-service` ARM64 imageID 为
  `harbor.mnnu.eu.org/ircs/ircs-magnet-service@sha256:8adacd816cd0e71008924b554a9c444ace1878e6d1ed2197ff6edc2ec5ff1ad1`，
  后置检查仍无 HTTPRoute，ResourceQuota 为 `pods=28/30`、`services=24/26`。
- `028-12` storage-service handler audit 已完成，记录本地 `MARK 4/4` 与 live
  `MARK 4/4`；`ircs-storage-service` 已接入 `ServiceRequestAuditFilter` 并 rollout 到
  `ircs-dev`，本地测试证明 cover image fetch body、Authorization、Cookie、query token 和
  response content 不进入 audit 表；通过 `trace=02812-storage-audit-20260608152804`
  验证只读 cover image list live 请求写入
  `request_audit_logs.request_source=ircs-storage-service` 且 `token` 被脱敏为 `***`，
  fixture residue=0；`ircs-storage-service` ARM64 imageID 为
  `harbor.mnnu.eu.org/ircs/ircs-storage-service@sha256:89408df252a2fb53ad6a939fae66427f78bbd8808791fa4afd941e1a6c487ff2`，
  后置检查仍无 HTTPRoute，ResourceQuota 为 `pods=28/30`、`services=24/26`。
- `028-12` search-service handler audit 已完成，记录本地 `MARK 4/4` 与 live
  `MARK 4/4`；`ircs-search-service` 已接入 `ServiceRequestAuditFilter` 并 rollout 到
  `ircs-dev`，本地测试证明 Authorization、Cookie、query token 和 response content 不进入
  audit 表；通过 `trace=02812-search-audit-20260608154116` 验证只读 suggest live 请求写入
  `request_audit_logs.request_source=ircs-search-service` 且 `token` 被脱敏为 `***`，
  fixture residue=0；`ircs-search-service` ARM64 imageID 为
  `harbor.mnnu.eu.org/ircs/ircs-search-service@sha256:228bad735e35e0ac36d2303ac0bce67fe438a3598bbd4d460fd59f0ded5faefe`，
  后置检查仍无 HTTPRoute，ResourceQuota 为 `pods=28/30`、`services=24/26`。
- `028-12` portal-service handler audit 已完成，记录本地 `MARK 4/4` 与 live
  `MARK 4/4`；`ircs-portal-service` 已接入 `ServiceRequestAuditFilter` 并 rollout 到
  `ircs-dev`，本地测试证明 Authorization、Cookie、query token 和 response content 不进入
  audit 表；通过 `trace=02812-portal-audit-20260608155128` 验证只读 metadata live 请求写入
  `request_audit_logs.request_source=ircs-portal-service` 且 `token` 被脱敏为 `***`，
  fixture residue=0；`ircs-portal-service` ARM64 imageID 为
  `harbor.mnnu.eu.org/ircs/ircs-portal-service@sha256:6d10a946fce61b6930806ee4958c8e51ccee7243c55af90f71a6521b99bc4dc2`，
  后置检查仍无 HTTPRoute，ResourceQuota 为 `pods=28/30`、`services=24/26`。
- `028-12` task-service handler audit 已完成，记录本地 `MARK 4/4` 与 live
  `MARK 4/4`；`ircs-task-service` 已接入 `ServiceRequestAuditFilter` 并 rollout 到
  `ircs-dev`，本地测试证明 Authorization、Cookie、query token/password 和 response content
  不进入 audit 表；通过 `trace=02812-task-audit-20260608160734` 验证只读 collection task list
  live 请求写入 `request_audit_logs.request_source=ircs-task-service` 且 `token` 被脱敏为
  `***`，fixture residue=0；`ircs-task-service` ARM64 imageID 为
  `harbor.mnnu.eu.org/ircs/ircs-task-service@sha256:e5d8f0d50a921e6d0f5ade027c089b877669d177977596a815aac5f4701eefb0`，
  后置检查仍无 HTTPRoute，ResourceQuota 为 `pods=28/30`、`services=24/26`。
- `028-12` ops-service handler audit 已完成，记录本地 `MARK 5/5` 与 live `MARK 4/4`；
  `ircs-ops-service` 已接入 `ServiceRequestAuditFilter` 并 rollout 到 `ircs-dev`，本地测试证明
  Authorization、Cookie、query token/password 和 response content 不进入 audit 表，并且
  `/api/v1/ops/request-audit` 不产生 self-audit 噪音；通过
  `trace=02812-ops-audit-20260608174902` 验证只读 worker-job-audit summary live 请求写入
  `request_audit_logs.request_source=ircs-ops-service` 且 `token` 被脱敏为 `***`，fixture residue=0；
  `ircs-ops-service` ARM64 imageID 为
  `harbor.mnnu.eu.org/ircs/ircs-ops-service@sha256:7ec8a32ef43fb6c6bcd4ad165deddfc5490ef3b3d6a01b11ff2d022c81e1c39a`。
- `028-12` scraper-service handler audit 已完成，记录本地 `MARK 4/4` 与 live `MARK 4/4`；
  `ircs-scraper-service` 已接入 `ServiceRequestAuditFilter` 并 rollout 到 `ircs-dev`，本地测试证明
  Authorization、Cookie、query token/password、request body 和 response content 不进入 audit 表；
  live smoke 使用 invalid stream 请求，期望业务失败但仍写入 audit，避免创建 manual session、真实抓取
  或 RabbitMQ publish；`trace=02812-scraper-audit-20260608175041` 清理后 fixture residue=0；
  `ircs-scraper-service` ARM64 imageID 为
  `harbor.mnnu.eu.org/ircs/ircs-scraper-service@sha256:d33617061c9e3deda010d2db1fba40ca1e6323da2bd113ca4bb19bf1b91e0c55`。
- `028-06-C` 已完成 magnet YTS_BZ runner contract，记录 `MARK 5/5`；默认 no-network
  fixture gate，YTS_BZ runner 仅在显式开启且 allowlist 命中时支持 IMDb JSON contract。
- `028-07-C` 已完成 storage-service R2 path live smoke，记录 `MARK 6/6`；smoke 临时启用
  R2 runtime、禁用 watchdog scan、验证 cover fixture object lifecycle，并恢复
  `APP_STORAGE_R2_ENABLED=false` 和 ConfigMap `valueFrom`。
- `028-10-B` 已完成 MAIL credential adapter + fake/sink，记录 `MARK 5/5`；notification-worker
  可通过 credential-service 租用 MAIL credential，fake/sink 不执行真实 SMTP。
- `028-10-C` 已完成 ops runner registry/dev gate，记录 `MARK 5/5`；高风险 runner 在 dev
  稳定 refused，`search-reindex-unified` 继续保持 capped dev-safe runner。
- 本轮主线程复验通过：
  `.\gradlew.bat :services:ircs-magnet-service:test :services:ircs-notification-worker:test :services:ircs-credential-service:test :services:ircs-ops-service:test :services:ircs-storage-service:test --rerun-tasks`
  返回 `BUILD SUCCESSFUL in 44s`。
- `028-07-C` live smoke 复验通过：
  `.\scripts\smoke\storage-service-r2-path-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -ConfirmStorageServiceR2Write`
  返回 `SUCCESS/STORAGE_SERVICE_R2_PATH_SMOKE_OK`，后置检查确认 `cover_images` fixture
  residue=0、临时 Secret 不存在、无 HTTPRoute、ResourceQuota 仍为 `pods=28/30`、
  `services=24/26`。
- 本轮合并验证通过：
  `.\gradlew.bat :services:ircs-normalization-worker:test :services:ircs-credential-service:test :shared:ircs-common:test :services:ircs-notification-worker:test :platform:ircs-migrator:processResources --rerun-tasks`
  返回 `BUILD SUCCESSFUL in 16s`；`scripts/smoke/search-producer-metadata-result-02809-smoke.ps1`
  PowerShell parse check 返回 `syntax ok`；`ircs-dev` 仍无 HTTPRoute，ResourceQuota 仍为
  `pods=28/30`、`services=24/26`。
- 本轮合并验证通过：
  `.\gradlew.bat :services:ircs-storage-service:test :services:ircs-normalization-worker:test :services:ircs-catalog-service:test --rerun-tasks`
  返回 `BUILD SUCCESSFUL in 19s`；`metadata-provider-live-queue-02806-smoke.ps1`
  PowerShell parse check 返回 `syntax ok`；`ircs-dev` 仍无 HTTPRoute，ResourceQuota 仍为
  `pods=28/30`、`services=24/26`。
- `028-07-D` 已完成 avatar R2 live smoke，记录 `MARK 7/7`；已验证
  identity upload -> `q.storage.avatar_sync` -> storage-service R2 upload ->
  `members.avatar_url` R2 URL 回写 -> local avatar cleanup，并确认临时 Secret、
  fixture member、queue/DLQ、HTTPRoute 和 ResourceQuota 后置状态干净。
- `028-12-A` 已完成 task-service runner/watchdog/startup recovery worker-job audit，
  记录 `MARK 5/5`；本地测试
  `.\gradlew.bat :services:ircs-task-service:test --rerun-tasks` 返回
  `BUILD SUCCESSFUL`。
- `028-12-B` 已完成 aggregation-worker scheduler worker-job audit，记录
  `MARK 5/5`；本地测试
  `.\gradlew.bat :services:ircs-aggregation-worker:test --rerun-tasks` 返回
  `BUILD SUCCESSFUL`。
- `028-15` 已完成 JDK25 Mockito inline agent 测试运行时修正，所有 subproject Test
  通过 `-javaagent` 显式加载 `mockito-core` premain，组合测试
  `.\gradlew.bat --no-daemon :services:ircs-identity-service:test :services:ircs-storage-service:test :services:ircs-task-service:test :services:ircs-aggregation-worker:test --rerun-tasks`
  返回 `BUILD SUCCESSFUL`，并清理历史 `hs_err_pid*.log`。
- 2026-06-08 用户指出 HanLP/Tika 之外可能仍遗漏 V1 功能和库细节，后续不只补单个依赖，
  需要新增系统性差异审计，逐项核对 V1 dependencies、initializers、provider、
  scheduler、consumer、parser/normalizer、security validator、config cache 和外部 SDK。

2026-06-10 本轮 docs/tasks 收敛校准：

- 当前策略固定为“先实现补齐，后续统一测试”。本父任务不得把 implementation-first closure
  误写成生产闭环；只有已有 live smoke 或后续 `UTB-02816-P1P2-*` 实际执行通过的项目才算 live closed。
- `028-16-F` 已完成本轮 P1/P2 入账：8 个 backlog task spec 已创建，并写入
  `UTB-02816-P1P2-01/02/03`。这些 backlog 是后续可分配、可验收边界，不代表 backlog
  功能全部完成。
- `028-16-C1-S2c` 已完成实现：V1 full countries/languages preset、normalization
  SystemConfig TTL fallback、resolver/真实资源站 live smoke prep 已落地；resolver/player/真实资源站
  live smoke 和完整 suite 后置。
- `028-16-C3-S8/S9` 已完成 shared outbound adoption：identity avatar multipart internal caller
  与 admin/portal BFF streaming transport 已有 focused no-network tests；live 门户、组合测试和
  circuit ENV/K8S matrix 后置。
- `028-16-E` 已完成 implementation-first closure，`MARK 5/6` partial；真实 LLM 调用、K8S
  dry-run smoke 和 staging-only live 单条 fixture 后置。
- `028-12-C` 已完成 metadata/normalization/storage worker audit implementation-first closure，
  focused tests 通过；live queue smoke、ops query fixture cleanup 和资源后置检查并入
  `UTB-02816-P1P2-03`。
- `028-16-F2/B15` 已完成 aggregation local closure：结构化 search sync、malformed payload
  reject、pipeline failure raw PENDING/unified DIRTY、`recalculateUnified()` 全成功才 SYNCED；
  focused aggregation/search tests 通过，真实 R2/multi-context/outbox stats 后置到
  `UTB-02816-P1P2-01`。
- `028-19` 已准备 production-like smoke orchestrator plan-only 入口；统一测试执行尚未开始。
- 最新估算：V1/V3 parity 总进度约 `92%`，开发 MVP 可用度约 `95%`，生产就绪度约 `76%`。

## 剩余拆分候选

```text
028-10 production governor and credential pool parity: send history/retention/V1 SMTP one-shot/notification maintenance runner safety gate closed; remaining only other high-risk runner / production-like governor expansion
028-12 service audit rollout and worker job audit
028-16 V1/V3 功能与库细节差异审计: C1 first closure and C3-S6 circuit breaker first slice closed; C1/C3 parent tasks remain in-progress
```

2026-06-09 本轮 docs/tasks 收敛：

- 新增 `docs/reports/093-v1-v3-parity-progress-ledger.md` 作为最新总账。
- `028-07` 父任务已确认完成；cover path、avatar path、watchdog/recovery 已分别记录真实
  R2 写入、删除、DB/queue/local/secret/HTTPRoute/resource quota 清理证据。
- `028-06` 父任务已关闭；Douban 与 RT provider 均完成 first slice/full queue parity，RT
  live rollout digest `sha256:93db92be56a2a7dc0b85d84c7a43ecb8c8f854f5947f1bbd7a6ae2dafc34291a`，
  full queue smoke `MARK 6/6`，外网请求数为 1，gate/queue/DB/ES/port-forward 清理通过。
- `028-10-H` 已完成 send history dev DB migration 与 ops query live 闭环；`028-10-I` 已完成
  one-shot safety gate 验证且真实 SMTP 未发送；`028-10-J/K` 已完成 retention cleanup dev DB
  smoke，Harbor push 阻塞解除，DB/K8S fixture 无残留。
- `028-10-M` 已确认 notification-worker 从
  `CredentialServiceMailCredentialRepository: No default constructor found` CrashLoop 恢复到
  Ready 1/1、pod Running、restarts=0，并已被后续 SMTP smoke 覆盖。
- `028-10-L` 已完成 V1 SMTP one-shot：trace `02810l-smtp-smoke-20260609222338`，
  真实发送 1 封且用户确认收到，Rabbit `routed=true`，DB `status=sent`，ops endpoint
  `status=SENT`，recipient 脱敏为 `HuaWai System <hu***@gmail.com>`；开关恢复，
  deployment ready 1/1，无 Job/Pod/port-forward 残留。
- `028-16-C2` 已完成：C2-S17/C2-S18/C2-S19/C2-S20 已解除 content listener、
  config publisher 与 content DB fallback read probe 阻塞；C2-S21 已决策并最小验证
  Rabbit outage / missed-message 下采用 event evict + short TTL read-through 兜底。
- `028-10-N` 已完成 notification maintenance runner safety gate：默认关闭、enabled 后默认
  dry-run、真实删除必须 `DRY_RUN=false` 且 `EXECUTE_ENABLED=true`，写
  `worker_job_audit_events`，dev smoke PASS，`sent=false`、
  `mailQueuePublished=false`、`finalSmokeResidue=0`，image digest
  `sha256:4cff0405ff615adfd29fe9847518208a9320b0f3522afa0021ceaeef55ada6cc`。
- `028-16-C1` first closure 已完成：shared auto-start gate 可观察模型已落地，
  normalization watchdog、storage R2 watchdog、aggregation scheduler live gate 均为
  `false`，三个 Deployment `readyReplicas=1`。C1-S2c 已完成 cache warmup/TTL/evict
  first closure、catalog countries/languages 全量 preset 和 resolver/真实资源站 dry-run prep。
  C1 父任务仍不完全关闭，剩余 resolver/真实资源站 live smoke，以及如需 V1 式
  `RabbitListenerEndpointRegistry.start()` 自动放行。
- `028-16-C3-S6` 已完成 shared outbound HTTP circuit breaker first slice；
  `InternalContentClients#refetchRawVideo` 已采用 shared `INTERNAL_SERVICE`，focused
  与组合 tests 全过，live smoke 未执行。`028-16-C3-S7` 随后完成 task-service POST JSON
  adoption。C3 父任务仍进行中，剩余 identity multipart、storage strict shared adapter、
  BFF transport 等 adoption。
- `028-16-D3` 已完成 Tika MIME / file parsing first closure：V1 使用点对照完成，确认
  storage `Tika.detect(byte[])` 与 scraper `UniversalEncodingDetector` 为实际源码调用点，
  未发现 V1 通用 PDF/Office/text extraction parser 调用；V3 storage/scraper Tika 依赖保持
  service-scoped minimal，新增 focused tests 与 `scripts/smoke/tika-file-parsing-02816d-smoke.ps1`。
  本地小 fixture create/test/cleanup smoke `MARK 6/6`，组合回归
  `:services:ircs-storage-service:test :services:ircs-scraper-service:test :shared:ircs-common:test`
  通过。2026-06-10 D4/F3 已补 detail charset、pagination 输出、Big5/Shift_JIS/BOM/invalid
  bytes fixture 和 smoke prep；D 后续只剩真实 provider 低资源 live charset smoke 与完整统一测试。
- 整体 goal 仍未完成；当前 V1/V3 parity 总进度估算约 `90%`，开发 MVP 可用度约
  `94%`，生产就绪度约 `74%`。
