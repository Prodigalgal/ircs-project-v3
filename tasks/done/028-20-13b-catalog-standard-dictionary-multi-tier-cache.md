# 028-20-13B catalog standard dictionary multi-tier cache

状态：完成

来源：`docs/decisions/005-post-parity-runtime-platform-decisions.md` D30

## 背景

`028-20-13A` 已把多级 read-through cache 基座落到 portal anonymous public read model。
catalog standard dictionary 是 portal/admin/worker 都会反复读取的稳定热数据，适合作为第二个 owner
service 接入 L1/L2/L3。

## 范围

- `services:ircs-catalog-service`
- 标准分类、类型、地区、语言 summary/read list
- dev K8S catalog-service Redis/Valkey env

## 非目标

- 不缓存 raw mapping、data-source、分页查询或外部 fetch sample。
- 不新增 Pod/Service，不改变 catalog-service 资源 requests/limits。
- 不执行 K8S live rollout/smoke。

## 输出

- catalog-service 增加 `spring-boot-starter-data-redis`。
- 新增 `CatalogReadModelCache` 与 `CatalogRedisStringCacheStore`。
- 标准字典 summary/read list 接 L1 local + L2 Redis/Valkey + L3 DB fallback。
- 标准字典 create/update/patch/delete 成功后 evict 对应 summary/read cache。
- Redis miss/down/bad payload 回落 DB；DB 仍是唯一真相。
- `deploy/k8s/dev/catalog-service-dev.yaml` 显式指向 `valkey-svc.ircs-dev.svc.cluster.local:6379`。

## 验证

```powershell
.\gradlew.bat --no-daemon :shared:ircs-common:test --tests "*TieredReadThroughCacheTest" :services:ircs-catalog-service:test --tests "*CatalogServiceReadModelCacheTest" --tests "*CatalogControllerTest" --tests "*CatalogServiceDataSourceSyncTriggerTest" :services:ircs-catalog-service:bootJar

kubectl apply --dry-run=client -f deploy\k8s\dev\catalog-service-dev.yaml
```

结果：

```text
BUILD SUCCESSFUL
deployment.apps/ircs-catalog-service configured (dry run)
service/ircs-catalog-service configured (dry run)
```

## 遗留风险

- catalog default initializer 直接写 repository；运行时 reseed 若未来开放，需要同步触发 cache evict。
- raw mapping 与分页管理查询仍保持 DB 直读，后续需更细事件失效或短 TTL 策略后再缓存。
- Redis L2 跨副本命中和 `/internal/cache-governance` evict 仍需统一 K8S smoke 验证。
