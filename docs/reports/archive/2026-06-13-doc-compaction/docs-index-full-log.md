# IRCS V3 本地文档入口

状态：进行中

更新时间：2026-06-13

## 当前主线

当前仓库主线是将 `ircs-project-v1` 按 V3 微服务边界拆分，并在
`ircs-dev` namespace 中完成低资源、无公网 HTTPRoute 的 MVP 接入。18 个服务的
MVP rollout 已完成；当前继续推进 post-MVP 的 V1/V3 路由覆盖闭环，已完成
`025-01` 到 `025-20`，当前 HTTP Controller + BFF smoke 口径为
`V3 routes=226`、`missing=0`；其中 2 条为 post-025 新增的 worker/job audit
ops 查询端点。行为深度迁移第一阶段已完成 BFF request audit、
avatar 媒体持久化、magnet dev-safe provider search 和 ops `search-reindex-unified`
runner；queue consumer 深层副作用已推进到 `028-05-A` 至 `028-05-I`，其中
`028-05-H` 已完成 live smoke `MARK 5/5`。当前 `028` 第一阶段强制行为缺口为 `0`；
后续重点转向真实 provider、production governor 和 service/worker audit
  等历史内部链路；其中 `028-06`、`028-07` 已完成，`028-10`、`028-12` 保持进行中，
`028-08` 已完成 normalization deep mapping live smoke，`028-09` 已完成 search producer
live smoke parity，`028-11` 已完成首个 service handler audit，`028-12` 已补上
worker/job audit 的 ops 查询 API、dev rollout、live query smoke 第一刀、notification
worker live queue smoke、content-service handler audit live smoke 和 credential-service
sensitive handler audit live smoke、identity-service handler audit query token redaction
live smoke、interaction-service handler audit live smoke、magnet-service handler audit
  live smoke、storage-service handler audit live smoke、search-service handler audit
  live smoke、portal-service handler audit live smoke、task-service handler audit live smoke、
  ops-service handler audit live smoke 和 scraper-service handler audit live smoke；
  V1 credential 导入后，`028-06` TMDB direct diagnostic 与 live queue success 也已完成，真实
  TMDB provider/result/search 链路记录 `MARK 6/6`；`028-06-C` magnet YTS_BZ runner
  contract 已完成 `MARK 5/5`；`028-07` credential-only R2 object live smoke 已用 V1
  bucket 完成 `MARK 5/5`，并定位 dev ConfigMap bucket 漂移，`028-07-C` storage-service
  R2 path smoke 已完成 `MARK 6/6`，`028-07-D` avatar R2 live smoke 已完成 `MARK 7/7`，
  `028-07-E` dev R2 bucket policy guard 已完成 `MARK 4/4` 并固定显式临时覆盖边界，
  `028-07-H` cover explicit V1 bucket chain smoke 和 `028-07-I` avatar explicit V1 bucket
  chain smoke 已分别完成 `MARK 6/6`、`MARK 7/7`；
  `028-10-B` MAIL credential adapter fake sink、`028-10-C` ops runner registry dev gate、
  `028-10-D` notification send history fake sink 和 `028-10-E` notification send history
  schema / ops query 已完成，其中 `028-10-E` 已补 notification-worker + ops-service 定向
  build evidence；`028-10-H` 已完成 send history dev DB migration / ops query live 闭环，
  `028-10-I` 已通过 one-shot safety gate 阻断 dev SMTP 真发，`028-10-J/K` 已完成
  send history retention cleanup dev DB smoke，`028-10-M` 已确认 notification-worker 从
  `CredentialServiceMailCredentialRepository: No default constructor found` CrashLoop 恢复到
  Ready 1/1，`028-10-L` 已完成 V1 SMTP one-shot 真实发送 1 封并通过 send history、ops
  endpoint 和用户收件箱确认；`028-12-A`
  task-service runner audit、`028-12-B`
  aggregation-worker scheduler audit 已完成 `MARK 5/5`，`028-12-C` 已补 metadata /
  normalization / storage worker audit implementation first closure，并于 2026-06-12 完成 live/ops
  audit smoke `MARK 6/6`。`028-06`、`028-07`
  大类已闭环，`028-10`、`028-12` 仍保持进行中，继续承接高风险 runner 和更多
  worker/scheduler audit。`028-07` 大类已闭环。
  用户已进一步要求
  V1 各类功能边角细节要么更加完善、要么一比一迁移；因此新增 `028-16` 深层 parity
  总账，不再用“4 类剩余细节”概括全部风险，而是逐项核对初始化、SystemConfig、清洗、
  聚合、出站 HTTP、缓存、Tika、LLM、搜索补偿、scraper rawMetadata 和 metadata result
  深层合并等 V1 已验证能力；聚合链路 `028-16-B9` JGraphT same-batch raw/raw cluster
  第一刀、`028-16-B10` raw/unified context graph 第一刀、`028-16-B11` category bucket
  graph 第一刀、`028-16-B12` graph scoring metadata penalty 第一刀、`028-16-B13`
  ES-backed context recall 第一刀和 `028-16-B14` pipeline runtime order/nested savepoint failure isolation
  第一刀均已完成。SystemConfig
  链路已补 V1 seed 最低优先级、ENV/K8S/启动参数最高优先级、config-service/P0 consumer
  YAML-default guard、C2-S4 full V1 `SystemConfigKey` catalog 与 config change event 第一刀；
  C2-S5 已补 search-service cover URL 的 R2/public-path 配置消费者，C2-S6 已补
  interaction-service 收藏/历史 cover URL 的 R2/public-path 配置消费者，C2-S7 已补
  portal-service 首页/分类/探索/详情 cover URL 的 R2/public-path 配置消费者，C2-S8 已补
  storage-service 媒体 URL/头像同步 public-path/R2 domain/avatar prefix 配置消费者，C2-S9 已补
  normalization-worker retry/backoff/watchdog batch/pending timeout 配置消费者，C2-S10 已补
  aggregation-worker scheduler batch size 配置消费者，C2-S11 已补 notification-worker MAIL
  enable/from/SMTP connect options 配置消费者，C2-S12 已补 metadata-worker provider enable/pending TTL
  配置消费者，C2-S13 已补 ops-service reindex dev limit 配置消费者，C2-S14 已补
  content-service internal scraper base URL 运行期配置消费者；
  初始化链路已补 C1-S2c countries/languages full preset、normalization TTL fallback
  与 resolver/真实资源站 dry-run prep、C1-S4 task-service
  默认 collection task seed、task cron scheduler gate、C1-S5 content-service 默认 resolver
  preset seed、C1-S6 credential-service runtime initializer、C1-S7 credential summary redaction
  、C1-S8 data-source category sync trigger、C1-S9 task-service watchdog start gate，以及
  C1 full readiness / auto-start gate first closure；normalization/storage watchdog 与
  aggregation dev scheduler 常驻默认关闭，shared auto-start gate health detail 可观察
  `INJECTED`/`DEFAULT` 来源，live K8S gate smoke 已完成 3/3；
  normalization-worker 已补 A4
  description NER fallback / HanLP prewarm、A5 area/language composite alias 第一刀、
  A6 genre synonym/suffix/protected-term policy 第一刀、A7 title alias dedup policy
  第一刀、A8 title season/subtitle 第一刀和 A9 title/location/genre pipeline 本地 first
  closure；A9 dev live 首跑因 K8S TLS timeout + Windows 管道编码未闭合但 cleanup 已验证；
  shared outbound HTTP 已补 C3 PUBLIC_FETCH 首批 caller、C3-S4a metadata TMDB
  credential INTERNAL_SERVICE policy、admin-bff `BFF_PROXY` target validation 和
  portal-bff `BFF_PROXY` target validation；C2-S15 已补 config event live matrix no-write
  smoke harness，C2-S16 已补 live Rabbit config event safe smoke dry-run，C2-S17/C2-S19/C2-S20
  已完成 config-service -> Rabbit -> content queue 的真实 publish/deliver/ack 验证和 content
  DB fallback read probe，C2-S21 已决策并验证 Rabbit outage / missed-message 下采用
  event evict + short TTL read-through 兜底，C2 父任务已关闭；
  `028-07-F` 已补 explicit V1 bucket smoke preflight / no-write guard，`028-07-G` real R2
  explicit V1 bucket write smoke 已完成 `PUT 200 / HEAD 200 / DELETE 204 / cleanup HEAD 404`
  无 residue，`028-07-H` cover chain 和 `028-07-I` avatar chain 已用 V1 bucket `ircs`
  分别验证 `covers/...` 与 `avatars/...` public URL、DB/R2/local cleanup；`028-06-E` 已完成 Douban provider first slice `MARK 6/6`，真实只读
  Douban smoke 返回 HTTP 200 并解析候选，`PROVIDER_IMPLEMENTATION_MISSING` 与
  `CREDENTIAL_CATALOG_UNSUPPORTED` 已解除，`DEV_PROVIDER_GATE_DISABLED` 保留为 dev
  安全 gate；`028-06-H/I` 已完成 RT provider first slice 与 ARM64 live rollout/full queue parity
  `MARK 6/6`，真实只读 Rotten Tomatoes smoke 返回 HTTP 200 并解析 `The Matrix -> matrix`，
  live queue smoke 外网请求数为 1，metadata-worker live digest
  `sha256:93db92be56a2a7dc0b85d84c7a43ecb8c8f854f5947f1bbd7a6ae2dafc34291a`，dev gate 已恢复关闭；
  `028-10-H` 已完成 send history dev DB migration 与 ops 查询闭环，
  `028-10-I` one-shot live smoke 已确认在 `AllowSend=true` 和安全测试收件人形态下
  阻断真实 SMTP，send history 无新增 row；`028-10-J/K` 已完成 retention cleanup dev DB
  smoke，旧 fixture 删除、新 fixture 保留后清理，ops summary 可查，未发送 SMTP；
  `028-10-L` 已在 Ready 恢复后完成 Gmail/标准 SMTP V1 credential one-shot，真实发送 1 封，
  send history/ops endpoint 可审计，用户已确认测试邮箱收到邮件，secret 未输出。
  `028-16-E` LLM cleaning gated parity 已完成 implementation-first closure：normalization-worker
  默认关闭 LLM cleaning，mode 默认 `dry-run`，runner 默认关闭；fake path、credential-service
  lease/runtime credential path、固定错误语义和 focused tests 已完成，真实 LLM 调用与 live smoke
  统一测试后置。
  `028-16-F` 已完成本轮 P1/P2 backlog 入账，8 个 backlog task spec 已创建，并固定
  `UTB-02816-P1P2-01/02/03` 统一测试批次；F2/B15 已补 aggregation local closure，
  真实 R2/multi-context production-like smoke 后置。C3-S8 identity avatar multipart internal caller
  与 C3-S9 admin/portal BFF shared streaming transport 已完成实现和 focused no-network tests；
  C1-S2c full countries/languages preset、normalization TTL fallback、resolver/resource dry-run prep
  已完成实现；028-12-C metadata/normalization/storage worker audit hooks 已完成 focused tests。
  F1/A11 normalization golden P1/P2 已完成本地 implementation closure：title release/S02/Part/Vol、
  location hint、genre synonyms、raw category fallback、retry/watchdog PROCESSING reset、queue depth
  guard、adaptive batch min/max 和固定 failure reason log 已补；A12 已继续补 title bracket 噪音清理、
  raw area alias/composite、genre synonym 与脏 token 防护；A13 post-reboot 收敛已把
  `CharTable -> HanLP -> OpenCC` 接回并恢复 RawVideoTextNormalizer focused tests 24/24，
  但 `title` 显示字段切到 V1 mainTitle 语义仍需独立决策；live normalize smoke 统一测试后置。
  F4/E LLM cleaning no-real-cost closure 已补幂等护栏、fake path 无凭据/无 live client、
  credential 脱敏；F5/C1 已补 catalog/content/task gate health 和 catalog seed gate，
  C1-X 已补 UUID generator 门面与 AsyncLogger/Disruptor ADR，并记录 V1 式全服务自动放行、
  time-based UUID 全量替换、AsyncLogger/Disruptor 默认启用为后续独立切片或替代口径；
  F7/028-12-D 已补 search/scraper worker-job audit 第二切片，B16 已补 search_sync_tasks 只读 stats 端点。
  2026-06-12 已完成生产代码 no `@Autowired` 与 Spring 单构造器风险收敛，
  并完成全服务 dev 镜像运行态 digest 审计、`ircs-content-service` Redis env rollout 恢复、
  `ircs-dev` 25/25 Deployment Ready 和轻量 readiness smoke。以上实现项除已注明 live smoke
  的切片外，均按“功能实现完成待统一测试”入账，不写成生产闭环。
  `028-22-A/B/C/D/E/F` 已分别补齐 `unified-recalculate`、`aggregation-reset`、`sanitize`、`trend-sync`
  owner runner、trend background discovery、TMDB credential-service lease 和 trend discovery
  no-external live smoke/orchestrator，其中 `sanitize`
  保持 V1 只重置 raw video normalization status 的最小副作用；`trend-sync` 已补
  provider -> scraper -> content owner -> search sync outbox -> task-service discovery 主链路，
  task/scraper/ops K8S rollout、manifest apply 和 no-external live DB residue 验证已通过，
  真实 provider smoke 后置统一外部依赖窗口。

