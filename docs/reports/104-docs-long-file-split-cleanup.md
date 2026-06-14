# 104 docs long file split cleanup

状态：进行中

日期：2026-06-09

## 背景

当前 `docs/` 中部分 Markdown 同时承载总账、历史证据和 rollout 明细，单文件过长，不利于检索、审阅和后续 subagent 回写。

本次只做文档维护，不推进新功能切片，不修改业务代码、部署配置或 smoke 脚本。

## 范围

优先拆分当前最长的内容型文档：

- `docs/k8s/current-environment-2026-06-02.md`
- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`

暂不强拆 `docs/index.md`。它虽偏长，但作为入口索引承担导航职责，本次只补充必要链接。

## 拆分原则

- 原路径保留为导航页，避免旧引用失效。
- 历史证据不删除，只移动到同目录或子目录的专题文件。
- 拆分后每个专题文件尽量保持单一主题：环境基线、rollout 日志、gap inventory、MARK 明细、迁移记录等。
- 更新 `docs/index.md` 和相关入口，保证新文件可发现。

## 验收标准

- 超长源文件显著缩短。
- 新拆分文件可被索引发现。
- 原路径仍能说明内容迁移位置。
- 不修改业务代码和 K8S live 状态。


## 执行结果

- `docs/k8s/current-environment-2026-06-02.md` 已改为导航页，历史内容拆到 base、rollout 和二级 detail 文件。
- `docs/reports/089-v1-golden-behavior-inventory.md` 已改为导航页，拆出 overview、module notes、MARK ledger、next steps，以及 aggregation B1/B9 明细。
- `docs/migrations/089-v1-golden-behavior-inventory.md` 已改为导航页，拆出 records、MARK、remaining。
- `docs/reports/025-v1-v3-route-coverage-audit.md` 与 `docs/requirements/025-v1-route-coverage-closeout.md` 已改为导航页，拆出历史明细。
- `docs/index.md` 与 `tasks/index.md` 已更新。

## 验证

- 已复查 `docs/**/*.md` 行数排序。当前最长内容型文件已降到约 436 行；`docs/index.md` 作为入口索引保留。
- 未修改业务代码、部署配置或 live K8S 状态。

更新时间：2026-06-09 23:38:10
