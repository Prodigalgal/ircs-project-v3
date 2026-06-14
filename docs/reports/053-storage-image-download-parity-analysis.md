# 053 storage image download parity 分析

状态：已完成

更新时间：2026-06-07

## 背景

`028-05` queue consumer 深层副作用审计已确认 `DOWNLOAD_IMAGE` 的 owner 是
`ircs-storage-service`，队列为 `q.storage.image`。V3 当前已有
`ImageDownloadConsumer` 和 `CoverImageDownloadService`，但下载链路仍缺 V1 的最小安全和失败语义：
download enabled gate、SSRF 校验、禁止 3xx redirect、10MB 上限、Content-Type allowlist
预检，以及失败 retry/backoff 后进入 `DEAD`。

本切片只补 `028-05-H image download true download/failure parity`，并与同批
`028-05-G metadata pending gather` 保持写入范围不重叠。

## 当前 V3 缺口

- `CoverImageDownloadService` 无 `app.storage.image.download-enabled` gate。
- `HttpClient` 当前允许 redirect，不符合 V1 禁止 3xx redirect 的安全边界。
- 下载前只检查 URL 前缀，不做 host/IP SSRF 过滤。
- 下载后依赖 magic bytes normalize，但下载前没有 `Content-Type` allowlist 预检。
- 失败 retry/backoff 已有基础记录，但缺安全失败、max retry 到 `DEAD` 和 consumer eligibility 的单元测试。

## 本轮方案

最小 dev-safe 修复：

```text
ImageDownloadConsumer
  -> 解析 UUID payload
  -> 调用 CoverImageDownloadService.process(imageId)

CoverImageDownloadService
  -> download-enabled=false 时跳过，不改元数据
  -> 只允许 EXTERNAL + UNPROCESSED/FAILED 被 markFetching
  -> 校验 http/https、host、解析 IP，不允许 loopback/site-local/link-local/multicast/any-local
  -> 禁止 redirect，3xx 直接失败
  -> Content-Type 必须在 image/jpeg/png/webp/gif/avif/bmp/x-icon allowlist 内
  -> Content-Length 和流式读取均执行 10MB 上限
  -> 成功 normalize + 本地存储 + finalize LOCAL_STORED
  -> 失败 markFailed，低于 max retry 为 FAILED 并写 next_retry_time，达到 max retry 为 DEAD
```

## 非目标

- 不实现 R2 pending upload、R2 true live、R2 traffic governor。
- 不实现 normalization producer、watchdog、全局 traffic governor。
- 不修改 `services/ircs-metadata-worker/**` 或生产级 deploy 拓扑。
- 不保留新增 K8S 对象、测试环境变量或公共 HTTPRoute。

## 风险

- 本地单元测试需要通过 mock HTTP server 模拟成功、失败、redirect 和 unsafe 场景，避免依赖公网。
- SSRF 校验需要在连接前解析 host；动态 DNS rebinding 不是本 dev-safe 切片的完整生产级防护范围。
- `localhost` 在单元测试中需要可控放行，否则无法做真下载测试；本轮应通过测试专用开关或构造方式隔离。

## 验收标准

```text
MARK 1/5 DOWNLOAD_IMAGE consumer consumes eligible EXTERNAL/UNPROCESSED fixture
MARK 2/5 true download stores local cover and finalizes LOCAL_STORED metadata
MARK 3/5 failed download records FAILED retry_count next_retry_time last_error
MARK 4/5 unsafe download is blocked without storing object, max retries reaches DEAD
MARK 5/5 live smoke cleanup leaves no DB/object residue, q.storage.image and DLQ empty
```

## 测试边界

本地必须执行：

```text
.\gradlew.bat :services:ircs-storage-service:test --rerun-tasks
```

本轮最低覆盖：

- true download。
- 失败 retry/backoff。
- unsafe blocked/max retry `DEAD`。
- consumer eligibility。

