# 028-16-A normalization cleaning strategy parity task spec

状态：进行中

父任务：

- `tasks/in-progress/028-16-v1-v3-deep-parity-gap-audit-parent.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/088-v1-v3-deep-parity-gap-audit.md`
- `docs/migrations/088-v1-v3-deep-parity-gap-audit.md`
- `tasks/done/028-08-normalization-deep-mapping-parity.md`

## 背景

`028-08` 已完成 raw relation、standard exact mapping、retry watchdog 和 live smoke，但明确
没有引入 LLM、HanLP、缓存推断或 fuzzy matching。V1 清洗链路包含完整
`NormalizationPipeline`、多 title handler、`MetadataNormalizer`、`AreaMappingPolicy`、
`StandardGenreMappingPolicy` 和 `DescriptionMetadataExtractor`。当前 V3 只是 MVP 近似。

## 目标

- 接回 V1 文本标准化语义：CharTable、HanLP 简繁转换、opencc4j 日式汉字转换。
- 分阶段复刻 V1 title pipeline：预处理、噪音移除、括号/结构、双语标题、数字归一、重复清理、alias 去重。
- 接回 description actor/director 抽取。
- 接回 area alias/composite/NLP 地名识别。
- 接回 genre synonym/suffix/protected-term 映射。
- 保持 `028-08` 已完成 raw relation、standard exact mapping 和 live smoke 行为不回退。

## 非目标

- 本子任务不直接执行 LLM cleaning；LLM 单独由 `028-16-E` 承接。
- 不做大批量历史数据重洗。
- 不把 HanLP/OpenCC 加到所有服务，只允许 service-scoped 接入 normalization-worker。

## 输入

V1：

```text
MetadataNormalizer
DescriptionMetadataExtractor
AreaMappingPolicy
StandardGenreMappingPolicy
NormalizationPipeline
TitlePreProcessingHandler
TitleNoiseRemovalHandler
TitleCleanupHandler
TitleStructureHandler
TitleBilingualHandler
TitleNumeralHandler
TitleRedundantRepetitionHandler
AliasTitleNormalizationHandler
PersonHandler
AreaLanguageHandler
CategoryGenreHandler
```

V3：

```text
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoTextNormalizer.java
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoNormalizationRepository.java
services/ircs-normalization-worker/src/test/java/com/prodigalgal/ircs/normalization/**
```

## 输出

- normalization-worker service-scoped dependency updates。
- V1-compatible text normalizer/strategy classes。
- Parser/strategy fixture tests。
- 最小 queue live smoke 复验。
- 报告、迁移记录和任务状态回写。

## 影响范围

```text
services/ircs-normalization-worker/**
docs/reports/088-v1-v3-deep-parity-gap-audit.md
tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md
```

## 风险

- 标题策略回归会影响 search/aggregation；必须用 V1 风格 fixture 做断言。
- HanLP/OpenCC 增加镜像体积，后续 image push 要关注 ARM64 layer。
- description 抽取可能产生额外 person relation，live smoke fixture 必须可清理。

## 验收标准

```text
MARK 1/8 normalization-worker has service-scoped HanLP and opencc4j dependencies: done first slice by A2/A4
MARK 2/8 text standardizer matches V1 CharTable/HanLP/OpenCC behavior on TC/SC/JP fixtures: partial, A2 done and A4 prewarm added; CharTable case policy remains
MARK 3/8 title pipeline covers V1 release-noise/bracket/season/bilingual/numeral/repetition fixtures: partial, A2 first slice done; full handler chain remains
MARK 4/8 alias title normalization removes duplicate main title and normalizes separators: partial, A2 first slice done; full V1 alias chain remains
MARK 5/8 description extractor adds actor/director from HanLP nr terms around director/actor keywords: done first slice by A4
MARK 6/8 area mapping covers alias/composite/location NLP fixtures: partial, A5 area composite/suffix alias first slice done; location NLP remains
MARK 7/8 genre mapping covers synonym/suffix/protected-term fixtures: partial, A6 first slice done; full StandardGenreMappingPolicy/HanLP genre NLP remains
MARK 8/8 q.normalize.video live smoke still passes with DB/queue/ES residue cleanup
```

