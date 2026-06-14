# 093 V1/V3 parity 本轮进度总账

状态：进行中总账

更新时间：2026-06-11

## 背景

本报告用于收敛本轮已结束 worker / subagent 的 docs/tasks 入账结果，只记录状态、证据摘要、
阻塞边界和下一批优先级；不代表新增业务代码、脚本、deploy 或配置变更。

## 总体结论

- 整体目标仍未完成，V1/V3 深层行为 parity 继续进行中。
- 当前估算进度：约 `94%`。本轮新增完成项主要是实现面收敛：028-16-F backlog task 化、
  C1-S2c/F5、C3-S8/S9/S10、028-16-D4/F3、028-16-E/F4、028-16-A12、028-12-C/D 和 028-19 plan-only 入口；其中多项仍带统一测试债务，
  因此不按生产闭环计满。
- 开发 MVP 可用度：约 `95%`。18/18 业务服务 Ready，HTTP route missing=0，028 第一阶段
  route/BFF/queue/live-smoke 强制缺口为 0；开发环境已可支撑主体 portal/admin/worker 验证。
- 生产就绪度：约 `77%`。真实 provider、R2、SystemConfig C2、SMTP one-shot 和 notification
  maintenance runner gate 已有 live 证据；C1 auto-start gate first closure 与 C3 circuit breaker
  first slice 已入账；C1 cache/full preset/gate health、C3 identity/BFF/circuit adoption、
  028-12-C/D audit expansion、A12 cleaning dictionary hardening 和 LLM cleaning no-cost closure
  已完成实现或 focused tests，但 live/组合/production-like 统一测试仍后置，不能写成生产闭环。
- `028-06` real provider live parity 已关闭：TMDB、magnet YTS_BZ、Douban 与 RT 均已完成。
- `028-07` R2 live/watchdog parity 已关闭。
- `028-10` production governor/credential pool parity 仍进行中；send history、retention、
  notification-worker Ready 恢复、V1 SMTP one-shot 真实发送和 notification maintenance runner gate 均已完成。
- `028-16-C1` first closure 已完成：shared auto-start gate 可观察模型、normalization/storage watchdog
  和 aggregation scheduler 默认关闭已入账；C1-S2c 已补 countries/languages full preset、
  normalization SystemConfig TTL fallback 和 resolver/真实资源站 live prep；C1 父任务仍进行中。
- `028-16-C2` SystemConfig cache/event parity 已关闭；C2-S17/S19/S20/S21 已覆盖真实 Rabbit、
  content DB fallback read probe 与 missed-message TTL fallback。
- `028-16-C3-S6/S7/S8/S9/S10` 已完成 shared outbound HTTP circuit breaker first slice、
  `InternalContentClients` adoption、task-service `ScraperTaskExecutionClient` POST JSON
  adoption、identity-service avatar multipart adoption 和 admin/portal BFF shared streaming
  transport adoption、caller-specific circuit ENV matrix implementation；C3 父任务仍进行中，
  live/full/K8S matrix tests 按统一测试策略后置。
- `028-16-D` Tika MIME / file parsing first closure 已完成；`028-16-D4/F3` 已补
  detail charset、pagination 输出、Big5/Shift_JIS/UTF-8 BOM/invalid bytes fixture 和
  smoke fixture prep；真实 provider 低资源 live charset smoke 与完整统一测试仍后置。
- `028-16-E` LLM cleaning gated parity 已完成 implementation-first closure，状态 `MARK 5/6`
  partial：默认关闭、默认 `dry-run`、runner 默认关闭、fake/no-cost path、credential-service
  lease/runtime credential path 和固定错误语义已完成；真实 LLM 调用与 live smoke 统一测试后置。
- `028-19` 已从 production-like smoke orchestrator prep 推进到统一测试执行：
  `UTB-02816-P1P2-02` no-real-cost/dry-run 子集通过，`UTB-02816-P1P2-03` 在 DB migrator
  漂移修复后重跑通过，`RESOURCE-02818` 只读后置检查通过；orchestrator 已修复非 0 exit code 假绿问题。
  真实 LLM、C3 live/K8S matrix、外部 provider/SMTP/R2 仍需独立 gate。
- `028-12-C/D` 更多 worker/scheduler/runner audit expansion 已完成 implementation first closure，
  状态 partial：metadata provider queue、normalization raw queue / LLM runner、storage media queue /
  R2 watchdog、search raw/unified sync consumer 与 scraper manual/refetch runner 已补 worker-job audit；
  `UTB-02816-P1P2-03` 已提供 notification/task/search/scraper/resource 通过证据，metadata/
  normalization/storage worker live queue smoke 仍后置。
- `028-16-F1/A11` normalization golden P1/P2 已完成 implementation-first closure，状态 partial：
  title release/S02/Part/Vol、location NLP first closure、genre NLP first closure、raw category
  fallback、retry/watchdog PROCESSING reset + queue depth guard + adaptive batch min/max 已补；
  normalization-worker focused tests 69/69 通过，live normalize smoke 统一后置。
- `028-16-A12` normalization title/raw dictionary 余量切片已完成 focused closure：title bracket
  noise cleanup、area alias/composite、genre synonym 与脏 token 防护已补；normalization-worker
  focused tests 71/71 通过；CharTable 标准化入口已由 A13 接回，title display mainTitle 切换继续后置。
