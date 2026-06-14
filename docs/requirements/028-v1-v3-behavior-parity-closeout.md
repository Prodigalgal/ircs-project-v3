# 028 V1 到 V3 行为级 1:1 迁移闭环需求

状态：进行中

更新时间：2026-06-09

## 背景

`025` 已经把 V1 Controller HTTP 路由覆盖缺口推进到 `missing=0`，portal/admin BFF
也已经拆分部署。这个结果只能证明接口入口可访问，不证明 V1 已验证功能在 V3 中完成
行为级 1:1 复现。

当前目标转向行为深度迁移：对每个已经迁移的 HTTP 接口和内部流程，继续核对 V1 的业务
逻辑、数据副作用、异步任务、外部 provider、审计采集、媒体存储和错误语义，直到 portal
和 admin 能持续验证 V1 功能已经无损迁移到 V3 微服务。

## 目标

- 保持 V1/V3 HTTP Controller 覆盖 `missing=0`。
- 为每个已迁移接口补充行为 parity 标记，区分 `HTTP contract`、`data side effect`、
  `async side effect`、`external integration` 和 `error semantics`。
- 补齐当前已知 guarded/MVP 行为缺口：
  - BFF/request audit 采集。已由 `028-01` 完成 portal/admin BFF 入口采集。
  - ops-service maintenance runner。
  - magnet-service 外部 provider search。
  - identity/avatar 实体媒体落地和 storage sync。
  - interaction、normalization、metadata、search 相关 queue consumer 深层副作用。
- 继续保持 `ircs-dev` 低资源、无公共 HTTPRoute、无命名空间和流量冲突。
- 每完成一个 V1 接口或内部流程的完整复现，在迁移文档和任务中标记一次。

## 非目标

- 不把已经完成的 HTTP 路由覆盖重新计算为行为完成。
- 不在本需求内追求生产级高可用、压测或公网流量开放。
- 不强制一次性完成严格物理数据库拆分；当前仍允许共享 PostgreSQL 作为迁移桥。
- 不引入新增中间件或高资源服务，除非后续 task spec 单独说明并验证配额。

## 当前基线

来自 `025` closeout 的当前状态：

```text
V1 backend Controller routes: 203
V3 service Controller routes: 226
Direct missing routes:         0
```

说明：`226` 包含 post-025 新增的 2 条 worker/job audit ops 查询端点；V1 direct
missing 仍为 0。

行为级当前不能视为完成的领域：

```text
BFF request audit collection   已完成
per-service audit collection   部分完成：config-service、catalog-service、content-service、credential-service、identity-service、interaction-service、magnet-service、storage-service、search-service、portal-service、task-service、ops-service、scraper-service handler audit 已完成；worker/job audit 已有 notification live，其他 worker/scheduler/runner 后续
ops maintenance real runner    部分完成：search-reindex-unified dev-safe runner 已完成
magnet external provider search部分完成：dev-safe provider run/link 已完成
avatar media persistence/sync  部分完成：本地媒体、/media 服务、SYNC_AVATAR dev-safe consumer 和 R2 watchdog 第一刀已完成；真实 R2 live 后续
queue consumer deep parity     第一阶段完成：WATCH_PROGRESS、SYNC_AVATAR、metadata fan-out、metadata provider worker dev-safe、metadata pending gather、image unlink search refresh、normalization raw relation/deep mapping live smoke、image download live smoke、mail disabled/failure 已完成；真实 provider/R2 等后续 backlog
physical data ownership split  非本阶段强制目标
```

## 第一阶段策略

第一阶段优先选择能直接支撑 portal/admin 验证闭环、资源消耗低、风险可控的行为缺口：

1. `028-01` BFF request audit 采集：已完成。portal-bff/admin-bff 可写入
   `request_audit_logs`，并由现有 `ops-service` 查询接口验证。
2. `028-02` identity avatar 媒体持久化与 storage sync：已完成。头像 URL 已对应
   storage-service 可服务资源。
3. `028-03` magnet unified search 外部 provider dev-safe runner：已完成。从 SKIPPED
   job 推进到可控 provider run/link 写入。
