# 028-16-B15 aggregation pipeline runtime live smoke task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `tasks/in-progress/028-16-b14-pipeline-runtime-order-failure-isolation.md`
- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`
- `scripts/smoke/search-producer-aggregation-02809-smoke.ps1`
- `docs/reports/064-search-producer-aggregation-live-smoke-02809.md`

## 背景

B14 已在本地测试中补回 V1 `AggregationPipeline` 的 runtime 契约：按
`BasicAttribute(10) -> ExternalId(20) -> Metadata(30) -> DynamicField(40) ->
Category(50) -> CoverImage(60)` 执行，并对单个 handler 失败做隔离。当前聚合 B 线剩余
gap 已收敛为 K8S live smoke。

`028-09` 已证明 aggregation scheduler 在 `ircs-dev` 中可把单 raw 从
`READY/PENDING` 推进到 `BOUND`，并刷新 raw/unified search document。但该 smoke 只覆盖
基础 scalar 与 search sync，不足以证明 B14 runtime 的 V1 handler 粒度输出、relation
rebuild、category 投票、cover promote command 和 cleanup 在真实 dev 环境中闭环。

## 目标

- 复用 `028-09` 的 Kubernetes smoke 模式，不新增 Deployment、Service、HTTPRoute 或 Job。
- 允许脚本临时 patch `ircs-aggregation-worker` 的 batch-size/fixed-delay 运行时 env 以证明
  multi-raw 聚合；脚本必须 capture 原值并在 finally 中 restore。
- 在 `ircs-dev` PostgreSQL 写入 multi-raw fixture，通过 aggregation-worker scheduler
  自动 claim，而不是直接调用 repository。
- 验证真实 runtime 输出：
  - BasicAttribute：title/year 投票、score 最大值、published_at 首个非空。
  - ExternalId：填充有效 external IDs，忽略空值和 `0`。
  - Metadata：actors/directors/genres/languages/areas relation union。
  - DynamicField：alias/description/subtitle 最长、remarks 最新、episodes/duration/season
    首个非空。
  - Category：raw category -> standard category 投票写入 `unified_videos.category_id`。
  - CoverImage：选择 `LOCAL` + `LOCAL_STORED` managed cover，并发布 `PROMOTE_COVER_R2`
    command，由 storage-service 在 R2 inactive 时保持不调度上传。
- 验证 raw/unified search sync 仍刷新 ES document。
- 验证 smoke 前后队列/DLQ、DB、ES、HTTPRoute 与 ResourceQuota 均无污染或冲突。

## 非目标

- 不执行真实 R2 upload；storage-service R2 runtime 默认保持 inactive。
- 不修改 K8S manifest、ResourceQuota、Secret 或 ConfigMap 文件；live smoke 只允许临时 patch
  Deployment env 并 restore。
- 不新增 image tag；若当前 dev Pod 未包含 B14/B15 代码，可以按既有 Jib ARM64 路径只推送
  `ircs-aggregation-worker:dev` 并 rollout restart 当前 Deployment，不新增 Pod/Service。
- 若当前 dev storage-service 尚未声明 `q.storage.cover_r2_promote` 队列和 consumer，可以按既有
  Jib ARM64 路径只推送 `ircs-storage-service:dev` 并 rollout restart 当前 Deployment，用于
  `PROMOTE_COVER_R2` consumer 拓扑；仍不得新增 Pod/Service/HTTPRoute。
- 不覆盖 duplicate victim merge、ES-backed context recall 或 provider live smoke；这些由
  B2-B14 的本地 tests 和后续独立 smoke 继续承接。

## 输入

```text
scripts/smoke/search-producer-aggregation-02809-smoke.ps1
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/AggregationPipelineRuntime.java
services/ircs-aggregation-worker/src/main/java/com/prodigalgal/ircs/aggregation/JdbcAggregationRepository.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/**
services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/**
```

## 输出

- 新增或更新 aggregation pipeline runtime live smoke 脚本。
- K8S live smoke 执行证据：MARK、DB/ES/RabbitMQ/HTTPRoute/ResourceQuota/cleanup。
- B15 任务、089 报告、089 迁移记录、docs/tasks 索引回写。

## 影响范围

```text
scripts/smoke/**
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md
tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md
tasks/index.md
docs/index.md
```

## 依赖

- `kubectl` 可访问 `ircs-dev`。
- `ircs-aggregation-worker`、`ircs-search-service`、`ircs-storage-service`、PostgreSQL、
  RabbitMQ 和 Elasticsearch 在 `ircs-dev` 可用。
- search sync queues 清洁或可由 smoke 明确 SKIPPED。
- dev cluster 仍无公共 HTTPRoute。

## 风险

- aggregation scheduler 固定延迟轮询，fixture 需要等待窗口。
- dev Pod 可能尚未 rollout 到 B14/B15 镜像；此时 live smoke 不能证明 runtime，必须先只推
  aggregation-worker ARM64 `:dev` 镜像并 `rollout restart`，或记录 SKIPPED/runtime mismatch。
- 当前 dev manifest `APP_AGGREGATION_BATCH_SIZE=1`，multi-raw cluster smoke 必须临时 patch 到
  `3` 或更高，跑完恢复原值。
- 当前 dev storage-service 可能仍未 rollout 到含 `PROMOTE_COVER_R2` 的镜像；如果
  `q.storage.cover_r2_promote` 不存在，则 B15 只能先记录 runtime/queue mismatch，或先仅刷新现有
  storage-service 镜像再验证。
- 直接写共享 dev DB 必须使用唯一 `codex02816b15*` marker，并在 finally 中覆盖所有关系表、
  cover image、storage command、ES document 和 raw/unified residue。
- R2 inactive 是预期，验证点应是 command 被消费后不调度上传，而不是上传成功。
- live smoke 发现 aggregation status heartbeat 复用 `raw_videos.updated_at` 会污染 V1
  DynamicField `remarks` 的“最新更新时间”选择语义；修复必须拆出独立
  `aggregation_status_updated_at`，避免 scheduler claim/markBound 改写 raw 业务更新时间。

## 验收标准

```text
B15 MARK 1/8 ircs-dev aggregation/search/storage consumers are ready and no HTTPRoute exists
B15 MARK 2/8 multi-raw pipeline fixture is seeded and pre-cleaned from DB/ES/queues
B15 MARK 3/8 scheduler binds fixture raws to one unified through aggregation-worker
B15 MARK 4/8 runtime scalar and category outputs match V1 handler rules
B15 MARK 5/8 runtime metadata relations match V1 union rules
B15 MARK 6/8 cover stage selects managed cover and storage command is safely consumed/gated
B15 MARK 7/8 raw/unified ES documents are refreshed and queues/DLQs stay empty
B15 MARK 8/8 cleanup leaves no DB/ES/Rabbit/HTTPRoute/port-forward residue and resource quota is unchanged
```

## 测试边界

```powershell
$null = [scriptblock]::Create((Get-Content -LiteralPath 'scripts/smoke/aggregation-pipeline-runtime-02816b15-smoke.ps1' -Raw))
.\scripts\smoke\aggregation-pipeline-runtime-02816b15-smoke.ps1
```

## 当前状态

2026-06-09：任务已创建，准备实现并执行 smoke。

2026-06-09：只读侦察确认当前 dev aggregation-worker 镜像 tag 为
`harbor.mnnu.eu.org/ircs/ircs-aggregation-worker:dev`，`imagePullPolicy=Always`，
Deployment 单副本 `Recreate`，Hikari max pool 为 `2`，资源 request/limit 为
`25m/128Mi` 与 `250m/512Mi`。B15 若需要部署最新 B14/B15 代码，应只执行：

```powershell
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-aggregation-worker' -ImageName ircs-aggregation-worker -Tag dev -Architecture arm64 -Os linux
kubectl rollout restart deployment/ircs-aggregation-worker -n ircs-dev
kubectl rollout status deployment/ircs-aggregation-worker -n ircs-dev --timeout=240s
```

不得新增 Deployment/Service/HTTPRoute，不扩大副本，不改 ResourceQuota。

2026-06-09：live 环境复核：

- `ircs-aggregation-worker`、`ircs-search-service`、`ircs-storage-service` 均为 `1/1 Ready`。
- `ircs-dev` 当前无 HTTPRoute。
- `ircs-dev-quota` 当前 used 为 pods `28/30`、services `24/26`、limits.cpu `7650m/8`，
  因此 B15 仍必须只复用现有 Deployment/Pod 拓扑，不新增服务资源。
- search 队列 `q.search.sync.raw` 和 `q.search.sync.unified` 当前为空且各有 1 个 consumer。
- storage-service 当前 `APP_STORAGE_R2_ENABLED=false`，R2 promote smoke 应断言
  `next_retry_time` 保持 `null`。
- live RabbitMQ 当前尚未出现 `q.storage.cover_r2_promote`，说明 dev storage-service 可能仍需先按
  下列既有路径只刷新现有镜像和 Deployment：

```powershell
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-storage-service' -ImageName ircs-storage-service -Tag dev -Architecture arm64 -Os linux
kubectl rollout restart deployment/ircs-storage-service -n ircs-dev
kubectl rollout status deployment/ircs-storage-service -n ircs-dev --timeout=240s
```

2026-06-09：B15 live smoke 前置修复进度：

- 已新增 `scripts/smoke/aggregation-pipeline-runtime-02816b15-smoke.ps1`，语法检查通过。
- 已按 ARM64 Jib 路径刷新 `ircs-storage-service:dev`，rollout 后
  `q.storage.cover_r2_promote` 与 `.dlq` 已出现，主队列有 1 个 consumer，均为空。
- 已刷新 `ircs-aggregation-worker:dev`。第一版镜像因 `AggregationPipelineRuntime` 有多个
  构造器而缺少 Spring 注入构造器标记，Pod 报
  `No default constructor found`；已补 `@Autowired` 并通过
  `.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks`。
- 修复镜像 rollout 后 `ircs-aggregation-worker` 恢复 `1/1 Ready`，quota 仍为
  pods `28/30`、services `24/26`，未新增服务资源。
- B15 smoke 第三轮已完成 MARK 1/8、2/8、3/8，证明 scheduler 能把两条 fixture raw 绑定到一个
  unified；随后在 MARK 4/8 发现真实语义缺口：`remarks` 选择了 older remark。
- 根因：`claimPending()` 与 `markBound()` 更新 `raw_videos.updated_at`，破坏
  `updateUnifiedLatestRemarks()` 依赖的 raw 业务更新时间排序。
- 当前追加子范围：新增 `raw_videos.aggregation_status_updated_at` 并让 aggregation status
  heartbeat 使用该列；`updated_at` 继续表示 raw 业务更新时间，供 V1 dynamic field 规则使用。

2026-06-09：B15 已完成，live smoke 通过。

实现结果：

- 新增 DB schema `raw_videos.aggregation_status_updated_at`，并加入
  `platform/ircs-migrator` changelog；该列专用于 aggregation scheduler/状态心跳。
- `JdbcAggregationRepository.claimPending()`、`resetStuckProcessing()`、`markPending()`、
  `markBound()` 改为只更新 `aggregation_status_updated_at`，不再污染 raw 业务
  `updated_at`。
- `JdbcAggregationRepositoryTest` 增加状态时间线 guard，固定 `markBound()` 不修改业务
  `updated_at`。
- B15 smoke fixture 显式设置 `aggregation_status_updated_at`，保证 multi-raw scheduler
  claim 顺序稳定，同时保留 raw1/raw2 的业务 `updated_at` 用于 V1 DynamicField
  `remarks` 最新规则。
- 为低资源 dev 验证，已通过 `kubectl exec postgres-0 psql` 直接应用幂等 SQL，没有新增
  Job/Pod；后续 migrator 正常执行时仍可由 changelog 记录该 schema。
- 已重新推送 ARM64 `ircs-aggregation-worker:dev` 并 rollout 当前单副本 Deployment，未新增
  Deployment、Service、HTTPRoute 或副本。

验证：

```powershell
$null = [scriptblock]::Create((Get-Content -LiteralPath 'scripts/smoke/aggregation-pipeline-runtime-02816b15-smoke.ps1' -Raw))
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-aggregation-worker' -ImageName ircs-aggregation-worker -Tag dev -Architecture arm64 -Os linux
.\scripts\smoke\aggregation-pipeline-runtime-02816b15-smoke.ps1
```

结果：

```text
BUILD SUCCESSFUL
B15 MARK 1/8 ircs-dev aggregation/search/storage consumers are ready and no HTTPRoute exists
B15 MARK 2/8 multi-raw pipeline fixture is seeded and pre-cleaned from DB/ES/queues
B15 MARK 3/8 scheduler binds fixture raws to one unified through aggregation-worker
B15 MARK 4/8 runtime scalar and category outputs match V1 handler rules
B15 MARK 5/8 runtime metadata relations match V1 union rules
B15 MARK 6/8 cover stage selects managed cover and storage command is safely consumed/gated
B15 MARK 7/8 raw/unified ES documents are refreshed and queues/DLQs stay empty
B15 MARK 8/8 cleanup leaves no DB/ES/Rabbit/HTTPRoute/port-forward residue and resource quota is unchanged
status: PASSED
reason: AGGREGATION_PIPELINE_RUNTIME_LIVE_SMOKE_PASSED
```

后置检查：

```text
ircs-aggregation-worker: 1/1 Ready
ircs-search-service: 1/1 Ready
ircs-storage-service: 1/1 Ready
ircs-dev quota: pods=28/30|services=24/26|requests.cpu=880m/4|requests.memory=4480Mi/8Gi|limits.cpu=7650m/8|limits.memory=14208Mi/16Gi
ircs-dev HTTPRoute: none
```

遗留边界：

- B15 已证明 V1 aggregation pipeline runtime 在 dev K8S 的核心路径闭环；真实 R2 upload
  仍保持 gated，不在本任务执行。
- 后续若运行 migrator Job，应确认 `add-raw-video-aggregation-status-time.sql` 被
  Liquibase 正常记录；当前 dev DB 已先行应用 schema 以避免新增 Pod 资源。
