# 028-16-F V1 golden behavior inventory task spec

状态：本轮 P1/P2 backlog 入账完成；inventory 长线审计继续进行中

父任务：

- `tasks/in-progress/028-16-v1-v3-deep-parity-gap-audit-parent.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/088-v1-v3-deep-parity-gap-audit.md`
- `docs/migrations/088-v1-v3-deep-parity-gap-audit.md`
- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`

## 背景

用户明确指出：不只是 HanLP/Tika、清洗和聚合，V1 各种功能边角细节都可能被遗漏。V3
要么更加完善，要么一比一迁移，不能只用接口覆盖或 MVP smoke 说明完成。

因此需要建立 V1 golden behavior inventory：逐模块读取 V1 controller、service、
scheduler、consumer、initializer、support、provider、repository side effect，给每个行为
一个迁移状态和证据归属。

## 目标

- 对 V1 Java 源码按模块生成行为 inventory。
- 为每个行为标注 V3 状态：`1:1 migrated`、`improved replacement`、`partial MVP`、
  `explicit non-goal`、`missing`、`needs evidence`。
- 所有 `missing/partial/needs evidence` 必须关联到 task。
- 所有 `improved replacement` 必须有测试或 smoke 证据。
- 形成后续实现优先级队列。

## 非目标

- 本任务不直接修改业务代码。
- 不把未审计清楚的行为直接标记完成。
- 不以 HTTP route missing=0 代替行为完成。

## 输入

```text
D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/**
D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/build.gradle
D:/WorkSpace/Project/ircs/ircs-project-v3/services/**
D:/WorkSpace/Project/ircs/ircs-project-v3/shared/**
D:/WorkSpace/Project/ircs/ircs-project-v3/docs/**
D:/WorkSpace/Project/ircs/ircs-project-v3/tasks/**
```

## 输出

```text
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md
follow-up child tasks/backlog items
```

## 影响范围

```text
docs/reports/**
docs/migrations/**
tasks/**
```

后续子任务才会修改代码。

## 审计模块

```text
infrastructure/dataInit
infrastructure/http
infrastructure/support
infrastructure/cache
infrastructure/adaptive
infrastructure/llm
modules/ingestion
modules/datasource
modules/storage
modules/scraper
modules/magnet
modules/system
modules/portal
modules/task
modules/search
modules/content
modules/interaction
modules/metadata
```

## 风险

- V1 源码类数量多，必须分批审计，不能把未读模块写成完成。
- 一些 V1 行为可能已由 V3 更好方式替代，但必须提供证据。
- 子任务过大时需要拆小，避免实现阶段互相覆盖。

## 验收标准

```text
MARK 1/8 V1 source module inventory generated
MARK 2/8 V1 dependency-to-behavior mapping generated
MARK 3/8 normalization/datasource/storage/scraper behavior statuses assigned
MARK 4/8 aggregation/content/search behavior statuses assigned
MARK 5/8 identity/portal/interaction/task/system/magnet behavior statuses assigned
MARK 6/8 all missing/partial/needs-evidence items link to tasks or backlog
MARK 7/8 all improved-replacement items cite tests or smoke evidence
MARK 8/8 docs/tasks indexes reference the inventory and remaining parity queue
```

## 测试边界

本任务是文档审计，不运行业务测试。后续实现任务按服务执行 Gradle tests 和 smoke。

## 当前状态

已创建 `docs/reports/089-v1-golden-behavior-inventory.md` 和
`docs/migrations/089-v1-golden-behavior-inventory.md` 的初版总账，并合并清洗/标准化、
聚合/匹配、依赖/初始化器三个只读审计结果。

当前 MARK：

```text
MARK 1/8 V1 source module inventory generated
MARK 2/8 V1 dependency-to-behavior mapping generated as first draft
MARK 3/8 normalization/datasource/storage/scraper behavior statuses assigned: first draft
MARK 4/8 aggregation/content/search behavior statuses assigned: first draft
MARK 5/8 identity/portal/interaction/task/system/magnet behavior statuses assigned: partial
MARK 6/8 all missing/partial/needs-evidence items link to tasks or backlog: partial
MARK 7/8 all improved-replacement items cite tests or smoke evidence: pending
MARK 8/8 docs/tasks indexes reference the inventory and remaining parity queue: pending
```

已拆出第一批 P0 实现子任务：

```text
tasks/in-progress/028-16-a1-canonical-rawmetadata-contract.md
tasks/in-progress/028-16-a2-text-standardization-title-policy.md
tasks/in-progress/028-16-a3-metadata-result-deep-merge.md
tasks/in-progress/028-16-b1-aggregation-matching-golden-fixtures.md
```

下一步继续将 `028-16-C` 拆成初始化编排、SystemConfig、shared outbound HTTP、cache/UUID 等
更小的 P0/P1 task spec，并进入首批实现。

2026-06-10：按 `028-16-F golden inventory P1/P2 转 task` 要求，已将 089/093 中仍未关闭的
P1/P2 模糊剩余项转为 8 个 backlog task spec。当前策略固定为：先推进实现完整度，不逐一执行
测试；后续按统一测试批次集中验收。

Backlog 子任务：

```text
tasks/backlog/028-16-f1-normalization-golden-p1p2-backlog.md
tasks/backlog/028-16-f2-aggregation-golden-p1p2-backlog.md
tasks/backlog/028-16-f3-tika-scraper-golden-p1p2-backlog.md
tasks/backlog/028-16-f4-llm-cleaning-golden-p1p2-backlog.md
tasks/backlog/028-16-f5-c1-readiness-golden-p1p2-backlog.md
tasks/backlog/028-16-f6-c3-outbound-adoption-golden-p1p2-backlog.md
tasks/backlog/028-16-f7-worker-scheduler-audit-golden-p1p2-backlog.md
tasks/backlog/028-16-f8-production-like-smoke-golden-p1p2-backlog.md
```

统一测试批次建议：

```text
UTB-02816-P1P2-01 normalization + aggregation + Tika/scraper fixture/live smoke
UTB-02816-P1P2-02 LLM fake/no-cost + C1 readiness + C3 outbound adoption
UTB-02816-P1P2-03 worker/scheduler audit + production-like cleanup/resource checks
```

当前 MARK 更新：

```text
MARK 6/8 all missing/partial/needs-evidence items link to tasks or backlog: done for known P1/P2
MARK 8/8 docs/tasks indexes reference the inventory and remaining parity queue: done for current P1/P2 backlog batch
```

收敛说明：

- 028-16-F 本轮交付物是 8 个 P1/P2 backlog specs 与统一测试批次，不代表 backlog 功能实现全部完成。
- 后续关闭这些 backlog 时必须引用对应 `UTB-02816-P1P2-*` 的实际执行结果、live smoke 证据或明确的
  deferred reason。

2026-06-10：`028-16-F1/A11 normalization golden P1/P2 implementation closure` 已推进一批
normalization 本地实现，不再只是 backlog 入账。当前状态为 partial：

- 已补 title release group / `S02` / `Part II` / `Vol.2`、location hint、genre synonyms、
  raw category fallback、retry/watchdog PROCESSING reset、queue depth guard、adaptive batch
  min/max 和 fixed failure reason logging。
- 已跑 focused normalization-worker tests：

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 30s
69 tests completed, 0 failed
```

- 未跑 live smoke、K8S rollout、全量测试、scraper/metadata focused tests；仍按
  `UTB-02816-P1P2-01` 集中验收。

2026-06-08：`028-16-A2` 和 `028-16-A3` 已完成第一刀并通过组合测试：

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test :services:ircs-metadata-worker:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-08：`028-16-A1` 也已完成第一刀，A1/A2/A3 前半链路组合测试通过：

```text
.\gradlew.bat --no-daemon :services:ircs-scraper-service:test :services:ircs-normalization-worker:test :services:ircs-metadata-worker:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-08：`028-16-B1` 已完成 aggregation matching 第一刀，覆盖 title/year 召回、
external ID 冲突阻断、season/version 阻断和相似标题复用 unified；duplicate unified merge
后续已由 B2/B3/B4 继续推进，JGraphT raw/raw cluster、raw-source AggregationPipeline 和
live smoke 仍保留为后续 gap。

2026-06-08：`028-16-C2` 已完成 SystemConfig 默认值/校验第一刀，五服务组合测试通过：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test :services:ircs-scraper-service:test :services:ircs-normalization-worker:test :services:ircs-metadata-worker:test :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-C2` 已按用户确认补充“V1 config 初始化最低优先级、ENV/K8S Secret/
启动参数等注入最高优先级”的防回退测试；`028-16-D2` 已完成 scraper list charset/streaming
第一刀；`028-16-B2` 已创建 duplicate unified merge/victim delete task spec。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test :services:ircs-scraper-service:test :services:ircs-storage-service:test :shared:ircs-common:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B2` 已完成第一刀，覆盖 duplicate unified root/victim merge plan、
victim raw binding 迁移、favorite/history 冲突清理、victim scoped relation 删除、victim
unified delete 和 search DELETE side effect。victim relation union 后续已由 B3 推进；完整
JGraphT raw/raw cluster、更多 graph scoring 细节和 K8S live smoke 仍未完成。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B4` 已完成第一刀，覆盖 duplicate victim 删除前的 scalar merge：
root 只在自身字段为空时补 victim 的 external IDs、description、score、published_at、
alias_title、total_episodes、duration、remarks、season、subtitle，并保留更新的 `last_trend_at`。
raw-source `AggregationPipeline`、category 投票、dynamic fields、cover 优选/R2 promote、完整
JGraphT raw/raw cluster 和 K8S live smoke 仍未完成。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B3` 已完成第一刀，覆盖 duplicate victim 删除前的 unified relation
union：`actors/directors/genres/standard_languages/standard_areas` 先合并到 root，再删除 victim。
raw-source `AggregationPipeline`、category 投票、dynamic fields、cover 优选/R2 promote、完整
JGraphT raw/raw cluster 和 K8S live smoke 仍未完成。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B5` 已完成第一刀，覆盖 raw-source `AggregationPipeline` 的
category 投票和 relation rebuild：raw 绑定 unified 后，按同一 unified 下全部 raw sources
投票写入 `category_id`，并重建 actors/directors/genres/languages/areas；`locked_fields`
命中时不清空、不覆盖。`BasicAttributeHandler`、`ExternalIdHandler`、`DynamicFieldHandler`、
`CoverImagePipelineHandler`、handler order/failure isolation、完整 JGraphT raw/raw cluster 和
K8S live smoke 仍未完成。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B6` 已完成第一刀，覆盖 raw-source `AggregationPipeline` 的
Basic/ExternalId/Dynamic 标量规则：title/year 投票、score 最大、published_at 首个非空、
external IDs 填空且不受锁保护、alias/description/subtitle 最长、remarks 最新、
total_episodes/duration/season 首个非空；`locked_fields` 命中时保护 Basic/Dynamic 字段。
cover 选择规则后续已由 B7 推进，cover R2 promote side effect 后续已由 B8 推进；当前仍未完成的是
handler order/failure isolation、完整 JGraphT raw/raw cluster 和 K8S live smoke。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B7` 已完成第一刀，覆盖 raw-source `AggregationPipeline` 的
`CoverImagePipelineHandler` 核心 cover 选择规则：空 cover 采用第一个 raw cover，
managed `LOCAL`/`R2` 优先于 `EXTERNAL`，同类 managedness 只有 `file_size > current * 1.1`
才替换，`file_size` null 按 0 处理；`coverImageUrl` locked field 不阻止该 handler。
本切片不执行真实 R2 upload，V1 `promoteToR2()` side effect 后续已由 B8 拆到
storage command / due upload scheduling；handler order/failure isolation、完整 JGraphT
raw/raw cluster 和 K8S live smoke 仍未完成。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B8` 已完成第一刀，覆盖 V1
`coverImageService.promoteToR2(sourceImage.getId())` 的跨服务副作用。B8 新增
`PROMOTE_COVER_R2` storage command；aggregation-worker 只在 B7 cover pipeline 选中
`LOCAL` + `LOCAL_STORED` cover 时发布 UUID command；storage-service 消费后在 R2 active、
cover 仍符合 `LOCAL` + `LOCAL_STORED` 且 `next_retry_time is null` 时设置
`next_retry_time=now()` 与 `last_error=null`。本切片不执行真实 R2 upload，真实上传仍由
`CoverImageR2SyncService` / `StorageR2Watchdog` 承接；handler order/failure isolation、
完整 JGraphT raw/raw cluster 和 K8S live smoke 仍未完成。

验证：

```text
.\gradlew.bat --no-daemon :shared:ircs-messaging:test :services:ircs-aggregation-worker:test :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B9` 已完成第一刀，覆盖 V1 `GraphClusteringEngine` 的 same-batch
raw/raw cluster 核心行为。aggregation-worker service scope 接入 JGraphT，新增
`RawVideoGraphClusterer`；同一批 claimed raw 按 edge `0.85`、consistency `0.90`、有效年份差
`<= 1` 和未知年份桶隔离规则成簇。multi-raw cluster 会创建或复用一个 unified，将 cluster
内 raw 全部 bind 到该 unified，并在所有 bind 后运行一次 B5-B8 已验证的 pipeline rebuild、
search sync 和 cover R2 promote side effect。context unified 混合图、完整 graph scoring、
handler runtime 和 K8S live smoke 当时仍未完成；后续 B10-B14 已补 context graph、
category bucket、metadata scoring、ES-backed context recall 和 pipeline runtime 第一刀。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B10` 已完成第一刀，覆盖 V1
`GraphAggregationServiceImpl.loadContextAccelerated()` + `GraphClusteringEngine` 的
raw/unified context mixed graph 核心行为。context recall 按 V1 只用 douban/tmdb 做 external
recall，raw 缺少有效 douban/tmdb 时才退到 title/year recall；mixed graph 中 raw/raw 与
raw/unified 可建边，unified/unified 不建边，纯 unified component 跳过。cluster 可复用一个
context unified，或将多个 context unified 作为 root/victim 走 B2-B4 merge path。真实
ES-backed context recall、category bucket、actor/director Jaccard、episode/area penalty、
handler runtime 和 K8S live smoke 当时仍未完成；后续 B11 已补 category bucket 第一刀，
B12 已补 metadata scoring/penalty 第一刀，B13 已补 ES-backed context recall 第一刀，B14
已补 pipeline runtime 第一刀。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B11` 已完成第一刀，覆盖 V1
`GraphClusteringEngine.buildGraphOptimized()` 的 category bucket。raw candidate 从
`raw_category -> standard_category` 暴露标准分类名，unified candidate 从
`unified_videos.category_id -> standard_category` 暴露标准分类名；graph 建边前按
`categoryName.trim()` 分桶，空/blank 分类归入 `UNKNOWN_CATEGORY`。raw/raw 与 raw/unified
edge 均不会跨具体分类，`UNKNOWN_CATEGORY` 只和 `UNKNOWN_CATEGORY` 比较。真实
ES-backed context recall、actor/director Jaccard、episode/area penalty、handler runtime
和 K8S live smoke 当时仍未完成；后续 B12 已补 metadata scoring/penalty 第一刀，B13
已补 ES-backed context recall 第一刀。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B12` 已完成第一刀，覆盖 V1 `DefaultWeightedMatchingStrategy`
metadata scoring 与 penalty。candidate 主查询后按 V1 `populateMetadata()` 语义补
actor/director/standard area names；无 metadata 时保持 `titleScore * 0.95`，双方都有
metadata 时使用 `titleScore * 0.70 + actor/director Jaccard * 0.30`，episode 不兼容扣
`0.15`，双方 area 都存在且不 contains 匹配扣 `0.10`。ES-backed context recall 当时仍未
完成，后续已由 B13 补第一刀。

2026-06-09：`028-16-B13` 已完成第一刀，覆盖 V1
`SearchQueryService.findCandidateUnifiedVideoIds()` 的 ES-backed context recall。search-service
新增内部只读 UUID recall API，query 复刻 V1 `multi_match best_fields`、`AND`、
`minimum_should_match 90%`、字段权重、year `+-2` range 和 limit 20；aggregation-worker
对无有效 douban/tmdb 的 raw 通过 search-service ES recall 拿 UUID，并回 PostgreSQL 装载
context candidate。ES 空结果或异常默认不退 SQL title fallback，只有显式关闭 ES recall 时
才使用旧 SQL fallback 作为开发兜底。handler order/failure isolation 后续已由 B14 补第一刀；
当前仍未完成的是 K8S live smoke。

2026-06-09：`028-16-B14` 已完成第一刀，覆盖 V1 `AggregationPipeline` 的 handler 顺序和
失败隔离 runtime。aggregation-worker 新增 `AggregationPipelineRuntime`，按
`BasicAttribute(10) -> ExternalId(20) -> Metadata(30) -> DynamicField(40) ->
Category(50) -> CoverImage(60)` 执行；`JdbcAggregationRepository` 已拆出 handler 粒度
rebuild 方法并保留 grouped wrapper；single raw 与 multi-raw cluster bind 均通过 runtime
执行 pipeline，cover stage 成功时才透传 R2 promote cover IDs。随后已补
`TransactionTemplate` + `PROPAGATION_NESTED` stage savepoint，避免 PostgreSQL failed
transaction block 破坏 V1 handler 失败隔离语义。当前仍未完成的是 K8S live smoke。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-search-service:test :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B15` 已完成 dev K8S aggregation pipeline runtime live smoke。B15
补充 `raw_videos.aggregation_status_updated_at`，让 aggregation scheduler/状态心跳不再
改写 raw 业务 `updated_at`，从而保持 V1 DynamicField `remarks` 最新更新时间语义。live smoke
通过 MARK 8/8，覆盖 multi-raw scheduler bind、pipeline scalar/category、metadata relation
union、cover promote gated、raw/unified ES refresh、队列/DLQ cleanup、HTTPRoute/ResourceQuota
后置检查。至此前文 B9-B14 记录的“当前仍未完成 K8S live smoke”已由 B15 关闭；真实 R2
upload、dirty lifecycle/reconciliation 深化仍是后续独立边界。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\scripts\smoke\aggregation-pipeline-runtime-02816b15-smoke.ps1
PASSED, B15 MARK 8/8
```

2026-06-10：`028-16-F2/B15` 已完成 aggregation golden P1/P2 local implementation closure。
本轮补齐 search sync 结构化 INDEX/DELETE、malformed structured payload no-requeue reject、
pipeline failure 后 raw `PENDING` + unified `DIRTY`、`recalculateUnified()` 全成功才
`SYNCED` + unified INDEX，以及 duplicate victim 删除后的 DELETE side effect 保底。focused
tests 通过：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --tests "*Aggregation*"
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-search-service:test --tests "*SearchSync*"
BUILD SUCCESSFUL
```

未执行 live smoke、K8S rollout、真实 R2 upload 或全量测试。F2 保持 partial/local closure：
真实 R2 upload、多 context victim merge production-like smoke、队列/DLQ/ES residue 和持久
search outbox/stats processor 仍纳入 `UTB-02816-P1P2-01` 或后续独立任务。

2026-06-09：`028-16-C2` 已再次补强注入优先级防回退：`SystemConfigDefaults.injectionKeys()`
同时暴露原始 property key、V3 runtime alias 和 `APP_*` 大写下划线 env name；`ConfigService`
摘要和 MAIL SSL/STARTTLS 校验共用 env-first 解析。新增测试覆盖 `APP_MAIL_ENABLED`、
`APP_IDENTITY_JWT_SECRET`、`APP_MAIL_PROPERTIES_SSL` 高于 DB 管理值。随后继续加固
identity-service / interaction-service 的直连读取，固定运行时覆盖源 -> DB -> fallback；
`application.yaml` 静态默认值不会覆盖 DB，`SECURITY_ADMIN_PASSWORD` 和
`MEMBER_MESSAGE_DAILY_LIMIT` 等 K8S env name 已有测试覆盖。

2026-06-09：`028-16-C2` 再次补强 config-service 自身的 runtime injection guard。
`ConfigService` 已不再直接用 `Environment.getProperty()` 把 classpath `application.yaml`
静态默认值识别成最高优先级注入源；summary、事件来源和 MAIL SSL/STARTTLS 校验统一走
`RuntimeInjectedConfig -> DB -> default seed`。`ConfigServiceTest` 新增 YAML-default
non-override 与 non-conflict 测试，组合回归通过。

2026-06-09：`028-16-C2` 又补 K8S 外部注入 source name 识别：config-service、
identity-service 和 interaction-service 均把 Spring Boot config tree、Spring Cloud Kubernetes
ConfigMap/Secret property source 识别为 `INJECTED`，而 classpath `application.yaml`
仍低于 DB。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test :services:ircs-identity-service:test :services:ircs-interaction-service:test --rerun-tasks
BUILD SUCCESSFUL
```

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-config-service:test :services:ircs-identity-service:test :services:ircs-interaction-service:test --rerun-tasks
BUILD SUCCESSFUL
```
