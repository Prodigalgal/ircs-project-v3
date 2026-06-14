# 036 config-service common timezones API extraction

状态：已完成

日期：2026-06-06

## 范围

本轮将 V1 admin 前端使用的 common timezone 字符串数组接口迁移到
`ircs-config-service`：

```text
GET /api/v1/common/timezones
```

## 实现

- 新增 `CommonController`。
- 数据来源为 `ZoneId.getAvailableZoneIds()`。
- 响应按字母顺序排序。
- 不访问数据库，不触发外部 HTTP 调用。

## 验证

```text
.\gradlew.bat :services:ircs-config-service:test -> PASS
.\gradlew.bat build -> PASS
image:  harbor.mnnu.eu.org/ircs/ircs-config-service@sha256:eb25f939d7cc2b73a0b682623aa8fc12ef318226d5f74f300e3fa44f12165e98
rollout: ircs-config-service Ready 1/1
smoke:  admin-bff MARK 1/1
```

## 结果

`025-14` 完成，common timezones API `1/1` 完成迁移和复现标记。
