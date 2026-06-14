# 028-20-11D normalization LLM credential shared outbound

状态：完成

来源：`tasks/in-progress/028-20-post-parity-runtime-platform-breakdown.md`

## 背景

`028-20-10B` 已建立 K8S mTLS rollout 入口，但 normalization-worker 的 LLM credential-service lease
resolver 仍直接使用原生 JDK `HttpClient`。这会绕开 shared outbound 的 policy、circuit、header 和
trace/correlation 语义，不利于后续真实 mTLS smoke。

## 范围

- `services/ircs-normalization-worker`
- `LlmCredentialResolver`
- `LlmCredentialResolverTest`

## 非目标

- 不执行真实 LLM provider 调用。
- 不执行真实 credential-service / mTLS K8S smoke。
- 不修改 credential lease contract。

## 输出

- credential-service lease 调用改为 shared `OutboundHttpClient`。
- 使用 `INTERNAL_SERVICE` policy 和 caller circuit key `normalization-llm-credential-service`。
- service identity headers 由 `InternalServiceAuthHeaders` 统一注入。
- runtime API key 仍优先于 credential-service lease，且命中后不触发 outbound。
- 单测覆盖 service identity headers、policy/circuit key、非 2xx 错误、真实 circuit open fast-fail。

## 验证

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --tests "*LlmCredentialResolverTest" --tests "*LlmCleaningServiceTest" --tests "*NormalizationConfigValuesTest" :services:ircs-normalization-worker:bootJar
```

结果：

```text
BUILD SUCCESSFUL
```

## 遗留风险

- 真实 mTLS/client cert、service identity header 和 circuit ENV override 仍需在统一 K8S smoke 窗口验证。
- remaining library follow-up 继续按“行为是否需要”逐项补齐，不机械接回依赖。
