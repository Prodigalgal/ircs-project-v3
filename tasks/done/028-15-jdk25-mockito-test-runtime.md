# 028-15 JDK25 Mockito test runtime task spec

状态：已完成

父任务：

- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/087-jdk25-mockito-test-runtime.md`
- `docs/migrations/087-jdk25-mockito-test-runtime.md`

## 背景

主线程组合测试在 JDK25 下遇到 Mockito inline mock maker 初始化失败。单模块和前序测试
可以通过，但组合测试运行到后续模块时 ByteBuddy 外部 attacher 进程会因 native memory
allocation failure 生成 `hs_err_pid*.log`，使验收结果不稳定。

## 目标

- 复现并记录 Mockito/ByteBuddy attach failure 证据。
- 在 Gradle `Test` task 中固化稳定的 Mockito agent 加载方式。
- 重跑 aggregation-worker 和四模块组合测试。

## 非目标

- 不改业务代码。
- 不改 K8S、deploy 或镜像。
- 不绕过测试断言。

## 输入

```text
build.gradle
services/ircs-identity-service/build/test-results/test/*.xml
services/ircs-aggregation-worker/build/test-results/test/*.xml
services/ircs-aggregation-worker/hs_err_pid*.log
```

## 输出

```text
build.gradle test runtime configuration
docs/reports/087-jdk25-mockito-test-runtime.md
docs/migrations/087-jdk25-mockito-test-runtime.md
```

## 影响范围

```text
build.gradle
docs/reports/087-jdk25-mockito-test-runtime.md
docs/migrations/087-jdk25-mockito-test-runtime.md
tasks/done/028-15-jdk25-mockito-test-runtime.md
```

## 依赖

- Mockito core test runtime jar 可作为 javaagent 使用。
- JDK25 toolchain 可运行 Gradle 测试。

## 风险

- 全局 Test task 参数配置可能影响所有子项目测试，需要用组合测试验证。
- 不能硬编码本机 Gradle cache 路径。

## 验收标准

```text
MARK 1/4 JDK25 Mockito inline attach failure has concrete XML/hs_err evidence
MARK 2/4 Gradle Test tasks preload Mockito javaagent or equivalent stable JVM flags
MARK 3/4 aggregation-worker tests pass without ad-hoc JAVA_TOOL_OPTIONS
MARK 4/4 identity/storage/task/aggregation combined tests pass
```

## 测试边界

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
.\gradlew.bat --no-daemon :services:ircs-identity-service:test :services:ircs-storage-service:test :services:ircs-task-service:test :services:ircs-aggregation-worker:test --rerun-tasks
```

## 实施结果

- 已确认 failure XML 与 `hs_err_pid*.log` 均指向 JDK25/Mockito inline ByteBuddy self-attach
  失败，而非业务测试断言失败。
- 已在 `build.gradle` 通过 Gradle configuration 解析 `org.mockito:mockito-core`，并对
  所有 `Test` task 添加 Mockito `-javaagent`。
- 该改动只影响测试 JVM，不影响服务运行时和 K8S。

## MARK

```text
MARK 1/4 JDK25 Mockito inline attach failure has concrete XML/hs_err evidence - PASS
MARK 2/4 Gradle Test tasks preload Mockito javaagent or equivalent stable JVM flags - PASS
MARK 3/4 aggregation-worker tests pass without ad-hoc JAVA_TOOL_OPTIONS - PASS
MARK 4/4 identity/storage/task/aggregation combined tests pass - PASS
```

## 测试结果

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL in 21s`。

```powershell
.\gradlew.bat --no-daemon :services:ircs-identity-service:test :services:ircs-storage-service:test :services:ircs-task-service:test :services:ircs-aggregation-worker:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL in 42s`。
