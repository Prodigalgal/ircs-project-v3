# 089 V1 golden behavior inventory

状态：进行中

日期：2026-06-08

## 背景

V3 已完成 HTTP route coverage、门户/BFF、队列消费者、R2、provider、audit 和多条
live smoke。但这些只能证明“入口和若干主链路可运行”，不能证明 V1 已验证功能已经
无损迁移。

用户明确要求：不只是 HanLP/Tika、清洗和聚合，各种功能边角细节都必须要么在 V3
更加完善，要么按 V1 一比一迁移。本文档作为后续行为迁移总账，逐模块记录 V1 行为、
V3 当前状态、缺口风险和任务归属，避免继续靠聊天中的临时记忆推进。

## 审计来源

```text
V1:
  D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/build.gradle
  D:/WorkSpace/Project/ircs/ircs-project-v1/ircs-backend/src/main/java

V3:
  D:/WorkSpace/Project/ircs/ircs-project-v3/build.gradle
  D:/WorkSpace/Project/ircs/ircs-project-v3/services/**
  D:/WorkSpace/Project/ircs/ircs-project-v3/shared/**
  D:/WorkSpace/Project/ircs/ircs-project-v3/docs/**
  D:/WorkSpace/Project/ircs/ircs-project-v3/tasks/**
```

第一轮模块计数来自 V1 `src/main/java` 按包路径统计，后续要继续细化到类、方法和
side effect。

## 状态口径

| 状态 | 含义 |
| --- | --- |
| `1:1 migrated` | V3 已按 V1 行为迁移，并有对应测试或 smoke 证据。 |
| `improved replacement` | V3 有更完整或更安全的替代实现，且有证据证明覆盖 V1 关键语义。 |
| `partial MVP` | V3 有可运行实现，但只覆盖主路径或接口外壳。 |
| `explicit non-goal` | 明确不迁移，且有需求、决策或用户确认。 |
| `missing` | V3 未发现等价行为。 |
| `needs evidence` | 可能已实现，但尚无足够源码、测试或 live smoke 证据。 |

任何 `partial MVP`、`missing`、`needs evidence` 都必须关联到 in-progress 或 backlog
任务；任何 `improved replacement` 必须引用测试或 smoke 证据。

2026-06-09 最小账本摘要：A6/A7 已按 done first slice 纳入 inventory；C2-S15 已记录为 no-write config event live matrix smoke harness，C2-S16 已记录为 live Rabbit config event safe smoke dry-run；C3 admin-bff 与 portal-bff `BFF_PROXY` target validation 已记录为已完成切片；`028-07-F` 已记录为 explicit V1 bucket smoke preflight/no-write guard。本轮追加：`028-10-E` notification send history schema / ops query 已完成并有 notification-worker + ops-service 定向 build evidence；`028-16-C3` notification MAIL credential INTERNAL_SERVICE 已实现并通过 focused no-real-network 证据，但 full suite 仍受 send history H2 `TIMESTAMPTZ` 阻塞；`028-16-A8` title season/subtitle first slice 已完成；`028-07-G` real R2 explicit V1 bucket write smoke 已完成 `PUT 200 / HEAD 200 / DELETE 204 / cleanup HEAD 404` 且无 residue；`028-06-E` Douban provider first slice 已完成 `MARK 6/6`，真实只读 smoke HTTP 200 且解析候选，provider implementation 与 credential catalog 阻塞已解除，dev gate 仍按预期关闭；`028-10-F` SMTP staging live preflight 已记录真实阻塞点且无残留；`028-16-C2-S17/C2-S19/C2-S20` 已完成 real Rabbit publish/deliver/ack 与 content DB fallback read probe，均无 residue；`028-16-C3-S6` 已完成 shared outbound circuit breaker first slice + content internal caller。剩余风险只保留 Rabbit outage TTL、storage strict adapter、task/identity internal callers、SMTP staging schema deploy/send、Douban queue full parity/credential 场景/RT provider 与 cover/avatar R2 full chain。

2026-06-09 文档集成补记：最小账本只补真实切片状态，不改父任务结构。当前建议口径为：`028-10-E` 视为 done with build evidence；`028-16-C3` notification credential 视为 implemented with targeted evidence，full suite rerun pending；`028-16-A8` 视为 done first slice；`028-07-G` 视为 real live smoke done；`028-06-E` 视为 Douban provider implementation/catalog first slice done，full queue parity 另拆；`028-10-F`、`028-16-C2-S17` 视为有真实执行证据的 BLOCKED/阻塞切片。

