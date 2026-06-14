# 028-08 normalization deep mapping parity 后续子任务

状态：已完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/018-normalization-worker-boundary-analysis.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- `docs/reports/051-normalization-raw-relation-parity-analysis.md`
- `docs/reports/057-normalization-raw-category-data-source-category-02808.md`
- `docs/reports/063-normalization-deep-mapping-live-smoke-02808.md`

## 背景

`028-05-F` 已完成 raw genre/language/area relation parity，但 V1 normalization
还包含 actor/director relation、raw category、standard mapping、FAILED backoff scheduler、
watchdog 和 live queue smoke。当前这些能力仍是接口背后的内部细节缺口。

## 已完成切片

本次第一刀聚焦 `actorNames/directorNames -> actors/directors + video_actors/video_directors`，
已完成并验证。关联报告和迁移记录：

- `docs/reports/056-normalization-deep-mapping-person-relation-02808.md`
- `docs/migrations/056-normalization-deep-mapping-person-relation.md`

## 本轮切片

本轮推进 raw category / `data_source_category_id` mapping parity，关联文档：

- `docs/reports/057-normalization-raw-category-data-source-category-02808.md`
- `docs/migrations/058-normalization-raw-category-data-source-category.md`

本轮只实现 `raw_metadata.rawTypeId/rawTypeName/dataSourceId`
到 `raw_category` 与 `raw_videos.data_source_category_id` 的最小可测副作用。
不实现 standard mapping、FAILED backoff scheduler、startup recovery、watchdog 或 live queue smoke。

## 目标

- 补 actor/director relation 写入和 join relation 替换。
- 补 raw category / data source category 关系。
- 补 standard genre/language/area/category mapping。
- 补 normalization FAILED backoff scheduler、startup recovery 和 watchdog。
- 执行 `q.normalize.video` live smoke。

## 非目标

- 不重构完整数据模型。
- 不强制物理库拆分。
- 不执行大批量全量 normalization。

## 输入

- `q.normalize.video`
- `raw_videos.raw_metadata`
- raw/standard dictionaries
- current raw relation tables

## 输出

- normalization deep mapping 实现。
- 本地测试、live smoke 和迁移记录。

## 影响范围

```text
services/ircs-normalization-worker/**
services/ircs-catalog-service/**
scripts/smoke/**
docs/reports/**
docs/migrations/**
tasks/**
```

## 依赖

- catalog dictionaries 可通过 V3 admin API 写入 fixture。
- search-service raw sync consumer 可用。

## 风险

- mapping 规则可能与 V1 历史数据强耦合。
- 关系替换必须保持幂等，避免重复 join rows。

## 验收标准

- actor/director、raw category 和 standard mapping 均有 MARK。
- FAILED/backoff/watchdog 有独立测试。
- live smoke 后 DB、queue/DLQ、ES fixture residue 为 0。

## 测试边界

- 本地 repository/service 测试。
- 最小 raw video fixture live smoke。

## 当前状态

进行中，第一刀已完成：

- 已完成最小可测切片：actor/director person relation parity。
- 本次验收 MARK：
  - `MARK 1/4 normalization extracts actor/director relation values from raw_metadata`
  - `MARK 2/4 normalization upserts actors/directors by name`
  - `MARK 3/4 normalization replaces video_actors/video_directors join rows`
  - `MARK 4/4 normalization preserves READY status and raw search sync after person relation writes`
- 验证命令：`.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks`
- 验证结果：`BUILD SUCCESSFUL in 9s`

本轮 raw category / `data_source_category_id` 切片已完成：

- 本次验收 MARK：
  - `MARK 1/4 normalization extracts raw category fields from raw_metadata`
  - `MARK 2/4 normalization falls back to raw_videos.data_source_id when metadata dataSourceId is absent`
  - `MARK 3/4 normalization upserts raw_category by data_source_id/source_code without clearing category_id`
  - `MARK 4/4 normalization updates raw_videos.data_source_category_id and preserves READY/search sync`
- 验证命令：`.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks`
- 验证结果：`BUILD SUCCESSFUL in 8s`

剩余项：

- `q.normalize.video` live smoke、queue/DLQ cleanup、ES residue cleanup。

## 子任务 028-08-C：standard mapping fill parity

### 背景

