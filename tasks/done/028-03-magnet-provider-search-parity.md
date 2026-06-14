# 028-03 magnet external provider search parity

状态：已完成

创建日期：2026-06-07

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/041-v1-v3-behavior-parity-gap-audit.md`
- `docs/reports/043-magnet-provider-search-parity-analysis.md`
- `docs/migrations/017-magnet-service-provider-cache-extraction.md`
- `tasks/done/017-03-magnet-provider-cache-mvp.md`

## 背景

`025` 已确认 `POST /api/v1/magnets/search/unified/{id}` 的 HTTP Controller
入口在 V3 存在；`017-03` 已完成 `ircs-magnet-service` provider/cache 只读 MVP。
但当前 `triggerUnifiedSearch` 仍只创建 `status=SKIPPED` 的
`magnet_search_jobs`，`skipped_reason=DEV_MVP_EXTERNAL_SEARCH_DISABLED`，
不写 `magnet_provider_runs`，也不写 `magnet_links`。

V1 `MagnetSearchServiceImpl` 的行为是：为 unified video 规划外部 ID 查询，创建
search job，按 enabled provider 执行 provider run，把候选 upsert 到
`magnet_links`，最后回写 job 统计和终态。完整真实 provider 接入涉及公网网络、
内容策略、延迟控制和 cache eviction，本切片只推进 dev-safe 行为副作用。

## 目标

- 保持 `POST /api/v1/magnets/search/unified/{id}` 的 V1 兼容入口。
- 从 `SKIPPED` job 推进到 dev-safe provider runner 执行。
- 默认不访问真实外网 provider。
- 对存在 IMDb ID 的 unified video 写入：
  - `magnet_search_jobs`
  - `magnet_provider_runs`
  - `magnet_links`
- 通过本地测试证明 job/run/link 写入和响应统计。

## 非目标

- 不调用 YTS.BZ、The Pirate Bay APIBay 或其他真实外部 provider。
- 不新增公网 HTTPRoute。
- 不修改 K8S dev 环境快照、`docs/index.md` 或 `tasks/index.md`。
- 不实现 portal cache eviction。
- 不扩展 admin auth 或审计策略。
- 不做生产级 provider 熔断、限速、重试或内容安全审核。

## 输入

- HTTP：`POST /api/v1/magnets/search/unified/{id}`
- V3 service：`services/ircs-magnet-service`
- 迁移桥表：
  - `unified_videos`
  - `magnet_providers`
  - `magnet_search_jobs`
  - `magnet_provider_runs`
  - `magnet_links`
- V1 参考：
  - `MagnetSearchServiceImpl`
  - `ExternalIdQueryPlanner`
  - `MagnetProviderClient`
  - `MagnetSearchJobDTO`

## 输出

- `services/ircs-magnet-service` 中的 dev-safe provider search runner。
- JDBC 写入 job/run/link 的最小实现。
- 单元测试或 JDBC slice 测试证明副作用。
- 本任务和分析报告的实现结果、测试结果与剩余风险回写。

## 影响范围

包含：

- `tasks/in-progress/028-03-magnet-provider-search-parity.md`
- `docs/reports/043-magnet-provider-search-parity-analysis.md`
- `services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet/**`
- `services/ircs-magnet-service/src/test/java/com/prodigalgal/ircs/magnet/**`

不包含：

- `docs/index.md`
- `tasks/index.md`
- `docs/k8s/current-environment-2026-06-02.md`
- `deploy/k8s/dev/**`
- 其他业务服务

## 依赖

- `unified_videos.id` 存在。
- `unified_videos.imdb_id` 或等价 IMDb 字段可用于第一阶段 external ID plan。
- `magnet_providers.enabled=true` provider 作为 run 执行对象。
- `magnet_links` 的 `(unified_video_id, info_hash)` 唯一约束用于幂等 upsert。
- 本地测试可使用 H2 PostgreSQL mode 或 repository mock 验证写入语义。

## 风险

- 如果 live schema 的 unified video IMDb 字段与 V1 不同，planner 需要保守降级。
- fixture runner 写入的是 dev-safe 候选，不代表真实 provider 搜索质量。
- 默认自动批准逻辑需遵循 provider `auto_approve_allowed`，避免把不可自动批准的
  provider 结果直接暴露为 approved link。
- 本切片不做 portal cache eviction，因此详情页缓存刷新仍是后续风险。

## 验收标准

- missing HTTP Controller/BFF route 仍保持 0 的既有前提不被破坏。
- unified video 不存在时仍返回 `404`。
- unified video 缺少可用 IMDb ID 时可创建 `SKIPPED` job，且不写 provider run/link。
- unified video 有 IMDb ID 且存在 enabled provider 时：
  - job 终态为 `SUCCESS` 或等价成功态；
  - `provider_codes` 和 `external_id_plan` 有值；
  - 至少写入 1 条 `magnet_provider_runs`；
  - 至少写入 1 条 `magnet_links`；
  - 响应中的 `totalCandidates`、`acceptedCount`、`rejectedCount` 与写入结果一致。
- 测试证明默认 runner 不访问真实外网 provider。

## 测试边界

本切片必须运行：

```text
.\gradlew.bat :services:ircs-magnet-service:test
```

可选但本轮不强制：

```text
.\gradlew.bat :services:ircs-magnet-service:bootJar
```

本轮不做 K8S rollout、ClusterIP smoke、HTTPRoute 和 ResourceQuota 后置检查；这些由
主 agent 后续集成时统一执行。

## 关联文档

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/041-v1-v3-behavior-parity-gap-audit.md`
- `docs/reports/043-magnet-provider-search-parity-analysis.md`
- `docs/migrations/017-magnet-service-provider-cache-extraction.md`

## 关联任务

- 父任务：`tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- 前置任务：`tasks/done/017-03-magnet-provider-cache-mvp.md`
- 同批行为 parity：`028-02`、`028-04`、`028-05`

## 当前状态

2026-06-07：

- 已确认当前 V3 行为仍是 `DEV_MVP_EXTERNAL_SEARCH_DISABLED` / `SKIPPED` job。
- 已限定本轮最小实现为 dev-safe fixture provider runner。
- 已实现 dev-safe fixture provider runner，默认不访问真实外网 provider。
- 已把 `POST /api/v1/magnets/search/unified/{id}` 推进到可写
  `magnet_search_jobs`、`magnet_provider_runs`、`magnet_links` 的最小闭环。
- 已保留 unified video 不存在时的 `404`，无 IMDb ID、无 enabled provider 或无支持
  provider 时仍安全创建 `SKIPPED` job。
- 已补充 JDBC 测试证明 job/run/link 三张表副作用。

## 实现结果

代码变更：

```text
services/ircs-magnet-service/build.gradle
services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet/FixtureMagnetProviderSearchRunner.java
services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet/JdbcMagnetRepository.java
services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet/MagnetAdminDtos.java
services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet/MagnetProviderSearchRunner.java
services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet/MagnetQueryService.java
services/ircs-magnet-service/src/test/java/com/prodigalgal/ircs/magnet/JdbcMagnetRepositoryTest.java
services/ircs-magnet-service/src/test/java/com/prodigalgal/ircs/magnet/MagnetControllerTest.java
services/ircs-magnet-service/src/test/java/com/prodigalgal/ircs/magnet/MagnetQueryServiceTest.java
```

行为结果：

```text
MARK 1/3 POST /api/v1/magnets/search/unified/{id} creates non-skipped search job
MARK 2/3 provider fixture run writes magnet_provider_runs
MARK 3/3 provider fixture candidate upserts magnet_links
```

## 验证结果

已运行：

```text
.\gradlew.bat :services:ircs-magnet-service:test
```

结果：

```text
BUILD SUCCESSFUL
15 tests completed
```

覆盖点：

```text
MagnetQueryServiceTest
- existing unified video + IMDb + enabled provider -> SUCCESS job
- provider runner returns fixture candidate -> createProviderRun + upsertLink
- missing unified video -> 404
- missing IMDb -> SKIPPED and no runner

JdbcMagnetRepositoryTest
- createRunningSearchJob writes magnet_search_jobs
- createProviderRun writes magnet_provider_runs
- upsertLink writes magnet_links
- skipped job does not write provider run/link

MagnetControllerTest
- POST /api/v1/magnets/search/unified/{id} returns SUCCESS job and links
```

## 遗留问题

- 主 agent 集成阶段已执行 K8S rollout、ClusterIP smoke、HTTPRoute 和 ResourceQuota
  后置检查。
- 本轮 fixture runner 只证明 provider run/link 副作用，不代表真实 provider 搜索质量。
- portal detail cache eviction 仍未恢复，需后续单独任务处理。
- 真实 provider、限速、重试、内容安全审核和 admin auth 不在本切片内。

## 主线程集成验收

2026-06-07：

```text
.\gradlew.bat :services:ircs-magnet-service:test --rerun-tasks
BUILD SUCCESSFUL

ircs-magnet-service@sha256:5fb8ae6bb463ab202c971d2ca8cffc1fb21491bf7d78de0510b2715ada1984e6

deployment/ircs-magnet-service Ready 1/1
deployment/ircs-admin-bff      Ready 1/1
```

K8S smoke：

```text
.\scripts\smoke\behavior-02802-02804-parity-smoke.ps1
MARK 3/8 POST /api/v1/magnets/search/unified/{id} creates non-skipped search job
MARK 4/8 provider fixture run writes magnet_provider_runs
MARK 5/8 provider fixture candidate upserts magnet_links
```

后置检查：

```text
kubectl -n ircs-dev get httproute -> No resources found
ResourceQuota: pods=28/30, services=24/26
```
