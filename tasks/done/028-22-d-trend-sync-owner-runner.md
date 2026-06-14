# 028-22-D trend sync owner runner

状态：完成

父任务：

- `tasks/in-progress/028-22-production-candidate-closeout-parent.md`

报告：

- `docs/reports/121-02822d-trend-sync-owner-runner.md`

## 目标

将 V1 `trend/sync` 从 ops-service 占位拒绝项升级为按 scraper/content owner 边界执行的
high-write runner，迁移 provider 抓取、external id trend 更新时间、title/year 相似命中、
ghost unified 创建和 search sync 副作用。

## 改动

- shared contracts 增加 trend item、apply response 和 run response。
- content-service 增加 trend apply internal API 与 V1 数据副作用实现。
- scraper-service 增加 Douban/TMDB trend providers、trend sync service、content owner client 和 internal API。
- ops-service 增加 scraper trend owner client，`trend-sync` 进入 audited runner registry。
- 三个服务补 runtime 配置入口。

## 验证

```powershell
.\gradlew.bat --no-daemon :services:ircs-content-service:test --tests "*TrendSyncContentServiceTest" --tests "*TrendSyncContentInternalControllerTest" :services:ircs-scraper-service:test --tests "*TrendSyncServiceTest" --tests "*TrendSyncInternalControllerTest" :services:ircs-ops-service:test --tests "*MaintenanceRunnerServiceTest" --tests "*ScraperTrendSyncMaintenanceClientTest" --tests "*OpsConfigValuesTest"
```

```powershell
.\gradlew.bat --no-daemon :services:ircs-content-service:test :services:ircs-scraper-service:test :services:ircs-ops-service:test
```

结果：`BUILD SUCCESSFUL`。

## 遗留

- 未做 K8S rollout / live provider smoke / live DB smoke。
- V1 staggered background discovery 与 TMDB credential-service lease 已由 `028-22-E` 补齐；
  本任务只保留 `trend-sync` owner runner 主链路记录。
