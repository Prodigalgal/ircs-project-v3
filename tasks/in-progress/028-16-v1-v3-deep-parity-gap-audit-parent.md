# 028-16 V1/V3 功能与库细节深层 parity 审计父任务

状态：进行中

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/088-v1-v3-deep-parity-gap-audit.md`
- `docs/migrations/088-v1-v3-deep-parity-gap-audit.md`
- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`

## 背景

V3 已完成 HTTP route coverage、BFF、若干 queue/live smoke 和 audit 链路，但用户指出：
清洗、聚合、HanLP/Tika 以外的功能与库细节仍未逐项复刻。V1 的功能已经经过验证，V3
不能用 MVP 近似实现代替 1:1 行为迁移。

## 目标

- 建立 V1/V3 深层功能差异矩阵。
- 将清洗、聚合、依赖/初始化、Tika/文件解析、LLM cleaning 拆为可执行子任务。
- 建立 V1 golden behavior inventory，避免继续遗漏功能边角细节。
- 每个子任务明确 V1 类/方法、V3 对应文件、缺口、风险和 `MARK` 验收。
- 后续实现时优先补 P0 清洗策略链和 P0 聚合图匹配链。

## 非目标

- 不在本父任务创建时立即修改业务代码。
- 不把所有 V1 依赖无差别加到所有 V3 服务。
- 不执行大批量清洗、聚合或真实 LLM 调用。
- 不新增 HTTPRoute 或高资源中间件。

## 输入

```text
D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/build.gradle
D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java/**
D:/WorkSpace/Project/ircs/ircs-project-v3/build.gradle
D:/WorkSpace/Project/ircs/ircs-project-v3/services/**
D:/WorkSpace/Project/ircs/ircs-project-v3/shared/**
```

## 输出

- 深层 parity 审计报告。
- 子任务 task spec。
- 后续实现和 smoke 的 `MARK n/m`。
- docs/tasks 索引和 028 父任务状态回写。

## 影响范围

```text
docs/reports/088-v1-v3-deep-parity-gap-audit.md
docs/migrations/088-v1-v3-deep-parity-gap-audit.md
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/in-progress/028-16-*.md
docs/requirements/028-v1-v3-behavior-parity-closeout.md
docs/index.md
tasks/index.md
```

后续实现可能影响：

```text
services/ircs-normalization-worker/**
services/ircs-aggregation-worker/**
services/ircs-storage-service/**
services/ircs-scraper-service/**
services/ircs-config-service/**
services/ircs-credential-service/**
shared/**
```

## 依赖

- V1 源码可读。
- V3 当前任务和报告可追踪。
- 子任务实现前需要按服务边界决定依赖接入位置。

## 风险

- 清洗和聚合属于策略链，直接重写可能影响已有 smoke；必须分阶段接回并保持 fixture 可清理。
- HanLP、Tika、JGraphT 等依赖会增加镜像体积，必须按服务最小化接入。
- LLM cleaning 涉及真实 credential 和成本，必须 gated/fake-first。

## 验收标准

```text
MARK 1/5 V1/V3 dependency and initializer gap matrix created
MARK 2/5 V1/V3 normalization cleaning strategy gap matrix created
MARK 3/5 V1/V3 aggregation graph/pipeline gap matrix created
MARK 4/5 Tika/HanLP/OpenCC/LLM/CQEngine/JGraphT gaps have service-scoped tasks
MARK 5/5 docs/tasks/index and 028 parent requirements reflect deep parity remaining work
```

## 测试边界

本父任务当前是文档/任务审计，无业务测试。后续子任务必须分别提供：

```text
本地 Gradle test
必要的 parser/strategy fixture tests
必要的 ClusterIP/live smoke
DB/queue/ES/object residue cleanup
HTTPRoute/ResourceQuota 后置检查
```

## 当前子任务

进行中：

```text
tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md
tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md
tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md
tasks/in-progress/028-16-d-tika-file-parsing-security-parity.md
tasks/in-progress/028-16-e-llm-cleaning-gated-parity.md
tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md
```

## 当前状态