当前已接入并 Ready 的业务服务为 18/18：

```text
ircs-notification-worker
ircs-search-service
ircs-storage-service
ircs-content-service
ircs-metadata-worker
ircs-credential-service
ircs-catalog-service
ircs-config-service
ircs-task-service
ircs-ingestion-worker
ircs-normalization-worker
ircs-aggregation-worker
ircs-magnet-service
ircs-identity-service
ircs-scraper-service
ircs-portal-service
ircs-interaction-service
ircs-ops-service
```

当前尚未部署接入的目标业务服务为 0/18：

```text
暂无
```

## 需求文档

| 文档 | 用途 |
| --- | --- |
| `docs/requirements/011-mvp-service-onboarding-acceleration.md` | MVP 服务整体接入加速需求、范围、风险和验收标准。 |
| `docs/requirements/025-v1-route-coverage-closeout.md` | V1 路由覆盖闭环需求导航页。 |
| `docs/requirements/025-v1-route-coverage-closeout-scope.md` | 025 scope / baseline。 |
| `docs/requirements/025-v1-route-coverage-closeout-priority-strategy.md` | 025 route gap priority strategy。 |
| `docs/requirements/025-v1-route-coverage-closeout-acceptance.md` | 025 acceptance。 |
| `docs/requirements/027-portal-admin-frontend-deployment.md` | V1 portal/admin 门户在 `ircs-dev` 的三 Pod 部署与验证入口需求。 |
| `docs/requirements/028-v1-v3-behavior-parity-closeout.md` | V1 到 V3 行为级 1:1 迁移闭环需求、缺口和验收口径。 |
| `docs/requirements/028-22-production-candidate-closeout-goal.md` | V1 -> V3 最后收敛 goal：剩余开发、destructive runner、统一测试、真实外部依赖和生产候选准入编排。 |
| `docs/requirements/029-rabbit-redis-task-system-redesign.md` | 任务系统破坏性切到 Rabbit 主队列、Redis MT snapshot/状态热层和 DB 粗账本的需求。 |

