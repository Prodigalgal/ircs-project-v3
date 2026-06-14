# 017-03 magnet-service provider/cache MVP 子任务

状态：已完成

完成日期：2026-06-04

父任务：

- `tasks/in-progress/011-mvp-service-onboarding-parent.md`

关联文档：

- `docs/reports/017-portal-interaction-magnet-boundary-analysis.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`
- `docs/requirements/011-mvp-service-onboarding-acceleration.md`

## 背景

本任务创建时 `ircs-magnet-service` 尚未接入 `ircs-dev`。V1 magnet 边界位于
`modules/magnet`，HTTP 路由为 `/api/v1/magnet-providers/**` 与
`/api/v1/magnets/**`，核心表为 `magnet_providers`、`magnet_links`、
`magnet_search_jobs`、`magnet_provider_runs`。V1
`POST /api/v1/magnets/search/unified/{id}` 会同步调用外部 provider 并写 job/run/link，
风险较高；但 provider 列表和 approved magnet links 可以作为只读 cache MVP 先接入。
当前 subagent 已完成只读代码和 dev manifest 草案，但尚未由主 agent 完成代码复核、
镜像推送、K8S 部署和集群内 smoke。本任务已由主 agent 完成后续部署和 smoke。

## 目标

- 为 `ircs-magnet-service` 提供只读 provider/cache API。
- 保留 V1 路径 `GET /api/v1/magnet-providers`。
- 保留 V1 路径 `GET /api/v1/magnet-providers/{id}`。
- 保留 V1 路径 `GET /api/v1/magnets/unified/{id}`，仅返回 `APPROVED`
  magnet links。
- 使用当前 PostgreSQL `magnet_providers` 和 `magnet_links` 表作为迁移桥。
- 不触发外部 provider 搜索，不写 job/run。
- 后续实施时完成本地测试、server dry-run、ARM64 镜像、K8S rollout 和
  ClusterIP smoke。

## 非目标

- 不接入 `POST /api/v1/magnets/search/unified/{id}`。
- 不调用 `YtsBzMagnetProviderClient`。
- 不调用 `ThePirateBayMagnetProviderClient`。
- 不创建或更新 magnet provider。
- 不写 `magnet_links`。
- 不写 `magnet_search_jobs`。
- 不写 `magnet_provider_runs`。
- 不驱逐 `portal_detail_v1` cache。
- 不新增公网 HTTPRoute。
- 不拆分独立数据库。

## 上游来源

- `docs/reports/017-portal-interaction-magnet-boundary-analysis.md`
- V1 `MagnetProviderController`
- V1 `MagnetSearchController`
- V1 `MagnetProviderServiceImpl`
- V1 `MagnetSearchServiceImpl`
- V1 `MagnetAssembler`
- V1 `MagnetProviderRepository`
- V1 `MagnetLinkRepository`
- V1 `add-magnet-scraping.sql`

## 输入

- `services/ircs-magnet-service`
- `GET /api/v1/magnet-providers`
- `GET /api/v1/magnet-providers/{id}`
- `GET /api/v1/magnets/unified/{id}`
- `MagnetProviderDTO`
- `MagnetLinkDTO`
- `magnet_providers`
- `magnet_links`
- `unified_videos` 作为 link FK 和 smoke 锚点

## 输出

- `ircs-magnet-service` provider/cache 只读 MVP 实现。
- `GET /api/v1/magnet-providers` 兼容响应。
- `GET /api/v1/magnet-providers/{id}` 兼容响应。
- `GET /api/v1/magnets/unified/{id}` approved links 兼容响应。
- dev Deployment/Service。
- 迁移文档、K8S 快照和任务状态回写。

当前实际输出：

```text
services/ircs-magnet-service/build.gradle
services/ircs-magnet-service/src/main/resources/application.yaml
services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet/*
services/ircs-magnet-service/src/test/java/com/prodigalgal/ircs/magnet/MagnetControllerTest.java
deploy/k8s/dev/magnet-service-dev.yaml
```

## 影响范围

