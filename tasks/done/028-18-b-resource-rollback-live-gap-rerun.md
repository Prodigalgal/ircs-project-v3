# 028-18-B ircs-dev resource rollback live gap rerun task spec

状态：已完成

父任务：

- `tasks/done/028-18-k8s-dev-staging-resource-budget-rollback-guard.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

## 背景

`028-18` first closure 已完成 runbook 与只读 verifier，但 live verifier summary 为 `FAILED`。已知缺口为：

- live `traffic-manager` Deployment 缺 `requests/limits`。
- live `ircs-task-service` 缺 `APP_TASK_SCHEDULER_ENABLED=false` 与 `APP_TASK_WATCHDOG_ENABLED=false`。
- live `ircs-ops-service` 缺 `OPS_MAINTENANCE_REINDEX_DEV_LIMIT=5`。

## 目标

- 确认 `traffic-manager` ownership；若 Helm 管理，优先使用可追踪 Helm values/文档方式补齐资源预算，不做不可追踪漂移。
- 将 `ircs-task-service` live Deployment 与本地 dev manifest 对齐，补齐 scheduler/watchdog dev-safe gate 并确认 Ready。
- 将 `ircs-ops-service` live Deployment 与本地 dev manifest 对齐，补齐 reindex dev limit 并确认 Ready。
- 重跑 `scripts/ops/verify-k8s-dev-resource-rollback-guard.ps1`，目标 summary `PASSED`。
- 回写 report、task 与总账。

## 非目标

- 不修改业务代码。
- 不扩大 HTTPRoute。
- 不新增常驻 workload、不删除或重启无关服务。
- 不执行真实外网、R2 或 SMTP 写入 smoke。
- 不调整 `ResourceQuota`。

## 影响文件

```text
docs/k8s/dev-staging-resource-budget-and-rollback-runbook.md
docs/reports/103-02818-k8s-resource-budget-rollback-verifier.md
tasks/done/028-18-b-resource-rollback-live-gap-rerun.md
tasks/done/028-18-k8s-dev-staging-resource-budget-rollback-guard.md
tasks/index.md
docs/index.md
```

本轮未修改业务代码，`deploy/k8s/dev/task-service-dev.yaml` 与 `deploy/k8s/dev/ops-service-dev.yaml` 已包含所需 env；本轮仅将它们 apply 到 live。

## 验收结果

```text
028-18-B MARK 1/5 traffic-manager ownership/resources decision recorded - PASS
028-18-B MARK 2/5 ircs-task-service live gate env present and Ready - PASS
028-18-B MARK 3/5 ircs-ops-service live gate env present and Ready - PASS
028-18-B MARK 4/5 verifier summary is PASSED - PASS
028-18-B MARK 5/5 docs/tasks ledger updated with command and residue evidence - PASS
```

## Live 命令

```powershell
helm upgrade traffic-manager oci://ghcr.io/telepresenceio/telepresence-oss --namespace ircs-dev --version 2.28.0 --reuse-values --set resources.requests.cpu=10m --set resources.requests.memory=64Mi --set resources.limits.cpu=100m --set resources.limits.memory=256Mi --wait --timeout 240s
kubectl -n ircs-dev apply -f deploy/k8s/dev/task-service-dev.yaml
kubectl -n ircs-dev rollout status deployment/ircs-task-service --timeout=240s
kubectl -n ircs-dev apply -f deploy/k8s/dev/ops-service-dev.yaml
kubectl -n ircs-dev rollout status deployment/ircs-ops-service --timeout=240s
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\ops\verify-k8s-dev-resource-rollback-guard.ps1
```

## 证据

- `traffic-manager` Helm release revision `2`，resources 为 requests `10m/64Mi`、limits `100m/256Mi`。
- `ircs-task-service` Ready `1/1`，`APP_TASK_SCHEDULER_ENABLED=false`，`APP_TASK_WATCHDOG_ENABLED=false`。
- `ircs-ops-service` Ready `1/1`，`OPS_MAINTENANCE_REINDEX_DEV_LIMIT=5`。
- Verifier exit code `0`，summary `PASSED`，failures `0`，warnings `0`。
- Quota：pods `28/30`，services `24/26`，configmaps `4/30`，secrets `5/30`，requests.cpu `890m/4`，requests.memory `4544Mi/8Gi`，limits.cpu `7750m/8`，limits.memory `14464Mi/16Gi`。
- HTTPRoute count `0`。
- Smoke Job residue `0`。
- Completed/failed smoke Pod residue `0`；Running Pod `ircs-notification-worker-7cf755d45b-fg2p2` 被 verifier 显式列出但不是失败 residue。