## V1 模块库存

| V1 范围 | 类数量 | 第一轮判断 | 任务归属 |
| --- | ---: | --- | --- |
| `common/base` | 2 | needs evidence | `028-16-F` |
| `common/constant` | 2 | needs evidence | `028-16-F` |
| `common/enums` | 3 | needs evidence | `028-16-F` |
| `common/exception` | 7 | needs evidence | `028-16-F` |
| `common/utils` | 4 | needs evidence | `028-16-F` |
| `infrastructure/adaptive` | 3 | partial MVP | `028-16-C` |
| `infrastructure/aop` | 5 | partial MVP | `028-12`、`028-16-F` |
| `infrastructure/cache` | 2 | partial MVP | `028-16-C` |
| `infrastructure/config` | 11 | partial MVP | `028-16-C` |
| `infrastructure/dataInit` | 13 | missing | `028-16-C` |
| `infrastructure/event` | 10 | partial MVP | `028-16-C`、`028-16-F` |
| `infrastructure/http` | 6 | partial MVP | `028-16-C` |
| `infrastructure/llm` | 3 | missing | `028-16-E` |
| `infrastructure/queue` | 6 | partial MVP | `028-05`、`028-12`、`028-16-F` |
| `infrastructure/security` | 8 | partial MVP | `028-16-C`、`028-16-F` |
| `infrastructure/support` | 5 | partial MVP | `028-16-C` |
| `infrastructure/traffic` | 4 | partial MVP | `028-10`、`028-16-C` |
| `modules/content` | 87 | partial MVP | `028-12`、`028-16-F` |
| `modules/datasource` | 62 | partial MVP | `028-16-A`、`028-16-E` |
| `modules/ingestion` | 66 | partial MVP | `028-16-A`、`028-16-B` |
| `modules/interaction` | 30 | partial MVP | `028-05-A`、`028-12`、`028-16-F` |
| `modules/magnet` | 34 | partial MVP | `028-06`、`028-16-F` |
| `modules/metadata` | 33 | partial MVP | `028-16-A`、`028-16-F` |
| `modules/portal` | 53 | partial MVP | `028-07`、`028-12`、`028-16-F` |
| `modules/resolver` | 6 | needs evidence | `028-16-F` |
| `modules/scraper` | 42 | partial MVP | `028-06`、`028-16-D`、`028-16-F` |
| `modules/search` | 28 | partial MVP | `028-09`、`028-16-B`、`028-16-F` |
| `modules/storage` | 31 | partial MVP | `028-07`、`028-16-D`、`028-16-F` |
| `modules/system` | 72 | partial MVP | `028-10`、`028-12`、`028-16-C` |
| `modules/task` | 31 | partial MVP | `028-12`、`028-16-F` |

## P0 缺口总账

