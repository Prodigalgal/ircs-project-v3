# 028-05 queue consumer deep side-effect parity 审计子任务

状态：已完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/041-v1-v3-behavior-parity-gap-audit.md`
- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/queue/QueueTopic.java`

## 背景

HTTP route missing 已归零，但队列消费者和 producer 副作用仍需要单独证明。V1 单体里
很多接口成功后依赖异步任务继续落库、写索引、同步媒体或发送通知；V3 如果只复现同步
HTTP contract，就会出现“接口可用但内部功能不完整”的假完成。

## 目标

- 建立当前 `QueueTopic` 到 owner、consumer、producer、验证证据的审计表。
- 标记已完成、部分完成、未完成和需复核的 queue parity 项。
- 产出后续可独立实现的 task spec 候选。

## 非目标

- 不在本子任务实现新的 consumer。
- 不修改各业务服务代码。
- 不执行 K8S rollout。
- 不与 `028-02`、`028-03`、`028-04` 的写入范围重叠。

## 输入

- `QueueTopic`、`QueueOwnership`。
- 各服务 `@RabbitListener`、`RabbitTemplate` producer。
- 已有迁移记录和 smoke 结果。

## 输出

- `docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md`
- 本 task spec。
- 后续 `028-05-B` 等实现子任务建议。

## 影响范围

包含：

```text
docs/reports/045-queue-consumer-deep-side-effect-parity-audit.md
tasks/done/028-05-queue-consumer-deep-side-effect-parity-audit.md
```

不包含：

```text
services/**
deploy/**
scripts/**
docs/index.md
tasks/index.md
```

## 依赖

- 当前 `QueueTopic` 是队列契约权威。
- 当前 docs/task/smoke 记录可作为历史证据，但实现完成必须以后续 live smoke 为准。

## 风险

- 文档中历史 smoke 不一定覆盖 V1 全量副作用，不能把“队列有 consumer”直接判定为 1:1。
- 多个 subagent 正在并行实现其他 028 子任务，本审计必须避免写入冲突。

## 验收标准

- 队列审计表覆盖所有 `QueueTopic.values()`。
- 每项都有 owner、当前证据和 parity 状态。
- 明确列出高优先级后续任务。
- 不改动代码、不触发部署。

## 测试边界

本任务为文档/审计任务，不运行 Gradle 或 K8S smoke。后续实现子任务必须单独定义测试。

## 当前状态

2026-06-07：

- 已完成 16 个 `QueueTopic` 的 owner 和当前 evidence 初筛。
- `028-05-A interaction watch-progress consumer parity` 已完成，记录 `MARK 4/4`。
- `028-05-B storage avatar async sync consumer parity` 已完成，记录 `MARK 4/4`。
- `028-05-C metadata provider fan-out parity` 已完成，fan-out routing 本地测试通过。
- `028-05-D raw/unified search producer matrix parity` 已完成，content image unlink 搜索刷新
  live smoke 记录 `MARK 2/2`。
- `028-05-E metadata provider worker and enrichment result parity` 已完成，记录 `MARK 4/4`。
- `028-05-F normalization raw relation parity` 已完成，记录 `MARK 4/4`。
- `028-05-G metadata pending gather parity` 已完成，记录 `MARK 4/4`。
- `028-05-H storage image download parity` 已完成本地实现、单元测试和 live smoke，
  记录 `MARK 5/5`。
- `028-05-I mail notification disabled/failure parity` 已完成，记录 `MARK 4/4`。
- 第一阶段 queue consumer deep side-effect parity 审计已完成；后续生产级或外部集成能力
  已拆入 backlog。

```text
028-06 real provider live parity
028-07 R2 live and watchdog parity
028-08 normalization deep mapping parity
028-09 search producer live smoke parity
028-10 production governor and credential pool parity
028-12 service audit rollout and worker job audit
```
