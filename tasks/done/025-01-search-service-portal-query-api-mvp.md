# 025-01 search-service portal query API 子任务

状态：已完成

父任务：

- `tasks/in-progress/025-v1-route-coverage-closeout-parent.md`

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- V1 `SearchController`
- V1 `SearchQueryServiceImpl`
- V3 `ircs-search-service`

## 背景

`ircs-search-service` 已完成 ES sync worker MVP，但尚未暴露 V1 portal search 的查询
HTTP API。V1 `SearchController` 仍有两个前台可见路由未在 V3 中接管：

```text
GET /api/portal/search/suggest
GET /api/portal/search/recommendations/{videoId}
```

## 目标

- 在 `ircs-search-service` 中新增 portal search query HTTP API。
- `GET /api/portal/search/suggest?keyword=` 返回 `List<String>`。
- `GET /api/portal/search/recommendations/{videoId}` 返回分页 movie card。
- 查询只读 Elasticsearch，不修改 PostgreSQL、RabbitMQ 或业务表。
- 保持 V1 path 兼容，后续 edge routing 可直接转发。

## 非目标

- 不实现 `/api/portal/explore`。
- 不实现 admin raw/unified search API。
- 不新增 HTTPRoute。
- 不新增新的 Deployment 或 Service。
- 不修改 ES index schema，除非本地测试证明现有 mapping 无法支持。
- 不做生产切换。

## 输入

- `services/ircs-search-service`
- V1 `modules/search/controller/SearchController.java`
- V1 `modules/search/service/impl/SearchQueryServiceImpl.java`
- V1 `modules/search/service/support/PortalSearchQueryFactory.java`
- V1 `modules/search/model/assembler/SearchAssembler.java`
- V3 `UnifiedVideoSearchDocument`

## 输出

- `SearchPortalController`
- portal search query service/factory/response DTO
- 单元测试
- 文档和任务状态回写
- `docs/migrations/023-search-service-portal-query-api-extraction.md`

## 影响范围

```text
services/ircs-search-service
docs/requirements/025-v1-route-coverage-closeout.md
docs/reports/025-v1-v3-route-coverage-audit.md
tasks/in-progress/025-v1-route-coverage-closeout-parent.md
tasks/done/025-01-search-service-portal-query-api-mvp.md
tasks/index.md
docs/index.md
```

## 依赖

- Spring Web controller 支持。
- Spring Data Elasticsearch `ElasticsearchOperations`。
- 现有 `ircs_unified_video` index 和 `UnifiedVideoSearchDocument`。

## 风险

- 当前 search-service 原本主要是 worker，新增 web starter 需要确认不会影响 listener
  默认关闭策略。
- ES suggest 依赖 `suggestion` completion field；若 dev 索引没有数据，应返回空数组而不是 500。
- recommendations 使用 more-like-this 时，如果目标 document 不存在，应返回空分页或明确错误，
  不能让 portal 页面崩溃。
- Page JSON shape 需与 Spring `Page` 默认序列化兼容。

## 验收标准

- `.\gradlew.bat :services:ircs-search-service:test` 通过。
- `.\gradlew.bat build` 通过或明确记录失败原因。
- `GET /api/portal/search/suggest` controller 单元测试覆盖空 keyword 和正常 keyword。
- `GET /api/portal/search/recommendations/{videoId}` controller/service 单元测试覆盖分页响应。
- 代码不新增公网 HTTPRoute。

## 测试边界

本地阶段：

```text
.\gradlew.bat :services:ircs-search-service:test
```

如进入集群阶段：

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-search-service' -ImageName ircs-search-service -Tag dev -Architecture arm64 -Os linux
kubectl rollout restart deployment/ircs-search-service -n ircs-dev
kubectl rollout status deployment/ircs-search-service -n ircs-dev --timeout=240s
ClusterIP smoke GET /api/portal/search/suggest?keyword=...
ClusterIP smoke GET /api/portal/search/recommendations/{videoId}
kubectl get httproute -n ircs-dev
```

## 实现结果

- `PortalSearchController` 已新增 V1 兼容路径：
  - `GET /api/portal/search/suggest`
  - `GET /api/portal/search/recommendations/{videoId}`
- `PortalSearchQueryService` 已实现只读 Elasticsearch 查询：
  - suggest 使用 `suggestion` completion field。
  - recommendations 使用 `more_like_this` 查询。
  - 空 keyword、缺数据或 ES 查询异常时降级为空数组/空分页。
- `PortalMovieCardResponse` 保持 V1 portal movie card 的主要字段 shape。
- 未新增 HTTPRoute、Deployment、Service 或中间件。

## 验证结果 2026-06-04

```text
.\gradlew.bat :services:ircs-search-service:test
.\gradlew.bat build
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-search-service' -ImageName ircs-search-service -Tag dev -Architecture arm64 -Os linux
kubectl rollout restart deployment/ircs-search-service -n ircs-dev
kubectl rollout status deployment/ircs-search-service -n ircs-dev --timeout=240s
```

结果：全部通过。

ClusterIP smoke：

```text
GET /api/portal/search/suggest?keyword=codex -> 200, []
GET /api/portal/search/recommendations/00000000-0000-0000-0000-000000000001?size=3 -> 200, empty page
```

部署状态：

```text
image:    harbor.mnnu.eu.org/ircs/ircs-search-service:dev
imageID:  harbor.mnnu.eu.org/ircs/ircs-search-service@sha256:1898ca9f9d8d78165f803ed6fcb650150da1ac12cc928e2069e3829b4616b38b
ready:    deployment/ircs-search-service 1/1
quota:    pods=23/24, services=19/20
route:    no HTTPRoute in ircs-dev
logs:     no ERROR/Exception/Portal search warning after smoke
```

覆盖复核：

```text
V1 routes: 203
V3 routes: 54
missing:   160
search missing: []
```
