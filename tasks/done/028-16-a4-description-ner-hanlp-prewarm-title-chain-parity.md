# 028-16-A4 description NER / HanLP prewarm / title chain parity task spec

状态：已完成第一刀

父任务：

- `tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- V1 `DescriptionMetadataExtractor`
- V1 `PersonHandler`
- V1 `HanLPInitializer`
- V1 title normalization handlers
- V3 `services/ircs-normalization-worker`

## 背景

`028-16-A2` 已把 normalization-worker 的文本标准化第一刀接回 HanLP/opencc4j，并覆盖
NFKC、繁简、日中、release noise、双语、自重复等 title fixture。但 V1 还有两个 P0/P1
细节未完全复刻：

- `HanLPInitializer` 在 initialization order 5 预热 segment、繁简转换和 CharTable。
- `PersonHandler` 在 actor/director 显式字段缺失时，会用 `DescriptionMetadataExtractor`
  从 description 附近的 `nr` 人名做 NER fallback。

title 方面，V1 是多 handler chain，包含 season/subtitle/mainTitle、alias 分隔等更细语义；
当前 V3 仍是集中式 `RawVideoTextNormalizer.cleanTitle()`，本任务先固定边界并补第一刀，
完整 title handler chain 继续拆后续 MARK。

## 目标

- normalization-worker 增加本地 HanLP prewarm runner，启动时预热 `HanLP.segment()`、
  `HanLP.convertToSimplifiedChinese()` 和 `CharTable.convert()`。
- 增加 description NER fallback：当 metadata 中 actor/director 显式字段为空且 raw video
  description 有值时，按 V1 关键词窗口从 HanLP term 中抽取 `nr*` 人名。
- 显式 actor/director 字段优先，不被 description NER 覆盖。
- locked `actors` / `directors` 字段仍阻止对应 relation 写入。
- 回写 089 和任务索引，标记 A4 第一刀完成情况。

## 非目标

- 不在本小刀完整重构 V1 title handler chain。
- 不新增 season/subtitle/mainTitle schema。
- 不接 LLM cleaning、fuzzy mapping 或 metadata provider 搜索策略。
- 不改 ingestion queue contract、aggregation merge 或 search sync 语义。
- 不做 K8S live smoke；本小刀先用本地 normalization-worker tests 固定行为。

## 影响范围

```text
services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/**
services/ircs-normalization-worker/src/test/java/com/prodigalgal/ircs/normalization/**
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/index.md
docs/index.md
```

## 验收标准

```text
MARK 1/5 HanLP prewarm runner exists and is locally testable: done
MARK 2/5 description NER fallback extracts director/actor names from V1 keyword windows: done
MARK 3/5 explicit actor/director metadata remains higher priority than description NER: done
MARK 4/5 locked actors/directors fields suppress corresponding NER relation writes: done
MARK 5/5 normalization-worker tests pass and docs/tasks indexes updated: done
```

## 测试边界

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
```

## 当前状态

2026-06-09：已完成只读取证。A4 第一刀只实现 HanLP prewarm 和 description NER fallback；
完整 title handler chain 继续保留为后续同任务或后续子任务。

2026-06-09：已完成第一刀实现。

实现结果：

- normalization-worker 新增 `HanLpPrewarmRunner`，启动时按 V1 `HanLPInitializer` 预热
  `HanLP.segment()`、`HanLP.convertToSimplifiedChinese()` 和 `CharTable.convert()`；失败只记录
  error，不阻断 worker 启动。
- 新增 `DescriptionMetadataExtractor`，复刻 V1 `导演|执导|监督`、`主演|饰演|出演|演员`
  关键词和 `director=3`、`actor=5` 窗口，收集窗口内 `nr*` 且长度大于 1 的人名。
- `RawVideoTextNormalizer` 在 `actorNames/actors/actor` 或
  `directorNames/directors/director` 显式字段为空时，才从 description NER fallback 补关系。
- 显式 actor/director metadata 不被 description NER 覆盖；`locked_fields` 中的
  `actors` / `directors` 继续压制对应关系写入。
- `deploy/k8s/dev/normalization-worker-dev.yaml` 显式设置
  `APP_NORMALIZATION_HANLP_PREWARM_ENABLED=true`，不新增 Pod、Service 或 HTTPRoute。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL

DescriptionMetadataExtractorTest: tests=2, failures=0, errors=0
HanLpPrewarmRunnerTest: tests=2, failures=0, errors=0
RawVideoTextNormalizerTest: tests=9, failures=0, errors=0

kubectl apply --dry-run=server -f deploy\k8s\dev\normalization-worker-dev.yaml
deployment.apps/ircs-normalization-worker configured (server dry run)
```

遗留：

- 完整 V1 title handler chain 仍未 1:1；季数/副标题/mainTitle/alias 去重继续留在 A 线后续。
- area alias/composite/NLP 地名识别、genre synonym/suffix/protected-term 策略仍未完成。
- 未做本轮 K8S live normalize smoke；只做了 manifest server dry-run。