4. `028-04` ops maintenance runner 行为复现：已完成第一条低风险 runner，
   `search-reindex-unified` 可投递 search sync 并由 search-service 索引。
5. `028-05` queue consumer 深层副作用审计：逐项确认 interaction、normalization、
   metadata、search 消费链是否和 V1 副作用一致。
   - `028-05-A` interaction watch progress consumer：已完成。
   - `028-05-B` storage avatar sync consumer：已完成。
   - `028-05-C` metadata provider fan-out routing：已完成。
   - `028-05-D` raw/unified search producer matrix：已完成。
   - `028-05-E` metadata provider worker and enrichment result dev-safe parity：已完成。
   - `028-05-F` normalization raw relation parity：已完成。
   - `028-05-G` metadata pending gather parity：已完成。
   - `028-05-H` image download true download/failure parity：已完成。
   - `028-05-I` mail notification disabled/failure parity：已完成。

## 验收标准

- 每个子任务都有明确上游来源、范围、非目标、输入输出、风险和测试边界。
- 每个行为缺口至少具备本地单元测试或集成测试。
- 涉及 K8S 的任务必须完成 ClusterIP smoke，并记录：
  - Deployment rollout。
  - HTTP/BFF smoke。
  - 数据副作用检查。
  - `kubectl -n ircs-dev get httproute`。
  - ResourceQuota 后置状态。
- 每个完整复现的接口或内部流程记录 `MARK n/m`。
- 未完成或被刻意后置的行为不能写成已完成，只能保留在 backlog 或父任务剩余项中。

## 关联文档

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/040-remaining-route-slices-02518-02520.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/decisions/003-formal-portal-admin-bff-split.md`

## 关联任务

- 父任务：`tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- 已完成第一子任务：`tasks/done/028-01-bff-request-audit-collection-parity.md`
- 已完成第二子任务：`tasks/done/028-02-identity-avatar-media-sync-parity.md`
- 已完成第三子任务：`tasks/done/028-03-magnet-provider-search-parity.md`
- 已完成第四子任务：`tasks/done/028-04-ops-maintenance-runner-parity.md`
- 已完成第五-A 子任务：`tasks/done/028-05-a-interaction-watch-progress-consumer-parity.md`
- 已完成第五-B 子任务：`tasks/done/028-05-b-storage-avatar-sync-consumer-parity.md`
- 已完成第五-C 子任务：`tasks/done/028-05-c-metadata-provider-fanout-parity.md`
- 已完成第五-D 子任务：`tasks/done/028-05-d-search-producer-matrix-parity.md`
- 已完成第五-E 子任务：`tasks/done/028-05-e-metadata-provider-worker-enrichment-parity.md`
- 已完成第五-F 子任务：`tasks/done/028-05-f-normalization-raw-relation-parity.md`
- 已完成第五-G 子任务：`tasks/done/028-05-g-metadata-pending-gather-parity.md`
- 已完成第五-H 子任务：`tasks/done/028-05-h-storage-image-download-parity.md`
- 已完成第五-I 子任务：`tasks/done/028-05-i-mail-notification-disabled-failure-parity.md`
- 已完成第五子任务：`tasks/done/028-05-queue-consumer-deep-side-effect-parity-audit.md`
- 已完成 per-service audit 首个切片：`tasks/done/028-11-per-service-audit-collection-parity.md`
- 进行中后续：`tasks/in-progress/028-06-real-provider-live-parity.md`
- 已完成后续：`tasks/done/028-07-r2-live-watchdog-parity.md`
- 已完成后续：`tasks/done/028-08-normalization-deep-mapping-parity.md`
- 已完成后续：`tasks/done/028-09-search-producer-live-smoke-parity.md`
- 进行中后续：`tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`
- 进行中后续：`tasks/in-progress/028-10-production-governor-credential-pool-parity.md`

## 2026-06-07 第一阶段完成记录

