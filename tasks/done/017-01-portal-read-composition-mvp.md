# 017-01 portal-service 公开读聚合 MVP 子任务

状态：已完成

父任务：

- `tasks/in-progress/011-mvp-service-onboarding-parent.md`

关联文档：

- `docs/reports/017-portal-interaction-magnet-boundary-analysis.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`
- `docs/requirements/011-mvp-service-onboarding-acceleration.md`

## 背景

`ircs-portal-service` 尚未接入 `ircs-dev`。V1 portal 内容读边界位于
`modules/portal/controller/PortalContentController.java`，公开内容路由为
`/api/portal/home`、`/api/portal/explore`、`/api/portal/metadata` 和
`/api/portal/movies/{id}`。其中 `/explore` 已委托 `SearchQueryService`，
`/movies/{id}` 还会组装播放源、封面 URL 和 approved magnet 链接。为了降低
首个 portal MVP 风险，本子任务先限定为公开读聚合的低风险路径。

## 目标

- 为 `ircs-portal-service` 接入公开只读内容聚合 MVP。
- 优先保留 V1 路径 `GET /api/portal/metadata`。
- 优先保留 V1 路径 `GET /api/portal/home`。
- 使用当前 PostgreSQL 内容、字典、封面相关表作为迁移桥。
- 响应字段保持 V1 portal 前端 `PortalMetadata`、`HomeData` 消费 shape。
- 后续实施时完成本地测试、server dry-run、ARM64 镜像、K8S rollout 和
  ClusterIP smoke。

## 非目标

- 不接入 `/api/portal/auth/**`。
- 不接入 `/api/portal/profile/**`。
- 不接入 `/api/portal/interaction/**`。
- 不接入 `/api/portal/feedback/**`。
- 不接入 `/api/v1/members/**`。
- 不在本 MVP 中重写 search-service 的 explore 查询逻辑。
- 不在本 MVP 中承诺公网 HTTPRoute 或 edge cutover。
- 不拆分独立数据库。

## 上游来源

- `docs/reports/017-portal-interaction-magnet-boundary-analysis.md`
- V1 `PortalContentController`
- V1 `PortalContentServiceImpl`
- V1 `PortalDataCacheServiceImpl`
- V1 `PortalAssembler`
- V1 `UnifiedVideoPortalRepository`
- V1 `huawai/src/lib/api/content.ts`
- V1 `huawai/src/lib/types/index.ts`

## 输入

- `services/ircs-portal-service`
- `GET /api/portal/metadata`
- `GET /api/portal/home`
- `PortalMetadataVO`
- `PortalHomeVO`
- `PortalMovieCardVO`
- `PortalCategorySectionVO`
- `CategoryItem`
- `unified_videos`
- `cover_images`
- `source_domains`
- `standard_category`
- `standard_genre`
- `standard_areas`
- `standard_languages`
- `unified_video_genres`
- `unified_video_standard_areas`
- `unified_video_standard_languages`

## 输出

- `ircs-portal-service` 公开读聚合 MVP 实现。
- `GET /api/portal/metadata` 兼容响应。
- `GET /api/portal/home` 兼容响应。
- dev Deployment/Service。
- 迁移文档、K8S 快照和任务状态回写。

## 影响范围

```text
services/ircs-portal-service
deploy/k8s/dev/portal-service-dev.yaml
docs/migrations
docs/k8s
deploy/k8s/README.md
tasks
```

## 依赖

- 当前 V3 shared PostgreSQL。
- 已接入的 `catalog-service` 只读字典边界可作为后续替代方向，但本 MVP 可先走
  共享 DB 迁移桥。
- 已接入的 `storage-service` 物理资源边界；本 MVP 只解析 URL，不写存储。
- 如后续纳入 `/api/portal/movies/{id}`，需要明确是否通过 `magnet-service`
  内部 API 读取 approved links。

## 风险

- V1 `PortalContentServiceImpl` 的 `/home` 聚合依赖多表和缓存，直接迁移完整逻辑
  可能扩大范围。
- `/api/portal/explore` 在 V1 中委托 `SearchQueryService`，不应在
  portal-service 内重复实现 search-service 能力。
- `/api/portal/movies/{id}` 会涉及播放源、集数、封面、磁链和详情缓存，建议后置
  或单独拆子任务。
- 仍使用共享 PostgreSQL 时，需要保持只读边界，避免 portal-service 写 content
  或 catalog 表。

## 验收标准

- `GET /api/portal/metadata` 返回 `categories`、`genres`、`areas`、
  `languages`、`years`，缺数据时返回空数组而不是 `null`。
- `GET /api/portal/home` 返回 `spotlight`、`trending`、`sections`，缺数据时
  返回空数组而不是 `null`。
- 响应字段可被 V1 `huawai` 前端 `PortalMetadata` 和 `HomeData` 类型消费。
- 服务在 `ircs-dev` Ready 1/1。
- smoke 不新增或修改业务数据。
- 未新增公网 HTTPRoute。

## 测试边界

```text
.\gradlew.bat :services:ircs-portal-service:test
.\gradlew.bat build
kubectl apply --dry-run=server -f deploy\k8s\dev\portal-service-dev.yaml
kubectl rollout status deployment/ircs-portal-service -n ircs-dev --timeout=240s
集群内 GET /api/portal/metadata smoke
集群内 GET /api/portal/home smoke
```

## 关联任务

- 父任务：`tasks/in-progress/011-mvp-service-onboarding-parent.md`
- 同批边界分析：`tasks/in-progress/017-02-interaction-feedback-history-mvp.md`
- 同批边界分析：`tasks/done/017-03-magnet-provider-cache-mvp.md`

## 验证结果

已完成：

```text
.\gradlew.bat :services:ircs-portal-service:test
.\gradlew.bat :services:ircs-portal-service:build
.\gradlew.bat build
kubectl apply --dry-run=server -f deploy\k8s\dev\portal-service-dev.yaml
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-portal-service' -ImageName ircs-portal-service -Tag dev -Architecture arm64 -Os linux
kubectl rollout status deployment/ircs-portal-service -n ircs-dev --timeout=240s
ClusterIP GET /api/portal/metadata smoke
ClusterIP GET /api/portal/home smoke
```

smoke 结果：

```text
GET /api/portal/metadata 返回 categories, genres, areas, languages, years
GET /api/portal/home 返回 spotlight, trending, sections
Deployment Ready 1/1
Service ClusterIP 10.99.214.176
HTTPRoute none
image digest sha256:a2ca28ce3f71ad223257640c2c9396bb591c7ab022407717dc94ca60aa52a7cb
```

## 当前状态

已完成。`ircs-portal-service` 已在 `ircs-dev` Ready 1/1，暂不新增公网
HTTPRoute；`/api/portal/explore` 和 `/api/portal/movies/{id}` 仍后置。
