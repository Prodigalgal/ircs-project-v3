# 025-02 portal-service explore/detail MVP

状态：已完成

完成时间：2026-06-05

父任务：`tasks/in-progress/025-v1-route-coverage-closeout-parent.md`

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/028-portal-service-explore-detail-boundary-analysis.md`
- `docs/migrations/020-portal-service-read-composition-extraction.md`
- `docs/migrations/025-portal-service-explore-detail-extraction.md`

## 背景

`ircs-portal-service` 已完成首页和元数据只读 MVP。V1 前台内容页仍有
`/api/portal/explore` 与 `/api/portal/movies/{id}` 未迁移，影响前端浏览、筛选和详情查看。

## 目标

已实现并验证：

```text
GET /api/portal/explore
GET /api/portal/movies/{id}
```

## 非目标

- 不迁移会员登录、注册、profile、avatar、check-in。
- 不迁移后台会员管理 `/api/v1/members/**`。
- 不新增搜索服务 HTTP client。
- 不新增 K8S Service、HTTPRoute、Deployment。
- 不做物理 DB 拆分。

## 输出

- `PageResponse`。
- `PortalMovieDetailResponse`。
- `PortalController` 新增 explore/detail route。
- `PortalQueryService` 新增 explore/detail 查询入口。
- `JdbcPortalRepository` 新增 SQL explore 和 detail 多表聚合。
- Controller/Service 单元测试。
- ARM64 镜像推送并完成 `ircs-dev` rollout。
- ClusterIP smoke 完成并清理临时数据。

## 验收结果

自动测试：

```text
.\gradlew.bat :services:ircs-portal-service:test
BUILD SUCCESSFUL

.\gradlew.bat build
BUILD SUCCESSFUL
```

K8S：

```text
image: harbor.mnnu.eu.org/ircs/ircs-portal-service:dev
imageID: harbor.mnnu.eu.org/ircs/ircs-portal-service@sha256:f3c0ac1364a0517821f1a512364282683682ded50c4bdb1ef620d162a6bab32a
rollout: successfully rolled out
HTTPRoute: none
ResourceQuota: pods=23/24, services=19/20
```

Smoke：

```text
explore: 200, totalElements=1
detail: 200, sources + episode URL + approved magnet present
notFound: 404
cleanup_remaining unified,raw,episodes,magnet,category=0,0,0,0,0
```

## 剩余事项

- SQL explore 尚未做到 V1 ES relevance 排序完全等价。
- portal 相关剩余 auth/profile/member-admin 缺口需要分别归属到 identity/admin 子任务。

