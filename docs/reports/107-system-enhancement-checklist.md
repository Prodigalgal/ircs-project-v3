# IRCS V3 系统增强清单

状态：待处理

更新时间：2026-06-11

## 一、分布式协调问题（多副本安全）

### 问题总览

| 组件 | 服务 | 风险 | 有分布式锁 | 问题描述 |
|------|------|------|-----------|---------|
| MaintenanceScheduler | ops-service | **高** | 无 | 多副本重复执行维护任务 |
| TaskStartupRecovery | task-service | **高** | 无 | 多副本并发恢复中断任务 |
| NormalizationRetryWatchdog | normalization-worker | **中** | 无 | 多副本重复 republish 消息 |
| StorageR2Watchdog | storage-service | **中** | 无 | 多副本重复上传封面/同步头像 |
| LlmCleaningRunner | normalization-worker | **中** | 无 | 多副本重复调用 LLM API |
| SearchReconciliationRunner | search-service | **中** | 无 | 默认 dryRun 安全，启用后有风险 |
| LlmCleaningInternalController | normalization-worker | **中** | 无 | HTTP 触发与定时任务并发冲突 |
| MetadataPendingRegistry | metadata-worker | **中** | 无 | Redis Set 操作非原子 |
| SystemConfigChangedListener | 各服务（10个） | **中** | - | 需确认队列 exclusive 配置 |
| SearchOutboxRelay | search-service | **低** | ✅ FOR UPDATE SKIP LOCKED | 安全 |
| AggregationScheduler | aggregation-worker | **低** | ✅ FOR UPDATE SKIP LOCKED | 安全 |
| TaskWatchdog | task-service | **低** | 无（幂等） | 安全 |
| DefaultCollectionTaskSeeder | task-service | **低** | 无（幂等） | 安全 |
| 所有业务 @RabbitListener | 各服务 | **低** | ✅ RabbitMQ 竞争消费 | 安全 |

### 高风险项

#### 1. MaintenanceScheduler（ops-service）

**文件**: `services/ircs-ops-service/src/main/java/com/prodigalgal/ircs/ops/MaintenanceScheduler.java:15`

**问题**: 多个 ops-service 副本会同时执行 `scheduledTick()`，调用 `MaintenanceRunnerService.run()` 触发批量同步任务（search sync batch、LLM cleaning 等），导致重复执行。

**当前保护**: 仅 `AtomicBoolean running` 防本地重入。

**建议修复**:
```java
// 方案 A: 使用 ShedLock（推荐，基于数据库）
@SchedulerLock(name = "maintenanceScheduler", lockAtLeastFor = "PT5M", lockAtMostFor = "PT10M")
@Scheduled(...)
void tick() { ... }

// 方案 B: 使用 Redis 分布式锁
if (!redisLock.tryLock("maintenance:scheduler", Duration.ofMinutes(10))) {
    return;
}
try {
    schedulerService.scheduledTick();
} finally {
    redisLock.unlock("maintenance:scheduler");
}
```

#### 2. TaskStartupRecovery（task-service）

**文件**: `services/ircs-task-service/src/main/java/com/prodigalgal/ircs/task/TaskStartupRecovery.java:28`

**问题**: 多个 task-service 副本同时启动时，会并发执行 `recoverInterruptedTasks`，将 QUEUED/RUNNING/STOPPING 状态的 task 改为 PAUSED。虽然结果是幂等的，但会产生重复审计日志，且可能误杀正在执行的任务。

**当前保护**: 无。

**建议修复**:
```java
// 使用 FOR UPDATE SKIP LOCKED 改写查询
WITH interrupted AS (
    SELECT id FROM collection_tasks
    WHERE status IN ('QUEUED', 'RUNNING', 'STOPPING')
    FOR UPDATE SKIP LOCKED
)
UPDATE collection_tasks t
SET status = 'PAUSED', error_message = 'Startup recovery'
FROM interrupted
WHERE t.id = interrupted.id
RETURNING t.id
```

### 中风险项

#### 3. NormalizationRetryWatchdog（normalization-worker）

**文件**: `services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/NormalizationRetryWatchdog.java:25`

**问题**: `findDueFailedNormalizationRecoveryIds` 是普通 SELECT（无 FOR UPDATE SKIP LOCKED），然后逐条 `markRecoveryQueued` 做乐观 UPDATE。多副本会查询到同一批 FAILED 记录，导致重复 publish 消息到队列。

**当前保护**: `markRecoveryQueued` 的 WHERE 条件提供一定去重。

