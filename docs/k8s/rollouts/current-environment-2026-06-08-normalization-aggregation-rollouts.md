# 2026-06-08 normalization and aggregation rollout log

来源：`current-environment-2026-06-08-rollouts.md` 拆分。

## 2026-06-08 Behavior Parity 028-08 Normalization Deep Mapping Live Smoke

`028-08` 已完成 `q.normalize.video` deep mapping live smoke。该切片复用既有
`ircs-normalization-worker` Deployment，不新增 Pod、Service、PVC、中间件或公共
HTTPRoute。smoke 使用唯一 `02808` fixture，覆盖 actor/director、raw relation、
standard mapping、raw category 和 raw search sync。

Rollout evidence：

```text
deployment/ircs-normalization-worker Ready 1/1

ircs-normalization-worker imageID:
  harbor.mnnu.eu.org/ircs/ircs-normalization-worker@sha256:d0e35af46a0bd8aa0ca60e4db2c973a90824b7e84783cd2f5c6db501d0f043fe

ResourceQuota:
  pods=28/30, services=24/26
  requests.cpu=880m/4, requests.memory=4480Mi/8Gi
  limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
HTTPRoute in ircs-dev:
  No resources found in ircs-dev namespace.
RabbitMQ:
  q.normalize.video messages=0, consumers=1, ready=0, unacked=0
  q.normalize.video.dlq messages=0, consumers=0, ready=0, unacked=0
  q.search.sync.raw messages=0, consumers=1, ready=0, unacked=0
  q.search.sync.raw.dlq messages=0, consumers=0, ready=0, unacked=0
Fixture cleanup:
  raw_videos 02808 residue=0
  standard_genre 02808 residue=0
  data_sources 02808 residue=0
```

Smoke summary：

```text
.\scripts\smoke\normalization-deep-mapping-02808-smoke.ps1
MARK 1/6 normalization/search consumers are ready and queues are clean
MARK 2/6 q.normalize.video fixture is consumed and raw video becomes READY
MARK 3/6 actor/director and raw relation join rows are written
MARK 4/6 raw relations and raw category map to existing standard dictionaries
MARK 5/6 raw search sync refreshes ES document with deep mapping fields
MARK 6/6 smoke cleanup leaves no DB/ES residue and queues remain empty
```


## 2026-06-08 Behavior Parity 028-09 Aggregation Search Producer Live Smoke

`028-09` 已完成 aggregation -> raw/unified search sync live smoke。该切片复用既有
`ircs-aggregation-worker` 与 `ircs-search-service` Deployment，不新增 Pod、Service、PVC、
中间件或公共 HTTPRoute，也不 rollout 镜像。smoke 使用唯一 `codex02809agg` fixture，
验证 scheduler 轮询模型的真实 side effect。

Rollout evidence：

```text
deployment/ircs-aggregation-worker Ready 1/1
deployment/ircs-search-service     Ready 1/1

ResourceQuota:
  pods=28/30, services=24/26
  requests.cpu=880m/4, requests.memory=4480Mi/8Gi
  limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
HTTPRoute in ircs-dev:
  No resources found in ircs-dev namespace.
RabbitMQ:
  q.search.sync.raw messages=0, consumers=1, ready=0, unacked=0
  q.search.sync.raw.dlq messages=0, consumers=0, ready=0, unacked=0
  q.search.sync.unified messages=0, consumers=1, ready=0, unacked=0
  q.search.sync.unified.dlq messages=0, consumers=0, ready=0, unacked=0
Fixture cleanup:
  raw_videos codex02809agg residue=0
  unified_videos Codex 02809 Aggregated Unified residue=0
  local port-forward residue=none
```

Smoke summary：

```text
.\scripts\smoke\search-producer-aggregation-02809-smoke.ps1
MARK 1/6 aggregation/search consumers are ready and HTTPRoute remains absent
MARK 2/6 aggregation fixture is seeded and pre-cleaned from DB/ES
MARK 3/6 aggregation scheduler binds raw video to unified video and marks BOUND
MARK 4/6 aggregation publishes raw/unified legacy UUID search sync and ES documents are refreshed
MARK 5/6 aggregation/search queues and DLQs are empty for the smoke window
MARK 6/6 smoke cleanup leaves no DB/ES fixture residue
```


## 2026-06-08 Behavior Parity 028-09 Old Unified Dirty Lifecycle Live Smoke

`028-09` 已完成 old unified dirty lifecycle live smoke。该切片复用既有
`ircs-content-service` 与 `ircs-search-service` Deployment，不新增 Pod、Service、PVC、
中间件或公共 HTTPRoute，也不 rollout 镜像。smoke 通过 content-service HTTP endpoint
触发 raw critical update 和 unified recalculate，验证当前 dirty lifecycle 语义。

Rollout evidence：

```text
deployment/ircs-content-service Ready 1/1
deployment/ircs-search-service  Ready 1/1

ResourceQuota:
  pods=28/30, services=24/26
  requests.cpu=880m/4, requests.memory=4480Mi/8Gi
  limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
HTTPRoute in ircs-dev:
  No resources found in ircs-dev namespace.
RabbitMQ:
  q.search.sync.raw messages=0, consumers=1, ready=0, unacked=0
  q.search.sync.raw.dlq messages=0, consumers=0, ready=0, unacked=0
  q.search.sync.unified messages=0, consumers=1, ready=0, unacked=0
  q.search.sync.unified.dlq messages=0, consumers=0, ready=0, unacked=0
Fixture cleanup:
  raw_videos codex02809dirty residue=0
  unified_videos Codex 02809 Dirty Unified residue=0
  local port-forward residue=none
```

Smoke summary：

```text
.\scripts\smoke\search-producer-old-unified-dirty-02809-smoke.ps1
MARK 1/6 old unified fixture is seeded and initial ES unified document has sourceCount=1
MARK 2/6 raw critical update marks old unified DIRTY and unbinds raw
MARK 3/6 raw critical update publishes raw search INDEX and refreshes ircs_raw_video
MARK 4/6 old unified remains a dirty lifecycle candidate until recalculate closes it
MARK 5/6 unified recalculate publishes q.search.sync.unified and refreshes ircs_unified_video
MARK 6/6 search queues/DLQs are clean and cleanup leaves no DB/ES fixture residue
```


