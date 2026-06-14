# 039 config/credential admin write contract extraction

状态：已完成

更新时间：2026-06-07

关联任务：

- `tasks/done/025-17-config-credential-admin-write-contract-mvp.md`

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/039-parallel-subagent-route-slices-02515-02517.md`

## 背景

V1 admin 设置页依赖系统配置写入、连接测试、credential templates 与 credential
create/update/delete/refresh。`025-17` 将这些接口拆入 `ircs-config-service` 和
`ircs-credential-service`，并通过 `admin-bff` 完成 live smoke。

## 迁移范围

```text
GET    /api/v1/credentials/templates?provider=TMDB
POST   /api/v1/credentials
PUT    /api/v1/credentials/{id}
DELETE /api/v1/credentials/{id}
POST   /api/v1/credentials/refresh
POST   /api/v1/configs
PUT    /api/v1/configs/{key}
DELETE /api/v1/configs/{key}
POST   /api/v1/configs/test/{type}
```

## 实现结果

- `ircs-config-service` 增加 configs create/update/delete 与 dry-run connection test。
- `ircs-config-service` 继续对敏感 config key 做响应脱敏，非敏感 smoke key 返回真实 value。
- `ircs-credential-service` 增加 provider templates、create/update/delete/refresh。
- credential templates 覆盖 `OPENAI`、`TMDB`、`MAIL`、`R2` 的 admin 表单字段形状。
- credential payload 写入 `sys_credentials`，使用 provider payload fingerprint 做重复检查。
- connection test 不访问 R2、SMTP、LLM 或公网代理，仅验证参数接收与服务端契约。
- 未修改 `admin-bff` 路由，未新增 Pod、Service、HTTPRoute、PVC 或中间件。

## 验证结果

```text
.\gradlew.bat :services:ircs-config-service:test :services:ircs-credential-service:test -> PASS
.\gradlew.bat build -> PASS
deployment/ircs-config-service successfully rolled out
deployment/ircs-credential-service successfully rolled out
config imageID: harbor.mnnu.eu.org/ircs/ircs-config-service@sha256:3ce1e6be62dd11a6912a01715b007699b3f02ff8445c2383464545f7d2c9ddac
credential imageID: harbor.mnnu.eu.org/ircs/ircs-credential-service@sha256:c1eaa572403be48308155a4edc8dcfca219b8008fb6f6f228e6d5352828d5b60
smoke: scripts/smoke/admin-bff-02517-smoke.ps1 -> MARK 9/9
cleanup: sys_credentials=0, system_configs=0
```

资源与路由：

```text
ircs-dev ResourceQuota: pods=28/30, services=24/26
No public HTTPRoute in ircs-dev.
```