```text
028-01 BFF request audit collection:       MARK 2/2
028-02 avatar media persistence/sync:      MARK 2/2
028-03 magnet provider search side effect: MARK 3/3
028-04 ops maintenance runner side effect: MARK 3/3
028-05-A interaction WATCH_PROGRESS:       MARK 4/4
028-05-B storage SYNC_AVATAR:              MARK 4/4
028-05-C metadata provider fan-out:        MARK 1/1
028-05-D search producer matrix:           MARK 2/2
028-05-E metadata provider worker/result:  MARK 4/4
028-05-F normalization raw relation:       MARK 4/4
028-05-G metadata pending gather:          MARK 4/4
028-05-H image download true/failure:      MARK 5/5
028-05-I mail disabled/failure:            MARK 4/4
028-11 config-service handler audit:        MARK 3/3
028-07-A storage R2 watchdog recovery:      MARK 4/4
028-07-B R2 credential-only object smoke:   MARK 5/5 with V1 bucket; dev ConfigMap bucket drift found
028-07-J storage R2 watchdog long-run smoke: MARK 8/8 with V1 bucket; dev local fixture rehydrate after rollout recorded
028-08 raw category/data source category:   MARK 4/4
028-08 standard mapping fill:               MARK 4/4
028-08 normalization retry watchdog:        MARK 4/4
028-08 normalization deep mapping live:      MARK 6/6
028-09 metadata-result raw search live:     MARK 6/6
028-09 aggregation raw/unified search live: MARK 6/6
028-09 old unified dirty lifecycle live:    MARK 6/6
028-10 production governor/MAIL pool guard: MARK 3/3
028-10-M notification-worker CrashLoop recovery: MARK 6/6, Ready 1/1, SMTP sent=0
028-10-L notification SMTP V1 credential one-shot: MARK 6/6, real email sent=1, send history/ops/user inbox confirmed
028-10-N notification maintenance runner gate: MARK 6/6, dry-run/execute/audit/cleanup passed, SMTP sent=false
028-12 catalog-service handler audit:       MARK 2/2
028-12 content-service handler audit:       MARK 2/2
028-12 content-service handler audit live:  MARK 4/4
028-12 credential-service handler audit:    MARK 3/3
028-12 credential-service handler audit live: MARK 4/4
028-12 identity-service handler audit:      MARK 4/4
028-12 identity-service handler audit live: MARK 4/4
028-12 interaction-service handler audit:   MARK 4/4
028-12 interaction-service handler audit live: MARK 4/4
028-12 magnet-service handler audit:        MARK 4/4
028-12 magnet-service handler audit live:   MARK 4/4
028-12 storage-service handler audit:       MARK 4/4
028-12 storage-service handler audit live:  MARK 4/4
028-12 search-service handler audit:        MARK 4/4
028-12 search-service handler audit live:   MARK 4/4
028-12 portal-service handler audit:        MARK 4/4
028-12 portal-service handler audit live:   MARK 4/4
028-12 task-service handler audit:          MARK 4/4
028-12 task-service handler audit live:     MARK 4/4
028-12 ops-service handler audit:           MARK 5/5
028-12 ops-service handler audit live:      MARK 4/4
028-12 scraper-service handler audit:       MARK 4/4
028-12 scraper-service handler audit live:  MARK 4/4
028-12 notification worker/job audit:       MARK 3/3
028-12 ops worker/job audit query:          MARK 3/3
028-12 ops worker/job audit live query:     MARK 4/4
028-12 notification worker/job audit live queue: MARK 5/5
028-06 TMDB live queue success:             MARK 6/6
```

集成 smoke：

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

后置检查：

```text
kubectl -n ircs-dev get httproute -> No resources found
ResourceQuota: pods=28/30, services=24/26
```

队列消费者 smoke：

```text
.\scripts\smoke\interaction-watch-progress-02805a-smoke.ps1
MARK 1/4 WATCH_PROGRESS consumer listens on q.interaction.watch_progress
MARK 2/4 consumer upserts member_watch_histories from fixture payload
MARK 3/4 duplicate messages keep latest timestamp per member/unified pair
MARK 4/4 smoke cleanup leaves no DB residue and WATCH_PROGRESS queues empty
```

后续 queue parity 标记：

