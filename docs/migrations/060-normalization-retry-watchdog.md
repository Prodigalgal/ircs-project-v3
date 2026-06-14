# 060 normalization retry watchdog

状态：已完成

更新时间：2026-06-07

关联任务：

- `tasks/done/028-08-normalization-deep-mapping-parity.md`

关联报告：

- `docs/reports/059-normalization-retry-watchdog-02808.md`

## 迁移目标

为 V3 normalization worker 增加低资源 watchdog/recovery，让 due `FAILED` 和 stale `PENDING`
raw video 能重新进入 `q.normalize.video`，补齐 V1 的失败重试与启动恢复语义。

## 行为变化

- 新增可关闭 watchdog。
- 小批量扫描 due `FAILED` 和 stale `PENDING`。
- 重新发布 `q.normalize.video`，不直接改业务状态。
- 跳过 `READY`、`PERMANENT_FAILURE` 和未到 retry time 的记录。

## 兼容性

- 不新增数据库结构。
- 不新增 HTTPRoute。
- 不新增 Pod、Service、PVC 或中间件。
- 默认 batch size 小，适合 `ircs-dev` 低资源环境。

## 验证边界

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
```

## 实施结果

已完成 normalization watchdog/recovery 代码和测试：

```text
MARK 1/4 normalization watchdog scans due FAILED records and republishes q.normalize.video
MARK 2/4 normalization watchdog scans stale PENDING records for startup recovery
MARK 3/4 normalization watchdog skips READY/PERMANENT_FAILURE and future retry records
MARK 4/4 normalization watchdog remains low-resource and configurable
```

验证：

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 9s
```
