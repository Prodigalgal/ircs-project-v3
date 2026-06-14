# 028-06-I RT provider ARM64 live rollout + full queue parity smoke

日期：2026-06-09

状态：完成

## 结论

`ircs-metadata-worker:dev` 已重新构建并推送 linux/arm64 镜像，`ircs-dev`
metadata-worker 已 rollout 到包含 RT provider 代码的新 digest。短期开启
`APP_METADATA_RT_ENABLED=true` 后，单条 `q.fetch.metadata.rt` full queue smoke 通过
`MARK 6/6`；脚本触发 metadata-worker 发起 1 次 Rotten Tomatoes search GET，随后完成
raw metadata、search sync、queue/DLQ、DB/ES 和 port-forward 清理。测试结束后 gate 已恢复为
`APP_METADATA_RT_ENABLED=false`。

## 镜像与 live 证据

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
q.fetch.metadata.rt messages=0 consumers=1
```

## 验证命令

```powershell
.\gradlew.bat :services:ircs-metadata-worker:test
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-metadata-worker' -ImageName ircs-metadata-worker -Tag dev -Architecture arm64 -Os linux
kubectl -n ircs-dev rollout restart deployment/ircs-metadata-worker
kubectl -n ircs-dev rollout status deployment/ircs-metadata-worker --timeout=420s
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-rt-live-queue-02806-smoke.ps1 -EnableRtProviderForSmoke -WaitAttempts 60 -WaitSeconds 2
```

结果摘要：

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
credentialLease HTTP 200, leaseCount=1
```

## 清理与恢复

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

## MARK

```text
MARK 1/6 ARM64 metadata-worker dev image built/pushed and rolled out with RT code
MARK 2/6 live pod contains RT provider class/config and q.fetch.metadata.rt consumers=1
MARK 3/6 RT gate enabled only for one smoke and credential diagnostic succeeded
MARK 4/6 q.fetch.metadata.rt task drained and provider result updated raw metadata to SUCCESS
MARK 5/6 q.search.sync.raw refreshed ES raw document with RT enrichment fields
MARK 6/6 gate restored, queue/DLQ/DB/ES/port-forward cleanup verified
```

## 后续边界

`028-06 real provider live parity` 已关闭。扩大 RT 样本量、增加 credential/cookie 策略或公网
magnet runner live success 不属于本任务，应另拆新任务并保留显式 live gate。
