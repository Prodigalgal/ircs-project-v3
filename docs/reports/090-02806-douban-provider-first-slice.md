# 090 028-06-E Douban provider first slice 报告

状态：已完成

关联任务：

- `tasks/in-progress/028-06-real-provider-live-parity.md`
- `tasks/done/028-06-e-douban-provider-implementation-first-slice.md`

上游来源：

- `tasks/done/028-06-d-douban-minimal-live-smoke.md`
- `docs/reports/089-v1-golden-behavior-inventory.md`

## 背景

`028-06-D` 已用最小只读 live smoke 证明 Douban 公网页面/API 可返回 HTTP 200，但当时 V3
provider parity 仍被三个原因阻塞：

- `PROVIDER_IMPLEMENTATION_MISSING`
- `CREDENTIAL_CATALOG_UNSUPPORTED`
- `DEV_PROVIDER_GATE_DISABLED`

本切片只迁移 Douban provider 的可验证第一刀：注册、可选 credential、低频只读 suggest 请求、
响应解析、错误语义映射和最小字段映射。dev gate 仍默认关闭，避免默认公网流量。

## 目标

- `metadata-worker` 存在 `ProviderType.DOUBAN` provider implementation，并能被 worker 注册。
- credential-service 支持 `DOUBAN` catalog/template/create/lease，不泄露 secret。
- Douban credential 缺失时 provider 仍可使用公开 suggest endpoint 进行一次低频 GET。
- HTTP 200/404/429/403/5xx/网络错误/解析失败具备明确语义。
- 保持配置优先级：运行时注入优先于 DB/default seed，默认值不覆盖真实注入。
- 执行真实外网最小 smoke，记录 URL、状态码、限频方式、解析结果和 no-write 结论。

## 非目标

- 不开启或常驻 `APP_METADATA_DOUBAN_ENABLED=true`。
- 不投递共享 Douban queue 做批量抓取。
- 不绕过 Douban 访问策略或反爬。
- 不写入真实业务数据。
- 不改 Rabbit publisher/listener、storage、notification、portal/admin/BFF。

## 方案

- 在 `services/ircs-metadata-worker` 中提供 `DoubanMetadataProvider`、`DoubanHttpClient`、
  `DoubanCredentialRepository` 和 `DoubanProviderProperties`。
- 使用 `OutboundHttpPolicy.publicFetch` 约束公网 GET，使用 `INTERNAL_SERVICE` policy 访问
  credential-service lease endpoint。
- `DoubanCredentialRepository` 对 credential-service 不可用、空 lease 和异常全部降级为
  `Optional.empty()`，provider 继续走公开 suggest endpoint。
- `CredentialProviderCatalog` 增加 `DOUBAN` 模板、校验、fingerprint 和 lease 支持。
- smoke 脚本只读扫描代码/K8S gate/credential catalog，并对
  `https://movie.douban.com/j/subject_suggest?q=The%20Matrix` 做一次 GET。

## 风险

- Douban suggest endpoint 属于公网非稳定契约，字段可能变化。
- dev gate 默认关闭时，queue live path 不会调用 provider；这是安全边界。
- 无真实 cookie 时，本切片不能证明完整登录态或反爬场景，只证明 public suggest first slice。

## 实现结果

- `services/ircs-metadata-worker` 已补 Douban provider first slice：
  `DoubanMetadataProvider`、`DoubanHttpClient`、`RestClientDoubanHttpClient`、
  `DoubanCredentialRepository`、`DoubanProviderProperties`、`DoubanCredential`。
- `DoubanMetadataProvider` 以 `ProviderType.DOUBAN` 注册，worker provider list 可发现。
- credential 缺失时继续执行公开 suggest endpoint；credential-service 不可用、lease 为空或异常不会阻断 public first slice。
- `services/ircs-credential-service` 已支持 `DOUBAN` catalog/template/create/fingerprint/lease。
- `deploy/k8s/dev/metadata-worker-dev.yaml` 仍保持 `APP_METADATA_DOUBAN_ENABLED=false`。
- 配置优先级由 `MetadataConfigValues` 的 `RuntimeInjectedConfig.find(...) -> repository.findValue(...) -> default`
  保持，focused test 已覆盖 `APP_METADATA_DOUBAN_ENABLED=false` 覆盖 DB `true`。

## 测试证据

通过：

```powershell
.\gradlew.bat :services:ircs-metadata-worker:test :services:ircs-credential-service:test
```

覆盖点：

- provider 缺失：worker 测试中构造无 Douban provider list，输出 `PROVIDER_NOT_FOUND` terminal failure。
- credential 缺失：Douban provider 在 `credentialRepository.findPreferred() == Optional.empty()` 时仍发起 public suggest 查询。
- 外部 200 解析：fake JSON 映射出 `doubanId/title/originalTitle/year/posterUrl/imageSource`。
- 错误映射：HTTP `429 -> RATE_LIMITED`、`5xx -> UPSTREAM_UNAVAILABLE`、`403 -> DOUBAN_FORBIDDEN`、
  `404 -> empty`、outbound private URL/IO -> retryable。
- credential catalog：`DOUBAN` template、create/fingerprint、lease 和 empty payload reject。

## 真实外网 smoke 证据

命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-douban-live-02806-smoke.ps1
```

结果：

```text
status=SUCCESS_WITH_DEV_GATE_DISABLED
reason=DEV_PROVIDER_GATE_DISABLED
externalRequestCount=1
writeDb=false
publishedQueue=false
patchedKubernetesEnv=false
credentialLease HTTP 200, leaseCount=0, secretPayloadOmitted=true
```

Douban 只读请求：

```text
URL=https://movie.douban.com/j/subject_suggest?q=The%20Matrix
HTTP=200
contentType=application/json; charset=utf-8
timeTotalSeconds=0.298122
sizeDownloadBytes=269
requestCount=1
rateLimitMode=single readonly GET; no queue; no loop; no retry storm
```

解析结果：

```text
candidateCount=1
firstId=1291843
firstTitle=黑客帝国
firstSubTitle=The Matrix
firstYear=1999
fullBodyOmitted=true
```

阻塞变化：

```text
PROVIDER_IMPLEMENTATION_MISSING -> 已解除
CREDENTIAL_CATALOG_UNSUPPORTED -> 已解除
DEV_PROVIDER_GATE_DISABLED -> 保留为预期 dev 安全 gate
```

## MARK

```text
MARK 1/6 provider implementation registered for DOUBAN
MARK 2/6 credential catalog supports DOUBAN template/create/fingerprint/lease
MARK 3/6 dev gate remains default disabled and runtime env keeps priority over DB/default seed
MARK 4/6 request/response/error mapping covered by focused tests
MARK 5/6 real Douban readonly smoke returns HTTP 200 and parses one candidate
MARK 6/6 no write/no queue/no K8S env mutation/no runtime residue
```

## 剩余清单

- Douban queue live success：需要另拆显式启用 gate 的 low-resource smoke，验证 provider result、
  raw metadata、search sync 和 cleanup。
- Douban credential/cookie 场景：当前 dev lease 为 `leaseCount=0`，只证明 public suggest first slice。
- Rotten Tomatoes provider：仍未实现，不在本切片范围。
