# 017 magnet-service provider/cache extraction

状态：已完成

完成日期：2026-06-04

上游来源：

- `docs/reports/017-portal-interaction-magnet-boundary-analysis.md`
- `tasks/done/017-03-magnet-provider-cache-mvp.md`
- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`

## 背景

V1 magnet 边界位于 `modules/magnet`，包含 provider 配置、外部搜索、job/run
记录、magnet link 缓存和 portal cache 驱逐。完整外部 provider 搜索风险较高：
它涉及网络、内容策略、延迟控制、job/run 写入和 cache eviction。

本轮只抽取低风险只读 cache MVP：

- provider 列表和详情读取。
- unified video 下已批准 magnet links 读取。
- 不触发外部 provider 搜索。
- 不写 job/run/link。

## 实现范围

本轮已实现：

- `GET /api/v1/magnet-providers`
- `GET /api/v1/magnet-providers/{id}`
- `GET /api/v1/magnets/unified/{id}`
- 读取 `magnet_providers`。
- 读取 `magnet_links` 中 `status='APPROVED'` 的记录。
- dev Deployment/Service：
  `deploy/k8s/dev/magnet-service-dev.yaml`。
- ClusterIP only，无 HTTPRoute。

## 非目标和延期范围

本轮未迁移：

- `POST /api/v1/magnets/search/unified/{id}`。
- 外部 provider 调用。
- `magnet_search_jobs` 写入。
- `magnet_provider_runs` 写入。
- `magnet_links` 写入。
- provider 创建、更新、删除。
- portal cache 驱逐。
- admin auth 集成。
- 生产切换或物理数据库拆分。

## 文件变更

```text
services/ircs-magnet-service/build.gradle
services/ircs-magnet-service/src/main/resources/application.yaml
services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet/*
services/ircs-magnet-service/src/test/java/com/prodigalgal/ircs/magnet/MagnetControllerTest.java
deploy/k8s/dev/magnet-service-dev.yaml
docs/reports/017-portal-interaction-magnet-boundary-analysis.md
tasks/done/017-03-magnet-provider-cache-mvp.md
```

## 数据边界

读取：

```text
magnet_providers
magnet_links
```

本轮不写：

```text
magnet_providers
magnet_links
magnet_search_jobs
magnet_provider_runs
```

Smoke 为构造临时数据会直接插入并清理 `magnet_providers`、`magnet_links` 和
`unified_videos`，这是验证数据，不属于服务写路径。

## K8S 部署

镜像：

```text
harbor.mnnu.eu.org/ircs/ircs-magnet-service:dev
digest: sha256:4f34bd2e77eab80ea2c42253c329f4b4f425080a7fadf94b73dc7e2425082401
platform: linux/arm64
```

资源：

```text
request: cpu 25m, memory 128Mi
limit:   cpu 250m, memory 512Mi
```

入口：

```text
ClusterIP only
No HTTPRoute
```

## 验证结果

本地验证：

```text
.\gradlew.bat :services:ircs-magnet-service:test
.\gradlew.bat :services:ircs-magnet-service:bootJar
kubectl apply --dry-run=server -f .\deploy\k8s\dev\magnet-service-dev.yaml
```

结果：

```text
magnet-service tests: BUILD SUCCESSFUL
bootJar: BUILD SUCCESSFUL
server dry-run: deployment/service created by server dry run
```

镜像与 rollout：

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-magnet-service' -ImageName ircs-magnet-service -Tag dev -Architecture arm64 -Os linux
kubectl apply -f .\deploy\k8s\dev\magnet-service-dev.yaml
kubectl rollout status deployment/ircs-magnet-service -n ircs-dev --timeout=240s
```

结果：

```text
Pushed harbor.mnnu.eu.org/ircs/ircs-magnet-service:dev for linux/arm64
deployment "ircs-magnet-service" successfully rolled out
imageID: harbor.mnnu.eu.org/ircs/ircs-magnet-service@sha256:4f34bd2e77eab80ea2c42253c329f4b4f425080a7fadf94b73dc7e2425082401
```

Smoke：

```text
temporary provider id: e1a38a28-1f4d-4141-b197-6fe4c6c8c001
temporary provider code: CODEX_SMOKE
temporary unified video id: 6f7a5f0b-99dc-4fa5-8d3b-d5d0430f0c01
temporary approved link id: e5177d08-6d24-44a6-87ac-3bb6f1a7c001
temporary rejected link id: e5177d08-6d24-44a6-87ac-3bb6f1a7c002
```

验证结果：

```text
GET /api/v1/magnet-providers: CODEX_SMOKE returned
GET /api/v1/magnet-providers/{id}: code=CODEX_SMOKE, priority=1
GET /api/v1/magnets/unified/{id}: returned 1 link
returned status: APPROVED
contains rejected link: false
magnet_search_jobs rows for temporary unified: 0
magnet_provider_runs rows for temporary provider: 0
```

清理结果：

```text
temporary magnet_links rows: 0
temporary magnet_providers rows: 0
temporary unified_videos rows: 0
RabbitMQ queues and DLQs: 0 messages
```

## 当前环境影响

`ircs-dev` 新增 1 个业务 Deployment 和 1 个 ClusterIP Service。当前业务服务
接入数从 12/18 提升到 13/18。

当前 quota 使用：

```text
pods:            18 / 20
services:        19 / 20
requests.cpu:    675m / 4
requests.memory: 3328Mi / 8Gi
limits.cpu:      5500m / 8
limits.memory:   10Gi / 16Gi
```

后续最紧的限制是 `services=19/20`。继续部署 identity-service 后会达到
`20/20`，再接 scraper、portal、interaction 或 ops 前需要处理 service quota 或
worker Service 策略。

## 后续建议

- `POST /api/v1/magnets/search/unified/{id}` 另立任务，明确外部 provider、内容策略、
  job/run 写入和 cache eviction。
- portal-service 接入详情页前，可通过内部调用或服务发现读取本服务的 approved
  links。
- 公网暴露前必须补 admin/member 访问策略，不能把 magnet URI 直接无保护暴露到
  dev HTTPRoute。
