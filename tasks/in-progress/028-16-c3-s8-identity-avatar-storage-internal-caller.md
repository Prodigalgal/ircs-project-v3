# 028-16-C3-S8 identity avatar storage INTERNAL_SERVICE caller task spec

状态：实现完成；统一测试后置

父任务：

- `tasks/in-progress/028-16-c3-shared-outbound-http-client-parity.md`

上游来源：

- `tasks/in-progress/028-16-c3-shared-outbound-http-client-parity.md`
- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/reports/093-v1-v3-parity-progress-ledger.md`

## 背景

C3-S6 已完成 shared circuit breaker first slice 与 content internal caller；C3-S7 已完成
task-service POST JSON internal caller。并行 worker 已补 storage `IMAGE_DOWNLOAD_STRICT`
adapter 与 portal/admin BFF target validation 记录。本轮继续优先推进剩余 internal caller adoption：
identity-service avatar multipart 上传到 storage-service。

## 目标

- `RestClientAvatarStorageClient` 改用 shared `OutboundHttpClient` +
  `OutboundHttpPolicy.internalService()`。
- 保留固定 `/internal/storage/avatars` path、multipart `file` 字段、filename、content type、
  response JSON 到 `StoredAvatar` 的映射。
- 保留 timeout/5xx/IO/open circuit 等失败统一映射为现有
  `ApiException(BAD_GATEWAY, "storage.sync.failed")` 外观。
- 增加 caller-specific request timeout 配置，避免继续依赖 RestClient 默认行为。
- 使用 fake transport 覆盖 multipart body、invalid internal URL fail-before-send、5xx/IO/open
  circuit failure mapping。

## 非目标

- 不修改 storage avatar API、R2 sync、cover download streaming、BFF transport streaming。
- 不修改 notification、normalization、C1 readiness 或 metadata provider。
- 不做 live smoke，不做全量长测试；统一测试后置。

## 影响文件

```text
services/ircs-identity-service/src/main/java/com/prodigalgal/ircs/identity/RestClientAvatarStorageClient.java
services/ircs-identity-service/src/test/java/com/prodigalgal/ircs/identity/RestClientAvatarStorageClientTest.java
services/ircs-identity-service/src/test/java/com/prodigalgal/ircs/identity/RestClientAvatarStorageClientContextTest.java
tasks/in-progress/028-16-c3-shared-outbound-http-client-parity.md
tasks/in-progress/028-16-c3-s8-identity-avatar-storage-internal-caller.md
tasks/index.md
docs/reports/089-v1-golden-behavior-inventory-marks-main.md
docs/reports/089-v1-golden-behavior-inventory-overview.md
docs/reports/093-v1-v3-parity-progress-ledger.md
```

## 验收标准

```text
C3-S8 MARK 1/6 RestClientAvatarStorageClient uses shared INTERNAL_SERVICE multipart POST
C3-S8 MARK 2/6 multipart field filename content type and body are preserved
C3-S8 MARK 3/6 2xx response mapping and empty response failure remain compatible
C3-S8 MARK 4/6 timeout/5xx/IO/open circuit map to storage.sync.failed BAD_GATEWAY
C3-S8 MARK 5/6 invalid internal base URL fails before transport send
C3-S8 MARK 6/6 docs/tasks ledger updated and live/full tests deferred to unified testing
```

## 测试方式

轻量验证优先：

```text
.\gradlew.bat --no-daemon :services:ircs-identity-service:test --tests "com.prodigalgal.ircs.identity.RestClientAvatarStorageClientTest" --tests "com.prodigalgal.ircs.identity.RestClientAvatarStorageClientContextTest" --rerun-tasks
```

统一后置测试：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-identity-service:test :services:ircs-storage-service:test :services:ircs-portal-bff:test --rerun-tasks
```

## 实现结果

- `RestClientAvatarStorageClient` 已从 Spring `RestClient` multipart upload 改为 shared
  `OutboundHttpClient` + `OutboundHttpPolicy.internalService()`。
- 生产路径使用 `DefaultOutboundAddressResolver`、`OutboundUrlPolicy` 和
  `JdkOutboundTransport`；固定上传 URI 为
  `/internal/storage/avatars`。
- 保留 multipart `file` 字段、filename、content type 和 binary body；multipart body 在 caller
  内构造，shared transport 仍只承接 method/body/header，不强行引入 streaming/multipart 专用抽象。
- 新增 `app.identity.storage-service.request-timeout`，默认 `10s`；caller 使用
  `identity-avatar-storage` circuit breaker key。
- 2xx response JSON 继续映射为 `StoredAvatar`；空 body、空 url、4xx/5xx、IO、timeout、invalid
  internal URL、open circuit 均映射为现有
  `ApiException(BAD_GATEWAY, "storage.sync.failed")` 外观。

## 验证结果

已运行轻量定向测试：

```text
.\gradlew.bat --no-daemon :services:ircs-identity-service:test --tests "com.prodigalgal.ircs.identity.RestClientAvatarStorageClientTest" --tests "com.prodigalgal.ircs.identity.RestClientAvatarStorageClientContextTest" --rerun-tasks
BUILD SUCCESSFUL in 16s
```

说明：

- 首次运行发现轻量 `ApplicationContextRunner` 无法把 `@Value String` 转成 `Duration`；已改为注入
  String 并由 caller 解析 `10s` / `ms` / `m` / ISO-8601 duration。
- 本轮按用户策略未执行 live smoke、未执行 identity/storage/BFF 组合长测试。
- 编译期间仍出现既有 `JsonInclude.Include.NON_EMPTY` annotation warning；未影响本定向测试。

## MARK

```text
C3-S8 MARK 1/6 RestClientAvatarStorageClient uses shared INTERNAL_SERVICE multipart POST: done
C3-S8 MARK 2/6 multipart field filename content type and body are preserved: done
C3-S8 MARK 3/6 2xx response mapping and empty response failure remain compatible: done
C3-S8 MARK 4/6 timeout/5xx/IO/open circuit map to storage.sync.failed BAD_GATEWAY: done
C3-S8 MARK 5/6 invalid internal base URL fails before transport send: done
C3-S8 MARK 6/6 docs/tasks ledger updated and live/full tests deferred to unified testing: done
```

## 统一测试后置项

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-identity-service:test :services:ircs-storage-service:test :services:ircs-portal-bff:test --rerun-tasks
```

场景：

- identity avatar upload -> storage internal avatar endpoint fake/live-safe chain。
- storage `IMAGE_DOWNLOAD_STRICT` adapter focused regression。
- BFF target validation 与后续 method/body/streaming transport 复用边界。
- shared outbound circuit breaker caller-specific ENV / key matrix。

## 剩余 C3 风险

- `storage-service/CoverImageDownloadService` 的 `IMAGE_DOWNLOAD_STRICT` adapter 已由并行 worker
  完成并记录在 `tasks/done/028-16-c3-storage-image-download-strict-adapter.md`；本轮未重复修改。
- admin/portal BFF target validation 已有完成记录；BFF full transport method/body/streaming 复用仍未实现。
- caller-specific circuit config 仍需后续按 service-level ENV / caller key 做统一矩阵。
