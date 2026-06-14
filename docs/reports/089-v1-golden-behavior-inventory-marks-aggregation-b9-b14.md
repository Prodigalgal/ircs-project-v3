# 089 V1 golden behavior inventory - aggregation B9-B14 MARK detail

来源：`089-v1-golden-behavior-inventory-marks-aggregation-b1.md` 拆分。

2026-06-09 已完成 `028-16-B9 JGraphT raw/raw cluster first slice`：aggregation-worker
service scope 接入 `org.jgrapht:jgrapht-core:1.5.2`，新增 `RawVideoGraphClusterer`。
同一批 claimed raw 会先按 V1 风格 edge `0.85` 建图、`ConnectivityInspector` 连通分量和
consistency `0.90` star peeling 成簇；有效年份差 `<= 1` 可比较，未知年份只和未知年份比较。
multi-raw cluster 创建或复用一个 unified，将 cluster 内 raw 全部 bind 到该 unified，并在
所有 bind 后只运行一次 scalars、relations、cover pipeline 与 cover R2 promote side effect。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

后续 B10/B11/B12/B13 已分别承接 raw/unified context graph、category bucket、metadata
scoring/penalty 与 ES-backed context recall 第一刀；当前仍需拆分承接 handler order/failure
isolation 和 K8S live smoke；真实 R2 upload live smoke 后续仍需在 dev R2
配置明确后执行。

2026-06-09 已完成 `028-16-B10 raw/unified context graph first slice`：aggregation-worker
在 B9 graph 基础上加入 context unified nodes。context recall 按 V1
`loadContextAccelerated()` 收窄为 douban/tmdb external ID；raw 缺少有效 douban/tmdb 时才走
title/year SQL recall；imdb/rottenTomatoes 仍保留在 matching 阶段参与 match/conflict。
mixed graph 中 raw/raw 与 raw/unified 可建边，unified/unified 不建边，纯 unified component
跳过；cluster 可复用一个 context unified，也可将多个 context unified 作为 root/victim 走
B2-B4 merge path。B5-B8 pipeline/search/R2 promote side effect 保持原顺序。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

后续 B11/B12/B13/B14 已分别承接 category bucket、metadata scoring/penalty、ES-backed
context recall 与 pipeline runtime 第一刀；当前仍需拆分承接 K8S live smoke。

2026-06-09 已完成 `028-16-B11 category bucket graph first slice`：aggregation-worker
在 B9/B10 graph 基础上补回 V1 `GraphClusteringEngine.buildGraphOptimized()` 的第一层
category bucket。raw candidate 的 categoryName 来源为 `raw_category -> standard_category`，
unified candidate 的 categoryName 来源为 `unified_videos.category_id -> standard_category`；
graph 建边前按 `categoryName.trim()` 分桶，空/blank 分类归入 `UNKNOWN_CATEGORY`。raw/raw
和 raw/unified edge 均不会跨具体分类，`UNKNOWN_CATEGORY` 只和 `UNKNOWN_CATEGORY` 比较。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

后续 B12/B13/B14 已承接 actor/director Jaccard、episode/area penalty、ES-backed context
recall 与 pipeline runtime 第一刀；当前仍需拆分承接 K8S live smoke。

