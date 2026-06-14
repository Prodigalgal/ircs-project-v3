# 028-20-13C search portal read-model multi-tier cache

状态：done

父任务：`tasks/in-progress/028-20-post-parity-runtime-platform-breakdown.md`

## 范围

- search-service portal suggest / recommendations 匿名公共热读缓存。
- L1 service-local + L2 Redis/Valkey + L3 Elasticsearch fallback。
- scoped member/admin/data-scope 查询绕过缓存。
- unified search index 写入/删除后失效 public search read-model cache。
- dev K8S manifest 接入既有 `valkey-svc`，不新增 Pod/Service，不调整资源 requests/limits。

## 非目标

- 不缓存主搜索、admin 搜索或带用户 data scope 的个性化结果。
- 不在本切片执行 K8S Redis live smoke。
- 不改变 search API payload、权限模型或 ES query 语义。

## 输出

- `services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/SearchPortalReadModelCache.java`
- `services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/SearchRedisStringCacheStore.java`
- `services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/PortalSearchQueryService.java`
- `services/ircs-search-service/src/main/java/com/prodigalgal/ircs/search/index/SearchIndexService.java`
- `services/ircs-search-service/src/test/java/com/prodigalgal/ircs/search/PortalSearchQueryServiceTest.java`
- `services/ircs-search-service/src/test/java/com/prodigalgal/ircs/search/index/SearchIndexServiceTest.java`
- `deploy/k8s/dev/search-service-dev.yaml`

## 验证

```powershell
.\gradlew.bat --no-daemon :shared:ircs-common:test --tests "*TieredReadThroughCacheTest" :services:ircs-search-service:test --tests "*PortalSearchQueryServiceTest" --tests "*SearchIndexServiceTest" --tests "*PortalSearchControllerTest" :services:ircs-search-service:bootJar
kubectl apply --dry-run=client -f deploy\k8s\dev\search-service-dev.yaml
```

结果：

```text
BUILD SUCCESSFUL
deployment.apps/ircs-search-service configured (dry run)
service/ircs-search-service configured (dry run)
```

## 遗留边界

- search 查询主搜索、admin 搜索和带 keyword/data scope 的个性化结果暂不缓存。
- public search cache 当前依赖短 TTL + unified index save/delete evict；跨服务精准事件失效仍经 search index 写入路径间接触发。
- K8S Redis L2 key、跨副本读取和 `/internal/cache-governance` evict 需要在统一测试窗口确认。
