# 014-01 文档目录与索引整理子任务

状态：完成

父任务：

- `tasks/in-progress/011-mvp-service-onboarding-parent.md`

上游来源：

- `AGENTS.md`
- `docs/requirements/011-mvp-service-onboarding-acceleration.md`

关联文档：

- `docs/index.md`
- `tasks/index.md`
- `docs/reports/014-documentation-organization-2026-06-03.md`

## 背景

仓库根目录 `AGENTS.md` 要求本地文档使用规范目录，其中迁移记录目录为
`docs/migrations/`。当前仓库已有迁移记录位于旧单数目录，且缺少
统一文档入口和任务入口，不利于后续多 subagent 并行推进。

## 目标

- 读取并遵循根目录 `AGENTS.md`。
- 将迁移记录目录统一为 `docs/migrations/`。
- 修正文档和任务中的旧路径引用。
- 新增文档总入口和任务总入口。
- 记录本轮整理范围与验证边界。

## 非目标

- 不修改服务代码。
- 不执行 K8S 部署。
- 不新增服务接入状态。
- 不翻译全部历史英文文档。

## 输入

- `AGENTS.md`
- `docs/`
- `tasks/`
- `deploy/k8s/README.md`

## 输出

- `docs/migrations/`
- `docs/index.md`
- `tasks/index.md`
- `docs/reports/014-documentation-organization-2026-06-03.md`
- 更新后的文档路径引用。

## 影响范围

```text
docs
tasks
deploy/k8s/README.md
```

## 风险

- 历史文档仍有英文内容，需要后续单独处理。
- 目录迁移后，若外部脚本硬编码旧单数目录，需要同步修正。

## 验收标准

- 旧单数迁移目录不再作为仓库文档目录存在。
- `docs/migrations/` 包含已有迁移记录。
- `docs/index.md` 和 `tasks/index.md` 可作为后续推进入口。
- 旧路径引用被修正为 `docs/migrations/`。

## 测试边界

```text
检查异常复数拼写
Test-Path .\docs\migrations
rg --files docs tasks
```

本任务为文档整理，不执行 Gradle、Docker 或 K8S 测试。