```text
services/ircs-magnet-service
deploy/k8s/dev/magnet-service-dev.yaml
docs/migrations
docs/k8s
deploy/k8s/README.md
tasks
```

## 依赖

- 当前 V3 shared PostgreSQL。
- `magnet_links.unified_video_id` 依赖 `unified_videos.id`，smoke 需要现有或临时
  unified video 锚点。
- V1 provider seed 数据来自 `add-magnet-scraping.sql`。
- 如未来由 portal detail 调用该服务，需要补内部 API 调用或服务发现约定。

## 风险

- V1 magnet controller 标注 `hasRole('ADMIN')`；MVP 若不接入 identity-service，
  应保持 ClusterIP only，并在后续统一处理 admin auth。
- 外部 provider 搜索包含网络、内容策略、延迟、失败记录和 cache eviction，本 MVP
  必须明确排除。
- `magnet_uri` 是敏感资源链接，公网暴露前需要由主 agent 明确访问策略。
- shared DB 阶段只允许读取 magnet 表，不应写 job/run/link。

## 验收标准

- `GET /api/v1/magnet-providers` 按 priority 返回 provider 列表。
- `GET /api/v1/magnet-providers/{id}` 能返回临时或 seed provider 详情。
- `GET /api/v1/magnets/unified/{id}` 只返回 `APPROVED` links。
- 只读 smoke 后 `magnet_search_jobs` 和 `magnet_provider_runs` 没有新增记录。
- 临时 `magnet_links` smoke 数据清理为 0。
- 服务在 `ircs-dev` Ready 1/1。
- 未新增公网 HTTPRoute。

## 测试边界

```text
.\gradlew.bat :services:ircs-magnet-service:test
.\gradlew.bat build
kubectl apply --dry-run=server -f deploy\k8s\dev\magnet-service-dev.yaml
ARM64 Jib image push
kubectl rollout status deployment/ircs-magnet-service -n ircs-dev --timeout=240s
集群内 GET /api/v1/magnet-providers smoke
集群内 GET /api/v1/magnet-providers/{id} smoke
集群内 GET /api/v1/magnets/unified/{id} smoke
确认 magnet_search_jobs / magnet_provider_runs 未新增
清理临时 magnet_links 数据
```

## 关联任务

- 父任务：`tasks/in-progress/011-mvp-service-onboarding-parent.md`
- 同批边界分析：`tasks/in-progress/017-01-portal-read-composition-mvp.md`
- 同批边界分析：`tasks/in-progress/017-02-interaction-feedback-history-mvp.md`

## 当前状态

## 实现结果

已完成 provider/cache 只读 MVP：

```text
GET /api/v1/magnet-providers
GET /api/v1/magnet-providers/{id}
GET /api/v1/magnets/unified/{id}
```

已部署：

```text
deployment/ircs-magnet-service Ready 1/1
service/ircs-magnet-service ClusterIP 10.107.43.118
HTTPRoute: none
image digest: sha256:4f34bd2e77eab80ea2c42253c329f4b4f425080a7fadf94b73dc7e2425082401
```

验证：

```text
.\gradlew.bat :services:ircs-magnet-service:test
.\gradlew.bat :services:ircs-magnet-service:bootJar
kubectl apply --dry-run=server -f deploy\k8s\dev\magnet-service-dev.yaml
ARM64 Jib image push
kubectl rollout status deployment/ircs-magnet-service -n ircs-dev --timeout=240s
```

Smoke：

```text
provider list/detail returned temporary CODEX_SMOKE provider
unified magnets returned only 1 APPROVED link
REJECTED temporary link was not returned
magnet_search_jobs rows for temporary unified: 0
magnet_provider_runs rows for temporary provider: 0
temporary magnet_links rows after cleanup: 0
temporary magnet_providers rows after cleanup: 0
temporary unified_videos rows after cleanup: 0
RabbitMQ queues and DLQs after cleanup: 0
```

## 当前状态

已完成。本任务完成时 `ircs-dev` 业务服务接入数从 12/18 提升到 13/18；当前整体接入状态以
`docs/index.md` 和 `tasks/in-progress/011-mvp-service-onboarding-parent.md` 为准。
