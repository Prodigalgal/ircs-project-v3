# 028-16-D Tika file parsing and security parity task spec

状态：进行中

父任务：

- `tasks/in-progress/028-16-v1-v3-deep-parity-gap-audit-parent.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/088-v1-v3-deep-parity-gap-audit.md`
- `docs/migrations/088-v1-v3-deep-parity-gap-audit.md`

## 背景

V1 使用 Apache Tika 覆盖两类能力：storage image MIME 检测和 scraper/list 页面编码识别。
当前 V3 没有 Tika 依赖，storage-service 主要依赖轻量签名/扩展判断，scraper-service 未复刻
Tika encoding detector。

## 目标

- 在 storage-service service-scoped 接回 `tika-core`，复刻 V1 图片 MIME 检测。
- 在 scraper-service service-scoped 接回 Tika encoding detector 需要的最小依赖。
- 对 parser standard package 做依赖体积评估，只在 scraper-service 需要时接入。
- 增加 spoofed image、unknown bytes、GBK/UTF-8/charset fallback fixtures。

## 非目标

- 不给所有服务加 Tika。
- 不引入 PDF/Office 解析功能，除非 V1 明确路径需要。
- 不执行公网抓取。

## 输入

V1：

```text
modules/storage/service/impl/ImageSecurityValidatorImpl
modules/scraper/service/impl/ListScraperServiceImpl
build.gradle tika-core/tika-parsers-standard-package
```

V3：

```text
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/ImageSecurityValidator.java
services/ircs-scraper-service/src/main/java/com/prodigalgal/ircs/scraper/ListScraperClient.java
```

## 输出

- service-scoped Tika dependencies。
- storage MIME detection parity tests。
- scraper encoding detection parity tests。
- smoke/parser fixture 说明。

## 影响范围

```text
services/ircs-storage-service/**
services/ircs-scraper-service/**
docs/reports/088-v1-v3-deep-parity-gap-audit.md
tasks/in-progress/028-16-d-tika-file-parsing-security-parity.md
```

## 风险

- Tika parser package 依赖较重，可能影响 ARM64 image push。
- MIME 检测策略变严可能影响现有 fixture，需要明确兼容边界。
- 编码识别必须只用本地 fixture，不触发真实网站抓取。

## 验收标准

```text
MARK 1/5 storage-service has minimal Tika MIME dependency
MARK 2/5 image validator rejects spoofed/non-image bytes like V1
MARK 3/5 image validator accepts valid png/jpeg/webp fixtures
MARK 4/5 scraper-service detects GBK/UTF-8/no-charset HTML fixtures with Tika-equivalent behavior
MARK 5/5 storage/scraper tests pass without live network
```

## 测试边界

```powershell
.\gradlew.bat :services:ircs-storage-service:test :services:ircs-scraper-service:test --rerun-tasks
```

## 当前状态

2026-06-09：已完成 Tika/charset 只读盘点，确认 V1 与 V3 关键差异：

- V1 `ImageSecurityValidatorImpl` 使用 `new Tika().detect(byte[])` 做图片 MIME 判定；
  允许 `jpeg/png/webp/gif/avif/bmp/x-icon`，10MB 上限和文件名黑名单仍由 storage 链路处理，
  `declaredMimeType` 只用于日志，不作为放行依据。
- V3 `ImageSecurityValidator` 当前是手写 magic bytes 判定，allowlist 基本接近，但不是 Tika
  detect，畸形容器、spoof/polyglot 与 declared MIME 不一致场景仍可能和 V1 偏离。
- V1 `ListScraperServiceImpl` 对 list 响应用 `httpService.executeStream()`，在响应体闭包内用
  `BufferedInputStream.mark(8192)` + `UniversalEncodingDetector.detect()` 探测 charset，
  失败回退 UTF-8，再用 Jackson `JsonParser` streaming 扫描 mapping。