- `028-16-A13` post-reboot 收敛已完成：`CharTable -> HanLP -> OpenCC` 接回，同时保留 V3 已验证显示大小写；
  `TitleStructure` 继续提取 season/subtitle，但不覆盖 `title` 显示字段；`RawVideoTextNormalizerTest`
  focused tests 24/24 通过。完整 V1 mainTitle display 切换仍需独立评估。
- `028-16-B16` search outbox stats 已完成 implementation-first closure：新增只读
  `GET /api/v1/search/ops/search-sync-tasks/stats`，聚合 `search_sync_tasks` 状态、due pending、
  entity type、operation 与 retry 计数；表缺失时返回 stable unavailable；focused tests 通过。
- `028-16-F4/E` LLM cleaning no-real-cost hardening 已完成：`applyMatch/applyNoise` 幂等护栏、
  fake path 不解析 credential、不调用 live client、`LlmCredential.toString()` 脱敏和 no-cost
  focused tests 已完成；真实 LLM/live/cost 测试继续后置。
- `028-16-F5/C1` readiness 优先切片已完成：catalog/content/task 暴露
  `autoStartGateHealthIndicator`，catalog seed 增加 `APP_CATALOG_DEFAULT_SEED_ENABLED` gate；
  C1-X 已补 UUID generator 门面与 AsyncLogger/Disruptor ADR；V1 式全服务自动放行、
  time-based UUID 全量替换和 AsyncLogger/Disruptor 默认启用均已记录为后续独立切片或替代口径。
- `028-16-F7/028-12-D` worker/scheduler audit 第二切片已完成：search-service
  `SearchSyncConsumer` raw/unified queue 与 scraper-service `ManualScraperService`
  collection/refetch runner 已补 worker-job audit；focused tests search=2、scraper=4 通过。

## 本轮收敛校准

本轮采用“实现优先、统一测试后置”账本口径。以下项目视为功能实现或任务边界已完成，但只有标明
live smoke 证据的项目才计入生产闭环：

| 项 | 入账状态 | 生产闭环口径 |
| --- | --- | --- |
| 028-16-F | 完成 P1/P2 backlog 入账 | 已创建 8 个 backlog specs，并写入 `UTB-02816-P1P2-01/02/03`；这是任务边界完成，不代表 backlog 功能全部完成。 |
| 028-16-D4/F3 | UTB-01 local smoke 通过 | scraper detail charset、pagination `totalPages/totalItems` 输出、Big5/Shift_JIS/BOM/invalid bytes fixture 已补；D3-D4 local fixture smoke 已通过，真实 provider charset live smoke 后置。 |
| C3-S8 | 完成实现 + focused tests | identity-service avatar multipart caller 已接 shared `INTERNAL_SERVICE`；live/full tests 后置。 |
| C3-S9 | 完成实现 + focused tests | admin/portal BFF shared streaming transport 已接入；真实门户、SSE/download/debug-stream、K8S circuit matrix 后置。 |
| C3-S10 | 完成实现 + light config tests | caller-specific circuit ENV matrix 已接入 shared/content/task/identity/admin-bff/portal-bff；K8S override/open-circuit matrix 后置。 |
| 028-16-E | implementation-first closure，`MARK 5/6` partial | fake/dry-run/config/credential/error semantics 已落地；真实 LLM/live/full tests 后置。 |
| C1-S2c | 完成实现 + focused tests + dry-run prep | countries/languages full preset、normalization TTL fallback、resolver/resource prep 已完成；resolver/player/真实资源站 live smoke 后置。 |
| 028-19 | 统一测试已开始 | `UTB-02816-P1P2-02` no-real-cost/dry-run 子集通过；`UTB-02816-P1P2-03` 在 migrator/notification 修复后重跑通过；`RESOURCE-02818` 后置检查通过；orchestrator exit-code 假绿已修复。 |
| 028-12-C | implementation-first closure，partial | notification/task/search/scraper/resource 已有 live/UTB 证据；metadata/normalization/storage worker audit hooks 已补，live ops query/fixture cleanup 后置。 |
| 028-16-F1/A11 | implementation-first closure，partial | normalization title/location/genre/raw category/retry-watchdog P1/P2 已补 focused tests；live queue/ES/cleanup smoke 后置。 |
| 028-16-A12 | focused implementation closure，partial | title bracket cleanup、raw area alias/composite、genre synonym 和单字脏 token 防护已补；live normalize smoke 后置。 |
| 028-16-A13 | post-reboot repair closure，partial | CharTable 标准化入口已接回并恢复 focused tests；title display mainTitle 切换仍独立后置。 |
| 028-16-F4/E | no-real-cost closure，partial | LLM cleaning 幂等、fake path、credential 脱敏和 no-cost tests 已补；真实 LLM、成本和 K8S dry-run smoke 后置。 |
| 028-16-F5/C1 | readiness implementation closure，partial | catalog/content/task gate health、catalog seed gate 和 UUID/AsyncLogger 决策已落地；resolver/player/真实资源站 live smoke 后置。 |
| 028-16-F7/028-12-D | audit implementation closure，partial | notification/task/search/scraper/resource 已通过 UTB-03；search raw/unified sync consumer 与 scraper manual/refetch runner audit 已补；metadata/normalization/storage live queue ops query 后置。 |

当前测试债务集中到统一测试窗口，而不是散落在各切片里重复执行；后续关闭任务时必须引用
`UTB-02816-P1P2-*` 或对应 live smoke 的实际结果，不能只引用本轮实现完成记录。

