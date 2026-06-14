# 031 ircs-prod GitOps bootstrap and clean cutover

状态：核心闭环完成，进入生产候选收尾

日期：2026-06-14

## 目标

把 V3 代码与生产部署链路接入 Gitea runner / Harbor / ArgoCD，并为旧生产数据进入
`ircs-prod` 建立可审计边界。2026-06-14 决策更新：旧生产数据不迁移，`ircs-prod`
按干净数据起步，后续真实测试和开发都直接在 `ircs-prod` 进行。

## 关键决策

- 新生产 namespace：`ircs-prod`。
- 旧生产 namespace：`ircs-system`，只作为对照、回滚参考和退役对象。
- 代码仓库：新建 Gitea 仓库，建议名 `ircs-project-v3`。
- GitOps 仓库：参考旧 V1 仓库 `https://gitea.mnnu.eu.org/gitea-admin/ircs-config`，
  但 V3 清单必须写向 `ircs-prod`。
- 镜像仓库：`harbor.mnnu.eu.org/ircs`。
- 生产数据：不迁移旧 `ircs-system` 业务数据；`ircs-prod` 使用 V3 migrator 初始化后的干净库。
- 退役状态：旧 `ircs-system` namespace 与旧 ArgoCD `ircs-app` 已删除；Gitea 旧仓库可保留。

## 数据处理原则

1. `ircs-prod` 以 V3 schema、V3 seed 和运行时 Secret/ConfigMap 为唯一生产候选事实源。
2. 旧 `ircs-system` 只保留为历史对照，不再向 `ircs-prod` 导入业务数据。
3. 已完成的 schema preflight 和备份只作为撤销迁移路径的证据，不再执行兼容数据导入。
4. Rabbit/Valkey/search index/hot-state/task runtime/cache 等运行态全部在 `ircs-prod` 重建。
5. 管理员账号、运行时 Secret、provider credential 优先使用 `ircs-prod` Secret/ConfigMap 注入；
   旧 DB seed 只能作为最低优先级兜底。

## 旧数据结论

不迁移：

- 管理员/会员账号、旧 session/token、历史任务、旧搜索派生 index。
- raw/unified 业务数据、playlist、person、category、genre、area、language、tag 等旧内容。
- cover/media 对象引用、interaction 历史、旧 audit/debug 临时记录。
- Rabbit/Valkey/Elasticsearch 派生运行态。

保留：

- 已生成的迁移前备份只作为本次撤销导入的恢复证据，不能再作为默认执行步骤。
- 旧 `ircs-system` 退役前的非敏感快照作为历史对照保留，不包含 Secret 明文。

## GitOps bootstrap 范围

- 初始化本地 `ircs-project-v3` git 仓库并推送到新 Gitea 仓库。
- Gitea Actions 使用 V1 runner 运行 `.gitea/workflows/build-and-push.yml`。
- CI 通过 Jib 构建并推送 ARM64 Linux 镜像到 Harbor。
- GitOps config repo 后续引用固定 tag 或 digest，部署到 `ircs-prod`。
- `huawai.sophia.fr.eu.org` 最终 HTTPRoute 位于 `ircs-prod`，切流后退役旧 `ircs-system` route。

## 验收标准

1. 新 Gitea 代码仓库存在，并包含 V3 后端代码、CI workflow 和 `.gitignore`。
2. Gitea runner 能构建并推送所有 Java service / worker / BFF / migrator ARM64 镜像。
3. Harbor 中可查到对应 tag/digest。
4. `ircs-prod` GitOps 清单可由 ArgoCD 同步。
5. 旧生产数据 schema preflight 可追踪；最终执行策略已改为不迁移、干净起步。
6. 生产切流前存在备份、恢复、回滚和 route 退役方案。
7. `ircs-prod-core` 与 `ircs-prod-edge` 由 ArgoCD 同步并保持 Healthy。

## 当前进展

2026-06-14：

- 已创建 Gitea 仓库 `https://gitea.mnnu.eu.org/gitea-admin/ircs-project-v3`。
- 已初始化本地 git 并推送 `main`。
- 已配置 repo Actions secrets：`HARBOR_USERNAME`、`HARBOR_PASSWORD`。
- 已接入 Gitea runner label `ircs`。
- Actions run `#6` 手动触发成功，run `#7` push 自动触发成功，commit
  `a9a813b160df7c638604bfbefa26dcd88666b876`。