## 架构文档

| 文档 | 用途 |
| --- | --- |
| `docs/architecture/001-target-microservice-boundaries.md` | 目标微服务边界、HTTP 路由、队列和数据归属。 |
| `docs/architecture/002-cutover-plan.md` | V1 到 V3 的服务接入顺序、迁移策略和当前接入状态。 |

## 开发指南

| 文档 | 用途 |
| --- | --- |
| `docs/developer-guide.md` | 技术栈、构建命令、测试、镜像构建、Monorepo 结构、包命名约定和架构约束。 |

## 决策记录

| 文档 | 用途 |
| --- | --- |
| `docs/decisions/003-formal-portal-admin-bff-split.md` | 正式拆分 `ircs-portal-bff` 和 `ircs-admin-bff` 的职责、边界和资源决策。 |
| `docs/decisions/004-uuid-async-logging-parity.md` | UUID strategy 与 AsyncLogger/Disruptor 第一阶段决策记录。 |
| `docs/decisions/005-post-parity-runtime-platform-decisions.md` | 2026-06-10 过会拍板的 post-parity runtime/platform 决策总表。 |
| `docs/decisions/007-manual-mtls-removal.md` | 修订 D15：不再保留手动应用层 mTLS；服务间回到集群内 HTTP，保留应用层 service identity/scopes，未来如重启 mTLS 必须由基础设施层承接。 |
| `docs/decisions/008-rabbit-redis-mt-pt-dt-task-system.md` | 任务系统 MT/PT/DT、Rabbit 队列、Redis 热状态和破坏性切换决策。 |

## K8S 文档

| 文档 | 用途 |
| --- | --- |
| `docs/k8s/current-environment-2026-06-02.md` | K8S 环境快照导航页，入口保留，正文已拆分。 |
| `docs/k8s/current-environment-2026-06-02-overview.md` | namespace、topology、Helm 和 runtime/dev namespace 概览。 |
| `docs/k8s/current-environment-2026-06-02-conflict-controls.md` | 2026-06-02 冲突控制明细。 |
| `docs/k8s/rollouts/current-environment-2026-06-07-rollouts.md` | 2026-06-07 rollout 日志。 |
| `docs/k8s/rollouts/current-environment-2026-06-08-rollouts.md` | 2026-06-08 rollout 日志导航页。 |
| `docs/k8s/rollouts/current-environment-2026-06-08-audit-rollouts.md` | 2026-06-08 028-12 audit rollout 日志。 |
| `docs/k8s/rollouts/current-environment-2026-06-08-normalization-aggregation-rollouts.md` | 2026-06-08 normalization/aggregation rollout 日志。 |
| `docs/k8s/rollouts/current-environment-2026-06-08-provider-storage-rollouts.md` | 2026-06-08 credential/provider/storage rollout 日志。 |
| `docs/k8s/dev-staging-resource-budget-and-rollback-runbook.md` | `ircs-dev`/staging 资源预算、危险 gate、外部 smoke 次数预算和回滚 runbook。 |
| `docs/k8s/image-build-strategy.md` | 本机 x86 到 VPS ARM64 的镜像构建策略。 |
| `docs/k8s/local-dev-telepresence.md` | 本地开发接入 K8S 的 Telepresence 说明。 |
| `docs/k8s/v1-config-environment-map.md` | 从 V1 `ircs-config` 提取的环境变量映射。 |

## 迁移记录

迁移记录统一放在 `docs/migrations/`：