V1 在 raw genre/language/area/category 解析时，会尽量把 raw 值关联到已有标准字典。
V3 目前已经写入 raw relation、person relation 和 raw category，但只保留 raw 值，没有在
normalization 阶段回填 `standard_genre_id`、`standard_language_id`、`standard_area_id` 或
`raw_category.category_id`。这会让后续 aggregation、portal/admin 筛选和 dashboard 仍依赖
人工二次映射。

### 目标

- raw genre 新增或命中时，如果 `source_value` 精确命中 `standard_genre.name`，回填
  `standard_genre_id`。
- raw language 新增或命中时，如果 `source_value` 精确命中 `standard_languages.name/code/english_name/native_name`，
  回填 `standard_language_id`。
- raw area 新增或命中时，如果 `source_value` 精确命中 `standard_areas.name/code`，回填
  `standard_area_id`。
- raw category 新增或命中时，如果 `source_name` 命中 `standard_category.name` 或
  `source_code` 命中 `standard_category.slug`，回填 `category_id`。
- 已有人工标准映射不被覆盖。

### 非目标

- 不引入 LLM、HanLP、缓存推断或 fuzzy matching。
- 不实现 FAILED backoff scheduler、startup recovery、watchdog 或 live queue smoke。
- 不修改 catalog/content/search/aggregation 服务代码。
- 不做 K8S rollout。

### 上游来源

- `docs/reports/051-normalization-raw-relation-parity-analysis.md`
- `docs/reports/056-normalization-deep-mapping-person-relation-02808.md`
- `docs/reports/057-normalization-raw-category-data-source-category-02808.md`
- `docs/reports/058-normalization-standard-mapping-02808.md`

### 输入

- `raw_metadata.genreNames/language/area/rawTypeId/rawTypeName`
- `standard_genre`
- `standard_languages`
- `standard_areas`
- `standard_category`

### 输出

- normalization worker repository standard mapping 回填。
- 本地 repository 单元测试。
- `docs/reports/058-normalization-standard-mapping-02808.md`
- `docs/migrations/059-normalization-standard-mapping.md`

### 影响范围

```text
services/ircs-normalization-worker/**
docs/reports/058-normalization-standard-mapping-02808.md
docs/migrations/059-normalization-standard-mapping.md
tasks/done/028-08-normalization-deep-mapping-parity.md
```

### 风险

- 精确命中策略比 V1 的缓存/策略推断更保守；不会错误扩大匹配，但仍有未映射 raw 值。
- SQL upsert 必须保留已有人工映射，不能把 `standard_*_id` 或 `category_id` 清空或覆盖。

### 验收标准

```text
MARK 1/4 normalization maps raw_genres to existing standard_genre by exact name without overriding manual mapping
MARK 2/4 normalization maps raw_languages to existing standard_languages by exact name/code/native/english name without overriding manual mapping
MARK 3/4 normalization maps raw_areas to existing standard_areas by exact name/code without overriding manual mapping
MARK 4/4 normalization maps raw_category to existing standard_category by source_name/name or source_code/slug without overriding manual mapping
```

### 测试边界

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
```

### 当前状态

已完成代码、测试和文档回写。

### 实现结果

- `raw_genres` upsert 时按 `standard_genre.name` 精确命中回填 `standard_genre_id`。
- `raw_languages` upsert 时按 `standard_languages.name/code/english_name/native_name`
  精确命中回填 `standard_language_id`。
- `raw_areas` upsert 时按 `standard_areas.name/code` 精确命中回填 `standard_area_id`。
- `raw_category` upsert 时按 `standard_category.name/slug` 精确命中回填 `category_id`。
- 所有标准映射回填均使用 `coalesce(existing, excluded)` 保留已有人工映射。

### 验证结果

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 9s
```

MARK：

```text
MARK 1/4 normalization maps raw_genres to existing standard_genre by exact name without overriding manual mapping
MARK 2/4 normalization maps raw_languages to existing standard_languages by exact name/code/native/english name without overriding manual mapping
MARK 3/4 normalization maps raw_areas to existing standard_areas by exact name/code without overriding manual mapping
MARK 4/4 normalization maps raw_category to existing standard_category by source_name/name or source_code/slug without overriding manual mapping
```

### 剩余项

- normalization FAILED backoff scheduler、startup recovery 和 watchdog。
- `q.normalize.video` live smoke、queue/DLQ cleanup、ES residue cleanup。

