# 025-18 magnet-service admin write/search contract MVP 子任务

状态：已完成

父任务：

- `tasks/in-progress/025-v1-route-coverage-closeout-parent.md`

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/040-remaining-route-slices-02518-02520.md`

## 背景

`ircs-magnet-service` 当前已覆盖 provider list/detail 和 unified approved magnets read
contract，但 V1 admin 仍有 provider create/update 和 unified magnet search 触发接口。

## 目标

- 在 `ircs-magnet-service` 复现 `POST /api/v1/magnet-providers`。
- 在 `ircs-magnet-service` 复现 `PUT /api/v1/magnet-providers/{id}`。
- 在 `ircs-magnet-service` 复现 `POST /api/v1/magnets/search/unified/{id}`。
- 保持 V1 provider DTO 字段形状和 created location/id mismatch 基础语义。

## 非目标

- 不访问外部磁力资源站。
- 不新增 provider worker、Pod、Service、HTTPRoute、PVC 或中间件。
- 不修改 `admin-bff` 路由。
- 不修改 identity/content/ops 服务。

## 输入

```text
V1 MagnetProviderController / MagnetProviderService / MagnetProviderDTO
V1 MagnetSearchController / MagnetSearchService / MagnetSearchJobDTO
services/ircs-magnet-service
```

## 输出

- magnet provider/search write DTO、Controller/Service/Repository 增量。
- 对应单元测试。
- `admin-bff` live smoke 脚本或主 agent smoke 建议。

## 影响范围

允许写入：

```text
services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet
services/ircs-magnet-service/src/test/java/com/prodigalgal/ircs/magnet
tasks/done/025-18-magnet-service-admin-write-search-contract-mvp.md
```

禁止写入：

```text
services/ircs-admin-bff
services/ircs-identity-service
services/ircs-ops-service
deploy/k8s
```

## 验收标准

- 3 条接口可用并通过单元测试。
- dev search 接口创建可追踪 `magnet_search_jobs`，不访问外部资源站。
- 后续主 agent 通过 `admin-bff` live smoke 完成 `3/3` 标记。

## 测试边界

worker 计划执行：

```text
.\gradlew.bat :services:ircs-magnet-service:test
```

主 agent 后续执行全仓 build、ARM64 rollout 和 live smoke。

## 本轮实施计划（2026-06-07）

计划在白名单范围内完成：

- `MagnetController` 增加 provider create/update 和 unified search trigger 路由。
- `MagnetQueryService` 增加 admin write/search 事务边界、provider id 校验、基础 payload 校验。
- `JdbcMagnetRepository` 增加 `magnet_providers` insert/update 与 dev MVP `magnet_search_jobs` 写入。
- 新增或扩展 magnet package 内 DTO，保持 provider read DTO 字段 shape，search 返回可追踪 job DTO。
- 扩展 `MagnetControllerTest`，覆盖 3 条新增接口、created Location、update id mismatch 和 search job 响应。

本轮明确不进入：

- 不访问外部磁力资源站。
- 不写 `magnet_provider_runs` 或 `magnet_links`。
- 不修改 `admin-bff`、identity、ops、deploy/k8s 或索引文件。

## 本轮实施结果（2026-06-07）

已完成：

- `POST /api/v1/magnet-providers`：新增 provider create contract，返回 `201 Created` 和 `Location: /api/v1/magnet-providers/{id}`，响应体沿用 provider DTO 字段 shape。
- `PUT /api/v1/magnet-providers/{id}`：新增 provider update contract，body `id` 可为空；非空时必须与 path id 一致，写入 `magnet_providers`。
- `POST /api/v1/magnets/search/unified/{id}`：新增 dev MVP search trigger，先校验 `unified_videos` 存在，然后创建可追踪 `magnet_search_jobs` 记录。
- Search dev MVP 不访问外部 provider，不写 `magnet_provider_runs`，不写 `magnet_links`；job 使用 `trigger_type=ADMIN_MANUAL`、`status=SKIPPED`、`skipped_reason=DEV_MVP_EXTERNAL_SEARCH_DISABLED`。
- 单元测试覆盖新增 3 条接口、created Location、provider update route、update id mismatch、missing unified 404 和 search job 创建。

主 agent 已完成后续验收：

- 新增 `scripts/smoke/admin-bff-02518-smoke.ps1`。
- 已通过 `admin-bff` 复现 3 条接口。
- Smoke 创建并清理临时 `magnet_providers`、`unified_videos` 和 `magnet_search_jobs` 数据。

## 本轮测试结果（2026-06-07）

已执行：

```text
.\gradlew.bat :services:ircs-magnet-service:test
```

结果：

```text
BUILD SUCCESSFUL in 5s
10 actionable tasks: 3 executed, 7 up-to-date
```

主 agent 集成验证：

```text
.\gradlew.bat :services:ircs-magnet-service:test :services:ircs-identity-service:test :services:ircs-ops-service:test
BUILD SUCCESSFUL

.\gradlew.bat build
BUILD SUCCESSFUL
```

ARM64 rollout：

```text
deployment/ircs-magnet-service successfully rolled out
imageID: harbor.mnnu.eu.org/ircs/ircs-magnet-service@sha256:31c72a41f0d2d70967df00931b8037fa74188d7689b7556f8b508142d763b4ea
```

Live smoke：

```text
MARK 1/3 POST /api/v1/magnet-providers
MARK 2/3 PUT /api/v1/magnet-providers/{id}
MARK 3/3 POST /api/v1/magnets/search/unified/{id}
```

完成计数：`3/3`。
