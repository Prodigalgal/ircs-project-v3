# IRCS V3 本地任务入口

状态：进行中

更新时间：2026-06-14

## 任务规则

任务体系采用父任务、子任务和 task spec：

- 父任务承接需求、报告、决策或迁移记录。
- 子任务必须归属于父任务，并具备独立输出、边界、依赖和验证方式。
- 每个 task spec 至少记录背景、目标、非目标、输入、输出、影响范围、风险、验收标准和测试边界。
- 2026-06-13 起，后续验证禁止本地 smoke、禁止 mock/fake/造数；只能连接集群使用真实数据、
  真实配置、真实凭据和真实外部链路。

## 进行中任务

| 任务 | 来源 | 当前状态 |
| --- | --- | --- |
| `tasks/in-progress/030-production-candidate-k8s-real-validation-closeout.md` | `docs/requirements/030-production-candidate-k8s-real-validation-closeout.md` | 新主线，进行中；所有验证只允许连接 `ircs-dev` K8S 集群，使用真实数据/配置/凭据/外部链路；本轮已创建 goal、刷新集群/Rabbit 基线、压缩归档第一批超长文档，并完成 029 trend discovery、detail retry 自恢复、terminal detail 失败、DoT 默认修复、IPv6 限制判因、25 源一页真实任务矩阵和源站网络核心策略矩阵 |
| `tasks/in-progress/011-mvp-service-onboarding-parent.md` | `docs/requirements/011-mvp-service-onboarding-acceleration.md` | 进行中 |
| `tasks/in-progress/025-v1-route-coverage-closeout-parent.md` | `docs/requirements/025-v1-route-coverage-closeout.md` | 进行中 |
| `tasks/in-progress/028-v1-v3-behavior-parity-parent.md` | `docs/requirements/028-v1-v3-behavior-parity-closeout.md` | 进行中 |
| `tasks/in-progress/028-10-production-governor-credential-pool-parity.md` | `docs/reports/041-v1-v3-behavior-parity-gap-audit.md` | 进行中，production governor/MAIL pool MARK 3/3、MAIL fake sink MARK 5/5、ops runner registry MARK 5/5、notification send history fake sink、`028-10-E/G/H` send history schema/migrator/dev DB/ops query 已完成；`028-10-I` one-shot 安全门禁已阻断真实 SMTP 且未发送；`028-10-J/K` retention cleanup dev DB smoke 已完成；`028-10-M` notification-worker CrashLoop 已恢复 Ready 并被后续 SMTP smoke 覆盖；`028-10-L` V1 SMTP one-shot 真实发送 1 封并完成 send history/ops/收件箱确认；`028-10-N` notification maintenance runner gate MARK 6/6；`028-10-O/028-12-E` 已补 raw reindex capped owner runner、manual session correlationId 和 ingestion-worker audit；`028-22-A/B/C/D/E` 已补 unified-recalculate、aggregation-reset、sanitize、trend-sync owner runner、background discovery 与 TMDB credential lease；runner/governor 基座已无已知占位拒绝缺口 |
| `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md` | `docs/reports/055-per-service-audit-collection-parity-analysis.md` | 进行中，HTTP service handler audit、notification worker/job audit、task-service runner/watchdog/startup recovery audit、aggregation-worker scheduler audit、028-12-C/D implementation first closure 已完成；`UTB-02816-P1P2-03` 重跑 7/7 PASSED，notification/task/search/scraper/resource 有 live/UTB 证据；2026-06-12 metadata/normalization/storage live worker audit smoke MARK 6/6 PASSED，container freshness CURRENT_TAG_DIGEST=22；`028-12-E` 已补 ingestion-worker raw/playlist queue consumer worker-job audit，当前已知 worker/job audit matrix 实现闭环，live rollout/smoke 后置 |
| `tasks/in-progress/028-19-production-like-smoke-orchestrator-prep.md` | `docs/reports/106-02819-production-like-smoke-orchestrator-prep.md` | prep 完成，统一测试已开始；`UTB-02816-P1P2-02` no-real-cost/dry-run 子集通过，`UTB-02816-P1P2-03` 在 migrator/notification 修复后重跑通过，`RESOURCE-02818` 后置检查通过；orchestrator 已修复非 0 exit code 假绿问题，真实 LLM/C3 live/外部 provider/SMTP/R2 仍后置 |
| `tasks/in-progress/028-20-post-parity-runtime-platform-breakdown.md` | `docs/decisions/005-post-parity-runtime-platform-decisions.md` | 进行中，已固化 32 项拍板结果并拆成 15 个实现切片；`028-20-01` 到 `028-20-09D` 已完成，`028-20-10` 应用层 service identity/scopes 已完成，手动 K8S mTLS rollout 历史任务 `028-20-25/26` 已由 `028-20-27` 撤销；`028-20-11A` Tika standard package、`028-20-11B` LLM maintenance owner runner、`028-20-11C` storage media R2 canonical cleanup/health、`028-20-11D` normalization LLM credential shared outbound、`028-20-12A` search outbox relay observability、`028-20-12B` worker/job audit source 唯一化、`028-20-12C` worker/job Micrometer 基础指标、`028-20-13A` portal read-model multi-tier cache、`028-20-13B` catalog standard dictionary multi-tier cache、`028-20-13C` search portal read-model multi-tier cache、`028-20-13D` magnet provider/approved-links read-model cache、`028-20-13E` credential redacted summary read-model cache、`028-20-14` ops maintenance scheduler cluster lease、`028-20-15` distributed lock manager multi-service rollout、`028-20-22` distributed lock observability、`028-20-23` no `@Autowired` / 单构造器 / dev rollout health、`028-20-24` Prometheus/health/shared Redis env live、`028-20-27` manual mTLS removal 已完成；剩余 `028-20-11` remaining library follow-up、Redis lock contention live 查询、`028-20-13` 后续热读 owner 扩展和统一业务行为测试 |
| `tasks/in-progress/028-21-security-source-network-hardening.md` | `docs/migrations/093-security-source-network-hardening.md` / `docs/decisions/006-pow-and-source-transport-upgrade.md` | 进行中，JWT/ops/storage/auth rate limit/cover size/source network hardening 已完成实现；新增 header-based scoped PoW、登录数学验证码、portal/admin 前端适配、admin 数据源网络策略 UI、`transport_mode`、Apache HC5 source transport、DoH/DoT/IP policy resolver；后端 compile guard 与 portal/admin frontend build 通过，行为/live 测试后置 |
| `tasks/in-progress/028-22-production-candidate-closeout-parent.md` | `docs/requirements/028-22-production-candidate-closeout-goal.md` | 进行中，已把剩余开发、destructive/high-write runner、统一测试、真实外部依赖和生产候选准入编制到同一目标；`028-22-A/B/C/D/E` 开发切片已完成 focused/affected tests，`028-22-F` no-external live smoke 已通过，剩余 provider-gated 外部窗口与生产候选报告 |
| `tasks/in-progress/028-22-f-unified-live-smoke-execution.md` | `docs/reports/123-02822f-trend-discovery-live-orchestrator.md` | 进行中，trend discovery no-external live smoke、task/scraper/ops rollout、manifest apply 修复已完成；Douban/TMDB provider-gated window 待显式执行 |
| `tasks/in-progress/028-16-f6-source-field-auto-mapping-hardening.md` | `docs/migrations/094-source-field-auto-mapping-hardening.md` | 实现完成，admin 数据源向导自动映射升级为打分式 CMS/VOD 字段识别，手动映射保留且限制为 JSONPath 下拉点选，scraper 后端补 `categoryName` -> canonical `rawTypeName` alias；Node 映射/手动保留探针、admin frontend build、scraper mapping focused test 通过，真实资源站 live matrix 后置 |
| `tasks/in-progress/029-rabbit-redis-task-system-redesign.md` | `docs/requirements/029-rabbit-redis-task-system-redesign.md` / `docs/decisions/008-rabbit-redis-mt-pt-dt-task-system.md` | 进行中，约 92%；Rabbit MT/PT/DT、hot-state、DB flush、Admin/Ops runtime、DLQ 运维、source failure classifier、manual ack/retry/DLQ 已上线；真实集群已覆盖成功链路、page/source 失败、detail retry 自恢复、terminal detail 失败、DoT 默认修复、IPv6 限制判因、25 源一页矩阵和网络核心策略；当前 digest：task `sha256:5cfa2a92833305052f7eabd2300ecf7030439e5becaa199411dc86a7ba46da96`，scraper `sha256:8ed6a7cd0ad659cc92f7a5acc62db032d49cb5dd7ccfc3273f5fa127b8b1fdb2`，catalog `sha256:0a1c221fc60be117bf4f3681da5441aa1aa8ae46cb4ae86f94a97ffcd06656a9`，ops `sha256:47cd308b9465642c9e475d91f2156a956f8492a02e5fdcf35a7b2fe14f40cddb`；剩 provider-gated trend-sync、Admin 网络策略展示、Prometheus 指标查询和多副本 contention 证据 |
| `tasks/done/028-20-16-fine-grained-lock-audit-first-pass.md` | `docs/reports/110-02820-fine-grained-lock-audit.md` | 已完成第一批粗锁排查和拆分：LLM cleaning 改 DB claim lease，search outbox relay / aggregation batch 移除吞吐型外层 mutex；当时遗留的 normalization retry watchdog 已由 `028-20-18` 关闭 |
| `tasks/done/028-20-17-storage-r2-watchdog-claim-first.md` | `docs/reports/110-02820-fine-grained-lock-audit.md` | 已完成 storage R2 watchdog claim-first：avatar member claim lease、cover `UPLOADING` claim、移除外层 `storage:r2-watchdog` mutex；K8S 多副本 contention smoke 后置 |
| `tasks/done/028-20-18-normalization-retry-watchdog-claim-first.md` | `docs/reports/110-02820-fine-grained-lock-audit.md` | 已完成 normalization retry watchdog claim-first：due failed/stale active raw video 使用 `FOR UPDATE SKIP LOCKED` 行级认领，删除 normalization cluster-lock runner 与死配置；K8S 多副本 contention smoke 后置 |
| `tasks/done/028-20-19-aggregation-cluster-lock-dead-config-cleanup.md` | `docs/reports/110-02820-fine-grained-lock-audit.md` | 已确认 aggregation-worker 无服务级粗锁，按 `raw_videos` row claim / batch 并发；删除 scheduler cluster-lock 死配置；K8S 多副本 contention smoke 后置 |
| `tasks/done/028-20-20-aggregation-match-key-fine-grained-lock.md` | `docs/reports/110-02820-fine-grained-lock-audit.md` | 已补 aggregation match-key 细粒度 Redis mutex，按 external id/title fingerprint/context unified id 防并发重复 unified；锁持有到 DB 事务完成；K8S 两副本 contention smoke 后置 |
| `tasks/done/028-20-21-db-claim-release-semantics.md` | `docs/reports/110-02820-fine-grained-lock-audit.md` | 已补 DB claim 释放语义：LLM cleaning provider timeout/error 显式 release；normalization retry publish 失败恢复未发布 raw 为 `FAILED + next_retry_time`；focused tests/bootJar 通过 |
| `tasks/done/028-20-22-distributed-lock-observability.md` | `docs/reports/109-02820-distributed-lock-manager-multi-service-rollout.md` | 已补 shared distributed lock metrics/health：mutex、时间片、令牌桶统一记录 `ircs.distributed.lock.*`；`distributedLock` health 暴露 Redis backend；focused tests 与代表服务 bootJar 通过 |
| `tasks/done/028-20-23-no-autowired-single-constructor-dev-rollout.md` | `docs/reports/112-no-autowired-single-constructor-dev-rollout.md` | 已完成生产代码 no `@Autowired` 与单构造器风险审计，content-service Redis env apply/rollout，`ircs-dev` 25/25 Ready、digest 审计和 readiness smoke |
| `tasks/done/028-20-24-prometheus-health-shared-env-live.md` | `docs/reports/113-02820-prometheus-health-shared-env-live.md` | 已完成 `ircs-dev` ServiceMonitor/PodMonitor、Actuator components 展开、Redis shared env 收敛、BFF CrashLoop 修复；20/20 Prometheus targets UP，20/20 Java root health UP |
| `tasks/done/028-20-25-mtls-live-rollout.md` | `docs/reports/114-02820-mtls-live-rollout.md` | 历史完成真实 mTLS first slice，后续已由 `028-20-27` 全量撤销，不再作为当前目标态 |
| `tasks/done/028-20-26-internal-server-mtls-rollout.md` | `docs/reports/115-02820-internal-server-mtls-rollout.md` | 历史完成 Service-backed internal server 全量 mTLS，后续已由 `028-20-27` 全量撤销，不再作为当前目标态 |
| `tasks/done/028-20-27-manual-mtls-removal.md` | `docs/reports/116-02820-manual-mtls-removal.md` | 已撤销手动应用层 mTLS：删除 repo 证书/rollout/smoke 入口，live 清理 Spring/Nginx/Prometheus/cert-manager mTLS 配置；25/25 Ready，HTTP readiness 20/20，Prometheus 20/20 UP，HTTPS/mTLS targets=0，secrets 5/30 |

