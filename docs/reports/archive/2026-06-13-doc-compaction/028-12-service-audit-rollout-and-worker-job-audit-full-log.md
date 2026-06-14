# 028-12 service audit rollout and worker job audit 后续子任务

状态：当前已知 service/worker audit matrix 实现闭环；ingestion-worker live rollout/smoke 后置

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `tasks/done/028-11-per-service-audit-collection-parity.md`
- `docs/reports/055-per-service-audit-collection-parity-analysis.md`
- `docs/migrations/057-per-service-audit-config-handler.md`
- `docs/reports/058-02812-catalog-service-audit-first-slice.md`
- `docs/migrations/058-catalog-service-audit-handler.md`

## 背景

`028-11` 已证明首个业务服务 `ircs-config-service` 可以写入 service handler audit，并可通过
`ops-service` 使用 `requestSource` 查询。但 V1 行为级迁移最终需要更广的后端可观察性：
其他 HTTP 服务、服务间调用、queue consumer、scheduler 和 maintenance runner 仍缺统一事件
口径。

## 目标

- 按服务风险顺序铺开 service handler audit。
- 为 worker/job audit 落地首个 queue consumer 或 maintenance runner 事件写入。
- 让 ops-service 能查询 HTTP request audit 与 worker/job audit 的关联证据。
- 保持不采集请求体、Authorization、Cookie、credential 或 token。

本轮第一刀目标：

- 选择低风险 HTTP 服务 `ircs-catalog-service` 接入 `ServiceRequestAuditFilter`。
- 本地单元测试证明 catalog-service handler 请求写入 `request_audit_logs.request_source=ircs-catalog-service`。
- 不做 worker/job audit 表结构大改动；仅在报告和迁移记录中保留独立 job audit 事件口径。

## 非目标

- 不一次性修改全部服务。
- 不引入新中间件或高写入量日志系统。
- 不新增公共 HTTPRoute。
- 不做生产审计合规设计。
- 本轮不修改 `services/ircs-metadata-worker/**`、`services/ircs-normalization-worker/**` 或 `services/ircs-storage-service/**`。
- 本轮不修改 `tasks/index.md`、`docs/index.md` 或当前 K8S 环境总快照。

## 输入

- `ServiceRequestAuditFilter`
- `request_audit_logs.request_source`
- queue consumer message id / trace id
- scheduler session id / maintenance runner session id

## 输出

- 至少一个新增 HTTP service handler audit 接入。
- 至少一个 worker/job audit 事件实现或专用表设计。
- ops-service 查询或报告增强。
- smoke 脚本、报告、迁移记录和任务回写。

本轮第一刀输出：

