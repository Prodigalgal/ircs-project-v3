# 019 scraper-service manual scrape MVP 迁移记录

状态：已完成

更新时间：2026-06-04

## 背景

本迁移从 V1 `modules/scraper` 抽取 manual scrape API、SSE、list/detail
mapping 和 ingest queue producer。`scraper-service` 只负责抓取、映射和投递
`q.ingest.video`，不直接写 `raw_videos`、`playlists`、`episodes` 或
`source_domains`；写入仍由已接入的 `ircs-ingestion-worker` 承接。

## 实现范围

```text
services/ircs-scraper-service
deploy/k8s/dev/scraper-service-dev.yaml
```

已接入路由：

```text
POST /api/v1/scraper/manual/init
GET  /api/v1/scraper/manual/stream/{sessionId}
```

MVP 能力：

- 读取共享 PostgreSQL `data_sources` 作为迁移桥。
- 支持 V1 风格 manual scrape 输入：关键词、页码、UA、proxy、headers、delay。
- 支持 list/detail JSONPath mapping 和 playlist 字符串解析。
- 支持 `directItems` dev smoke 输入，用于在没有真实外部数据源时验证 producer 到
  ingestion-worker 的端到端链路。
- 使用进程内单副本 session Map；后续多副本或长任务应切到 Valkey。
- 通过 RabbitMQ 发布 shared `IngestionItem` 到 `QueueTopic.INGEST_VIDEO`。
- SSE 输出 `LOG`、`CARD`、`DONE`、`ERROR`；`CARD.status=PUBLISHED` 表示已投递
  queue，不表示 DB 已完成 upsert。

## K8S 结果

```text
deployment:    ircs-scraper-service Ready 1/1
service:       ircs-scraper-service ClusterIP 10.104.166.59
HTTPRoute:     none
resources:     request 25m/128Mi, limit 250m/512Mi
image:         harbor.mnnu.eu.org/ircs/ircs-scraper-service:dev
digest:        sha256:ac848d829489d2358887046f88f746ba361feb00a0aba83a652dad6ee1d26cad
```

当前 `ircs-dev` quota 在 scraper 接入后为：

```text
pods:            20 / 20
services:        16 / 20
requests.cpu:    725m / 4
requests.memory: 3584Mi / 8Gi
limits.cpu:      6 / 8
limits.memory:   11Gi / 16Gi
```

`services` quota 仍有余量；`pods=20/20` 已成为后续 portal、interaction、ops
rollout 的新硬门槛。

## 验证结果

```text
.\gradlew.bat :services:ircs-scraper-service:test
kubectl apply --dry-run=server -f deploy\k8s\dev\scraper-service-dev.yaml
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-scraper-service' -ImageName ircs-scraper-service -Tag dev -Architecture arm64 -Os linux
kubectl rollout status deployment/ircs-scraper-service -n ircs-dev --timeout=240s
```

结果：通过。

ClusterIP smoke：

```text
data_source: CODEX_SCRAPER_SMOKE_20260604125940
source_vid:  codex-scraper-smoke-20260604125940
sessionId:   4126f876-5bcf-443a-9c46-08445efbaf9e
SSE:         LOG -> CARD(PUBLISHED) -> LOG -> DONE
raw_video:   6f228cc8-7aeb-4aad-9862-f687fe127dfd
playlist:    1
episode:     1
statuses:    normalization=PENDING, enrichment=PENDING, aggregation=PENDING
```

第一次 smoke 中曾因测试脚本过早删除临时 `data_sources`，导致
`ircs-ingestion-worker` 消费时触发 `fk_videos_on_data_source` 外键失败并进入
`q.ingest.video.dlq`。该死信已确认属于测试清理顺序问题，清空后改为等待
`raw_videos`、`playlists`、`episodes` 落库再清理，最终队列和 DLQ 均为 0。

清理结果：

```text
raw_videos where source_vid like codex-scraper-smoke-%: 0
data_sources where name like CODEX_SCRAPER_SMOKE_%: 0
RabbitMQ queues and DLQs: 0 messages
HTTPRoute in ircs-dev: none
```

## 遗留问题

- 真实外部数据源 list/detail 抓取需要在后续有 dev data source 后补 smoke。
- session 目前是单 Pod 进程内状态，多副本和重启场景会丢失。
- admin 鉴权仍未接入 scraper route；当前通过 ClusterIP 内部 smoke 验证。
- collection task lifecycle、trend sync、metadata provider fan-out 均保持后置。

## 关联任务

- `tasks/done/016-01-scraper-service-manual-scrape-mvp.md`
- `tasks/done/016-02-ingestion-worker-video-ingest-mvp.md`
- `tasks/in-progress/011-mvp-service-onboarding-parent.md`