**建议修复**: 将查询改为 `FOR UPDATE SKIP LOCKED`，或将 find+mark 合并为一个原子 CTE 语句（与 SearchOutboxRelay 和 AggregationScheduler 保持一致）。

#### 4. StorageR2Watchdog（storage-service）

**文件**: `services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/StorageR2Watchdog.java:48`

**问题**: `findLocalAvatarMemberIds` 和 `findDueLocalR2UploadIds` 是普通 SELECT。多副本会重复 publish 相同的 avatar sync 和 cover image 上传命令。

**当前保护**: avatar sync 是幂等的（最终一致性），但 cover image R2 上传如果并发执行可能导致重复请求。

**建议修复**: 对 cover image 上传增加数据库级别的乐观锁（如 `UPDATE WHERE status='PENDING'`），或加分布式锁。

#### 5. LlmCleaningRunner（normalization-worker）

**文件**: `services/ircs-normalization-worker/src/main/java/com/prodigalgal/ircs/normalization/LlmCleaningRunner.java:23`

**问题**: `findCandidates` 是普通 SELECT，`touchCandidates` 只更新 `updated_at`。多副本会找到同一批候选记录，调用 LLM API 处理相同数据，浪费资源。

**当前保护**: `applyMatch` 使用 `WHERE ... is null` 有一定幂等性。

**建议修复**: 将 findCandidates + touchCandidates 合并为一个原子 `SELECT ... FOR UPDATE SKIP LOCKED`，或引入分布式锁。

#### 6. MetadataPendingRegistry（metadata-worker）

**文件**: `services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/MetadataPendingRegistry.java:17`

**问题**: 使用 `redisTemplate.opsForSet().add/remove/size` 操作 Redis Set。`register()` 中 `add` 和 `expire` 不是原子操作；`complete()` 中 `remove` 和 `size` 也不是原子的。

**建议修复**: 使用 Lua 脚本将 `complete` 操作原子化；`register` 中用 Lua 保证 add+expire 原子性。

```lua
-- complete() 的 Lua 脚本
local remaining = redis.call('SREM', KEYS[1], ARGV[1])
if remaining == 0 then
    local size = redis.call('SCARD', KEYS[1])
    return size
end
return -1
```

---

## 二、代码质量审计

### 2.1 异常处理

#### 问题：大量 `catch (Exception ignored)` 和 `catch (RuntimeException ignored)`

**统计**: 代码库中有 104 处 `catch (.*ignored)` 模式。

**主要分布**:
- `EmptyResultDataAccessException ignored`: 约 40 处，用于 Optional 查询模式，**可接受**
- `NumberFormatException ignored`: 约 15 处，用于配置解析 fallback，**可接受**
- `RuntimeException ignored`: 约 10 处，需审查

**需关注的高风险项**:
| 文件 | 行号 | 问题 |
|------|------|------|
| `DashboardQueryService.java` | 49, 58 | 吞掉 RuntimeException，仪表盘查询失败时静默返回空 |
| `TrafficMonitorService.java` | 46 | 吞掉 RuntimeException，流量监控数据丢失 |
| `PortalSearchQueryService.java` | 178 | 吞掉 RuntimeException，搜索降级时无日志 |
| `ScraperMappingService.java` | 164, 195, 251, 316, 332 | 多处 Exception ignored，爬虫解析失败时静默跳过 |
| `RawVideoMetadataRepository.java` | 100 | 吞掉 Exception，元数据保存失败时无日志 |

**建议**: 对 `RuntimeException ignored` 的 catch 块，至少添加 `log.debug` 或 `log.trace` 级别日志，便于排查问题。

### 2.2 事务管理

#### 问题：@Transactional 使用统计

**统计**: 代码库中有 121 处 `@Transactional` 注解。

**正面发现**:
- 查询方法正确使用 `@Transactional(readOnly = true)` ✅
- 写操作方法使用 `@Transactional` ✅
- 未发现嵌套事务问题

**潜在风险**:
| 文件 | 行号 | 问题 |
|------|------|------|
| `AggregationService.java` | 21 | `aggregateBatch` 在事务中执行大量数据库操作，事务持有时间可能较长 |
| `CatalogService.java` | 77-348 | 30+ 个 @Transactional 方法，部分可能涉及大量数据操作 |

**建议**: 对批量操作考虑分批提交，避免长事务。

### 2.3 并发安全

#### 问题：共享可变状态

**正面发现**:
- 所有 `@Scheduled` 方法使用 `AtomicBoolean running` 防重入 ✅
- 使用 `ConcurrentHashMap` 管理缓存 ✅
- 使用 `@RequiredArgsConstructor` + `final` 字段保证不可变性 ✅

