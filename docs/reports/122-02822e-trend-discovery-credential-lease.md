# 122 028-22-E trend discovery and TMDB credential lease

日期：2026-06-13

## 目标

补齐 `028-22-D` 后遗留的两项 V1 trend-sync 行为：

- V1 `TrendSyncServiceImpl.scheduleStaggeredDiscovery` 对新建 ghost title 调用
  `ManualScraperService.backgroundSearch(keyword)`。
- V1 TMDB provider 使用 key pool；V3 scraper-service 不应长期依赖
  `APP_SCRAPER_TMDB_API_KEY` 单点配置。

## 实现

- shared contracts 新增 `TrendDiscoveryScheduleRequest`、`TrendDiscoveryScheduleResponse`、
  `TrendDiscoveryScheduledTask`，并在 `TrendSyncRunResponse` 暴露 `discoveryResult`。
- task-service 新增 `POST /internal/v1/tasks/trend-discovery`、`TaskInternalAccessPolicy`
  和 `TrendDiscoveryTaskService`。
- task-service discovery 语义：keyword 去重限量，默认 `startPage=1/endPage=1/fixedDelayMs=0`，
  对每个 datasource 创建或复用 deterministic collection task，并通过内部排队入口启动。
- scraper-service 新增 `TrendDiscoveryTaskClient` 和 `TmdbCredentialResolver`。
- scraper-service trend-sync 在 content apply 后把 `discoveryKeywords` 调给 task-service。
- TMDB trend provider 改为 credential-service lease 优先，env key 兜底。
- dev scraper manifest 增加 credential-service base URL/token 和 task-service base URL。

## 验证

Focused tests：

```powershell
.\gradlew.bat --no-daemon :services:ircs-task-service:test --tests "*TrendDiscoveryTaskServiceTest" --tests "*TrendDiscoveryInternalControllerTest" :services:ircs-scraper-service:test --tests "*TrendSyncServiceTest" --tests "*TrendDiscoveryTaskClientTest" --tests "*TmdbCredentialResolverTest"
```

结果：`BUILD SUCCESSFUL in 20s`

Affected service tests：

```powershell
.\gradlew.bat --no-daemon :services:ircs-task-service:test :services:ircs-scraper-service:test :services:ircs-ops-service:test
```

结果：`BUILD SUCCESSFUL in 22s`

生产代码扫描：

```powershell
rg -n "@Autowired" services shared --glob "!**/src/test/**" --glob "!**/build/**"
```

结果：无输出。

## MARK

```text
MARK E1 PASS V1 staggered background discovery behavior mapped to task-service owner API
MARK E2 PASS task-service creates/reuses deterministic discovery collection tasks and queues them
MARK E3 PASS scraper-service trend-sync schedules discoveryKeywords after content apply
MARK E4 PASS TMDB trend provider leases api_key from credential-service before env fallback
MARK E5 PASS focused and affected task/scraper/ops tests pass
```

## 遗留

- 本轮未执行真实 Douban/TMDB 外呼。
- 本轮未 rollout K8S；后续统一 live 测试窗口再推镜像、rollout、验证 `discoveryResult`
  与 collection task 实际入队/执行。
- task-service internal token gate 默认仍是配置后启用；dev 未新增未知 secret key。
