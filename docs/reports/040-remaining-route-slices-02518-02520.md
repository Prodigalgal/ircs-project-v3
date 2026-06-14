# 040 remaining route slices 025-18 to 025-20

状态：已完成

更新时间：2026-06-07

## 背景

`025-16` 和 `025-17` 已完成后，`025` 主覆盖口径更新为：

```text
V1 routes: 203
V3 routes: 201
missing:   13
```

为继续推进完整迁移，本报告按当前 V1/V3 Controller 源码逐条复核下一批未完成路径。
由于历史审计脚本会过滤部分高风险运维命令和 V3-only alias，逐条源码扫描发现的候选
未实现路径多于主口径的 13 条。后续完成标记以“接口在 V3 服务中实现并通过
`admin-bff` 或对应 BFF live smoke 复现”为准。

## 候选缺口分组

```text
025-18 magnet-service admin provider/search contract: 3 routes
025-19 identity-service portal avatar upload contract: 1 route
025-20 ops-service guarded operations/debug/stream contract: 19 candidate routes
```

## 完成结果

2026-06-07 已完成三组切片的本地测试、ARM64 镜像推送、`ircs-dev` rollout 和 BFF
live smoke：

```text
025-18 magnet-service admin provider/search contract: MARK 3/3
025-19 identity-service portal avatar upload contract: MARK 1/1
025-20 ops-service guarded operations/debug/stream contract: MARK 19/19
```

本轮额外修复 `ircs-portal-bff` 和 `ircs-admin-bff` 的 multipart 代理行为：
关闭 BFF 自身的 `spring.servlet.multipart` 预解析，让后端服务按原始请求体解析
multipart。该修复通过 `POST /api/portal/profile/avatar` live smoke 复现。

按 `025` HTTP Controller + BFF smoke 口径，当前剩余未复现接口为：

```text
strict HTTP/BFF-smoked missing: 0
```

说明：`025-20` 的 19 条 ops candidate route 包含历史主审计中为避免高风险误操作而
过滤或延后计数的运维命令。本轮按 guarded dev contract 接入：维护类接口创建可观察
session/SSE，metadata debug 为 dry-run，磁力搜索不访问外部资源站。因此 HTTP 接口
层已可复现，但真实生产级全量 maintenance runner、外部 provider 搜索和物理数据拆分
仍属于后续行为深度迁移。

## ASCII 切片图

```text
                    +-------------------+
                    | admin portal      |
                    +---------+---------+
                              |
                              v
                    +-------------------+
                    | ircs-admin-bff    |
                    +----+---------+----+
                         |         |
              +----------+         +------------------+
              |                                     |
              v                                     v
 +-------------------------+             +-------------------------+
 | ircs-magnet-service     |             | ircs-ops-service        |
 | 025-18                  |             | 025-20                  |
 | provider create/update  |             | DLQ retry/discard       |
 | unified magnet search   |             | traffic reset           |
 +------------+------------+             | maintenance init        |
              |                          | dashboard stream        |
              v                          | metadata debug          |
       PostgreSQL bridge                 +------------+------------+
                                                   |
                                                   v
                                         PostgreSQL/RabbitMQ/Valkey

                    +-------------------+
                    | portal frontend   |
                    +---------+---------+
                              |
                              v
                    +-------------------+
                    | ircs-portal-bff   |
                    +---------+---------+
                              |
                              v
                    +-------------------+
                    | identity-service  |
                    | 025-19 avatar     |
                    +-------------------+
```

## 025-18 magnet-service admin provider/search contract

目标接口：

```text
POST /api/v1/magnet-providers
PUT  /api/v1/magnet-providers/{id}
POST /api/v1/magnets/search/unified/{id}
```

边界：

- 复用 `ircs-magnet-service` 和现有 `admin-bff` `/api/v1/magnet-providers`、`/api/v1/magnets` 转发。
- provider create/update 写入 `magnet_providers`。
- unified search 创建 `magnet_search_jobs`，dev MVP 不访问外部资源站。
- 不新增 Pod、Service、HTTPRoute、PVC 或中间件。

## 025-19 identity-service portal avatar upload contract

目标接口：

```text
POST /api/portal/profile/avatar
```

边界：

- 复用 `ircs-identity-service`、`ircs-portal-bff` 和现有 member profile auth。
- dev MVP 可写入本地临时存储或复用 storage-service media URL 形状，但必须更新 `members.avatar_url`。
- 上传大小、空文件、content type 至少复现 V1 基础校验。
- 不新增 Pod、Service、HTTPRoute、PVC 或中间件。

## 025-20 ops-service guarded operations/debug/stream contract

候选接口：

```text
GET    /api/v1/dashboard/metrics/stream
GET    /api/v1/debug/metadata/search
POST   /api/v1/ops/traffic/reset
POST   /api/v1/ops/dlq/{id}/retry
POST   /api/v1/ops/dlq/batch-retry
DELETE /api/v1/ops/dlq/{id}
POST   /api/v1/ops/dlq/batch-discard
POST   /api/v1/ops/maintenance/sanitize/init
POST   /api/v1/ops/maintenance/aggregation/reset-init
POST   /api/v1/ops/maintenance/unified/recalculate-init
POST   /api/v1/ops/maintenance/area/clean-init
POST   /api/v1/ops/maintenance/language/clean-init
POST   /api/v1/ops/maintenance/llm/sanitize/init
POST   /api/v1/ops/maintenance/llm/area/init
POST   /api/v1/ops/maintenance/llm/genre/init
POST   /api/v1/ops/maintenance/llm/category/init
POST   /api/v1/ops/maintenance/search/reindex-raw/init
POST   /api/v1/ops/maintenance/search/reindex-unified/init
POST   /api/v1/ops/maintenance/trend/sync
```

边界：

- 复用 `ircs-ops-service` 和现有 `admin-bff` `/api/v1/dashboard`、`/api/v1/ops`、`/api/v1/debug` 转发。
- DLQ retry/discard 需要更新 `failed_messages` 状态；retry 可重新发布 RabbitMQ 消息，但 smoke 必须使用临时队列/交换或可控记录。
- traffic reset 只删除 `traffic:limit:*` 目标 key。
- maintenance init 先复现 V1 session 创建和 SSE 事件契约；真实重建索引、LLM 清洗、全量聚合等长任务可以由后续 runner 接管，但接口返回和流式状态必须可验证。
- metadata debug 不访问公网 provider；dev smoke 使用 provider unavailable/dry-run 响应验证契约。
- 不新增 Pod、Service、HTTPRoute、PVC 或中间件。

## 验收口径

- 每个子任务先通过本地单元测试，再推送 ARM64 dev 镜像。
- 所有 live smoke 通过 BFF 或 ClusterIP 路径，不创建公网 HTTPRoute。
- 每条接口只有在 V3 完整实现且 live smoke 复现后才标记 `1/1`。
- 完成后回写 `docs/reports/025-v1-v3-route-coverage-audit.md`、`tasks/index.md`、
  `docs/index.md` 和对应迁移记录。
