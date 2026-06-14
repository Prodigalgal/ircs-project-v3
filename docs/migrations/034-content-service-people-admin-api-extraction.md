# 034 content-service people admin API extraction

状态：已完成

日期：2026-06-06

## 范围

本轮将 V1 admin 演员/导演字典接口迁移到 `ircs-content-service`，并继续通过
`ircs-admin-bff` 暴露给 admin 门户。

```text
POST   /api/v1/actors
GET    /api/v1/actors
GET    /api/v1/actors/all
GET    /api/v1/actors/{id}
PUT    /api/v1/actors/{id}
PATCH  /api/v1/actors/{id}
DELETE /api/v1/actors/{id}
POST   /api/v1/directors
GET    /api/v1/directors
GET    /api/v1/directors/all
GET    /api/v1/directors/{id}
PUT    /api/v1/directors/{id}
PATCH  /api/v1/directors/{id}
DELETE /api/v1/directors/{id}
```

## 实现

- 新增 `content/people` Controller、Service、Repository、DTO 和资源枚举。
- 分页列表支持 `name` 模糊过滤和安全排序。
- `PUT`/`PATCH` 使用 path id，兼容 V1 admin 前端 body 不带 `id` 的实际调用。
- 删除前检查 raw/unified 视频关联，避免删除已使用字典。

## 验证

```text
.\gradlew.bat :services:ircs-content-service:test -> PASS
.\gradlew.bat build -> PASS
image:  harbor.mnnu.eu.org/ircs/ircs-content-service@sha256:069bdf026e19c30dbbd653d784525d0b7ddccb49b1d1948d564706c6f6e2e8d1
rollout: ircs-content-service Ready 1/1
smoke:  admin-bff MARK 14/14
cleanup: actors/directors residue 0/0
```

## 结果

`025-12` 完成，actors/directors admin API 共 `14/14` 条接口完成迁移和复现标记。
