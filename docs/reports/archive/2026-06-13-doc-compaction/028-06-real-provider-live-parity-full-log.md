# 028-06 real provider live parity 后续子任务

状态：完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/041-v1-v3-behavior-parity-gap-audit.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`

## 背景

`028-03`、`028-05-C`、`028-05-E` 和 `028-05-G` 已经证明 dev-safe provider
job/run/link、fan-out、terminal failure 和 pending gather 语义，但真实 Douban/RT
provider、公网失败分类、TMDB credential gating 和 live ack/DLQ 仍未完整复现。

## 目标

- 为 metadata Douban/RT provider 补真实 provider task spec 和 dev-safe live smoke。
- 验证 TMDB provider 在 dev credential 配置下的 gating、成功和失败路径。
- 复核 magnet provider search 从 fixture runner 走向真实 provider 的最小安全策略。
- 记录 queue ack/DLQ、provider run/result、raw metadata 和 search sync 副作用。

## 非目标

- 不开放公网生产流量。
- 不引入高资源 crawler 集群。
- 不绕过 dev credential 和 provider enable gate。

## 输入

- `q.fetch.metadata.douban`
- `q.fetch.metadata.tmdb`
- `q.fetch.metadata.rt`
- magnet provider search job/run/link 记录
- dev credential/config

## 输出

- 真实 provider live parity 设计和实现。
- 对应 smoke 脚本、迁移记录和任务回写。
- 第一刀切片输出：
  - metadata provider worker 在 disabled provider task 到达时输出可追踪终止分类，不调用真实 provider。
  - TMDB live credential/upstream diagnostic smoke，在缺少 credential 或 credential-service 不可用时输出 skip/diagnostic 证据。
  - provider parity 第一刀报告和迁移记录。

## 影响范围

```text
services/ircs-metadata-worker/**
services/ircs-magnet-service/**
scripts/smoke/**
docs/reports/**
docs/migrations/**
tasks/**
```

## 依赖

- dev provider credential 已明确配置或明确跳过。
- RabbitMQ、PostgreSQL、search-service 可用。

## 风险

- 外部站点和公网质量不稳定，smoke 必须区分 provider 失败和代码回归。
- provider 真实调用可能触发限速，需要最小样本和可清理 fixture。

## 验收标准

- 每个启用的 provider 都有成功或明确失败分类证据。
- queue/DLQ 清理为 0 或失败进入预期 DLQ。
- provider run/result、raw metadata 和 search sync 副作用可追溯。

## 测试边界

- 先本地单元/集成测试，再执行 dev live smoke。
- 未配置 credential 时必须输出 skip 证据，不能伪装成通过。

## 当前状态

已完成第一刀 provider enable gate、TMDB direct/queue live success、magnet YTS_BZ runner contract、
Douban provider implementation/full queue parity 和 RT provider first slice。`028-06-E` 已解除 Douban 的
`PROVIDER_IMPLEMENTATION_MISSING` 与 `CREDENTIAL_CATALOG_UNSUPPORTED`；`028-06-H` 已解除 RT 本地
provider implementation/catalog 缺口，并以真实 Rotten Tomatoes readonly HTTP 200 验证 `The Matrix -> matrix`。
`DEV_PROVIDER_GATE_DISABLED` 仍保留为 dev 安全 gate；RT live image rollout 和 full queue parity
已由 `028-06-I` 完成，`028-06 real provider live parity` 闭环。

## 子任务 028-06-A：metadata provider enable gate + TMDB diagnostic smoke

### 背景

当前 metadata dispatcher 已通过 `app.metadata.providers.*` 控制 provider fan-out，但
`MetadataProviderWorker` 只受 `app.metadata.provider-workers.tmdb-enabled` 控制。启用
TMDB worker 后，Douban/RT/TMDB queue task 都会被同一个 worker 处理；如果 disabled
provider queue 中出现历史消息或外部误投消息，需要给出明确分类，不能误认为真实 provider
已经执行。

### 目标

- 在 `MetadataProviderWorker` 内复用本地 provider enable gate，对 disabled provider 输出
  `PROVIDER_DISABLED` 终止失败。
- 保持 TMDB provider 启用时的成功、retryable credential failure 和 unsupported context
  语义不变。
- 新增 `02806` smoke 脚本，真实探测 TMDB credential lease 和 TMDB upstream；缺 credential
  或 credential-service 不可用时只输出 skip/diagnostic，不伪造成功。
- 新增 provider parity 第一刀报告与迁移记录，记录范围、验证和剩余 backlog。

### 非目标

- 不新增公共 `HTTPRoute` 或新 controller/BFF endpoint。
- 不实现 Douban/RT 真实 crawler。
- 不改 magnet fixture runner 的生产替换策略，只在报告中记录后续边界。
- 不修改 normalization、storage、shared observability、per-service audit、`docs/index.md`、
  `tasks/index.md`、K8S 总快照。

### 上游来源

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/041-v1-v3-behavior-parity-gap-audit.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- `docs/reports/050-metadata-provider-worker-enrichment-parity-analysis.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

### 输入

- `services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/MetadataProviderWorker.java`
- `services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/dispatch/MetadataProviderRoutingProperties.java`
- `services/ircs-metadata-worker/src/test/java/com/prodigalgal/ircs/metadata/provider/MetadataProviderWorkerTest.java`
- `services/ircs-metadata-worker/src/main/resources/application.yaml`
- dev credential/config 环境变量。

### 输出

- provider enable gate 实现和单元测试。
- `scripts/smoke/metadata-provider-live-parity-02806-smoke.ps1`
- `docs/reports/056-provider-parity-02806-first-slice.md`
- `docs/migrations/056-metadata-provider-parity-gating.md`

### 影响范围

```text
services/ircs-metadata-worker/**
scripts/smoke/*02806*.ps1
docs/reports/*provider*02806*.md
docs/migrations/*provider*parity*.md
tasks/done/028-06-real-provider-live-parity.md
```

### 依赖

- 本地 Gradle/JDK 能运行 `:services:ircs-metadata-worker:test`。
- live smoke 依赖 credential-service URL、可选 internal token、TMDB upstream 网络。

### 风险

- disabled provider 历史消息会被明确终止，可能暴露上游误投或旧队列残留。
- credential-service 不可用与无 credential 都不能作为成功，只能作为 skip/diagnostic 证据。
- 外部 TMDB 网络不稳定时，smoke 需要输出 HTTP/network 分类。

### 验收标准

- disabled provider task 输出 `PROVIDER_DISABLED`，且不调用 provider implementation。
- TMDB enabled provider 的成功与 `POOL_EXHAUSTED` retryable failure 测试继续通过。
- smoke 在缺 credential 或 credential-service 不可用时退出成功并输出 `SKIPPED`/diagnostic JSON；
  有 credential 时至少执行一次真实 TMDB search 并输出 `SUCCESS` 或真实失败分类。

### 测试边界

- 必跑：`./gradlew.bat :services:ircs-metadata-worker:test`
- 视环境执行：`powershell -ExecutionPolicy Bypass -File scripts/smoke/metadata-provider-live-parity-02806-smoke.ps1`
- 不跑公网生产流量，不构造伪成功。

### 关联文档

- `docs/reports/056-provider-parity-02806-first-slice.md`
- `docs/migrations/056-metadata-provider-parity-gating.md`

### 关联任务

- 父任务：`tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- 本任务：`tasks/done/028-06-real-provider-live-parity.md`

### 当前状态

已完成第一刀代码、smoke 和文档回写。历史中间态曾保留 provider live parity backlog；
当前已由后续 `028-06-C/E/F/G/H/I` 全部关闭，`028-06` 总任务完成。

### 实现结果

- 已在 `MetadataProviderWorker` 增加 provider enable gate。
- disabled provider task 输出 `PROVIDER_DISABLED` terminal failure，且不调用 provider implementation。
- 已新增 `scripts/smoke/metadata-provider-live-parity-02806-smoke.ps1`，用于 TMDB credential/upstream
  diagnostic。
- 已新增并回写：
  - `docs/reports/056-provider-parity-02806-first-slice.md`
  - `docs/migrations/056-metadata-provider-parity-gating.md`

### 验证结果

- 通过：`.\gradlew.bat :services:ircs-metadata-worker:test`
- 通过：`.\gradlew.bat :services:ircs-magnet-service:test`
- 通过 diagnostic：`powershell -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-live-parity-02806-smoke.ps1`
  - 当前结果：`SKIPPED/CREDENTIAL_SERVICE_UNAVAILABLE`
  - 原因：本机 `http://127.0.0.1:18088` 未启动 credential-service。
  - 说明：这是预期 skip/diagnostic，不伪造 TMDB live 成功。

### 剩余项

- 连接 dev credential-service 与真实 TMDB credential 后，补 TMDB success/HTTP failure live 证据。
- 补 TMDB queue ack/DLQ、provider result、raw metadata、search sync 联动 smoke。
- Douban queue full parity 仍需另拆显式启用 gate 的 low-resource live smoke。
- 拆分 RT 真实 provider task spec 与 dev-safe live smoke。
- 拆分 magnet 真实 provider runner 的 endpoint/credential gate、失败分类和 live smoke。

## 子任务 028-06-B：TMDB provider live queue diagnostic smoke

### 背景

第一刀 smoke 只验证 credential-service lease 和 TMDB upstream direct call，尚未真实投递
`q.fetch.metadata.tmdb`，也未观察 provider result 被 metadata result consumer 聚合后对
`raw_videos` 和 `q.search.sync.raw` 的副作用。当前 `ircs-dev` metadata-worker 清单中
`APP_METADATA_TMDB_ENABLED=false`，所以 live queue 成功路径必须显式允许临时启用并在结束后恢复。

### 目标

- 新增 TMDB provider live queue smoke，投递最小 provider task。
- 区分 credential-service 不可用、无 enabled credential、credential 无 `api_key`、TMDB upstream
  failure、provider disabled、消费者不可用和真实成功路径。
- 成功路径验证 queue ack/DLQ、raw metadata、search sync ES document 与清理结果。

### 非目标

- 不新增公共 `HTTPRoute`。
- 不修改 normalization、storage、audit、ops service 代码。
- 不实现 Douban/RT 真实 provider。
- 不替换 magnet 真实 provider runner。
- 不默认 purge 共享队列或伪造 TMDB 成功。

### 上游来源

- `docs/reports/056-provider-parity-02806-first-slice.md`
- `docs/migrations/056-metadata-provider-parity-gating.md`
- `docs/reports/057-provider-live-queue-02806-tmdb-smoke.md`
- `docs/migrations/057-metadata-provider-live-queue-smoke.md`

### 输入

- `q.fetch.metadata.tmdb`
- `q.enrich.metadata.result`
- `q.search.sync.raw`
- dev PostgreSQL `raw_videos`
- dev credential-service TMDB lease
- TMDB upstream search/detail API

### 输出

- `scripts/smoke/metadata-provider-live-queue-02806-smoke.ps1`
- 本任务、报告和迁移记录回写。

### 影响范围

```text
scripts/smoke/*02806*.ps1
docs/reports/*02806*.md
docs/migrations/*provider*.md
tasks/done/028-06-real-provider-live-parity.md
```

### 依赖

- `kubectl` 可访问 `ircs-dev`。
- `postgres-0`、`rabbitmq-0`、`svc/rabbitmq-svc`、`svc/ircs-credential-service`、`svc/elasticsearch-svc`
  可用。
- 如需成功路径，dev credential-service 中存在 enabled TMDB credential，且允许脚本临时设置
  `APP_METADATA_TMDB_ENABLED=true`。

### 风险

- metadata-worker env patch 会触发 rollout，可能与其他 agent 的 live smoke 冲突。
- 共享 queue 若已有消息，脚本必须默认跳过以避免误判或误清理。
- TMDB upstream 和 credential 限速不稳定，failure 分类应作为诊断证据，不作为成功替代。

### 验收标准

- 默认 provider disabled 时输出 `SKIPPED/TMDB_PROVIDER_DISABLED`。
- 缺 credential 或 credential-service 不可用时输出 `SKIPPED`/diagnostic。
- 显式允许临时启用且 credential/upstream 正常时输出 `MARK 1/6` 到 `MARK 6/6`。
- 成功后 `raw_videos` fixture 与 ES raw document 清理完成，相关 queue/DLQ 为空。

### 测试边界

- 必跑 diagnostic：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-live-queue-02806-smoke.ps1
```

- 视 credential 和共享环境执行成功路径：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-live-queue-02806-smoke.ps1 -EnableTmdbProviderForSmoke
```

### 关联文档

- `docs/reports/057-provider-live-queue-02806-tmdb-smoke.md`
- `docs/migrations/057-metadata-provider-live-queue-smoke.md`

### 关联任务

- 父任务：`tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- 本任务：`tasks/done/028-06-real-provider-live-parity.md`

### 当前状态

已完成 smoke 脚本、默认 diagnostic 验证和 V1 credential 导入后的真实 queue success 复跑。

### 实现结果

- 已新增 `scripts/smoke/metadata-provider-live-queue-02806-smoke.ps1`。
- 默认路径只做 credential/upstream/provider gate diagnostic，不满足条件时输出 `SKIPPED`。
- 成功路径需要显式传入 `-EnableTmdbProviderForSmoke`，脚本会临时设置 metadata-worker
  `APP_METADATA_TMDB_ENABLED=true` 并在 `finally` 恢复。
- 已将 Elasticsearch port-forward 默认端口改为 `39201`，避开 Windows `19201` bind forbidden。
- 已新增 `-SkipPreflightTmdbDiagnostic`，用于跳过本地 TMDB preflight，直接验证集群内 provider
  worker 的真实调用和副作用链路。
- queue live 成功路径已覆盖：
  - `q.fetch.metadata.tmdb` 投递与 drain。
  - `q.enrich.metadata.result` result consumer 聚合后 raw metadata 更新。
  - `q.search.sync.raw` 被 search-service 消费后 ES raw document 刷新。
  - 相关 queue/DLQ 为空、DB/ES fixture 清理。

### 验证结果

- 通过：PowerShell parse check。
- 通过：默认 diagnostic smoke。
- 通过：V1 credential 导入后的 live queue success smoke。
- 通过：`ircs-dev` HTTPRoute 只读检查，结果为 `No resources found in ircs-dev namespace`。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-live-queue-02806-smoke.ps1
```

历史 diagnostic 结果：

```text
SKIPPED/TMDB_CREDENTIAL_WITHOUT_API_KEY
credential-service HTTP 200
leaseCount=1
```

说明：当时 `ircs-dev` credential-service 可达，但 TMDB lease 不含 `api_key`；脚本按预期没有投递
provider queue，也没有伪造 TMDB success。

V1 credential 导入后复跑：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-live-queue-02806-smoke.ps1 -SkipPreflightTmdbDiagnostic -WaitAttempts 45 -WaitSeconds 2 -PurgeSmokeQueuesOnCleanup
```

结果：

```text
MARK 1/6 credential lease succeeded; TMDB upstream diagnostic skipped for direct queue verification
MARK 2/6 metadata/search consumers are ready and HTTPRoute remains absent
MARK 3/6 TMDB provider queue task is published and q.fetch.metadata.tmdb drains
MARK 4/6 provider result updates raw metadata to SUCCESS with tmdb_id
MARK 5/6 raw search sync refreshes ES document with TMDB enrichment fields
MARK 6/6 provider/result/search queues and DLQs are empty; smoke fixture is cleaned
```

后置状态：

```text
raw metadata reached SUCCESS|603|tt0133093|1999|8.2
DB fixture residue: 0
q.fetch.metadata.tmdb/q.enrich.metadata.result/q.search.sync.raw and DLQ: 0 messages
APP_METADATA_TMDB_ENABLED restored to false
ResourceQuota unchanged: pods 28/30, services 24/26
```

### 剩余项

- Douban/RT 真实 provider task spec 与 dev-safe live smoke。
- magnet YTS_BZ runner contract 已由 `028-06-C` 完成，默认 no-network gate 与失败分类已固定；
  后续若需要公网 live success，必须另拆显式授权的 provider live smoke。

## 子任务 028-06-C：magnet YTS_BZ runner contract

### 当前状态

已完成并移动到 `tasks/done/028-06-c-magnet-yts-bz-runner-contract.md`。

### 验收结果

```text
MARK 1/5 magnet real-provider gate defaults to fixture/no-network：通过
MARK 2/5 YTS_BZ runner supports IMDb query only：通过
MARK 3/5 YTS_BZ JSON response maps to normalized magnet candidates：通过
MARK 4/5 HTTP error/timeout/parse failure are classified without leaking response body：通过
MARK 5/5 magnet-service tests pass without public network access：通过
```

### 验证记录

```powershell
.\gradlew.bat :services:ircs-magnet-service:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL in 20s`；主线程聚合复验包含 magnet-service，返回
`BUILD SUCCESSFUL in 44s`。

### 剩余项

- Douban queue full parity 仍需另拆显式启用 gate 的 low-resource live smoke。
- 拆分 RT 真实 provider task spec 与 dev-safe live smoke。
- 如需真实公网 magnet provider success，需要另拆 explicit live smoke，不在默认 dev gate 中打开。

## 子任务 028-06-E：Douban provider implementation first slice

### 当前状态

已完成并移动到 `tasks/done/028-06-e-douban-provider-implementation-first-slice.md`。

### 验收结果

```text
MARK 1/6 provider implementation registered for DOUBAN
MARK 2/6 credential catalog supports DOUBAN template/create/fingerprint/lease
MARK 3/6 dev gate remains default disabled and runtime env keeps priority over DB/default seed
MARK 4/6 request/response/error mapping covered by focused tests
MARK 5/6 real Douban readonly smoke returns HTTP 200 and parses one candidate
MARK 6/6 no write/no queue/no K8S env mutation/no runtime residue
```

### 验证记录

```powershell
.\gradlew.bat :services:ircs-metadata-worker:test :services:ircs-credential-service:test
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-douban-live-02806-smoke.ps1
```

live smoke 摘要：

```text
status=SUCCESS_WITH_DEV_GATE_DISABLED
reason=DEV_PROVIDER_GATE_DISABLED
externalRequestCount=1
Douban HTTP 200
parsed firstId=1291843, firstTitle=黑客帝国, firstSubTitle=The Matrix, firstYear=1999
writeDb=false, publishedQueue=false, patchedKubernetesEnv=false
```

### 剩余项

- Douban queue full parity：显式临时开启 `APP_METADATA_DOUBAN_ENABLED` 后验证 provider result、
  raw metadata、search sync、queue/DLQ 和 cleanup。
- Douban credential/cookie 场景：当前 dev lease 为 0，只证明公开 suggest first slice。
- RT 真实 provider 仍需独立切片。

## 子任务 028-06-F：Douban provider live queue parity

### 当前状态

完成，独立 task spec 已移动：

- `tasks/done/028-06-f-douban-provider-live-queue-parity.md`

### 目标

- 受控短期开启 `APP_METADATA_DOUBAN_ENABLED=true`。
- 投递一条最小 `q.fetch.metadata.douban` provider task。
- 验证 provider result、raw metadata、search sync、queue/DLQ 和 cleanup。
- 完成后恢复原 gate/env/deployment 状态并记录证据。

### 关联文档

- `docs/reports/091-02806-f-douban-provider-live-queue-parity.md`

### 验证结果

早期阻塞已由 `028-06-G` 解除，最终 full queue smoke 通过。

```text
MARK 1/6 credential lease diagnostic completed and Douban provider gate is enabled for one smoke
MARK 2/6 metadata/search consumers are ready and HTTPRoute remains absent
MARK 3/6 Douban provider queue task is published and q.fetch.metadata.douban drains
MARK 4/6 provider result updates raw metadata to SUCCESS with douban_id
MARK 5/6 raw search sync refreshes ES document with Douban enrichment fields
MARK 6/6 provider/result/search queues and DLQs are empty; smoke fixture is cleaned
status=SUCCESS
reason=DOUBAN_LIVE_QUEUE_PARITY_OK
externalRequestCount=1
q.fetch.metadata.douban consumers=1
```

恢复/清理：

```text
APP_METADATA_DOUBAN_ENABLED=false
APP_METADATA_TMDB_WORKER_ENABLED=true
APP_METADATA_LISTENER_ENABLED=true
queue/DLQ messages=0
DB smoke residue=0
```

最终镜像：`sha256:341baddc3286551eff7970eb4ffb166df67f4fa227e324aecaea3cf66a09b526`。

2026-06-09 docs/tasks 收敛补充：

- `028-06-E` 已按完成项入账：Douban provider implementation、credential catalog 和真实只读
  Douban smoke 已完成，HTTP 200 并解析 `黑客帝国 / The Matrix / 1999`。
- `028-06-F` 已标记完成，独立任务位于 `tasks/done/028-06-f-douban-provider-live-queue-parity.md`。
- 原阻塞不是外网失败；`028-06-G` 发布 metadata-worker 后复验 `q.fetch.metadata.douban consumers=1`，full queue smoke 外网请求数为 `1`。
- 清理状态保持干净：`APP_METADATA_DOUBAN_ENABLED=false` 已恢复，目标 queue/DLQ messages=0，
  DB smoke residue=0。
- 下一步剩余 `028-06` 范围仅为 RT provider 独立切片。

## 子任务 028-06-G：Douban metadata-worker live image queue parity

### 当前状态

完成，独立 task spec 已移动：

- `tasks/done/028-06-g-douban-metadata-worker-live-image-queue-parity.md`

### 目标

- 发布或确认 `ircs-dev` metadata-worker live 镜像包含 Douban listener/provider。
- 复验 `q.fetch.metadata.douban consumers=1`。
- 复跑 `028-06-F` full queue parity smoke，记录外网请求、raw metadata、ES 和 cleanup 证据。
- 测试后恢复 `APP_METADATA_DOUBAN_ENABLED=false` 等原 gate/env。

### 关联文档

- `docs/reports/094-02806g-douban-metadata-worker-live-image-queue-parity.md`

### 验证结果

完成。最终 metadata-worker live 镜像 digest：

```text
sha256:341baddc3286551eff7970eb4ffb166df67f4fa227e324aecaea3cf66a09b526
q.fetch.metadata.douban consumers=1
externalRequestCount=1
MARK 6/6
APP_METADATA_DOUBAN_ENABLED=false restored
DB smoke residue=0
port-forward residue=0
```

## 子任务 028-06-G retry：Douban provider full queue parity 复验

### 当前状态

完成，独立 task spec 已移动：

- `tasks/done/028-06-g-retry-douban-provider-full-queue-parity.md`

### 关联文档

- `docs/reports/096-02806g-retry-douban-provider-full-queue-parity.md`

### 验证结果

本次 retry 不直接采信上一 worker 结果，重新检查 live 状态并复跑 full queue smoke。

```text
current workspace has no .git; git status unavailable
metadata-worker imageID=harbor.mnnu.eu.org/ircs/ircs-metadata-worker@sha256:341baddc3286551eff7970eb4ffb166df67f4fa227e324aecaea3cf66a09b526
APP_METADATA_DOUBAN_ENABLED=false before smoke
q.fetch.metadata.douban consumers=1
BUILD SUCCESSFUL :services:ircs-metadata-worker:test
MARK 6/6
status=SUCCESS
reason=DOUBAN_LIVE_QUEUE_PARITY_OK
expectedDoubanId=1291843
externalRequestCount=1
APP_METADATA_DOUBAN_ENABLED=false restored
queue/DLQ messages=0
DB smoke residue=0
port-forward residue=0
```

结论：当前 live 镜像/manifest 已满足 Douban provider full queue parity；本次不需要重新构建或推送
metadata-worker 镜像。历史中间态剩余 RT provider 独立切片，当前已由 `028-06-H/I` 关闭。

## 子任务 028-06-H：RT provider first slice + 真实外网最小 smoke

### 状态

完成 first slice；full queue parity 后续已由 `028-06-I` live image rollout + 受控 gate smoke 关闭。

### 背景

`028-06-E/F/G` 已完成 Douban provider first slice 与 full queue parity；历史中间态曾将
`028-06` 剩余范围收敛为 Rotten Tomatoes provider 独立切片，当前已由 `028-06-H/I` 关闭。
当前 V3 已具备 RT provider queue topic、provider enable gate
和 terminal failure 框架，但仍需要对照 V1 行为补齐 provider implementation、credential catalog
最小支持、请求/响应/错误语义、最小只读公网 smoke，并在环境允许时推进受控 queue smoke。

### 目标

- 梳理 V1 RT provider 的请求入口、字段映射和失败语义，形成本切片报告。
- 在 `metadata-worker` 补齐或确认 `ProviderType.ROTTEN_TOMATOES` provider 注册。
- 在 `credential-service` 增加 `ROTTEN_TOMATOES` catalog/template/create/fingerprint/lease 最小支持。
- 保持 dev gate 默认关闭；仅在显式 smoke 时短期开启并恢复。
- 覆盖 request construction、response parsing、empty result、HTTP/parse/network error mapping 和最小字段映射。
- 执行真实外网最小只读 smoke，记录 URL、状态、请求次数、no-write/no-queue/cleanup。
- 如果 live consumer、gate 和 cleanup 条件满足，再执行受控 full queue smoke；否则记录精确阻塞。

### 非目标

- 不绕过 Rotten Tomatoes 反爬策略，不使用高频抓取，不保存公网响应全文。
- 不修改 config-service/content-service C2、notification、storage、portal/admin/BFF。
- 不常驻开启 `APP_METADATA_RT_ENABLED` 或扩大公网流量。
- 不把公网 HTTP 可达伪装成 full provider parity。

### 影响范围

```text
services/ircs-metadata-worker/** RT provider/adapter/tests
services/ircs-credential-service/** provider catalog/tests
deploy/k8s/dev/metadata-worker* RT gate only
scripts/smoke/metadata-provider-rt*
docs/reports/*02806*rt*.md
docs/migrations/*rt*.md
tasks/**
```

### 验收标准

- `ROTTEN_TOMATOES` provider implementation 被 worker provider list 注册。
- `credential-service` catalog 支持 RT template/create/fingerprint/lease，不泄露 secret。
- dev gate 默认关闭，runtime env 优先级不被 DB/default seed 覆盖。
- focused tests 覆盖请求构造、响应解析、字段映射、empty result、403/404/429/5xx/network/parse failure。
- 真实外网最小 smoke 低频只读，记录 URL/HTTP 状态/请求次数，且不写 DB、不投递 queue、不修改 K8S env。
- queue smoke 如执行，必须恢复 gate/env 并提供 queue/DLQ、DB/ES、port-forward 清理证据。

### 测试方式

```powershell
.\gradlew.bat :services:ircs-metadata-worker:test :services:ircs-credential-service:test
$null = [scriptblock]::Create((Get-Content -Raw 'scripts\smoke\metadata-provider-rt-live-02806-smoke.ps1')); Write-Output 'parse-ok'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-rt-live-02806-smoke.ps1
```

后续 live image rollout 切片创建并执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-rt-live-queue-02806-smoke.ps1 -EnableRtProviderForSmoke
```

### 关联文档

- `docs/reports/097-02806h-rt-provider-first-slice.md`
- `docs/migrations/091-rt-provider-first-slice.md`
- `tasks/done/028-06-h-rt-provider-first-slice.md`

### 实现结果

- 已对照 V1 `RottenTomatoesMetadataProvider`：RT first slice 使用
  `https://www.rottentomatoes.com/search?search={title}`，解析 `search-page-media-row a[slot=title]`
  并回填 URL slug 到 `rottenTomatoesId`。
- 已新增 V3 `RottenTomatoesMetadataProvider`、`RestClientRottenTomatoesHttpClient`、
  `RottenTomatoesCredentialRepository` 和 `RottenTomatoesProviderProperties`。
- `credential-service` 已支持 `ROTTEN_TOMATOES` template/create/fingerprint/lease。
- dev `APP_METADATA_RT_ENABLED=false` 保持关闭，未修改 live env。

### 验证结果

通过：

```powershell
.\gradlew.bat :services:ircs-metadata-worker:test :services:ircs-credential-service:test
```

结果：`BUILD SUCCESSFUL in 17s`。

通过：

```powershell
$null = [scriptblock]::Create((Get-Content -Raw 'scripts\smoke\metadata-provider-rt-live-02806-smoke.ps1')); Write-Output 'parse-ok'
```

结果：`parse-ok`。

真实外网最小 smoke：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-rt-live-02806-smoke.ps1
```

结果：

```text
status=SUCCESS_WITH_DEV_GATE_DISABLED
reason=DEV_PROVIDER_GATE_DISABLED
URL=https://www.rottentomatoes.com/search?search=The%20Matrix
HTTP=200
externalRequestCount=1
firstTitle=The Matrix
firstExtractedId=matrix
writeDb=false
publishedQueue=false
patchedKubernetesEnv=false
APP_METADATA_RT_ENABLED=false
credentialLease HTTP 200, leaseCount=0
```

Queue 只读检查：

```text
q.fetch.metadata.rt       messages=0 consumers=1
q.fetch.metadata.rt.dlq   messages=0 consumers=0
q.enrich.metadata.result  messages=0 consumers=1
q.search.sync.raw         messages=0 consumers=1
```

### MARK

```text
MARK 1/6 V1 RT provider behavior audited
MARK 2/6 provider implementation registered for ROTTEN_TOMATOES
MARK 3/6 credential catalog supports ROTTEN_TOMATOES template/create/fingerprint/lease
MARK 4/6 request/response/error mapping covered by focused tests
MARK 5/6 real Rotten Tomatoes readonly smoke returns HTTP 200 and parses The Matrix -> matrix
MARK 6/6 no write/no queue/no K8S env mutation/no runtime residue; queue full parity deferred to live image rollout slice
```

### 剩余边界

历史中间态：当时 live metadata-worker 镜像尚未包含本地 RT provider 代码，本轮未构建/推送/rollout
新镜像，也未临时开启 `APP_METADATA_RT_ENABLED=true`。当前该剩余边界已由 `028-06-I` 关闭：
发布包含本切片代码的 ARM64 metadata-worker 镜像，并完成单条 queue smoke、raw metadata/search
sync 和完整 cleanup 证据，最终 `MARK 6/6`。

## 子任务 028-06-I：RT provider ARM64 live rollout + full queue parity smoke

### 状态

完成，独立 task spec：

- `tasks/done/028-06-i-rt-provider-arm64-live-rollout-queue-parity.md`

### 范围

发布包含 RT provider first slice 的 ARM64 metadata-worker dev 镜像到 `ircs-dev`，短期开启
`APP_METADATA_RT_ENABLED=true`，执行单条 `q.fetch.metadata.rt` full queue parity smoke，并在结束后恢复
gate/env/deployment 状态、清理 DB/queue/port-forward 残留。

### 验证结果

```text
Jib imageDigest=sha256:93db92be56a2a7dc0b85d84c7a43ecb8c8f854f5947f1bbd7a6ae2dafc34291a
live imageID=harbor.mnnu.eu.org/ircs/ircs-metadata-worker@sha256:93db92be56a2a7dc0b85d84c7a43ecb8c8f854f5947f1bbd7a6ae2dafc34291a
live pod contains RottenTomatoesMetadataProvider.class
application.yaml contains rotten-tomatoes-enabled: ${APP_METADATA_RT_ENABLED:false}
q.fetch.metadata.rt consumers=1
MARK 6/6
status=SUCCESS
reason=RT_LIVE_QUEUE_PARITY_OK
externalRequestCount=1
expectedRtId=matrix
APP_METADATA_RT_ENABLED=false restored
queue/DLQ messages=0
DB smoke residue=0
ES smoke residue=404 found=false
port-forward residue=0
```

### 028-06 父任务关闭结论

`028-06` 已完成 provider enable gate、TMDB direct/live queue、magnet YTS_BZ runner contract、
Douban first slice/full queue parity、RT first slice/full queue parity。真实 provider live parity
范围内无剩余项；后续若要扩大公网 provider 样本量、增加 RT credential/cookie 策略或替换 magnet
公网 runner，应另拆新任务并保留显式 gate。
