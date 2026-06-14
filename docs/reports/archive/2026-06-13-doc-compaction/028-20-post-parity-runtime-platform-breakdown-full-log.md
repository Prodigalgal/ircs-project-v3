# 028-20 post-parity runtime platform breakdown

状态：进行中

来源：`docs/decisions/005-post-parity-runtime-platform-decisions.md`

## 目标

把 2026-06-10 已拍板的 V1/V3 行为级迁移决策拆成可独立实现、可验证、写入范围不互踩的任务。

## 非目标

- 不在本父任务一次性完成全部实现。
- 不把统一测试窗口替代真实实现。
- 不做生产级压测或跨库物理拆分。

## 子任务顺序

| 子任务 | 状态 | 范围 | 验收 |
| --- | --- | --- | --- |
| `028-20-01` | done | AsyncLogger / Disruptor 全业务服务启用 | runtimeClasspath、shared jar resource 和代表服务 bootJar 已通过 |
| `028-20-02` | done | search persistent outbox relay/reconciliation | producer transactional outbox、lease relay、retry/dead-letter、reconcile、stats 已实现，live smoke 后置 |
| `028-20-03` | done | UUID 全量新增写入切 `timeBasedEpoch` | 新增业务写入点使用 `IrcsUuidGenerators.nextId()`，历史数据不迁移 |
| `028-20-04` | done | V1 `mainTitle` title 语义 + portal/admin/search 兼容 | DB/API `title` 为 mainTitle；`season`/`subtitle` 原子字段和 search variants 补齐 |
| `028-20-05` | done | 统一错误模型 + portal/admin 适配 | 共享 error envelope、服务/BFF advice、trace/correlation headers 和 BFF contract tests 已完成 |
| `028-20-06` | done | RBAC / permission code / data scope / visibility | destructive token migration、BFF permission gate、portal/search data scope、content visibility/tag 模型已完成 |
| `028-20-07` | done | versioned config event + credential lease cache + cache governance | 破坏性 versioned event、credential lease TTL cache、shared cache governance 已完成 |
| `028-20-08` | done | 三类审计、DB->ES 副本、retention、统一归档表 | audit_class、ES outbox、统一 archive table、ops summary 已实现 |
| `028-20-09` | done | ops-service maintenance scheduler | ops 统一调度、dry-run/gate/audit，`search-reindex-unified` 由 search-service owner API 执行 |
| `028-20-09B` | done | maintenance scoped write/consumer gate | 持久 gate registry、ops API、shared inspector、统一 423 错误语义已完成；owner service 写入口接入后续推进 |
| `028-20-09C` | done | content-service maintenance write gate first owner | raw/unified video 写入口已接入 generic/specific scoped write gate |
| `028-20-09D` | done | maintenance gate versioned event + Redis hot cache | gate changed event、content L1/L2/L3 blocked cache 与事件失效已完成；多级业务缓存治理升级为 D30 |
| `028-20-10` | done | service identity | 应用层 service identity/scopes 已完成；手动 K8S mTLS rollout 后续已由 `028-20-27` 撤销 |
| `028-20-11` | partial | Tika parsers / LLM runner / media 双轨 / library follow-up | Tika standard package 已按服务边界接回 scraper-service；LLM maintenance owner runner 已接 normalization-worker；storage media R2 canonical cleanup/health 已补齐；normalization LLM credential resolver 已迁移 shared outbound；remaining library follow-up 继续后置 |
| `028-20-12` | partial | workerId 唯一化 / outbox relay health / Micrometer 指标 | search outbox relay observability、worker/job audit source 唯一化、async worker/job Micrometer 基础指标、distributed lock metrics/health 已完成；live 可见性确认后置 |
| `028-20-13` | partial | multi-tier business cache / read-model rollout | shared read-through cache 基座、portal anonymous public read model、catalog standard dictionary、search portal suggest/recommend、magnet provider/approved-links、credential redacted summary 热读已完成；后续继续按 owner service 接剩余安全/业务热读 |
| `028-20-14` | done | ops maintenance scheduler cluster lease | shared Redis lease 抽象第一刀与 ops scheduler 跨副本互斥已完成 |
| `028-20-15` | done | distributed lock manager multi-service rollout | scraper/magnet traffic reservation 与 task/search/aggregation/normalization/config/catalog/content/credential/storage/notification 冲突入口互斥已完成 |
| `028-20-22` | done | distributed lock observability | shared mutex/time-slice/token-bucket metrics 与 `distributedLock` health 已完成；K8S/Prometheus live 查询后置 |
| `028-20-23` | done | no `@Autowired` / single-constructor / dev rollout health | 生产代码 no `@Autowired` 与单构造器风险清零；content-service Redis env rollout 恢复；`ircs-dev` 25/25 Ready、digest 审计和 readiness smoke 通过 |
| `028-20-27` | done | manual mTLS removal | 手动 Spring/Nginx/Prometheus/cert-manager mTLS 已从 repo 与 `ircs-dev` live 拆除；HTTP readiness 20/20、Prometheus 20/20 UP、HTTPS/mTLS targets=0 |

## 当前切片：028-20-01

### 范围

- 根 `build.gradle` 的 `serviceProjects` 依赖增加 `com.lmax:disruptor:3.4.4`。
- `shared:ircs-common` 提供 classpath 级 `log4j2-spring.xml`，业务服务复用。
- `platform/ircs-migrator` 暂不接入。
- 保留 console 输出，同时补 V1 风格 info/warn/error/ingestion-trace rolling appenders。

### 验收

- 所有 `services:*` runtimeClasspath 能看到 Disruptor。
- 代表服务 `bootJar` 或 `classes` 通过。
- 不影响 existing focused tests。
- 文档索引回写。

## 028-20-01 完成证据

```text
com.lmax:disruptor:3.4.4 present on services runtimeClasspath
shared ircs-common jar contains log4j2-spring.xml
config-service/search-service/admin-bff/portal-bff/normalization-worker/storage-service bootJar passed
```

## 028-20-02 完成证据

search persistent outbox relay/reconciliation：

- `shared:ircs-common` 增加 `SearchSyncOutboxWriter`，业务服务在事务里写 `search_sync_tasks`。
- content / aggregation / normalization / metadata / ops search sync producer 已迁移，不再直发 search Rabbit。
- search-service 增加 internal enqueue API、lease claim relay、失败退避、`DEAD_LETTER`、worker audit。
- reconciliation runner 增加 bounded DB->ES missing check，默认 dry-run。
- migrator 增加 `extend-search-outbox-runtime.sql`。

```text
focused tests passed:
shared:ircs-common
services:ircs-search-service
services:ircs-content-service
services:ircs-aggregation-worker
services:ircs-normalization-worker
services:ircs-metadata-worker
services:ircs-ops-service

affected bootJar passed:
platform:ircs-migrator
services:ircs-search-service
services:ircs-content-service
services:ircs-aggregation-worker
services:ircs-normalization-worker
services:ircs-metadata-worker
services:ircs-ops-service
```

## 028-20-03 完成证据

UUID 全量新增写入切 `timeBasedEpoch`：

- `IrcsUuidGenerators.defaultGenerator()` 已切到 `TIME_BASED_EPOCH`。
- `IrcsUuidGenerators.nextId()` 成为新增业务 ID 统一入口。
- credential / scraper / task / content / aggregation / catalog / notification / ingestion / magnet / identity / normalization / interaction / storage / ops 等生产新增业务 ID 写入点已切换。
- 历史数据、migration/backfill SQL 不迁移。
- `UUID.randomUUID()` 生产源码仅保留 generated fallback secret、multipart boundary、PoW challenge、activation/reset token、temporary file suffix 五类非业务随机用途。

