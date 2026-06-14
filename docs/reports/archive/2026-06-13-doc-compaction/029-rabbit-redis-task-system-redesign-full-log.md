# 029 Rabbit + Redis Task System Redesign

状态：进行中

日期：2026-06-13

来源：

- `docs/requirements/029-rabbit-redis-task-system-redesign.md`
- `docs/decisions/008-rabbit-redis-mt-pt-dt-task-system.md`

## 目标

破坏性重构 task-service 采集任务执行模型，切到 Rabbit 主队列、Redis MT snapshot/状态热层、
DB MT 粗账本/周期快照。

## 当前切片

第一刀范围：

- 新增 `x.task` 与 PT/DT/page discovered/page failed/detail done/master done 队列契约。
- 新增 MT/PT/DT shared message contracts。
- task-service 接入 AMQP 与 shared topology。
- `start/resume/startInternal/cron` 切到 `TaskQueueDispatchService`。
- 写入 Redis `TaskMasterSnapshot` 与 active/source/dirty index。
- 新增 DT completion Redis Lua 原子计数骨架。
- 禁用旧本地 runner 自动入口，不做 fallback。

## 非目标

- 本切片不 rollout 或默认开启 PT/DT consumer。
- 本切片不做真实资源站 live test。
- 本切片不 rollout K8S。

## 验收

```text
MARK 1/8 goal + requirements + ADR + task spec written
MARK 2/8 QueueTopic/shared topology declares x.task and q.task.*
MARK 3/8 TaskCommandService start path dispatches Rabbit page tasks after commit
MARK 4/8 cron uses TaskCommandService.startInternal, no local runner fallback
MARK 5/8 Redis MT snapshot and dirty index service implemented
MARK 6/8 Redis Lua detail completion atomic counter skeleton implemented
MARK 7/8 focused tests pass for contracts/messaging/task-service first slice
MARK 8/8 first-slice docs/tasks recorded PT/DT consumer, retry/DLQ, DB flush, live rollout boundaries
```

## 下一步边界

1. 补真实源站 HTTP/DNS/protocol live matrix：验证 4xx、429、5xx、DNS、连接、
   HTTP/1.0/Apache HC5 策略。
2. 扩展更多真实数据源/多页窗口 MT 验证，观察 Rabbit -> scraper PT/DT -> Redis hot layer ->
   DB flush 在更大真实批次下的稳定性。
3. live 验证 Rabbit retry TTL、page.failed/detail.failed 终局进度事件和 DB flush 的失败路径。
4. 029 后续验证禁止本地 smoke，禁止 mock/fake/造数；只允许连接 `ircs-dev` 集群，
   使用真实配置、真实凭据、真实集群数据和真实外部链路。
5. trend discovery 已完成 `ircs-dev` 真实小窗口验证；后续只剩 provider-gated trend-sync
   全链路扩展，不再把 discovery 是否进入 Rabbit MT/PT/DT 作为未知项。
6. worker-job audit 已补失败根因可观测性：源站/parser 错误不再被 `token` 普通词误判为敏感信息；
   真实 `卧龙资源` page failure 已验证审计保留 parser 根因。

## 2026-06-13 第一刀实现记录

已完成：

- `QueueTopic` 新增 `TASK_PAGE`、`TASK_DETAIL`、`TASK_PAGE_DISCOVERED`、
  `TASK_PAGE_FAILED`、`TASK_DETAIL_DONE`、`TASK_MASTER_DONE`。
- `shared:ircs-contracts` 新增 `TaskPageMessage`、`TaskDetailMessage`、
  `TaskPageDiscoveredMessage`、`TaskPageFailedMessage`、`TaskDetailDoneMessage`、
  `TaskMasterDoneMessage`、`TaskMasterSnapshot`、`TaskScrapeOptions`。
- `shared:ircs-messaging` 通过统一 topology 自动声明 `x.task`、`q.task.*` 和对应 DLQ，
  `QueueOwnership` 按 topic 分配到 `ircs-scraper-service` / `ircs-task-service`。
- `TaskCommandService.start/startInternal` 破坏性切到 `TaskQueueDispatchService` after-commit dispatch。
- `CollectionTaskCronScheduler` 改为调用 `TaskCommandService.startInternal`。
- `CollectionTaskRunnerService` 不再注册为 Spring bean，不再监听旧 task queued event。
- task-service 增加 AMQP 依赖、Rabbit 配置和 dev manifest Rabbit env。
- `TaskMasterSnapshotService` 写 Redis snapshot、active/source/dirty index。
- `TaskProgressRedisService` 增加 DT completion Lua 原子计数骨架。
- 第二刀调整队列归属：`q.task.page/detail` 由 scraper-service 消费，
  `q.task.page.discovered/detail.done/master.done` 由 task-service 聚合。
- `TaskPageMessage` / `TaskDetailMessage` 增加 `TaskScrapeOptions`，避免 scraper-service 回查 task-service。
- scraper-service 新增 `TaskQueueConsumer` / `TaskQueueScraperService`：
  - PT consumer 只抓列表页并拆 DT。
  - DT consumer 才抓详情、mapping、publish ingestion。
  - queue consumer 写 worker-job audit。
- scraper-service listener 由 `APP_SCRAPER_TASK_QUEUE_LISTENER_ENABLED` gate 控制，dev manifest 默认 false。
- task-service 新增 `TaskQueueProgressConsumer`：
  - 消费 `q.task.page.discovered`，通过 Lua 幂等初始化 PT detail scheduled 并累加 MT。
  - 消费 `q.task.page.failed`，通过 Lua 幂等记录 PT 失败状态并累加 MT pageFailed。
  - 消费 `q.task.detail.done`，通过 Lua 幂等累加 PT/MT detail completed/succeeded/failed。
  - queue consumer 写 worker-job audit。
- shared queue contract/topology 新增 retry queue 与 `q.task.page.failed`：
  - 每个 queue 自动声明 `.retry` TTL queue，TTL 到期 dead-letter 回原 queue。
  - `RabbitTaskRetryPublisher` 保留原消息体并追加 `x-ircs-retry-count`、disposition 和错误 headers。