## 已完成任务

| 任务 | 输出 |
| --- | --- |
| `tasks/done/010-02-credential-internal-token-hardening.md` | credential-service internal API token hardening |
| `tasks/done/011-01-catalog-service-readonly-mvp.md` | catalog-service read-only MVP |
| `tasks/done/012-01-config-service-readonly-mvp.md` | config-service read-only MVP |
| `tasks/done/012-02-dev-resource-compression-for-mvp-onboarding.md` | dev resource compression |
| `tasks/done/013-01-task-service-readonly-mvp.md` | task-service read-only MVP |
| `tasks/done/014-01-documentation-organization.md` | 文档目录与索引整理 |
| `tasks/done/104-docs-long-file-split-cleanup.md` | docs 超长 Markdown 拆分清理 |
| `tasks/done/028-20-01-async-logger-disruptor-runtime-parity.md` | 全业务服务启用 Disruptor 与共享 V1 风格 `log4j2-spring.xml`；runtimeClasspath、shared jar resource 和代表服务 bootJar 验证通过 |
| `tasks/done/028-20-02-search-persistent-outbox-relay-reconciliation.md` | V1 search persistent outbox 生产级实现：事务入账、lease relay、retry/dead-letter、reconciliation、业务 producer 迁移；focused tests 与 affected bootJar 通过 |
| `tasks/done/028-20-03-uuid-time-based-epoch-new-writes.md` | V1 timeBasedEpoch UUID 语义恢复：生产新增业务 ID 切到 `IrcsUuidGenerators.nextId()`，随机 UUID 仅保留安全 token、边界、临时文件和 fallback secret；focused tests 与 affected bootJar 通过 |
| `tasks/done/028-20-04-v1-main-title-display-search-compat.md` | V1 mainTitle title 语义恢复：normalization 写入 `title=mainTitle`，`season/subtitle/aliasTitle` 保持原子字段，search variants 补齐组合检索；affected tests 与 bootJar 通过 |
| `tasks/done/028-20-05-unified-error-model-portal-admin.md` | 统一错误模型：共享 error envelope、trace/correlation headers、服务/BFF advice、JWT/filter/catalog/config 手写错误替换；affected tests 与 bootJar 通过 |
| `tasks/done/028-20-06-rbac-permission-data-scope-visibility.md` | RBAC/permission/data scope/content visibility：旧 role-only token 破坏性失效，BFF route permission gate，portal/search category/genre/tag scope 过滤，content visibility/tag 模型；focused tests 与 bootJar 通过 |
| `tasks/done/028-20-07-versioned-config-credential-cache-governance.md` | versioned config/credential/cache governance：配置事件破坏性升级 revision/eventId，credential lease TTL cache，shared cache summary/evict endpoint；focused tests 与 bootJar 通过 |
| `tasks/done/028-20-08-audit-archive-retention-governance.md` | 三类审计、ES replication outbox、统一 archive table、notification retention 先归档后删除、ops audit governance summary；focused tests、affected service tests 与 bootJar 通过 |
| `tasks/done/028-20-09-ops-maintenance-scheduler.md` | ops-service 统一 maintenance scheduler：默认 disabled/dry-run，execute gate 默认开启，scheduler audit/status/run-once API，`search-reindex-unified` 通过 search-service owner API 执行；focused tests 与 bootJar 通过 |
| `tasks/done/028-20-09b-maintenance-scoped-write-gate.md` | maintenance scoped write/consumer gate：`maintenance_operations` 持久模型、ops gate API、shared inspector、统一 423 错误语义；focused tests、ops-service bootJar 与 migrator bootJar 通过 |
| `tasks/done/028-20-09c-content-maintenance-write-gate.md` | content-service 首个 owner 写 gate：raw/unified video 写入口接入 generic `video/*` 与 specific `raw-video|unified-video/<id>` scoped gate；focused tests 与 bootJar 通过 |
| `tasks/done/028-20-09d-maintenance-gate-versioned-event-redis-cache.md` | maintenance gate versioned event + Redis hot cache：ops create/close 发布 `maintenance.gate.changed`，content-service L1 local + L2 Redis blocked cache + L3 DB truth，事件驱动失效；focused tests 与 bootJar 通过 |
| `tasks/done/028-20-10-mtls-service-identity-destructive-migration.md` | 应用层 service identity/scopes 破坏性迁移：旧 internal token header 服务端失效，credential/search internal API 与调用方切新 headers；focused tests 与 bootJar 通过，K8S mTLS rollout 后续已由 `028-20-27` 撤销 |
| `tasks/done/028-20-10b-k8s-mtls-first-slice.md` | 历史 K8S mTLS rollout 入口：cert-manager dev CA/certificates、PKCS12 keystore/truststore、dry-run rollout script 与 smoke parser 曾通过；入口文件后续已由 `028-20-27` 删除 |
| `tasks/done/028-20-11a-tika-standard-package-service-scoped.md` | Tika standard package 按服务边界接回：scraper-service 切到 `tika-parsers-standard-package:3.2.2`，storage-service 保持 `tika-core` only；focused tests、Tika smoke 与 scraper bootJar 通过 |
| `tasks/done/028-20-11b-llm-maintenance-owner-runner.md` | LLM maintenance owner runner：normalization-worker internal LLM cleaning API、ops-service owner client/registry、通用 SSE runner 文案、dev ClusterIP Service；focused tests 与 bootJar 通过 |
| `tasks/done/028-20-11c-storage-media-r2-canonical-cleanup-health.md` | storage media R2 canonical cleanup/health：R2 成功后按 `download-enabled` 清理本地临时副本，补回 R2 health component；focused tests 与 storage bootJar 通过 |
| `tasks/done/028-20-11d-normalization-llm-credential-shared-outbound.md` | normalization LLM credential resolver 迁移 shared `OutboundHttpClient`：`INTERNAL_SERVICE` policy、service identity headers、caller circuit key 与稳定错误语义；focused tests 与 normalization bootJar 通过 |
| `tasks/done/028-20-12a-search-outbox-relay-observability.md` | search outbox relay observability：唯一 workerId、health component、Micrometer run/group/task delay 指标、K8S Pod 名注入 workerId；focused tests 通过 |
| `tasks/done/028-20-12b-worker-job-audit-instance-source.md` | worker/job audit source 唯一化：默认 `job_source=service@host#pid`，支持显式 worker instance id，保留 service prefix；focused tests 与 affected bootJar 通过 |
| `tasks/done/028-20-12c-worker-job-micrometer-baseline.md` | worker/job Micrometer 基础指标：`WorkerJobAuditWriter.record` 统一记录 `ircs.worker.job.runs` 与 `ircs.worker.job.duration`，覆盖已接 audit 的 consumer/runner/scheduler；focused tests 与 affected bootJar 通过 |
| `tasks/done/028-20-13a-portal-read-model-multi-tier-cache.md` | 多级业务缓存第一刀：shared `TieredReadThroughCache` 基座，portal 匿名公共 metadata/home/explore/detail 接 L1 local + L2 Redis/Valkey + L3 DB fallback；focused tests 与 portal bootJar 通过 |
| `tasks/done/028-20-13b-catalog-standard-dictionary-multi-tier-cache.md` | 多级业务缓存第二刀：catalog 标准分类/类型/地区/语言 summary/read list 接 L1 local + L2 Redis/Valkey + L3 DB fallback，标准字典写后 evict；focused tests、catalog bootJar 与 K8S manifest dry-run 通过 |
| `tasks/done/028-20-13c-search-portal-read-model-cache.md` | 多级业务缓存第三刀：search portal suggest/recommend 匿名公共热读接 L1 local + L2 Redis/Valkey + Elasticsearch fallback，scoped 读绕过缓存，unified index 写/删后 evict；focused tests、search bootJar 与 K8S manifest dry-run 通过 |
| `tasks/done/028-20-13d-magnet-provider-link-read-model-cache.md` | 多级业务缓存第四刀：magnet provider list/detail/enabled-list 与 approved links 接 L1 local + L2 Redis/Valkey + DB fallback，provider 写入和 link upsert 后 evict；focused tests、magnet bootJar 与 K8S manifest dry-run 通过 |
| `tasks/done/028-20-13e-credential-redacted-summary-cache.md` | 多级业务缓存第五刀：credential redacted summary list/detail 接 L1 local + L2 Redis/Valkey + DB fallback，secret lease payload 不写 Redis，credential 写入/refresh 后 evict；focused tests、credential bootJar 与 K8S manifest dry-run 通过 |
| `tasks/done/028-20-14-ops-maintenance-scheduler-cluster-lease.md` | Redis 分布式 lease 抽象第一刀：shared `ClusterLeaseService` + Redis SET NX/Lua release/renew，ops maintenance scheduler 跨副本互斥；focused tests 与 ops bootJar 通过 |
| `tasks/done/028-20-15-distributed-lock-manager-multi-service-rollout.md` | 分布式锁抽象全服务扩展：scraper/magnet 时间片预占用，task/search/aggregation/normalization/config/catalog/content/credential/storage/notification 关键冲突入口互斥；focused tests 与 affected bootJar 通过 |
| `tasks/done/028-20-22-distributed-lock-observability.md` | shared distributed lock metrics/health：mutex、时间片、令牌桶统一 Micrometer 结果/耗时指标，Actuator `distributedLock` health 暴露 Redis backend；focused tests 与代表服务 bootJar 通过 |
| `tasks/done/028-20-23-no-autowired-single-constructor-dev-rollout.md` | no `@Autowired` / 单构造器风险收敛、content-service Redis env rollout 恢复、`ircs-dev` 25/25 Ready、运行中 digest 审计与 readiness smoke |
| `tasks/done/028-20-24-prometheus-health-shared-env-live.md` | Prometheus ServiceMonitor/PodMonitor live、Actuator components 根 health 展开、Redis shared env ConfigMap + envFrom 收敛、BFF CrashLoop 修复；20/20 targets UP、20/20 Java root health UP |
| `tasks/done/028-20-25-mtls-live-rollout.md` | 历史真实 mTLS first slice live，后续已由 `028-20-27` 全量撤销 |
| `tasks/done/028-20-26-internal-server-mtls-rollout.md` | 历史 Service-backed internal server 全量 mTLS live，后续已由 `028-20-27` 全量撤销 |
| `tasks/done/028-20-27-manual-mtls-removal.md` | 手动应用层 mTLS 撤销：服务间回到 HTTP + app-layer service identity/scopes，旧 mTLS cert/secret/monitor/env/volume/base-url 清零；HTTP readiness 20/20、Prometheus 20/20 UP |
| `tasks/done/015-01-identity-service-auth-profile-mvp.md` | identity-service auth/profile MVP |
| `tasks/done/016-01-scraper-service-manual-scrape-mvp.md` | scraper-service manual scrape MVP |
| `tasks/done/016-02-ingestion-worker-video-ingest-mvp.md` | ingestion-worker video ingest MVP |
| `tasks/done/017-01-portal-read-composition-mvp.md` | portal-service read composition MVP |
| `tasks/done/017-02-interaction-feedback-history-mvp.md` | interaction-service feedback/history MVP |
| `tasks/done/017-03-magnet-provider-cache-mvp.md` | magnet-service provider/cache MVP |
| `tasks/done/018-01-normalization-worker-normalize-mvp.md` | normalization-worker normalize queue MVP |
| `tasks/done/019-01-aggregation-worker-scheduler-mvp.md` | aggregation-worker scheduler MVP |
| `tasks/done/020-01-dev-java-limit-compression-for-next-rollouts.md` | dev Java CPU limit compression |
| `tasks/done/021-01-documentation-state-sync-after-identity-rollout.md` | identity rollout 后文档状态同步 |
| `tasks/done/022-01-dev-worker-service-pruning-for-service-quota.md` | worker-only Service 裁剪释放 dev Service quota |
| `tasks/done/023-01-ops-service-maintenance-dashboard-mvp.md` | ops-service maintenance/dashboard MVP |
| `tasks/done/024-01-dev-pod-quota-adjustment-for-final-rollouts.md` | dev Pod quota 后续服务 rollout 前置调整 |
| `tasks/done/025-01-search-service-portal-query-api-mvp.md` | search-service portal suggest/recommendations API |
| `tasks/done/025-02-portal-service-explore-detail-mvp.md` | portal-service explore/detail API |
| `tasks/done/025-03-interaction-service-portal-write-api-mvp.md` | interaction-service 门户收藏、历史、反馈写接口 |
| `tasks/done/025-04-task-service-lifecycle-api-mvp.md` | task-service 生命周期 command/log/state API |
| `tasks/done/025-05-content-service-raw-unified-admin-api-lossless.md` | content-service raw/unified admin API 21/21 复现 |
| `tasks/done/025-06-storage-service-cover-image-admin-api-lossless.md` | storage-service cover image admin API 7/7 复现 |
| `tasks/done/025-07-ops-service-observability-read-api-mvp.md` | ops-service observability read API 4/4 复现 |
| `tasks/done/025-08-interaction-service-admin-message-api-mvp.md` | interaction-service admin message API 4/4 复现 |
| `tasks/done/025-09-task-service-runner-scheduler-mvp.md` | task-service runner/scheduler runtime contract 1/1 复现 |
| `tasks/done/025-10-identity-service-admin-auth-api-mvp.md` | identity-service admin auth API 3/3 复现 |
| `tasks/done/025-11-identity-service-member-admin-api-mvp.md` | identity-service member admin API 6/6 复现 |
| `tasks/done/025-12-content-service-people-admin-api-mvp.md` | content-service actors/directors admin API 14/14 复现 |
| `tasks/done/025-13-catalog-service-admin-read-contract-mvp.md` | catalog-service admin read contract 21/21 复现 |
| `tasks/done/025-14-config-service-common-timezones-api-mvp.md` | config-service common timezones API 1/1 复现 |
| `tasks/done/025-15-content-service-aux-admin-api-mvp.md` | content-service playlists/source-domains/resolvers admin API 14/14 复现 |
| `tasks/done/025-16-catalog-service-admin-write-contract-mvp.md` | catalog-service admin write contract 25/25 复现 |
| `tasks/done/025-17-config-credential-admin-write-contract-mvp.md` | config/credential admin write contract 9/9 复现 |
| `tasks/done/025-18-magnet-service-admin-write-search-contract-mvp.md` | magnet-service admin provider/search contract 3/3 复现 |
| `tasks/done/025-19-identity-service-portal-avatar-upload-contract-mvp.md` | identity-service portal avatar upload contract 1/1 复现 |
| `tasks/done/025-20-ops-service-guarded-operations-debug-stream-contract-mvp.md` | ops-service guarded operations/debug/stream contract 19/19 复现 |
| `tasks/done/026-01-vps-resource-comparison-before-dev-migration.md` | VPS 资源对比与 dev 迁移判断 |
| `tasks/done/027-01-portal-admin-frontend-deployment-mvp.md` | portal/admin/gateway 三前端 Pod dev 部署和 smoke |
| `tasks/done/027-02-portal-admin-bff-split-mvp.md` | portal-bff/admin-bff 正式拆分、rollout 和 smoke |
| `tasks/done/028-01-bff-request-audit-collection-parity.md` | BFF request audit 采集行为 parity 2/2 复现 |
| `tasks/done/028-02-identity-avatar-media-sync-parity.md` | identity avatar media persistence/sync parity 2/2 复现 |
| `tasks/done/028-03-magnet-provider-search-parity.md` | magnet provider search job/run/link parity 3/3 复现 |
| `tasks/done/028-04-ops-maintenance-runner-parity.md` | ops search-reindex-unified runner parity 3/3 复现 |
| `tasks/done/028-05-a-interaction-watch-progress-consumer-parity.md` | interaction WATCH_PROGRESS queue consumer parity 4/4 复现 |
| `tasks/done/028-05-b-storage-avatar-sync-consumer-parity.md` | storage SYNC_AVATAR consumer parity 4/4 复现 |
| `tasks/done/028-05-c-metadata-provider-fanout-parity.md` | metadata provider fan-out routing parity 1/1 复现 |
| `tasks/done/028-05-d-search-producer-matrix-parity.md` | raw/unified search producer matrix and image unlink refresh parity 2/2 复现 |
| `tasks/done/028-05-e-metadata-provider-worker-enrichment-parity.md` | metadata provider worker/result dev-safe parity 4/4 复现 |
| `tasks/done/028-05-f-normalization-raw-relation-parity.md` | normalization raw relation parity 4/4 复现 |
| `tasks/done/028-05-g-metadata-pending-gather-parity.md` | metadata pending gather parity 4/4 复现 |
| `tasks/done/028-05-h-storage-image-download-parity.md` | image download true/failure parity 5/5 复现 |
| `tasks/done/028-05-i-mail-notification-disabled-failure-parity.md` | mail notification disabled/failure parity 4/4 复现 |
| `tasks/done/028-05-queue-consumer-deep-side-effect-parity-audit.md` | queue consumer deep side-effect parity 审计 |
| `tasks/done/028-08-normalization-deep-mapping-parity.md` | normalization deep mapping、retry watchdog 与 live smoke 6/6 复现 |
| `tasks/done/028-09-search-producer-live-smoke-parity.md` | search producer normalization、metadata-result、aggregation、old dirty lifecycle live smoke 复现 |
| `tasks/done/028-11-per-service-audit-collection-parity.md` | config-service handler audit parity 3/3 复现 |
| `tasks/done/028-13-parallel-subagent-behavior-parity-integration.md` | 028 并行 subagent ownership、ops/scraper handler audit 集成和 live smoke |
| `tasks/done/028-14-v1-credential-import-for-live-parity.md` | V1 credential 导入 V3 dev 凭据池 4/4，TMDB direct/live queue 复验通过 |
| `tasks/done/028-06-c-magnet-yts-bz-runner-contract.md` | magnet YTS_BZ runner contract 5/5，本地 fake HTTP/parser，无公网访问 |
| `tasks/done/028-06-e-douban-provider-implementation-first-slice.md` | Douban provider first slice MARK 6/6，真实 Douban readonly smoke HTTP 200，provider implementation/catalog 阻塞解除，dev gate 保持关闭 |
| `tasks/done/028-06-f-douban-provider-live-queue-parity.md` | Douban provider full queue parity MARK 6/6，`q.fetch.metadata.douban consumers=1`，真实外网请求 1 次，raw metadata/search sync/cleanup 通过 |
| `tasks/done/028-06-g-douban-metadata-worker-live-image-queue-parity.md` | metadata-worker dev 镜像发布并确认 Douban listener/provider，digest `sha256:341baddc3286551eff7970eb4ffb166df67f4fa227e324aecaea3cf66a09b526`，复跑 028-06-F 通过 |
| `tasks/done/028-06-g-retry-douban-provider-full-queue-parity.md` | Douban full queue retry 复验通过，live digest `sha256:341baddc3286551eff7970eb4ffb166df67f4fa227e324aecaea3cf66a09b526`，`q.fetch.metadata.douban consumers=1`，full smoke MARK 6/6，externalRequestCount=1，gate/queue/DB/port-forward cleanup 通过 |
| `tasks/done/028-06-h-rt-provider-first-slice.md` | RT provider first slice MARK 6/6，真实 Rotten Tomatoes readonly HTTP 200，解析 `The Matrix -> matrix`，credential catalog 已支持，dev gate 保持关闭，queue parity 留待 live image rollout |
| `tasks/done/028-06-i-rt-provider-arm64-live-rollout-queue-parity.md` | RT provider ARM64 live rollout/full queue parity MARK 6/6，live digest `sha256:93db92be56a2a7dc0b85d84c7a43ecb8c8f854f5947f1bbd7a6ae2dafc34291a`，externalRequestCount=1，gate/queue/DB/ES/port-forward cleanup 通过 |
| `tasks/done/028-06-real-provider-live-parity.md` | provider enable gate、TMDB、magnet YTS_BZ、Douban 和 RT live parity 全部闭环，028-06 父任务关闭 |
| `tasks/done/028-07-c-storage-service-r2-path-smoke.md` | storage-service R2 path live smoke 6/6，临时启用、对象同步、cleanup 和 dev 恢复 |
| `tasks/done/028-07-d-avatar-r2-live-smoke.md` | avatar upload -> SYNC_AVATAR -> R2 URL 回写 live smoke 7/7，临时启用、对象同步、cleanup 和 dev 恢复 |
| `tasks/done/028-07-e-dev-r2-bucket-policy-guard.md` | dev R2 bucket policy guard 4/4，`ircs-dev` 常驻 bucket 保持 `ircs-dev`，V1 bucket 仅允许显式临时覆盖 |
| `tasks/done/028-07-f-explicit-v1-bucket-smoke-preflight.md` | explicit V1 bucket preflight/no-write guard，R2 smoke 默认只读且写入前必须显式 bucket override |
| `tasks/done/028-07-g-real-r2-explicit-v1-bucket-write-smoke.md` | real R2 explicit V1 bucket object smoke 5/5，PUT/HEAD/DELETE/cleanup HEAD 404 |
| `tasks/done/028-07-h-r2-cover-explicit-v1-bucket-chain-smoke.md` | cover explicit V1 bucket chain smoke 6/6，URL/DB/R2/local/Secret/Deployment cleanup 全链路通过 |
| `tasks/done/028-07-i-storage-service-r2-avatar-path-smoke.md` | avatar explicit V1 bucket chain smoke 7/7，SYNC_AVATAR、R2 URL、R2 HEAD 404、member/local cleanup 全链路通过 |
| `tasks/done/028-07-j-storage-r2-watchdog-recovery-smoke.md` | storage-service R2 watchdog recovery smoke 8/8，due cover 与 local avatar backlog 恢复、R2/DB/local/queue/env cleanup 全链路通过 |
| `tasks/done/028-07-r2-live-watchdog-parity.md` | R2 live/watchdog parity 父任务闭环，credential/object/cover/avatar/bucket guard/watchdog recovery 全部完成 |
| `tasks/done/028-10-b-mail-credential-adapter-fake-sink.md` | notification-worker MAIL credential adapter 与 fake/sink 5/5，不执行真实 SMTP |
| `tasks/done/028-10-c-ops-runner-registry-dev-gate.md` | ops runner registry/dev gate/audit evidence 5/5，不执行高风险 runner |
| `tasks/done/028-10-d-notification-send-history-fake-sink.md` | notification send history fake sink，disabled=SKIPPED、fake success=SENT、fake/credential failure=FAILED，`APP_MAIL_SEND_HISTORY_ENABLED=false` 默认关闭 |
| `tasks/done/028-10-e-notification-send-history-schema-ops-query.md` | notification send history schema / ops query 第一刀完成，本地 build evidence 已记录 |
| `tasks/done/028-10-f-smtp-staging-live-preflight.md` | SMTP staging live preflight 安全门禁，dev 默认不真发 |
| `tasks/done/028-10-g-send-history-migrator-dev-deploy-smoke.md` | send history migrator dev deploy smoke 完成，为 H 的 live DB/ops 查询闭环提供前置 |
| `tasks/done/028-10-h-notification-mail-send-history-dev-migration-ops-query.md` | notification send history dev DB migration + ops-service query live 闭环，migrator/ops-service ARM64 rollout，表/endpoint 可查，未发送 SMTP |
| `tasks/done/028-10-i-notification-smtp-staging-one-shot-live-smoke.md` | notification SMTP one-shot smoke 门禁 6/6，真实 SMTP 未发送，send history 无新增 row；后续允许 Gmail/标准 SMTP 但必须加专用凭据和单收件人 allowlist 等门禁 |
| `tasks/done/028-10-j-notification-mail-send-history-retention-cleanup.md` | notification send history retention cleanup 5/5，旧 fixture 删除、新 fixture 保留后清理，ops summary 可查，未发送 SMTP |
| `tasks/done/028-10-k-notification-worker-harbor-push-retention-smoke.md` | notification-worker Harbor push unblock + retention smoke 5/5，ARM64 image digest `sha256:79462463933647277e6069c61d0081e75ed241ccdff94f264d311d6ee164027b`，无 Job/DB 残留 |
| `tasks/done/028-10-l-notification-smtp-v1-live-smoke.md` | notification SMTP V1 credential one-shot MARK 6/6，真实发送 1 封，send history/ops 查询和用户收件箱确认通过，runtime/port-forward 清理完成 |
| `tasks/done/028-10-m-notification-worker-crashloop-ready-recovery.md` | notification-worker CrashLoop 清障 6/6，ARM64 image digest `sha256:5271750ff0740e1f2aaf520642cb821fd845215f846bffbf8c0008571c99895e`，Ready 1/1、restart 0、SMTP sent=0 |
| `tasks/done/028-10-n-notification-maintenance-runner-safety-gate.md` | notification maintenance runner gate MARK 6/6，默认关闭、dry-run 默认、真实执行双开关、audit/ops 查询、fixture cleanup 通过，SMTP sent=false，mailQueuePublished=false，digest `sha256:4cff0405ff615adfd29fe9847518208a9320b0f3522afa0021ceaeef55ada6cc` |
| `tasks/done/028-10-o-02812-runner-audit-closeout.md` | runner/audit closeout：`search-reindex-raw` capped dev-safe owner runner、manual maintenance session correlationId、ingestion-worker raw/playlist worker-job audit；ops-service 85/85、ingestion-worker 8/8 tests 通过 |
| `tasks/done/028-22-a-unified-recalculate-owner-runner.md` | unified-recalculate owner runner：aggregation-worker authenticated capped dirty recalc API、ops aggregation owner client、runner audit；focused aggregation-worker/ops-service tests 通过 |
| `tasks/done/028-22-b-aggregation-reset-owner-runner.md` | aggregation-reset owner runner：aggregation-worker prepare/raw-pending、search-service unified index hard reset、ops-service V1 顺序编排与 audit；受影响服务测试通过 |
| `tasks/done/028-22-c-sanitize-owner-runner.md` | sanitize owner runner：normalization-worker reset-normalization owner API、ops-service owner client 与 audit；保持 V1 status/updated_at reset 语义，受影响服务测试通过 |
| `tasks/done/028-22-d-trend-sync-owner-runner.md` | trend-sync owner runner：scraper-service provider 汇聚、content-service trend apply、ops-service owner client 与 audit；受影响服务测试通过 |
| `tasks/done/028-22-e-background-discovery-credential-lease.md` | trend-sync background discovery 与 TMDB credential lease：task-service discovery owner API、scraper discovery client、TMDB credential-service lease 优先/env 兜底；受影响服务测试通过 |
| `tasks/done/028-12-a-task-service-runner-audit.md` | task-service runner/watchdog/startup recovery worker-job audit 5/5 |
| `tasks/done/028-12-b-aggregation-scheduler-audit.md` | aggregation-worker scheduler worker-job audit 5/5 |
| `tasks/done/028-15-jdk25-mockito-test-runtime.md` | JDK25 Mockito inline agent 测试运行时修正，组合测试通过 |
| `tasks/done/028-16-b15-aggregation-pipeline-runtime-live-smoke.md` | aggregation pipeline runtime dev K8S live smoke 8/8，multi-raw pipeline、ES/Rabbit cleanup、quota/HTTPRoute 后置检查通过 |
| `tasks/done/028-18-k8s-dev-staging-resource-budget-rollback-guard.md` | K8S dev/staging 资源预算与回滚护栏已闭环；028-18-B 已通过 Helm values 补齐 `traffic-manager` resources、apply task/ops dev manifest 补齐 live gate，verifier MARK 7/7 且 summary PASSED |
| `tasks/done/028-18-b-resource-rollback-live-gap-rerun.md` | 028-18-B live 缺口修复与 verifier 重跑，MARK 5/5；quota/HTTPRoute/smoke residue 均通过 |
| `tasks/done/028-16-c1-s3-notification-listener-start-gate.md` | notification-worker mail listener start gate 4/4，默认关闭 Rabbit listener 并保留 env 显式开启 |
| `tasks/done/028-16-c1-s4-task-service-default-collection-task-seed.md` | task-service default collection task seed 5/5，V1 自动/全量采集任务字段复刻并默认关闭 cron scheduler |
| `tasks/done/028-16-c1-s5-content-resolver-preset-seed.md` | content-service resolver preset seed 5/5，V1 四个默认解析源与默认线路幂等补缺 |
| `tasks/done/028-16-c1-s6-credential-runtime-initializer.md` | credential-service runtime initializer 6/6，V1 ENV/文件凭据导入与 fingerprint 幂等补缺 |
| `tasks/done/028-16-c1-s7-credential-summary-redaction.md` | credential summary redaction 5/5，admin/public payload 脱敏且 internal lease 保留真实 secret |
| `tasks/done/028-16-c1-s8-data-source-category-sync-trigger.md` | data-source category sync trigger 5/5，V1 create/update/patch 后 afterCommit best-effort 触发远程分类同步 |
| `tasks/done/028-16-c1-s9-task-service-watchdog-start-gate.md` | task-service watchdog start gate 5/5，`APP_TASK_WATCHDOG_ENABLED=false` 默认关闭 watchdog，显式开启才扫描 stale active tasks |
| `tasks/done/028-16-c1-full-readiness-auto-start-gate-first-closure.md` | C1 full readiness / auto-start gate first closure，shared gate health detail、normalization/storage watchdog 默认关闭、aggregation dev scheduler 关闭，live K8S gate smoke 3/3 |
| `tasks/done/028-16-c2-s4-system-config-key-catalog-parity.md` | SystemConfigKey catalog parity 6/6，V1 133 个 key 全量纳入 V3 默认 seed，diff missing=0 |
| `tasks/done/028-16-c2-s5-search-cover-url-config-parity.md` | search cover URL config parity 6/6，V1 StorageUrlHelper 的 R2/public-path 配置读取迁入 search-service |
| `tasks/done/028-16-c2-s6-interaction-cover-url-config-parity.md` | interaction cover URL config parity 6/6，V1 StorageUrlHelper 的 R2/public-path 配置读取迁入 interaction 收藏/历史响应 |
| `tasks/done/028-16-c2-s7-portal-cover-url-config-parity.md` | portal cover URL config parity 7/7，V1 StorageUrlHelper 的 R2/public-path 配置读取迁入 portal 首页/分类/探索/详情响应 |
| `tasks/done/028-16-c2-s8-storage-media-url-config-parity.md` | storage media URL config parity 8/8，V1 media public-path/R2 domain/avatar prefix 配置读取迁入 storage 媒体 URL 与头像同步 |
| `tasks/done/028-16-c2-s9-normalization-retry-config-parity.md` | normalization retry config parity 7/7，V1 retry/backoff/batch/pending timeout 配置读取迁入 normalization-worker |
| `tasks/done/028-16-c2-s10-aggregation-batch-config-parity.md` | aggregation batch config parity 6/6，V1 aggregation.batch-size 配置读取迁入 aggregation-worker scheduler |
| `tasks/done/028-16-c2-s11-notification-mail-config-parity.md` | notification mail config parity 8/8，V1 app.mail.* 配置读取迁入 notification-worker MAIL enable/from/SMTP connect options |
| `tasks/done/028-16-c2-s12-metadata-provider-routing-config-parity.md` | metadata provider routing config parity 8/8，V1 app.metadata.* provider enable 与 pending TTL 配置读取迁入 metadata-worker dispatch/worker |
| `tasks/done/028-16-c2-s13-ops-maintenance-reindex-config-parity.md` | ops maintenance reindex config parity 7/7，V3 dev-safe reindex limit 配置读取迁入 ops-service runtime/DB/fallback |
| `tasks/done/028-16-c2-s14-content-scraper-base-url-config-parity.md` | content scraper base URL config parity，`InternalContentClients` 运行期读取 RuntimeInjectedConfig -> DB -> V3 fallback，新增 `q.content.config_changed` |
| `tasks/in-progress/028-16-a8-title-season-subtitle-first-slice.md` | title season/subtitle first slice 已完成，MARK 5/5，保留在 in-progress 继续承接完整 title handler pipeline |
| `tasks/in-progress/028-16-a9-title-location-genre-pipeline-first-closure.md` | title/location/genre pipeline 本地 first closure，MARK 5/6；focused tests 通过，dev live 首跑遇 K8S TLS timeout + Windows 管道编码，DB/queue/port-forward cleanup 已验证 |
| `tasks/in-progress/028-16-c3-notification-credential-internal-service-policy.md` | C3 notification credential INTERNAL_SERVICE 已实现，focused no-real-network 定向测试通过；full suite 仍受 send history H2 `TIMESTAMPTZ` 阻塞 |
| `tasks/done/028-16-c2-s17-real-live-rabbit-config-event-smoke.md` | real live Rabbit config event smoke 完成，C2-S20 可恢复 probe 后 `MARK 7/7`；Rabbit publish/deliver/ack 与 content DB fallback read 均有 live 证据 |
| `tasks/done/028-16-c2-s19-config-service-change-publisher-live-rollout.md` | config-service publisher live rollout 完成；镜像 digest `sha256:de6cc3f758ca62ff42bea22b422b2c570e488ac341da7380effdc9ce93472f05`，Rabbit username 修正为 `admin`，C2-S17 重跑 publish/deliver/ack 通过且无 residue |
| `tasks/done/028-16-c2-s20-content-db-fallback-recoverable-probe.md` | content DB fallback recoverable probe 完成；显式临时移除/恢复 content runtime override，补齐 C2-S17 content read 证据且无 residue |
| `tasks/done/028-16-c2-s21-system-config-missed-message-ttl-fallback.md` | SystemConfig missed-message TTL fallback 完成；决策为 Rabbit event evict + short TTL read-through，content-service focused tests 证明无 event 时 TTL 到期刷新 DB |
| `tasks/done/028-16-c2-system-config-cache-event-parity.md` | SystemConfig cache/event parity 父任务完成；C2-S17/S19/S20 真实 Rabbit 与 content DB fallback read probe 完成，C2-S21 TTL 兜底决策和最小验证完成 |
| `tasks/done/028-16-c2-s15-live-matrix-smoke-harness.md` | config event live matrix smoke harness，新增 no-write `-DryRun` 入口、publisher payload shape guard 和 content-service evict 后 DB fallback 代表回归 |
| `tasks/done/028-16-c3-s4a-metadata-tmdb-credential-internal-service-policy.md` | metadata TMDB credential INTERNAL_SERVICE policy 5/5，shared outbound 区分 PUBLIC_FETCH/INTERNAL_SERVICE/BFF_PROXY |
| `tasks/done/028-16-c3-bff-proxy-admin-target-validation.md` | admin-bff `BFF_PROXY` target validation 5/5，固定 `targetBaseUrl` 在拼接 path/query 前先校验，invalid target fail-before-send |
| `tasks/in-progress/028-16-c3-s10-caller-specific-circuit-env-matrix.md` | C3-S10 caller-specific circuit ENV matrix implementation-first MARK 5/5；shared config test 与 5 service compile 通过，live/K8S/full 后置 |
| `tasks/done/028-16-a4-description-ner-hanlp-prewarm-title-chain-parity.md` | description NER / HanLP prewarm first slice 5/5，V1 简介人名 fallback 与 HanLP 预热复刻 |
| `tasks/in-progress/028-16-a5-area-language-composite-alias-policy.md` | area/language composite alias policy done first slice 4/4，复合地区/语言 token 展开且 locked_fields 仍压制 relation |
| `tasks/in-progress/028-16-a6-genre-synonym-suffix-protected-term-policy.md` | genre synonym/suffix/protected-term policy done first slice 5/5，genre 未锁定时追加 V1 风格展开 token，`locked_fields.genres` 仍完全压制 relation |
| `tasks/in-progress/028-16-e-llm-cleaning-gated-parity.md` | LLM cleaning gated parity implementation-first + no-real-cost closure，MARK 5/6；默认关闭、mode 默认 dry-run、runner 默认关闭，fake path/credential-service lease path/fixed error semantics、幂等护栏、fake path 不解析凭据、不调用 live client、credential 脱敏已完成，真实 LLM/live smoke 统一测试后置 |
| `tasks/in-progress/028-16-f5-c1-readiness-golden-p1p2.md` | C1 readiness golden P1/P2 implementation closure；catalog/content/task gate health、catalog seed gate、UUID/AsyncLogger/Disruptor 决策已完成，resolver/player/真实资源站 live smoke 后置 |

