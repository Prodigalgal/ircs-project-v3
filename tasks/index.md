# IRCS V3 任务入口

状态：进行中

更新时间：2026-06-14

完整历史归档：

- `docs/reports/archive/2026-06-13-doc-compaction/tasks-index-2026-06-14-full-log.md`

## 执行规则

- 后续功能验证只允许连接 `ircs-prod` K8S 集群。
- 验证必须使用真实配置、真实凭据、真实数据库数据和真实外部链路。
- 禁止用本地 smoke、mock/fake payload 或造数任务作为完成证据。
- 本地 compile/build/Jib 只作为发布前守卫，不作为功能验收。
- 开发库允许污染；生产会重新部署独立 namespace。

## 当前 Goal

`030-production-candidate-k8s-real-validation-closeout`

目标：把 V3 从“实现基本完成”推进到“生产候选可判断”，形成代码、K8S rollout、
真实在线测试证据、剩余风险清单和生产 namespace 重部署准备。所有功能验收只承认
`ircs-prod` 集群真实入口、真实配置、真实凭据、真实数据和真实外部链路。

生产目标：`ircs-prod`。旧 `ircs-system` 不再作为 V3 生产命名空间，只作为现状对照和退役对象。

## 进度估算

| 领域 | 进度 | 当前判断 |
| --- | ---: | --- |
| HTTP route parity | 100% | V1 对外路由缺口维持 `missing=0` |
| 开发 MVP 可用度 | 98% | 门户/BFF/任务/运行时主链路已通过集群入口验证 |
| V1/V3 行为 parity | 97% | 主行为已迁移，剩组合场景、边缘源站和生产回归 |
| 029 Rabbit MT/PT/DT | 97% | 成功/失败/retry/DLQ/provider-gated trend-sync/search outbox/Prometheus 已真实验证 |
| 生产就绪度 | 95% | `ircs-prod` GitOps core/edge、公网切流、旧 `ircs-system` 退役和 BFF target 修复已完成；剩安全复核、回滚演练和 go/no-go 报告 |

## 进行中主线

| 任务 | 当前状态 | 下一步 |
| --- | --- | --- |
| `030-production-candidate-k8s-real-validation-closeout` | 主线；`ircs-prod` 已成为唯一验证目标，core/edge 已 `Synced/Healthy`，公网 HTTPRoute 已切到 V3，旧 `ircs-system`/`ircs-app` 已退役 | 生产候选报告、备份/回滚和安全复核 |
| `029-rabbit-redis-task-system-redesign` | 约 97%；Rabbit MT/PT/DT、hot-state、DB flush、Admin/Ops runtime、DLQ 运维、provider-gated trend-sync、Prometheus 队列指标已上线 | 多副本 contention、扩展源站边缘矩阵 |
| `028-22-production-candidate-closeout-parent` | 生产候选父任务；A-F 关键真实窗口已完成 | 生产候选报告、统一回归 |
| `028-21-security-source-network-hardening` | JWT/ops/storage/auth rate limit/source network hardening 已实现 | Admin 入口真实策略选择与错误展示回归 |
| `028-20-post-parity-runtime-platform-breakdown` | runtime/platform 主体完成；mTLS 已撤销 | lock contention、metrics、cache hit/evict 和统一业务行为测试 |
| `028-12-service-audit-rollout-and-worker-job-audit` | 已知 worker/job audit matrix 实现闭环 | 新增 ingestion/audit 的 live 证据归并 |
| `028-10-production-governor-credential-pool-parity` | runner/governor 基座无已知占位拒绝缺口 | dry-run/execute gate/audit 组合证据 |
| `028-16-f6-source-field-auto-mapping-hardening` | 自动映射和下拉点选已实现 | 真实资源站 live matrix 纳入统一回归 |
| `031-ircs-prod-gitops-data-migration` | Gitea/Harbor/ArgoCD/GitOps/core/edge/cutover 已闭环；旧数据迁移取消，`ircs-prod` 干净起步；旧 `ircs-system` 与旧 `ircs-app` 已删除 | 回滚 runbook、安全复核和 go/no-go |

## 029 最新线上事实

