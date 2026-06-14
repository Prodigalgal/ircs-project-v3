# 063 normalization deep mapping live smoke 迁移记录

状态：已完成

日期：2026-06-08

## 迁移内容

为 `028-08` 补齐 `q.normalize.video` live smoke，证明 normalization 深层映射不仅在本地
单元测试成立，也能在 `ircs-dev` 的真实 RabbitMQ 消费路径中写入 DB 副作用并刷新 ES。

## 代码范围

```text
scripts/smoke/normalization-deep-mapping-02808-smoke.ps1
services/ircs-normalization-worker/**
deploy/k8s/dev/normalization-worker-dev.yaml
docs/reports/063-normalization-deep-mapping-live-smoke-02808.md
docs/migrations/063-normalization-deep-mapping-live-smoke.md
tasks/done/028-08-normalization-deep-mapping-parity.md
```

## 风险控制

- smoke fixture 使用唯一 `02808` 前缀和 UUID。
- 开始前检查 `q.normalize.video`、`q.normalize.video.dlq`、`q.search.sync.raw`、
  `q.search.sync.raw.dlq` 均为空。
- cleanup 只删除本轮 fixture 的 raw video、relation、standard dictionary、data source
  和 ES document。
- 不新增 HTTPRoute，不执行全量 normalization。

## 迁移结果

```text
MARK 1/6 normalization/search consumers are ready and queues are clean
MARK 2/6 q.normalize.video fixture is consumed and raw video becomes READY
MARK 3/6 actor/director and raw relation join rows are written
MARK 4/6 raw relations and raw category map to existing standard dictionaries
MARK 5/6 raw search sync refreshes ES document with deep mapping fields
MARK 6/6 smoke cleanup leaves no DB/ES residue and queues remain empty
```

验证证据：

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 9s

ircs-normalization-worker imageID:
  harbor.mnnu.eu.org/ircs/ircs-normalization-worker@sha256:d0e35af46a0bd8aa0ca60e4db2c973a90824b7e84783cd2f5c6db501d0f043fe

trace: 02808-normalization-deep-20260608103455
rawVideoId: 2158c0c9-4b78-498d-9cae-4334261f640e
status: PASSED
residue: 0
HTTPRoute: No resources found in ircs-dev namespace.
ResourceQuota: pods=28/30, services=24/26
```
