# 024 dev Pod quota 后续服务 rollout 风险报告

状态：已完成

更新时间：2026-06-04

## 背景

`ircs-scraper-service` 接入后，`ircs-dev` 已推进到 scraper 阶段。Service quota
已通过 worker-only Service 裁剪保持余量，但 Pod quota 当时达到
硬限制：

```text
pods:            20 / 20
services:        16 / 20
requests.cpu:    725m / 4
requests.memory: 3584Mi / 8Gi
limits.cpu:      6 / 8
limits.memory:   11Gi / 16Gi
```

剩余 `portal-service`、`interaction-service`、`ops-service` 都需要至少 1 个 Pod。
如果不先处理 Pod quota，后续 Deployment 会被 ResourceQuota 拒绝或无法创建新 Pod。

## 结论

当前瓶颈不是 CPU 或 memory，而是 Pod 数量硬限制。已按本报告建议只小幅调整
`ircs-dev-quota.spec.hard.pods`，例如从 `20` 调整到 `24`，为剩余 3 个服务和 1 个
缓冲 Pod 留出空间；保持 CPU、memory、services、secrets、configmaps hard limit 不变。

这不会自动增加资源占用，实际资源仍由每个 Deployment 的 request/limit 控制。继续维持
Java 服务 `request 25m/128Mi`、`limit 250m/512Mi`。

## 非目标

- 不增加中间件副本。
- 不放宽 CPU/memory hard limit。
- 不新增公网 HTTPRoute。
- 不调整生产 namespace。

## 验收建议

```text
kubectl apply --dry-run=server -f deploy/k8s/dev/resource-quota-dev.yaml
kubectl apply -f deploy/k8s/dev/resource-quota-dev.yaml
kubectl get resourcequota ircs-dev-quota -n ircs-dev -o yaml
```

验收口径：

- `pods` hard limit 提升到可容纳剩余服务的最小值。
- `services`、CPU、memory hard limit 保持不变。
- 已有 Deployments 仍 Ready。
- RabbitMQ 队列和 DLQ 保持 0 消息。

执行结果：通过。live hard `pods=24`；portal/interaction 接入后曾为
`pods=22/24`、`services=18/20`、`requests.cpu=770m/4`、`limits.cpu=6450m/8`。
ops-service 接入后当时 used 为 `pods=23/24`、`services=19/20`、
`requests.cpu=795m/4`、`limits.cpu=6700m/8`，仍保留 1 个缓冲 Pod 和
1 个缓冲 Service。

## 关联任务

- `tasks/done/024-01-dev-pod-quota-adjustment-for-final-rollouts.md`
- `tasks/in-progress/011-mvp-service-onboarding-parent.md`
