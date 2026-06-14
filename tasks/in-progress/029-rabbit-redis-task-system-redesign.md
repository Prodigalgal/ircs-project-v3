# 029 Rabbit + Hot-State Task System Redesign

状态：进行中

日期：2026-06-14

来源：

- `docs/requirements/029-rabbit-redis-task-system-redesign.md`
- `docs/decisions/008-rabbit-redis-mt-pt-dt-task-system.md`
- 完整历史流水归档：`docs/reports/archive/2026-06-13-doc-compaction/029-rabbit-redis-task-system-redesign-full-log.md`

## 目标

破坏性重构采集任务执行模型：

- RabbitMQ 承载 MT/PT/DT 任务流与 retry/DLQ。
- hot-state store 承载 MT snapshot、PT/DT 进度、active/dirty index。
- DB 只保留 MT 粗账本、周期/终态 flush 和长期查询入口。
- Admin/Ops 通过 runtime API 查看细粒度队列进度和失败样本。

## 当前架构

```text
Admin/Ops
  |
  | start/resume/runtime/DLQ ops
  v
task-service
  |-- after-commit publish q.task.page
  |-- consume page.discovered/detail.done/master.done
  |-- write hot-state + DB ledger flush
  |
  v
RabbitMQ x.task
  | q.task.page -> scraper-service -> q.task.detail
  | q.task.detail -> scraper-service -> q.ingest.video + q.task.detail.done
  | retry TTL queues -> original queues
  | DLQ queues -> ops-service retry/discard
  v
hot-state store + DB
```

## 已完成能力

- `QueueTopic`、shared contracts、shared topology 已声明 `q.task.page`、
  `q.task.detail`、`q.task.page.discovered`、`q.task.page.failed`、
  `q.task.detail.done`、`q.task.master.done` 及 retry/DLQ。
- `TaskCommandService` start/resume/startInternal/cron 已切到 Rabbit dispatch，
  旧本地 runner 自动入口不再 fallback。
- scraper-service 已消费 PT/DT：PT 抓列表并拆 DT，DT 抓详情、mapping、publish ingestion。
- task-service 已聚合 page/detail/master progress，写 hot-state 和 DB 粗账本 flush。
- retry/DLQ 已支持 manual ack/nack、retry header、failure classifier、ops retry/discard API。
- Admin 任务详情和 Ops dashboard 已展示 MT/PT/DT runtime、失败 DT 样本、队列/DLQ 指标。
- worker-job audit 已覆盖 task/scraper/ops 关键 consumer/runner，并带唯一 `job_source`。
- source network hardening 已接入 Apache HC5、HTTP/1.0、DoH/DoT、IP policy、超时和 UA 策略。

## 线上版本

`ircs-dev` 当前 029 相关运行 digest：

- task-service：
  `sha256:5cfa2a92833305052f7eabd2300ecf7030439e5becaa199411dc86a7ba46da96`
- scraper-service：
  `sha256:318954490aec86fa98aeacd69577bfab2f4c122312ef3ec51e75d1257199ec25`
- content-service：
  `sha256:67c6c5e0315445bdf77a76412ca0f7c6db5f8a0954b07395ff4eacf4da4de981`
- search-service：
  `sha256:b64220ab2285b0667ce92ad22c9993b726cbc4e49fbabe29fdcb642ab8c639a3`
- catalog-service：
  `sha256:0a1c221fc60be117bf4f3681da5441aa1aa8ae46cb4ae86f94a97ffcd06656a9`
- ops-service：
  `sha256:a674d3304f704ab410f1b43e7ff49b4a43fc2c12fa7a47afe4e736de091a0ad9`

## 真实验证证据

验证规则：只连接 `ircs-dev` K8S 集群，使用真实源站、真实配置、真实数据库和真实外部链路。
本地 compile/build/Jib 只作为发布守卫，不作为功能完成证据。

已闭环：