```text
focused tests passed:
shared:ircs-common
services:ircs-credential-service
services:ircs-scraper-service
services:ircs-task-service
services:ircs-content-service
services:ircs-aggregation-worker
services:ircs-catalog-service
services:ircs-notification-worker
services:ircs-ingestion-worker
services:ircs-magnet-service
services:ircs-identity-service
services:ircs-normalization-worker
services:ircs-interaction-service
services:ircs-storage-service
services:ircs-ops-service

affected bootJar passed:
services:ircs-credential-service
services:ircs-scraper-service
services:ircs-task-service
services:ircs-content-service
services:ircs-aggregation-worker
services:ircs-catalog-service
services:ircs-notification-worker
services:ircs-ingestion-worker
services:ircs-magnet-service
services:ircs-identity-service
services:ircs-normalization-worker
services:ircs-interaction-service
services:ircs-storage-service
services:ircs-ops-service
```

## 028-20-04 完成证据

V1 `mainTitle` title 语义 + portal/admin/search 兼容：

- V1 `TitleStructureHandler` 语义已落回 V3 normalization：`title` 写 mainTitle，`season` / `subtitle` 保持原子字段。
- `aliasTitle` 去重升级，避免把 `title + season` 或 `title + subtitle` 组合串重新污染别名字段。
- `Part II` / `Vol.2` 保留为标题内容，不误判为季数。
- `SearchTextHelper` title variants 上限提高到 64，并补充 colon/dash/season/subtitle 组合检索形式。
- portal/admin/BFF payload 未新增 `displayTitle`，继续保留原子字段；组合展示交给 portal/admin 视图层。

```text
focused tests passed:
services:ircs-normalization-worker RawVideoTextNormalizerTest
services:ircs-search-service SearchTextHelperTest

affected service tests passed:
services:ircs-normalization-worker
services:ircs-search-service
services:ircs-aggregation-worker
services:ircs-content-service
services:ircs-portal-bff
services:ircs-admin-bff

affected bootJar passed:
services:ircs-normalization-worker
services:ircs-search-service
services:ircs-aggregation-worker
services:ircs-content-service
services:ircs-portal-bff
services:ircs-admin-bff
```

## 028-20-05 完成证据

统一错误模型 + portal/admin 适配：

- `shared:ircs-common` 增加 `ApiErrorResponse`、`ApiErrorResponses`、`CommonApiExceptionHandler`。
- identity/content/storage/interaction/ops 现有 handler 已切换到共享 envelope。
- config/catalog/JWT filter 手写 HTTP error body 已替换为共享 helper。
- admin-bff/portal-bff 自身 404/502 等错误由 common advice 输出统一 envelope；BFF 对上游响应继续透明透传。
- V3 仓库未包含 portal/admin 前端源码包，本切片以 BFF/API contract 覆盖门户兼容边界。

```text
affected service tests passed:
shared:ircs-common
services:ircs-identity-service
services:ircs-content-service
services:ircs-storage-service
services:ircs-interaction-service
services:ircs-ops-service
services:ircs-config-service
services:ircs-catalog-service
services:ircs-admin-bff
services:ircs-portal-bff

affected bootJar passed:
services:ircs-identity-service
services:ircs-content-service
services:ircs-storage-service
services:ircs-interaction-service
services:ircs-ops-service
services:ircs-config-service
services:ircs-catalog-service
services:ircs-admin-bff
services:ircs-portal-bff
```

## 028-20-06 完成证据

RBAC / permission code / data scope / content visibility：

- 旧 role-only JWT 已破坏性失效；identity 新 token 强制携带 permissions/scopes/dataCategories/dataGenres/dataTags/contentVisibility。
- admin-bff 从 admin role gate 升级为 route-level permission gate。
- portal-bff 对 profile / interaction / feedback owner APIs 执行 permission gate；公开读保持公开。
- BFF 统一清理客户端伪造身份头，只转发本地认证后的 trusted principal。
- `unified_videos.content_visibility` 与 `unified_video_tags` 迁移已加入 migrator。
- content-service unified admin payload 支持 `contentVisibility` 与 `tags`。
- portal-service SQL 强制过滤 content visibility + category/genre/tag data scope。
- search-service ES 查询强制过滤 content visibility + category/genre/tag data scope。

```text
focused tests passed:
shared:ircs-common
platform:ircs-migrator
services:ircs-identity-service
services:ircs-interaction-service
services:ircs-admin-bff
services:ircs-portal-bff
services:ircs-portal-service
services:ircs-search-service
services:ircs-content-service

affected bootJar passed:
platform:ircs-migrator
services:ircs-identity-service
services:ircs-interaction-service
services:ircs-admin-bff
services:ircs-portal-bff
services:ircs-portal-service
services:ircs-search-service
services:ircs-content-service
```

## 028-20-07 完成证据

versioned config event + credential lease cache + cache governance：

- `SystemConfigChangedEvent` 升级为 `eventId/key/action/effectiveSource/sensitive/revision/previousRevision/changedAt`，旧无 revision payload 不再兼容。
- config-service `SystemConfigRecord` / `SystemConfigSummary` 暴露 `revision`，create/update/delete 事件携带 revision 轨迹。
- identity-service `security.jwt.iat-floor` side-write 同步切到 versioned config event。
- `ProviderCredentialLease` 暴露 `revision/leasedAt/expiresAt`。
- credential-service lease 接入短 TTL cache；create/update/delete/refreshPool best-effort evict。
- shared common 提供 `CacheRegistry`、`TtlGovernedCache` 与 `/internal/cache-governance` summary/evict endpoint。
- migrator 补 `system_configs.version`、`sys_credentials.version` not-null/default/index。

```text
focused tests passed:
shared:ircs-contracts
shared:ircs-common
platform:ircs-migrator
services:ircs-config-service
services:ircs-credential-service
services:ircs-identity-service
services:ircs-content-service
services:ircs-normalization-worker
services:ircs-aggregation-worker
services:ircs-search-service
services:ircs-notification-worker
services:ircs-ops-service
services:ircs-storage-service
services:ircs-portal-service
services:ircs-interaction-service
services:ircs-metadata-worker

affected bootJar passed:
shared:ircs-contracts
shared:ircs-common
platform:ircs-migrator
services:ircs-config-service
services:ircs-credential-service
services:ircs-identity-service
```

## 028-20-08 完成证据

三类审计、DB->ES 副本、retention、统一归档表：

- request audit 已写入 `audit_class`，安全类路径归 `SECURITY`，ops/internal 类路径归 `SYSTEM`，普通业务 API 归 `BEHAVIOR`。
- worker/job audit 与 notification mail send history 已写入 `SYSTEM`。
- request / worker / mail audit 写入后 best-effort 创建 `audit_es_replication_outbox` 记录；outbox 失败不阻断主审计。
- notification mail send history cleanup 在真实删除前写入 `audit_archive_entries`。
- ops-service 新增 archive/outbox summary 只读入口。
- 按破坏性 schema 迁移处理，notification-worker 本地 schema 与 migrator 基础 changelog 均改成新目标态。

```text
focused tests passed:
shared:ircs-common
platform:ircs-migrator
services:ircs-notification-worker
services:ircs-ops-service

affected bootJar passed:
shared:ircs-common
platform:ircs-migrator
services:ircs-notification-worker
services:ircs-ops-service

affected service tests passed:
services:ircs-config-service
services:ircs-credential-service
services:ircs-catalog-service
services:ircs-content-service
services:ircs-identity-service
services:ircs-interaction-service
services:ircs-magnet-service
services:ircs-portal-service
services:ircs-scraper-service
services:ircs-search-service
services:ircs-storage-service
services:ircs-task-service
```