## 本轮完成项

| 项 | 状态 | 入账结论 |
| --- | --- | --- |
| 028-06-I RT provider ARM64 live rollout + full queue parity | 完成 | metadata-worker live digest `sha256:93db92be56a2a7dc0b85d84c7a43ecb8c8f854f5947f1bbd7a6ae2dafc34291a`，`q.fetch.metadata.rt consumers=1`，full queue `MARK 6/6`，外网请求数 `1`，`APP_METADATA_RT_ENABLED=false` 已恢复，queue/DB/ES/port-forward 清理完成。 |
| 028-06 real provider live parity parent | 完成 | provider enable gate、TMDB direct/live queue、magnet YTS_BZ、Douban first slice/full queue、RT first slice/full queue 全部闭环，父任务关闭。 |
| 028-10-K notification retention Harbor/DB smoke | 完成 | notification-worker image digest `sha256:79462463933647277e6069c61d0081e75ed241ccdff94f264d311d6ee164027b`；028-10-J dev DB retention smoke 通过，旧 fixture 删除、新 fixture 保留后清理，SMTP 未发送。 |
| 028-10-M notification-worker CrashLoop recovery | 完成 | 曾出现 `CredentialServiceMailCredentialRepository: No default constructor found` CrashLoop；后续只读核查已确认 deployment Ready 1/1、pod Running、restarts=0、日志正常 Started，并已被 028-10-L SMTP smoke 覆盖。 |
| 028-10-L-retry / 028-10-L V1 SMTP real one-shot | 完成 | trace `02810l-smtp-smoke-20260609222338`；真实发送 1 封且用户确认收到，Rabbit `routed=true`，DB `status=sent`，ops endpoint `status=SENT`，recipient 脱敏为 `HuaWai System <hu***@gmail.com>`；开关恢复，deployment ready 1/1，无 Job/Pod/port-forward 残留。 |
| 028-10-N notification maintenance runner gate | 完成 | notification-worker send-history cleanup runner 已补默认关闭、dry-run 默认、真实执行双开关、batch/rate limit、worker-job audit；dev smoke `MARK 6/6`，dry-run 不删除、execute 只删除旧 fixture，ops audit 可查，SMTP/mail queue 均未触发。 |
| 028-16-C1 full readiness / auto-start gate first closure | 完成 | shared auto-start gate health detail 可观察 `INJECTED`/`DEFAULT` 来源；normalization watchdog、storage R2 watchdog、aggregation scheduler live gate 均为 `false`，三个 Deployment `readyReplicas=1`；C1 父任务不完全关闭。 |
| 028-16-C1-S2c readiness/initialization remaining first closure | 完成实现，live 后置 | catalog-service 已复用 V1 countries/languages 全量 preset（251 countries、187 languages）；normalization-worker SystemConfig cache 增加 30s TTL missed-event fallback；resolver/真实资源站 live smoke prep 默认 dry-run。 |
| 028-16-C3-S6 shared outbound circuit breaker first slice | 完成 | shared outbound circuit breaker 覆盖 timeout、5xx、IO/open/half-open/recovery；`InternalContentClients#refetchRawVideo` 已走 shared `INTERNAL_SERVICE`；focused 与组合 tests 全过；live smoke 未执行。 |
| 028-16-C3-S7 task-service scraper execution POST JSON caller | 完成 | `ScraperTaskExecutionClient` 已走 shared `INTERNAL_SERVICE` POST JSON caller；fake transport 覆盖 POST body、2xx response、4xx/5xx、timeout、connection refused、open circuit fast fail；focused tests 全过；live smoke 未执行。 |
| 028-16-C3-S8 identity avatar storage multipart caller | 完成 | `RestClientAvatarStorageClient` 已走 shared `INTERNAL_SERVICE` multipart POST caller；fake transport 覆盖 multipart body、2xx/empty response、503、IO、invalid internal URL 和 open circuit fast fail；focused tests 通过；live/full tests 统一后置。 |
| 028-16-C3-S9 BFF shared transport method/body/streaming | 完成实现，统一测试后置 | admin/portal `BffProxyClient` 已走 shared `BFF_PROXY` streaming adapter；保留 route/query/header/status/body streaming，支持任意 method/body；新增 `APP_BFF_PROXY_*` timeout/circuit/caller-key ENV；focused shared/admin-bff/portal-bff tests 通过；live/full tests 统一后置。 |
| 028-16-C3-S10 caller-specific circuit ENV matrix | 完成实现，统一测试后置 | shared outbound 支持 caller-specific ENV/K8S override 与 `IRCS_OUTBOUND_CIRCUIT_*` fallback；content/task/identity 使用 caller-aware config，admin/portal BFF YAML 支持 service-level + caller fallback；shared 定向测试和 5 个 service compile 通过。 |
| 028-16-D3 Tika MIME / file parsing first closure | 完成 | V1 Tika usage 已对照，V3 storage/scraper deps 保持 minimal；新增 `FileNormalizationServiceTest`、`ScraperCharsetDetectorTest` 与 `scripts/smoke/tika-file-parsing-02816d-smoke.ps1`；小 fixture 生成、定向 tests、fixture cleanup 均通过，`MARK 6/6`。 |
| 028-16-D4/F3 Tika/scraper golden P1/P2 implementation closure | partial | `fetchDetail()` 走 raw bytes charset detector，`fetchListPage()` 输出 `totalPages/totalItems`，SSE/task logs 可见 pagination；Big5/Shift_JIS/BOM/invalid bytes/detail fixture 和 smoke prep 已补；focused scraper tests 通过；真实 provider live charset smoke 统一后置。 |
| 028-16-E LLM cleaning gated parity | partial | normalization-worker 已补默认关闭 gate、默认 `dry-run` mode、默认关闭 runner、fake deterministic client、credential-service/runtime credential path、候选 touch、四类 raw dictionary match/noise side effect 与固定错误语义；`.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test` 通过；真实 LLM/live smoke 统一测试后置。 |
| 028-12-C/D worker/scheduler/runner audit expansion | partial | metadata/normalization/storage/search/scraper 高价值路径已补 `worker_job_audit_events`；`UTB-02816-P1P2-03` 中 notification/task/search/scraper/resource 通过，metadata/normalization/storage live queue ops query 后置。 |
| 028-16-F1/A11 normalization golden P1/P2 closure | partial | `RawVideoTextNormalizer` / `RawRelationAliasPolicy` / `NormalizationRetryWatchdog` 已补 title release/S02/Part/Vol、location hint、genre synonyms、raw category fallback、PROCESSING reset、queue depth guard、adaptive batch min/max 和固定 failure reason log；`.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks` 通过，69 tests；live smoke 未跑。 |
| 028-16-A12 normalization title/raw dictionary closure | partial | `RawVideoTextNormalizer` / `RawRelationAliasPolicy` 已补 bracket 噪音清理、area alias/composite、genre synonym 与 suffix 防脏 token；`.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks` 通过，71 tests；live smoke 未跑。 |
| 028-16-F4/E LLM cleaning no-cost closure | partial | `applyMatch/applyNoise` 不覆盖人工映射，fake path 不解析 credential、不调用 live client，`LlmCredential.toString()` 脱敏；LLM focused 和 normalization-worker tests 通过；真实 LLM/live/cost 测试未跑。 |
| 028-16-F5/C1 readiness gate health slice | partial | catalog/content/task 新增 auto-start gate health detail，catalog seed gate 可关闭且默认保持开启；UUID 与 AsyncLogger/Disruptor 决策已记录；focused catalog/content/task tests 通过；live readiness smoke 未跑。 |
| 028-16-F7/028-12-D worker audit second slice | partial | search-service raw/unified sync consumer 与 scraper-service collection/refetch runner 写 worker-job audit；focused tests search=2、scraper=4 通过；UTB-03 已补 notification/task/search/scraper/resource 证据；metadata/normalization/storage live queue ops query 未跑。 |
| C2 SystemConfig cache/event parity | 完成 | C2-S17/S19/S20 真实 Rabbit publish/deliver/ack 与 content DB fallback read probe 完成，C2-S21 TTL fallback 决策和最小验证完成；保持完成状态。 |
| 028-07 R2 live/watchdog parity | 完成 | credential/object/cover/avatar/bucket guard/watchdog recovery 全部完成；保持完成状态。 |