2026-06-09 已完成 `028-16-B12 graph scoring metadata penalty first slice`：aggregation-worker
在 B9-B11 graph 基础上补回 V1 `DefaultWeightedMatchingStrategy` 的 metadata scoring 与
penalty。candidate 主查询后按 V1 `populateMetadata()` 语义补 actor/director/standard
area names；无 metadata 时保持 `titleScore * 0.95`，双方都有 metadata 时使用
`titleScore * 0.70 + actor/director Jaccard * 0.30`，episode 不兼容扣 `0.15`，双方 area
都存在且不 contains 匹配扣 `0.10`。同标题但 actor/director 不重叠、episode 不兼容或
raw/unified context metadata 不一致时，不再误建 graph edge。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09 已完成 `028-16-B13 ES-backed context recall first slice`：search-service
新增内部只读 `GET /internal/v1/search/unified-context-candidates`，只返回 UUID list；candidate
query 复刻 V1 `multi_match best_fields`、`AND`、`minimum_should_match 90%`、字段权重、
year `+-2` range 和 limit 20。aggregation-worker 对无有效 douban/tmdb 的 raw 通过
search-service ES recall 拿 UUID，再回 PostgreSQL 装载 context candidate；ES 空结果或异常
默认不退 SQL title fallback，只有显式关闭 ES recall 时才使用旧 SQL fallback 作为开发兜底。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-search-service:test :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL
```

后续 B14 已承接 pipeline handler order/failure isolation runtime 第一刀；当前仍需拆分承接
K8S live smoke。

2026-06-09 已完成 `028-16-B14 pipeline runtime order/failure isolation first slice`：
aggregation-worker 新增 `AggregationPipelineRuntime`，按 V1 handler 顺序执行
`BasicAttribute(10)`、`ExternalId(20)`、`Metadata(30)`、`DynamicField(40)`、
`Category(50)`、`CoverImage(60)`。`JdbcAggregationRepository` 拆出 handler 粒度 rebuild
方法并保留旧 grouped wrapper；runtime 对单 stage 异常做失败隔离并继续后续 stage，
cover stage 成功时才返回 R2 promote cover IDs。`AggregationService` 的 single raw 和
multi-raw cluster bind 流程均已切换到 runtime。

2026-06-09 随后补强 B14 事务语义：每个 pipeline stage 已通过 `TransactionTemplate` +
`PROPAGATION_NESTED` 运行在独立 JDBC savepoint 中，避免某个 stage SQL 抛错后 PostgreSQL
事务进入 failed transaction block 而阻断后续 stage 或 `markBound()`。未使用 `REQUIRES_NEW`，
以免 dev Hikari 小连接池额外占连接。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-search-service:test :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL
```

当前聚合 B 线仍需拆分承接 K8S live smoke。

2026-06-09 已完成 `028-16-C2 config-service runtime injection source guard`：config-service
新增 service-local `RuntimeInjectedConfig`，summary `effectiveSource`、config change event
来源和 MAIL SSL/STARTTLS 冲突校验均按真正运行时覆盖源 -> DB -> 默认 seed 计算；classpath
`application.yaml` 静态默认值不再被误判为 `INJECTED`，因此 V1 默认 seed / 静态 fallback
始终低于 DB，ENV/K8S Secret/启动参数仍保持最高优先级。

2026-06-09 随后补强 C2 K8S 外部注入 source 识别：config-service、identity-service 和
interaction-service 的 `RuntimeInjectedConfig` 已把 Spring Boot config tree、Spring Cloud
Kubernetes ConfigMap/Secret property source 识别为 `INJECTED`；config-service 已补
`Kubernetes Secret ...` 显式回归测试，classpath `application.yaml` 仍明确保持低优先级静态默认值。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test --rerun-tasks
BUILD SUCCESSFUL

ConfigServiceTest: tests=23, failures=0, errors=0

.\gradlew.bat --no-daemon :services:ircs-config-service:test :services:ircs-identity-service:test :services:ircs-interaction-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09 已完成 `028-16-A4 description NER / HanLP prewarm` 第一刀：normalization-worker
新增 `HanLpPrewarmRunner`，按 V1 `HanLPInitializer` 预热 segment、简繁转换和 CharTable；
新增 `DescriptionMetadataExtractor`，复刻 V1 导演/主演关键词、窗口和 `nr*` 人名规则。
`RawVideoTextNormalizer` 只在显式 actor/director metadata 为空时使用 description NER，
且 `locked_fields` 中的 `actors` / `directors` 会继续压制对应关系写入。完整 V1 title
handler chain、area alias/composite/NLP 和 genre synonym/protected-term 策略仍留在 A 线后续。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL

