# 028-06-I RT provider ARM64 live rollout + full queue parity smoke

状态：完成

父任务：

- `tasks/done/028-06-real-provider-live-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

## 背景

`028-06-H` 已完成 Rotten Tomatoes provider first slice：本地 provider implementation、credential
catalog、focused tests 和真实只读 RT HTTP 200 均已通过。剩余风险是 `ircs-dev` live
metadata-worker 镜像仍可能停留在上一轮 Douban digest，未包含本地 RT provider 代码，因此需要发布
ARM64 dev 镜像后再做受控 full queue parity smoke。

## 目标

- 构建并推送 ARM64 `harbor.mnnu.eu.org/ircs/ircs-metadata-worker:dev`。
- rollout `ircs-dev` `ircs-metadata-worker`，记录 live image digest。
- 确认 live pod 含 RT provider class/配置，且 `q.fetch.metadata.rt consumers=1`。
- 短期开启 `APP_METADATA_RT_ENABLED=true`，投递一条最小 RT provider task。
- 验证 provider result、raw metadata、search sync、queue/DLQ、DB/ES cleanup。
- 恢复 `APP_METADATA_RT_ENABLED=false` 和原 deployment env，清理 port-forward/临时数据。

## 非目标

- 不修改 config/content C2、notification、storage、portal/admin/BFF。
- 不常驻开启 RT provider gate。
- 不进行高频 RT 抓取，不保存公网响应全文。
- 不清理非本 smoke 创建的业务数据。

## 影响范围

```text
deploy/k8s/dev/metadata-worker*
scripts/smoke/metadata-provider-rt*
docs/**
tasks/**
services/ircs-metadata-worker/** 仅限 RT provider live 编译/运行阻塞的最小修复
```

## 输入

- `q.fetch.metadata.rt`
- `q.enrich.metadata.result`
- `q.search.sync.raw`
- dev PostgreSQL `raw_videos`
- dev RabbitMQ、credential-service、Elasticsearch
- Rotten Tomatoes search readonly endpoint

## 输出

- ARM64 metadata-worker dev image digest。
- RT full queue smoke 脚本和执行记录。
- `028-06-I` task/report/migration/index 回写。
- 如全链路通过，关闭 `028-06 real provider live parity` 父任务；如阻塞，记录精确证据。

## 风险

- 共享 `ircs-dev` 环境可能存在并行 worker rollout/env patch，执行前后都必须复核 gate 与 queue 状态。
- RT upstream 可能返回 403/429/HTML 结构变化；失败必须分类为外网/解析/代码问题。
- Jib push 覆盖 `:dev` tag，必须确认 ARM64 digest 和 live imageID。

## 验收标准

- 本地 metadata-worker focused/full test 通过，或阻塞点明确。
- live pod `imageID` digest 已记录，且 pod Ready、restartCount 可接受。
- `q.fetch.metadata.rt consumers=1`，result/search queue consumers ready。
- smoke 输出 `MARK 6/6` 或给出精确阻塞分类。
- 外网请求数记录为最小单次 queue-triggered RT 请求。
- `APP_METADATA_RT_ENABLED=false` 已恢复，queue/DLQ messages=0，DB/ES smoke residue=0，port-forward residue=0。

## 测试方式

```powershell
.\gradlew.bat :services:ircs-metadata-worker:test
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-metadata-worker' -ImageName ircs-metadata-worker -Tag dev -Architecture arm64 -Os linux
kubectl -n ircs-dev rollout restart deployment/ircs-metadata-worker
kubectl -n ircs-dev rollout status deployment/ircs-metadata-worker --timeout=420s
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-rt-live-queue-02806-smoke.ps1 -EnableRtProviderForSmoke -WaitAttempts 45 -WaitSeconds 2
```

## 当前状态

task spec 已创建；上一轮 worker 未留下可靠 build/push/rollout/smoke 结果。

## 2026-06-09 本轮 worker 接手记录

- 当前目录不是 Git worktree，`git status --short` 返回 `fatal: not a git repository`；本轮改用文件内容和路径范围核对，避免覆盖并行 worker 改动。
- 已复核允许范围：`deploy/k8s/dev/metadata-worker*`、`scripts/smoke/metadata-provider-rt*`、`docs/**`、`tasks/**`，以及必要时 `services/ircs-metadata-worker/**` 最小修复。
- 计划先运行 `:services:ircs-metadata-worker:test`，再构建/推送 ARM64 dev 镜像、rollout `ircs-dev`、执行 RT full queue smoke，最后恢复 gate/env 并回写证据。

## 2026-06-09 完成记录

### 镜像与 rollout

```text
Jib imageId:     sha256:56db94095f68c633d4dd7003a60b9cc237e7852bf60a182e1ac18cd9b595b473
Jib imageDigest: sha256:93db92be56a2a7dc0b85d84c7a43ecb8c8f854f5947f1bbd7a6ae2dafc34291a
rollout before:  harbor.mnnu.eu.org/ircs/ircs-metadata-worker@sha256:d2c29c9ed9973505336ff4bddf5be532da71af4661415e194a60ff7462ea180a
rollout after:   harbor.mnnu.eu.org/ircs/ircs-metadata-worker@sha256:93db92be56a2a7dc0b85d84c7a43ecb8c8f854f5947f1bbd7a6ae2dafc34291a
pod:             ircs-metadata-worker-677b448c66-4j2qh ready=true restarts=0
```

live pod jar 证据：

```text
com/prodigalgal/ircs/metadata/provider/rt/RottenTomatoesMetadataProvider.class
application.yaml: rotten-tomatoes-enabled: ${APP_METADATA_RT_ENABLED:false}
```

### 验证命令

```powershell
.\gradlew.bat :services:ircs-metadata-worker:test
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-metadata-worker' -ImageName ircs-metadata-worker -Tag dev -Architecture arm64 -Os linux
kubectl -n ircs-dev rollout restart deployment/ircs-metadata-worker
kubectl -n ircs-dev rollout status deployment/ircs-metadata-worker --timeout=420s
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-rt-live-queue-02806-smoke.ps1 -EnableRtProviderForSmoke -WaitAttempts 60 -WaitSeconds 2
```

结果：

```text
BUILD SUCCESSFUL in 8s
Pushed harbor.mnnu.eu.org/ircs/ircs-metadata-worker:dev for linux/arm64
deployment "ircs-metadata-worker" successfully rolled out
MARK 1/6 credential lease diagnostic completed and RT provider gate is enabled for one smoke
MARK 2/6 metadata/search consumers are ready and HTTPRoute remains absent
MARK 3/6 RT provider queue task is published and q.fetch.metadata.rt drains
MARK 4/6 provider result updates raw metadata to SUCCESS with rotten_tomatoes_id
MARK 5/6 raw search sync refreshes ES document with RT enrichment fields
MARK 6/6 provider/result/search queues and DLQs are empty; smoke fixture is cleaned
status=SUCCESS
reason=RT_LIVE_QUEUE_PARITY_OK
rawVideoId=74e73456-966a-4187-83fe-de72ca19161c
expectedRtId=matrix
externalRequestCount=1
externalRequestMode=metadata-worker issued one Rotten Tomatoes search GET for one queue task; script made no direct RT request
credentialLease HTTP 200, leaseCount=1
```

### 恢复与清理

```text
APP_METADATA_RT_ENABLED=false
APP_METADATA_TMDB_WORKER_ENABLED=true
APP_METADATA_LISTENER_ENABLED=true
q.fetch.metadata.rt              messages=0 consumers=1
q.fetch.metadata.rt.dlq          messages=0 consumers=0
q.enrich.metadata.result         messages=0 consumers=1
q.enrich.metadata.result.dlq     messages=0 consumers=0
q.search.sync.raw                messages=0 consumers=1
q.search.sync.raw.dlq            messages=0 consumers=0
DB residue raw_videos/raw_video_unified_video = 0|0
ES residue ircs_raw_video/74e73456-966a-4187-83fe-de72ca19161c = 404 found=false
local smoke ports 19078/18092/39203 = no listeners
kubectl port-forward 19078/18092/39203 = no process
HTTPRoute remains absent
```

### MARK

```text
MARK 1/6 ARM64 metadata-worker dev image built/pushed and rolled out with RT code
MARK 2/6 live pod contains RT provider class/config and q.fetch.metadata.rt consumers=1
MARK 3/6 RT gate enabled only for one smoke and credential diagnostic succeeded
MARK 4/6 q.fetch.metadata.rt task drained and provider result updated raw metadata to SUCCESS
MARK 5/6 q.search.sync.raw refreshed ES raw document with RT enrichment fields
MARK 6/6 gate restored, queue/DLQ/DB/ES/port-forward cleanup verified
```