```text
services/ircs-catalog-service/src/main/java/com/prodigalgal/ircs/catalog/CatalogServiceAuditConfiguration.java
services/ircs-catalog-service/src/test/java/com/prodigalgal/ircs/catalog/CatalogServiceAuditFilterTest.java
services/ircs-catalog-service/build.gradle
docs/reports/058-02812-catalog-service-audit-first-slice.md
docs/migrations/058-catalog-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 影响范围

```text
shared/ircs-common/**
services/ircs-catalog-service/**
docs/reports/*02812*.md
docs/migrations/*audit*.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 依赖

- `028-11` 已完成并部署。
- dev PostgreSQL 有 `request_source` 字段。
- 待接入服务保持 Ready，Hikari pool 仍为低资源 profile。

## 风险

- 全服务铺开可能增加 DB 写入，应分批、小流量、可关闭。
- worker/job audit 若复用 HTTP 表会污染 request 语义，需谨慎设计。
- 审计字段必须避免敏感信息泄露。

## 验收标准

- 新增至少一个非 config-service 的 service handler audit MARK。
- worker/job audit 有首个真实事件写入或完成专用 schema + smoke。
- ops-service 能查询并区分 HTTP audit 与 worker/job audit。
- smoke 后 DB residue、HTTPRoute 和 ResourceQuota 均保持干净。

本轮第一刀验收：

```text
MARK 1/2 catalog-service handler request writes request_source
MARK 2/2 catalog-service audit filter skips actuator noise
```

worker/job audit 的真实事件写入或专用 schema + smoke 仍为 `028-12` 后续子任务，不纳入本轮
catalog-service 第一刀完成口径。

## 测试边界

- 本地单元测试覆盖新增 filter/config 或 worker event writer。
- ClusterIP smoke 只打最小端点或最小 queue fixture。
- 不执行高风险维护任务或全量队列任务。

本轮第一刀测试边界：

```text
.\gradlew.bat :services:ircs-catalog-service:test --rerun-tasks
```

本轮不 rollout、不新增 HTTPRoute；如后续做 live smoke，只能通过 ClusterIP 最小端点并保持
`ircs-dev` 低资源环境无新增公共路由。

## 当前状态

2026-06-07：第一刀已完成。`ircs-catalog-service` 已接入 service handler audit，本地测试通过。
`028-12` 父范围仍保持进行中，因为 worker/job audit 和其他服务铺开尚未完成。

2026-06-07：worker/job audit 第一刀已完成。新增专用 `worker_job_audit_events`
schema、shared writer，并让 `ircs-notification-worker` 的 `MailConsumer` 在本地测试中
写入成功/失败 queue consumer 事件。`028-12` 父范围仍保持进行中，因为 ops 查询、
live smoke、其他 worker/runner 铺开和更多 HTTP service handler audit 尚未完成。

2026-06-08：完成 worker/job audit ops 查询 API 切片。已在 `ircs-ops-service`
提供 `worker_job_audit_events` 的只读分页查询与 24 小时摘要，使 queue consumer、
scheduler 和 runner 的事件证据能通过 ops 查询面验证。

2026-06-08：启动 notification worker/job audit live queue smoke 切片。目标是在
`ircs-dev` 中让 `q.notification.mail` 真实消费路径写入 `worker_job_audit_events`，
并通过 ops 查询 API 读出可清理 fixture 事件。

2026-06-08：完成 notification worker/job audit live queue smoke 切片。`ircs-dev`
中的 `q.notification.mail` 真实消费路径已写入 `worker_job_audit_events`，并通过
ops 查询 API 读出；fixture DB/queue/DLQ residue 均为 0。

2026-06-08：完成 content-service handler audit 与 live smoke 切片。`ircs-content-service`
已接入 `ServiceRequestAuditFilter`，本地测试通过，并 rollout 到 `ircs-dev`；通过唯一
`X-Trace-Id` 证明 live handler 请求写入 `request_audit_logs.request_source=ircs-content-service`，
fixture DB residue 为 0。

2026-06-08：完成 credential-service handler audit 与 live smoke 切片。`ircs-credential-service`
已接入 `ServiceRequestAuditFilter`，本地敏感边界测试证明 request body、Authorization、
Cookie 和 internal token 不进入 audit 表；live smoke 只访问安全 template endpoint，
fixture DB residue 为 0。

2026-06-08：完成 identity-service handler audit、query token 脱敏与 live smoke 切片。
`ircs-identity-service` 已接入 `ServiceRequestAuditFilter`，shared service/BFF audit
统一脱敏敏感 query 参数；本地测试证明 request body、Authorization、Cookie、query token
和 response token 不进入 audit 表；live smoke 只访问 public PoW endpoint，fixture DB
residue 为 0。

2026-06-08：完成 interaction-service handler audit、query token 脱敏与 live smoke 切片。
`ircs-interaction-service` 已接入 `ServiceRequestAuditFilter`，本地测试证明 feedback body、
Authorization、Cookie、query token 和 response content 不进入 audit 表；live smoke 只访问
public feedback wall endpoint，fixture DB residue 为 0。

2026-06-08：完成 magnet-service handler audit、query token 脱敏与 live smoke 切片。
2026-06-08：完成 storage-service handler audit、query token 脱敏与 live smoke 切片。
2026-06-08：完成 search-service handler audit、query token 脱敏与 live smoke 切片。
2026-06-08：完成 portal-service handler audit、query token 脱敏与 live smoke 切片。
`ircs-magnet-service` 已接入 `ServiceRequestAuditFilter`，本地测试证明 provider write body、
Authorization、Cookie、query token 和 response content 不进入 audit 表；live smoke 只访问
只读 provider list endpoint，fixture DB residue 为 0。

2026-06-10：启动 `028-12-C` 更多 worker/scheduler/runner audit expansion。当前策略为
先推进实现完整，跳过 live queue smoke 和耗时完整测试，后续统一测试。覆盖决策见
`docs/reports/106-02812c-worker-scheduler-runner-audit-expansion.md`；本轮优先实现
metadata-worker provider queue、normalization-worker raw queue / LLM runner、storage-service
avatar/download/R2 promote/watchdog 的 worker/job audit 写入。scraper manual runner 暂由
handler audit + task-service runner audit 覆盖，避免重复计数。

2026-06-10：`028-12-C` implementation first closure 完成，overall partial。metadata provider
queue、normalization raw queue / LLM runner、storage avatar/download/R2 promote/watchdog 已写入
`worker_job_audit_events`；focused tests `metadata=7/7`、`normalization=5/5`、`storage=10/10`
通过。live queue smoke、ops query fixture cleanup 和资源/无副作用检查统一后置到
`UTB-02816-P1P2-03`。

收敛口径：028-12 已有 HTTP service handler audit 和 notification worker/job audit 的 live
闭环证据，但 028-12-C 只按实现完成和 focused tests 入账；metadata/normalization/storage 新增
worker audit hooks 仍需在 `UTB-02816-P1P2-03` 中补 live queue smoke、ops 查询、fixture cleanup
和资源/no-residue 检查后，才能写成 live smoke 闭环。

2026-06-11：`UTB-02816-P1P2-03` 首次执行为 partial，暴露 notification queue/gate 与 live
DB schema 漂移问题；后续已通过 migrator 正式链路和 notification listener wrapper 收敛，重跑
`UTB-02816-P1P2-03` 后 notification/task/search/scraper/resource 7/7 PASSED。`028-12-C/D`
仍保留 metadata/normalization/storage worker live queue smoke 与 ops query cleanup 后置。

2026-06-12：补齐 `028-12-C` metadata/normalization/storage worker live queue smoke 与 ops query
cleanup。执行前完成 `ircs-dev` 运行中镜像 freshness 对账，结果为 `CURRENT_TAG_DIGEST=22`、
`EXTERNAL_OR_INFRA=6`、`STALE_TAG_DIGEST=0`；证据见
`build/tmp/ircs-dev-container-image-freshness-20260612111028.json` 和 `.csv`。本轮修复并滚动
 `ircs-normalization-worker`、`ircs-storage-service`，两者均 Ready 1/1；smoke 脚本
`scripts/smoke/worker-audit-metadata-normalization-storage-02812c-smoke.ps1 -FailOnSkip`
返回 `MARK 6/6 PASSED`，trace 为 `02812c-worker-audit-20260612111112`，metadata/normalization/storage
ops query 均可查，queue/DLQ/DB fixture cleanup 无残留。边界：全项目 `src/main` 已清理
`@Autowired`，但全服务源码当前态重打包/滚动需先继续收敛历史多构造器组件。

2026-06-12：完成 `028-12-E` ingestion-worker worker/job audit closeout。`RawVideoIngestConsumer`
写入 `ingestion.raw-video` 成功/失败 audit，`PlaylistSyncConsumer` 写入
`ingestion.playlist-sync` 成功/失败 audit；错误语义使用稳定包装消息，业务异常继续抛给 Rabbit。
focused tests `ircs-ingestion-worker tests=8 failures=0 errors=0 skipped=0` 通过；live rollout/smoke 后置。

## 本轮 028-12-E ingestion-worker worker/job audit closeout task spec

状态：实现完成，live rollout / smoke 后置

目标：

- 给 `ingestion-worker` 两个队列消费者补 `worker_job_audit_events` 写入。
- 成功和失败路径都记录 job type/name、source、correlationId、duration 和稳定错误语义。
- 失败时不吞业务异常，继续交给 Rabbit listener 的原有重试/DLQ 语义。

范围：

- `RawVideoIngestConsumer`：`q.ingest` / `ingestion.raw-video`。
- `PlaylistSyncConsumer`：`q.process.playlist` / `ingestion.playlist-sync`。
- `IngestionWorkerAuditConfiguration`：按现有 worker audit 配置口径接入 `WorkerJobAuditWriter`。

验收结果：

```text
MARK 1/4 PASS raw video ingestion success writes worker-job audit
MARK 2/4 PASS raw video ingestion failure writes failed audit and rethrows
MARK 3/4 PASS playlist sync success writes worker-job audit
MARK 4/4 PASS playlist sync failure writes failed audit and rethrows
```

验证：

```powershell
.\gradlew.bat --no-daemon :services:ircs-ingestion-worker:test --rerun-tasks
```

结果：

```text
ircs-ingestion-worker tests=8 failures=0 errors=0 skipped=0
```

## 本轮 028-12-C worker/scheduler/runner audit expansion task spec

状态：implementation first closure，overall partial

目标：

- 梳理 normalization、metadata、storage、scraper 等 worker/scheduler/runner 缺口。
- 给 2-3 个高价值 worker/runner 家族补 `worker_job_audit_events` 或等价审计写入。
- 固定 succeeded/failed/skipped/dry-run 语义，保留 correlation/job kind/duration。
- 复用既有 shared audit writer 和 ops query，不新增 schema。

非目标：

- 不修改 BFF transport、C1 catalog/normalization cache、storage R2 core behavior。
- 不改 notification runner 已完成路径。
- 不执行 live queue smoke、完整测试或真实外部依赖。

影响范围：

```text
services/ircs-metadata-worker/**
services/ircs-normalization-worker/**
services/ircs-storage-service/**
docs/reports/106-02812c-worker-scheduler-runner-audit-expansion.md
docs/migrations/092-worker-scheduler-runner-audit-expansion.md
tasks/backlog/028-16-f7-worker-scheduler-audit-golden-p1p2-backlog.md
docs/reports/093-v1-v3-parity-progress-ledger.md
```

验收标准：

```text
MARK 1/6 PASS worker/scheduler/runner inventory completed
MARK 2/6 PASS audit coverage decision recorded per runner
MARK 3/6 PASS metadata provider worker audit hooks implemented
MARK 4/6 PASS normalization queue/LLM runner audit hooks implemented
MARK 5/6 PASS storage media queue/watchdog audit hooks implemented
MARK 6/6 PASS focused lightweight tests compile/pass or unified-test deferred reason recorded
```

测试边界：

```text
不做 live queue smoke。
不做全量测试。
优先运行 metadata/normalization/storage focused tests；如本地环境或并行状态阻塞，则记录到 UTB-02816-P1P2-03。
```

验证结果：

```text
focused tests BUILD SUCCESSFUL
metadata-worker tests=7, failures=0, errors=0
normalization-worker tests=5, failures=0, errors=0
storage-service tests=10, failures=0, errors=0
```

## 本轮 notification worker live smoke task spec

状态：已完成

背景：

notification worker 第一刀已经证明 `MailConsumer` 本地成功/失败路径会写入
`worker_job_audit_events`，ops 查询 API 也已经 rollout 并能查询 live fixture。但
`ircs-notification-worker` dev 清单仍未注入 audit datasource，导致真实
`q.notification.mail` 队列消费还没有 worker/job audit live 证据。

目标：

- 为 `ircs-notification-worker` dev Deployment 注入 `APP_WORKER_AUDIT_*` datasource。
- `MailConsumer` 优先使用 Rabbit `message_id` 作为 `correlation_id`，使 live smoke
  可以精确定位和清理自身事件。
- 新增 `notification-worker-audit-02812-smoke.ps1`，投递 1 条可清理
  `MailMessageDTO` fixture，并验证 DB/ops 查询/队列清理。

非目标：

- 不启用真实 SMTP；dev 仍保持 `APP_MAIL_ENABLED=false`。
- 不修改 identity-service 业务发送逻辑。
- 不新增发送历史表。
- 不接入其他 worker、scheduler 或 maintenance runner。
- 不新增 HTTPRoute。

上游来源：

- `docs/reports/059-02812-worker-job-audit-notification-worker-first-slice.md`
- `docs/reports/061-02812-worker-job-audit-ops-query.md`
- `docs/reports/062-02812-notification-worker-live-audit-smoke.md`
- `docs/migrations/062-notification-worker-live-audit-smoke.md`

输入：

- `q.notification.mail`
- `MailMessageDTO`
- Rabbit `message_id`
- `worker_job_audit_events`
- `GET /api/v1/ops/worker-job-audit`

输出：

- `MailConsumer` correlation id 接入。
- notification worker Rabbit Java serialization allowed-list。
- notification-worker dev audit datasource env。
- live smoke 脚本、报告、迁移记录和任务回写。

影响范围：

```text
services/ircs-notification-worker/**
deploy/k8s/dev/notification-worker-dev.yaml
scripts/smoke/notification-worker-audit-02812-smoke.ps1
docs/reports/062-02812-notification-worker-live-audit-smoke.md
docs/migrations/062-notification-worker-live-audit-smoke.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

依赖：

- `worker_job_audit_events` live schema 已存在。
- `ops-service` worker/job audit 查询 API 已 rollout。
- `q.notification.mail` 和 DLQ 在 smoke 开始前为空。

风险：

- Rabbit fixture 必须使用和现有 producer 兼容的 Java serialization。
- smoke 不能清理非本轮消息；若队列 baseline 非 0 必须 SKIPPED。
- audit 写入失败不能改变 Rabbit retry/ack 语义。

验收标准：

```text
MARK 1/5 notification worker has q.notification.mail consumer
MARK 2/5 live smoke publishes MailMessageDTO fixture with Rabbit message_id
MARK 3/5 notification worker writes succeeded worker_job_audit_events with correlation_id
MARK 4/5 ops worker-job audit query exposes notification worker event
MARK 5/5 live smoke cleanup leaves no DB residue and mail queues empty
```

测试边界：

```text
.\gradlew.bat :services:ircs-notification-worker:test --rerun-tasks
.\scripts\smoke\notification-worker-audit-02812-smoke.ps1
```

实现结果：

- `MailConsumer` 使用 Rabbit `message_id` 作为 audit `correlation_id`。
- `NotificationWorkerRabbitConfiguration` 注册 notification contract allowed-list
  `SimpleMessageConverter`，避免全局 trust-all。
- `notification-worker-dev.yaml` 已注入 `APP_WORKER_AUDIT_*` datasource。
- smoke 脚本以 UTF-8 no BOM 生成临时 Java serializer，投递 Java-serialized
  `MailMessageDTO` fixture，并通过 DB 与 ops API 验证。

验证结果：

```text
.\gradlew.bat :services:ircs-notification-worker:test --rerun-tasks
BUILD SUCCESSFUL in 8s

MailConsumerTest tests=5, failures=0, errors=0, skipped=0
NotificationWorkerRabbitConfigurationTest tests=1, failures=0, errors=0, skipped=0
SmtpNotificationMailServiceTest tests=3, failures=0, errors=0, skipped=0

ircs-notification-worker imageID:
  harbor.mnnu.eu.org/ircs/ircs-notification-worker@sha256:742fe58a97b29e57eeae201da03ec63e331d4759284582d6d2351654bb7df6d4
```

MARK：

```text
MARK 1/5 notification worker has q.notification.mail consumer
MARK 2/5 live smoke publishes MailMessageDTO fixture with Rabbit message_id
MARK 3/5 notification worker writes succeeded worker_job_audit_events with correlation_id
MARK 4/5 ops worker-job audit query exposes notification worker event
MARK 5/5 live smoke cleanup leaves no DB residue and mail queues empty
```

Live smoke 证据：

```text
trace: 02812-notification-audit-20260608101907
status: PASSED
residue: 0
q.notification.mail messages=0, consumers=1, ready=0, unacked=0
q.notification.mail.dlq messages=0, consumers=0, ready=0, unacked=0
HTTPRoute: No resources found in ircs-dev namespace.
ResourceQuota: pods=28/30, services=24/26
```

## 本轮 ops 查询 API task spec

状态：已完成

背景：

`worker_job_audit_events` 已经具备 schema、shared writer 和 notification worker
第一条写入路径，但还没有 `ops-service` 查询 API。V1 到 V3 行为级迁移需要 admin/ops
能够持续查看异步任务执行证据，否则 worker/job audit 只能证明写端存在，不能支撑门户验证。

目标：

- 新增 `ops-service` worker/job audit 只读分页查询 API。
- 新增 24 小时摘要，统计 total、failed、succeeded 和最大耗时。
- 支持按 `jobSource`、`jobType`、`jobName`、`correlationId`、`status`、
  `errorClass`、`from`、`to` 过滤。
- 与 HTTP request audit 保持独立 URL 和独立 response 类型。

非目标：

- 不新增或修改 `worker_job_audit_events` 表结构。
- 不 rollout 到 `ircs-dev`，不新增 HTTPRoute。
- 不修改 notification worker 写入逻辑。
- 不做 worker/job audit 与 request audit 跨表 join。
- 不查询 credential、token、Cookie、Authorization、邮件正文或请求体。

上游来源：

- `docs/reports/059-02812-worker-job-audit-notification-worker-first-slice.md`
- `docs/migrations/059-worker-job-audit-events.md`
- `docs/reports/061-02812-worker-job-audit-ops-query.md`
- `docs/migrations/061-worker-job-audit-ops-query.md`

输入：

- `worker_job_audit_events`
- `JdbcPageSorts`
- `RequestAuditRepository` 分页查询风格

输出：

- `WorkerJobAuditController`
- `WorkerJobAuditQueryService`
- `WorkerJobAuditRepository`
- `WorkerJobAuditEventResponse`
- `WorkerJobAuditSummaryResponse`
- controller/repository tests

影响范围：

```text
services/ircs-ops-service/**
docs/reports/061-02812-worker-job-audit-ops-query.md
docs/migrations/061-worker-job-audit-ops-query.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

依赖：

- `worker_job_audit_events` changelog 已存在。
- `ops-service` 已具备 JDBC 和分页查询依赖。

风险：

- 排序字段必须白名单，避免把 pageable sort 直接拼进 SQL。
- 时间参数必须显式转换，避免 live PostgreSQL 类型推断问题。
- API 不能与 HTTP request audit URL 混淆。

验收标准：

```text
MARK 1/3 ops-service exposes worker/job audit page query
MARK 2/3 ops-service filters worker/job audit by source/status/correlation/time
MARK 3/3 ops-service exposes worker/job audit 24h summary
```

测试边界：

```text
.\gradlew.bat :services:ircs-ops-service:test --rerun-tasks
```

实现结果：

- `WorkerJobAuditController` 暴露 `GET /api/v1/ops/worker-job-audit` 和
  `GET /api/v1/ops/worker-job-audit/summary`。
- `WorkerJobAuditRepository` 查询 `worker_job_audit_events`，支持 source/type/name/
  correlation/status/error/time 过滤和白名单排序。
- `WorkerJobAuditRepositoryTest` 使用 H2 PostgreSQL mode 验证过滤和摘要 SQL。
- `WorkerJobAuditControllerTest` 验证 controller 参数透传和 summary response。
- `deploy/k8s/dev/migrator-job-dev.yaml` 将一次性 migrator Job CPU 调低到
  `request=25m, limit=250m`，用于在当前 `ircs-dev` ResourceQuota 下执行 live schema
  migration。
- `ircs-ops-service` 已推送 ARM64 `dev` 镜像并 rollout 到 `ircs-dev`。
- `ircs-migrator` 已推送 ARM64 `dev` 镜像并完成 live schema migration。
- 已通过可清理 live fixture 验证 `GET /api/v1/ops/worker-job-audit` 和
  `/summary`。

验证结果：

```text
.\gradlew.bat :services:ircs-ops-service:test --rerun-tasks
exit code: 0
WorkerJobAuditControllerTest tests=2, failures=0, errors=0
WorkerJobAuditRepositoryTest tests=2, failures=0, errors=0
RequestAuditControllerTest tests=2, failures=0, errors=0
```

MARK：

```text
MARK 1/3 ops-service exposes worker/job audit page query
MARK 2/3 ops-service filters worker/job audit by source/status/correlation/time
MARK 3/3 ops-service exposes worker/job audit 24h summary
```

Live MARK：

```text
MARK 1/4 worker_job_audit_events live table accepts fixture event
MARK 2/4 ops worker-job audit page query filters live fixture
MARK 3/4 ops worker-job audit summary returns live totals
MARK 4/4 live fixture cleanup leaves no worker_job_audit_events residue
```

Rollout 和 smoke 证据：

```text
ircs-ops-service imageID:
  harbor.mnnu.eu.org/ircs/ircs-ops-service@sha256:34a42fe11f08a78ba2ae1b042b6cbe2b32bf04e42e6b9711affc1cc57600d01a

ircs-migrator imageID:
  harbor.mnnu.eu.org/ircs/ircs-migrator@sha256:aaaf00ef4567fb47daeda23d85eaf92f0bc5bde702a8bdac43cf1b2a38ddace0

trace: 02812-ops-query-20260608094520
pageTotalElements: 1
summaryTotalLast24h: 1
residue: 0

HTTPRoute: No resources found in ircs-dev namespace.
ResourceQuota: pods=28/30, services=24/26
```

## 本轮 worker/job audit 第一刀 task spec

状态：已完成

背景：

`028-11` 和 `028-12` catalog 第一刀已经证明 HTTP handler audit 可以写入
`request_audit_logs`，但 queue consumer、scheduler 和 runner 仍没有独立事件口径。
worker/job 执行不是 HTTP request，本轮不能复用 `request_audit_logs`，需要一个轻量、
专用、默认低风险的 worker/job audit 写入切片。

目标：

- 新增专用 `worker_job_audit_events` schema 与 shared writer。
- 选择低风险 `ircs-notification-worker` 的 `MailConsumer` 写入首个 queue consumer 事件。
- 只记录 `job_source`、`job_type`、`job_name`、`correlation_id`、`status`、
  `duration_ms`、`error_class`、`error_message` 这类执行元数据。
- writer 在缺少 datasource 或关闭时 no-op，避免 worker 因 audit 失败影响业务或启动。
- 本地 H2 测试验证成功和失败事件真实落库。

非目标：

- 不新增 HTTPRoute，不 rollout 到 `ircs-dev`。
- 不修改 ops-service 查询面，不新增 worker/job audit HTTP 查询 API。
- 不修改 metadata worker、normalization worker、storage-service、search producer、
  storage、normalization、metadata provider 或 K8S 快照。
- 不记录 credential、token、request body、Cookie、Authorization、邮件正文、
  邮件收件人或 SMTP 配置。

上游来源：

- `docs/reports/055-per-service-audit-collection-parity-analysis.md`
- `docs/reports/058-02812-catalog-service-audit-first-slice.md`
- `docs/migrations/058-catalog-service-audit-handler.md`

输入：

- `shared/ircs-common/src/main/java/com/prodigalgal/ircs/common/audit/**`
- `services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/mail/MailConsumer.java`
- `services/ircs-notification-worker/src/test/java/com/prodigalgal/ircs/notification/mail/MailConsumerTest.java`
- Liquibase master changelog 和 `2026/07` SQL changelog 目录。

输出：

- `worker_job_audit_events` 新表 changelog。
- shared `WorkerJobAuditWriter` 与 `WorkerJobAuditEvent`。
- `ircs-notification-worker` audit configuration 和 `MailConsumer` 最小接入。
- 本地报告、迁移记录和任务回写。

影响范围：

```text
shared/ircs-common/**
services/ircs-notification-worker/**
platform/ircs-migrator/src/main/resources/db/changelog/**
docs/reports/*02812*worker*job*.md
docs/migrations/*worker*job*audit*.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

依赖：

- shared common 继续保持轻量，不引入运行时强耦合 DataSource bean。
- notification worker 当前未注入 `DB_URL`；本轮 writer 必须允许 datasource 缺省 no-op。
- 后续若要 live smoke，需要先由主线程或后续任务注入 datasource/env 并执行 schema migration。

风险：

- queue consumer 失败路径必须继续抛出异常给 RabbitMQ retry，audit 失败不能吞掉业务异常。
- 邮件消息可能包含地址、正文或模板变量，本轮 correlation 不从消息内容派生，避免泄露。
- 新表写入量后续可能增长，本轮只接一个低频 disabled-mail worker 并保留开关。

验收标准：

```text
MARK 1/3 worker_job_audit_events schema is dedicated and non-HTTP
MARK 2/3 notification worker mail consumer writes succeeded event
MARK 3/3 notification worker mail consumer writes failed event and preserves retry exception
```

测试边界：

```text
.\gradlew.bat :shared:ircs-common:test :services:ircs-notification-worker:test --rerun-tasks
```

本轮不执行 live smoke，不触碰 HTTPRoute、ResourceQuota、K8S 快照或索引文件。

## 实现结果

- `services/ircs-catalog-service/src/main/java/com/prodigalgal/ircs/catalog/CatalogServiceAuditConfiguration.java`
  注册 `ServiceRequestAuditFilter`。
- `services/ircs-catalog-service/src/test/java/com/prodigalgal/ircs/catalog/CatalogServiceAuditFilterTest.java`
  验证 catalog-service handler 请求写入 `request_source`，并验证 actuator 噪音排除。
- `services/ircs-catalog-service/build.gradle` 增加 H2 test dependency，仅用于本地 audit 表测试。
- `platform/ircs-migrator/src/main/resources/db/changelog/2026/07/add-worker-job-audit-events.sql`
  新增 `worker_job_audit_events` 专用表和索引。
- `platform/ircs-migrator/src/main/resources/db/changelog/db.changelog-master.yaml`
  include worker/job audit 新 changelog，未修改历史 migration。
- `shared/ircs-common/src/main/java/com/prodigalgal/ircs/common/audit/WorkerJobAuditEvent.java`
  定义 worker/job audit 事件输入。
- `shared/ircs-common/src/main/java/com/prodigalgal/ircs/common/audit/WorkerJobAuditWriter.java`
  通过 JDBC URL 写入 worker/job audit，datasource 缺省或关闭时 no-op，写入失败只 warning。
- `services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/NotificationWorkerAuditConfiguration.java`
  注册 notification worker audit writer。
- `services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/mail/MailConsumer.java`
  在成功/失败路径写入 `notification.mail` queue consumer 事件，失败后保留 RabbitMQ retry 异常。
- `services/ircs-notification-worker/build.gradle` 增加 PostgreSQL runtime dependency 和 H2 test dependency。

## 验证结果

```text
.\gradlew.bat :services:ircs-catalog-service:test --rerun-tasks
BUILD SUCCESSFUL in 11s
```

worker/job audit 本地测试：

```text
.\gradlew.bat :shared:ircs-common:test :services:ircs-notification-worker:test --rerun-tasks
BUILD SUCCESSFUL in 12s
```

migrator 资源处理：

```text
.\gradlew.bat :platform:ircs-migrator:processResources --rerun-tasks
BUILD SUCCESSFUL in 1s
```

测试证据：

```text
CatalogServiceAuditFilterTest tests=2, failures=0, errors=0
WorkerJobAuditWriterTest tests=3, failures=0, errors=0
MailConsumerTest tests=4, failures=0, errors=0
SmtpNotificationMailServiceTest tests=3, failures=0, errors=0
BffRequestAuditWriterTest tests=2, failures=0, errors=0
```

MARK：

```text
MARK 1/2 catalog-service handler request writes request_source
MARK 2/2 catalog-service audit filter skips actuator noise
MARK 1/3 worker_job_audit_events schema is dedicated and non-HTTP
MARK 2/3 notification worker mail consumer writes succeeded event
MARK 3/3 notification worker mail consumer writes failed event and preserves retry exception
```

## notification worker 第一刀历史未执行项与当前补齐状态

- worker/job audit 第一刀当时未 rollout 到 `ircs-dev`，也未执行 ClusterIP smoke、DB residue
  或 ResourceQuota 检查。
- 当前 `062` live smoke 切片已补齐 notification worker dev rollout、audit datasource 注入、
  queue fixture smoke、DB residue、HTTPRoute 和 ResourceQuota 后置检查。
- 当前仍未新增 HTTPRoute。
- 第一刀未修改 metadata worker、normalization worker、storage-service、search producer、K8S
  快照或索引文件；当前 `062` 仅补充 K8S 快照与索引回写。

说明：`ops-service` worker/job audit 只读查询 API、dev rollout 和 live query smoke 已在
`docs/reports/061-02812-worker-job-audit-ops-query.md` 完成。

## 剩余子任务

- 继续选择下一个 HTTP service 铺开 handler audit；credential-service 敏感字段边界已完成第一刀。

## 本轮 task-service handler audit task spec

状态：已完成

背景：

`028-12` 已完成 portal-service handler audit live smoke，剩余 HTTP service handler audit
需要继续铺开到尚未接入的后端服务。`ircs-task-service` 承载采集任务列表、详情、生命周期命令、
cron scheduler 和 scraper-service 执行桥接；如果缺少 service-level request audit，admin 门户
后续验证任务管理和 runner 行为时只能看到 BFF 入口日志，不能定位 task-service handler 边界。

目标：

- 为 `ircs-task-service` 注册 `ServiceRequestAuditFilter`。
- 用本地 H2 测试证明 collection task handler audit 会写入
  `request_source=ircs-task-service`。
- 证明 query token/password 被脱敏，Authorization、Cookie、request body 和 response content
  不进入 audit 表。
- 用只读 `GET /api/v1/collection-tasks` 完成 `ircs-dev` live smoke。

非目标：

- 不修改 task-service create/update/delete/start/resume/pause/stop 业务逻辑。
- 不触发 task lifecycle、cron scheduler、runner、scraper-service 调用或 task log 写入。
- 不新增或修改 HTTPRoute、Service、Pod、PVC、中间件和资源配额。
- 不接入新的 worker/job audit 事件。

上游来源：

- `docs/reports/055-per-service-audit-collection-parity-analysis.md`
- `docs/reports/073-02812-portal-service-audit-handler.md`
- `docs/reports/074-02812-task-service-audit-handler.md`
- `docs/migrations/074-task-service-audit-handler.md`

输入：

- `GET /api/v1/collection-tasks`
- `request_audit_logs`
- `X-Trace-Id`
- `X-Authenticated-User`
- query string 中用于脱敏验证的 `token`

输出：

- `TaskServiceAuditConfiguration`
- `TaskServiceAuditFilterTest`
- `scripts/smoke/task-service-audit-02812-smoke.ps1`
- 本任务、报告、迁移记录和索引回写。

影响范围：

```text
services/ircs-task-service/**
scripts/smoke/task-service-audit-02812-smoke.ps1
docs/reports/074-02812-task-service-audit-handler.md
docs/migrations/074-task-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
docs/requirements/028-v1-v3-behavior-parity-closeout.md
docs/index.md
tasks/index.md
docs/k8s/current-environment-2026-06-02.md
```

依赖：

- shared `ServiceRequestAuditFilter` 与 `RequestAuditSanitizer` 可复用。
- task-service 使用现有 dev PostgreSQL `request_audit_logs` schema。
- `ircs-dev` 中 `ircs-task-service`、PostgreSQL 和 kubectl 访问可用。

风险：

- task lifecycle endpoint 会改变任务状态，live smoke 必须只读。
- audit 写入失败不得影响 task handler 返回。
- 敏感 query、Authorization、Cookie、request body 和 response content 不能进入 audit 表。

验收标准：

```text
MARK 1/4 task-service handler request writes request_source
MARK 2/4 task-service audit redacts query token
MARK 3/4 task-service audit excludes Authorization Cookie body and response content
MARK 4/4 task-service audit filter skips actuator noise
```

live smoke 验收：

```text
MARK 1/4 task-service pod is Ready
MARK 2/4 task-service collection task list request returns success
MARK 3/4 task-service live audit row redacts query token
MARK 4/4 task-service live smoke cleanup leaves no DB residue
```

测试边界：

```text
.\gradlew.bat :services:ircs-task-service:test --rerun-tasks
.\scripts\smoke\task-service-audit-02812-smoke.ps1
```

关联文档：

- `docs/reports/074-02812-task-service-audit-handler.md`
- `docs/migrations/074-task-service-audit-handler.md`

关联任务：

- 父任务：`tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- 本任务：`tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

实现结果：

- `TaskServiceAuditConfiguration` 注册 task-service handler audit。
- `TaskServiceAuditFilterTest` 覆盖 collection task list handler audit 写入、query
  token/password 脱敏、Authorization / Cookie / response content 排除和 actuator 噪音排除。
- `task-service-audit-02812-smoke.ps1` 通过只读 collection task list endpoint 完成 live audit
  与 query token 脱敏验证。

验证结果：

```text
.\gradlew.bat :services:ircs-task-service:test --rerun-tasks
BUILD SUCCESSFUL in 8s

TaskServiceAuditFilterTest tests=2, failures=0, errors=0, skipped=0
task-service total tests=14, failures=0, errors=0, skipped=0
```

Rollout 证据：

```text
deployment/ircs-task-service successfully rolled out
pod: ircs-task-service-84f784487c-rmn2q Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-task-service@sha256:e5d8f0d50a921e6d0f5ade027c089b877669d177977596a815aac5f4701eefb0
```

MARK：

```text
MARK 1/4 task-service handler request writes request_source
MARK 2/4 task-service audit redacts query token
MARK 3/4 task-service audit excludes Authorization Cookie body and response content
MARK 4/4 task-service audit filter skips actuator noise
MARK 1/4 task-service pod is Ready
MARK 2/4 task-service collection task list request returns success
MARK 3/4 task-service live audit row redacts query token
MARK 4/4 task-service live smoke cleanup leaves no DB residue
```

Live smoke 证据：

```text
trace: 02812-task-audit-20260608160734
request_audit_logs task-service 02812 residue: 0
local task-service port-forward residue: none
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
requests.cpu=880m/4, requests.memory=4480Mi/8Gi
limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
```

## 2026-06-11 notification worker audit live closure

已完成 notification worker live audit 收敛：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\notification-worker-audit-with-listener-02812-smoke.ps1 -FailOnSkip
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-03 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\utb-02816-p1p2-03-after-db-migration-20260611.json
```

MARK：

```text
MARK 1/5 notification worker has q.notification.mail consumer
MARK 2/5 live smoke publishes MailMessageDTO fixture with Rabbit message_id
MARK 3/5 notification worker writes succeeded worker_job_audit_events with correlation_id
MARK 4/5 ops worker-job audit query exposes notification worker event
MARK 5/5 live smoke cleanup leaves no DB residue and mail queues empty
```

结果：

```text
status=PASSED
trace=02812-notification-audit-20260611233955
residue=0
q.notification.mail=0
q.notification.mail.dlq=0
APP_NOTIFICATION_LISTENER_ENABLED restored/absent
```

前置 DB 修复：

- 正式 migrator Job 已执行 10 个 pending changeset，补齐 `audit_class`、audit outbox/archive 与近期 runtime schema。
- ops worker-job-audit endpoint 已恢复 HTTP 200。

本任务剩余：

- metadata/normalization/storage worker live queue smoke 与 ops query cleanup 仍待统一测试或独立切片确认。
- notification worker 已完成 audit datasource 注入和最小 queue fixture live smoke。
- 其他 worker、scheduler 和 maintenance runner 仍需按风险分批接入。
- 后续 live smoke 只能走 ClusterIP 或最小 queue fixture，并补 DB residue、HTTPRoute 和 ResourceQuota 检查。

## 本轮 content-service handler audit task spec

状态：已完成

背景：

`028-12` 已完成 `config-service`、`catalog-service` 的 HTTP handler audit，以及
notification worker/job audit 的写入、ops 查询和 live queue smoke。`ircs-content-service`
承载 raw/unified video、people、playlist、resolver 和 source-domain 等 portal/admin
持续验证路径；当前 HTTP 路由已经存在，但服务 handler 层还没有独立 request audit 证据。

目标：

- 为 `ircs-content-service` 接入 `ServiceRequestAuditFilter`。
- 让 content handler 请求写入 `request_audit_logs.request_source=ircs-content-service`。
- 保持不采集请求体、Authorization、Cookie、credential 或 token。
- 本地测试验证正常请求写入和 actuator 噪音排除。

非目标：

- 不修改 content-service 业务逻辑、响应语义或数据副作用。
- 不新增 controller、BFF route、Service、Pod 或 HTTPRoute。
- 不接入 credential-service 敏感 handler audit。
- 不接入新的 worker、scheduler 或 maintenance runner audit。
- 不构造 content 数据写入 fixture，不触发 RabbitMQ、search sync 或 provider 链路。
- 不新增 Service、Pod、PVC、中间件或 HTTPRoute。

上游来源：

- `docs/reports/055-per-service-audit-collection-parity-analysis.md`
- `docs/reports/058-02812-catalog-service-audit-first-slice.md`
- `docs/reports/066-02812-content-service-audit-handler.md`
- `docs/migrations/066-content-service-audit-handler.md`

输入：

- `ServiceRequestAuditFilter`
- `request_audit_logs.request_source`
- `ircs-content-service` controller handler 请求

输出：

- content-service audit configuration。
- content-service audit filter 本地测试。
- content-service live audit smoke 脚本。
- 本报告、迁移记录和任务回写。

影响范围：

```text
services/ircs-content-service/**
scripts/smoke/content-service-audit-02812-smoke.ps1
docs/reports/066-02812-content-service-audit-handler.md
docs/migrations/066-content-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

依赖：

- `request_audit_logs.request_source` schema 已存在。
- content-service 已使用 JDBC datasource。
- 本地测试可使用 H2 PostgreSQL mode。

风险：

- content-service admin 接口较多，审计必须保持只记录元数据，不能记录 request body。
- 若后续执行 live smoke，只能打最小只读 endpoint，并清理本轮 audit fixture。
- audit 写入失败不得影响业务 handler 返回。

验收标准：

```text
MARK 1/2 content-service handler request writes request_source
MARK 2/2 content-service audit filter skips actuator noise
```

测试边界：

```text
.\gradlew.bat :services:ircs-content-service:test --rerun-tasks
.\scripts\smoke\content-service-audit-02812-smoke.ps1
```

实现结果：

- `ContentServiceAuditConfiguration` 注册 `ServiceRequestAuditFilter`。
- `ContentServiceAuditFilterTest` 使用 MockMvc + H2 PostgreSQL mode 覆盖 handler audit
  写入和 actuator 噪音排除。
- `content-service-audit-02812-smoke.ps1` 通过唯一 `X-Trace-Id` 调用 live
  `/api/v1/actors/all` 只读端点，并清理本轮 audit fixture。
- `ircs-content-service` 已推送 ARM64 `dev` 镜像并 rollout 到 `ircs-dev`。

验证结果：

```text
.\gradlew.bat :services:ircs-content-service:test --rerun-tasks
BUILD SUCCESSFUL in 15s

ContentServiceAuditFilterTest tests=2, failures=0, errors=0
content-service total tests=29, failures=0, errors=0, skipped=0
```

Rollout 证据：

```text
deployment/ircs-content-service successfully rolled out
pod: ircs-content-service-58d98958c8-pbr6v Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-content-service@sha256:5557e950adbce042a9d51a15fe551ae9cefe86227be50b9e52ef976a68ade42a
```

MARK：

```text
MARK 1/2 content-service handler request writes request_source
MARK 2/2 content-service audit filter skips actuator noise
MARK 1/4 content-service pod is Ready
MARK 2/4 content-service live handler request returns success
MARK 3/4 content-service live audit row is queryable by trace_id
MARK 4/4 content-service live smoke cleanup leaves no DB residue
```

Live smoke 证据：

```text
trace: 02812-content-audit-20260608113559
request_audit_logs content-service 02812 residue: 0
local content-service port-forward residue: none
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
requests.cpu=880m/4, requests.memory=4480Mi/8Gi
limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
```

## 本轮 credential-service handler audit task spec

状态：已完成

背景：

`028-12` 已完成 config-service、catalog-service、content-service handler audit，并补齐
notification worker/job audit 的写入、ops 查询和 live smoke。`ircs-credential-service`
承载 admin credential 管理和 internal credential lease，属于 V3 内部 provider、SMTP、R2
等真实运行逻辑的关键支撑服务。它的敏感边界比普通服务更高：secret 在 request body、
internal token 在 header、lease response 可包含 secret payload，因此本轮必须把敏感字段
不进入 request audit 作为核心验收。

目标：

- 为 `ircs-credential-service` 接入 `ServiceRequestAuditFilter`。
- 让 credential handler 请求写入 `request_audit_logs.request_source=ircs-credential-service`。
- 本地测试证明 request body、Authorization、Cookie、internal token 和 secret payload 不进入 audit 表。
- 使用安全只读模板 endpoint 完成 live smoke，并清理本轮 audit fixture。

非目标：

- 不修改 credential-service 业务逻辑、credential sanitizer、lease response 或 secret storage。
- 不在 audit 中记录 request body、response body、Authorization、Cookie、internal token 或 secret payload。
- 不新增 controller、BFF route、Service、Pod、PVC、中间件或 HTTPRoute。
- 不构造或写入真实 credential fixture。
- 不调用 internal lease endpoint 做 live smoke。

上游来源：

- `docs/reports/055-per-service-audit-collection-parity-analysis.md`
- `docs/reports/066-02812-content-service-audit-handler.md`
- `docs/reports/067-02812-credential-service-audit-handler.md`
- `docs/migrations/067-credential-service-audit-handler.md`

输入：

- `ServiceRequestAuditFilter`
- `request_audit_logs.request_source`
- `CredentialController`
- `InternalCredentialController`

输出：

- credential-service audit configuration。
- credential-service audit sensitive-boundary 本地测试。
- credential-service live audit smoke 脚本。
- 本报告、迁移记录和任务回写。

影响范围：

```text
services/ircs-credential-service/**
scripts/smoke/credential-service-audit-02812-smoke.ps1
docs/reports/067-02812-credential-service-audit-handler.md
docs/migrations/067-credential-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

依赖：

- `request_audit_logs.request_source` schema 已存在。
- credential-service 已使用 JDBC datasource。
- 本地测试可使用 H2 PostgreSQL mode。
- live smoke 只依赖 credential-service safe template endpoint。

风险：

- credential write body 和 internal token 不能进入 audit 表。
- live smoke 不能访问 internal lease endpoint，避免读取真实 secret payload。
- audit 写入失败不得影响 credential handler 返回。

验收标准：

```text
MARK 1/3 credential-service handler request writes request_source
MARK 2/3 credential-service audit excludes body Authorization Cookie and internal token
MARK 3/3 credential-service audit filter skips actuator noise
```

live smoke 验收：

```text
MARK 1/4 credential-service pod is Ready
MARK 2/4 credential-service safe template request returns success
MARK 3/4 credential-service live audit row is queryable by trace_id
MARK 4/4 credential-service live smoke cleanup leaves no DB residue
```

测试边界：

```text
.\gradlew.bat :services:ircs-credential-service:test --rerun-tasks
.\scripts\smoke\credential-service-audit-02812-smoke.ps1
```

实现结果：

- `CredentialServiceAuditConfiguration` 注册 `ServiceRequestAuditFilter`。
- `CredentialServiceAuditFilterTest` 使用 MockMvc + H2 PostgreSQL mode 覆盖：
  - public credential write 请求写入 audit。
  - request body secret、Authorization、Cookie 和 internal token 不进入 audit 表。
  - internal lease unauthorized 请求写入 audit，但不记录 internal token。
  - actuator 噪音排除。
- `credential-service-audit-02812-smoke.ps1` 通过唯一 `X-Trace-Id` 调用 live
  `/api/v1/credentials/templates?provider=TMDB` 安全只读端点，并清理本轮 audit fixture。
- `ircs-credential-service` 已推送 ARM64 `dev` 镜像并 rollout 到 `ircs-dev`。

验证结果：

```text
.\gradlew.bat :services:ircs-credential-service:test --rerun-tasks
BUILD SUCCESSFUL in 13s

CredentialServiceAuditFilterTest tests=3, failures=0, errors=0
credential-service total tests=29, failures=0, errors=0, skipped=0
```

Rollout 证据：

```text
deployment/ircs-credential-service successfully rolled out
pod: ircs-credential-service-84449db48c-s57wq Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-credential-service@sha256:634f635076842a4a0b4518ef1ea5ca0beaee8980e80544f15c5eebb3ff750e96
```

MARK：

```text
MARK 1/3 credential-service handler request writes request_source
MARK 2/3 credential-service audit excludes body Authorization Cookie and internal token
MARK 3/3 credential-service audit filter skips actuator noise
MARK 1/4 credential-service pod is Ready
MARK 2/4 credential-service safe template request returns success
MARK 3/4 credential-service live audit row is queryable by trace_id
MARK 4/4 credential-service live smoke cleanup leaves no DB residue
```

Live smoke 证据：

```text
trace: 02812-credential-audit-20260608115419
request_audit_logs credential-service 02812 residue: 0
local credential-service port-forward residue: none
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
requests.cpu=880m/4, requests.memory=4480Mi/8Gi
limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
```

## 本轮 identity-service handler audit task spec

状态：已完成

背景：

`028-12` 已完成 config-service、catalog-service、content-service、credential-service
handler audit，并补齐 notification worker/job audit 的写入、ops 查询和 live smoke。
`ircs-identity-service` 承载 admin auth、portal member auth、profile、avatar 和 member
admin，是 portal/admin 持续验证入口。该服务的敏感边界包括 password request body、
Authorization/Cookie header、reset token 和 `JwtAuthenticationFilter` 支持的 query
parameter `token`。

目标：

- 为 `ircs-identity-service` 接入 `ServiceRequestAuditFilter`。
- 在 shared `ServiceRequestAuditFilter` 中脱敏敏感 query 参数，避免 `token` 等进入
  `request_audit_logs.query_string`。
- 本地测试证明 request body、Authorization、Cookie 和 query token 不进入 audit 表。
- 使用安全 public PoW endpoint 完成 live smoke，并清理本轮 audit fixture。

非目标：

- 不修改 identity-service auth/profile/member 业务逻辑。
- 不在 audit 中记录 request body、response body、Authorization、Cookie、JWT、password、
  reset token 或 avatar 文件内容。
- 不新增 controller、BFF route、Service、Pod、PVC、中间件或 HTTPRoute。
- 不构造真实登录、注册、密码重置或头像上传 fixture。

上游来源：

- `docs/reports/055-per-service-audit-collection-parity-analysis.md`
- `docs/reports/067-02812-credential-service-audit-handler.md`
- `docs/reports/068-02812-identity-service-audit-handler.md`
- `docs/migrations/068-identity-service-audit-handler.md`

输入：

- `ServiceRequestAuditFilter`
- `request_audit_logs.request_source`
- `AdminAuthController`
- `MemberAuthController`
- `MemberProfileController`
- `MemberAdminController`

输出：

- shared query sensitive redaction。
- identity-service audit configuration。
- identity-service audit sensitive-boundary 本地测试。
- identity-service live audit smoke 脚本。
- 本报告、迁移记录和任务回写。

影响范围：

```text
shared/ircs-common/**
services/ircs-identity-service/**
scripts/smoke/identity-service-audit-02812-smoke.ps1
docs/reports/068-02812-identity-service-audit-handler.md
docs/migrations/068-identity-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

依赖：

- `request_audit_logs.request_source` schema 已存在。
- identity-service 已使用 JDBC datasource。
- 本地测试可使用 H2 PostgreSQL mode。
- live smoke 只依赖 identity-service public PoW endpoint。

风险：

- query string token 必须脱敏，否则 service audit 会泄露 JWT/reset token。
- live smoke 不能执行登录、注册、改密或头像上传。
- audit 写入失败不得影响 identity handler 返回。

验收标准：

```text
MARK 1/4 shared audit redacts sensitive query parameters
MARK 2/4 identity-service handler request writes request_source
MARK 3/4 identity-service audit excludes body Authorization Cookie and query token
MARK 4/4 identity-service audit filter skips actuator noise
```

live smoke 验收：

```text
MARK 1/4 identity-service pod is Ready
MARK 2/4 identity-service PoW request returns success
MARK 3/4 identity-service live audit row redacts query token
MARK 4/4 identity-service live smoke cleanup leaves no DB residue
```

测试边界：

```text
.\gradlew.bat :shared:ircs-common:test :services:ircs-identity-service:test --rerun-tasks
.\scripts\smoke\identity-service-audit-02812-smoke.ps1
```

实现结果：

- `RequestAuditSanitizer` 统一脱敏 query string 中的 token/password/secret/api_key/
  authorization/cookie 类敏感参数。
- `ServiceRequestAuditFilter` 与 `BffRequestAuditWriter` 写入脱敏后的 `query_string`。
- `IdentityServiceAuditConfiguration` 注册 identity-service handler audit。
- `IdentityServiceAuditFilterTest` 覆盖 login handler audit、敏感 body/header/cookie/query/
  response token 排除和 actuator 噪音排除。
- `identity-service-audit-02812-smoke.ps1` 通过 public PoW endpoint 完成 live audit 与
  query token 类字段脱敏验证。

验证结果：

```text
.\gradlew.bat :shared:ircs-common:test :services:ircs-identity-service:test --rerun-tasks
BUILD SUCCESSFUL in 12s

BffRequestAuditWriterTest tests=3, failures=0, errors=0
RequestAuditSanitizerTest tests=2, failures=0, errors=0
WorkerJobAuditWriterTest tests=3, failures=0, errors=0
IdentityServiceAuditFilterTest tests=2, failures=0, errors=0
identity-service total tests=25, failures=0, errors=0, skipped=0
```

Rollout 证据：

```text
deployment/ircs-identity-service successfully rolled out
pod: ircs-identity-service-7f8c65d969-f9fbt Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-identity-service@sha256:09d4574336c51d54c7716e8d6697fad8a063e0f770c0caa74f1afa49ef9481c7
```

MARK：

```text
MARK 1/4 shared audit redacts sensitive query parameters
MARK 2/4 identity-service handler request writes request_source
MARK 3/4 identity-service audit excludes body Authorization Cookie and query token
MARK 4/4 identity-service audit filter skips actuator noise
MARK 1/4 identity-service pod is Ready
MARK 2/4 identity-service PoW request returns success
MARK 3/4 identity-service live audit row redacts query token
MARK 4/4 identity-service live smoke cleanup leaves no DB residue
```

Live smoke 证据：

```text
trace: 02812-identity-audit-20260608144010
request_audit_logs identity-service 02812 residue: 0
local identity-service port-forward residue: none
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
requests.cpu=880m/4, requests.memory=4480Mi/8Gi
limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
```

说明：live smoke 最初使用精确 `token=query-secret-token` 时触发
`JwtAuthenticationFilter` query JWT 解析并返回 401，且请求未进入 request audit。最终
live smoke 使用 `access_token=query-secret-token` 验证 token 类 query key 脱敏；精确
`token` 参数脱敏由本地单测覆盖。

## 本轮 interaction-service handler audit task spec

状态：已完成

背景：

`028-12` 已完成 config-service、catalog-service、content-service、credential-service、
identity-service handler audit，并补齐 notification worker/job audit 的写入、ops 查询和
live smoke。`ircs-interaction-service` 承载 portal 收藏、历史、反馈墙、我的留言，以及
admin 留言管理，是 portal/admin 持续验证 V1 交互功能无损迁移的核心服务之一。该服务的
portal endpoint 支持 query parameter `token` 作为 member JWT，因此本轮需要同时验证 handler
audit 写入和 query token 脱敏。

目标：

- 为 `ircs-interaction-service` 接入 `ServiceRequestAuditFilter`。
- 让 interaction handler 请求写入 `request_audit_logs.request_source=ircs-interaction-service`。
- 本地测试证明 request body、Authorization、Cookie 和 query token 不进入 audit 表。
- 使用 public feedback wall endpoint 完成 live smoke，并清理本轮 audit fixture。

非目标：

- 不修改 interaction-service 收藏、历史、反馈、留言管理或 watch-progress 业务逻辑。
- 不在 audit 中记录 request body、response body、Authorization、Cookie、JWT 或反馈正文。
- 不新增 controller、BFF route、Service、Pod、PVC、中间件或 HTTPRoute。
- 不构造真实 member 登录、收藏、历史、留言或 admin reply fixture。
- 不接入新的 queue consumer 或 worker/job audit 事件。

上游来源：

- `docs/reports/055-per-service-audit-collection-parity-analysis.md`
- `docs/reports/068-02812-identity-service-audit-handler.md`
- `docs/reports/069-02812-interaction-service-audit-handler.md`
- `docs/migrations/069-interaction-service-audit-handler.md`

输入：

- `ServiceRequestAuditFilter`
- `request_audit_logs.request_source`
- `FeedbackController`
- `PortalInteractionController`
- `AdminMessageController`

输出：

- interaction-service audit configuration。
- interaction-service audit sensitive-boundary 本地测试。
- interaction-service live audit smoke 脚本。
- 本报告、迁移记录和任务回写。

影响范围：

```text
services/ircs-interaction-service/**
scripts/smoke/interaction-service-audit-02812-smoke.ps1
docs/reports/069-02812-interaction-service-audit-handler.md
docs/migrations/069-interaction-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

依赖：

- `request_audit_logs.request_source` schema 已存在。
- interaction-service 已使用 JDBC datasource。
- shared query parameter 脱敏已在 `068` 完成。
- 本地测试可使用 H2 PostgreSQL mode。
- live smoke 只依赖 interaction-service public feedback wall endpoint。

风险：

- portal query token 必须脱敏，否则 service audit 会泄露 member JWT。
- live smoke 不能执行收藏、历史、留言写入或 admin reply。
- audit 写入失败不得影响 interaction handler 返回。

验收标准：

```text
MARK 1/4 interaction-service handler request writes request_source
MARK 2/4 interaction-service audit redacts query token
MARK 3/4 interaction-service audit excludes body Authorization and Cookie
MARK 4/4 interaction-service audit filter skips actuator noise
```

live smoke 验收：

```text
MARK 1/4 interaction-service pod is Ready
MARK 2/4 interaction-service feedback wall request returns success
MARK 3/4 interaction-service live audit row redacts query token
MARK 4/4 interaction-service live smoke cleanup leaves no DB residue
```

测试边界：

```text
.\gradlew.bat :services:ircs-interaction-service:test --rerun-tasks
.\scripts\smoke\interaction-service-audit-02812-smoke.ps1
```

实现结果：

- `services/ircs-interaction-service/build.gradle` 增加 `shared:ircs-common` 和 H2 test dependency。
- `InteractionServiceAuditConfiguration` 注册 interaction-service handler audit。
- `InteractionServiceAuditFilterTest` 覆盖 feedback handler audit、query token/password 脱敏、
  request body / Authorization / Cookie / response content 排除和 actuator 噪音排除。
- `interaction-service-audit-02812-smoke.ps1` 通过 public feedback wall endpoint 完成
  live audit 与 query token 脱敏验证。

验证结果：

```text
.\gradlew.bat :services:ircs-interaction-service:test --rerun-tasks
BUILD SUCCESSFUL in 15s

InteractionServiceAuditFilterTest tests=2, failures=0, errors=0
interaction-service total tests=51, failures=0, errors=0, skipped=0
```

Rollout 证据：

```text
deployment/ircs-interaction-service successfully rolled out
pod: ircs-interaction-service-5ff44bdf48-g2ggz Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-interaction-service@sha256:2cd5b1086233d618ac0354be4a1c0f982a5c75793670acfcd14ca451c0492cb0
```

MARK：

```text
MARK 1/4 interaction-service handler request writes request_source
MARK 2/4 interaction-service audit redacts query token
MARK 3/4 interaction-service audit excludes body Authorization and Cookie
MARK 4/4 interaction-service audit filter skips actuator noise
MARK 1/4 interaction-service pod is Ready
MARK 2/4 interaction-service feedback wall request returns success
MARK 3/4 interaction-service live audit row redacts query token
MARK 4/4 interaction-service live smoke cleanup leaves no DB residue
```

Live smoke 证据：

```text
trace: 02812-interaction-audit-20260608145509
request_audit_logs interaction-service 02812 residue: 0
local interaction-service port-forward residue: none
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
requests.cpu=880m/4, requests.memory=4480Mi/8Gi
limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
```

## 本轮 magnet-service handler audit task spec

状态：已完成

背景：

`028-12` 已完成 config-service、catalog-service、content-service、credential-service、
identity-service、interaction-service handler audit，并补齐 notification worker/job audit 的
写入、ops 查询和 live smoke。`ircs-magnet-service` 承载 magnet provider 配置、统一影片磁力
链接查询和 provider search 触发；`028-03` 已完成 provider search dev-safe 行为第一刀，但
service handler 层仍缺少可查询的 request audit 证据。

目标：

- 为 `ircs-magnet-service` 接入 `ServiceRequestAuditFilter`。
- 让 magnet handler 请求写入 `request_audit_logs.request_source=ircs-magnet-service`。
- 本地测试证明 request body、Authorization、Cookie 和 query token 不进入 audit 表。
- 使用只读 provider list endpoint 完成 live smoke，并清理本轮 audit fixture。

非目标：

- 不修改 magnet-service provider/search/cache 业务逻辑。
- 不在 audit 中记录 request body、response body、Authorization、Cookie、JWT、provider payload
  或 magnet URI。
- 不新增 controller、BFF route、Service、Pod、PVC、中间件或 HTTPRoute。
- 不构造真实 provider write、provider search、magnet link 或 external provider fixture。
- 不接入新的 queue consumer、scheduler 或 worker/job audit 事件。

上游来源：

- `docs/reports/055-per-service-audit-collection-parity-analysis.md`
- `docs/reports/070-02812-magnet-service-audit-handler.md`
- `docs/migrations/070-magnet-service-audit-handler.md`
- `tasks/done/028-03-magnet-provider-search-parity.md`

输入：

- `ServiceRequestAuditFilter`
- `request_audit_logs.request_source`
- `MagnetController`
- `MagnetQueryService`

输出：

- magnet-service audit configuration。
- magnet-service audit sensitive-boundary 本地测试。
- magnet-service live audit smoke 脚本。
- 本报告、迁移记录和任务回写。

影响范围：

```text
services/ircs-magnet-service/**
scripts/smoke/magnet-service-audit-02812-smoke.ps1
docs/reports/070-02812-magnet-service-audit-handler.md
docs/migrations/070-magnet-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

依赖：

- `request_audit_logs.request_source` schema 已存在。
- magnet-service 已使用 JDBC datasource。
- shared query parameter 脱敏已在 `068` 完成。
- 本地测试可使用 H2 PostgreSQL mode。
- live smoke 只依赖 magnet-service provider list endpoint。

风险：

- provider write body 和 magnet URI 不能进入 audit 表。
- live smoke 不能执行 provider search 或写入真实 provider/magnet fixture。
- audit 写入失败不得影响 magnet handler 返回。

验收标准：

```text
MARK 1/4 magnet-service handler request writes request_source
MARK 2/4 magnet-service audit redacts query token
MARK 3/4 magnet-service audit excludes body Authorization and Cookie
MARK 4/4 magnet-service audit filter skips actuator noise
```

live smoke 验收：

```text
MARK 1/4 magnet-service pod is Ready
MARK 2/4 magnet-service provider list request returns success
MARK 3/4 magnet-service live audit row redacts query token
MARK 4/4 magnet-service live smoke cleanup leaves no DB residue
```

测试边界：

```text
.\gradlew.bat :services:ircs-magnet-service:test --rerun-tasks
.\scripts\smoke\magnet-service-audit-02812-smoke.ps1
```

实现结果：

- `services/ircs-magnet-service/build.gradle` 增加 `shared:ircs-common` dependency。
- `MagnetServiceAuditConfiguration` 注册 magnet-service handler audit。
- `MagnetServiceAuditFilterTest` 覆盖 provider write handler audit、query token/password 脱敏、
  request body / Authorization / Cookie / response content 排除和 actuator 噪音排除。
- `magnet-service-audit-02812-smoke.ps1` 通过只读 provider list endpoint 完成 live audit
  与 query token 脱敏验证。

验证结果：

```text
.\gradlew.bat :services:ircs-magnet-service:test --rerun-tasks
BUILD SUCCESSFUL in 9s

MagnetServiceAuditFilterTest tests=2, failures=0, errors=0
magnet-service total tests=17, failures=0, errors=0, skipped=0
```

Rollout 证据：

```text
deployment/ircs-magnet-service successfully rolled out
pod: ircs-magnet-service-569d445dd9-pmc7f Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-magnet-service@sha256:8adacd816cd0e71008924b554a9c444ace1878e6d1ed2197ff6edc2ec5ff1ad1
```

MARK：

```text
MARK 1/4 magnet-service handler request writes request_source
MARK 2/4 magnet-service audit redacts query token
MARK 3/4 magnet-service audit excludes body Authorization and Cookie
MARK 4/4 magnet-service audit filter skips actuator noise
MARK 1/4 magnet-service pod is Ready
MARK 2/4 magnet-service provider list request returns success
MARK 3/4 magnet-service live audit row redacts query token
MARK 4/4 magnet-service live smoke cleanup leaves no DB residue
```

Live smoke 证据：

```text
trace: 02812-magnet-audit-20260608150827
request_audit_logs magnet-service 02812 residue: 0
local magnet-service port-forward residue: none
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
requests.cpu=880m/4, requests.memory=4480Mi/8Gi
limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
```

## 本轮 storage-service handler audit task spec

状态：已完成

背景：

`028-12` 已完成 config-service、catalog-service、content-service、credential-service、
identity-service、interaction-service、magnet-service handler audit，并完成 notification
worker/job audit、ops 查询和 live smoke。`ircs-storage-service` 承载 `/media/**`、avatar
upload、cover image admin API、image download consumer 和 R2 watchdog，是媒体存储行为 parity
的核心服务。补齐 handler audit 后，portal/admin 持续验证媒体链路时可以用 service-level trace
定位 BFF、storage handler、local object storage 与 R2/watchdog 的边界。

目标：

- 为 `ircs-storage-service` 接入 `ServiceRequestAuditFilter`。
- 让 storage handler 请求写入 `request_audit_logs.request_source=ircs-storage-service`。
- 本地测试验证 query token 脱敏、request body / Authorization / Cookie 排除和 actuator 噪音排除。
- 通过只读 cover image list endpoint 完成 live smoke。

非目标：

- 不修改 storage-service avatar、cover image、local object storage 或 R2 业务语义。
- 不触发 upload、download、delete、retry、sync-r2、avatar upload 或 queue publish。
- 不新增或修改 HTTPRoute、Service、Pod、PVC、中间件和资源配额。
- 不接入新的 queue consumer、scheduler 或 worker/job audit 事件。

上游来源：

- `docs/reports/055-per-service-audit-collection-parity-analysis.md`
- `docs/reports/071-02812-storage-service-audit-handler.md`
- `docs/migrations/071-storage-service-audit-handler.md`

输入：

- `GET /api/v1/cover-images`
- `request_audit_logs`
- `X-Trace-Id`
- `X-Authenticated-User`
- query string 中用于脱敏验证的 `token`

输出：

- `StorageServiceAuditConfiguration`
- `StorageServiceAuditFilterTest`
- `scripts/smoke/storage-service-audit-02812-smoke.ps1`
- 本任务、报告、迁移记录和索引回写。

影响范围：

```text
services/ircs-storage-service/**
scripts/smoke/storage-service-audit-02812-smoke.ps1
docs/reports/071-02812-storage-service-audit-handler.md
docs/migrations/071-storage-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
docs/requirements/028-v1-v3-behavior-parity-closeout.md
docs/index.md
tasks/index.md
docs/k8s/current-environment-2026-06-02.md
```

依赖：

- shared `ServiceRequestAuditFilter` 与 `RequestAuditSanitizer` 可复用。
- storage-service 使用现有 dev PostgreSQL `request_audit_logs` schema。
- `ircs-dev` 中 `ircs-storage-service`、PostgreSQL 和 kubectl 访问可用。

风险：

- storage endpoint 涉及文件和 R2，live smoke 必须只读，避免制造对象或队列残留。
- audit 写入失败不得影响 storage handler 返回。
- 敏感 query、Authorization、Cookie 和请求体不能进入 audit 表。

验收标准：

```text
MARK 1/4 storage-service handler request writes request_source
MARK 2/4 storage-service audit redacts query token
MARK 3/4 storage-service audit excludes body Authorization and Cookie
MARK 4/4 storage-service audit filter skips actuator noise
```

live smoke 验收：

```text
MARK 1/4 storage-service pod is Ready
MARK 2/4 storage-service cover image list request returns success
MARK 3/4 storage-service live audit row redacts query token
MARK 4/4 storage-service live smoke cleanup leaves no DB residue
```

测试边界：

```text
.\gradlew.bat :services:ircs-storage-service:test --rerun-tasks
.\scripts\smoke\storage-service-audit-02812-smoke.ps1
```

关联文档：

- `docs/reports/071-02812-storage-service-audit-handler.md`
- `docs/migrations/071-storage-service-audit-handler.md`

关联任务：

- 父任务：`tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- 本任务：`tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

实现结果：

- `StorageServiceAuditConfiguration` 注册 storage-service handler audit。
- `StorageServiceAuditFilterTest` 覆盖 cover image fetch handler audit 写入、query
  token/password 脱敏、request body / Authorization / Cookie / response content 排除和
  actuator 噪音排除。
- `storage-service-audit-02812-smoke.ps1` 通过只读 cover image list endpoint 完成 live audit
  与 query token 脱敏验证。

验证结果：

```text
.\gradlew.bat :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL in 10s

StorageServiceAuditFilterTest tests=2, failures=0, errors=0, skipped=0
storage-service total tests=52, failures=0, errors=0, skipped=0
```

Rollout 证据：

```text
deployment/ircs-storage-service successfully rolled out
pod: ircs-storage-service-6c8788f87-jhg57 Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-storage-service@sha256:89408df252a2fb53ad6a939fae66427f78bbd8808791fa4afd941e1a6c487ff2
```

MARK：

```text
MARK 1/4 storage-service handler request writes request_source
MARK 2/4 storage-service audit redacts query token
MARK 3/4 storage-service audit excludes body Authorization and Cookie
MARK 4/4 storage-service audit filter skips actuator noise
MARK 1/4 storage-service pod is Ready
MARK 2/4 storage-service cover image list request returns success
MARK 3/4 storage-service live audit row redacts query token
MARK 4/4 storage-service live smoke cleanup leaves no DB residue
```

Live smoke 证据：

```text
trace: 02812-storage-audit-20260608152804
request_audit_logs storage-service 02812 residue: 0
local storage-service port-forward residue: none
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
requests.cpu=880m/4, requests.memory=4480Mi/8Gi
limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
```

## 本轮 search-service handler audit task spec

状态：已完成

背景：

`028-12` 已完成 config-service、catalog-service、content-service、credential-service、
identity-service、interaction-service、magnet-service、storage-service handler audit，并完成
notification worker/job audit、ops 查询和 live smoke。`ircs-search-service` 承载 portal search
suggest/recommendations API，也是 normalization、metadata result、aggregation 等内部链路最终刷新
Elasticsearch 的下游服务。补齐 handler audit 后，portal/admin 持续验证搜索链路时可以用
service-level trace 定位 BFF、search handler、ES 降级和 search sync consumer 的边界。

目标：

- 为 `ircs-search-service` 接入 `ServiceRequestAuditFilter`。
- 让 search handler 请求写入 `request_audit_logs.request_source=ircs-search-service`。
- 本地测试验证 query token 脱敏、Authorization / Cookie 排除和 actuator 噪音排除。
- 通过只读 search suggest endpoint 完成 live smoke。

非目标：

- 不修改 search-service suggest/recommendations 业务语义。
- 不修改 search sync consumer、legacy UUID parser、Elasticsearch document mapping 或 index 写入。
- 不触发 search sync queue 投递、ES fixture 写入或 recommendations fixture。
- 不新增或修改 HTTPRoute、Service、Pod、PVC、中间件和资源配额。

上游来源：

- `docs/reports/055-per-service-audit-collection-parity-analysis.md`
- `docs/reports/072-02812-search-service-audit-handler.md`
- `docs/migrations/072-search-service-audit-handler.md`

输入：

- `GET /api/portal/search/suggest`
- `request_audit_logs`
- `X-Trace-Id`
- `X-Authenticated-User`
- query string 中用于脱敏验证的 `token`

输出：

- `SearchServiceAuditConfiguration`
- `SearchServiceAuditFilterTest`
- `scripts/smoke/search-service-audit-02812-smoke.ps1`
- 本任务、报告、迁移记录和索引回写。

影响范围：

```text
services/ircs-search-service/**
scripts/smoke/search-service-audit-02812-smoke.ps1
docs/reports/072-02812-search-service-audit-handler.md
docs/migrations/072-search-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
docs/requirements/028-v1-v3-behavior-parity-closeout.md
docs/index.md
tasks/index.md
docs/k8s/current-environment-2026-06-02.md
```

依赖：

- shared `ServiceRequestAuditFilter` 与 `RequestAuditSanitizer` 可复用。
- search-service 使用现有 dev PostgreSQL `request_audit_logs` schema。
- `ircs-dev` 中 `ircs-search-service`、PostgreSQL 和 kubectl 访问可用。

风险：

- suggest endpoint 依赖 Elasticsearch；live smoke 需要接受空列表但必须保持 HTTP success。
- audit 写入失败不得影响 search handler 返回。
- 敏感 query、Authorization 和 Cookie 不能进入 audit 表。

验收标准：

```text
MARK 1/4 search-service handler request writes request_source
MARK 2/4 search-service audit redacts query token
MARK 3/4 search-service audit excludes Authorization and Cookie
MARK 4/4 search-service audit filter skips actuator noise
```

live smoke 验收：

```text
MARK 1/4 search-service pod is Ready
MARK 2/4 search-service suggest request returns success
MARK 3/4 search-service live audit row redacts query token
MARK 4/4 search-service live smoke cleanup leaves no DB residue
```

测试边界：

```text
.\gradlew.bat :services:ircs-search-service:test --rerun-tasks
.\scripts\smoke\search-service-audit-02812-smoke.ps1
```

关联文档：

- `docs/reports/072-02812-search-service-audit-handler.md`
- `docs/migrations/072-search-service-audit-handler.md`

关联任务：

- 父任务：`tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- 本任务：`tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

实现结果：

- `SearchServiceAuditConfiguration` 注册 search-service handler audit。
- `SearchServiceAuditFilterTest` 覆盖 search suggest handler audit 写入、query
  token/password 脱敏、Authorization / Cookie / response content 排除和 actuator 噪音排除。
- `search-service-audit-02812-smoke.ps1` 通过只读 suggest endpoint 完成 live audit 与
  query token 脱敏验证。

验证结果：

```text
.\gradlew.bat :services:ircs-search-service:test --rerun-tasks
BUILD SUCCESSFUL in 11s

SearchServiceAuditFilterTest tests=2, failures=0, errors=0, skipped=0
search-service total tests=15, failures=0, errors=0, skipped=0
```

Rollout 证据：

```text
deployment/ircs-search-service successfully rolled out
pod: ircs-search-service-5477cf5d99-8wnhm Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-search-service@sha256:228bad735e35e0ac36d2303ac0bce67fe438a3598bbd4d460fd59f0ded5faefe
```

MARK：

```text
MARK 1/4 search-service handler request writes request_source
MARK 2/4 search-service audit redacts query token
MARK 3/4 search-service audit excludes Authorization and Cookie
MARK 4/4 search-service audit filter skips actuator noise
MARK 1/4 search-service pod is Ready
MARK 2/4 search-service suggest request returns success
MARK 3/4 search-service live audit row redacts query token
MARK 4/4 search-service live smoke cleanup leaves no DB residue
```

Live smoke 证据：

```text
trace: 02812-search-audit-20260608154116
request_audit_logs search-service 02812 residue: 0
local search-service port-forward residue: none
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
requests.cpu=880m/4, requests.memory=4480Mi/8Gi
limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
```

## 本轮 portal-service handler audit task spec

状态：已完成

背景：

`028-12` 已完成 config-service、catalog-service、content-service、credential-service、
identity-service、interaction-service、magnet-service、storage-service、search-service handler audit，
并完成 notification worker/job audit、ops 查询和 live smoke。`ircs-portal-service` 承载
portal metadata、home、explore 和 movie detail 读聚合接口，是后续通过门户持续验证 V1 到 V3 行为迁移
的第一层业务读面。补齐 handler audit 后，portal/admin 持续验证门户链路时可以用 service-level trace
定位 portal-bff、portal handler、缓存头、参数归一化和下游数据差异。

目标：

- 为 `ircs-portal-service` 接入 `ServiceRequestAuditFilter`。
- 让 portal handler 请求写入 `request_audit_logs.request_source=ircs-portal-service`。
- 本地测试验证 query token 脱敏、Authorization / Cookie 排除和 actuator 噪音排除。
- 通过只读 metadata endpoint 完成 live smoke。

非目标：

- 不修改 portal-service metadata、home、explore、detail 业务语义。
- 不修改 portal-bff route、前端静态门户、缓存策略或参数归一化行为。
- 不触发 DB/ES/queue fixture、写入或缓存刷新。
- 不新增或修改 HTTPRoute、Service、Pod、PVC、中间件和资源配额。

上游来源：

- `docs/reports/055-per-service-audit-collection-parity-analysis.md`
- `docs/reports/073-02812-portal-service-audit-handler.md`
- `docs/migrations/073-portal-service-audit-handler.md`

输入：

- `GET /api/portal/metadata`
- `request_audit_logs`
- `X-Trace-Id`
- `X-Authenticated-User`
- query string 中用于脱敏验证的 `token`

输出：

- `PortalServiceAuditConfiguration`
- `PortalServiceAuditFilterTest`
- `scripts/smoke/portal-service-audit-02812-smoke.ps1`
- 本任务、报告、迁移记录和索引回写。

影响范围：

```text
services/ircs-portal-service/**
scripts/smoke/portal-service-audit-02812-smoke.ps1
docs/reports/073-02812-portal-service-audit-handler.md
docs/migrations/073-portal-service-audit-handler.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
docs/requirements/028-v1-v3-behavior-parity-closeout.md
docs/index.md
tasks/index.md
docs/k8s/current-environment-2026-06-02.md
```

依赖：

- shared `ServiceRequestAuditFilter` 与 `RequestAuditSanitizer` 可复用。
- portal-service 使用现有 dev PostgreSQL `request_audit_logs` schema。
- `ircs-dev` 中 `ircs-portal-service`、PostgreSQL 和 kubectl 访问可用。

风险：

- metadata endpoint 依赖 PostgreSQL；live smoke 需要只读且不构造 fixture。
- audit 写入失败不得影响 portal handler 返回。
- 敏感 query、Authorization 和 Cookie 不能进入 audit 表。

验收标准：

```text
MARK 1/4 portal-service handler request writes request_source
MARK 2/4 portal-service audit redacts query token
MARK 3/4 portal-service audit excludes Authorization and Cookie
MARK 4/4 portal-service audit filter skips actuator noise
```

live smoke 验收：

```text
MARK 1/4 portal-service pod is Ready
MARK 2/4 portal-service metadata request returns success
MARK 3/4 portal-service live audit row redacts query token
MARK 4/4 portal-service live smoke cleanup leaves no DB residue
```

测试边界：

```text
.\gradlew.bat :services:ircs-portal-service:test --rerun-tasks
.\scripts\smoke\portal-service-audit-02812-smoke.ps1
```

关联文档：

- `docs/reports/073-02812-portal-service-audit-handler.md`
- `docs/migrations/073-portal-service-audit-handler.md`

关联任务：

- 父任务：`tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- 本任务：`tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

实现结果：

- `PortalServiceAuditConfiguration` 注册 portal-service handler audit。
- `PortalServiceAuditFilterTest` 覆盖 metadata handler audit 写入、query token/password
  脱敏、Authorization / Cookie / response content 排除和 actuator 噪音排除。
- `portal-service-audit-02812-smoke.ps1` 通过只读 metadata endpoint 完成 live audit 与
  query token 脱敏验证。

验证结果：

```text
.\gradlew.bat :services:ircs-portal-service:test --rerun-tasks
BUILD SUCCESSFUL in 9s

PortalServiceAuditFilterTest tests=2, failures=0, errors=0, skipped=0
portal-service total tests=16, failures=0, errors=0, skipped=0
```

Rollout 证据：

```text
deployment/ircs-portal-service successfully rolled out
pod: ircs-portal-service-84978475cc-smxwg Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-portal-service@sha256:6d10a946fce61b6930806ee4958c8e51ccee7243c55af90f71a6521b99bc4dc2
```

MARK：

```text
MARK 1/4 portal-service handler request writes request_source
MARK 2/4 portal-service audit redacts query token
MARK 3/4 portal-service audit excludes Authorization and Cookie
MARK 4/4 portal-service audit filter skips actuator noise
MARK 1/4 portal-service pod is Ready
MARK 2/4 portal-service metadata request returns success
MARK 3/4 portal-service live audit row redacts query token
MARK 4/4 portal-service live smoke cleanup leaves no DB residue
```

Live smoke 证据：

```text
trace: 02812-portal-audit-20260608155128
request_audit_logs portal-service 02812 residue: 0
local portal-service port-forward residue: none
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
requests.cpu=880m/4, requests.memory=4480Mi/8Gi
limits.cpu=7650m/8, limits.memory=14208Mi/16Gi
```
