# 028-20-12A search outbox relay observability

状态：完成

日期：2026-06-11

## 背景

新增运维增强要求：

1. `workerId` 不能只使用 `spring.application.name`，需要能追踪到具体副本。
2. `/actuator/health` 要暴露 outbox relay 状态，便于 K8S/监控观察。
3. Micrometer 要覆盖任务处理延迟、失败率等指标。

本切片先落 search-service 的 persistent search outbox relay，因为它已经存在 DB lease、`locked_by`
和 relay scheduler，是 workerId 重复问题最直接的 owner。

## 范围

- shared `WorkerInstanceIds`：显式 workerId 优先，兜底 `application@host#pid`。
- search outbox relay `locked_by` 使用唯一 workerId。
- dev K8S search-service 注入 `APP_SEARCH_OUTBOX_RELAY_WORKER_ID=metadata.name`。
- search-service `/actuator/health` 增加 relay health component。
- search outbox relay 增加 Micrometer run/group/task delay 指标。

## 非目标

- 不在本切片改所有 worker 的 `worker_job_audit_events.job_source`。
- 不调整 worker audit schema。
- 不启用 search outbox relay 的 dev 开关。
- 不执行 K8S live rollout。

## 验证

```powershell
.\gradlew.bat --no-daemon :shared:ircs-common:test --tests "*WorkerInstanceIdsTest" :services:ircs-search-service:test --tests "*SearchOutboxRelayTest" --tests "*SearchOutboxRelayHealthIndicatorTest" --tests "*SearchSyncTaskOpsRepositoryTest"
.\gradlew.bat --no-daemon :shared:ircs-common:jar :services:ircs-search-service:bootJar
kubectl apply --dry-run=client -f deploy\k8s\dev\search-service-dev.yaml
```

结果：通过。

## 遗留

- 后续继续把 worker instance identity 扩展到 worker/job audit source 或 audit detail。
- 后续为 Rabbit consumers、maintenance runners、normalization/storage/metadata runners 增加统一 latency/failure metrics。
- 需要在统一 K8S smoke 窗口确认 `/actuator/health` 中 search outbox relay health detail 和 Prometheus 指标实际可见。
