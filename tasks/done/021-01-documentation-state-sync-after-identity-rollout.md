# 021-01 identity rollout 后文档状态同步 task spec

当前状态：已完成

父任务：

- `tasks/in-progress/011-mvp-service-onboarding-parent.md`

上游来源：

- `AGENTS.md`
- `docs/reports/021-documentation-state-sync-after-identity-rollout.md`

## 背景

当前 live K8S 已显示 `ircs-identity-service` 部署并 Ready，但本地文档仍有部分状态停留在
magnet-service 接入后的 13/18，且将 identity 描述为未部署。为避免后续服务接入基于错误
配额和错误服务数推进，需要先整理文档状态。

## 目标

- 更新当前文档入口和任务入口。
- 更新 011 父任务当前进度。
- 更新 identity 子任务当前状态和已验证/未验证边界。
- 更新 K8S 快照、cutover plan 和部署 README 的当前状态。
- 保留历史迁移记录中的历史事实，不进行无差别改写。

## 非目标

- 不完成 identity `PUT /api/portal/profile` smoke。
- 不移动 identity 子任务到 done。
- 不创建 identity 迁移记录。
- 不部署新的服务。
- 不修改业务代码或 K8S manifest。

## 输入

```text
AGENTS.md
kubectl get deployments,svc,httproute,resourcequota -n ircs-dev
kubectl get pods -n ircs-dev
docs/index.md
tasks/index.md
tasks/in-progress/011-mvp-service-onboarding-parent.md
tasks/done/015-01-identity-service-auth-profile-mvp.md
```

## 输出

```text
docs/reports/021-documentation-state-sync-after-identity-rollout.md
tasks/done/021-01-documentation-state-sync-after-identity-rollout.md
docs/index.md
tasks/index.md
tasks/in-progress/011-mvp-service-onboarding-parent.md
tasks/done/015-01-identity-service-auth-profile-mvp.md
docs/requirements/011-mvp-service-onboarding-acceleration.md
docs/architecture/002-cutover-plan.md
docs/k8s/current-environment-2026-06-02.md
deploy/k8s/README.md
```

## 影响范围

仅限 Markdown 文档与任务文件。

## 依赖

- 当前 `ircs-dev` live 状态。
- 已有 identity 部署结果；后续已由 018 迁移记录补齐 smoke 结论。

## 风险

- 将 identity 误标为完成会掩盖 `PUT /api/portal/profile` smoke 缺口。
- 将 identity 继续写成未部署会导致接入数、Service quota 和下一步计划错误。

## 验收标准

- 主文档显示 14 个已部署 Ready 服务。
- 任务索引后续已显示 identity MVP 完成，并归档到 `tasks/done/`。
- 011 父任务显示未部署服务为 4/18，并记录 `services=20/20`。
- identity 子任务列出已完成的 test、bootJar、dry-run、ARM64 push、rollout 和主要 auth/profile smoke。
- identity 子任务仍位于 `tasks/in-progress/`。

## 测试边界

```text
kubectl get deployments,svc,httproute,resourcequota -n ircs-dev
kubectl get pods -n ircs-dev
rg -n "13/18|5/18|identity-service.*未部署|limit 500m/512Mi" docs tasks deploy\k8s\README.md
```

## 关联文档

- `docs/reports/021-documentation-state-sync-after-identity-rollout.md`
- `docs/reports/015-identity-service-boundary-analysis.md`
- `docs/requirements/011-mvp-service-onboarding-acceleration.md`

## 关联任务

- 父任务：`tasks/in-progress/011-mvp-service-onboarding-parent.md`
- 相关完成任务：`tasks/done/015-01-identity-service-auth-profile-mvp.md`

## 实施结果

已同步当前入口文档、任务索引、011 父任务、identity 子任务、K8S 快照、cutover plan
和部署 README。

当前状态固定为：

```text
已部署并 Ready: 14/18
已完成迁移验收: 14/18
identity-service: Ready 1/1, auth/profile MVP smoke 已闭环
尚未部署: scraper-service, portal-service, interaction-service, ops-service
ResourceQuota: services=15/20 after 022 worker-only Service pruning
```

## 当前状态

已完成，移动到 `tasks/done/`。
