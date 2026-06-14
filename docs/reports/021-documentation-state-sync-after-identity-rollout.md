# 021 文档状态同步报告：identity rollout 后

状态：完成

更新时间：2026-06-04

## 背景

根目录 `AGENTS.md` 要求本地文档和任务在实现前后保持可追溯闭环。当前 live
K8S 已显示 `ircs-identity-service` 在 `ircs-dev` Ready 1/1，但已有文档仍有部分
停留在 magnet-service 接入后的 13/18 状态，并将 identity 描述为尚未部署。

本报告用于记录本轮只整理文档与任务追踪，不修改业务代码、不新增 K8S manifest。

## 目标

- 将主索引、任务索引、父任务、K8S 快照和 cutover plan 同步到当前 live 状态。
- 明确区分 `ircs-identity-service` 的两种状态：
  - Deployment/Service 已部署并 Ready。
  - `PUT /api/portal/profile` smoke 尚未闭环，因此子任务仍保持进行中。
- 更新当前资源配额风险：`services=20/20` 已成为最紧限制。
- 固定后续继续接入服务前的文档结论：需要先处理 Service quota 或移除不必要的 worker
  Service。

## 非目标

- 不移动 identity 子任务到 `tasks/done/`。
- 不创建 identity 迁移记录。
- 不继续执行新的服务接入或 smoke。
- 不调整 K8S ResourceQuota、LimitRange、Deployment 或 Service。
- 不修改业务代码。

## 输入

```text
AGENTS.md
docs/index.md
tasks/index.md
tasks/in-progress/011-mvp-service-onboarding-parent.md
tasks/done/015-01-identity-service-auth-profile-mvp.md
docs/requirements/011-mvp-service-onboarding-acceleration.md
docs/architecture/001-target-microservice-boundaries.md
docs/architecture/002-cutover-plan.md
docs/k8s/current-environment-2026-06-02.md
deploy/k8s/README.md
kubectl get deployments,svc,httproute,resourcequota -n ircs-dev
kubectl get pods -n ircs-dev
```

## 当前 live 观察

截至本报告创建时：

```text
ircs-identity-service Deployment Ready 1/1
ircs-identity-service Service ClusterIP 10.97.88.143
ircs-dev pods:            19 / 20
ircs-dev services:        20 / 20
ircs-dev requests.cpu:    700m / 4
ircs-dev requests.memory: 3456Mi / 8Gi
ircs-dev limits.cpu:      5750m / 8
ircs-dev limits.memory:   10752Mi / 16Gi
```

## 风险

- 如果直接继续部署 scraper/portal/interaction/ops 任一服务，`services=20/20` 会阻塞
  新 Service 创建。
- identity smoke 尚缺 `PUT /api/portal/profile` 成功证据，不能将 identity 子任务当作
  完成迁移。
- 历史迁移记录中的当时资源数和当时 `500m` limit 是历史事实，不应全部批量改写为当前值；
  本轮只修正当前索引和当前状态文档。

## 验收标准

- `docs/index.md` 与 `tasks/index.md` 展示当前 14 个已部署 Ready 服务和 4 个未部署服务。
- identity 子任务最初保持 `tasks/in-progress/`；后续已由
  `docs/migrations/018-identity-service-auth-profile-extraction.md` 收口并移动到
  `tasks/done/`。
- 011 父任务记录当前 live quota 和下一步 Service quota 风险。
- K8S 快照记录 identity Deployment/Service、当前 quota 和 image digest。
- 本任务完成后移动到 `tasks/done/`，并在索引中可查。

## 实施结果

已同步：

```text
docs/index.md
tasks/index.md
tasks/in-progress/011-mvp-service-onboarding-parent.md
tasks/done/015-01-identity-service-auth-profile-mvp.md
docs/requirements/011-mvp-service-onboarding-acceleration.md
docs/architecture/002-cutover-plan.md
docs/k8s/current-environment-2026-06-02.md
deploy/k8s/README.md
docs/reports/015-identity-service-boundary-analysis.md
docs/reports/017-portal-interaction-magnet-boundary-analysis.md
tasks/backlog/017-02-interaction-feedback-history-mvp.md
tasks/done/017-03-magnet-provider-cache-mvp.md
```

后续最新结论：

```text
已部署并 Ready: 14/18
已完成迁移验收: 14/18
已部署但验收未闭环: none
尚未部署: scraper-service, portal-service, interaction-service, ops-service
Service quota: services=15/20
```

后续状态同步：

```text
identity-service auth/profile MVP smoke 已在 2026-06-04 收口。
当前权威迁移记录见 docs/migrations/018-identity-service-auth-profile-extraction.md。
worker-only Service 裁剪已在 2026-06-04 完成。
当前权威配额记录见 docs/reports/022-dev-service-quota-worker-service-pruning.md。
```

验证：

```text
kubectl get deployments,svc,httproute,resourcequota -n ircs-dev
kubectl get pods -n ircs-dev
rg stale-status checks
```
