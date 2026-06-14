# 028-22 production candidate closeout parent

状态：进行中

父目标：

- `docs/requirements/028-22-production-candidate-closeout-goal.md`

上游来源：

- `tasks/index.md`
- `docs/reports/093-v1-v3-parity-progress-ledger.md`
- `docs/reports/106-02819-production-like-smoke-orchestrator-prep.md`
- `docs/reports/117-02810-02812-runner-audit-closeout.md`

## 背景

当前 V3 已进入最后收敛阶段。HTTP route、BFF、queue、主体 worker 和门户入口不再是主缺口；
剩余风险集中在 destructive / high-write V1 maintenance 行为、深层 parity 旧口径、真实外部依赖
统一测试和生产候选治理。

## 当前整体口径

```text
V1/V3 parity 总进度: 约 96%
开发 MVP 可用度:     约 97%
生产就绪度:          约 87%
route/BFF/queue 强制缺口: 0
```

## 第一阶段开发切片

### 028-22-A unified-recalculate owner runner

目标：

- 将 `unified-recalculate` 从 dev refusal 升级为 capped、owner-service、可审计的维护 runner。
- ops-service 不直接写 aggregation 表，只调用 aggregation-worker internal maintenance API。
- aggregation-worker 负责 dirty unified 候选选择、行级跳锁和 `recalculateUnified` 数据副作用。
- internal API 接入 service identity / scopes。

验收：

```text
MARK A1 aggregation-worker exposes authenticated capped dirty unified recalculate API
MARK A2 ops-service calls aggregation owner with INTERNAL_SERVICE policy and correlationId
MARK A3 MaintenanceRunnerService marks unified-recalculate as devAllowed, supportsDryRun and audited
MARK A4 focused aggregation-worker / ops-service tests pass
```

### 后续切片

- 028-22-F UTB unified test execution and external dependency windows：no-external live smoke
  已通过，provider-gated window 仍待显式打开。
- 028-22-G production candidate report。

### 028-22-C sanitize owner runner

目标：

- 将 `sanitize` 从 dev refusal 升级为 normalization-worker owner-service、可审计的维护 runner。
- 保持 V1 真实副作用：只把全部 raw video normalization status 重置为 `PENDING` 并更新 `updated_at`。
- 不额外重置 retry count、next retry time、relation、queue 或 search/aggregation 状态。
- internal API 接入 service identity / scopes。

验收：

```text
MARK C1 V1 sanitize reset-only behavior documented
MARK C2 normalization-worker exposes authenticated reset-normalization API
MARK C3 ops-service calls normalization owner with INTERNAL_SERVICE policy and correlationId
MARK C4 LLM cleaning existing circuit/error semantics preserved
MARK C5 focused normalization-worker / ops-service tests pass
```

### 028-22-D trend-sync owner runner

目标：

- 将 `trend-sync` 从 dev refusal 升级为 scraper/content owner-service、可审计的维护 runner。
- scraper-service 负责 Douban/TMDB trend provider 抓取与 content owner 调用。
- content-service 负责 external id 更新时间、title/year similarity 命中、ghost unified 创建和
  search sync outbox。
- ops-service 只调用 scraper owner API，不直接写内容库。

验收：

```text
MARK D1 V1 trend-sync provider/update/title-match/ghost-create behavior documented
MARK D2 content-service exposes authenticated trend apply API
MARK D3 scraper-service exposes authenticated trend sync API and provider aggregation
MARK D4 ops-service calls scraper owner with INTERNAL_SERVICE policy and correlationId
MARK D5 focused content-service / scraper-service / ops-service tests pass
```

### 028-22-E background discovery / TMDB credential lease

目标：

- 补齐 V1 `trend-sync` 新建 ghost 后的 staggered background discovery 副作用。
- 由 task-service owner API 创建/复用并排队 discovery collection task。
- scraper-service 在 content apply 后把 `discoveryKeywords` 调给 task-service。
- TMDB trend provider 通过 credential-service lease 获取 `api_key`，env key 仅作为兜底。

验收：

```text
MARK E1 V1 staggered background discovery behavior mapped to task-service owner API
MARK E2 task-service creates/reuses deterministic discovery collection tasks and queues them
MARK E3 scraper-service trend-sync schedules discoveryKeywords after content apply
MARK E4 TMDB trend provider leases api_key from credential-service before env fallback
MARK E5 focused and affected task/scraper/ops tests pass
```