DescriptionMetadataExtractorTest: tests=2, failures=0, errors=0
HanLpPrewarmRunnerTest: tests=2, failures=0, errors=0
RawVideoTextNormalizerTest: tests=9, failures=0, errors=0

kubectl apply --dry-run=server -f deploy\k8s\dev\normalization-worker-dev.yaml
deployment.apps/ircs-normalization-worker configured (server dry run)
```

2026-06-09 已完成 `028-16-A5 area/language composite alias policy` 第一刀：
normalization-worker 新增 service-scoped `RawRelationAliasPolicy`，对 `港台`、`港澳台`、
`日韩`、`日韩欧美`、`欧美`、`日本地区`、`国粤双语`、`国语粤语`、`粤语国语` 和
`日语中字` 等 V1 常见 P0 area/language 复合或后缀 token 追加可命中 raw alias /
standard exact mapping 的 raw token，同时保留原始 raw token 供追溯；`locked_fields.area`
和 `locked_fields.language` 仍完全压制对应 relation 输出。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL
36 tests, 0 failures
```

2026-06-09 已完成 `028-16-A7 title alias dedup policy` 第一刀：
`RawVideoTextNormalizer` 已把 alias 清洗收敛为以 normalized `title` 为参照的列表级去重：
规整 `rawMetadata.aliasTitle` / `rawMetadata.alias` / `rawMetadata.subTitle` 常见分隔符，
删除与主标题完全等价的 alias 项，删除 alias 内部重复项，并保留 first-seen 稳定顺序；
`locked_fields.aliasTitle` 仍继续压制 alias 清洗。当前仅覆盖完全等价项，不做 fuzzy alias
去重，完整 V1 title handler chain、season/subtitle/mainTitle 字段拆分和复杂 bracket
结构仍留在后续。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 18s
```

2026-06-09 已完成 `028-16-C2-S10 aggregation batch config parity`：
V1 aggregation scheduler batch size 通过 `AdaptiveWorkloadManager` 的静态批次路径读取
`SystemConfigKey.AGGREGATION_BATCH_SIZE`，canonical key 为 `aggregation.batch-size`，默认值
为 `200`。V3 aggregation-worker 已把 scheduler batch size 接入同一优先级：运行时注入 ->
`system_configs` DB -> V1 default。

本轮实现：

- aggregation-worker 新增本地 config cache、`q.aggregation.config_changed` 广播队列、DLQ
  绑定和 `APP_AGGREGATION_CONFIG_LISTENER_ENABLED` 开关。
- `AggregationScheduler.runBatch()` 每轮通过 `AggregationConfigValues.batchSize()` 读取
  `aggregation.batch-size`，兼容 V3 alias `app.aggregation.batch-size` /
  `APP_AGGREGATION_BATCH_SIZE`。
- dev manifest 继续用 `APP_AGGREGATION_BATCH_SIZE=1` 覆盖 V1 `200` 默认批次，保持低资源
  开发环境。
- V1 adaptive min/max、readiness/maintenance gate 和 live Rabbit smoke 明确留给后续独立切片。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\aggregation-worker-dev.yaml
deployment.apps/ircs-aggregation-worker configured (server dry run)
```

2026-06-09 已完成 `028-16-C2-S11 notification mail config parity`：
V1 mail 发送链路通过 `SystemConfigKey.MAIL_ENABLED` 控制启停，通过
`SystemConfigKey.MAIL_FROM` 控制发件人，并在 `MailCredentialHandler` 创建
`JavaMailSender` 时读取 `app.mail.host`、`app.mail.port`、`app.mail.protocol`、
`app.mail.properties.auth`、`app.mail.properties.starttls`、`app.mail.properties.ssl`、
`app.mail.timeout` 和 `app.mail.debug`。V3 notification-worker 已把这些 MAIL 配置接入
同一优先级：运行时注入 -> `system_configs` DB -> V1 default。

本轮实现：

