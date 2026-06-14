# 028-16-E LLM cleaning gated parity task spec

状态：partial，MARK 5/6；实现优先闭环已完成，live smoke、真实 LLM 调用和完整回归统一测试后置

父任务：

- `tasks/in-progress/028-16-v1-v3-deep-parity-gap-audit-parent.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/088-v1-v3-deep-parity-gap-audit.md`
- `docs/migrations/088-v1-v3-deep-parity-gap-audit.md`

## 背景

V1 具备 `AbstractLlmCleaningService` 和 genre/language/area/category 四类 LLM cleaning service，
由 `LlmCleaningScheduler` gated 执行。V3 当前没有对应服务。由于 LLM cleaning 涉及真实凭据、
成本和潜在误删，需要先做 gated/fake-first parity。

## 目标

- 建立 V3 LLM cleaning 边界：默认 disabled。
- 通过 config-service/credential-service 获取 OpenAI-compatible credential。
- fake LLM client 先复刻 V1 cleanBatch 行为：候选筛选、timestamp touch、noise 删除、standard match。
- 后续再做 staging-only live smoke。

## 非目标

- 不默认执行真实 OpenAI/LLM 调用。
- 不大批量清洗历史 raw 字典。
- 不在无审批情况下删除真实数据。

## 输入

V1：

```text
AbstractLlmCleaningService
LlmGenreCleaningService
LlmLanguageCleaningService
LlmAreaCleaningService
LlmCategoryCleaningService
LlmCleaningScheduler
OpenAILlmServiceImpl
CredentialInitializer
SystemConfigKey.LLM_ENABLED
```

V3：

```text
services/ircs-config-service/**
services/ircs-credential-service/**
services/ircs-normalization-worker/**
```

## 输出

- gated LLM cleaning design：默认 disabled，runtime ENV/K8S/启动参数优先于 DB seed/default。
- fake/dry-run client path：不触发真实外部 LLM 调用，可验证候选 touch、match/noise 决策和错误语义。
- service ownership decision：先落在 `ircs-normalization-worker`，credential 只租 OpenAI-compatible lease 或读取 runtime 配置，不输出 secret。
- 后续 staging-only smoke spec。

## 影响范围

```text
services/ircs-normalization-worker/**
services/ircs-config-service/**
services/ircs-credential-service/**
docs/reports/088-v1-v3-deep-parity-gap-audit.md
tasks/in-progress/028-16-e-llm-cleaning-gated-parity.md
```

## 风险

- LLM response 不稳定，必须有 fake deterministic path。
- cleaning 可能删除 raw 字典噪音，需要 fixture isolation。
- 真实 credential 不能写入仓库或日志。

## 验收标准

```text
MARK 1/6 LLM cleaning is disabled by default and controlled by config
MARK 2/6 fake LLM cleaning maps raw genre/language/area/category to standard dictionaries
MARK 3/6 fake LLM cleaning handles noise without leaking or deleting non-fixture data
MARK 4/6 candidate timestamp touch prevents fast-loop behavior
MARK 5/6 credential-service lease path is defined without printing secrets
MARK 6/6 staging-only live smoke spec exists before any real LLM call
```

## 测试边界

```powershell
.\gradlew.bat :services:ircs-normalization-worker:test :services:ircs-config-service:test :services:ircs-credential-service:test --rerun-tasks
```

