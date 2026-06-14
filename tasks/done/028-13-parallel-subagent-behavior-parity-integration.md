# 028-13 parallel subagent behavior parity integration

状态：已完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/077-028-behavior-parity-parallel-subagent-slices.md`

## 背景

用户要求在继续推进 V1 到 V3 行为级 1:1 迁移时，将模块任务拆给多个 subagent，
每个 subagent 使用 `gpt-5.5` + `xhigh`。当前 HTTP Controller / BFF route missing 已归零，
`028-12` service handler audit 已推进到 task-service，代码侧剩余未接入
`ServiceRequestAuditFilter` 的 HTTP service 为 `ircs-ops-service` 与 `ircs-scraper-service`。
本任务用于固定该轮 subagent ownership，并由主线程完成集成、rollout、live smoke 和公共账本回写。

## 目标

- 将剩余 handler audit 与外部依赖硬缺口拆给互不重叠的 subagent。
- 明确每个 subagent 的写入范围、非目标、测试边界和主线程集成边界。
- 主线程统一审查、合并、rollout、live smoke 和公共文档回写。

## 非目标

- 不让 subagent 同时修改公共索引、父任务、K8S 快照或同一服务目录。
- 不让 subagent 执行 `ircs-dev` rollout、push ARM64 镜像或清理共享队列。
- 不在缺少 credential / staging gate 时伪造 TMDB、R2、SMTP 成功。

## 当前 subagents

```text
Ohm       worker    gpt-5.5 xhigh    scraper-service handler audit
Ampere    worker    gpt-5.5 xhigh    ops-service handler audit
Descartes explorer  gpt-5.5 xhigh    provider/R2/SMTP/worker audit 剩余硬缺口拆分
```

## 输入

- 当前 worktree。
- `AGENTS.md`。
- `docs/reports/077-028-behavior-parity-parallel-subagent-slices.md`。
- 各服务已有 audit 模式和 `028` 任务文档。

## 输出

- subagent 各自服务切片的代码、测试、报告、迁移记录或只读分析结果。
- 主线程整合后的公共索引、父任务、K8S 快照和 MARK。

## 影响范围

主线程允许写入：

```text
docs/reports/077-028-behavior-parity-parallel-subagent-slices.md
tasks/done/028-13-parallel-subagent-behavior-parity-integration.md
docs/index.md
tasks/index.md
docs/requirements/028-v1-v3-behavior-parity-closeout.md
docs/k8s/current-environment-2026-06-02.md
tasks/in-progress/028-v1-v3-behavior-parity-parent.md
tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md
```

## 依赖

- subagent worker 返回可审查的改动和测试结果。
- `ircs-dev` 当前保持低资源配额，rollout 只能复用已有 Deployment。
- scraper-service 若无只读 endpoint，live smoke 必须改用明确安全的负向 handler 请求或只做本地验证。

## 风险

- 并行 worker 若修改公共索引会造成冲突，因此公共账本只由主线程回写。
- `scraper-service` 的正向 endpoint 可能触发 session、抓取、RabbitMQ publish 或 refetch，必须避免。
- `ops-service` request audit endpoint 已被 filter 排除，测试时需要避免自审计噪音。

## 验收标准

- subagent ownership 与写入范围已记录。
- 主线程能从 worker 结果中确认测试命令、MARK 和变更文件。
- 通过主线程最终 rollout/live smoke 的服务，必须记录 trace、imageID、residue、HTTPRoute 和 ResourceQuota。

## 测试边界

- subagent 运行各自服务单测和 smoke parser 检查。
- 主线程执行集成后的服务单测、ARM64 push、rollout、live smoke 与后置检查。

## 集成结果

```text
scraper-service:
MARK 1/4 scraper-service handler request writes request_source
MARK 2/4 scraper-service audit redacts query token/password
MARK 3/4 scraper-service audit excludes Authorization Cookie body and response content
MARK 4/4 scraper-service audit filter skips actuator noise
live MARK 4/4, trace=02812-scraper-audit-20260608175041, residue=0

ops-service:
MARK 1/5 ops-service handler request writes request_source
MARK 2/5 ops-service audit redacts query token/password
MARK 3/5 ops-service audit excludes Authorization Cookie and response content
MARK 4/5 ops-service audit filter skips actuator noise
MARK 5/5 ops-service request-audit endpoint is not self-audited
live MARK 4/4, trace=02812-ops-audit-20260608174902, residue=0
```

## 当前状态

已完成。该轮 subagent 已完成并由主线程集成；后续并行工作应围绕 magnet real provider runner、
MAIL credential adapter/fake SMTP sink、ops runner registry 和 storage-service R2 path smoke
重新拆分。