## 028-20-09 完成证据

ops-service maintenance scheduler：

- `ops-service` 增加统一 scheduler 状态与 run-once API。
- 默认 `APP_OPS_MAINTENANCE_SCHEDULER_ENABLED=false`，启用后默认 `dry-run=true`，`execute-enabled=true`。
- scheduler dry-run / execute gate refusal / execute delegation 均写 `worker_job_audit_events`。
- runner metadata 增加 owner-step；多 owner service 后续按 workflow step 分配，ops 只做调度治理。
- `search-reindex-unified` 从 ops 直接写 search outbox 改为调用 search-service internal `/internal/v1/search/sync-tasks/batch`。
- owner call 使用 `INTERNAL_SERVICE` outbound policy、service identity headers、`search:sync` scope 和 correlation header。
- search-service 多副本依赖 K8S Service DNS、无状态 owner API、共享 DB outbox 与 `FOR UPDATE SKIP LOCKED` lease；本地内存索引场景需后续单独广播/重建策略。
- dev manifest 显式保持 scheduler disabled/dry-run，并配置 search-service owner base URL。

```text
focused tests passed:
services:ircs-ops-service

affected bootJar passed:
services:ircs-ops-service
```

## 028-20-09B 完成证据

maintenance scoped write/consumer gate：

- `maintenance_operations` 持久模型已加入 migrator，记录 owner service、resource type、resource scope、gate mode、status、TTL、requestedBy 和 correlationId。
- `shared:ircs-contracts` 增加 `MaintenanceGateMode` / `MaintenanceGateStatus`；`READ_ONLY` / `QUIESCE_WRITES` 阻断 WRITE，`PAUSE_CONSUMERS` 阻断 CONSUMER。
- `shared:ircs-common` 增加 `MaintenanceWriteGateInspector`、`MaintenanceGateDecision` 与 `MaintenanceGateLockedException`。
- `CommonApiExceptionHandler` 将 gate 命中统一映射为 HTTP 423 `maintenance.gate.locked`，保留 trace/correlation/error envelope。
- `ops-service` 暴露 `/api/v1/ops/maintenance/gates`、`/gates/{id}/close`、`/gates/check`。
- gate 默认 TTL 15m、最大 TTL 6h，支持 runtime/env 覆盖，避免异常永久停写。

```text
focused tests passed:
shared:ircs-common MaintenanceWriteGateInspectorTest
services:ircs-ops-service MaintenanceGate*, MaintenanceControllerTest, OpsConfigValuesTest

affected bootJar passed:
services:ircs-ops-service
platform:ircs-migrator
```

遗留边界：

- content/search/storage/credential 等 owner service 写入口和 worker consumer 仍需逐项接入 `MaintenanceWriteGateInspector`。
- 当前不会为每个 runner 自动创建 gate；破坏性/覆盖性 runner 后续按 owner/resource/scope 显式创建。

## 028-20-09C 完成证据

content-service maintenance write gate first owner：

- 新增 `ContentMaintenanceGate`，owner service 固定为 `content-service`。
- generic `video/*` gate 可同时阻断 raw/unified video 写。
- specific `raw-video/<id>` / `unified-video/<id>` gate 可按单资源阻断。
- raw video `create/update/delete/batchDelete/reNormalize/reEnrich/reFetch` 在写 DB 或 dispatch worker 前先检查 gate。
- unified video `create/update/delete/batchDelete/recalculateMetadata` 在写 DB 或 dispatch aggregation/search 前先检查 gate。
- 命中 gate 时沿用 shared `MaintenanceGateLockedException`，由统一错误模型返回 423。

```text
focused tests passed:
services:ircs-content-service ContentMaintenanceGateTest
services:ircs-content-service RawVideoAdminServiceTest
services:ircs-content-service UnifiedVideoAdminServiceTest

affected bootJar passed:
services:ircs-content-service
```

遗留边界：

- content-service people/auxiliary/source-domain 写入口尚未接入。
- search/storage/credential owner service 写入口与 worker consumer `PAUSE_CONSUMERS` 仍后置。

## 028-20-09D 完成证据

maintenance gate versioned event + Redis hot cache：

- 新增 `MaintenanceGateChangedEvent`，使用 `maintenance.gate.changed` routing key，携带 `eventId`、`operationId`、`action`、`ownerService`、`resourceType`、`resourceScope`、`revision`、`changedAt` 和 `correlationId`。
- `MaintenanceGateResponse` 暴露 DB `version`，创建 gate revision=0，关闭 gate 后 `version + 1`。
- `ops-service` 在 create/close 成功后 best-effort 发布 gate changed event；Rabbit/序列化失败只记录 warn，不回滚 DB 事实。
- `content-service` 新增 `q.content.maintenance_gate_changed` durable queue + DLQ，并以 `APP_CONTENT_MAINTENANCE_GATE_LISTENER_ENABLED` 显式控制 listener。
- content gate 热路径改为 L1 service local blocked cache + L2 Redis/Valkey blocked cache + L3 DB fallback；DB 是唯一真相。
- 出于安全语义，gate 只缓存短 TTL blocked decision，不缓存 allow decision，避免新建 gate 后被旧 allow 缓存误放行。
- gate event 到达后清空 content L1 cache，并删除 Redis 中对应 scoped blocked key；miss/bad payload/Redis down 均回落 DB。
- dev content-service manifest 已补 `SPRING_DATA_REDIS_HOST/PORT` 与 maintenance gate listener 开关，避免默认连 localhost。

```text
focused tests passed:
shared:ircs-contracts compileJava
services:ircs-ops-service MaintenanceGate*, MaintenanceControllerTest
services:ircs-content-service ContentMaintenanceGateTest, MaintenanceGateChangedListenerTest, ContentMaintenanceGateEventTopologyTest, ContentAutoStartGateHealthConfigurationTest, RawVideoAdminServiceTest, UnifiedVideoAdminServiceTest

affected bootJar passed:
services:ircs-ops-service
services:ircs-content-service
```

遗留边界：

- 目前 Redis hot cache 先落 content-service gate 判定；search/storage/credential 等 owner service 接 gate 时复用同一 L1/L2/L3 口径。
- `PAUSE_CONSUMERS` 的 worker consumer 失效/暂停仍需按 owner service 单独接入。
- wildcard scope 的 Redis 全量失效已支持 scan，但 dev/prod 大规模 key 数量上升后可再升级为 revision key 或 set-index 失效。
- D30 已拍板：读多写少业务数据可逐步采用 L1/L2/L3 多级缓存或 owner-service materialized read model；DB 保持唯一真相，service-local cache 必须可失效、可重建。

## 028-20-10 应用层完成证据

mTLS service identity destructive migration 第一阶段：

- credential-service internal lease 和 search-service internal sync 已从旧共享 token 切到 `X-IRCS-SERVICE-ID` / `X-IRCS-SERVICE-TOKEN` / `X-IRCS-SERVICE-SCOPES`。
- metadata-worker、notification-worker、normalization-worker credential-service 客户端已发送新 service identity headers。
- 本切片涉及配置不再 fallback 到 `APP_*_INTERNAL_TOKEN`，改用 `APP_*_SERVICE_TOKEN`。
- dev credential-service manifest 改为读取 `SERVICE_CREDENTIAL_TOKEN`。
- 真实 mTLS 证书、Secret 注入、服务端 client cert 校验和 K8S rollout smoke first slice 已由 `028-20-25` 完成；Service-backed internal server 全量 mTLS 已由 `028-20-26` 完成。

