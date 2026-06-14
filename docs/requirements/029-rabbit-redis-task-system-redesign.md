# 029 Rabbit + Redis Task System Redesign

状态：进行中

日期：2026-06-13

## 目标

将 V3 采集任务系统从旧的单体式 `collection task -> local runner -> scraper execute`
破坏性切换为 Rabbit 主队列、Redis 热状态、DB 粗账本的 MT/PT/DT 分层任务模型。

本需求不做缓慢兼容迁移：手动启动、恢复、cron 和 trend discovery 触发后的任务执行入口都必须进入
新的 Rabbit 任务队列；旧本地 runner 不再作为自动 fallback。

## 架构

```text
+-------------------+        +-----------------------+        +-----------------------+
| task-service       |        | RabbitMQ x.task       |        | scraper-service       |
| start/resume/cron  +------->+ q.task.page           +------->+ PT consumer           |
| MT dispatcher      |        | q.task.detail         +------->+ DT consumer           |
+---------+---------+        | q.task.page.discovered|<-------+ PT split result       |
          |                  | q.task.page.failed    |<-------+ PT terminal failure  |
          |                  | q.task.detail.done    |<-------+ DT terminal result    |
          v                  +-----------+-----------+        +-----------+-----------+
                                      |
                                      v
                             +-----------------------+
                             | task-service          |
                             | progress consumers    |
                             | MT aggregator/flush   |
                             +-----------+-----------+
                                         |
                                         v
+-------------------+                                             |
| Redis hot layer   |<--------------------------------------------+
| mt snapshot       |        atomic Lua progress updates
| pt/dt state       |
| dirty mt index    |
+---------+---------+
          |
          | periodic/final flush
          v
+-------------------+
| PostgreSQL        |
| collection_tasks  |
| MT coarse ledger  |
| history/audit     |
+-------------------+
```

## 任务模型

- MT：Master Task，对应一次任务启动或恢复，是前端主要查询对象。
- PT：Page Task，由 MT 拆出，负责列表页抓取、分页发现和 DT 拆分。
- DT：Detail Task，由 PT 拆出，负责详情页抓取、映射、入库和 ingestion publish。
- DT 完成后原子更新 PT/MT Redis 计数；PT 全部完成后回写 MT；MT 完成后进入 final flush。

## 存储职责

- RabbitMQ：唯一任务调度队列，负责 PT/DT 投递、ack、retry、DLQ。
- Redis/Valkey：运行态热状态、MT snapshot、PT/DT counter、幂等去重、dirty MT index。
- PostgreSQL：DB 是唯一真相，只保存 MT 粗账本、最终状态、历史、审计和周期快照。

## 完成标准

1. `QueueTopic` 声明 `x.task` 与 `q.task.page/detail/page.discovered/page.failed/detail.done/master.done`。
2. task-service 启动任务后只投递 Rabbit PT，不再自动走旧本地 runner。
3. Redis 写入 `mt:{id}:snapshot` 等价热读模型，并维护 active/source/dirty index。
4. DT 完成计数使用 Redis Lua 原子更新，避免 add+expire/remove+size 分离问题重复出现。
5. scraper-service 消费 PT/DT：PT 只拆 DT，DT 才做 detail fetch、mapping 和 ingestion publish。
6. 后续 consumer 具备 manual ack、延迟 retry、fatal DLQ 和幂等入库语义。
7. DB flush 只做低频 MT 粗账本和最终状态，不对每个 DT 做同步写。
8. Admin 可通过 `GET /api/v1/collection-tasks/{id}/runtime` 查看 MT/PT/DT 运行态，
   展示 master 计数、page task 明细、bounded detail done 样本和 failed detail 样本。
9. Ops 可通过 `GET /api/v1/dashboard/task-runtime` 查看全局 active MT hot runtime 汇总，
   用于 live rollout 时观察多任务进度、失败、backlog 和 dirty snapshot flush 压力。
10. Ops 可通过 `GET/POST /api/v1/ops/rabbit-dlq` 管理 Rabbit broker 中真实 DLQ：
    只允许 `QueueTopic` 白名单内 DLQ，支持 list/retry/discard。
