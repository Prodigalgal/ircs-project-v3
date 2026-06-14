# 028-20-13E credential redacted summary read-model cache

状态：done

父任务：`tasks/in-progress/028-20-post-parity-runtime-platform-breakdown.md`

## 范围

- credential-service redacted summary list / detail 热读缓存。
- L1 service-local + L2 Redis/Valkey + L3 DB fallback。
- cache value 只包含 `CredentialSummary`，即 API 返回前已经 redacted 的摘要对象。
- credential create/update/delete/createIfAbsent/refreshPool 后 evict read-model cache。
- 原有 credential lease cache 继续保持 service-local 短 TTL；不把 secret payload 写入 Redis/Valkey。
- dev K8S manifest 接入既有 `valkey-svc`，不新增 Pod/Service，不调整资源 requests/limits。

## 非目标

- 不缓存 `ProviderCredentialLease` secret payload 到 Redis/Valkey。
- 不缓存 templates/provider catalog。
- 不在本切片执行 K8S Redis live smoke。
- 不改变 credential API payload、redaction 语义或 internal lease contract。

## 输出

- `services/ircs-credential-service/build.gradle`
- `services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/CredentialReadModelCache.java`
- `services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/CredentialRedisStringCacheStore.java`
- `services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/CredentialService.java`
- `services/ircs-credential-service/src/test/java/com/prodigalgal/ircs/credential/CredentialServiceTest.java`
- `deploy/k8s/dev/credential-service-dev.yaml`

## 验证

```powershell
.\gradlew.bat --no-daemon :shared:ircs-common:test --tests "*TieredReadThroughCacheTest" :services:ircs-credential-service:test --tests "*CredentialServiceTest" --tests "*CredentialControllerTest" :services:ircs-credential-service:bootJar
kubectl apply --dry-run=client -f deploy\k8s\dev\credential-service-dev.yaml
```

结果：

```text
BUILD SUCCESSFUL
deployment.apps/ircs-credential-service configured (dry run)
service/ircs-credential-service configured (dry run)
```

## 遗留边界

- 跨副本 lease cache 若未来需要 L2，必须先定义敏感 cache 加密、TTL、evict 和审计策略。
- direct DB 修改仍依赖短 TTL 兜底；正常服务写入口会触发 evict。
- K8S Redis L2 key、跨副本读取和 `/internal/cache-governance` evict 需要在统一测试窗口确认。
