# K8S dev/staging resource budget and rollback runbook

状态：first closure

适用范围：`ircs-dev`，以及后续 staging / production-like smoke 的默认护栏。本文只定义预算、门禁和回滚步骤，不授权扩大资源、不授权真实外网/R2/SMTP 写入。

## Resource budget

Namespace hard quota：

| ResourceQuota | hard |
| --- | --- |
| pods | `30` |
| services | `26` |
| configmaps | `30` |
| secrets | `30` |
| requests.cpu | `4` |
| requests.memory | `8Gi` |
| limits.cpu | `8` |
| limits.memory | `16Gi` |

Dev baseline：

- 默认单副本 `Deployment`，worker 不创建 `Service`，所有服务保持 ClusterIP-only。
- `ircs-dev` 必须保持 HTTPRoute-free；任何 staging / production-like smoke 需要公网入口时，必须在 staging namespace 单独申请，不复用 dev namespace。
- Pod 与 Service 至少保留 2 个缓冲名额；少于 2 个时只允许复用现有 Deployment/Service，不新增资源。
- Java 服务默认 Hikari `maximum-pool-size=2`、`minimum-idle=0`，避免并发 smoke 挤爆 shared PostgreSQL。

Per-service requests / limits：

| Component | Containers | Requests | Limits |
| --- | --- | --- | --- |
| `ircs-catalog-service` | `app` | `25m`, `128Mi` | `250m`, `512Mi` |
| `ircs-config-service` | `app` | `25m`, `128Mi` | `250m`, `512Mi` |
| `ircs-content-service` | `app` | `25m`, `128Mi` | `250m`, `512Mi` |
| `ircs-credential-service` | `app` | `25m`, `128Mi` | `250m`, `512Mi` |
| `ircs-identity-service` | `app` | `25m`, `128Mi` | `250m`, `512Mi` |
| `ircs-interaction-service` | `app` | `25m`, `128Mi` | `250m`, `512Mi` |
| `ircs-magnet-service` | `app` | `25m`, `128Mi` | `250m`, `512Mi` |
| `ircs-ops-service` | `app` | `25m`, `128Mi` | `250m`, `512Mi` |
| `ircs-scraper-service` | `app` | `25m`, `128Mi` | `250m`, `512Mi` |
| `ircs-search-service` | `app` | `25m`, `128Mi` | `250m`, `512Mi` |
| `ircs-storage-service` | `app` | `25m`, `128Mi` | `250m`, `512Mi` |
| `ircs-task-service` | `app` | `25m`, `128Mi` | `250m`, `512Mi` |
| `ircs-ingestion-worker` | `app` | `25m`, `128Mi` | `250m`, `512Mi` |
| `ircs-normalization-worker` | `app` | `25m`, `128Mi` | `250m`, `512Mi` |
| `ircs-aggregation-worker` | `app` | `25m`, `128Mi` | `250m`, `512Mi` |
| `ircs-metadata-worker` | `app` | `25m`, `128Mi` | `250m`, `512Mi` |
| `ircs-notification-worker` | `app` | `25m`, `128Mi` | `250m`, `512Mi` |
| `ircs-portal-service` | `app` | `20m`, `128Mi` | `200m`, `384Mi` |
| `ircs-portal-bff` | `app` | `20m`, `128Mi` | `250m`, `384Mi` |
| `ircs-admin-bff` | `app` | `20m`, `128Mi` | `250m`, `384Mi` |
| `ircs-portal-web` | `app` | `25m`, `192Mi` | `250m`, `512Mi` |
| `ircs-admin-web` | `app` | `10m`, `32Mi` | `100m`, `128Mi` |
| `ircs-gateway` | `app` | `10m`, `32Mi` | `100m`, `128Mi` |
| `postgres` | `postgres` | `100m`, `256Mi` | `500m`, `512Mi` |
| `rabbitmq` | `rabbitmq` | `100m`, `256Mi` | `500m`, `768Mi` |
| `valkey` | `valkey` | `50m`, `128Mi` | `250m`, `256Mi` |
| `elasticsearch` | `sysctl`, `elasticsearch` | `50m`, `64Mi`; `100m`, `1Gi` | `250m`, `256Mi`; `1000m`, `2Gi` |
| `ircs-migrator` Job | `migrator` | `25m`, `128Mi` | `250m`, `512Mi` |
| `traffic-manager` | `traffic-manager` | `10m`, `64Mi` | `100m`, `256Mi` |

Job budget：

- `ircs-migrator` uses `backoffLimit: 0` and `ttlSecondsAfterFinished: 300`.
- Smoke Job default upper bound：`parallelism <= 1`、`completions <= 1`、`backoffLimit <= 0`、`ttlSecondsAfterFinished <= 300`。
- Any Job or Pod with `smoke` / `codex` / `028-*` labels or names must be cleaned or listed by the verifier before MARK.

External smoke budget：

| Smoke type | Dev default | Per-run allowance | Required cleanup |
| --- | --- | --- | --- |
| Public metadata fetch | gated off unless task explicitly enables one provider | at most 1 real external request per provider smoke | queue/DLQ empty, DB/ES fixture removed, provider gate restored |
| R2 object write | `APP_STORAGE_R2_ENABLED=false` and watchdog off | at most 1 PUT/HEAD/DELETE chain per explicit smoke | object HEAD returns 404 after cleanup, env/Secret/ConfigMap restored |
| SMTP live send | `APP_MAIL_ENABLED=false` | at most 1 allowlisted recipient, only with explicit confirmation | send history/audit retained; SMTP delivery itself is irreversible |
| Ops maintenance runner | dev limit `5`; destructive runners not auto-started | dry-run first, live execute only after explicit dual gate | worker-job audit must show completed or failed; failed run does not MARK |

