# 028-16-F3 Tika/scraper golden P1/P2 backlog

状态：backlog

父任务：

- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-d-tika-file-parsing-security-parity.md`

## 背景

D1-D3 已完成 storage Tika MIME、scraper list charset/streaming 与本地小 fixture smoke。089/093
仍保留 P1/P2：detail response charset、pagination 输出、Big5/更多编码、真实 provider live charset
smoke，以及 V1 build 中存在但源码未使用的通用 parser package 裁剪决策。

## 目标

- 补齐 list/detail/pagination 的 charset 与 streaming parser golden fixture。
- 增加 Big5、GB18030、UTF-8 BOM、invalid bytes、numeric/raw nested value 的覆盖边界。
- 明确 Tika parser standard package 是否继续作为 explicit non-goal；该历史问题已由 `028-20-11A` 取代为 scraper-service 接回、storage core-only。
- 为真实 provider charset live smoke 准备安全、可清理的 datasource 输入。

## 非目标

- 不新增 PDF/Office/text extraction 功能，除非后续发现 V1 实际源码调用点。
- 不扩大 storage-service Tika 依赖到 parser standard package。
- 不在本任务中触发真实抓取或公网请求。

## 影响范围

```text
services/ircs-scraper-service/**
services/ircs-storage-service/**
docs/reports/089-v1-golden-behavior-inventory*.md
docs/reports/093-v1-v3-parity-progress-ledger.md
tasks/in-progress/028-16-d*.md
```

## 依赖

- D1-D3 已完成的 minimal Tika dependency boundary。
- scraper private datasource URL、header/proxy/auth 与 shared outbound PUBLIC_FETCH 现有实现。
- 后续统一测试窗口允许执行 controlled live provider smoke。

## 风险

- 编码探测与 streaming parser 变更可能影响 manual scrape、pagination 和错误语义。
- 真实 provider live smoke 可能受网络、站点反爬或字符集漂移影响。
- 依赖扩张会增加镜像体积，必须保持 service-scoped。

## 验收标准

```text
MARK 1/6 detail charset fixture matrix completed
MARK 2/6 pagination output and streaming parser fixture matrix completed
MARK 3/6 Big5/BOM/invalid bytes behavior covered or explicitly deferred
MARK 4/6 Tika parser package decision recorded
MARK 5/6 focused scraper/storage tests pass
MARK 6/6 real provider charset smoke passes or records external blocker with cleanup
```

## 统一测试边界

纳入统一测试批次 `UTB-02816-P1P2-01`。统一执行 scraper/storage focused tests、本地 fixture
create/cleanup smoke、受控 provider charset live smoke、DB/queue residue 检查和 HTTPRoute/ResourceQuota
后置检查。

## 2026-06-10 D4 implementation closure spec

状态：in-progress，按“实现优先、统一测试后置”推进。

目标：

- `fetchDetail()` 复用 scraper charset detector，避免 detail response 固定 UTF-8 导致 Big5/Shift_JIS/GB18030
  详情 JSON mojibake。
- `fetchList()` 保持现有调用兼容，同时新增 list page result 承接 V1
  `totalPages` / `totalItems` pagination 输出。
- 增加 Big5、Shift_JIS、UTF-8 BOM、invalid bytes 和 pagination fixture，覆盖本地 no-network golden matrix。
- 确认 storage MIME mismatch、empty、oversize guard 已由 D1/D3 覆盖；本轮仅在缺口存在时小修。
- 为统一测试准备 `UTB-02816-P1P2-01` 的 Tika/scraper smoke 参数，不执行真实外网。

非目标：

- 不做真实 provider/live smoke，不新增公网 datasource 请求。
- 历史 D4 非目标曾是不接 `tika-parsers-standard-package`；该依赖边界已被 `028-20-11A` 取代。仍不新增 PDF/Office/text extraction。
- 不修改 normalization、aggregation、notification、C3 outbound 或 metadata provider。

影响文件：

```text
services/ircs-scraper-service/src/main/java/com/prodigalgal/ircs/scraper/ListScraperClient.java
services/ircs-scraper-service/src/main/java/com/prodigalgal/ircs/scraper/ManualScraperService.java
services/ircs-scraper-service/src/main/java/com/prodigalgal/ircs/scraper/ScraperDtos.java
services/ircs-scraper-service/src/test/java/com/prodigalgal/ircs/scraper/ListScraperClientTest.java
services/ircs-scraper-service/src/test/java/com/prodigalgal/ircs/scraper/ScraperCharsetDetectorTest.java
scripts/smoke/tika-file-parsing-02816d-smoke.ps1
docs/reports/089-v1-golden-behavior-inventory*.md
docs/reports/093-v1-v3-parity-progress-ledger.md
tasks/**
```

验收标准：

```text
D4 MARK 1/6 detail charset fixture matrix completed
D4 MARK 2/6 pagination totalPages/totalItems parsed and surfaced without breaking fetchList callers
D4 MARK 3/6 Big5/Shift_JIS/BOM/invalid bytes behavior covered locally
D4 MARK 4/6 parser standard package decision superseded by `028-20-11A`
D4 MARK 5/6 focused scraper tests pass or deferred reason recorded
D4 MARK 6/6 F3/D/089/093/unified test ledger updated
```

测试方式：

```powershell
.\gradlew.bat --no-daemon :services:ircs-scraper-service:test --tests "*ListScraperClientTest" --tests "*ScraperCharsetDetectorTest" --tests "*ManualScraperServiceTaskExecutionTest" --rerun-tasks
```

统一测试后置：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-01
```

## 2026-06-10 D4 implementation closure result

状态：partial，D4 implementation closure 已完成；真实 provider live charset smoke 后置到统一测试。

实现结果：

- `fetchDetail()` 已改为读取 raw bytes 后走 `ScraperCharsetDetector`，支持 response
  `Content-Type` charset、Tika detector、UTF-8 fallback 和 GB18030 fallback；短 JSON 的 Big5/
  Shift_JIS fixture 通过 header charset 稳定解码。
- `fetchList()` 保持旧签名返回 `List<ListItem>`；新增 `fetchListPage()` 返回 `ListPage(items,
  totalPages,totalItems)`，复刻 V1 `ScrapeListResult` pagination contract。
- `ManualScraperService` 在 SSE/task execution logs 中输出 `pagination totalPages/totalItems`，
  对外可见但不改变 ingestion item contract。
- `ListScraperClientTest` 覆盖 GB18030、Big5、Shift_JIS、UTF-8 BOM pagination、invalid bytes、
  nested raw value 和 detail Big5 charset。
- `ScraperCharsetDetectorTest` 覆盖 UTF-8、GB18030 HTML、Big5 HTML、Shift_JIS HTML、
  declared `Content-Type charset`、UTF-8 BOM、invalid bytes 和 empty fallback。
- `scripts/smoke/tika-file-parsing-02816d-smoke.ps1` 已新增 Big5 detail、Shift_JIS list 和
  UTF-8 BOM pagination 本地 fixture；`production-like-smoke-orchestrator-02819.ps1` 的
  `UTB-02816-P1P2-01` Tika step 已改名为 D3-D4 local fixture smoke。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-scraper-service:test --tests "*ListScraperClientTest" --tests "*ScraperCharsetDetectorTest" --tests "*ManualScraperServiceTaskExecutionTest" --rerun-tasks
BUILD SUCCESSFUL

powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\tika-file-parsing-02816d-smoke.ps1 -SkipGradle
MARK 1/6 dependency/V1 usage guards passed
MARK 3/6 Big5/Shift_JIS/BOM local fixtures created
MARK 4/6 focused Gradle tests skipped by -SkipGradle; fixture prep and dependency guards only
MARK 6/6 fixture directory cleaned
```

当前 D4 MARK：

```text
D4 MARK 1/6 detail charset fixture matrix completed: done
D4 MARK 2/6 pagination totalPages/totalItems parsed and surfaced without breaking fetchList callers: done
D4 MARK 3/6 Big5/Shift_JIS/BOM/invalid bytes behavior covered locally: done
D4 MARK 4/6 historical parser standard non-goal: superseded by `028-20-11A`; scraper-service now uses `tika-parsers-standard-package`, storage remains core-only
D4 MARK 5/6 focused scraper tests pass or deferred reason recorded: done
D4 MARK 6/6 F3/D/089/093/unified test ledger updated: done
```

剩余统一测试：

- 真实 provider 低资源 charset smoke 仍后置，需单条 datasource、明确 cleanup 和外网窗口批准。
- storage MIME mismatch、empty、oversize guard 已由 D1/D3 focused tests 覆盖；本轮未改 storage-service。

## 2026-06-12 UTB-01 evidence

`UTB-02816-P1P2-01` 已执行完整 D3-D4 Tika/scraper local fixture smoke，未使用 `-SkipGradle`。

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\tika-file-parsing-02816d-smoke.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-01 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\utb-02816-p1p2-01-rerun-20260612085514.json
```

结果：

```text
Tika/scraper D3-D4 local fixture smoke: PASSED
MARK 1/6 V1 Tika usage points documented
MARK 2/6 V3 dependencies service-scoped: storage core-only, scraper standard parser package
MARK 3/6 Big5 detail, Shift_JIS list, GB18030 list, UTF-8 BOM pagination, invalid/empty/spoof fixtures created
MARK 4/6 storage/scraper focused Gradle tests passed
MARK 5/6 no live network/uploads/business DB writes/public HTTPRoute
MARK 6/6 fixture directory cleaned
```

本轮还修正 `CoverImageAdminServiceTest` 的 `maxUploadBytes` 注入，并新增 oversized upload guard，
确保前期安全加固不会被测试夹具误判为回归。

剩余：真实 provider 低资源 charset smoke 仍后置，需单条 datasource、明确 cleanup 和外网窗口批准。
