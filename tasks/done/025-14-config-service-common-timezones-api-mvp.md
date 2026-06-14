# 025-14 config-service common timezones API MVP 子任务

状态：已完成

父任务：

- `tasks/in-progress/025-v1-route-coverage-closeout-parent.md`

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/038-parallel-subagent-next-route-slices.md`

## 背景

V1 admin 前端 settings 和 collection task schedule 组件会读取
`GET /api/v1/common/timezones`。`admin-bff` 已把 `/api/v1/common/**` 代理到
`ircs-config-service`，但 config-service 目前尚未实现该 common API。

## 目标

- 在 `ircs-config-service` 复现 `GET /api/v1/common/timezones`。
- 返回 V1 前端可直接使用的 timezone 字符串数组。
- 不访问数据库，不触发外部调用。

## 非目标

- 不实现 config create/update/delete。
- 不实现 config connection test。
- 不修改 admin-bff。
- 不新增 K8S 资源。

## 输入

```text
V1 commonApi.js
V1 CommonController 或 common/timezones 实现
services/ircs-config-service
```

## 输出

- config-service common controller/service 或等价小实现。
- 对应单元测试。
- 子任务完成结果和 smoke 建议。

## 影响范围

允许写入：

```text
services/ircs-config-service/src/main/java/com/prodigalgal/ircs/config
services/ircs-config-service/src/test/java/com/prodigalgal/ircs/config
tasks/done/025-14-config-service-common-timezones-api-mvp.md
```

禁止写入：

```text
services/ircs-content-service
services/ircs-catalog-service
services/ircs-admin-bff
deploy/k8s
```

## 验收标准

- `GET /api/v1/common/timezones` 返回 200。
- 响应包含 `Asia/Shanghai`、`UTC` 等常用 timezone。
- 单元测试通过。
- 后续主 agent 通过 admin-bff live smoke 完成 `1/1` 标记。

## 测试边界

worker 计划执行：

```text
.\gradlew.bat :services:ircs-config-service:test
```

主 agent 后续执行全仓 build、ARM64 rollout 和 live smoke。

## 实现结果

实现时间：2026-06-06

- 已读取 V1 `ircs-frontend/src/features/common/commonApi.js`，确认前端通过 `/common/timezones` 读取字符串数组。
- 已读取 V1 `CommonController#getTimezones`，确认返回 Java `ZoneId` 支持的 timezone ID，并按字母顺序排序。
- 已在 `ircs-config-service` 新增 `CommonController`：
  - 路由：`GET /api/v1/common/timezones`
  - 返回：按字母顺序排序的 `List<String>`
  - 数据来源：`ZoneId.getAvailableZoneIds()`
  - 不访问数据库，不触发外部调用，不实现 configs write/test/delete。
- 已新增 `CommonControllerTest`，通过 standalone `MockMvc` 验证 V1 前端可直接使用的 JSON 数组。

## 验证结果

验证时间：2026-06-06

```text
.\gradlew.bat :services:ircs-config-service:test
```

结果：通过。

覆盖点：

- `GET /api/v1/common/timezones` 返回 200。
- 响应体是 JSON array。
- 响应包含 `Asia/Shanghai`、`UTC`、`America/New_York`、`Europe/London`、`Asia/Tokyo`。
- 响应数组按字母顺序排序。

## 主 agent 集成验证

```text
.\gradlew.bat :services:ircs-config-service:test -> PASS
.\gradlew.bat build -> PASS
image:  harbor.mnnu.eu.org/ircs/ircs-config-service@sha256:eb25f939d7cc2b73a0b682623aa8fc12ef318226d5f74f300e3fa44f12165e98
rollout: ircs-config-service Ready 1/1
smoke:  admin-bff MARK 1/1
```

live smoke：

```text
MARK 1/1 GET /api/v1/common/timezones
```

验证响应包含：

```text
Asia/Shanghai
UTC
America/New_York
```

迁移记录：

```text
docs/migrations/036-config-service-common-timezones-api-extraction.md
```
