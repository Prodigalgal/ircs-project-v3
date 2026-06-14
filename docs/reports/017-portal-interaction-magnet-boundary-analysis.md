# 017 portal / interaction / magnet MVP 边界分析

状态：已完成边界分析；magnet、portal、interaction 后续均已实现并部署

更新时间：2026-06-03

## 背景

当前主线目标是将 V1 单体 `D:\WorkSpace\Project\ircs\ircs-project-v1`
逐步拆分迁移到当前 V3 微服务仓库。本报告形成时 `ircs-dev` 已接入 10/18
个业务服务；portal/interaction rollout 后曾达到 17/18。后续
`ircs-ops-service` 已完成 MVP rollout，当前目标业务服务为 18/18。本报告主体只负责分析
`portal-service`、`interaction-service`、`magnet-service` 的 MVP 接入边界；后续
magnet-service 实现和部署结果以
`tasks/done/017-03-magnet-provider-cache-mvp.md` 与
`docs/migrations/017-magnet-service-provider-cache-extraction.md` 为准。

## 上游来源

- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`
- `tasks/in-progress/011-mvp-service-onboarding-parent.md`
- V1 后端源码：`D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-backend`
- V1 portal 前端源码：`D:\WorkSpace\Project\ircs\ircs-project-v1\huawai`

## 结论

此前推荐下一个低风险服务优先选择 `ircs-magnet-service` 的只读 provider/cache
MVP。当前 magnet-service 已完成 ARM64 镜像、K8S rollout 和集群 smoke，已计入
Ready 服务。

推荐顺序：

1. `magnet-service` 只读 provider/cache MVP
2. `portal-service` 公开读聚合 MVP
3. `interaction-service` feedback/history MVP

理由：

- `magnet-service` 可以先只读 `magnet_providers` 和 `magnet_links`，跳过外部
  provider 抓取、写 job、admin 写接口和公网路由，独立 smoke 成本最低。
- `portal-service` 是公开读入口，但 V1 实现同时依赖 content、search、storage
  URL 解析、magnet 链接和多层缓存，组合面比单表只读更宽。
- `interaction-service` 同时涉及会员登录态、用户写入、`q.interaction.watch_progress`
  队列和跨 content 读取。`identity-service` 已部署 Ready，但完整 auth/profile
  smoke 尚未收口，真实端到端验收仍要先补齐 token 与 profile 更新验证。

## V1 证据汇总

### portal-service 候选边界

V1 Controller：

- `modules/portal/controller/PortalContentController.java`
  - `GET /api/portal/home`
  - `GET /api/portal/explore`
  - `GET /api/portal/metadata`
  - `GET /api/portal/movies/{id}`
- `modules/portal/controller/MemberAuthController.java`
  - `GET /api/portal/auth/pow`
  - `POST /api/portal/auth/register`
  - `POST /api/portal/auth/activate`
  - `POST /api/portal/auth/login`
  - `POST /api/portal/auth/resend-code`
  - `POST /api/portal/auth/forgot-password`
  - `POST /api/portal/auth/reset-password`
- `modules/portal/controller/MemberProfileController.java`
  - `GET /api/portal/profile`
  - `PUT /api/portal/profile`
  - `POST /api/portal/profile/password`
  - `POST /api/portal/profile/avatar`
  - `POST /api/portal/profile/check-in`
- `modules/portal/controller/MemberAdminController.java`
  - `GET /api/v1/members`
  - `GET /api/v1/members/{id}/favorites`
  - `GET /api/v1/members/{id}/history`
  - `PUT /api/v1/members/{id}`
  - `PUT /api/v1/members/{id}/status`
  - `DELETE /api/v1/members/{id}`

V3 目标边界中，`auth/profile/members` 更接近 `identity-service`，不应进入
`portal-service` MVP。本报告的 `portal-service` MVP 仅覆盖公开内容读聚合：

- `GET /api/portal/home`
- `GET /api/portal/metadata`
- 可选后续：`GET /api/portal/movies/{id}`
- 后置：`GET /api/portal/explore`，因为它在 V1 中委托 `SearchQueryService`

V1 Service / 组装：

- `PortalContentService` / `PortalContentServiceImpl`
- `PortalDataCacheService` / `PortalDataCacheServiceImpl`
- `PortalAssembler`
- `PortalMapper`
- `PortalCacheInvalidationListener`
- `CacheWarmingScheduler`

V1 DTO / VO：

- `PortalHomeVO`
- `PortalMovieCardVO`
- `PortalCategorySectionVO`
- `PortalMetadataVO`
- `PortalMovieDetailVO`
- `PortalVideoSourceVO`
- `PortalEpisodeVO`
- `PortalCastMemberVO`
- `PortalMagnetLinkVO`
- `CategoryItem`

V1 Repository / 依赖：

- `UnifiedVideoRepository`
- `UnifiedVideoPortalRepository`
- `PlaylistRepository`
- `SearchQueryService`
- `MagnetSearchService`
- `StorageUrlHelper`

主要表边界：

- 只读主表：`unified_videos`
- 只读内容关联：`videos`、`playlists`、`episodes`
- 只读人物/分类：`actors`、`directors`、`standard_category`、
  `standard_genre`、`standard_areas`、`standard_languages`
- 只读封面/来源：`cover_images`、`source_domains`
- 详情磁链依赖：`magnet_links`
- 缓存：`home_aggregate_v1`、`home_spotlight_v2`、`home_trending_v2`、
  `home_section_v2`、`portal_detail_v1`

MVP 风险：

- `/home` 与 `/metadata` 可用共享 PostgreSQL 只读实现，风险中等。
- `/movies/{id}` 需要拼装播放源、集数、封面 URL 与 magnet 链接，风险中高。
- `/explore` 已委托 search-service，若 portal-service 直接重写会扩大范围；
  更稳妥的是后续通过 search-service 内部 API 或保留 V1 兼容代理。

### interaction-service 候选边界

V1 Controller：

- `modules/interaction/controller/PortalInteractionController.java`
  - `POST /api/portal/interaction/favorites/{unifiedVideoId}`
  - `GET /api/portal/interaction/favorites/{unifiedVideoId}/status`
  - `GET /api/portal/interaction/favorites`
  - `POST /api/portal/interaction/history`
  - `GET /api/portal/interaction/history`
  - `DELETE /api/portal/interaction/history`
  - `DELETE /api/portal/interaction/history/{id}`
- `modules/interaction/controller/PortalMessageController.java`
  - `POST /api/portal/feedback`
  - `GET /api/portal/feedback`
  - `GET /api/portal/feedback/wall`
  - `DELETE /api/portal/feedback/{id}`
- `modules/interaction/controller/AdminMessageController.java`
  - `GET /api/v1/messages`
  - `POST /api/v1/messages/{id}/reply`
  - `PUT /api/v1/messages/{id}/visibility`
  - `DELETE /api/v1/messages/{id}`

V1 Service / Consumer：

- `MemberFavoriteService` / `MemberFavoriteServiceImpl`
- `WatchHistoryService` / `WatchHistoryServiceImpl`
- `UserMessageService` / `UserMessageServiceImpl`
- `InteractionMigrationService` / `InteractionMigrationServiceImpl`
- `WatchHistoryConsumer`

V1 DTO / Command：

- `HistoryRecordDTO`
- `WatchProgressMsg`
- `UserMessageDTO`
- `UserMessageSubmitCmd`
- `AdminReplyCmd`

V1 Entity / Repository：

- `Member`
- `MemberFavorite` / `MemberFavoriteRepository`
- `MemberWatchHistory` / `MemberWatchHistoryRepository`
- `MemberWatchHistoryRepositoryCustomImpl`
- `UserMessage` / `UserMessageRepository`
- `MemberRepository`
- `InteractionRepositoryCustom`

主要表边界：

- 自有交互表：`member_favorites`、`member_watch_histories`、`user_messages`
- 会员引用：`members`
- 内容引用：`unified_videos`、`videos`、`episodes`
- 配置读取：`system_configs` 中的 `member.message.daily-limit`
- 队列：`x.interaction` / `q.interaction.watch_progress`

MVP 风险：

- 大多数 portal interaction 路由要求 `isAuthenticated()`，当前可以复用
  `identity-service` member token；真实用户态 smoke 仍需要 interaction-service 自身
  的写入、权限和 cleanup 验证。
- `POST /api/portal/interaction/history` 在 V1 只 publish
  `WATCH_PROGRESS`，实际写入由 consumer 批量 upsert。若 MVP 同时迁移 HTTP
  与 consumer，范围会扩大到 RabbitMQ、批处理和幂等写。
- `user_messages` 写入需要每日限额配置和 HTML 清洗；public wall 只读较安全，
  但完整 feedback 仍要处理会员、删除权限和 admin 可见性。

### magnet-service 候选边界

V1 Controller：

- `modules/magnet/controller/MagnetProviderController.java`
  - `GET /api/v1/magnet-providers`
  - `GET /api/v1/magnet-providers/{id}`
  - `POST /api/v1/magnet-providers`
  - `PUT /api/v1/magnet-providers/{id}`
- `modules/magnet/controller/MagnetSearchController.java`
  - `POST /api/v1/magnets/search/unified/{id}`
  - `GET /api/v1/magnets/unified/{id}`

V1 Service / Provider：

- `MagnetProviderService` / `MagnetProviderServiceImpl`
- `MagnetSearchService` / `MagnetSearchServiceImpl`
- `MagnetProviderClientRegistry`
- `MagnetProviderClient`
- `YtsBzMagnetProviderClient`
- `ThePirateBayMagnetProviderClient`
- `ExternalIdQueryPlanner`
- `MagnetUriUtils`

V1 DTO / Assembler：

- `MagnetProviderDTO`
- `MagnetLinkDTO`
- `MagnetSearchJobDTO`
- `MagnetCandidate`
- `MagnetProviderSearchResult`
- `ExternalIdQuery`
- `MagnetAssembler`

V1 Entity / Repository：

- `MagnetProvider` / `MagnetProviderRepository`
- `MagnetLink` / `MagnetLinkRepository`
- `MagnetSearchJob` / `MagnetSearchJobRepository`
- `MagnetProviderRun` / `MagnetProviderRunRepository`

主要表边界：

- 自有表：`magnet_providers`、`magnet_links`、`magnet_search_jobs`、
  `magnet_provider_runs`
- 内容引用：`unified_videos`

MVP 风险：

- 只读 `GET /api/v1/magnet-providers`、`GET /api/v1/magnet-providers/{id}`、
  `GET /api/v1/magnets/unified/{id}` 风险低。
- `POST /api/v1/magnets/search/unified/{id}` 会同步调用外部 provider，写
  job/run/link，并驱逐 `portal_detail_v1` 缓存，MVP 应后置。
- provider 创建/更新是 admin 写路径，涉及权限、校验和审计，MVP 应后置。

## 推荐 MVP 边界

### 1. magnet-service provider/cache 只读 MVP

HTTP 边界：

```text
GET /api/v1/magnet-providers
GET /api/v1/magnet-providers/{id}
GET /api/v1/magnets/unified/{id}
```

数据边界：

```text
read magnet_providers
read magnet_links
read unified_videos only for smoke anchor if needed
no writes in MVP
```

非目标：

- 不接入 `POST /api/v1/magnets/search/unified/{id}`。
- 不调用 YTS.BZ、The Pirate Bay APIBay 或其他外部 provider。
- 不创建、更新 provider。
- 不写 `magnet_search_jobs`、`magnet_provider_runs`、`magnet_links`。
- 不驱逐 portal cache。
- 不新增 HTTPRoute。

Smoke 思路：

- 在 `ircs-dev` 内 ClusterIP 调用 readiness。
- 查询 `GET /api/v1/magnet-providers`，确认 seed provider 可读。
- 使用现有或临时 `unified_videos` 作为锚点，插入临时 approved
  `magnet_links` 后调用 `GET /api/v1/magnets/unified/{id}`，结束后清理临时
  `magnet_links`。
- 确认 search job/run 计数没有因只读 smoke 增加。

### 2. portal-service 公开读聚合 MVP

HTTP 边界：

```text
GET /api/portal/metadata
GET /api/portal/home
```

可选后续：

```text
GET /api/portal/movies/{id}
GET /api/portal/explore
```

数据边界：

```text
read unified_videos and content relation tables
read catalog tables through existing shared DB bridge or later catalog-service
read cover_images/source_domains only to resolve poster URLs
read magnet_links only if detail endpoint enters the slice
no writes in MVP
```

非目标：

- 不接入 `/api/portal/auth/**`、`/api/portal/profile/**`。
- 不接入 `/api/portal/interaction/**`、`/api/portal/feedback/**`。
- 不实现 member/admin 路由。
- 不重写 search-service 查询逻辑。
- 不新增 HTTPRoute，除非主 agent 明确进入 public edge cutover。

Smoke 思路：

- ClusterIP 调用 `GET /api/portal/metadata`，确认 categories/genres/areas/
  languages/years shape 与 V1 portal 前端 `PortalMetadata` 兼容。
- ClusterIP 调用 `GET /api/portal/home`，确认 `spotlight`、`trending`、
  `sections` 均返回数组且字段可被 V1 `HomeData` 消费。
- 若接入 detail，使用已有 unified video 调用 `GET /api/portal/movies/{id}`，
  检查 `sources` 与 `magnets` 字段始终为数组。

### 3. interaction-service feedback/history MVP

HTTP 边界建议：

```text
GET /api/portal/feedback/wall
GET /api/portal/interaction/history
GET /api/portal/interaction/favorites
```

后置写路径：

```text
POST /api/portal/feedback
DELETE /api/portal/feedback/{id}
POST /api/portal/interaction/favorites/{unifiedVideoId}
POST /api/portal/interaction/history
DELETE /api/portal/interaction/history
DELETE /api/portal/interaction/history/{id}
POST /api/v1/messages/{id}/reply
PUT /api/v1/messages/{id}/visibility
```

数据边界：

```text
read user_messages for public wall
read member_watch_histories and member_favorites for authenticated member history
read members for feedback author display and auth subject validation
read unified_videos for card projection
defer writes and q.interaction.watch_progress consumer
```

非目标：

- 不在 identity-service auth/profile smoke 完全收口前承诺真实 portal 登录态验收。
- 不迁移 watch progress queue consumer。
- 不做 admin message 管理写接口。
- 不做互动数据迁移合并。
- 不新增 HTTPRoute。

Smoke 思路：

- `GET /api/portal/feedback/wall` 可用临时公开留言验证，无需 auth。
- history/favorites 若要 smoke，需要后续明确内部测试 token，并复用已部署的
  identity-service 登录流程。
- 若迁移 queue consumer，必须补 RabbitMQ publish + DB upsert + cleanup 验证。

## 后续 task spec

本报告对应三个 backlog task spec：

- `tasks/done/017-01-portal-read-composition-mvp.md`
- `tasks/done/017-02-interaction-feedback-history-mvp.md`
- `tasks/done/017-03-magnet-provider-cache-mvp.md`

本 worker 未修改 `docs/index.md`、`tasks/index.md`、011 父任务或 011 需求文档，
索引集成留给主 agent。
