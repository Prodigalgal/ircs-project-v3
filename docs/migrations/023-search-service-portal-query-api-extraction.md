# 023 search-service portal query API extraction

状态：已完成

更新时间：2026-06-04

## 背景

`011` 已完成 search-service ES sync worker MVP，但 V1 portal search 的查询 API 仍留在
V1 单体中。`025` 路由覆盖审计确认 search 模块仍缺两个前台可见路由：

```text
GET /api/portal/search/suggest
GET /api/portal/search/recommendations/{videoId}
```

## 实现范围

- 在 `ircs-search-service` 中新增 `PortalSearchController`。
- 新增 `PortalSearchQueryService`，通过 `ElasticsearchOperations` 只读查询 ES。
- 新增 `PortalMovieCardResponse`，保持 portal card 主要字段 shape。
- suggest 使用 `suggestion` completion field。
- recommendations 使用 `more_like_this` 查询。

## 非目标

- 不实现 `/api/portal/explore`。
- 不实现 admin raw/unified search API。
- 不新增 HTTPRoute。
- 不新增 Deployment、Service 或中间件。
- 不修改 ES mapping。
- 不写 PostgreSQL、RabbitMQ 或业务表。

## 验证

本地验证：

```text
.\gradlew.bat :services:ircs-search-service:test
.\gradlew.bat build
```

结果：通过。

镜像与 rollout：

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-search-service' -ImageName ircs-search-service -Tag dev -Architecture arm64 -Os linux
kubectl rollout restart deployment/ircs-search-service -n ircs-dev
kubectl rollout status deployment/ircs-search-service -n ircs-dev --timeout=240s
```

结果：通过。

运行态证据：

```text
imageID: harbor.mnnu.eu.org/ircs/ircs-search-service@sha256:1898ca9f9d8d78165f803ed6fcb650150da1ac12cc928e2069e3829b4616b38b
ready:   deployment/ircs-search-service 1/1
quota:   pods=23/24, services=19/20
route:   no HTTPRoute in ircs-dev
```

ClusterIP smoke：

```text
GET /api/portal/search/suggest?keyword=codex
-> 200
-> []

GET /api/portal/search/recommendations/00000000-0000-0000-0000-000000000001?size=3
-> 200
-> empty page
```

日志检查：

```text
kubectl logs deployment/ircs-search-service -n ircs-dev --since=10m | Select-String "ERROR|Exception|Portal search"
```

结果：无匹配。

## 覆盖结果

路由抽取复核：

```text
V1 backend Controller routes: 203
V3 service Controller routes: 54
Direct missing routes after path normalization: 160
Search module missing routes: 0
```

## 后续

- `/api/portal/explore` 留给 portal/search 组合切片。
- content、interaction、task、storage、ops 的剩余路由缺口继续在 `025` 父任务下拆分。
