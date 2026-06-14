# 028-06-G Douban metadata-worker live image queue parity

状态：完成

父任务：

- `tasks/in-progress/028-06-real-provider-live-parity.md`

上游来源：

- `docs/reports/091-02806-f-douban-provider-live-queue-parity.md`
- `docs/reports/093-v1-v3-parity-progress-ledger.md`
- `tasks/done/028-06-f-douban-provider-live-queue-parity.md`

## 背景

`028-06-E` 已完成 Douban provider first slice，并通过真实 Douban readonly smoke；
`028-06-F` 已补 full queue parity smoke 脚本，但受控启用 gate 后阻塞在
`q.fetch.metadata.douban consumers=0`。同一运行态中 `q.fetch.metadata.tmdb consumers=1`，
说明 Rabbit、metadata-worker 基础 listener 和 TMDB provider worker 可用，剩余缺口集中在
live metadata-worker 镜像/manifest 是否包含并注册 Douban listener/provider。

## 目标

- 检查 `ircs-dev` live metadata-worker 镜像、manifest 与运行态 listener 是否包含 Douban。
- 如 live 镜像缺少当前 Douban listener/provider，构建并推送 ARM64 `ircs-metadata-worker:dev`
  镜像，rollout `ircs-dev`。
- 复验 `q.fetch.metadata.douban consumers=1`。
- 短期开启 `APP_METADATA_DOUBAN_ENABLED=true`，复跑 028-06-F full queue parity smoke。
- 记录 provider 请求、解析结果、DB/search sync、queue/DLQ、外网请求次数和恢复/清理证据。

## 非目标

- 不常驻开启 Douban provider gate。
- 不修改 config-service/content-service Rabbit C2、notification、storage、portal/admin/BFF。
- 不批量访问 Douban，不新增 crawler，不绕过 Douban 访问策略。
- 不验证 Douban 登录态或 cookie 凭据质量。

## 输入

- `services/ircs-metadata-worker/**`
- `deploy/k8s/dev/metadata-worker-dev.yaml`
- `scripts/smoke/metadata-provider-douban-live-queue-02806-smoke.ps1`
- `q.fetch.metadata.douban`
- `q.enrich.metadata.result`
- `q.search.sync.raw`
- dev PostgreSQL、RabbitMQ、Elasticsearch、Kubernetes deployment/pod 状态

## 输出

- 如需要：metadata-worker 最小代码/manifest 修复。
- 如需要：ARM64 metadata-worker dev 镜像与 digest。
- `docs/reports/094-02806g-douban-metadata-worker-live-image-queue-parity.md`
- 本任务、父任务、`028-06-F`、`docs/index.md`、`tasks/index.md`、`093` 总账回写。

## 影响范围

```text
services/ircs-metadata-worker/**
deploy/k8s/dev/metadata-worker-dev.yaml
scripts/smoke/metadata-provider-douban-live-queue-02806-smoke.ps1
docs/reports/093-v1-v3-parity-progress-ledger.md
docs/reports/094-02806g-douban-metadata-worker-live-image-queue-parity.md
docs/index.md
tasks/index.md
tasks/in-progress/028-06-real-provider-live-parity.md
tasks/done/028-06-f-douban-provider-live-queue-parity.md
tasks/done/028-06-g-douban-metadata-worker-live-image-queue-parity.md
tasks/done/**
```

## 依赖

- `kubectl` 可访问 `ircs-dev`。
- Harbor push 凭据可通过 `ircs-system/harbor-secret` 读取。
- dev 节点为 ARM64，镜像必须以 `linux/arm64` 推送。
- RabbitMQ、PostgreSQL、credential-service、Elasticsearch 与 search-service 可用。
- Douban public suggest endpoint 可低频访问。

## 风险

- 共享 `ircs-dev` rollout 和 queue smoke 可能与其他 worker 并行操作冲突。
- Harbor tag `dev` 是可变 tag，必须记录 rollout 后 pod `imageID` / digest。
- Douban upstream 可能 403、429、5xx、空结果或字段漂移。
- smoke 失败路径也必须恢复 `APP_METADATA_DOUBAN_ENABLED=false` 并清理 DB/ES/queue/port-forward。

## 验收标准

- 记录 live manifest image、pod imageID/digest 和必要的镜像构建/推送命令。
- 复验 `q.fetch.metadata.douban consumers=1`。
- full queue smoke 至少给出 `MARK 1/6` 到最终成功或精确阻塞点。
- 成功时记录 `MARK 6/6`、`externalRequestCount=1`、raw metadata `SUCCESS` 与 Douban id、ES 投影和 cleanup。
- 失败/跳过时记录真实原因、外网请求次数和恢复/清理证据。
- 结束后 `APP_METADATA_DOUBAN_ENABLED=false`，目标 queue/DLQ messages=0，DB/ES fixture residue=0，本脚本 port-forward 无残留。

## 测试边界

```powershell
.\gradlew.bat :services:ircs-metadata-worker:test
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-metadata-worker' -ImageName ircs-metadata-worker -Tag dev -Architecture arm64 -Os linux
kubectl apply -f deploy\k8s\dev\metadata-worker-dev.yaml
kubectl -n ircs-dev rollout status deployment/ircs-metadata-worker --timeout=240s
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-douban-live-queue-02806-smoke.ps1 -EnableDoubanProviderForSmoke
```

## 关联文档

- `docs/reports/091-02806-f-douban-provider-live-queue-parity.md`
- `docs/reports/093-v1-v3-parity-progress-ledger.md`
- `docs/reports/094-02806g-douban-metadata-worker-live-image-queue-parity.md`

## 关联任务

- 父任务：`tasks/in-progress/028-06-real-provider-live-parity.md`
- 前置阻塞任务：`tasks/done/028-06-f-douban-provider-live-queue-parity.md`
- 本任务：`tasks/done/028-06-g-douban-metadata-worker-live-image-queue-parity.md`

## 当前状态

完成。已发布 metadata-worker dev 镜像、复验 Douban consumer，并复跑 full queue smoke。

## 实现结果

- 修复 Douban/TMDB provider 相关生产构造器未被 Spring 选中的问题：
  - `DoubanCredentialRepository`
  - `RestClientDoubanHttpClient`
  - `TmdbCredentialRepository`
  - `RestClientTmdbHttpClient`
- 新增 Douban/TMDB provider context 装配测试。
- 放宽 metadata-worker dev startup/readiness/liveness probe timeout，避免低资源 rollout 时过早杀进程。
- 推送并 rollout ARM64 dev 镜像：

```text
sha256:341baddc3286551eff7970eb4ffb166df67f4fa227e324aecaea3cf66a09b526
```

## 验证结果

```text
.\gradlew.bat :services:ircs-metadata-worker:test
BUILD SUCCESSFUL

q.fetch.metadata.douban consumers=1

MARK 1/6 credential lease diagnostic completed and Douban provider gate is enabled for one smoke
MARK 2/6 metadata/search consumers are ready and HTTPRoute remains absent
MARK 3/6 Douban provider queue task is published and q.fetch.metadata.douban drains
MARK 4/6 provider result updates raw metadata to SUCCESS with douban_id
MARK 5/6 raw search sync refreshes ES document with Douban enrichment fields
MARK 6/6 provider/result/search queues and DLQs are empty; smoke fixture is cleaned

externalRequestCount=1
APP_METADATA_DOUBAN_ENABLED=false restored
queue/DLQ messages=0
DB smoke residue=0
port-forward residue=0
```

## 后续边界

- `028-06` 父任务仍保持进行中，后续只剩 RT provider first slice / live queue parity 等独立切片。
