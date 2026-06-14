# 047 interaction watch progress consumer parity 迁移记录

状态：已完成

日期：2026-06-07

## 背景

本记录承接 `028-05-A`，把 V1 `WATCH_PROGRESS` 队列消费者迁移到
`ircs-interaction-service`。V3 原先只有同步 `POST /api/portal/interaction/history`
写库路径，缺少 `q.interaction.watch_progress` 消费者，导致异步播放心跳无法落到
`member_watch_histories`。

## 范围

新增：

```text
WatchProgressMessage contract
WatchProgressMessageParser
WatchProgressConsumer
WatchProgressBatchService
JdbcInteractionRepository.upsertWatchHistoryIfNewer
scripts/smoke/interaction-watch-progress-02805a-smoke.ps1
```

配置：

```text
APP_INTERACTION_LISTENER_ENABLED=false by default
APP_INTERACTION_LISTENER_ENABLED=true in deploy/k8s/dev/interaction-service-dev.yaml
```

## 行为复现

```text
RabbitMQ x.interaction / interaction.progress
  -> q.interaction.watch_progress
  -> WatchProgressConsumer
  -> WatchProgressBatchService
  -> member_watch_histories upsert
```

完成标记：

```text
MARK 1/4 WATCH_PROGRESS consumer listens on q.interaction.watch_progress
MARK 2/4 consumer upserts member_watch_histories from fixture payload
MARK 3/4 duplicate messages keep latest timestamp per member/unified pair
MARK 4/4 smoke cleanup leaves no DB residue and WATCH_PROGRESS queues empty
```

## 验证

```text
.\gradlew.bat :services:ircs-interaction-service:test --rerun-tasks
BUILD SUCCESSFUL

.\scripts\smoke\interaction-watch-progress-02805a-smoke.ps1
MARK 4/4

deployment/ircs-interaction-service Ready 1/1
imageID:
  harbor.mnnu.eu.org/ircs/ircs-interaction-service@sha256:a70f46ed265944ac06f180e68d8930268542c7c53519f624273b52b7cb296ab4

kubectl -n ircs-dev get httproute -> No resources found
ResourceQuota: pods=28/30, services=24/26
```

## 遗留项

- 现有 `POST /api/portal/interaction/history` 仍保持同步写库，未改为异步 publish-only。
- 真实播放端 producer 统一和 portal 心跳接入后续可单拆。
- `028-05-B` 继续处理 `q.storage.avatar_sync` 远端同步消费者。
