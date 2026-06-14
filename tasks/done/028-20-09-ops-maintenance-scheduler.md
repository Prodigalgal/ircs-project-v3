# 028-20-09 ops-service maintenance scheduler

状态：done

父任务：`tasks/in-progress/028-20-post-parity-runtime-platform-breakdown.md`

来源：`docs/decisions/005-post-parity-runtime-platform-decisions.md` D28

## 目标

- 在 `ops-service` 落地统一 maintenance scheduler，不新增 `maintenance-service`。
- scheduler 默认不自动运行；启用后默认 `dry-run=true`，`execute gate` 默认开启。
- ops 负责调度、gate、审计和状态查询；具体维护行为默认交给 owner service 接收执行。
- 首个 owner-service 迁移目标为 `search-reindex-unified`：ops 选取候选 unified id，调用 search-service internal sync API 入队。

## 范围

- `ops-service` maintenance runner / scheduler / config / controller。
- `search-reindex-unified` 从 ops 直写 search outbox 切为通过 search-service internal `/internal/v1/search/sync-tasks/batch`。
- service identity headers 使用 `X-IRCS-SERVICE-ID`、`X-IRCS-SERVICE-TOKEN`、`X-IRCS-SERVICE-SCOPES`。
- worker/job audit 覆盖 scheduler dry-run、execute gate refusal 和真实执行调度。
- 多 owner service 按 owner-step/workflow 分配：ops 只做调度治理，业务数据和副作用由 owner service 执行。

## 非目标

- 不在本切片恢复全服务统一自动 start。
- 不为每次维护操作创建 K8S Job。
- 不实现真实 mTLS 证书 rollout；该项归 `028-20-10` 后续 K8S 阶段。
- 不把所有高风险 V1 runner 一次性改为可执行。

## 影响文件

- `services/ircs-ops-service/src/main/java/com/prodigalgal/ircs/ops/*`
- `services/ircs-ops-service/src/main/resources/application.yaml`
- `services/ircs-ops-service/src/test/java/com/prodigalgal/ircs/ops/*`
- `tasks/in-progress/028-20-post-parity-runtime-platform-breakdown.md`
- `tasks/index.md`

## 验收标准

- scheduler 状态与 run-once API 可查询/触发。已完成。
- scheduler disabled 时无副作用；dry-run 时只写 scheduler audit，不执行 owner call。已完成。
- execute gate 关闭时拒绝执行并写失败 audit。已完成。
- execute 时调用现有 runner，runner 对 `search-reindex-unified` 通过 search-service owner API 入队。已完成。
- 配置优先级保持 runtime/ENV/K8S > DB > 默认值。已完成。
- focused tests 与 `ircs-ops-service` bootJar 通过。已完成。

## 测试方式

```powershell
.\gradlew.bat --no-daemon :services:ircs-ops-service:test
.\gradlew.bat --no-daemon :services:ircs-ops-service:bootJar
```

结果：以上命令均通过。

## 完成内容

- `MaintenanceRunnerMetadata` 增加 owner-step 元数据，`search-reindex-unified` 标记为 `ircs-search-service` owner step。
- `MaintenanceReindexCommand` 从 ops 直接写 `search_sync_tasks` 改为调用 `MaintenanceSearchSyncClient`。
- `SearchServiceMaintenanceSyncClient` 通过 shared `OutboundHttpClient` 访问 search-service internal batch API，并发送 service identity、scope 与 correlation header。
- `MaintenanceSchedulerService` / `MaintenanceScheduler` 增加统一 scheduler：默认 disabled，启用后默认 dry-run，execute gate 默认开启。
- `MaintenanceController` 增加 `/api/v1/ops/maintenance/scheduler` 和 `/api/v1/ops/maintenance/scheduler/run-once`。
- dev K8S manifest 显式保持 scheduler disabled/dry-run，并配置 search-service owner base URL。
- 单测覆盖 disabled、dry-run、execute gate refusal、execute delegation、owner client body/headers 和配置优先级。

## 多副本 owner 策略

- ops 调用 K8S Service DNS，不调用 Pod IP，不依赖 sticky session。
- owner API 必须无状态；`search-reindex-unified` 通过 search-service 写共享 `search_sync_tasks`。
- search-service 多副本 relay 依赖 DB `FOR UPDATE SKIP LOCKED`、`locked_until` 和 expired lease restore 分片领取任务。
- 搜索索引写入必须保持幂等；重复 `INDEX unified_video(id)` 只能覆盖同一文档。
- 若未来 search-service 引入本地内存索引，不能使用 competing consumer 只更新单副本；必须改为共享索引、广播更新或每副本自建增量索引。

## 遗留边界

- K8S live smoke 当时后置；真实 mTLS rollout 后续已由 `028-20-25` 完成。
- 其他高风险 runner 仍保留 dev refusal，后续按 owner service 能力逐项接回。
- 多副本生产调度 lease 当前只做进程内防重入；若生产 ops-service 多副本，需要补 DB/leader-election lease。
- owner idempotency key 尚未落 DB 唯一约束；后续生产化建议用 `maintenanceRunId + stepName + entityId + operation` 做 retry dedupe。
