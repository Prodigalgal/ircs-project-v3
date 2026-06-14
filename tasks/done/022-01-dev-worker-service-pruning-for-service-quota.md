# 022-01 dev worker-only Service 裁剪 task spec

当前状态：已完成

父任务：

- `tasks/in-progress/011-mvp-service-onboarding-parent.md`

上游来源：

- `docs/reports/022-dev-service-quota-worker-service-pruning.md`
- `docs/migrations/018-identity-service-auth-profile-extraction.md`

## 背景

identity-service 接入后，`ircs-dev` 已达到 `services=20/20`。剩余 4 个 MVP 服务继续
接入前，必须释放 Service 配额或调整 quota。为了减少资源声明并避免影响中间件，本任务
只裁剪 worker-only Service。

## 目标

- 从 dev manifest 中移除 5 个 worker-only Service 定义。
- 删除 live K8S 中对应 5 个 Service。
- 保持 worker Deployment Ready 和队列 consumer 正常。
- 为剩余 4 个服务预留 Service quota。

## 非目标

- 不删除 Deployment。
- 不调整 CPU/memory。
- 不修改业务代码。
- 不接入新服务。
- 不新增 HTTPRoute。

## 输入

```text
deploy/k8s/dev/notification-worker-dev.yaml
deploy/k8s/dev/metadata-worker-dev.yaml
deploy/k8s/dev/ingestion-worker-dev.yaml
deploy/k8s/dev/normalization-worker-dev.yaml
deploy/k8s/dev/aggregation-worker-dev.yaml
kubectl get service -n ircs-dev
kubectl get resourcequota ircs-dev-quota -n ircs-dev
```

## 输出

```text
更新后的 5 个 dev worker manifest
docs/reports/022-dev-service-quota-worker-service-pruning.md
tasks/done/022-01-dev-worker-service-pruning-for-service-quota.md
docs/k8s/current-environment-2026-06-02.md
deploy/k8s/README.md
tasks/index.md
docs/index.md
```

## 影响范围

```text
deploy/k8s/dev/notification-worker-dev.yaml
deploy/k8s/dev/metadata-worker-dev.yaml
deploy/k8s/dev/ingestion-worker-dev.yaml
deploy/k8s/dev/normalization-worker-dev.yaml
deploy/k8s/dev/aggregation-worker-dev.yaml
docs/*
tasks/*
```

## 依赖

- 这些 worker 没有入站 HTTP API。
- RabbitMQ consumer 不依赖 K8S Service。
- K8S probes 使用 Pod/container port。

## 风险

- actuator 不能再通过 ClusterIP Service 访问。
- 后续如要 Telepresence intercept worker，需要重新创建 Service 或改用 Deployment/Pod 调试方式。

## 验收标准

- `kubectl get svc -n ircs-dev` 不再包含 5 个 worker Service。
- `kubectl get deployment -n ircs-dev` 显示 5 个 worker 仍 Ready 1/1。
- RabbitMQ 关键队列 consumer 保持符合当前切片预期。
- `kubectl get httproute -n ircs-dev` 仍为空。
- `kubectl get resourcequota ircs-dev-quota -n ircs-dev` 显示 Service 使用量下降。

## 测试边界

```text
kubectl apply --dry-run=server -f <updated-worker-manifest>
kubectl delete service <worker-services> -n ircs-dev
kubectl get deployment -n ircs-dev
kubectl exec -n ircs-dev rabbitmq-0 -- rabbitmqctl list_queues name messages consumers
kubectl get resourcequota ircs-dev-quota -n ircs-dev
kubectl get httproute -n ircs-dev
```

## 关联文档

- `docs/reports/022-dev-service-quota-worker-service-pruning.md`
- `docs/k8s/current-environment-2026-06-02.md`

## 关联任务

- 父任务：`tasks/in-progress/011-mvp-service-onboarding-parent.md`
- 本任务：`tasks/done/022-01-dev-worker-service-pruning-for-service-quota.md`

## 实施结果

已移除 5 个 worker-only manifest 中的 Service 文档，并删除 live K8S 对应 Service。

验证：

```text
kubectl apply --dry-run=server -f deploy\k8s\dev\notification-worker-dev.yaml
kubectl apply --dry-run=server -f deploy\k8s\dev\metadata-worker-dev.yaml
kubectl apply --dry-run=server -f deploy\k8s\dev\ingestion-worker-dev.yaml
kubectl apply --dry-run=server -f deploy\k8s\dev\normalization-worker-dev.yaml
kubectl apply --dry-run=server -f deploy\k8s\dev\aggregation-worker-dev.yaml
kubectl delete service ircs-notification-worker ircs-metadata-worker ircs-ingestion-worker ircs-normalization-worker ircs-aggregation-worker -n ircs-dev
kubectl get deployment ircs-notification-worker ircs-metadata-worker ircs-ingestion-worker ircs-normalization-worker ircs-aggregation-worker -n ircs-dev
kubectl exec -n ircs-dev rabbitmq-0 -- rabbitmqctl list_queues name messages consumers --formatter=json
kubectl get resourcequota ircs-dev-quota -n ircs-dev
kubectl get httproute -n ircs-dev
```

结果：

```text
worker Deployments Ready: 5/5
HTTPRoute: none
RabbitMQ messages: all 0
services quota: 15/20
```

## 当前状态

已完成，移动到 `tasks/done/`。
