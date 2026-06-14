# 041 V1 到 V3 行为级 parity 缺口审计

状态：进行中

更新时间：2026-06-07

## 结论

HTTP Controller 和 BFF smoke 口径已经达到 `missing=0`，但 V3 还不能声明内部功能
1:1。当前缺口集中在接口背后的数据副作用、异步副作用、外部 provider 调用和可观察性
采集链路。

## 行为缺口表

| 领域 | 当前 V3 状态 | 1:1 风险 | 下一步 |
| --- | --- | --- | --- |
| BFF/request audit | portal/admin BFF 已采集写入，ops-service 可查询 | 仅覆盖 BFF 入口，不覆盖每个后端 handler | per-service audit 后续单独设计 |
| ops maintenance | `search-reindex-unified` 已接入 dev-safe runner | Raw reindex、hard reset、trend sync 仍未完成 | 后续拆独立 runner task |
| magnet search | dev-safe provider runner 已写 job/run/link | 真实 provider、限速、重试和 cache eviction 未完成 | 后续接真实 provider 策略 |
| avatar upload | 已通过 storage-service 落地并服务 `/media/avatars/*`；`028-05-B` 已补 `SYNC_AVATAR` consumer 和 identity producer | 真实 R2 live、watchdog 和 dev emptyDir 生命周期风险仍在 | 后续拆 R2 live/watchdog |
| queue consumers | WATCH_PROGRESS、SYNC_AVATAR、metadata fan-out、metadata provider worker dev-safe、metadata pending gather、search producer matrix、normalization raw relation、image download live smoke、mail disabled/failure 已完成第一阶段 | 真实 provider、R2 live/watchdog、deep mapping、producer live smoke 等仍需继续 | 后续 backlog 单拆 |
| physical ownership | 共享 PostgreSQL 迁移桥 | 不是严格微服务数据隔离 | 本阶段只记录风险 |

## 第一刀结果

第一刀 BFF request audit 采集已完成：

```text
MARK 1/2 portal-bff proxied request writes request_audit_logs
MARK 2/2 admin-bff proxied request writes request_audit_logs
```

完成后仍保留以下边界：

- 当前采集点是 portal/admin BFF，能覆盖门户和后台入口请求。
- 后端业务服务内部 handler 级审计仍需后续单独设计。

选择该切片的原因：

- 不新增 Pod、Service、HTTPRoute 或中间件。
- 复用已有 `request_audit_logs` 表和 `ops-service` 查询接口。
- 能同时覆盖 portal/admin 验证入口。
- 可以通过 BFF 请求后再查询 `GET /api/v1/ops/request-audit` 证明行为副作用。
- 对业务数据无破坏性，只写审计日志。

## 验收口径

`028-01` 已证明：

```text
MARK 1/2 portal-bff proxied request writes request_audit_logs
MARK 2/2 admin-bff proxied request writes request_audit_logs
```

后置检查：

```text
portal-bff Ready 1/1
admin-bff Ready 1/1
ops-service request-audit query sees BFF rows
kubectl -n ircs-dev get httproute -> no resources
ResourceQuota no new Service/Pod object count
```

## 后续拆分建议

- `028-02` identity avatar media persistence/sync parity。
- `028-03` magnet external provider search parity。
- `028-04` ops maintenance runner parity。
- `028-05` queue consumer deep side-effect parity。

## 第一阶段扩展结果

2026-06-07 已完成 `028-02`、`028-03`、`028-04` 集成验收：

```text
028-02 identity avatar media persistence/sync: MARK 2/2
028-03 magnet external provider search parity: MARK 3/3
028-04 ops maintenance runner parity:          MARK 3/3
```

统一 K8S smoke：

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
deployment/ircs-storage-service    Ready 1/1
deployment/ircs-identity-service   Ready 1/1
deployment/ircs-magnet-service     Ready 1/1
deployment/ircs-ops-service        Ready 1/1
deployment/ircs-search-service     Ready 1/1
deployment/ircs-portal-bff         Ready 1/1
deployment/ircs-admin-bff          Ready 1/1
kubectl -n ircs-dev get httproute -> No resources found
ResourceQuota: pods=28/30, services=24/26
```

## 队列消费者首个闭环

2026-06-07 已完成 `028-05-A` interaction watch progress consumer parity：

```text
.\scripts\smoke\interaction-watch-progress-02805a-smoke.ps1
MARK 1/4 WATCH_PROGRESS consumer listens on q.interaction.watch_progress
MARK 2/4 consumer upserts member_watch_histories from fixture payload
MARK 3/4 duplicate messages keep latest timestamp per member/unified pair
MARK 4/4 smoke cleanup leaves no DB residue and WATCH_PROGRESS queues empty
```

## 第二阶段 queue parity 扩展结果

2026-06-07 已完成 `028-05-B` 至 `028-05-I`，其中 `028-05-H` 已补齐 live smoke：

```text
028-05-B storage SYNC_AVATAR:              MARK 4/4
028-05-C metadata provider fan-out:        MARK 1/1
028-05-D search producer matrix:           MARK 2/2
028-05-E metadata provider worker/result:  MARK 4/4
028-05-F normalization raw relation:       MARK 4/4
028-05-G metadata pending gather:          MARK 4/4
028-05-H image download true/failure:      MARK 5/5
028-05-I mail disabled/failure:            MARK 4/4
```

第一阶段之后的 backlog 缺口：

- 真实 provider、metadata live ack/DLQ smoke。
- R2 live、watchdog、cover image R2 pending upload。
- normalization actor/director、raw category、standard mapping、watchdog/backoff 和 live queue smoke。
- normalization、metadata result、aggregation 等 producer 的 live smoke。
- production traffic governor、真实 SMTP credential pool 和发送历史。
- 后端业务服务内部 handler 级 audit collection。

normalization 已完成 raw genre/language/area relation 最小切片，actor/director、raw category、
standard mapping、watchdog/backoff 和 live queue smoke 继续后置。
metadata 已完成 pending gather 最小切片，provider 真实实现和 live queue smoke 继续后置。
mail notification 已完成 disabled/failure 最小切片，真实 SMTP/credential pool 继续后置。
provider enable gate、normalization actor/director、normalization producer live smoke 和首个
config-service handler audit 已在后续切片中推进；真实 provider live、R2/watchdog、standard
mapping、metadata/aggregation producer、production governor 和 service/worker audit 铺开仍需继续。

后置检查：

```text
deployment/ircs-interaction-service Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-interaction-service@sha256:a70f46ed265944ac06f180e68d8930268542c7c53519f624273b52b7cb296ab4
kubectl -n ircs-dev get httproute -> No resources found
ResourceQuota: pods=28/30, services=24/26
q.interaction.watch_progress=0 messages, 1 consumer
q.interaction.watch_progress.dlq=0 messages
```

`028-05-H` H-live 后置检查：

```text
deployment/ircs-storage-service Ready 1/1
storage-service imageID: harbor.mnnu.eu.org/ircs/ircs-storage-service@sha256:2fdc14e5fc39f1939ebde89c8d06963a4cc39d720a085469fb22ba678d498df4
q.storage.image=0 messages, 1 consumer
q.storage.image.dlq=0 messages
source_domains/cover_images smoke residue: 0
ResourceQuota: pods=28/30, services=24/26
HTTPRoute: No resources found in ircs-dev namespace
```
