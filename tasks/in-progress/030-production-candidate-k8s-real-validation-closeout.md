# 030 production candidate K8S real validation closeout

状态：进行中

来源：

- `docs/requirements/030-production-candidate-k8s-real-validation-closeout.md`

## 执行原则

- 只用 `ircs-prod` K8S 集群做功能验证。
- 只使用真实配置、真实凭据、真实数据库数据和真实外部链路。
- 允许污染 `ircs-prod` 生产候选数据库；后续正式生产窗口可重新初始化。
- 生产目标 namespace 是 `ircs-prod`；旧 `ircs-system` 不再作为 V3 生产目标。
- 本地 compile/build/Jib 可以作为准备动作，但不能作为功能完成证据。
- 禁止用本地 smoke、mock/fake payload、造数任务替代真实验证。

## 当前进度

| 领域 | 进度 | 状态 |
| --- | ---: | --- |
| HTTP route parity | 100% | missing=0，后续关注行为细节 |
| portal/admin/BFF/gateway | 98% | `ircs-prod` 集群内与公网入口、BFF prod target、admin/portal PoW 已通过真实验证 |
| V1 行为级迁移 | 98% | 主链路和 go/no-go live 场景完成；剩入口 fallback、长尾源站、多副本和非空 portal 数据回归 |
| runtime/platform/security/cache/lock/audit | 90% | Prometheus/Rabbit/lock/audit 主证据已补，入口 fallback 与 cache hit/evict 仍需收口 |
| 029 Rabbit MT/PT/DT | 97% | 23/25 源站、失败/retry/DLQ、DoT/IPv6、provider-gated trend-sync、search outbox、Admin 网络策略和 Prometheus 已验证 |
| 生产候选准入 | 96% | `ircs-prod` core/edge 已同步并切流，旧 `ircs-system`/`ircs-app` 已退役，go/no-go 报告已出；剩入口 fallback、回滚演练和配置有效源 |

## 剩余任务清单

1. 入口 fallback 修复：公网 `GET /actuator/*`、`GET /internal/*` 当前返回 admin SPA HTML `200`，
   需改为显式 `404/403`。
2. `ircs-prod` GitOps 执行：Secret、core Application、edge HTTPRoute 已完成；后续只保留手动 migrator。
3. 安全最终复核：JWT/PoW/rate limit、Actuator、ops/storage internal API、统一错误模型和敏感日志；
   go/no-go 已确认 PoW 登录、JWT Secret 注入和敏感 config redaction 通过。
4. 029 源站网络扩展：补 429、连接超时、老旧 TLS/HTTP 兼容等长尾真实证据。
5. 028-20 runtime：补 distributed lock 多副本 contention、cache L2/L3 hit/evict 的 K8S/Prometheus 证据。
6. 028-10 runner/governor：补真实 runner dry-run/execute gate/audit 的组合证据。
7. 旧生产数据：已决策不迁移，`ircs-prod` 干净起步；剩回滚演练和历史备份处置。

## 本轮已完成

- 创建长期 goal。
- 只读刷新 `ircs-dev`：28/28 Pod Running，23/23 Deployment ready，task/ingest/fetch/search/notification
  相关 Rabbit 队列 messages_ready/messages_unacknowledged 均为 0。
- 第一批压缩 5 个超长/冗长入口文档，原文保留到
  `docs/reports/archive/2026-06-13-doc-compaction/`。
- 追加压缩 `tasks/done/028-06-real-provider-live-parity.md`，全文归档到
  `docs/reports/archive/2026-06-13-doc-compaction/028-06-real-provider-live-parity-full-log.md`；
  当前非归档 `docs/`/`tasks/` Markdown 入口均低于 500 行。
- 完成 029 trend discovery K8S 真实验证：真实关键词、真实 iKun 源站、taskId
  `019ec172-e5dc-75a4-9bc2-bc4432c01b60`，MT/PT/DT runtime 与 DB `COMPLETED`，
  `detailSucceeded=1`，Rabbit task/ingest queues ready/unacked 均为 0。
- 完成 029 detail retry 与失败审计根因线上闭环：真实 `茅台资源` detail transient failure
  经 retry 自恢复到 `20/20` 成功；真实 `卧龙资源` page failure 的 worker-job audit
  已保留 parser/source 根因，不再被 `token 'null'` 误脱敏；当前 scraper digest
  `sha256:8ed6a7cd0ad659cc92f7a5acc62db032d49cb5dd7ccfc3273f5fa127b8b1fdb2`。
