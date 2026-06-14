# 088 V1/V3 功能与库细节深层 parity 缺口审计

状态：进行中

日期：2026-06-08

## 背景

`025` 已关闭 HTTP route missing，`028` 已完成多条 queue、R2、provider、audit 和
search sync live smoke。但用户指出：V1 是经过验证的单体实现，V3 不能只按接口壳和最小
MVP 行为判断完成；清洗、聚合、依赖库、初始化器、工具类和外部 SDK 的细节仍可能大量遗漏。

本报告将 `028` 后续范围从单点 HanLP/Tika 缺口升级为系统性差异审计。当前结论是：
HanLP/Tika 只是明确缺口之一；清洗策略链与聚合策略链均存在 MVP 近似实现，不能视为
V1 1:1 复刻。用户进一步明确要求：各种功能边角细节必须更加完善或一比一迁移，因此
本报告后续还要扩展为 V1 golden behavior inventory，对 V1 controller、service、scheduler、
consumer、initializer、support、provider、repository side effect 逐项给出归属结论。

## 审计口径

```text
V1 source:
  D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/build.gradle
  D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java

V3 source:
  D:/WorkSpace/Project/ircs/ircs-project-v3/build.gradle
  D:/WorkSpace/Project/ircs/ircs-project-v3/services/**
  D:/WorkSpace/Project/ircs/ircs-project-v3/shared/**
  D:/WorkSpace/Project/ircs/ircs-project-v3/docs/**
  D:/WorkSpace/Project/ircs/ircs-project-v3/tasks/**
```

## 第一轮明确缺口

| 编号 | 领域 | V1 证据 | V3 当前状态 | 判断 | 风险 |
| --- | --- | --- | --- | --- | --- |
| G1 | 文本标准化 | `MetadataNormalizer` 使用 `CharTable.convert`、`HanLP.convertToSimplifiedChinese`、`ZhJpConverterUtil.toSimple` | `RawVideoTextNormalizer.standardize` 仅 `trim` | 缺失 | 繁体、日式汉字、异体字、全角/半角文本无法按 V1 标准化 |
| G2 | 标题清洗 pipeline | `TitlePreProcessingHandler`、`TitleNoiseRemovalHandler`、`TitleCleanupHandler`、`TitleStructureHandler`、`TitleBilingualHandler`、`TitleNumeralHandler`、`TitleRedundantRepetitionHandler`、`AliasTitleNormalizationHandler` | 单个 `cleanTitle` 正则去 release noise 和年份 | 部分实现 | 标题、别名、副标题、季数、双语标题、括号噪音处理与 V1 差异大 |
| G3 | 描述抽取人名 | `DescriptionMetadataExtractor` 通过 HanLP `nr` 词性围绕“导演/主演”等关键词抽取 actor/director | V3 只从 metadata `actorNames/directorNames` 读取 | 缺失 | provider 未返回结构化人员时，V3 不会从 description 补齐演员/导演 |
| G4 | 地区映射策略 | `AreaMappingPolicy` 含 alias/composite 规则和 HanLP `ns` 地名识别 | V3 仅保存 raw area，并按标准表精确命中 | 部分实现 | 港台/欧美/新马泰、地区别名、地名词性识别缺失 |
| G5 | 类型同义词策略 | `StandardGenreMappingPolicy` 处理繁简替换、后缀剥离、保护词和同义词 | V3 exact name mapping | 部分实现 | “冒險/惊悚/韩剧/微短剧/动漫”等无法按 V1 规则归一 |
| G6 | LLM cleaning | `AbstractLlmCleaningService`、`LlmGenreCleaningService`、`LlmLanguageCleaningService`、`LlmAreaCleaningService`、`LlmCategoryCleaningService`、`LlmCleaningScheduler` | V3 无对应清洗 scheduler/service | 缺失 | raw 字典噪音和未映射值无法后处理，admin 筛选质量下降 |
| G7 | HanLP 预热 | `HanLPInitializer` 启动时预热 segmentation、简繁转换和 CharTable | V3 无 HanLP 依赖和预热 | 缺失 | 首次清洗请求抖动，且依赖实际未接回 |
| G8 | Tika MIME/编码 | `ImageSecurityValidatorImpl` 使用 Tika 检测图片；`ListScraperServiceImpl` 使用 Tika encoding detector | V3 无 Tika 依赖；storage image validator 为轻量签名判断，scraper 未复刻 Tika encoding | 缺失 | MIME spoof、网页编码识别与 V1 差异 |
| G9 | 图聚合与 fuzzy matching | `GraphClusteringEngine` + `DefaultWeightedMatchingStrategy` + JGraphT + commons-text JaroWinkler | V3 aggregation 仅按外部 ID 命中或新建 unified | 缺失 | 无外部 ID 或多源标题相似时无法合并，重复 unified 增加 |
| G10 | 聚合 pipeline | `AggregationPipeline` 包含 `BasicAttributeHandler`、`ExternalIdHandler`、`MetadataHandler`、`CategoryHandler`、`CoverImagePipelineHandler`、`DynamicFieldHandler` | V3 `upsertUnifiedFields` 只 coalesce 基础字段 | 部分实现 | unified category/genre/area/language/person/cover/dynamic metadata 未按 V1 汇总 |
| G11 | unified merge victim | `GraphAggregationServiceImpl` 会处理含既有 unified 的 cluster，选择 root 并删除 victim | V3 不合并多个 unified，不删除 victim | 缺失 | 历史重复 unified 无法收敛，关系和交互迁移缺失 |
| G12 | 依赖与初始化器 | V1 使用 opencc4j、HanLP、Tika、CQEngine、JGraphT、RxJava、Disruptor、HttpClient5、commons-text、java-uuid-generator、hibernate-micrometer、Spring AI、Thymeleaf、Caffeine/cache initializers | V3 只按服务最小依赖接入，很多库无对应服务切片 | 待逐项裁剪接回 | 运行时行为、性能、缓存、ID、LLM、邮件模板等与 V1 有差异 |
| G13 | V1 golden edge-case inventory | V1 大量边角能力分散在 controller、service、scheduler、consumer、initializer、support 和 repository 中 | V3 当前缺少逐类迁移状态总账 | 缺失 | 继续靠人工发现会遗漏注册、任务、缓存、维护、scraper、provider、mail、config、SSRF、HTTP client 等细节 |