- task-service/scraper-service consumer 接入 retry/DLQ 第一刀：
  - payload 无法解析直接 DLQ。
  - 业务失败低于 max-retries 时发布 retry 并 audit `retrying`。
  - 达到 max-retries 时发布 DLQ 并 audit `dead-lettered`。
  - scraper PT 终局失败发布 `TaskPageFailedMessage`。
  - scraper DT 终局失败发布 `TaskDetailDoneMessage(success=false)`。
  - task-service progress consumer 与 scraper-service task consumer 已切到 `ackMode=MANUAL`：
    成功、retry 已发布、DLQ 已发布后 `basicAck`；无法安全处理时 `basicNack(requeue=false)`。
- shared `RabbitTaskFailureClassifier` 已接入 task-service/scraper-service consumer：
  - `IllegalArgumentException`、`NoSuchElementException`、`UnsupportedOperationException`、
    `SecurityException` 等确定性错误直接 DLQ。
  - 网络/IO/timeout、Redis/Rabbit/DB transient 和未知运行时错误默认 retry。
  - scraper fatal page/detail failure 仍先发布 `page.failed` 或 `detail.done(success=false)`，
    再投 DLQ，确保 Admin runtime 不会卡在 backlog。
- shared `RabbitTaskHttpStatusException` 已接入 scraper-service：
  - `ListScraperClient` 对源站 HTTP 非 2xx 抛出明确 status 信号。
  - classifier 将 408/409/425/429/5xx 判定为 retry，将确定性 4xx 判定为 DLQ。
  - `fetchListPage` 已拆开 HTTP fetch 与 JSON parse 失败，避免网络/HTTP transient
    被包装成 parse/config fatal。
- task-service 新增 Admin runtime read API：
  - `GET /api/v1/collection-tasks/{id}/runtime`
  - 合并 DB 任务详情、Redis MT snapshot、master state、PT state、DT done bounded 样本。
  - `pageOffset/pageLimit/detailLimit` 控制响应体大小。
- `TaskProgressRedisService` Lua 脚本补齐 Admin 可展示字段：
  `masterTaskId`、`pageTaskId`、`status`、`updatedAt`。
- admin-bff 路由测试确认 runtime endpoint 由 `/api/v1/collection-tasks` 前缀透传到 task-service。
- shared `TaskRuntimeHotKeys` 固化 MT/PT/DT hot-state key 契约，命名不绑定 Redis；
  task-service 内部 `TaskHotKeys` 委托共享契约，避免跨服务复制 key 字符串。
- ops-service 新增 `GET /api/v1/dashboard/task-runtime`：
  - 从 active MT index 读取 master snapshot 和 master state。
  - 汇总 active/returned/dirty MT、page/detail counters、detail backlog。
  - 输出 `statusCounts`、`attentionCounts` 和每个 MT 的 progress/attention/error/freshness 字段。
  - 只返回 MT 摘要，不展开 PT/DT 明细，单任务深度排障仍走 task-service runtime API。
- ops-service `QueueTopicDescriptor` 补齐 `TASK_PAGE_FAILED`，Task queue/DLQ metrics 分组完整。
- V1 admin frontend `Dashboard > 系统监控` 接入全局 task runtime overview：
  - `dashboardApi.getTaskRuntimeOverview` 调用 `GET /api/v1/dashboard/task-runtime`。
  - 系统监控页以 3s polling 展示 active/dirty MT、PT/DT counters、detail backlog、
    attention/status counts 和前 8 个 active MT 摘要行。
- V1 admin frontend `ircs-frontend` 的采集任务详情弹窗接入 runtime API，
  新增 `MT/PT/DT 队列视图`，展示 master counters、PT 明细和 DT done 样本。
- V1 admin frontend `ircs-frontend` 的 runtime 面板升级为细粒度排障视图：
  展示 PT 当前窗口分布、DT backlog、成功/失败率、处理速率、状态新鲜度、
  需要关注的 PT、每页 DT 进度/更新时间。
- task-service runtime API 增加失败 DT 细节：
  - `TaskProgressRedisService` 将失败 DT 写入 `pt:{id}:details:failed` 和
    `pt:{id}:details:failed:errors`。
  - `TaskRuntimeReadService` 输出 `failedDetailTaskIds`、`failedDetailErrors`、
    `detailBacklog`、`progressPercent`、`attentionLevel`。
  - Admin runtime 面板展示失败 DT 样本和 sourceVid/error 摘要，仍只作为排障视图，
    不反写组合展示到业务 DB。
- task-service 新增 Redis dirty MT -> DB 粗账本 flush：
  - `TaskSnapshotFlushService` 从 `ircs:task:mt:dirty` 读取 MT，汇总 Redis master/page state。
  - `JdbcCollectionTaskRepository.flushRuntimeLedger` 只写 `collection_tasks` MT 粗账本字段。
  - 非终态 flush 不覆盖管理员手动 `PAUSED/STOPPING`。
  - 成功 flush 后清 dirty marker，终态时移出 active MT index。
- task-service 新增 `TaskSnapshotFlushScheduler`：
  - `app.task.snapshot.flush.enabled` 显式 gate，默认 false。
  - 使用 `TaskDistributedLockRunner` 防多副本重复 flush。
  - 写 worker-job audit 证据。
- task-service `TaskQueueProgressConsumer` 新增 `q.task.master.done` consumer：
  - `recordMasterDone` 先写 Redis master state。
  - 然后立即 `flushOne`，作为完成事件 final flush。
- dev manifest 增加 `APP_TASK_SNAPSHOT_FLUSH_*` 显式配置，当前仍保持 disabled。
- dev manifest 已将 `ircs-admin-frontend` 镜像更新到
  `harbor.mnnu.eu.org/ircs/ircs-frontend:029-task-runtime-20260613-1556`。
- dev manifest 已为 `ircs-identity-service` 补 `SECURITY_ADMIN_PASSWORD` from
  `ircs-dev-secrets.ADMIN_PASSWORD`，恢复 V1 raw admin password runtime 注入优先于 DB/default seed 的语义。