11. docs/tasks 记录测试命令、未完成 consumer/flush/live rollout 边界。

## 非目标

- 不保留旧本地 runner 自动 fallback。
- 不在本切片完成所有真实资源站 live 测试。
- 不引入新的独立 task-worker 服务；先由 task-service 承接 owner worker。
- 不做物理库拆分或完整压测。

## 风险

- 在 PT/DT consumer 完成前，启动任务会进入 Rabbit page queue，但不会完成端到端采集。
- Redis 不可用会导致新任务调度失败；这是破坏性新模型的必需依赖。
- 仍需 live verification 验证 retry TTL、page.failed/detail.failed 失败路径和更多真实源站网络矩阵。
- 自 2026-06-13 起，029 后续验证禁止本地 smoke、禁止 mock/fake/造数；只能连接
  `ircs-dev` 集群，使用真实配置、真实凭据、真实集群数据和真实外部链路。

## 2026-06-13 Admin Runtime View

新增 `GET /api/v1/collection-tasks/{id}/runtime`，用于 Admin 详情弹窗读取更细的运行态：

- DB `collection_tasks`：提供任务基础配置和 DB 粗账本兜底。
- Redis `mt:{id}:snapshot`：提供 MT 快照。
- Redis `mt:{id}:state`：提供 master hot counters。
- Redis `mt:{id}:pages:discovered` + `pt:{id}:state`：提供 PT 页任务列表。
- Redis `pt:{id}:details:done`：提供每页 bounded DT 完成样本。
- Redis `pt:{id}:details:failed` + `pt:{id}:details:failed:errors`：提供每页 bounded
  DT 失败样本和 sourceVid/error 摘要。

Admin 列表页继续保持轻量，不对每行拉 runtime；只有打开详情弹窗时才轮询 runtime。

2026-06-13 追加：Admin runtime 面板从基础 MT/PT/DT 列表升级为细粒度排障视图。
runtime API 仍保留 MT/PT/DT 原子字段，同时新增 `failedDetailTaskIds`、
`failedDetailErrors`、`detailBacklog`、`progressPercent`、`attentionLevel` 等排障字段。
Admin UI 基于这些字段展示 PT 当前窗口分布、DT backlog、成功/失败率、状态新鲜度、
需要关注的 PT、每页 DT 进度、失败 DT 样本和更新时间，不把组合展示反写进业务 DB。

## 2026-06-13 Ops Task Runtime Overview

新增 `GET /api/v1/dashboard/task-runtime`，用于 ops/dashboard 读取全局 active MT 热层摘要：

- `TaskRuntimeHotKeys` 作为共享 hot-state key 契约，不把命名绑定到 Redis 实现；当前实现仍由
  Redis/Valkey 承载热层状态。
- ops-service 从 active MT index 读取 master snapshot 和 master state，只返回 MT 摘要，
  不展开 PT/DT 明细，避免运维首页对热层产生过大读压力。
- response 汇总 active/returned/dirty MT 数量、page/detail counters、detail backlog、
  `statusCounts`、`attentionCounts` 和每个 MT 的 progress/attention/error/freshness 字段。
- `QueueTopicDescriptor` 已补齐 `TASK_PAGE_FAILED`，ops queue metrics 可完整展示 task queues
  及其 DLQ。
- V1 admin `Dashboard > 系统监控` 已接入全局任务运行态面板：
  `dashboardApi.getTaskRuntimeOverview` 以 3s polling 读取该 endpoint，系统监控页展示 active/dirty MT、
  PT/DT counters、detail backlog、attention/status counts 和前 8 个 active MT 摘要行。
- 单任务深度排障仍走 `/api/v1/collection-tasks/{id}/runtime`。

2026-06-13 集群实测：

- `ircs-ops-service:dev` 已 rollout 到 `ircs-dev`，运行 digest 为
  `sha256:134ef7fb99c684f0c143640b4d2d9731ede05d97671c50e8d7c96e9f25753015`。
