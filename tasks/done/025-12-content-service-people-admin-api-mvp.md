# 025-12 content-service people dictionary admin API MVP 子任务

状态：已完成

父任务：

- `tasks/in-progress/025-v1-route-coverage-closeout-parent.md`

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/038-parallel-subagent-next-route-slices.md`

## 背景

V1 admin 前端演员/导演管理页依赖 `/api/v1/actors/**` 和 `/api/v1/directors/**`。
`admin-bff` 已把这些前缀代理到 `ircs-content-service`，但 V3 content-service 目前只实现
raw/unified admin API，people dictionary 仍缺真实接口。

## 目标

- 在 `ircs-content-service` 复现 V1 actor/director admin API。
- 覆盖 actors 7 条、directors 7 条，共 14 条接口。
- 保持 V1 前端字段形状和分页契约。
- `PUT`/`PATCH` 兼容前端 body 不带 `id` 的实际调用，以 path id 为准。

## 非目标

- 不修改 `admin-bff` 路由。
- 不新增 K8S 资源。
- 不处理 playlist、source-domain、resolver。
- 不删除或修改真实已关联的 actor/director 数据。

## 输入

```text
V1 ActorController / DirectorController
V1 actorsApi.js / directorsApi.js
services/ircs-content-service
```

## 输出

- `services/ircs-content-service/.../content/people/**`
- 对应单元测试。
- 子任务完成结果和 smoke 记录。

## 影响范围

允许写入：

```text
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/people
services/ircs-content-service/src/test/java/com/prodigalgal/ircs/content/people
tasks/done/025-12-content-service-people-admin-api-mvp.md
```

禁止写入：

```text
services/ircs-admin-bff
services/ircs-catalog-service
services/ircs-config-service
deploy/k8s
```

## 验收标准

- actor create/list/all/detail/update/patch/delete 可用。
- director create/list/all/detail/update/patch/delete 可用。
- 单元测试通过。
- 后续主 agent 通过 admin-bff live smoke 完成 `14/14` 标记。

## 测试边界

worker 计划执行：

```text
.\gradlew.bat :services:ircs-content-service:test
```

主 agent 后续执行全仓 build、ARM64 rollout 和 live smoke。

## 实现结果

2026-06-06 worker 实现完成；主 agent 已完成全仓 build、ARM64 rollout 和
`admin-bff` live smoke。

- 新增 `content/people` admin API 实现，覆盖 actors/directors 各 7 条 V1 路由。
- `PUT`/`PATCH` 均以 path id 为准，兼容 V1 admin 前端 body 不带 `id` 的调用。
- `POST` 保留 V1 新建语义：body 带 `id` 时返回 `400`。
- `GET /all` 按 `name asc` 返回无分页数组；分页列表支持 `name` 模糊过滤和 `createdAt,desc` 等安全排序字段。
- 删除前检查 `video_actors`/`video_directors` 与 `unified_video_actors`/`unified_video_directors` 关联；存在关联时返回 `409`，只允许删除未关联临时字典数据。

实现文件：

```text
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/people/ActorAdminController.java
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/people/DirectorAdminController.java
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/people/PeopleAdminDtos.java
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/people/PeopleAdminRepository.java
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/people/PeopleAdminService.java
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/people/PeopleResource.java
```

测试文件：

```text
services/ircs-content-service/src/test/java/com/prodigalgal/ircs/content/people/PeopleAdminControllerTest.java
services/ircs-content-service/src/test/java/com/prodigalgal/ircs/content/people/PeopleAdminRepositoryTest.java
services/ircs-content-service/src/test/java/com/prodigalgal/ircs/content/people/PeopleAdminServiceTest.java
```

## 验证结果

2026-06-06 执行：

```powershell
.\gradlew.bat :services:ircs-content-service:test
```

结果：

```text
BUILD SUCCESSFUL in 7s
12 actionable tasks: 3 executed, 9 up-to-date
```

验证覆盖：

- controller 单元测试覆盖 actors/directors 各 7 条目标路由。
- controller 单元测试覆盖 `PUT`/`PATCH` body 不带 `id` 时仍按 path id 调用服务。
- service 单元测试覆盖 create body 带 `id` 的 V1 兼容拒绝语义，以及 update/patch 使用 path id。
- repository 单元测试覆盖有关联 actor 时不执行删除，以及未关联 director 可删除。

主 agent 集成验证：

```text
.\gradlew.bat :services:ircs-content-service:test -> PASS
.\gradlew.bat build -> PASS
image:  harbor.mnnu.eu.org/ircs/ircs-content-service@sha256:069bdf026e19c30dbbd653d784525d0b7ddccb49b1d1948d564706c6f6e2e8d1
rollout: ircs-content-service Ready 1/1
smoke:  admin-bff MARK 14/14
cleanup: actors/directors Codex smoke residue 0/0
```

live smoke 覆盖：

```text
MARK 1/14 POST /api/v1/actors
MARK 2/14 GET /api/v1/actors
MARK 3/14 GET /api/v1/actors/all
MARK 4/14 GET /api/v1/actors/{id}
MARK 5/14 PUT /api/v1/actors/{id}
MARK 6/14 PATCH /api/v1/actors/{id}
MARK 7/14 DELETE /api/v1/actors/{id}
MARK 8/14 POST /api/v1/directors
MARK 9/14 GET /api/v1/directors
MARK 10/14 GET /api/v1/directors/all
MARK 11/14 GET /api/v1/directors/{id}
MARK 12/14 PUT /api/v1/directors/{id}
MARK 13/14 PATCH /api/v1/directors/{id}
MARK 14/14 DELETE /api/v1/directors/{id}
```

迁移记录：

```text
docs/migrations/034-content-service-people-admin-api-extraction.md
```