验证：

```powershell
.\gradlew.bat :shared:ircs-contracts:compileJava :shared:ircs-messaging:test :services:ircs-task-service:test --tests "com.prodigalgal.ircs.task.TaskQueuePublisherTest" --tests "com.prodigalgal.ircs.task.TaskQueueDispatchServiceTest" --tests "com.prodigalgal.ircs.task.TaskMasterSnapshotServiceTest" --tests "com.prodigalgal.ircs.task.CollectionTaskCronSchedulerTest" :services:ircs-ops-service:compileJava
.\gradlew.bat :services:ircs-task-service:test
.\gradlew.bat :services:ircs-task-service:bootJar :services:ircs-ops-service:test --tests "com.prodigalgal.ircs.ops.SystemMetricsServiceTest"
.\gradlew.bat :shared:ircs-contracts:compileJava :shared:ircs-messaging:test :services:ircs-task-service:test --tests "com.prodigalgal.ircs.task.TaskQueuePublisherTest" --tests "com.prodigalgal.ircs.task.TaskQueueDispatchServiceTest" :services:ircs-scraper-service:test --tests "com.prodigalgal.ircs.scraper.TaskQueueScraperServiceTest" :services:ircs-ops-service:compileJava
.\gradlew.bat :services:ircs-scraper-service:test :services:ircs-task-service:bootJar :services:ircs-scraper-service:bootJar
.\gradlew.bat :shared:ircs-messaging:test :services:ircs-task-service:test --tests "com.prodigalgal.ircs.task.TaskQueueProgressConsumerTest" --tests "com.prodigalgal.ircs.task.TaskQueuePublisherTest" --tests "com.prodigalgal.ircs.task.TaskQueueDispatchServiceTest" :services:ircs-scraper-service:test --tests "com.prodigalgal.ircs.scraper.TaskQueueScraperServiceTest" :services:ircs-task-service:bootJar :services:ircs-scraper-service:bootJar :services:ircs-ops-service:compileJava
.\gradlew.bat :services:ircs-task-service:test :services:ircs-scraper-service:test
.\gradlew.bat :services:ircs-task-service:test --tests "com.prodigalgal.ircs.task.CollectionTaskControllerTest" --tests "com.prodigalgal.ircs.task.TaskRuntimeReadServiceTest" --tests "com.prodigalgal.ircs.task.TaskMasterSnapshotServiceTest" :services:ircs-admin-bff:test --tests "com.prodigalgal.ircs.adminbff.AdminBffRoutesTest"
.\gradlew.bat :services:ircs-task-service:test :services:ircs-admin-bff:test :services:ircs-task-service:bootJar
npm run build
.\gradlew.bat :services:ircs-task-service:test --tests "com.prodigalgal.ircs.task.TaskQueueProgressConsumerTest" --tests "com.prodigalgal.ircs.task.TaskSnapshotFlushServiceTest" --tests "com.prodigalgal.ircs.task.TaskSnapshotFlushSchedulerTest" --tests "com.prodigalgal.ircs.task.TaskMasterSnapshotServiceTest"
.\gradlew.bat :services:ircs-task-service:test :services:ircs-task-service:bootJar
.\gradlew.bat :shared:ircs-messaging:test :services:ircs-task-service:test --tests "com.prodigalgal.ircs.task.TaskQueueProgressConsumerTest" --tests "com.prodigalgal.ircs.task.TaskRuntimeReadServiceTest" --tests "com.prodigalgal.ircs.task.TaskSnapshotFlushServiceTest" :services:ircs-scraper-service:test --tests "com.prodigalgal.ircs.scraper.TaskQueueConsumerTest"
.\gradlew.bat :services:ircs-task-service:test :services:ircs-scraper-service:test :services:ircs-task-service:bootJar :services:ircs-scraper-service:bootJar
.\gradlew.bat :services:ircs-task-service:test --tests "com.prodigalgal.ircs.task.TaskQueueProgressConsumerTest" :services:ircs-scraper-service:test --tests "com.prodigalgal.ircs.scraper.TaskQueueConsumerTest"
.\gradlew.bat :services:ircs-task-service:test :services:ircs-scraper-service:test :services:ircs-task-service:bootJar :services:ircs-scraper-service:bootJar
.\gradlew.bat --no-daemon :services:ircs-task-service:test --tests "com.prodigalgal.ircs.task.TaskRuntimeReadServiceTest"
.\gradlew.bat --no-daemon :services:ircs-task-service:test :services:ircs-task-service:bootJar
npm run build
.\gradlew.bat --no-daemon :shared:ircs-messaging:test --tests "com.prodigalgal.ircs.messaging.RabbitTaskFailureClassifierTest" :services:ircs-task-service:test --tests "com.prodigalgal.ircs.task.TaskQueueProgressConsumerTest" :services:ircs-scraper-service:test --tests "com.prodigalgal.ircs.scraper.TaskQueueConsumerTest"
.\gradlew.bat --no-daemon :shared:ircs-messaging:test :services:ircs-task-service:test :services:ircs-scraper-service:test :services:ircs-task-service:bootJar :services:ircs-scraper-service:bootJar
.\gradlew.bat --no-daemon :shared:ircs-messaging:test --tests "com.prodigalgal.ircs.messaging.RabbitTaskFailureClassifierTest" :services:ircs-scraper-service:test --tests "com.prodigalgal.ircs.scraper.ListScraperClientTest" --tests "com.prodigalgal.ircs.scraper.TaskQueueConsumerTest"
.\gradlew.bat --no-daemon :shared:ircs-messaging:test :services:ircs-scraper-service:test :services:ircs-scraper-service:bootJar
.\gradlew.bat --no-daemon :shared:ircs-contracts:compileJava :services:ircs-task-service:test --tests "com.prodigalgal.ircs.task.TaskMasterSnapshotServiceTest" --tests "com.prodigalgal.ircs.task.TaskRuntimeReadServiceTest" :services:ircs-ops-service:test --tests "com.prodigalgal.ircs.ops.TaskRuntimeOverviewServiceTest" --tests "com.prodigalgal.ircs.ops.DashboardQueryServiceTest" --tests "com.prodigalgal.ircs.ops.DashboardControllerTest" --tests "com.prodigalgal.ircs.ops.SystemMetricsServiceTest"
.\gradlew.bat --no-daemon :services:ircs-ops-service:test :services:ircs-ops-service:bootJar
rg -n "@Autowired" shared services platform --glob "!**/src/test/**" --glob "!**/build/**" -S
rg -n "TaskRuntimeRedisKeys|TaskRedisKeys" shared services platform -S --glob "!**/build/**"
rg -n "TaskQueuedEvent|onTaskQueued|runnerService\.submit\(|CollectionTaskRunnerService" services\ircs-task-service\src\main\java services\ircs-task-service\src\test\java -S
```

