# 008 Rabbit + Redis MT/PT/DT Task System

状态：已拍板，分批实现

日期：2026-06-13

## 决策

V3 任务系统采用破坏性新模型：

- RabbitMQ 是任务队列事实入口。
- Redis/Valkey 是运行态热层和原子进度层。
- PostgreSQL 是 MT 粗账本、历史、审计和最终快照的唯一真相。
- 旧 `collection task -> local runner -> scraper execute` 不再作为自动 fallback。

## 设计原则

1. 任务等待/失败策略放在队列层，不塞进锁层。
2. 锁只处理并发冲突和源站限流，例如 provider 时间片、令牌桶、singleton 维护任务。
3. Rabbit consumer 使用 manual ack；成功后 ack，retryable 失败进入延迟重试，fatal 失败 ack 后写 DLQ/audit。
4. DT 数据写入必须幂等，重复投递不能污染业务 DB。
5. Redis 计数和幂等标记必须原子化，避免 `add + expire`、`remove + size` 这类分离窗口。
6. 前端读取 MT snapshot；不把全量 DT 明细塞进 MT snapshot。
7. DB flush 按 dirty MT 低频执行，失败可重跑，不为强一致性阻断业务。

## 队列契约

```text
x.task
  task.page            -> q.task.page             owned by scraper-service
  task.detail          -> q.task.detail           owned by scraper-service
  task.page.discovered -> q.task.page.discovered  owned by task-service
  task.page.failed     -> q.task.page.failed      owned by task-service
  task.detail.done     -> q.task.detail.done      owned by task-service
  task.master.done     -> q.task.master.done      owned by task-service
```

每个 queue 都沿用共享 Rabbit topology 的 DLQ 规则：

```text
q.task.page.dlq
q.task.detail.dlq
q.task.page.discovered.dlq
q.task.page.failed.dlq
q.task.detail.done.dlq
q.task.master.done.dlq
```

每个 queue 额外声明 `.retry` TTL queue。consumer 业务失败时先转投 retry queue；
TTL 到期后消息回到原 queue。达到 retry 上限或 payload 无法解析时转投 DLQ。

## Redis key 草案

```text
ircs:task:mt:{mtId}:snapshot
ircs:task:mt:{mtId}:state
ircs:task:pt:{ptId}:state
ircs:task:pt:{ptId}:details:done
ircs:task:pt:{ptId}:details:failed
ircs:task:pt:{ptId}:details:failed:errors
ircs:task:mt:{mtId}:pages:scheduled
ircs:task:mt:{mtId}:pages:discovered
ircs:task:mt:active
ircs:task:source:{dataSourceId}:masters
ircs:task:mt:dirty
```

## 迁移方式

本 ADR 明确选择 breaking change：

- `TaskCommandService.start/startInternal` 直接调度 MT 到 Rabbit。
- `CollectionTaskCronScheduler` 直接调用 `TaskCommandService.startInternal`。
- 旧 `CollectionTaskRunnerService` 不再注册为 Spring bean。
- 如果 `app.task.queue.enabled=false`，任务标记失败，不回落旧 runner。
- scraper-service 新增 `q.task.page/detail` consumer，但 listener 仍由
  `APP_SCRAPER_TASK_QUEUE_LISTENER_ENABLED` 显式 gate 控制，避免未 rollout 验证前误抓真实源站。

## 当前实现

- task-service 已能 dispatch MT -> PT。
- scraper-service 已能消费 PT/DT，PT 只拆 DT，DT 才抓详情并 publish ingestion。
- task-service 已能消费 `page.discovered` / `detail.done`，更新 Redis PT/MT 计数。
- task-service 已能消费 `page.failed`，更新 Redis PT 失败状态和 MT `pageFailed`。
- scraper-service 在 PT 终局失败时发布 `page.failed`，DT 终局失败时发布
  `detail.done(success=false)`，避免最终失败只停留在 Rabbit DLQ。
- task-service progress consumer 与 scraper-service task consumer 已使用 `ackMode=MANUAL`：
  成功、retry 已发布、DLQ 已发布后 `basicAck`；无法安全处理时 `basicNack(requeue=false)`。
- shared `RabbitTaskFailureClassifier` 已接入 task-service progress consumer 与 scraper-service task
  consumer；确定性 payload/参数/权限/不支持错误直接 DLQ，网络/IO/timeout、Redis/Rabbit/DB
  transient 和未知运行时错误默认 retry。
- scraper-service 已将源站 HTTP 非 2xx 转换为 `RabbitTaskHttpStatusException`；
  408/409/425/429/5xx 走 retry，确定性 4xx 直投 DLQ。`ListScraperClient`
  已拆开 HTTP fetch 与 JSON parse 失败，避免网络/HTTP transient 被包装成 parse fatal。
