# 028-20-10 mTLS service identity destructive migration

状态：done

父任务：`tasks/in-progress/028-20-post-parity-runtime-platform-breakdown.md`

来源：`docs/decisions/005-post-parity-runtime-platform-decisions.md`

## 完成内容

- `shared:ircs-common` 增加 `InternalServiceAuthHeaders` 和 `InternalServiceAccessPolicy`，统一 `X-IRCS-SERVICE-ID`、`X-IRCS-SERVICE-TOKEN`、`X-IRCS-SERVICE-SCOPES`。
- credential-service `/internal/credentials/**` 破坏性移除旧 `X-IRCS-INTERNAL-TOKEN` 服务端认证输入，改为 service identity + scope 校验。
- search-service internal sync API 破坏性切到 service identity + `search:sync` scope。
- metadata-worker、notification-worker、normalization-worker 的 credential-service 调用方已切到新 service identity headers。
- `APP_*_INTERNAL_TOKEN` runtime fallback 已从本切片涉及服务配置中移除，改为 `APP_*_SERVICE_TOKEN` 口径。
- dev credential-service manifest 改为读取 `SERVICE_CREDENTIAL_TOKEN`，不再读取 `INTERNAL_CREDENTIAL_TOKEN`。

## 验证

```powershell
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-credential-service:test :services:ircs-metadata-worker:test :services:ircs-notification-worker:test :services:ircs-normalization-worker:test :services:ircs-search-service:test
.\gradlew.bat --no-daemon :shared:ircs-common:jar :services:ircs-credential-service:bootJar :services:ircs-metadata-worker:bootJar :services:ircs-notification-worker:bootJar :services:ircs-normalization-worker:bootJar :services:ircs-search-service:bootJar
```

结果：以上命令均通过。

## 遗留边界

- 本切片落地应用层 service identity / scope contract；真实 mTLS 证书签发、K8S Secret 注入、服务端 TLS client cert 校验和 rollout smoke 后续已由 `028-20-25` 完成。
- 旧 `X-IRCS-INTERNAL-TOKEN` 在源码中只保留测试负断言字符串，用于确认客户端不再发送旧 header。