2026-06-13 真实集群验证：

```powershell
kubectl -n ircs-dev rollout restart deployment/ircs-ops-service
kubectl -n ircs-dev set image deployment/ircs-admin-frontend ircs-admin-frontend=harbor.mnnu.eu.org/ircs/ircs-frontend:029-task-runtime-20260613-1556
kubectl -n ircs-dev rollout status deployment/ircs-ops-service --timeout=240s
kubectl -n ircs-dev rollout status deployment/ircs-admin-frontend --timeout=240s
kubectl -n ircs-dev patch deployment ircs-identity-service --type=json --patch-file <identity-admin-password-and-http-probe-patch.json>
kubectl -n ircs-dev rollout status deployment/ircs-identity-service --timeout=240s
kubectl -n ircs-dev port-forward --address 127.0.0.1 svc/ircs-frontend-gateway 18088:8080
```

说明：最后一步使用 Node `crypto` 作为客户端求解 PoW，并通过 gateway 真实调用
`/api/v1/auth/pow`、`/api/v1/auth/login` 和 `/api/v1/dashboard/task-runtime`；
未启动本地服务，未写入 fake/mock 数据。

结果：

- focused tests：通过。
- task-service 全量 test：通过。
- task-service bootJar：通过。
- scraper-service focused/full tests：通过。
- scraper-service bootJar：通过。
- task-service progress consumer focused test：通过。
- task-service + scraper-service latest full tests：通过。
- ops-service queue metrics focused test：通过。
- task-service Admin runtime focused tests：通过。
- admin-bff runtime route focused test：通过。
- task-service/admin-bff full tests + task-service bootJar：通过。
- V1 admin frontend `ircs-frontend` production build：通过。
- V1 admin frontend `ircs-frontend` runtime panel refinement production build：通过。
- task-service snapshot flush/progress consumer focused tests：通过。
- task-service latest full tests + bootJar：通过。
- Rabbit retry/DLQ focused tests：通过。
- task-service/scraper-service latest full tests + bootJar：通过。
- Rabbit manual ack/nack focused tests：通过。
- task-service/scraper-service manual ack latest full tests + bootJar：通过。
- Admin runtime failed DT samples focused test：通过。
- task-service failed DT runtime latest full tests + bootJar：通过。
- V1 admin frontend failed DT runtime panel production build：通过。
- Rabbit fatal/retryable classifier focused tests：通过。
- task-service/scraper-service fatal direct DLQ focused tests：通过。
- shared messaging + task-service + scraper-service latest full tests + bootJar：通过。
- Rabbit HTTP status/source-specific classifier focused tests：通过。
- scraper-service source HTTP status + queue routing focused tests：通过。
- shared messaging + scraper-service latest full tests + scraper bootJar：通过。
- ops-service task runtime overview focused tests：通过。
- shared contracts compile + task runtime key delegation focused tests：通过。
- ops-service latest full tests + bootJar：通过。
- `TaskRuntimeRedisKeys` / `TaskRedisKeys` 命名残留：无命中。
- 生产代码 `@Autowired`：无命中。
- `TaskQueuedEvent` / `onTaskQueued`：无命中；旧 runner 只剩普通类和自身单测。
- `ircs-ops-service:dev` 已在 `ircs-dev` rollout，运行 digest：
  `sha256:134ef7fb99c684f0c143640b4d2d9731ede05d97671c50e8d7c96e9f25753015`。
- `ircs-admin-frontend` 已在 `ircs-dev` rollout，运行 digest：
  `sha256:132b7d3bb073734ab8072769521e94885b25f8e9e319253e7c1c433d87c91302`。
- `ircs-identity-service` 已在 `ircs-dev` rollout，`SECURITY_ADMIN_PASSWORD` 来自
  `ircs-dev-secrets/ADMIN_PASSWORD`，readiness probe 已切到 actuator HTTP。
- gateway port-forward 真实链路通过：PoW challenge 200、admin login 200、
  `GET /api/v1/dashboard/task-runtime?limit=3` 200。
- 当前集群真实任务热层为空，runtime API 返回 `activeMasterCount=0`、
  `returnedMasterCount=0`、`detailBacklog=0`。

2026-06-13 trend discovery K8S real verification：

```powershell
kubectl -n ircs-dev exec deploy/ircs-task-service -- wget --post-file=/tmp/trend-discovery.json `
  --header='Content-Type: application/json' `
  --header='X-Correlation-Id: 030-trend-20260613224632' `
  --header='X-IRCS-SERVICE-ID: codex-k8s-real-validation' `
  --header='X-IRCS-SERVICE-SCOPES: task:maintenance' `
  http://127.0.0.1:8080/internal/v1/tasks/trend-discovery
kubectl -n ircs-dev exec deploy/ircs-task-service -- wget -q -O - `
  "http://127.0.0.1:8080/api/v1/collection-tasks/019ec172-e5dc-75a4-9bc2-bc4432c01b60/runtime?pageLimit=5&detailLimit=5"
kubectl -n ircs-dev exec rabbitmq-0 -- rabbitmqctl list_queues name messages messages_ready messages_unacknowledged consumers
```