- notification-worker 新增本地 config cache、`q.notification.config_changed` 广播队列、DLQ
  绑定和 `APP_NOTIFICATION_CONFIG_LISTENER_ENABLED` 开关。
- `SmtpNotificationMailService` 每次发送前动态读取 `app.mail.enabled` 和 `app.mail.from`，
  默认 from 恢复为 `HuaWai System <huawai.system@gmail.com>`，并兼容
  `app.notification.mail.enabled` / `app.notification.mail.from`。
- `SmtpMailDelivery` 每次 SMTP 发送时按当前配置和 MAIL credential lease 构建
  `JavaMailSender`，覆盖 host、port、protocol、auth、STARTTLS、SSL、timeout 和 debug。
- dev manifest 继续用 `APP_MAIL_ENABLED=false` 覆盖 V1 默认 true，避免开发环境误发真实邮件。
- V1 mail rate-limit、真实 SMTP live smoke 和 live Rabbit config event smoke 明确留给后续切片。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\notification-worker-dev.yaml
configmap/ircs-dev-app-config unchanged (server dry run)
deployment.apps/ircs-notification-worker configured (server dry run)
```

2026-06-09 已完成 `028-16-C1-S5 content-service resolver preset seed` 第一刀：
content-service 启动时会按 V1 `ResolverInitializer` 的四个默认解析源
`金蝉解析`、`789解析`、`火花解析`、`麒麟解析` 幂等补缺，每个 resolver 写入
`isActive=true`、V1 remark 和一条 `默认线路`。同名 resolver 存在时跳过，不覆盖用户编辑值；
`APP_CONTENT_RESOLVER_PRESET_SEED_ENABLED` 可显式关闭。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-content-service:test --rerun-tasks
BUILD SUCCESSFUL
32 tests completed

kubectl apply --dry-run=server -f deploy\k8s\dev\content-service-dev.yaml
deployment.apps/ircs-content-service configured (server dry run)
service/ircs-content-service unchanged (server dry run)
```

2026-06-09 已完成 `028-16-C1-S6 credential-service runtime initializer` 第一刀：
credential-service 启动时会按 V1 `CredentialInitializer` 从 ENV 导入 `OPENAI`、`TMDB`、
`MAIL`、`R2`，并从 `/etc/ircs/init/credentials.json` 或 classpath
`presets/credentials.json` 导入文件凭据。导入复用 V3 provider catalog 的校验和 fingerprint，
重复 fingerprint 跳过，不覆盖用户编辑值；日志和文档不记录 payload 值。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-credential-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\credential-service-dev.yaml
deployment.apps/ircs-credential-service configured (server dry run)
service/ircs-credential-service unchanged (server dry run)
```

2026-06-09 已完成 `028-16-C1-S8 data-source category sync trigger` 第一刀：
catalog-service `createDataSource()`、`updateDataSource()` 和 `patchDataSource()` 成功保存后，
在事务提交后 best-effort 触发 `CatalogRemoteCategorySyncService`，复刻 V1
`DataSourceServiceImpl` 写操作后同步远程 `class[]` 分类的副作用。同步失败只记录，不影响
data source 写入响应。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-catalog-service:test --rerun-tasks
BUILD SUCCESSFUL

 kubectl apply --dry-run=server -f deploy\k8s\dev\catalog-service-dev.yaml
deployment.apps/ircs-catalog-service configured (server dry run)
service/ircs-catalog-service unchanged (server dry run)
```

