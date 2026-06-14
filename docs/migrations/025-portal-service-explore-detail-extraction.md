# 025 portal-service explore/detail 迁移记录

状态：已完成

完成时间：2026-06-05

## 范围

本次迁移在现有 `ircs-portal-service` 中补齐 V1 前台内容只读接口：

```text
GET /api/portal/explore
GET /api/portal/movies/{id}
```

不迁移会员 auth/profile、后台会员管理、生产切换或物理 DB 拆分。不新增
Deployment、Service 或 HTTPRoute。

## 实现结果

新增响应模型：

```text
services/ircs-portal-service/src/main/java/com/prodigalgal/ircs/portal/PageResponse.java
services/ircs-portal-service/src/main/java/com/prodigalgal/ircs/portal/PortalMovieDetailResponse.java
```

更新：

```text
services/ircs-portal-service/src/main/java/com/prodigalgal/ircs/portal/PortalController.java
services/ircs-portal-service/src/main/java/com/prodigalgal/ircs/portal/PortalQueryService.java
services/ircs-portal-service/src/main/java/com/prodigalgal/ircs/portal/JdbcPortalRepository.java
services/ircs-portal-service/src/test/java/com/prodigalgal/ircs/portal/PortalControllerTest.java
services/ircs-portal-service/src/test/java/com/prodigalgal/ircs/portal/PortalQueryServiceTest.java
```

行为说明：

- `GET /api/portal/explore` 支持 `keyword`、`type`、`genre`、`area`、`year`、
  `language`、`sort` 和分页参数。
- `keyword` 超过 50 字截断，`size` 上限 70，分页深度超过 10000 返回 400。
- 首屏无 keyword 返回短缓存，否则 `no-store`。
- SQL explore 覆盖基础筛选与常用排序；ES relevance 排序作为后续增强。
- `GET /api/portal/movies/{id}` 返回 detail 基础字段、genres、area/language、cast、
  sources/episodes、approved magnets 和空 `cloudLinks`。
- 不存在的 movie id 返回 404。

## 验证

本地验证：

```text
.\gradlew.bat :services:ircs-portal-service:test
BUILD SUCCESSFUL

.\gradlew.bat build
BUILD SUCCESSFUL
```

ARM64 镜像：

```text
.\scripts\docker\push-project-image-jib.ps1 `
  -ProjectPath ':services:ircs-portal-service' `
  -ImageName ircs-portal-service `
  -Tag dev `
  -Architecture arm64 `
  -Os linux

Pushed harbor.mnnu.eu.org/ircs/ircs-portal-service:dev for linux/arm64
```

K8S rollout：

```text
kubectl rollout restart deployment/ircs-portal-service -n ircs-dev
kubectl rollout status deployment/ircs-portal-service -n ircs-dev --timeout=240s

deployment "ircs-portal-service" successfully rolled out
```

运行镜像：

```text
harbor.mnnu.eu.org/ircs/ircs-portal-service@sha256:f3c0ac1364a0517821f1a512364282683682ded50c4bdb1ef620d162a6bab32a
```

ClusterIP smoke：

```text
inserted unified,raw,episodes,magnet = 1,1,1,1

GET /api/portal/explore?... -> 200
totalElements=1
content[0].title=CODEX_02502 Movie 20260605150327

GET /api/portal/movies/{id} -> 200
sources[0].episodes[0].url=https://media.example.invalid/play/codex-02502.m3u8
magnets[0].title=CODEX_02502 Magnet

GET /api/portal/movies/{randomId} -> 404
```

清理结果：

```text
cleanup_remaining unified,raw,episodes,magnet,category=0,0,0,0,0
```

K8S 资源状态：

```text
No HTTPRoute in ircs-dev namespace.
pods=23/24
services=19/20
requests.cpu=795m/4
requests.memory=3968Mi/8Gi
limits.cpu=6700m/8
limits.memory=12672Mi/16Gi
```

最近 90 秒 portal-service 日志未发现 `ERROR`、`Exception`、constraint 或
`HttpMessageNotReadable`。

## 剩余缺口

本切片关闭 `PortalContentController` 中的内容浏览缺口。portal 相关剩余缺口主要为：

- `/api/portal/auth/**`：归属 `ircs-identity-service` 增强。
- `/api/portal/profile/**`：归属 `ircs-identity-service` profile 增强。
- `/api/v1/members/**`：后台会员管理，需单独 admin slice。

