# 028-16-A6 genre synonym/suffix/protected-term policy task spec

状态：已完成第一刀

父任务：

- `tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md`
- `tasks/in-progress/028-16-a5-area-language-composite-alias-policy.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md` 的 P0-04 / P0-05
- `docs/reports/088-v1-v3-deep-parity-gap-audit.md` 的 G5
- V1 `StandardGenreMappingPolicy`
- V3 `RawVideoTextNormalizer`
- V3 `CatalogDefaultSeedCatalog` standard genre seed

## 背景

`028-16-A5` 已在 normalization-worker 内完成 area/language 复合 alias 第一刀，
但 P0-04 仍记录 genre synonym/suffix/protected-term policy 缺口。当前 V3 raw genre
只依赖 `genreNames` / `genres` / `genre` 的原始 token 和 `standard_genre.name` 精确命中。
资源站常见 `古装剧`、`都市剧`、`悬疑片`、`纪录片`、`微短剧`、繁体 `冒險` 等 token
可能无法命中现有标准题材或 raw alias；同时 `短剧`、`韩剧`、`美剧` 这类本身就是标准题材
的词不能被通用 `剧` 后缀误剥离。

## 目标

- 在 normalization-worker 内复用 service-scoped `RawRelationAliasPolicy`，新增 genre alias 展开。
- 对 P0 常见同义词做小范围映射，例如 `冒险` / `冒險`、`记录` / `纪录片`、`微短剧`。
- 对明确 genre 后缀做保守剥离，例如 `古装剧`、`都市剧`、`悬疑片`。
- 对 protected term 保持原 token，不做破坏性剥离，例如 `短剧`、`韩剧`、`美剧`、`日剧`。
- 保留原始 raw token，追加可命中或可追溯 alias token，避免破坏 raw dictionary 追溯。
- `locked_fields` 中的 `genres` 继续完全压制 raw genre 输出。

## 非目标

- 不改 catalog-service seed、standard table 或 content config。
- 不做完整 V1 `StandardGenreMappingPolicy` 全量规则迁移。
- 不做完整 title pipeline、full NLP、LLM cleaning 或 live smoke。
- 不改 BFF/shared outbound C3、R2、SMTP、metadata-worker、aggregation-worker。

## 输入

```text
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoTextNormalizer.java
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawRelationAliasPolicy.java
services/ircs-normalization-worker/src/test/java/com/prodigalgal/ircs/normalization/RawVideoTextNormalizerTest.java
```

## 输出

```text
normalization-worker genre raw relation alias policy
genre synonym/suffix/protected-term golden fixture
locked fields regression fixture
```

## 影响范围

```text
services/ircs-normalization-worker/**
tasks/in-progress/028-16-a6-genre-synonym-suffix-protected-term-policy.md
```

## 依赖

- 复用 normalization-worker 已有 NFKC、HanLP 简繁转换和 opencc4j 日式汉字转换。
- 运行时仍依赖 catalog-service 已写入的 `standard_genre` 与 raw genre alias seed 做最终标准映射。

## 风险

- 通用 `剧` / `片` 后缀剥离可能误伤标准题材；本切片通过 protected term 限制。
- `都市` 当前不在本仓库 `CatalogDefaultSeedCatalog` standard genre seed 中，本轮只追加 token，
  不强行补 seed 或映射到其他题材。
- 只覆盖 P0 fixture，不代表完整 V1 genre policy 已迁移。

## 验收标准

```text
MARK 1/4 genre suffix token emits original raw token plus V1-compatible alias tokens
MARK 2/4 genre synonym token emits original raw token plus canonical alias tokens
MARK 3/4 locked genres fields suppress synonym/suffix/protected-term expansion
MARK 4/4 normalization-worker tests pass
```

## 测试边界

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
```

## 当前状态

2026-06-09：已完成任务边界固定，准备进入 normalization-worker focused fixture 和实现。

2026-06-09：已完成第一刀实现。

实现结果：

- `RawVideoTextNormalizer` 对未锁定的 `genreNames` / `genres` / `genre` raw relation
  调用 `RawRelationAliasPolicy.expandGenres`。
- `RawRelationAliasPolicy` 新增 genre synonym/suffix/protected-term 小策略：
  - synonym：`记录`、`记录片`、`纪录片` 追加 `纪录`；`微短剧` 追加 `短剧`；
    `动漫` 追加 `动画`。
  - suffix：`古装剧`、`都市剧`、`悬疑片` 等保留原 token，并追加剥离后的 token。
  - protected term：`短剧`、`韩剧`、`美剧`、`日剧`、`港剧`、`台剧`、`泰剧`、
    `英剧`、`内地剧`、`国产剧`、`短片` 等不做破坏性后缀剥离。
- 新增 golden fixture 覆盖 `古装剧/都市剧/悬疑片/纪录片/纪录/微短剧/动漫/短剧`
  的 raw token 保留与 alias 追加行为。
- 新增 locked regression fixture，确认 `locked_fields` 中 `genres` 仍返回空 raw genre set，
  不触发 synonym/suffix/protected-term 展开。

验收标准：

```text
MARK 1/4 genre suffix token emits original raw token plus V1-compatible alias tokens: done
MARK 2/4 genre synonym token emits original raw token plus canonical alias tokens: done
MARK 3/4 locked genres fields suppress synonym/suffix/protected-term expansion: done
MARK 4/4 normalization-worker tests pass: done
```

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 18s
```

遗留：

- 本轮没有迁移完整 V1 `StandardGenreMappingPolicy` 全量 synonym/protected-term 表。
- 本轮没有做 HanLP genre NLP、LLM cleaning、raw dictionary 后处理或 live smoke。
- `都市` 当前只作为追加 raw token 输出，本仓库默认 `standard_genre` seed 未包含 `都市`，
  是否补 seed 或映射到其他题材需后续独立决策。
- 完整 title handler pipeline、location NLP 和 queue live smoke 仍由 A 线后续切片承接。
