# 030 content-service raw/unified video admin API 无损迁移边界分析

状态：已完成

更新时间：2026-06-06

## 背景

V1 `RawVideoController` 和 `UnifiedVideoController` 是已经验证过的后台内容管理能力。
V3 的拆分目标不是降级功能，而是在新的服务边界下尽量无损承接 V1 行为。

当前结论：

- 不迁移开发资源，继续使用 `.106` 的 `ircs-dev` namespace。
- 复用已部署的 `ircs-content-service` 作为 V1 兼容 HTTP 入口。
- 跨服务副作用必须交给真实 owner：search-service、normalization-worker、
  metadata-worker、scraper-service。
- unified recalculate 当前由 content-service 基于已绑定 raw source 执行 V1 等价重算，
  避免在 `services=19/20` 的 dev 配额下恢复 aggregation-worker Service。

## 目标路由

本切片覆盖 V1 raw/unified admin API 全量 21 条路由。复现计数采用接口粒度：
一条接口只有在 V3 入口、核心行为、副作用和 smoke 证据均通过后才标记 1 次。

```text
GET    /api/v1/raw-videos
GET    /api/v1/raw-videos/{id}
POST   /api/v1/raw-videos
PUT    /api/v1/raw-videos/{id}
DELETE /api/v1/raw-videos/{id}
POST   /api/v1/raw-videos/batch/delete
POST   /api/v1/raw-videos/batch/sync-search
POST   /api/v1/raw-videos/{id}/renormalize
POST   /api/v1/raw-videos/{id}/reenrich
POST   /api/v1/raw-videos/{id}/refetch
POST   /api/v1/raw-videos/batch/renormalize
POST   /api/v1/raw-videos/batch/reenrich
POST   /api/v1/raw-videos/batch/refetch

GET    /api/v1/unified-videos
GET    /api/v1/unified-videos/{id}
POST   /api/v1/unified-videos
PUT    /api/v1/unified-videos/{id}
DELETE /api/v1/unified-videos/{id}
POST   /api/v1/unified-videos/batch/delete
POST   /api/v1/unified-videos/batch/sync-search
POST   /api/v1/unified-videos/{id}/recalculate
```

按当前 025 估算，本切片完成后：

```text
V3 service Controller routes: 72 -> 93
Direct missing routes:         142 -> 121
Raw/Unified admin API gap:     closed
```

## 服务承接

```text
Admin UI
  |
  | V1-compatible HTTP
  v
+----------------------------------------------------------------+
| ircs-content-service                                           |
|                                                                |
| raw/unified CRUD, detail/list, relation writes                 |
| single/batch delete                                            |
| search sync command publishing                                 |
| normalize/enrich command publishing                            |
| internal call for raw refetch                                  |
| unified recalculate fallback                                   |
+---------+----------------------+----------------------+---------+
          |                      |                      |
          v                      v                      v
 +----------------+     +--------------------+   +----------------+
 | search-service |     | normalization      |   | metadata       |
 | SEARCH_SYNC_*  |     | NORMALIZE_VIDEO    |   | ENRICH_METADATA|
 +----------------+     +--------------------+   +----------------+
          |
          v
 +----------------+
 | scraper-service|
 | refetch raw    |
 +----------------+

Optional future owner contract, not current dev runtime path:

+--------------------+
| aggregation-worker |
| recalculate unified|
+--------------------+
```

## live schema 结论

已在 `ircs-dev` PostgreSQL 核验：

- `raw_category`、`standard_category`、`standard_genre` 是单数表名。
- smoke 使用 `codex-smoke-*` 临时 raw/unified 数据，并已在验证后清理。
- content-service、scraper-service 均已部署，更新镜像不新增 Pod/Service。
- aggregation-worker 已存在代码级 internal recalculation contract，但当前 dev runtime
  不通过该 Service 承接 unified recalculate。

## 兼容原则

- V1 的 HTTP 路由、状态码语义和核心副作用是迁移基线。
- V3 实现可以换成队列或内部 HTTP，但对外入口不应丢失。
- 如果某个 V1 行为需要新增内部 contract，优先补 contract；不把接口变成空操作。
- 若某个行为无法在本轮完全等价，必须在任务中记录差异、风险和后续闭环项。

## 具体行为承接

| V1 行为 | V3 承接方式 |
| --- | --- |
| raw/unified list/detail/create/update/delete | content-service JDBC |
| raw/unified batch delete | content-service 批量清理主表和 join table |
| raw/unified sync-search | 发布 `SEARCH_SYNC_RAW` / `SEARCH_SYNC_UNIFIED` |
| raw renormalize | 更新 raw `normalization_status=PENDING` 并发布 `NORMALIZE_VIDEO` |
| raw reenrich | 发布 `ENRICH_METADATA` |
| raw refetch | content-service 调 scraper-service 内部 refetch contract |
| unified recalculate | content-service 基于已绑定 raw source 执行重算并发布 search sync；aggregation-worker 内部 contract 作为后续可切换实现 |

## 接口复现计数

当前计数：

```text
已完整迁移并复现: 21/21
待 live smoke 标记: 0/21
```