## Backlog 任务

| 任务 | 输出 |
| --- | --- |
| `tasks/backlog/014-02-legacy-doc-language-normalization.md` | 历史文档简体中文规范化 |
| `tasks/backlog/028-16-f1-normalization-golden-p1p2-backlog.md` | normalization golden P1/P2：F1/A11 已完成本地 implementation closure，title release/S02/Part/Vol、location hint、genre synonyms、raw category fallback、retry/watchdog P2 已补；UTB-01 A9/A12 live/focused 证据通过 |
| `tasks/backlog/028-16-f2-aggregation-golden-p1p2-backlog.md` | aggregation golden P1/P2：local closure 已补 search sync 结构化、pipeline failure dirty lifecycle、recalculate guard；UTB-01 B15 runtime smoke 通过，真实 R2 cover promote、multi context merge、outbox/stats 后置 |
| `tasks/backlog/028-16-f3-tika-scraper-golden-p1p2-backlog.md` | Tika/scraper golden P1/P2：D4 已补 detail charset、pagination、Big5/Shift_JIS/BOM/invalid bytes fixture；UTB-01 D3-D4 local fixture smoke 通过，真实 provider charset smoke 后置 |
| `tasks/backlog/028-16-f4-llm-cleaning-golden-p1p2-backlog.md` | LLM cleaning golden P1/P2：no-real-cost closure 已完成；真实 LLM 单条、成本、K8S dry-run runner smoke/audit/DB residue 后置 |
| `tasks/backlog/028-16-f5-c1-readiness-golden-p1p2-backlog.md` | C1 readiness golden P1/P2：cache/full preset、catalog/content/task gate health、catalog seed gate 和 UUID/async logging 决策已完成；resolver/player smoke 统一测试后置 |
| `tasks/backlog/028-16-f6-c3-outbound-adoption-golden-p1p2-backlog.md` | C3 outbound adoption golden P1/P2：identity multipart、storage strict adapter、BFF shared streaming transport 与 caller-specific circuit ENV matrix 已完成实现；剩余 unified tests、dev-safe BFF smoke 与 K8S override/open-circuit matrix |
| `tasks/backlog/028-16-f7-worker-scheduler-audit-golden-p1p2-backlog.md` | worker/scheduler audit golden P1/P2：028-12-C/D metadata/normalization/storage/search/scraper audit implementation first closure；`UTB-02816-P1P2-03` notification/task/search/scraper/resource 通过，metadata/normalization/storage live queue ops query 后置 |
| `tasks/backlog/028-16-f8-production-like-smoke-golden-p1p2-backlog.md` | production-like smoke golden P1/P2：UTB-01/03 已通过，UTB-02 no-real-cost/dry-run 子集通过；C3 live/full、真实 LLM 和外部 provider/SMTP/R2 后置 |

