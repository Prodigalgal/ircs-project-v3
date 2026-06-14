# 028-20-13D magnet provider/link read-model multi-tier cache

状态：done

父任务：`tasks/in-progress/028-20-post-parity-runtime-platform-breakdown.md`

## 范围

- magnet-service provider list / detail / enabled provider list 热读缓存。
- approved magnet links 按 `unifiedVideoId` 热读缓存。
- L1 service-local + L2 Redis/Valkey + L3 DB fallback。
- provider create/update 后 evict provider 读模型。
- provider search upsert link 后 evict 对应 unified video 的 approved-links 读模型。
- dev K8S manifest 接入既有 `valkey-svc`，不新增 Pod/Service，不调整资源 requests/limits。

## 非目标

- 不缓存 search job/run 管理查询。
- 不在本切片执行 K8S Redis live smoke。
- 不改变 magnet API payload、provider runner 策略或 link approve 语义。

## 输出

- `services/ircs-magnet-service/build.gradle`
- `services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet/MagnetReadModelCache.java`
- `services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet/MagnetRedisStringCacheStore.java`
- `services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet/MagnetQueryService.java`
- `services/ircs-magnet-service/src/test/java/com/prodigalgal/ircs/magnet/MagnetQueryServiceTest.java`
- `deploy/k8s/dev/magnet-service-dev.yaml`

## 验证

```powershell
.\gradlew.bat --no-daemon :shared:ircs-common:test --tests "*TieredReadThroughCacheTest" :services:ircs-magnet-service:test --tests "*MagnetQueryServiceTest" --tests "*MagnetControllerTest" :services:ircs-magnet-service:bootJar
kubectl apply --dry-run=client -f deploy\k8s\dev\magnet-service-dev.yaml
```

结果：

```text
BUILD SUCCESSFUL
deployment.apps/ircs-magnet-service configured (dry run)
service/ircs-magnet-service configured (dry run)
```

## 遗留边界

- search job/run 状态查询仍直接读 DB；这些状态变化频繁，后续若缓存应使用极短 TTL 或 no-store 策略。
- provider/detail cache 由 magnet-service 写入口触发失效；直接 DB 改动仍依赖 TTL 兜底。
- K8S Redis L2 key、跨副本读取和 `/internal/cache-governance` evict 需要在统一测试窗口确认。
