# 028-16-A8 title season/subtitle first slice task spec

状态：done first slice

父任务：

- `tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md`
- `tasks/in-progress/028-16-a7-title-alias-dedup-policy.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md` P0-05
- `tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md`
- `tasks/in-progress/028-16-a7-title-alias-dedup-policy.md`
- V1 title handler pipeline 中 season/subtitle/mainTitle 语义
- V3 `RawVideoTextNormalizer`

## 背景

A7 已完成 aliasTitle 去重第一刀：alias 输入会规整分隔符、删除与 normalized title 等价的项、
删除 alias 内部重复项，并保留 `locked_fields.aliasTitle` 压制语义。P0-05 仍记录完整
title pipeline 未 1:1，尤其 season/subtitle/mainTitle、release group、复杂 bracket、多语言 alias
仍缺口较大。

本切片只处理最保守的 season/subtitle 第一刀。仓库迁移已存在 `raw_videos.season` 和
`raw_videos.subtitle`，但 normalization-worker 当前 `RawVideoRecord` / `RawVideoPatch` /
repository 未接入这两个列。

## 目标

- 在 normalization-worker 内接入 `raw_videos.season` 与 `raw_videos.subtitle` 读写。
- 从 `title` / `rawMetadata.title` 中识别保守季数表达：`第2季`、`第二季`、`Season 2`。
- 从冒号标题中识别保守副标题，例如 `主标题: 副标题` / `主标题：副标题`。
- 把识别出的 season/subtitle 写入现有 raw normalized field：`season` / `subtitle`。
- 添加 golden fixture，覆盖中文季数和英文冒号副标题。
- 保留 `locked_fields.title` / `locked_fields.aliasTitle` 现有语义；`locked_fields.season` /
  `locked_fields.subtitle` 命中时不覆盖对应字段。

## 非目标

- 不实现完整 V1 title handler pipeline。
- 不做 fuzzy alias 删除。
- 不做 release group 识别。
- 不处理复杂 bracket、多语言 alias 语义拆分。
- 不修改 notification、storage、BFF、shared outbound。
- 不更新 `docs/index.md`、`tasks/index.md`、`docs/reports/089`、`docs/migrations/089`。

## 输入

```text
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoTextNormalizer.java
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoRecord.java
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoPatch.java
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoNormalizationRepository.java
services/ircs-normalization-worker/src/test/java/com/prodigalgal/ircs/normalization/RawVideoTextNormalizerTest.java
services/ircs-normalization-worker/src/test/java/com/prodigalgal/ircs/normalization/RawVideoNormalizationServiceTest.java
services/ircs-normalization-worker/src/test/java/com/prodigalgal/ircs/normalization/RawVideoNormalizationRepositoryTest.java
```

## 输出

```text
normalization-worker season/subtitle first-slice extraction
season/subtitle golden fixture tests
A8 task spec status and verification notes
```

## 影响范围

允许写入：

```text
services/ircs-normalization-worker/**
tasks/in-progress/028-16-a8-title-season-subtitle-first-slice.md
```

避免修改：

```text
docs/index.md
tasks/index.md
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
```

禁止改动：

```text
notification
storage
BFF
shared outbound
```

## 依赖

- 复用 A2/A7 当前 `standardize`、中文序数归一、title 清洗和 alias 去重顺序。
- 依赖现有 migration 中的 `raw_videos.season` / `raw_videos.subtitle` 列。
- 依赖 normalization-worker 单元测试作为本切片验收入口。

## 风险

- 过度拆 title 可能损伤搜索召回；本切片只处理明确季数和冒号副标题。
- `Season 2` 可能是英文主标题的一部分；本切片只在可提取出非空 main title 时写入 season。
- 冒号副标题可能属于片名原文；本切片仅把右侧保存到 `subtitle`，不从 title 删除副标题。
- 完整 release group、复杂 bracket 和多语言 alias 仍会保留为后续风险。

## 验收标准

```text
MARK 1/5 normalization-worker reads and writes raw_videos.season/subtitle: done
MARK 2/5 Chinese season fixture extracts 第2季/第二季 to season=2 without regressing title: done
MARK 3/5 English colon subtitle fixture extracts subtitle and preserves title/alias semantics: done
MARK 4/5 locked_fields.season/subtitle preserves raw record values: done
MARK 5/5 normalization-worker tests pass: done
```

## 测试边界

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
```

不做：

```text
K8S live smoke
跨服务 aggregation/search/content 端到端验证
真实 provider live smoke
```

## 关联文档

- `docs/reports/089-v1-golden-behavior-inventory.md` P0-05
- `tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md`
- `tasks/in-progress/028-16-a7-title-alias-dedup-policy.md`

## 关联任务

- 上游父任务：`tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md`
- 前置切片：`tasks/in-progress/028-16-a7-title-alias-dedup-policy.md`
- 当前切片：`tasks/in-progress/028-16-a8-title-season-subtitle-first-slice.md`

## 当前状态

2026-06-09：A8 第一刀完成。normalization-worker 已把 `raw_videos.season` /
`raw_videos.subtitle` 接入 `RawVideoRecord`、`RawVideoPatch` 和
`RawVideoNormalizationRepository` 的查询、更新与参数绑定。

`RawVideoTextNormalizer` 新增保守 title structure 提取：

- `season` 优先读取显式 `rawMetadata.season`，否则从 `title` / `rawMetadata.title` 中识别
  `第2季`、`第二季`、`Season 2`；中文数字先沿用 A2 的 `normalizeChineseOrdinals` 归一。
- `subtitle` 优先读取显式 `rawMetadata.subtitle` / `rawMetadata.sub_title`，否则从首个冒号右侧
  识别保守副标题；右侧如果只是 `第N季` / `Season N` 则不写 subtitle。
- `rawMetadata.subTitle` 继续只作为 A7 alias 兼容输入，不在本切片改为 subtitle，以避免破坏
  既有 aliasTitle 语义。
- `locked_fields.season` 会保留原始 `season`；`locked_fields.subtitle` / `locked_fields.sub_title`
  会保留原始 `subtitle`。`locked_fields.title` / `locked_fields.aliasTitle` 的既有行为保持不变。

新增 golden fixtures：

- `extractsChineseSeasonFromTitleWithoutRegressingAliasDedup`
- `extractsEnglishColonSubtitleAndSeasonWhenExplicitMetadataIsMissing`
- `lockedSeasonAndSubtitlePreserveRawRecordValues`

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 17s
12 actionable tasks: 12 executed
```

剩余风险：

- 完整 V1 title handler chain 仍未 1:1，尤其 mainTitle 拆分、release group、复杂 bracket、
  多语言 alias 和 fuzzy alias 去重仍留后续。
- 冒号副标题当前只写 `subtitle`，不从 `title` 删除右侧内容，避免第一刀影响搜索召回。
- `Season 2` 只做显式数字识别，不处理 `S02`、`Part II`、`Vol.2` 等更多英文/发行组格式。
