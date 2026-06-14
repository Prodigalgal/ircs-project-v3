# 031 ircs-prod GitOps bootstrap and V1 data migration

状态：进行中

日期：2026-06-14

## 目标

把 V3 代码与生产部署链路接入 Gitea runner / Harbor / ArgoCD，并为旧生产数据进入
`ircs-prod` 建立兼容迁移边界。

## 关键决策

- 新生产 namespace：`ircs-prod`。
- 旧生产 namespace：`ircs-system`，只作为迁移来源、对照和退役对象。
- 代码仓库：新建 Gitea 仓库，建议名 `ircs-project-v3`。
- GitOps 仓库：参考旧 V1 仓库 `https://gitea.mnnu.eu.org/gitea-admin/ircs-config`，
  但 V3 清单必须写向 `ircs-prod`。
- 镜像仓库：`harbor.mnnu.eu.org/ircs`。
- 生产数据：能兼容迁移的迁移；无法可靠兼容的数据明确丢弃并记录原因。

## 数据迁移原则

1. 迁移前必须备份旧 `ircs-system` PostgreSQL、RabbitMQ、Valkey、Elasticsearch 和媒体对象引用。
2. DB 是迁移唯一事实源；Rabbit/Valkey 只作为运行态，可重建的运行态默认不迁移。
3. 迁移脚本必须先做 schema/version preflight，不对表结构做猜测。
4. 可兼容数据迁移到 V3 表结构；不可兼容或运行态数据丢弃，并写入迁移报告。
5. 迁移后重建派生物：search index、hot-state snapshot、任务 runtime、缓存、部分 audit 聚合。
6. 管理员账号、运行时 Secret、provider credential 优先使用 `ircs-prod` Secret/ConfigMap 注入；
   旧 DB seed 只能作为最低优先级兜底。

## 初始迁移分类

应迁移：

- 管理员/会员账号、角色、权限和第三方 provider 绑定中可映射字段。
- SystemConfig、credential metadata、provider key 引用；secret value 走 K8S Secret 或 credential-service。
- catalog 字典、data source、source category/field mapping、source network options。
- raw video、unified video、playlist、person、category、genre、area、language、tag 等业务数据。
- cover/media 对象引用；对象本体按 R2/存储策略校验后保留。
- interaction 中仍有业务价值的反馈、消息、收藏、观看历史、进度。

默认丢弃或重建：

- Rabbit 队列、unacked message、retry/DLQ 运行态。
- Valkey/cache/hot-state、分布式锁、rate-limit counter、PoW/captcha nonce。
- 旧 session/token、临时登录状态。
- search/ES 派生 index，迁移后由 search outbox/reindex 重建。
- 旧任务执行中的细粒度 runtime；只保留可映射的 master ledger，必要时重新执行任务。
- 无法映射到 V3 owner-service 边界的旧 audit/debug 临时记录。

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
5. 旧生产数据迁移 dry-run 生成报告：迁移成功、丢弃、重建三类数据均可追踪。
6. 生产切流前存在备份、恢复、回滚和 route 退役方案。

