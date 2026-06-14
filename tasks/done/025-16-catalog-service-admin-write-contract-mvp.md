# 025-16 catalog-service admin write contract MVP 子任务

状态：已完成

父任务：

- `tasks/in-progress/025-v1-route-coverage-closeout-parent.md`

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/039-parallel-subagent-route-slices-02515-02517.md`

## 背景

`025-13` 已完成 catalog-service 的 admin read contract，但 V1 admin 仍有 data-sources、
standard-* 和 raw-* 的 create/update/patch/delete/fetch-sample 缺口。当前需要在
`ircs-catalog-service` 内补齐写契约。

## 目标

- 在 `ircs-catalog-service` 复现 data-source 写接口和 `fetch-sample`。
- 在 `ircs-catalog-service` 复现 standard category/genre/area/language 写接口。
- 在 `ircs-catalog-service` 复现 raw category/genre/area/language 写接口。
- 保持 V1 id 校验、created location、404/400 等基础语义。

## 非目标

- 不修改 content/config/credential 服务。
- 不修改 `admin-bff` 路由。
- 不新增 Pod、Service、HTTPRoute、PVC 或中间件。
- 不在 smoke 中访问生产外部数据源；`fetch-sample` 必须使用隔离可控输入。

## 输入

```text
V1 DataSourceController
V1 RawCategory/RawGenre/RawArea/RawLanguage Controller
V1 StandardCategory/StandardGenre/StandardArea/StandardLanguage Controller
services/ircs-catalog-service
```

## 输出

- catalog write DTO、Controller/Service/Repository 增量。
- 对应单元测试。
- worker 完成结果和 smoke 建议。

## 影响范围

允许写入：

```text
services/ircs-catalog-service/src/main/java/com/prodigalgal/ircs/catalog
services/ircs-catalog-service/src/test/java/com/prodigalgal/ircs/catalog
tasks/done/025-16-catalog-service-admin-write-contract-mvp.md
```

禁止写入：

```text
services/ircs-content-service
services/ircs-config-service
services/ircs-credential-service
services/ircs-admin-bff
deploy/k8s
```

## 验收标准

- data-sources 5 条写/测试接口可用。
- standard dictionary 9 条写接口可用。
- raw dictionary 11 条写接口可用。
- 单元测试通过。
- 后续主 agent 通过 `admin-bff` live smoke 完成 `25/25` 标记。

## 测试边界

已执行：

```text
.\gradlew.bat :services:ircs-catalog-service:test
.\gradlew.bat build
```

主 agent 已完成全仓 build、ARM64 rollout 和 `admin-bff` live smoke。

## 实施结果

更新时间：2026-06-07

已完成范围：

- `ircs-catalog-service` 增加 data-sources create/update/patch/delete 和 `fetch-sample`。
- `ircs-catalog-service` 增加 standard category/genre/area/language 写契约。
- `ircs-catalog-service` 增加 raw category/genre/area/language 写契约。
- `fetch-sample` 使用 JDK `HttpClient`，live smoke 使用 namespace 内可控 URL，不访问生产外部数据源。
- 增加 `CatalogAdminControllerTest` 覆盖 created location、id mismatch、merge patch、删除和 fetch-sample 契约。
- 增加 `scripts/smoke/admin-bff-02516-smoke.ps1`，通过 `admin-bff` 完成 25 条接口复现标记。

未进入范围：

- 未修改 `content/config/credential` 服务。
- 未修改 `admin-bff` 路由。
- 未新增 Pod、Service、HTTPRoute、PVC 或中间件。

## 验证结果

本地测试：

```text
.\gradlew.bat :services:ircs-catalog-service:test -> PASS
.\gradlew.bat build -> PASS
```

K8S rollout：

```text
deployment/ircs-catalog-service successfully rolled out
imageID: harbor.mnnu.eu.org/ircs/ircs-catalog-service@sha256:bc8ec3c3b0a72b2f8ffbbb97814839bb91d783a6f0c474153f5f538a8a58cd39
```

`admin-bff` live smoke：

```text
MARK 1/25 POST /api/v1/data-sources
MARK 2/25 PUT /api/v1/data-sources/{id}
MARK 3/25 PATCH /api/v1/data-sources/{id}
MARK 4/25 POST /api/v1/data-sources/fetch-sample
MARK 5/25 POST /api/v1/standard-categories
MARK 6/25 PUT /api/v1/standard-categories/{id}
MARK 7/25 PATCH /api/v1/standard-categories/{id}
MARK 8/25 POST /api/v1/standard-genres
MARK 9/25 PUT /api/v1/standard-genres/{id}
MARK 10/25 POST /api/v1/standard-areas
MARK 11/25 POST /api/v1/standard-languages
MARK 12/25 PUT /api/v1/raw-categories/{id}
MARK 13/25 DELETE /api/v1/raw-categories/{id}
MARK 14/25 POST /api/v1/raw-genres
MARK 15/25 PUT /api/v1/raw-genres/{id}
MARK 16/25 DELETE /api/v1/raw-genres/{id}
MARK 17/25 POST /api/v1/raw-areas
MARK 18/25 PUT /api/v1/raw-areas/{id}
MARK 19/25 DELETE /api/v1/raw-areas/{id}
MARK 20/25 POST /api/v1/raw-languages
MARK 21/25 PUT /api/v1/raw-languages/{id}
MARK 22/25 DELETE /api/v1/raw-languages/{id}
MARK 23/25 DELETE /api/v1/standard-categories/{id}
MARK 24/25 DELETE /api/v1/standard-genres/{id}
MARK 25/25 DELETE /api/v1/data-sources/{id}
```

清理结果：

```text
data_sources=0
standard_categories=0
standard_genres=0
standard_areas=0
standard_languages=0
raw_categories=0
raw_genres=0
raw_areas=0
raw_languages=0
```