## 子任务 028-08-D：normalization retry watchdog parity

### 背景

V3 `RawVideoNormalizationService` 已能在失败时写入 `FAILED/PERMANENT_FAILURE`、
`normalization_retry_count` 和 `next_normalization_retry_time`，但缺少 V1 风格 recovery：
当 queue 消息丢失、worker 重启或失败 backoff 到期时，没有后台 watchdog 把 due raw video
重新投递到 `q.normalize.video`。

### 目标

- 扫描 `FAILED` 且 `next_normalization_retry_time <= now()` 的 raw video 并重新投递。
- 扫描长时间停留在 `PENDING` 的 raw video，用作 startup/stuck recovery，并重新投递。
- 跳过 `READY` 和 `PERMANENT_FAILURE`。
- 小批量、可关闭、可配置，避免 dev 环境资源抖动。

### 非目标

- 不执行 live queue smoke。
- 不修改 content/ingestion/search/aggregation 服务。
- 不新增 HTTPRoute、Service、Pod 或 K8S manifest。
- 不改变现有失败重试次数和 backoff 计算公式。

### 上游来源

- `docs/reports/058-normalization-standard-mapping-02808.md`
- `docs/reports/051-normalization-raw-relation-parity-analysis.md`
- V1 `RawVideoNormalizationServiceImpl.rescueStuckTasks` / `retryPendingTasks`

### 输入

- `raw_videos.normalization_status`
- `raw_videos.normalization_retry_count`
- `raw_videos.next_normalization_retry_time`
- `raw_videos.updated_at`
- `q.normalize.video`

### 输出

- normalization retry watchdog 代码。
- 本地 repository/scheduler 单元测试。
- `docs/reports/059-normalization-retry-watchdog-02808.md`
- `docs/migrations/060-normalization-retry-watchdog.md`

### 影响范围

```text
services/ircs-normalization-worker/**
docs/reports/059-normalization-retry-watchdog-02808.md
docs/migrations/060-normalization-retry-watchdog.md
tasks/done/028-08-normalization-deep-mapping-parity.md
```

### 风险

- PENDING recovery 不能过于激进，默认只处理超过 stale threshold 的记录。
- watchdog 只做重投递，不直接改业务字段，避免与正在消费的 worker 竞争。

### 验收标准

```text
MARK 1/4 normalization watchdog scans due FAILED records and republishes q.normalize.video
MARK 2/4 normalization watchdog scans stale PENDING records for startup recovery
MARK 3/4 normalization watchdog skips READY/PERMANENT_FAILURE and future retry records
MARK 4/4 normalization watchdog remains low-resource and configurable
```

### 测试边界

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
```

### 当前状态

已完成代码、测试和文档回写。

### 实现结果

- 新增 `NormalizeVideoPublisher`，向 `q.normalize.video` 发布 raw video id。
- 新增 `NormalizationRetryWatchdog`，小批量扫描 due `FAILED` 和 stale `PENDING`。
- 新增 `RawVideoNormalizationRepository.findDueNormalizationRecoveryIds(...)`。
- 新增 watchdog 配置：
  - `APP_NORMALIZATION_WATCHDOG_ENABLED`
  - `APP_NORMALIZATION_WATCHDOG_INITIAL_DELAY_MS`
  - `APP_NORMALIZATION_WATCHDOG_FIXED_DELAY_MS`
  - `APP_NORMALIZATION_WATCHDOG_BATCH_SIZE`
  - `APP_NORMALIZATION_WATCHDOG_STALE_PENDING_SECONDS`

### 验证结果

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 9s
```

MARK：

```text
MARK 1/4 normalization watchdog scans due FAILED records and republishes q.normalize.video
MARK 2/4 normalization watchdog scans stale PENDING records for startup recovery
MARK 3/4 normalization watchdog skips READY/PERMANENT_FAILURE and future retry records
MARK 4/4 normalization watchdog remains low-resource and configurable
```

### 剩余项

- 已由 `028-08-E` 完成 `q.normalize.video` live smoke、queue/DLQ cleanup 和 ES residue cleanup。

## 子任务 028-08-E：normalization deep mapping live smoke

状态：已完成

### 背景

actor/director、raw category、standard mapping 和 retry watchdog 已在本地测试中完成，
但 `q.normalize.video` 真实消费路径尚未实证这些深层副作用。`028-09` 早期 live smoke
只覆盖基础 normalization 与 raw search sync，不能替代 `028-08` 的深层映射验收。