- task-service 已提供 `GET /api/v1/collection-tasks/{id}/runtime`，
  供 Admin 读取 MT snapshot、master hot counters、PT rows、bounded DT done 样本、
  failed DT 样本、PT backlog/progress/attention 派生字段。
- Admin 任务详情弹窗已基于 runtime 原子字段派生细粒度运行视图：
  MT 速率/新鲜度、PT 当前窗口分布、DT backlog、异常/慢 PT、失败 DT 样本和每页 DT 进度。
- admin-bff 通过既有 `/api/v1/collection-tasks` 前缀透传 runtime endpoint。
- shared `TaskRuntimeHotKeys` 已固化 hot-state key 契约，避免跨服务读热层时复制字符串，
  命名不绑定 Redis；当前实现仍使用 Redis/Valkey 作为热层存储。
- ops-service 已提供 `GET /api/v1/dashboard/task-runtime`，汇总 active MT hot runtime、
  dirty MT 数量、page/detail counters、status/attention counts 和每个 MT 的轻量进度摘要。
- V1 admin `Dashboard > 系统监控` 已接入 `/api/v1/dashboard/task-runtime` 全局运行态面板，
  以 3s polling 展示 active/dirty MT、PT/DT counters、detail backlog、attention/status counts
  和 active MT 摘要。
- ops-service queue metrics 已补齐 `TASK_PAGE_FAILED` descriptor，Task queue/DLQ 分组完整。
- task-service 已实现 Redis dirty MT -> DB `collection_tasks` 粗账本 flush：
  周期 flush 由 `app.task.snapshot.flush.enabled` gate 控制，并使用分布式锁；
  `q.task.master.done` consumer 会写 Redis master state 并立即 final flush。
- task-service 已实现 page window 扩展基础语义：初始页写入 `pages:scheduled`，
  page terminal 后按 `initialPageWindow` 原子抢占下一页；dev 通过
  `APP_TASK_QUEUE_MAX_PAGES_PER_RUN=1` 限制真实测试页数，生产默认不限制。
- task-service fresh start 会清理旧 MT/PT hot state；resume 才继承热状态。
- scraper-service detail URL token 支持 `{ids}` / `{vod_id}` / `{id}` / `{sourceVid}`；
  空任务关键词保持为空，不再注入 `"task-runner"`。
- 2026-06-13 已完成 dev 集群 rollout 与真实入口验证：
  `ircs-admin-frontend` 运行 digest
  `sha256:132b7d3bb073734ab8072769521e94885b25f8e9e319253e7c1c433d87c91302`，
  `ircs-ops-service` 运行 digest
  `sha256:134ef7fb99c684f0c143640b4d2d9731ede05d97671c50e8d7c96e9f25753015`；
  通过 gateway port-forward 完成 PoW + admin login + JWT + dashboard runtime API 200。
- 2026-06-13 已打开 task-service/scraper-service Rabbit listener gate 与 task-service snapshot
  flush gate，完成 `ircs-dev` 基础 rollout：
  `ircs-task-service@sha256:3335df58e1f94ca07f5ec0913e4e59b4df252f259b413b8be398ad66f5621e7b`，
  `ircs-scraper-service@sha256:575c7a65ae334b36e7d82f1dd867fbf00c14ee46d43dbeb898e513fc2f6f825a`。
  RabbitMQ 中 `q.task.page/detail/page.discovered/page.failed/detail.done/master.done` 主队列均已
  durable 声明且 consumers=1；对应 retry/dlq 队列已声明。
- 后续 029 验证只允许连接集群使用真实数据/真实凭据/真实外部链路；禁止本地 smoke 和
  mock/fake/造数验证。
- 2026-06-13 已完成真实 MT/PT/DT 端到端验证：
  `全量采集 - 魔都资源` 第 1 页真实发现 20 条 detail，20/20 detail 成功，
  `raw_videos` 入库 20 条；Redis runtime 与 DB `collection_tasks` 均收敛为
  `COMPLETED`，`stat_total_found=83489`、`stat_processed=20`、`stat_success=20`。
  当前运行 digest：`task-service sha256:8695c6b7668cffaf53e6b1da7447a0a5487eabaebcd08af2989d75997ff41882`，
  `scraper-service sha256:1ecc85f7931b5277fb1c2a6e1785b23d7abec8e186c59937bcdc972e5e840da7`。
- ops-service 已新增 Rabbit broker DLQ 运维入口：
  `GET /api/v1/ops/rabbit-dlq`、`POST /api/v1/ops/rabbit-dlq/retry`、
  `POST /api/v1/ops/rabbit-dlq/discard`；只允许 `QueueTopic` 白名单 DLQ，单次 action
  上限 100 条。`ircs-ops-service` 已 rollout 到
  `harbor.mnnu.eu.org/ircs/ircs-ops-service:029-rabbit-dlq-20260613-1915`，
  digest `sha256:47cd308b9465642c9e475d91f2156a956f8492a02e5fdcf35a7b2fe14f40cddb`。
