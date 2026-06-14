# 028-06-E Douban provider implementation first slice

状态：已完成

父任务：

- `tasks/in-progress/028-06-real-provider-live-parity.md`

上游来源：

- `tasks/done/028-06-d-douban-minimal-live-smoke.md`
- `tasks/in-progress/028-06-real-provider-live-parity.md`
- `docs/reports/089-v1-golden-behavior-inventory.md`

## 背景

`028-06-D` 已证明 Douban 只读公网 endpoint 对最小 smoke 可返回 HTTP 200，但 V3 仍因
`PROVIDER_IMPLEMENTATION_MISSING`、`CREDENTIAL_CATALOG_UNSUPPORTED` 和
`DEV_PROVIDER_GATE_DISABLED` 无法进入真实 provider parity。本切片先补最小 Douban provider
implementation 和可选 credential catalog 支持，保留 dev provider gate 默认关闭。

## 目标

- 在 `services/ircs-metadata-worker` 增加最小 Douban `MetadataProvider` 实现，支持单标题低频查询。
- provider 仅执行单次公开 suggest 查询，不批量抓取，不绕过反爬，不保存公网响应全文。
- 保留 `APP_METADATA_DOUBAN_ENABLED=false` 的 dev 默认 gate；未显式开启时不调用 Douban。
- 如 credential-service catalog 仍不支持 `DOUBAN`，补充模板、校验、fingerprint 和 lease 测试。
- fake/no-real-network tests 覆盖 status/error mapping、empty result 和 credential absent。
- 在允许真实外网测试前提下执行最小 live smoke；若仍因 dev gate 或 credential 缺失阻塞，记录 precise evidence。

## 非目标

- 不投递共享 provider queue 进行批量抓取。
- 不开启或常驻 `APP_METADATA_DOUBAN_ENABLED`。
- 不修改 Rabbit config、R2、SMTP/migrator 或 normalization。
- 不实现 Rotten Tomatoes provider。
- 不把公网 HTTP 200 伪装成完整 provider parity success。

## 输入

- `services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/**`
- `services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/CredentialProviderCatalog.java`
- `scripts/smoke/metadata-provider-douban-live-02806-smoke.ps1`
- `deploy/k8s/dev/metadata-worker-dev.yaml`

## 输出

- Douban provider/adapter 与对应 fake tests。
- `DOUBAN` credential catalog/lease 支持与测试。
- 新增或更新最小 Douban provider smoke 脚本。
- 本 task spec 的实现、验证、live smoke 和遗留风险回写。

## 影响范围

```text
services/ircs-metadata-worker/** Douban provider/adapter/tests
services/ircs-credential-service/** provider catalog/tests
scripts/smoke/metadata-provider-douban*
tasks/done/028-06-e-douban-provider-implementation-first-slice.md
```

仅在必要时修改：

```text
deploy/k8s/dev/metadata-worker-dev.yaml
```

明确不包含：

```text
docs/index.md
tasks/index.md
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
Rabbit config
R2
SMTP/migrator
normalization
```

## 依赖

- 本地 Gradle/JDK 可运行 metadata-worker 与 credential-service 测试。
- 真实 smoke 依赖外网可访问 Douban。
- 如果 live queue 路径需要 credential lease，credential-service 必须有 `DOUBAN` catalog 支持；本切片不会输出 secret。

## 风险

- Douban 公开 suggest endpoint 结构和访问策略可能变化，provider 只能作为低频第一刀。
- dev gate 默认关闭时，真实 worker queue 不会调用 provider；这是安全边界，不视为代码失败。
- 无真实 credential 或 cookie 时，只能证明 catalog/lease 支持和公开 endpoint 可达，不能证明完整反爬场景。

## 验收标准

- metadata-worker tests 通过，覆盖 Douban provider status/error mapping、empty result、credential absent。
- credential-service tests 通过，覆盖 `DOUBAN` template/validate/fingerprint/lease 不泄露 secret 的最小契约。
- 最小 live smoke 输出 provider implementation exists、credential catalog supported、dev gate 状态、Douban HTTP 分类和 no-write/no-queue 结论。
- 不留下队列、DB、K8S env 或 port-forward 残留；如无法执行 cleanup，明确记录 evidence。

## 测试边界

必跑：

```powershell
.\gradlew.bat :services:ircs-metadata-worker:test :services:ircs-credential-service:test
```