```text
028-05-B:
MARK 1/4 SYNC_AVATAR consumer listens on q.storage.avatar_sync
MARK 2/4 R2 disabled dev path acks fixture and leaves member avatar_url unchanged
MARK 3/4 R2 enabled unit path uploads local avatar, rewrites R2 URL and deletes local copy
MARK 4/4 identity uploadAvatar publishes SYNC_AVATAR after local avatar persistence

028-05-C:
MARK 1/1 metadata provider fan-out routing covers Douban/TMDB/RT topic mapping

028-05-D:
MARK 1/2 search producer matrix built and content image unlink cover search sync gap fixed
MARK 2/2 content image unlink search refresh clears ES coverImageUrl in live smoke

028-05-E:
MARK 1/4 MetadataProviderWorker listens on Douban fetch queue
MARK 2/4 MetadataProviderWorker listens on RT fetch queue
MARK 3/4 missing Douban/RT provider publishes terminal ENRICHMENT_RESULT failure
MARK 4/4 metadata result collector records terminal failure and publishes raw search sync

028-05-F:
MARK 1/4 normalization extracts genre/language/area relation values from raw_metadata
MARK 2/4 normalization upserts raw_genres/raw_languages/raw_areas
MARK 3/4 normalization replaces video_raw_genres/video_raw_languages/video_raw_areas
MARK 4/4 normalization preserves READY status and raw search sync after relation writes

028-05-G:
MARK 1/4 MetadataPendingRegistry removes finished provider and reports remaining count
MARK 2/4 first provider result with remaining pending does not finalize or publish raw search sync
MARK 3/4 final provider result settles raw_videos status, clears pending key and publishes raw search sync
MARK 4/4 missing raw video result clears pending key and does not publish raw search sync

028-05-H:
MARK 1/5 DOWNLOAD_IMAGE consumer consumes eligible EXTERNAL/UNPROCESSED fixture
MARK 2/5 true download stores local cover and finalizes LOCAL_STORED metadata
MARK 3/5 failed download records FAILED retry_count next_retry_time last_error
MARK 4/5 unsafe download is blocked without storing object, max retries reaches DEAD
MARK 5/5 live smoke cleanup leaves no DB/object residue, q.storage.image and DLQ empty

028-05-I:
MARK 1/4 SEND_MAIL consumer calls mail service for queue message
MARK 2/4 mail disabled path skips JavaMailSender and returns without exception
MARK 3/4 template rendering failure is wrapped and propagated for retry/DLQ
MARK 4/4 SMTP send failure is wrapped and propagated for retry/DLQ

028-11:
MARK 1/3 request_audit_logs schema can store request_source
MARK 2/3 config-service handler request writes service audit row
MARK 3/3 ops-service request audit response exposes requestSource

028-12 content-service:
MARK 1/2 content-service handler request writes request_source
MARK 2/2 content-service audit filter skips actuator noise
MARK 1/4 content-service pod is Ready
MARK 2/4 content-service live handler request returns success
MARK 3/4 content-service live audit row is queryable by trace_id
MARK 4/4 content-service live smoke cleanup leaves no DB residue

028-12 credential-service:
MARK 1/3 credential-service handler request writes request_source
MARK 2/3 credential-service audit excludes body Authorization Cookie and internal token
MARK 3/3 credential-service audit filter skips actuator noise
MARK 1/4 credential-service pod is Ready
MARK 2/4 credential-service safe template request returns success
MARK 3/4 credential-service live audit row is queryable by trace_id
MARK 4/4 credential-service live smoke cleanup leaves no DB residue

028-12 identity-service:
MARK 1/4 shared audit redacts sensitive query parameters
MARK 2/4 identity-service handler request writes request_source
MARK 3/4 identity-service audit excludes body Authorization Cookie and query token
MARK 4/4 identity-service audit filter skips actuator noise
MARK 1/4 identity-service pod is Ready
MARK 2/4 identity-service PoW request returns success
MARK 3/4 identity-service live audit row redacts query token
MARK 4/4 identity-service live smoke cleanup leaves no DB residue

028-12 interaction-service:
MARK 1/4 interaction-service handler request writes request_source
MARK 2/4 interaction-service audit redacts query token
MARK 3/4 interaction-service audit excludes body Authorization and Cookie
MARK 4/4 interaction-service audit filter skips actuator noise
MARK 1/4 interaction-service pod is Ready
MARK 2/4 interaction-service feedback wall request returns success
MARK 3/4 interaction-service live audit row redacts query token
MARK 4/4 interaction-service live smoke cleanup leaves no DB residue

028-12 magnet-service:
MARK 1/4 magnet-service handler request writes request_source
MARK 2/4 magnet-service audit redacts query token
MARK 3/4 magnet-service audit excludes body Authorization and Cookie
MARK 4/4 magnet-service audit filter skips actuator noise
MARK 1/4 magnet-service pod is Ready
MARK 2/4 magnet-service provider list request returns success
MARK 3/4 magnet-service live audit row redacts query token
MARK 4/4 magnet-service live smoke cleanup leaves no DB residue

028-12 task-service:
MARK 1/4 task-service handler request writes request_source
MARK 2/4 task-service audit redacts query token
MARK 3/4 task-service audit excludes Authorization Cookie body and response content
MARK 4/4 task-service audit filter skips actuator noise
MARK 1/4 task-service pod is Ready
MARK 2/4 task-service collection task list request returns success
MARK 3/4 task-service live audit row redacts query token
MARK 4/4 task-service live smoke cleanup leaves no DB residue

028-12 ops-service:
MARK 1/5 ops-service handler request writes request_source
MARK 2/5 ops-service audit redacts query token/password
MARK 3/5 ops-service audit excludes Authorization Cookie and response content
MARK 4/5 ops-service audit filter skips actuator noise
MARK 5/5 ops-service request-audit endpoint is not self-audited
MARK 1/4 ops-service pod is Ready
MARK 2/4 worker job audit summary request success
MARK 3/4 ops-service live audit row redacts query token
MARK 4/4 ops-service live smoke cleanup leaves no DB residue

028-12 scraper-service:
MARK 1/4 scraper-service handler request writes request_source
MARK 2/4 scraper-service audit redacts query token/password
MARK 3/4 scraper-service audit excludes Authorization Cookie body and response content
MARK 4/4 scraper-service audit filter skips actuator noise
MARK 1/4 scraper-service pod is Ready
MARK 2/4 invalid stream request fails without scraper execution
MARK 3/4 scraper-service live audit row redacts query token/password
MARK 4/4 scraper-service live smoke cleanup leaves no DB residue
```

