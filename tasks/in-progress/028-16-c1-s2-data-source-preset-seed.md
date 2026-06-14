# 028-16-C1-S2 data source preset seed task spec

状态：完成第一刀

父任务：

- `tasks/in-progress/028-16-c1-initialization-readiness-gate.md`
- `tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- V1 `DataSourceInitializer`
- V1 `presets/default_mapping.json`

## 背景

V1 空库启动会创建 25 个预设资源站，并给每个资源站写入默认 list/detail API
路径、params 和 `field_mapping`。新增 DataSource 时，V1 `DataSourceService.create()`
会保存配置后尝试远程 GET 同步分类；该外联失败只记录日志，不回滚已创建的数据源。

V1 还会检查现有数据源的 mapping，把旧的 `subTitle` 迁移为 `aliasTitle`，并补齐
`aliasTitle`、`totalEpisodes` 和 `duration` 等新 detail 字段。

C1-S1 已经完成 catalog 默认字典 seed，但空库仍没有 `data_sources`，这会导致
scraper-service 和后续 task-service 默认任务派生没有输入。

## 目标

- 在 catalog-service 启动时幂等补齐 V1 25 个预设资源站配置。
- 按 V1 默认 API path、list/detail params 和 `default_mapping.json` 写入新站点。
- 对已有 data source 只补缺失配置或缺失 mapping 字段，不覆盖用户已编辑值。
- 复刻 V1 `patchExistingMappings()` 的关键迁移：`subTitle` -> `aliasTitle`，并补
  `aliasTitle`、`totalEpisodes`、`duration`。
- 用 catalog-service empty-db/idempotent 测试证明 seed 和 patch 行为。
- 明确不在启动 seed 中复刻 V1 的远程分类同步，避免 dev/K8S 启动阶段外联和资源抖动。

## 非目标

- 不访问任何外部资源站 URL；V1 启动时分类同步外联拆到后续可控 job。
- 不创建 collection task；按 data source 派生默认采集任务已由 `028-16-C1-S4`/task-service 完成。
- 不新增 HTTPRoute，不改 K8S。
- 不补 V3 schema 目前不存在的 `searchable`、`active` 等字段；V1 `DataSource`
  本身也没有这些落库字段。

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
MARK 1/5 V1 25 data source presets and default_mapping facts documented: done
MARK 2/5 catalog-service startup runner seeds data_sources without external HTTP: done
MARK 3/5 existing field_mapping patch preserves user edits and fills V1 missing keys: done
MARK 4/5 catalog-service tests cover empty-db and idempotent data source seed: done
MARK 5/5 docs/tasks indexes and 089 inventory updated: done
```

## 测试边界

```text
.\gradlew.bat --no-daemon :services:ircs-catalog-service:test --rerun-tasks
```

## 设计约束

- 默认初始化是最低优先级兜底：已有 data source 的非空字段不被 seed 覆盖。
- `field_mapping` 使用 Jackson JSON 树做结构化 patch，不做字符串拼接。
- seed 只写配置，不触发 sample fetch、scraper run、task run 或 provider 调用。

## 实现结果

2026-06-08 已完成第一刀：

- `CatalogDefaultSeedCatalog` 新增 V1 25 个预设资源站、默认 list/detail params 和
  `default_mapping.json` 模板。
- `CatalogDefaultInitializerRunner` 启动时写入 data source preset，并用 Jackson 复刻
  V1 `patchExistingMappings()` 的关键补丁。
- `JdbcCatalogRepository.seedDataSource()` 按 name 幂等插入；已有行只补空字段，不覆盖
  已有 `base_url`、path、params 或 `field_mapping`。
- 明确未复刻 V1 启动时远程同步分类；该外联行为拆到后续 C1-S2b 或 scraper/category
  sync job。
- 已回写 `028-16-C1` 父任务、`089` 报告/迁移记录、`docs/index.md` 和 `tasks/index.md`。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-catalog-service:test --rerun-tasks
BUILD SUCCESSFUL
```

遗留：

- 未同步远程 `class[]` 到 raw category/data_source_categories。
- 按 data source 派生 V1 默认“自动采集/全量采集” collection task 已由 `028-16-C1-S4` 完成。
- 未做 K8S empty dev DB live smoke。
