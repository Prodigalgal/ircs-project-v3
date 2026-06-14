# 028-16-B16 search outbox stats parity

状态：implementation first closure，统一 live smoke 后置

更新时间：2026-06-10

## 目标

补齐 V1 `search_sync_tasks` search outbox 的最小可观察统计能力，让 search-service 能以只读方式暴露
pending/processing/completed/failed、due pending、entity type、operation 和 retry 计数，便于后续统一测试窗口检查
search outbox residue 与补偿链路。

## 范围

- 新增 `GET /api/v1/search/ops/search-sync-tasks/stats`。
- 查询迁移表 `search_sync_tasks`，只读聚合，不修改 outbox 状态。
- 表缺失或 schema 未迁移时返回 `available=false` 和稳定 reason，不让 ops 查询变成 500。
- 响应设置 `Cache-Control: no-store`，避免任务状态被缓存。

## 非目标

- 本切片不实现 V1 relay/poller 的任务认领、重试或发布。
- 本切片不执行 K8S/dev DB live smoke，不触碰真实 outbox 数据。
- 不调整 search producer/consumer 的已有 queue 语义。

## 影响文件

- `services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/sync/SearchSyncTaskOpsController.java`
- `services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/sync/SearchSyncTaskOpsRepository.java`
- `services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/sync/SearchSyncTaskStatsResponse.java`
- `services/ircs-search-service/src/test/java/com/prodigalgal/ircs/search/sync/SearchSyncTaskOpsControllerTest.java`
- `services/ircs-search-service/src/test/java/com/prodigalgal/ircs/search/sync/SearchSyncTaskOpsRepositoryTest.java`

## 验收标准

- 有表时聚合 `total/pending/processing/completed/failed/duePending/rawVideo/unifiedVideo/index/delete/maxRetryCount`。
- 无表时返回 `available=false`、`unavailableReason=search_sync_tasks_unavailable`、计数为 0。
- Controller 返回 no-store header。
- focused tests 通过。

## 验证

已执行：

```powershell
.\gradlew.bat --no-daemon :services:ircs-search-service:test --tests "*SearchSyncTaskOps*" --rerun-tasks
```

结果：`BUILD SUCCESSFUL`

补充核查：

```powershell
python NUL scan for non-build files
```

结果：源码和任务文档中的 NUL 损坏文件已清零；仅 build/test binary index 仍可能是 1 字节 NUL，属 Gradle 产物。

## 后续

- 统一测试窗口中补 dev DB 真实 endpoint smoke。
- 如需复刻 V1 relay/poller，再拆独立 B17，避免把只读 ops 与状态机写操作混在一起。
