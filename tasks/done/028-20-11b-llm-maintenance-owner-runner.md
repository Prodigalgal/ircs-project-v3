# 028-20-11B LLM maintenance owner runner

状态：完成

日期：2026-06-11

## 背景

V3 已有 normalization-worker 的 `LlmCleaningService` 与默认关闭的 `LlmCleaningRunner`，
但 ops 门户维护入口中的 `llm-*` runner 仍被 registry 拒绝，无法通过门户/admin 触发 owner service 行为。
本切片补齐 ops-service 到 normalization-worker 的 internal owner runner 链路。

## 范围

- 新增 shared internal contract：`LlmCleaningRunResponse`。
- normalization-worker 增加 LLM cleaning internal API。
- internal API 支持 service identity token/scope gate，dev 默认不强制。
- ops-service 增加 normalization owner client、runner registry 映射和 audit evidence。
- dev K8S 增加 normalization-worker ClusterIP Service 与 ops owner base URL。

## 非目标

- 不默认开启真实 LLM provider。
- 不做真实外网 LLM live smoke。
- 不迁移 normalization LLM credential resolver 到 shared outbound client。
- 不处理 media storage 双轨或其他 library behavior follow-up。

## 行为映射

| ops runner | normalization kind | 说明 |
| --- | --- | --- |
| `llm-sanitize` | `language` | 当前 V3 cleaning kind 无 SANITIZE，按 V1 language cleaning 语义承接 |
| `llm-area-clean` | `area` | area raw value cleaning |
| `llm-genre-clean` | `genre` | genre raw value cleaning |
| `llm-category-clean` | `category` | category raw value cleaning |

## 验证

```powershell
.\gradlew.bat --no-daemon :shared:ircs-contracts:compileJava :services:ircs-normalization-worker:compileJava :services:ircs-ops-service:compileJava
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --tests "*LlmCleaning*" --tests "*NormalizationConfigValuesTest" :services:ircs-ops-service:test --tests "*MaintenanceRunnerServiceTest" --tests "*MaintenanceSessionServiceTest" --tests "*NormalizationLlmMaintenanceClientTest" --tests "*SearchServiceMaintenanceSyncClientTest" --tests "*OpsConfigValuesTest" --tests "*MaintenanceControllerTest"
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:bootJar :services:ircs-ops-service:bootJar
kubectl apply --dry-run=client -f deploy\k8s\dev\normalization-worker-dev.yaml -f deploy\k8s\dev\ops-service-dev.yaml
```

结果：全部通过。

## 遗留

- 真实 LLM single-row live smoke、成本控制、audit/DB residue 继续放入统一测试窗口。
- normalization LLM credential resolver 后续应迁移 shared outbound client，以获得统一 circuit、service identity 和 trace 传播。
- media storage 双轨和 behavior-needed library follow-up 仍在 `028-20-11` 下继续推进。