```text
focused tests passed:
shared:ircs-common
services:ircs-credential-service
services:ircs-metadata-worker
services:ircs-notification-worker
services:ircs-normalization-worker
services:ircs-search-service

affected bootJar passed:
shared:ircs-common
services:ircs-credential-service
services:ircs-metadata-worker
services:ircs-notification-worker
services:ircs-normalization-worker
services:ircs-search-service
```

## 028-20-10B 完成证据

K8S mTLS rollout first slice：

- `deploy/k8s/dev/mtls-certificates.yaml` 增加 cert-manager dev CA、Issuer 和 covered workload certificates。
- 证书开启 PKCS12 `keystore.p12` / `truststore.p12`，供 Spring Boot server SSL 和 JVM client SSL 使用。
- `scripts/k8s/apply-dev-mtls-first-slice.ps1` 默认 dry-run，`-Execute` 才会真实 patch dev Deployment。
- rollout 脚本覆盖 credential-service/search-service server SSL、client-auth need、TCP probes。
- rollout 脚本覆盖 metadata-worker、notification-worker、normalization-worker、admin-bff、ops-service、portal-bff、aggregation-worker 的 client cert mount、JVM SSL system properties 和 HTTPS base URL。
- dev `metadata-worker` manifest 已从旧 `INTERNAL_CREDENTIAL_TOKEN` secret key 切到 `SERVICE_CREDENTIAL_TOKEN`。
- provider/storage/notification live smoke 脚本已改为优先 `SERVICE_CREDENTIAL_TOKEN`，旧 `INTERNAL_CREDENTIAL_TOKEN` 仅作为历史 fallback。
- notification SMTP one-shot runtime patch 已改为 `APP_MAIL_CREDENTIAL_SERVICE_TOKEN` + `SERVICE_CREDENTIAL_TOKEN`，不再注入旧 `APP_CREDENTIAL_INTERNAL_TOKEN`。

```text
dry-run plan passed:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\k8s\apply-dev-mtls-first-slice.ps1

kubectl client dry-run passed:
kubectl apply --dry-run=client -f deploy\k8s\dev\mtls-certificates.yaml

smoke script parser passed:
11 provider/storage/notification smoke scripts parsed by System.Management.Automation.Language.Parser
```

遗留边界：

- 真实 dev rollout 与 smoke first slice 已由 `028-20-25` 闭环；全 Service-backed internal server mTLS live 已由 `028-20-26` 闭环。
- search-service internal token gate 已在 dev 强制开启，`SERVICE_SEARCH_TOKEN` 由 K8S Secret 注入，不写入 repo。
- normalization LLM credential resolver 已由 `028-20-11D` 迁移到 shared outbound client；真实 credential-service mTLS 基础链路已在 `028-20-25` 验证，全 internal server mTLS 已在 `028-20-26` 验证。

## 028-20-25 完成证据

mTLS live rollout：

- `scripts/k8s/apply-dev-mtls-first-slice.ps1 -Execute` 已真实执行。
- credential-service/search-service server 端启用 HTTPS + `SERVER_SSL_CLIENT_AUTH=need`。
- metadata-worker、notification-worker、normalization-worker、admin-bff、ops-service、portal-bff、aggregation-worker 已挂载独立 client cert 并注入 JVM keyStore/trustStore。
- `SERVICE_SEARCH_TOKEN` 已写入 `ircs-dev-secrets`，search-service internal sync API 已开启 `APP_SEARCH_SERVICE_REQUIRE_TOKEN=true`。
- Prometheus 通过 `ircs-prometheus-scraper-mtls` client cert 抓取 credential/search HTTPS metrics。

```text
script parser passed:
scripts/k8s/apply-dev-mtls-first-slice.ps1
scripts/smoke/mtls-first-slice-live-smoke.ps1

live smoke passed:
MARK 1/5 credential-service mTLS readiness passed
MARK 2/5 search-service mTLS readiness passed
MARK 3/5 no-client-cert request rejected by mTLS
MARK 4/5 credential-service token gate passed with status 200
MARK 5/5 search-service token gate passed without outbox write

rollout and monitoring:
ircs-dev deployments Ready 25/25
mTLS certificates Ready 11/11
Prometheus ircs-dev targets 20/20 UP
portal-bff -> search-service suggest HTTP 200
```

## 028-20-26 完成证据

internal server mTLS rollout：

- 16 个 Service-backed Spring/BFF server 已启用 HTTPS + `SERVER_SSL_CLIENT_AUTH=need`。
- admin-bff/portal-bff 也作为 server 切 mTLS；frontend-gateway 作为 nginx mTLS client，挂载 `ircs-frontend-gateway-mtls` 访问 BFF。
- portal frontend SSR `BACKEND_URL` 改走 frontend-gateway，由 gateway 持证转发到 portal-bff。
- admin/portal BFF 到所有后端 target base URL 改为 HTTPS。
- content/task/identity 到 scraper/storage owner call 改为 HTTPS。
- Prometheus Service-backed targets 改为 HTTPS+mTLS ServiceMonitor；worker-only PodMonitor 继续 HTTP。

```text
script parser passed:
scripts/k8s/apply-dev-mtls-first-slice.ps1
scripts/k8s/apply-dev-mtls-internal-servers.ps1
scripts/smoke/mtls-internal-servers-live-smoke.ps1

live rollout:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\k8s\apply-dev-mtls-internal-servers.ps1 -Execute
all affected deployments successfully rolled out

live smoke:
MARK 1/20..16/20 all Service-backed Spring/BFF mTLS readiness passed
MARK 17/20 catalog-service rejected no-client-cert request
MARK 18/20 portal-bff rejected no-client-cert request
MARK 19/20 gateway portal->portal-bff->portal-service passed
MARK 20/20 gateway admin->admin-bff->identity-service passed

cluster:
ircs-dev deployments Ready 25/25
mTLS certificates Ready 22/22
ResourceQuota: secrets 28/30, pods 28/30, services 25/26
Prometheus ircs-dev targets 20/20 UP
```

遗留边界：

- `GET /api/v1/auth/pow?scope=admin.login` 经 gateway/admin-bff/identity-service 当前返回 500；本轮以 admin login 空体 400 证明 mTLS 传输链路，PoW 业务错误单独排查。
- dev Secret 配额已到 `28/30`，后续新增证书前需要清理或提高 quota。

## 028-20-27 完成证据

manual mTLS removal：

- 用户拍板：mTLS 暂不作为当前目标；传输层安全不能继续由业务服务手动管理。
- `deploy/k8s/dev/mtls-certificates.yaml`、旧 mTLS rollout 脚本和旧 mTLS smoke 脚本已删除。
- frontend gateway 改回 HTTP upstream，不再挂载 `ircs-frontend-gateway-mtls`。
- Prometheus 改回 HTTP ServiceMonitor/PodMonitor，旧 16 个 HTTPS+mTLS ServiceMonitor 已删除。
- live Deployment 中 `SERVER_SSL_*`、`JAVA_TOOL_OPTIONS` keystore/truststore、mTLS volumes 和 internal `https://ircs-*` base URL 已清零。
- cert-manager manual mTLS `Certificate`/`Issuer` 与 `*-mtls` Secrets 已清理。

```text
kubectl apply --dry-run=server -n ircs-dev -f deploy\k8s\dev\frontend-portal-admin-dev.yaml
kubectl apply --dry-run=server -n ircs-dev -f deploy\k8s\dev\observability-monitoring-dev.yaml
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\k8s\remove-dev-manual-mtls.ps1 -Execute

ircs-dev deployments Ready: 25/25
NO_MANUAL_MTLS_IN_DEPLOYMENTS
HTTP readiness smoke: MARK 1/20..20/20 passed
Prometheus ircs-dev active targets: 20
Prometheus ircs-dev up targets: 20
Prometheus HTTPS targets: 0
Prometheus mTLS targets: 0
ResourceQuota secrets: 5/30
```

