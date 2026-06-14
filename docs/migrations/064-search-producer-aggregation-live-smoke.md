# 064 search producer aggregation live smoke 迁移记录

状态：已完成

更新时间：2026-06-08

## 上游来源

- `docs/reports/049-search-producer-matrix-parity-analysis.md`
- `docs/reports/058-search-producer-live-smoke-02809-metadata-result.md`
- `tasks/done/028-09-search-producer-live-smoke-parity.md`
- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`

## 迁移目标

验证 V3 `ircs-aggregation-worker` 从 V1 迁移出的聚合副作用是否在 dev 环境真实闭环：

```text
raw_videos READY/PENDING
  -> aggregation scheduler
  -> raw_videos aggregation_status=BOUND
  -> unified_videos + raw_video_unified_video
  -> q.search.sync.raw + q.search.sync.unified
  -> ircs_raw_video + ircs_unified_video
```

## 边界

本迁移记录只覆盖 aggregation scheduler 创建/绑定 unified 后的 raw/unified search sync
live smoke。不修改服务代码、不新增 HTTPRoute、不 rollout、不覆盖 old unified dirty
lifecycle。

## 验收

完成后必须记录：

- PowerShell scriptblock parse check。
- live smoke `MARK 1/6` 到 `MARK 6/6`。
- raw/unified DB 与 ES residue 为 0。
- `q.search.sync.raw`、`q.search.sync.unified` 及 DLQ 为空。
- `kubectl -n ircs-dev get httproute` 无资源。
- `ircs-dev` ResourceQuota 后置状态。

## 完成记录

PowerShell 语法检查通过：

```text
$null = [scriptblock]::Create((Get-Content -LiteralPath 'scripts/smoke/search-producer-aggregation-02809-smoke.ps1' -Raw)); Write-Output 'syntax ok'
syntax ok
```

Live smoke 已完成：

```text
.\scripts\smoke\search-producer-aggregation-02809-smoke.ps1

MARK 1/6 aggregation/search consumers are ready and HTTPRoute remains absent
MARK 2/6 aggregation fixture is seeded and pre-cleaned from DB/ES
MARK 3/6 aggregation scheduler binds raw video to unified video and marks BOUND
MARK 4/6 aggregation publishes raw/unified legacy UUID search sync and ES documents are refreshed
MARK 5/6 aggregation/search queues and DLQs are empty for the smoke window
MARK 6/6 smoke cleanup leaves no DB/ES fixture residue
```

后置环境：

```text
DB residue=0|0
q.search.sync.raw=0 messages, 1 consumer
q.search.sync.raw.dlq=0 messages
q.search.sync.unified=0 messages, 1 consumer
q.search.sync.unified.dlq=0 messages
HTTPRoute=none
ResourceQuota pods=28/30, services=24/26
port-forward residue=none
```

`028-09` 当前只剩 `old unified dirty lifecycle live smoke` 未闭环。
