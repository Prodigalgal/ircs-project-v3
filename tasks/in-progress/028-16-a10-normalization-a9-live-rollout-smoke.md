# 028-16-A10 normalization A9 live rollout smoke task spec

状态：进行中

父任务：

- `tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md`
- `tasks/in-progress/028-16-a9-title-location-genre-pipeline-first-closure.md`

## 背景

028-16-A9 已完成本地 first closure：title/subtitle、alias 去重、中文/英文季、地区/语言
alias、genre synonym/protected-term、locked fields 不覆盖、invalid metadata failure path 均已由
normalization-worker tests 覆盖。A9 live 首跑只到 MARK 1/5，阻塞在 K8S TLS handshake timeout；
同时发现 Windows PowerShell 管道 UTF-8 问题，脚本已显式设置 UTF-8。

## 目标

- 构建并推送 `harbor.mnnu.eu.org/ircs/ircs-normalization-worker:dev` ARM64 镜像。
- rollout `ircs-dev` 中 `ircs-normalization-worker`，记录 live image digest / imageID。
- 重跑 `scripts/smoke/normalization-a9-title-location-genre-closure-smoke.ps1` live 模式。
- 验证 DB 标量、genre/language/area relation、Rabbit queue/search sync 基线和脚本全部 MARK。
- 复查中文 fixture 未出现 `?` 替换。
- 清理本轮 DB rows、Rabbit queues/DLQ、port-forward 残留。

## 非目标

- 不修改 notification、metadata provider、content C2、storage、portal/admin/BFF、shared outbound。
- 不扩展完整 V1 location NLP、完整 genre 词表、LLM cleaning 或 title fuzzy pipeline。
- 不做生产环境变更。

## 影响文件

允许写入：

```text
services/ircs-normalization-worker/**
deploy/k8s/dev/normalization-worker*
scripts/smoke/normalization-a9-title-location-genre-closure-smoke.ps1
docs/**
tasks/**
```

优先实际写入：

```text
tasks/in-progress/028-16-a10-normalization-a9-live-rollout-smoke.md
tasks/in-progress/028-16-a9-title-location-genre-pipeline-first-closure.md
tasks/in-progress/028-16-a-normalization-cleaning-strategy-parity.md
docs/reports/093-v1-v3-parity-progress-ledger.md
docs/index.md
tasks/index.md
```

## 验收标准

```text
MARK 1/5 normalization/search consumers are ready and queues are clean
MARK 2/5 q.normalize.video fixture is consumed and title/alias/season/subtitle scalars are normalized
MARK 3/5 genre/language/area alias relation rows are written
MARK 4/5 DB fixture residue is cleaned
MARK 5/5 Rabbit queues remain empty after smoke
```

额外验收：

```text
image digest recorded
rollout deployment/ircs-normalization-worker Ready 1/1
Chinese fixture output contains 庆余年/特别篇/国粤双语 and no ? replacement
DB residue raw_videos/data_sources/raw relation temp rows = 0
q.normalize.video/q.normalize.video.dlq/q.search.sync.raw/q.search.sync.raw.dlq messages = 0
Rabbit port-forward 19094 residue = 0
```

## 测试方式

```powershell
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test --rerun-tasks
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-normalization-worker' -ImageName ircs-normalization-worker -Tag dev -Architecture arm64 -Os linux
kubectl apply -f .\deploy\k8s\dev\normalization-worker-dev.yaml
kubectl -n ircs-dev rollout restart deployment/ircs-normalization-worker
kubectl -n ircs-dev rollout status deployment/ircs-normalization-worker --timeout=240s
.\scripts\smoke\normalization-a9-title-location-genre-closure-smoke.ps1
```

## 结果记录

待执行。
