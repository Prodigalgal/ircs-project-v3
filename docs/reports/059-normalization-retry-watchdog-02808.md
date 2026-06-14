# 059 normalization retry watchdog 02808 分析

状态：已完成

更新时间：2026-06-07

关联任务：

- `tasks/done/028-08-normalization-deep-mapping-parity.md`

## 背景

当前 V3 normalization worker 已经能在 raw metadata 缺失或解析失败时写入 `FAILED`、
`PERMANENT_FAILURE`、retry count 和 next retry time。但如果 `FAILED` 到期后没有新的队列消息，
或者 worker 重启期间 `PENDING` 消息丢失，记录会停在中间状态，缺少 V1 风格 watchdog/recovery。

## 本轮目标

```text
FAILED + next_normalization_retry_time <= now()
  -> publish q.normalize.video

PENDING + updated_at older than stale threshold
  -> publish q.normalize.video

READY / PERMANENT_FAILURE / future FAILED
  -> skip
```

## 非目标

- 不做 live queue smoke。
- 不新增 K8S 资源或 HTTPRoute。
- 不改 backoff 公式。
- 不直接修改 raw video 状态，只重投 normalization queue。

## MARK 设计

```text
MARK 1/4 normalization watchdog scans due FAILED records and republishes q.normalize.video
MARK 2/4 normalization watchdog scans stale PENDING records for startup recovery
MARK 3/4 normalization watchdog skips READY/PERMANENT_FAILURE and future retry records
MARK 4/4 normalization watchdog remains low-resource and configurable
```

## 测试边界

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
```

## 完成结果

2026-06-07 已完成 normalization retry watchdog 第一刀：

```text
MARK 1/4 normalization watchdog scans due FAILED records and republishes q.normalize.video
MARK 2/4 normalization watchdog scans stale PENDING records for startup recovery
MARK 3/4 normalization watchdog skips READY/PERMANENT_FAILURE and future retry records
MARK 4/4 normalization watchdog remains low-resource and configurable
```

实现摘要：

- 新增 `NormalizeVideoPublisher`，按 `QueueTopic.NORMALIZE_VIDEO` 发布 raw video id。
- 新增 `NormalizationRetryWatchdog`，按小批量扫描 due `FAILED` 和 stale `PENDING` 并重投队列。
- `RawVideoNormalizationRepository` 增加 `findDueNormalizationRecoveryIds(...)`。
- normalization worker 启用 Spring scheduling，并增加 `app.normalization.watchdog.*` 配置。

验证：

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 9s
```

后续仍需独立切片：

- `q.normalize.video` live smoke、queue/DLQ cleanup、ES residue cleanup。
