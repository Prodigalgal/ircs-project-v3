# 065 search producer old unified dirty lifecycle 02809 报告

状态：已完成

更新时间：2026-06-08

## 背景

`049` producer matrix 指出：`RawVideoAdminService.update()` 遇到 raw critical 字段变化时，
会把旧绑定 unified 标记为 `DIRTY` 并解绑 raw，但不会直接对 old unified 发布 search
`INDEX`。这是当前 V3 的 dirty lifecycle 语义：old unified 先成为待重算候选，再由
`UnifiedVideoAdminService.recalculateMetadata()` 或 aggregation/recalculate 闭环刷新。

`028-09` 已完成 normalization、metadata result 和 aggregation producer path 的 live
smoke。本切片验证最后一条 old unified dirty lifecycle，重点证明：

```text
raw critical update
  -> old unified DIRTY + raw unbound + raw INDEX
  -> old unified remains dirty candidate
  -> unified recalculate
  -> old unified SYNCED + unified INDEX
```

## 范围

包含：

```text
scripts/smoke/search-producer-old-unified-dirty-02809-smoke.ps1
tasks/done/028-09-search-producer-live-smoke-parity.md
docs/reports/065-search-producer-old-unified-dirty-lifecycle-02809.md
docs/migrations/065-search-producer-old-unified-dirty-lifecycle.md
```

不包含：

```text
services/**
shared/**
deploy/**
HTTPRoute
镜像 rollout
改变 raw update 后 old unified 是否立即刷新 ES 的语义
```

## 方案

- 在 DB 中 seed 1 个 raw、1 个 old unified 和 1 条 `raw_video_unified_video` 绑定。
- 通过 `POST /api/v1/unified-videos/batch/sync-search` 预热 old unified ES 文档，确认
  `sourceCount=1`。
- 通过 `PUT /api/v1/raw-videos/{id}` 修改 raw title，触发 critical update。
- 验证 DB：old unified `metadata_status='DIRTY'`、raw/unified 绑定删除、
  raw `aggregation_status='PENDING'`。
- 验证 raw ES 文档已刷新到新 title，同时 old unified 仍是 dirty candidate。
- 通过 `POST /api/v1/unified-videos/{id}/recalculate` 关闭 dirty lifecycle。
- 验证 old unified `metadata_status='SYNCED'`，ES old unified 文档刷新为 `sourceCount=0`。

## 验收 MARK

```text
MARK 1/6 old unified fixture is seeded and initial ES unified document has sourceCount=1
MARK 2/6 raw critical update marks old unified DIRTY and unbinds raw
MARK 3/6 raw critical update publishes raw search INDEX and refreshes ircs_raw_video
MARK 4/6 old unified remains a dirty lifecycle candidate until recalculate closes it
MARK 5/6 unified recalculate publishes q.search.sync.unified and refreshes ircs_unified_video
MARK 6/6 search queues/DLQs are clean and cleanup leaves no DB/ES fixture residue
```

## 验证结果

PowerShell 语法检查：

```text
$null = [scriptblock]::Create((Get-Content -LiteralPath 'scripts/smoke/search-producer-old-unified-dirty-02809-smoke.ps1' -Raw)); Write-Output 'syntax ok'
syntax ok
```

Live smoke：

```text
.\scripts\smoke\search-producer-old-unified-dirty-02809-smoke.ps1

MARK 1/6 old unified fixture is seeded and initial ES unified document has sourceCount=1
MARK 2/6 raw critical update marks old unified DIRTY and unbinds raw
MARK 3/6 raw critical update publishes raw search INDEX and refreshes ircs_raw_video
MARK 4/6 old unified remains a dirty lifecycle candidate until recalculate closes it
MARK 5/6 unified recalculate publishes q.search.sync.unified and refreshes ircs_unified_video
MARK 6/6 search queues/DLQs are clean and cleanup leaves no DB/ES fixture residue
```

后置检查：

```text
DB residue:
  raw_videos source_hash like codex02809dirty% = 0
  unified_videos title like Codex 02809 Dirty Unified% = 0

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

old unified dirty lifecycle 在 dev 环境完成行为验证：

```text
PUT /api/v1/raw-videos/{id}
  -> old unified metadata_status=DIRTY
  -> raw_video_unified_video 解绑
  -> raw_videos aggregation_status=PENDING
  -> q.search.sync.raw 刷新 ircs_raw_video
  -> POST /api/v1/unified-videos/{id}/recalculate
  -> old unified metadata_status=SYNCED
  -> q.search.sync.unified 刷新 ircs_unified_video sourceCount=0
```

本切片未修改服务代码、K8S manifest 或 HTTPRoute。`028-09` normalization、
metadata-result、aggregation 与 old dirty lifecycle 均已完成，可归档。

## 风险

- 本切片有意验证当前 dirty lifecycle，不把“raw update 立即刷新 old unified ES”作为目标。
- raw update 后 raw 会变为 `aggregation_status='PENDING'`；fixture 必须使用唯一外部 ID，
  cleanup 需要覆盖可能被 scheduler 后续重新聚合出的 unified。
- 需要通过 content-service HTTP endpoint 触发业务逻辑，不能只用 SQL 伪造状态。
