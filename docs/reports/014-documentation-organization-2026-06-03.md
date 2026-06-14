# 014 文档目录整理报告

状态：完成

更新时间：2026-06-03

## 背景

用户要求先读取仓库根目录 `AGENTS.md`，再整理本地文档。`AGENTS.md`
要求当前项目默认采用本地优先协作流程，并推荐使用：

```text
docs/requirements
docs/reports
docs/decisions
docs/migrations
tasks/backlog
tasks/in-progress
tasks/done
```

## 本轮整理内容

- 将历史旧单数迁移目录调整为 `docs/migrations/`。
- 修正 `docs/`、`tasks/`、`deploy/k8s/` 中的迁移目录引用。
- 新增 `docs/index.md` 作为文档总入口。
- 新增 `tasks/index.md` 作为任务总入口。
- 新增本文档记录本轮整理范围、风险和验证方式。
- 新增已完成任务 `tasks/done/014-01-documentation-organization.md`。

## 范围

本轮包含：

- 文档目录命名规范化。
- 文档索引补齐。
- 任务索引补齐。
- 已知资源 profile 文档修正。

本轮不包含：

- 服务代码实现。
- K8S 资源重新部署。
- 历史英文文档全文翻译。
- 新增或关闭任何 HTTPRoute。

## 风险

- 历史迁移文档中仍有部分英文内容，和 `AGENTS.md` 的简体中文要求不完全一致。
- `ircs-task-service` 已在后续 `013` 切片中完成只读 MVP 接入；文档入口已补入对应迁移记录。
- 本轮没有触碰真实 K8S，因此 K8S 快照仍以最近一次实测记录为准。

## 验证方式

- 使用 `rg` 检查旧路径 `docs/migration` 是否仍存在。
- 使用 `rg --files` 检查 `docs/migrations/`、`docs/reports/` 和任务索引是否存在。
- 只做文档整理，不执行 Gradle build、镜像推送或 K8S rollout。

## 后续建议

- 继续按 backlog 推进 identity、ingestion、scraper、portal、interaction 和 magnet 等后续服务。
- 可单独开一个 backlog 子任务，把历史英文迁移文档逐步改写成简体中文。
- 多 subagent 并行推进前，先在 `tasks/index.md` 中分配互不重叠的服务写入范围。
