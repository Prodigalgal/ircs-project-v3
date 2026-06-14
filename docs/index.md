# IRCS V3 文档入口

状态：进行中

更新时间：2026-06-13

归档原文：

- `docs/reports/archive/2026-06-13-doc-compaction/docs-index-full-log.md`

## 当前主线

V3 已从 V1 单体拆分为多服务，并部署在 `ircs-dev` K8S namespace 中持续验证。
HTTP route parity 历史口径为 `missing=0`；当前完成标准已经升级为 V1 已验证功能的行为级
1:1 迁移和生产候选真实验证。

后续验证规则：

- 只允许连接 `ircs-dev` K8S 集群做真实数据在线测试。
- 使用真实配置、真实凭据、真实数据库数据和真实外部链路。
- 允许污染开发数据库；生产会重新部署独立 namespace。
- 本地 compile/build/Jib 可作为准备动作，但本地 smoke、mock/fake payload、造数任务不能作为完成证据。

## 当前进度

| 领域 | 进度 | 状态 |
| --- | ---: | --- |
| HTTP route parity | 100% | V1 对外路由缺口保持 missing=0 |
| portal/admin/BFF/gateway | 85% | 已部署，可作为持续验证入口，仍需统一真实业务回归 |
| V1 行为级迁移 | 80%-85% | 主链路完成，剩外部 provider、源站网络、异步失败和组合场景 |
| runtime/platform/security/cache/lock/audit | 75%-80% | 实现多已完成，真实矩阵证据待补 |
| 029 Rabbit MT/PT/DT | 80% | 成功链路和 page 失败已验证，剩 detail 失败与网络策略 |
| 生产候选准入 | 70% | 需统一真实回归、生产 namespace、备份/回滚和安全复核 |

## 关键入口

- 生产候选闭环：`docs/requirements/030-production-candidate-k8s-real-validation-closeout.md`
- 当前任务总入口：`tasks/index.md`
- 030 执行账本：`tasks/in-progress/030-production-candidate-k8s-real-validation-closeout.md`
- 029 Rabbit 任务系统：`docs/requirements/029-rabbit-redis-task-system-redesign.md`
- 029 ADR：`docs/decisions/008-rabbit-redis-mt-pt-dt-task-system.md`
- 029 task：`tasks/in-progress/029-rabbit-redis-task-system-redesign.md`
- post-parity runtime：`docs/decisions/005-post-parity-runtime-platform-decisions.md`
- source network/security：`docs/decisions/006-pow-and-source-transport-upgrade.md`

## 当前剩余主任务

1. 029 Rabbit MT/PT/DT：补真实 `detail.done(success=false)`、trend discovery、DNS/DoH/DoT/IP
   policy/HTTP_1_0 策略矩阵。
2. 028-21 security/source network：完成 admin 数据源策略选择、真实源站测试和结果展示闭环。
3. 028-20 runtime/platform：补 Redis lock contention、worker/job metrics、cache L2 hit/evict 的
   K8S 真实证据。
4. 028-12 audit：补 service handler 与 worker/job audit 统一真实在线证据。
5. 028-10 runner/governor：补 dry-run/execute gate/audit 的真实组合证据。
6. 文档治理：继续压缩超过 500 行的流水账型文档，主入口只保留状态、边界、风险和归档链接。
7. 生产候选报告：汇总 go/no-go、生产 namespace 重部署步骤、备份、回滚和风险。

## 文档治理

2026-06-13 第一批已归档压缩：

- `028-12-service-audit-rollout-and-worker-job-audit`
- `028-16-C2 SystemConfig cache/event parity`
- `028-16-C3 shared outbound HTTP client parity`
- `028-20 post-parity runtime platform breakdown`
- 旧 `docs/index.md`

完整历史保留在 `docs/reports/archive/2026-06-13-doc-compaction/`。