## 服务进行中任务

```text
暂无
```

## 待切分服务

`011` 父任务下 18 个目标业务服务均已完成 MVP rollout。post-MVP 完整覆盖缺口在
`025` 父任务下继续拆分。门户验证入口和 BFF 拆分已完成：

```text
tasks/done/027-01-portal-admin-frontend-deployment-mvp.md
tasks/done/027-02-portal-admin-bff-split-mvp.md
```

当前 `025` 路由覆盖口径：

```text
V1 routes: 203
V3 routes: 226
missing:   0
```

说明：`226` 比 `025` closeout 的 `224` 多 2 条 post-025 additive ops 查询端点：
`GET /api/v1/ops/worker-job-audit` 和
`GET /api/v1/ops/worker-job-audit/summary`，不影响 V1 missing=0。

当前 `028` 行为级 parity 第一阶段：

```text
028-01 BFF request audit collection: done, MARK 2/2
028-02 avatar media persistence/sync: done, MARK 2/2
028-03 magnet provider search:        done, MARK 3/3
028-04 ops maintenance runner:        done, MARK 3/3
028-05-A interaction watch progress:  done, MARK 4/4
028-05-B storage avatar sync:         done, MARK 4/4
028-05-C metadata fan-out routing:    done, MARK 1/1
028-05-D search producer matrix:      done, MARK 2/2
028-05-E metadata worker/result:      done, MARK 4/4
028-05-F normalization raw relation:  done, MARK 4/4
028-05-G metadata pending gather:     done, MARK 4/4
028-05-H image download true/failure: done, MARK 5/5
028-05-I mail disabled/failure:       done, MARK 4/4
028-05 queue consumer parity audit:   done
028-06 real provider live parity:      done, enable gate done, TMDB direct diagnostic/live queue MARK 6/6, 028-06-C magnet YTS_BZ runner MARK 5/5, 028-06-E/F/G Douban provider first slice/full queue MARK 6/6, 028-06-H/I RT provider first slice/full queue MARK 6/6, RT live digest sha256:93db92be56a2a7dc0b85d84c7a43ecb8c8f854f5947f1bbd7a6ae2dafc34291a, externalRequestCount=1, APP_METADATA_RT_ENABLED=false restored, queue/DB/ES/port-forward cleanup verified
028-07 R2 live/watchdog parity:        done, watchdog MARK 4/4, credential-only R2 object MARK 5/5 with V1 bucket, 028-07-C storage-service R2 path MARK 6/6, 028-07-D avatar R2 live MARK 7/7, 028-07-E dev R2 bucket policy guard MARK 4/4, 028-07-F explicit V1 bucket smoke preflight/no-write guard done, 028-07-G real R2 explicit V1 bucket write smoke PUT 200/HEAD 200/DELETE 204/cleanup HEAD 404 done, 028-07-H cover explicit V1 bucket chain MARK 6/6 done, 028-07-I avatar explicit V1 bucket chain MARK 7/7 done, 028-07-J watchdog long-run recovery MARK 8/8 done
028-08 normalization deep mapping:     done, actor/director MARK 4/4, raw category MARK 4/4, standard mapping MARK 4/4, retry watchdog MARK 4/4, live smoke MARK 6/6
028-09 search producer live smoke:     done, normalization producer MARK 5/5, metadata-result MARK 6/6, aggregation MARK 6/6, dirty lifecycle MARK 6/6
028-10 production governor/credential: implementation closeout for current governor/runner base, boundary/MAIL pool guard MARK 3/3, 028-10-B MAIL fake sink MARK 5/5, 028-10-C runner registry MARK 5/5, 028-10-D notification send history fake sink done, 028-10-E schema/ops query done, 028-10-G migrator dev deploy smoke done, 028-10-H send history dev DB migration + ops query live closed, 028-10-I one-shot safety block completed with no SMTP sent, 028-10-J retention cleanup MARK 5/5, 028-10-K Harbor push unblock + retention smoke MARK 5/5, 028-10-M notification-worker CrashLoop recovery MARK 6/6, 028-10-L V1 SMTP one-shot MARK 6/6 with one real email/send history/ops/user inbox confirmation, 028-10-N notification maintenance runner gate MARK 6/6 with default-off/dry-run/default audit/finalSmokeResidue=0, 028-10-O raw reindex capped owner runner + session correlation done, 028-22-A unified-recalculate owner runner done, 028-22-B aggregation-reset owner runner done, 028-22-C sanitize owner runner done, 028-22-D trend-sync owner runner done, 028-22-E background discovery/TMDB credential lease done, 028-22-F no-external live smoke done with provider-gated window pending; runner/governor base has no known placeholder refusal gap
028-11 per-service audit collection:   done, config-service MARK 3/3
028-12 service/worker audit expansion: implementation closeout for known matrix, catalog-service MARK 2/2, content-service MARK 2/2 + live MARK 4/4, credential-service MARK 3/3 + live MARK 4/4, identity-service MARK 4/4 + live MARK 4/4, interaction-service MARK 4/4 + live MARK 4/4, magnet-service MARK 4/4 + live MARK 4/4, storage-service MARK 4/4 + live MARK 4/4, search-service MARK 4/4 + live MARK 4/4, portal-service MARK 4/4 + live MARK 4/4, task-service MARK 4/4 + live MARK 4/4, ops-service MARK 5/5 + live MARK 4/4, scraper-service MARK 4/4 + live MARK 4/4, notification worker/job audit MARK 3/3, ops query MARK 3/3, ops live query MARK 4/4, notification live queue MARK 5/5, task runner audit MARK 5/5, aggregation scheduler audit MARK 5/5, 028-12-C metadata/normalization/storage worker audit MARK 6/6 implementation + live smoke passed, 028-12-D search/scraper worker audit focused tests passed, ingestion-worker raw/playlist audit done; live rollout/smoke for new ingestion audit deferred
028-16 deep behavior/library parity:     in-progress, golden inventory first draft, P0 cleaning/aggregation/dependency gaps identified; A4 description NER/HanLP prewarm done, A5 area/language composite alias done first slice, A6 genre synonym/suffix/protected-term done first slice, A7 title alias dedup done first slice, A8 title season/subtitle done first slice, A9 title/location/genre pipeline live closure MARK 5/5 done via UTB-01, A12 title bracket cleanup/raw area/genre dictionary focused closure done, C1-S1 catalog dictionary seed done, C1-S2 data source preset seed done, C1-S2b remote category sync done, C1-S2c readiness/initialization remaining first closure done implementation with full countries/languages preset + normalization TTL fallback + resolver/resource dry-run prep, F5 catalog/content/task gate health + catalog seed gate + UUID/AsyncLogger decision done, C1-S3 notification listener gate done, C1-S4 task-service default collection task seed done, C1-S5 content resolver seed done, C1-S6 credential runtime initializer done, C1-S7 credential summary redaction done, C1-S8 data-source category sync trigger done, C1-S9 task-service watchdog start gate done, C1 full readiness/auto-start gate first closure done with live K8S gate smoke 3/3, C2 SystemConfig cache/event parity closed: env-first/effective source/config event consumers, full key catalog, representative live Rabbit publish/deliver/ack, content DB fallback read probe and missed-message TTL fallback done; C3 shared outbound PUBLIC_FETCH first slices done, C3-S4a metadata TMDB credential INTERNAL_SERVICE done, C3 notification credential INTERNAL_SERVICE implemented with focused evidence and full suite pending send history H2 fix, C3 admin-bff BFF_PROXY target validation done, C3 portal-bff BFF_PROXY target validation done, C3-S6 shared circuit breaker first slice + content InternalContentClients INTERNAL_SERVICE done, C3-S7 task-service ScraperTaskExecutionClient POST JSON INTERNAL_SERVICE done, C3-S8 identity RestClientAvatarStorageClient multipart INTERNAL_SERVICE done, C3-S9 BFF shared streaming transport done with focused tests, C3-S10 caller-specific circuit ENV matrix done, D4/F3 Tika/scraper detail charset + pagination + Big5/Shift_JIS/BOM fixtures done with UTB-01 local fixture smoke, E/F4 LLM cleaning gated parity implementation/no-cost MARK 5/6 partial done with live LLM deferred, B2 duplicate unified merge first slice done, B3 victim relation union first slice done, B4 victim scalar merge done first slice, B5 raw-source pipeline category/relation rebuild done, B6 raw-source scalar pipeline done, B7 raw-source cover pipeline done, B8 cover R2 promote side effect done, B9 same-batch JGraphT raw/raw cluster done, B10 raw/unified context graph done, B11 category bucket graph done, B12 graph scoring metadata penalty done, B13 ES-backed context recall done, B14 pipeline runtime order/savepoint failure isolation done, B15 dev K8S pipeline runtime live smoke MARK 8/8 done
028-16 deep behavior/library parity update 2026-06-12: UTB-01 A9/A12/B15/D3-D4 all PASSED; A9 live closure MARK 5/5, D3-D4 local fixture smoke passed, provider charset external window remains deferred
028-16-A1 canonical rawMetadata:          done first slice, MARK 4/4 local test
028-16-A2 text/title policy:              done first slice, MARK 5/5 local test
028-16-A3 metadata result deep merge:     done first slice, MARK 5/5 local test
028-16-A4 description NER/HanLP prewarm:  done first slice, MARK 5/5 local normalization-worker test + K8S dry-run; full title chain/area/genre/live smoke remain
028-16-A5 area/language composite alias:  done first slice, MARK 4/4 local normalization-worker test; full location NLP/genre policy/live smoke remain
028-16-A6 genre policy:                   done first slice, MARK 5/5 local normalization-worker test; full StandardGenreMappingPolicy/HanLP genre NLP/title pipeline/live smoke remain
028-16-A7 title alias dedup:             done first slice, MARK 4/4 local normalization-worker test; full title handler chain/season-subtitle-mainTitle pipeline remain
028-16-A8 title season/subtitle:          done first slice, MARK 5/5 local normalization-worker test; full title handler chain remains
028-16-A9 title/location/genre closure:   live closure, MARK 5/5 dev K8S smoke via UTB-01; DB/queue cleanup verified
028-16-F1/A11 normalization golden P1/P2: UTB-01 A9/A12 evidence passed; title/location/genre/raw category/retry-watchdog P1/P2 done, full historical rewash matrix deferred
028-16-A12 normalization title/raw dict:  partial focused closure, bracket 噪音清理、area alias/composite、genre synonym 与脏 token 防护 done；normalization-worker 71/71 tests passed
028-16-A13 CharTable/title structure:    post-reboot repair done, CharTable->HanLP->OpenCC 接回并保留显示大小写，season/subtitle 结构提取保留，title display mainTitle 切换 deferred；RawVideoTextNormalizer focused tests 24/24 passed
028-16-B16 search outbox stats:          implementation first closure, `GET /api/v1/search/ops/search-sync-tasks/stats` 只读统计 search_sync_tasks，missing table 返回 unavailable；focused tests passed，dev DB live smoke deferred
028-16-B1 aggregation matching fixtures:  done first slice, MARK 5/5 local test
028-16-B2 duplicate unified merge:        done first slice, MARK 6/6 local aggregation/search test; victim merge/delete/search DELETE done, raw-source pipeline/JGraphT/live smoke deferred
028-16-B3 victim relation union:          done first slice, MARK 5/5 local aggregation/search test; victim actors/directors/genres/languages/areas union before delete, raw-source AggregationPipeline deferred
028-16-B4 victim scalar merge:            done first slice, MARK 5/5 local aggregation/search test; victim scalar fill-empty and last_trend_at merge before delete, raw-source AggregationPipeline deferred
028-16-B5 raw-source pipeline relations:  done first slice, MARK 6/6 local aggregation/search test; category vote and actors/directors/genres/languages/areas rebuild done, dynamic/cover/JGraphT/live smoke deferred
028-16-B6 raw-source pipeline scalars:    done first slice, MARK 7/7 local aggregation/search test; Basic/ExternalId/Dynamic scalar rules done, cover/JGraphT/live smoke deferred
028-16-B7 raw-source pipeline cover:      done first slice, MARK 5/5 local aggregation/search test; CoverImagePipelineHandler choose-cover rules done, R2 promote moved to B8, JGraphT/live smoke deferred
028-16-B8 cover R2 promote side effect:  done first slice, MARK 5/5 local messaging/aggregation/storage test; storage command and due upload scheduling done; graph/runtime later completed by B9-B14, live smoke deferred
028-16-B9 JGraphT raw/raw cluster:        done first slice, MARK 6/6 local aggregation/search test; same-batch raw/raw graph cluster done; context graph/category/scoring/ES recall/runtime later completed by B10-B14, live smoke deferred
028-16-B10 raw/unified context graph:     done first slice, MARK 6/6 local aggregation/search test; V1 douban/tmdb context recall, raw/unified graph node, pure unified skip, context reuse/merge done; category bucket/metadata scoring/ES recall/runtime later completed by B11-B14, live smoke deferred
028-16-B11 category bucket graph:         done first slice, MARK 5/5 local aggregation/search test; raw/unified standard categoryName bucket and UNKNOWN_CATEGORY guard done; metadata scoring/ES recall/runtime later completed by B12-B14, live smoke deferred
028-16-B12 graph scoring metadata:        done first slice, MARK 6/6 local aggregation/search test; raw/unified actor/director/standard area projection, V1 title/metadata weighted scoring, episode and area penalty done; ES recall/runtime later completed by B13-B14, live smoke deferred
028-16-B13 ES-backed context recall:      done first slice, MARK 6/6 local aggregation/search test; search-service internal UUID recall and aggregation-worker ES-first context recall done; runtime later completed by B14, live smoke deferred
028-16-B14 pipeline runtime order:        done first slice, MARK 6/6 local aggregation/search test; V1 handler order and nested savepoint failure isolation runtime done; K8S runtime smoke completed by B15
028-16-B15 pipeline runtime live smoke:   done, MARK 8/8 dev K8S smoke; F2/B15 local closure added structured search sync, malformed reject, pipeline failure raw PENDING/unified DIRTY and recalculate guard; real R2/multi-context production-like smoke deferred
028-16-C1 initialization readiness gate:  in-progress, MARK 1/6+2/6+3/6 done, MARK 4/6 partial via catalog dictionary/data-source/full countries+languages preset/resolver/credential/task seed tests, MARK 5/6 first closure done via notification/task/normalization/storage/aggregation gate tests, normalization TTL fallback test and K8S smoke, MARK 6/6 current-scope indexes done; shared auto-start gate health detail is observable，F5 已扩展 catalog/content/task gate health 与 catalog seed gate，C1-X 已补 UUID generator 门面与 AsyncLogger/Disruptor ADR，normalization/storage watchdog and aggregation scheduler live gates are false，V1 式全服务自动放行/time-based UUID 全量替换/AsyncLogger 默认启用记为后续独立切片；full preset/live matrix unified testing remains deferred
028-16-C1-S1 catalog dictionary seed:     done first slice, MARK 5/5 local catalog-service test
028-16-C1-S2 data source preset seed:     done first slice, MARK 5/5 local catalog-service test
028-16-C1-S2b remote category sync:       done first slice, MARK 5/5 fake HTTP/repository test; admin write trigger completed by C1-S8, ops manual runner/live provider smoke remain
028-16-C1-S2c readiness remaining closure: done implementation, MARK 6/6 focused catalog/normalization tests + dry-run prep; resolver/real-source live smoke deferred to unified testing
028-16-C1-S3 notification listener gate:  done first slice, MARK 4/4 local notification-worker test; full readiness orchestrator remains
028-16-C1-S4 task-service default task:   done first slice, MARK 5/5 local task-service test + K8S dry-run; full readiness orchestrator remains
028-16-C1-S5 content resolver seed:       done first slice, MARK 5/5 local content-service test + K8S dry-run; player live smoke/cache evict remains
028-16-C1-S6 credential runtime init:     done first slice, MARK 6/6 local credential-service test + K8S dry-run; credential live smoke remains
028-16-C1-S7 credential summary redact:   done first slice, MARK 5/5 local credential-service test; credential live smoke remains
028-16-C1-S8 data-source category sync:   done first slice, MARK 5/5 local catalog-service test + K8S dry-run; ops manual runner/live provider smoke remain
028-16-C1-S9 task watchdog start gate:    done first slice, MARK 5/5 local task-service test + K8S dry-run; remaining scheduler guards covered by C1 full auto-start first closure
028-16-C2 SystemConfig cache/event:       done, full V1 key catalog、env-first/effective source/config event consumers、C2-S17/C2-S19 real live Rabbit publish/deliver/ack、C2-S20 content DB fallback read probe 和 C2-S21 missed-message TTL fallback 均已完成，父任务已归档到 tasks/done/028-16-c2-system-config-cache-event-parity.md
028-16-C3 shared outbound HTTP client:    in-progress, MARK 1/6+2/6+3/6+4/6+5/6+6/6 done first slices; catalog+magnet+scraper+metadata TMDB shared PUBLIC_FETCH done, C3-S4a metadata TMDB credential INTERNAL_SERVICE done, C3 notification credential INTERNAL_SERVICE implemented with targeted evidence, admin/portal BFF_PROXY target validation done, storage IMAGE_DOWNLOAD_STRICT adapter done by parallel worker, C3-S6 shared circuit breaker first slice + content InternalContentClients INTERNAL_SERVICE done, C3-S7 task-service ScraperTaskExecutionClient POST JSON INTERNAL_SERVICE done, C3-S8 identity RestClientAvatarStorageClient multipart INTERNAL_SERVICE done with focused tests, C3-S9 admin/portal BFF shared BFF_PROXY streaming transport done with focused tests, C3-S10 caller-specific circuit ENV matrix implementation done; live/full tests, dev-safe BFF smoke and K8S override/open-circuit matrix unified later
028-16-D1 storage Tika MIME:              done first slice, MARK 5/5 local storage-service test
028-16-D2 scraper charset/streaming:      done first slice, MARK 5/5 local scraper/config/storage/shared test
028-16-D3 Tika/file parsing live closure: done first closure, MARK 6/6 local smoke; V1 Tika usage documented, V3 deps minimal, small fixture create/parse/cleanup verified
028-16-D4/F3 Tika/scraper P1/P2 closure: UTB-01 local fixture smoke passed; detail charset, pagination totalPages/totalItems, Big5/Shift_JIS/BOM/invalid bytes fixtures done; real provider live charset smoke deferred
```

