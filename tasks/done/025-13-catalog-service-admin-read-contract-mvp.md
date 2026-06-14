# 025-13 catalog-service admin read contract MVP 子任务

状态：已完成

父任务：

- `tasks/in-progress/025-v1-route-coverage-closeout-parent.md`

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/038-parallel-subagent-next-route-slices.md`

## 背景

`ircs-catalog-service` 已完成 summary/read-only MVP，但 V1 admin 前端需要
`data-sources`、`standard-*` 和 `raw-*` 的分页、详情、过滤和 trace 读契约。当前部分路径
虽然存在，但响应形状仍是 summary list，不等价于 V1 admin 页面契约。

## 目标

- 补齐或纠正 catalog-service 的 V1 admin read contract。
- 优先覆盖分页、详情、过滤和 `raw-languages/{id}/trace`。
- 保留 `/api/v1/catalog/**` summary API，不破坏内部消费者。

## 非目标

- 不实现 create/update/delete。
- 不实现 `data-sources/fetch-sample`。
- 不修改 content-service、config-service 或 admin-bff。
- 不新增 K8S 资源。

## 输入

```text
V1 DataSourceController
V1 StandardCategory/Genre/Area/Language Controller
V1 RawCategory/Genre/Area/Language Controller
services/ircs-catalog-service
```

## 输出

- catalog read DTO、page DTO、Controller/Service/Repository 增量。
- 对应单元测试。
- 子任务完成结果和 smoke 建议。

## 影响范围

允许写入：

```text
services/ircs-catalog-service/src/main/java/com/prodigalgal/ircs/catalog
services/ircs-catalog-service/src/test/java/com/prodigalgal/ircs/catalog
tasks/done/025-13-catalog-service-admin-read-contract-mvp.md
```

禁止写入：

```text
services/ircs-content-service
services/ircs-config-service
services/ircs-admin-bff
deploy/k8s
```

## 验收标准

- `GET /api/v1/data-sources?page=0&size=...` 返回 V1 兼容 Page。
- `GET /api/v1/data-sources/{id}` 返回详情。
- `GET /api/v1/raw-categories|raw-genres|raw-areas|raw-languages` 支持 V1 过滤。
- `GET /api/v1/raw-languages/{id}/trace` 返回 trace。
- `GET /api/v1/standard-categories/page` 和标准字典详情读契约可用。
- 单元测试通过。

## 测试边界

worker 计划执行：

```text
.\gradlew.bat :services:ircs-catalog-service:test
```

主 agent 后续执行全仓 build、ARM64 rollout 和 live smoke。

## 实现结果

worker 已完成 `ircs-catalog-service` 只读 admin contract MVP，写入范围限定在
`services/ircs-catalog-service/src/main/java/com/prodigalgal/ircs/catalog/**` 和
`services/ircs-catalog-service/src/test/java/com/prodigalgal/ircs/catalog/**`。

本轮实现：

- 保留 `/api/v1/catalog/**` summary API，继续返回原 summary list。
- 新增 `/api/v1/data-sources` Page 读契约和 `/api/v1/data-sources/{id}` detail。
- 新增 `/api/v1/standard-categories/page`、`/api/v1/standard-categories/{id}`。
- 新增 `/api/v1/standard-genres/page`、`/api/v1/standard-genres/{id}`。
- 保留并纠正 `/api/v1/standard-categories`、`/api/v1/standard-categories/all`、
  `/api/v1/standard-genres`、`/api/v1/standard-areas`、`/api/v1/standard-areas/page`、
  `/api/v1/standard-languages` 只读返回。
- 新增 `/api/v1/raw-categories` Page/filter 和 `/api/v1/raw-categories/{id}` detail。
- 新增 `/api/v1/raw-genres` Page/filter 和 `/api/v1/raw-genres/{id}` detail。
- 新增 `/api/v1/raw-areas` Page/filter 和 `/api/v1/raw-areas/{id}` detail。
- 新增 `/api/v1/raw-languages` Page/filter、`/api/v1/raw-languages/{id}` detail 和
  `/api/v1/raw-languages/{id}/trace` trace。
- 未实现 create/update/delete/fetch-sample，符合本子任务非目标。

## 验证结果

本地执行：

```text
.\gradlew.bat :services:ircs-catalog-service:test
```

结果：

```text
BUILD SUCCESSFUL in 5s
10 actionable tasks: 3 executed, 7 up-to-date
```

单元测试覆盖：

- `/api/v1/catalog/**` summary 路由仍调用 summary service。
- `data-sources` Page/detail。
- `standard-categories` Page/detail。
- `standard-genres` Page/detail。
- `raw-categories` V1 filter/detail。
- `raw-genres`、`raw-areas` Page/filter，`raw-areas` detail。
- `raw-languages` Page/filter/detail/trace。

主 agent 集成验证：

```text
.\gradlew.bat :services:ircs-catalog-service:test -> PASS
.\gradlew.bat build -> PASS
image:  harbor.mnnu.eu.org/ircs/ircs-catalog-service@sha256:4d6a3536242ab5c6c1fbd68cf215358f607a3c2eafba0e4ffa228ce47364b625
rollout: ircs-catalog-service Ready 1/1
smoke:  admin-bff MARK 21/21
cleanup: data_sources/raw_videos/raw_languages Codex smoke residue 0/0/0
```

主 agent 修复：

- live schema 复核发现 dev PostgreSQL 已将 V1 `videos` 重命名为 `raw_videos`。
- `raw-languages/{id}/trace` SQL 已从 `join videos` 修正为 `join raw_videos`。
- 新增 `JdbcCatalogRepositoryTest` 锁定 trace SQL 不再回退到旧表名。
- live smoke 使用临时 `detail_params={"ids":"{id}"}` 复核 `originalUrl` 组装。

live smoke 覆盖：

```text
MARK 1/21 GET /api/v1/data-sources
MARK 2/21 GET /api/v1/data-sources/{id}
MARK 3/21 GET /api/v1/standard-categories
MARK 4/21 GET /api/v1/standard-categories/all
MARK 5/21 GET /api/v1/standard-categories/page
MARK 6/21 GET /api/v1/standard-categories/{id}
MARK 7/21 GET /api/v1/standard-genres
MARK 8/21 GET /api/v1/standard-genres/page
MARK 9/21 GET /api/v1/standard-genres/{id}
MARK 10/21 GET /api/v1/standard-areas
MARK 11/21 GET /api/v1/standard-areas/page
MARK 12/21 GET /api/v1/standard-languages
MARK 13/21 GET /api/v1/raw-categories
MARK 14/21 GET /api/v1/raw-categories/{id}
MARK 15/21 GET /api/v1/raw-genres
MARK 16/21 GET /api/v1/raw-genres/{id}
MARK 17/21 GET /api/v1/raw-areas
MARK 18/21 GET /api/v1/raw-areas/{id}
MARK 19/21 GET /api/v1/raw-languages
MARK 20/21 GET /api/v1/raw-languages/{id}/trace
MARK 21/21 GET /api/v1/raw-languages/{id}
```

迁移记录：

```text
docs/migrations/035-catalog-service-admin-read-contract-extraction.md
```
