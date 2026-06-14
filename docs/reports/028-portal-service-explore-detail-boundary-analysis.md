# 028 portal-service explore/detail 边界分析

状态：已完成

更新时间：2026-06-05

## 背景

`025` 路由覆盖审计中，V1 `portal` 模块仍有用户侧内容接口缺口。V3
`ircs-portal-service` 已完成 `/api/portal/home` 和 `/api/portal/metadata`
只读 MVP，但尚未覆盖 V1 的探索页和详情页：

```text
GET /api/portal/explore
GET /api/portal/movies/{id}
```

这两个接口是前台用户可见的只读能力，适合在不新增 K8S 资源的前提下继续关闭缺口。

## 目标

本切片覆盖：

```text
GET /api/portal/explore
GET /api/portal/movies/{id}
```

实现后，`portal-service` 将具备：

- 首页聚合：已完成。
- 元数据：已完成。
- 探索页分页筛选：本轮补齐。
- 影片详情：本轮补齐。

## 非目标

- 不迁移 `/api/portal/auth/**`，这类能力归属 `ircs-identity-service`。
- 不迁移 `/api/portal/profile/**`，这类能力归属 `ircs-identity-service` 后续 profile
  增强。
- 不迁移 `/api/v1/members/**` 后台会员管理。
- 不新增 inter-service HTTP client；本轮继续使用共享 PostgreSQL 迁移桥。
- 不新增 Pod、Service 或 HTTPRoute。

## V1 行为摘要

### Explore

V1 `PortalContentController`：

- 路径：`GET /api/portal/explore`。
- 入参：`page`、`size`、`keyword`、`type`、`genre`、`area`、`year`、
  `language`、`sort`。
- `keyword` 超过 50 字截断。
- `size` 上限 70。
- 分页深度超过 10000 返回 400。
- 首屏无 keyword 时允许短缓存，否则 `no-store`。

V1 Service 内部委托 `SearchQueryService.explore`。V3 本轮采用 SQL 只读筛选，
覆盖基础筛选和排序，ES 相关性排序作为后续增强。

### Detail

V1 `PortalContentController`：

- 路径：`GET /api/portal/movies/{id}`。
- 返回 `PortalMovieDetailVO`。
- 未找到时返回 404。
- 短缓存 300 秒。

详情包含：

- unified video 基础字段、封面、分类、外部 ID、趋势时间。
- genre、area、language。
- director/actor cast。
- raw video source、playlist、episode。
- approved magnet links。

## 目标数据流

```text
client
  |
  v
+---------------------+
| ircs-portal-service |
| /api/portal/explore |
| /api/portal/movies  |
+----------+----------+
           |
           v
+-----------------------------+
| shared PostgreSQL bridge    |
| unified_videos              |
| standard_category/metadata  |
| raw_video_unified_video     |
| raw_videos/playlists        |
| episodes/source_domains     |
| magnet_links                |
+-----------------------------+
```

## 风险

- SQL explore 与 V1 ES explore 的 relevance 排序不完全等价；本轮先覆盖 HTTP
  合约、分页、安全上限、基础筛选和常用排序。
- detail 查询跨多张表，必须控制 N+1 和空关联。
- live schema 中 raw video 与 unified video 通过 `raw_video_unified_video` 关联，
  不能沿用旧 `videos.unified_video_id` 假设。
- smoke 需要插入临时 category、unified video、raw video、playlist、episode 和
  magnet link，必须严格清理。

## 验收标准

- 新增 2 条 portal 内容路由。
- `GET /api/portal/explore` 支持分页、keyword、type/category、genre、area、
  year、language、sort 参数。
- `GET /api/portal/movies/{id}` 返回基础详情、genres、area/language、cast、sources、
  magnets；不存在返回 404。
- `.\gradlew.bat :services:ircs-portal-service:test` 通过。
- `.\gradlew.bat build` 通过。
- ARM64 镜像推送并 rollout 当前 `ircs-portal-service` Deployment。
- ClusterIP smoke 覆盖 explore/detail/not-found，并确认临时数据清理为 0。

## 验收结果

已完成：

- `.\gradlew.bat :services:ircs-portal-service:test` 通过。
- `.\gradlew.bat build` 通过。
- ARM64 镜像推送到 `harbor.mnnu.eu.org/ircs/ircs-portal-service:dev`。
- `ircs-dev` 中 `ircs-portal-service` rollout 成功。
- 运行镜像 digest：
  `harbor.mnnu.eu.org/ircs/ircs-portal-service@sha256:f3c0ac1364a0517821f1a512364282683682ded50c4bdb1ef620d162a6bab32a`。
- ClusterIP smoke 覆盖 explore/detail/not-found。
- 临时数据清理结果：
  `unified,raw,episodes,magnet,category=0,0,0,0,0`。
- `ircs-dev` 未新增 HTTPRoute，ResourceQuota 仍为 `pods=23/24`、`services=19/20`。
