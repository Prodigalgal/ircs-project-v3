# 028-14 V1 credential import for live parity

状态：已完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/requirements/028-v1-v3-behavior-parity-closeout.md`
- `docs/reports/078-02814-v1-credential-import-for-live-parity.md`

## 背景

V1 已有经过验证的真实 provider/R2/MAIL 凭据，位于
`D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-config`。V3 当前接口已经完成迁移，
但 `sys_credentials` 在 `ircs-dev` 为空，导致 TMDB、R2 和 SMTP 真实 live parity 无法推进。

## 目标

- 从 V1 `ircs-config/01-secrets.yaml` 读取初始化凭据结构。
- 将 V1 凭据按 V3 provider catalog 规则导入 `ircs-dev.sys_credentials`。
- 保持脚本默认 dry-run，显式 `-Apply` 才写入。
- 不在脚本输出、文档或任务中打印明文 secret。
- 为后续 `028-06` TMDB live queue、`028-07` R2 live、`028-10` SMTP adapter/smoke 提供凭据基础。

## 非目标

- 不立即执行真实 TMDB queue、R2 上传或 SMTP 发送。
- 不长期打开 `APP_METADATA_TMDB_ENABLED`、`APP_STORAGE_R2_ENABLED` 或 `APP_MAIL_ENABLED`。
- 不把凭据明文提交到仓库。
- 不修改 V1 文件。

## 输入

```text
D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-config\01-secrets.yaml
D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-config\02-configmap.yaml
ircs-dev PostgreSQL sys_credentials
```

## 输出

```text
scripts/k8s/import-v1-credentials-to-dev.ps1
docs/reports/078-02814-v1-credential-import-for-live-parity.md
docs/migrations/078-v1-credential-import-for-live-parity.md
```

## 影响范围

```text
scripts/k8s/**
docs/reports/**
docs/migrations/**
tasks/in-progress/**
docs/index.md
tasks/index.md
```

## 依赖

- `kubectl` 可访问 `ircs-dev`。
- `postgres-0` 可用。
- V1 `01-secrets.yaml` 可读。

## 风险

- 写入真实 secret 后，后续 live smoke 可能触发外部限流或成本。
- 脚本若输出 payload 明文会造成凭据泄露。
- 重复导入如果不幂等，会污染 credential pool。

## 验收标准

```text
MARK 1/4 V1 credential source structure is parsed without printing secret values
MARK 2/4 V3 sys_credentials empty state is confirmed before import
MARK 3/4 import script dry-run reports provider counts and planned inserts
MARK 4/4 apply mode imports credentials idempotently and leaves no plaintext in docs
```

## 测试边界

- 脚本 AST/parser 检查。
- dry-run 只读检查。
- apply 后只查询 provider/count/payload key existence，不输出 payload 值。

## 当前状态

已完成并归档。

## 实现结果

- 已新增 `scripts/k8s/import-v1-credentials-to-dev.ps1`。
- 已从 V1 `ircs-config/01-secrets.yaml` 解析 credential source，不输出明文 secret。
- 已将 V1 `GEMINI` 映射为 V3 `OPENAI`，并补充 `base_url=https://ai.mnnu.eu.org/v1`。
- 已将 `MAIL`、`R2`、`TMDB` 导入 V3 `ircs-dev.sys_credentials`。
- 已按 provider fingerprint 规则保持幂等导入。

## 验证结果

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

后续联动验证：

```text
TMDB direct credential/upstream diagnostic: SUCCESS/TMDB_LIVE_SEARCH_OK
TMDB live queue success: MARK 6/6
```