| 编号 | 文档 | 状态 |
| --- | --- | --- |
| 001 | `docs/migrations/001-notification-worker-extraction.md` | 已完成 |
| 002 | `docs/migrations/002-search-service-extraction.md` | 已完成 |
| 003 | `docs/migrations/003-migrator-extraction.md` | 已完成 |
| 004 | `docs/migrations/004-storage-service-extraction.md` | 已完成 |
| 005 | `docs/migrations/005-content-service-image-unlink-extraction.md` | 已完成 |
| 006 | `docs/migrations/006-metadata-worker-result-extraction.md` | 已完成 |
| 007 | `docs/migrations/007-metadata-worker-dispatcher-extraction.md` | 已完成 |
| 008 | `docs/migrations/008-metadata-worker-tmdb-provider-extraction.md` | 已完成 |
| 009 | `docs/migrations/009-credential-service-readonly-extraction.md` | 已完成 |
| 010 | `docs/migrations/010-credential-service-provider-lease-and-metadata-client.md` | 已完成 |
| 011 | `docs/migrations/011-catalog-service-readonly-extraction.md` | 已完成 |
| 012 | `docs/migrations/012-config-service-readonly-extraction.md` | 已完成 |
| 013 | `docs/migrations/013-task-service-readonly-extraction.md` | 已完成 |
| 014 | `docs/migrations/014-ingestion-worker-video-ingest-extraction.md` | 已完成 |
| 015 | `docs/migrations/015-normalization-worker-normalize-extraction.md` | 已完成 |
| 016 | `docs/migrations/016-aggregation-worker-scheduler-extraction.md` | 已完成 |
| 017 | `docs/migrations/017-magnet-service-provider-cache-extraction.md` | 已完成 |
| 018 | `docs/migrations/018-identity-service-auth-profile-extraction.md` | 已完成 |
| 019 | `docs/migrations/019-scraper-service-manual-scrape-extraction.md` | 已完成 |
| 020 | `docs/migrations/020-portal-service-read-composition-extraction.md` | 已完成 |
| 021 | `docs/migrations/021-interaction-service-feedback-history-extraction.md` | 已完成 |
| 022 | `docs/migrations/022-ops-service-maintenance-dashboard-extraction.md` | 已完成 |
| 023 | `docs/migrations/023-search-service-portal-query-api-extraction.md` | 已完成 |
| 024 | `docs/migrations/024-interaction-service-portal-write-api-extraction.md` | 已完成 |
| 025 | `docs/migrations/025-portal-service-explore-detail-extraction.md` | 已完成 |
| 026 | `docs/migrations/026-task-service-lifecycle-api-extraction.md` | 已完成 |
| 027 | `docs/migrations/027-content-service-raw-unified-admin-api-extraction.md` | 已完成 |
| 028 | `docs/migrations/028-storage-service-cover-image-admin-api-extraction.md` | 已完成 |
| 029 | `docs/migrations/029-ops-service-observability-read-api-extraction.md` | 已完成 |
| 030 | `docs/migrations/030-interaction-service-admin-message-api-extraction.md` | 已完成 |
| 031 | `docs/migrations/031-task-service-runner-scheduler-extraction.md` | 已完成 |
| 032 | `docs/migrations/032-identity-service-admin-auth-api-extraction.md` | 已完成 |
| 033 | `docs/migrations/033-identity-service-member-admin-api-extraction.md` | 已完成 |
| 034 | `docs/migrations/034-content-service-people-admin-api-extraction.md` | 已完成 |
| 035 | `docs/migrations/035-catalog-service-admin-read-contract-extraction.md` | 已完成 |
| 036 | `docs/migrations/036-config-service-common-timezones-api-extraction.md` | 已完成 |
| 037 | `docs/migrations/037-content-service-aux-admin-api-extraction.md` | 已完成 |
| 038 | `docs/migrations/038-catalog-service-admin-write-contract-extraction.md` | 已完成 |
| 039 | `docs/migrations/039-config-credential-admin-write-contract-extraction.md` | 已完成 |
| 040 | `docs/migrations/040-magnet-service-admin-write-search-contract-extraction.md` | 已完成 |
| 041 | `docs/migrations/041-identity-service-portal-avatar-upload-extraction.md` | 已完成 |
| 042 | `docs/migrations/042-ops-service-guarded-operations-debug-stream-extraction.md` | 已完成 |
| 043 | `docs/migrations/043-bff-request-audit-collection-parity.md` | 已完成 |
| 044 | `docs/migrations/044-identity-avatar-media-sync-parity.md` | 已完成 |
| 045 | `docs/migrations/045-magnet-provider-search-parity.md` | 已完成 |
| 046 | `docs/migrations/046-ops-maintenance-runner-parity.md` | 已完成 |
| 047 | `docs/migrations/047-interaction-watch-progress-consumer-parity.md` | 已完成 |
| 048 | `docs/migrations/048-storage-avatar-sync-consumer-parity.md` | 已完成 |
| 049 | `docs/migrations/049-metadata-provider-fanout-parity.md` | 已完成 |
| 050 | `docs/migrations/050-search-producer-matrix-parity.md` | 已完成 |
| 051 | `docs/migrations/051-metadata-provider-worker-enrichment-parity.md` | 已完成 |
| 052 | `docs/migrations/052-normalization-raw-relation-parity.md` | 已完成 |
| 053 | `docs/migrations/053-metadata-pending-gather-parity.md` | 已完成 |
| 054 | `docs/migrations/054-storage-image-download-parity.md` | 已完成 |
| 055 | `docs/migrations/055-mail-notification-disabled-failure-parity.md` | 已完成 |
| 056 | `docs/migrations/056-metadata-provider-parity-gating.md` | 完成第一刀 |
| 056 | `docs/migrations/056-normalization-deep-mapping-person-relation.md` | 完成第一刀 |
| 056 | `docs/migrations/056-search-producer-normalization-live-smoke.md` | 完成第一刀 |
| 057 | `docs/migrations/057-per-service-audit-config-handler.md` | 已完成 |
| 057 | `docs/migrations/057-metadata-provider-live-queue-smoke.md` | 已完成 |
| 058 | `docs/migrations/058-search-producer-metadata-result-live-smoke.md` | 完成第一刀 |
| 058 | `docs/migrations/058-normalization-raw-category-data-source-category.md` | 已完成 |
| 058 | `docs/migrations/058-catalog-service-audit-handler.md` | 完成第一刀 |
| 058 | `docs/migrations/058-storage-r2-watchdog-recovery.md` | 完成第一刀 |
| 059 | `docs/migrations/059-normalization-standard-mapping.md` | 已完成 |
| 059 | `docs/migrations/059-02810-governor-credential-pool-boundary.md` | 完成第一刀 |
| 059 | `docs/migrations/059-worker-job-audit-events.md` | 完成第一刀 |
| 060 | `docs/migrations/060-normalization-retry-watchdog.md` | 已完成 |
| 061 | `docs/migrations/061-worker-job-audit-ops-query.md` | 已完成 |
| 062 | `docs/migrations/062-notification-worker-live-audit-smoke.md` | 已完成 |
| 063 | `docs/migrations/063-normalization-deep-mapping-live-smoke.md` | 已完成 |
| 064 | `docs/migrations/064-search-producer-aggregation-live-smoke.md` | 已完成 |
| 065 | `docs/migrations/065-search-producer-old-unified-dirty-lifecycle.md` | 已完成 |
| 066 | `docs/migrations/066-content-service-audit-handler.md` | 已完成 |
| 067 | `docs/migrations/067-credential-service-audit-handler.md` | 已完成 |
| 068 | `docs/migrations/068-identity-service-audit-handler.md` | 已完成 |
| 069 | `docs/migrations/069-interaction-service-audit-handler.md` | 已完成 |
| 070 | `docs/migrations/070-magnet-service-audit-handler.md` | 已完成 |
| 071 | `docs/migrations/071-storage-service-audit-handler.md` | 已完成 |
| 072 | `docs/migrations/072-search-service-audit-handler.md` | 已完成 |
| 073 | `docs/migrations/073-portal-service-audit-handler.md` | 已完成 |
| 074 | `docs/migrations/074-task-service-audit-handler.md` | 已完成 |
| 078 | `docs/migrations/078-v1-credential-import-for-live-parity.md` | 已完成 |
| 079 | `docs/migrations/079-r2-live-object-smoke.md` | 完成第一刀 |
| 080 | `docs/migrations/080-magnet-yts-bz-runner-contract.md` | 已完成 |
| 081 | `docs/migrations/081-mail-credential-adapter-fake-sink.md` | 已完成 |
| 082 | `docs/migrations/082-ops-runner-registry-dev-gate.md` | 已完成 |
| 083 | `docs/migrations/083-storage-service-r2-path-smoke.md` | 已完成 |
| 084 | `docs/migrations/084-avatar-r2-live-smoke.md` | 已完成 |
| 085 | `docs/migrations/085-task-service-runner-audit.md` | 已完成 |
| 086 | `docs/migrations/086-aggregation-scheduler-audit.md` | 已完成 |
| 087 | `docs/migrations/087-jdk25-mockito-test-runtime.md` | 已完成 |
| 092 | `docs/migrations/092-worker-scheduler-runner-audit-expansion.md` | implementation first closure，overall partial |
| 090 | `docs/migrations/090-douban-provider-first-slice.md` | 已完成 |
| 091 | `docs/migrations/091-rt-provider-first-slice.md` | 完成 first slice |
| 093 | `docs/migrations/093-security-source-network-hardening.md` | 进行中，安全/POW/source network hardening 实现完成，live 测试后置 |
| 094 | `docs/migrations/094-source-field-auto-mapping-hardening.md` | 已完成，启发式自动映射保留，手动映射改为 JSONPath 点选 |
| 095 | `docs/migrations/095-db-migrator-drift-and-notification-audit-smoke.md` | 已完成，DB migrator 漂移修复与 UTB-03 notification audit 通过 |

## 报告

