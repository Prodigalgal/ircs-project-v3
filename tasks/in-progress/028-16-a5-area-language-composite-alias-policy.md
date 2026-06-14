# 028-16-A5 area/language composite alias policy task spec

状态：已完成第一刀

父任务：

- `tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md` 的 P0-04
- V1 `AreaMappingPolicy`
- V1 `RawLanguageServiceImpl`
- V3 `RawVideoTextNormalizer`
- V3 `CatalogDefaultSeedCatalog` P0 area/language raw alias seed

## 背景

`028-16-A2` 和 `028-16-A4` 已完成 text/title/description NER 第一刀，但 P0-04 中
language/area mapping 仍只依赖 raw relation split 和 catalog exact/raw alias seed。资源站常见
`港台`、`日韩欧美`、`国粤双语`、`日语中字`、`日本地区` 这类复合或带后缀 token 会作为单个
raw value 写入，可能无法命中现有 raw alias 或 standard exact mapping，导致后续 aggregation
无法重建标准地区/语言关系。

## 目标

- 在 normalization-worker 内新增 service-scoped raw relation alias policy 第一刀。
- 对 area 复合 token 展开 V1 常见 P0 组合：`港台`、`港澳台`、`日韩`、`日韩欧美`、`欧美`。
- 对 area 长 token 去掉常见后缀后补可命中 token，例如 `日本地区` -> `日本`。
- 对 language 复合/字幕后缀 token 展开 V1 常见 P0 组合：`国粤双语`、`粤语中字`、`日语中字`。
- 保留原始 raw token，新增可命中 alias token，避免破坏 raw dictionary 可追溯性。
- locked `area` / `language` 字段仍完全压制对应 raw relation 输出。

## 非目标

- 不改 catalog-service seed、standard table 或全局初始化策略。
- 不实现完整 V1 NLP 地名识别。
- 不实现 genre synonym/suffix/protected-term policy。
- 不改 title handler chain、description NER、aggregation 或 search sync。
- 不做 K8S live smoke，本切片先以 normalization-worker focused golden fixture 固定行为。

## 输入

```text
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/RawVideoTextNormalizer.java
services/ircs-normalization-worker/src/test/java/com/prodigalgal/ircs/normalization/RawVideoTextNormalizerTest.java
```

## 输出

```text
normalization-worker raw relation alias policy
area/language composite alias golden fixture
locked fields regression fixture
```

## 影响范围

```text
services/ircs-normalization-worker/**
tasks/in-progress/028-16-a5-area-language-composite-alias-policy.md
tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md
```

## 依赖

- 复用 normalization-worker 已有 HanLP/opencc4j/NFKC 标准化。
- 依赖 catalog-service 已有 P0 raw alias seed 在运行时提供 raw value -> standard relation 映射。

## 风险

- 过度 canonicalize 会破坏 raw dictionary 追溯；本切片只追加 alias token，不删除原始 token。
- 单字 alias 容易误判；本切片只处理明确组合或后缀，不做任意包含匹配。
- `日韩欧美` 的欧美语义在当前 P0 standard area seed 中只能安全落到 `美国`，完整欧洲国家拆分后续再做。

## 验收标准

```text
MARK 1/4 area composite token emits original raw token plus V1-compatible alias tokens: done
MARK 2/4 language composite/suffix token emits original raw token plus V1-compatible alias tokens: done
MARK 3/4 locked area/language fields suppress composite alias expansion: done
MARK 4/4 normalization-worker tests pass: done
```

## 测试边界

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
```

## 当前状态

2026-06-09：已完成任务边界固定，准备进入 normalization-worker focused fixture 和实现。

2026-06-09：已完成第一刀实现。

实现结果：

- 新增 `RawRelationAliasPolicy`，只在 normalization-worker service scope 内处理 raw relation
  token alias 展开。
- area 复合 token 支持 `港澳台`、`港台`、`日韩`、`日韩欧美`、`欧美`，并保留原始 raw token。
- area 后缀支持 `地区`、`剧`、`片` 的保守剥离，例如 `日本地区` 同时输出 `日本地区` 和 `日本`。
- language 复合 token 支持 `国粤双语`、`国语粤语`、`粤语国语`，并保留原始 raw token。
- language 后缀支持 `中字`、`字幕`、`配音` 的保守剥离，例如 `日语中字` 同时输出
  `日语中字` 和 `日语`。
- locked `area` / `language` 字段继续返回空 raw relation set，不触发 alias 展开或 join row
  replace。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 16s
```

遗留：

- 本轮没有实现完整 V1 NLP 地名识别。
- 本轮没有实现 genre synonym/suffix/protected-term policy。
- `日韩欧美` 的 `欧美` 当前只保守追加 `美国`，完整欧洲/地区拆分需要等标准 area seed 和 V1
  countries policy 对齐后再做。
