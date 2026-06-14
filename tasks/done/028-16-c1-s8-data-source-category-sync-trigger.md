# 028-16-C1-S8 data-source category sync trigger task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c1-initialization-readiness-gate.md`
- `tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `tasks/done/028-16-c1-s2b-remote-category-sync-job.md`
- V1 `DataSourceServiceImpl`
- V3 `services/ircs-catalog-service`

## 背景

V1 `DataSourceServiceImpl` 在 `create()`、`update()`、`partialUpdate()` 保存数据源后，
都会 best-effort 调用 `syncCategoriesFromRemote()`，请求资源站 list URL 的 `class[]`，
再把 `type_id/type_name` 写入 raw category。V3 已在 `028-16-C1-S2b` 落地
`CatalogRemoteCategorySyncService`，但目前 admin data-source create/update/patch 成功后
没有自动触发该副作用。

## 目标

- V3 `CatalogService.createDataSource()` 成功后触发远程分类同步。
- V3 `CatalogService.updateDataSource()` 成功后触发远程分类同步。
- V3 `CatalogService.patchDataSource()` 成功且实体存在时触发远程分类同步。
- 同步失败只记录，不影响 data source 写入响应，保持 V1 best-effort 语义。
- 不在启动 seed 中自动外联，不新增公网 HTTPRoute。
- 用 fake no-network 测试固定触发时机和失败隔离。

## 非目标

- 不新增 ops-service 跨服务 HTTP runner。
- 不做真实资源站公网 smoke。
- 不改变已有 `CatalogRemoteCategorySyncService` 的 `class[]` 解析规则。
- 不处理全服务 readiness auto start gate。

## 影响范围

```text
services/ircs-catalog-service/src/main/java/com/prodigalgal/ircs/catalog/**
services/ircs-catalog-service/src/test/java/com/prodigalgal/ircs/catalog/**
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/in-progress/028-16-c1-initialization-readiness-gate.md
tasks/index.md
docs/index.md
```

## 验收标准

```text
MARK 1/5 V1 create/update/partialUpdate sync trigger facts documented in this task: done 2026-06-09
MARK 2/5 catalog createDataSource triggers remote category sync after save: done 2026-06-09
MARK 3/5 catalog updateDataSource and patchDataSource trigger remote category sync after successful write: done 2026-06-09
MARK 4/5 sync failure is best-effort and does not fail data-source write: done 2026-06-09
MARK 5/5 catalog-service tests pass and docs/tasks updated: done 2026-06-09
```

## 测试边界

```text
.\gradlew.bat --no-daemon :services:ircs-catalog-service:test --rerun-tasks
```

## 当前状态

2026-06-09：已完成第一刀实现并归档。

V1 事实：

- `DataSourceServiceImpl.create()` 保存成功后调用 `syncCategoriesFromRemote(savedDataSource)`。
- `DataSourceServiceImpl.update()` 保存成功后调用 `syncCategoriesFromRemote(updatedDataSource)`。
- `DataSourceServiceImpl.partialUpdate()` 保存成功后调用 `syncCategoriesFromRemote(updatedDataSource)`。
- `syncCategoriesFromRemote()` 的异常会被 catch 并记录，不影响 data source 写入响应。

V3 实现结果：

- `CatalogService.createDataSource()` 成功保存后触发 `CatalogRemoteCategorySyncService`。
- `CatalogService.updateDataSource()` 成功保存后触发 `CatalogRemoteCategorySyncService`。
- `CatalogService.patchDataSource()` 仅在数据源存在且成功保存后触发同步。
- 同步通过 Spring transaction synchronization 在提交后执行；没有活动事务时直接执行。
- 同步失败只记录，不让 data source 写接口失败。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-catalog-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\catalog-service-dev.yaml
deployment.apps/ircs-catalog-service configured (server dry run)
service/ircs-catalog-service unchanged (server dry run)
```

后续未完成项：

- 未做真实资源站公网 smoke。
- 未新增 ops-service 跨服务 runner；admin data source 写操作触发链路已补齐。
- 未处理全服务 readiness auto start gate。