- 当前相关 digest：
  - task-service `sha256:5cfa2a92833305052f7eabd2300ecf7030439e5becaa199411dc86a7ba46da96`
  - scraper-service `sha256:318954490aec86fa98aeacd69577bfab2f4c122312ef3ec51e75d1257199ec25`
  - content-service `sha256:67c6c5e0315445bdf77a76412ca0f7c6db5f8a0954b07395ff4eacf4da4de981`
  - search-service `sha256:b64220ab2285b0667ce92ad22c9993b726cbc4e49fbabe29fdcb642ab8c639a3`
  - catalog-service `sha256:0a1c221fc60be117bf4f3681da5441aa1aa8ae46cb4ae86f94a97ffcd06656a9`
  - ops-service `sha256:a674d3304f704ab410f1b43e7ff49b4a43fc2c12fa7a47afe4e736de091a0ad9`
- 25 源一页矩阵：`23/25` 成功，`2/25` 为 page/source 失败。
- 成功源站最新任务合计 `471` 条 detail/result 成功。
- terminal detail failure 已用真实 `魔都资源` 404 窗口验证：`detailFailed=20`，MT/DB `FAILED`。
- DoT 默认已修复为 `one.one.one.one:853`，catalog/scraper 真实 DOT 验证成功。
- IPv6-only 当前失败是基础设施/源站限制：Pod 无默认 IPv6 route，目标源站无 AAAA。
- provider-gated trend-sync 已真实跑通：2 个 provider 抓取 20 条，content 创建 20 个 trend ghost，
  生成 3 个 discovery collection task，其中 1 个真实采集 1/1 成功。
- search outbox relay 已在 dev 开启并清空 DB outbox；search sync audit：raw 481、unified 20 成功。
- Rabbit task/retry/DLQ 与 `q.ingest.video` 当前主链路无积压。
- Admin gateway 已用真实登录、PoW、数学验证码验证数据源网络策略入口：`iKun资源`
  `APACHE_HC5 + HTTP_1_1 + IPV4_PREFERRED + DOT one.one.one.one:853` fetch-sample 成功，
  返回 20 条真实数据。
- ops-service 已暴露 Rabbit queue Prometheus gauges；Prometheus 可查询 DLQ depth、task queue depth、
  consumers、worker duration、lock ops 和 search outbox delay。
- portal/admin 统一入口已完成当前轮真实回归；真实 Admin 采集任务
  `019ec381-6c91-76bc-82d2-4742ef71ca85` 终态 `COMPLETED`，`20/20` 成功。
- 当前生产入口真实状态：`envoy-gateway-system/production-gateway` 已 Programmed；
  Envoy LoadBalancer service 监听 `80:30971/TCP,443:30432/TCP`；
  `ircs-prod/huawai-route` 承接 `huawai.sophia.fr.eu.org`，
  `ircs-prod/ircs-route` 承接 `ircs.sophia.fr.eu.org`。

## 剩余任务

P0：

1. 生产候选报告：汇总 go/no-go、`ircs-prod`、备份和回滚。
2. 回滚演练计划：恢复旧 route、重新启用旧 `ircs-app` automated self-heal、或回滚 GitOps revision。
3. 安全/资源最终复核：Actuator 暴露面、JWT/PoW/rate limit、ops/storage internal API、资源 requests/limits。
4. `ircs-prod` 生产候选真实业务回归：登录、portal、admin 配置、任务触发、provider/source 抽样。

P1：

1. 多副本 contention：资源允许时验证 task/scraper/lock 多副本行为。
2. 源站网络矩阵扩展：补 429、连接超时、老旧 TLS/HTTP 兼容和错误展示。
3. cache L1/L2/L3：补热读 hit/evict 真实证据。
4. runner/governor：补 dry-run 默认、execute gate、audit 的组合证据。

P2：

1. service/worker audit：把 ingestion 等后续新增 audit 证据并入总账。
2. 长尾 provider live smoke：对低频 provider 做生产前抽样。

## 最近完成