## 028-20-11A 完成证据

Tika standard package service-scoped restoration：

- V1 `ircs-backend` build 同时声明 `tika-core:3.2.2` 与 `tika-parsers-standard-package:3.2.2`。
- V1 源码实际 Tika 调用点仍是 storage `Tika.detect(byte[])` 与 scraper `UniversalEncodingDetector`，未发现 `AutoDetectParser` / `BodyContentHandler` / PDF parser 通用文本抽取调用。
- V3 storage-service 继续保持 `tika-core` only，避免把 PDF/Office parser 带进图片 MIME 校验服务。
- V3 scraper-service 从窄 `tika-parser-text-module` 切回 `tika-parsers-standard-package:3.2.2`，承接 D06 “只给需要文件解析的服务接入”的边界。
- `scripts/smoke/tika-file-parsing-02816d-smoke.ps1` 依赖 guard 已改为校验 storage core-only、scraper standard package。

```text
focused tests passed:
services:ircs-scraper-service ScraperCharsetDetectorTest, ListScraperClientTest, ManualScraperServiceTaskExecutionTest
services:ircs-storage-service ImageSecurityValidatorTest, FileNormalizationServiceTest

smoke passed:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\tika-file-parsing-02816d-smoke.ps1 -SkipGradle
MARK 1/6..6/6 passed, no live network, no uploads, fixture cleanup verified

affected bootJar passed:
services:ircs-scraper-service
```

遗留边界：

- 本切片只恢复 Tika parser package 边界，不新增 PDF/Office/text extraction API。
- 真实 provider charset smoke 仍归入 `UTB-02816-P1P2-01` 统一测试窗口。
- `028-20-11` 剩余 LLM runner、media 双轨和其他 library behavior follow-up 继续拆分。

## 028-20-11B 完成证据

LLM maintenance owner runner first slice：

- `shared:ircs-contracts` 增加 `LlmCleaningRunResponse`，作为 ops-service 与 normalization-worker 的 internal owner contract。
- normalization-worker 暴露 `POST /internal/v1/normalization/llm-cleaning/{kind}/run`，支持 `language/area/genre/category`，并复用 shared internal service identity/scope 校验。
- `llm-sanitize` 在 ops runner registry 中映射为 V1 language cleaning 语义；`llm-area-clean`、`llm-genre-clean`、`llm-category-clean` 分别映射对应 kind。
- ops-service 新增 `NormalizationLlmMaintenanceClient`，通过 `INTERNAL_SERVICE` outbound policy、`X-IRCS-SERVICE-*` headers 与 `X-Correlation-Id` 调用 normalization owner API。
- LLM maintenance runner 不再被 ops registry dev gate 拒绝；真正执行仍由 normalization 的 `APP_AI_LLM_ENABLED`、`mode=dry-run/fake/live` 与 credential gate 控制，默认不会真实调用 provider。
- `MaintenanceSessionService` 的 SSE 文案改成通用 selected/changed 语义，不再把所有 runner 都描述为 UnifiedVideo 搜索投递。
- dev K8S 为 normalization-worker 补 ClusterIP Service，并在 ops-service manifest 显式配置 normalization owner base URL。

```text
compile passed:
shared:ircs-contracts compileJava
services:ircs-normalization-worker compileJava
services:ircs-ops-service compileJava

focused tests passed:
services:ircs-normalization-worker LlmCleaning*, NormalizationConfigValuesTest
services:ircs-ops-service MaintenanceRunnerServiceTest, MaintenanceSessionServiceTest, NormalizationLlmMaintenanceClientTest, SearchServiceMaintenanceSyncClientTest, OpsConfigValuesTest, MaintenanceControllerTest

affected bootJar passed:
services:ircs-normalization-worker
services:ircs-ops-service

K8S manifest client dry-run passed:
kubectl apply --dry-run=client -f deploy\k8s\dev\normalization-worker-dev.yaml -f deploy\k8s\dev\ops-service-dev.yaml
```

遗留边界：

- 默认 dev 仍保持 no-real-cost：normalization `APP_AI_LLM_ENABLED=false`、LLM cleaning `mode=dry-run`。
- 未执行真实 LLM live smoke；真实单条/成本/DB residue/audit 闭环继续归入 `UTB-02816-P1P2-02`。
- normalization LLM credential resolver 已由 `028-20-11D` 迁移到 shared outbound client；真实 credential-service/mTLS 基础 live smoke 已由 `028-20-25` 覆盖。
- `028-20-11` 剩余 media storage 双轨和其他 behavior-needed library follow-up 继续拆分。

## 028-20-11C 完成证据

storage media R2 canonical cleanup and health：

- 对齐 V1 `ImageProcessorImpl.autoCleanLocalFile` 行为：cover image R2 上传成功后，如果 `app.storage.image.download-enabled=false`，本地文件只作为临时中转，成功切到 R2 后自动清理。
- 当 `app.storage.image.download-enabled=true` 时保留本地副本，继续作为 local cache / dev fallback。
- 本地临时清理失败不反向污染已经成功的 R2 canonical metadata，仅记录 warn；避免 R2 已成功但 metadata 被本地缓存清理异常打回失败态。
- 补回 V1 `R2HealthIndicator` 语义：`/actuator/health` 暴露 R2 gate 状态，disabled 时 UP + `DISABLED_BY_CONFIG`，enabled 但配置/凭据不完整时 DOWN，active 时 UP + bucket/domain/connectivity detail。
- R2 health 只检查配置/凭据 active 状态，不做 HEAD 请求，不消耗 R2 操作额度。

```text
focused tests passed:
services:ircs-storage-service CoverImageR2SyncServiceTest, CoverImageDownloadServiceTest, StorageR2WatchdogTest, CoverImagePhysicalDeletionServiceTest, StorageR2HealthIndicatorTest

affected bootJar passed:
services:ircs-storage-service bootJar
```

遗留边界：

- 未执行真实 R2 live smoke；R2 object PUT/HEAD/DELETE 和 `/actuator/health` live 可见性归入统一 K8S smoke 窗口。
- `028-20-11` remaining library follow-up 仍按“行为是否需要”逐项判断，不机械接回依赖。

## 028-20-11D 完成证据

normalization LLM credential resolver shared outbound adoption：

- `LlmCredentialResolver` 的 credential-service lease 调用不再直接使用原生 JDK `HttpClient`，改为 shared `OutboundHttpClient`。
- lease 请求使用 `INTERNAL_SERVICE` outbound policy，并设置 caller circuit key `normalization-llm-credential-service`。
- 请求继续携带 `Accept: application/json`，并通过 shared `InternalServiceAuthHeaders` 注入 `X-IRCS-SERVICE-ID`、`X-IRCS-SERVICE-TOKEN`、`X-IRCS-SERVICE-SCOPES`。
- runtime API key 仍保持最高优先级；命中 runtime credential 时不会读取 credential-service token，也不会发起 outbound 调用。
- 错误语义保持稳定：非 2xx 返回 `ProviderError`，timeout 返回 `ProviderTimeout`，circuit open 返回固定 `ProviderError`，interrupted 保留线程中断标记。
- 单测用真实 `OutboundCircuitBreaker` 行为打开 circuit，验证第二次 credential lease 不再触达 fake transport。

```text
focused tests and build passed:
services:ircs-normalization-worker LlmCredentialResolverTest, LlmCleaningServiceTest, NormalizationConfigValuesTest
services:ircs-normalization-worker bootJar
```

遗留边界：

- 未执行真实 credential-service / mTLS live smoke；统一 K8S smoke 窗口需要验证 HTTPS client cert、service identity header、circuit ENV override 和 trace/correlation 传播。
- 本切片只迁移 normalization LLM credential resolver；其他 remaining library follow-up 继续按“行为是否需要”拆分。