- `ircs-admin-frontend` 已 rollout 到
  `harbor.mnnu.eu.org/ircs/ircs-frontend:029-task-runtime-20260613-1556`，运行 digest 为
  `sha256:132b7d3bb073734ab8072769521e94885b25f8e9e319253e7c1c433d87c91302`。
- 通过 `kubectl port-forward svc/ircs-frontend-gateway 18088:8080` 走真实 gateway：
  PoW challenge 200、admin login 200、`GET /api/v1/dashboard/task-runtime?limit=3` 200。
  当前集群真实任务热层为空，返回 `activeMasterCount=0`、`returnedMasterCount=0`、
  `detailBacklog=0`。
- `identity-service` dev manifest 已补 `SECURITY_ADMIN_PASSWORD` 从
  `ircs-dev-secrets.ADMIN_PASSWORD` 注入，恢复 runtime secret 优先于 DB/default seed 的登录语义。

## 2026-06-13 DB Flush

新增 Redis dirty MT -> DB 粗账本 flush：

- `TaskSnapshotFlushService` 扫描 `ircs:task:mt:dirty`，读取 MT/PT Redis state，
  将汇总后的 `status/current_page/stat_total_found/stat_processed/stat_success/stat_failed`
  写回 `collection_tasks`。
- `TaskSnapshotFlushScheduler` 通过 `app.task.snapshot.flush.enabled` 显式 gate 控制，
  使用 task-service 现有分布式锁避免多副本重复 flush。
- `q.task.master.done` consumer 会先写 Redis master state，再立即触发 `flushOne`，
  作为完成事件的 final flush。
- 非终态 flush 不覆盖管理员手动 `PAUSED/STOPPING` 状态；终态 `COMPLETED/FAILED`
  会清理 dirty marker，并从 active MT index 移除。

## 2026-06-13 Retry/DLQ First Slice

新增任务队列延迟 retry/DLQ 第一刀：

- `QueueTopic` 增加 `retryName()` / `retryRoutingKey()`，共享 Rabbit topology 为每个 queue
  声明 `.retry` TTL queue，TTL 到期后 dead-letter 回原 exchange/routing key。
- `RabbitTaskRetryPublisher` 负责保留原消息体并追加 `x-ircs-retry-count`、
  `x-ircs-disposition`、原 routing key、错误类型/摘要等 headers。
- `RabbitTaskFailureClassifier` 已作为共享 fatal/retryable 分类层接入 task-service progress consumer
  和 scraper task consumer：`IllegalArgumentException`、`NoSuchElementException`、
  `UnsupportedOperationException`、`SecurityException` 等确定性错误直接 DLQ；网络/IO/timeout、
  Redis/Rabbit/DB transient 和未知运行时错误默认先 retry。
- scraper-service 已将源站 HTTP 非 2xx 转换为 `RabbitTaskHttpStatusException`：
  408/409/425/429/5xx 进入 retry，400/401/403/404/410/422 等确定性 4xx 直接 DLQ；
  旧有 `upstream status NNN` 消息也由 classifier 兜底识别。
- `ListScraperClient.fetchListPage` 已拆开 HTTP fetch 与 JSON parse 失败，避免网络/HTTP transient
  被误包成 parse/config fatal。
- task-service progress consumer 和 scraper task consumer 对 retryable 业务失败先转投 retry；
  达到 `APP_*_TASK_QUEUE_RETRY_MAX_RETRIES` 后转投 DLQ 并写 worker-job audit。
- 不可解析 payload 直接 DLQ，避免无限重试坏消息。
- 新增 `q.task.page.failed`：PT 终局失败会写 Redis page state 和 master `pageFailed`，
  DT 终局失败会发布 `detail.done(success=false)`，避免 MT/PT runtime 永远卡在 backlog。
- task-service progress consumer 与 scraper-service task consumer 已切换到 channel-level manual ack；
  成功、retry 已发布、DLQ 已发布后显式 `basicAck`，无法安全转投时 `basicNack(requeue=false)`。
- live Rabbit verification 不得用本地 smoke 或 fake payload 代替。

## 2026-06-13 Rabbit Listener Live Rollout