- 压缩 029 任务长文，全文归档到 `docs/reports/archive/2026-06-13-doc-compaction/`。
- 压缩 `tasks/index.md`，全文归档到同一 archive 目录。
- 修复 DoT 默认 endpoint 并 rollout catalog/scraper。
- 验证 terminal `detail.done(false)` 真实终态。
- 验证 provider-gated trend-sync，开启并验证 search outbox relay。
- 归档 `tasks/done/028-22-f-unified-live-smoke-execution.md`。
- 补 Common API 5xx server-side logging，客户端错误模型不暴露内部信息。
- 刷新 030 goal/task/requirements 的进度和剩余项。
- Admin 源站网络策略已通过 gateway 真实入口验证，覆盖 PoW 登录、策略参数透传和 fetch-sample。
- ops-service 已新增 Rabbit queue Prometheus gauges，镜像 digest
  `sha256:a674d3304f704ab410f1b43e7ff49b4a43fc2c12fa7a47afe4e736de091a0ad9`，`ircs-dev` rollout 成功。
- portal/admin 统一真实回归已完成当前轮：portal 首页、metadata、explore/search/detail；admin
  dashboard/config/data-sources/audit/DLQ/task-runtime；真实采集任务 20/20 成功。
- 新增 ADR：`docs/decisions/009-ircs-prod-gitops-edge-routing.md`，固化 `ircs-prod`
  生产目标、ArgoCD/Gitea 发布链路、Envoy/NLB/Cloudflare 入口链路和旧 `ircs-system` 退役边界。
- 新增 `docs/requirements/031-ircs-prod-gitops-data-migration.md`，固化 Gitea runner/Harbor/ArgoCD
  bootstrap 与旧生产数据兼容迁移原则。
- 新增 `.gitignore`、`.gitea/workflows/build-and-push.yml` 和
  `scripts/ci/jib-build-push-all.sh`，为新 Gitea 仓库自动构建 ARM64 镜像做准备。
- 创建并推送 Gitea 仓库 `gitea-admin/ircs-project-v3`；Actions run `#6` 成功，21 个
  Java/migrator 镜像均推送到 Harbor，tag `sha-a9a813b160df`。
- 删除废弃 `ircs-dev` namespace；后续开发、测试和生产候选验证统一在 `ircs-prod`。
- 完成 `ircs-prod` GitOps core/edge：`ircs-prod-core` 与 `ircs-prod-edge` 均
  `Synced/Healthy`，24/24 Deployment Ready，3/3 StatefulSet Ready。
- 决策取消旧数据迁移，已把中断导入后的 `ircs-prod` 恢复为干净初始库。
- 完成公网切流：`huawai.sophia.fr.eu.org` 和 `ircs.sophia.fr.eu.org` 已由
  `ircs-prod` HTTPRoute 承接，公网 `/gateway-health` 均返回 `204`。
- 删除 ArgoCD 旧 Application `ircs-app`、旧 namespace `ircs-system`，并清理旧
  `ircs-app` 遗留在 `monitoring` namespace 的 otel/tempo 资源；当前 ArgoCD 仅保留
  `ircs-prod-core`、`ircs-prod-edge`、`ircs-prod-migrator`。
- 修复 admin/portal BFF 仍回退 `ircs-dev.svc.cluster.local` 的生产配置问题，GitOps
  revision `8d9e11a500906898eb38d12a78d1f10850329af6` 已同步，BFF rollout 成功。
- 公网真实回归：admin PoW + 数学验证码 + 真实登录成功；configs、data-sources、
  collection-tasks 和 dashboard 子接口组均 `200`；portal 首页和 portal login PoW 均 `200`。
- 统一未知映射 404 已上线：admin dashboard root、admin unknown route、portal unknown route
  均返回 `404 / http.404 / Resource not found`。
- V3 内部调用链接口已做静态对齐审计：45 条 BFF route target 和 31 条 `/internal/...`
  调用常量均匹配到 Controller 映射，未发现新增接口缺口。

## 历史完成入口

历史完成任务保留在：

- `tasks/done/`
- `docs/reports/archive/2026-06-13-doc-compaction/`
- 各父任务对应的 `docs/requirements/`、`docs/decisions/`、`docs/reports/`