| 编号 | 范围 | V1 行为 | V3 当前状态 | 风险 | 任务 |
| --- | --- | --- | --- | --- | --- |
| P0-01 | 初始化编排 | `DataInitializationOrchestrator` 按 `InitTask` 顺序写默认数据、`markAsReady()` 后再启动 Rabbit listener | partial：`028-16-C1` 已完成 V1 initializer-to-V3 owner matrix；`028-16-C1-S1` 已在 catalog-service 落地 category/language/area/genre 默认字典 seed 并通过 empty-db 测试；`028-16-C1-S2` 已落地 25 个 data source preset 和 default_mapping patch；`028-16-C1-S2b` 已落地显式远程 `class[]` 分类同步 service 和 raw_category upsert 测试；`028-16-C1-S3` 已补 notification-worker mail listener `APP_NOTIFICATION_LISTENER_ENABLED:false` 默认关闭 guard；`028-16-C1-S4` 已补 task-service 默认 collection task seed 和 `APP_TASK_SCHEDULER_ENABLED:false` cron gate；`028-16-C1-S5` 已补 content-service 默认 resolver preset seed；`028-16-C1-S6` 已补 credential-service runtime env/file initializer；`028-16-C1-S7` 已补 credential admin/public summary 脱敏；`028-16-C1-S8` 已补 data source admin 写操作后远程分类同步触发；`028-16-C1-S9` 已补 task-service `TaskWatchdog` `APP_TASK_WATCHDOG_ENABLED:false` 默认关闭 guard；C1 full readiness / auto-start gate first closure 已新增 shared gate health detail，normalization/storage watchdog 默认关闭，aggregation dev scheduler 常驻关闭，并通过 live K8S gate smoke 3/3；V3 有 config-service runtime defaults 和多服务 `APP_*_LISTENER_ENABLED:false` listener 开关，但未恢复 V1 式全局自动放行 | 缓存预热顺序、全服务 worker 真正自动放行、全量 preset 与 resolver/真实资源站 live smoke 仍需后续拆分；credential/provider、resolver 播放链路和真实资源站分类同步仍需 live smoke | `028-16-C1`、`028-16-C` |
| P0-02 | SystemConfig 默认值与缓存 | `SystemConfigInitializer` 生成 `ADMIN_PASSWORD`、`JWT_SECRET`、classpath 默认 JSON；`SystemConfigCacheAccessor` + `@CacheEvict` | partial MVP：`028-16-C2` 已在 config-service 补默认 key catalog、敏感默认值生成、LLM prompt 校验、MAIL SSL/STARTTLS 冲突校验和 core config delete 保护；`028-16-C2-S4` 已把 V1 133 个 `SystemConfigKey` 全量纳入 `SystemConfigDefaults`，diff 为 `V1=133 V3=133 Missing=0 Extra=0`；运行时优先级已固定为 ENV/K8S Secret/ConfigMap/configtree/启动参数 -> DB -> 默认 seed，并在 config API 摘要中暴露 `effectiveSource`/脱敏后的 `effectiveValue`；已显式兼容 `APP_*` 大写下划线 K8S env name、Spring Boot config tree、Spring Cloud Kubernetes ConfigMap/Secret property source 与 V3 runtime alias，例如 `APP_MAIL_ENABLED`、`APP_IDENTITY_JWT_SECRET`；config-service 自身 summary、事件来源和 MAIL SSL/STARTTLS 校验已改用 runtime injection source guard，classpath `application.yaml` 静态默认值不会被误判为 `INJECTED`；identity/interaction 的直连读取也已补运行时覆盖源识别，避免 `application.yaml` 静态默认值误压 DB；已新增 `SystemConfigChangedEvent` domain event，config-service create/update/delete afterCommit best-effort 发布 JSON payload；identity/interaction 已补本地 config cache、独立 `config.system.changed` 广播队列和按 key evict；identity admin 改密旁路写入已补 afterCommit best-effort event publisher；`028-16-C2-S5` 已让 search-service cover URL 读取 `app.storage.r2.public-domain` / `app.storage.public-path` 走 env-first/DB/default seed，并补 `q.search.config_changed` 按 key evict；`028-16-C2-S6` 已让 interaction-service 收藏/历史 cover URL 读取同样走 env-first/DB/default seed，并复用 `q.interaction.config_changed` evict 路径；`028-16-C2-S7` 已让 portal-service 首页/分类/探索/详情 cover URL 读取同样走 env-first/DB/default seed，并新增 `q.portal.config_changed` evict 路径；`028-16-C2-S8` 已让 storage-service cover 管理 URL、头像上传响应、头像 R2 同步 URL 改写和 watchdog 本地头像扫描前缀走同样优先级，并新增 `q.storage.config_changed` evict 路径；`028-16-C2-S9` 已让 normalization-worker retry/backoff/watchdog batch/pending timeout 读取 `normalization.max-retries`、`normalization.backoff.base-seconds`、`normalization.batch-size` 和 `normalization.timeout.pending-min` 走同样优先级，并新增 `q.normalization.config_changed` evict 路径；`028-16-C2-S10` 已让 aggregation-worker scheduler batch size 读取 `aggregation.batch-size` 走同样优先级，V1 默认恢复为 `200`，dev K8S 用 `APP_AGGREGATION_BATCH_SIZE=1` 低资源覆盖，并新增 `q.aggregation.config_changed` evict 路径；`028-16-C2-S11` 已让 notification-worker MAIL enable/from/SMTP connect options 读取 `app.mail.*` canonical keys 走同样优先级，V1 默认 from 恢复为 `HuaWai System <huawai.system@gmail.com>`，dev K8S 用 `APP_MAIL_ENABLED=false` 安全覆盖，并新增 `q.notification.config_changed` evict 路径；`028-16-C2-S12` 已让 metadata-worker provider fan-out、provider execute gate 和 pending TTL 读取 `app.metadata.enabled`、`app.metadata.tmdb.enabled`、`app.metadata.douban.enabled`、`app.metadata.rotten-tomatoes.enabled`、`app.metadata.timeout.pending-min` 走同样优先级，dev K8S 用 provider enable env false 安全覆盖，并新增 `q.metadata.config_changed` evict 路径；`028-16-C2-S13` 已让 ops-service dev-safe reindex limit 读取 `app.ops.maintenance.reindex.dev-limit` 走运行时注入/DB/V3 fallback，dev K8S 用 `OPS_MAINTENANCE_REINDEX_DEV_LIMIT=5` 保持低资源，并新增 `q.ops.config_changed` evict 路径；`028-16-C2-S14` 已让 content-service `InternalContentClients` 调用期读取 `app.content.internal.scraper-base-url`，优先级为 RuntimeInjectedConfig -> `system_configs` DB -> V3 fallback，并新增 `q.content.config_changed` 与 `APP_CONTENT_CONFIG_LISTENER_ENABLED`；`028-16-C2-S17/C2-S19` 已完成 dev live config-service publisher rollout 和真实 Rabbit publish/deliver/ack smoke，临时 update 与 restore delete 均 ack，DB/port-forward 无残留 | content-service 已完成 internal scraper base URL 消费者扫描和第一刀迁移，但 live deployment 有 `APP_CONTENT_SCRAPER_BASE_URL` runtime override，DB fallback read probe 暂不可直接观察；audit 开关、resolver seed gate、content media URL fallback 等仍明确为启动绑定或后续独立切片；ops full maintenance runner、metadata provider HTTP 参数、provider live smoke、aggregation adaptive min/max、readiness/maintenance gate 仍需后续独立迁移；storage 的 R2 credential、image download knobs、watchdog batch size 仍需后续独立迁移或标注重启生效；normalization 完整 scheduler 的 PROCESSING reset、queue depth guard 和 adaptive workload 仍未闭环；cache TTL/evict 全矩阵和 Rabbit outage fallback 仍未闭环 | `028-16-C2`、`028-16-C2-S4`、`028-16-C2-S5`、`028-16-C2-S6`、`028-16-C2-S7`、`028-16-C2-S8`、`028-16-C2-S9`、`028-16-C2-S10`、`028-16-C2-S11`、`028-16-C2-S12`、`028-16-C2-S13`、`028-16-C2-S14`、`028-16-C2-S17`、`028-16-C2-S19`、`028-16-C` |