2026-06-08：已创建第一轮审计报告和任务切片；并行只读审计 agent 已补充清洗/标准化、
聚合/匹配、依赖/初始化器细节，主线程已合并到 088/089 报告和本父任务。

2026-06-08：用户要求所有功能边角细节要么更完善、要么一比一迁移；已新增
`028-16-F V1 golden behavior inventory`，用于逐模块建立行为状态总账。

2026-06-08：已合并清洗/标准化、聚合/匹配、依赖/初始化器三个只读审计结果，新增
`docs/reports/089-v1-golden-behavior-inventory.md` 与
`docs/migrations/089-v1-golden-behavior-inventory.md`。当前第一轮 P0 包含初始化编排、
SystemConfig 默认值与缓存失效、HanLP/OpenCC 文本标准化、标题 pipeline、canonical
rawMetadata、metadata result 深层合并、聚合图匹配、聚合 pipeline、duplicate unified merge
和统一出站 HTTP。

2026-06-08：首批 P0 第一刀已完成 `028-16-A1/A2/A3/B1`，并通过组合测试：

```text
.\gradlew.bat --no-daemon :services:ircs-scraper-service:test :services:ircs-normalization-worker:test :services:ircs-metadata-worker:test :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-08：`028-16-C2` config-service 第一刀完成后，扩展组合测试通过：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test :services:ircs-scraper-service:test :services:ircs-normalization-worker:test :services:ircs-metadata-worker:test :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-08：`028-16-C2` config change event P0 消费者第一刀完成后，identity/interaction
本地 cache evict、独立 domain event queue binding、identity side-write publisher 和
config-service publisher 回归测试通过：

```text
.\gradlew.bat --no-daemon :services:ircs-identity-service:test :services:ircs-interaction-service:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :shared:ircs-messaging:test :services:ircs-config-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-08：`028-16-C3` shared outbound HTTP 第一刀完成后，`PUBLIC_FETCH`
SSRF/redirect/UA/retry/gzip fake contract、catalog sample fetch adapter 和 magnet YTS_BZ
adapter 测试通过：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-catalog-service:test :services:ircs-magnet-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-08：`028-16-C3-S2` scraper shared outbound HTTP 第二刀完成后，shared proxy policy、
V1 风格 random UA、scraper custom header/proxy/UA 透传和 private datasource URL transport
前阻断测试通过；catalog/magnet 已接入 caller 回归同步通过：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-scraper-service:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-scraper-service:test :services:ircs-catalog-service:test :services:ircs-magnet-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-C3-S3` metadata TMDB shared outbound HTTP 第三刀完成后，TMDB public
API adapter 已接 shared `PUBLIC_FETCH`，状态码分类、私网阻断、retry、gzip 和 redirect
never fake 测试通过；catalog/magnet/scraper 已接入 caller 回归同步通过：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-metadata-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-metadata-worker:test :services:ircs-scraper-service:test :services:ircs-catalog-service:test :services:ircs-magnet-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-D` Tika/charset 只读盘点完成，已确认 D1 storage Tika MIME 与 D2
scraper charset/streaming 两个后续实现边界；本轮未修改 D 相关代码。

2026-06-09：`028-16-D1` storage Tika MIME 第一刀完成后，storage-service 仅接入
`tika-core:3.2.2`，`ImageSecurityValidator` 使用 `Tika.detect(byte[])`；有效图片、spoof/
non-image、空文件、超 10MB、非法文件名和 MIME spoof fixture 通过。storage/scraper/
metadata/shared common 组合回归同步通过：

```text
.\gradlew.bat --no-daemon :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-storage-service:test :services:ircs-scraper-service:test :services:ircs-metadata-worker:test :shared:ircs-common:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-C2` 注入优先级防回退与 `028-16-D2` scraper charset/streaming 第一刀完成后，
config/scraper/storage/shared common 组合回归通过。D2 中 scraper-service 只接入 `tika-core`
与 `tika-parser-text-module`，`ListScraperClient.fetchList()` 已切换到 raw bytes charset
detection + Jackson streaming parser；C2 已补 create/update 后 env/K8S/启动参数注入仍压过 DB

