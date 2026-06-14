# 028-22-C sanitize owner runner

状态：完成

父任务：

- `tasks/in-progress/028-22-production-candidate-closeout-parent.md`

报告：

- `docs/reports/120-02822c-sanitize-owner-runner.md`

## 目标

将 V1 `sanitize/init` 从 ops-service 占位拒绝项升级为按 normalization-worker owner
边界执行的 high-write runner，保持 V1 只重置 raw video normalization status 的真实语义。

## 改动

- shared contracts 增加 `NormalizationMaintenanceRunResponse`。
- normalization-worker 增加 reset-normalization internal API，并保留 service identity / scopes。
- raw reset SQL 只更新 `normalization_status = 'PENDING'` 与 `updated_at = now()`。
- ops-service 增加 normalization owner reset client，`sanitize` 进入 audited runner registry。
- 保留 LLM cleaning 原 circuit key 与错误前缀，避免复用 maintenance reset 语义污染旧 runner。

## 验证

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --tests "*RawVideoNormalizationServiceTest" --tests "*RawVideoNormalizationRepositoryTest" --tests "*NormalizationMaintenanceInternalControllerTest" :services:ircs-ops-service:test --tests "*MaintenanceRunnerServiceTest" --tests "*NormalizationLlmMaintenanceClientTest" --tests "*MaintenanceSessionServiceTest" --tests "*OpsConfigValuesTest"
```

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test :services:ircs-ops-service:test
```

结果：`BUILD SUCCESSFUL`。

## 遗留

- 未做 live rollout / live high-write smoke。
- `trend-sync` 后续已由 `028-22-D` 完成；真实 live smoke 后置统一测试窗口。