> 2026-06-09 C2-S20 补充：上表中 C2-S17 read probe blocked 的旧状态已被
> `docs/reports/095-02816c2s20-content-db-fallback-recoverable-probe.md` 覆盖；C2-S17
> 现已完成 `MARK 7/7`，剩余 C2 风险只保留 Rabbit outage TTL fallback。
>
> 2026-06-09 C2-S21 补充：Rabbit outage / missed-message 策略已决策并落地为
> `Rabbit event evict + short TTL read-through`；content-service 代表消费者新增默认
> `30s`、可配置的 DB fallback cache TTL。未执行新的 live Rabbit outage smoke，未停 RabbitMQ；
> 使用 focused test 模拟未收到 event 但 DB 已变更后的最终一致性。C2 父任务可关闭。
| P0-03 | 文本标准化 | `MetadataNormalizer` 使用 CharTable、HanLP、OpenCC/日中转换；`HanLPInitializer` 预热 | partial MVP：`028-16-A2` 已在 normalization-worker service-scoped 接入 HanLP/opencc4j，并覆盖 NFKC、繁简、日中转换第一批 fixture；`028-16-A4` 已补 V1 `HanLPInitializer` 预热 runner，覆盖 `HanLP.segment()`、`HanLP.convertToSimplifiedChinese()` 和 `CharTable.convert()` no-throw 测试；未直接在 title/text standardize 主路径调用会小写 ASCII 的 `CharTable.convert` | 已缓解繁简、全半角、日中混排和启动预热差异；CharTable ASCII case 差异和更完整 raw token 策略仍待决策 | `028-16-A` |
| P0-04 | 语言/地区/类型映射 | V1 `RawLanguageServiceImpl`、`AreaMappingPolicy`、`StandardGenreMappingPolicy` 使用分词、别名、同义词、保护词 | partial done：V3 已有 raw relation 与 standard exact mapping；`028-16-A5` 第一刀已在 normalization-worker 增加 `RawRelationAliasPolicy`，对 `港台`、`港澳台`、`日韩`、`日韩欧美`、`欧美`、`日本地区`、`国粤双语`、`国语粤语`、`粤语国语`、`日语中字` 等 area/language 复合或后缀 token 追加可命中 raw alias / standard exact mapping 的 token，且保留原始 token；`locked_fields.area` / `locked_fields.language` 仍完全压制对应 relation 输出；`028-16-C1-S2c` 已补 countries/languages 全量 V1 preset seed | 地区/语言 P0 复合 token 和 full preset seed 已缓解；完整 V1 NLP 地名识别、genre synonym/suffix/protected-term policy 和 live smoke 仍未完成 | `028-16-A`、`028-16-A5`、`028-16-C1-S2c` |
| P0-05 | 标题清洗 pipeline | V1 多个 `NormalizationHandler` 分阶段处理噪音、结构、双语、数字、别名 | partial MVP：`028-16-A2` 已覆盖符号规整、HTML/URL/文件后缀/括号年份/release noise、双语、自重复、中文序数和数字敏感 fixture | 季数/副标题字段落库、alias 去重、完整 title handler pipeline 仍未 1:1 | `028-16-A` |
| P0-06 | description 人名抽取 | `DescriptionMetadataExtractor` 使用 HanLP `nr` 和导演/主演上下文抽取 | done first slice：`028-16-A4` 已在 normalization-worker 新增 V1 风格 `DescriptionMetadataExtractor`，复刻导演/主演关键词、`director=3`/`actor=5` 窗口和 `nr*` 人名规则；`RawVideoTextNormalizer` 仅在显式 actor/director metadata 缺失时从 description fallback，且 locked fields 压制对应关系写入 | 简介 NER 主语义已恢复；真实 HanLP 词库召回质量和 full title/person pipeline 仍需后续 live/golden fixture 深化 | `028-16-A` |
| P0-07 | 聚合图匹配 | `GraphClusteringEngine` + JGraphT + `DefaultWeightedMatchingStrategy` + JaroWinkler | partial done：B1 已覆盖单 raw -> unified matching fixture；B9 已在 aggregation-worker service scope 接入 JGraphT same-batch raw/raw graph cluster 第一刀，覆盖 edge `0.85`、consistency `0.90`、有效年份差 `<= 1`、未知年份桶隔离、season/version/external-id conflict 阻断和 multi-raw cluster 一次性绑定；B10 已补 raw/unified context mixed graph 第一刀，context external recall 按 V1 只用 douban/tmdb，graph 中 unified/unified 不建边、纯 unified component 跳过；B11 已补 V1 category bucket 第一刀，raw/unified candidate 均暴露标准分类名，空分类落 `UNKNOWN_CATEGORY`，graph edge 不跨分类；B12 已补 V1 metadata scoring/penalty 第一刀，candidate 补 actor/director/standard area names，按 `0.70 title + 0.30 Jaccard`、episode `-0.15`、area `-0.10` 计算；B13 已补 V1 ES-backed context recall 第一刀，无有效 douban/tmdb 的 raw 通过 search-service internal ES candidate query 召回 UUID，再回 PostgreSQL 装载 context candidate；B14 已补 V1 pipeline handler order/failure isolation runtime，并用 JDBC nested savepoint 避免 PostgreSQL failed transaction block 阻断后续 stage；B15 已在 dev K8S 通过 multi-raw scheduler live smoke MARK 8/8 | 图匹配与 pipeline runtime 主链路已通过 dev K8S；真实 R2 upload、dirty lifecycle/reconciliation 深化仍待后续 | `028-16-B`、`028-16-B9`、`028-16-B10`、`028-16-B11`、`028-16-B12`、`028-16-B13`、`028-16-B14`、`028-16-B15` |
| P0-08 | 聚合 pipeline | V1 `AggregationPipeline` 合并基础、外部 ID、metadata、category、cover、dynamic fields | partial done：B1/B2/B3/B4 已覆盖 matching、duplicate victim merge、victim unified relation union 和 victim scalar merge；B5 已完成 raw-source category 投票、locked_fields 边界和 actors/directors/genres/languages/areas relation rebuild 第一刀；B6 已完成 BasicAttribute、ExternalId、DynamicField 标量聚合第一刀；B7 已完成 `CoverImagePipelineHandler` cover 选择规则第一刀；B8 已完成 cover R2 promote side effect 的 storage command / due upload scheduling 第一刀；B9 已完成 same-batch raw/raw cluster 多 raw bind 后一次 pipeline rebuild 第一刀；B10 已完成 context unified reuse/merge 后的 pipeline rebuild 第一刀；B11 已让 graph 层 category bucket 与 pipeline category 投票来源保持标准分类口径；B12 已让 graph scoring 使用同一批 raw/unified 关系 metadata；B13 已让 context title recall 从 SQL fallback 改为 search-service owned ES recall；B14 已将 V3 pipeline rebuild 收束为 V1 顺序 `BasicAttribute(10)->ExternalId(20)->Metadata(30)->DynamicField(40)->Category(50)->CoverImage(60)`，并补单 handler nested savepoint 失败隔离与 cover promote 透传测试；B15 已新增 `raw_videos.aggregation_status_updated_at`，避免 scheduler 状态心跳污染 V1 DynamicField `remarks` 所依赖的 raw 业务 `updated_at`，并通过 dev K8S runtime live smoke | pipeline 核心 runtime 已通过 dev K8S；真实 R2 upload live smoke 后续仍需执行 | `028-16-B`、`028-16-B8`、`028-16-B9`、`028-16-B10`、`028-16-B11`、`028-16-B12`、`028-16-B13`、`028-16-B14`、`028-16-B15` |
| P0-09 | duplicate unified merge | V1 cluster 中可选 root 并删除 victim，迁移绑定和 search side effect | partial done：`028-16-B2` 已让 aggregation-worker 形成 root/victim plan，迁移 victim raw binding，清理 favorite/history 冲突，删除 victim unified row，并发布结构化 `UNIFIED_VIDEO DELETE`；`028-16-B3` 已在删除 victim 前 union victim `actors/directors/genres/languages/areas` 到 root；`028-16-B4` 已按 V1 `UnifiedVideo.merge(victim)` 在 root 缺值时补 victim 标量字段并保留更新的 `last_trend_at`；`028-16-B9` 已让 same-batch raw/raw cluster 可复用或合并多个 unified victim；`028-16-B10` 已让多个 context unified 通过 raw bridge 进入同一 cluster 并复用 B2-B4 victim merge path；`028-16-B11` 已阻断跨 category context bridge；`028-16-B12` 已用 actor/director Jaccard 与 episode/area penalty 阻断低置信 context bridge；`028-16-B13` 已补真实 ES context recall 第一刀；B15 已在 dev K8S 证明 clustered raw 能经 scheduler 绑定到单个 unified，并完成 pipeline/search/queue cleanup | victim merge 本地证据充分，dev K8S 覆盖 multi-raw 单 unified 主链路；多 context unified victim merge live smoke 可后续独立补强 | `028-16-B`、`028-16-B2`、`028-16-B3`、`028-16-B4`、`028-16-B9`、`028-16-B10`、`028-16-B11`、`028-16-B12`、`028-16-B13`、`028-16-B15` |
| P0-10 | 统一出站 HTTP | V1 `AppHttpClientFactoryImpl`/`HttpServiceImpl` 统一池化、DNS/SSRF、retry、circuit breaker、gzip、随机 UA | partial MVP：`028-16-C3` 已在 `shared:ircs-common` 落地 fake-first `PUBLIC_FETCH` contract，覆盖 SSRF fail-closed、redirect never、默认 UA、429/5xx/IO retry、gzip 解压和显式 proxy policy；`CatalogFetchSampleClient`、`YtsBzMagnetProviderSearchRunner`、`ListScraperClient` 与 `RestClientTmdbHttpClient` 已接入 shared outbound client，scraper 保留 custom header/proxy/auth、显式 UA 和 V1 random UA 语义，TMDB 保留 2xx/404/429/5xx/4xx 业务分类；`028-16-C3-S4a` 已新增 policy type 区分 `PUBLIC_FETCH` / `INTERNAL_SERVICE` / `BFF_PROXY`，并把 metadata-worker `TmdbCredentialRepository` 迁到 shared `INTERNAL_SERVICE`；`028-16-C3-S6` 已补 shared circuit breaker first slice，覆盖 timeout、5xx、IO/connection refused、open、half-open recovery、ENV/K8S override 和 caller key，并把 content-service `InternalContentClients` 迁到 shared `INTERNAL_SERVICE` | storage strict adapter、task/identity 内部 caller、BFF streaming/multipart/shared transport 仍未全量接入；尚未做真实公网 provider smoke | `028-16-C3`、`028-16-C3-S4a`、`028-16-C` |
| P0-11 | canonical rawMetadata | V1 `ScrapedVideoAssembler` 主动构造 `actorNames/directorNames/genreNames/rawTypeId/rawTypeName/coverImageUrl/dataSourceId` 等 canonical key | partial improved: `028-16-A1` 第一刀已让 `ScraperMappingService.mapDetail/directItem` 输出 canonical rawMetadata，并用 scraper-service fixture 覆盖 `sourcePayload`、rawType、genre、actor、director、cover key | 已缓解真实 scraper 数据缺 canonical key 的风险；仍未做真实 provider live smoke 和大 payload/charset 行为验证 | `028-16-A`、`028-16-F` |
| P0-12 | metadata result 深层合并 | V1 `MetadataResultCollector` 合并 area/language/person/genre 与 cover image | partial MVP：`028-16-A3` 第一刀已将 provider area/language/person/genre/poster URL 合并进 `raw_metadata`，供 normalization 后续消费 | 已缓解富字段丢失；仍未做真实 provider live smoke、cover 下载和 aggregation pipeline 深层汇总 | `028-16-A`、`028-16-B` |