- 2026-06-13 已用修复前真实失败留下的 `q.task.detail.dlq messages_ready=20` 完成
  Rabbit DLQ live retry：ops API 读取 20 条，retry `affected=20`，随后
  `q.task.detail.dlq/q.task.detail/q.task.detail.done/q.ingest.video` ready/unacked 均为 0。
  真实任务 `019ebad3-d653-71c7-8d8f-3cf8947f9f66` DB 粗账本变为
  `stat_processed=40`、`stat_success=40`、`stat_failed=0`；`raw_videos` 仍为 20 条，
  worker-job audit 写入 `scraper.task-detail succeeded=20` 和 `task.detail-done succeeded=20`。
- 2026-06-13 已明确源站业务失败不等于 Rabbit 毒消息：
  - `TaskSourceTerminalException` 表达非 JSON/不可处理源站响应。
  - `ListScraperClient` 不再把 HTML/非 JSON 列表响应吞成空成功。
  - 源站 HTTP 4xx、非 JSON 响应、以及 408/409/425/429/5xx 重试耗尽后，均落
    `page.failed` / `detail.done(false)` 和 worker-job audit `failed`，不进入 Rabbit DLQ。
  - invalid payload、非源站 fatal 仍进入 DLQ。
- 2026-06-13 已完成源站 live 第一轮：
  - `旺旺资源` 更新为 `https://tyyszy.com`，`极速资源` 更新为 `https://jszyapi.com`。
  - `自动采集 - 旺旺资源` 与 `自动采集 - 极速资源` 均真实完成 `20/20` detail。
  - `自动采集 - 卧龙资源` 因 HTML lander 页失败，runtime/DB `FAILED`，DLQ 为 0。
  - `自动采集 - 猫眼资源` 当前源站 502，经历 3 次 retry 后 runtime/DB `FAILED`，DLQ 为 0。
  - Admin source preview 已加 JSON gate；25 个真实源站中 23 个 JSON 成功，失败项为
    `卧龙资源` 和 `猫眼资源`。
  - 当前运行 digest：`task-service sha256:5cfa2a92833305052f7eabd2300ecf7030439e5becaa199411dc86a7ba46da96`，
    `scraper-service sha256:7fb395f7f2b753846c4e0531eb7c772cff86f07b48244abb19659af472a25508`，
    `catalog-service sha256:9d6bca6dbae33831db769f318137519533da647980b62a93975d25f7fd0d455d`。
- 2026-06-13 已完成 trend discovery 小窗口真实验证：
  task-service internal schedule 创建并排队 `019ec172-e5dc-75a4-9bc2-bc4432c01b60`，
  Rabbit MT/PT/DT runtime、DB flush、worker-job audit 和 queue drain 均收敛成功。
- 2026-06-13 已补 worker-job audit 失败根因可观测性：page/detail task failure 保留
  root cause summary；审计脱敏只匹配真实凭据形态，不再把 JSON parser 中的普通
  `token 'null'` 诊断整条隐藏。真实 `卧龙资源` page failure 已验证 audit 保留根因，
  task/ingest queues drain 为 0。
- 2026-06-13 已完成 25 个真实源站一页任务矩阵当前轮覆盖：`23/25` 源站成功，
  `2/25` 失败为 page/source 级；成功源站最新任务合计 `471` 条 detail/result 成功、
  `0` 条 detail 失败。任务队列、retry、DLQ 与 ingestion 队列最终均清零。
- 2026-06-13 已完成源站网络核心策略 live matrix：`魔都资源` 和 `电影天堂资源`
  分别覆盖 HTTPS/HTTP 源；`AUTO/SYSTEM`、`JDK HTTP_1_1`、`APACHE_HC5 HTTP_1_1`、
  `HTTP_1_0`、`IPV4_ONLY`、`IPV4_PREFERRED`、`IPV6_PREFERRED`、`DOH` 在对应源上成功；
  `DOT` 与 `IPV6_ONLY` 当前返回 502，`CURL_NATIVE` 保持 future/fail-closed。

## 待实现

- 源站网络矩阵：核心策略组合已完成；还需判定 `DOT` / `IPV6_ONLY` 失败原因和是否需要
  配置化 endpoint 或基础设施放行。
- 失败路径矩阵：`page.failed` 真实失败路径已覆盖；还需用真实 detail 失败补
  `detail.done(success=false)` 终局路径。
- trend-sync provider-gated 全链路仍需从 scraper provider 聚合入口做一次真实外部窗口验证。
- 源站状态风险：`卧龙资源` 当前无可用 JSON API；`猫眼资源` 当前外部 502，先记录为源站不可用。