**潜在风险**:
| 文件 | 行号 | 问题 |
|------|------|------|
| `MetadataPendingRegistry.java` | 17 | Redis 操作非原子（见分布式协调问题） |
| `TaskLogService.java` | - | Redis List 的 `leftPush + trim` 非原子，日志场景可容忍 |

### 2.4 SQL 注入

**正面发现**: 代码库全面使用 `NamedParameterJdbcTemplate` 和参数化查询 ✅，未发现字符串拼接 SQL 的情况。

### 2.5 配置管理

**正面发现**:
- 敏感信息通过 K8S Secret 注入 ✅
- 配置有合理的默认值 ✅
- 使用 `@ConditionalOnProperty` 控制功能开关 ✅

**潜在风险**:
| 文件 | 行号 | 问题 |
|------|------|------|
| `CredentialSanitizer.java` | 67 | `catch (Exception ignored)` 可能掩盖凭据脱敏失败 |
| `IrcsJwtRuntimeConfigResolver.java` | 54 | JWT 配置解析失败时静默 fallback |

### 2.6 日志规范

**正面发现**:
- 使用 Log4j2 + Disruptor 异步日志 ✅
- 使用结构化日志（MDC）✅
- 审计日志使用独立的 `WorkerJobAuditWriter` ✅

**潜在风险**:
| 文件 | 行号 | 问题 |
|------|------|------|
| `ManualScraperService.java` | 103 | 爬虫异常日志可能包含敏感 URL |
| `SearchSyncConsumer.java` | 49 | 消息处理失败时日志可能包含完整消息体 |

### 2.7 API 设计

**正面发现**:
- 使用 `@Valid` 进行输入验证 ✅
- 使用分页查询（`LIMIT` + `OFFSET`）✅
- 返回类型使用 DTO 而非实体 ✅

**潜在风险**:
| 文件 | 行号 | 问题 |
|------|------|------|
| `SearchReconciliationRunner.java` | 94 | 批量查询无上限保护，可能返回大量数据 |
| `AggregationService.java` | 22 | `aggregateBatch` 无超时保护 |

---

## 三、架构改进建议

### 3.1 分布式锁基础设施

**现状**: 当前没有统一的分布式锁机制。

**建议**: 引入以下之一：
- **ShedLock**: 基于数据库的分布式锁，适合当前架构
- **Redis 分布式锁**: 使用 Redisson 或手写 Lua 脚本
- **Kubernetes Leader Election**: 适合单副本执行的场景

### 3.2 Outbox 模式统一

**现状**: SearchOutboxRelay 和 AggregationScheduler 使用 `FOR UPDATE SKIP LOCKED`，其他组件未使用。

**建议**: 将 NormalizationRetryWatchdog、StorageR2Watchdog、LlmCleaningRunner 统一改为相同的模式。

### 3.3 监控和告警

**现状**: 使用 Micrometer + Prometheus 收集指标，但缺乏告警规则。

**建议**: 添加以下告警：
- 任务处理延迟超过阈值
- 任务失败率超过阈值
- 分布式锁持有时间过长
- RabbitMQ 队列积压

### 3.4 配置管理增强

**现状**: 配置通过 SystemConfig + 环境变量管理。

**建议**:
- 添加配置变更审计日志
- 添加配置验证（启动时校验必填配置）
- 添加配置回滚机制

---

## 四、优先级排序

### P0（立即修复）

1. **MaintenanceScheduler 分布式锁**: 防止多副本重复执行维护任务
2. **TaskStartupRecovery 分布式锁**: 防止多副本并发恢复中断任务

### P1（尽快修复）

3. **NormalizationRetryWatchdog 改为 FOR UPDATE SKIP LOCKED**: 与 SearchOutboxRelay 保持一致
4. **StorageR2Watchdog 改为 FOR UPDATE SKIP LOCKED**: 防止重复上传
5. **LlmCleaningRunner 改为 FOR UPDATE SKIP LOCKED**: 防止重复调用 LLM API
6. **MetadataPendingRegistry Lua 脚本**: 保证 Redis 操作原子性

### P2（计划修复）

7. **异常处理增强**: 对 `RuntimeException ignored` 添加 debug 日志
8. **监控告警规则**: 添加关键指标的告警
9. **配置验证**: 启动时校验必填配置

### P3（长期改进）

10. **分布式锁基础设施**: 统一分布式锁方案
11. **Outbox 模式统一**: 所有定时任务使用相同的协调机制
12. **配置管理增强**: 添加变更审计和回滚机制