当前 `028` 第一阶段 route/BFF/queue/live-smoke 强制缺口：`0`。

2026-06-10：`028-16-F` 已把 089/093 中剩余 P1/P2 模糊缺口转为 8 个 backlog task spec。
当前策略为先推进实现完整度，不逐一即时测试；后续按统一批次集中验收：

```text
UTB-02816-P1P2-01 normalization + aggregation + Tika/scraper
UTB-02816-P1P2-02 LLM fake/no-cost + C1 readiness + C3 outbound adoption
UTB-02816-P1P2-03 worker/scheduler audit + production-like cleanup/resource checks
```

028-19 已把统一测试阶段入口固化为：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1
```

默认只输出 plan；后续统一测试必须显式 `-Execute`，provider/SMTP/R2 还需独立 allow flag。

当前整体估算：

```text
V1/V3 parity 总进度: 约 96%
开发 MVP 可用度:     约 97%
生产就绪度:          约 86%
```

本轮状态口径：

```text
功能实现完成待统一测试:
  028-16-C1-S2c full countries/languages preset + normalization TTL fallback + resolver prep
  028-16-C3-S8 identity multipart outbound adoption
  028-16-C3-S9 BFF shared streaming transport adoption
  028-16-D4/F3 Tika/scraper detail charset + pagination + more encoding fixtures
  028-16-E LLM cleaning gated parity MARK 5/6 partial
  028-16-F1/A11 normalization golden P1/P2 MARK 5/6 partial
  028-16-A12 title/raw dictionary focused closure
  028-16-F4/E LLM no-real-cost hardening
  028-16-F5/C1 catalog/content/task gate health
  028-12-C/D metadata/normalization/storage/search/scraper worker audit hooks
  028-20-09 ops-service maintenance scheduler owner-step local closure
  028-20-09B maintenance scoped write/consumer gate local closure
  028-20-09C content-service raw/unified maintenance write gate
  028-20-10B K8S mTLS certificate/rollout entry dry-run closure, superseded by 028-20-27 removal
  028-20-13A/B/C/D/E portal/catalog/search/magnet/credential multi-tier read-model cache local closure
  028-10-O / 028-12-E raw reindex runner + ingestion-worker audit closeout
  028-22-A unified-recalculate owner runner
  028-22-B aggregation-reset owner runner
  028-22-C sanitize owner runner
  028-22-D trend-sync owner runner
  028-22-E background discovery / TMDB credential lease
  028-22-F provider-gated Douban/TMDB live window

