# 028-06-C magnet YTS_BZ runner contract task spec

状态：完成

父任务：

- `tasks/in-progress/028-06-real-provider-live-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/080-02806-magnet-yts-bz-runner-contract.md`
- `docs/migrations/080-magnet-yts-bz-runner-contract.md`

## 背景

TMDB live queue 已完成，但 magnet provider 仍停留在 fixture runner。V1 已有
YTS_BZ IMDb JSON provider client，适合先迁移为 V3 dev-safe runner contract。

## 目标

- 增加 YTS_BZ runner contract。
- 增加真实/fixture gate，默认不访问公网。
- 只支持 IMDb query。
- 用本地测试验证 JSON parser、候选规范化和错误分类。

## 非目标

- 不访问真实公网 provider。
- 不实现 ThePirateBay、Douban 或 RT。
- 不修改 K8S manifest、HTTPRoute、BFF 或公共索引。
- 不执行 ARM64 image push 或 rollout。

## 输入

```text
services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet/MagnetProviderSearchRunner.java
services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet/FixtureMagnetProviderSearchRunner.java
services/ircs-magnet-service/src/main/java/com/prodigalgal/ircs/magnet/MagnetQueryService.java
V1 YtsBzMagnetProviderClient 行为
```

## 输出

```text
YTS_BZ runner/gate/parser implementation
magnet-service unit tests
docs/reports/080-02806-magnet-yts-bz-runner-contract.md
docs/migrations/080-magnet-yts-bz-runner-contract.md
```

## 影响范围

```text
services/ircs-magnet-service/**
docs/reports/080-02806-magnet-yts-bz-runner-contract.md
docs/migrations/080-magnet-yts-bz-runner-contract.md
tasks/done/028-06-c-magnet-yts-bz-runner-contract.md
```

## 依赖

- 本地 Gradle/JDK 可运行 magnet-service tests。
- 测试必须使用 mock/fake HTTP，不访问公网。

## 风险

- provider 响应漂移导致 parser 误判。
- 真实 provider 被误开会产生公网访问。
- 候选去重/规范化不严会污染 `magnet_links`。

## 验收标准

```text
MARK 1/5 magnet real-provider gate defaults to fixture/no-network
MARK 2/5 YTS_BZ runner supports IMDb query only
MARK 3/5 YTS_BZ JSON response maps to normalized magnet candidates
MARK 4/5 HTTP error/timeout/parse failure are classified without leaking response body
MARK 5/5 magnet-service tests pass without public network access
```

## 测试边界

```powershell
.\gradlew.bat :services:ircs-magnet-service:test --rerun-tasks
```

## 子任务

### 子任务 A：runner gate

状态：完成

- 输出：默认 fixture/no-network gate，显式开启且 allowlist 命中后才进入真实 runner。
- 边界：只影响 `ircs-magnet-service` runner 选择，不修改 K8S 或公共索引。
- 验证：单元测试确认默认 gate 不触发 HTTP client。

### 子任务 B：YTS_BZ IMDb runner contract

状态：完成

- 输出：IMDb-only YTS_BZ request、JSON parser、候选规范化。
- 边界：不支持非 IMDb query，不访问真实公网。
- 验证：fake HTTP / parser test 覆盖成功响应。

### 子任务 C：失败分类与验收回写

状态：完成

- 输出：HTTP error、timeout、parse failure 分类且不泄漏响应 body；回写报告、迁移和任务状态。
- 边界：不 rollout、不 push image。
- 验证：必跑 Gradle 测试命令。

## 实现结果

- `GatedMagnetProviderSearchRunner`：默认 fixture/no-network；只有显式开启
  `APP_MAGNET_REAL_PROVIDER_ENABLED=true` 且 allowlist 命中 `YTS_BZ` 时才调用真实 runner。
- `YtsBzMagnetProviderSearchRunner`：只支持 IMDb query；构造 YTS_BZ JSON API 请求；
  解析 `data.movies[].torrents[]` 并规范化为 `MagnetProviderCandidate`。
- `MagnetProviderRunnerException`：把 HTTP error、timeout、parse failure 和 unsupported query
  固定为不含响应 body 的错误分类。
- `MagnetQueryService`：provider run 失败时记录分类、request URL 和 HTTP status，不泄漏响应 body。

## 验收结果

```text
MARK 1/5 magnet real-provider gate defaults to fixture/no-network：通过
MARK 2/5 YTS_BZ runner supports IMDb query only：通过
MARK 3/5 YTS_BZ JSON response maps to normalized magnet candidates：通过
MARK 4/5 HTTP error/timeout/parse failure are classified without leaking response body：通过
MARK 5/5 magnet-service tests pass without public network access：通过
```

## 测试结果

```powershell
.\gradlew.bat :services:ircs-magnet-service:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL in 20s`，10 个 actionable tasks 全部执行完成。

本任务未访问真实公网，未 rollout，未 push image。主线程已完成复验、索引回写和父任务集成，
并将本任务移动到 `tasks/done/`。
