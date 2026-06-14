# 025-15 content-service auxiliary admin API MVP 子任务

状态：已完成

父任务：

- `tasks/in-progress/025-v1-route-coverage-closeout-parent.md`

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/039-parallel-subagent-route-slices-02515-02517.md`

## 背景

V1 admin 内容管理还依赖 playlists、source-domains 和 video resolver sources。`admin-bff`
已把 `/api/v1/playlists/**`、`/api/v1/source-domains/**` 和 `/api/v1/resolvers/**`
代理到 `ircs-content-service`，但目标服务尚未实现这些真实接口。

## 目标

- 在 `ircs-content-service` 复现 V1 playlists/source-domains/resolvers admin API。
- 覆盖 14 条接口，并保持 V1 前端字段形状和分页契约。
- 复用共享 PostgreSQL 迁移桥，不新增服务或中间件。

## 非目标

- 不修改 `admin-bff` 路由。
- 不修改 catalog/config/credential 服务。
- 不新增 K8S 资源。
- 不接入新的外部 resolver 执行能力，只迁移 resolver source 字典管理。

## 输入

```text
V1 PlaylistController / PlaylistService / Playlist DTO/VO
V1 SourceDomainController / SourceDomainService / SourceDomainDTO
V1 VideoResolverSourceController / VideoResolverSourceService / VideoResolverSourceDTO
services/ircs-content-service
```

## 输出

- content-service playlists/source-domains/resolvers Controller/Service/Repository/DTO。
- 对应单元测试。
- worker 完成结果和 smoke 建议。

## 影响范围

允许写入：

```text
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content
services/ircs-content-service/src/test/java/com/prodigalgal/ircs/content
tasks/done/025-15-content-service-aux-admin-api-mvp.md
```

禁止写入：

```text
services/ircs-admin-bff
services/ircs-catalog-service
services/ircs-config-service
services/ircs-credential-service
deploy/k8s
```

## 验收标准

- playlists 5 条接口可用。
- source-domains 3 条接口可用。
- resolvers 6 条接口可用。
- 单元测试通过。
- 后续主 agent 通过 `admin-bff` live smoke 完成 `14/14` 标记。

## 测试边界

worker 计划执行：

```text
.\gradlew.bat :services:ircs-content-service:test
```

主 agent 后续执行全仓 build、ARM64 rollout 和 live smoke。

## 实现结果

实现时间：2026-06-07

- 已在 `ircs-content-service` 新增 playlists/source-domains/resolvers 辅助后台 API。
- 已新增 Controller、Service、Repository 和 DTO，复用共享 PostgreSQL 迁移桥。
- playlists 绑定现有 `raw_videos`，episode URL 可从 `source_domains` 与相对路径重组。
- source-domains 支持列表、详情和更新，保留 sentinel domain 保护。
- resolvers 支持新增、更新、列表、active 列表、详情和删除，`lines` 按 JSONB 保存。
- 已新增 `AuxiliaryAdminControllerTest` 和 `AuxiliaryAdminServiceTest`。
- 已新增 `scripts/smoke/admin-bff-02515-smoke.ps1`，通过 `admin-bff` 复现 14 条接口并清理隔离测试数据。

## 验证结果

验证时间：2026-06-07

```text
.\gradlew.bat :services:ircs-content-service:test -> PASS
.\gradlew.bat build -> PASS
```

## 主 agent 集成验证

```text
.\scripts\docker\push-service-image-jib.ps1 -Service ircs-content-service -Tag dev -Architecture arm64 -Os linux -> PASS
image:   harbor.mnnu.eu.org/ircs/ircs-content-service@sha256:de6af58c54ddf0ddcf5cbf73a1396f5a51bcfac084758bd30782cc4e42b2a233
rollout: ircs-content-service Ready 1/1
admin-bff: Ready 1/1
HTTPRoute: No resources found in ircs-dev namespace
quota: pods=28/30, services=24/26
smoke:  admin-bff MARK 14/14
```

live smoke：

```text
MARK 1/14 POST /api/v1/playlists
MARK 2/14 PUT /api/v1/playlists/{id}
MARK 3/14 GET /api/v1/playlists
MARK 4/14 GET /api/v1/playlists/{id}
MARK 5/14 DELETE /api/v1/playlists/{id}
MARK 6/14 GET /api/v1/source-domains
MARK 7/14 GET /api/v1/source-domains/{id}
MARK 8/14 PUT /api/v1/source-domains/{id}
MARK 9/14 POST /api/v1/resolvers
MARK 10/14 PUT /api/v1/resolvers/{id}
MARK 11/14 GET /api/v1/resolvers
MARK 12/14 GET /api/v1/resolvers/active
MARK 13/14 GET /api/v1/resolvers/{id}
MARK 14/14 DELETE /api/v1/resolvers/{id}
```

清理校验：

```text
playlists=0
resolvers=0
source_domains=0
raw_videos=0
data_sources=0
```

迁移记录：

```text
docs/migrations/037-content-service-aux-admin-api-extraction.md
```