`ircs-dev` 已打开 task-service/scraper-service 的 Rabbit listener gate 与 task-service snapshot
flush gate：

- `ircs-task-service` 镜像：
  `harbor.mnnu.eu.org/ircs/ircs-task-service:029-task-runtime-20260613-1720`，
  运行 digest `sha256:3335df58e1f94ca07f5ec0913e4e59b4df252f259b413b8be398ad66f5621e7b`。
- `ircs-scraper-service` 镜像：
  `harbor.mnnu.eu.org/ircs/ircs-scraper-service:029-task-runtime-20260613-1720`，
  运行 digest `sha256:575c7a65ae334b36e7d82f1dd867fbf00c14ee46d43dbeb898e513fc2f6f825a`。
- dev manifest 使用 `APP_TASK_QUEUE_LISTENER_ENABLED=true`、
  `APP_TASK_SNAPSHOT_FLUSH_ENABLED=true`、`APP_TASK_QUEUE_INITIAL_PAGE_WINDOW=1`、
  `APP_SCRAPER_TASK_QUEUE_LISTENER_ENABLED=true`。
- RabbitMQ live queue evidence：
  `q.task.page/detail/page.discovered/page.failed/detail.done/master.done` 6 个主队列均 durable、
  messages_ready=0、messages_unacknowledged=0、consumers=1；对应 retry/dlq 队列已声明且
  consumers=0。
- `RabbitTaskRetryPublisher` 已修复为可扫描 bean，避免 task/scraper consumer 因缺少 shared
  retry publisher 进入 CrashLoop。
- `autoStartGate` actuator component 在 task-service 与 scraper-service 均为 UP。
- gateway cluster port-forward 首页返回 HTTP 200。

## 2026-06-13 Real MT/PT/DT E2E

已使用 `ircs-dev` 集群内已有真实任务和真实源站完成小窗口端到端验证，未使用本地服务、
mock/fake payload 或造数任务：

- 真实任务：`全量采集 - 魔都资源`
  (`019ebad3-d653-71c7-8d8f-3cf8947f9f66`)。
- `task-service` 运行镜像：
  `harbor.mnnu.eu.org/ircs/ircs-task-service:029-task-runtime-20260613-1850`，
  digest `sha256:8695c6b7668cffaf53e6b1da7447a0a5487eabaebcd08af2989d75997ff41882`。
- `scraper-service` 运行镜像：
  `harbor.mnnu.eu.org/ircs/ircs-scraper-service:029-task-runtime-20260613-1827`，
  digest `sha256:1ecc85f7931b5277fb1c2a6e1785b23d7abec8e186c59937bcdc972e5e840da7`。
- 修复队列任务空关键词错误：空 `filterKeywords` 不再注入 `"task-runner"`，恢复 V1
  全量采集语义。
- 修复 detail URL token：`{ids}` / `{vod_id}` 与 `{id}` / `{sourceVid}` 同等替换。
- fresh start 会清理旧 MT/PT hot state；`resume=true` 才继承热状态。
- dev manifest 启用 `APP_TASK_QUEUE_MAX_PAGES_PER_RUN=1`，生产默认不限制；与
  `APP_TASK_QUEUE_INITIAL_PAGE_WINDOW=1` 一起保证 dev 真实测试只跑 1 页。
- 真实 runtime：`pageScheduled=1`、`pageCompleted=1`、`detailScheduled=20`、
  `detailCompleted=20`、`detailSucceeded=20`、`detailFailed=0`、`totalItems=83489`，
  MT/PT 均为 `COMPLETED`。
- DB 粗账本：`collection_tasks.status=COMPLETED`、`stat_processed=20`、
  `stat_success=20`、`stat_failed=0`、`stat_end_time` 已写回。
- 数据副作用：该真实 `data_source_id` 下 `raw_videos` 入库 20 条。
- Rabbit：任务主队列与 `q.ingest.video` ready/unacked 均为 0；修复前真实失败曾留下
  `q.task.detail.dlq messages_ready=20`，已在后续 ops Rabbit DLQ live retry 中重放并清零。

## 2026-06-13 Rabbit DLQ Ops Live Verification

