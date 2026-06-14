# 039 parallel subagent route slices 025-15 to 025-17

状态：已完成

更新时间：2026-06-07

## 背景

`025-12`、`025-13`、`025-14` 已完成并通过 `admin-bff` live smoke。本报告创建时
V1/V3 HTTP Controller 覆盖口径为：

```text
V1 routes: 203
V3 routes: 153
missing:   61
```

`025-15` 已于 2026-06-07 完成并通过 `admin-bff` live smoke。当前口径更新为：

```text
V1 routes: 203
V3 routes: 167
missing:   47
```

用户要求继续加速 V1 到 V3 微服务拆分，并把任务拆分给 subagent，每个 subagent 使用
`gpt-5.5` + `xhigh`。本报告固定下一批 3 个互不重叠写入范围的并行切片。

`025-16` 和 `025-17` 已于 2026-06-07 完成并通过 `admin-bff` live smoke。当前口径更新为：

```text
V1 routes: 203
V3 routes: 201
missing:   13
```

## ASCII 架构图

```text
                         admin portal
                              |
                              v
                     +------------------+
                     | ircs-admin-bff   |
                     +---------+--------+
                               |
          +--------------------+--------------------+
          |                    |                    |
          v                    v                    v
+-------------------+ +-------------------+ +----------------------+
| content-service   | | catalog-service   | | config/credential    |
| 025-15            | | 025-16            | | 025-17               |
| playlists         | | data-sources      | | configs write/test   |
| source-domains    | | standard-* write  | | credentials write    |
| resolvers         | | raw-* write       | | credential templates |
+---------+---------+ +---------+---------+ +----------+-----------+
          |                     |                      |
          +---------------------+----------------------+
                                |
                                v
                    shared PostgreSQL migration bridge
```

## 下一批并行 worker

### 025-15 content-service auxiliary admin API

状态：已完成。迁移记录见
`docs/migrations/037-content-service-aux-admin-api-extraction.md`。

目标接口共 `14` 条：

```text
POST   /api/v1/playlists
PUT    /api/v1/playlists/{id}
GET    /api/v1/playlists
GET    /api/v1/playlists/{id}
DELETE /api/v1/playlists/{id}
GET    /api/v1/source-domains
GET    /api/v1/source-domains/{id}
PUT    /api/v1/source-domains/{id}
POST   /api/v1/resolvers
PUT    /api/v1/resolvers/{id}
GET    /api/v1/resolvers
GET    /api/v1/resolvers/active
GET    /api/v1/resolvers/{id}
DELETE /api/v1/resolvers/{id}
```

写入范围：

```text
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content
services/ircs-content-service/src/test/java/com/prodigalgal/ircs/content
tasks/done/025-15-content-service-aux-admin-api-mvp.md
```

验证结果：

```text
.\gradlew.bat :services:ircs-content-service:test -> PASS
.\gradlew.bat build -> PASS
rollout: ircs-content-service Ready 1/1
smoke:  admin-bff MARK 14/14
quota:  pods=28/30, services=24/26
```

### 025-16 catalog-service admin write contract

状态：已完成。迁移记录见
`docs/migrations/038-catalog-service-admin-write-contract-extraction.md`。

目标接口共 `25` 条：

```text
POST   /api/v1/data-sources
PUT    /api/v1/data-sources/{id}
PATCH  /api/v1/data-sources/{id}
DELETE /api/v1/data-sources/{id}
POST   /api/v1/data-sources/fetch-sample
POST   /api/v1/standard-categories
PUT    /api/v1/standard-categories/{id}
PATCH  /api/v1/standard-categories/{id}
DELETE /api/v1/standard-categories/{id}
POST   /api/v1/standard-genres
PUT    /api/v1/standard-genres/{id}
DELETE /api/v1/standard-genres/{id}
POST   /api/v1/standard-areas
POST   /api/v1/standard-languages
PUT    /api/v1/raw-categories/{id}
DELETE /api/v1/raw-categories/{id}
POST   /api/v1/raw-genres
PUT    /api/v1/raw-genres/{id}
DELETE /api/v1/raw-genres/{id}
POST   /api/v1/raw-areas
PUT    /api/v1/raw-areas/{id}
DELETE /api/v1/raw-areas/{id}
POST   /api/v1/raw-languages
PUT    /api/v1/raw-languages/{id}
DELETE /api/v1/raw-languages/{id}
```

