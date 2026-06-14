# 028-22-F unified live smoke execution

状态：已完成

父任务：

- `tasks/in-progress/028-22-production-candidate-closeout-parent.md`

报告：

- `docs/reports/123-02822f-trend-discovery-live-orchestrator.md`

## 目标

- 把 `028-22-E` trend discovery / credential lease 接入统一测试入口。
- 先完成 no-external live smoke，避免默认触发真实 provider 成本和写侧副作用。
- 保留真实 Douban/TMDB provider window，后续统一测试时显式打开。

## 已完成

- 新增 `scripts/smoke/trend-sync-discovery-02822f-smoke.ps1`。
- `028-19` orchestrator 新增 `TREND-02822-F` 批次。
- task-service / scraper-service / ops-service ARM64 dev 镜像 rollout 成功。
- 修复 scraper-service 单构造器 / shared outbound bean 启动问题。
- 修复 task/scraper/ops dev manifest 与 live probe handler 漂移，`kubectl apply` 恢复可用。
- no-external smoke `MARK 6/6` 通过，其中 provider-gated 步骤按设计 skipped。
- 2026-06-14 provider-gated live window 已通过：
  - 从 `ircs-scraper-service` Pod 内真实触发 `/internal/v1/scraper/trends/sync`。
  - Douban/TMDB provider 聚合返回 `fetchedItems=20`、`providerErrors=[]`。
  - content-service 创建 `20` 个 trend ghost。
  - task-service 创建并排队 `3` 个 discovery collection task。
  - discovery task 全部 `COMPLETED`，其中 `大濛` 任务 `stat_processed=1/stat_success=1`。
  - search outbox relay 已在 dev 开启，DB outbox 清零，Rabbit search 队列最终 drain。
  - search worker-job audit：`search.sync.raw succeeded=481`、`search.sync.unified succeeded=20`。

## 验证

```powershell
.\gradlew.bat --no-daemon :services:ircs-task-service:test --tests *TrendDiscoveryTaskServiceTest --tests *TrendDiscoveryInternalControllerTest :services:ircs-scraper-service:test --tests *TrendSyncServiceTest --tests *TrendDiscoveryTaskClientTest --tests *TmdbCredentialResolverTest
```

结果：`BUILD SUCCESSFUL in 9s`

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\trend-sync-discovery-02822f-smoke.ps1
```

结果：no-external `MARK 6/6`。

```powershell
rg -n "@Autowired" services shared --glob "!**/src/test/**" --glob "!**/build/**"
```

结果：无输出。

## 说明

- 本次按用户最新规则允许污染开发 DB，未清理 20 个 trend ghost 与 3 个 discovery task。
