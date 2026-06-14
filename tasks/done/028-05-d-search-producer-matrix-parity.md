# 028-05-D raw/unified search producer follow-up parity 子任务

状态：已完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- `tasks/done/028-05-queue-consumer-deep-side-effect-parity-audit.md`

上游来源：

- `docs/reports/049-search-producer-matrix-parity-analysis.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- `docs/reports/044-ops-maintenance-runner-parity-analysis.md`
- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`

## 背景

`028-05` 已经把 `SEARCH_SYNC_RAW` / `SEARCH_SYNC_UNIFIED` 标记为需要继续补全
producer 覆盖的队列项。本子任务负责建立 producer matrix，确认 raw/unified 搜索读模型
相关写操作是否发布对应 search sync 消息；如发现低风险、边界清晰的 producer 缺口，
可以在限定服务内做最小修补。

## 目标

- 建立 raw/unified search sync producer matrix。
- 覆盖 content、metadata result、normalization、aggregation、ops runner。
- 标出每类写操作是否发布 `SEARCH_SYNC_RAW` / `SEARCH_SYNC_UNIFIED`。
- 对一个清晰低风险缺口执行最小实现和测试，或明确本轮仅审计。

## 非目标

- 不修改 search-service consumer。
- 不修改 `docs/index.md`、`tasks/index.md`、`docs/k8s/current-environment-2026-06-02.md`。
- 本子任务初始阶段不执行 K8S rollout；主 agent 集成阶段允许对已修复缺口执行 rollout 和 smoke。
- 不重做 ops runner `028-04` 已完成内容。
- 不扩展到与 search sync publisher 无直接关系的业务重构。

## 输入

- 当前 V3 `QueueTopic.SEARCH_SYNC_RAW` / `SEARCH_SYNC_UNIFIED` 契约。
- 各服务 `RabbitTemplate` producer。
- 已有单元测试、迁移记录和 smoke 报告。

## 输出

- `docs/reports/049-search-producer-matrix-parity-analysis.md`
- `tasks/done/028-05-d-search-producer-matrix-parity.md`
- 可选：`services/ircs-content-service/**` 或
  `services/ircs-aggregation-worker/**` 内与 search sync publisher 直接相关的最小代码/测试。

## 影响范围

包含：

```text
docs/reports/049-search-producer-matrix-parity-analysis.md
tasks/done/028-05-d-search-producer-matrix-parity.md
services/ircs-content-service/**
services/ircs-aggregation-worker/**
```

不包含：

```text
services/ircs-search-service/**
deploy/**
scripts/**
docs/index.md
tasks/index.md
docs/k8s/current-environment-2026-06-02.md
```

## 依赖

- producer audit 以当前代码为准。
- search-service consumer 已存在，本任务只读不改。
- 若实现缺口，优先选择单元测试可覆盖且不需要 live K8S 的切片。

## 风险

- raw/unified 搜索字段来源分散，遗漏可能藏在 content admin、image unlink、normalization、
  metadata result 或 aggregation 链路里。
- 并行 agent 可能正在更新其他 `028` 子任务，本任务必须保持写入范围窄。
- producer 补发消息会触发 search-service upsert/delete；本轮只做低风险、可控的消息发布。

## 验收标准

- Producer Matrix 每行都有证据文件和 parity 结论。
- 若有代码改动，相关模块测试通过。
- 明确记录未覆盖或需要后续 live smoke 的风险。
- 不触碰禁止文件；主 agent 集成阶段可补最小 K8S rollout 和 smoke。

## 测试边界

- 文档审计使用 `rg` 和文件级证据。
- content-service 缺口修补使用 `.\gradlew.bat :services:ircs-content-service:test`。
- aggregation-worker 缺口修补使用 `.\gradlew.bat :services:ircs-aggregation-worker:test`。
- 本子任务初始阶段不做 K8S rollout；主 agent 集成阶段已补 live smoke。

## 当前状态

2026-06-07：

- 已创建本地分析报告和 task spec。
- 已完成 `SEARCH_SYNC_RAW` / `SEARCH_SYNC_UNIFIED` producer matrix。
- 已确认 content、metadata result、normalization、aggregation、ops runner 的发布点。
- 已修复 content image unlink 清空 raw/unified `cover_image_id` 后不刷新搜索索引的低风险缺口。
- 已运行 `.\gradlew.bat :services:ircs-content-service:test --rerun-tasks`，结果 `BUILD SUCCESSFUL`。
- 已完成 content-service ARM64 rollout。
- 已完成 RabbitMQ/Elasticsearch live smoke。

## 实施结果

改动文件：

```text
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/image/CoverImageReferenceRepository.java
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/image/CoverImageUnlinkService.java
services/ircs-content-service/src/test/java/com/prodigalgal/ircs/content/image/CoverImageReferenceRepositoryTest.java
services/ircs-content-service/src/test/java/com/prodigalgal/ircs/content/image/CoverImageUnlinkServiceTest.java
```

行为变化：

```text
CMD_IMAGE_UNLINK
  -> 查询受影响 raw_videos.id / unified_videos.id
  -> 清空 cover_image_id
  -> transaction commit 后发布 SEARCH_SYNC_RAW DELETE -> INDEX
  -> transaction commit 后发布 SEARCH_SYNC_UNIFIED DELETE -> INDEX
  -> 发布 EVENT_IMAGE_UNLINKED
```

完成标记：

```text
MARK 1/2 search producer matrix built and content image unlink cover search sync gap fixed
MARK 2/2 content image unlink search refresh clears ES coverImageUrl in live smoke
```

## 剩余风险

- 本轮已对 content image unlink 缺口执行 K8S rollout 和 RabbitMQ/Elasticsearch live smoke。
- aggregation、normalization、metadata result 仍有 legacy UUID payload producer；search-service
  consumer 支持 legacy UUID，本切片不改 consumer。
- old unified dirty lifecycle 未在本轮扩展；如主 agent 后续要做 live smoke，可重点验证
  raw critical update 后 old unified 的刷新时机。