### 目标

- 通过 RabbitMQ 投递 `q.normalize.video` fixture。
- 验证 raw video 变为 `READY`。
- 验证 actor/director join、raw genre/language/area join 写入。
- 验证 raw relation 和 raw category 回填已有标准字典。
- 验证 search-service 刷新 `ircs_raw_video`，且 ES 文档包含深层映射字段。
- 验证 DB、queue/DLQ、ES residue cleanup。

### 非目标

- 不验证 aggregation -> unified search sync。
- 不执行 provider live smoke。
- 不启用 R2。
- 不新增 HTTPRoute、Service、Pod 或 PVC。
- 不执行全量 normalization。

### 上游来源

- `docs/reports/056-normalization-deep-mapping-person-relation-02808.md`
- `docs/reports/057-normalization-raw-category-data-source-category-02808.md`
- `docs/reports/058-normalization-standard-mapping-02808.md`
- `docs/reports/059-normalization-retry-watchdog-02808.md`
- `docs/reports/063-normalization-deep-mapping-live-smoke-02808.md`

### 输入

- `q.normalize.video`
- `q.search.sync.raw`
- `raw_videos.raw_metadata`
- standard dictionaries
- relation tables
- Elasticsearch `ircs_raw_video`

### 输出

- `scripts/smoke/normalization-deep-mapping-02808-smoke.ps1`
- `docs/reports/063-normalization-deep-mapping-live-smoke-02808.md`
- `docs/migrations/063-normalization-deep-mapping-live-smoke.md`
- 本任务与父任务状态回写。

### 影响范围

```text
services/ircs-normalization-worker/**
deploy/k8s/dev/normalization-worker-dev.yaml
scripts/smoke/normalization-deep-mapping-02808-smoke.ps1
docs/reports/063-normalization-deep-mapping-live-smoke-02808.md
docs/migrations/063-normalization-deep-mapping-live-smoke.md
tasks/done/028-08-normalization-deep-mapping-parity.md
```

### 风险

- dev 镜像可能未包含最新 deep mapping 代码，需要先 rollout ARM64 normalization-worker。
- smoke 必须避免清理非本轮业务消息。
- aggregation scheduler 可能抢占 `PENDING` raw video，fixture 需隔离 aggregation。

### 验收标准

```text
MARK 1/6 normalization/search consumers are ready and queues are clean
MARK 2/6 q.normalize.video fixture is consumed and raw video becomes READY
MARK 3/6 actor/director and raw relation join rows are written
MARK 4/6 raw relations and raw category map to existing standard dictionaries
MARK 5/6 raw search sync refreshes ES document with deep mapping fields
MARK 6/6 smoke cleanup leaves no DB/ES residue and queues remain empty
```

### 测试边界

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
.\scripts\smoke\normalization-deep-mapping-02808-smoke.ps1
```

### 实现结果

- 新增 `scripts/smoke/normalization-deep-mapping-02808-smoke.ps1`。
- `ircs-normalization-worker` 已推送 ARM64 `dev` 镜像并 rollout 到 `ircs-dev`。
- live smoke 验证 actor/director、raw relation、standard mapping、raw category 与 ES raw
  document 刷新。

### 验证结果

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 9s

ircs-normalization-worker imageID:
  harbor.mnnu.eu.org/ircs/ircs-normalization-worker@sha256:d0e35af46a0bd8aa0ca60e4db2c973a90824b7e84783cd2f5c6db501d0f043fe
```

MARK：

```text
MARK 1/6 normalization/search consumers are ready and queues are clean
MARK 2/6 q.normalize.video fixture is consumed and raw video becomes READY
MARK 3/6 actor/director and raw relation join rows are written
MARK 4/6 raw relations and raw category map to existing standard dictionaries
MARK 5/6 raw search sync refreshes ES document with deep mapping fields
MARK 6/6 smoke cleanup leaves no DB/ES residue and queues remain empty
```

Live smoke 证据：

```text
trace: 02808-normalization-deep-20260608103455
rawVideoId: 2158c0c9-4b78-498d-9cae-4334261f640e
status: PASSED
DB fixture residue: 0
HTTPRoute: No resources found in ircs-dev namespace.
ResourceQuota: pods=28/30, services=24/26
```