- V3 `ListScraperClient` 当前经 shared outbound 拿到 `OutboundHttpResponse` 后使用
  `bodyAsUtf8()` + `JsonPath.read()`，没有 Tika charset detector，也不是 streaming parser；
  GBK/Big5/no charset 和大 JSON 仍有 parity 风险。

下一刀边界：

```text
D1 storage Tika MIME:
  - 只在 ircs-storage-service 接入 tika-core。
  - 用 Tika.detect(byte[]) 封装/替换 ImageSecurityValidator 的 MIME detector。
  - 保留现有 10MB、filename blacklist、download SSRF、redirect blocked、Content-Type 前置
    allowlist 和 FileNormalizationService 边界。
  - 补有效 png/jpeg/webp/gif/avif/bmp/ico、HTML/JSON 伪装、ZIP/PDF renamed image、
    unknown bytes、空文件、超 10MB、非法文件名 fixture。

D2 scraper charset/streaming:
  - 只在 ircs-scraper-service 接入 Tika charset detector 所需最小依赖。
  - 新增 ScraperCharsetDetector 封装 UniversalEncodingDetector，list fetch 先用 raw bytes/
    stream 探测 charset，再进入 Jackson streaming parser。
  - detail fetch 先不动，避免扩大范围。
  - 补 GBK/GB18030、UTF-8、no charset fallback、malformed JSON 和大 JSON fixture。
```

本轮未修改 D 相关代码；实现仍待后续 D1/D2 切片。

## 2026-06-09 D1 storage Tika MIME 最小接回

本轮只推进 storage-service 的 V1 `ImageSecurityValidatorImpl` 行为复刻。V1 用
`new Tika().detect(byte[])` 作为 MIME 判定内核，`declaredMimeType` 只记录日志不参与放行；
V3 当前是手写 magic bytes detector。为减少镜像体积，本轮只在 storage-service 接入
`tika-core`，不接 `tika-parsers-standard-package`，也不引入 PDF/Office 解析。

本轮范围：

- `ircs-storage-service` service-scoped 添加 `org.apache.tika:tika-core:3.2.2`。
- `ImageSecurityValidator` 改为使用 `Tika.detect(byte[])`；保留 10MB 上限、filename
  blacklist、allowlist、extension 映射和现有调用方接口。
- `declaredMimeType` 继续不作为放行依据，避免 header/声明值覆盖真实检测。
- 扩展本地 fixture 测试：有效 png/jpeg/webp/gif/avif/bmp/ico、HTML/JSON/ZIP/PDF/unknown
  bytes、空文件、超 10MB、非法文件名和 declared MIME spoof。

非目标：

- 不修改 `CoverImageDownloadService` 的 SSRF、redirect blocked、Content-Type 前置 allowlist、
  size limit 和 R2/path 行为。
- 不接 scraper charset/streaming；D2 后续单独处理。
- 不执行真实公网图片下载 smoke。

验收补充：

```text
D1 MARK 1/5 storage-service has tika-core only, no parser standard package
D1 MARK 2/5 ImageSecurityValidator uses Tika.detect(byte[]) as MIME source
D1 MARK 3/5 valid image fixture table passes for V1 allowlist MIME types
D1 MARK 4/5 spoof/non-image/empty/oversize/filename fixtures are rejected
D1 MARK 5/5 storage-service tests pass without live network
```

实现结果：

- `services/ircs-storage-service/build.gradle` 已只添加 `org.apache.tika:tika-core:3.2.2`，
  未添加 `tika-parsers-standard-package`，避免把 PDF/Office parser 引入 storage 镜像。
- `ImageSecurityValidator` 已使用 `Tika.detect(byte[])` 作为 MIME source，保留 V1/V3 共同的
  10MB 上限、filename blacklist、MIME allowlist 和 extension 映射。
- `declaredMimeType` 仍不参与放行判断；测试已覆盖 declared `text/html` 但 body 为 PNG 时
  以真实检测结果放行。
