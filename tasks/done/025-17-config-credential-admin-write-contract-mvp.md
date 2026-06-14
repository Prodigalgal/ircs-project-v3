# 025-17 config/credential admin write contract MVP 子任务

状态：已完成

父任务：

- `tasks/in-progress/025-v1-route-coverage-closeout-parent.md`

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/039-parallel-subagent-route-slices-02515-02517.md`

## 背景

`ircs-config-service` 当前只覆盖 configs read 和 common timezones；`ircs-credential-service`
当前只覆盖 credentials list/detail 与 internal lease。V1 admin 设置页仍依赖 configs 写入、
connection test、credential templates、credential create/update/delete/refresh。

## 目标

- 在 `ircs-config-service` 复现 `/api/v1/configs` create/update/delete/test。
- 在 `ircs-credential-service` 复现 `/api/v1/credentials` templates/create/update/delete/refresh。
- 保持 V1 admin 前端字段形状。
- 复用共享 PostgreSQL 迁移桥和现有低资源 profile。

## 非目标

- 不修改 content/catalog 服务。
- 不修改 `admin-bff` 路由。
- 不新增 K8S 资源。
- connection test 不访问生产外部资源；测试使用可控参数和降级响应。

## 输入

```text
V1 SystemConfigController / SystemConfigService / ConnectivityService
V1 CredentialController / CredentialRepository / CredentialHandler / KeyPoolManager
services/ircs-config-service
services/ircs-credential-service
```

## 输出

- config-service write/test Controller/Service/Repository 增量。
- credential-service templates/write/refresh Controller/Service/Repository 增量。
- 对应单元测试。
- worker 完成结果和 smoke 建议。

## 影响范围

允许写入：

```text
services/ircs-config-service/src/main/java/com/prodigalgal/ircs/config
services/ircs-config-service/src/test/java/com/prodigalgal/ircs/config
services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential
services/ircs-credential-service/src/test/java/com/prodigalgal/ircs/credential
tasks/done/025-17-config-credential-admin-write-contract-mvp.md
```

禁止写入：

```text
services/ircs-content-service
services/ircs-catalog-service
services/ircs-admin-bff
deploy/k8s
```

## 验收标准

- credential templates/create/update/delete/refresh 共 5 条接口可用。
- configs create/update/delete/test 共 4 条接口可用。
- 单元测试通过。
- 后续主 agent 通过 `admin-bff` live smoke 完成 `9/9` 标记。

## 测试边界

worker 计划执行：

```text
.\gradlew.bat :services:ircs-config-service:test :services:ircs-credential-service:test
```

主 agent 后续执行全仓 build、ARM64 rollout 和 live smoke。

## Worker 实施结果

更新时间：2026-06-07

状态：已完成并通过主 agent live smoke。

已完成范围：

- `ircs-config-service` 增加 configs create/update/delete 与 connection test contract。
- `ircs-config-service` connection test 使用 `dry-run` 降级响应，不访问 R2、SMTP、LLM 或公网代理。
- `ircs-credential-service` 增加 credential templates/create/update/delete/refresh contract。
- `ircs-credential-service` templates 覆盖 `OPENAI`、`TMDB`、`MAIL`、`R2` 的 V1 admin 表单字段形状。
- `ircs-credential-service` create/update 写入 `sys_credentials`，保留 V1 DTO 主要字段，并基于 provider payload 计算 SHA-256 fingerprint 做重复检查。
- `CredentialSummary` 补充 `payload` 与 usage 默认字段，便于 admin 前端按 V1 字段形状接收 create/update 响应。

未进入范围：

- 未修改 `services/ircs-admin-bff`。
- 未修改 `services/ircs-catalog-service`、`services/ircs-content-service`。
- 未新增 Pod、Service、HTTPRoute、PVC 或中间件。
- 未修改 `deploy/k8s`、`docs/index.md`、`tasks/index.md` 或 K8S 快照。

## Worker 验证结果

已通过：

```text
.\gradlew.bat :services:ircs-config-service:test :services:ircs-credential-service:test
BUILD SUCCESSFUL in 8s
14 actionable tasks: 6 executed, 8 up-to-date
```

本地单元测试覆盖：

- config list/get/create/update/delete/test controller contract。
- config create duplicate、update key mismatch、dry-run connection service。
- credential list/get/templates/create/update/delete/refresh controller contract。
- credential templates、create fingerprint、duplicate create、update、lease 和 sanitizer payload shape。

## 主 agent 验证结果

已执行：

```text
.\gradlew.bat :services:ircs-config-service:test :services:ircs-credential-service:test -> PASS
.\gradlew.bat build -> PASS
```

K8S rollout：

```text
deployment/ircs-config-service successfully rolled out
deployment/ircs-credential-service successfully rolled out
config imageID: harbor.mnnu.eu.org/ircs/ircs-config-service@sha256:3ce1e6be62dd11a6912a01715b007699b3f02ff8445c2383464545f7d2c9ddac
credential imageID: harbor.mnnu.eu.org/ircs/ircs-credential-service@sha256:c1eaa572403be48308155a4edc8dcfca219b8008fb6f6f228e6d5352828d5b60
```

新增 smoke 脚本：

```text
scripts/smoke/admin-bff-02517-smoke.ps1
```

`admin-bff` live smoke 已验接口：

```text
MARK 1/9 GET /api/v1/credentials/templates?provider=TMDB
MARK 2/9 POST /api/v1/credentials
MARK 3/9 PUT /api/v1/credentials/{id}
MARK 4/9 DELETE /api/v1/credentials/{id}
MARK 5/9 POST /api/v1/credentials/refresh
MARK 6/9 POST /api/v1/configs
MARK 7/9 PUT /api/v1/configs/{key}
MARK 8/9 DELETE /api/v1/configs/{key}
MARK 9/9 POST /api/v1/configs/test/{type}
```

清理结果：

```text
sys_credentials=0
system_configs=0
```