2026-06-09 已完成 `028-16-C1-S9 task-service watchdog start gate` 第一刀：
task-service `TaskWatchdog` 增加 `app.task.watchdog.enabled` 条件，`application.yaml` 和
dev manifest 均暴露 `APP_TASK_WATCHDOG_ENABLED:false`。默认缺失属性时不创建 watchdog bean，
需要 stale active task 自动扫描时必须显式开启。本切片不等价于 V1 全局 readiness
orchestrator；normalization retry watchdog、aggregation scheduler 和 storage R2 watchdog
已由 C1 full readiness / auto-start gate first closure 补齐默认关闭与 live gate smoke 证据。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-task-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\task-service-dev.yaml
deployment.apps/ircs-task-service configured (server dry run)
service/ircs-task-service unchanged (server dry run)
```

2026-06-09 已完成 `028-16-C1-S7 credential summary redaction` 第一刀：
admin/public `CredentialSummary.payload` 不再回显原始 secret 值，只返回每个 payload key 的
`present=true`、`redacted=true` metadata；`payloadKeys` 和 `fingerprintSuffix` 保持可用。
internal lease 仍通过 token 保护路径返回真实 secret payload。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-credential-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\credential-service-dev.yaml
deployment.apps/ircs-credential-service configured (server dry run)
service/ircs-credential-service unchanged (server dry run)
```

2026-06-09 已完成 `028-16-C2-S4 SystemConfigKey catalog parity`：
config-service `SystemConfigDefaults` 已覆盖 V1 `SystemConfigKey` 全量 133 个 key。
直接 diff 结果为 `V1=133 V3=133 Missing=0 Extra=0`；`security.admin.password` 与
`security.jwt.secret` 继续在 initializer resolve 阶段随机生成，不把空默认值作为真实 secret。
`app.storage.r2.bucket-name` 和 `app.storage.r2.public-domain` 已纳入 required key 边界。

本轮同时补回 LLM prompt 的 raw-preservation 默认约束，保留 `{standardItems}` /
`{rawItems}` 占位符；既有 `RuntimeInjectedConfig -> DB -> default seed` 优先级、
`JdbcConfigRepository.upsertDefault()` 不覆盖 `config_value` 的合约未改变。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-config-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09 已完成 `028-16-C2-S5 search cover URL config parity`：
V1 `StorageUrlHelper` 每次解析 R2 cover 读取 `app.storage.r2.public-domain`，解析本地托管
cover 读取 `app.storage.public-path`。V3 search-service `CoverImageUrlResolver` 已迁到同一优先级：
运行时注入 -> `system_configs` DB -> V1 default。

本轮实现：

- R2 cover 兼容 `app.storage.r2.public-domain` 和既有 V3 alias
  `app.storage.r2-public-domain` / `APP_STORAGE_R2_PUBLIC_DOMAIN`。
- LOCAL/LOCAL_STORED cover 使用 `app.storage.public-path` 拼接 `/media/...`，不再直接把
  `storagePath` 写入 search document。
- search-service 新增本地 config cache、`q.search.config_changed` 广播队列和按 key evict；
  `APP_SEARCH_CONFIG_LISTENER_ENABLED` 独立于 `APP_SEARCH_LISTENER_ENABLED`。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09 已完成 `028-16-C2-S6 interaction cover URL config parity`：
V1 interaction 收藏/历史 `coverImageUrl` 通过 `StorageUrlHelper.resolveUrl()` 读取
`app.storage.r2.public-domain` 与 `app.storage.public-path`。V3 interaction-service
`CoverImageUrlResolver` 已迁到同一优先级：运行时注入 -> `system_configs` DB -> V1 default。

本轮实现：

- R2 cover 兼容 `app.storage.r2.public-domain` 和既有 V3 alias
  `app.storage.r2-public-domain` / `APP_STORAGE_R2_PUBLIC_DOMAIN`。
- LOCAL/LOCAL_STORED cover 使用 `app.storage.public-path` 拼接 `/media/...`，portal 收藏/历史
  响应不再返回裸 `storagePath`。
- interaction-service 复用已有本地 config cache、`q.interaction.config_changed` 广播队列和
  `APP_INTERACTION_CONFIG_LISTENER_ENABLED` 开关。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-interaction-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\interaction-service-dev.yaml
