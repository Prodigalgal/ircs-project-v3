# 010-02 credential-service 内部 token 加固子任务

状态：已完成

父任务：

- `tasks/in-progress/011-mvp-service-onboarding-parent.md`

关联文档：

- `docs/migrations/010-credential-service-provider-lease-and-metadata-client.md`
- `docs/k8s/current-environment-2026-06-02.md`

## 背景

`credential-service` 已提供内部 provider lease API，`metadata-worker` 已通过该内部 API 获取 TMDB credential pool。该内部接口返回 scalar secret payload，必须在 dev 环境先加上最小内部访问令牌，避免无鉴权的集群内读取。

## 目标

- 为 `/internal/credentials/providers/{provider}/leases` 增加 `X-IRCS-INTERNAL-TOKEN` 校验。
- `metadata-worker` 通过环境变量注入相同 token 并随请求发送。
- token 使用现有 `ircs-dev-secrets`，不新增 Secret。

## 非目标

- 不实现完整 RBAC/admin 鉴权。
- 不暴露公网路由。
- 不增加 NetworkPolicy，后续单独补强。

## 输出

- `CredentialInternalAccessProperties`
- `InternalCredentialController` token 校验
- `MetadataCredentialServiceProperties.token`
- `TmdbCredentialRepository` 内部 header
- `deploy/k8s/dev/credential-service-dev.yaml`
- `deploy/k8s/dev/metadata-worker-dev.yaml`

## 验证

```text
.\gradlew.bat :services:ircs-credential-service:test
  PASS

.\gradlew.bat :services:ircs-metadata-worker:test
  PASS

.\gradlew.bat build
  PASS

kubectl apply --dry-run=server -f deploy\k8s\dev\credential-service-dev.yaml
  PASS

kubectl apply --dry-run=server -f deploy\k8s\dev\metadata-worker-dev.yaml
  PASS

no token -> 401
wrong token -> 401
correct token -> []
```

## 验收结果

`ircs-credential-service` 和 `ircs-metadata-worker` 均已重新推送 ARM64 镜像并在 `ircs-dev` rollout 成功。metadata-worker 空 TMDB credential pool smoke 仍走 credential-service lease 路径，结果为 `POOL_EXHAUSTED`，不是连接失败。

## 遗留问题

- 后续仍需要 NetworkPolicy 或更完整的内部身份模型。
- credential write、rotation、audit 仍未接入。

