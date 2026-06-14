# 087 JDK25 Mockito test runtime 稳定性报告

状态：已完成

日期：2026-06-08

## 背景

主线程集成 `028-07-D`、`028-12-A`、`028-12-B` 后执行组合测试时，业务断言没有先失败，
但 JDK25 下 Mockito inline mock maker 在自附加 ByteBuddy agent 时出现不稳定。

失败证据：

```text
Could not initialize inline Byte Buddy mock maker.
It appears as if your JDK does not supply a working agent attachment mechanism.
Could not self-attach to current VM using external process
```

aggregation-worker 目录还生成了 `hs_err_pid*.log`，根因是 ByteBuddy 外部 attacher 进程启动
时尝试映射大块 G1 virtual space，触发 Windows native memory allocation failure。

## 目标

- 固化 JDK25 下 Mockito 测试运行参数，避免依赖运行时 self-attach。
- 让多模块组合测试不再因为 ByteBuddy 外部 attacher OOM 随机失败。
- 不修改业务代码、不 rollout、不变更 K8S。

## 非目标

- 不升级 Spring Boot、Mockito 或 JDK。
- 不调整业务测试断言。
- 不新增生产运行时参数。

## 写入范围

```text
build.gradle
docs/reports/087-jdk25-mockito-test-runtime.md
docs/migrations/087-jdk25-mockito-test-runtime.md
tasks/done/028-15-jdk25-mockito-test-runtime.md
```

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

## 风险

- Gradle 测试配置是全子项目共享配置，必须确保只影响 `Test` task，不影响服务运行时镜像。
- Mockito javaagent 依赖 test runtime classpath，需要使用 Gradle configuration 管理，避免硬编码
  本机缓存路径。

## 实施结果

- 在根 `build.gradle` 为所有子项目新增 `mockitoAgent` configuration。
- `mockitoAgent` 使用 `org.mockito:mockito-core` 且 `transitive=false`，不硬编码本机 Gradle
  cache 路径。
- 所有 `Test` task 通过 `jvmArgs "-javaagent:${configurations.mockitoAgent.singleFile.absolutePath}"`
  预加载 Mockito javaagent，避免运行中再启动 ByteBuddy 外部 attacher。
- 该配置只作用于 Gradle `Test` task，不进入 Jib 镜像和服务运行参数。

## 验收结果

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

## 遗留边界

- 历史失败生成的 `hs_err_pid*.log` 属于本轮测试运行残留，收口时可删除，不作为源码交付。