已补齐 ops-service Rabbit broker DLQ 运维入口，并用 `ircs-dev` 集群内真实
`q.task.detail.dlq` 残留消息完成 live retry 验证：

- 新增 `GET /api/v1/ops/rabbit-dlq`，列出 `QueueTopic` 白名单内所有 `.dlq` 队列。
- 新增 `POST /api/v1/ops/rabbit-dlq/retry?queueName=...&limit=N`，从 DLQ 拉取消息并
  重投原 exchange/routing key。
- 新增 `POST /api/v1/ops/rabbit-dlq/discard?queueName=...&limit=N`，从 DLQ 拉取并丢弃消息。
- 单次 action 上限 100 条，未知 queueName 直接拒绝，避免任意 Rabbit 队列误操作。
- `ircs-ops-service` 已 rollout 到
  `harbor.mnnu.eu.org/ircs/ircs-ops-service:029-rabbit-dlq-20260613-1915`，
  digest `sha256:47cd308b9465642c9e475d91f2156a956f8492a02e5fdcf35a7b2fe14f40cddb`。
- live retry 前：`q.task.detail.dlq messages_ready=20`，主链路队列 ready/unacked 均为 0。
- ops API live list 读取到 `q.task.detail.dlq messagesReady=20`。
- 经用户确认允许开发库真实重放污染后，执行
  `POST /api/v1/ops/rabbit-dlq/retry?queueName=q.task.detail.dlq&limit=20`：
  返回 `affected=20`，action 后 `messagesReady=0`。
- retry 后 Rabbit 证据：`q.task.detail.dlq`、`q.task.detail`、`q.task.detail.done`、
  `q.ingest.video` ready/unacked 均为 0，没有新增任务 DLQ。
- DB 证据：真实任务 `019ebad3-d653-71c7-8d8f-3cf8947f9f66` 仍为 `COMPLETED`，
  `stat_processed=40`、`stat_success=40`、`stat_failed=0`，`raw_videos` 对应真实
  source 仍为 20 条，证明重放走完整处理链路且入库保持幂等/upsert。
- worker-job audit 证据：最近 10 分钟 `scraper.task-detail succeeded=20`、
  `task.detail-done succeeded=20`。
- `ircs-ops-service` readiness `UP`，`ircs-dev` 无非 Running Pod。
- 本次不使用本地 smoke，不使用 fake/mock/造数；`compileJava` 仅为镜像发布前编译守卫。

## 2026-06-13 Source Failure Semantics Live Verification

已补源站业务失败与 Rabbit 毒消息的边界，并用 `ircs-dev` 真实源站验证：

- `TaskSourceTerminalException` 表达源站返回不可处理数据，和 invalid payload/代码契约坏消息分离。
- `ListScraperClient` 遇到 HTML/非 JSON 列表响应不再返回空列表，而是让 PT 终态失败。
- 源站 HTTP 4xx、非 JSON 响应，以及 408/409/425/429/5xx 重试耗尽后，都落
  `page.failed` / `detail.done(false)` + worker-job audit `failed`，不投 Rabbit DLQ。
- catalog-service `fetch-sample` 增加 JSON gate，Admin 预览不再把 HTML 停放页算成功。
- `旺旺资源` 已切到 `https://tyyszy.com`，`极速资源` 已切到 `https://jszyapi.com`。
- 当前运行 digest：
  - `ircs-task-service@sha256:5cfa2a92833305052f7eabd2300ecf7030439e5becaa199411dc86a7ba46da96`
  - `ircs-scraper-service@sha256:7fb395f7f2b753846c4e0531eb7c772cff86f07b48244abb19659af472a25508`
  - `ircs-catalog-service@sha256:9d6bca6dbae33831db769f318137519533da647980b62a93975d25f7fd0d455d`
- 真实成功任务：
  - `自动采集 - 旺旺资源`：runtime/DB `COMPLETED`，`20/20` detail 成功，
    `totalItems=56180`。
  - `自动采集 - 极速资源`：runtime/DB `COMPLETED`，`20/20` detail 成功，
    `totalItems=103872`。