- Tika 3.2.2 对 ICO fixture 返回 `image/vnd.microsoft.icon`，D1 做窄归一化为 V1 allowlist
  使用的 `image/x-icon`；其他 MIME 不做放宽。
- `ImageSecurityValidatorTest` 已扩展为 fixture 表，覆盖 png/jpeg/webp/gif/avif/bmp/ico、
  HTML/JSON/ZIP/PDF/unknown bytes、空文件、超 10MB、非法文件名和 MIME spoof。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-storage-service:test :services:ircs-scraper-service:test :services:ircs-metadata-worker:test :shared:ircs-common:test --rerun-tasks
BUILD SUCCESSFUL
```

当前 D1 MARK：

```text
D1 MARK 1/5 storage-service has tika-core only, no parser standard package: done
D1 MARK 2/5 ImageSecurityValidator uses Tika.detect(byte[]) as MIME source: done
D1 MARK 3/5 valid image fixture table passes for V1 allowlist MIME types: done
D1 MARK 4/5 spoof/non-image/empty/oversize/filename fixtures are rejected: done
D1 MARK 5/5 storage-service tests pass without live network: done
```

剩余：

- D2 scraper charset/streaming 尚未实现；`ListScraperClient` 仍是 `bodyAsUtf8()` +
  `JsonPath.read()`，GBK/Big5/no charset 和大 JSON streaming parity 仍待后续切片。

## 2026-06-09 D2 scraper charset/streaming 最小接回

本轮推进 V1 `ListScraperServiceImpl` 中 list 抓取的 charset detection 与 Jackson streaming
parse 语义。V1 对 list 响应使用 `UniversalEncodingDetector.detect()` 探测 charset，失败回退
UTF-8，再用 Jackson `JsonParser` streaming 扫描 `items_path`、`primary_id_path`、
`update_time_path` 和 pagination path；V3 当前固定 `bodyAsUtf8()` 后 `JsonPath.read()` 全量解析。

本轮范围：

- `ircs-scraper-service` service-scoped 接入 Tika charset detector 所需依赖，不放到 shared。
- 新增 scraper 本地 charset detector，优先用 Tika `UniversalEncodingDetector`，探测失败回退
  UTF-8。
- `ListScraperClient.fetchList()` 改为基于 `OutboundHttpResponse.body()` 原始 bytes 做 charset
  detection，再用 Jackson streaming parser 解析列表 item，不再走 `bodyAsUtf8()` + JsonPath。
- 保留 `fetchDetail()` 现状，避免扩大 detail mapping 行为范围。
- 保留已完成的 shared outbound SSRF、UA、proxy、retry、gzip 语义。

非目标：

- 不引入 PDF/Office parse。
- 不做真实资源站公网抓取。
- 不改变 `ListItem` 对外结构；V1 pagination 先以内部 parser 支持为准备，不改变现有 caller。

验收补充：

```text
D2 MARK 1/5 scraper-service has charset detector dependency scoped locally
D2 MARK 2/5 list fetch detects GBK/GB18030 and UTF-8 fixtures without mojibake
D2 MARK 3/5 list fetch parses items via Jackson streaming parser, not JsonPath.read
D2 MARK 4/5 malformed/non-JSON list response keeps existing failure boundary documented by tests
D2 MARK 5/5 scraper/storage/shared tests pass without live network
```

实现结果：

- `services/ircs-scraper-service/build.gradle` 已 service-scoped 接入 `tika-core:3.2.2` 和
  `tika-parser-text-module:3.2.2`；未接 `tika-parsers-standard-package`，避免把 PDF/Office/
  OCR 等无关 parser 带进 scraper 镜像。
- 新增 `ScraperCharsetDetector`，优先使用 Tika `UniversalEncodingDetector`，失败时回退
  UTF-8；对纯 JSON GB18030 样本，Tika 可能误判为单字节编码，因此补了严格 UTF-8 失败后
  的 GB18030 本地 fallback，仅在解码结果包含 CJK 文本时启用。
- `ListScraperClient.fetchList()` 已改为读取 `OutboundHttpResponse.body()` 原始 bytes，先
  charset detection，再用 Jackson `JsonParser` streaming 扫描 `items_path`，不再走
  `bodyAsUtf8()` + `JsonPath.read()`。
- `fetchDetail()` 保持原 UTF-8 body 语义，避免扩大 detail mapping 行为范围。
- `ListScraperClientTest` 已覆盖 shared outbound header/UA/proxy、random UA、GB18030 list
  response、nested items、numeric id/update time/raw nested value 和 malformed non-JSON 返回空列表。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-scraper-service:test --rerun-tasks
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-config-service:test :services:ircs-scraper-service:test :services:ircs-storage-service:test :shared:ircs-common:test --rerun-tasks
BUILD SUCCESSFUL
```

