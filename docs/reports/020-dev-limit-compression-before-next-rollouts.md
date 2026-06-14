# 020 dev Java CPU limit 压缩分析

状态：已完成

分析日期：2026-06-04

上游来源：

- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `tasks/in-progress/011-mvp-service-onboarding-parent.md`
- `docs/k8s/current-environment-2026-06-02.md`
- `docs/migrations/016-aggregation-worker-scheduler-extraction.md`

关联任务：

- `tasks/in-progress/020-01-dev-java-limit-compression-for-next-rollouts.md`

## 背景

`ircs-aggregation-worker` 接入后，`ircs-dev` namespace 的 CPU limit 已达到
ResourceQuota hard limit：

```text
limits.cpu: 8 / 8
```

此时 `ircs-identity-service` 和 `ircs-magnet-service` 已有本地代码与 dev manifest
草案，但无法继续进行实际 rollout；即使 server dry-run 通过，真实创建 Pod 时仍会
被 `limits.cpu` 配额拦住。

当前已部署 Java 服务中，除 `ircs-aggregation-worker` 外，大多数 dev manifest 仍为：

```text
request.cpu: 25m
limit.cpu:   500m
```

开发测试环境更关注小流量 smoke 和边界验证，不需要为每个 Java 服务保留 500m CPU
limit。

## 目标

- 将已部署和待部署的 dev Java 服务 CPU limit 统一压缩到 `250m`。
- 不修改 CPU request、memory request 或 memory limit。
- 不改业务代码。
- 不新增或删除 HTTPRoute。
- 释放足够 CPU limit，让后续 identity/magnet rollout 可以继续。
- 记录 service quota 风险：identity/magnet 都部署后，`services` 将达到 `20/20`。

## 非目标

- 不压缩 PostgreSQL、RabbitMQ、Elasticsearch、Valkey。
- 不删除 worker Service。
- 不调整 ResourceQuota hard limit。
- 不做 production namespace 变更。
- 不部署 identity/magnet；本任务只处理资源配额前置阻塞。

## 影响范围

```text
deploy/k8s/dev/catalog-service-dev.yaml
deploy/k8s/dev/config-service-dev.yaml
deploy/k8s/dev/content-service-dev.yaml
deploy/k8s/dev/credential-service-dev.yaml
deploy/k8s/dev/identity-service-dev.yaml
deploy/k8s/dev/ingestion-worker-dev.yaml
deploy/k8s/dev/magnet-service-dev.yaml
deploy/k8s/dev/metadata-worker-dev.yaml
deploy/k8s/dev/normalization-worker-dev.yaml
deploy/k8s/dev/notification-worker-dev.yaml
deploy/k8s/dev/search-service-dev.yaml
deploy/k8s/dev/storage-service-dev.yaml
deploy/k8s/dev/task-service-dev.yaml
docs/k8s/current-environment-2026-06-02.md
deploy/k8s/README.md
tasks/in-progress/011-mvp-service-onboarding-parent.md
```

## 风险

- 过低 CPU limit 可能导致 Spring Boot 启动和 smoke 响应变慢。
- 多个 Deployment 同时更新会短暂重启 dev 服务；当前为 dev namespace，无公网
  HTTPRoute。
- identity/magnet 继续部署后，`services` quota 会达到 `20/20`；后续 scraper、
  portal、interaction、ops 接入前，需要提高 service hard limit、删除不必要的
  worker Service，或改为按需创建 Service。

## 验收标准

- 修改后的 manifests server dry-run 通过。
- 已部署 Java 服务 rollout 后仍 Ready 1/1。
- `ircs-dev` 无 HTTPRoute。
- `limits.cpu` 从 `8/8` 降到可继续部署下一批服务的水平。
- 文档和父任务记录压缩结果、quota 结果和剩余风险。

## 实现与验证回写

已完成 dev Java CPU limit 压缩：

```text
dev Java services:
  request.cpu: 25m
  limit.cpu:   250m
  request.memory: 128Mi
  limit.memory:   512Mi
```

实际 apply 到已部署服务：

```text
ircs-catalog-service
ircs-config-service
ircs-content-service
ircs-credential-service
ircs-ingestion-worker
ircs-metadata-worker
ircs-normalization-worker
ircs-notification-worker
ircs-search-service
ircs-storage-service
ircs-task-service
```

仅更新 manifest 并通过 server dry-run，未部署：

```text
ircs-identity-service
ircs-magnet-service
```

验证结果：

```text
all changed manifests server dry-run: passed
all applied deployments rollout: passed
ircs-dev HTTPRoute: none
RabbitMQ queues and DLQs: 0 messages
```

当前 quota：

```text
pods:            17 / 20
services:        18 / 20
requests.cpu:    650m / 4
requests.memory: 3200Mi / 8Gi
limits.cpu:      5250m / 8
limits.memory:   9728Mi / 16Gi
```

本切片已释放约 `2750m` CPU limit。后续 identity/magnet 可以继续 rollout，但两者都
部署后 `services` 会达到 `20/20`，再接 scraper、portal、interaction 或 ops 前需要
处理 service quota 或 worker Service 策略。