- 完成 029 25 源一页真实任务矩阵当前轮：`23/25` 源站成功，`2/25` 为 page/source 级失败；
  成功源站最新任务合计 `471` 条 detail/result 成功、`0` 条 detail 失败，Rabbit task/retry/DLQ
  与 ingestion 队列最终均清零。
- 完成 029 源站网络核心策略矩阵：`魔都资源` 和 `电影天堂资源` 覆盖 HTTPS/HTTP 源，
  `AUTO/SYSTEM`、`JDK HTTP_1_1`、`APACHE_HC5 HTTP_1_1`、`HTTP_1_0`、`IPV4_ONLY`、
  `IPV4_PREFERRED`、`IPV6_PREFERRED`、`DOH` 在对应源上成功；`DOT`、`IPV6_ONLY`
  当前为真实 502，`CURL_NATIVE` 线上 fail-closed。
- 完成 029 DoT 默认修复和判因：集群到 `cloudflare-dns.com:853` 超时，但
  `one.one.one.one:853`、`1.1.1.1:853`、`dns.google:853`、`8.8.8.8:853` 可用；
  默认 endpoint 已切到 `one.one.one.one:853`，catalog/scraper 真实 DOT 验证成功。
- 完成 029 IPv6-only 判因：Pod 只有 link-local IPv6 且无默认 IPv6 route，目标源站也无 AAAA；
  当前 `IPV6_ONLY` 失败属于真实基础设施/源站限制。
- 完成 029 terminal detail failure：临时将真实 `魔都资源` detail path 指向真实 404，
  列表成功、20 个 detail 全部 `detail.done(false)`，runtime/DB 终态 `FAILED`，
  `stat_failed=20`，Rabbit task/ingest 队列清零；源站配置已恢复。
- 完成 029 provider-gated trend-sync：真实 provider 抓取 20 条，content-service 创建
  20 个 trend ghost，排队 3 个 discovery task；3 个任务均 `COMPLETED`，其中 1 个
  `stat_processed=1/stat_success=1`。
- 完成 search outbox relay dev 开启和验证：`APP_SEARCH_OUTBOX_RELAY_ENABLED=true`，
  DB outbox 清零，Rabbit search 队列 drain，search sync audit 记录 raw 481、unified 20 成功。
- 完成统一 5xx server-side logging：客户端继续只返回统一错误模型，服务端日志携带异常堆栈、
  path 和 correlationId，已通过 scraper/content rollout 验证。
- 完成 Admin 源站网络策略真实入口验证：通过 gateway 完成 admin PoW + 数学验证码登录，
  对 `iKun资源` 使用 `APACHE_HC5 + HTTP_1_1 + IPV4_PREFERRED + DOT one.one.one.one:853`
  调用 fetch-sample 成功，返回 20 条真实数据。
- 完成 Prometheus 可观测性补齐：ops-service 新增 Rabbit queue gauges 并 rollout，
  digest `sha256:a674d3304f704ab410f1b43e7ff49b4a43fc2c12fa7a47afe4e736de091a0ad9`；
  Prometheus 已能查询 task queue depth、DLQ depth、consumers、worker duration、lock ops
  和 search outbox delay。
- 完成 portal/admin 统一真实回归当前轮：portal 首页、metadata、explore、keyword search、detail；
  admin dashboard、configs、data-sources、request audit、worker-job audit、Rabbit DLQ、task runtime。
- 完成真实 Admin 采集任务触发：任务 `019ec381-6c91-76bc-82d2-4742ef71ca85`
  终态 `COMPLETED`，`statProcessed=20/statSuccess=20/statFailed=0`。
- 完成生产入口只读核对：`ircs-prod` 尚不存在；旧 `ircs-system` 仍持有
  `huawai-route/ircs-route/kibana-route`；旧 `ircs-app` ArgoCD Application 仍指向
  `ircs-system`；Envoy `production-gateway` 与 Oracle NLB nodePort `30971/30432` 可沿用。
- 固化生产入口决策：新增 `docs/decisions/009-ircs-prod-gitops-edge-routing.md`。
- 固化生产数据迁移和 GitOps bootstrap：新增
  `docs/requirements/031-ircs-prod-gitops-data-migration.md`。
- 准备新 Gitea 仓库 CI：新增 `.gitignore`、`.gitea/workflows/build-and-push.yml`、
  `scripts/ci/jib-build-push-all.sh`，runner 将使用 Jib 推送 `linux/arm64` 镜像到 Harbor。
- 完成新 Gitea 仓库和 runner 链路：`gitea-admin/ircs-project-v3` 已推送，Actions
  run `#6` 成功，Harbor tag `sha-a9a813b160df` 的 21 个 Java/migrator 镜像已核验。
