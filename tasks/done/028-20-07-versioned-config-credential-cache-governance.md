# 028-20-07 versioned config / credential / cache governance

状态：已完成

父任务：`tasks/in-progress/028-20-post-parity-runtime-platform-breakdown.md`

来源：`docs/decisions/005-post-parity-runtime-platform-decisions.md`

## 目标

以破坏性迁移方式升级 V3 运行时配置、凭据 lease 和本地缓存治理：

- 配置变更事件必须带可追踪 revision，不再兼容旧的无 revision 事件契约。
- credential-service 的内部 lease 支持短 TTL 缓存、写删强制失效和可观测 summary。
- shared common 提供轻量 cache governance 抽象，服务可注册本地 cache 并暴露 summary/evict。
- 追求更好一致性，但事件发布或 cache evict 失败不得阻断业务写入。

## 完成内容

- `SystemConfigChangedEvent` 破坏性升级为 `eventId/key/action/effectiveSource/sensitive/revision/previousRevision/changedAt`。
- config-service 的 `SystemConfigRecord` / `SystemConfigSummary` 暴露 `revision`，create/update/delete 事件携带 revision 轨迹。
- identity-service 的 `security.jwt.iat-floor` side-write 也切到 versioned config event。
- `ProviderCredentialLease` 暴露 `revision/leasedAt/expiresAt`。
- credential-service `sys_credentials.version` 进入 record/summary/lease，provider lease 接入 TTL cache。
- credential-service create/update/delete/refreshPool 会 best-effort evict lease cache，evict 失败不阻断业务。
- shared common 增加 `CacheRegistry`、`TtlGovernedCache`、`CacheGovernanceController`，通过 `/internal/cache-governance` 观测 summary 和执行 evict。
- migrator 增加 `add-versioned-config-credential-governance.sql`，补齐 `system_configs.version`、`sys_credentials.version` not-null/default/index。

## 验证证据

```powershell
.\gradlew.bat --no-daemon :shared:ircs-contracts:test :shared:ircs-common:test :platform:ircs-migrator:build :services:ircs-config-service:test :services:ircs-credential-service:test :services:ircs-identity-service:test :services:ircs-content-service:test :services:ircs-normalization-worker:test :services:ircs-aggregation-worker:test :services:ircs-search-service:test :services:ircs-notification-worker:test :services:ircs-ops-service:test :services:ircs-storage-service:test :services:ircs-portal-service:test :services:ircs-interaction-service:test :services:ircs-metadata-worker:test
```

结果：`BUILD SUCCESSFUL`

```powershell
.\gradlew.bat --no-daemon :shared:ircs-contracts:jar :shared:ircs-common:jar :platform:ircs-migrator:build :services:ircs-config-service:bootJar :services:ircs-credential-service:bootJar :services:ircs-identity-service:bootJar
```

结果：`BUILD SUCCESSFUL`

## 遗留风险

- 其他业务服务现有散落的 `SystemConfigRepository` 仍是各自本地实现，本切片只统一事件契约与 shared cache governance 基座，没有一次性重构所有缓存调用点。
- 未执行 K8S live rollout；后续统一测试窗口需要验证 `/internal/cache-governance` 在 dev Pod 内可访问，并确认旧事件 payload 在真实 Rabbit 中不会残留。
