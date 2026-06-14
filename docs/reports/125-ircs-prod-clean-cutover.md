# 125 ircs-prod clean cutover

日期：2026-06-14

## 结论

`ircs-prod` 已作为 V3 唯一开发、测试和生产候选 namespace 启用。旧 `ircs-dev`
已删除。旧 `ircs-system` 与旧 ArgoCD `ircs-app` 已退役删除，`huawai` / `ircs`
业务入口均由 `ircs-prod` 承接。

旧数据迁移已取消，`ircs-prod` 使用 V3 migrator 初始化后的干净数据库起步。

## 当前状态

- `ircs-prod-core`：`Synced/Healthy`。
- `ircs-prod-edge`：`Synced/Healthy`。
- `ircs-prod-migrator`：保留为手动应用，避免 core 同步重复运行迁移 Job。
- `ircs-app`：旧 ArgoCD Application 已删除。
- `ircs-prod`：24/24 Deployment Ready，3/3 StatefulSet Ready。
- `ircs-system`：旧 namespace 已删除。

## 数据处理

曾启动的旧数据导入已中止。随后用迁移前的 `ircs-prod` 备份恢复干净库。

恢复后抽样：

```text
unified_videos=0
raw_videos=0
episodes=0
system_configs=133
databasechangelog=138
```

## 入口切流

- 删除旧 `ircs-system/huawai-route`。
- 删除旧 `ircs-system/ircs-route`。
- 删除旧 `ircs-system/kibana-route` 随 namespace 退役完成。
- 新增并由 ArgoCD 接管：
  - `ircs-prod/huawai-route -> ircs-frontend-gateway:80`
  - `ircs-prod/ircs-route -> ircs-frontend-gateway:8080`

两个新 route 均为 `Accepted=True`、`ResolvedRefs=True`。

## 旧资源退役

退役前已在 `ircs-prod-config/backups/ircs-system-retire-20260614-174109/`
保存非敏感快照，未导出 Secret 明文。

- 删除 ArgoCD 旧 Application：`argocd/ircs-app`。
- 删除旧业务 namespace：`ircs-system`。
- 删除旧 `ircs-app` 遗留在 `monitoring` namespace 的 otel/tempo Service、Deployment、
  StatefulSet 和 ConfigMap。
- 复核 ArgoCD 当前仅保留 `ircs-prod-core`、`ircs-prod-edge`、`ircs-prod-migrator`。
- 复核无 `argocd.argoproj.io/tracking-id=ircs-app:*` 残留资源。

## BFF prod target 修复

切流后发现 admin/portal BFF 默认仍回退到 `ircs-dev.svc.cluster.local`，导致
`/api/v1/auth/pow` 通过公网返回 `500`。已在 GitOps config 中集中补充
`APP_BFF_*_BASE_URL` 指向 `ircs-prod.svc.cluster.local`，并通过 pod template annotation
触发 `admin-bff`、`portal-bff` rollout。

GitOps revision：`8d9e11a500906898eb38d12a78d1f10850329af6`。

## 验证

集群内真实验证通过：

- `http://ircs-frontend-gateway:80/gateway-health` -> `204`
- `http://ircs-frontend-gateway:8080/gateway-health` -> `204`
- `http://ircs-portal-frontend:3000/` -> `200`
- `http://ircs-admin-frontend:80/` -> `200`
- portal/admin BFF、identity、task、search、ops readiness 均 -> `200`

公网真实验证通过：

- `https://huawai.sophia.fr.eu.org/gateway-health` -> `204`
- `https://ircs.sophia.fr.eu.org/gateway-health` -> `204`
- `https://huawai.sophia.fr.eu.org/` -> portal HTML `200`
- `https://ircs.sophia.fr.eu.org/` -> admin HTML `200`
- `https://ircs.sophia.fr.eu.org/api/v1/auth/pow?scope=admin.login` -> `200`
- admin PoW + 数学验证码 + 真实管理员登录 -> `200`，JWT 结构正常。
- admin BFF 登录后接口：configs、data-sources、collection-tasks -> `200`。
- dashboard 真实接口组：statistics、analysis、metrics、task-runtime、source-quality -> `200`。
- portal BFF：首页、backend 兼容首页、portal login PoW -> `200`。
- 未知映射统一 404：admin dashboard root、admin unknown route、portal unknown route 均返回
  `404 / http.404 / Resource not found`。
- V3 内部接口静态审计：45 条 admin/portal BFF route target 均匹配后端 Controller；
  31 条 `/internal/...` 调用常量均匹配 Controller 映射。

## 剩余风险

- 还需要生产候选安全复核、回滚演练说明和 go/no-go 报告。
