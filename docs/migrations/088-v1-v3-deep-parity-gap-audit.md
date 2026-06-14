# 088 V1/V3 功能与库细节深层 parity 缺口审计迁移记录

状态：进行中

日期：2026-06-08

## 迁移内容

新增 `028-16` 深层 parity 审计范围，将 HanLP/Tika 缺口扩展为 V1/V3 功能与库细节
逐项差异审计，重点覆盖清洗、聚合、依赖、初始化器、工具库、文件解析和 gated LLM cleaning。

## 范围

```text
docs/reports/088-v1-v3-deep-parity-gap-audit.md
docs/migrations/088-v1-v3-deep-parity-gap-audit.md
tasks/in-progress/028-16-v1-v3-deep-parity-gap-audit-parent.md
tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md
tasks/in-progress/028-16-b-aggregation-graph-pipeline-parity.md
tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md
tasks/in-progress/028-16-d-tika-file-parsing-security-parity.md
tasks/in-progress/028-16-e-llm-cleaning-gated-parity.md
tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md
```

## 期望结果

```text
MARK 1/5 V1/V3 dependency and initializer gap matrix created
MARK 2/5 V1/V3 normalization cleaning strategy gap matrix created
MARK 3/5 V1/V3 aggregation graph/pipeline gap matrix created
MARK 4/5 Tika/HanLP/OpenCC/LLM/CQEngine/JGraphT gaps have service-scoped tasks
MARK 5/5 docs/tasks/index and 028 parent requirements reflect deep parity remaining work
```

## 回滚边界

本轮仅新增文档和任务，不修改业务代码、K8S、镜像、数据库或队列。若后续实现发现切片过大，
可以保留本审计报告，将单个子任务拆小进入 backlog。

## 当前状态

已创建第一轮审计报告和 task spec。并行只读审计 agent 已补充清洗、聚合和依赖细节，
主线程已合并到 `028-16` 与 `089` golden behavior inventory。用户进一步要求覆盖所有功能
边角细节，已新增 V1 golden behavior inventory 子任务，并已启动 A1/A2/A3/B1 首批 P0
实现切片。