## 旧阻塞状态修正

| 旧表述 | 最新修正 |
| --- | --- |
| RT provider 剩余 live image rollout/full queue parity | 已由 `028-06-I` 完成，028-06 父任务关闭。 |
| SMTP one-shot 仍待复跑 | 已由 `028-10-L` 完成真实 one-shot，发送 1 封并经 send history、ops endpoint、用户收件箱确认。 |
| notification-worker CrashLoop 阻塞 | 已恢复 Ready 1/1，restarts=0，日志正常 Started；后续 028-10-L SMTP smoke 已覆盖恢复后链路。 |
| C2 仍有 Rabbit outage TTL fallback gap | 已由 C2-S21 关闭，C2 父任务完成。 |

## 真实 smoke 证据摘要

```text
028-06-I:
metadata-worker digest=sha256:93db92be56a2a7dc0b85d84c7a43ecb8c8f854f5947f1bbd7a6ae2dafc34291a
q.fetch.metadata.rt consumers=1
MARK 6/6
externalRequestCount=1
APP_METADATA_RT_ENABLED=false restored
queue/DB/ES/port-forward cleanup passed

028-10-K:
notification-worker digest=sha256:79462463933647277e6069c61d0081e75ed241ccdff94f264d311d6ee164027b
retention cleanup deleted old fixture
fresh fixture retained then cleaned
SMTP sent=false
final DB residue=0

028-10-M:
previous error=CredentialServiceMailCredentialRepository: No default constructor found
deployment READY=1/1
pod phase=Running
restarts=0
logs include Started

028-10-L:
trace=02810l-smtp-smoke-20260609222338
real email sent=1
user inbox confirmed=true
Rabbit routed=true
DB status=sent
ops endpoint status=SENT
recipient=HuaWai System <hu***@gmail.com>
deployment ready=1/1
Job/Pod/port-forward residue=0

028-10-N:
notification-worker digest=sha256:4cff0405ff615adfd29fe9847518208a9320b0f3522afa0021ceaeef55ada6cc
enabled default=false
dryRun default=true
real delete requires DRY_RUN=false and EXECUTE_ENABLED=true
worker_job_audit_events written=true
dev smoke=PASS
sent=false
mailQueuePublished=false
finalSmokeResidue=0

C1 first closure:
normalization watchdog gate=false
storage R2 watchdog gate=false
aggregation scheduler gate=false
deployment readyReplicas=1 for normalization/storage/aggregation

C3-S6:
shared outbound circuit breaker first slice=done
InternalContentClients refetchRawVideo uses shared INTERNAL_SERVICE
tests=PASS
live smoke=not executed

C3-S7:
ScraperTaskExecutionClient task-executions POST JSON uses shared INTERNAL_SERVICE
POST body/2xx response/4xx/5xx/timeout/connection refused/open circuit tests=PASS
live smoke=not executed, no confirmed dev-safe dry-run task input

C3-S8:
RestClientAvatarStorageClient avatar multipart upload uses shared INTERNAL_SERVICE
multipart file field/filename/content type/body/2xx response/empty response/503/IO/invalid URL/open circuit tests=PASS
test command=.\gradlew.bat --no-daemon :services:ircs-identity-service:test --tests "com.prodigalgal.ircs.identity.RestClientAvatarStorageClientTest" --tests "com.prodigalgal.ircs.identity.RestClientAvatarStorageClientContextTest" --rerun-tasks
live/full tests=deferred to unified testing

C3-S9:
admin/portal BffProxyClient uses shared BFF_PROXY streaming adapter
method/body/header/status/response streaming tests=PASS
APP_BFF_PROXY_CONNECT_TIMEOUT/REQUEST_TIMEOUT/CIRCUIT_KEY/CIRCUIT_* env override entries added, with IRCS_OUTBOUND_CIRCUIT_* fallback
test command=.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-admin-bff:test :services:ircs-portal-bff:test --rerun-tasks
live/full tests=deferred to unified testing

C3-S10:
caller-specific circuit ENV matrix=done
keys=content-refetch-scraper, task-scraper-execution, identity-avatar-storage, admin-bff-proxy, portal-bff-proxy
env prefixes=IRCS_OUTBOUND_CIRCUIT_<CALLER>_*
global fallback=IRCS_OUTBOUND_CIRCUIT_*
light tests=PASS
service compile=PASS
live/K8S/full tests=deferred to unified testing

028-16-D3:
V1 usage points=ImageSecurityValidatorImpl Tika.detect + ListScraperServiceImpl UniversalEncodingDetector
V1 generic text extraction parser usage=not found
V3 dependency boundary=storage tika-core only; scraper tika-core + tika-parsers-standard-package as of 028-20-11A
fixture smoke=MARK 6/6
fixture files=valid.png, spoof-html.png, renamed.pdf.png, gb18030-list.json, empty.bin
fixture cleanup=verified
tests=PASS

028-16-D4/F3:
detail charset=raw bytes + ScraperCharsetDetector + declared Content-Type charset
pagination output=ListPage(items,totalPages,totalItems) plus SSE/task execution logs
fixtures=GB18030 list, Big5 list/detail, Shift_JIS list, UTF-8 BOM pagination, invalid bytes
parser package decision=028-20-11A superseded the old non-goal; scraper-service uses tika-parsers-standard-package, storage remains core-only
test command=.\gradlew.bat --no-daemon :services:ircs-scraper-service:test --tests "*ListScraperClientTest" --tests "*ScraperCharsetDetectorTest" --tests "*ManualScraperServiceTaskExecutionTest" --rerun-tasks
tests=PASS
live provider charset smoke=deferred to UTB-02816-P1P2-01

C2:
Rabbit publish/deliver/ack verified
content DB fallback read probe verified
missed-message strategy=event evict + short TTL read-through
parent=done
```