当前 D2 MARK：

```text
D2 MARK 1/5 scraper-service has charset detector dependency scoped locally: done
D2 MARK 2/5 list fetch detects GBK/GB18030 and UTF-8 fixtures without mojibake: done
D2 MARK 3/5 list fetch parses items via Jackson streaming parser, not JsonPath.read: done
D2 MARK 4/5 malformed/non-JSON list response keeps existing failure boundary documented by tests: done
D2 MARK 5/5 scraper/storage/shared tests pass without live network: done
```

剩余：

- D1/D2 已完成 Tika 第一批 storage/scraper 行为回接；真实 provider/live charset smoke、
  Big5/其他非 UTF-8 fixture、detail response charset 和 pagination path 输出仍未纳入本切片。

## 2026-06-09 D3 Tika MIME / file parsing live 验证 first closure

本轮目标是关闭 `028-16-D` 的 first closure 证据缺口：不再只记录 D1/D2 本地实现，
而是把 V1 对照点、V3 依赖边界、focused fixture tests、可复跑 smoke 和清理证据串起来。

V1 对照点：

- `ircs-backend/build.gradle` 同时声明 `tika-core:3.2.2` 与
  `tika-parsers-standard-package:3.2.2`。
- `ImageSecurityValidatorImpl` 只实际使用 `new Tika().detect(byte[])` 做图片 MIME 判定；
  `declaredMimeType` 只进入日志，不参与放行；空文件、10MB 上限、文件名黑名单和
  `jpeg/png/webp/gif/avif/bmp/x-icon` allowlist 是行为边界。
- `ListScraperServiceImpl` 只实际使用 `UniversalEncodingDetector` 做 list 响应 charset
  detection；`charset == null` 回退 UTF-8；随后使用 Jackson `JsonParser` streaming
  扫描 list item 与 pagination path。
- 当前 V1 源码检索未发现 `AutoDetectParser`、`BodyContentHandler`、`PDFParser` 或通用
  PDF/Office text extraction 调用；因此 V3 本轮不新增通用文件解析服务，只证明 PDF/HTML/
  ZIP 等坏文件不会被 storage 图片链路误接收。

本轮范围：

- 强化 storage focused tests：MIME mismatch、HTML/PDF/ZIP/unknown bytes、空文件、超
  10MB 和文件名 guard。
- 强化 scraper focused tests：UTF-8/GB18030 fixture、空响应 fallback、malformed non-JSON
  失败边界；继续约束 list parsing 使用 raw bytes + charset detector + Jackson streaming。
- 新增 `scripts/smoke/tika-file-parsing-02816d-smoke.ps1`，在本地生成小 fixture、运行定向
  Gradle tests、检查依赖边界、最后清理 fixture 目录。

非目标：

- 不上传大文件，不写真实业务数据。
- 不启用公网 provider 抓取，不新增 HTTPRoute。
- 不把 `tika-parsers-standard-package` 接进 V3 storage/scraper；除非后续 V1 inventory
  找到真实通用文本抽取调用。

验收补充：

