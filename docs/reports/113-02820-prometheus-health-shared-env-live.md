# 113 02820 Prometheus Health Shared Env Live

状态：完成

日期：2026-06-12

## 背景

本轮继续收敛 `028-20` runtime/platform live 缺口：

- `ircs-dev` 需要接入生产机已有 Prometheus stack，避免新建监控组件和资源冲突。
- `distributedLock`、`searchOutboxRelay`、`r2` 等 health component 需要在根 `/actuator/health` 可见，便于 K8S 和 Prometheus 监控。
- Redis/Valkey env 不应散落在多个 Deployment 中，需要通过一个公共 ConfigMap 统一注入。
- rollout 过程中暴露出 BFF classpath 对可选 JDBC 条件组件不够稳健，以及 notification/storage 的 health 配置缺口。

生产机资源配置参考：

```text
D:\WorkSpace\Project\Oracle-K8S-Repo\213.35.105.106\Prometheus\monitor-values.yaml
D:\WorkSpace\Project\Oracle-K8S-Repo\213.35.105.106\ArgoCD\ircs-app.yaml
D:\WorkSpace\Project\Oracle-K8S-Repo\213.35.105.106\EnvoyGateway\03-gateway.yaml
```

结论：当前 Prometheus stack 已允许跨 namespace `ServiceMonitor` / `PodMonitor`；`ircs-dev` 不新增 Prometheus，不触碰 production `ircs-system` ArgoCD app，不新增公网 route。

## 变更摘要

- 新增 `deploy/k8s/dev/observability-monitoring-dev.yaml`：
  - `ServiceMonitor/ircs-dev-service-monitor` 抓取 16 个 Service-backed Java/BFF target。
  - `PodMonitor/ircs-dev-worker-pod-monitor` 抓取 4 个 worker-only Pod。
  - label 使用 `release: prometheus-stack` 适配现有 Prometheus Operator。
- 扩展 `ircs-dev-app-config` 作为统一公共 runtime ConfigMap：
  - `MANAGEMENT_ENDPOINT_HEALTH_SHOW_COMPONENTS=always`
  - `SPRING_DATA_REDIS_HOST=valkey-svc.ircs-dev.svc.cluster.local`
  - `SPRING_DATA_REDIS_PORT=6379`
  - `MANAGEMENT_HEALTH_MAIL_ENABLED=false`
- 19 个 Java dev manifest 改为通过 `envFrom.configMapRef.name=ircs-dev-app-config` 注入公共 env，移除散落的 Redis/health 单项 env。
- `SearchSyncOutboxWriter` 的可选 JDBC 条件改为 string-based `@ConditionalOnClass` + `@ConditionalOnBean(type=...)`，避免 BFF 未引入 `spring-jdbc` 时扫描失败。
- 重新打包并推送 ARM64 `ircs-portal-bff:dev`、`ircs-admin-bff:dev`，恢复 BFF CrashLoop。
- live secret 补齐 `SERVICE_CREDENTIAL_TOKEN` key alias，修复 credential-service manifest 与 secret key 命名不一致；未记录任何 secret value。
- `storage-service` 补齐 shared Redis env 后，根 health 从 Redis/DistributedLock DOWN 恢复 UP。
- dev 下 `notification-worker` 在 `APP_MAIL_ENABLED=false` 时同步禁用 Spring Boot mail health，避免未配置 SMTP 密码导致根 health DOWN。

## 验证

本地代码验证：

```text
.\gradlew.bat :services:ircs-portal-bff:test :services:ircs-admin-bff:test --no-daemon
BUILD SUCCESSFUL

rg "@Autowired" services shared platform -g "*.java" -g "!**/src/test/**"
NO_AUTOWIRED_IN_PRODUCTION=OK
```

K8S manifest 验证：

```text
kubectl apply --dry-run=server -f deploy\k8s\dev\observability-monitoring-dev.yaml
DRY_RUN_OK for 19 Java dev manifests
```

K8S live readiness：

```text
ircs-dev deployments Ready 25/25
pods not running: 0
```

Actuator root health：

```text
20/20 Java Deployment /actuator/health = UP
20/20 Java Deployment health components visible
```

Shared env live：

```text
MISSING_HEALTH_ENV=0
MISSING_REDIS_PORT_ENV=0
```

Prometheus live query：

```text
prometheus_active_targets_ircs_dev                              20
prometheus_up_targets_ircs_dev                                  20
up{namespace="ircs-dev"}                                        20
ircs_distributed_lock_operations_total{namespace="ircs-dev"}    10
http_server_requests_seconds_count{namespace="ircs-dev"}        58
jvm_memory_used_bytes{namespace="ircs-dev"}                    160
ircs_worker_job_runs_total{namespace="ircs-dev"}                 0
ircs_search_outbox_relay_runs_total{namespace="ircs-dev"}        0
```

`worker_job` 与 `search_outbox_relay` 当前为 0 series 是因为本轮只做 health/monitoring smoke，没有触发对应 runner；Prometheus target 和 scrape path 已验证可用。

## 运行中镜像摘要

本轮 BFF 修复后 live digest：

```text
ircs-admin-bff   harbor.mnnu.eu.org/ircs/ircs-admin-bff@sha256:fddae1ebcd45c20edfdc06741869af1e1764a49a9956df69160958cbbf1967ce
ircs-portal-bff  harbor.mnnu.eu.org/ircs/ircs-portal-bff@sha256:8b382cf3ba894bef5526385b3cac6bd68833e7b023f5fb8fb40da1125106fbe5
```

全量 `ircs-*` Pod 均为 `Ready=true`、`RestartCount=0`。

## 边界

- 本轮没有执行真实 mTLS `-Execute` rollout。
- 本轮没有做多副本 Redis lock contention smoke；只验证了 Redis backend health 与 Prometheus lock metric 可见。
- 本轮没有触发 LLM/provider/SMTP/R2 真实业务 smoke。
- dev 下 mail health 默认关闭与 `APP_MAIL_ENABLED=false` 一致；后续真实 SMTP smoke 开启邮件时需要同步打开 mail health 或单独记录临时边界。