## K8S 资源与污染状态

- HTTPRoute：本轮入账不新增公共 HTTPRoute。
- Rabbit：`q.fetch.metadata.rt consumers=1`，RT full queue smoke 后目标队列和 DLQ 清理完成；
  028-10-L Rabbit routed=true 且无残留 Job/Pod/port-forward。
- DB/ES/R2/local：028-06-I queue/DB/ES 清理完成；028-10-J/K retention fixture 清理完成；
  028-10-L send history 保留真实 sent 证据，临时运行资源清理完成。
- notification-worker：CrashLoop 已恢复，deployment Ready 1/1，pod Running，restarts=0。

## 父任务状态

| 父任务 | 最新状态 | 说明 |
| --- | --- | --- |
| 028-06 real provider live parity | 完成 | TMDB、magnet YTS_BZ、Douban、RT 全部完成，父任务已在 `tasks/done/`。 |
| 028-07 R2 live/watchdog parity | 完成 | 父任务已在 `tasks/done/`。 |
| 028-10 production governor/credential pool parity | 进行中 | notification send history、retention、worker Ready 恢复、V1 SMTP one-shot 与 028-10-N maintenance runner safety gate 已完成；后续只保留其他高风险 runner / production-like governor 扩展。 |
| 028-16-C1 initialization readiness gate | 进行中 | first closure 已完成；cache warmup/TTL/evict first closure 与 catalog countries/languages 全量 preset 已完成实现；剩余 resolver/真实资源站 live smoke，以及如需 V1 式 `RabbitListenerEndpointRegistry.start()` 自动放行。 |
| 028-16-C2 SystemConfig cache/event parity | 完成 | listener/publisher live、C2-S17 Rabbit smoke、content read probe 与 missed-message TTL fallback 均已闭环。 |
| 028-16-C3 shared outbound HTTP client parity | 进行中 | C3-S6 circuit breaker first slice、content internal caller adoption、C3-S7 task-service POST JSON adoption、C3-S8 identity multipart adoption、C3-S9 BFF shared streaming transport adoption、C3-S10 caller-specific circuit ENV matrix 已完成；storage strict adapter 已由并行 worker 完成；剩余统一测试、dev-safe BFF smoke 和 K8S override/open-circuit matrix。 |
| 028-16-D Tika file parsing/security parity | implementation closure partial | D1/D2/D3 已覆盖 storage MIME、scraper list charset/streaming、本地 fixture smoke 与 cleanup；D4/F3 已补 detail charset、pagination 输出和 Big5/Shift_JIS/BOM/invalid bytes fixture；剩余真实 provider live charset smoke、完整统一测试，及后续如发现 V1 PDF/Office/text extraction 调用再接 parser package。 |
| 028-16-E LLM cleaning gated parity | partial | implementation-first closure `MARK 5/6`；fake/no-cost、dry-run、credential path 和 focused tests 完成，staging-only live 单条留到统一测试窗口。 |
| 028-12 service/worker audit expansion | 进行中 | HTTP service handler audit 和首批 worker audit 已完成；028-12-C 覆盖 metadata/normalization/storage，028-12-D 覆盖 search/scraper；live ops query 与 fixture cleanup 统一测试后置。 |
| 028 V1/V3 behavior parity parent | 进行中 | 整体 goal 未完成，剩余深层 parity 继续推进。 |