## 028-20-12A 完成证据

search outbox relay ops observability first slice：

- `shared:ircs-common` 新增 `WorkerInstanceIds`，workerId 解析顺序为显式配置/ENV 优先，兜底 `application@host#pid`，长度限制 128。
- search outbox relay 不再默认使用纯 `spring.application.name` 作为 `locked_by`，改为唯一 workerId；K8S dev manifest 使用 Downward API 将 Pod 名称注入 `APP_SEARCH_OUTBOX_RELAY_WORKER_ID`。
- search-service 新增 `SearchOutboxRelayHealthIndicator`，`/actuator/health` 暴露 relay enabled、workerId、running、最近成功/失败、连续失败次数、最近 published/restored、outbox pending/due/failed/oldest age 等状态。
- relay 禁用时 health 为 UP 且不查询 outbox 表；relay 启用后 outbox stats 不可用或最近 run 失败会 DOWN，便于 K8S/监控发现。
- `SearchOutboxRelayMetrics` 增加 Micrometer 指标：
  - `ircs.search.outbox.relay.runs` / `ircs.search.outbox.relay.run.duration`
  - `ircs.search.outbox.relay.groups{result=published|failed}`
  - `ircs.search.outbox.relay.task.delay`
  - `ircs.search.outbox.relay.published` / `ircs.search.outbox.relay.restored`

```text
focused tests passed:
shared:ircs-common WorkerInstanceIdsTest
services:ircs-search-service SearchOutboxRelayTest, SearchOutboxRelayHealthIndicatorTest, SearchSyncTaskOpsRepositoryTest

affected bootJar passed:
shared:ircs-common jar
services:ircs-search-service bootJar

K8S manifest client dry-run passed:
kubectl apply --dry-run=client -f deploy\k8s\dev\search-service-dev.yaml
```

遗留边界：

- 本切片先覆盖 search outbox relay；worker/job audit `job_source` 唯一化与 Rabbit/runner 基础指标已在 12B/12C 推进。
- `worker_id` tag 只使用副本级值，不拼接实体 ID，避免 Prometheus 高基数爆炸。

## 028-20-12B 完成证据

worker/job audit source instance identity：

- `WorkerJobAuditWriter` 默认 `job_source` 不再直接落纯 `spring.application.name`，而是通过 `WorkerInstanceIds` 解析为 `service@host#pid`。
- `WorkerJobAuditWriter` 增加可选 `workerInstanceId` 构造参数，允许后续由 Pod 名称或显式 ENV 注入稳定副本标识。
- 现有各服务 audit configuration 不需要逐个改写即可受益；K8S 默认 `HOSTNAME` 为 Pod 名，local/dev fallback 为 host + pid。
- `job_source` 仍保留 service prefix，ops 按服务名模糊过滤的查询语义不破坏。
- notification-worker、task-service、aggregation-worker 和 shared writer 测试已改为覆盖唯一副本 source 语义。

```text
focused tests passed:
shared:ircs-common WorkerJobAuditWriterTest, WorkerInstanceIdsTest
services:ircs-notification-worker MailConsumerTest, MailSendHistoryCleanupRunnerTest
services:ircs-aggregation-worker AggregationWorkerAuditConfigurationTest
services:ircs-task-service TaskServiceAuditConfigurationTest
services:ircs-ops-service WorkerJobAuditRepositoryTest

affected bootJar passed:
shared:ircs-common jar
services:ircs-notification-worker bootJar
services:ircs-aggregation-worker bootJar
services:ircs-task-service bootJar
services:ircs-ops-service bootJar
services:ircs-search-service bootJar
```

遗留边界：

- 本切片不改 `worker_job_audit_events` schema，仅提升 source 值的副本可追踪性。
- Rabbit consumer latency/failure 与 maintenance runner 基础 Micrometer 指标已在 12C 通过 `WorkerJobAuditWriter.record(...)` 统一推进。
- K8S live 侧需在统一 smoke 窗口确认 `job_source` 实际呈现 Pod 名称或显式 worker instance id。

## 028-20-12C 完成证据

async worker/job Micrometer baseline metrics：

- `WorkerJobAuditWriter.record(...)` 现在同步记录 Micrometer 原始事实指标，不依赖 DB audit 是否开启。
- 新增 `ircs.worker.job.runs{worker_id,job_type,job_name,outcome}` counter，用于成功/失败计数和失败率 PromQL 派生。
- 新增 `ircs.worker.job.duration{worker_id,job_type,job_name,outcome}` timer，用于 queue consumer、watchdog、runner、scheduler 等异步任务处理耗时观测。
- `worker_id` 与 12B 的 `job_source` 保持同源，默认 `service@host#pid`，显式 worker instance id 可覆盖。
- `WorkerJobAuditWriter.noop()` 继续保持 no-op，不写 DB 也不写 metrics，避免测试/显式禁用场景污染。
- notification Rabbit consumer 与 ops maintenance runner 均有代表性指标测试；其他已接 `WorkerJobAuditWriter` 的异步执行路径通过 shared writer 自动获得同类指标。

```text
focused tests passed:
shared:ircs-common WorkerJobAuditWriterTest, WorkerInstanceIdsTest
services:ircs-notification-worker MailConsumerTest, MailSendHistoryCleanupRunnerTest
services:ircs-ops-service MaintenanceRunnerServiceTest, MaintenanceSchedulerServiceTest, WorkerJobAuditRepositoryTest

affected bootJar passed:
shared:ircs-common jar
services:ircs-notification-worker bootJar
services:ircs-ops-service bootJar
services:ircs-aggregation-worker bootJar
services:ircs-task-service bootJar
services:ircs-search-service bootJar
services:ircs-storage-service bootJar
services:ircs-metadata-worker bootJar
services:ircs-normalization-worker bootJar
services:ircs-scraper-service bootJar
```

遗留边界：

- 失败率不在代码里做 Gauge，统一由 `ircs.worker.job.runs` 的 `outcome` 维度在 Prometheus/Grafana 查询层派生。
- 本切片覆盖已接 `WorkerJobAuditWriter` 的异步执行；未接 audit 的轻量 config event listener 暂不纳入 worker/job metrics。
- K8S live 侧需在统一 smoke 窗口确认 `/actuator/prometheus` 中指标实际可见。

## 028-20-13A 完成证据

multi-tier business cache / portal public read model first slice：

- `shared:ircs-common` 增加 `TieredReadThroughCache` 与 `TieredStringCacheStore`，统一表达 L1 service-local TTL cache + L2 external string cache + L3 loader/DB fallback。
- cache 通过现有 `CacheRegistry` 注册，继续暴露 `/internal/cache-governance` summary/evict 语义。
- external cache decode/read/write/evict 失败只记录 warn 并回落 loader，DB/owner repository 仍是唯一真相。
- portal-service 增加 `PortalReadModelCache`，匿名公共读接入：
  - metadata public cache；
  - home public cache；
  - 无 keyword 且第一页 explore public cache；
  - movie detail public cache。
- portal-service 增加 Redis/Valkey L2 adapter；Redis 不存在时自动降级为 L1 + DB fallback。
- 登录态或带 keyword explore 不进入公共 read-model cache，避免 data scope、visibility 或个性化结果污染公共缓存。
- 默认 TTL：metadata/detail 5m，home/explore 60s，均可通过 `app.portal.cache.*` / ENV 覆盖；`app.portal.cache.enabled=false` 可关闭。

```text
focused tests and build passed:
shared:ircs-common TieredReadThroughCacheTest, TtlGovernedCacheTest
services:ircs-portal-service PortalQueryServiceTest, PortalControllerTest
services:ircs-portal-service bootJar
```