- 完成源码增量构建：新增 affected build 脚本，workflow run `#8` 使用
  `build_scope=ircs-task-service` 成功；Harbor 只出现
  `ircs-task-service:sha-f5c9db698318`，抽样其他服务无同 tag。
- 完成独立 GitOps 仓库：`gitea-admin/ircs-prod-config` 已推送 commit `f83d115`，
  包含 `ircs-prod/core`、`ircs-prod/edge-cutover`、ArgoCD Application、Secret 准备、
  兼容数据迁移和 HTTPRoute 切流脚本。
- 完成 GitOps 静态验证：`kubectl kustomize`、`kubectl apply --dry-run=client -k`
  对 core/edge 通过，ArgoCD Application client dry-run 通过，脚本 PowerShell AST parse 通过，
  GitOps repo 未检出明文真实凭据。
- 删除废弃 `ircs-dev` namespace；后续开发、测试和生产候选验证统一使用 `ircs-prod`。
- 完成 `ircs-prod` Secret 准备、core Application 同步和 Pod Ready：
  `ircs-prod-core` 为 `Synced/Healthy`，24/24 Deployment Ready，3/3 StatefulSet Ready。
- 取消旧生产数据迁移，按干净数据起步。中止半导入后已恢复迁移前 `ircs-prod` 备份，
  当前 `unified_videos=0`、`raw_videos=0`、`episodes=0`、`system_configs=133`、
  `databasechangelog=138`。
- 完成 `ircs-prod` edge cutover：旧 `ircs-system/huawai-route` 和 `ircs-system/ircs-route`
  已删除，新 `ircs-prod/huawai-route` 与 `ircs-prod/ircs-route` 已 `Accepted/Resolved`；
  `ircs-prod-edge` 为 `Synced/Healthy`。
- 完成集群内真实验证：gateway、portal/admin frontend、portal/admin BFF、
  identity/task/search/ops readiness 均返回 2xx。
- 完成公网真实验证：`https://huawai.sophia.fr.eu.org/gateway-health` 和
  `https://ircs.sophia.fr.eu.org/gateway-health` 均返回 `204`，两个根路径均返回对应
  portal/admin HTML。
- 完成旧 ArgoCD 部署包退役：删除 `argocd/ircs-app`，删除旧 `ircs-system` namespace，
  清理 `monitoring` namespace 内旧 `ircs-app` tracking 的 otel/tempo 资源；复核当前
  ArgoCD 仅保留 `ircs-prod-core`、`ircs-prod-edge`、`ircs-prod-migrator`。
- 完成 BFF prod target 修复：`APP_BFF_*_BASE_URL` 已集中指向
  `ircs-prod.svc.cluster.local`，GitOps revision
  `8d9e11a500906898eb38d12a78d1f10850329af6` 已同步，admin/portal BFF rollout 成功。
- 完成 BFF 修复后的公网真实回归：admin `/api/v1/auth/pow?scope=admin.login` 从 `500`
  恢复为 `200`；admin PoW + 数学验证码 + 真实登录成功；configs、data-sources、
  collection-tasks、dashboard statistics/analysis/metrics/task-runtime/source-quality 均 `200`；
  portal 首页、backend 兼容首页、portal login PoW 均 `200`。
- 完成未知映射统一 404：公共异常处理把 Spring MVC `NoResourceFoundException` /
  `NoHandlerFoundException` 映射为统一 `404 / http.404 / Resource not found`；admin/portal
  BFF route table 未命中也收敛为同一 message。公网真实验证覆盖 admin dashboard root、
  admin unknown route、portal unknown route。
- 完成 V3 内部接口静态审计：45 条 admin/portal BFF route target 均有后端 Controller；
  31 条 `/internal/...` 调用常量均有 Controller 映射；本轮未发现 V3 内部调用链接口缺口。
- 完成深层行为级 parity go/no-go live 验证：报告
  `docs/reports/126-production-candidate-go-no-go.md`，结果 `28 PASS / 2 WARN / 0 FAIL`。
  真实入口覆盖 gateway、PoW + 数学验证码 + admin login、portal home/metadata/explore、
  admin dashboard/config/data-sources/audit/DLQ/maintenance、真实源站 fetch-sample、
  真实任务 `019ec64f-6ddb-725b-a588-0111ace83fb5` 和 Rabbit drain。
  当前结论为生产候选受控 `Conditional GO`，无人值守正式生产 `NO-GO`，阻塞项为入口
  fallback、回滚演练和配置有效源可观测性。

## 下一步

优先修入口 fallback，随后做备份/回滚演练和配置有效源对齐；所有后续功能验证都直接连接
`ircs-prod`，不再使用 `ircs-dev`。