## 测试边界

```powershell
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
.\scripts\smoke\normalization-deep-mapping-02808-smoke.ps1
```

## 当前状态

已合并审计 agent A 的清洗/标准化细节，并拆出第一批 P0 实现切片：

```text
tasks/in-progress/028-16-a1-canonical-rawmetadata-contract.md
tasks/in-progress/028-16-a2-text-standardization-title-policy.md
tasks/in-progress/028-16-a3-metadata-result-deep-merge.md
tasks/done/028-16-a4-description-ner-hanlp-prewarm-title-chain-parity.md
tasks/in-progress/028-16-a5-area-language-composite-alias-policy.md
tasks/in-progress/028-16-a6-genre-synonym-suffix-protected-term-policy.md
tasks/in-progress/028-16-a7-title-alias-dedup-policy.md
tasks/in-progress/028-16-a8-title-season-subtitle-first-slice.md
tasks/in-progress/028-16-a9-title-location-genre-pipeline-first-closure.md
```

这些切片分别拥有 scraper-service、normalization-worker、metadata-worker 的写入范围，
可以并行推进。

当前子切片进度：

```text
028-16-A1 canonical rawMetadata: done first slice, MARK 4/4, local scraper-service test passed
028-16-A2 text/title policy: done first slice, MARK 5/5, local normalization-worker test passed
028-16-A3 metadata result deep merge: done first slice, MARK 5/5, local metadata-worker test passed
028-16-A4 description NER / HanLP prewarm: done first slice, MARK 5/5, local normalization-worker test and K8S dry-run passed
028-16-A5 area/language composite alias policy: done first slice, MARK 4/4, local normalization-worker test passed
028-16-A6 genre synonym/suffix/protected-term policy: done first slice, MARK 5/5, local normalization-worker test passed
028-16-A7 title alias dedup policy: done first slice, MARK 4/4, local normalization-worker test passed
028-16-A8 title season/subtitle first slice: done first slice, MARK 5/5, local normalization-worker test passed
028-16-A9 title/location/genre pipeline first closure: local MARK 5/6 done, dev live smoke first run failed on K8S TLS timeout plus Windows pipe encoding, cleanup verified
```

组合验证：

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test :services:ircs-metadata-worker:test --rerun-tasks
BUILD SUCCESSFUL
.\gradlew.bat --no-daemon :services:ircs-scraper-service:test :services:ircs-normalization-worker:test :services:ircs-metadata-worker:test --rerun-tasks
BUILD SUCCESSFUL
```

2026-06-09：A4 第一刀完成后，normalization-worker 已恢复 V1 `HanLPInitializer`
预热行为和 `DescriptionMetadataExtractor` / `PersonHandler` 的 description NER fallback。
显式 actor/director metadata 保持最高优先级，`locked_fields` 仍压制对应关系写入。

2026-06-09：A5 第一刀完成后，normalization-worker 增加 service-scoped
`RawRelationAliasPolicy`，对 `港台`、`日韩`、`日韩欧美`、`日本地区`、`国粤双语`、
`日语中字` 等 V1 常见 P0 area/language 复合或后缀 token 追加可命中 catalog raw alias /
standard exact mapping 的 raw token，同时保留原始 token 供 raw dictionary 追溯。
`locked_fields` 中的 `area` / `language` 仍完全压制对应 raw relation 输出。

文档集成 worker 已把 A5 按 done first slice 纳入 `docs/index.md`、`tasks/index.md`、
`docs/reports/089-v1-golden-behavior-inventory.md` 和
`docs/migrations/089-v1-golden-behavior-inventory.md`；任务文件仍保留在 `tasks/in-progress/`，
不强行移动，后续继续承接完整 location NLP、genre policy 和 live smoke。

2026-06-09：A6 第一刀完成后，`RawVideoTextNormalizer` 在 genres 未锁定时接入
`expandGenres`，并为 `RawRelationAliasPolicy` 增加 genre 小策略；已用
`古装剧`、`都市剧`、`悬疑片`、`纪录片`、`纪录`、`微短剧`、`动漫`、`短剧` fixture 固定
V1 风格 synonym/suffix/protected-term 展开，`locked_fields.genres` 仍完全压制 relation 输出。
任务文件继续保留在 `tasks/in-progress/`，后续承接 full `StandardGenreMappingPolicy`、
HanLP genre NLP、title pipeline 联动和 live smoke。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\normalization-worker-dev.yaml
deployment.apps/ircs-normalization-worker configured (server dry run)

.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 16s
```

