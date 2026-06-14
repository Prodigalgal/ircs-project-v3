Always respond in Chinese (Simplified).

# AGENTS.md

本文件用于给 coding agent 提供最小必要项目规则。
它不是完整项目管理制度；普通开发任务优先完成代码、验证和清晰交付。

## Communication

- 默认使用简体中文回复。
- 专有名词、接口名、类名、变量名、命令保持英文。
- 结论要直接、可执行；未验证的内容必须明确说明。

## Work Priority

默认优先级：

1. 理解当前代码和真实运行状态。
2. 做最小必要修改。
3. 运行相关测试、构建或 smoke 验证。
4. 在最终回复中说明改动、验证结果和遗留风险。
5. 只有在需要沉淀长期决策时才更新文档。

## Task Levels

按任务影响范围选择流程：

- S0 快速任务：
  - 适用：单文件修复、小 bug、明确命令、简单排查、文案调整。
  - 流程：直接查、改、跑、验。
  - 不创建或更新 `docs/`、`tasks/`。

- S1 小功能：
  - 适用：多文件但低风险，不改架构、不改数据契约、不影响部署。
  - 流程：可直接实现。
  - 如需记录，只在 `tasks/current.md` 增加一条短记录。

- S2 正常功能：
  - 适用：跨模块功能、API 变化、数据库字段、权限、前后端联动。
  - 流程：实现前先写一个 compact task spec。
  - task spec 只需包含：目标、范围、非目标、影响文件、验收标准、测试方式。

- S3 长线/高风险任务：
  - 适用：架构迁移、微服务边界、生产部署、数据迁移、安全/合规、长期规划。
  - 流程：需要 `docs/requirements/`、`docs/decisions/` 或 ExecPlan。
  - 这类任务才使用父任务、子任务、状态归档和完整回写。

## Documentation Rules

- 不要为了满足流程而重复描述代码已经表达清楚的内容。
- 文档只记录：
  - 新需求边界
  - 架构决策
  - 数据/API 契约
  - 迁移步骤
  - 验证结果
  - 遗留风险
- 普通任务完成后，不强制移动 task 文件。
- 长线任务完成后，才同步 `tasks/backlog/`、`tasks/in-progress/`、`tasks/done/`。

## Repository Map

- `docs/requirements/`：长期需求或高风险需求。
- `docs/decisions/`：架构决策记录。
- `docs/reports/`：调查报告、性能分析、事故分析。
- `docs/migrations/`：迁移记录。
- `tasks/current.md`：轻量当前任务板。
- `tasks/backlog/`、`tasks/in-progress/`、`tasks/done/`：仅用于 S3 长线任务。

## Development Rules

- 修改前先检查当前工作树，不能覆盖用户已有改动。
- 优先遵循现有代码风格和模块边界。
- 不做无关重构。
- 能用现有 helper、配置、测试入口时，不新增平行体系。
- 交付前尽量运行最相关的测试或构建；无法运行时说明原因。

## Subagents

- 只有在任务边界清晰、写入范围不重叠、结果可独立验证时使用 subagents。
- subagent 适合调查、并行实现独立模块、测试验证。
- subagent 返回内容应简短：结论、关键文件、验证命令、风险。
- 主 agent 负责集成、冲突处理和最终验收。

## Stop Conditions

遇到以下情况先暂停并说明：

- 需求边界会影响架构或数据契约，但用户没有确认。
- 需要生产凭据、外部 SaaS 写入或破坏性操作。
- 当前工作树存在会被本任务覆盖的用户改动。