Dangerous live gates expected in dev：

| Deployment | Gate | Expected |
| --- | --- | --- |
| `ircs-storage-service` | `APP_STORAGE_R2_ENABLED` | `false` |
| `ircs-storage-service` | `APP_STORAGE_R2_WATCHDOG_ENABLED` | `false` |
| `ircs-notification-worker` | `APP_MAIL_ENABLED` | `false` |
| `ircs-notification-worker` | `APP_MAIL_SEND_HISTORY_CLEANUP_ENABLED` | `false` |
| `ircs-notification-worker` | `APP_MAIL_SEND_HISTORY_CLEANUP_DRY_RUN` | `true` |
| `ircs-notification-worker` | `APP_MAIL_SEND_HISTORY_CLEANUP_EXECUTE_ENABLED` | `false` |
| `ircs-task-service` | `APP_TASK_SCHEDULER_ENABLED` | `false` |
| `ircs-task-service` | `APP_TASK_WATCHDOG_ENABLED` | `false` |
| `ircs-aggregation-worker` | `APP_AGGREGATION_SCHEDULER_ENABLED` | `false` |
| `ircs-aggregation-worker` | `APP_AGGREGATION_BATCH_SIZE` | `1` |
| `ircs-normalization-worker` | `APP_NORMALIZATION_WATCHDOG_ENABLED` | `false` |
| `ircs-metadata-worker` | `APP_METADATA_DOUBAN_ENABLED` | `false` |
| `ircs-metadata-worker` | `APP_METADATA_TMDB_ENABLED` | `false` |
| `ircs-metadata-worker` | `APP_METADATA_RT_ENABLED` | `false` |
| `ircs-ops-service` | `OPS_MAINTENANCE_REINDEX_DEV_LIMIT` | `5` |

Verifier：

```powershell
.\scripts\ops\verify-k8s-dev-resource-rollback-guard.ps1
```

Telepresence `traffic-manager` is Helm-managed. Resource budget changes must be applied through the release values, not by an untracked live patch:

```powershell
helm upgrade traffic-manager oci://ghcr.io/telepresenceio/telepresence-oss --namespace ircs-dev --version 2.28.0 --reuse-values --set resources.requests.cpu=10m --set resources.requests.memory=64Mi --set resources.limits.cpu=100m --set resources.limits.memory=256Mi --wait --timeout 240s
```

## Rollback runbook

Preflight capture：

```powershell
kubectl -n ircs-dev get deploy <deployment> -o jsonpath="{.spec.template.spec.containers[0].image}{'\n'}"
kubectl -n ircs-dev get deploy <deployment> -o jsonpath="{.spec.template.spec.containers[0].imageID}{'\n'}"
kubectl -n ircs-dev get deploy <deployment> -o yaml > <deployment>.before.yaml
kubectl -n ircs-dev get configmap ircs-dev-app-config -o yaml > ircs-dev-app-config.before.yaml
kubectl -n ircs-dev get secret ircs-dev-secrets -o yaml > ircs-dev-secrets.before.yaml
```

Image rollback：

1. Prefer immutable digest rollback from captured image digest.
2. If only `:dev` tag exists, restore the previous manifest snapshot or rebuild/push the known-good image, then restart only the affected Deployment.
3. Verify with `kubectl -n ircs-dev rollout status deployment/<deployment> --timeout=240s`.
4. Re-run the verifier; do not MARK until quota, HTTPRoute and residue checks pass.

Env / ConfigMap / Secret patch restore：

- Any live `kubectl set env` or `kubectl patch deployment` must capture the previous value first.
- Restore exact previous values in `finally` or with the captured `<deployment>.before.yaml`.
- For ConfigMap and Secret changes, restore only the keys touched by the smoke unless the whole object snapshot is known to be current.
- Never print Secret data; record key names and restore status only.

DB fixture cleanup：

- Use a unique task marker such as `codex02818*` or the task-specific marker already in the smoke script.
- Cleanup order must follow FK direction: child relation rows, commands/audit fixtures, main fixture rows, then optional search documents.
- Verify zero residue with a read-only SQL count before MARK.

Queue / DLQ cleanup：

- Before a live smoke, capture target queue depth, DLQ depth and consumer count.
- After smoke, the expected state is target queues empty or back to preflight depth; DLQ must not increase.
- Do not purge shared queues unless the task explicitly owns every message by marker. Unknown messages are evidence, not cleanup targets.

R2 cleanup：

- Real R2 smoke must create at most one marker object path per run.
- After DELETE, verify HEAD returns 404 or equivalent not-found.
- Restore `APP_STORAGE_R2_ENABLED=false`, watchdog false, bucket/domain ConfigMap values and any temporary Secret/env overrides.

SMTP cleanup and irreversibility：

- SMTP delivery cannot be rolled back. The only rollback is restoring runtime gates and preserving send history/audit evidence.
- A failed or partial SMTP task must not MARK success. Record whether any email was sent, recipient allowlist, send history row id if present, and audit query evidence.

Failure rule：

- If rollback restore, DB cleanup, queue/DLQ cleanup, R2 cleanup or SMTP audit cannot be verified, the task remains failed or blocked.
- Failed tasks do not MARK. Partial evidence belongs in `docs/reports/` and the task file with exact command output summary.