H-live 使用短暂 in-cluster fixture server，不依赖公网；storage-service 在 smoke 期间临时设置
`APP_STORAGE_IMAGE_ALLOW_LOCAL_ADDRESSES=true`，结束后恢复。

## 实现结果

2026-06-07 已完成：

- `CoverImageDownloadService` 增加 `app.storage.image.download-enabled` gate，关闭时不触碰元数据。
- `CoverImageDownloadService` 在连接前校验 http/https scheme、host、user info 和解析后的 IP 地址，
  默认阻断 loopback、site-local、link-local、multicast、any-local、常见内网/保留地址段。
- `HttpClient` 改为 `Redirect.NEVER`，3xx 响应直接失败。
- 下载前预检 `Content-Type` allowlist，支持 `image/jpeg`、`image/png`、`image/webp`、
  `image/gif`、`image/avif`、`image/bmp`、`image/x-icon`。
- `Content-Length` 与流式读取均执行 10MB 上限。
- 下载成功后把响应 `Content-Type` 传给 `FileNormalizationService`，本地存储后 finalize 为
  `LOCAL_STORED`。
- `CoverImageAdminRepository.markFetching()` 改为只允许 `EXTERNAL` + `UNPROCESSED/FAILED`，
  不再自动消费 `DEAD`。
- 新增 `CoverImageDownloadServiceTest`、`ImageDownloadConsumerTest`、
  `CoverImageAdminRepositoryTest` 覆盖真下载、失败 retry/backoff、unsafe blocked、redirect、
  Content-Type 预检、download disabled gate、consumer UUID payload 和 eligibility SQL。

完成标记：

```text
MARK 1/5 DOWNLOAD_IMAGE consumer consumes eligible EXTERNAL/UNPROCESSED fixture
MARK 2/5 true download stores local cover and finalizes LOCAL_STORED metadata
MARK 3/5 failed download records FAILED retry_count next_retry_time last_error
MARK 4/5 unsafe download is blocked without storing object, max retries reaches DEAD
MARK 5/5 live smoke cleanup leaves no DB/object residue, q.storage.image and DLQ empty
```

## 验证结果

```text
.\gradlew.bat :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL in 9s
12 actionable tasks: 12 executed
```

主线程 live smoke：

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-storage-service' -ImageName ircs-storage-service -Tag dev -Architecture arm64 -Os linux
Pushed harbor.mnnu.eu.org/ircs/ircs-storage-service:dev for linux/arm64

kubectl -n ircs-dev rollout restart deployment/ircs-storage-service
kubectl -n ircs-dev rollout status deployment/ircs-storage-service --timeout=240s
deployment "ircs-storage-service" successfully rolled out

.\scripts\smoke\storage-image-download-02805h-smoke.ps1
MARK 1/5 DOWNLOAD_IMAGE consumer listens on q.storage.image
MARK 2/5 DOWNLOAD_IMAGE fixture finalizes cover_images as LOCAL_STORED
MARK 3/5 downloaded cover object exists under storage-service local storage
MARK 4/5 q.storage.image and DLQ are empty after live download
MARK 5/5 live smoke cleanup leaves no DB/object residue, q.storage.image and DLQ empty
```

后置复核：

```text
fixture Pod/Service/ConfigMap: deleted
q.storage.image=0 messages, 1 consumer
q.storage.image.dlq=0 messages
source_domains/cover_images smoke residue: 0
ResourceQuota: pods=28/30, services=24/26
HTTPRoute: No resources found in ircs-dev namespace
storage-service imageID: harbor.mnnu.eu.org/ircs/ircs-storage-service@sha256:2fdc14e5fc39f1939ebde89c8d06963a4cc39d720a085469fb22ba678d498df4
```

## 遗留边界

- R2 pending upload、R2 true live、normalization producer/watchdog 和生产级 traffic governor
  仍为非目标，已拆入后续 backlog task specs。
- 本 task 文件已移动到 `tasks/done/028-05-h-storage-image-download-parity.md`。
