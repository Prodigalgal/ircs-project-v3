# 004 UUID / AsyncLogger-Disruptor Cross-Cutting Parity

状态：Accepted for minimal C1-X slice

日期：2026-06-10

## Context

V1 证据：

- `D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-backend\src\main\java\com\prodigalgal\ircs\common\utils\IdGenerator.java` 使用 `Generators.timeBasedEpochGenerator()`。
- V1 `ircs-backend/build.gradle` 依赖 `com.fasterxml.uuid:java-uuid-generator:5.2.0` 和 `com.lmax:disruptor:3.4.4`。
- V1 `log4j2.xml` 对 `com.prodigalgal.ircs`、`org.springframework`、`ingestion-tracer` 等 logger 使用 `<AsyncLogger>`。

V3 证据：

- 根 `build.gradle` 已排除 `spring-boot-starter-logging` 并为 service projects 统一加入 `spring-boot-starter-log4j2`。
- V3 当前未见共享 `log4j2.xml` / `log4j2-spring.xml`，也未见显式 `com.lmax:disruptor` 依赖。
- V3 UUID 写入点仍分散使用 `UUID.randomUUID()`，迁移 SQL 仍使用 PostgreSQL/H2 侧 `gen_random_uuid()` / `random_uuid()`。

## Decision

- 本轮不全量替换业务代码 UUID，不修改 schema default，不触碰 normalization/search/scraper 相关实现。
- 在 `shared:ircs-common` 提供 `IrcsUuidGenerators` 门面：
  - `defaultGenerator()` 和 `random()` 明确保持 V3 当前 random UUID 兼容策略。
  - `timeBasedEpoch()` 只作为后续显式迁移切片可选策略，语义对齐 V1 `IdGenerator`。
- 本轮不默认启用 AsyncLogger，也不把 Disruptor 加到所有 service runtime。
- Async logging 后续按服务级 opt-in 开启，先在单个低风险 service 做启动 smoke，再推广。

## Async Logging Enablement Plan

服务级开启时需要同时满足：

- 对目标 service 增加 `implementation 'com.lmax:disruptor:3.4.4'`，或在确认所有 service 都要启用后再提升到根 `serviceProjects`。
- 添加服务自己的 `log4j2-spring.xml`，使用 `<AsyncLogger>` / `<AsyncRoot>`，`includeLocation="false"`。
- 通过环境变量或 profile 指向异步配置，例如：

```powershell
$env:LOGGING_CONFIG = "classpath:log4j2-async-spring.xml"
```

- 保留默认同步 Log4j2 配置作为回滚路径；删除或改回 `LOGGING_CONFIG` 即可回退。

## Rollback Conditions

- service 启动时报 `NoClassDefFoundError` / `ClassNotFoundException` for Disruptor。
- 日志落盘路径、console 格式或 trace/span MDC 丢失。
- shutdown 期间日志 flush 不完整。
- smoke 中发现日志吞吐优化收益不足以覆盖运维复杂度。

## Consequences

- C1-X 当前落地为“可编译 UUID strategy + 明确 ADR”，不会改变已有 ID 分布和索引局部性。
- AsyncLogger parity 保留为明确后置项，避免在 readiness P1/P2 中扩大 service 镜像和日志语义变更面。
