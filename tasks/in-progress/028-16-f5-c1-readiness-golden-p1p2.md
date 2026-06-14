# 028-16-F5 C1 readiness golden P1/P2

状态：进行中；本轮完成 C1 readiness 优先切片，live 统一后置到 `UTB-02816-P1P2-02`

父任务：

- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`
- `tasks/in-progress/028-16-c1-initialization-readiness-gate.md`

## 本轮范围

- 不逐项 live 测试，不默认开启 Rabbit listener、watchdog、scheduler。
- 只做 focused tests / dry-run 级别验证。
- 收敛 `auto-start gate`、UUID、Log4j2 async logger / Disruptor 的 C1 readiness 口径。
- 补 catalog/content/task 三个 owner service 的 gate health detail。

## 代码证据与决策

### auto-start gate

V1 证据：

- V1 `DataInitializationOrchestrator` 初始化完成后调用 `RabbitListenerEndpointRegistry.start()`。
- V1 `RabbitMqConfig` 对 listener container 设置 `setAutoStartup(false)`。
- V1 `SystemStartupReadinessAspect` 在系统未 ready 时跳过 `@Scheduled` 方法。

V3 当前口径：

- 不在 C1 readiness P1/P2 中恢复全服务自动 `RabbitListenerEndpointRegistry.start()`。
- 保持 V3 更安全的显式 opt-in gate：content listener、content config listener、task scheduler、task watchdog 默认关闭。
- catalog/content/task 新增 health detail，暴露 gate 名称、property key、ENV key、enabled、source、blocked。
- catalog owner seed 新增 `APP_CATALOG_DEFAULT_SEED_ENABLED`，默认 `true`，关闭时不会触碰 repository。

本轮实现：

- `services/ircs-catalog-service/src/main/java/com/prodigalgal/ircs/catalog/CatalogAutoStartGateHealthConfiguration.java`
- `services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/ContentAutoStartGateHealthConfiguration.java`
- `services/ircs-task-service/src/main/java/com/prodigalgal/ircs/task/TaskAutoStartGateHealthConfiguration.java`
- `services/ircs-catalog-service/src/main/java/com/prodigalgal/ircs/catalog/CatalogDefaultInitializerRunner.java`
- `services/ircs-catalog-service/src/main/resources/application.yaml`

### UUID

V1 证据：

- V1 `com.prodigalgal.ircs.common.utils.IdGenerator` 使用 `Generators.timeBasedEpochGenerator()`。

V3 当前证据：

- V3 目标服务和 shared 仍主要使用 `UUID.randomUUID()` 或 migration SQL `gen_random_uuid()`。
- 当前 C1 readiness 缺口不是 ID 生成正确性 bug，而是横切性能/索引策略差异。

决策：

- 本轮不替换为 time-based UUID，不引入新的全局 UUID generator。
- 理由：会跨 schema、索引局部性、审计表、消息 payload 和测试 fixture，超出 C1 readiness 优先切片。
- 后续若需要，独立做 UUID strategy migration：先列写入热点表、索引、排序依赖和兼容窗口，再迁移。

### Log4j2 async logger / Disruptor

V1 证据：

- V1 `log4j2.xml` 使用多个 `<AsyncLogger>`，依赖 Disruptor。

V3 当前证据：

- 根 `build.gradle` 已排除 `spring-boot-starter-logging` 并统一使用 `spring-boot-starter-log4j2`。
- V3 当前未见共享 `log4j2.xml` / `log4j2-spring.xml`，也未见显式 `com.lmax:disruptor` 依赖。

决策：

- 本轮确认 V3 采用 Log4j2 同步默认配置作为 C1 readiness 可接受替代，不在 P1/P2 中启用 AsyncLogger。
- 理由：启用 AsyncLogger 需要服务级日志配置、Disruptor 依赖和启动 smoke，影响所有 service 镜像与日志落盘语义。
- 后续若追 V1 性能 parity，独立切片加入 `com.lmax:disruptor`、共享 log4j2 配置、dev/prod log path 验证和启动失败回滚方案。

## 验证

```powershell
.\gradlew.bat --no-daemon `
  :services:ircs-catalog-service:test `
    --tests com.prodigalgal.ircs.catalog.CatalogAutoStartGateHealthConfigurationTest `
    --tests com.prodigalgal.ircs.catalog.CatalogDefaultInitializerRunnerTest `
  :services:ircs-content-service:test `
    --tests com.prodigalgal.ircs.content.ContentAutoStartGateHealthConfigurationTest `
  :services:ircs-task-service:test `
    --tests com.prodigalgal.ircs.task.TaskAutoStartGateHealthConfigurationTest
```

结果：`BUILD SUCCESSFUL`

## 后置项

- `UTB-02816-P1P2-02` 执行 resolver/player/真实资源站 controlled live smoke。
- 如确认需要，另开全服务 listener 自动放行切片，专门验证 `RabbitListenerEndpointRegistry.start()`。
- 如确认需要，另开 UUID strategy / AsyncLogger+Disruptor 横切迁移切片。

## 2026-06-10 C1-X 补充

系统重启后核对已确认 C1-X 最小切片已落盘：

- `shared/ircs-common` 新增 `IrcsUuidGenerators` 门面，默认保持 `RANDOM`。
- `timeBasedEpoch()` 提供 V1 同款 `Generators.timeBasedEpochGenerator()` 显式入口，但未替换业务 UUID。
- shared audit 写入点改用 `IrcsUuidGenerators.defaultGenerator()`，不改变当前 ID 分布。
- `docs/decisions/004-uuid-async-logging-parity.md` 记录 AsyncLogger/Disruptor 不默认启用的原因和后续 opt-in 方案。

验证：

```powershell
.\gradlew.bat --no-daemon :shared:ircs-common:test --tests "com.prodigalgal.ircs.common.id.IrcsUuidGeneratorsTest" --rerun-tasks
```

结果：`BUILD SUCCESSFUL`。
