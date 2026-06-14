# 028-20-24 Prometheus health shared env live

状态：已完成

来源：`docs/reports/113-02820-prometheus-health-shared-env-live.md`

## 目标

把 `ircs-dev` Java/BFF/worker runtime health、Prometheus scrape 和 Redis/Valkey env 从局部修复推进到统一、可观测、可追踪的 live 状态。

## 范围

- 接入现有 Prometheus stack，不新建监控中间件。
- 为 `ircs-dev` 增加 ServiceMonitor/PodMonitor。
- 统一 Java runtime shared env 到 `ircs-dev-app-config + envFrom`。
- 根 `/actuator/health` 展开 components，并验证 20 个 Java target UP。
- 修复 BFF 可选 JDBC 条件导致的 CrashLoop。
- 修复 storage Redis/distributedLock health 与 notification dev mail health。

## 非目标

- 不执行真实 mTLS `-Execute` rollout。
- 不执行多副本 Redis lock contention。
- 不触发 LLM/provider/SMTP/R2 业务 smoke。

## 验收证据

```text
BFF focused tests BUILD SUCCESSFUL
observability-monitoring-dev.yaml server dry-run passed
19 Java dev manifests server dry-run passed
ircs-dev deployments Ready 25/25
20/20 Java Deployment /actuator/health = UP
20/20 Java Deployment health components visible
MISSING_HEALTH_ENV=0
MISSING_REDIS_PORT_ENV=0
Prometheus ircs-dev active targets: 20
Prometheus ircs-dev up targets: 20
NO_AUTOWIRED_IN_PRODUCTION=OK
```

运行中 BFF digest：

```text
ircs-admin-bff   sha256:fddae1ebcd45c20edfdc06741869af1e1764a49a9956df69160958cbbf1967ce
ircs-portal-bff  sha256:8b382cf3ba894bef5526385b3cac6bd68833e7b023f5fb8fb40da1125106fbe5
```

## 后续

- `028-20-10` 真实 mTLS `-Execute` smoke 已由 `028-20-25` 完成。
- Redis lock contention 需要在 K8S 多副本窗口单独验证。
- `worker_job` / `search_outbox_relay` metric 需要在真实 runner 触发窗口补充有事件样本。
