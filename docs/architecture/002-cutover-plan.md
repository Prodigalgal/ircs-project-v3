# IRCS V3 cutover plan

The target is one-step service ownership, but code movement should be audited
service by service.

## Step 1: target skeleton

Done in this repository:

- Multi-project Gradle structure.
- Shared contracts for queues and routes.
- Spring Boot entrypoint for every target service.
- K8S service inventory and edge routing skeleton.

## Step 2: extract stable contracts

Move these first from V1:

- Queue names from `infrastructure.queue.QueueTopic`.
- DTOs used as RabbitMQ payloads.
- Route ownership constants.
- Common error envelope and pagination envelope.

Contracts must not depend on JPA entities.

## Step 3: move services by ownership

Recommended order for code migration:

```text
1. notification-worker (first worker slice deployed)
2. search-service (search sync consumer + JDBC/ES processor deployed)
3. storage-service (image physical deletion worker deployed)
4. content-service (image unlink worker deployed; full content APIs later)
5. metadata-worker (metadata result collector + dispatcher + TMDB provider worker deployed; TMDB credentials leased from credential-service; Douban/RT providers later)
6. catalog-service (read-only dictionary and data-source API deployed; write/normalization maintenance later)
7. identity-service
8. task-service (read-only collection task list/detail API deployed; commands/logs/scheduler later)
9. scraper-service
10. ingestion-worker
11. normalization-worker
12. aggregation-worker
13. portal-service
14. interaction-service
15. magnet-service
16. config-service (read-only system config API deployed; write/connectivity tests later)
17. credential-service (read-only redacted credential metadata API + token-protected internal provider lease API deployed; write/rotation/audit later)
18. ops-service
```

This is not a staged target. It is the safest code extraction order for the
one-step target map.

## MVP 接入状态 2026-06-04

当前 `ircs-dev` 已接入并 Ready 的业务服务为 18/18：

```text
notification-worker
search-service
storage-service
content-service
metadata-worker
credential-service
catalog-service
config-service
task-service
ingestion-worker
normalization-worker
aggregation-worker
magnet-service
identity-service
scraper-service
portal-service
interaction-service
ops-service
```

尚未部署接入的业务服务为 0/18：

```text
暂无
```

本轮新增 `normalization-worker` normalize 队列 consumer 后，没有新增
HTTPRoute，仍然只在 `ircs-dev` 内通过 ClusterIP 访问。该 worker 已消费
`q.normalize.video`，将 `raw_videos.normalization_status` 推进到 `READY`，
并在成功后发布 `q.search.sync.raw` 触发 `ircs-search-service` 写入 ES。

随后新增 `aggregation-worker` scheduler MVP，同样没有新增 HTTPRoute。该 worker
按 V1 真实边界扫描 `raw_videos`，将满足条件的记录推进到
`aggregation_status=BOUND`，创建或复用 `unified_videos`，写入
`raw_video_unified_video`，并发布 raw/unified search sync。

随后新增 `magnet-service` provider/cache 只读 MVP，同样没有新增 HTTPRoute。该服务
读取 `magnet_providers` 和 `APPROVED` `magnet_links`，不触发外部 provider 搜索，
不写 job/run。

随后 `identity-service` 完成 ARM64 镜像推送和 `ircs-dev` rollout，同样没有新增
HTTPRoute。当前 auth/profile smoke 已覆盖 PoW、register、login、profile get、
profile update、check-in、password change、forgot-password mail disabled、
reset-password temp Valkey token、relogin、BANNED 登录拒绝和临时数据清理。

随后 `scraper-service` 完成 manual scrape MVP，同样没有新增 HTTPRoute。该服务保留
V1 兼容 manual scrape init/SSE 路由，读取 `data_sources`，将抓取结果或 dev
`directItems` 映射为 `IngestionItem` 并发布到 `q.ingest.video`，由
`ingestion-worker` 负责实际落库和 playlist 同步。

随后小幅调整 dev Pod quota 到 `24`，并新增 `portal-service` 与
`interaction-service` MVP。portal-service 保留公开 metadata/home 读聚合接口；
interaction-service 保留公开 feedback wall 和 member JWT 下的 history/favorites
只读接口。两者均保持 ClusterIP only，无公网 HTTPRoute。

随后新增 `ops-service` maintenance/dashboard MVP，同样没有新增 HTTPRoute。该服务保留
dashboard 只读聚合、queue/Valkey 运行指标、dashboard refresh 兼容入口，以及
maintenance active/expired stream 兼容入口；destructive maintenance、DLQ retry/discard、
traffic reset 和 request audit 采集后置。至此 18 个目标业务服务均已完成 MVP rollout。

## Step 4: migrations

Do not keep Liquibase in every application Deployment init container.

Use one `ircs-migrator` Job. V3 already has the first implementation in
`platform/ircs-migrator` with the V1 changelog copied as the initial baseline:

```text
CI/CD builds images
  -> apply ircs-migrator Job
  -> wait for schema success
  -> rollout services
```

The dev Job lives at:

```text
deploy/k8s/dev/migrator-job-dev.yaml
```

The default Job does not force-unlock Liquibase. Forced unlock is a maintenance
action, not a normal rollout action.

Each service owns one changelog namespace:

```text
db/changelog/identity
db/changelog/content
db/changelog/catalog
db/changelog/task
db/changelog/portal
db/changelog/search
db/changelog/storage
db/changelog/interaction
db/changelog/config
db/changelog/credential
db/changelog/ops
```

Workers normally do not own separate schemas unless they persist state outside
their owning domain.
