# 028-16-A9 title/location/genre pipeline first closure task spec

状态：本地 first closure 完成，dev live smoke 待部署后重跑

父任务：

- `tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md`
- `tasks/in-progress/028-16-a8-title-season-subtitle-first-slice.md`

上游来源：

- `tasks/in-progress/028-16-a5-area-language-composite-alias-policy.md`
- `tasks/in-progress/028-16-a6-genre-synonym-suffix-protected-term-policy.md`
- `tasks/in-progress/028-16-a7-title-alias-dedup-policy.md`
- `tasks/in-progress/028-16-a8-title-season-subtitle-first-slice.md`
- V1 `NormalizationPipeline` / `AreaMappingPolicy` / `StandardGenreMappingPolicy`

## 背景

A5-A8 已分别完成 area/language composite alias、genre synonym/suffix/protected-term、
aliasTitle dedup、title season/subtitle 的 first slice。当前缺口不是单点规则，而是同一条
raw metadata 同时经过 title、alias、location、genre、lock/override、错误处理后，能被
focused fixture 和 live smoke 证明是一条可验证 pipeline。

## 目标

- 固定 raw title/alias/location/genre 输入到标准字段输出的组合 fixture。
- 覆盖中文/英文季、冒号副标题、地区语言别名、genre synonym/protected-term、alias 去重。
- 覆盖 `locked_fields` 对 title/alias/season/subtitle/area/language/genres 的不覆盖语义。
- 保持空/非法 raw metadata 走失败重试，不发布 search sync。
- 新增低资源 dev live smoke 脚本，投递少量 fixture 并清理 DB/queue/port-forward 残留。

## 非目标

- 不迁移完整 V1 NLP 地名识别。
- 不迁移完整 V1 `StandardGenreMappingPolicy` 全量词表。
- 不做 LLM cleaning、release group 识别、复杂 bracket、多语言 alias fuzzy 去重。
- 不修改 notification、metadata provider、content C2、storage、portal/admin/BFF 或 shared outbound。

## 输入

```text
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoTextNormalizer.java
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoNormalizationService.java
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoNormalizationRepository.java
scripts/smoke/normalization-a9-title-location-genre-closure-smoke.ps1
```

## 输出

```text
title/location/genre pipeline closure focused tests
normalization A9 live smoke script
docs/tasks ledger updates
remaining V1 cleaning edge list
```

## 影响范围

允许写入：

```text
services/ircs-normalization-worker/**
scripts/smoke/normalization*
docs/**
tasks/**
```

禁止改动：

```text
notification
metadata provider
content C2
storage
portal/admin/BFF
shared outbound
```

## 风险

- 组合 fixture 可能暴露 A5-A8 单点行为之间的顺序问题；优先以 minimal rule 修复。
- live smoke 依赖 dev K8S/Rabbit/Postgres 可用；不可用时必须明确 SKIPPED 并不假装通过。
- 清理语句必须只匹配本轮唯一 trace，避免删除他人并行数据。

## 验收标准

```text
MARK 1/6 task spec and V1/V3 delta recorded: done
MARK 2/6 focused pipeline fixture covers title/alias/season/subtitle/location/genre output: done
MARK 3/6 focused lock fixture proves locked fields are not overwritten: done
MARK 4/6 empty/invalid metadata failure path remains covered: done
MARK 5/6 normalization-worker tests pass: done
MARK 6/6 dev live smoke runs or skips with explicit evidence and cleanup result: partial, first run failed on K8S TLS timeout plus Windows pipe encoding; DB/queue/port-forward cleanup verified
```

