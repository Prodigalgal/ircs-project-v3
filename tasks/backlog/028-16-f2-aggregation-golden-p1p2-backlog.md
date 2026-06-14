# 028-16-F2 aggregation golden P1/P2 backlog

状态：backlog，部分 local closure 已完成

更新时间：2026-06-10

## 背景

本任务承接 aggregation pipeline 的 V1/V3 行为级 parity。此前本地实现已补结构化 search sync、
malformed reject、pipeline failure raw PENDING / unified DIRTY、recalculate 成功后才 SYNCED 等关键链路；
真实 R2 cover promote、multi-context victim merge、持久 search outbox/stats 和 production-like smoke 仍后置。

## 范围

- 聚合 pipeline 的 raw/unified 状态副作用。
- aggregation 失败、重试、partial success 与错误语义。
- search sync producer 与 residue 检查。
- cover promote / storage side effect 与 R2 后置验证。
- multi context merge / victim merge 真实数据一致性。

## 非目标

- 不在 backlog 文档中执行 live smoke。
- 不扩大到完整压测、物理库拆分或生产高可用。

## 已完成切片

- 结构化 search sync payload。
- malformed relation reject。
- pipeline failure raw PENDING / unified DIRTY lifecycle。
- recalculate 成功后才 SYNCED 的 guard。
- aggregation pipeline runtime dev K8S live smoke 已由 `028-16-b15` 覆盖一批。
- search outbox stats 的只读 ops 能力拆到 `tasks/in-progress/028-16-b16-search-outbox-stats-parity.md`。

## 剩余验收

- 真实 R2 cover promote smoke。
- multi-context victim merge production-like smoke。
- search outbox stats endpoint 的 dev DB 真实查询与 residue 清理。
- aggregation 与 search/ES/Rabbit residue 的统一后置检查。

## 统一测试入口

`UTB-02816-P1P2-01` 和 `UTB-02816-P1P2-03` 负责承接本 backlog 的组合测试证据。

## 2026-06-12 UTB-01 evidence

`UTB-02816-P1P2-01` 已重跑并关闭 aggregation B15 runtime live smoke：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\production-like-smoke-orchestrator-02819.ps1 -Batch UTB-02816-P1P2-01 -Execute -ContinueOnFailure -OutputJsonPath build\tmp\utb-02816-p1p2-01-rerun-20260612085514.json
```

结果：

```text
aggregation pipeline runtime B15: PASSED
B15 MARK 1/8 consumers ready and no HTTPRoute
B15 MARK 2/8 fixture seeded and pre-cleaned
B15 MARK 3/8 scheduler binds fixture raws to one unified
B15 MARK 4/8 scalar/category outputs match V1 handler rules
B15 MARK 5/8 metadata relations match V1 union rules
B15 MARK 6/8 cover stage selects managed cover and storage command is safely consumed/gated
B15 MARK 7/8 raw/unified ES documents refreshed and queues/DLQs empty
B15 MARK 8/8 DB/ES/Rabbit/HTTPRoute/port-forward cleanup and quota unchanged
```

仍未关闭：真实 R2 cover promote、multi-context victim merge、search outbox stats dev DB 查询。
