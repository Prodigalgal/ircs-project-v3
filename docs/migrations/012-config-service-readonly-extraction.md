# 012 config-service 只读配置 API 抽取

状态：已完成并在 `ircs-dev` smoke 通过

更新时间：2026-06-03

## 目标服务

```text
services/ircs-config-service
deploy/k8s/dev/config-service-dev.yaml
```

## V1 来源边界

```text
modules/system/controller/SystemConfigController
modules/system/model/domain/SystemConfig
modules/system/model/dto/SystemConfigDTO
system_configs
```

本切片只保留 V1 只读路径：

```text
GET /api/v1/configs
GET /api/v1/configs/{key}
```

后置路径：

```text
POST /api/v1/configs
PUT /api/v1/configs/{key}
DELETE /api/v1/configs/{key}
POST /api/v1/configs/test/{type}
```

## 边界决策

`ircs-config-service` 当前只读取 `system_configs`，并对敏感 key 进行响应脱敏。

敏感 key 规则包含：

```text
password
passwd
pwd
secret
token
api-key / api_key / apikey
access-key
private-key
credential
jwt
auth
```

命中敏感规则时：

```text
value:     null
sensitive: true
```

未命中时：

```text
value:     原始 config_value
sensitive: false
```

## ASCII 架构图

```text
                         ircs-dev namespace

        +-----------------------------------------------+
        | ircs-config-service                           |
        |                                               |
        | GET /api/v1/configs                           |
        | GET /api/v1/configs/{key}                     |
        |                                               |
        | - pageable + keyword read                     |
        | - sensitive value redaction                   |
        | - no write/test routes in this slice          |
        +----------------------+------------------------+
                               |
                               | JDBC read-only
                               v
        +-----------------------------------------------+
        | PostgreSQL                                    |
        | system_configs                                |
        +-----------------------------------------------+

        ClusterIP only. No HTTPRoute in ircs-dev.
```

## Dev 资源策略

```text
config-service: request 25m/128Mi, limit 500m/512Mi
deployment:     Recreate strategy to avoid one-pod surge on a tight node
routes:         ClusterIP only
```

本轮资源压缩同时完成：

```text
dev Java services: request.cpu 75m -> 25m
dev Elasticsearch: request.cpu 250m -> 100m, limit.cpu 1500m -> 1000m
deployment mode:   single-replica dev services use Recreate
```

原因：

```text
ircs-dev has LimitRange defaults:
  defaultRequest.cpu    = 100m
  defaultRequest.memory = 256Mi
  default.cpu           = 500m
  default.memory        = 1Gi

Omitting resources would be larger than explicit 25m/128Mi requests.
```

## 验证

本地测试：

```text
.\gradlew.bat :services:ircs-config-service:test
  PASS

.\gradlew.bat build
  PASS
```

Manifest 验证：

```text
kubectl apply --dry-run=server -f deploy\k8s\dev\config-service-dev.yaml
  PASS
```

ARM64 镜像：

```text
image:       harbor.mnnu.eu.org/ircs/ircs-config-service:dev
imageId:     sha256:871ce96c09311923a3a9e4ffd13cab2f1afc22d16394bda18f6397fa190ae781
imageDigest: sha256:ec0013d767dffd8ac19c19d6a6c7adfe99da6f474b6246b8384a4012767275b7
platform:    linux/arm64
```

Rollout：

```text
kubectl apply -f deploy\k8s\dev\config-service-dev.yaml
kubectl rollout status deployment/ircs-config-service -n ircs-dev --timeout=240s
  PASS, deployment Ready 1/1
```

Readiness：

```text
GET http://localhost:8080/actuator/health/readiness
  {"status":"UP"}
```

API smoke：

```text
GET /api/v1/configs/codex.mvp.config.enabled
  value=true
  sensitive=false

GET /api/v1/configs/codex.mvp.jwt.secret
  value=null
  sensitive=true
  response did not contain the fake secret value

GET /api/v1/configs?keyword=codex.mvp&page=0&size=10
  returned both temporary rows without leaking the fake secret value
```

清理：

```text
remaining_temp_configs=0
```

最终 `ircs-dev` 配额：

```text
pods:            13 / 20
services:        14 / 20
requests.cpu:    550m / 4
requests.memory: 2688Mi / 8Gi
limits.cpu:      6250m / 8
limits.memory:   7680Mi / 16Gi
```

节点 CPU request：

```text
before compression: 3997m / 4000m
after compression:  3522m / 4000m
```

## 剩余工作

- 接入配置写 API、校验、审计和权限模型。
- 接入 R2/Proxy/Mail/LLM 连通性测试，但必须先明确内部访问控制。
- 后续业务服务应通过 config-service 内部 API 读取系统配置，逐步减少直接读 `system_configs`。