| 文档 | 用途 |
| --- | --- |
| `docs/reports/014-documentation-organization-2026-06-03.md` | 本轮文档目录整理、引用修正和后续文档风险记录。 |
| `docs/reports/015-identity-service-boundary-analysis.md` | identity-service auth/profile MVP 边界分析。 |
| `docs/reports/016-scraper-ingestion-boundary-analysis.md` | scraper-service 与 ingestion-worker 边界和接入顺序分析。 |
| `docs/reports/017-portal-interaction-magnet-boundary-analysis.md` | portal、interaction、magnet 三个服务的 MVP 边界和风险对比。 |
| `docs/reports/018-normalization-worker-boundary-analysis.md` | normalization-worker normalize queue MVP 边界分析。 |
| `docs/reports/019-aggregation-worker-boundary-analysis.md` | aggregation-worker scheduler MVP 边界分析。 |
| `docs/reports/020-dev-limit-compression-before-next-rollouts.md` | identity/magnet rollout 前的 dev Java CPU limit 压缩分析。 |
| `docs/reports/021-documentation-state-sync-after-identity-rollout.md` | identity rollout 后的文档状态同步与配额风险整理。 |
| `docs/reports/022-dev-service-quota-worker-service-pruning.md` | worker-only Service 裁剪与 dev Service quota 释放记录。 |
| `docs/reports/023-ops-service-boundary-analysis.md` | ops-service maintenance/dashboard MVP 边界分析。 |
| `docs/reports/024-dev-pod-quota-before-final-rollouts.md` | scraper 接入后 Pod quota 阻塞后续 rollout 的风险和前置调整建议。 |
| `docs/reports/025-v1-v3-route-coverage-audit.md` | V1/V3 route coverage audit 导航页。 |
| `docs/reports/025-v1-v3-route-coverage-audit-overview.md` | 025 route coverage overview / initial gap analysis。 |
| `docs/reports/025-v1-v3-route-coverage-audit-slices.md` | 025 slice ledger。 |
| `docs/reports/025-v1-v3-route-coverage-audit-risk-acceptance.md` | 025 risk and acceptance。 |
| `docs/reports/026-vps-resource-comparison-before-dev-migration.md` | VPS 资源对比、SSH key 可用性和是否迁移 dev 资源的判断。 |
| `docs/reports/027-interaction-service-portal-write-boundary-analysis.md` | interaction-service 门户收藏、历史、反馈写接口边界分析。 |
| `docs/reports/028-portal-service-explore-detail-boundary-analysis.md` | portal-service explore/detail 只读接口边界分析。 |
| `docs/reports/029-task-service-lifecycle-boundary-analysis.md` | task-service 生命周期 command/log/state API 边界分析。 |
| `docs/reports/030-content-service-raw-unified-boundary-analysis.md` | content-service raw/unified admin API 无损迁移边界分析。 |
| `docs/reports/031-storage-service-cover-image-boundary-analysis.md` | storage-service cover image admin API 无损迁移边界分析。 |
| `docs/reports/032-ops-service-observability-read-api-boundary-analysis.md` | ops-service request-audit、DLQ、traffic read-first API 边界分析。 |
| `docs/reports/033-interaction-service-admin-message-boundary-analysis.md` | interaction-service admin message API 边界分析与完成结果。 |
| `docs/reports/034-portal-admin-frontend-deployment-boundary-analysis.md` | portal/admin 门户三 Pod dev 部署、gateway 路由和资源边界分析。 |
| `docs/reports/035-task-service-runner-scheduler-boundary-analysis.md` | task-service runner、cron scheduler 与 scraper-service 内部执行契约边界分析。 |
| `docs/reports/036-identity-service-admin-auth-boundary-analysis.md` | identity-service admin auth API 边界分析。 |
| `docs/reports/037-identity-service-member-admin-boundary-analysis.md` | identity-service member admin API 边界分析与完成结果。 |
| `docs/reports/038-parallel-subagent-next-route-slices.md` | 025-12/025-13/025-14 并行推进切片、写入边界和完成结果。 |
| `docs/reports/039-parallel-subagent-route-slices-02515-02517.md` | 025-15/025-16/025-17 并行推进切片、写入边界和预期覆盖变化。 |
| `docs/reports/040-remaining-route-slices-02518-02520.md` | 025-18/025-19/025-20 剩余接口候选切片、风险分组和验收口径。 |
| `docs/reports/041-v1-v3-behavior-parity-gap-audit.md` | V1 到 V3 行为级 1:1 缺口审计和 028 子任务切分依据。 |
| `docs/reports/042-identity-avatar-media-sync-parity-analysis.md` | identity avatar 媒体落地和 storage-service 同步 parity 分析与验收。 |
| `docs/reports/043-magnet-provider-search-parity-analysis.md` | magnet provider search job/run/link dev-safe parity 分析与验收。 |
| `docs/reports/044-ops-maintenance-runner-parity-analysis.md` | ops `search-reindex-unified` dev-safe runner parity 分析与验收。 |
| `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md` | queue consumer 深层副作用 parity 审计和后续切片建议。 |
| `docs/reports/046-interaction-watch-progress-consumer-parity-analysis.md` | interaction WATCH_PROGRESS 队列消费者 parity 分析与验收。 |
| `docs/reports/047-storage-avatar-sync-consumer-parity-analysis.md` | storage SYNC_AVATAR consumer parity 分析与验收。 |
| `docs/reports/048-metadata-provider-fanout-parity-analysis.md` | metadata provider fan-out routing parity 分析与验收。 |
| `docs/reports/049-search-producer-matrix-parity-analysis.md` | raw/unified search producer matrix 和 image unlink refresh parity 分析与验收。 |
| `docs/reports/050-metadata-provider-worker-enrichment-parity-analysis.md` | metadata provider worker/result dev-safe parity 分析与验收。 |
| `docs/reports/051-normalization-raw-relation-parity-analysis.md` | normalization raw genre/language/area relation parity 分析与验收。 |
| `docs/reports/052-metadata-pending-gather-parity-analysis.md` | metadata provider result pending gather parity 分析与验收。 |
| `docs/reports/053-storage-image-download-parity-analysis.md` | storage DOWNLOAD_IMAGE true download/failure parity 分析与本地验收。 |
| `docs/reports/054-mail-notification-disabled-failure-parity-analysis.md` | notification SEND_MAIL disabled/failure parity 分析与验收。 |
| `docs/reports/055-search-producer-live-smoke-02809-normalization.md` | search producer normalization live smoke 第一刀报告。 |
| `docs/reports/055-per-service-audit-collection-parity-analysis.md` | service handler audit 首个切片分析与验收。 |
| `docs/reports/056-provider-parity-02806-first-slice.md` | metadata provider enable gate 与 TMDB diagnostic 第一刀报告。 |
| `docs/reports/056-normalization-deep-mapping-person-relation-02808.md` | normalization actor/director 深层映射第一刀报告。 |
| `docs/reports/057-provider-live-queue-02806-tmdb-smoke.md` | TMDB provider live queue success smoke 报告，真实 provider/result/search MARK 6/6。 |
| `docs/reports/057-normalization-raw-category-data-source-category-02808.md` | normalization raw category/data source category 映射完成报告。 |
| `docs/reports/057-storage-r2-watchdog-02807-analysis.md` | storage R2 watchdog recovery 第一刀报告。 |
| `docs/reports/058-search-producer-live-smoke-02809-metadata-result.md` | search producer metadata-result live smoke 第一刀报告。 |
| `docs/reports/058-02812-catalog-service-audit-first-slice.md` | catalog-service handler audit 第一刀报告。 |
| `docs/reports/058-normalization-standard-mapping-02808.md` | normalization standard mapping fill 第一刀报告。 |
| `docs/reports/059-normalization-retry-watchdog-02808.md` | normalization retry watchdog 第一刀报告。 |
| `docs/reports/059-02810-production-governor-credential-pool-first-slice.md` | production governor/MAIL credential pool 第一刀报告。 |
| `docs/reports/059-02812-worker-job-audit-notification-worker-first-slice.md` | notification-worker worker/job audit 第一刀报告。 |
| `docs/reports/061-02812-worker-job-audit-ops-query.md` | worker/job audit ops 查询 API、rollout 与 live query smoke 完成报告。 |
| `docs/reports/062-02812-notification-worker-live-audit-smoke.md` | notification-worker worker/job audit live queue smoke 完成报告。 |
| `docs/reports/063-normalization-deep-mapping-live-smoke-02808.md` | normalization deep mapping live queue smoke 完成报告。 |
| `docs/reports/064-search-producer-aggregation-live-smoke-02809.md` | search producer aggregation raw/unified live smoke 完成报告。 |
| `docs/reports/065-search-producer-old-unified-dirty-lifecycle-02809.md` | search producer old unified dirty lifecycle live smoke 完成报告。 |
| `docs/reports/066-02812-content-service-audit-handler.md` | content-service handler audit 与 live smoke 完成报告。 |
| `docs/reports/067-02812-credential-service-audit-handler.md` | credential-service handler audit、敏感字段边界与 live smoke 完成报告。 |
| `docs/reports/068-02812-identity-service-audit-handler.md` | identity-service handler audit、query token 脱敏与 live smoke 完成报告。 |
| `docs/reports/069-02812-interaction-service-audit-handler.md` | interaction-service handler audit、query token 脱敏与 live smoke 完成报告。 |
| `docs/reports/070-02812-magnet-service-audit-handler.md` | magnet-service handler audit、query token 脱敏与 live smoke 完成报告。 |
| `docs/reports/071-02812-storage-service-audit-handler.md` | storage-service handler audit、query token 脱敏与 live smoke 完成报告。 |
| `docs/reports/072-02812-search-service-audit-handler.md` | search-service handler audit、query token 脱敏与 live smoke 完成报告。 |
| `docs/reports/073-02812-portal-service-audit-handler.md` | portal-service handler audit、query token 脱敏与 live smoke 完成报告。 |
| `docs/reports/074-02812-task-service-audit-handler.md` | task-service handler audit、query token 脱敏与 live smoke 完成报告。 |
| `docs/reports/077-028-behavior-parity-parallel-subagent-slices.md` | 028 行为 parity 并行 subagent ownership、写入边界和主线程集成边界。 |
| `docs/reports/078-02814-v1-credential-import-for-live-parity.md` | V1 credential 安全导入 V3 dev 凭据池并支撑 TMDB live success 复验。 |
| `docs/reports/079-02807-r2-live-object-smoke.md` | R2 credential-only live object smoke，验证 V1 bucket 成功并发现 dev bucket 配置漂移。 |
| `docs/reports/080-02806-magnet-yts-bz-runner-contract.md` | magnet YTS_BZ runner contract，默认 no-network fixture gate。 |
| `docs/reports/081-02810-mail-credential-adapter-fake-sink.md` | MAIL credential adapter 与 fake/sink，不执行真实 SMTP。 |
| `docs/reports/082-02810-ops-runner-registry-dev-gate.md` | ops runner registry、dev gate 与 runner audit evidence。 |
| `docs/reports/083-02807-storage-service-r2-path-smoke.md` | storage-service R2 path smoke 配置修正与可恢复 live 验证。 |
| `docs/reports/084-02807-avatar-r2-live-smoke.md` | avatar R2 live smoke，验证 identity upload -> SYNC_AVATAR -> R2 URL 回写。 |
| `docs/reports/090-02807-storage-service-r2-avatar-path-smoke.md` | storage-service R2 avatar path 显式 V1 bucket 真实链路 smoke，验证 R2 URL、HEAD 404、DB/local cleanup 和 dev 恢复。 |
| `docs/reports/085-02812-task-service-runner-audit.md` | task-service runner/watchdog/startup recovery worker-job audit。 |
| `docs/reports/086-02812-aggregation-scheduler-audit.md` | aggregation-worker scheduler worker-job audit。 |
| `docs/reports/087-jdk25-mockito-test-runtime.md` | JDK25 Mockito inline agent 测试运行时修正报告。 |
| `docs/reports/088-v1-v3-deep-parity-gap-audit.md` | V1/V3 功能与库细节深层 parity 缺口审计。 |
| `docs/reports/089-v1-golden-behavior-inventory.md` | V1 golden behavior inventory，逐模块追踪边角功能迁移状态；本轮额外纳入 `028-10-E` notification send history schema / ops query 已完成并有定向 build evidence、`028-16-C3` notification MAIL credential INTERNAL_SERVICE 实现已完成但 full suite 仍受 send history H2 `TIMESTAMPTZ` 阻塞、`028-16-A8` title season/subtitle first slice 已完成、`028-07-G` real R2 explicit V1 bucket write smoke 已完成、`028-06-E` Douban provider first slice MARK 6/6、`028-10-F` SMTP staging live preflight blocked、`028-16-C2-S17/C2-S19/C2-S20` 已完成 real Rabbit publish/deliver/ack 与 content DB fallback read probe。 |
| `docs/reports/090-02816c2s19-config-service-publisher-live-rollout.md` | C2-S19 config-service publisher live rollout：config-service dev 镜像 digest `sha256:de6cc3f758ca62ff42bea22b422b2c570e488ac341da7380effdc9ce93472f05`，Rabbit username 修正为 `admin`，C2-S17 重跑观察到 temporary update 与 restore delete 的 publish/deliver/ack；DB/port-forward 无残留。 |
| `docs/reports/095-02816c2s20-content-db-fallback-recoverable-probe.md` | C2-S20 content DB fallback recoverable probe：显式临时移除/恢复 content runtime override，C2-S17 `MARK 7/7`，DB fallback read、Rabbit ack、runtime restore、DB/raw fixture/port-forward cleanup 均有 live 证据。 |
| `docs/reports/096-02806g-retry-douban-provider-full-queue-parity.md` | 028-06-G retry 完成报告：重新复验 metadata-worker live 镜像、Douban consumer、full queue smoke、外网请求数和恢复清理证据。 |
| `docs/reports/097-02806h-rt-provider-first-slice.md` | RT provider first slice 报告：V1 行为对照、provider implementation/catalog、focused tests 和真实 Rotten Tomatoes readonly HTTP 200。 |
| `docs/reports/099-02806i-rt-provider-arm64-live-rollout-queue-parity.md` | RT provider ARM64 live rollout/full queue parity 报告：metadata-worker digest、live class/config、`q.fetch.metadata.rt` MARK 6/6、外网请求数和 gate/queue/DB/ES/port-forward cleanup 证据。 |
| `docs/reports/090-02806-douban-provider-first-slice.md` | Douban provider first slice 完成报告，记录 provider implementation/catalog、配置优先级、focused tests 和真实 Douban readonly smoke HTTP 200。 |
| `docs/reports/092-02810i-notification-smtp-staging-one-shot-live-smoke.md` | notification SMTP one-shot live smoke 安全门禁报告；真实 SMTP 未发送，send history 表和 ops endpoint 可查但无新增 row。 |
| `docs/reports/093-v1-v3-parity-progress-ledger.md` | 本轮已结束 worker / subagent 结果收敛总账；统一记录 028-06-I、028-10-K/L/M/N、C1 first closure/C1-S2c、C2、C3-S6/S8/S9、028-16-D/E/F、028-12-C、028-19、028-07 的完成状态、smoke/test 证据、整体进度估算、测试债务和统一测试后置清单。 |
| `docs/reports/094-02806g-douban-metadata-worker-live-image-queue-parity.md` | 028-06-G metadata-worker live 镜像确认/rollout 与 Douban full queue parity 复跑报告。 |
| `docs/reports/098-02810k-notification-worker-harbor-push-retention-smoke.md` | 028-10-K Harbor push unblock 与 retention cleanup dev DB smoke 完成报告；notification-worker ARM64 image push、cleanup Job、DB/Job cleanup、SMTP 未发送证据齐备。 |
| `docs/reports/100-02810m-notification-worker-crashloop-ready-recovery.md` | 028-10-M notification-worker CrashLoop Ready 恢复报告；`CredentialServiceMailCredentialRepository` 构造器问题已修复，Ready 1/1、restart 0、SMTP 未发送。 |
| `docs/reports/101-02810l-notification-smtp-v1-live-smoke.md` | 028-10-L V1 SMTP credential one-shot 完成报告；真实发送 1 封，send history/ops endpoint 与用户收件箱确认通过，runtime/port-forward 清理完成。 |
| `docs/reports/102-02810n-notification-maintenance-runner-safety-gate.md` | 028-10-N notification maintenance runner safety gate 完成报告；默认关闭、enabled 后默认 dry-run、真实删除双开关、`worker_job_audit_events`、dev smoke PASS、SMTP/mail queue 未触发、finalSmokeResidue=0。 |
| `docs/reports/103-02818-k8s-resource-budget-rollback-verifier.md` | 028-18 K8S dev/staging 资源预算与回滚 verifier；028-18-B 已修复 `traffic-manager` resources 与 live gate 缺口，重跑 MARK 7/7 且 summary PASSED。 |
| `docs/reports/104-docs-long-file-split-cleanup.md` | docs 超长 Markdown 拆分清理报告。 |
| `docs/reports/105-02816e-llm-cleaning-gated-parity.md` | 028-16-E LLM cleaning gated parity first closure；默认关闭、dry-run/fake path、credential-service lease path 和后置 live 测试计划。 |
| `docs/reports/106-02812c-worker-scheduler-runner-audit-expansion.md` | 028-12-C worker/scheduler/runner audit expansion；metadata/normalization/storage worker-job audit implementation first closure 与 live/ops smoke `MARK 6/6`。 |
| `docs/reports/106-02819-production-like-smoke-orchestrator-prep.md` | 028-19 production-like smoke orchestrator plan-only 入口；统一测试执行未开始，真实执行需 `-Execute` 与外部依赖 allow flag。 |
| `docs/reports/107-system-enhancement-checklist.md` | 系统增强清单：分布式协调问题、代码质量审计、架构改进建议、优先级排序。 |
| `docs/reports/108-02820-ops-maintenance-scheduler-cluster-lease.md` | 028-20 ops maintenance scheduler cluster lease：shared Redis lease 抽象与 ops scheduler 跨副本互斥第一刀。 |
| `docs/reports/109-02820-distributed-lock-manager-multi-service-rollout.md` | 028-20 distributed lock manager multi-service rollout：mutex lease、时间片预占用和令牌桶扩展到主要冲突入口。 |
| `docs/reports/110-02820-fine-grained-lock-audit.md` | 028-20 fine-grained lock audit：复核全服务粗锁，拆除 LLM cleaning/search outbox/aggregation batch 吞吐型外层 mutex。 |
| `docs/reports/111-ircs-dev-container-image-freshness-audit.md` | `ircs-dev` 运行中容器镜像 freshness 对账；Harbor 镜像 CURRENT_TAG_DIGEST=22，外部/基础设施镜像=6，STALE=0。 |
| `docs/reports/112-no-autowired-single-constructor-dev-rollout.md` | 生产代码 no `@Autowired` 与单构造器风险收敛，content-service Redis env rollout 恢复，`ircs-dev` 25/25 Ready、运行中 digest 审计和 readiness smoke。 |
| `docs/reports/113-02820-prometheus-health-shared-env-live.md` | `ircs-dev` Prometheus ServiceMonitor/PodMonitor、Actuator health components、Redis shared env 收敛与 BFF CrashLoop 修复；20/20 Prometheus targets UP，20/20 Java root health UP。 |
| `docs/reports/114-02820-mtls-live-rollout.md` | 真实 mTLS first slice 上线：credential/search 强 client cert、covered clients JVM mTLS、search token gate、Prometheus HTTPS+mTLS scrape、live smoke 5/5。 |
| `docs/reports/115-02820-internal-server-mtls-rollout.md` | Service-backed internal server 全量 mTLS 上线：16 个 Spring/BFF HTTPS+mTLS、gateway 持证访问 BFF、Prometheus HTTPS+mTLS scrape、live smoke 20/20。 |
| `docs/reports/116-02820-manual-mtls-removal.md` | 手动应用层 mTLS 从 repo 与 `ircs-dev` live 拆除：25/25 Ready、HTTP readiness 20/20、Prometheus 20/20 UP、HTTPS/mTLS targets=0、secrets 降到 5/30。 |
| `docs/reports/117-02810-02812-runner-audit-closeout.md` | 028-10/028-12 runner audit closeout：`search-reindex-raw` 升级为 capped dev-safe owner runner，manual maintenance session 下传 correlationId，`ingestion-worker` raw/playlist queue consumer 接入 worker-job audit；ops-service 85/85、ingestion-worker 8/8 tests 通过。 |
| `docs/reports/118-02822a-unified-recalculate-owner-runner.md` | 028-22-A unified-recalculate owner runner：aggregation-worker authenticated capped dirty recalc API、ops aggregation owner client、runner audit；focused tests 通过。 |
| `docs/reports/119-02822b-aggregation-reset-owner-runner.md` | 028-22-B aggregation-reset owner runner：aggregation/search/ops 多 owner 编排，按 V1 顺序执行 prepare、hard reset unified index、raw pending；受影响服务测试通过。 |
| `docs/reports/120-02822c-sanitize-owner-runner.md` | 028-22-C sanitize owner runner：normalization-worker reset-normalization owner API、ops owner client 和 runner audit；保持 V1 只更新 normalization status/updated_at 的副作用，受影响服务测试通过。 |
| `docs/reports/121-02822d-trend-sync-owner-runner.md` | 028-22-D trend-sync owner runner：scraper-service Douban/TMDB provider 汇聚、content-service trend apply 数据副作用、ops runner audit；受影响服务测试通过，真实 provider smoke 后置。 |
| `docs/reports/122-02822e-trend-discovery-credential-lease.md` | 028-22-E trend background discovery 与 TMDB credential-service lease：task-service discovery owner API、scraper discovery client、TMDB lease 优先/env 兜底；受影响服务测试通过，live 后置。 |
| `docs/reports/123-02822f-trend-discovery-live-orchestrator.md` | 028-22-F trend discovery live orchestrator：no-external smoke、task/scraper/ops rollout、manifest apply 修复、运行 digest 和 provider-gated 外部窗口边界。 |

