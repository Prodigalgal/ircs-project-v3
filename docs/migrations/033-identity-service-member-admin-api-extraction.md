# 033 identity-service member admin API extraction

状态：已完成

更新时间：2026-06-06

## 背景

V1 admin 门户的会员管理页依赖 `/api/v1/members/**` 6 条接口。`025-10` 已经把
admin auth 迁入 `ircs-identity-service`，并让 `admin-bff` 可以代理后台登录链路。本轮继续
按 V1 `MemberAdminController`、`MemberAdminServiceImpl` 和 admin 前端
`membersApi.js` 复现会员后台管理接口。

## 迁移范围

```text
GET    /api/v1/members
GET    /api/v1/members/{id}/favorites
GET    /api/v1/members/{id}/history
PUT    /api/v1/members/{id}
PUT    /api/v1/members/{id}/status
DELETE /api/v1/members/{id}
```

## 服务边界

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

本轮复用已部署的 `ircs-identity-service` 和 `ircs-admin-bff`，不新增 Pod、Service 或
HTTPRoute。收藏/历史查询仍通过共享 PostgreSQL 迁移桥读取 interaction 相关表；后续物理拆库
时应转为 interaction-service internal API。

## 实现结果

- 新增 `MemberAdminController`、`MemberAdminService`、`MemberAdminRepository` 和
  `JdbcMemberAdminRepository`。
- 新增 V1 兼容分页响应、会员后台 DTO、收藏/历史 DTO 与封面 URL 解析。
- 会员列表支持 `keyword`、`status`、`page`、`size`、`sort`，并对白名单字段做 SQL
  排序映射。
- 后台更新支持 email、nickname、avatarUrl、password、role、status、experience、
  points、checkInStreak。
- 状态变更和删除会同步 `MemberStatusCacheService`。
- 删除会员前清理收藏和观看历史，保持 V1 显式删除语义。

## 验证结果

```text
.\gradlew.bat :services:ircs-identity-service:test -> PASS
.\gradlew.bat build -> PASS
kubectl apply --dry-run=server -f deploy\k8s\dev\identity-service-dev.yaml -> PASS
kubectl apply --dry-run=server -f deploy\k8s\dev\frontend-bff-dev.yaml -> PASS
kubectl -n ircs-dev rollout status deployment/ircs-identity-service --timeout=240s -> PASS
```

ARM64 dev 镜像：

```text
harbor.mnnu.eu.org/ircs/ircs-identity-service:dev
sha256:f8e44e063ce3ae32b132b53209bc96c22c0f14e1bed577af806870e528184d66
```

ClusterIP smoke 通过 `admin-bff -> ircs-identity-service` 完成：

```text
MARK 1/6 GET /api/v1/members -> 200 total=1
MARK 2/6 GET /api/v1/members/{id}/favorites -> 200 total=1 title=Codex Member Admin Smoke
MARK 3/6 GET /api/v1/members/{id}/history -> 200 total=1 episode=Episode 1
MARK 4/6 PUT /api/v1/members/{id} -> 200 email=codex-member-smoke-updated-*.example.com points=99
MARK 5/6 PUT /api/v1/members/{id}/status -> 200 status=BANNED
MARK 6/6 DELETE /api/v1/members/{id} -> 204
```

清理结果：

```text
cleanup_check members|favorites|history|unified=0|0|0|1
post_cleanup members|favorites|history|unified|temp_config=0|0|0|0|0
```

资源与冲突检查：

```text
HTTPRoute in ircs-dev -> none
ResourceQuota -> pods=28/30, services=24/26
identity-service Ready 1/1
admin-bff Ready 1/1
```

## 路由覆盖结果

本轮新增 6 条 V1 兼容 Controller route：

```text
V3 service Controller routes: 111 -> 117
Direct missing routes:         103 -> 97
```

6 条接口均已完成接口级复现标记，总计 `6/6`。
