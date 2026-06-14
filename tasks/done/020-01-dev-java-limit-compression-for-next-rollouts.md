# 020-01 dev Java CPU limit 压缩子任务

状态：已完成

完成日期：2026-06-04

父任务：

- `tasks/in-progress/011-mvp-service-onboarding-parent.md`

上游来源：

- `docs/reports/020-dev-limit-compression-before-next-rollouts.md`
- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/k8s/current-environment-2026-06-02.md`

## 背景

`ircs-aggregation-worker` 接入后，`ircs-dev` 的 CPU limit quota 已达到
`8/8`。`identity-service` 和 `magnet-service` 已有本地代码草案和 dev manifest，
但真实 rollout 仍需要先释放 CPU limit。

## 目标

- 将 dev Java 服务 `limit.cpu` 从 `500m` 压缩到 `250m`。
- 保留 `request.cpu=25m`、`request.memory=128Mi`、`limit.memory=512Mi`。
- 对已部署服务执行 `kubectl apply` 和 rollout 检查。
- 为后续 identity/magnet 部署释放 CPU limit。

## 非目标

- 不修改业务代码。
- 不调整中间件资源。
- 不新增 HTTPRoute。
- 不部署 identity/magnet。
- 不调整 ResourceQuota hard limit。

## 输入

- 当前 `deploy/k8s/dev/*-dev.yaml` manifests。
- 当前 `ircs-dev` ResourceQuota。
- 当前 `ircs-dev` Deployment 资源配置。

## 输出

- 已压缩 CPU limit 的 dev Java manifests。
- K8S dry-run、apply、rollout 结果。
- 更新后的 K8S 快照和父任务状态。

## 影响范围

```text
deploy/k8s/dev/*service-dev.yaml
deploy/k8s/dev/*worker-dev.yaml
docs/k8s/current-environment-2026-06-02.md
deploy/k8s/README.md
tasks/in-progress/011-mvp-service-onboarding-parent.md
```

## 依赖

- `ircs-dev` Kubernetes context 可用。
- 已部署服务允许在 dev 环境短暂滚动重启。

## 风险

- CPU limit 降低后，服务启动时间可能变长。
- `services` quota 仍是下一阶段瓶颈，identity/magnet 部署后会达到 `20/20`。

## 验收标准

- 修改后的 manifests dry-run 通过。
- 已部署 Java 服务 Ready 1/1。
- `ircs-dev` 无 HTTPRoute。
- ResourceQuota `limits.cpu` 明显低于 `8/8`。
- 文档记录实际结果和剩余风险。

## 测试边界

```text
kubectl apply --dry-run=server -f deploy\k8s\dev\<service>.yaml
kubectl apply -f deploy\k8s\dev\<service>.yaml
kubectl rollout status deployment/<name> -n ircs-dev --timeout=240s
kubectl get resourcequota -n ircs-dev
kubectl get httproute -n ircs-dev
```

## 实现结果

已将以下 dev Java manifests 的 `limit.cpu` 从 `500m` 压缩到 `250m`：

```text
catalog-service-dev.yaml
config-service-dev.yaml
content-service-dev.yaml
credential-service-dev.yaml
identity-service-dev.yaml
ingestion-worker-dev.yaml
magnet-service-dev.yaml
metadata-worker-dev.yaml
normalization-worker-dev.yaml
notification-worker-dev.yaml
search-service-dev.yaml
storage-service-dev.yaml
task-service-dev.yaml
```

`ircs-aggregation-worker` 原本已是 `250m`，未再修改。

## 验证结果

Server dry-run：

```text
kubectl apply --dry-run=server -f deploy\k8s\dev\<changed-manifest>
```

结果：全部通过。

实际 apply 范围：

```text
已部署服务：
catalog-service
config-service
content-service
credential-service
ingestion-worker
metadata-worker
normalization-worker
notification-worker
search-service
storage-service
task-service

仅 dry-run，未部署：
identity-service
magnet-service
```

Rollout：

```text
kubectl rollout status deployment/<name> -n ircs-dev --timeout=240s
```

结果：上述已部署服务均成功 rollout，Ready 1/1。

当前 quota：

```text
pods:            17 / 20
services:        18 / 20
requests.cpu:    650m / 4
requests.memory: 3200Mi / 8Gi
limits.cpu:      5250m / 8
limits.memory:   9728Mi / 16Gi
```

安全检查：

```text
HTTPRoute in ircs-dev: none
RabbitMQ queues and DLQs: 0 messages
```

## 当前状态

已完成。已释放约 `2750m` CPU limit，可继续推进 identity/magnet 的 ARM64 镜像、
rollout 和 smoke。后续仍需关注 `services` quota：identity 和 magnet 都部署后将到
`20/20`。
