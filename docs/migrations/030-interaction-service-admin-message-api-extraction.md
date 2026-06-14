# 030 interaction-service admin message API extraction

状态：已完成

日期：2026-06-06

## 背景

V1 `AdminMessageController` 已验证的后台留言管理接口需要迁移到 V3
`ircs-interaction-service`。本切片承接 `025` 路由覆盖闭环，并在 portal/admin 门户和
admin-bff 已部署后，通过真实 admin 入口链路验证。

## 已迁移接口

```text
MARK 1/4 GET    /api/v1/messages -> HTTP 200
MARK 2/4 POST   /api/v1/messages/{id}/reply -> HTTP 200
MARK 3/4 PUT    /api/v1/messages/{id}/visibility -> HTTP 200
MARK 4/4 DELETE /api/v1/messages/{id} -> HTTP 204
```

## 实现范围

```text
services/ircs-interaction-service/src/main/java/com/prodigalgal/ircs/interaction/AdminMessageController.java
services/ircs-interaction-service/src/main/java/com/prodigalgal/ircs/interaction/AdminMessageService.java
services/ircs-interaction-service/src/main/java/com/prodigalgal/ircs/interaction/JdbcAdminMessageRepository.java
services/ircs-interaction-service/src/main/java/com/prodigalgal/ircs/interaction/AdminReplyRequest.java
services/ircs-interaction-service/src/main/java/com/prodigalgal/ircs/interaction/MessageVisibilityRequest.java
services/ircs-interaction-service/src/main/java/com/prodigalgal/ircs/interaction/UserMessageResponse.java
```

`UserMessageResponse` 同时输出 `isPublic` 和 `public`，用于兼容 V1 DTO 字段和 admin
门户 `row.public` 开关读取。

## 验证

本地验证：

```text
.\gradlew.bat :services:ircs-interaction-service:test -> PASS
.\gradlew.bat build -> PASS
```

K8S 验证：

```text
ARM64 image:
  harbor.mnnu.eu.org/ircs/ircs-interaction-service:dev
  sha256:6b272d19acf269781317b9c59028eb67220601b79f977bbd40efc50fc4cbccd0

rollout:
  kubectl -n ircs-dev rollout status deploy/ircs-interaction-service --timeout=240s -> PASS

route:
  frontend-gateway -> admin-bff -> ircs-interaction-service

cleanup:
  remaining temporary user_messages | members = 0 | 0

httproute:
  No resources found in ircs-dev namespace.

quota:
  pods 28/30
  services 24/26
```

## 非目标与遗留

- 不新增 Pod、Service、HTTPRoute、RabbitMQ 队列或 PVC。
- 不新增后台认证体系。
- 不做消息软删除或审计表。
- `025` 后续继续推进 `025-09 task-service task-runner contract and scheduler execution`。
