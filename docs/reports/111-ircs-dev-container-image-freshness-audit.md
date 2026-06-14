# 111 ircs-dev container image freshness audit

状态：完成本轮运行中镜像对账；全服务源码清理后的全量重打包仍需后续切片

日期：2026-06-12

## 背景

本轮目标是确认 `ircs-dev` 中所有运行中容器是否使用 registry 当前 tag 的最新 digest，
避免旧镜像导致新代码、worker audit 或 smoke 结果失真。过程中同时按项目约束清理生产代码
`@Autowired`，并修复由新镜像暴露出的 worker 启动缺口。

## 结果

全量运行中 Pod 镜像对账结果：

```text
CURRENT_TAG_DIGEST 22
EXTERNAL_OR_INFRA   6
STALE_TAG_DIGEST    0
REMOTE_LOOKUP_FAILED 0
```

证据文件：

```text
build/tmp/ircs-dev-container-image-freshness-20260612111028.json
build/tmp/ircs-dev-container-image-freshness-20260612111028.csv
```

本轮发现并处理的旧镜像：

```text
ircs-metadata-worker        已重推并滚动，Ready 1/1
ircs-normalization-worker   已修复启动缺口、重推并滚动，Ready 1/1
ircs-storage-service        已重推并滚动，Ready 1/1
```

## 修复摘要

- 生产代码 `@Autowired` 已清为 0：`rg "@Autowired|import org.springframework.beans.factory.annotation.Autowired" services shared platform -g "*.java"` 在 `src/main` 范围无结果。
- `LlmCredentialResolver` 改为 Lombok `@RequiredArgsConstructor` 单构造器，`OutboundHttpClient` 由 `NormalizationOutboundConfiguration` 提供。
- `normalization-worker` 补 `NormalizationSearchSyncConfiguration`，为 `RawSearchSyncPublisher` 提供 `SearchSyncOutboxWriter`。
- `LlmCleaningService`、`RawVideoTextNormalizer`、`SystemConfigRepository`、`StorageR2Watchdog`、`CoverImageDownloadService` 去除会影响 Spring 构造器选择的生产多构造器路径。
- `normalization-worker` dev probe 保持更宽启动窗口，适配 HanLP/Tomcat 冷启动。

## 验证

```powershell
.\gradlew.bat --no-daemon compileJava
.\gradlew.bat --no-daemon :services:ircs-normalization-worker:test :services:ircs-storage-service:test --tests *LlmCredentialResolverTest --tests *LlmCleaningServiceTest --tests *LlmCleaningInternalControllerTest --tests *RawVideoNormalizationServiceTest --tests *RawVideoTextNormalizerTest --tests *SystemConfigRepositoryTest --tests *StorageR2WatchdogTest --tests *CoverImageDownloadServiceTest --rerun-tasks
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-normalization-worker' -ImageName ircs-normalization-worker -Tag dev -Architecture arm64 -Os linux
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-storage-service' -ImageName ircs-storage-service -Tag dev -Architecture arm64 -Os linux
kubectl -n ircs-dev rollout status deployment/ircs-normalization-worker --timeout=900s
kubectl -n ircs-dev rollout status deployment/ircs-storage-service --timeout=600s
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\worker-audit-metadata-normalization-storage-02812c-smoke.ps1 -FailOnSkip
```

结果：

```text
compileJava BUILD SUCCESSFUL
normalization/storage focused tests BUILD SUCCESSFUL
ircs-normalization-worker Ready 1/1, restart 0
ircs-storage-service Ready 1/1, restart 0
worker audit smoke MARK 6/6 PASSED
```

worker audit smoke 证据：

```text
trace: 02812c-worker-audit-20260612111112
metadata audit:      skipped
normalization audit: succeeded
storage audit:       succeeded
queue/DLQ residue:   0
ops query:           metadata=true, normalization=true, storage=true
```

## 边界与后续

本轮 digest 对账证明的是：当前运行中 Pod digest 等于 Harbor 当前 tag digest。

由于本轮同时清理了全项目生产 `@Autowired`，只有 normalization-worker、storage-service
以及先前 metadata-worker 已重新打包并滚动到包含本轮修复的镜像。其他服务当前仍是
registry tag 最新运行态，但不代表已经包含本轮源码清理。后续若要执行全服务“源码当前态”
重打包与滚动，必须先收敛历史多构造器 Spring 组件，避免移除 `@Autowired` 后在启动期复现
`No default constructor found`。