2026-06-10 C3-S8 入账修正 2026-06-09 旧剩余风险口径：
`identity-service/RestClientAvatarStorageClient` 已迁到 shared
`INTERNAL_SERVICE` multipart POST caller，保留 `/internal/storage/avatars`、multipart `file`
字段、filename/content type/binary body、`StoredAvatar` response mapping 和
`storage.sync.failed` BAD_GATEWAY 异常外观；focused fake transport tests 通过，live/full tests
按统一测试策略后置。C3 剩余项收敛为 BFF full transport method/body/streaming 复用和
caller-specific circuit config 矩阵；storage `IMAGE_DOWNLOAD_STRICT` adapter 已由并行 worker
完成。

## P1 缺口总账

| 编号 | 范围 | V1 行为 | V3 当前状态 | 风险 | 任务 |
| --- | --- | --- | --- | --- | --- |
| P1-01 | Tika 图片 MIME | V1 `ImageSecurityValidatorImpl` 用 Tika detect，并支持 jpeg/png/webp/gif/avif/bmp/x-icon | partial done：D1 已在 storage-service service-scoped 接入 `tika-core:3.2.2`，`ImageSecurityValidator` 改用 `Tika.detect(byte[])`，保留 10MB、filename blacklist、allowlist、declared MIME 不参与放行；ICO 的 Tika canonical MIME 已窄归一化到 V1 allowlist 的 `image/x-icon`，fixture 覆盖有效图片、spoof/non-image、空文件、超 10MB 和非法文件名 | storage MIME 内核已对齐第一刀；仍需真实下载链路 live smoke 或更多 polyglot fixture 证据 | `028-16-D` |
| P1-02 | Tika charset / scraper streaming | V1 list scraper 使用 `UniversalEncodingDetector` 和 Jackson streaming parse，`ScrapeListResult` 输出 `totalPages` / `totalItems`；V1 build 声明 `tika-parsers-standard-package` | partial done：D2/D4 已完成 raw bytes charset detect、Jackson parser、detail charset、declared `Content-Type` charset、`fetchListPage()` pagination 输出、GB18030/Big5/Shift_JIS/UTF-8 BOM/invalid bytes fixture；`028-20-11A` 已按 D06 把 scraper-service 从窄 text module 切回 `tika-parsers-standard-package:3.2.2`，storage-service 保持 `tika-core` only | list/detail 本地 golden matrix 已补齐 implementation closure；真实 provider 低资源 live charset smoke 和完整 `UTB-02816-P1P2-01` 仍后置；未新增 PDF/Office/text extraction API | `028-16-D` / `028-16-F3` / `028-20-11A` |
| P1-03 | LLM cleaning | V1 Spring AI/OpenAI + cleaning scheduler 处理 language/area/genre/category raw value | V3 未接真实清洗 service，connectivity 多为 dry-run | raw 字典噪音无法后处理，管理端连接测试语义偏弱 | `028-16-E` |
| P1-04 | Mail/Thymeleaf | V1 Thymeleaf 渲染、KeyPoolManager 租 MAIL credential、队列 consumer 发送 | V3 notification-worker 基本实现，仍需 disabled/fake/smtp/DLQ 证据 | 邮件开关、from display、失败重投、真实 SMTP 语义需要对齐 | `028-10`、`028-16-F` |
| P1-05 | Cache TTL/evict | V1 HybridCache、Caffeine micro cache、Redis L2 TTL、RateLimitRuleCache | V3 多服务局部 cache，无统一 V1 cache name/TTL/evict 映射 | 首页、字典、rate-limit、config 改动后生效窗口不明 | `028-16-C` |
| P1-06 | Async logging / Disruptor | V1 显式 `com.lmax:disruptor` 并使用 Log4j2 async logger | V3 未确认所有 service log4j2 async 配置和依赖 | 启动失败、退化或性能差异 | `028-16-C` |
| P1-07 | UUID generator | V1 `IdGenerator` 使用 time-based UUID；V3 多处 `UUID.randomUUID()` 或 SQL `gen_random_uuid()` | 行为不同但不一定破接口 | 索引局部性、时间排序和审计追踪假设不同 | `028-16-C` |
| P1-08 | Aggregation dirty lifecycle | V1 `UnifiedMetadataScheduler` 自动扫描 DIRTY 并重算 | V3 有 dirty 标记和 HTTP recalculate，但自动 closeout 不完整 | 旧 unified 脏状态需要人工触发或链路不闭合 | `028-16-B` |
| P1-09 | Search sync task 语义 | V1 有 search outbox/relay/reconciliation/stats processor | V3 已完成 producer live smoke，但持久任务语义或等价 DLQ/audit 仍需决策 | 搜索侧漏同步后自动补偿能力不明 | `028-16-B`、`028-16-F` |
| P1-10 | raw category 推断 | V1 `CategoryMappingPolicy.inferCategoryName` 未命中时可落到“其他”等兜底分类 | V3 主要按 `standard_category.name/slug` exact match 回填 | 来源分类统计和 portal/admin 分类筛选与 V1 偏离 | `028-16-A` |
| P1-11 | description HanLP NER fallback | V1 `PersonHandler` 在人员为空时调用 `DescriptionMetadataExtractor` 从简介抽取导演/主演 | done first slice：`028-16-A4` 已补 description NER fallback、显式字段优先和 locked actors/directors 压制测试 | 本地行为已固定；真实老数据批量重洗和 live normalize smoke 后续再做 | `028-16-A` |

