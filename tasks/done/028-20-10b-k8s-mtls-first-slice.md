# 028-20-10B K8S mTLS rollout first slice

状态：done

父任务：`tasks/in-progress/028-20-post-parity-runtime-platform-breakdown.md`

来源：`docs/decisions/005-post-parity-runtime-platform-decisions.md` D15

## 目标

- 在 `ircs-dev` 中补齐真实 mTLS rollout 的 K8S 证书与注入策略。
- 使用 cert-manager 生成 dev CA、server/client certificates、PKCS12 keystore/truststore。
- 首批覆盖已完成应用层 service identity 的 internal 链路：
  - credential-service server：metadata-worker、notification-worker、normalization-worker、admin-bff 调用。
  - search-service server：ops-service、portal-bff、aggregation-worker 调用。
- 默认不直接切换 live Deployment；真实切换必须通过脚本显式 `-Execute`。

## 范围

- `deploy/k8s/dev/mtls-certificates.yaml`
- `scripts/k8s/apply-dev-mtls-first-slice.ps1`
- `tasks/in-progress/028-20-post-parity-runtime-platform-breakdown.md`
- `tasks/index.md`

## 非目标

- 不一次性给所有服务强制启用 mTLS。
- 不引入 service mesh。
- 不在本切片修改公网/edge 路由。
- 不把 mTLS 替代 service token、service identity 和 scopes；这些仍作为应用层授权。

## 设计

- cert-manager `SelfSigned` Issuer 生成 dev root CA，再由 CA Issuer 签发各服务证书。
- 每个 covered workload 拥有独立 Secret：`<deployment>-mtls`。
- 每个证书同时生成 `keystore.p12` 和 `truststore.p12`。
- server 端使用 Spring Boot `SERVER_SSL_*` 启用 HTTPS 与 `SERVER_SSL_CLIENT_AUTH=need`。
- client 端使用 `JAVA_TOOL_OPTIONS` 设置 JVM 默认 keyStore/trustStore。
- HTTP base URL 从 `http://...:8080` 切换到 `https://...:8080`。

## 验收标准

- manifest 可被 kubectl client dry-run 接受，或至少脚本 dry-run 生成明确 patch plan。
- covered workloads 均有证书 Secret 定义。
- credential/search server patch 包含 server SSL、client-auth need、TCP probe。
- metadata/notification/normalization/admin-bff/ops/portal-bff/aggregation patch 包含 mTLS volume、JVM SSL system properties 和 HTTPS base URL。
- 不改变资源 request/limit。
- 文档记录真实 rollout 后置风险。

## 测试方式

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\k8s\apply-dev-mtls-first-slice.ps1
kubectl apply --dry-run=client -f deploy\k8s\dev\mtls-certificates.yaml
```

结果：以上命令均通过。

## 完成内容

- 新增 cert-manager dev CA / Issuer / per-workload Certificate manifest。
- 每个 covered workload 证书均开启 PKCS12 keystore/truststore 生成。
- 新增默认 dry-run 的 mTLS rollout 脚本，真实切换必须显式 `-Execute`。
- rollout 脚本覆盖：
  - credential-service/search-service server SSL、client-auth need、TCP probes。
  - metadata-worker/notification-worker/normalization-worker/admin-bff/ops-service/portal-bff/aggregation-worker client cert mount。
  - JVM 默认 keyStore/trustStore 注入。
  - credential/search internal base URL 切换到 HTTPS。
- dev `metadata-worker` manifest 的 credential-service token secret key 从旧 `INTERNAL_CREDENTIAL_TOKEN` 切到 `SERVICE_CREDENTIAL_TOKEN`。
- provider/storage/notification live smoke 脚本优先读取 `SERVICE_CREDENTIAL_TOKEN`，仅在历史集群缺失新 key 时 fallback 到 `INTERNAL_CREDENTIAL_TOKEN`。
- notification SMTP one-shot runtime patch 改为注入 `APP_MAIL_CREDENTIAL_SERVICE_TOKEN` + `SERVICE_CREDENTIAL_TOKEN`，不再注入旧 `APP_CREDENTIAL_INTERNAL_TOKEN`。

## 后续补充验证

```powershell
rg -n "INTERNAL_CREDENTIAL_TOKEN|APP_CREDENTIAL_INTERNAL_TOKEN|SERVICE_CREDENTIAL_TOKEN|APP_MAIL_CREDENTIAL_SERVICE_TOKEN|APP_METADATA_CREDENTIAL_SERVICE_TOKEN" scripts\smoke

$files = @(
  'scripts\smoke\metadata-provider-douban-live-02806-smoke.ps1',
  'scripts\smoke\metadata-provider-rt-live-02806-smoke.ps1',
  'scripts\smoke\metadata-provider-live-parity-02806-smoke.ps1',
  'scripts\smoke\metadata-provider-live-queue-02806-smoke.ps1',
  'scripts\smoke\metadata-provider-rt-live-queue-02806-smoke.ps1',
  'scripts\smoke\metadata-provider-douban-live-queue-02806-smoke.ps1',
  'scripts\smoke\notification-mail-smtp-02810f-preflight.ps1',
  'scripts\smoke\storage-avatar-r2-live-02807-smoke.ps1',
  'scripts\smoke\storage-r2-watchdog-02807j-smoke.ps1',
  'scripts\smoke\storage-service-r2-path-02807-smoke.ps1',
  'scripts\smoke\storage-r2-live-object-02807-smoke.ps1'
)
foreach ($file in $files) {
  $tokens = $null
  $errors = $null
  [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path $file), [ref]$tokens, [ref]$errors) | Out-Null
  if ($errors.Count -gt 0) { throw "$file parse failed" }
}
```

结果：11 个 smoke 脚本 PowerShell parser 通过；旧 credential token 仅作为 fallback 保留。

## 遗留边界

- 本切片先产出可执行 rollout 入口；真实 `-Execute`、search-service token gate 和 mTLS smoke 已由 `028-20-25` 完成。
- normalization LLM credential resolver 已在 `028-20-11D` 迁移到 shared outbound client；基础 credential-service mTLS live smoke 已由 `028-20-25` 覆盖。
- search-service internal token 在 dev 已强制 `require-token=true`，`SERVICE_SEARCH_TOKEN` 由 K8S Secret 注入，不写入 repo。
