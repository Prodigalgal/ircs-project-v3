# 065 search producer old unified dirty lifecycle 迁移记录

状态：已完成

更新时间：2026-06-08

## 上游来源

- `docs/reports/049-search-producer-matrix-parity-analysis.md`
- `docs/reports/064-search-producer-aggregation-live-smoke-02809.md`
- `tasks/done/028-09-search-producer-live-smoke-parity.md`
- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`

## 迁移目标

验证 V3 对 V1 raw critical update 后 old unified dirty lifecycle 的行为复现：

```text
PUT /api/v1/raw-videos/{id}
  -> mark old unified DIRTY
  -> unbind raw from old unified
  -> mark raw aggregation PENDING
  -> publish raw INDEX
  -> POST /api/v1/unified-videos/{id}/recalculate
  -> mark old unified SYNCED
  -> publish unified INDEX
```

## 边界

本迁移记录只覆盖 old unified dirty lifecycle 的 live smoke。不修改服务代码、不新增
HTTPRoute、不 rollout，不改变当前“raw critical update 不直接刷新 old unified”的语义。

## 验收

完成后必须记录：

- PowerShell scriptblock parse check。
- live smoke `MARK 1/6` 到 `MARK 6/6`。
- old unified `DIRTY -> SYNCED` 状态转换。
- raw/unified ES 文档刷新证据。
- `q.search.sync.raw`、`q.search.sync.unified` 及 DLQ 为空。
- `kubectl -n ircs-dev get httproute` 无资源。
- DB/ES fixture residue 为 0。
- `ircs-dev` ResourceQuota 后置状态。

## 完成记录

PowerShell 语法检查通过：

```text
$null = [scriptblock]::Create((Get-Content -LiteralPath 'scripts/smoke/search-producer-old-unified-dirty-02809-smoke.ps1' -Raw)); Write-Output 'syntax ok'
syntax ok
```

Live smoke 已完成：

```text
.\scripts\smoke\search-producer-old-unified-dirty-02809-smoke.ps1

MARK 1/6 old unified fixture is seeded and initial ES unified document has sourceCount=1
MARK 2/6 raw critical update marks old unified DIRTY and unbinds raw
MARK 3/6 raw critical update publishes raw search INDEX and refreshes ircs_raw_video
MARK 4/6 old unified remains a dirty lifecycle candidate until recalculate closes it
MARK 5/6 unified recalculate publishes q.search.sync.unified and refreshes ircs_unified_video
MARK 6/6 search queues/DLQs are clean and cleanup leaves no DB/ES fixture residue
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

`028-09` search producer live smoke parity 已闭环。
