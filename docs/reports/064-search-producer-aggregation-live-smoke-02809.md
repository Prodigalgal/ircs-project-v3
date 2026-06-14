# 064 search producer aggregation live smoke 02809 报告

状态：已完成

更新时间：2026-06-08

## 背景

`049` producer matrix 已确认 `ircs-aggregation-worker` 在 `aggregateBatch()` 成功绑定
raw/unified 后会发布 raw 与 unified 两路 search sync；`028-09` 已用 live smoke 覆盖
normalization 和 metadata result 两条 raw producer path。本切片继续验证 aggregation
scheduler 的真实 dev 队列副作用，避免只停留在单元测试和矩阵推断。

## 范围

包含：

```text
scripts/smoke/search-producer-aggregation-02809-smoke.ps1
tasks/done/028-09-search-producer-live-smoke-parity.md
docs/reports/064-search-producer-aggregation-live-smoke-02809.md
docs/migrations/064-search-producer-aggregation-live-smoke.md
```

不包含：

```text
services/**
shared/**
deploy/**
HTTPRoute
镜像 rollout
old unified dirty lifecycle
```

## 方案

- 在 PostgreSQL 中创建唯一 `codex02809agg` raw fixture，满足
  `aggregation_status='PENDING'`、`normalization_status='READY'`、
  `enrichment_status<>'PENDING'`。
- 等待 `ircs-aggregation-worker` scheduler claim fixture，将 raw 推进到 `BOUND`，
  创建或复用 unified video，并写入 `raw_video_unified_video`。
- 通过 `q.search.sync.raw` 与 `q.search.sync.unified` 验证 search-service 将
  `ircs_raw_video` 和 `ircs_unified_video` 两个 ES 文档刷新到目标投影。
- 检查目标队列和 DLQ 为空，确认无 HTTPRoute，最后清理 DB/ES fixture residue。

## 验收 MARK

```text
MARK 1/6 aggregation/search consumers are ready and HTTPRoute remains absent
MARK 2/6 aggregation fixture is seeded and pre-cleaned from DB/ES
MARK 3/6 aggregation scheduler binds raw video to unified video and marks BOUND
MARK 4/6 aggregation publishes raw/unified legacy UUID search sync and ES documents are refreshed
MARK 5/6 aggregation/search queues and DLQs are empty for the smoke window
MARK 6/6 smoke cleanup leaves no DB/ES fixture residue
```

## 验证结果

PowerShell 语法检查：

```text
$null = [scriptblock]::Create((Get-Content -LiteralPath 'scripts/smoke/search-producer-aggregation-02809-smoke.ps1' -Raw)); Write-Output 'syntax ok'
syntax ok
```

Live smoke：

```text
.\scripts\smoke\search-producer-aggregation-02809-smoke.ps1

MARK 1/6 aggregation/search consumers are ready and HTTPRoute remains absent
MARK 2/6 aggregation fixture is seeded and pre-cleaned from DB/ES
MARK 3/6 aggregation scheduler binds raw video to unified video and marks BOUND
MARK 4/6 aggregation publishes raw/unified legacy UUID search sync and ES documents are refreshed
MARK 5/6 aggregation/search queues and DLQs are empty for the smoke window
MARK 6/6 smoke cleanup leaves no DB/ES fixture residue
```

后置检查：

```text
DB residue:
  raw_videos source_hash like codex02809agg% = 0
  unified_videos title like Codex 02809 Aggregated Unified% = 0

RabbitMQ:
  q.search.sync.raw messages=0, consumers=1, ready=0, unacked=0
  q.search.sync.raw.dlq messages=0, consumers=0, ready=0, unacked=0
  q.search.sync.unified messages=0, consumers=1, ready=0, unacked=0
  q.search.sync.unified.dlq messages=0, consumers=0, ready=0, unacked=0

HTTPRoute:
  No resources found in ircs-dev namespace.

ResourceQuota:
  pods=28/30, services=24/26
  requests.cpu=880m/4, requests.memory=4480Mi/8Gi
  limits.cpu=7650m/8, limits.memory=14208Mi/16Gi

Local port-forward residue:
  none
```

## 结论

`ircs-aggregation-worker` scheduler 在 dev 环境真实完成：

```text
raw_videos READY/PENDING
  -> aggregation_status=BOUND
  -> unified_videos + raw_video_unified_video
  -> q.search.sync.raw + q.search.sync.unified
  -> ircs_raw_video + ircs_unified_video
```

本切片未修改服务代码、K8S manifest 或 HTTPRoute。`028-09` 剩余未闭环项缩小为
`old unified dirty lifecycle live smoke`。

## 风险

- aggregation scheduler 以固定延迟轮询，live smoke 需要等待窗口而不是直接投递消息。
- dev 集群若已有队列堆积，脚本必须结构化 `SKIPPED`，不能误报成功。
- fixture 写入共享 raw/unified 表，cleanup 必须覆盖 raw、unified、join、关系表和 ES
  两个 index。
- 本切片只证明 aggregation 新绑定链路，不覆盖 old unified dirty lifecycle。