- 真实失败任务：
  - `自动采集 - 卧龙资源`：HTML `lander` 页触发 invalid JSON，runtime/DB `FAILED`，
    `q.task.page.dlq=0`。
  - `自动采集 - 猫眼资源`：当前源站 HTTP 502，经历 retry `1/3 -> 2/3 -> 3/3` 后
    runtime/DB `FAILED`，任务相关 DLQ 均为 0。
- 源站 preview JSON 矩阵第一轮：25 个真实源站中 23 个 JSON 成功；失败项为
  `卧龙资源`（当前 API 不可用/非 JSON）和 `猫眼资源`（当前 502）。
- 本轮仍未使用本地服务、fake/mock payload 或造数任务。
- trend discovery 真实 K8S 小窗口已完成：
  - 通过 task-service internal endpoint 调度真实关键词 `那位天才对伪装女友过分执着`。
  - 生成 `趋势发现 - 那位天才对伪装女友过分执着 - iKun资源 - 8817b8c4`，
    taskId `019ec172-e5dc-75a4-9bc2-bc4432c01b60`。
  - MT/PT/DT runtime 与 DB 均 `COMPLETED`，`detailSucceeded=1`、`detailFailed=0`。
  - Rabbit task/ingest queues ready/unacked 均为 0，worker-job audit 可追踪到 task-service 与
    scraper-service 的具体 Pod source。
- worker-job audit 失败根因可观测性已补强：
  - `TaskQueueScraperService` 的 page/detail task failure 保留 root cause summary。
  - `WorkerJobAuditWriter` 不再把普通 parser 诊断里的 `token 'null'` 误判为敏感信息；
    只脱敏 `Bearer ...`、`token=...`、`access_token=...` 等凭据形态。
  - `ircs-scraper-service` 已 rollout 到
    `030-scraper-audit-redaction-20260614-0001`，digest
    `sha256:7fb395f7f2b753846c4e0531eb7c772cff86f07b48244abb19659af472a25508`。
  - 复跑真实 `自动采集 - 卧龙资源`
    (`019ebad3-d4c3-7149-8c75-b9814f73effd`)：runtime/DB `FAILED`，
    audit 保留 `invalid JSON list response ... token 'null' ...` 根因；Rabbit task/ingest
    queues ready/unacked 均为 0。
- 25 个真实源站一页任务矩阵已完成当前轮覆盖：
  - `23/25` 源站成功，`2/25` 失败为 page/source 级（卧龙非 JSON、猫眼 502）。
  - 成功源站最新任务合计 `471` 条 detail/result 成功、`0` 条 detail 失败。
  - 新增批次 worker-job audit 记录 `scraper.task-detail succeeded=370`、
    `task.detail-done succeeded=370`；任务队列、retry、DLQ 和 ingestion 队列最终均清零。
  - 真实源站尚未自然触发 `detail.done(success=false)`，该路径仍作为剩余验证项。
- 源站网络策略核心矩阵已通过 catalog-service 真实 `fetch-sample` 验证：
  - `魔都资源` HTTPS JSON 源：`AUTO/SYSTEM`、`JDK HTTP_1_1`、`APACHE_HC5 HTTP_1_1`、
    `HTTP_1_0`、`IPV4_ONLY`、`IPV4_PREFERRED`、`IPV6_PREFERRED`、`DOH` 均 HTTP 200；
    `IPV6_ONLY` 和 `DOT` 当前 HTTP 502。
  - `电影天堂资源` HTTP JSON 源：`AUTO/SYSTEM`、`HTTP_1_0`、`IPV4_ONLY`、`DOH`
    均 HTTP 200；`DOT` 当前 HTTP 502。
  - `CURL_NATIVE` 当前线上 HTTP 502，符合已拍板的 future/fail-closed 边界。

下一步必须继续用集群真实数据补真实 detail 失败场景下的 `detail.done(success=false)`
终局路径；DoT/IPV6_ONLY 需要继续判定是基础设施限制、源站限制还是 endpoint 配置问题；
trend-sync 还需从 scraper provider 聚合入口补 provider-gated 全链路真实窗口。
