# 028-16-C1-S1 catalog dictionary seed task spec

状态：完成第一刀

父任务：

- `tasks/in-progress/028-16-c1-initialization-readiness-gate.md`
- `tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- V1 `CategoryInitializer`
- V1 `AreaInitializer`
- V1 `LanguageInitializer`
- V1 `StandardGenreInitializer`

## 背景

V1 空库启动会补默认分类、语言、地区和题材字典。V3 当前 catalog-service 已有
CRUD 和 schema，但缺运行时 owner seed。接口壳可用不代表清洗、聚合、门户筛选在空库
环境中可复现 V1 行为。

## 目标

- 在 catalog-service 启动时幂等补齐 P0 默认字典。
- 默认 seed 不覆盖用户已编辑字段，只补缺失项或新 key。
- 补 empty-db repository/runner 测试，证明默认分类、语言 alias、地区 alias 和题材可补齐。
- 回写 C1、089 和索引中的 MARK 状态。

## 非目标

- 不导入真实资源站 data_sources/default_mapping；该部分等 V1 preset 精确值确认后拆下一刀。
- 不新增 HTTPRoute，不访问 K8S。
- 不实现全局 readiness orchestrator；本刀只完成 catalog owner seed。

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
MARK 1/5 catalog default seed catalog defined with V1 evidence: done
MARK 2/5 startup runner upserts category/language/area/genre idempotently: done
MARK 3/5 raw language/area/genre alias mapping seeded without duplicates: done
MARK 4/5 catalog-service tests pass and cover empty-db seed: done
MARK 5/5 docs/tasks indexes and 089 inventory updated: done
```

## 测试边界

```text
.\gradlew.bat --no-daemon :services:ircs-catalog-service:test --rerun-tasks
```

## 实现结果

2026-06-08 已完成第一刀：

- 新增 `CatalogDefaultSeedCatalog`，按 V1 `CategoryInitializer` 写入 8 类 category，
  按 V1 `StandardGenreInitializer` 写入全量 standard genre；language/area 先按 V1 preset
  证据写 P0 子集和 V1 alias 规则中的常见映射。
- 新增 `CatalogDefaultInitializerRunner`，catalog-service 启动时幂等补齐标准字典和
  raw alias mapping。
- 新增 repository seed 方法，规则是只补缺失项；已存在的标准项不覆盖用户编辑，已映射
  raw alias 不覆盖，只会补未映射 alias。
- 明确未导入 V1 `DataSourceInitializer` 的 25 个真实资源站和 `default_mapping.json`，
  该范围拆到后续 C1-S2。
- 已回写 `028-16-C1` 父任务、`089` 报告/迁移记录、`docs/index.md` 和 `tasks/index.md`。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-catalog-service:test --rerun-tasks
BUILD SUCCESSFUL
```

遗留：

- language/area 还不是 V1 JSON 全量 preset，当前是 P0 子集。
- data source/default_mapping、resolver seed、credential seed、task seed 和 worker start gate
  仍未完成。