## 剩余 Top 10

1. `028-12`：`028-12-C/D` 已完成更多 worker/scheduler/runner audit implementation first closure，
   已补 metadata、normalization、storage、search、scraper 高价值路径；ops query fixture cleanup 与 live smoke 后置统一测试。
2. `028-10`：在 028-10-N 已关闭后，只保留其他高风险 runner / production-like governor 扩展；不得把已完成的 send-history cleanup gate 重新列为剩余。
3. `028-16-C1`：resolver / 真实资源站 live smoke 仍后置到统一测试；V1 式全服务自动放行、time-based UUID 和 AsyncLogger/Disruptor 已记录为不在本切片恢复，后续如确需再独立拆实现。
4. `028-16-C1`：cache warmup/TTL/evict 已完成 first closure，但全服务 live matrix 和长期运行观察仍需统一测试覆盖。
5. `028-16-C1`：catalog countries/languages 全量 preset 已实现，后续只需 empty dev DB/live smoke 复验。
6. `028-16-C3`：C3-S10 已补 caller-specific circuit ENV matrix；C3-S6/S7/S8/S9/S10 live/full/K8S matrix tests 仍待统一测试窗口。
7. `028-16-C3`：做 identity/storage/BFF/shared outbound 组合回归，覆盖 avatar internal upload、storage strict adapter、BFF target validation、BFF streaming path 和 open circuit/caller key matrix。
8. `028-16-A/F1`：title/location/genre/retry-watchdog、A12 title/raw dictionary 与 A13 CharTable repair 已完成本地 implementation closure；剩余为
   live normalize smoke、ES residue、真实老数据重洗矩阵、title display mainTitle 切换和更激进的 full V1 NLP/fuzzy alias 可选深化。
9. `028-16-D/E/F`：D 已完成 Tika/scraper implementation closure partial，E 已完成 implementation-first partial；后续剩余 D 的真实 provider live charset smoke、完整 `UTB-02816-P1P2-01`、E 的 staging-only live 单条和其他 F backlog 统一测试。
10. 生产化收敛：补 staging/production-like smoke、配额/资源预算、回滚手册和长期运行告警口径。

## 028-16-F P1/P2 backlog 入账

2026-06-10 账本治理口径：当前先继续推进实现完整度，不对每个 backlog 逐一即时测试；测试、smoke
和资源后置检查在统一批次集中执行。089 inventory 中仍未关闭的 P1/P2 已转为以下 backlog：

| 分组 | Backlog | 边界 |
| --- | --- | --- |
| normalization | `tasks/backlog/028-16-f1-normalization-golden-p1p2-backlog.md` | F1/A11 已完成 title release/S02/Part/Vol、location NLP first closure、genre NLP first closure、raw category fallback、retry/watchdog PROCESSING reset + queue depth guard + adaptive batch min/max；CharTable ASCII case 仍按 V3 improved replacement 保留，live normalize smoke 后置 |
| aggregation | `tasks/backlog/028-16-f2-aggregation-golden-p1p2-backlog.md` | local closure 已补结构化 search sync、malformed reject、pipeline failure raw PENDING/unified DIRTY、recalculate 成功才 SYNCED；真实 R2 cover promote、多 context victim merge、持久 search outbox/stats 与 residue 检查后置 |
| Tika/scraper | `tasks/backlog/028-16-f3-tika-scraper-golden-p1p2-backlog.md` | D4 已补 detail charset、pagination、Big5/Shift_JIS/BOM/invalid bytes fixture；`028-20-11A` 已把 scraper-service 切回 parser standard package，storage 仍 core-only；UTB-01 D3-D4 local fixture smoke 已通过，真实 provider charset smoke 后置 |
| LLM cleaning | `tasks/backlog/028-16-f4-llm-cleaning-golden-p1p2-backlog.md` | no-real-cost closure 已补幂等护栏、fake path 无凭据/无 live client、credential 脱敏；真实单条 smoke、成本和 K8S dry-run 后置 |
| C1 readiness | `tasks/in-progress/028-16-f5-c1-readiness-golden-p1p2.md` | cache/full preset 已完成 first closure；catalog/content/task gate health、catalog seed gate、UUID/async logging 决策已完成；resolver/player live smoke 后置 |
| C3 outbound adoption | `tasks/backlog/028-16-f6-c3-outbound-adoption-golden-p1p2-backlog.md` | identity multipart、storage strict adapter、BFF shared streaming transport 与 caller-specific circuit ENV matrix 已完成实现；剩余 unified tests、dev-safe BFF smoke 和 K8S override/open-circuit matrix |
| worker/scheduler audit | `tasks/backlog/028-16-f7-worker-scheduler-audit-golden-p1p2-backlog.md` | 028-12-C/D partial：metadata/normalization/storage/search/scraper 高价值路径 implementation first closure；ops query 和 fixture cleanup 后置 |
| production-like smoke | `tasks/backlog/028-16-f8-production-like-smoke-golden-p1p2-backlog.md` | 统一测试批次、CQEngine/RxJava/MapStruct/JPA 裁剪证据、真实外部依赖门禁、后置检查 |

