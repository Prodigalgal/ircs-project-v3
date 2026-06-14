# 041 identity-service portal avatar upload extraction

状态：已完成

日期：2026-06-07

## 背景

本迁移承接 `025` V1 路由覆盖闭环。`ircs-identity-service` 已覆盖 member
auth/profile/password/check-in，但 V1 portal 仍有头像上传接口缺口。

## 覆盖接口

```text
POST /api/portal/profile/avatar
```

## 实现边界

- 接收 multipart `file`。
- 校验空文件、2MB 大小限制、content type 与基础图片签名。
- 返回 `{ "url": "..." }`，URL 形状为 `/media/avatars/{sha256}.{ext}`。
- 上传成功后更新 `members.avatar_url`。
- MVP 不落盘、不接 R2、不新增 avatar sync worker。

## BFF 修复

Live smoke 初次发现 `portal-bff` 预解析 multipart 导致上游 identity-service 收到的
`file` part 丢失。已在两个 BFF 中关闭 multipart 预解析：

```text
services/ircs-portal-bff/src/main/resources/application.yaml
services/ircs-admin-bff/src/main/resources/application.yaml
spring.servlet.multipart.enabled=false
```

这样 BFF 保留原始请求体，后端服务负责解析 multipart。

## 验证

```text
.\gradlew.bat :services:ircs-identity-service:test
BUILD SUCCESSFUL

.\gradlew.bat :services:ircs-portal-bff:test :services:ircs-admin-bff:test
BUILD SUCCESSFUL

.\gradlew.bat build
BUILD SUCCESSFUL
```

ARM64 rollout：

```text
deployment/ircs-identity-service successfully rolled out
deployment/ircs-portal-bff successfully rolled out
deployment/ircs-admin-bff successfully rolled out

identity imageID:   harbor.mnnu.eu.org/ircs/ircs-identity-service@sha256:24edd3d5c562b1bcd383ff19e9e111eb05ba8a55fb097179ab2709f35af4453a
portal-bff imageID: harbor.mnnu.eu.org/ircs/ircs-portal-bff@sha256:4a5aa6b1431666f9b5ac86077f16a20e120b9aa1b4000d75131262c245a2e25e
admin-bff imageID:  harbor.mnnu.eu.org/ircs/ircs-admin-bff@sha256:9dbac6b9532eb8c2a216a0994ed9d490dbd2657cb367959bea4ad8c2e58e3a50
```

`portal-bff` live smoke：

```text
SETUP portal member registered
SETUP portal member login
MARK 1/1 POST /api/portal/profile/avatar
members residue: 0
```

## 资源与流量

- 未新增 Pod、Service、PVC、中间件或 HTTPRoute。
- 只滚动已有 `ircs-identity-service`、`ircs-portal-bff` 和 `ircs-admin-bff`。
- `ircs-dev` 仍无 HTTPRoute。

完成计数：`1/1`。
