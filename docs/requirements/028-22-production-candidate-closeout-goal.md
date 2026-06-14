# 028-22 production candidate closeout goal

状态：进行中

日期：2026-06-12

更新：2026-06-13

## 目标

把 V1 -> V3 行为级迁移的最后阶段收敛为一个可持续执行目标：剩余开发、统一测试、
真实外部依赖验证、K8S 生产候选检查和上线前风险清账在同一张任务图里推进。

当前 route / BFF / queue 强制缺口为 0，后续不再以“接口存在”为完成标准，而以业务行为、
数据副作用、运维可控性、审计证据和生产候选稳定性为完成标准。

## 开发范围

- destructive / high-write V1 maintenance 行为：
  - `unified-recalculate`
  - `aggregation-reset`
  - `sanitize`
  - `trend-sync`
- 深层 parity 清账：
  - normalization / title / relation / category 旧口径复核
  - aggregation real R2 cover promote、多上下文 merge、持久 search outbox/stats
  - Tika/scraper 真实资源站 charset 与 transport 策略
  - LLM cleaning 真实单条与成本保护
  - C1 readiness / initializer 旧口径清理
  - C3 shared outbound 剩余调用方与 K8S override/open-circuit matrix
- 生产化治理：
  - Redis lock contention 观测
  - 多级缓存后续 owner 扩展
  - remaining library follow-up
  - 镜像 freshness、资源预算、回滚手册、长期告警

## 测试范围

- 聚焦测试：每个开发切片必须有最小本地 test / compile / bootJar 证据。
- 统一测试：按 `028-19` orchestrator 执行 UTB 批次。
- 真实外部依赖：provider / SMTP / R2 / LLM 必须显式 allow flag，记录成本、残留和清理证据。
- K8S 生产候选：保持 `ircs-dev` 低资源、无流量冲突、无 namespace 污染。

## 当前执行任务图

1. 开发闭环：`028-22-A/B/C/D/E` 已完成 owner runner、background discovery 和 TMDB
   credential lease 的实现与 focused/affected tests。
2. 安全 live 闭环：`028-22-F` 已建立 no-external smoke 和 `TREND-02822-F`
   orchestrator 批次，task/scraper/ops 已 rollout，内部 contract 与 DB residue cleanup 通过。
3. 外部依赖窗口：Douban/TMDB provider live、SMTP/R2/LLM 仍必须显式 allow flag。
4. 生产候选报告：等待统一 live 窗口完成后汇总 rollback、quota、health、known risks。

## 非目标

- 不在 dev 环境无门禁执行无界 destructive maintenance。
- 不恢复手动应用层 mTLS；内部安全继续走 app-layer service identity / scopes，基础设施 mTLS 留给未来 service mesh。
- 不做物理库拆分或完整压测。

## 优先级

1. 先补 owner-service 边界清晰、可 dry-run/capped 的 maintenance runner。
2. 再清理旧任务口径，避免上下文压缩后误判已完成项。
3. 再执行统一测试批次与真实外部依赖窗口。
4. 最后做生产候选准入报告。

## 验收

```text
MARK 1/8 goal/task spec created and indexed
MARK 2/8 unified-recalculate owner runner implemented with capped dev-safe path
MARK 3/8 remaining destructive runners split into owner-service task specs
MARK 4/8 stale in-progress/backlog wording reconciled
MARK 5/8 focused tests pass for each implementation slice
MARK 6/8 UTB and 028-22 no-external smoke completed or explicitly deferred with reason
MARK 7/8 external provider/SMTP/R2/LLM windows completed or explicitly deferred with risk owner
MARK 8/8 production candidate report written with rollback, quota, health and known risks
```