```text
D3 MARK 1/6 V1 Tika usage points and parser-package non-use are documented
D3 MARK 2/6 V3 storage/scraper Tika dependencies stay service-scoped and minimal
D3 MARK 3/6 focused tests cover valid images, MIME mismatch, bad files, empty/size/name guards
D3 MARK 4/6 focused tests cover UTF-8/GB18030 list parsing and malformed/empty response boundary
D3 MARK 5/6 smoke script creates small local fixtures, runs targeted tests, and cleans fixtures
D3 MARK 6/6 docs/tasks total ledger records remaining file types or production limits
```

实现结果：

- 新增 `FileNormalizationServiceTest`，验证 storage normalization 使用 Tika 实测 MIME，
  而不是 `declaredMimeType`；`text/html` 声明 + PNG body 会产出 `.png` storage key。
- 新增 `ScraperCharsetDetectorTest`，固定 UTF-8、GB18030 HTML fixture 与空响应回退 UTF-8。
- 新增 `scripts/smoke/tika-file-parsing-02816d-smoke.ps1`，脚本会：
  - 检查 V1 build 与源码中 Tika 使用点，并确认未发现通用 text extraction parser 调用。
  - 检查 V3 storage/scraper Tika 依赖仍是 service-scoped minimal。
  - 在 `build/tmp/02816d-tika-smoke-*` 生成 `valid.png`、`spoof-html.png`、
    `renamed.pdf.png`、`gb18030-list.json`、`empty.bin` 小 fixture。
  - 运行 storage/scraper 定向 tests。
  - `finally` 删除 fixture 目录并确认目录不存在。

smoke fixture 摘要：

```text
valid.png bytes=45 sha256=cb9ee84a55dfe3cb7c73189dfce37bc0ccb2dd549f8d83093eff135d12614191
spoof-html.png bytes=38 sha256=c56405ce99e926b542ebc4166dc898510ce73d0dd5124d1b5dce3aedf2da7295
renamed.pdf.png bytes=9 sha256=0716f9264c9fe19f5d7455276107f3ddcc1d3497f63d60689a73558ae8a1bf5e
gb18030-list.json bytes=71 sha256=9ac38af2aac0cb613ccc26433efd5af5ee3b9f762b94a7237f437f3e8646de63
empty.bin bytes=0 sha256=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
cleanup path=build/tmp/02816d-tika-smoke-20260609231209
cleanup verified=True via Test-Path => False
```

验证：

```text
powershell -ExecutionPolicy Bypass -File scripts\smoke\tika-file-parsing-02816d-smoke.ps1
MARK 1/6 V1 Tika usage points documented and no generic text extraction parser usage found
MARK 2/6 V3 Tika dependencies are service-scoped and minimal
MARK 3/6 small local fixtures created for image spoof, pdf rename, GB18030 list and empty file
MARK 4/6 focused Gradle tests cover MIME mismatch, bad files, size/name guards and charset parsing
MARK 5/6 smoke completed without live network, uploads, business DB writes or public HTTPRoute
MARK 6/6 fixture directory cleaned
BUILD SUCCESSFUL

.\gradlew.bat --no-daemon :services:ircs-storage-service:test :services:ircs-scraper-service:test :shared:ircs-common:test --rerun-tasks
BUILD SUCCESSFUL
```

当前 D3 MARK：

```text
D3 MARK 1/6 V1 Tika usage points and parser-package non-use are documented: done
D3 MARK 2/6 V3 storage/scraper Tika dependencies stay service-scoped and minimal: done
D3 MARK 3/6 focused tests cover valid images, MIME mismatch, bad files, empty/size/name guards: done
D3 MARK 4/6 focused tests cover UTF-8/GB18030 list parsing and malformed/empty response boundary: done
D3 MARK 5/6 smoke script creates small local fixtures, runs targeted tests, and cleans fixtures: done
D3 MARK 6/6 docs/tasks total ledger records remaining file types or production limits: done
```

first closure 结论：

- `028-16-D` storage Tika MIME 与 scraper list charset/streaming 的 V1 对照、实现、
  focused tests 和本地 smoke 已闭环；本切片不写真实业务数据、不上传文件、不触发公网。
