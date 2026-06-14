# 028-22-E background discovery and TMDB credential lease

状态：已完成实现，live smoke 后置

父任务：

- `tasks/in-progress/028-22-production-candidate-closeout-parent.md`

报告：

- `docs/reports/122-02822e-trend-discovery-credential-lease.md`

## 目标

- 补齐 V1 trend-sync 新建 ghost 后触发 background discovery 的副作用。
- 将 scraper-service TMDB trend provider 的 key 获取接入 credential-service lease。
- 保持 dev 低风险：不立即执行真实 provider/live rollout。

## 实现结果

- shared trend contract 增加 discovery schedule request/response。
- task-service 增加 `POST /internal/v1/tasks/trend-discovery`。
- task-service 对 discovery keywords 按 datasource 创建/复用 deterministic collection task，并通过内部排队入口启动。
- scraper-service trend-sync 在 content apply 后调用 task-service discovery API。
- scraper-service TMDB provider 改为 credential-service lease 优先，env key 兜底。
- dev scraper manifest 增加 credential-service/task-service base URL 与 credential token。

## 验证

```powershell
.\gradlew.bat --no-daemon :services:ircs-task-service:test --tests "*TrendDiscoveryTaskServiceTest" --tests "*TrendDiscoveryInternalControllerTest" :services:ircs-scraper-service:test --tests "*TrendSyncServiceTest" --tests "*TrendDiscoveryTaskClientTest" --tests "*TmdbCredentialResolverTest"
```

结果：`BUILD SUCCESSFUL in 20s`

```powershell
.\gradlew.bat --no-daemon :services:ircs-task-service:test :services:ircs-scraper-service:test :services:ircs-ops-service:test
```

结果：`BUILD SUCCESSFUL in 22s`

```powershell
rg -n "@Autowired" services shared --glob "!**/src/test/**" --glob "!**/build/**"
```

结果：无输出。

## 后置

- 真实 Douban/TMDB provider smoke。
- K8S ARM64 镜像推送与 rollout。
- live DB 验证 discovery task 创建、排队、执行和清理。
