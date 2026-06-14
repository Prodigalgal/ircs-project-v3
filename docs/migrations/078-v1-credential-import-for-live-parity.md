# 078 V1 credential import for live parity 迁移记录

状态：已完成

更新时间：2026-06-08

## 背景

`028-06`、`028-07` 和 `028-10` 的真实 provider/R2/SMTP live parity 需要 dev 凭据池。
用户确认真实凭据从 V1 `ircs-config` 获取。当前 V3 `ircs-dev.sys_credentials` 为 `0` 行，
因此先补一个安全、幂等、默认 dry-run 的 V1 凭据导入脚本。

## 变更范围

```text
scripts/k8s/import-v1-credentials-to-dev.ps1
docs/reports/078-02814-v1-credential-import-for-live-parity.md
docs/migrations/078-v1-credential-import-for-live-parity.md
tasks/done/028-14-v1-credential-import-for-live-parity.md
```

## 迁移规则

```text
V1 GEMINI -> V3 OPENAI，payload 增加 base_url=https://ai.mnnu.eu.org/v1
V1 TMDB   -> V3 TMDB
V1 R2     -> V3 R2
V1 MAIL   -> V3 MAIL
```

fingerprint 规则与 V3 服务保持一致：

```text
OPENAI: base_url + "\n" + api_key
TMDB:   api_key
R2:     account_id + "|" + access_key + "|" + secret_key
MAIL:   username + "|" + password
```

## 验收

已执行：

```powershell
.\scripts\k8s\import-v1-credentials-to-dev.ps1
.\scripts\k8s\import-v1-credentials-to-dev.ps1 -Apply
```

脚本输出不得包含明文 secret。

验证结果：

```text
MARK 1/4 V1 credential source structure is parsed without printing secret values
MARK 2/4 V3 sys_credentials empty state is confirmed before import
MARK 3/4 import script dry-run reports provider counts and planned inserts
MARK 4/4 apply mode imports credentials idempotently and leaves no plaintext in docs
MAIL/OPENAI/R2/TMDB imported and enabled
TMDB live queue success: MARK 6/6
```
