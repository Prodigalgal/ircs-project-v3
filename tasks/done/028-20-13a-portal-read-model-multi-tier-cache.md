# 028-20-13A portal read-model multi-tier cache

状态：完成

来源：`docs/decisions/005-post-parity-runtime-platform-decisions.md` D30

## 背景

D30 已确认大部分读多写少业务数据可以采用 L1/L2/L3 多级缓存来减少 IO，但 DB 仍是唯一真相。
`028-20-09D` 已在 maintenance gate 热路径证明了 L1 local + L2 Redis + L3 DB fallback 口径，
本切片把该口径扩展到通用业务 read model 的第一位 owner：portal-service 匿名公共读。

## 范围

- `shared:ircs-common`：通用 read-through cache 基座。
- `services:ircs-portal-service`：匿名公共 portal read model。

## 非目标

- 不把所有业务数据一次性缓存化。
- 不缓存登录态 / data scope / visibility 个性化结果。
- 不为强一致性阻断业务读；短 TTL + 可治理 evict 是本阶段边界。

## 输出

- 新增 `TieredReadThroughCache` 与 `TieredStringCacheStore`。
- 复用 `CacheRegistry` 暴露 summary/evict 治理语义。
- portal-service 新增 `PortalReadModelCache` 与 Redis L2 adapter。
- 匿名公共 metadata/home/explore/detail 进入 L1 local + L2 Redis/Valkey + L3 DB fallback。
- Redis miss/down/bad payload 自动回落 DB，Redis 不成为事实源。
- 登录态和带 keyword explore 继续直读 DB，避免公共缓存污染权限/个性化结果。

## 验证

```powershell
.\gradlew.bat --no-daemon :shared:ircs-common:test --tests "*TieredReadThroughCacheTest" --tests "*TtlGovernedCacheTest" :services:ircs-portal-service:test --tests "*PortalQueryServiceTest" --tests "*PortalControllerTest" :services:ircs-portal-service:bootJar
```

结果：

```text
BUILD SUCCESSFUL
```

## 遗留风险

- 未执行 K8S Redis live smoke；统一测试窗口需要验证 Redis L2 key 写入、跨副本命中和 governance evict。
- portal read model 当前依赖 TTL 和手动 governance evict；content/search 变更事件精确失效后续增强。
- catalog/search/credential/provider 等热读候选仍需逐 owner service 接入。