必跑 live smoke：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-douban-live-02806-smoke.ps1
```

如新增脚本，先跑 PowerShell parse check。

## 关联文档

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `docs/reports/090-02806-douban-provider-first-slice.md`
- `docs/migrations/090-douban-provider-first-slice.md`

## 关联任务

- 父任务：`tasks/in-progress/028-06-real-provider-live-parity.md`
- 上游 blocked evidence：`tasks/done/028-06-d-douban-minimal-live-smoke.md`
- 本任务：`tasks/done/028-06-e-douban-provider-implementation-first-slice.md`

## 当前状态

已完成 first slice，`PROVIDER_IMPLEMENTATION_MISSING` 与 `CREDENTIAL_CATALOG_UNSUPPORTED`
已解除；`DEV_PROVIDER_GATE_DISABLED` 保持为预期安全 gate。

## 实现结果

- `metadata-worker` 已有 `DoubanMetadataProvider`，作为 `ProviderType.DOUBAN`
  的 `MetadataProvider` Spring component 被 worker provider list 注册。
- `DoubanMetadataProvider` 使用 `DoubanCredentialRepository` 获取可选 credential；credential-service
  不可用、lease 为空或异常时降级为无 credential 公开 suggest 查询。
- `RestClientDoubanHttpClient` 使用 `OutboundHttpPolicy.publicFetch` 进行公网 GET，支持默认
  User-Agent、credential User-Agent 和 Cookie header。
- Douban HTTP 语义：`2xx` 返回 JSON body，空 body 视为空结果；`404` 为空结果；
  `429` 为 `RATE_LIMITED` retryable；`5xx` 为 `UPSTREAM_UNAVAILABLE` retryable；
  `403` 为 `DOUBAN_FORBIDDEN` terminal；outbound/IO/interrupt 为 retryable；
  JSON parse failure 为 `PROVIDER_PARSE_ERROR` retryable。
- 最小字段映射：`id -> doubanId`、`title -> title`、`sub_title -> originalTitle`、
  `year -> year`、`img -> posterUrl`，并设置 `imageSource=DOUBAN`。
- credential-service 已支持 `DOUBAN` template、validate、fingerprint、create 和 lease。
- dev K8S 仍保持 `APP_METADATA_DOUBAN_ENABLED=false`，未常驻打开 provider gate。

## 验证结果

通过：

```powershell
.\gradlew.bat :services:ircs-metadata-worker:test :services:ircs-credential-service:test
```

覆盖点：

- Douban provider 无 credential 公开查询。
- Douban suggest 结果不匹配时返回 empty。
- optional credential header 传递。
- blank title unsupported。
- HTTP 200/404/429/403/5xx/outbound private URL 保护与错误映射。
- Douban credential lease 可选、credential-service unavailable 降级。
- worker provider disabled、provider missing terminal failure。
- credential-service `DOUBAN` template/create/fingerprint/lease/empty payload reject。
- `APP_METADATA_DOUBAN_ENABLED=false` 运行时注入覆盖 DB `true`，不被默认 seed 覆盖。

## 真实外网 smoke

通过：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-douban-live-02806-smoke.ps1
```

结果摘要：

```text
status=SUCCESS_WITH_DEV_GATE_DISABLED
reason=DEV_PROVIDER_GATE_DISABLED
externalRequestCount=1
writeDb=false
publishedQueue=false
patchedKubernetesEnv=false
credentialLease HTTP 200, leaseCount=0, secretPayloadOmitted=true
Douban URL=https://movie.douban.com/j/subject_suggest?q=The%20Matrix
Douban HTTP 200, contentType=application/json; charset=utf-8
parsed candidateCount=1, firstId=1291843, firstTitle=黑客帝国, firstSubTitle=The Matrix, firstYear=1999
cleanup runtimeStateChanged=false, credentialPortForwardStoppedByFinally=true
```

说明：

- `PROVIDER_IMPLEMENTATION_MISSING` 已不再出现。
- `CREDENTIAL_CATALOG_UNSUPPORTED` 已不再出现。
- `DEV_PROVIDER_GATE_DISABLED` 是预期 dev 安全 gate；本切片不临时 patch K8S env、不投递 queue、不写 DB。

## MARK

```text
MARK 1/6 provider implementation registered for DOUBAN
MARK 2/6 credential catalog supports DOUBAN template/create/fingerprint/lease
MARK 3/6 dev gate remains default disabled and runtime env keeps priority over DB/default seed
MARK 4/6 request/response/error mapping covered by focused tests
MARK 5/6 real Douban readonly smoke returns HTTP 200 and parses one candidate
MARK 6/6 no write/no queue/no K8S env mutation/no runtime residue
```

## 遗留项

- Douban full provider parity 仍需另拆：真实 queue 启用路径、provider result 聚合、raw metadata
  写入、search sync 副作用和 cleanup 验证。
- 无真实 Douban cookie，本切片不证明登录态或反爬场景。
- Rotten Tomatoes provider 仍未实现，保持后续独立切片。
