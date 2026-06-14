# 028-16-C1-X UUID / AsyncLogger-Disruptor cross-cutting parity

状态：进行中；本轮完成最小落地切片，不做 live rollout

父任务：

- `tasks/in-progress/028-16-f5-c1-readiness-golden-p1p2.md`

## 目标

- 为 V3 UUID 生成提供 shared 门面，默认保持 random UUID 兼容，给后续 time-based epoch 迁移留显式入口。
- 为 V1 AsyncLogger / Disruptor parity 记录服务级 opt-in 方案、默认不开原因和回滚条件。

## 范围

- 写入 `shared/ircs-common/**`、`docs/decisions/**` 和本任务文件。
- 不全量替换业务代码 `UUID.randomUUID()`。
- 不修改 DB migration 的 `gen_random_uuid()` / `random_uuid()`。
- 不修改 normalization/search/scraper 文件。

## 实现

- `IrcsUuidGenerators.defaultGenerator()` / `random()`：保持 V3 当前 random UUID 策略。
- `IrcsUuidGenerators.timeBasedEpoch()`：使用 V1 同款 `Generators.timeBasedEpochGenerator()`，只供后续显式迁移切片接入。
- shared/common audit 写入点改用 `IrcsUuidGenerators.defaultGenerator()`，仍然生成 random v4 UUID。
- `docs/decisions/004-uuid-async-logging-parity.md`：记录 async logging 的服务级开启方式和回滚条件。

## 验收标准

- `shared:ircs-common` 单测通过。
- 当前默认 UUID 策略仍为 `RANDOM`，不会改变现有业务写入行为。
- AsyncLogger / Disruptor 不默认进入所有 service runtime。

## 验证

已运行：

```powershell
.\gradlew.bat --no-daemon :shared:ircs-common:test --tests com.prodigalgal.ircs.common.id.IrcsUuidGeneratorsTest
```

结果：`BUILD SUCCESSFUL`

## 后置项

- 若要迁移业务 UUID，先盘点写入热点表、索引、排序依赖、消息 payload、审计表和 fixture，再按 service 分批替换。
- 若要启用 AsyncLogger，先选择单个 service 加 Disruptor + `log4j2-async-spring.xml`，跑启动 smoke 和日志落盘验证，通过后再推广。