2026-06-09：A9 本地 first closure 完成。`RawVideoTextNormalizer` 已把 alias 去重扩展为同时
比较完整 normalized title 和冒号左侧 main title，修复 title/subtitle 组合后主标题 alias 仍被保留的
问题。新增 focused pipeline fixture 覆盖中文/英文季、冒号副标题、地区语言别名、genre
synonym/protected-term、alias 去重和 lock 不覆盖；新增 invalid raw metadata failure path。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 18s

$null = [scriptblock]::Create((Get-Content -LiteralPath .\scripts\smoke\normalization-a9-title-location-genre-closure-smoke.ps1 -Raw)); 'SCRIPT_PARSE_OK'
SCRIPT_PARSE_OK
```

dev live smoke 首跑已执行到 `MARK 1/5 normalization/search consumers are ready and queues are clean`，
随后在 `Wait-DbReady` 遇到 Kubernetes API `TLS handshake timeout`；同时发现 Windows PowerShell
未设置 UTF-8 时中文 fixture 经 `psql` 管道变成 `?`。脚本已补 UTF-8 输出设置。首跑 rawVideoId
`5eef5077-35b6-444c-96f7-6ac72dc0a75d` 已捕获 join id 清理，DB residue
`raw_videos|data_sources|video_raw_genres|video_raw_languages|video_raw_areas = 0|0|0|0|0`，
Rabbit `q.normalize.video` / `q.search.sync.raw` 及 DLQ messages 均为 `0`，`19094` port-forward
残留为 `0`。

A9 后剩余清洗边角：

- 完整 V1 location NLP 地名识别。
- 完整 V1 `StandardGenreMappingPolicy` 全量 synonym/protected-term 表。
- release group、复杂 bracket、mainTitle 独立字段、`S02` / `Part II` / `Vol.2` 等更多标题结构。
- fuzzy alias 去重和多语言 alias 语义去重。
- 部署包含 A9 代码的 normalization-worker 镜像后重跑
  `scripts/smoke/normalization-a9-title-location-genre-closure-smoke.ps1`。

2026-06-10：F1/A11 normalization golden P1/P2 implementation closure 已完成本地实现切片。

补齐内容：

- title handler chain 追加 release group 前缀剥离、`S02`、`Part II`、`Vol.2` season 识别。
- location NLP first closure 追加高置信地名 hint 与复合地区 alias，例如 `东京电影节 -> 日本`、
  `中国香港 -> 香港`，仍保留原 raw token。
- genre NLP first closure 追加 `推理/惊栗/动画片/卡通/综艺节目/伦理片/情色片` 等保守同义词展开。
- raw category fallback 从 `category/type/videoType` 兜底 raw category sourceCode/sourceName。
- normalization retry watchdog 拆分 due `FAILED` 和 stale `PENDING/PROCESSING`，队列非空时不 rescue
  stale active，重投前把可恢复状态归一为 `PENDING`。
- `normalization.batch-size.min/max` 纳入 watchdog batch clamp；runtime/K8S override 仍优先。
- failure log 固定输出 reason code + error class，不输出 raw metadata 或异常 message。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 30s
69 tests completed, 0 failed
```

