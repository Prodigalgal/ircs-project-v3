# 028-16-A2 text standardization and title policy task spec

状态：进行中

父任务：

- `tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- V1 `MetadataNormalizer`
- V1 title normalization handlers
- V3 `RawVideoTextNormalizer`

## 背景

V1 文本归一化使用 CharTable、HanLP 简繁转换、OpenCC/日中转换，并通过 title handler
pipeline 处理 release noise、双语标题、重复标题、中文数字、季数、副标题和 alias。V3 当前
`RawVideoTextNormalizer.standardize` 基本只做 trim，`cleanTitle` 也只是简化正则。

## 目标

- 在 normalization-worker 内补 text standardizer，覆盖全角/半角、繁简、日中混排的第一批
  golden fixture。
- 将 `cleanTitle` 拆成更接近 V1 pipeline 的可测试步骤。
- 保持现有 `028-08` relation/live smoke 语义不退化。
- 为后续 HanLP/OpenCC 依赖接入保留服务内边界，避免其他服务膨胀。

## 非目标

- 本切片不实现 LLM cleaning。
- 本切片不实现 aggregation fuzzy matching。
- 本切片不做真实 K8S live smoke，先以本地 fixture test 为准。

## 输入

```text
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoTextNormalizer.java
services/ircs-normalization-worker/src/test/**
build.gradle
settings.gradle
```

## 输出

```text
normalization-worker text standardizer
title policy fixture tests
dependency decision documented in task/doc
```

## 影响范围

```text
services/ircs-normalization-worker/**
build.gradle only if dependency is service-scoped or shared test fixture requires it
```

## 依赖

- 可选 HanLP/opencc4j 依赖必须只接入 normalization-worker 或明确 shared module。

## 风险

- 一次性完全复制 V1 pipeline 可能影响现有数据，因此先以 fixture/golden 分阶段。
- HanLP 依赖会增加镜像体积，需避免全服务接入。

## 验收标准

```text
MARK 1/5 standardize handles full-width ASCII and repeated whitespace: done
MARK 2/5 standardize handles first simplified/traditional golden fixtures: done
MARK 3/5 cleanTitle handles release noise, trailing year and bilingual title fixture: done
MARK 4/5 cleanTitle keeps season/number-sensitive fixtures such as 高达00 and 洛克王国4: done
MARK 5/5 normalization-worker tests pass without weakening existing 028-08 cases: done
```

## 测试边界

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
```

## 当前状态

2026-06-08：已完成第一刀实现。normalization-worker service-scoped 引入 HanLP/opencc4j，
`RawVideoTextNormalizer` 增加 NFKC、HanLP 简繁、OpenCC 日中转换、标题符号规整、
HTML/URL/文件后缀/括号年份/release noise 清理、双语标题清理、自重复标题清理和中文序数
标准化。为避免 V1 `CharTable.convert` 将 ASCII title/alias 全部小写导致 V3 既有行为回退，
本切片未直接调用 `CharTable.convert`，保留为后续差异决策项。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL
```