## 任务入口

任务导航见：

```text
tasks/index.md
```

主任务：

```text
tasks/in-progress/011-mvp-service-onboarding-parent.md
tasks/in-progress/025-v1-route-coverage-closeout-parent.md
tasks/in-progress/028-v1-v3-behavior-parity-parent.md
```

当前进行中子任务：

```text
tasks/in-progress/028-10-production-governor-credential-pool-parity.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
tasks/in-progress/028-16-v1-v3-deep-parity-gap-audit-parent.md
tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md
tasks/in-progress/028-16-a1-canonical-rawmetadata-contract.md
tasks/in-progress/028-16-a2-text-standardization-title-policy.md
tasks/in-progress/028-16-a3-metadata-result-deep-merge.md
tasks/in-progress/028-16-a9-title-location-genre-pipeline-first-closure.md
tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md
tasks/in-progress/028-16-b1-aggregation-matching-golden-fixtures.md
tasks/in-progress/028-16-b2-duplicate-unified-merge-victim-delete.md
tasks/in-progress/028-16-b3-unified-relation-union-victim-merge.md
tasks/in-progress/028-16-b4-victim-scalar-merge.md
tasks/in-progress/028-16-b5-raw-source-pipeline-category-relation.md
tasks/in-progress/028-16-b6-raw-source-pipeline-scalar-fields.md
tasks/in-progress/028-16-b7-raw-source-pipeline-cover-image.md
tasks/in-progress/028-16-b8-cover-r2-promote-side-effect.md
tasks/in-progress/028-16-b9-jgrapht-raw-cluster-first-slice.md
tasks/in-progress/028-16-b10-raw-unified-context-graph-first-slice.md
tasks/in-progress/028-16-b11-category-bucket-graph-first-slice.md
tasks/in-progress/028-16-b12-graph-scoring-metadata-penalty-first-slice.md
tasks/in-progress/028-16-b13-es-backed-context-recall-first-slice.md
tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md
tasks/in-progress/028-16-c1-initialization-readiness-gate.md
tasks/in-progress/028-16-c1-s1-catalog-dictionary-seed.md
tasks/in-progress/028-16-c1-s2-data-source-preset-seed.md
tasks/in-progress/028-16-c1-s2b-remote-category-sync-job.md
tasks/in-progress/028-16-c3-shared-outbound-http-client-parity.md
tasks/in-progress/028-16-d-tika-file-parsing-security-parity.md
tasks/in-progress/028-16-e-llm-cleaning-gated-parity.md
tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md
```

