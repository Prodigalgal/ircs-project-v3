# 028-16-C1-S2b remote category sync job task spec

状态：完成第一刀

父任务：

- `tasks/in-progress/028-16-c1-initialization-readiness-gate.md`
- `tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `tasks/in-progress/028-16-c1-s2-data-source-preset-seed.md`
- V1 `DataSourceServiceImpl.syncCategoriesFromRemote`
- V1 `DataSourceUrlBuilder`
- V1 `StandardCategoryServiceImpl.resolveRawCategory`

## 背景

C1-S2 已经把 V1 的 25 个默认 data source 配置和 `default_mapping.json` seed 到
catalog-service，但未复刻 V1 在创建/更新 data source 后立即远程同步 `class[]`
分类的行为。该行为会产生外部 HTTP 请求，不能继续放在启动 initializer 中，否则会让
K8S/dev 启动阶段产生外部流量和不可控延迟。

## 目标

- 在 catalog-service 内提供 dev-safe 的远程分类同步服务逻辑。
- 构造与 V1 等价的 list URL：`baseUrl + listPath + limit=20 + pagesize=20`，并解析
  `listParams` 中的 `{page}`、`{t}`、`{h}`、`{wd}` 占位符。
- 解析响应根节点 `class[]`，读取 `type_id`、`type_name`，写入 `raw_category`。
- 复刻 V1 raw category 语义：按 `(data_source_id, source_code)` 幂等；已有行可更新
  `source_name`，只在 `category_id` 为空时做标准分类推断，不覆盖用户手工映射。
- 提供 fake-first 测试，不访问真实资源站。

## 非目标

- 不在服务启动时自动外联。
- 不新增 HTTPRoute 或公网入口。
- 不调度全量定时任务；本刀只完成可控 service/job 逻辑和本地测试。
- 不实现 C3 的统一 outbound HTTP contract；本刀先沿用 catalog-service 当前
  `CatalogFetchSampleClient`，后续可切到 shared outbound client。

## 影响范围

```text
services/ircs-catalog-service/**
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
docs/index.md
tasks/index.md
tasks/in-progress/028-16-c1-initialization-readiness-gate.md
```

## 验收标准

```text
MARK 1/5 V1 remote category sync contract documented: done
MARK 2/5 catalog-service can build V1-equivalent category list URL and parse class[]: done
MARK 3/5 raw_category upsert preserves existing category_id and updates missing mapping only: done
MARK 4/5 catalog-service fake HTTP/repository tests pass without public network: done
MARK 5/5 docs/tasks indexes and 089 inventory updated: done
```

## 测试边界

```text
.\gradlew.bat --no-daemon :services:ircs-catalog-service:test --rerun-tasks
```

## 设计约束

- 远程同步必须由显式方法触发，不在 `ApplicationRunner` 中自动执行。
- HTTP 异常、JSON 解析失败、缺失 `class[]` 均不回滚 data source 配置。
- 用户已映射的 `raw_category.category_id` 不能被自动推断覆盖。

## 实现结果

2026-06-08 已完成第一刀：

- 新增 `CatalogRemoteCategorySyncService`，显式触发 data source 远程分类同步；不在
  `ApplicationRunner` 中自动外联。
- 新增 `CatalogCategoryMappingPolicy`，按 V1 短剧、动漫、体育、纪录片、综艺、电视剧、
  电影、其他的关键词和负向关键词规则推断标准分类。
- `JdbcCatalogRepository.syncRawCategory()` 按 `(data_source_id, source_code)` 幂等写入；
  已有行可更新 `source_name`，仅当 `category_id` 为空时补推断分类，推断不到时尝试“其他”。
- fake HTTP service test 覆盖 `class[]` 解析和 V1 等价 list URL；H2 repository test 覆盖
  用户手工 `category_id` 不被覆盖。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-catalog-service:test --rerun-tasks
BUILD SUCCESSFUL
```

遗留：

- 尚未暴露 admin/ops 手动触发入口。
- 尚未迁移 V1 create/update 后自动 best-effort 同步行为；当前是可控显式 service 逻辑。
- 尚未接入 C3 shared outbound HTTP contract。