2026-06-10：`028-16-E` LLM cleaning gated parity implementation-first closure 完成，状态
`MARK 5/6` partial。normalization-worker 已补默认关闭 gate、默认 `dry-run` mode、默认关闭
runner、fake deterministic client、credential-service/runtime credential path、candidate touch、
四类 raw dictionary match/noise side effect 和固定错误语义；真实 LLM 调用、K8S dry-run smoke
与 staging-only live 单条 fixture 留到统一测试窗口。
管理值的回归测试。

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-scraper-service:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-config-service:test :services:ircs-scraper-service:test :services:ircs-storage-service:test :shared:ircs-common:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-D3` Tika MIME / file parsing live 验证 first closure 完成。V1 对照已确认
实际 Tika 调用点为 storage `Tika.detect(byte[])` 与 scraper `UniversalEncodingDetector`；
V1 build 虽声明 `tika-parsers-standard-package`，但源码未发现 `AutoDetectParser`、
`BodyContentHandler` 或 `PDFParser` 等通用 text extraction 调用。本轮新增 focused tests 和
`scripts/smoke/tika-file-parsing-02816d-smoke.ps1`，生成 `valid.png`、HTML spoof、renamed
PDF、GB18030 list JSON、empty file 小 fixture，运行定向 storage/scraper tests，并清理
`build/tmp/02816d-tika-smoke-*`。本地 smoke `MARK 6/6`，组合回归通过：

```text
powershell -ExecutionPolicy Bypass -File scripts\smoke\tika-file-parsing-02816d-smoke.ps1
MARK 6/6
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-storage-service:test :services:ircs-scraper-service:test :shared:ircs-common:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：已关闭 aggregation 只读 subagent，并将结论收敛为
`tasks/in-progress/028-16-b2-duplicate-unified-merge-victim-delete.md`。B2 下一刀只做
duplicate unified root/victim merge、victim delete 和 search DELETE side effect；relation
union 与完整 JGraphT raw/raw cluster 当时继续拆后续切片；后续 B3/B4 已分别补上 victim
relation union 和 victim scalar merge。

2026-06-09：`028-16-B2` 已完成第一刀。aggregation-worker 已支持 root/victim merge plan、
victim raw binding 迁移、favorite/history 冲突清理、victim scoped relation 删除和结构化
`UNIFIED_VIDEO DELETE`；victim relation union 和 victim scalar merge 后续已由 B3/B4 补上，
完整 raw-source AggregationPipeline、JGraphT raw/raw cluster 与 K8S live smoke 继续保留。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B4` 已完成第一刀。duplicate victim 删除前，root 会在自身字段为空时
从 victim 补 external IDs、description、score、published_at、alias_title、total_episodes、
duration、remarks、season、subtitle，并保留更新的 `last_trend_at`。raw-source
`AggregationPipeline`、category 投票、dynamic fields、cover 优选/R2 promote、完整
JGraphT raw/raw cluster 与 K8S live smoke 继续保留。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B3` 已完成第一刀。duplicate victim 删除前会先把 victim unified 侧
`actors/directors/genres/standard_languages/standard_areas` union 到 root，并使用
`on conflict do nothing` 去重。raw-source `AggregationPipeline`、category 投票、dynamic
fields、cover 优选/R2 promote、完整 JGraphT raw/raw cluster 与 K8S live smoke 继续保留。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B5` 已完成第一刀。raw 绑定 unified 后会重建 V1
`CategoryHandler` 与 `MetadataHandler` 的核心输出：category 投票写入
`unified_videos.category_id`，并从 raw sources 重建 actors/directors/genres/languages/areas；
`locked_fields` 命中时保持现有 unified 值，不清空、不覆盖。`BasicAttributeHandler`、
`ExternalIdHandler`、`DynamicFieldHandler`、`CoverImagePipelineHandler`、handler
order/failure isolation、完整 JGraphT raw/raw cluster 与 K8S live smoke 继续保留。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B6` 已完成第一刀。raw 绑定 unified 后会重建 V1
`BasicAttributeHandler`、`ExternalIdHandler` 与 `DynamicFieldHandler` 的标量输出：
title/year 投票、score 最大、published_at 首个非空、external IDs 填空且不受锁保护、
alias/description/subtitle 最长、remarks 最新、total_episodes/duration/season 首个非空；
`locked_fields` 命中时保护 Basic/Dynamic 字段。cover 选择规则后续已由 B7 推进，
cover R2 promote side effect 后续已由 B8 推进；当前仍保留的是 handler order/failure
isolation、完整 JGraphT raw/raw cluster 与 K8S live smoke。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B7` 已完成第一刀。raw 绑定 unified 后，aggregation-worker 会按
V1 `CoverImagePipelineHandler` 的核心规则从同一 unified 的 raw sources 重建
`unified_videos.cover_image_id`：空 cover 采用第一个 raw cover；managed `LOCAL`/`R2`
优先于 `EXTERNAL`；同类 managedness 只有 `file_size > current * 1.1` 才替换；`file_size`
null 按 0 处理；`coverImageUrl` locked field 不阻止该 handler。本切片不做真实 R2 外呼，
V1 `promoteToR2()` side effect 后续已由 B8 拆到 storage command / due upload scheduling。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B8` 已完成第一刀。V3 新增 `PROMOTE_COVER_R2` storage command，
aggregation-worker 在 cover pipeline 选中 `LOCAL` + `LOCAL_STORED` cover 时发布命令；
storage-service 消费后按 V1 `promoteToR2()` 语义只安排 due upload，即 R2 active、
cover eligible 且 `next_retry_time is null` 时写入 `next_retry_time=now()`，不在
aggregation-worker 内执行真实 R2 upload。真实上传仍由 storage-service 的现有
`CoverImageR2SyncService` / `StorageR2Watchdog` 承接。