遗留边界：

- 本切片不把所有业务数据一次性缓存化；后续按 owner service 和读写热度推进 catalog dictionary、search/sidebar、credential/provider summaries 等候选。
- portal read model 当前依赖短 TTL + governance evict；content/search 变更事件精确失效仍是后续增强，不为强一致性阻断业务读。
- 未执行 K8S Redis live smoke；统一测试窗口需确认 Redis L2 key 写入、跨副本命中、`/internal/cache-governance` 可见性和 evict 行为。

## 028-20-13B 完成证据

catalog standard dictionary multi-tier cache：

- catalog-service 接入 `spring-boot-starter-data-redis`，并复用 shared `TieredReadThroughCache` 作为标准字典 read-model cache 基座。
- 新增 `CatalogReadModelCache`，覆盖标准字典热读：
  - `/api/v1/catalog/standard-categories` summary list；
  - `/api/v1/catalog/standard-genres` summary list；
  - `/api/v1/catalog/standard-areas` summary list；
  - `/api/v1/catalog/standard-languages` summary list；
  - `/api/v1/standard-categories` / `all` read list；
  - `/api/v1/standard-genres` read list；
  - `/api/v1/standard-areas` read list；
  - `/api/v1/standard-languages` read list。
- 标准字典 create/update/patch/delete 成功后 evict 对应 summary/read 两类 cache；raw mapping、data-source 和分页查询暂不缓存。
- Redis/Valkey 只作为 L2；miss/down/bad payload 均回落 catalog repository / DB，DB 仍是唯一真相。
- dev manifest 为 catalog-service 显式配置 `SPRING_DATA_REDIS_HOST=valkey-svc.ircs-dev.svc.cluster.local`、`SPRING_DATA_REDIS_PORT=6379`、`APP_CATALOG_CACHE_ENABLED=true` 和 `APP_CATALOG_CACHE_STANDARD_DICTIONARY_TTL=PT10M`，不新增 Pod/Service，不改变资源 requests/limits。

```text
focused tests and build passed:
shared:ircs-common TieredReadThroughCacheTest
services:ircs-catalog-service CatalogServiceReadModelCacheTest, CatalogControllerTest, CatalogServiceDataSourceSyncTriggerTest
services:ircs-catalog-service bootJar

K8S manifest client dry-run passed:
kubectl apply --dry-run=client -f deploy\k8s\dev\catalog-service-dev.yaml
```

遗留边界：

- 本切片不缓存 raw category/genre/area/language mapping 与分页管理查询；这些读写更频繁，后续需要更明确的事件失效或短 TTL 策略。
- catalog default initializer 仍直接写 repository；启动期 cache 通常尚未承载流量，若未来支持运行时 reseed，需要同步触发 cache evict。
- 未执行 K8S Redis live smoke；统一测试窗口需确认 catalog L2 key、跨副本读取和 `/internal/cache-governance` evict 实际可见。

## 028-20-13C 完成证据

search portal read-model multi-tier cache：

- search-service 接入 `spring-boot-starter-data-redis`，并复用 shared `TieredReadThroughCache` 作为 portal search 热读 cache 基座。
- 新增 `SearchPortalReadModelCache`，覆盖匿名公共读：
  - `/api/portal/search/suggest`；
  - `/api/portal/search/recommendations/{videoId}`。
- 缓存只对 anonymous/public principal 生效；member/admin/data-scope/visibility 相关查询继续直读 Elasticsearch，避免权限结果串用。
- unified search index `saveUnified` / `delete(UNIFIED_VIDEO)` 成功后 evict public suggest/recommend cache；raw video index 删除不触发 portal public search cache evict。
- Redis/Valkey 只作为 L2；miss/down/bad payload 均回落 Elasticsearch，ES/DB 仍是唯一真相。
- dev manifest 为 search-service 显式配置 `SPRING_DATA_REDIS_HOST=valkey-svc.ircs-dev.svc.cluster.local`、`SPRING_DATA_REDIS_PORT=6379`、`APP_SEARCH_CACHE_ENABLED=true`、`APP_SEARCH_CACHE_PORTAL_SUGGEST_TTL=PT60S` 和 `APP_SEARCH_CACHE_PORTAL_RECOMMEND_TTL=PT60S`，不新增 Pod/Service，不改变资源 requests/limits。

```text
focused tests and build passed:
shared:ircs-common TieredReadThroughCacheTest
services:ircs-search-service PortalSearchQueryServiceTest, SearchIndexServiceTest, PortalSearchControllerTest
services:ircs-search-service bootJar

K8S manifest client dry-run passed:
kubectl apply --dry-run=client -f deploy\k8s\dev\search-service-dev.yaml
```

遗留边界：

- search 查询主搜索、admin 搜索和带 keyword/data scope 的个性化结果暂不缓存；后续需要更细粒度 key 与事件失效策略。
- search public cache 当前依赖短 TTL + index save/delete evict；跨服务 content/aggregation 侧的精准事件失效仍通过 search index 写入路径间接触发。
- 未执行 K8S Redis live smoke；统一测试窗口需确认 search L2 key、跨副本读取和 `/internal/cache-governance` evict 实际可见。

## 028-20-13D 完成证据

magnet provider/cache read-model multi-tier cache：

- magnet-service 接入 `spring-boot-starter-data-redis`，并复用 shared `TieredReadThroughCache` 作为 provider/cache 热读 cache 基座。
- 新增 `MagnetReadModelCache`，覆盖：
  - `/api/v1/magnet-providers` provider list；
  - `/api/v1/magnet-providers/{id}` provider detail；
  - provider search runner 使用的 enabled provider list；
  - `/api/v1/magnets/unified/{id}` approved magnet links。
- provider create/update 成功后 evict provider list、enabled list 与 detail cache。
- `triggerUnifiedSearch` 发生 link upsert 后按 `unifiedVideoId` evict approved-links cache，避免旧 approved links 读模型滞留。
- Redis/Valkey 只作为 L2；miss/down/bad payload 均回落 magnet repository / DB，DB 仍是唯一真相。
- dev manifest 为 magnet-service 显式配置 `SPRING_DATA_REDIS_HOST=valkey-svc.ircs-dev.svc.cluster.local`、`SPRING_DATA_REDIS_PORT=6379`、`APP_MAGNET_CACHE_ENABLED=true`、`APP_MAGNET_CACHE_PROVIDER_TTL=PT5M` 和 `APP_MAGNET_CACHE_APPROVED_LINKS_TTL=PT60S`，不新增 Pod/Service，不改变资源 requests/limits。

```text
focused tests and build passed:
shared:ircs-common TieredReadThroughCacheTest
services:ircs-magnet-service MagnetQueryServiceTest, MagnetControllerTest
services:ircs-magnet-service bootJar

K8S manifest client dry-run passed:
kubectl apply --dry-run=client -f deploy\k8s\dev\magnet-service-dev.yaml
```

遗留边界：

- 本切片不缓存 magnet search job/run 管理查询；这些状态变化更频繁，后续应优先依赖 no-store 或短 TTL。
- provider/detail cache 失效当前由 magnet-service 写入口触发；直接 DB 改动仍依赖 TTL 兜底。
- 未执行 K8S Redis live smoke；统一测试窗口需确认 magnet L2 key、跨副本读取和 `/internal/cache-governance` evict 实际可见。

## 028-20-13E 完成证据

credential redacted summary read-model multi-tier cache：