统一测试批次建议：

```text
UTB-02816-P1P2-01 normalization + aggregation + Tika/scraper
UTB-02816-P1P2-02 LLM fake/no-cost + C1 readiness + C3 outbound adoption
UTB-02816-P1P2-03 worker/scheduler audit + production-like cleanup/resource checks
```

这些 backlog 不代表新增业务实现已完成；它们只把 089/093 的模糊剩余缺口转成后续可分配、
可验收、可统一测试的任务边界。

## 028-19 统一测试入口

2026-06-10 追加统一测试 / production-like smoke orchestrator prep。入口：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1
```

默认不执行真实测试，只输出 plan。后续统一测试阶段按需选择批次：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-01 -Execute
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-02 -Execute
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-03 -Execute
```

外部副作用批次保留二次门禁：

```powershell
# 只在统一 live 窗口确认后使用对应 allow flag
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch EXTERNAL-PROVIDER-SMTP-R2 -Execute -AllowProviderExternal
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch EXTERNAL-PROVIDER-SMTP-R2 -Execute -AllowSmtpSend
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch EXTERNAL-PROVIDER-SMTP-R2 -Execute -AllowR2Write
```

批次和预算详见 `docs/reports/106-02819-production-like-smoke-orchestrator-prep.md`。

2026-06-11 追加执行证据：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-02 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\utb-02816-p1p2-02-20260611.json
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-03 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\utb-02816-p1p2-03-20260611.json
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch RESOURCE-02818 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\resource-02818-after-utb02-20260611.json
```

结果：

```text
UTB-02816-P1P2-02:
  LLM cleaning fake/no-cost focused regression: PASSED
  C1 gate health focused regression: PASSED
  C1 resolver/resource prep dry-run: PASSED
  C2 config event matrix dry-run guard: PASSED
  C3 live/full transport: MANUAL/deferred

RESOURCE-02818:
  status: PASSED
  HTTPRoute count: 0
  ResourceQuota: pods 28/30, services 24/26
  dangerous gates: dev-safe defaults retained

UTB-02816-P1P2-03:
  rerun after DB migrator drift fix:
    notification worker audit smoke: PASSED
    task service audit smoke: PASSED
    search worker audit focused regression: PASSED
    scraper worker audit focused regression: PASSED
    search service audit smoke: PASSED
    scraper service audit smoke: PASSED dev-safe dry-run
    resource rollback verifier: PASSED

  earlier partial run:
    notification worker audit smoke: FAILED, MAIL_QUEUE_NOT_EMPTY, q.notification.mail messages=1 consumers=0

UTB-02816-P1P2-01:
  rerun after A9 smoke harness and cover upload test fixture fixes:
    normalization A9 title/location/genre closure: PASSED
    normalization A12 title/raw dictionary focused regression: PASSED
    aggregation pipeline runtime B15: PASSED
    Tika/scraper D3-D4 local fixture smoke: PASSED
    outputJsonPath: build\tmp\utb-02816-p1p2-01-rerun-20260612085514.json
    RESOURCE-02818 post-check: PASSED
```

本次未使用 `-AllowProviderExternal`、`-AllowSmtpSend` 或 `-AllowR2Write`，因此没有真实
provider、SMTP 或 R2 副作用；真实 LLM 单条、C3 dev-safe live/K8S circuit matrix 仍未关闭。
`production-like-smoke-orchestrator-02819.ps1` 已修复非 0 exit code 误标 `PASSED` 的问题；
notification worker audit wrapper 会在 dev smoke 前临时打开 listener gate 并恢复。

DB 取证与 migrator 入账：

```text
ircs-migrator imageDigest=sha256:8e6305b43ca6f1ae0911a333f8e412307cda099c571e94a72cf6038a04b41da0
migrator Run=10, Total change sets=137
request_audit_logs.audit_class / worker_job_audit_events.audit_class / notification_mail_send_history.audit_class live schema 已补齐
audit_es_replication_outbox / audit_archive_entries live schema 已存在
```
 
## 统一测试后置清单
 
```text
UTB-02816-P1P2-01:
  done: normalization A9 live smoke with DB/queue cleanup
  done: normalization A12 focused regression
  done: aggregation B15 runtime smoke with DB/ES/Rabbit/HTTPRoute/port-forward cleanup
  done: Tika/scraper D3-D4 local fixture smoke without -SkipGradle
  remaining: Tika/scraper real provider low-resource charset smoke after explicit external window approval
  remaining: aggregation real R2 cover promote and multi-context production-like fixtures outside default UTB-01