- gateway/runtime live：真实 PoW + admin login + Ops runtime 入口 200。
- Rabbit listener + snapshot flush gate：`q.task.*` 主队列 consumers=1，队列无积压。
- 成功链路：魔都/旺旺/极速等真实任务完成 MT/PT/DT -> ingestion -> DB flush。
- DLQ 运维：ops-service 真实重放历史 `q.task.detail.dlq=20`，DLQ 清零，raw upsert 保持幂等。
- page/source 失败：卧龙资源非 JSON、猫眼资源 502 retry TTL 耗尽，任务失败且 Rabbit DLQ 不污染。
- detail retry 自恢复：茅台资源真实 detail transient failure 经 retry 后 20/20 成功。
- terminal detail 失败：临时将魔都资源 `detail_path` 指向真实 404，列表成功、20 个 DT 全部
  `detail.done(false)`，MT/DB 终态 `FAILED`，`stat_failed=20`，失败样本进入 runtime；
  源站配置随后恢复。
- 25 源一页矩阵：23/25 源站成功，2/25 为 page/source 失败；成功源站合计 471 条 detail/result 成功。
- 网络策略矩阵：魔都/电影天堂覆盖 HTTPS/HTTP、JDK/Apache、HTTP_1_0、IPv4、IPv4/IPv6 preferred、
  DoH；CURL_NATIVE 当前 fail-closed。
- DoT 默认修复：默认 endpoint 从 `cloudflare-dns.com:853` 切到 `one.one.one.one:853`，
  catalog fetch-sample 和 scraper 真实 DOT 任务均成功。
- IPv6-only 判因：当前 Pod 只有 link-local IPv6，无默认 IPv6 路由；验证源站也无 AAAA，
  因此 IPV6_ONLY 失败属于真实基础设施/源站限制。
- provider-gated trend-sync：从 `ircs-scraper-service` Pod 内真实 POST
  `/internal/v1/scraper/trends/sync`，2 个 provider 抓取 20 条；content-service 创建
  20 个 trend ghost，排队 3 个 discovery collection task。
- discovery follow-up：3 个 discovery task 均终态 `COMPLETED`，其中 `大濛` 任务
  `stat_processed=1/stat_success=1/stat_failed=0`。
- search side effect：trend ghost 生成 20 条 `UNIFIED_VIDEO INDEX` outbox；开启
  `APP_SEARCH_OUTBOX_RELAY_ENABLED=true` 后 DB outbox 清空，Rabbit search 队列 drain，
  worker-job audit 记录 `search.sync.raw succeeded=481`、`search.sync.unified succeeded=20`。
- 5xx 可观测性：`CommonApiExceptionHandler` 已补 server-side unexpected exception logging；
  客户端仍返回统一 `internal.error`，不暴露内部异常。
- Admin 网络策略入口：通过 gateway 真实 admin 登录、PoW、数学验证码后，对 `iKun资源`
  以 `APACHE_HC5 + HTTP_1_1 + IPV4_PREFERRED + DOT one.one.one.one:853`
  调用 fetch-sample 成功，返回 20 条真实数据。
- Prometheus 队列指标：ops-service 已暴露 `ircs.rabbit.queue.messages` 和
  `ircs.rabbit.queue.consumers`，覆盖 task main/retry/DLQ 与业务 DLQ；Prometheus 查询到
  task queue depth 为 0、`q.process.playlist_sync.dlq=1`、task 主队列 consumers 正常。
- 真实 Admin 采集任务：任务 `019ec381-6c91-76bc-82d2-4742ef71ca85`
  从 Admin gateway 创建并启动，终态 `COMPLETED`，`20/20` 成功。

## 当前缺口

1. 源站网络矩阵扩展：429、连接超时、老旧 TLS/HTTP 兼容和错误展示还需真实覆盖。
2. 多副本/锁竞争证据：task/scraper 当前单副本 Ready，多副本 contention 仍需在资源允许时做。
3. cache/runner 组合证据：热读缓存 hit/evict、runner dry-run/execute gate/audit 仍归入 030 总回归。
4. 生产候选报告：029 的真实证据需要汇总进入 go/no-go 与生产 namespace 方案。

## 下一步

1. 资源允许时做 task/scraper/search 多副本 contention 真实窗口。
2. 扩展长尾源站网络真实矩阵：429、timeout、老旧 TLS/HTTP。
3. 把 029 证据并入 030 生产候选 go/no-go 报告。
4. 029 当前按约 97% 管理，剩余项并入 030 生产候选回归。
