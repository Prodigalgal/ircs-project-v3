# 024-01 dev Pod quota 后续服务 rollout 前置调整任务

状态：已完成

父任务：

- `tasks/in-progress/011-mvp-service-onboarding-parent.md`

上游来源：

- `docs/reports/024-dev-pod-quota-before-final-rollouts.md`

## 背景

scraper-service 接入后，`ircs-dev` ResourceQuota 中 `pods=20/20`。剩余
portal、interaction、ops 三个服务若继续 rollout，需要新增 Pod，因此必须先处理 Pod
数量硬限制。

## 目标

- 将 `ircs-dev-quota.spec.hard.pods` 小幅提升到可容纳剩余 MVP 服务的最小值。
- 保持 CPU、memory、services、secrets、configmaps hard limit 不变。
- 不改变任何生产 namespace。
- 调整后复核现有服务 Ready、无 HTTPRoute、队列无残留。

## 非目标

- 不提升 CPU/memory hard limit。
- 不增加中间件副本。
- 不部署 portal、interaction 或 ops 业务代码。
- 不新增公网 HTTPRoute。

## 输入

```text
deploy/k8s/dev/resource-quota-dev.yaml
kubectl get resourcequota ircs-dev-quota -n ircs-dev -o yaml
```

## 输出

- 已新增并应用 `deploy/k8s/dev/resource-quota-dev.yaml`。
- `pods` hard limit 已从 `20` 调整到 `24`。
- CPU、memory、services、secrets、configmaps hard limit 保持不变。
- 父任务、K8S 快照和任务状态已回写。

## 验收标准

- live quota `pods` hard limit 大于当前 20，建议为 24。
- `services=20`、`requests.cpu=4`、`requests.memory=8Gi`、`limits.cpu=8`、
  `limits.memory=16Gi` 保持不变。
- 当前已部署业务服务和中间件仍 Ready。
- RabbitMQ 队列和 DLQ 均为 0 消息。

## 测试边界

```text
kubectl apply --dry-run=server -f deploy\k8s\dev\resource-quota-dev.yaml
kubectl apply -f deploy\k8s\dev\resource-quota-dev.yaml
kubectl get resourcequota ircs-dev-quota -n ircs-dev -o yaml
kubectl get deploy,statefulset -n ircs-dev
kubectl exec -n ircs-dev rabbitmq-0 -- rabbitmqctl list_queues name messages consumers
```

## 验证结果

```text
kubectl apply --dry-run=server -f deploy\k8s\dev\resource-quota-dev.yaml
kubectl apply -f deploy\k8s\dev\resource-quota-dev.yaml
kubectl get resourcequota ircs-dev-quota -n ircs-dev -o yaml
kubectl get deploy,statefulset -n ircs-dev
kubectl exec -n ircs-dev rabbitmq-0 -- rabbitmqctl list_queues name messages consumers
```

结果：通过。live quota hard `pods=24`，used 在 portal/interaction 部署后为
portal/interaction 接入后曾为 `pods=22/24`、`services=18/20`、
`requests.cpu=770m/4`、`limits.cpu=6450m/8`。ops-service 接入后当前为
`pods=23/24`、`services=19/20`、`requests.cpu=795m/4`、`limits.cpu=6700m/8`。
已部署 workload Ready，RabbitMQ 队列和 DLQ 均为 0。

`kubectl top pods` 因 Metrics API 不可用无法返回实时用量；本任务验收依赖
ResourceQuota、Deployment Ready 和队列状态。

## 当前状态

已完成。