deployment.apps/ircs-interaction-service configured (server dry run)
service/ircs-interaction-service unchanged (server dry run)
```

2026-06-09 已完成 `028-16-C2-S7 portal cover URL config parity`：
V1 portal 首页、分类、探索和详情卡片 `coverImageUrl` 通过 `PortalAssembler` 调用
`StorageUrlHelper.resolveUrl()`，读取 `app.storage.r2.public-domain` 与
`app.storage.public-path`。V3 portal-service `PortalCoverImageUrlResolver` 已迁到同一优先级：
运行时注入 -> `system_configs` DB -> V1 default。

本轮实现：

- R2 cover 兼容 `app.storage.r2.public-domain` 和既有 V3 alias
  `app.storage.r2-public-domain` / `APP_STORAGE_R2_PUBLIC_DOMAIN`。
- LOCAL/LOCAL_STORED cover 使用 `app.storage.public-path` 拼接 `/media/...`，门户首页/分类/探索/
  详情卡片不再返回裸 `storagePath`。
- portal-service 新增本地 config cache、`q.portal.config_changed` 广播队列、DLQ 绑定和
  `APP_PORTAL_CONFIG_LISTENER_ENABLED` 开关；dev manifest 已补 RabbitMQ 与 R2 public domain env。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-portal-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\portal-service-dev.yaml
deployment.apps/ircs-portal-service configured (server dry run)
service/ircs-portal-service unchanged (server dry run)
```

2026-06-09 已完成 `028-16-C2-S8 storage media URL config parity`：
V1 storage/portal 头像和封面媒体路径通过 `SystemConfigService` 读取
`app.storage.public-path`、`app.storage.r2.public-domain` 和
`app.storage.path.prefix.avatar`。V3 storage-service 已为 cover 管理 URL、头像上传、头像 R2
同步和 watchdog 本地头像扫描接入同一优先级：运行时注入 -> `system_configs` DB -> V1 default。

本轮实现：

- storage-service 新增本地 config cache、`q.storage.config_changed` 广播队列、DLQ 绑定和
  `APP_STORAGE_CONFIG_LISTENER_ENABLED` 开关。
- `CoverImageUrlResolver` 使用 env/DB/default 解析 R2 和 LOCAL/LOCAL_STORED cover URL。
- `AvatarStorageService` 使用 env/DB/default 读取头像目录前缀和 public path。
- `AvatarSyncService` 使用 env/DB/default 读取 local public path 和 R2 public domain。
- `StorageR2Watchdog` 使用 env/DB/default 读取 local public path；batch size 仍保留启动绑定。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\storage-service-dev.yaml
deployment.apps/ircs-storage-service configured (server dry run)
service/ircs-storage-service unchanged (server dry run)
```

2026-06-09 已完成 `028-16-C2-S9 normalization retry config parity`：
V1 normalization retry/backoff/watchdog 通过 `SystemConfigService` 读取
`normalization.max-retries`、`normalization.backoff.base-seconds`、`normalization.batch-size`
和 `normalization.timeout.pending-min`。V3 normalization-worker 已为 normalize failure retry
和 retry watchdog 接入同一优先级：运行时注入 -> `system_configs` DB -> V1 default。

本轮实现：

- normalization-worker 新增本地 config cache、`q.normalization.config_changed` 广播队列、DLQ
  绑定和 `APP_NORMALIZATION_CONFIG_LISTENER_ENABLED` 开关。
- `RawVideoNormalizationService` 使用 env/DB/default 读取 `normalization.max-retries`，V1
  默认恢复为 `5`，并兼容 V3 alias。
- `RawVideoNormalizationService` 使用 env/DB/default 读取
  `normalization.backoff.base-seconds`，V1 默认保持 `60`。
- `NormalizationRetryWatchdog` 使用 env/DB/default 读取 `normalization.batch-size` 和
  `normalization.timeout.pending-min`；dev manifest 用
  `APP_NORMALIZATION_WATCHDOG_BATCH_SIZE=25` 覆盖 V1 `500` 默认批次，保持低资源开发环境。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\normalization-worker-dev.yaml
deployment.apps/ircs-normalization-worker configured (server dry run)
```

