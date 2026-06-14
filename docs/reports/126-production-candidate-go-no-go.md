# 126 production candidate go/no-go

日期：2026-06-14

## 结论

当前结论：`Conditional GO`。

V3 可以继续作为 `ircs-prod` 生产候选环境承接受控真实业务回归、管理员录入、源站验证和小批量数据填充。
但在未完成下列 P0 风险收口前，不建议进入无人值守正式生产：

1. 公网入口需要显式处理 `GET /actuator/*` 和 `GET /internal/*`，当前返回 admin SPA HTML `200`，未泄露配置，但安全语义不够干净。
2. 需要完成一次可执行的备份/回滚演练；旧 `ircs-system` 已退役，不能再作为实时 fallback。
3. `config-service` 的 `security.jwt.secret` 管理页有效来源仍显示 `DB`，而 `identity-service` 运行时 Secret 已由环境注入。运行不受影响，但配置可观测语义需要对齐。
4. 当前是干净库起步，portal `explore` 为 `0` 条；正式面向用户前需要完成基础数据填充或接受空库上线。

## 本轮验证范围

验证只连接真实 `ircs-prod` K8S 集群和公网入口，未使用本地 smoke、mock/fake payload 或造数脚本。

- 公网入口：`https://huawai.sophia.fr.eu.org`、`https://ircs.sophia.fr.eu.org`
- K8S namespace：`ircs-prod`
- GitOps：`ircs-prod-core`、`ircs-prod-edge`
- 外部链路：真实源站 `iKun资源`，真实 DNS over TLS 策略
- 真实写入：复用/验证 `go-nogo-parity-*` 一页采集任务

## 结果摘要

```text
PASS: 28
WARN: 2
FAIL: 0
taskId: 019ec64f-6ddb-725b-a588-0111ace83fb5
GitOps revision: c86deb5
```

两个 `WARN` 均为入口路由语义问题，不是敏感数据泄露：

- `GET https://ircs.sophia.fr.eu.org/actuator/env` 返回 admin SPA HTML `200`。
- `GET https://ircs.sophia.fr.eu.org/internal/v1/search/sync-tasks/batch` 返回 admin SPA HTML `200`。
- 同一路径 `POST /internal/v1/search/sync-tasks/batch` 返回 `405`，未命中内部业务 API。

## 行为级 parity 场景

| 场景 | 结果 | 证据 |
| --- | --- | --- |
| portal gateway health | PASS | `https://huawai.sophia.fr.eu.org/gateway-health` -> `204` |
| admin gateway health | PASS | `https://ircs.sophia.fr.eu.org/gateway-health` -> `204` |
| portal/admin HTML | PASS | portal HTML `200`，admin HTML `200` |
| admin PoW + 数学验证码 + 真实登录 | PASS | `GET /api/v1/auth/pow?scope=admin.login` 后登录 `200`，JWT 三段结构 |
| portal public APIs | PASS | `/api/portal/home`、`/metadata`、`/explore` 均 `200` |
| portal unknown mapping | PASS | `404 / http.404 / Resource not found` |
| admin dashboard/config/catalog | PASS | dashboard statistics/metrics `200`；configs `20/133`；data-sources `25/25` |
| admin audit/ops | PASS | request audit、worker-job audit、Rabbit DLQ、maintenance runners/scheduler/gate check 均 `200` |
| sensitive config redaction | PASS | `security.jwt.secret` value/effectiveValue 均不返回明文 |
| admin unknown mapping | PASS | `404 / http.404 / Resource not found` |
| source network strategy | PASS | `iKun资源` 使用 `APACHE_HC5 / HTTP_1_1 / IPV4_PREFERRED / DOT one.one.one.one:853` fetch-sample `200` |
| task MT/PT/DT runtime | PASS | `019ec64f-6ddb-725b-a588-0111ace83fb5` 终态 `COMPLETED`，`20/20` 成功 |
| Rabbit drain | PASS | `93` 个队列，`nonEmpty=0` |
| ArgoCD core/edge | PASS | `ircs-prod-core` 与 `ircs-prod-edge` 均 `Synced/Healthy` |
| Deployment readiness | PASS | `24/24` Deployment Ready |
| JWT runtime Secret | PASS | `identity-service` 运行时 `APP_IDENTITY_JWT_SECRET` 存在，长度 `64`，未打印值 |