## 测试方式

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
.\scripts\smoke\normalization-a9-title-location-genre-closure-smoke.ps1
```

## 当前状态

2026-06-09：已建立 compact task spec，准备进入 focused tests、必要最小实现和 smoke 脚本。

2026-06-09：本地 first closure 已完成。

实现结果：

- `RawVideoTextNormalizer` 的 alias 去重从只比较完整 normalized title，扩展为同时比较冒号左侧
  main title。`The Last Kingdom: Seven Kings Must Die Season 2` / `The Last Kingdom` 和
  `庆余年 第2季:特别篇` / `庆余年 第2季` 这类 subtitle title 不再保留重复主标题 alias。
- 新增 focused pipeline fixture，单条 raw metadata 覆盖：
  - 中文季数 `第二季` -> `season=2`
  - 中文冒号副标题 -> `subtitle=特别篇`
  - alias 去重 -> 仅保留 `Joy of Life 2`
  - `港台`、`日本地区` -> 保留 raw token 并追加 `香港`、`台湾`、`日本`
  - `国粤双语`、`日语中字` -> 保留 raw token 并追加 `国语`、`粤语`、`日语`
  - `纪录片`、`微短剧`、`短剧`、`古装剧` -> synonym/suffix/protected-term 展开
- 新增 focused lock fixture，证明 locked `title` / `aliasTitle` / `season` / `subtitle` /
  `area` / `language` / `genres` 不被 metadata 覆盖，且 relation expansion 被压制。
- `RawVideoNormalizationServiceTest` 新增 invalid JSON raw metadata 失败路径，保持不发布
  search sync。
- 新增 `scripts/smoke/normalization-a9-title-location-genre-closure-smoke.ps1`，低资源投递 1 条
  fixture 到 `q.normalize.video`，验证 DB scalar/relation 输出，并按 rawVideoId 捕获 join id 清理。
  Windows PowerShell 管道已显式设置 UTF-8，避免中文 fixture 进入 `psql` 时变成 `?`。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 18s

$null = [scriptblock]::Create((Get-Content -LiteralPath .\scripts\smoke\normalization-a9-title-location-genre-closure-smoke.ps1 -Raw)); 'SCRIPT_PARSE_OK'
SCRIPT_PARSE_OK
```

live smoke 首跑证据：

```text
.\scripts\smoke\normalization-a9-title-location-genre-closure-smoke.ps1
MARK 1/5 normalization/search consumers are ready and queues are clean
FAILED UNEXPECTED_ERROR: psql rows failed with exit code 1
kubectl stderr: TLS handshake timeout
```

首跑 fixture 实际已被 dev worker 消费为 `READY`，但当时脚本未设置 UTF-8，中文 metadata
经 Windows PowerShell 管道进入 `psql` 后变成 `?`，导致 normalized scalar 断言无法通过。
已按 rawVideoId `5eef5077-35b6-444c-96f7-6ac72dc0a75d` 捕获 join row id 并清理。

清理证据：

```text
DB residue raw_videos|data_sources|video_raw_genres|video_raw_languages|video_raw_areas = 0|0|0|0|0
Rabbit q.normalize.video/q.search.sync.raw and DLQ messages = 0
port-forward 19094 residue = 0
```

剩余清洗边角：

- 完整 V1 location NLP 地名识别仍未迁移；当前只覆盖 composite/suffix alias 第一刀。
- 完整 V1 `StandardGenreMappingPolicy` 全量 synonym/protected-term 表仍未迁移。
- title pipeline 仍缺 release group、复杂 bracket、mainTitle 独立字段、`S02` / `Part II` /
  `Vol.2` 等更多英文季/部格式。
- alias 仍只做等价去重，不做 fuzzy alias 去重，避免误删搜索召回。
- A9 live smoke 需要在包含本地 A9 代码的 normalization-worker 镜像部署后重跑。

2026-06-10：F1/A11 已在 A9 本地 first closure 基础上补一轮 normalization golden P1/P2
implementation closure：

- 原剩余 `S02` / `Part II` / `Vol.2` 已有 focused fixture 覆盖；release group 前缀会被保守剥离。
- location NLP 已补第一批高置信地名 hint；完整 V1 任意分词地名识别仍不做扩大化迁移。
- genre NLP 已补第一批保守同义词；完整 V1 全量词表仍保留为后续可选矩阵。
- retry/watchdog P2 已补 due `FAILED`、stale `PENDING/PROCESSING`、queue depth guard 和 adaptive
  batch min/max 的本地状态机测试。
- live smoke 状态不变：仍等统一测试窗口和部署后重跑。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 30s
```