- V1 虽声明 `tika-parsers-standard-package`，但当前源码未发现通用 PDF/Office/text
  extraction 调用；V3 暂不新增 parser standard package 或通用文件解析 API。
- 剩余边界：detail response charset、pagination totalPages/totalItems 对外输出、Big5/Shift_JIS
  等更多非 UTF-8 fixture、真实 provider 低资源 live charset smoke，以及如后续 inventory
  找到真实 PDF/Office/text extraction 调用时再单独拆 parser package 接回任务。

## 2026-06-10 D4 Tika/scraper golden P1/P2 implementation closure

本轮承接 `028-16-F3` 中 D3 first closure 后剩余的 P1/P2 scraper 边界，按实现优先策略只做
本地 focused tests 和 fixture prep，不执行真实外网 provider smoke。

实现结果：

- `ListScraperClient.fetchDetail()` 不再固定 `bodyAsUtf8()`；detail response 改为 raw bytes
  + `ScraperCharsetDetector` 解码，优先采用 response `Content-Type` charset。
- `ScraperCharsetDetector` 新增 declared charset 解析，短 JSON Big5/Shift_JIS 通过
  `Content-Type: application/json; charset=...` 稳定解码；无声明时保留 UTF-8 strict、
  Tika detector 和 GB18030 CJK fallback。
- `ListScraperClient.fetchListPage()` 新增 `ListPage(items,totalPages,totalItems)`，解析
  `list_mapping.pagination.total_pages_path` / `total_items_path`；旧 `fetchList()` 仍返回
  item list，兼容现有 caller。
- `ManualScraperService` 在 SSE 和 task execution logs 输出 pagination totalPages/totalItems，
  实现对外可见，不改变 ingestion contract。
- 本地 fixture 覆盖 GB18030、Big5、Shift_JIS、UTF-8 BOM pagination、invalid bytes 和
  detail charset；`scripts/smoke/tika-file-parsing-02816d-smoke.ps1` 已增加对应 fixture 生成。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-scraper-service:test --tests "*ListScraperClientTest" --tests "*ScraperCharsetDetectorTest" --tests "*ManualScraperServiceTaskExecutionTest" --rerun-tasks
BUILD SUCCESSFUL

powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\tika-file-parsing-02816d-smoke.ps1 -SkipGradle
MARK 1/6 V1 Tika usage points documented and no generic text extraction parser usage found
MARK 2/6 V3 Tika dependencies are service-scoped and minimal
MARK 3/6 small local fixtures created for image spoof, pdf rename, GB18030/Big5/Shift_JIS/BOM JSON and empty file
MARK 4/6 focused Gradle tests skipped by -SkipGradle; fixture prep and dependency guards only
MARK 5/6 smoke completed without live network, uploads, business DB writes or public HTTPRoute
MARK 6/6 fixture directory cleaned
```

当前 D4 MARK：

```text
D4 MARK 1/6 detail charset fixture matrix completed: done
D4 MARK 2/6 pagination totalPages/totalItems parsed and surfaced without breaking fetchList callers: done
D4 MARK 3/6 Big5/Shift_JIS/BOM/invalid bytes behavior covered locally: done
D4 MARK 4/6 historical parser standard non-goal: superseded by `028-20-11A`; scraper-service now uses parser standard package, storage remains core-only
D4 MARK 5/6 focused scraper tests pass or deferred reason recorded: done
D4 MARK 6/6 F3/D/089/093/unified test ledger updated: done
```

D 任务当前状态：

- storage MIME/file parsing guard：D1/D3 已完成，本轮未改 storage-service。
- scraper list/detail charset + pagination：D2/D4 已完成 implementation closure。
- 仍后置：真实 provider 低资源 live charset smoke、完整 `UTB-02816-P1P2-01` 统一测试执行，
  以及如后续 V1 inventory 找到真实 PDF/Office/text extraction 调用时再单独拆 parser package。
