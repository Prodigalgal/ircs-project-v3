# 028-16-C dependency initializer tooling parity task spec

状态：进行中

父任务：

- `tasks/in-progress/028-16-v1-v3-deep-parity-gap-audit-parent.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/088-v1-v3-deep-parity-gap-audit.md`
- `docs/migrations/088-v1-v3-deep-parity-gap-audit.md`

## 背景

V1 `build.gradle` 和 infrastructure 包含大量运行时能力：opencc4j、HanLP、Tika、CQEngine、
JGraphT、RxJava、Disruptor、HttpClient5、commons-text、MapStruct、UUID generator、
hibernate-micrometer、Spring AI、Thymeleaf/Mail、Caffeine/cache、SSRF/UserAgent、HTTP client、
data initializers、SystemConfig cache/event。V3 当前按微服务最小可运行方式接入，仍需逐项判断
哪些属于必须 1:1 接回，哪些可以明确后置或替代。

## 目标

- 建立 V1/V3 dependency matrix。
- 建立 V1 initializer/config/support matrix。
- 按服务边界决定依赖接回位置。
- 对不接回的库给出等效实现或非目标理由。

## 非目标

- 不把 V1 所有依赖加到根 `build.gradle`。
- 不引入未使用依赖。
- 不修改 K8S 或镜像，除非后续子任务单独实现。

## 输入

```text
V1 build.gradle
V1 infrastructure/dataInit/**
V1 infrastructure/http/**
V1 infrastructure/support/**
V1 infrastructure/cache/**
V1 infrastructure/adaptive/**
V1 infrastructure/llm/**
V3 build.gradle
V3 services/*/build.gradle
V3 shared/**
```

## 输出

- dependency/initializer/tooling matrix。
- service-scoped follow-up tasks。
- 被替代或后置的依赖决策记录。

## 影响范围

```text
docs/reports/088-v1-v3-deep-parity-gap-audit.md
tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md
```

后续实现可能影响各服务 `build.gradle` 和 shared modules。

## 风险

- 盲目接回依赖会膨胀 ARM64 image。
- 不接回依赖又可能导致 V1 行为差异继续隐藏。
- 初始化器若重复执行，可能污染 dev 数据。

## 验收标准

```text
MARK 1/6 V1/V3 Gradle dependency matrix is complete
MARK 2/6 data initializer parity matrix is complete
MARK 3/6 support/http/cache/llm/tooling matrix is complete
MARK 4/6 each missing dependency has service owner or explicit non-goal
MARK 5/6 each initializer has target service owner or explicit migration decision
MARK 6/6 follow-up tasks are linked from docs/tasks indexes
```

## 测试边界

本任务当前是只读审计和任务拆分，不执行 Gradle 测试。后续接回依赖时按服务运行测试。

## 当前状态

已合并审计 agent C 的依赖/初始化器/工具库细节，并拆出第一批 P0/P1 实现切片：

```text
tasks/in-progress/028-16-c1-initialization-readiness-gate.md
tasks/in-progress/028-16-c2-system-config-cache-event-parity.md
tasks/in-progress/028-16-c3-shared-outbound-http-client-parity.md
```

后续还需继续拆 cache TTL/evict、UUID generator、AsyncLogger/Disruptor、CQEngine/RxJava
依赖卫生和 MapStruct/mapper guard。

2026-06-09：`028-16-C2` 已按用户要求确认并加固配置优先级：V1 风格 SystemConfig
初始化可以保留，但只作为最低优先级 seed；ENV、K8S Secret/ConfigMap、configtree、启动参数等运行时注入源为最高
优先级。config-service、identity-service 和 interaction-service 已补本地测试，且 P0
消费者不会把 `application.yaml` 静态默认值误判为高优先级注入源。随后继续加固
config-service 自身的 effective source 计算与 MAIL SSL/STARTTLS 校验，统一使用
`RuntimeInjectedConfig -> DB -> default seed`，`ConfigServiceTest` 已增至 22 个用例并通过。

2026-06-10：`028-16-C1-S2c` 已完成 readiness/initialization remaining first closure
实现。catalog-service 已复用 V1 countries/languages 全量 preset；cache 口径收敛为 owner
initializer 启动期 seed/warmup + SystemConfig event evict/short TTL read-through，且
normalization-worker 已补默认 30s TTL fallback focused test。resolver/真实资源站 live smoke
只准备 dry-run 脚本，真实 live 后置到统一测试窗口。
