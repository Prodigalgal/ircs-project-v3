# 094 028-06-G Douban metadata-worker live image queue parity 报告

状态：完成

关联任务：

- `tasks/in-progress/028-06-real-provider-live-parity.md`
- `tasks/done/028-06-f-douban-provider-live-queue-parity.md`
- `tasks/done/028-06-g-douban-metadata-worker-live-image-queue-parity.md`

## 背景

`028-06-F` 在受控启用 `APP_METADATA_DOUBAN_ENABLED=true` 后未投递 provider queue，原因是
`q.fetch.metadata.douban consumers=0`；同一环境中 `q.fetch.metadata.tmdb consumers=1`。
本报告记录 `028-06-G` 对 live metadata-worker 镜像/manifest、Douban listener/provider 和
full queue smoke 的复验结果。

## 目标

- 确认 live metadata-worker 镜像包含 Douban listener/provider。
- 必要时构建并推送 ARM64 dev 镜像并 rollout。
- 复验 `q.fetch.metadata.douban consumers=1`。
- 复跑 028-06-F full queue parity smoke。
- 恢复 `APP_METADATA_DOUBAN_ENABLED=false`，清理 queue/DB/ES/port-forward 残留。

## 执行记录

### 本地测试

```powershell
.\gradlew.bat :services:ircs-metadata-worker:test
```

结果：`BUILD SUCCESSFUL`。新增 `DoubanProviderContextTest` 与 `TmdbProviderContextTest`，
覆盖 provider 相关 bean 的 Spring 容器装配。

### 镜像与 rollout

初始 live 状态：

```text
imageID=harbor.mnnu.eu.org/ircs/ircs-metadata-worker@sha256:455af9d6206aac8e8de89b93a93f749bc3b20b8355a31d6e7d9246aa67ca794a
q.fetch.metadata.douban consumers=0
q.fetch.metadata.tmdb consumers=1
```

首次发布后确认 live 镜像包含 Douban provider/listener，但暴露 constructor 装配错误：

```text
sha256:e0b1b52b619074becb4bc8151fd26620d589ec5e7f6bf606e2c8ce9d51e6a721
DoubanCredentialRepository: No default constructor found
RestClientDoubanHttpClient: No default constructor found
```

第二次发布后继续暴露 TMDB 同类 constructor 装配错误：

```text
sha256:0d3b2d6491acb06a7e6b1d27df0ac628c872529ee5f98f2124a878c8d3bbace8
TmdbCredentialRepository: No default constructor found
```

最小修复：

- `DoubanCredentialRepository` 生产构造器显式 `@Autowired`。
- `RestClientDoubanHttpClient` 生产构造器显式 `@Autowired`。
- `TmdbCredentialRepository` 生产构造器显式 `@Autowired`。
- `RestClientTmdbHttpClient` 生产构造器显式 `@Autowired`。
- `metadata-worker-dev.yaml` 放宽 probe：`startupProbe.failureThreshold=60`，startup/readiness/liveness `timeoutSeconds=3`。

最终发布：

```powershell
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-metadata-worker' -ImageName ircs-metadata-worker -Tag dev -Architecture arm64 -Os linux
kubectl -n ircs-dev rollout restart deployment/ircs-metadata-worker
kubectl -n ircs-dev rollout status deployment/ircs-metadata-worker --timeout=420s
```

结果：

```text
imageDigest=sha256:341baddc3286551eff7970eb4ffb166df67f4fa227e324aecaea3cf66a09b526
deployment "ircs-metadata-worker" successfully rolled out
pod=ircs-metadata-worker-56c9646bc6-wzb7c
ready=True
restartCount=0
imageID=harbor.mnnu.eu.org/ircs/ircs-metadata-worker@sha256:341baddc3286551eff7970eb4ffb166df67f4fa227e324aecaea3cf66a09b526
```

### consumer 复验

```text
q.fetch.metadata.douban       0  1
q.fetch.metadata.tmdb         0  1
q.fetch.metadata.rt           0  1
q.enrich.metadata.result      0  1
q.search.sync.raw             0  1
q.fetch.metadata.douban.dlq   0  0
q.enrich.metadata.result.dlq  0  0
q.search.sync.raw.dlq         0  0
```

### full queue smoke

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-douban-live-queue-02806-smoke.ps1 -EnableDoubanProviderForSmoke -WaitAttempts 45 -WaitSeconds 2
```

结果：

```text
MARK 1/6 credential lease diagnostic completed and Douban provider gate is enabled for one smoke
MARK 2/6 metadata/search consumers are ready and HTTPRoute remains absent
MARK 3/6 Douban provider queue task is published and q.fetch.metadata.douban drains
MARK 4/6 provider result updates raw metadata to SUCCESS with douban_id
MARK 5/6 raw search sync refreshes ES document with Douban enrichment fields
MARK 6/6 provider/result/search queues and DLQs are empty; smoke fixture is cleaned
status=SUCCESS
reason=DOUBAN_LIVE_QUEUE_PARITY_OK
rawVideoId=933867bc-5700-4a25-bb59-b3c89e02b56a
expectedDoubanId=1291843
credential lease HTTP 200, leaseCount=1
externalRequestCount=1
```

外网请求口径：脚本没有 direct Douban 请求；metadata-worker 由一条 provider queue task 触发
一次 Douban suggest GET。

### 恢复与清理

```text
APP_METADATA_LISTENER_ENABLED=true
APP_METADATA_DOUBAN_ENABLED=false
APP_METADATA_TMDB_WORKER_ENABLED=true
deployment "ircs-metadata-worker" successfully rolled out
DB smoke residue=0
port-forward residue=0
HTTPRoute=No resources found in ircs-dev namespace.
```

最终 queue/DLQ：

```text
q.fetch.metadata.douban       0  1
q.fetch.metadata.tmdb         0  1
q.fetch.metadata.rt           0  1
q.enrich.metadata.result      0  1
q.search.sync.raw             0  1
q.fetch.metadata.douban.dlq   0  0
q.fetch.metadata.tmdb.dlq     0  0
q.fetch.metadata.rt.dlq       0  0
q.enrich.metadata.result.dlq  0  0
q.search.sync.raw.dlq         0  0
```

## MARK

```text
MARK 1/6 live image/manifest inspected and final image digest recorded
MARK 2/6 q.fetch.metadata.douban consumers=1
MARK 3/6 Douban provider queue task published and drained
MARK 4/6 raw metadata success with douban_id=1291843
MARK 5/6 ES raw document projection refreshed with Douban enrichment fields
MARK 6/6 restore and cleanup complete
```

## 当前状态

完成。`028-06-F` full queue parity 已解除 `consumers=0` 阻塞并复跑成功；`028-06` 父任务仍保持进行中，
继续承接 RT provider 后续切片。
