# 012-01 config-service 只读配置 MVP 子任务

状态：已完成

父任务：

- `tasks/in-progress/011-mvp-service-onboarding-parent.md`

关联文档：

- `docs/requirements/011-mvp-service-onboarding-acceleration.md`
- `docs/architecture/001-target-microservice-boundaries.md`
- `docs/architecture/002-cutover-plan.md`

## 背景

`ircs-config-service` 尚未接入 `ircs-dev`。V1 配置边界位于 `modules/system`，核心表为 `system_configs`，主要读路径为 `/api/v1/configs` 和 `/api/v1/configs/{key}`。为了加快 MVP 服务整体接入，本切片先抽取只读配置查询能力。

## 目标

- 为 `ircs-config-service` 提供只读系统配置 API。
- 保留 V1 查询路径 `/api/v1/configs` 和 `/api/v1/configs/{key}`。
- 使用当前 PostgreSQL `system_configs` 表作为迁移桥。
- 增加 dev Deployment/Service，保持 ClusterIP only。
- 完成本地测试、server dry-run、ARM64 镜像、K8S rollout 和 smoke。

## 非目标

- 不接入配置创建、更新、删除。
- 不接入 `/api/v1/configs/test/{type}` 连通性测试。
- 不处理 JWT/admin 权限模型。
- 不新增 HTTPRoute。
- 不拆分独立数据库。

## 输入

- V1 `SystemConfigController`
- V1 `SystemConfig` / `SystemConfigDTO`
- V3 `services/ircs-config-service`
- `system_configs` 表

## 输出

- `ircs-config-service` 只读 JDBC 实现。
- `deploy/k8s/dev/config-service-dev.yaml`。
- 迁移文档和 K8S 快照更新。

## 影响范围

```text
services/ircs-config-service
deploy/k8s/dev/config-service-dev.yaml
docs/migrations
docs/k8s
deploy/k8s/README.md
tasks
```

## 风险

- `system_configs` 包含密钥类配置，公开读接口不能直接暴露敏感值。
- V1 admin 权限尚未迁移，本切片只能在 ClusterIP 内部 smoke，不新增公网路由。
- 前端如果依赖写接口，仍需后续单独迁移。

## 验收标准

- 敏感 key 的 `value` 在响应中脱敏或置空。
- `GET /api/v1/configs` 支持 keyword 和分页参数。
- `GET /api/v1/configs/{key}` 能返回临时测试 key。
- 临时测试数据 smoke 后清理为 0。
- `ircs-config-service` 在 `ircs-dev` Ready 1/1 且无 HTTPRoute。

## 测试边界

```text
.\gradlew.bat :services:ircs-config-service:test
.\gradlew.bat build
kubectl apply --dry-run=server -f deploy\k8s\dev\config-service-dev.yaml
kubectl rollout status deployment/ircs-config-service -n ircs-dev --timeout=240s
集群内 HTTP smoke
```

## 实现结果

- `ircs-config-service` 已提供只读 `/api/v1/configs` 和
  `/api/v1/configs/{key}`。
- 敏感 key 响应会返回 `value=null` 与 `sensitive=true`。
- `deploy/k8s/dev/config-service-dev.yaml` 已部署到 `ircs-dev`，ClusterIP
  only，无 HTTPRoute。
- 当前 MVP 接入数在本阶段继续提升；后续 `013` 已继续完成 task-service 接入。

## 验证结果

```text
.\gradlew.bat :services:ircs-config-service:test
  PASS

.\gradlew.bat build
  PASS

kubectl apply --dry-run=server -f deploy\k8s\dev\config-service-dev.yaml
  PASS

kubectl exec -n ircs-dev deployment/ircs-config-service -- wget -qO- http://localhost:8080/actuator/health/readiness
  {"status":"UP"}

GET /api/v1/configs/codex.mvp.config.enabled
  value=true
  sensitive=false

GET /api/v1/configs/codex.mvp.jwt.secret
  value=null
  sensitive=true
  fake secret value was not present

GET /api/v1/configs?keyword=codex.mvp&page=0&size=10
  returned both temporary rows without leaking the fake secret value

remaining_temp_configs=0
```