## P2/P3 待裁剪项

| 范围 | V1 依赖/行为 | 第一轮判断 | 后续动作 |
| --- | --- | --- | --- |
| CQEngine / RxJava | build 中存在，第一轮源码未发现直接 import | 待确认 | 做 dependency insight、jdeps/classpath 扫描，确认是否历史依赖 |
| MapStruct | V1 多个 mapper 做 DTO/entity 映射 | 非直接缺失 | 为 V3 手写 JDBC/record mapper 建字段级 golden guard |
| hibernate-micrometer / JPA | V1 JPA 指标 | 多数不适用 | 做 ADR，说明 V3 JDBC 化后的 Hikari/JDBC 指标替代 |
| normalization retry/watchdog | V1 启动重置 `PROCESSING`、队列空闲才补 PENDING、FAILED 到期重试并接 adaptive workload | 部分实现 | 当前 V3 retry watchdog 已可重投，但 queue depth guard、PROCESSING reset、adaptive workload 语义不同 |

2026-06-10 F1/A11 normalization golden P1/P2 implementation closure：

- P0-04/P0-05 增量：normalization-worker 已补 location NLP first closure、genre NLP first closure、
  raw category fallback、release group 前缀剥离和 `S02` / `Part II` / `Vol.2` season fixture。
- retry/watchdog 增量：`NormalizationRetryWatchdog` 已拆分 due `FAILED` 与 stale
  `PENDING/PROCESSING` recovery；stale active rescue 受 `q.normalize.video` queue depth guard
  控制；重投前会把可恢复状态归一为 `PENDING` 并清空 `next_normalization_retry_time`。
- adaptive workload 增量：`normalization.batch-size.min/max` 已纳入 watchdog batch clamp；
  runtime/K8S `APP_NORMALIZATION_WATCHDOG_BATCH_SIZE` 仍保持最高优先级，不被 DB/default min
  seed 抬高。
- failure reason/redaction：normalization failure log 输出固定 reason code + error class，不输出
  raw metadata 或异常 message。
- focused evidence：`.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks`
  通过，69 tests；live normalize smoke、K8S rollout、全量测试按 `UTB-02816-P1P2-01` 后置。
