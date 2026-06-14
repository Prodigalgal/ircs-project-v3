# 121 028-22-D trend sync owner runner

状态：实现完成，真实 provider / K8S rollout / live smoke 后置到统一测试批次

日期：2026-06-13

## V1 行为

V1 `POST /api/v1/ops/maintenance/trend/sync` 直接调用 `TrendSyncService.syncTrends()`：

1. 并发调用 `TrendListProvider`，当前包括 Douban 和 TMDB。
2. 候选按 `doubanId`、`tmdbId`、`title:year` 去重。
3. 对已有 `doubanId` / `tmdbId` 的 `unified_videos` 批量更新 `last_trend_at`。
4. 对无 external id 的候选按 `year + title similarity >= aggregation.db.similarity-threshold`
   命中已有 unified 后更新 `last_trend_at`。
5. 剩余候选创建 ghost `UnifiedVideo`，写入 title/year/publishedAt/score/description/external ids/poster
   和 `last_trend_at`。
6. 对新建 ghost 的 title 触发 staggered background discovery。

## V3 实现

- shared contracts：
  - `TrendItemPayload`
  - `TrendSyncApplyRequest`
  - `TrendSyncApplyResponse`
  - `TrendSyncRunResponse`
- content-service：
  - `POST /internal/v1/content/trends/apply`
  - `TrendSyncContentService` 负责 V1 数据副作用：external id 更新时间、title/year 相似命中、
    ghost unified 创建。
  - 写入前接入 `ContentMaintenanceGate`。
  - 更新/创建 unified 后发布 `UNIFIED_VIDEO INDEX` search sync outbox。
  - internal API 走 service identity / scopes。
- scraper-service：
  - `POST /internal/v1/scraper/trends/sync`
  - `DoubanTrendListProvider` 和 `TmdbTrendListProvider` 已接回 V1 provider 抓取逻辑。
  - provider 单点失败写入 `providerErrors`，不阻断其他 provider。
  - 抓取为空时按 V1 语义不调用 content apply。
  - 通过 `TrendSyncContentClient` 调用 content owner API。
- ops-service：
  - `MaintenanceRunnerService` 将 `trend-sync` 注册为 HIGH risk / devAllowed / supportsDryRun runner。
  - `ScraperTrendSyncMaintenanceClient` 调用 scraper owner API，透传 `correlationId`，使用
    `INTERNAL_SERVICE` policy、service identity headers 和 `ops-scraper-trend-maintenance` circuit key。
  - runner 继续写 worker/job audit。

## 验证

聚焦测试：

```powershell
.\gradlew.bat --no-daemon :services:ircs-content-service:test --tests "*TrendSyncContentServiceTest" --tests "*TrendSyncContentInternalControllerTest" :services:ircs-scraper-service:test --tests "*TrendSyncServiceTest" --tests "*TrendSyncInternalControllerTest" :services:ircs-ops-service:test --tests "*MaintenanceRunnerServiceTest" --tests "*ScraperTrendSyncMaintenanceClientTest" --tests "*OpsConfigValuesTest"
```

受影响服务完整测试：

```powershell
.\gradlew.bat --no-daemon :services:ircs-content-service:test :services:ircs-scraper-service:test :services:ircs-ops-service:test
```

结果：

```text
BUILD SUCCESSFUL
```

测试 XML 证据：

```text
TrendSyncContentInternalControllerTest tests=1 failures=0 errors=0
TrendSyncContentServiceTest tests=1 failures=0 errors=0
TrendSyncInternalControllerTest tests=1 failures=0 errors=0
TrendSyncServiceTest tests=2 failures=0 errors=0
MaintenanceRunnerServiceTest tests=11 failures=0 errors=0
ScraperTrendSyncMaintenanceClientTest tests=1 failures=0 errors=0
```

## 边界

- 本轮未执行真实 Douban/TMDB 外呼。
- 本轮未执行 K8S rollout，未触碰 live DB。
- V1 的 staggered background discovery 与 TMDB credential-service lease 已由 `028-22-E` 补齐。
- 本报告只覆盖 `028-22-D` owner runner 主链路；真实 provider smoke 与 K8S rollout
  后置统一测试窗口。

## MARK

```text
MARK D1 PASS V1 trend-sync provider/update/title-match/ghost-create behavior documented
MARK D2 PASS content-service trend apply owner API implemented
MARK D3 PASS scraper-service trend provider aggregation and content owner client implemented
MARK D4 PASS ops-service trend-sync runner executes scraper owner with audit
MARK D5 PASS focused and affected service tests pass
```