live smoke 已闭环:
  028-06 provider live parity
  028-07 R2 live/watchdog parity
  028-10-L SMTP one-shot
  028-10-N notification maintenance runner gate
  028-16-C2 SystemConfig cache/event parity
  028-16-C1 full readiness/auto-start gate first closure
  028-20-24 Prometheus targets + health components + shared Redis env live
  028-20-26 internal server mTLS rollout live, superseded by 028-20-27 removal
  028-20-27 manual mTLS removal live
  028-22-F trend discovery no-external smoke + task/scraper/ops rollout

统一测试待执行:
  UTB-02816-P1P2-01 done except provider charset external window
  UTB-02816-P1P2-02 LLM + C1 readiness + C3 outbound adoption live/full remainder
  UTB-02816-P1P2-03 done for notification/task/search/scraper/resource; metadata/normalization/storage worker audit live ops query closed 2026-06-12
```

深层行为迁移缺口：不再按旧的 `4` 类口径统计。当前已进入 `028-16` 总账，第一轮
P0 包含初始化编排、SystemConfig 默认值与缓存失效、HanLP/OpenCC 文本标准化、标题
pipeline、canonical rawMetadata、metadata result 深层合并、聚合图匹配、聚合 pipeline、
duplicate unified merge 和统一出站 HTTP；P1/P2 覆盖 Tika、LLM、Cache TTL、UUID、
AsyncLogger、search reconciliation 等。

## 并行开发分配建议

可以并行分配，但必须保持写入范围不重叠：

- Agent A：`scraper-service` manual scrape MVP 已完成，已归档。
- Agent B：`portal-service` read composition MVP 已完成并归档。
- Agent C：`interaction-service` feedback/history MVP 已完成并归档。
- Agent D：`ops-service` maintenance/dashboard MVP 已完成并归档。

主 agent 负责合并结果、处理冲突、执行最终 build、镜像推送、rollout 和 smoke。

当前 ResourceQuota 为 `pods=28/30`、`services=25/26`、`secrets=5/30`，仍保留 2 个缓冲 Pod 和
1 个缓冲 Service；每个新服务仍应保持无公网 HTTPRoute。
