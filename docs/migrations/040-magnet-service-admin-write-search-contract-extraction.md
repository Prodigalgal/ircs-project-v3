# 040 magnet-service admin write/search contract extraction

状态：已完成

日期：2026-06-07

## 背景

本迁移承接 `025` V1 路由覆盖闭环。`ircs-magnet-service` 之前已覆盖
provider list/detail 和 unified approved magnets read contract，但 V1 admin 仍有
provider create/update 与 unified magnet search trigger 缺口。

## 覆盖接口

```text
POST /api/v1/magnet-providers
PUT  /api/v1/magnet-providers/{id}
POST /api/v1/magnets/search/unified/{id}
```

## 实现边界

- `POST /api/v1/magnet-providers` 写入 `magnet_providers`，返回 `201 Created` 和
  `Location`。
- `PUT /api/v1/magnet-providers/{id}` 允许 body `id` 为空；非空时必须与 path id
  一致。
- `POST /api/v1/magnets/search/unified/{id}` 在 dev MVP 中创建
  `magnet_search_jobs`，`status=SKIPPED`，`skipped_reason=DEV_MVP_EXTERNAL_SEARCH_DISABLED`。
- 不访问外部磁力资源站，不写 `magnet_provider_runs` 或 `magnet_links`。

## 验证

```text
.\gradlew.bat :services:ircs-magnet-service:test
BUILD SUCCESSFUL

.\gradlew.bat build
BUILD SUCCESSFUL
```

ARM64 rollout：

```text
deployment/ircs-magnet-service successfully rolled out
imageID: harbor.mnnu.eu.org/ircs/ircs-magnet-service@sha256:31c72a41f0d2d70967df00931b8037fa74188d7689b7556f8b508142d763b4ea
```

`admin-bff` live smoke：

```text
MARK 1/3 POST /api/v1/magnet-providers
MARK 2/3 PUT /api/v1/magnet-providers/{id}
MARK 3/3 POST /api/v1/magnets/search/unified/{id}
residue: magnet_providers=0, unified_videos=0, magnet_search_jobs=0
```

## 资源与流量

- 未新增 Pod、Service、PVC、中间件或 HTTPRoute。
- 复用 `ircs-magnet-service` 和 `ircs-admin-bff`。
- `ircs-dev` 仍无 HTTPRoute。

完成计数：`3/3`。