验证：

```text
.\gradlew.bat --no-daemon :shared:ircs-messaging:test :services:ircs-aggregation-worker:test :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B9` 已完成第一刀。aggregation-worker service scope 接入 JGraphT，
新增 same-batch raw/raw graph cluster：同一批 claimed raw 先按 edge `0.85`、
consistency `0.90`、有效年份差 `<= 1` 与未知年份桶隔离规则成簇；multi-raw cluster
会创建或复用一个 unified，将 cluster 内 raw 全部 bind 到该 unified，并在所有 bind 后
运行一次 pipeline rebuild、search sync 和 cover R2 promote side effect。context unified
混合图后续已由 B10 补第一刀，category/scoring 后续已由 B11/B12 补第一刀；真实 ES-backed
context recall、handler runtime 和 K8S live smoke 仍未完成。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B10` 已完成第一刀。aggregation-worker 已加入 raw/unified context
mixed graph：context recall 按 V1 只使用 douban/tmdb external ID，缺少有效 douban/tmdb 时
才退到 title/year recall；raw/unified node 可参与 edge、connected component 和 star
peeling，unified/unified 不建边，纯 unified component 跳过。cluster 可复用 context unified，
多个 context unified 通过 B2-B4 root/victim merge path 收敛。真实 ES-backed context recall、
category bucket、actor/director Jaccard、episode/area penalty、handler runtime 和 K8S live
smoke 当时仍未完成；后续 B11 已补 category bucket 第一刀，B12 已补 metadata
scoring/penalty 第一刀。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B11` 已完成第一刀。aggregation-worker 在 B9/B10 graph 基础上补回
V1 category bucket：raw candidate 从 `raw_category -> standard_category` 暴露标准分类名，
unified candidate 从 `unified_videos.category_id -> standard_category` 暴露标准分类名；
graph 建边前按 `categoryName.trim()` 分桶，空/blank 分类归入 `UNKNOWN_CATEGORY`。raw/raw
与 raw/unified edge 均不会跨具体分类，`UNKNOWN_CATEGORY` 只和 `UNKNOWN_CATEGORY` 比较。
actor/director Jaccard、episode/area penalty 后续已由 B12 补第一刀；真实 ES-backed
context recall、handler runtime 和 K8S live smoke 仍未完成。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B12` 已完成第一刀。aggregation-worker 在 B9-B11 graph 基础上补回
V1 `DefaultWeightedMatchingStrategy` 的 metadata scoring 与 penalty：raw/unified candidate
均补 actor/director/standard area names；metadata 为空时走 `titleScore * 0.95`，双方
metadata 存在时走 `titleScore * 0.70 + actor/director Jaccard * 0.30`；episode 不兼容扣
`0.15`，双方 area 都存在且不 contains 匹配扣 `0.10`，并 clamp 到 `0..1`。真实
ES-backed context recall、pipeline handler order/failure isolation runtime 和 K8S live smoke
仍未完成。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-C2` 注入优先级再次补强。config-service 已支持 property key、V3 alias
和 `APP_*` 大写下划线 env name 的统一 env-first 解析，展示摘要、事件来源和 MAIL SSL/STARTTLS
校验均保持 `ENV/K8S Secret/启动参数 -> DB -> 默认 seed`。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：`028-16-B15` 已完成聚合 B 线 dev K8S runtime live smoke，关闭此前 B9-B14
流水中记录的 K8S live smoke 缺口。为保留 V1 raw 业务更新时间语义，已新增
`raw_videos.aggregation_status_updated_at`；aggregation status claim/mark/reset 改用该列，
`updated_at` 继续供 DynamicField `remarks` 最新规则使用。B15 smoke 通过 MARK 8/8，后置
检查确认 `ircs-aggregation-worker`、`ircs-search-service`、`ircs-storage-service` 均 Ready，
`ircs-dev` 未新增 HTTPRoute，ResourceQuota 仍为 pods `28/30`、services `24/26`。

