# 037 identity-service member admin API 边界分析

状态：已完成

更新时间：2026-06-06

## 背景

`portal-bff` 和 `admin-bff` 已正式拆分并部署，admin 门户 API 流量已经通过
`admin-bff` 进入 V3 服务。`025-10` 已完成后台登录链路，但 admin 会员管理页仍依赖
V1 单体的 `/api/v1/members/**`：

```text
GET    /api/v1/members
GET    /api/v1/members/{id}/favorites
GET    /api/v1/members/{id}/history
PUT    /api/v1/members/{id}
PUT    /api/v1/members/{id}/status
DELETE /api/v1/members/{id}
```

V1 admin 前端 `membersApi.js` 对这些接口的调用已稳定，后端 V1
`MemberAdminController`/`MemberAdminServiceImpl` 已验证可用。当前切片按无损迁移口径把
该管理面正式拆入 `ircs-identity-service`。

## 目标

- 在 `ircs-identity-service` 承接 V1 后台会员管理 6 条接口。
- 保持 V1 admin 前端字段形状：`MemberDTO` 和 `HistoryRecordDTO`。
- 保持 V1 查询语义：会员列表支持 `keyword`、`status`、`page`、`size`、`sort`。
- 保持 V1 写语义：后台可更新 email、nickname、avatarUrl、password、role、status、
  experience、points、checkInStreak。
- 更新会员状态时同步 `MemberStatusCacheService`。
- 删除会员时清理收藏和观看历史，避免共享迁移表中的外键阻断。
- 通过 `admin-bff` 做 ClusterIP live smoke，每条接口完整复现后各标记 `1/1`。

## 非目标

- 不新增 Pod、Service、HTTPRoute、PVC 或 RabbitMQ topic。
- 不修改 V1 admin 前端。
- 不做物理数据库拆分；本切片仍使用共享 PostgreSQL 迁移桥。
- 不把收藏/历史管理 ownership 永久放到 identity-service；未来物理拆库时可改为
  identity-service 调用 interaction-service internal API。
- 不实现批量会员导入、RBAC、多管理员或审计 UI。

## API 契约

```text
GET /api/v1/members
  query: page, size, sort, keyword, status
  -> Page<MemberDTO>

GET /api/v1/members/{id}/favorites
  query: page, size
  -> Page<HistoryRecordDTO>

GET /api/v1/members/{id}/history
  query: page, size
  -> Page<HistoryRecordDTO>

PUT /api/v1/members/{id}
  body: { email, nickname, avatarUrl, password, role, status, experience, points, checkInStreak }
  -> MemberDTO

PUT /api/v1/members/{id}/status
  body: { status }
  -> MemberDTO

DELETE /api/v1/members/{id}
  -> 204
```

所有 `/api/v1/members/**` 均要求 admin token，继续由 identity-service
`JwtAuthenticationFilter` 识别 `ROLE_ADMIN`。

## 边界图

```text
admin frontend
  -> frontend gateway
  -> admin-bff
  -> ircs-identity-service
       -> members
       -> member_favorites
       -> member_watch_histories
       -> unified_videos / cover_images / source_domains
       -> MemberStatusCacheService / Valkey
```

## 风险

- 收藏和历史表属于 interaction ownership；当前共享数据库桥可复现 V1 行为，但后续物理拆分
  需要转成 internal API。
- 会员删除可能被其他共享表外键阻断；本切片至少清理 V1 同步清理的收藏/历史，并对 dev
  smoke 使用隔离临时会员。
- `sort` 是前端字符串，必须白名单映射到 SQL 列，不能直接拼接。
- admin smoke 需要临时 admin token，必须避免破坏现有开发管理员配置。

## 验收标准

- 本地 task spec 已建立，并挂到 `025` 父任务。
- 6 条 V1 `/api/v1/members/**` API 在 `ircs-identity-service` 中实现。
- 单元测试覆盖列表过滤/排序、更新、状态变更、删除和收藏/历史查询。
- `.\gradlew.bat :services:ircs-identity-service:test` 和 `.\gradlew.bat build` 通过。
- ARM64 镜像 rollout 后 `ircs-identity-service` Ready 1/1。
- ClusterIP smoke 通过 `admin-bff` 覆盖 6 条接口，并清理临时数据。
- 6 条接口完整迁移且 live smoke 复现后标记 `6/6`。
- `ircs-dev` 仍无 HTTPRoute，Pod/Service quota 不增加。

## 完成结果

本切片已完成并归档到：

```text
docs/migrations/033-identity-service-member-admin-api-extraction.md
tasks/done/025-11-identity-service-member-admin-api-mvp.md
```

验证摘要：

```text
.\gradlew.bat :services:ircs-identity-service:test -> PASS
.\gradlew.bat build -> PASS
kubectl apply --dry-run=server -f deploy\k8s\dev\identity-service-dev.yaml -> PASS
kubectl apply --dry-run=server -f deploy\k8s\dev\frontend-bff-dev.yaml -> PASS
ircs-identity-service rollout -> Ready 1/1
identity-service image digest -> sha256:f8e44e063ce3ae32b132b53209bc96c22c0f14e1bed577af806870e528184d66
admin-bff smoke /api/v1/members/** -> MARK 6/6
post_cleanup members|favorites|history|unified|temp_config -> 0|0|0|0|0
HTTPRoute in ircs-dev -> none
ResourceQuota -> pods=28/30, services=24/26
```