结果：

- 使用真实源站 `iKun资源`、真实关键词 `那位天才对伪装女友过分执着`。
- internal schedule response：`createdTasks=1`、`queuedTasks=1`、taskId
  `019ec172-e5dc-75a4-9bc2-bc4432c01b60`。
- runtime/Redis：`COMPLETED`，`pageScheduled=1`、`pageDiscovered=1`、`pageCompleted=1`、
  `detailScheduled=1`、`detailCompleted=1`、`detailSucceeded=1`、`detailFailed=0`、
  `totalItems=1`，`redisSnapshotPresent=true`、`redisStatePresent=true`。
- DB 粗账本：`collection_tasks.status=COMPLETED`、`stat_total_found=1`、
  `stat_processed=1`、`stat_success=1`、`stat_failed=0`。
- 数据副作用：`raw_videos` 中该真实标题存在 1 条。
- worker-job audit：4 条 `queue-consumer succeeded`，`job_source` 包含
  `ircs-task-service@ircs-task-service-7c76558976-tc98g#1` 与
  `ircs-scraper-service@ircs-scraper-service-9d4fd59d7-qmcm2#1`，可追踪具体 Pod 副本。
- Rabbit：`q.task.*`、`q.ingest.video` 主队列/retry/DLQ 的 ready/unacked 均为 0。
- 本验证未启动本地服务，未使用 mock/fake payload，允许真实写入开发库。

2026-06-13 Rabbit listener + snapshot flush gate 真实集群 rollout：

```powershell
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-task-service' -ImageName ircs-task-service -Tag 029-task-runtime-20260613-1720 -Architecture arm64 -Os linux
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-scraper-service' -ImageName ircs-scraper-service -Tag 029-task-runtime-20260613-1720 -Architecture arm64 -Os linux
kubectl apply -f deploy/k8s/dev/task-service-dev.yaml
kubectl apply -f deploy/k8s/dev/scraper-service-dev.yaml
kubectl -n ircs-dev rollout status deployment/ircs-task-service --timeout=300s
kubectl -n ircs-dev rollout status deployment/ircs-scraper-service --timeout=300s
kubectl -n ircs-dev exec rabbitmq-0 -- rabbitmqctl list_queues name messages_ready messages_unacknowledged consumers durable --formatter json
kubectl -n ircs-dev port-forward --address 127.0.0.1 svc/ircs-frontend-gateway 18080:80
```

结果：

- `shared:ircs-messaging` 修复 `RabbitTaskRetryPublisher` bean 注册：移除普通组件上的
  `@ConditionalOnBean(RabbitTemplate.class)`，避免 task/scraper consumer 因缺少 shared
  retry publisher 进入 CrashLoop。
- `ircs-task-service` rollout 到
  `harbor.mnnu.eu.org/ircs/ircs-task-service:029-task-runtime-20260613-1720`，运行 digest：
  `sha256:3335df58e1f94ca07f5ec0913e4e59b4df252f259b413b8be398ad66f5621e7b`。
- `ircs-scraper-service` rollout 到
  `harbor.mnnu.eu.org/ircs/ircs-scraper-service:029-task-runtime-20260613-1720`，运行 digest：
  `sha256:575c7a65ae334b36e7d82f1dd867fbf00c14ee46d43dbeb898e513fc2f6f825a`。
- `APP_TASK_QUEUE_LISTENER_ENABLED=true`、`APP_TASK_SNAPSHOT_FLUSH_ENABLED=true`、
  `APP_TASK_QUEUE_INITIAL_PAGE_WINDOW=1`、`APP_SCRAPER_TASK_QUEUE_LISTENER_ENABLED=true`
  已在 `ircs-dev` 生效。
- RabbitMQ 已声明 `q.task.page/detail/page.discovered/page.failed/detail.done/master.done`
  及对应 `.retry`、`.dlq`；6 个主任务队列 consumers 均为 1，消息积压为 0。
- `svc/ircs-task-service/actuator/health/autoStartGate` 与
  `svc/ircs-scraper-service/actuator/health/autoStartGate` 均返回 UP。
- gateway 入口通过集群 port-forward 返回 HTTP 200。
- 本次未启动本地服务，未创建 fake/mock/造数任务。

2026-06-13 真实 MT/PT/DT 端到端验证：

```powershell
kubectl -n ircs-dev exec deploy/ircs-task-service -- wget -S -O- --post-data= http://127.0.0.1:8080/api/v1/collection-tasks/019ebad3-d653-71c7-8d8f-3cf8947f9f66/start
kubectl -n ircs-dev exec deploy/ircs-task-service -- wget -qO- 'http://127.0.0.1:8080/api/v1/collection-tasks/019ebad3-d653-71c7-8d8f-3cf8947f9f66/runtime?pageLimit=3&detailLimit=4'
kubectl -n ircs-dev exec postgres-0 -- psql -U postgres -d ircs -P pager=off -F ' | ' -A -c <collection_tasks/raw_videos readonly SQL>
kubectl -n ircs-dev exec rabbitmq-0 -- rabbitmqctl list_queues name messages_ready messages_unacknowledged consumers durable --formatter json
```

结果：

- 修复 `TaskQueueDispatchService` / `TaskQueueScraperService` 空关键词语义：未配置
  `filterKeywords` 时不再注入 `"task-runner"`，恢复 V1 全量采集语义。
- 修复 `ListScraperClient` detail token：支持 `{ids}` / `{vod_id}`，与 catalog source preview
  契约对齐。
- 修复 zero-detail page：真实 `iKun资源` 和 `魔都资源` 空结果页可将 PT/MT 收敛为
  `COMPLETED`，DB flush 也写回 `COMPLETED`。
- 修复 fresh start 热状态：`resume=false` 会清理旧 MT/PT Redis hot keys，避免上批
  `DISCOVERED/RUNNING` 残留污染新批次。
- 新增 page window 扩展语义和 dev 页数保护阀：`APP_TASK_QUEUE_INITIAL_PAGE_WINDOW=1`
  控制窗口，`APP_TASK_QUEUE_MAX_PAGES_PER_RUN=1` 控制 dev 单次最多真实处理 1 页；
  生产默认不限制。