UTB-02816-P1P2-02:
  LLM fake/no-cost focused regression done; staging-only live single fixture remains if approved
  C1 gate health focused regression and resolver/resource dry-run done; full preset live verification and resolver/player/real-source smoke remain
  C3 identity/storage/BFF/shared outbound live/full combination remains,
  caller key / circuit ENV/K8S matrix and live-safe avatar chain remain

UTB-02816-P1P2-03:
  notification/task/search/scraper/resource checks done
  metadata/normalization/storage worker audit live queue smoke and ops query fixture cleanup remain
```

明确不在本轮视为闭环的测试债务：

- 未执行真实 LLM 调用。
- 未执行 portal/admin 真实页面访问或 BFF live streaming path。
- 未执行 resolver/player/真实资源站 live smoke。
- metadata/normalization/storage live queue smoke 与 ops query fixture cleanup 未关闭。
- 未执行 aggregation F2 live/R2 smoke；持久 search outbox/relay/stats processor 仍需后续独立任务确认数据契约。
- 028-19 已执行 `UTB-02816-P1P2-01/02/03`；`UTB-02816-P1P2-02` 的 C3 live/full、真实 LLM 单条仍后置，真实 provider charset/SMTP/R2 仍需独立 allow flag。
- 未执行全量 Gradle suite 或 production-like 大规模 smoke。

## 旧后置清单快照

```text
UTB-02816-P1P2-03 earlier partial:
  task service audit smoke: PASSED
  search worker audit focused regression: PASSED
  scraper worker audit focused regression: PASSED
  search service audit smoke: PASSED
  scraper service audit smoke: PASSED dev-safe dry-run
  resource rollback verifier: PASSED
  notification worker audit smoke: FAILED, MAIL_QUEUE_NOT_EMPTY, q.notification.mail messages=1 consumers=0
```

## 本轮入账动作

- 更新本总账报告，替换旧的 RT rollout / SMTP one-shot / CrashLoop 阻塞表述，并新增 028-10-N、C1 first closure、C3-S6、C3-S7、C3-S8、C3-S9、028-16-D3 入账。
- 追加 028-16-E LLM cleaning gated parity implementation-first closure：默认关闭、dry-run/fake path、credential-service/runtime credential path、固定错误语义和 normalization-worker focused tests 已入账；真实 LLM/live smoke 后置。
- 修正 `docs/index.md` 与 `tasks/index.md` 中 028-10、C1、C3、C2、028-07 的状态摘要。
- 回写 `028` 父任务、`028-10` 父任务、`028-16-C1/C3` 父任务中的旧剩余项，保持 in-progress/done 不冲突。
- 追加 028-16-F P1/P2 backlog 入账：8 个 backlog task spec 已创建，统一测试后置策略和 `UTB-02816-P1P2-01/02/03` 批次边界已明确。
- 追加 028-16-F2/B15 aggregation local closure：结构化 search sync、malformed reject、pipeline failure dirty lifecycle 和 recalculate guard 已完成 focused tests；live/R2/full smoke 后置。
- 追加 028-16-D4/F3 Tika/scraper local closure：detail charset、pagination 输出、Big5/Shift_JIS/BOM/invalid bytes fixture、smoke prep 和 focused tests 已完成；真实 provider charset live smoke 后置。
- 追加 028-19 入账：`scripts/smoke/production-like-smoke-orchestrator-02819.ps1`
  默认 dry-run / plan-only，统一列出 UTB、resource verifier、portal/admin/BFF、provider/SMTP/R2
  smoke 入口；真实执行必须显式 `-Execute`，本轮未执行 live。
- 追加 028-16-A12/A13 入账：title bracket 噪音清理、raw area alias/composite、genre synonym 与脏 token 防护已完成 focused tests；CharTable 标准化入口已接回；title display mainTitle 与 live smoke 后置。
- 追加 028-16-F4/E no-real-cost 入账：LLM cleaning 幂等、fake path、credential 脱敏和 no-cost tests 已完成；真实 LLM/live/cost 后置。
- 追加 028-16-F5/C1 入账：catalog/content/task gate health、catalog seed gate 与 UUID/AsyncLogger 决策已完成；resolver/player live smoke 后置。
- 追加 2026-06-11 UTB-02816-P1P2-02 执行入账：LLM fake/no-cost、C1 gate health、resolver/resource dry-run、C2 config event dry-run guard 通过；RESOURCE-02818 后置检查通过；C3 live/full、真实 LLM 和外部依赖仍后置。
- 追加 2026-06-11 UTB-02816-P1P2-03 入账：先发现 notification 队列/gate 与 DB schema 漂移问题，修复 migrator/notification wrapper 后重跑 7/7 PASSED；orchestrator 非 0 exit code 假绿已修复。
- 追加 028-16-F7/028-12-D 入账：search/scraper worker-job audit 第二切片已完成 focused tests，UTB-03 已补 notification/task/search/scraper/resource 证据；metadata/normalization/storage live queue ops query 后置。
- 追加 2026-06-12 UTB-02816-P1P2-01 入账：修复 A9 smoke harness 与 cover upload size-limit test fixture 后，normalization A9/A12、aggregation B15、Tika/scraper D3-D4 local fixture smoke 全部 PASSED；RESOURCE-02818 后置检查 PASSED。
