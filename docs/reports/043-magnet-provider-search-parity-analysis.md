# 043 magnet provider search parity analysis

状态：已完成（dev-safe provider runner、ARM64 rollout 与 ClusterIP smoke 已闭环）

更新时间：2026-06-07

## 背景

`028` 行为级 parity 审计把 magnet search 标记为未完成：V3 当前
`POST /api/v1/magnets/search/unified/{id}` 只创建 `SKIPPED`
`magnet_search_jobs`，不执行 provider run，也不写 magnet link。HTTP route 已存在，
但数据副作用没有达到 V1 行为深度。

本报告对应子任务：

- `tasks/done/028-03-magnet-provider-search-parity.md`

## 当前 V3 基线

当前 `ircs-magnet-service` 已实现：

```text
GET  /api/v1/magnet-providers
GET  /api/v1/magnet-providers/{id}
POST /api/v1/magnet-providers
PUT  /api/v1/magnet-providers/{id}
GET  /api/v1/magnets/unified/{id}
POST /api/v1/magnets/search/unified/{id}
```

其中 unified search 当前行为：

```text
MagnetQueryService.triggerUnifiedSearch
  -> existsUnifiedVideo
  -> JdbcMagnetRepository.createSkippedSearchJob

job.status=SKIPPED
job.skipped_reason=DEV_MVP_EXTERNAL_SEARCH_DISABLED
provider_runs=0
magnet_links=0
```

这只能证明入口和 job 表可写，不能证明 V1 的 provider run/link/cache 副作用。

## V1 行为摘要

V1 `MagnetSearchServiceImpl.searchUnifiedVideo` 的核心行为：

1. 读取 `UnifiedVideo`。
2. 通过 `ExternalIdQueryPlanner` 规划外部 ID，第一阶段只使用 IMDb ID。
3. 创建 `MagnetSearchJob`，初始 `RUNNING`。
4. 按 enabled provider priority 顺序执行 provider client。
5. 每个 provider/query 生成 `MagnetProviderRun`。
6. provider 候选通过 `(unified_video_id, info_hash)` upsert 到 `MagnetLink`。
7. 根据 `autoApproveAllowed` 写入 `APPROVED` 或 `CANDIDATE`。
8. 回写 job 的 `providerCodes`、`totalCandidates`、`acceptedCount`、
   `rejectedCount`、`finishedAt` 和终态。
9. 驱逐 portal detail cache。

## 表结构约束

本切片依赖以下迁移桥表：

```text
magnet_search_jobs(
  id, unified_video_id, trigger_type, status, provider_codes,
  external_id_plan, started_at, finished_at, total_candidates,
  accepted_count, rejected_count, skipped_reason, error_message
)

magnet_provider_runs(
  id, job_id, provider_id, provider_code, external_id_type,
  external_id_value, status, request_url, http_status,
  candidate_count, accepted_count, duration_ms, error_message
)

magnet_links(
  id, unified_video_id, provider_id, provider_code, info_hash,
  magnet_uri, title, size_bytes, size_label, published_at,
  seeders, leechers, quality, resolution, matched_external_id_type,
  matched_external_id_value, match_score, status, source_url,
  tags, provider_evidence, first_seen_at, last_seen_at
)
```

`magnet_links` 通过唯一约束 `(unified_video_id, info_hash)` 支撑幂等 upsert。

## 本轮方案

已采用最小 dev-safe provider runner：

- 新增可注入 runner interface。
- 默认实现为 fixture runner，只根据 `provider`、`unifiedVideoId` 和 IMDb query
  生成确定性候选。
- 默认实现不发 HTTP 请求，不解析真实公网 provider，不依赖外网。
- repository 负责：
  - 读取 unified video 的 IMDb ID。
  - 读取 enabled providers。
  - 创建 RUNNING job。
  - 缺少 IMDb ID 或 enabled provider 时写 `SKIPPED` job。
  - 对每个 provider/query 写 run。
  - 对 fixture 候选 upsert link。
  - 回写 job 统计和终态。

实现文件：

```text
services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet/MagnetProviderSearchRunner.java
services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet/FixtureMagnetProviderSearchRunner.java
services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet/MagnetQueryService.java
services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet/JdbcMagnetRepository.java
services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet/MagnetAdminDtos.java
```

## 状态语义

本轮保留 V1 风格的成功/跳过语义：

| 场景 | job 状态 | run/link |
| --- | --- | --- |
| unified video 不存在 | `404` | 不写 |
| 无 IMDb ID | `SKIPPED` | 不写 run/link |
| 无 enabled provider | `SKIPPED` | 不写 run/link |
| fixture runner 返回候选 | `SUCCESS` | 写 run/link |
| fixture runner 失败 | `FAILED` 或 `PARTIAL_SUCCESS` | 写 failed run，不写或少写 link |

## 非目标

- 不调用真实 YTS.BZ、The Pirate Bay APIBay 或其他外网 provider。
- 不恢复 provider delay、rate limit、retry、真实 HTTP status 语义。
- 不驱逐 portal detail cache。
- 不新增 RabbitMQ、Valkey、K8S Deployment、Service 或 HTTPRoute。
- 不修改 `docs/index.md`、`tasks/index.md` 或环境快照。

## 验证设计

本轮测试已覆盖：

- `MagnetQueryService` 从 `createSkippedSearchJob` 改为 provider search run。
- Controller 响应从 `SKIPPED` 推进到成功 job，并携带 `links`。
- repository/JDBC 层至少通过可替身方式证明：
  - job 被创建并回写成功终态；
  - provider run 被写入；
  - magnet link 被 upsert；
  - 缺少 IMDb ID 时仍安全跳过。

必须运行：

```text
.\gradlew.bat :services:ircs-magnet-service:test
```

实际结果：

```text
BUILD SUCCESSFUL
15 tests completed
```

## 验收标记

本切片已记录：

```text
MARK 1/3 POST /api/v1/magnets/search/unified/{id} creates non-skipped search job
MARK 2/3 provider fixture run writes magnet_provider_runs
MARK 3/3 provider fixture candidate upserts magnet_links
```

## 当前结论

028-03 已在不访问真实外网 provider 的前提下完成行为 parity：job/run/link 副作用已
恢复为可注入、可测试、dev-safe 的最小闭环。真实 provider、cache eviction 和生产安全
策略保留为后续独立任务。

## 剩余风险

- `FixtureMagnetProviderSearchRunner` 生成的是确定性 dev-safe candidate，只证明写入链路。
- 本轮未执行真实 provider 网络调用，也没有恢复 provider delay、rate limit 或 retry。
- K8S rollout、ClusterIP smoke、HTTPRoute 和 ResourceQuota 后置检查已由主线程完成。
- `portal_detail_v1` cache eviction 未纳入本切片。

## 主线程集成验收

2026-06-07 已完成 ARM64 镜像推送、K8S rollout 和 ClusterIP smoke：

```text
ircs-magnet-service@sha256:5fb8ae6bb463ab202c971d2ca8cffc1fb21491bf7d78de0510b2715ada1984e6

deployment/ircs-magnet-service Ready 1/1
deployment/ircs-admin-bff      Ready 1/1
```

Smoke：

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