- `ircs-task-service` 已 rollout 到
  `harbor.mnnu.eu.org/ircs/ircs-task-service:029-task-runtime-20260613-1850`，运行 digest：
  `sha256:8695c6b7668cffaf53e6b1da7447a0a5487eabaebcd08af2989d75997ff41882`。
- `ircs-scraper-service` 已 rollout 到
  `harbor.mnnu.eu.org/ircs/ircs-scraper-service:029-task-runtime-20260613-1827`，运行 digest：
  `sha256:1ecc85f7931b5277fb1c2a6e1785b23d7abec8e186c59937bcdc972e5e840da7`。
- 真实任务：`全量采集 - 魔都资源`
  (`019ebad3-d653-71c7-8d8f-3cf8947f9f66`)。
- runtime 证据：`pageScheduled=1`、`pageDiscovered=1`、`pageCompleted=1`、
  `detailScheduled=20`、`detailCompleted=20`、`detailSucceeded=20`、`detailFailed=0`、
  `totalPages=4175`、`totalItems=83489`、master/page status 均为 `COMPLETED`。
- DB 粗账本证据：`collection_tasks.status=COMPLETED`、`stat_total_found=83489`、
  `stat_processed=20`、`stat_success=20`、`stat_failed=0`、`stat_end_time` 已写回。
- 数据副作用证据：`raw_videos` 中该 `data_source_id` 当前真实入库 20 条。
- Rabbit 证据：`q.task.page/detail/page.discovered/page.failed/detail.done/master.done`
  主队列 ready/unacked 均为 0，consumers=1；`q.ingest.video` ready/unacked 为 0。
- 运维项：修复 `{ids}` 前的一次真实失败曾留下 `q.task.detail.dlq messages_ready=20`，
  已在后续 ops Rabbit DLQ live retry 中重放并清零。
- 全程未启动本地服务，未使用 fake/mock/造数 payload。

2026-06-13 Rabbit DLQ 运维入口真实集群验证：

```powershell
.\gradlew.bat :services:ircs-ops-service:compileJava
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-ops-service' -ImageName ircs-ops-service -Tag 029-rabbit-dlq-20260613-1915 -Architecture arm64 -Os linux
kubectl apply -f deploy/k8s/dev/ops-service-dev.yaml
kubectl -n ircs-dev rollout status deploy/ircs-ops-service --timeout=180s
kubectl -n ircs-dev exec rabbitmq-0 -- rabbitmqctl list_queues name messages_ready messages_unacknowledged consumers
kubectl -n ircs-dev exec deploy/ircs-ops-service -- wget -q -O - --header <internal-service-headers> http://127.0.0.1:8080/api/v1/ops/rabbit-dlq
kubectl -n ircs-dev exec deploy/ircs-ops-service -- wget -q -O - --post-data x --header <internal-service-headers> 'http://127.0.0.1:8080/api/v1/ops/rabbit-dlq/retry?queueName=q.task.detail.dlq&limit=20'
kubectl -n ircs-dev exec postgres-0 -- psql -U postgres -d ircs -f <readonly verification sql>
```

结果：

- 新增 ops-service Rabbit DLQ 运维 API：
  - `GET /api/v1/ops/rabbit-dlq`：列出 `QueueTopic` 白名单内所有 `.dlq` 队列。
  - `POST /api/v1/ops/rabbit-dlq/retry?queueName=...&limit=N`：从 Rabbit DLQ 拉取消息并重投原 exchange/routing key。
  - `POST /api/v1/ops/rabbit-dlq/discard?queueName=...&limit=N`：从 Rabbit DLQ 拉取并丢弃消息。
  - 单次 action 上限 100 条，只允许操作 `QueueTopic` 声明过的 DLQ，避免任意队列误删。
- `ircs-ops-service` 已 rollout 到
  `harbor.mnnu.eu.org/ircs/ircs-ops-service:029-rabbit-dlq-20260613-1915`，
  运行 digest：
  `sha256:47cd308b9465642c9e475d91f2156a956f8492a02e5fdcf35a7b2fe14f40cddb`。
- live 前置状态：`q.task.detail.dlq messages_ready=20`，`q.task.detail`、
  `q.task.detail.done`、`q.ingest.video` ready/unacked 均为 0。
- ops-service Rabbit DLQ API live 读取到 `q.task.detail.dlq messagesReady=20`。
- 按用户确认“允许污染开发数据库”执行真实 retry：`affected=20`，action 后
  `q.task.detail.dlq messagesReady=0`。
- retry 后 Rabbit 证据：`q.task.detail.dlq`、`q.task.detail`、`q.task.detail.done`、
  `q.ingest.video` ready/unacked 均为 0，没有新增任务 DLQ。
- DB 粗账本证据：真实任务 `019ebad3-d653-71c7-8d8f-3cf8947f9f66`
  仍为 `COMPLETED`，`stat_total_found=83489`，`stat_processed=40`，
  `stat_success=40`，`stat_failed=0`，`stat_end_time=2026-06-13 11:08:57.011`。
- 数据副作用证据：该真实 `data_source_id` 下 `raw_videos` 仍为 20 条，说明
  DLQ 重放走完整处理链路，但业务入库保持幂等/upsert，没有追加重复 raw video。
- worker-job audit 证据：最近 10 分钟 `scraper.task-detail succeeded=20`，
  `task.detail-done succeeded=20`。
- 集群健康：`ircs-ops-service` readiness `UP`，`ircs-dev` 无非 Running Pod。
- 本次未使用 fake/mock/造数，未启动本地服务；`compileJava` 只作为镜像发布前编译守卫，
  不作为功能验收。

2026-06-13 源站失败语义与真实网络矩阵第一轮：

