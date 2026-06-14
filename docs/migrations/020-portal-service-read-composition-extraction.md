# 020 portal-service read composition MVP 迁移记录

状态：已完成

更新时间：2026-06-04

## 背景

本迁移从 V1 portal 内容读边界中抽取公开读聚合 MVP。首个切片只接入
`/api/portal/metadata` 和 `/api/portal/home`，保持前端消费 shape，不接入 search、
movie detail、auth/profile 或 interaction 写路径。

## 实现范围

```text
services/ircs-portal-service
deploy/k8s/dev/portal-service-dev.yaml
```

已接入路由：

```text
GET /api/portal/metadata
GET /api/portal/home
```

MVP 使用共享 PostgreSQL 作为迁移桥，只读 `unified_videos`、catalog 关联表、
`cover_images` 和 `source_domains`。

## K8S 结果

```text
deployment:    ircs-portal-service Ready 1/1
service:       ircs-portal-service ClusterIP 10.99.214.176
HTTPRoute:     none
resources:     request 20m/128Mi, limit 200m/384Mi
image:         harbor.mnnu.eu.org/ircs/ircs-portal-service:dev
digest:        sha256:a2ca28ce3f71ad223257640c2c9396bb591c7ab022407717dc94ca60aa52a7cb
```

## 验证结果

```text
.\gradlew.bat :services:ircs-portal-service:test
.\gradlew.bat :services:ircs-portal-service:build
.\gradlew.bat build
kubectl apply --dry-run=server -f deploy\k8s\dev\portal-service-dev.yaml
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-portal-service' -ImageName ircs-portal-service -Tag dev -Architecture arm64 -Os linux
kubectl rollout status deployment/ircs-portal-service -n ircs-dev --timeout=240s
```

ClusterIP smoke：

```text
GET /api/portal/metadata -> categories, genres, areas, languages, years
GET /api/portal/home     -> spotlight, trending, sections
```

无新增 HTTPRoute；smoke 不写业务数据。

## 遗留问题

- `/api/portal/explore` 仍应通过 search-service 承接。
- `/api/portal/movies/{id}` 涉及播放源、封面、磁链和详情缓存，后续单独拆分。
- portal read model cache 和跨服务内部 API 替代共享 DB 读取仍后置。

## 关联任务

- `tasks/done/017-01-portal-read-composition-mvp.md`
- `tasks/in-progress/011-mvp-service-onboarding-parent.md`
