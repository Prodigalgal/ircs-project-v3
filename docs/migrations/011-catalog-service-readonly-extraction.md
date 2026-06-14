# 011 catalog-service 只读字典 API 抽取

状态：已完成并在 `ircs-dev` smoke 通过

更新时间：2026-06-03

## 目标服务

```text
services/ircs-catalog-service
deploy/k8s/dev/catalog-service-dev.yaml
```

## V1 来源边界

```text
standard_category
standard_genre
standard_areas
standard_languages
raw_category
raw_genres
raw_areas
raw_languages
data_sources
```

V1 对应的读取路由包括：

```text
/api/v1/standard-categories
/api/v1/standard-genres
/api/v1/standard-areas
/api/v1/standard-languages
/api/v1/raw-categories
/api/v1/raw-genres
/api/v1/raw-areas
/api/v1/raw-languages
/api/v1/data-sources
```

## V3 实现范围

本切片只抽取只读查询能力。服务使用 JDBC 读取当前迁移桥 PostgreSQL 表，不接管写入、归一化清洗或独立 schema。

新增或变更的主要文件：

```text
services/ircs-catalog-service/build.gradle
services/ircs-catalog-service/src/main/resources/application.yaml
services/ircs-catalog-service/src/main/java/com/prodigalgal/ircs/catalog/CatalogController.java
services/ircs-catalog-service/src/main/java/com/prodigalgal/ircs/catalog/CatalogService.java
services/ircs-catalog-service/src/main/java/com/prodigalgal/ircs/catalog/JdbcCatalogRepository.java
services/ircs-catalog-service/src/main/java/com/prodigalgal/ircs/catalog/*Summary.java
services/ircs-catalog-service/src/test/java/com/prodigalgal/ircs/catalog/CatalogControllerTest.java
deploy/k8s/dev/catalog-service-dev.yaml
```

## API 边界

提供新 catalog 前缀和 V1 兼容别名：

```text
GET /api/v1/catalog/standard-categories
GET /api/v1/catalog/standard-genres
GET /api/v1/catalog/standard-areas
GET /api/v1/catalog/standard-languages
GET /api/v1/catalog/raw-categories
GET /api/v1/catalog/raw-genres
GET /api/v1/catalog/raw-areas
GET /api/v1/catalog/raw-languages
GET /api/v1/catalog/data-sources

GET /api/v1/standard-categories
GET /api/v1/standard-genres
GET /api/v1/standard-areas
GET /api/v1/standard-languages
GET /api/v1/raw-categories
GET /api/v1/raw-genres
GET /api/v1/raw-areas
GET /api/v1/raw-languages
GET /api/v1/data-sources
```

## ASCII 架构图

```text
                         ircs-dev namespace

        +-----------------------------------------------+
        | ircs-catalog-service                          |
        |                                               |
        | /api/v1/catalog/standard-categories           |
        | /api/v1/catalog/standard-genres               |
        | /api/v1/catalog/standard-areas                |
        | /api/v1/catalog/standard-languages            |
        | /api/v1/catalog/raw-categories                |
        | /api/v1/catalog/raw-genres                    |
        | /api/v1/catalog/raw-areas                     |
        | /api/v1/catalog/raw-languages                 |
        | /api/v1/catalog/data-sources                  |
        |                                               |
        | V1 aliases stay available under /api/v1/*      |
        +----------------------+------------------------+
                               |
                               | JDBC read-only
                               v
        +-----------------------------------------------+
        | PostgreSQL                                    |
        |                                               |
        | standard_category      raw_category            |
        | standard_genre         raw_genres              |
        | standard_areas         raw_areas               |
        | standard_languages     raw_languages           |
        | data_sources                                  |
        +-----------------------------------------------+

        ClusterIP only. No HTTPRoute in ircs-dev.
        No queue consumer in this slice.
```

## Dev 资源策略

```text
catalog-service: request 25m/128Mi, limit 500m/512Mi
routes:          ClusterIP only
middleware:      reuse current PostgreSQL, no new middleware
```

部署后的 `ircs-dev` 配额：

```text
pods:            12 / 20
services:        13 / 20
configmaps:      3 / 30
secrets:         4 / 30
requests.cpu:    1025m / 4
requests.memory: 2560Mi / 8Gi
limits.cpu:      6250m / 8
limits.memory:   7Gi / 16Gi
```

## 验证

本地测试：

```text
.\gradlew.bat :services:ircs-catalog-service:test
  PASS

.\gradlew.bat build
  PASS
```

Manifest 验证：

```text
kubectl apply --dry-run=server -f deploy\k8s\dev\catalog-service-dev.yaml
  PASS
```

ARM64 镜像：

```text
image:       harbor.mnnu.eu.org/ircs/ircs-catalog-service:dev
imageId:     sha256:76a0b19605f1a0662d4711df8a386a14ba1ba0ed3f30636c816e5deef3ff362d
imageDigest: sha256:dc8af6f1de3513697b1b32973f4b1db3844395346179b783d4206b2e0beef08e
platform:    linux/arm64
```

Rollout：

```text
kubectl apply -f deploy\k8s\dev\catalog-service-dev.yaml
kubectl rollout status deployment/ircs-catalog-service -n ircs-dev --timeout=240s
  PASS, deployment Ready 1/1
```

Readiness：

```text
GET http://localhost:8080/actuator/health/readiness
  {"status":"UP"}
```

API smoke 使用临时数据覆盖以下路径：

```text
/api/v1/catalog/standard-categories
/api/v1/standard-categories
/api/v1/catalog/raw-categories
/api/v1/catalog/data-sources
/api/v1/standard-genres
/api/v1/standard-areas
/api/v1/standard-languages
/api/v1/raw-genres
/api/v1/raw-areas
/api/v1/raw-languages
```

结果：

```text
catalog_smoke_ok
remaining_temp_rows=0
```

## 剩余工作

- 增加 catalog 写入 API、校验和审计。
- 抽出 raw 到 standard 的归一化维护边界。
- 后续让 content-service、normalization-worker 等通过 catalog-service 内部 API 读取字典，逐步减少跨域直接读表。
- 按需要再补 NetworkPolicy，限制只读 catalog API 的内部调用方。