```powershell
.\gradlew.bat --no-daemon :shared:ircs-messaging:compileJava :shared:ircs-messaging:compileTestJava :services:ircs-scraper-service:compileJava :services:ircs-scraper-service:compileTestJava
.\gradlew.bat --no-daemon :services:ircs-catalog-service:compileJava :services:ircs-catalog-service:compileTestJava
kubectl apply -f deploy/k8s/dev/scraper-service-dev.yaml
kubectl apply -f deploy/k8s/dev/catalog-service-dev.yaml
kubectl -n ircs-dev rollout status deploy/ircs-scraper-service --timeout=180s
kubectl -n ircs-dev rollout status deploy/ircs-catalog-service --timeout=180s
kubectl -n ircs-dev exec deploy/ircs-task-service -- wget -q -O - --post-data x http://127.0.0.1:8080/api/v1/collection-tasks/<taskId>/start
kubectl -n ircs-dev exec deploy/ircs-task-service -- wget -q -O - 'http://127.0.0.1:8080/api/v1/collection-tasks/<taskId>/runtime?pageLimit=1&detailLimit=1'
kubectl -n ircs-dev exec rabbitmq-0 -- rabbitmqctl list_queues name messages messages_ready messages_unacknowledged
kubectl -n ircs-dev exec postgres-0 -- psql -U postgres -d ircs -c <readonly verification sql>
```

结果：

- `shared:ircs-messaging` 新增 `TaskSourceTerminalException`，用于区分“源站返回不可处理数据”
  与“Rabbit 毒消息/payload 坏消息”。
- `ListScraperClient` 不再吞掉 `JsonParseException` 返回空列表；HTML/非 JSON 列表响应会让
  PT 明确失败，避免源站停放页被记成 0 条成功。
- scraper-service task consumer 调整源站失败分流：
  - 确定性源站 HTTP 失败和源站非 JSON 失败直接发布 `page.failed` / `detail.done(false)`，
    写 worker-job audit `failed`，不投 Rabbit DLQ。
  - 408/409/425/429/5xx 仍按 retry TTL 重试；重试耗尽后同样落任务失败，不投 DLQ。
  - invalid payload、非源站 fatal 仍保留 DLQ 语义。
- catalog-service `fetch-sample` 现在要求 2xx 响应至少看起来是 JSON；HTML 停放页/跳转页不再被
  Admin 预览误判为成功。
- dev seed 与开发库已刷新两个迁移域名：
  - `旺旺资源`: `https://tyyszy.com`
  - `极速资源`: `https://jszyapi.com`
- 已 rollout：
  - `ircs-task-service` 当前运行
    `harbor.mnnu.eu.org/ircs/ircs-task-service:029-runtime-effective-snapshot-20260613-2000`，
    digest `sha256:5cfa2a92833305052f7eabd2300ecf7030439e5becaa199411dc86a7ba46da96`。
  - `ircs-scraper-service` 当前运行
    `harbor.mnnu.eu.org/ircs/ircs-scraper-service:030-scraper-audit-redaction-20260614-0001`，
    digest `sha256:7fb395f7f2b753846c4e0531eb7c772cff86f07b48244abb19659af472a25508`。
  - `ircs-catalog-service` 当前运行
    `harbor.mnnu.eu.org/ircs/ircs-catalog-service:029-fetch-sample-json-gate-20260613-2035`，
    digest `sha256:9d6bca6dbae33831db769f318137519533da647980b62a93975d25f7fd0d455d`。
- 真实成功链路：
  - `自动采集 - 旺旺资源` 完成，runtime/DB 均 `COMPLETED`，`totalItems=56180`，
    `stat_processed=20`，`stat_success=20`，`stat_failed=0`。
  - `自动采集 - 极速资源` 完成，runtime/DB 均 `COMPLETED`，`totalItems=103872`，
    `stat_processed=20`，`stat_success=20`，`stat_failed=0`。
- 真实非 JSON 失败链路：
  - `自动采集 - 卧龙资源` 重新启动后不再假完成；runtime/DB 均 `FAILED`，
    `lastError=Source terminal failure ... invalid JSON list response ... '<'`，
    `q.task.page.dlq=0`。
  - 集群内验证 `collect.wolongzyw.com`、`collect.wolongzy.cc`、`wolongzy.cc`、`www.wolongzy.cc`
    当前不可解析或 NXDOMAIN；`wolongzyw.com` 当前返回 HTML 停放/lander 页。
- 真实 5xx retry TTL 失败链路：
  - `自动采集 - 猫眼资源` 当前源站返回 502；audit 记录 `retry scheduled 1/3`、
    `2/3`、`3/3`，最终 runtime/DB 均 `FAILED`，`lastError=HTTP status 502 from data source 猫眼资源`。
  - 任务相关主队列、retry 队列、DLQ 最终 ready/unacked 全为 0；没有新增 Rabbit DLQ。
- Admin 源站 preview JSON 矩阵：25 个真实源站中 23 个返回 JSON 成功，2 个失败：
  `卧龙资源` 当前非 JSON/不可用，`猫眼资源` 当前 502。
- 本轮验证全部通过 `ircs-dev` 集群和真实外部源站完成；未启动本地服务，未使用 fake/mock/造数。

## 2026-06-13 Detail Retry and Audit Observability

继续按“只连 `ircs-dev` 集群、只用真实源站/真实任务”的规则补失败路径证据：

- `自动采集 - 茅台资源` 真实运行中出现 detail transient failure，worker-job audit 记录
  `scraper.task-detail failed` 与 `retrying 1/3`、`retrying 2/3`，最终 `20/20`
  detail 成功，runtime/DB `COMPLETED`，任务和 ingestion 队列 ready/unacked 均为 0。
- 临时将 scraper-service retry max retries 调为 0 后复跑 `茅台资源`，源站在限流器时间片控制下
  仍 `20/20` 成功，未制造出 terminal detail failure；随后已恢复
  `APP_SCRAPER_TASK_QUEUE_RETRY_MAX_RETRIES=3`。
- 修复 `TaskQueueScraperService` 失败审计：page/detail task failure 会携带 root cause summary，
  不再只写 `page task failed` / `detail task failed`。