2026-06-09 已完成 `028-16-C2-S14 content scraper base URL config parity`：content-service
`InternalContentClients` 的 internal scraper base URL 已从构造期固定配置推进到调用期读取。
canonical key 为 `app.content.internal.scraper-base-url`，兼容 `app.content.scraper-base-url`、
`APP_CONTENT_SCRAPER_BASE_URL` 和 `APP_CONTENT_INTERNAL_SCRAPER_BASE_URL`；读取优先级为
`RuntimeInjectedConfig -> system_configs DB -> V3 fallback`。content-service 新增本地 config
cache、`q.content.config_changed` 广播队列、DLQ 绑定和 `APP_CONTENT_CONFIG_LISTENER_ENABLED`
独立开关。resolver preset seed gate、service audit 开关和 content media URL fallback 未纳入
本切片，继续按启动绑定或后续独立切片记录。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-content-service:test --rerun-tasks
BUILD SUCCESSFUL
42 tests, 0 failures

kubectl apply --dry-run=server -f deploy\k8s\dev\content-service-dev.yaml
deployment.apps/ircs-content-service configured (server dry run)
service/ircs-content-service unchanged (server dry run)
```

2026-06-09 已完成 `028-16-C3-S4a metadata TMDB credential INTERNAL_SERVICE policy` 第一刀：
shared outbound contract 新增 policy type 区分 `PUBLIC_FETCH`、`INTERNAL_SERVICE` 和
`BFF_PROXY`。`INTERNAL_SERVICE` / `BFF_PROXY` 只校验 `http/https`、host 必填和禁止
userinfo，不执行 public fetch 的 DNS SSRF 阻断，因此允许 cluster DNS 与 localhost dev base
URL。metadata-worker `TmdbCredentialRepository` 已迁到 shared `INTERNAL_SERVICE`，保留固定
lease path/query、`X-IRCS-INTERNAL-TOKEN`、空凭据过滤和失败转
`CREDENTIAL_SERVICE_UNAVAILABLE` retryable 语义。

验证：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-metadata-worker:test --rerun-tasks
BUILD SUCCESSFUL

TmdbCredentialRepositoryTest: tests=4, failures=0
```

2026-06-09 已完成 `028-16-C1 full readiness orchestrator / auto start gate first closure`：
本切片不恢复 V1 单体式全局 initializer，而是把剩余危险自动入口先收口为
ENV/K8S/启动参数最高优先级、default seed 最低兜底、默认关闭、可观察。

本轮实现：

- `shared:ircs-common` 新增 `AutoStartGateInspector` 和 `AutoStartGateHealthIndicator`，
  health detail 暴露 gate 名称、配置 key、env key、enabled、source、sourceKey 和 blocked。
- source 只把 command line、system properties、system environment、configtree、K8S
  ConfigMap/Secret 等运行时注入视为 `INJECTED`；静态 `application.yaml` 默认继续视为
  `DEFAULT`。
- normalization retry watchdog 改为 `APP_NORMALIZATION_WATCHDOG_ENABLED=false` 默认关闭。
- storage R2 watchdog 改为 `APP_STORAGE_R2_WATCHDOG_ENABLED=false` 默认关闭。
- dev K8S aggregation scheduler 改为 `APP_AGGREGATION_SCHEDULER_ENABLED=false` 常驻关闭；
  后续 pipeline smoke 需要时必须临时显式开启。

验证：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
BUILD SUCCESSFUL

.\scripts\smoke\readiness-auto-start-gate-02816c1.ps1 -UseCluster
MARK 1/3 local application defaults keep dangerous gates false
MARK 2/3 dev manifests explicitly keep dangerous gates false
MARK 3/3 live deployments keep dangerous gates false
```

C1 父任务不完全关闭；full auto-start gate first closure 已关闭。剩余拆分项：cache
warmup/TTL/evict matrix、catalog countries/languages 全量 V1 preset、resolver/真实资源站
live smoke，以及后续如确需 V1 式 `RabbitListenerEndpointRegistry.start()` 自动放行再独立实现。

