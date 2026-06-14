# 028-16-C3-S10 caller-specific circuit ENV matrix task spec

状态：进行中

父任务：

- `tasks/in-progress/028-16-c3-shared-outbound-http-client-parity.md`
- `tasks/backlog/028-16-f6-c3-outbound-adoption-golden-p1p2-backlog.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

## 目标

- 建立 adopted caller 的 circuit key -> ENV var matrix。
- 支持 caller-specific circuit override，并保留全局 `IRCS_OUTBOUND_CIRCUIT_*` fallback。
- 让 ENV / K8S env 高于 JVM property fallback。
- 更新 adopted services 的 `application.yaml` / dev manifest 默认值，保持低资源和功能默认行为不变。
- 增加轻量 config 解析测试；live/full/组合测试后置到统一测试批次。

## 范围

| Caller | Circuit key | 服务 |
| --- | --- | --- |
| content internal scraper refetch | `content-refetch-scraper` | `ircs-content-service` |
| task scraper execution | `task-scraper-execution` | `ircs-task-service` |
| identity avatar storage | `identity-avatar-storage` | `ircs-identity-service` |
| admin-bff proxy | `admin-bff-proxy` | `ircs-admin-bff` |
| portal-bff proxy | `portal-bff-proxy` | `ircs-portal-bff` |

## 非目标

- 不修改 normalization、aggregation、Tika/scraper、notification 或业务逻辑。
- 不做 live smoke。
- 不做全量测试。

## 影响文件

```text
shared/ircs-common/src/main/java/com/prodigalgal/ircs/common/outbound/**
shared/ircs-common/src/test/java/com/prodigalgal/ircs/common/outbound/**
services/ircs-content-service/src/main/resources/application.yaml
services/ircs-task-service/src/main/resources/application.yaml
services/ircs-identity-service/src/main/resources/application.yaml
services/ircs-admin-bff/src/main/resources/application.yaml
services/ircs-portal-bff/src/main/resources/application.yaml
deploy/k8s/dev/*-dev.yaml
docs/**
tasks/**
```

## 验收标准

```text
MARK 1/5 caller key -> ENV var matrix documented
MARK 2/5 shared outbound resolves caller-specific override before global fallback
MARK 3/5 adopted service configs/dev manifests expose safe defaults
MARK 4/5 lightweight config tests pass or are explicitly deferred
MARK 5/5 C3/F6/089/093 ledgers updated with partial unified-test debt
```

## 测试方式

只跑 shared outbound config 解析相关轻量测试；不跑 live smoke，不跑全量测试。统一测试待办纳入
`UTB-02816-P1P2-02`。

## 2026-06-10 实现结果

ENV matrix：

| Caller key | ENV prefix | Global fallback |
| --- | --- | --- |
| `content-refetch-scraper` | `IRCS_OUTBOUND_CIRCUIT_CONTENT_REFETCH_SCRAPER_*` | `IRCS_OUTBOUND_CIRCUIT_*` |
| `task-scraper-execution` | `IRCS_OUTBOUND_CIRCUIT_TASK_SCRAPER_EXECUTION_*` | `IRCS_OUTBOUND_CIRCUIT_*` |
| `identity-avatar-storage` | `IRCS_OUTBOUND_CIRCUIT_IDENTITY_AVATAR_STORAGE_*` | `IRCS_OUTBOUND_CIRCUIT_*` |
| `admin-bff-proxy` | `IRCS_OUTBOUND_CIRCUIT_ADMIN_BFF_PROXY_*` | `IRCS_OUTBOUND_CIRCUIT_*` |
| `portal-bff-proxy` | `IRCS_OUTBOUND_CIRCUIT_PORTAL_BFF_PROXY_*` | `IRCS_OUTBOUND_CIRCUIT_*` |

每个 prefix 支持：

```text
ENABLED
FAILURE_THRESHOLD
OPEN_DURATION
OPEN_DURATION_MS
HALF_OPEN_MAX_CALLS
```

优先级：

```text
caller-specific ENV/K8S
global ENV/K8S
caller-specific JVM property
global JVM property
built-in default
```

默认值保持原行为：

```text
enabled=true
failure-threshold=5
open-duration=PT30S / 30000ms
half-open-max-calls=1
```

代码结果：

- `OutboundCircuitBreakerConfig.enabledFromEnvironment(String callerKey)` 支持 caller-specific
  override、global fallback 和 ENV 高于 property。
- `OutboundHttpPolicy.withCallerCircuitBreakerKey(String)` 会按 caller key 解析 circuit config
  并设置 circuit key。
- content/task/identity adopted internal callers 已切到 `withCallerCircuitBreakerKey(...)`。
- admin/portal BFF 保留 Spring 绑定的 service-level `APP_BFF_PROXY_CIRCUIT_*` 高优先级，
  并新增 admin/portal caller-specific `IRCS_OUTBOUND_CIRCUIT_*` fallback。
- 5 个 adopted services 的 `application.yaml` 与 dev manifests 已暴露 caller-specific 默认值；
  dev 默认仍低资源、功能行为不变。

验证：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test --tests "com.prodigalgal.ircs.common.outbound.OutboundHttpClientTest" --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-content-service:compileJava :services:ircs-task-service:compileJava :services:ircs-identity-service:compileJava :services:ircs-admin-bff:compileJava :services:ircs-portal-bff:compileJava
BUILD SUCCESSFUL
```

未执行：

- live smoke。
- 全量测试。
- K8S rollout / open-circuit matrix。

统一测试待办：

- `UTB-02816-P1P2-02` 覆盖 dev K8S caller-specific override、global fallback、BFF
  service-level override、open/half-open 恢复和 identity/storage/BFF 组合回归。

当前 MARK：

```text
C3-S10 MARK 1/5 caller key -> ENV var matrix documented: done
C3-S10 MARK 2/5 shared outbound resolves caller-specific override before global fallback: done
C3-S10 MARK 3/5 adopted service configs/dev manifests expose safe defaults: done
C3-S10 MARK 4/5 lightweight config tests pass or are explicitly deferred: done
C3-S10 MARK 5/5 C3/F6/089/093 ledgers updated with partial unified-test debt: done
```