本轮新增完成切片：

```text
028-06-C magnet YTS_BZ runner contract: MARK 5/5
028-06 Douban/RT provider live parity parent: done
028-07-C storage-service R2 path smoke: MARK 6/6
028-07-J storage-service R2 watchdog recovery smoke: MARK 8/8
028-10-B MAIL credential adapter and fake sink: MARK 5/5
028-10-C ops runner registry and dev gate: MARK 5/5
028-10-N notification maintenance runner gate: MARK 6/6, default-off/dry-run/double execute gate/audit/cleanup passed
028-16-C1 full readiness / auto-start gate first closure: done, C1 parent remains in-progress
028-16-C2 SystemConfig cache/event parity parent: done
028-16-C3-S6 shared outbound circuit breaker first slice + content internal caller: done, live smoke not executed
```

后续剩余行为切片：

```text
028-10 other high-risk runner / production-like governor expansion; do not relist completed send-history cleanup gate
028-12 more worker/scheduler/runner audit expansion
028-16 V1 golden behavior inventory and deep library/runtime parity
028-16-A canonical rawMetadata, HanLP/OpenCC, title pipeline, raw dictionary policy and metadata result deep merge
028-16-B aggregation graph matching, pipeline relation union, duplicate unified merge and dirty lifecycle
028-16-C C1 cache/full preset first closure done implementation; remaining resolver/real-source live smoke, C3 task/identity/storage/BFF outbound adoption, UUID and async logging
028-16-D Tika MIME/charset and scraper streaming parity
028-16-E gated LLM cleaning parity
```

当前后续功能细节口径：

```text
第一阶段 route/BFF/queue/live-smoke 强制缺口：0
深层功能细节：进入 028-16 golden inventory，不再按旧 4 类口径统计
已关闭父项：028-06、028-07、028-16-C2
进行中：028-10、028-12、028-16
待办：028-16 后续将继续拆出更细 P0/P1/P2 子任务
```