## 清洗差异细节

V1 清洗不是一个函数，而是一条 handler pipeline：

```text
NormalizationPipeline
  -> TitlePreProcessingHandler
  -> TitleNoiseRemovalHandler
  -> TitleCleanupHandler
  -> TitleStructureHandler
  -> TitleBilingualHandler
  -> TitleNumeralHandler
  -> TitleRedundantRepetitionHandler
  -> AliasTitleNormalizationHandler
  -> BaseAttributesHandler
  -> YearNormalizationHandler
  -> AreaLanguageHandler
  -> CategoryGenreHandler
  -> PersonHandler
  -> CoverImageHandler
```

V3 当前 `RawVideoTextNormalizer` 已完成 MVP 和 `028-08` 的深层数据副作用：

```text
raw field normalization
actor/director relation
raw genre/language/area relation
raw category/data_source_category_id
standard exact mapping
retry watchdog
q.normalize.video live smoke
```

但 V3 尚未一比一复刻 V1 的标题 handler、HanLP/OpenCC 标准化、description 人名抽取、
AreaMappingPolicy、StandardGenreMappingPolicy 和 LLM cleaning。`028-08` 文档中也明确写过
“不引入 LLM、HanLP、缓存推断或 fuzzy matching”，因此这些不能算完成。

## 聚合差异细节

V1 聚合是图聚类和策略匹配：

```text
GraphAggregationServiceImpl
  -> load source candidates
  -> load context unified candidates
  -> GraphClusteringEngine
  -> DefaultWeightedMatchingStrategy
  -> AggregationPipeline
  -> bind raw videos
  -> merge/delete victim unified videos
  -> publish raw/unified search sync
```

`DefaultWeightedMatchingStrategy` 包含：

```text
season blocking
anime/live-action version blocking
external ID conflict/match
main/alias/subtitle cross-field JaroWinkler similarity
actor/director Jaccard metadata score
episode count penalty
area mismatch penalty
fingerprint cache
```

V3 当前 aggregation-worker 已完成 scheduler、claim、外部 ID 命中、新建 unified、基础字段
coalesce、绑定、BOUND 状态、raw/unified search sync 和 worker/job audit。但
`019` 文档明确将完整图聚类、CQEngine/JGraphT、merge victim、category/genre/area/language/person
关系和 cover image 归并列为非目标。因此当前聚合链路也是“可运行 MVP”，不是 V1 1:1。

并行审计 B 已补充如下更细缺口：

```text
P0 graph clustering/JGraphT parity
P0 DefaultWeightedMatchingStrategy parity
P0 title/year/context unified candidate recall
P0 duplicate unified merge, victim delete and search DELETE side effects
P0 aggregation metadata pipeline and relation table union
P1 UnifiedMetadataScheduler dirty lifecycle automatic closeout
P1 recalculate pipeline parity
P1 scheduler readiness/maintenance/backoff parity
P2 search sync persistent task or equivalent DLQ/audit decision
```