最近完成的行为 parity 子任务：

```text
tasks/done/028-05-b-storage-avatar-sync-consumer-parity.md
tasks/done/028-05-c-metadata-provider-fanout-parity.md
tasks/done/028-05-d-search-producer-matrix-parity.md
tasks/done/028-05-e-metadata-provider-worker-enrichment-parity.md
tasks/done/028-05-f-normalization-raw-relation-parity.md
tasks/done/028-05-g-metadata-pending-gather-parity.md
tasks/done/028-05-h-storage-image-download-parity.md
tasks/done/028-05-i-mail-notification-disabled-failure-parity.md
tasks/done/028-05-queue-consumer-deep-side-effect-parity-audit.md
tasks/done/028-08-normalization-deep-mapping-parity.md
tasks/done/028-09-search-producer-live-smoke-parity.md
tasks/done/028-11-per-service-audit-collection-parity.md
tasks/done/028-13-parallel-subagent-behavior-parity-integration.md
tasks/done/028-06-c-magnet-yts-bz-runner-contract.md
tasks/done/028-06-e-douban-provider-implementation-first-slice.md
tasks/done/028-06-h-rt-provider-first-slice.md
tasks/done/028-06-i-rt-provider-arm64-live-rollout-queue-parity.md
tasks/done/028-06-real-provider-live-parity.md
tasks/done/028-07-c-storage-service-r2-path-smoke.md
tasks/done/028-07-d-avatar-r2-live-smoke.md
tasks/done/028-07-e-dev-r2-bucket-policy-guard.md
tasks/done/028-07-f-explicit-v1-bucket-smoke-preflight.md
tasks/done/028-07-g-real-r2-explicit-v1-bucket-write-smoke.md
tasks/done/028-07-h-r2-cover-explicit-v1-bucket-chain-smoke.md
tasks/done/028-07-i-storage-service-r2-avatar-path-smoke.md
tasks/done/028-07-j-storage-r2-watchdog-recovery-smoke.md
tasks/done/028-07-r2-live-watchdog-parity.md
tasks/done/028-10-b-mail-credential-adapter-fake-sink.md
tasks/done/028-10-c-ops-runner-registry-dev-gate.md
tasks/done/028-10-d-notification-send-history-fake-sink.md
tasks/done/028-10-e-notification-send-history-schema-ops-query.md
tasks/done/028-10-f-smtp-staging-live-preflight.md
tasks/done/028-10-g-send-history-migrator-dev-deploy-smoke.md
tasks/done/028-10-h-notification-mail-send-history-dev-migration-ops-query.md
tasks/done/028-10-i-notification-smtp-staging-one-shot-live-smoke.md
tasks/done/028-10-j-notification-mail-send-history-retention-cleanup.md
tasks/done/028-10-k-notification-worker-harbor-push-retention-smoke.md
tasks/done/028-10-l-notification-smtp-v1-live-smoke.md
tasks/done/028-10-m-notification-worker-crashloop-ready-recovery.md
tasks/done/028-12-a-task-service-runner-audit.md
tasks/done/028-12-b-aggregation-scheduler-audit.md
tasks/done/028-15-jdk25-mockito-test-runtime.md
tasks/done/028-16-c2-s4-system-config-key-catalog-parity.md
tasks/done/028-16-c2-s5-search-cover-url-config-parity.md
tasks/done/028-16-c2-s10-aggregation-batch-config-parity.md
tasks/done/028-16-c2-s11-notification-mail-config-parity.md
tasks/done/028-16-c2-s12-metadata-provider-routing-config-parity.md
tasks/done/028-16-c2-s13-ops-maintenance-reindex-config-parity.md
tasks/done/028-16-c1-s9-task-service-watchdog-start-gate.md
tasks/done/028-16-c2-s14-content-scraper-base-url-config-parity.md
tasks/done/028-16-c2-s15-live-matrix-smoke-harness.md
tasks/done/028-16-c3-s4a-metadata-tmdb-credential-internal-service-policy.md
tasks/done/028-16-c3-bff-proxy-admin-target-validation.md
tasks/in-progress/028-16-a6-genre-synonym-suffix-protected-term-policy.md
tasks/in-progress/028-16-a5-area-language-composite-alias-policy.md
```

当前 backlog 子任务：

```text
tasks/backlog/014-02-legacy-doc-language-normalization.md
```

## 并行开发约束

后续使用 subagent 并行开发时，必须遵守：

- 每个 subagent 只负责一个明确服务切片或一个明确文档/验证切片。
- 每个 subagent 必须拥有互不重叠的写入范围。
- 每个服务切片开始前必须先补齐对应 task spec。
- 每个服务切片不得新增 HTTPRoute，除非用户明确要求。
- `ircs-dev` Java 服务默认资源 profile 为 `request 25m/128Mi`、`limit 250m/512Mi`。
- 当前 ResourceQuota 中 `pods=28/30`、`services=24/26`，仍保留 2 个缓冲 Pod 和
  2 个缓冲 Service。
- ARM64 镜像必须推送到 dev 使用的 Harbor tag，不能把 x86 镜像推到 `ircs-dev` 使用的 tag。
- 子任务完成后必须回写迁移记录、K8S 快照、任务状态和验证结果。
