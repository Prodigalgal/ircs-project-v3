# 078 028-14 V1 真实凭据导入 V3 dev 凭据池报告

状态：已完成

更新时间：2026-06-08

## 背景

`028` 行为级迁移当前 HTTP Controller / BFF missing 已为 `0`，但真实 provider、R2 和
SMTP live parity 仍缺外部凭据证据。用户确认真实凭据应从 V1
`D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-config` 获取。

本轮只处理凭据进入 V3 dev 凭据池的安全导入边界，不在文档中记录任何明文 secret。

## 当前证据

V1 `ircs-config/01-secrets.yaml` 中 `ircs-init-credentials.credentials.json` 的结构统计：

```text
GEMINI: 1, payload keys=api_key
MAIL:   1, payload keys=password,username
R2:     1, payload keys=access_key,account_id,secret_key
TMDB:   8, payload keys=api_key
```

V1 `ircs-config/02-configmap.yaml` 中包含普通环境配置：

```text
R2_BUCKET_NAME
R2_PUBLIC_DOMAIN
MAIL_HOST
MAIL_PORT
MAIL_FROM
```

导入前，V3 `ircs-dev.sys_credentials` 为空：

```text
select count(*) from sys_credentials;
0
```

因此 `028-06` 的 `SKIPPED/TMDB_CREDENTIAL_WITHOUT_API_KEY` 不能视为 V1 缺少凭据，而是
V1 初始化凭据尚未进入 V3 dev `sys_credentials` 凭据池。

## 方案

新增本地脚本 `scripts/k8s/import-v1-credentials-to-dev.ps1`：

- 默认 dry-run，只输出 provider 数量、字段集合和待导入条目数量。
- 只有显式传入 `-Apply` 才写入 `ircs-dev` PostgreSQL。
- 使用与 V3 `CredentialService` 一致的 provider 规范：
  - V1 `GEMINI` 导入为 V3 `OPENAI`。
  - `OPENAI` payload 自动补 `base_url=https://ai.mnnu.eu.org/v1`。
  - `TMDB`、`R2`、`MAIL` 保留 provider 名。
- 使用 SHA-256 fingerprint，与 `CredentialProviderCatalog.fingerprintSource(...)`
  语义对齐，避免重复导入。
- 写入 `sys_credentials` 时不打印 payload 明文。

## 范围

包含：

```text
scripts/k8s/import-v1-credentials-to-dev.ps1
docs/reports/078-02814-v1-credential-import-for-live-parity.md
docs/migrations/078-v1-credential-import-for-live-parity.md
tasks/done/028-14-v1-credential-import-for-live-parity.md
```

不包含：

```text
TMDB live queue success smoke
R2 live object upload smoke
真实 SMTP 发送 smoke
长期开启 provider/R2/SMTP 高风险开关
把任何 secret 明文写入仓库或报告
```

## 风险

- `sys_credentials` 写入真实 secret，脚本输出必须保持脱敏。
- 重复导入会污染 credential pool，因此必须通过 fingerprint 做幂等判断。
- R2/SMTP live smoke 会触发外部资源，导入凭据不等于立即执行外呼。

## 验收标准

```text
MARK 1/4 V1 credential source structure is parsed without printing secret values
MARK 2/4 V3 sys_credentials empty state is confirmed before import
MARK 3/4 import script dry-run reports provider counts and planned inserts
MARK 4/4 apply mode imports credentials idempotently and leaves no plaintext in docs
```

## 实现与验证结果

已新增并执行 `scripts/k8s/import-v1-credentials-to-dev.ps1`：

```text
MARK 1/4 V1 credential source structure is parsed without printing secret values
MARK 2/4 V3 sys_credentials empty state is confirmed before import
MARK 3/4 import script dry-run reports provider counts and planned inserts
MARK 4/4 apply mode imports credentials idempotently and leaves no plaintext in docs
```

导入后 provider 汇总：

```text
MAIL   total=1 enabled=1
OPENAI total=1 enabled=1
R2     total=1 enabled=1
TMDB   total=8 enabled=8
```

payload key existence 验证：

```text
MAIL complete: 1
R2 complete:   1
TMDB api_key:  8
```

后续复验：

```text
TMDB direct credential/upstream diagnostic: SUCCESS/TMDB_LIVE_SEARCH_OK
TMDB live queue success: MARK 6/6
```

本报告不记录任何明文 secret。