- credential-service 接入 `spring-boot-starter-data-redis`，并复用 shared `TieredReadThroughCache` 作为 admin/API 红acted credential summary 热读 cache 基座。
- 新增 `CredentialReadModelCache`，覆盖：
  - `/api/v1/credentials` summary list；
  - `/api/v1/credentials/{id}` summary detail。
- cache value 只存储 `CredentialSummary`，即已经由 `CredentialSanitizer` 红acted 的 API 返回对象；`ProviderCredentialLease` secret payload 仍保持原有 service-local 短 TTL cache，不写入 Redis/Valkey。
- credential create/update/delete/createIfAbsent/refreshPool 成功后 evict redacted summary cache，并保持 lease cache evict 语义。
- Redis/Valkey 只作为 L2；miss/down/bad payload 均回落 credential repository / DB，DB 仍是唯一真相。
- dev manifest 为 credential-service 显式配置 `SPRING_DATA_REDIS_HOST=valkey-svc.ircs-dev.svc.cluster.local`、`SPRING_DATA_REDIS_PORT=6379`、`APP_CREDENTIAL_CACHE_ENABLED=true` 和 `APP_CREDENTIAL_CACHE_SUMMARY_TTL=PT60S`，不新增 Pod/Service，不改变资源 requests/limits。

```text
focused tests and build passed:
shared:ircs-common TieredReadThroughCacheTest
services:ircs-credential-service CredentialServiceTest, CredentialControllerTest
services:ircs-credential-service bootJar

K8S manifest client dry-run passed:
kubectl apply --dry-run=client -f deploy\k8s\dev\credential-service-dev.yaml
```

遗留边界：

- 出于安全边界，本切片不把包含 secret payload 的 credential lease 写入 Redis/Valkey；后续若需要跨副本 lease cache，必须先引入加密/敏感 cache policy。
- templates/provider catalog 仍直接本地计算，不需要 Redis cache。
- 未执行 K8S Redis live smoke；统一测试窗口需确认 credential L2 key、跨副本读取和 `/internal/cache-governance` evict 实际可见。

## 028-20-15 完成证据

distributed lock manager multi-service rollout：

- shared common 已提供统一 `DistributedLockManager`，覆盖 Redis mutex lease、time-slice reservation 和 token bucket 三类锁语义。
- scraper-service datasource 出站抓取、magnet-service provider 出站抓取已接时间片预占用。
- task-service default seed/startup recovery/watchdog、search-service outbox relay/reconciliation、aggregation-worker scheduler、normalization-worker LLM cleaning/retry watchdog、config-service initializer、catalog-service default seed、content-service resolver preset seed、credential-service runtime initializer、storage-service R2 watchdog、notification-worker mail send history cleanup 已接 mutex lease。
- RabbitMQ consumer、per-task runner、HanLP prewarm 不做机械全局锁；继续由 queue 分发、DB 原子迁移/幂等约束、本地进程语义兜底。
- 后续 `028-20-16/17/18/19/20/21` 复核粗锁粒度后，已将 normalization LLM cleaning、normalization retry watchdog、storage R2 watchdog 下沉到 DB claim lease/claim-first，移除 search outbox relay / aggregation batch 的外层吞吐型 mutex，为 aggregation bind/create 补 match-key 细粒度 Redis mutex，并补强 LLM cleaning / normalization retry watchdog 的失败释放语义；search reconciliation 仍按合理 singleton 保留。

```text
focused tests and bootJar passed:
services:ircs-scraper-service
services:ircs-task-service
services:ircs-search-service
services:ircs-magnet-service
services:ircs-aggregation-worker
services:ircs-normalization-worker
services:ircs-config-service
services:ircs-catalog-service
services:ircs-credential-service
services:ircs-storage-service
services:ircs-notification-worker
```

遗留边界：

- K8S Redis live / 多副本 contention smoke 后置到统一测试窗口。
- 当前多数 lock TTL 默认 `PT10M`；若后续真实 runner 超过 TTL，需要补 renew 或按 runner 拉长 TTL。
- Rabbit consumer 的幂等性、dead-letter 和重放行为仍需在统一测试中按队列单独审计。

## 028-20-23 完成证据

no `@Autowired` / single-constructor / dev rollout health：

- 生产源码 `@Autowired` 清零。
- Spring stereotype class 显式多构造器风险清零。
- `ircs-content-service` live Deployment 已 apply Redis/Valkey env，解除 `localhost:6379` CrashLoop。
- `ircs-dev` Deployment readiness 恢复 25/25。
- 运行中应用镜像 digest 已落账到 `docs/reports/112-no-autowired-single-constructor-dev-rollout.md`。
- 16 个有 Service 的 Java/BFF 服务 `/actuator/health/readiness` 返回 `UP`。
- 4 个无 Service 的 worker 通过 Pod IP `/actuator/health/readiness` 返回 `UP`。

```text
PROD_AUTOWIRED_COUNT=0
SPRING_MULTI_CONSTRUCTOR_RISK_COUNT=0
ircs-content-service Ready 1/1 restart=0
ircs-dev deployments Ready 25/25
service health smoke UP: 16/16
worker health smoke UP: 4/4
```

## 028-20-24 完成证据

Prometheus / health components / shared Redis env live：

- `ircs-dev` 已接入现有 Prometheus stack，不新增 Prometheus/Alertmanager/Grafana，不触碰 production `ircs-system` ArgoCD app。
- 新增 `ServiceMonitor/ircs-dev-service-monitor` 和 `PodMonitor/ircs-dev-worker-pod-monitor`，覆盖 16 个 Service-backed Java/BFF target 和 4 个 worker-only Pod。
- `ircs-dev-app-config` 扩展为统一 runtime shared env，Java dev manifest 通过 `envFrom.configMapRef.name=ircs-dev-app-config` 注入 health/Redis 公共配置，移除散落的 `SPRING_DATA_REDIS_*` 和 `MANAGEMENT_ENDPOINT_HEALTH_SHOW_COMPONENTS` 单项 env。
- BFF CrashLoop 根因是 `SearchSyncOutboxWriter` 在 BFF classpath 无 `spring-jdbc` 时解析 `JdbcTemplate.class` 条件失败；已改为 string-based `@ConditionalOnClass` + `@ConditionalOnBean(type=...)`，并重推 ARM64 BFF 镜像。
- `storage-service` 根 health 中 Redis/distributedLock DOWN 已修复为 UP。
- `notification-worker` dev 下 `APP_MAIL_ENABLED=false` 时同步禁用 mail health，根 health 恢复 UP。

```text
BFF focused tests BUILD SUCCESSFUL
observability-monitoring-dev.yaml server dry-run passed
19 Java dev manifests server dry-run passed
ircs-dev deployments Ready 25/25
20/20 Java Deployment /actuator/health = UP
20/20 Java Deployment health components visible
MISSING_HEALTH_ENV=0
MISSING_REDIS_PORT_ENV=0
Prometheus ircs-dev active targets: 20
Prometheus ircs-dev up targets: 20
NO_AUTOWIRED_IN_PRODUCTION=OK
```

## 下一切片

剩余 post-parity runtime/platform 切片：

- `028-20-11`：remaining library behavior follow-up；LLM/R2 live smoke 后置到统一测试窗口。
- `028-20-12`：Prometheus scrape path 已完成 live；后续在真实 runner/lock contention 窗口补 `worker_job`、`search_outbox_relay` 和 lock skipped/failed 事件样本。
- `028-20-13`：继续把 D30 多级业务缓存从 portal/catalog/search/magnet/credential first slices 扩展到剩余安全/业务热读；逐服务接入 L1/L2/L3，DB 仍是唯一真相。
- `028-20-15`：后续统一测试窗口补 K8S 多副本 Redis lock contention smoke；若真实 runner 超过 TTL，再补 renew 或按 runner 拉长 TTL。