未跑：live smoke、K8S rollout、全量测试、scraper/metadata focused tests。统一测试继续落到
`UTB-02816-P1P2-01`。

2026-06-10：A12 normalization cleaning/title/raw dictionary 余量切片完成 focused 本地实现。

补齐内容：

- title bracket edge：按 V1 `TitleCleanupHandler#processBrackets` 语义，删除 bracket 内纯 release/language
  噪音，例如 `国语中字`，但保留有效 bracket 内容并统一为圆括号，例如
  `电影名 (国语中字) [导演剪辑版] 2026 1080p WEB-DL -> 电影名 (导演剪辑版)`。
- raw area dictionary edge：按 V1 `AreaMappingPolicy` 补充 `大陆/cn/china/hk/tw/jp/kr/us/uk` 等 exact alias，
  以及 `英美/大陆香港/大陆台湾/新马/新马泰/欧美` composite expansion；保留原 raw token 供 raw dictionary
  追溯。
- genre dictionary edge：补充 V1 `StandardGenreMappingPolicy` 中 `武打/功夫/正剧/情节/二次元/纪实/解谜/烧脑/惊险`
  等绝对同义词扩展；V3 额外采用更安全替代：suffix stripping 不产出单字候选，避免 `正剧 -> 正`
  这类低价值 raw token。
- `locked_fields` 优先级未改变：`genres/language/area/category/title/aliasTitle/season/subtitle`
  相关压制路径继续由既有 fixture 覆盖。

显式非目标 / 延后：

- A12 未切换到 V1 `MetadataNormalizer.standardizeText` 的完整 `CharTable.convert -> HanLP -> OpenCC`
  顺序。证据：V1 先调用 HanLP `CharTable.convert`，V3 当前仍是 Java `Normalizer.NFKC` + HanLP + OpenCC。
  该差异影响所有字段标准化，需单独 CharTable fixture 决策，不在本余量切片里混改。
- A12 未改 V3 当前 title/mainTitle 策略；完整 V1 `TitleStructureHandler` 会把 colon/dash/space subtitle
  从 title main 部分拆出，当前 V3 仍保留 A9 既有 title 形态。该行为面较大，留给后续 mainTitle
  parity 切片。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 32s
71 tests completed, 0 failed
```

未跑：live smoke、K8S rollout、全量 workspace 测试、scraper/metadata focused tests。

2026-06-10：A13 CharTable / title structure post-reboot 收敛完成。

背景：系统重启后，worker 的 A13 改动已部分落盘，但直接把 `title` 改成 `mainTitle` 并让
`CharTable.convert` 的 ASCII 小写行为穿透到显示字段，导致既有 A11/A12 focused tests 大量失败。

本轮修复：

- `standardize` 接回 V1 顺序：`CharTable.convert -> HanLP.convertToSimplifiedChinese -> OpenCC`。
- 为 V3 已验证显示契约保留 ASCII 大小写回灌，避免英文 title/alias 从 `Title` 变成 `title`。
- `TitleStructure` 继续用于提取 `season/subtitle`，但不再覆盖 `title` 主字段；完整 V1 mainTitle
  display 行为保留为后续独立决策/切片。
- 修复 `CharTable` 后 release group bracket 归一化导致 `[VCB-Studio]` 不能剥离的问题。
- 修复英文 subtitle 中 `Season 2` 被过度剥离的问题；`season` 字段仍单独解析。

验证：

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --tests "*RawVideoTextNormalizerTest" --rerun-tasks
```

结果：`BUILD SUCCESSFUL`，24 tests completed, 0 failed。

后置：

- 是否把 `title` 显示字段切到 V1 `mainTitle` 语义，需要单独评估 portal/admin 显示、search title
  variants、alias 去重和老数据重洗影响，不能作为 reboot repair 混改。
