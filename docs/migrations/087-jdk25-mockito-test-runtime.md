# 087 JDK25 Mockito test runtime 迁移记录

状态：已完成

日期：2026-06-08

## 迁移内容

为本地 Gradle `Test` task 固化 Mockito inline agent 加载方式，避免 JDK25 下 ByteBuddy
self-attach/external attach 不稳定导致组合测试失败。

## 范围

```text
build.gradle
docs/reports/087-jdk25-mockito-test-runtime.md
tasks/done/028-15-jdk25-mockito-test-runtime.md
```

## 期望结果

```text
MARK 1/4 JDK25 Mockito inline attach failure has concrete XML/hs_err evidence
MARK 2/4 Gradle Test tasks preload Mockito javaagent or equivalent stable JVM flags
MARK 3/4 aggregation-worker tests pass without ad-hoc JAVA_TOOL_OPTIONS
MARK 4/4 identity/storage/task/aggregation combined tests pass
```

## 回滚边界

如测试 JVM 参数影响其他模块，回滚 `build.gradle` 的 Test task agent 配置；不涉及业务代码、
数据库、K8S 或镜像。

## 实施结果

- 根 `build.gradle` 已为所有子项目 `Test` task 预加载 Mockito javaagent。
- 未修改业务代码、K8S manifest、Jib 镜像运行参数或生产运行时参数。

## 验证结果

```powershell
.\gradlew.bat --no-daemon :services:ircs-aggregation-worker:test --rerun-tasks
.\gradlew.bat --no-daemon :services:ircs-identity-service:test :services:ircs-storage-service:test :services:ircs-task-service:test :services:ircs-aggregation-worker:test --rerun-tasks
```

结果均为 `BUILD SUCCESSFUL`。
