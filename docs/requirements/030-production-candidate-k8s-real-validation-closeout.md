# 030 Production Candidate K8S Real Validation Closeout

状态：进行中

日期：2026-06-13

## 目标

把 V3 从“实现基本完成”推进到“生产候选可判断”。后续所有验证必须连接 `ircs-prod`
K8S 集群，使用真实配置、真实凭据、真实数据库数据和真实外部链路；不再把本地 smoke、
mock/fake payload 或造数脚本作为完成证据。

## 范围

- 收敛超长 docs/tasks，保留当前态、剩余任务、风险和归档证据入口。
- 完成 029 Rabbit MT/PT/DT 任务系统剩余真实矩阵。
- 补齐 028-20 runtime/platform 剩余真实证据和业务热读缓存 owner 扩展。
- 补齐 028-21 security/source network hardening 的真实源站网络策略矩阵。
- 补齐 028-12 audit/worker-job 与 028-10 runner/governor 的真实在线证据。
- 汇总 `ircs-prod` 生产命名空间重部署前的准入清单、风险和回滚边界。

## 非目标

- 不追求当前 `ircs-prod` 生产候选数据库纯净；真实验证允许写入和污染。
- 不做本地服务 smoke，不用 fake/mock/造数任务伪造完成度。
- 不在本阶段重新引入手动应用层 mTLS；mTLS 后续只作为基础设施/服务网格议题评估。

## 完成标准

1. `ircs-prod` 全部核心 Pod Ready，Rabbit/DB/Valkey/ES/Prometheus 基础依赖健康。
2. portal/admin/BFF/gateway 可通过真实入口访问并用于持续验证。
3. 029 任务系统完成成功、业务失败、retry 耗尽、DLQ 运维、DB flush、Admin/Ops runtime 真实证据。
4. 源站网络矩阵覆盖 DNS/DoH/DoT/IP policy、IPv4/IPv6、HTTP/1.0/老旧协议、HTML/非 JSON、
   4xx/429/5xx、连接超时等真实外部情况。
5. runner/governor/audit/worker-job/metrics/cache/lock 关键运行时能力都有真实 K8S 证据。
6. 文档入口短、状态清楚，长历史只保留在 archive。
7. 输出生产候选报告：完成度、未闭环项、生产 namespace 重部署步骤、回滚方案和准入/不准入结论。

## 生产目标拓扑

生产目标 namespace 固定为 `ircs-prod`。旧 `ircs-system` 不再作为 V3 生产目标，只作为回滚参考、
冲突排查和退役对象。

发布链路：

```text
local / CI code
  -> Gitea
  -> image build and publish
  -> ArgoCD Application
  -> ircs-prod
```

入口链路：

```text
Cloudflare A huawai.sophia.fr.eu.org -> 129.150.47.1
  -> Oracle Cloud NLB 80/443
  -> nodePort 30971/30432
  -> envoy-gateway-system/production-gateway
  -> ircs-prod HTTPRoute
  -> V3 frontend gateway
```

当前切流后核对显示：`ircs-prod-core` 与 `ircs-prod-edge` 均为 `Synced/Healthy`；
`huawai.sophia.fr.eu.org` 由 `ircs-prod/huawai-route -> ircs-frontend-gateway:80`
承接，`ircs.sophia.fr.eu.org` 由 `ircs-prod/ircs-route -> ircs-frontend-gateway:8080`
承接。旧 `ircs-system/huawai-route` 和 `ircs-system/ircs-route` 已删除，旧
`ircs-app` automated self-heal 已关闭。

## 当前进度估算

- HTTP route parity：约 100%，历史 missing=0。
- 门户/BFF/gateway：约 97%，已完成 `ircs-prod` 集群内和公网真实入口回归。
- V1 行为级迁移：约 87%-90%，主要剩长尾 provider、源站边缘网络、异步组合场景和生产回归。
- runtime/platform/security/cache/lock/audit：约 85%-88%，Prometheus/Rabbit/lock/audit 主证据已补，
  剩多副本 contention、cache hit/evict 和 runner/governor 组合证据。
- 029 Rabbit 任务系统：约 97%，25 源一页真实任务矩阵达到 `23/25` 成功，核心网络策略、
  page 失败、detail retry 自恢复、terminal detail 失败、DoT 默认修复、IPv6 限制判因、
  provider-gated trend-sync、search outbox、Admin 网络策略和 Prometheus 观测均已跑通。
- 生产准入整体：约 96%，`ircs-prod` GitOps core/edge、公网切流和 go/no-go 报告已完成，
  离无人值守正式生产还差入口 fallback 修复、回滚演练和配置有效源可观测性对齐。

## 最新真实证据

- Admin gateway 已验证源站网络策略入口：真实登录、PoW、数学验证码后，对 `iKun资源`
  使用 `APACHE_HC5 + HTTP_1_1 + IPV4_PREFERRED + DOT one.one.one.one:853`
  fetch-sample 成功，返回 20 条真实数据。
- ops-service 已新增 Rabbit queue Prometheus gauges，digest
  `sha256:a674d3304f704ab410f1b43e7ff49b4a43fc2c12fa7a47afe4e736de091a0ad9`，
  `ircs-dev` rollout 成功；Prometheus 可查 task queue depth、DLQ depth、consumers、
  worker duration、lock ops 和 search outbox delay。
- portal/admin 当前轮真实回归完成：portal 首页、metadata、explore、keyword search、detail；
  admin dashboard、configs、data-sources、request audit、worker-job audit、Rabbit DLQ、task runtime。
- Admin 真实采集任务 `019ec381-6c91-76bc-82d2-4742ef71ca85` 终态 `COMPLETED`，
  `statProcessed=20/statSuccess=20/statFailed=0`。
- `ircs-prod` 入口切流验证：集群内 gateway、portal/admin frontend、portal/admin BFF、
  identity/task/search/ops readiness 均返回 2xx；公网
  `https://huawai.sophia.fr.eu.org/gateway-health` 和
  `https://ircs.sophia.fr.eu.org/gateway-health` 均返回 `204`，根路径返回对应
  portal/admin HTML。
- 深层行为级 go/no-go live 验证已完成，报告见
  `docs/reports/126-production-candidate-go-no-go.md`。本轮 `28 PASS / 2 WARN / 0 FAIL`；
  真实任务 `019ec64f-6ddb-725b-a588-0111ace83fb5` 终态 `COMPLETED`，`20/20` 成功；
  Rabbit `93` 个队列全部 drain；ArgoCD core/edge 为 `Synced/Healthy`。
- 当前结论为生产候选受控 `Conditional GO`；无人值守正式生产仍为 `NO-GO`，阻塞项是
  公网 `/actuator/*`、`/internal/*` GET fallback、回滚演练和配置有效源可观测性。
