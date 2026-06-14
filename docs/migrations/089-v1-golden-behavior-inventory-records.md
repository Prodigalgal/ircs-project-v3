# 089 V1 golden behavior inventory migration records

来源：`089-v1-golden-behavior-inventory.md` 拆分。

## 本次记录

```text
1. 已按 V1 src/main/java 包路径生成第一轮模块库存。
2. 已把聚合只读审计结果合并为 P0/P1 aggregation gap。
3. 已把依赖/初始化器只读审计结果合并为 P0/P1 dependency/runtime gap。
4. 已把清洗/标准化只读审计结果合并为 canonical rawMetadata、HanLP/OpenCC、标题
   pipeline、description NER、raw 字典策略、metadata result 深层合并等缺口。
5. 已明确 HTTP route missing=0 不再代表行为迁移完成。
6. 已把所有第一轮 missing/partial/needs-evidence 项挂到 028-16 或既有 028 子任务。
7. 已完成 `028-16-C3` 出站 HTTP caller inventory：V1 `AppHttpClientFactoryImpl`、
   `HttpServiceImpl`、`SecureFileDownloaderImpl`、`SsrfValidator`、`UserAgentUtils`
   行为已摘要，V3 `RestClient`/JDK `HttpClient` caller 已按 P0/P1 分类。
8. 已为 shared outbound contract 设计 `PUBLIC_FETCH`、`IMAGE_DOWNLOAD_STRICT`、
   `INTERNAL_SERVICE`、`BFF_PROXY` 四类 policy，并建议第一刀从
   `ircs-catalog-service/CatalogFetchSampleClient` 的 fake-first contract test 开始。
9. 已完成 `028-16-C1` 初始化 readiness gate 只读矩阵：覆盖 SystemConfig、Language、
   Area、Category、StandardGenre、DataSource、Credential、Task、Cache、HanLP、Resolver。
10. 已确认 V3 现状：config-service 已有 runtime SystemConfig defaults；catalog/content/
    credential/task 仍缺默认 dictionary/resolver/credential/task seed runner；多个 worker
    已用 `APP_*_LISTENER_ENABLED:false` 阻止 listener 默认启动，但缺自动 readiness 放行。
11. 已完成 `028-16-C2` 配置优先级第二刀：V1 风格初始化只作为最低优先级默认 seed；
    运行时读取固定为 ENV/K8S Secret/启动参数等环境注入优先，其次 `system_configs` DB，
    最后默认值。identity/interaction 已补 env-first 读取测试。
12. 已完成 `028-16-C2` 配置优先级可见化补强：config-service API 摘要新增
    `effectiveSource` / `effectiveValue`，来源可区分 `INJECTED`、`DB`、`DEFAULT`；
    敏感 key 的 DB 值与 effective value 均不回显，且 `security.jwt.secret` 等 V1 key
    会识别 `app.identity.jwt.secret` 等 V3 runtime alias。
13. 已完成 `028-16-C2` config change event 第一刀：新增 `SystemConfigChangedEvent`，
    config-service create/update/delete 在事务提交后 best-effort 发布 JSON payload 到
    `x.domain.events` / `config.system.changed`；payload 不携带配置值，K8S dev manifest 已补
    RabbitMQ env。
14. 已完成 `028-16-C1-S1` catalog 默认字典 seed 第一刀：catalog-service 启动时幂等补
    V1 8 类 category、V1 全量 `STANDARD_GENRES`、P0 language/area 子集和 raw alias；
    data source/default_mapping 明确拆到后续 C1-S2。
15. 已完成 `028-16-C1-S2` data source preset/default_mapping 第一刀：catalog-service
    启动时幂等补 V1 25 个资源站配置、默认 list/detail params、`default_mapping.json`，
    并复刻 `subTitle` -> `aliasTitle` 与 `aliasTitle`/`totalEpisodes`/`duration`
    mapping patch；V1 启动时远程分类同步已明确拆到后续可控 job。
16. 已完成 `028-16-C1-S2b` remote category sync 第一刀：catalog-service 已具备显式
    远程 `class[]` 分类同步 service、V1 category inference、raw_category 幂等 upsert 和
    用户 `category_id` 不覆盖测试；该逻辑未挂启动自动外联。
16a. 已完成 `028-16-C1-S2c` readiness/initialization remaining first closure 实现：
    catalog-service 复用 V1 `presets/countries.json` / `presets/languages.json` 全量
    preset（251 countries、187 languages），默认 seed 仍只补缺失项/空字段，不覆盖用户
    编辑值；normalization-worker SystemConfig cache 增加默认 `30s` TTL missed-event
    fallback；新增 resolver/真实资源站 live smoke prep 脚本，默认 dry-run 不发 HTTP。
17. 已完成 `028-16-C1-S3` notification listener start gate 第一刀：
    notification-worker mail listener 现在默认 `autoStartup=false`，通过
    `APP_NOTIFICATION_LISTENER_ENABLED` 显式开启，测试固定该表达式。
18. 已完成 `028-16-C1-S4` task-service default collection task seed 第一刀：
    task-service 会按每个 `data_sources` 幂等派生 `自动采集 - {name}` 与
    `全量采集 - {name}`，复刻 V1 `BY_PAGE`、`Asia/Shanghai`、`startPage=1`、
    `endPage=0`、随机延迟 `3000..8000ms`、`timeoutMs=15000`、`maxRetries=3`
    和 `enableRandomUa=true`；同名任务存在时跳过不覆盖。dev manifest 已显式保持
    `APP_TASK_SCHEDULER_ENABLED=false`，默认 seed 但不自动 cron 抓取。
19. 已完成 `028-16-C1-S5` content-service resolver preset seed 第一刀：
    content-service 启动时按 V1 `ResolverInitializer` 幂等补齐 `金蝉解析`、
    `789解析`、`火花解析`、`麒麟解析` 四个默认解析源，每个 resolver 写入
    `isActive=true`、V1 remark 和一条 `默认线路`；同名 resolver 存在时跳过不覆盖。
    dev manifest 已显式配置 `APP_CONTENT_RESOLVER_PRESET_SEED_ENABLED=true`。
20. 已完成 `028-16-C1-S6` credential-service runtime initializer 第一刀：
    credential-service 启动时按 V1 `CredentialInitializer` 从 ENV 导入 `OPENAI`、`TMDB`、
    `MAIL`、`R2`，并从 `/etc/ircs/init/credentials.json` 或 classpath
    `presets/credentials.json` 导入文件凭据；导入复用 provider catalog 校验和 fingerprint，
    重复 fingerprint 跳过不覆盖，日志和文档不记录 payload 值。
21. 已完成 `028-16-C1-S7` credential summary redaction 第一刀：admin/public
    `CredentialSummary.payload` 不再回显原始 secret 值，只返回每个 payload key 的
    `present=true`、`redacted=true` metadata；internal lease 仍通过 token 保护路径返回真实
    secret payload。
22. 已完成 `028-16-C1-S8` data-source category sync trigger 第一刀：catalog-service
    `createDataSource()`、`updateDataSource()` 和 `patchDataSource()` 成功保存后，会按 V1
    `DataSourceServiceImpl.create/update/partialUpdate` 语义 afterCommit best-effort 触发
    `CatalogRemoteCategorySyncService`；同步失败只记录，不影响 data source 写接口响应。
23. 已完成 `028-16-C2` config change event P0 消费者第一刀：identity-service 和
    interaction-service 增加本地 config cache、独立广播消费队列和按 key evict；identity
    admin 改密直接写 `system_configs` 后也会 afterCommit best-effort 发布
    `SystemConfigChangedEvent`，避免绕过 config-service publisher。
24. 已完成 `028-16-C3-S1` shared outbound HTTP 第一刀：`shared:ircs-common` 新增
    `PUBLIC_FETCH` fake-first contract，覆盖 SSRF fail-closed、redirect never、默认 UA、
    429/5xx/IO retry 和 gzip 解压；`CatalogFetchSampleClient` 与
    `YtsBzMagnetProviderSearchRunner` 默认 adapter 已接入 shared outbound client。
25. 已完成 `028-16-C3-S2` scraper shared outbound HTTP 第二刀：`shared:ircs-common`
    新增 proxy policy 与 V1 风格受控 UA 池；`ListScraperClient` 已接入 shared
    `PUBLIC_FETCH`，保留 custom header、custom proxy/auth、显式 UA 和 `enableRandomUa`，
    并用 fake resolver/transport 测试证明 private datasource URL 在 transport 前被阻断。
26. 已完成 `028-16-C3-S3` metadata TMDB shared outbound HTTP 第三刀：
    `RestClientTmdbHttpClient` 已接入 shared `PUBLIC_FETCH`，新增独立
    `app.metadata.tmdb.request-timeout`，保留 TMDB 2xx/404/429/5xx/4xx 业务分类，并用
    fake resolver/transport 覆盖 private URL 阻断、retry、gzip 和 redirect never。
27. 已完成 `028-16-D1` storage Tika MIME 第一刀：storage-service 仅接入
    `tika-core:3.2.2`，`ImageSecurityValidator` 使用 `Tika.detect(byte[])`，保留 V1
    allowlist、10MB、filename blacklist 和 declared MIME 不参与放行语义；ICO canonical
    MIME 窄归一化到 `image/x-icon`，fixture 覆盖有效图片、spoof/non-image、空文件、超
    10MB 和非法文件名。
28. 已按用户确认补强 `028-16-C2` 配置优先级防回退测试：V1 风格 config 初始化可以保留，
    但只作为最低优先级兜底；ENV、K8S Secret、启动参数和 `Environment`/`@Value` 注入
    是最高优先级，create/update 摘要与事件来源均按 `INJECTED` 生效；仓储级测试已锁定
    `upsertDefault()` 冲突分支不覆盖 `config_value`。
29. 已完成 `028-16-D2` scraper charset/streaming 第一刀：scraper-service 仅接入
    `tika-core:3.2.2` 与 `tika-parser-text-module:3.2.2`，未接全量 parser standard
    package；`ListScraperClient.fetchList()` 已从 `bodyAsUtf8()` + `JsonPath.read()` 改为
    raw bytes charset detect + Jackson streaming parser，并覆盖 GB18030、UTF-8、nested
    item、numeric raw value 和 malformed non-JSON fixture。
30. 已根据 aggregation 只读盘点新增 `028-16-B2 duplicate unified merge victim delete`
    task spec；下一刀固定为 root/victim 收敛、raw binding 迁移、favorite/history 冲突清理、
    victim unified delete 和 search DELETE side effect，不把 relation union/JGraphT raw/raw
    cluster 混入同一切片。
31. 已完成 `028-16-B2` 第一刀：aggregation-worker 现在可形成 root/victim merge plan，
    迁移 victim raw bindings，清理 `member_favorites` / `member_watch_histories` 的 root
    冲突，删除 victim scoped relations 与 `unified_videos` victim row，并向 search unified
    queue 发布结构化 `UNIFIED_VIDEO DELETE`。victim relation union 和 victim scalar merge 后续
    已由 B3/B4 补上；完整 JGraphT raw/raw cluster 和 K8S live smoke 继续后移。
32. 已完成 `028-16-B3` 第一刀：duplicate victim 删除前，先将 victim unified 侧
    `actors/directors/genres/standard_languages/standard_areas` 通过 `insert ... select ...
    on conflict do nothing` union 到 root，避免 B2 删除 victim 时丢失 V1
    `UnifiedVideo.merge(victim)` 的 relation `addAll` 语义。raw-source AggregationPipeline、
    category 投票、cover 优选和 live smoke 继续后移。
33. 已完成 `028-16-B4` 第一刀：duplicate victim 删除前，root 会在自身字段为空时补 victim
    的 external IDs、description、score、published_at、alias_title、total_episodes、
    duration、remarks、season、subtitle，并保留更新的 `last_trend_at`；root 已有非空字段不被
    victim 覆盖。raw-source AggregationPipeline、category 投票、cover 优选和 live smoke 继续后移。
34. 已完成 `028-16-B5` 第一刀：raw 绑定 unified 后，aggregation-worker 会基于同一 unified
    下全部 raw sources 重建 V1 `CategoryHandler` 与 `MetadataHandler` 的核心输出：
    category 投票写入 `unified_videos.category_id`，并从 raw `video_actors`、
    `video_directors`、`video_raw_genres`、`video_raw_languages`、`video_raw_areas`
    重建 unified `actors/directors/genres/languages/areas`；`locked_fields` 命中时不清空、
    不覆盖。`BasicAttributeHandler`、`ExternalIdHandler`、`DynamicFieldHandler`、
    `CoverImagePipelineHandler`、handler order/failure isolation、JGraphT raw/raw cluster
    和 live smoke 继续后移。
35. 已完成 `028-16-B6` 第一刀：raw 绑定 unified 后，aggregation-worker 会基于同一 unified
    下全部 raw sources 重建 V1 `BasicAttributeHandler`、`ExternalIdHandler` 和
    `DynamicFieldHandler` 的标量输出：title/year 投票、score 最大、published_at 首个非空、
    external IDs 填空且不受锁保护、alias/description/subtitle 最长、remarks 最新、
    total_episodes/duration/season 首个非空；`locked_fields` 命中时保护 Basic/Dynamic
    字段。`CoverImagePipelineHandler`、handler order/failure isolation、JGraphT raw/raw
    cluster 和 live smoke 继续后移。
36. 已完成 `028-16-B7` 第一刀：raw 绑定 unified 后，aggregation-worker 会基于当前 unified
    cover 与同一 unified 下全部 raw sources 重建 V1 `CoverImagePipelineHandler` 的核心选择
    输出：空 cover 采用第一个 raw cover，managed `LOCAL`/`R2` 优先于 `EXTERNAL`，同类
    managedness 只有 `file_size > current * 1.1` 才替换，`file_size` null 按 0 处理；
    `coverImageUrl` locked field 不阻止该 handler。真实 R2 promote side effect、handler
    order/failure isolation、JGraphT raw/raw cluster 和 live smoke 继续后移。
37. 已完成 `028-16-B8` 第一刀：新增 `PROMOTE_COVER_R2` storage command，aggregation-worker
    在 B7 cover pipeline 选中 `LOCAL` + `LOCAL_STORED` cover 时发布 UUID command；
    storage-service 消费后按 V1 `promoteToR2()` 语义只安排 due upload，即 R2 active、
    cover eligible 且 `next_retry_time is null` 时写入 `next_retry_time=now()` 与
    `last_error=null`，真实 R2 upload 仍交给 `CoverImageR2SyncService` /
    `StorageR2Watchdog`。
38. 已完成 `028-16-B9` 第一刀：aggregation-worker service scope 接入
    `org.jgrapht:jgrapht-core:1.5.2`，新增 same-batch raw/raw graph cluster。该切片按
    V1 `GraphClusteringEngine` 的 edge `0.85`、consistency `0.90`、有效年份差 `<= 1`
    与未知年份桶隔离规则成簇；multi-raw cluster 会创建或复用一个 unified，把 cluster 内
    raw 全部 bind 到该 unified，并在所有 bind 后运行一次 B5-B8 pipeline/search/R2 promote
    副作用。context unified 混合图、完整 graph scoring、handler runtime 和 K8S live
    smoke 继续后移。
39. 已完成 `028-16-B10` 第一刀：aggregation-worker 在 B9 graph 基础上加入 context
    unified nodes。context external recall 按 V1 `loadContextAccelerated()` 只使用
    douban/tmdb；raw 缺少有效 douban/tmdb 时才退到 title/year recall。mixed graph 中
    raw/raw 与 raw/unified 可建边，unified/unified 不建边，纯 unified component 跳过；
    cluster 可复用一个 context unified，也可将多个 context unified 作为 root/victim 走
    B2-B4 merge path。真实 ES-backed context recall、category bucket、actor/director
    Jaccard、episode/area penalty、handler runtime 和 K8S live smoke 当时继续后移；
    后续 B11 已补 category bucket 第一刀，B12 已补 metadata scoring/penalty 第一刀。
40. 已按用户确认再次加固 `028-16-C2` 配置优先级：`SystemConfigDefaults.injectionKeys()`
    现在同时暴露 property key、V3 alias 和 `APP_*` 大写下划线 env name；`ConfigService`
    摘要与 MAIL SSL/STARTTLS 校验共用这套 env-first 解析。新增测试覆盖
    `APP_MAIL_ENABLED`、`APP_IDENTITY_JWT_SECRET` 和 `APP_MAIL_PROPERTIES_SSL` 覆盖 DB
    管理值，V1 默认 seed 仍保持最低优先级。
41. 已继续加固 `028-16-C2` P0 消费者读取模型：identity-service 和 interaction-service
    的直连配置读取只把 commandLineArgs、systemProperties、systemEnvironment、
    `SPRING_APPLICATION_JSON` 和测试显式注入 source 视为最高优先级运行时覆盖源；
    `application.yaml` 静态默认值不会覆盖 `system_configs` DB。新增测试覆盖
    `APP_IDENTITY_JWT_SECRET`、`SECURITY_ADMIN_PASSWORD`、`MEMBER_MESSAGE_DAILY_LIMIT`
    和 YAML-default guard。
42. 已完成 `028-16-B11` 第一刀：aggregation-worker 在 B9/B10 graph 基础上补回 V1
    `GraphClusteringEngine.buildGraphOptimized()` 的 category bucket。raw candidate 从
    `raw_category -> standard_category` 暴露标准分类名，unified candidate 从
    `unified_videos.category_id -> standard_category` 暴露标准分类名；建边前按
    `categoryName.trim()` 分桶，空/blank 分类归入 `UNKNOWN_CATEGORY`。raw/raw 与
    raw/unified edge 均不会跨具体分类，`UNKNOWN_CATEGORY` 只和 `UNKNOWN_CATEGORY`
    比较。actor/director Jaccard、episode/area penalty 后续已由 B12 补第一刀；真实
    ES-backed context recall、handler runtime 和 K8S live smoke 继续后移。
43. 已完成 `028-16-B12` 第一刀：aggregation-worker 在 B9-B11 graph 基础上补回 V1
    `DefaultWeightedMatchingStrategy` 的 metadata scoring 与 penalty。candidate 主查询后
    按 V1 `populateMetadata()` 语义补 actor/director/standard area names；无 metadata 时
    保持 `titleScore * 0.95`，双方都有 metadata 时使用
    `titleScore * 0.70 + actor/director Jaccard * 0.30`，episode 不兼容扣 `0.15`，
    双方 area 都存在且不 contains 匹配扣 `0.10`。真实 ES-backed context recall、handler
    runtime 和 K8S live smoke 继续后移。
44. 已完成 `028-16-C2` config-service runtime injection source guard：config-service 自身
    新增 `RuntimeInjectedConfig`，summary `effectiveSource`、config change event 来源和 MAIL
    SSL/STARTTLS 冲突校验均按真正运行时覆盖源 -> DB -> 默认 seed 计算；classpath
    `application.yaml` 静态默认值不再被误判为 `INJECTED`。新增测试覆盖 YAML-default
    不覆盖 DB、不误触发 MAIL 冲突；config/identity/interaction 组合回归通过。
45. 已完成 `028-16-C2` K8S Secret priority guard 补强：config-service 已新增
    `Kubernetes Secret ...` property source 回归测试，证明 Secret/ConfigMap/config tree
    这类运行时注入源高于 `system_configs` DB 和 V1 默认 seed；本轮
    `ConfigServiceTest` 为 23 个用例且全部通过。
46. 已完成 `028-16-B13` 第一刀：search-service 新增内部只读 UUID context recall API，
    `PortalSearchQueryService.findCandidateUnifiedVideoIds(title, year)` 复刻 V1 ES candidate
    query 语义；aggregation-worker 对无有效 douban/tmdb 的 raw 通过 search-service ES
    recall 获取 UUID，并回 PostgreSQL 装载 `UnifiedVideoAggregationCandidate`。ES 空结果或
    异常默认不触发 SQL title fallback，只有显式关闭 ES recall 时才走旧 SQL fallback。
47. 已完成 `028-16-B14` 第一刀：aggregation-worker 新增
    `AggregationPipelineRuntime`，按 V1 handler 顺序执行 BasicAttribute(10)、
    ExternalId(20)、Metadata(30)、DynamicField(40)、Category(50)、CoverImage(60)；
    `JdbcAggregationRepository` 拆出 handler 粒度 rebuild 方法并保留 grouped wrapper。
    runtime 单 stage 异常只记录 failure 并继续后续 stage；cover stage 成功时才透传
    R2 promote cover IDs，single raw 与 multi-raw cluster bind 均已切到 runtime。
48. 已完成 `028-16-A4` 第一刀：normalization-worker 新增 V1 风格 `HanLpPrewarmRunner`
    和 `DescriptionMetadataExtractor`；prewarm 预热 `HanLP.segment()`、简繁转换和
    `CharTable.convert()`，description NER 复刻 V1 导演/主演关键词、窗口和 `nr*`
    人名规则。`RawVideoTextNormalizer` 只在显式 actor/director metadata 为空时 fallback，
    locked actors/directors 继续压制关系写入。normalization-worker 单测和 dev manifest
    server dry-run 均通过。
49. 已完成 `028-16-C2-S4` 第一刀：config-service `SystemConfigDefaults` 覆盖 V1
    `SystemConfigKey` 全量 133 个 key，diff 为 `V1=133 V3=133 Missing=0 Extra=0`。
    `security.admin.password` 与 `security.jwt.secret` 继续随机生成；R2 bucket/public-domain
    已标记 required；LLM prompt 默认值补回 raw-preservation 和 `{standardItems}` /
    `{rawItems}` 占位符。既有 ENV/K8S/启动参数 -> DB -> default seed 优先级不变。
50. 已完成 `028-16-C2-S5` 第一刀：search-service `CoverImageUrlResolver` 复刻 V1
    `StorageUrlHelper` 的 cover URL 配置读取。R2 cover 读取
    `app.storage.r2.public-domain`，兼容 V3 alias `app.storage.r2-public-domain`；
    LOCAL/LOCAL_STORED cover 读取 `app.storage.public-path` 拼接 `/media/...`。读取顺序为
    运行时注入 -> `system_configs` DB -> V1 default，并新增 `q.search.config_changed`
    按 key evict。
51. 已完成 `028-16-C2-S6` 第一刀：interaction-service `CoverImageUrlResolver` 复刻 V1
    interaction 收藏/历史 `StorageUrlHelper.resolveUrl()` 的 cover URL 配置读取。R2 cover
    读取 `app.storage.r2.public-domain`，兼容 V3 alias `app.storage.r2-public-domain`；
    LOCAL/LOCAL_STORED cover 读取 `app.storage.public-path` 拼接 `/media/...`。读取顺序为
    运行时注入 -> `system_configs` DB -> V1 default，并复用既有
    `q.interaction.config_changed` 按 key evict。
52. 已完成 `028-16-C2-S7` 第一刀：portal-service `PortalCoverImageUrlResolver` 复刻 V1
    portal 首页、分类、探索和详情卡片 `StorageUrlHelper.resolveUrl()` 的 cover URL 配置读取。
    R2 cover 读取 `app.storage.r2.public-domain`，兼容 V3 alias
    `app.storage.r2-public-domain`；LOCAL/LOCAL_STORED cover 读取 `app.storage.public-path`
    拼接 `/media/...`。读取顺序为运行时注入 -> `system_configs` DB -> V1 default，
    并新增 `q.portal.config_changed` 按 key evict。
53. 已完成 `028-16-C2-S8` 第一刀：storage-service 新增 service-local config cache/event
    listener，并将 cover 管理 URL、头像上传响应、头像 R2 同步 URL 改写和 watchdog 本地头像
    扫描前缀迁到运行时注入 -> `system_configs` DB -> V1 default。覆盖
    `app.storage.public-path`、`app.storage.r2.public-domain` 和
    `app.storage.path.prefix.avatar`，兼容 V3 alias `app.storage.r2-public-domain` 与
    `app.storage.avatar-path-prefix`。
54. 已完成 `028-16-C2-S9` 第一刀：normalization-worker 新增 service-local config cache/event
    listener，并将 normalize failure retry 的 max retries/backoff base seconds 以及 retry
    watchdog 的 batch size/pending timeout 迁到运行时注入 -> `system_configs` DB -> V1 default。
    覆盖 V1 canonical key `normalization.max-retries`、`normalization.backoff.base-seconds`、
    `normalization.batch-size` 和 `normalization.timeout.pending-min`，同时兼容 V3
    `APP_NORMALIZATION_*` alias；dev K8S 用 `APP_NORMALIZATION_WATCHDOG_BATCH_SIZE=25`
    保持低资源覆盖。
55. 已完成 `028-16-C2-S10` 第一刀：aggregation-worker 新增 service-local config cache/event
    listener，并将 scheduler batch size 迁到运行时注入 -> `system_configs` DB -> V1 default。
    覆盖 V1 canonical key `aggregation.batch-size`，默认值恢复为 `200`，兼容 V3 alias
    `app.aggregation.batch-size` / `APP_AGGREGATION_BATCH_SIZE`；dev K8S 用
    `APP_AGGREGATION_BATCH_SIZE=1` 保持低资源覆盖。
56. 已完成 `028-16-C2-S11` 第一刀：notification-worker 新增 service-local config cache/event
    listener，并将 MAIL enable/from 和 SMTP connect options 迁到运行时注入 ->
    `system_configs` DB -> V1 default。覆盖 V1 canonical keys `app.mail.enabled`、
    `app.mail.from`、`app.mail.host`、`app.mail.port`、`app.mail.protocol`、
    `app.mail.properties.auth`、`app.mail.properties.starttls`、`app.mail.properties.ssl`、
    `app.mail.timeout` 和 `app.mail.debug`，同时兼容 V3/Spring Mail alias；dev K8S 用
    `APP_MAIL_ENABLED=false` 保持无真实 SMTP side effect。
57. 已完成 `028-16-C2-S12` 第一刀：metadata-worker 新增 service-local config cache/event
    listener，并将 metadata 全局 enable、Douban/TMDB/RT provider enable 和 pending rescue TTL
    迁到运行时注入 -> `system_configs` DB -> V1 default。覆盖 V1 canonical keys
    `app.metadata.enabled`、`app.metadata.tmdb.enabled`、`app.metadata.douban.enabled`、
    `app.metadata.rotten-tomatoes.enabled` 和 `app.metadata.timeout.pending-min`，同时兼容
    V3/Spring alias；dev K8S 用 provider enable env false 保持无真实 provider side effect，
    且 `APP_METADATA_LISTENER_ENABLED` 不再被误用为业务全局开关。
58. 已完成 `028-16-C2-S13` 第一刀：ops-service 新增 service-local config cache/event
    listener，并将 dev-safe `search-reindex-unified` runner limit 迁到运行时注入 ->
    `system_configs` DB -> V3 dev-safe fallback。覆盖 V3 runtime/DB key
    `app.ops.maintenance.reindex.dev-limit` 与 env alias `OPS_MAINTENANCE_REINDEX_DEV_LIMIT` /
    `APP_OPS_MAINTENANCE_REINDEX_DEV_LIMIT`；dev K8S 保持 limit=5。该切片不把
    V3-only key 加入 V1 133-key catalog，也不声称完成 V1 full maintenance reindex。
59. 已完成 `028-16-C1-S9` 第一刀：task-service `TaskWatchdog` 增加
    `APP_TASK_WATCHDOG_ENABLED:false` 默认关闭 guard，缺省时不创建 watchdog bean，显式开启
    后才扫描 stale active tasks；本切片不等价于 V1 全局 readiness orchestrator，剩余
    scheduler guard 继续保留。
60. 已完成 `028-16-C2-S14` 第一刀：content-service `InternalContentClients` 的
    `app.content.internal.scraper-base-url` 改为调用期读取
    `RuntimeInjectedConfig -> system_configs DB -> V3 fallback`，新增 `q.content.config_changed`
    和 `APP_CONTENT_CONFIG_LISTENER_ENABLED`；resolver seed gate、audit 开关和 media URL fallback
    仍按启动绑定或后续独立切片记录。
61. 已完成 `028-16-C3-S4a` 第一刀：shared outbound policy type 增加
    `PUBLIC_FETCH`、`INTERNAL_SERVICE`、`BFF_PROXY`，metadata-worker
    `TmdbCredentialRepository` 迁到 shared `INTERNAL_SERVICE`，保留内部 token、固定 lease
    path/query、空凭据过滤和 `CREDENTIAL_SERVICE_UNAVAILABLE` retryable 映射。
62. 已完成 `028-16-A5` 第一刀：normalization-worker `RawRelationAliasPolicy` 对
    area/language 复合或后缀 token 追加可命中 alias token，例如 `港台`、`日韩`、`日韩欧美`、
    `日本地区`、`国粤双语` 和 `日语中字`；保留原始 raw token，`locked_fields` 继续压制
    对应 relation。
63. 已完成 `028-16-C3-S9` BFF shared transport method/body/streaming adoption：
    shared 新增 streaming outbound client/transport，admin/portal BFF proxy 改走
    `BFF_PROXY` streaming adapter，保留 method/body/header/status/streaming 语义，并补
    `APP_BFF_PROXY_*` timeout/circuit/caller-key ENV 覆盖与 `IRCS_OUTBOUND_CIRCUIT_*`
    fallback；focused no-real-network tests 通过，live/full tests 后置到
    `UTB-02816-P1P2-02`。
64. 已完成 `028-16-F2/B15` aggregation local implementation closure：aggregation-worker
    search sync producer 统一发布结构化 raw/unified INDEX 与 unified DELETE；search-service
    对结构化 sync payload 做字段完整性校验，缺字段 no-requeue reject；pipeline stage failure
    后 raw 回到 PENDING、unified 保持 DIRTY，root INDEX/R2 promote side effect 被抑制，
    duplicate victim DELETE 仍保底发布；`recalculateUnified()` 仅在所有 raw 成功后才
    SYNCED + unified INDEX。focused aggregation/search sync tests 通过，live/R2/full tests 后置。
65. 已完成 `028-16-C3-S10` caller-specific circuit ENV matrix implementation：shared outbound
    新增 caller-aware circuit config 解析，优先级为 caller-specific ENV/K8S -> global ENV/K8S
    -> caller-specific JVM property -> global JVM property -> default；content/task/identity
    adopted callers 使用 `withCallerCircuitBreakerKey`，admin/portal BFF 保留
    `APP_BFF_PROXY_CIRCUIT_*` service-level 高优先级并回落到
    `IRCS_OUTBOUND_CIRCUIT_ADMIN_BFF_PROXY_*` / `IRCS_OUTBOUND_CIRCUIT_PORTAL_BFF_PROXY_*`。
    application.yaml 与 dev manifests 已暴露 5 个 caller 默认矩阵；shared 定向测试和 5 个
    service `compileJava` 通过，live/K8S/full tests 后置到 `UTB-02816-P1P2-02`。
```

