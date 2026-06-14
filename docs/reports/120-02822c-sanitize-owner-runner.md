# 120 028-22-C sanitize owner runner

状态：实现完成，live rollout / smoke 后置到统一测试批次

日期：2026-06-13

## V1 行为

V1 `POST /api/v1/ops/maintenance/sanitize/init` 的真实数据副作用只有一项：

```text
UPDATE RawVideo v
   SET v.normalizationStatus = PENDING,
       v.updatedAt = now
```

V1 不清理 raw relation，不重置 retry count，不清空 `next_normalization_retry_time`，也不重建
search / aggregation 索引。

## V3 实现

- shared contracts：
  - `NormalizationMaintenanceRunResponse`
- normalization-worker：
  - `POST /internal/v1/normalization/maintenance/raw-videos/reset-normalization`
  - internal API 继续走 service identity / scopes。
  - `RawVideoNormalizationService.resetAllNormalizationPending` 先采样、计数，再执行 reset。
  - `RawVideoNormalizationRepository.resetAllNormalizationPending` 保持 V1 语义，只更新
    `normalization_status = 'PENDING'` 与 `updated_at = now()`。
- ops-service：
  - `MaintenanceRunnerService` 将 `sanitize` 注册为 HIGH risk / devAllowed / supportsDryRun runner。
  - `NormalizationLlmMaintenanceClient.resetAllNormalization` 调用 normalization-worker owner API，
    使用 `INTERNAL_SERVICE` policy、service identity headers、`X-Correlation-Id` 和独立
    `ops-normalization-maintenance` circuit key。
  - 原 LLM cleaning runner 的 circuit key 与错误前缀保持不变。

## 验证

聚焦测试：

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --tests "*RawVideoNormalizationServiceTest" --tests "*RawVideoNormalizationRepositoryTest" --tests "*NormalizationMaintenanceInternalControllerTest" :services:ircs-ops-service:test --tests "*MaintenanceRunnerServiceTest" --tests "*NormalizationLlmMaintenanceClientTest" --tests "*MaintenanceSessionServiceTest" --tests "*OpsConfigValuesTest"
```

受影响服务完整测试：

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test :services:ircs-ops-service:test
```

结果：

```text
BUILD SUCCESSFUL
```

测试 XML 证据：

```text
NormalizationMaintenanceInternalControllerTest tests=2 failures=0 errors=0
RawVideoNormalizationRepositoryTest tests=12 failures=0 errors=0
RawVideoNormalizationServiceTest tests=5 failures=0 errors=0
MaintenanceRunnerServiceTest tests=10 failures=0 errors=0
MaintenanceSessionServiceTest tests=2 failures=0 errors=0
NormalizationLlmMaintenanceClientTest tests=3 failures=0 errors=0
OpsConfigValuesTest tests=11 failures=0 errors=0
```

## 边界

- 本轮未执行 K8S rollout，未触碰 live DB。
- `sanitize` 是全量 high-write runner；live 执行必须放到统一测试窗口，并先确认样本、备份、
  namespace 和回滚策略。
- V3 当前保持 V1 最小副作用，不额外清理 retry / queue / relation / search 状态。

## MARK

```text
MARK C1 PASS V1 sanitize reset-only behavior documented
MARK C2 PASS normalization-worker reset-normalization owner API implemented
MARK C3 PASS ops-service sanitize runner calls normalization owner with service identity and audit
MARK C4 PASS LLM cleaning runner circuit/error semantics preserved
MARK C5 PASS focused and affected service tests pass
```
