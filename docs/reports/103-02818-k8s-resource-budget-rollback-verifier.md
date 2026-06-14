# 103 028-18 K8S resource budget and rollback verifier

状态：028-18-B live rerun 通过

任务：`028-18`

## 结论

First closure 已把资源预算和回滚护栏从口头说明落成：

- `docs/k8s/dev-staging-resource-budget-and-rollback-runbook.md`
- `scripts/ops/verify-k8s-dev-resource-rollback-guard.ps1`

028-18-B 已修复 first closure 的 live 缺口并重跑 verifier。`traffic-manager` 通过 Helm values 补齐 resources，`ircs-task-service` 与 `ircs-ops-service` 通过 dev manifest apply 补齐 dev-safe gate，最终 verifier exit code 为 `0`，summary status 为 `PASSED`。

## 命令

```powershell
$null = [scriptblock]::Create((Get-Content -LiteralPath 'scripts/ops/verify-k8s-dev-resource-rollback-guard.ps1' -Raw))
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\ops\verify-k8s-dev-resource-rollback-guard.ps1
```

结果：

- PowerShell parser：`parse-ok`
- Verifier exit code：`1`
- MARK：`028-18 MARK 1/7` 到 `028-18 MARK 7/7` 均已输出
- Summary status：`FAILED`

## First closure live resource state

`ircs-dev-quota` 当前状态：

| Resource | used | hard |
| --- | --- | --- |
| pods | `28` | `30` |
| services | `24` | `26` |
| configmaps | `4` | `30` |
| secrets | `4` | `30` |
| requests.cpu | `880m` | `4` |
| requests.memory | `4480Mi` | `8Gi` |
| limits.cpu | `7650m` | `8` |
| limits.memory | `14208Mi` | `16Gi` |

Observation：

- Pod / Service 仍各有 2 个缓冲名额，符合当前 dev 低资源目标。
- `limits.cpu` 仅余 `350m`，后续 smoke 仍应复用现有 Deployment，不新增常驻 workload。
- HTTPRoute count 为 `0`，没有 dev 公网路由污染。
- Smoke Job residue 为 `0`。
- Smoke Pod completed/failed residue 为 `0`。
- Verifier 列出一个带 smoke/codex/028 signature 的 Running Pod：`pods/ircs-notification-worker-7cf755d45b-fg2p2`。它不是 completed/failed residue，本轮未清理。

## First closure failures

First closure live verifier 失败项：

```text
deployment/traffic-manager container/traffic-manager missing resources: requests.cpu,requests.memory,limits.cpu,limits.memory
gate mismatch ircs-task-service APP_TASK_SCHEDULER_ENABLED: expected=false actual=<missing>
gate mismatch ircs-task-service APP_TASK_WATCHDOG_ENABLED: expected=false actual=<missing>
gate mismatch ircs-ops-service OPS_MAINTENANCE_REINDEX_DEV_LIMIT: expected=5 actual=<missing>
```

Interpretation：

- `traffic-manager` 是 live namespace 中的额外 Deployment，不在本仓库 `deploy/k8s/dev` 清单内；它消耗 quota 且缺少 requests/limits，是 namespace 污染/预算缺口。
- 本地 `deploy/k8s/dev/task-service-dev.yaml` 已声明 `APP_TASK_SCHEDULER_ENABLED=false` 和 `APP_TASK_WATCHDOG_ENABLED=false`，但 live `ircs-task-service` Deployment 未体现这两个 env，说明 live 状态可能未应用最新 dev manifest。
- 本地 `deploy/k8s/dev/ops-service-dev.yaml` 已声明 `OPS_MAINTENANCE_REINDEX_DEV_LIMIT=5`，但 live `ircs-ops-service` Deployment 未体现该 env，说明 live 状态可能未应用最新 dev manifest。

## Budget summary

已固定的预算口径：

- Namespace hard quota：pods `30`、services `26`、configmaps `30`、secrets `30`、requests.cpu `4`、requests.memory `8Gi`、limits.cpu `8`、limits.memory `16Gi`。
- Java service baseline：多数业务服务 `25m/128Mi` requests、`250m/512Mi` limits，Hikari max pool `2`、minimum idle `0`。
- Portal/BFF/frontend baseline：portal service `20m/128Mi -> 200m/384Mi`；BFF `20m/128Mi -> 250m/384Mi`；admin/gateway nginx `10m/32Mi -> 100m/128Mi`。
- Job baseline：`parallelism <= 1`、`completions <= 1`、`backoffLimit <= 0`、`ttlSecondsAfterFinished <= 300`。
- External smoke：真实 provider/R2/SMTP 均必须显式 gate，默认只读或关闭；失败任务不 MARK success。

## Rollback summary

Runbook 已覆盖：

- image tag/digest preflight capture 与 digest-first rollback。
- Deployment env、ConfigMap、Secret patch restore。
- DB fixture cleanup 顺序与 zero-residue count。
- queue / DLQ depth capture、restore 与 unknown message 处理原则。
- R2 object PUT/HEAD/DELETE 后 HEAD 404 cleanup 验证。
- SMTP 不可撤回说明：只能恢复 gate 并保留 send history/audit，不能撤回已投递邮件。
- 任一 restore/cleanup/audit 不可验证时，任务不得 MARK success。

## 028-18-B rerun

执行命令：

```powershell
helm upgrade traffic-manager oci://ghcr.io/telepresenceio/telepresence-oss --namespace ircs-dev --version 2.28.0 --reuse-values --set resources.requests.cpu=10m --set resources.requests.memory=64Mi --set resources.limits.cpu=100m --set resources.limits.memory=256Mi --wait --timeout 240s
kubectl -n ircs-dev apply -f deploy/k8s/dev/task-service-dev.yaml
kubectl -n ircs-dev rollout status deployment/ircs-task-service --timeout=240s
kubectl -n ircs-dev apply -f deploy/k8s/dev/ops-service-dev.yaml
kubectl -n ircs-dev rollout status deployment/ircs-ops-service --timeout=240s
$null = [scriptblock]::Create((Get-Content -LiteralPath 'scripts/ops/verify-k8s-dev-resource-rollback-guard.ps1' -Raw))
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\ops\verify-k8s-dev-resource-rollback-guard.ps1
```

结果：

- `traffic-manager` ownership：Helm release `traffic-manager`，chart `telepresence-oss-2.28.0`，revision `2`。
- `traffic-manager` resources：requests `10m/64Mi`，limits `100m/256Mi`。
- `ircs-task-service` live gates：`APP_TASK_SCHEDULER_ENABLED=false`，`APP_TASK_WATCHDOG_ENABLED=false`，Ready `1/1`。
- `ircs-ops-service` live gate：`OPS_MAINTENANCE_REINDEX_DEV_LIMIT=5`，Ready `1/1`。
- Verifier：`028-18 MARK 1/7` 到 `028-18 MARK 7/7`，exit code `0`，summary `PASSED`，failures `0`，warnings `0`。
- Quota：pods `28/30`，services `24/26`，configmaps `4/30`，secrets `5/30`，requests.cpu `890m/4`，requests.memory `4544Mi/8Gi`，limits.cpu `7750m/8`，limits.memory `14464Mi/16Gi`。
- HTTPRoute count：`0`。
- Smoke Job residue：`0`。
- Completed/failed smoke Pod residue：`0`；verifier 仍显式列出 Running Pod `ircs-notification-worker-7cf755d45b-fg2p2`，不是失败 residue。

## Follow-up

当前无阻塞缺口。后续若 Telepresence chart 升级，必须保留 `resources` values，避免 Helm upgrade 覆盖预算。
