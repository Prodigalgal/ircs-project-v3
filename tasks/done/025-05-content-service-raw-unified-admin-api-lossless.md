# 025-05 content-service raw/unified admin API 无损迁移

状态：已完成

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/030-content-service-raw-unified-boundary-analysis.md`
- `tasks/in-progress/025-v1-route-coverage-closeout-parent.md`

## 背景

V1 raw/unified 内容管理接口已经经过验证。V3 微服务拆分需要尽量无损迁移这些能力，而不是
只保留低风险 CRUD。当前任务覆盖 V1 `RawVideoController` 和 `UnifiedVideoController`
的全量 21 条路由，并在 V3 中按真实服务 owner 承接副作用。

已确认不迁移开发环境，继续在 `.106` 的 `ircs-dev` namespace 推进。

## 目标

- 在 `ircs-content-service` 暴露 V1 兼容 raw/unified admin API。
- 将 search sync、normalize、enrich 通过现有 RabbitMQ topic 转交给对应 worker。
- 为 raw refetch 补 scraper-service 内部 contract，并由 content-service 调用。
- unified recalculate 在 content-service 内按已绑定 raw source 执行 V1 等价重算，避免本轮恢复
  aggregation-worker Service；aggregation-worker 内部 contract 作为后续可切换实现保留。
- 保持 K8S 对象数量不变，不新增 HTTPRoute。

## 非目标

- 不做公网流量切换。
- 不迁移 `.214` 开发资源。
- 不做物理数据库拆分。
- 不重写后台 UI。
- 不把暂未实现的副作用伪装成成功。

## 输入

- V1 `RawVideoController`、`UnifiedVideoController`。
- V1 `RawVideoCommandServiceImpl`、`RawVideoQueryServiceImpl`、`UnifiedVideoServiceImpl`。
- V3 shared queue/search contracts。
- V3 scraper-service 和 aggregation-worker 当前能力。
- `ircs-dev` PostgreSQL live schema。

## 输出

- content-service raw/unified 全量 admin API。
- scraper-service internal raw refetch contract。
- content-service unified recalculation fallback；aggregation-worker internal recalculation code 作为后续
  owner contract 保留，不作为当前 dev runtime 路径。
- 单元测试、全仓 build、ARM64 rollout、ClusterIP smoke。
- 接口级复现计数表；每条接口完整迁移且复现后只标记 1 次。
- 迁移记录和任务归档。

## 影响范围

包含：

- `services/ircs-content-service/**`
- `services/ircs-scraper-service/**`
- `services/ircs-aggregation-worker/**`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/030-content-service-raw-unified-boundary-analysis.md`
- `docs/migrations/027-content-service-raw-unified-admin-api-extraction.md`
- `docs/index.md`
- `tasks/index.md`
- `tasks/in-progress/025-v1-route-coverage-closeout-parent.md`

不包含：

- 新 K8S Service。
- 新 HTTPRoute。
- 生产流量切换。
- `.214` 迁移。

## 依赖

- `ircs-dev` PostgreSQL、RabbitMQ 可用。
- `ircs-content-service`、`ircs-scraper-service` 可滚动替换。
- Harbor ARM64 push 可用。

## 接口复现计数规则

一条接口只有同时满足以下条件，才标记 `1/1`：

- V3 暴露与 V1 兼容的 HTTP method 和 path。
- DTO、状态码、核心数据写入或读取行为与 V1 基线对齐。
- search sync、normalize、enrich、refetch、recalculate 等副作用有真实承接路径。
- 在 `ircs-dev` ClusterIP smoke 中复现成功，并且临时数据完成清理。

当前计数：

```text
已完整迁移并复现: 21/21
待 live smoke 标记: 0/21
```

| 接口 | 标记 | 状态 |
| --- | ---: | --- |
| `GET /api/v1/raw-videos` | 1/1 | ClusterIP smoke passed |
| `GET /api/v1/raw-videos/{id}` | 1/1 | ClusterIP smoke passed |
| `POST /api/v1/raw-videos` | 1/1 | ClusterIP smoke passed |
| `PUT /api/v1/raw-videos/{id}` | 1/1 | ClusterIP smoke passed |
| `DELETE /api/v1/raw-videos/{id}` | 1/1 | ClusterIP smoke passed |
| `POST /api/v1/raw-videos/batch/delete` | 1/1 | ClusterIP smoke passed |
| `POST /api/v1/raw-videos/batch/sync-search` | 1/1 | ClusterIP smoke passed |
| `POST /api/v1/raw-videos/{id}/renormalize` | 1/1 | ClusterIP smoke passed |
| `POST /api/v1/raw-videos/{id}/reenrich` | 1/1 | ClusterIP smoke passed |
| `POST /api/v1/raw-videos/{id}/refetch` | 1/1 | ClusterIP smoke passed |
| `POST /api/v1/raw-videos/batch/renormalize` | 1/1 | ClusterIP smoke passed |
| `POST /api/v1/raw-videos/batch/reenrich` | 1/1 | ClusterIP smoke passed |
| `POST /api/v1/raw-videos/batch/refetch` | 1/1 | ClusterIP smoke passed |
| `GET /api/v1/unified-videos` | 1/1 | ClusterIP smoke passed |
| `GET /api/v1/unified-videos/{id}` | 1/1 | ClusterIP smoke passed |
| `POST /api/v1/unified-videos` | 1/1 | ClusterIP smoke passed |
| `PUT /api/v1/unified-videos/{id}` | 1/1 | ClusterIP smoke passed |
| `DELETE /api/v1/unified-videos/{id}` | 1/1 | ClusterIP smoke passed |
| `POST /api/v1/unified-videos/batch/delete` | 1/1 | ClusterIP smoke passed |
| `POST /api/v1/unified-videos/batch/sync-search` | 1/1 | ClusterIP smoke passed |
| `POST /api/v1/unified-videos/{id}/recalculate` | 1/1 | ClusterIP smoke passed |

## 风险

- 跨服务调用失败必须显式暴露错误。
- search-sync 消息必须区分 INDEX/DELETE。
- batch delete 要清理 join table，避免 FK 冲突和脏关联。
- live schema 表名与历史 migration 不完全一致，代码必须按 live schema。

## 验收标准

- 21 条 V1 raw/unified admin API 在 V3 有兼容入口。
- 21 条接口按接口粒度完成 `1/1` 复现标记，总计 `21/21`。
- `.\gradlew.bat :services:ircs-content-service:test` 成功。
- 相关 scraper/aggregation 测试成功。
- `.\gradlew.bat build` 成功。
- ARM64 镜像推送并 rollout Ready。
- smoke 后临时数据清理。
- `kubectl -n ircs-dev get httproute` 不出现 ircs-dev 公网入口。

## 测试边界

本地：

```text
.\gradlew.bat :services:ircs-content-service:test
.\gradlew.bat :services:ircs-scraper-service:test
.\gradlew.bat :services:ircs-aggregation-worker:test
.\gradlew.bat build
```

K8S：

```text
kubectl -n ircs-dev rollout status deploy/ircs-content-service
kubectl -n ircs-dev rollout status deploy/ircs-scraper-service
kubectl -n ircs-dev get resourcequota ircs-dev-quota
kubectl -n ircs-dev get httproute
```

## 当前状态

- 已确认 V1 全量行为和 V3 承接点。
- 已完成 content-service raw/unified API、scraper-service internal refetch、content-service
  unified recalculation fallback 代码切片。
- 已完成本地测试、全仓 build、ARM64 镜像推送、content-service rollout 和 ClusterIP
  smoke。
- 21 条接口均按接口粒度完成 `1/1` 复现标记。
- smoke 后临时数据清理为 `0|0|0|0|0`，`ircs-dev` 无 HTTPRoute，配额保持
  `pods=23/24`、`services=19/20`。

## 验证结果

本地验证：

```text
.\gradlew.bat :services:ircs-content-service:test :services:ircs-scraper-service:test :services:ircs-aggregation-worker:test -> BUILD SUCCESSFUL
.\gradlew.bat build -> BUILD SUCCESSFUL
kubectl apply --dry-run=client -f deploy/k8s/dev/*.yaml -> all dev yaml dry-run ok
```

镜像与 rollout：

```text
content-service image: harbor.mnnu.eu.org/ircs/ircs-content-service:dev
content-service digest: sha256:e70bc1e9ca03b80f103775a2eb50ac8978af2af9b1fadb5f79e3cf06f839fb9a
kubectl -n ircs-dev rollout status deploy/ircs-content-service -> successfully rolled out
```

ClusterIP smoke：

```text
MARK 21/21 raw/unified admin API -> passed
POST /api/v1/raw-videos -> 201
raw list/detail/update/delete/batch/sync/renormalize/reenrich/refetch -> passed
POST /api/v1/unified-videos -> 201
unified list/detail/update/delete/batch/sync/recalculate -> passed
pre-cleanup smoke anchors: 1|1|1|1|1
post-cleanup smoke anchors: 0|0|0|0|0
PostgreSQL pg_stat_activity after Hikari compression: 1 active / 7 idle / 8 internal-null
ircs-dev HTTPRoute: none
ResourceQuota: pods=23/24, services=19/20
```
