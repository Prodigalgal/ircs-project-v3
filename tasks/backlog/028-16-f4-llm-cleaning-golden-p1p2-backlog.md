# 028-16-F4 LLM cleaning golden P1/P2 backlog

状态：backlog

父任务：

- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-e-llm-cleaning-gated-parity.md`

## 背景

089 将 LLM cleaning 列为 P1：V1 使用 Spring AI/OpenAI 与 cleaning scheduler 处理 language、area、
genre、category raw value；V3 目前主要是 dry-run 或缺真实 cleaning service。该能力涉及真实凭据、
成本和数据写入，必须 fake-first、gated-first。

## 目标

- 定义 raw dictionary cleaning 的输入、输出、状态流转、幂等和 audit 语义。
- 先实现 fake transport / fixture contract，再接真实 OpenAI-compatible provider gate。
- 明确 language/area/genre/category cleaning scheduler 与人工映射的优先级。
- 准备统一测试批次中的 no-real-cost smoke 和可选真实单条 smoke。

## 非目标

- 不默认调用真实 LLM。
- 不做大批量 raw dictionary 清洗。
- 不把 provider key 写入文档、代码或非 secret 配置。

## 影响范围

```text
services/ircs-normalization-worker/**
services/ircs-config-service/**
services/ircs-credential-service/**
services/ircs-ops-service/**
docs/reports/089-v1-golden-behavior-inventory*.md
tasks/in-progress/028-16-e-llm-cleaning-gated-parity.md
```

## 依赖

- credential-service 对 provider key 的 internal lease 能力。
- SystemConfig/RuntimeInjectedConfig 的 env-first gate。
- worker_job_audit_events 或等价 ops 可观察性。

## 风险

- 真实 LLM 调用有成本、速率限制和输出漂移。
- cleaning 输出可能覆盖人工映射或引入错误标准字典关系。
- scheduler 若默认开启会产生不可控外部调用。

## 验收标准

```text
MARK 1/6 cleaning input/output/status contract documented
MARK 2/6 fake-first transport and deterministic fixture tests pass
MARK 3/6 scheduler gate defaults off and audit visibility defined
MARK 4/6 credential/config precedence and redaction verified
MARK 5/6 no-real-cost focused tests pass
MARK 6/6 optional one-row live smoke gated and cleanup-safe, or explicitly deferred
```

## 统一测试边界

纳入统一测试批次 `UTB-02816-P1P2-02`。默认只跑 fake/no-cost tests、scheduler disabled smoke、
audit query 和 DB residue 检查；真实 LLM 单条 smoke 需要单独确认凭据、allowlist 和成本边界。

## 2026-06-10 no-real-cost implementation closure

状态：implementation closure reached；live smoke/真实 LLM 调用仍后置到 `UTB-02816-P1P2-02`。

- 补齐 input/output/status/idempotency/audit contract：disabled/dry-run/live missing credential/provider error 使用固定 reason；runner audit 仅记录状态 reason，不记录 prompt、request body 或 credential。
- `fake` path 继续使用 deterministic fixture client，不解析 runtime/credential-service secret，不调用 live client。
- raw language/area/genre/category 写入增加 idempotency guard：仅当 raw row 当前仍未映射时才 apply match/noise；category noise 只在 `category_id is null` 时映射到 `其他`。
- repository 返回实际变更结果，service 只对真实变更计入 `mapped` / `noise`，避免候选读取后人工映射并发写入时被 LLM cleaning 覆盖或夸大计数。
- `LlmCredential.toString()` 脱敏 api key，降低日志/断言误打印风险。

本轮验证：

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --tests "com.prodigalgal.ircs.normalization.LlmCleaning*"
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test
```

后置 live 项：

- K8S dry-run runner smoke、audit query 和 DB residue 检查。
- staging-only live 单条 fixture，需显式确认 provider credential、allowlist 和成本边界。

## 2026-06-11 UTB-02816-P1P2-02 no-real-cost execution

`UTB-02816-P1P2-02` 已执行 LLM cleaning fake/no-cost focused regression，仍未触发真实 LLM 调用。

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-02 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\utb-02816-p1p2-02-20260611.json
```

结果：

```text
LLM cleaning F4/E fake/no-cost focused regression: PASSED
allowFlags: providerExternal=false, smtpSend=false, r2Write=false
```

当前 MARK 口径：

```text
MARK 5/6 no-real-cost focused tests pass: done by UTB-02816-P1P2-02
MARK 6/6 optional one-row live smoke gated and cleanup-safe, or explicitly deferred: still deferred
```
