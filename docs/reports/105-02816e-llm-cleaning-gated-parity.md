# 105 028-16-E LLM cleaning gated parity first closure

状态：partial，MARK 5/6；真实 LLM 调用和 live smoke 统一测试后置

更新时间：2026-06-10

## 目标

对照 V1 `AbstractLlmCleaningService`、`LlmCleaningScheduler`、`OpenAILlmServiceImpl` 和
`SystemConfigKey.LLM_*`，在 V3 `ircs-normalization-worker` 内补齐 gated/fake-first LLM
cleaning 基线：

- 默认安全关闭。
- runtime ENV/K8S/启动参数优先于 DB/default seed。
- 支持 `dry-run` / `fake` / `live` mode。
- 支持 runtime OpenAI-compatible credential 或 credential-service lease，不输出 secret。
- 先接入 normalization worker 的受控 runner，不做 live smoke 和真实外部调用。

## 实现摘要

- `NormalizationConfigValues` 新增 LLM cleaning gate、mode、batch/model、credential-service、timeout 和 runtime secret 读取。
- `application.yaml` 新增默认关闭的 `app.ai.llm.enabled=false`、默认 `app.normalization.llm-cleaning.mode=dry-run`、默认关闭的 runner gate。
- `LlmCleaningService` 固定状态语义：
  - `PROVIDER_DISABLED`
  - `DRY_RUN_SKIPPED`
  - `CREDENTIAL_MISSING`
  - `PROVIDER_TIMEOUT`
  - `PROVIDER_ERROR`
- `LlmCleaningRepository` 对四类 V1 cleaning 对象提供候选读取、timestamp touch、match/noise side effect：
  - `GENRE`: `raw_genres.standard_genre_id`
  - `LANGUAGE`: `raw_languages.standard_language_id`
  - `AREA`: `raw_areas.standard_area_id`
  - `CATEGORY`: `raw_category.category_id`
- `FakeLlmCleaningClient` 提供 deterministic no-cost path：精确归一化匹配标准项，`unknown/noise/广告/n/a` 等标为 noise。
- `OpenAiCompatibleLlmCleaningClient` 保留 OpenAI-compatible `/chat/completions` live path，但只有显式 `mode=live` 且 credential 可用时才会调用。
- `LlmCleaningRunner` 作为 pipeline 接入口，`app.normalization.llm-cleaning.runner.enabled=false` 默认不创建。

## Gate / Config 优先级

运行期注入优先级沿用 `RuntimeInjectedConfig`：

```text
commandLineArgs / systemProperties / systemEnvironment / spring.application.json /
K8S ConfigMap/Secret/config tree / test dynamic properties
  > DB system_configs
  > code defaults
```

关键开关：

```text
APP_AI_LLM_ENABLED=false
APP_NORMALIZATION_LLM_CLEANING_MODE=dry-run
APP_NORMALIZATION_LLM_CLEANING_RUNNER_ENABLED=false
APP_NORMALIZATION_LLM_CLEANING_BATCH_SIZE=500
SPRING_AI_OPENAI_API_KEY=<runtime only>
SPRING_AI_OPENAI_BASE_URL=https://ai.mnnu.eu.org/v1
APP_NORMALIZATION_LLM_CREDENTIAL_SERVICE_BASE_URL=<optional>
APP_NORMALIZATION_LLM_CREDENTIAL_SERVICE_TOKEN=<runtime only>
APP_AI_LLM_MODEL=gemma-4-31b-it
```

## 验收

```text
MARK 1/6 LLM cleaning disabled by default and controlled by config: PASS
MARK 2/6 fake LLM cleaning maps raw genre/language/area/category to standard dictionaries: PASS by focused fake test and shared repository path
MARK 3/6 fake LLM cleaning handles noise without leaking secrets: PASS by fake test; no secret output
MARK 4/6 candidate timestamp touch prevents fast-loop behavior: PASS by service test verifying touch before decisions
MARK 5/6 credential-service lease path is defined without printing secrets: PASS by resolver path; live not executed
MARK 6/6 staging-only live smoke spec exists before real LLM call: PARTIAL, plan documented below; not executed by current strategy
```

## 验证

已运行：

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test
BUILD SUCCESSFUL
```

未运行：

- 真实 LLM 外部调用。
- live smoke。
- 全量 Gradle test。
- K8S rollout。

## 统一测试后置清单

fake/no-cost：

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
```

dry-run K8S 后置：

```powershell
kubectl -n ircs-dev set env deployment/ircs-normalization-worker APP_AI_LLM_ENABLED=true APP_NORMALIZATION_LLM_CLEANING_MODE=dry-run APP_NORMALIZATION_LLM_CLEANING_RUNNER_ENABLED=true
kubectl -n ircs-dev rollout status deployment/ircs-normalization-worker --timeout=240s
kubectl -n ircs-dev logs deployment/ircs-normalization-worker --tail=200 | Select-String "DRY_RUN_SKIPPED"
```

staging-only live 单条：

```powershell
# 仅统一测试窗口执行，要求单条 fixture、专用 credential、明确 cleanup。
kubectl -n ircs-dev set env deployment/ircs-normalization-worker APP_AI_LLM_ENABLED=true APP_NORMALIZATION_LLM_CLEANING_MODE=live APP_NORMALIZATION_LLM_CLEANING_RUNNER_ENABLED=true
kubectl -n ircs-dev set env deployment/ircs-normalization-worker APP_NORMALIZATION_LLM_CREDENTIAL_SERVICE_BASE_URL=http://ircs-credential-service.ircs-dev.svc.cluster.local:8080
kubectl -n ircs-dev rollout status deployment/ircs-normalization-worker --timeout=240s
```

## 遗留风险

- live provider prompt/response 稳定性未验证。
- credential-service lease 的真实 token/base-url 未在本轮 live 验证。
- category noise 依赖 `standard_category` 中存在 `其他`。
- 当前 runner 是按四类顺序执行的第一闭环；ops 手动触发/audit 仍可在后续任务补。