写入范围：

```text
services/ircs-catalog-service/src/main/java/com/prodigalgal/ircs/catalog
services/ircs-catalog-service/src/test/java/com/prodigalgal/ircs/catalog
tasks/done/025-16-catalog-service-admin-write-contract-mvp.md
```

验证结果：

```text
.\gradlew.bat :services:ircs-catalog-service:test -> PASS
.\gradlew.bat build -> PASS
rollout: ircs-catalog-service Ready 1/1
imageID: harbor.mnnu.eu.org/ircs/ircs-catalog-service@sha256:bc8ec3c3b0a72b2f8ffbbb97814839bb91d783a6f0c474153f5f538a8a58cd39
smoke:  admin-bff MARK 25/25
cleanup: data_sources/standard_*/raw_* residue 0
quota:  pods=28/30, services=24/26
```

`fetch-sample` live smoke 使用 namespace 内部可控 URL，未访问生产外部数据源。

### 025-17 config/credential admin write contract

状态：已完成。迁移记录见
`docs/migrations/039-config-credential-admin-write-contract-extraction.md`。

目标接口共 `9` 条：

```text
GET    /api/v1/credentials/templates
POST   /api/v1/credentials
PUT    /api/v1/credentials/{id}
DELETE /api/v1/credentials/{id}
POST   /api/v1/credentials/refresh
POST   /api/v1/configs
PUT    /api/v1/configs/{key}
DELETE /api/v1/configs/{key}
POST   /api/v1/configs/test/{type}
```

写入范围：

```text
services/ircs-config-service/src/main/java/com/prodigalgal/ircs/config
services/ircs-config-service/src/test/java/com/prodigalgal/ircs/config
services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential
services/ircs-credential-service/src/test/java/com/prodigalgal/ircs/credential
tasks/done/025-17-config-credential-admin-write-contract-mvp.md
```

验证结果：

```text
.\gradlew.bat :services:ircs-config-service:test :services:ircs-credential-service:test -> PASS
.\gradlew.bat build -> PASS
rollout: ircs-config-service Ready 1/1, ircs-credential-service Ready 1/1
config imageID: harbor.mnnu.eu.org/ircs/ircs-config-service@sha256:3ce1e6be62dd11a6912a01715b007699b3f02ff8445c2383464545f7d2c9ddac
credential imageID: harbor.mnnu.eu.org/ircs/ircs-credential-service@sha256:c1eaa572403be48308155a4edc8dcfca219b8008fb6f6f228e6d5352828d5b60
smoke:  admin-bff MARK 9/9
cleanup: sys_credentials=0, system_configs=0
quota:  pods=28/30, services=24/26
```

## 并行规则

- 三个 worker 均使用 `gpt-5.5` + `xhigh`。
- 每个 worker 必须先读 `AGENTS.md` 和自己的 task spec。
- 每个 worker 只写自己的服务模块和对应测试，不修改其他 worker 文件。
- 不新增 Pod、Service、HTTPRoute、PVC 或中间件。
- 主 agent 负责审查、整合、全仓 build、ARM64 image、rollout 和 live smoke。

## 预期覆盖变化

`025-16` 和 `025-17` 已全部通过主 agent live smoke，接口覆盖已从：

```text
V1 routes: 203
V3 routes: 167
missing:   47
```

推进到：

```text
V1 routes: 203
V3 routes: 201
missing:   13
```

剩余接口预计集中在 destructive maintenance、DLQ retry/discard、traffic reset、
magnet search、metadata debug、dashboard stream 或少量 portal/media 边界，后续必须单独拆
task spec。
