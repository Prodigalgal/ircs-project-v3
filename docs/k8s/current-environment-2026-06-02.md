# K8S current environment snapshot - 2026-06-02

状态：已拆分为导航页

本文件原先同时包含环境基线、冲突控制和 2026-06-07/08 rollout 明细，单文件过长。历史内容已按主题拆分，原路径保留为入口，避免旧引用失效。

## 拆分入口

- [环境基线与冲突控制](current-environment-2026-06-02-base.md)
- [2026-06-07 rollout log](rollouts/current-environment-2026-06-07-rollouts.md)
- [2026-06-08 rollout log](rollouts/current-environment-2026-06-08-rollouts.md)

## 维护说明

- 新的 live 环境快照优先新增独立日期文件，不再继续追加到本导航页。
- 新的 rollout/smoke 证据优先写入 `docs/reports/` 或 `docs/k8s/rollouts/` 的专题文件。
- 资源预算与回滚护栏见 [dev-staging-resource-budget-and-rollback-runbook.md](dev-staging-resource-budget-and-rollback-runbook.md)。
