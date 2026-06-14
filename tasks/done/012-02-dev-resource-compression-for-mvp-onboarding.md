# 012-02 开发环境资源压缩以支撑 MVP 服务接入

状态：已完成

父任务：

- `tasks/in-progress/011-mvp-service-onboarding-parent.md`

关联文档：

- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/k8s/current-environment-2026-06-02.md`

## 背景

`ircs-config-service` 已创建 Deployment/Service，但 Pod 调度失败，事件显示单节点 `Insufficient cpu`。用户明确允许压低其他服务资源，尤其是 `monitoring` namespace，以释放开发测试所需调度空间。

## 目标

- 盘点节点、`monitoring` 和 `ircs-dev` 的资源 requests/limits。
- 优先压低非关键观测组件或开发环境资源 request。
- 让 `ircs-config-service` 能够调度并完成 smoke。
- 记录变更范围、验证结果和回滚口径。

## 非目标

- 不删除生产业务资源。
- 不删除 PVC 或监控历史数据。
- 不关闭 `ircs-system` 生产 HTTPRoute。
- 不做 Helm chart 长期 values 治理，本切片先解决开发集成阻塞。

## 风险

- `monitoring` 多数资源可能由 Helm 管理，手工 patch 可能被后续 Helm upgrade 覆盖。
- 过度压低 Prometheus/Alertmanager/Grafana 可能影响观测稳定性。
- 单节点资源紧张时，即使 namespace quota 充足，Pod 仍可能因为节点可分配 CPU 不足而 Pending。

## 验收标准

- 资源压缩后 `ircs-config-service` Ready 1/1。
- `monitoring` 关键 Pod 保持 Running/Ready，或明确记录被缩容的非关键组件。
- `ircs-dev` 仍无 HTTPRoute。
- 文档记录实际压缩对象和最终配额/调度状态。

## 实现结果

本次没有压低 `monitoring` CPU request，因为实际盘点显示 `monitoring`
namespace 当前 CPU request 基本为 0，无法解决 `Insufficient cpu`。真正的
阻塞来自单节点 CPU request 接近满额，以及 `ircs-dev` 已接入服务和 dev
Elasticsearch 的 request 较高。

已完成压缩：

```text
dev Java services request.cpu: 75m -> 25m
dev Elasticsearch request.cpu:    250m -> 100m
dev Elasticsearch limit.cpu:      1500m -> 1000m
single-replica dev Deployment:    Recreate
```

## 验证结果

```text
ircs-config-service:        Ready 1/1
ircs-notification-worker:   Ready 1/1
all extracted dev services: Ready 1/1
monitoring pods:            Running/Ready
ircs-dev HTTPRoute:         none

ircs-dev quota:
  pods:            13 / 20
  services:        14 / 20
  requests.cpu:    550m / 4
  requests.memory: 2688Mi / 8Gi
  limits.cpu:      6250m / 8
  limits.memory:   7680Mi / 16Gi

node allocated CPU request:
  before: 3997m / 4000m
  after:  3522m / 4000m
```

## 回滚口径

如果 dev 中间件性能不足，可以优先把
`deploy/k8s/dev/elasticsearch-dev.yaml` 的 Elasticsearch request 调回
`250m`；如果 Java 服务启动或响应慢，可以把对应服务 request 从 `25m`
调回 `50m` 或 `75m`。
