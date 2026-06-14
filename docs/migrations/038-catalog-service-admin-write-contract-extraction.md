# 038 catalog-service admin write contract extraction

状态：已完成

更新时间：2026-06-07

关联任务：

- `tasks/done/025-16-catalog-service-admin-write-contract-mvp.md`

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/039-parallel-subagent-route-slices-02515-02517.md`

## 背景

`025-13` 已把 catalog-service 的 admin read contract 迁移到 V3，但 V1 admin
仍依赖 data-sources、standard dictionary 和 raw dictionary 的写入接口。本迁移记录
固定 `025-16` 的 25 条接口复现结果。

## 迁移范围

```text
POST   /api/v1/data-sources
PUT    /api/v1/data-sources/{id}
PATCH  /api/v1/data-sources/{id}
DELETE /api/v1/data-sources/{id}
POST   /api/v1/data-sources/fetch-sample
POST   /api/v1/standard-categories
PUT    /api/v1/standard-categories/{id}
PATCH  /api/v1/standard-categories/{id}
DELETE /api/v1/standard-categories/{id}
POST   /api/v1/standard-genres
PUT    /api/v1/standard-genres/{id}
DELETE /api/v1/standard-genres/{id}
POST   /api/v1/standard-areas
POST   /api/v1/standard-languages
PUT    /api/v1/raw-categories/{id}
DELETE /api/v1/raw-categories/{id}
POST   /api/v1/raw-genres
PUT    /api/v1/raw-genres/{id}
DELETE /api/v1/raw-genres/{id}
POST   /api/v1/raw-areas
PUT    /api/v1/raw-areas/{id}
DELETE /api/v1/raw-areas/{id}
POST   /api/v1/raw-languages
PUT    /api/v1/raw-languages/{id}
DELETE /api/v1/raw-languages/{id}
```

## 实现结果

- `ircs-catalog-service` 增加 catalog admin write DTO、Service、Repository 和 Controller 契约。
- `fetch-sample` 通过 JDK `HttpClient` 执行受控 URL 请求，smoke 使用 namespace 内部 `ircs-config-service` URL。
- standard area/language 创建、raw dictionary 映射、data source path/baseUrl 规范化均按 V1 admin 字段形状返回。
- 未修改 `admin-bff` 路由，继续通过已有 `/api/v1/**` 转发到目标服务。
- 未新增 Pod、Service、HTTPRoute、PVC 或中间件。

## 验证结果

```text
.\gradlew.bat :services:ircs-catalog-service:test -> PASS
.\gradlew.bat build -> PASS
deployment/ircs-catalog-service successfully rolled out
imageID: harbor.mnnu.eu.org/ircs/ircs-catalog-service@sha256:bc8ec3c3b0a72b2f8ffbbb97814839bb91d783a6f0c474153f5f538a8a58cd39
smoke: scripts/smoke/admin-bff-02516-smoke.ps1 -> MARK 25/25
cleanup: data_sources/standard_*/raw_* smoke residue all 0
```

资源与路由：

```text
ircs-dev ResourceQuota: pods=28/30, services=24/26
No public HTTPRoute in ircs-dev.
```
