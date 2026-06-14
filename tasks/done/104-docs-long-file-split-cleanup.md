# 104 docs long file split cleanup

状态：已完成

## 背景

用户要求当前批次功能任务执行完后暂停推进，清理 `docs/` 中超长文件并拆分。

## 目标

将最长的内容型 Markdown 拆成可维护的导航页和专题页，降低后续回写冲突和阅读成本。

## 非目标

- 不推进新的 V1/V3 功能 parity 切片。
- 不修改业务代码、部署配置或 smoke 脚本。
- 不删除历史证据。

## 上游来源

- `docs/reports/104-docs-long-file-split-cleanup.md`
- 用户指令：这批任务完成后先暂停，清理 docs 超长文件并拆分。

## 输入

- `docs/k8s/current-environment-2026-06-02.md`
- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`
- `docs/index.md`

## 输出

- 拆分后的专题 Markdown 文件。
- 保留原路径导航页。
- 更新后的文档索引。

## 影响范围

仅 `docs/**` 与 `tasks/**`。

## 验收标准

- 原三个超长文件缩短为导航页。
- 新文件按主题保存完整历史内容。
- `docs/index.md` 能发现拆分后的文件。
- 当前任务状态回写为完成或保留明确遗留项。

## 测试边界

执行只读统计命令复查拆分后行数；不运行服务测试。


## 完成结果

- 已拆分 K8S current environment、089 golden inventory report、089 migration、025 route coverage report/requirement。
- 原路径均保留为导航页，新专题文件已补入 `docs/index.md`。
- 最长内容型文件已降到约 436 行；`docs/index.md` 作为入口索引保留。
- 未修改业务代码、部署配置或 live K8S 状态。
