# 025-19 identity-service portal avatar upload contract MVP 子任务

状态：已完成

父任务：

- `tasks/in-progress/025-v1-route-coverage-closeout-parent.md`

上游来源：

- `docs/requirements/025-v1-route-coverage-closeout.md`
- `docs/reports/025-v1-v3-route-coverage-audit.md`
- `docs/reports/040-remaining-route-slices-02518-02520.md`

## 背景

`ircs-identity-service` 当前已覆盖 member auth/profile/password/check-in，但 V1 portal
仍有 `POST /api/portal/profile/avatar`。门户后续持续验证需要头像上传契约完整复现。

## 目标

- 在 `ircs-identity-service` 复现 `POST /api/portal/profile/avatar` multipart 上传接口。
- 更新当前登录会员的 `members.avatar_url`。
- 返回 V1 兼容的 `{ "url": "..." }` 响应。
- 保持空文件、大小、content type 的基础校验。

## 非目标

- 不新增头像同步 worker。
- 不直接实现 R2 上传。
- 不新增 Pod、Service、HTTPRoute、PVC 或中间件。
- 不修改 `portal-bff` 路由，除非确认现有路由无法转发 multipart。

## 输入

```text
V1 MemberProfileController / MemberProfileServiceImpl.uploadAvatar
services/ircs-identity-service
services/ircs-portal-bff route table
```

## 输出

- identity avatar upload Controller/Service/Repository 增量。
- 对应单元测试。
- `portal-bff` live smoke 脚本或主 agent smoke 建议。

## 影响范围

允许写入：

```text
services/ircs-identity-service/src/main/java/com/prodigalgal/ircs/identity
services/ircs-identity-service/src/test/java/com/prodigalgal/ircs/identity
tasks/done/025-19-identity-service-portal-avatar-upload-contract-mvp.md
```

如现有 `portal-bff` 无法转发 multipart，允许先报告主 agent，不自行修改 BFF。

禁止写入：

```text
services/ircs-magnet-service
services/ircs-ops-service
deploy/k8s
```

## 验收标准

- 1 条接口可用并通过单元测试。
- 上传后 `members.avatar_url` 更新。
- 后续主 agent 通过 `portal-bff` live smoke 完成 `1/1` 标记。

## 测试边界

worker 计划执行：

```text
.\gradlew.bat :services:ircs-identity-service:test
```

主 agent 后续执行全仓 build、ARM64 rollout 和 live smoke。

## 本轮实施方案

- 在 `MemberProfileController` 增加 `POST /api/portal/profile/avatar`，声明 `multipart/form-data`，读取 `file` 字段和当前会员身份。
- 在 `MemberProfileService` 增加头像上传 contract 逻辑，完成空文件、文件大小和 content type 基础校验。
- MVP 不写本地磁盘、不接 R2、不新增 storage worker；对齐 V1 `/media` + `avatars` 形状，通过文件内容 SHA-256 和安全扩展名生成 `/media/avatars/{hash}.{ext}` URL。
- 使用现有 `MemberRecord` 更新能力写回 `members.avatar_url`，响应 `{ "url": "..." }`。
- `portal-bff` 已存在 `/api/portal/profile` 到 identity 的路由和 raw body proxy；本轮不改 BFF，multipart live smoke 留给主 agent。

## 本轮子任务

- `[x] 025-19-A`：identity-service controller/service/DTO 增量，输出头像上传 contract。
- `[x] 025-19-B`：identity-service 单元测试覆盖成功更新、空文件、大小限制、content type 校验。
- `[x] 025-19-C`：运行 `.\gradlew.bat :services:ircs-identity-service:test` 并回写实施和测试结果。

## 本轮实施结果

- 已在 `MemberProfileController` 增加 `POST /api/portal/profile/avatar`，请求类型为 `multipart/form-data`，参数为 `file`。
- 已在 `MemberProfileService` 增加 `uploadAvatar`，对齐 V1 2MB 限制，并覆盖空文件、大小、content type 与基础图片签名校验。
- 已新增 `AvatarUploadResponse`，接口响应 JSON 字段为 `{ "url": "..." }`。
- 已新增 `MemberRecord.withAvatarUrl`，上传通过后更新当前会员 `members.avatar_url`。
- 本 MVP 不落盘、不接 R2、不新增 worker；URL 使用 `/media/avatars/{sha256}.{ext}` 形状，供后续 storage/avatar sync contract 继续收敛。
- 已只读复核 `portal-bff` route table 与 proxy：`/api/portal/profile` 指向 identity，proxy 使用 raw body 转发并保留非 hop-by-hop headers；本轮未修改 BFF，multipart live smoke 交给主 agent。

## 本轮测试结果

命令：

```text
.\gradlew.bat :services:ircs-identity-service:test
```

结果：

```text
BUILD SUCCESSFUL in 7s
10 actionable tasks: 3 executed, 7 up-to-date
```

覆盖：

- 成功上传图片后返回 `/media/avatars/*.png` URL，并写回 `MemberRecord.avatarUrl`。
- 空文件返回 `avatar/file.empty`。
- 超过 2MB 返回 `avatar/file.size.limit`。
- 不支持的 content type 返回 `avatar/content.type.invalid`。

## 后续交接

- 主 agent 已通过 `portal-bff` live smoke 验证真实 multipart 请求链路。
- Live smoke 初次发现 BFF multipart 预解析导致上游缺失 `file` part；已在
  `services/ircs-portal-bff/src/main/resources/application.yaml` 和
  `services/ircs-admin-bff/src/main/resources/application.yaml` 关闭 BFF 自身
  `spring.servlet.multipart.enabled`，让后端服务解析原始 multipart body。
- `portal-bff` 和 `admin-bff` 已重新推送 ARM64 镜像并 rollout。

主 agent 集成验证：

```text
.\gradlew.bat :services:ircs-portal-bff:test :services:ircs-admin-bff:test
BUILD SUCCESSFUL

.\gradlew.bat build
BUILD SUCCESSFUL
```

ARM64 rollout：

```text
deployment/ircs-identity-service successfully rolled out
identity imageID:   harbor.mnnu.eu.org/ircs/ircs-identity-service@sha256:24edd3d5c562b1bcd383ff19e9e111eb05ba8a55fb097179ab2709f35af4453a
portal-bff imageID: harbor.mnnu.eu.org/ircs/ircs-portal-bff@sha256:4a5aa6b1431666f9b5ac86077f16a20e120b9aa1b4000d75131262c245a2e25e
admin-bff imageID:  harbor.mnnu.eu.org/ircs/ircs-admin-bff@sha256:9dbac6b9532eb8c2a216a0994ed9d490dbd2657cb367959bea4ad8c2e58e3a50
```

Live smoke：

```text
SETUP portal member registered
SETUP portal member login
MARK 1/1 POST /api/portal/profile/avatar
members residue: 0
```

完成计数：`1/1`。
