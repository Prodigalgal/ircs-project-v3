# 028-16-C3 storage IMAGE_DOWNLOAD_STRICT adapter task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c3-shared-outbound-http-client-parity.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md` shared outbound 表
- `tasks/in-progress/028-16-c3-shared-outbound-http-client-parity.md`
- `services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/CoverImageDownloadService.java`

## 背景

C3 已完成 `PUBLIC_FETCH` 第一批、metadata `INTERNAL_SERVICE`、admin/portal
`BFF_PROXY` target validation。storage cover image download 当前保留 V3 更严格
replacement：redirect blocked、10MB、image MIME 和私网阻断，但尚未纳入 shared
outbound policy 账本，也缺少 no-real-network 的 strict adapter contract 测试。

现有 shared `OutboundHttpClient` 以 byte array response 为主，不适合直接承接 cover
image 的二进制 streaming 下载；storage 需要继续在读取过程中执行 10MB 上限截断。

## 目标

- 在 `shared/ircs-common` 中补足 `IMAGE_DOWNLOAD_STRICT` policy 类型和 URL/DNS 校验入口。
- `CoverImageDownloadService` 下载前复用 shared `IMAGE_DOWNLOAD_STRICT` URL policy。
- 保留 storage strict streaming adapter，继续阻断 redirect、非 image MIME、超过 10MB 的
  `Content-Length` 或实际 body。
- 使用 fake resolver / fake transport 补 no-real-network focused tests，覆盖私网阻断、
  redirect blocked、MIME 或 size 中至少两个。

## 非目标

- 不把 cover image streaming 下载强行改成 shared byte-array transport。
- 不修改 R2 scripts、normalization、notification send history、BFF 或 HTTPRoute。
- 不做真实公网、真实内网或本地 HTTP server 调用。
- 不更新 `docs/index.md`、`tasks/index.md`、`docs/reports/089-v1-golden-behavior-inventory.md`
  或 `docs/migrations/089-v1-golden-behavior-inventory.md`。

## 输入

- cover image external URL。
- storage image download 配置：`download-enabled`、`allow-local-addresses`、
  `max-retries`、`download-timeout-seconds`。
- shared outbound URL policy 与 DNS resolver。

## 输出

- shared `IMAGE_DOWNLOAD_STRICT` policy 类型和测试证据。
- storage cover image download strict adapter seam 和 no-real-network contract tests。
- 完成后移动到 `tasks/done/` 并记录验证结果。

## 影响范围

允许修改：

- `shared/ircs-common/src/main/java/com/prodigalgal/ircs/common/outbound/**`
- `shared/ircs-common/src/test/java/com/prodigalgal/ircs/common/outbound/**`
- `services/ircs-storage-service/build.gradle`
- `services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/**`
- `services/ircs-storage-service/src/test/java/com/prodigalgal/ircs/storage/image/**`
- 本任务文件自身

不包含：

- `docs/index.md`
- `tasks/index.md`
- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/migrations/089-v1-golden-behavior-inventory.md`
- notification send history、normalization、BFF、R2 scripts

## 依赖

- `shared:ircs-common` outbound policy 现有 `PUBLIC_FETCH` / `INTERNAL_SERVICE` /
  `BFF_PROXY` 结构。
- JDK `HttpClient` production adapter。
- Mockito / JUnit focused unit tests。

## 风险

- shared byte-array transport 不适合大图 streaming，直接复用可能破坏 10MB 边读边截断语义。
- URL policy message 改变可能影响 `markFailed` 错误文本断言，需要测试只断言关键安全语义。
- `allow-local-addresses` 是既有配置，默认仍应阻断私网；测试需要用 fake resolver 而不是本地
  HTTP server。

## 验收标准

```text
C3-STORAGE-STRICT MARK 1/5 shared outbound policy includes IMAGE_DOWNLOAD_STRICT
C3-STORAGE-STRICT MARK 2/5 CoverImageDownloadService validates remote URL through shared IMAGE_DOWNLOAD_STRICT policy
C3-STORAGE-STRICT MARK 3/5 strict adapter contract tests cover redirect blocked and MIME or size checks without real network
C3-STORAGE-STRICT MARK 4/5 private/loopback address is blocked before download transport
C3-STORAGE-STRICT MARK 5/5 focused shared + storage tests pass without real network
```

## 测试边界

计划运行：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-storage-service:test --rerun-tasks
```

如果改动比预期更小，可缩小到对应 test class；最终记录实际命令和结果。

## 实现结果

- `shared/ircs-common` 新增 `OutboundHttpPolicyType.IMAGE_DOWNLOAD_STRICT` 和
  `OutboundHttpPolicy.imageDownloadStrict()` factory。
- `OutboundUrlPolicy` 新增 `validateImageDownloadStrict()`，复用 public host DNS
  fail-closed 校验，默认阻断 localhost、私网、link-local、metadata host、reserved 和
  multicast 地址。
- `IMAGE_DOWNLOAD_STRICT` 保留 storage 专用 `User-Agent`：`IRCS-Storage-Service/0.1`，
  不启用 shared gzip 解压和 HTTP retry，避免改变 cover image streaming 语义。
- `allow-local-addresses=true` 继续作为既有 dev escape hatch，只放开解析到本地/私网地址的
  下载；metadata host 仍始终阻断。
- `CoverImageDownloadService` 下载前改为调用 shared
  `validateImageDownloadStrict()`；生产下载仍走 JDK `HttpClient` streaming adapter，继续
  `followRedirects(NEVER)`。
- 新增 `CoverImageDownloadAdapter` / `JdkCoverImageDownloadAdapter` seam，测试用 fake adapter
  完成 no-real-network strict contract 覆盖。
- storage focused tests 已覆盖私网阻断发生在 adapter 前、redirect blocked、非 image MIME、
  `Content-Length` 超过 10MB、实际 body 超过 10MB、成功写本地和 disabled/non-eligible gate。

## 验证结果

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-storage-service:test --rerun-tasks
BUILD SUCCESSFUL
```

测试摘要：

```text
OutboundHttpClientTest: tests=11, failures=0, errors=0
CoverImageDownloadServiceTest: tests=10, failures=0, errors=0
```

## MARK

```text
C3-STORAGE-STRICT MARK 1/5 shared outbound policy includes IMAGE_DOWNLOAD_STRICT: done
C3-STORAGE-STRICT MARK 2/5 CoverImageDownloadService validates remote URL through shared IMAGE_DOWNLOAD_STRICT policy: done
C3-STORAGE-STRICT MARK 3/5 strict adapter contract tests cover redirect blocked and MIME or size checks without real network: done
C3-STORAGE-STRICT MARK 4/5 private/loopback address is blocked before download transport: done
C3-STORAGE-STRICT MARK 5/5 focused shared + storage tests pass without real network: done
```

## 剩余 C3 风险

- storage cover image download 仍未接 shared full transport；这是刻意保留的边界，因为当前
  shared transport 以 byte array response 为主，不适合大图 streaming 和边读边 10MB 截断。
- C3 全局 circuit breaker 仍未落代码。
- notification MAIL credential lease、task/content/identity internal caller 仍需继续接
  `INTERNAL_SERVICE` 或补 adapter contract。
- shared transport method/body/streaming 能力仍是后续扩展项；在它完成前，不建议把
  multipart、proxy pass-through 或 cover image streaming 强行迁到 shared byte-array client。

## 当前状态

已完成，等待后续文档集成 worker 同步 `docs/index.md`、`tasks/index.md` 和 089 inventory。
