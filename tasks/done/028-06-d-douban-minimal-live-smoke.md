# 028-06-D Douban 最小真实公网 smoke

状态：已完成，结论为 blocked evidence

父任务：

- `tasks/in-progress/028-06-real-provider-live-parity.md`

上游来源：

- `tasks/in-progress/028-06-real-provider-live-parity.md`
- `docs/reports/057-provider-live-queue-02806-tmdb-smoke.md`
- `docs/reports/089-v1-golden-behavior-inventory.md`

## 背景

`028-06-B` 已完成 TMDB live queue `MARK 1/6` 到 `MARK 6/6`，但 Douban/RottenTomatoes
真实 provider 仍未闭环。当前用户已允许真实外网测试，本切片选择 Douban 做最小只读公网
smoke，先确认 dev gate、credential/catalog 边界、provider implementation 是否存在，再执行
单次低频 HTTP 探测。

## 目标

- 检查 Douban provider 的 dev enable gate、worker gate 和 queue listener 状态。
- 检查 credential-service 是否支持 Douban credential template 或 lease。
- 检查 `services/ircs-metadata-worker` 是否存在 Douban `MetadataProvider` implementation。
- 对 Douban 公开只读 endpoint 执行单次低频 HTTP smoke，记录 HTTP 状态、curl 退出码和响应摘要。
- 明确本轮是否写 DB、是否投递队列、是否修改 provider gate，以及测试后 cleanup 状态。

## 非目标

- 不实现 Douban provider。
- 不投递 `q.fetch.metadata.douban`。
- 不开启或常驻 `APP_METADATA_DOUBAN_ENABLED`。
- 不批量抓取 Douban，不绕过反爬，不保存公网响应全文。
- 不修改 R2、SMTP、Rabbit config 或 normalization。

## 上游来源

- `tasks/in-progress/028-06-real-provider-live-parity.md`
- `docs/reports/057-provider-live-queue-02806-tmdb-smoke.md`
- `docs/reports/089-v1-golden-behavior-inventory.md`

## 输入

- `deploy/k8s/dev/metadata-worker-dev.yaml`
- `services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/**`
- `services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/CredentialProviderCatalog.java`
- Douban 只读公网 URL：`https://movie.douban.com/j/subject_suggest?q=The%20Matrix`

## 输出

- `scripts/smoke/metadata-provider-douban-live-02806-smoke.ps1`
- 本 task spec 的执行证据和结论回写。

## 影响范围

```text
scripts/smoke/*douban*02806*.ps1
tasks/in-progress/028-06-d-douban-minimal-live-smoke.md
```

## 依赖

- 本机 PowerShell 与 `curl.exe` 可用。
- 如执行 Kubernetes gate 检查，`kubectl` 可访问 `ircs-dev`；不可用时只记录 local manifest evidence。
- 外网能访问 Douban；如被 403、429、timeout 或 DNS 阻断，应记录真实分类。

## 风险

- Douban 公开 endpoint 可能限流、反爬或返回非稳定结构，smoke 只记录连通性和 HTTP 分类。
- V3 当前可能只有 Douban queue/gate 壳，没有 provider implementation；不能把公网 HTTP 200 伪装成 provider parity success。
- 真实公网结果受网络和站点策略影响，应与代码回归分开判断。

## 验收标准

- smoke 输出 JSON，包含 provider、gate、credential catalog、implementation、HTTP 状态和 no-write/no-queue 结论。
- 只发起一次 Douban HTTP 请求。
- 不修改 Kubernetes env，不写 DB，不投递 queue。
- 若 provider implementation 缺失，明确输出 `PROVIDER_IMPLEMENTATION_MISSING`。

## 测试边界

必跑：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-douban-live-02806-smoke.ps1
```

如改动脚本，先跑 PowerShell parse check。

## 关联文档

- `docs/reports/057-provider-live-queue-02806-tmdb-smoke.md`
- `docs/reports/089-v1-golden-behavior-inventory.md`

## 关联任务

- 父任务：`tasks/in-progress/028-06-real-provider-live-parity.md`
- 本任务：`tasks/in-progress/028-06-d-douban-minimal-live-smoke.md`

## 当前状态

已完成脚本实现、PowerShell parse check 和 Douban 最小真实公网 smoke；本切片结论为
`BLOCKED/PROVIDER_IMPLEMENTATION_MISSING;CREDENTIAL_CATALOG_UNSUPPORTED;DEV_PROVIDER_GATE_DISABLED`。

## 实现结果

- 已新增 `scripts/smoke/metadata-provider-douban-live-02806-smoke.ps1`。
- 脚本只做只读检查：
  - 读取本地 manifest/application gate evidence。
  - 只读查询 `ircs-dev` metadata-worker env。
  - 只读检查 credential-service 对 `DOUBAN` 的 lease 结果，不输出 secret payload。
  - 扫描 metadata-worker provider implementation，确认是否存在 `ProviderType.DOUBAN` 的
    `MetadataProvider` 实现。
  - 对 `https://movie.douban.com/j/subject_suggest?q=The%20Matrix` 发起 1 次公网只读 HTTP 请求。
- 脚本不写 DB、不投递 queue、不 patch Kubernetes env，结束后停止 credential-service port-forward。

## 验证结果

PowerShell parse check 通过：

```powershell
$tokens=$null; $errors=$null; [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path 'scripts\smoke\metadata-provider-douban-live-02806-smoke.ps1'), [ref]$tokens, [ref]$errors)
```

真实 smoke 命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-douban-live-02806-smoke.ps1
```

最终输出摘要：

```text
provider=DOUBAN
status=BLOCKED
reason=PROVIDER_IMPLEMENTATION_MISSING;CREDENTIAL_CATALOG_UNSUPPORTED;DEV_PROVIDER_GATE_DISABLED
externalRequestCount=1
writeDb=false
publishedQueue=false
patchedKubernetesEnv=false
```

外网结果：

```text
url=https://movie.douban.com/j/subject_suggest?q=The%20Matrix
httpStatus=200
contentType=application/json; charset=utf-8
timeTotalSeconds=0.359811
sizeDownloadBytes=269
bodySha256=694fcd3e2bf6508179ac5c33e5227a4b67c1177449ce7fd609e0543a141cad6a
```

gate/catalog/implementation 证据：

```text
local APP_METADATA_DOUBAN_ENABLED=false
ircs-dev APP_METADATA_DOUBAN_ENABLED=false
ircs-dev APP_METADATA_RT_ENABLED=false
ircs-dev APP_METADATA_TMDB_WORKER_ENABLED=true
credential-service DOUBAN lease HTTP 200, leaseCount=0, secretPayloadOmitted=true
credential catalog supportedProviders=MAIL,OPENAI,R2,TMDB
metadata-worker Douban MetadataProvider implementation exists=false
```

cleanup 状态：

```text
runtimeStateChanged=false
credentialPortForwardStoppedByFinally=true
DB/queue writes=none
Kubernetes env patch=none
```

## 遗留问题

- 需要另拆 Douban provider implementation/catalog task，明确是否使用公开页面、官方/非官方 API、
  credential/anti-abuse 策略、失败分类和真实 queue success 标准。
- 当前 Douban 公网只读 endpoint 可达，但这只能证明公网连通性，不能证明 V3 Douban provider parity。
- RT 仍未执行最小真实公网 smoke，保留在 `028-06` 剩余项中。