### 028-22-F trend discovery live smoke and orchestrator

目标：

- 将 `028-22-E` 接入可重复 live smoke 脚本和 `028-19` orchestrator。
- 默认不触发真实 Douban/TMDB provider 外呼，先验证 task discovery contract、rollout 和 residue cleanup。
- 推送并 rollout task-service / scraper-service / ops-service ARM64 dev 镜像。
- 修复 dev manifest 与 live probe handler 漂移，恢复 `kubectl apply` 可用。

验收：

```text
MARK F1 trend discovery live smoke script exists with no-external default and provider explicit gate
MARK F2 028-19 orchestrator exposes TREND-02822-F batch
MARK F3 task/scraper/ops ARM64 images are rolled out and Ready in ircs-dev
MARK F4 dev manifests apply cleanly after probe alignment
MARK F5 no-external task discovery contract and DB residue smoke passes
MARK F6 provider-gated Douban/TMDB live smoke remains explicit external window
```

## 测试编排

- 开发切片当场执行 focused tests。
- 不先执行全量 live smoke；统一测试按 `028-19` orchestrator 批次执行。
- 真实 provider / SMTP / R2 / LLM 需要显式 allow flag。

## 当前进展

2026-06-12：创建 `028-22` production candidate closeout goal，并启动 `028-22-A`。

2026-06-12：完成 `028-22-A` unified-recalculate owner runner。aggregation-worker 增加
authenticated capped dirty unified recalculate API，ops-service 增加 aggregation owner client，
`MaintenanceRunnerService` 将 `unified-recalculate` 切为 HIGH risk / devAllowed /
supportsDryRun / audited runner。focused tests 通过；live rollout/smoke 后置。

2026-06-13：完成 `028-22-B` aggregation-reset owner runner。V1 reset 顺序固定为
aggregation prepare -> search hard reset unified index -> aggregation raw pending；V3 已按
aggregation-worker、search-service、ops-service 多 owner 边界落地，并通过受影响服务完整测试。
live destructive smoke 后置到统一测试窗口。

2026-06-13：完成 `028-22-C` sanitize owner runner。V1 行为确认只更新
`raw_videos.normalization_status = PENDING` 与 `updated_at`；V3 已落到 normalization-worker
owner API，ops-service 通过 owner client、service identity 和 worker-job audit 编排执行，
并通过 normalization-worker / ops-service focused 与 affected tests。live high-write smoke
后置到统一测试窗口。

2026-06-13：完成 `028-22-D` trend-sync owner runner。V1 provider -> external id update ->
title/year match -> ghost unified 创建主链路已按 scraper/content/ops owner 边界落地，并通过
content-service / scraper-service / ops-service focused 与 affected tests。真实 Douban/TMDB
外呼、K8S rollout 和 live DB smoke 后置；V1 staggered background discovery 与 TMDB
credential-service lease 已由下一条 `028-22-E` 完成。

2026-06-13：完成 `028-22-E` background discovery / TMDB credential lease。V3 已新增
task-service trend discovery owner API，scraper-service trend-sync 会把 content apply 返回的
`discoveryKeywords` 排队成 collection task；TMDB trend provider 已改为 credential-service
lease 优先、env key 兜底。focused task/scraper tests 与 task-service / scraper-service /
ops-service affected tests 通过。真实 provider 外呼、K8S rollout 和 live DB smoke 后置到
统一测试窗口。

2026-06-13：完成 `028-22-F` no-external live smoke first closure。新增
`scripts/smoke/trend-sync-discovery-02822f-smoke.ps1`，`028-19` orchestrator 增加
`TREND-02822-F` 批次；task-service / scraper-service / ops-service 已推送并 rollout ARM64
dev 镜像，运行 digest 已记录；task discovery internal contract、DB residue cleanup 和
dev manifest apply 均通过。

2026-06-14：完成 `028-22-F` provider-gated live window。scraper-service 从 Pod 内真实触发
Douban/TMDB trend-sync，抓取 20 条 provider item，content-service 创建 20 个 trend ghost，
task-service 创建并排队 3 个 discovery task；3 个 discovery task 均 `COMPLETED`，search
outbox relay 已在 dev 开启并清空 DB outbox，Rabbit search 队列最终 drain。`028-22-F`
已移动到 `tasks/done/028-22-f-unified-live-smoke-execution.md`。