本轮已运行：

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test
BUILD SUCCESSFUL
```

## 当前状态

2026-06-10 本轮开始执行 `028-16-E`：

- 先对照 V1 `AbstractLlmCleaningService` / `LlmCleaningScheduler` / `OpenAILlmServiceImpl`。
- V3 实现范围收敛到 `services/ircs-normalization-worker` 的 LLM cleaning gate、fake/dry-run path、credential/config 读取和轻量单测。
- 按用户最新策略跳过 live smoke、真实 LLM 调用和耗时完整测试；后置统一测试清单在完成后回写。

2026-06-10 本轮完成 implementation-first closure：

- `NormalizationConfigValues` 已补 `app.ai.llm.enabled`、`app.ai.llm.cleaning.mode`、batch/model、credential-service base/token、runtime OpenAI-compatible key/base-url 和 timeout 读取。
- 配置优先级：ENV/K8S/启动参数等 runtime 注入优先于 DB `system_configs`，DB 优先于默认值；secret 只从 runtime/credential-service lease 读取，不写 DB default，不输出日志。
- `LlmCleaningService` / `LlmCleaningRepository` / `FakeLlmCleaningClient` / `OpenAiCompatibleLlmCleaningClient` / `LlmCredentialResolver` / `LlmCleaningRunner` 已接入 normalization-worker。
- 默认安全关闭：`APP_AI_LLM_ENABLED=false`，runner `APP_NORMALIZATION_LLM_CLEANING_RUNNER_ENABLED=false`，mode 默认 `dry-run`。
- fake path 可验证 raw genre/language/area/category 的 exact-normalized match；noise 对 genre/language/area 走 unlink+delete，对 category 映射到 V1 fallback `其他`。
- 固定错误语义：`PROVIDER_DISABLED`、`DRY_RUN_SKIPPED`、`CREDENTIAL_MISSING`、`PROVIDER_TIMEOUT`、`PROVIDER_ERROR`。
- 报告：`docs/reports/105-02816e-llm-cleaning-gated-parity.md`。

MARK 状态：

```text
MARK 1/6 PASS LLM cleaning is disabled by default and controlled by config
MARK 2/6 PASS fake LLM cleaning maps raw genre/language/area/category to standard dictionaries
MARK 3/6 PASS fake LLM cleaning handles noise without leaking or deleting non-fixture data in tests
MARK 4/6 PASS candidate timestamp touch prevents fast-loop behavior
MARK 5/6 PASS credential-service lease path is defined without printing secrets
MARK 6/6 PARTIAL staging-only live smoke spec exists, but real LLM call/live smoke deferred to unified testing
```

统一测试后置清单：

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks

kubectl -n ircs-dev set env deployment/ircs-normalization-worker APP_AI_LLM_ENABLED=true APP_NORMALIZATION_LLM_CLEANING_MODE=dry-run APP_NORMALIZATION_LLM_CLEANING_RUNNER_ENABLED=true
kubectl -n ircs-dev rollout status deployment/ircs-normalization-worker --timeout=240s
kubectl -n ircs-dev logs deployment/ircs-normalization-worker --tail=200 | Select-String "DRY_RUN_SKIPPED"

# staging-only live 单条 fixture，统一测试窗口执行
kubectl -n ircs-dev set env deployment/ircs-normalization-worker APP_AI_LLM_ENABLED=true APP_NORMALIZATION_LLM_CLEANING_MODE=live APP_NORMALIZATION_LLM_CLEANING_RUNNER_ENABLED=true
kubectl -n ircs-dev set env deployment/ircs-normalization-worker APP_NORMALIZATION_LLM_CREDENTIAL_SERVICE_BASE_URL=http://ircs-credential-service.ircs-dev.svc.cluster.local:8080
kubectl -n ircs-dev rollout status deployment/ircs-normalization-worker --timeout=240s
```

## 2026-06-10 本轮收敛口径

本任务按 implementation-first closure 入账，不按 live LLM 闭环关闭：

- 已完成：默认 disabled、默认 `dry-run`、runner 默认关闭、fake deterministic path、
  credential-service lease/runtime credential path、candidate touch、四类 raw dictionary match/noise
  side effect 和固定错误语义。
- 已验证：normalization-worker focused tests passed。
- 后置：真实 LLM 调用、K8S dry-run smoke、staging-only live 单条 fixture、完整 Gradle suite；
  并入 `UTB-02816-P1P2-02`。

## 2026-06-10 F4 no-real-cost hardening

- `LlmCleaningRepository.applyMatch/applyNoise` 增加未映射条件，避免 fake/live cleaning 输出覆盖候选读取后新写入的人工映射。
- `LlmCleaningService` 改为按 repository 实际变更计数，`mapped` / `noise` 不再把被幂等 guard 拒绝的写入计为成功。
- `LlmCredential.toString()` 脱敏 api key；runner audit 仍只写固定 reason/status。
- 新增/补强 deterministic fake、repository idempotency、scheduler disabled/dry-run audit 相关 tests。

验证：

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --tests "com.prodigalgal.ircs.normalization.LlmCleaning*"
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test
```

仍后置到 `UTB-02816-P1P2-02`：

- 真实 LLM 调用。
- live smoke / 成本测试。
- K8S dry-run runner smoke、audit query、DB residue 检查。

## 2026-06-11 028-20-11B owner runner 接入

- normalization-worker 已暴露 `POST /internal/v1/normalization/llm-cleaning/{kind}/run` internal owner API。
- ops-service `llm-sanitize` / `llm-area-clean` / `llm-genre-clean` / `llm-category-clean` runner 不再停在 registry refusal，会调用 normalization owner API。
- 默认 no-real-cost 仍不变：`APP_AI_LLM_ENABLED=false`、cleaning mode 默认 `dry-run`、scheduler runner 默认关闭。
- `llm-sanitize` 在当前 kind 模型中承接 V1 language cleaning 语义。

验证：

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --tests "*LlmCleaning*" --tests "*NormalizationConfigValuesTest" :services:ircs-ops-service:test --tests "*MaintenanceRunnerServiceTest" --tests "*MaintenanceSessionServiceTest" --tests "*NormalizationLlmMaintenanceClientTest" --tests "*SearchServiceMaintenanceSyncClientTest" --tests "*OpsConfigValuesTest" --tests "*MaintenanceControllerTest"
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:bootJar :services:ircs-ops-service:bootJar
```
