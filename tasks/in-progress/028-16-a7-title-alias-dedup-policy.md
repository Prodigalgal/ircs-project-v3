# 028-16-A7 title alias dedup policy task spec

状态：done first slice

父任务：

- `tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md`
- `tasks/in-progress/028-16-a2-text-standardization-title-policy.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md` P0-05
- V1 `AliasTitleNormalizationHandler`
- V3 `RawVideoTextNormalizer`

## 背景

`028-16-A2` 已完成 text/title 第一刀，覆盖 release noise、括号年份、双语标题、自重复标题和
中文序数；`028-16-A5` / `028-16-A6` 又分别补了 area/language 与 genre 小策略。P0-05
仍记录完整 title handler pipeline 未 1:1，其中 alias title normalization 仍缺少“去掉主标题重复项、
去掉 alias 内部重复项、保留稳定顺序”的最小可验证行为。

## 目标

- 在 normalization-worker 内补 `aliasTitle` 去重第一刀。
- 对 `rawMetadata.aliasTitle` / `rawMetadata.alias` / `rawMetadata.subTitle` 的输入做分隔符规整、
  标准化和稳定去重。
- 删除与 normalized `title` 相同的 alias 项，避免主标题重复进入 `alias_title`。
- 保留 `locked_fields.aliasTitle` 对 alias 清洗的压制语义。

## 非目标

- 不实现完整 V1 title pipeline。
- 不拆分或落库 season / subtitle / mainTitle 独立字段。
- 不引入 LLM、HanLP 之外的新大依赖或跨服务依赖。
- 不修改 BFF、content config、R2、SMTP、shared outbound。

## 输入

```text
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoTextNormalizer.java
services/ircs-normalization-worker/src/test/java/com/prodigalgal/ircs/normalization/RawVideoTextNormalizerTest.java
```

## 输出

```text
normalization-worker alias title dedup policy
alias title golden fixture tests
```

## 影响范围

```text
services/ircs-normalization-worker/**
tasks/in-progress/028-16-a7-title-alias-dedup-policy.md
```

## 依赖

- 复用当前 normalization-worker 内已有 `standardize` / `cleanTitle` 风格逻辑。
- 依赖 A2 已存在的 title 清洗顺序提供 normalized title 作为 alias 去重参照。

## 风险

- alias 过度删除可能影响后续搜索召回；本切片只删除与主标题完全一致的项和 alias 内部完全重复项。
- 分隔符规整若过宽可能改变资源站原始别名含义；本切片只处理常见列表分隔符，不做语义解析。
- 完整 V1 handler chain 中的季数、副标题、release group 和复杂 bracket 结构仍未覆盖。

## 验收标准

```text
MARK 1/4 aliasTitle removes items equal to normalized title: done
MARK 2/4 aliasTitle deduplicates repeated alias items while preserving first-seen order: done
MARK 3/4 locked aliasTitle preserves original raw field value: done
MARK 4/4 normalization-worker tests pass: done
```

## 测试边界

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
```

## 关联文档

- `docs/reports/089-v1-golden-behavior-inventory.md` P0-05
- `tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md`
- `tasks/in-progress/028-16-a2-text-standardization-title-policy.md`

## 关联任务

- 上游父任务：`tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md`
- 前置切片：`tasks/in-progress/028-16-a2-text-standardization-title-policy.md`
- 当前切片：`tasks/in-progress/028-16-a7-title-alias-dedup-policy.md`

## 当前状态

2026-06-09：A7 第一刀完成。`RawVideoTextNormalizer` 将 alias 清洗改为以 normalized title
为参照的列表级去重：标准化 alias 文本、规整常见分隔符、去掉与主标题等价的 alias 项、去掉
alias 内部重复项并保留 first-seen 顺序；`aliasComparisonKey` 复用简繁/日中转换、标题符号规整
和中文序数归一，覆盖 `庆余年 第二季` / `庆余年 第2季` 这类 P0 fixture。

新增 `RawVideoTextNormalizerTest.aliasTitleRemovesMainTitleAndDeduplicatesItems` 和
`lockedAliasTitlePreservesRawValue`，固定 `rawMetadata.title/year/aliasTitle` 到 normalized
`title/aliasTitle` 的 golden 行为，并证明 `locked_fields.aliasTitle` 继续压制 alias 清洗。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 18s
```

剩余风险：

- 完整 V1 title handler chain 仍未 1:1，特别是 season/subtitle/mainTitle 字段拆分、release group
  识别、复杂 bracket 结构和多语言 alias 语义。
- 本切片只做完全等价项删除，不做 fuzzy alias 去重，避免误删搜索召回用别名。