其中 `CQEngine` 在 V1 build.gradle 中存在，但第一轮源码检索尚未确认 aggregation 包实际 import；
`JGraphT` 在 `GraphClusteringEngine` 中明确使用。

## Golden Inventory 原则

后续不再只记录“已知大缺口”，而是为 V1 源码建立功能边角总账。每个 V1 类或行为必须归入
以下状态之一：

```text
1:1 migrated
improved replacement
partial MVP
explicit non-goal
missing
needs evidence
```

每个 `improved replacement` 必须说明为什么 V3 更完善，并提供测试或 smoke 证据；每个
`explicit non-goal` 必须有用户认可或文档决策；其余 `partial MVP/missing/needs evidence`
必须进入 `tasks/in-progress` 或 `tasks/backlog`。

第一批 inventory 模块：

```text
infrastructure/dataInit
infrastructure/http/support/cache/adaptive/llm/config
modules/ingestion/normalization
modules/ingestion/aggregation
modules/datasource/cleaning
modules/storage
modules/scraper
modules/magnet
modules/system
modules/portal
modules/task
modules/search
modules/content
modules/interaction
modules/metadata provider
```

## 优先级建议

| 优先级 | 子任务 | 原因 |
| --- | --- | --- |
| P0 | `028-16-A` 清洗策略链 parity | 直接影响所有 raw/unified 数据质量，portal/admin 后续验证会放大差异 |
| P0 | `028-16-B` 聚合图匹配与 pipeline parity | 直接影响重复 unified、绑定准确性、search/explore 结果 |
| P1 | `028-16-C` 依赖/初始化器/工具库裁剪接回 | 防止遗漏库级行为，但需要按服务边界控制镜像体积 |
| P1 | `028-16-D` Tika 文件解析与编码 parity | 影响媒体安全和 scraper 编码，需优先补 storage/scraper |
| P2 | `028-16-E` LLM cleaning gated parity | 依赖真实凭据与成本控制，应做 gated/fake sink 先行 |
| P0 | `028-16-F` V1 golden behavior inventory | 防止继续遗漏边角功能，是后续所有 parity 子任务的总账 |

## 验收边界

- 不能再用 “HTTP route missing=0” 表示功能完成。
- 每个缺口必须拆出 task spec，且用可验证 `MARK n/m` 证明。
- 接回大依赖前必须按服务隔离，避免所有服务镜像一起膨胀。
- 清洗和聚合的 parity 允许分阶段，但每阶段必须明确“已复刻哪些 V1 类/方法/行为”。
- 所有 live smoke 继续保持 `ircs-dev` 无公共 HTTPRoute、低资源和可清理 fixture。

## 当前状态

本报告为第一轮深层差异审计。并行只读审计 agent 已分别核对并合并：

```text
A: 清洗/标准化/文本提取链路，包括 canonical rawMetadata、title pipeline、description NER、
   raw 字典策略、metadata result 深层合并
B: 聚合/匹配/绑定/dirty lifecycle 链路，包括 JGraphT、JaroWinkler、duplicate unified merge、
   pipeline relation union、search sync task 语义
C: 依赖/初始化器/工具库/外部 SDK，包括 dataInit readiness gate、SystemConfig cache/event、
   shared outbound HTTP、Tika、UUID、Disruptor、Cache、LLM、Mail
```

已新增 `028-16-F` 承接全量 V1 golden behavior inventory，并落地
`docs/reports/089-v1-golden-behavior-inventory.md`。后续按 `028-16-A` 至 `028-16-F`
继续拆分和实现 P0/P1 行为切片。

2026-06-09 更新：本报告表格中的 G11 是第一轮审计快照。后续 `028-16-B2` 已完成
duplicate unified merge 第一刀：root/victim plan、victim raw binding 迁移、favorite/history
冲突清理、victim scoped relation 删除、victim unified delete 和 search DELETE side effect
已有本地 aggregation/search 测试固定。完整 JGraphT raw/raw cluster、relation union 和 K8S
live smoke 仍未完成，详见 `docs/reports/089-v1-golden-behavior-inventory.md`。

2026-06-09 追加更新：后续 B3-B15 已继续关闭聚合 B 线主要 runtime gap。当前以
`docs/reports/089-v1-golden-behavior-inventory.md` 为准：B15 已通过 dev K8S aggregation
pipeline runtime live smoke MARK 8/8；原 G11/B2 更新段中的 “K8S live smoke 仍未完成”
是当时状态，不再代表当前聚合 B 线主链路状态。