| 接口 | 当前标记 | 复现证据 |
| --- | ---: | --- |
| `GET /api/v1/raw-videos` | 1/1 | ClusterIP smoke `MARK 2/21` |
| `GET /api/v1/raw-videos/{id}` | 1/1 | ClusterIP smoke `MARK 3/21` |
| `POST /api/v1/raw-videos` | 1/1 | ClusterIP smoke `MARK 1/21` |
| `PUT /api/v1/raw-videos/{id}` | 1/1 | ClusterIP smoke `MARK 4/21` |
| `DELETE /api/v1/raw-videos/{id}` | 1/1 | ClusterIP smoke `MARK 5/21` |
| `POST /api/v1/raw-videos/batch/delete` | 1/1 | ClusterIP smoke `MARK 6/21` |
| `POST /api/v1/raw-videos/batch/sync-search` | 1/1 | ClusterIP smoke `MARK 7/21` |
| `POST /api/v1/raw-videos/{id}/renormalize` | 1/1 | ClusterIP smoke `MARK 8/21` |
| `POST /api/v1/raw-videos/{id}/reenrich` | 1/1 | ClusterIP smoke `MARK 9/21` |
| `POST /api/v1/raw-videos/{id}/refetch` | 1/1 | ClusterIP smoke `MARK 10/21` |
| `POST /api/v1/raw-videos/batch/renormalize` | 1/1 | ClusterIP smoke `MARK 11/21` |
| `POST /api/v1/raw-videos/batch/reenrich` | 1/1 | ClusterIP smoke `MARK 12/21` |
| `POST /api/v1/raw-videos/batch/refetch` | 1/1 | ClusterIP smoke `MARK 13/21` |
| `GET /api/v1/unified-videos` | 1/1 | ClusterIP smoke `MARK 15/21` |
| `GET /api/v1/unified-videos/{id}` | 1/1 | ClusterIP smoke `MARK 16/21` |
| `POST /api/v1/unified-videos` | 1/1 | ClusterIP smoke `MARK 14/21` |
| `PUT /api/v1/unified-videos/{id}` | 1/1 | ClusterIP smoke `MARK 17/21` |
| `DELETE /api/v1/unified-videos/{id}` | 1/1 | ClusterIP smoke `MARK 18/21` |
| `POST /api/v1/unified-videos/batch/delete` | 1/1 | ClusterIP smoke `MARK 19/21` |
| `POST /api/v1/unified-videos/batch/sync-search` | 1/1 | ClusterIP smoke `MARK 20/21` |
| `POST /api/v1/unified-videos/{id}/recalculate` | 1/1 | ClusterIP smoke `MARK 21/21` |

## 风险

- V1 list 复杂查询走 search-service；本轮如直接用 PostgreSQL fallback，需要保证字段和过滤
  足够覆盖后台管理页，否则应接 search-service admin query。
- refetch 需要跨服务调用；scraper-service 不可用时应返回明确 5xx，不静默成功。
- recalculate 当前不跨服务，需确保 SQL fallback 与 V1 已验证聚合语义一致；如果后续恢复
  aggregation-worker Service，再切换到内部 owner contract。
- delete 和 batch delete 必须同步发布搜索 DELETE，否则索引会残留。
- smoke 要避免外部真实站点依赖；refetch 先验证 scraper internal contract 可调用性，
  recalculate 验证 content-service SQL fallback 能基于绑定 raw source 更新 unified。

## 验收标准

- 21 条 V1 raw/unified admin API 在 V3 有兼容入口。
- content-service 本地测试通过。
- 涉及 scraper-service 的内部 refetch contract 测试通过；aggregation-worker 内部
  recalculation contract 作为后续可切换实现通过编译/测试。
- 全仓 `build` 通过。
- ARM64 镜像推送并 rollout Ready。
- ClusterIP smoke 覆盖 CRUD、batch、search-sync、renormalize、reenrich、scraper internal
  refetch 调用路径和 content-service unified recalculate fallback。
- smoke 后临时 raw/unified 数据清理干净。
- 不新增 HTTPRoute，不新增 K8S Service，不扩大资源配额。

## 完成结果

本切片已完成 21 条 V1 raw/unified admin API 的 V3 无损迁移入口和接口级复现标记。

验证摘要：

```text
.\gradlew.bat :services:ircs-content-service:test :services:ircs-scraper-service:test :services:ircs-aggregation-worker:test -> BUILD SUCCESSFUL
.\gradlew.bat build -> BUILD SUCCESSFUL
content-service ARM64 digest -> sha256:e70bc1e9ca03b80f103775a2eb50ac8978af2af9b1fadb5f79e3cf06f839fb9a
kubectl -n ircs-dev rollout status deploy/ircs-content-service -> successfully rolled out
ClusterIP smoke -> MARK 21/21 passed
post-cleanup smoke anchors -> 0|0|0|0|0
ircs-dev HTTPRoute -> none
ResourceQuota -> pods=23/24, services=19/20
```

本轮同时处理了 dev PostgreSQL 连接数压力：17 个运行中的 datasource Deployment 已通过
`kubectl set env` 加入 Hikari 小池配置，18 个本地 dev datasource YAML 也已同步：

```text
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=2
SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0
SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT=30000
```
