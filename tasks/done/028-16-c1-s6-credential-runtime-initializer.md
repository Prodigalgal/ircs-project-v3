# 028-16-C1-S6 credential-service runtime initializer task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c1-initialization-readiness-gate.md`
- `tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- V1 `CredentialInitializer`
- V1 `CredentialHandler`
- V3 `services/ircs-credential-service`

## 背景

V1 `CredentialInitializer` 在初始化阶段从 ENV 导入 `OPENAI`、`TMDB`、`MAIL`、`R2`
凭据，再从 `/etc/ircs/init/credentials.json` 或 classpath `presets/credentials.json`
批量导入凭据，并按 handler fingerprint 去重。V3 credential-service 已有
`sys_credentials` schema、CRUD、provider template、fingerprint 和 internal lease，
但缺等价 runtime initializer。

用户已明确真实凭据应从 V1/运行环境获取；本切片不得把真实 secret 写入仓库、文档、
日志或测试 fixture。

## 目标

- 复刻 V1 env credential import：`SPRING_AI_OPENAI_API_KEY`、`SPRING_AI_OPENAI_BASE_URL`、
  `APP_METADATA_TMDB_API_KEY`、`APP_MAIL_USERNAME`、`APP_MAIL_PASSWORD`、
  `APP_STORAGE_R2_ACCOUNT_ID`、`APP_STORAGE_R2_ACCESS_KEY`、`APP_STORAGE_R2_SECRET_KEY`。
- 复刻 V1 file import：默认读取 `/etc/ircs/init/credentials.json`，并允许用配置覆盖路径；
  classpath `presets/credentials.json` 只作为可选兜底，不提交真实 preset。
- 复用 V3 `CredentialService`/`CredentialProviderCatalog` 的校验、fingerprint 和默认字段规则。
- 通过 fingerprint 幂等补缺，已有相同凭据时跳过，不覆盖用户编辑值。
- 新增关闭开关与 dev manifest env，默认启用但没有 ENV/文件时不写库。
- 用本地测试覆盖 env 导入、占位符跳过、file 导入、重复 fingerprint 跳过、关闭开关和不泄露 payload。

## 非目标

- 不提交 V1 classpath `presets/credentials.json` 的真实内容。
- 不修复 public credential summary payload 脱敏差异；该安全差异另拆任务。
- 不执行真实 provider/TMDB/MAIL/R2 live smoke。
- 不新增 Liquibase seed 或把 secret 写入 DB migration。
- 不实现全局 readiness orchestrator 自动放行。

## 影响范围

```text
services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/**
services/ircs-credential-service/src/test/java/com/prodigalgal/ircs/credential/**
services/ircs-credential-service/src/main/resources/application.yaml
deploy/k8s/dev/credential-service-dev.yaml
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/in-progress/028-16-c1-initialization-readiness-gate.md
tasks/index.md
docs/index.md
```

## 验收标准

```text
MARK 1/6 V1 CredentialInitializer env/file facts documented in this task: done 2026-06-09
MARK 2/6 credential-service imports V1 env credentials with matching names/default fields: done 2026-06-09
MARK 3/6 file importer reads configured/default JSON and skips unsupported/invalid entries safely: done 2026-06-09
MARK 4/6 create-if-absent is fingerprint idempotent and does not overwrite existing credentials: done 2026-06-09
MARK 5/6 disabled gate/no-env/no-file paths perform no writes and logs do not include payload values: done 2026-06-09
MARK 6/6 credential-service tests and K8S dry-run pass, docs/tasks updated: done 2026-06-09
```

## 测试边界

```text
.\gradlew.bat --no-daemon :services:ircs-credential-service:test --rerun-tasks
kubectl apply --dry-run=server -f deploy\k8s\dev\credential-service-dev.yaml
```

## 当前状态

2026-06-09：已完成第一刀实现并归档。

V1 `CredentialInitializer` 事实：

- `getOrder() = 5`。
- 启动时先导入 ENV，再导入文件。
- ENV 来源：
  - `SPRING_AI_OPENAI_API_KEY`，可选 `SPRING_AI_OPENAI_BASE_URL`，默认 base URL 为
    `https://ai.mnnu.eu.org/v1`；name `Env: OpenAI Primary`，priority `100`，
    rate `15/MINUTE`。
  - `APP_METADATA_TMDB_API_KEY`；name `Env: TMDB Primary`，priority `100`，
    rate `40/MINUTE`。
  - `APP_MAIL_USERNAME` + `APP_MAIL_PASSWORD`；name `Env: Mail Primary`，
    priority `100`，dayLimit `500`，`rateLimit=0` 不落 rate limit。
  - `APP_STORAGE_R2_ACCOUNT_ID` + `APP_STORAGE_R2_ACCESS_KEY` +
    `APP_STORAGE_R2_SECRET_KEY`；name `Env: R2 Primary`，priority `100`，
    无 rate limit。
- 文件来源：优先 `/etc/ircs/init/credentials.json`，其次 classpath
  `presets/credentials.json`。
- 文件 DTO 空 payload 跳过；name 为空时使用 `{PROVIDER}-Init-{index}`。
- 幂等规则：按 provider handler fingerprint SHA-256 去重，已存在时跳过，不覆盖用户编辑值。
- 占位符 `YOUR_TMDB_API_KEY_HERE`、`AIzaSy...` 和 blank ENV 跳过。

V3 实现结果：

- credential-service 新增 `RuntimeCredentialInitializer`，`@Order(5)`，默认启用。
- 新增配置：
  - `APP_CREDENTIAL_INITIALIZER_ENABLED`
  - `APP_CREDENTIAL_INITIALIZER_FILE_PATH`
  - `APP_CREDENTIAL_INITIALIZER_CLASSPATH_FALLBACK_ENABLED`
- `CredentialService.createIfAbsent()` 复用既有 `CredentialProviderCatalog` 和 fingerprint 规则，
  重复 fingerprint 返回 false，不调用 repository create。
- initializer 支持 V1 env 四类 provider、配置文件导入、classpath fallback，并对异常 entry
  只记录 provider/name 和错误消息，不记录 payload 值。
- dev manifest 显式启用 initializer 并保留默认 `/etc/ircs/init/credentials.json` 路径；
  未把真实 secret 写入 YAML。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-credential-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\credential-service-dev.yaml
deployment.apps/ircs-credential-service configured (server dry run)
service/ircs-credential-service unchanged (server dry run)
```

后续未完成项：

- 不包含真实 provider/TMDB/MAIL/R2 live smoke。
- 不修复 public credential summary payload 回显差异；该安全问题需单独开刀。
- 不提交 V1 classpath preset 的真实 secret。
- 不包含全局 readiness orchestrator 自动放行。
