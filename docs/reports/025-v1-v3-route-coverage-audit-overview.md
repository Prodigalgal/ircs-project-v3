# 025 V1/V3 路由覆盖差距审计报告

状态：进行中

更新时间：2026-06-07

## 背景

`011` MVP 服务接入父任务已经将 18 个目标业务服务部署到 `ircs-dev`，并完成各自 MVP
smoke。但完整微服务迁移还需要逐步关闭 V1 单体 Controller、队列、后台任务和数据
ownership 的剩余差距。本报告先建立 HTTP Controller 路由覆盖基线。

## 审计方法

输入：

```text
V1: D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-backend
V3: D:\WorkSpace\Project\ircs\ircs-project-v3\services
```

抽取规则：

- 扫描 `@RestController` / `@Controller` Java 文件。
- 解析 class-level `@RequestMapping`。
- 解析 method-level `@GetMapping`、`@PostMapping`、`@PutMapping`、`@DeleteMapping`、
  `@PatchMapping` 和 `@RequestMapping(method=...)`。
- 比对键为 `HTTP_METHOD + normalized_path`。
- 规范化尾部 `/`，避免 `/api/v1/foo` 与 `/api/v1/foo/` 的假差异。

限制：

- 本轮只审计 Controller HTTP 路由，不证明 Service 内部行为完全等价。
- 不审计前端 route、队列消费者、scheduler、数据库 migration 和权限表达式覆盖。
- V3 新增的 internal route 不计为 V1 缺口。

## 总览

```text
V1 backend Controller routes: 203
V3 service Controller routes: 54
Direct missing routes after path normalization: 160
V3-only/internal or alias routes: 11
```

当前 post-MVP 已完成 `025-01` 到 `025-20` 后的推进口径：

```text
V3 service Controller routes: 224
Direct missing routes:         0
```

说明：`025-18` 到 `025-20` 关闭了 23 条源码复核 candidate route，其中 `025-20`
包含历史主审计为避免高风险误操作而延后计数的 ops command route。本报告当前
`missing=0` 仅代表 HTTP Controller 路径已在 V3/BFF live smoke 口径下可复现，不代表
生产级 maintenance runner、外部 provider 搜索、队列消费者和物理数据拆分已经完成。

## 初始缺口按 V1 模块分布

| V1 模块 | 缺口路由数 | 目标服务方向 |
| --- | ---: | --- |
| `content` | 49 | `ircs-content-service` |
| `system` | 36 | `ircs-identity-service` / `ircs-config-service` / `ircs-credential-service` / `ircs-ops-service` |
| `datasource` | 20 | `ircs-catalog-service` / `ircs-task-service` / `ircs-scraper-service` |
| `metadata` | 14 | `ircs-catalog-service` / `ircs-normalization-worker` / `ircs-ops-service` |
| `interaction` | 12 | `ircs-interaction-service` |
| `portal` | 9 | `ircs-portal-service` / `ircs-identity-service` |
| `task` | 8 | `ircs-task-service` |
| `storage` | 7 | `ircs-storage-service` |
| `magnet` | 3 | `ircs-magnet-service` |
| `scraper` | 2 | `ircs-scraper-service` |

## 初始缺口按 Controller 分布 Top

| V1 Controller | 缺口数 | 初步优先级 |
| --- | ---: | --- |
| `content/RawVideoController` | 13 | P1 |
| `system/MaintenanceController` | 12 | P3，高风险 |
| `content/UnifiedVideoController` | 8 | P1 |
| `task/CollectionTaskController` | 8 | P2 |
| `content/ActorController` | 7 | P2 |
| `content/DirectorController` | 7 | P2 |
| `storage/CoverImageController` | 7 | P2 |
| `content/VideoResolverSourceController` | 6 | P2 |
| `datasource/DataSourceController` | 6 | P2 |
| `metadata/StandardCategoryController` | 6 | P2 |
| `portal/MemberAdminController` | 6 | P2 |
| `interaction/PortalInteractionController` | 5 | P1 |
| `system/CredentialController` | 5 | P2 |
| `system/DlqController` | 5 | P3，需谨慎 |

## 第一批建议切片

### 025-01 search-service portal query API

状态：已完成。迁移记录见
`docs/migrations/023-search-service-portal-query-api-extraction.md`。

目标路由：

```text
GET /api/portal/search/suggest
GET /api/portal/search/recommendations/{videoId}
```

原因：

- V1 `SearchController` 只有 2 个缺口，边界小。
- 目标服务已拥有 `UnifiedVideoSearchDocument`、ES mapping 和索引同步能力。
- 只读 ES，不新增 RabbitMQ 消费者，不修改业务表。
- 已通过单元测试、全仓 build、ARM64 rollout 和 ClusterIP smoke 验证。复核后 search
  模块缺口为 0。

### 后续候选

```text
025-04 task-service create/update/start/pause/resume/stop/logs
025-05 content-service raw/unified video admin API
025-06 storage-service cover image admin API
```