任务运行态证据：

```text
taskId=019ec64f-6ddb-725b-a588-0111ace83fb5
status=COMPLETED
processed=20
success=20
failed=0
totalFound=65735
runtimeStatus=COMPLETED
runtimePages=1
runtimeDetailSucceeded=20
runtimeDetailFailed=0
```

## Go 条件

允许继续做：

- 在 `ircs-prod` 里进行真实管理员录入、源站配置、fetch-sample 和一页/小批量采集。
- 使用 portal/admin 作为持续验证入口。
- 扩展长尾源站矩阵、缓存命中/失效、多副本 contention、runner/governor 组合验证。
- 基于 GitOps revision `c86deb5` 继续增量修复。

## No-Go 边界

以下场景仍不建议放行：

- 无人值守公网正式生产。
- 面向普通用户承诺完整内容可浏览。
- 未备份/未演练回滚的批量源站采集或大规模数据导入。
- 未封堵或明确收敛 `GET /actuator/*`、`GET /internal/*` fallback 之前的安全准入签字。

## 风险和处置

| 优先级 | 风险 | 当前事实 | 建议处置 |
| --- | --- | --- | --- |
| P0 | 入口 fallback 语义不干净 | `GET /actuator/env`、`GET /internal/...` 返回 admin SPA HTML `200` | 在 frontend gateway 或 HTTPRoute 层把 `/actuator/*`、`/internal/*` 显式返回 `404/403` |
| P0 | 回滚未演练 | 旧 `ircs-system` 已删除，旧 `ircs-app` 已退役 | 做一次 GitOps revision rollback + DB restore drill，并记录 RTO/RPO |
| P1 | 配置有效源显示不一致 | identity runtime JWT Secret 已注入，config UI 对 `security.jwt.secret` 仍显示 `DB` | 让 config-service 能感知同一 Secret 或把跨服务 runtime source 标为 owner-specific |
| P1 | portal 内容为空 | 干净库下 `explore.totalElements=0` | 生产前跑受控采集或接受空库上线 |
| P1 | 长尾源站未穷尽 | 本轮只覆盖代表性源站和核心网络策略 | 继续补 429、老 TLS/HTTP、DNS 污染、连接超时和 IPv6 受限场景 |
| P1 | 多副本 contention 未复测 | 当前多数业务 Deployment 为 1 副本 | 资源允许后扩 task/scraper/search 做 Redis lock/Rabbit claim contention 验证 |

## 回滚边界

当前没有旧 namespace 热备 fallback。可用回滚路径是：

1. 回滚 GitOps 仓库到上一稳定 revision。
2. ArgoCD 同步 `ircs-prod-core` 和 `ircs-prod-edge`。
3. 如涉及数据变更，恢复 PostgreSQL 备份；必要时清理 Rabbit 未完成消息和 Valkey 热态。
4. 验证 gateway health、admin login、portal home、Rabbit drain、核心 Pod Ready。

正式生产前至少需要演练一次：

```text
backup -> apply controlled change -> verify break/fix -> rollback GitOps revision -> restore DB snapshot -> public gateway live verification
```

## 下一步

1. 先修入口层 `/actuator/*`、`/internal/*` 显式阻断或 404。
2. 写并执行 `ircs-prod` 备份/回滚演练 runbook。
3. 对齐 config-service 的 runtime Secret 有效源展示。
4. 继续补长尾源站网络矩阵、cache hit/evict、多副本 contention 和 runner/governor 组合证据。
5. 数据填充后重新跑 portal detail/search/explore 的非空业务回归。