- Harbor tag `sha-a9a813b160df` 已核验 21 个 Java/migrator 镜像全部存在。
- 详细证据见 `docs/reports/124-ircs-prod-gitops-bootstrap-runner-harbor.md`。
- 已新增独立 GitOps 仓库
  `https://gitea.mnnu.eu.org/gitea-admin/ircs-prod-config.git`，本地路径
  `D:\WorkSpace\Project\ircs\ircs-prod-config`。
- GitOps 仓库已推送 `ircs-prod/core`、`ircs-prod/edge-cutover`、`apps/ircs-prod-core-application.yaml`
  和 `apps/ircs-prod-edge-application.yaml`，commit `f83d115`。
- `ircs-prod/core` 已固定 21 个 Java/migrator image digest，并固定 `huawai` 与
  `ircs-frontend` digest；Secret 不入 Git，改由脚本从旧 namespace 复制并补齐 V3 token。
- 已准备脚本：
  - `scripts/prepare-prod-secrets.ps1`：复制旧 `ircs-system` Secret/PullSecret 到 `ircs-prod` 并生成 V3 缺失 token。
  - `scripts/migrate-compatible-data.ps1`：默认 dry-run，`-Execute` 后迁移兼容业务表，排除 changelog 与运行态派生表。
  - `scripts/cutover-httproute.ps1`：默认 dry-run，`-Execute` 后暂停旧 `ircs-app` 自动同步、应用新 route、删除旧业务 route。
- 已完成增量构建远程验证：workflow run `#8` 使用 `build_scope=ircs-task-service`
  成功，仅 Harbor `ircs-task-service:sha-f5c9db698318` 存在，同 tag 不存在于抽样其他服务。
- 已删除废弃 `ircs-dev` namespace；后续开发、测试和生产候选验证统一在 `ircs-prod`。
- 已执行 `scripts/prepare-prod-secrets.ps1`，创建 `ircs-prod` 并准备运行时 Secret/PullSecret。
- 已创建并同步 ArgoCD Application：
  - `ircs-prod-core`：`Synced/Healthy`。
  - `ircs-prod-edge`：`Synced/Healthy`。
  - `ircs-prod-migrator`：保留为手动应用，避免 core 每次同步重复运行 migrator。
- 已完成旧数据 schema preflight：43 张兼容业务表通过；随后按用户决策取消旧数据迁移。
- 曾启动的数据导入被中止后，已用迁移前 `ircs-prod` 备份恢复干净数据库：
  `unified_videos=0`、`raw_videos=0`、`episodes=0`、`system_configs=133`、
  `databasechangelog=138`。
- 已完成 HTTPRoute 切流：
  - `ircs-prod/huawai-route -> ircs-frontend-gateway:80`。
  - `ircs-prod/ircs-route -> ircs-frontend-gateway:8080`。
  - 旧 `ircs-system/huawai-route` 与 `ircs-system/ircs-route` 已删除。
  - 旧 `ircs-app` 自动 self-heal 已关闭，防止旧 route 抢回流量。
- 已完成集群内和公网真实验证：
  - ClusterIP：gateway、portal/admin frontend、portal/admin BFF、identity/task/search/ops readiness 均 2xx。
  - Public：`https://huawai.sophia.fr.eu.org/gateway-health`、`https://ircs.sophia.fr.eu.org/gateway-health`
    均 `204`；两个根路径均返回对应 portal/admin HTML。
- 已删除旧 ArgoCD Application `ircs-app`、旧 `ircs-system` namespace，并清理旧
  `ircs-app` tracking 的 monitoring otel/tempo 资源；当前 ArgoCD 仅保留
  `ircs-prod-core`、`ircs-prod-edge`、`ircs-prod-migrator`。
- 已修复 BFF 生产 target：`APP_BFF_*_BASE_URL` 指向 `ircs-prod.svc.cluster.local`，
  GitOps revision `8d9e11a500906898eb38d12a78d1f10850329af6` 已同步。
- 已完成 BFF 修复后的公网真实回归：admin PoW、数学验证码、真实登录、dashboard
  子接口组、configs、data-sources、collection-tasks、portal 首页和 portal login PoW 均通过。

## 下一执行批次

1. 做生产候选 go/no-go 报告和安全最终复核。
2. 做 `ircs-prod` 真实业务回归：登录、portal 浏览、admin 配置、任务触发、provider/source 抽样。
3. 补 rollback runbook：以 GitOps revision 回滚、备份恢复和重新应用历史快照为主；旧
   `ircs-system` / `ircs-app` 不再作为默认回滚目标。
