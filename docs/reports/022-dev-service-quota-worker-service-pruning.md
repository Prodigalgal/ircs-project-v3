# 022 dev Service quota 处理报告：worker-only Service 裁剪

状态：完成

更新时间：2026-06-04

## 背景

`ircs-identity-service` 接入后，`ircs-dev` ResourceQuota 中 `services=20/20` 已达到
硬限制。继续部署 scraper、portal、interaction 或 ops 任一服务都会因为 Service 数量
不足而失败。

当前多个 worker Deployment 只消费 RabbitMQ 或执行 scheduler，不需要通过 ClusterIP
承接入站 HTTP 流量。K8S readiness/liveness probe 直接访问 Pod 的 container port，
不依赖 Service；RabbitMQ 消费也不依赖 Service。

## 目标

- 释放至少 4 个 Service quota，为剩余 4 个 MVP 服务接入预留空间。
- 不删除任何 Deployment、StatefulSet、PVC、中间件或业务运行时。
- 不影响已有 RabbitMQ consumer。
- 不新增公网 HTTPRoute。
- 同步 dev manifest，避免未来 `kubectl apply` 又把已裁剪 Service 创建回来。

## 非目标

- 不调整 CPU/memory request/limit。
- 不删除中间件 Service。
- 不删除 API 型业务服务 Service。
- 不改生产 namespace。
- 不修改业务代码。

## 裁剪候选

本轮只裁剪 worker-only Service：

```text
ircs-notification-worker
ircs-metadata-worker
ircs-ingestion-worker
ircs-normalization-worker
ircs-aggregation-worker
```

保留 Service：

```text
ircs-search-service
ircs-storage-service
ircs-content-service
ircs-credential-service
ircs-catalog-service
ircs-config-service
ircs-task-service
ircs-magnet-service
ircs-identity-service
```

保留原因：这些服务当前或后续 MVP 有 HTTP API、内部 API、媒体/内容访问或前端组合读取需求。

## 预期结果

当前：

```text
services: 20 / 20
```

裁剪 5 个 worker-only Service 后：

```text
services: 15 / 20
```

这会给剩余 scraper、portal、interaction、ops 4 个服务各预留一个 ClusterIP Service，并
保留 1 个缓冲位。

## 风险

- 不能再通过 Service 名称直接访问被裁剪 worker 的 actuator；如需检查，使用
  `kubectl port-forward deployment/<worker>` 或 `kubectl exec`。
- 后续若某个 worker 增加入站 HTTP API，需要单独恢复 Service 并重新核算 quota。
- 必须同步删除 dev manifest 中的 Service 文档，否则下一次 apply 会重新占用配额。

## 验收标准

- 5 个 worker-only dev manifest 不再声明 Service。
- live K8S 中对应 5 个 Service 被删除。
- 对应 5 个 Deployment 保持 Ready 1/1。
- RabbitMQ 队列 consumer 数不下降到异常值。
- `ircs-dev` 无 HTTPRoute。
- ResourceQuota `services` 降到 `15/20` 左右。

## 实施结果

已从以下 manifest 中移除 Service 文档：

```text
deploy/k8s/dev/notification-worker-dev.yaml
deploy/k8s/dev/metadata-worker-dev.yaml
deploy/k8s/dev/ingestion-worker-dev.yaml
deploy/k8s/dev/normalization-worker-dev.yaml
deploy/k8s/dev/aggregation-worker-dev.yaml
```

已删除 live K8S Service：

```text
ircs-notification-worker
ircs-metadata-worker
ircs-ingestion-worker
ircs-normalization-worker
ircs-aggregation-worker
```

验证结果：

```text
kubectl apply --dry-run=server -f 5 个更新后的 worker manifest: passed
worker Deployments: Ready 1/1
HTTPRoute in ircs-dev: none
RabbitMQ queues and DLQs messages: 0
ResourceQuota services: 15/20
```

当前剩余 Service quota 为 5 个，足够继续接入 scraper、portal、interaction、ops 四个
剩余 MVP 服务，并保留 1 个缓冲位。
