# 028-18 K8S dev/staging resource budget and rollback guard task spec

状态：已完成；028-18-B live rerun 通过

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`
- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`

## 背景

用户追问“资源预算、回滚这两项打算做什么”。当前 `ircs-dev` 目标是低资源、无流量冲突、无配置冲突、无 namespace 污染。过去 live smoke 已多次记录 ResourceQuota、HTTPRoute 和 cleanup，但资源预算与回滚护栏仍偏口头化，缺少可复用文档和只读 verifier。

## 目标

- 建立 dev/staging 资源预算文档，覆盖 namespace quota、每服务 requests/limits 基线、Job TTL/并发上限、真实外网/R2/SMTP smoke 次数预算、危险 runner 默认 gate。
- 建立回滚 Runbook，覆盖 image digest、env/ConfigMap/Secret patch restore、DB fixture cleanup、queue/DLQ cleanup、R2 object cleanup、SMTP 审计不可撤回说明、失败任务不 MARK。
- 增加只读 verifier 脚本，检查 `ircs-dev` pods/services quota、Deployment resources、HTTPRoute 污染、Job/Pod residue、关键 gate 默认值和最近 smoke 残留标签。
- 执行 verifier 并记录当前 dev 环境状态、超预算项与缺口。
- 回写 docs/tasks 总账。

## 非目标

- 不修改业务代码。
- 不改 notification/metadata/content/storage 业务实现。
- 不删除、不重启、不扩缩 K8S 服务。
- 不执行真实外网、R2 或 SMTP 写入 smoke。
- 不调整 ResourceQuota，除非 verifier 发现 manifest 明显缺失且只需文档化。

## 影响文件

```text
docs/k8s/dev-staging-resource-budget-and-rollback-runbook.md
docs/reports/103-02818-k8s-resource-budget-rollback-verifier.md
scripts/ops/verify-k8s-dev-resource-rollback-guard.ps1
tasks/done/028-18-k8s-dev-staging-resource-budget-rollback-guard.md
tasks/index.md
docs/index.md
```

## 验收标准

```text
028-18 MARK 1/7 ircs-dev ResourceQuota pods/services/configmaps/secrets/cpu/memory can be read
028-18 MARK 2/7 every live Deployment container has requests and limits
028-18 MARK 3/7 no HTTPRoute exists in ircs-dev
028-18 MARK 4/7 no completed/failed smoke Job residue exceeds TTL budget and no smoke Pod residue remains
028-18 MARK 5/7 dangerous live gates remain at dev-safe defaults
028-18 MARK 6/7 recent smoke residue labels are absent or explicitly listed
028-18 MARK 7/7 verifier emits machine-readable summary for docs/tasks回写
```

## 测试方式

```powershell
$null = [scriptblock]::Create((Get-Content -LiteralPath 'scripts/ops/verify-k8s-dev-resource-rollback-guard.ps1' -Raw))
.\scripts\ops\verify-k8s-dev-resource-rollback-guard.ps1
```

## 当前状态

2026-06-09：任务创建。下一步实现 runbook、只读 verifier，并执行 `ircs-dev` 现场检查。

2026-06-09：first closure 完成。

- 已新增资源预算与回滚 runbook：`docs/k8s/dev-staging-resource-budget-and-rollback-runbook.md`。
- 已新增只读 verifier：`scripts/ops/verify-k8s-dev-resource-rollback-guard.ps1`。
- PowerShell parser 通过：`parse-ok`。
- live verifier 输出 `028-18 MARK 1/7` 到 `028-18 MARK 7/7`，exit code 为 `1`，summary status 为 `FAILED`。
- 当前 `ircs-dev-quota`：pods `28/30`、services `24/26`、configmaps `4/30`、secrets `4/30`、requests.cpu `880m/4`、requests.memory `4480Mi/8Gi`、limits.cpu `7650m/8`、limits.memory `14208Mi/16Gi`。
- HTTPRoute count 为 `0`，Smoke Job residue 为 `0`，completed/failed smoke Pod residue 为 `0`。
- 失败缺口：
  - `deployment/traffic-manager container/traffic-manager` 缺 `requests/limits`。
  - live `ircs-task-service` 缺 `APP_TASK_SCHEDULER_ENABLED=false` 与 `APP_TASK_WATCHDOG_ENABLED=false`。
  - live `ircs-ops-service` 缺 `OPS_MAINTENANCE_REINDEX_DEV_LIMIT=5`。
- 详细结果已记录：`docs/reports/103-02818-k8s-resource-budget-rollback-verifier.md`。

2026-06-09：028-18-B live 缺口修复完成。

- `traffic-manager` 确认为 Helm release `traffic-manager` / chart `telepresence-oss-2.28.0`，已通过 `helm upgrade --reuse-values` 补齐 resources：requests `10m/64Mi`，limits `100m/256Mi`，revision `2`。
- `ircs-task-service` 已 apply `deploy/k8s/dev/task-service-dev.yaml` 并 rollout 成功，live gate 为 `APP_TASK_SCHEDULER_ENABLED=false`、`APP_TASK_WATCHDOG_ENABLED=false`。
- `ircs-ops-service` 已 apply `deploy/k8s/dev/ops-service-dev.yaml` 并 rollout 成功，live gate 为 `OPS_MAINTENANCE_REINDEX_DEV_LIMIT=5`。
- Verifier 重跑输出 `028-18 MARK 1/7` 到 `028-18 MARK 7/7`，exit code `0`，summary `PASSED`，failures `0`，warnings `0`。
- 后置 quota：pods `28/30`，services `24/26`，configmaps `4/30`，secrets `5/30`，requests.cpu `890m/4`，requests.memory `4544Mi/8Gi`，limits.cpu `7750m/8`，limits.memory `14464Mi/16Gi`。
- HTTPRoute count 为 `0`；Smoke Job residue 为 `0`；completed/failed smoke Pod residue 为 `0`。
- 028-18-B MARK：`5/5` 完成。
