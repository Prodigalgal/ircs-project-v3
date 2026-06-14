# 037 content-service auxiliary admin API extraction

状态：已完成

日期：2026-06-07

## 范围

本轮将 V1 admin 内容辅助管理接口迁移到 `ircs-content-service`：

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

## 实现

- 新增 `content/auxiliary` 包，承接 playlists、source-domains 和 resolvers 后台 API。
- playlists 绑定 `raw_videos`，episode 完整 URL 写入时拆分为 source domain 与相对路径，读取时按 V1 字段形状重组。
- source-domains 支持 keyword/dataSourceId 查询、详情和更新，并禁止修改 sentinel domain。
- resolvers 支持新增、更新、列表、active 列表、详情和删除，`lines` 使用 JSONB 保存。
- `admin-bff` 已有路由不变，本轮不新增 Pod、Service、HTTPRoute、PVC 或中间件。

## 验证

```text
.\gradlew.bat :services:ircs-content-service:test -> PASS
.\gradlew.bat build -> PASS
image:  harbor.mnnu.eu.org/ircs/ircs-content-service@sha256:de6af58c54ddf0ddcf5cbf73a1396f5a51bcfac084758bd30782cc4e42b2a233
rollout: ircs-content-service Ready 1/1
smoke:  admin-bff MARK 14/14
quota:  pods=28/30, services=24/26
```

smoke 清理结果：

```text
playlists=0
resolvers=0
source_domains=0
raw_videos=0
data_sources=0
```

## 结果

`025-15` 完成，content-service playlists/source-domains/resolvers admin API `14/14`
完成迁移和复现标记。`025` 路由覆盖口径从 `V3 routes=153, missing=61` 更新为
`V3 routes=167, missing=47`。