- 修复 `WorkerJobAuditWriter` 过度脱敏：不再因为普通诊断文本中的 `token 'null'`
  整条写成 `[redacted-sensitive-error]`；只脱敏 `Bearer ...` 和
  `token=...` / `access_token=...` 等凭据形态。
- `ircs-scraper-service` 已 rollout 到
  `harbor.mnnu.eu.org/ircs/ircs-scraper-service:030-scraper-audit-redaction-20260614-0001`，
  运行 digest
  `sha256:7fb395f7f2b753846c4e0531eb7c772cff86f07b48244abb19659af472a25508`。
- 复跑真实 `自动采集 - 卧龙资源`
  (`019ebad3-d4c3-7149-8c75-b9814f73effd`)：runtime/DB `FAILED`，
  `pageFailed=1`、`detailScheduled=0`，失败根因为源站返回 HTML/非 JSON。
- worker-job audit 新记录已保留根因：
  `Source terminal failure from data source 卧龙资源: invalid JSON list response ... token 'null' ...`，
  并可对比旧镜像记录仍是 `[redacted-sensitive-error]`。
- Rabbit 证据：`q.task.*` 与 `q.ingest.video` ready/unacked 均为 0；`ircs-dev`
  无非 Running Pod。
- 本轮本地 focused tests 仅作为代码守卫，不计入功能验收；线上功能证据来自 K8S 真实任务。

## 2026-06-13 25 Source One-Page Real Task Matrix

继续使用 `ircs-dev` 现有真实 `collection_tasks`，在 dev 单页保护阀下跑完剩余源站的一页小窗口：

- 第一批 5 个源站：`光速资源`、`奥斯卡资源`、`电影天堂资源`、`非凡资源`、`淘片资源`
  全部 `COMPLETED`，合计 `stat_processed=110`、`stat_success=110`、`stat_failed=0`。
- 第二批 13 个源站：`优质资源站`、`如意资源`、`新浪资源`、`无尽资源`、`暴风资源`、
  `爱奇艺影视`、`牛牛资源`、`索尼资源`、`虎牙资源`、`豆瓣资源`、`豪华资源`、
  `速播资源`、`金鹰资源` 全部 `COMPLETED`，合计 `stat_processed=260`、
  `stat_success=260`、`stat_failed=0`。
- 加上此前已完成的 `魔都资源`、`旺旺资源`、`极速资源`、`茅台资源` 和
  `iKun资源` trend discovery 小窗口，25 个真实源站当前任务覆盖为 `23` 个成功、`2` 个失败。
- 失败项仍是源站 page/source 级：`卧龙资源` 当前非 JSON/不可用，`猫眼资源` 当前 502。
- 最新按源站汇总的成功处理量为 `471` 条 detail/result 成功、`0` 条 detail 失败；这说明
  `detail.done(success=false)` 还没有由真实源站自然触发。
- worker-job audit 汇总：新增 `scraper.task-detail succeeded=370`、
  `task.detail-done succeeded=370`、`scraper.task-page succeeded=18`、
  `task.page-discovered succeeded=18`。
- Rabbit 证据：任务主队列、retry、DLQ 与 `q.ingest.video` ready/unacked 全部为 0；
  `ircs-dev` 无非 Running Pod。

## 2026-06-13 Source Network Strategy Live Matrix

通过 `ircs-dev` 中的 `ircs-catalog-service` 真实 `POST /api/v1/data-sources/fetch-sample`
执行策略矩阵，不改 DB，不启动本地服务：

- 稳定 HTTPS JSON 源：`魔都资源` (`https://www.mdzyapi.com/api.php/provide/vod`)。
  - `AUTO + SYSTEM + AUTO`：HTTP 200，body `6420` bytes。
  - `JDK + HTTP_1_1`：HTTP 200，body `6420` bytes。
  - `APACHE_HC5 + HTTP_1_1`：HTTP 200，body `6420` bytes。
  - `AUTO + HTTP_1_0`：HTTP 200，body `6420` bytes，确认 HTTP/1.0 策略可经 Apache HC5 路由成功。
  - `IPV4_ONLY`：HTTP 200，body `6420` bytes。
  - `IPV4_PREFERRED`：HTTP 200，body `6420` bytes。
  - `IPV6_PREFERRED`：HTTP 200，body `6420` bytes。
  - `DOH` default endpoint：HTTP 200，body `6420` bytes。
  - `IPV6_ONLY`：HTTP 502，当前集群/源站链路不可用。
  - `DOT` default endpoint：HTTP 502，当前集群到 DoT endpoint 不可用或被阻断。
- HTTP JSON 源：`电影天堂资源` (`http://dyttzy.tv/api.php/provide/vod/`)。
  - `AUTO + SYSTEM`：HTTP 200，body `6158` bytes。
  - `AUTO + HTTP_1_0`：HTTP 200，body `6158` bytes。
  - `IPV4_ONLY`：HTTP 200，body `6158` bytes。
  - `DOH` default endpoint：HTTP 200，body `6158` bytes。
  - `DOT` default endpoint：HTTP 502。
- `CURL_NATIVE`：线上 `fetch-sample` 返回 HTTP 502，符合 ADR 中“已持久化但当前 fail-closed，
  等真实源站需要 libcurl 行为时再实现”的边界。

遗留：

- 真实源站 HTTP/DNS/protocol live matrix 已完成核心策略组合；DoT 与 IPv6-only 在当前集群失败，
  需要后续判断是基础设施限制、源站限制还是需要可配置 endpoint。
- `page.failed` 真实失败路径已覆盖；`detail.done(success=false)` 仍需用真实 detail 失败场景补充验证。
- `卧龙资源` 当前没有可用 JSON API；公开索引仍多指向已 NXDOMAIN 的 collect 域名或返回 HTML 的
  `wolongzyw.com`，需要后续找到新接口或改为显式禁用。
- `猫眼资源` 当前外部源站 502，先按源站临时不可用记录，不做盲目配置替换。
- 后续所有验证只允许集群真实数据/真实凭据/真实外链，不再接受本地 smoke 或 fake data。