2026-06-09：docs/tasks 收敛 worker 已将最新 C1/C3 完成态纳入父任务口径。`028-16-C1`
first closure 已完成：shared auto-start gate 可观察模型已落地，normalization watchdog、
storage R2 watchdog、aggregation scheduler live gate 均为 `false`，三个 Deployment
`readyReplicas=1`；C1-S2c 已完成 cache warmup/TTL/evict first closure、catalog
countries/languages 全量 preset 和 resolver/真实资源站 dry-run prep；C1 父任务仍进行中，
剩余 resolver/真实资源站 live smoke，以及如需 V1 式 `RabbitListenerEndpointRegistry.start()`
自动放行。`028-16-C3-S6` 已完成 shared outbound
HTTP circuit breaker first slice，`InternalContentClients#refetchRawVideo` 已采用 shared
`INTERNAL_SERVICE`，focused 与组合 tests 全过，live smoke 未执行；`028-16-C3-S7`
随后完成 task-service POST JSON adoption。C3 父任务仍进行中，剩余 identity multipart、
storage strict shared adapter、BFF transport 等 adoption。

2026-06-10：docs/tasks 收敛 worker 更新本轮 implementation-first 结果。`028-16-F`
已完成 P1/P2 backlog 入账，8 个 backlog task spec 已创建，并把统一测试批次固定为
`UTB-02816-P1P2-01/02/03`。`028-16-C1-S2c` 完成实现：catalog-service 已接 V1 full
countries/languages preset，normalization-worker SystemConfig cache 增加 30s TTL missed-event
fallback，resolver/真实资源站 live smoke prep 默认 dry-run。`028-16-C3-S8/S9` 完成实现：
identity avatar storage multipart caller 与 admin/portal BFF streaming transport 已采用 shared
outbound adapter，focused no-network tests passed。`028-16-E` 完成 implementation-first
closure，状态 `MARK 5/6` partial；fake/dry-run/config/credential/error semantics 已落地。
`028-12-C` 完成 metadata/normalization/storage worker audit expansion implementation-first
closure，focused tests passed。以上除已有 focused tests 外，真实 LLM、resolver/真实资源站、
portal/admin BFF live、identity/storage/BFF 组合回归、ops query fixture cleanup、resource/no-residue
检查均后置到统一测试窗口；不得将其写成完全生产闭环。

2026-06-10：`028-16-F2/B15` 已补 aggregation local closure：结构化 search sync、malformed
payload reject、pipeline failure raw PENDING/unified DIRTY、`recalculateUnified()` 全成功才
SYNCED；focused aggregation/search tests 通过。真实 R2 upload、multi context victim merge、
持久 search outbox/stats 和 residue 检查仍后置到 `UTB-02816-P1P2-01`。
