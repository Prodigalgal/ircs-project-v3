# 063 028-08 normalization deep mapping live smoke 报告

状态：已完成

日期：2026-06-08

关联任务：

- `tasks/done/028-08-normalization-deep-mapping-parity.md`

## 背景

`028-08` 已通过本地测试完成 actor/director relation、raw category、standard mapping
和 retry watchdog，但剩余 `q.normalize.video` live smoke 未闭环。此前 `028-09`
normalization live smoke 只验证基础字段和 raw search sync，不能证明深层映射在真实
队列消费路径中 1:1 生效。

## 目标

- 在 `ircs-dev` 中通过 `q.normalize.video` 投递最小 raw video fixture。
- 验证 normalization worker 写入：
  - `video_actors` / `video_directors`
  - `video_raw_genres` / `video_raw_languages` / `video_raw_areas`
  - raw relation 的 `standard_*_id`
  - `raw_category.category_id`
  - `raw_videos.data_source_category_id`
- 验证 `q.search.sync.raw` 被 search-service 消费并刷新 `ircs_raw_video`。
- 验证 smoke 后 DB、queue/DLQ、ES fixture residue 均为 0。

## 非目标

- 不验证 aggregation -> unified search sync。
- 不调用真实 TMDB/Douban/RT provider。
- 不启用 R2 或真实外部对象存储。
- 不新增公共 HTTPRoute。
- 不执行全量 normalization 或全量 search rebuild。

## 验收标准

```text
MARK 1/6 normalization/search consumers are ready and queues are clean
MARK 2/6 q.normalize.video fixture is consumed and raw video becomes READY
MARK 3/6 actor/director and raw relation join rows are written
MARK 4/6 raw relations and raw category map to existing standard dictionaries
MARK 5/6 raw search sync refreshes ES document with deep mapping fields
MARK 6/6 smoke cleanup leaves no DB/ES residue and queues remain empty
```

## 测试边界

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
.\scripts\smoke\normalization-deep-mapping-02808-smoke.ps1
```

live smoke 使用唯一 `02808` fixture，并在开始前要求相关 queue/DLQ baseline 为 0；
如果存在非本轮消息则输出 `SKIPPED`，避免误清真实消息。

## 实现结果

- 新增 `normalization-deep-mapping-02808-smoke.ps1`，通过 Rabbit Management API
  投递 raw video id 到 `x.process` / `video.normalize`。
- smoke seed 唯一 `02808` fixture，包括 standard genre/language/area/category、
  data source 和 raw video `raw_metadata`。
- smoke 验证 normalization worker 写入 actor/director、raw relation、standard mapping
  与 raw category，并等待 search-service 刷新 `ircs_raw_video`。
- 为确保 live 环境包含当前 deep mapping 代码，`ircs-normalization-worker` 已推送
  ARM64 `dev` 镜像并 rollout。

## 验证结果

本地测试：

```text
.\gradlew.bat :services:ircs-normalization-worker:test --rerun-tasks
BUILD SUCCESSFUL in 9s

NormalizationRetryWatchdogTest tests=2, failures=0, errors=0, skipped=0
NormalizeVideoMessageParserTest tests=3, failures=0, errors=0, skipped=0
RawVideoNormalizationRepositoryTest tests=4, failures=0, errors=0, skipped=0
RawVideoNormalizationServiceTest tests=2, failures=0, errors=0, skipped=0
RawVideoTextNormalizerTest tests=4, failures=0, errors=0, skipped=0
```

ARM64 rollout：

```text
deployment/ircs-normalization-worker successfully rolled out
pod=ircs-normalization-worker-59b8c79d58-nz4nz
imageID=harbor.mnnu.eu.org/ircs/ircs-normalization-worker@sha256:d0e35af46a0bd8aa0ca60e4db2c973a90824b7e84783cd2f5c6db501d0f043fe
```

live smoke：

```text
.\scripts\smoke\normalization-deep-mapping-02808-smoke.ps1
MARK 1/6 normalization/search consumers are ready and queues are clean
MARK 2/6 q.normalize.video fixture is consumed and raw video becomes READY
MARK 3/6 actor/director and raw relation join rows are written
MARK 4/6 raw relations and raw category map to existing standard dictionaries
MARK 5/6 raw search sync refreshes ES document with deep mapping fields
MARK 6/6 smoke cleanup leaves no DB/ES residue and queues remain empty

trace: 02808-normalization-deep-20260608103455
rawVideoId: 2158c0c9-4b78-498d-9cae-4334261f640e
status: PASSED
```

后置检查：

```text
raw_videos 02808 residue: 0
standard_genre 02808 residue: 0
data_sources 02808 residue: 0
q.normalize.video messages=0, consumers=1, ready=0, unacked=0
q.normalize.video.dlq messages=0
q.search.sync.raw messages=0, consumers=1, ready=0, unacked=0
q.search.sync.raw.dlq messages=0
HTTPRoute in ircs-dev: No resources found
ResourceQuota: pods=28/30, services=24/26
```
