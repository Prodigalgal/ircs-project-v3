# 080 028-06 magnet YTS_BZ runner contract 报告

状态：完成

日期：2026-06-08

## 背景

`028-06` 已完成 TMDB direct diagnostic 与 TMDB live queue success。剩余真实 provider
缺口中，Douban/RT 属 HTML scraping，dev 环境不应直接访问真实站点；magnet provider
在 V1 中已有 `YtsBzMagnetProviderClient` 的 IMDb-only JSON API 形态，适合作为下一刀
真实 provider runner contract。

V3 `ircs-magnet-service` 当前已有 `MagnetProviderSearchRunner` 抽象和
`FixtureMagnetProviderSearchRunner`，能够写入 `magnet_search_jobs`、
`magnet_provider_runs` 和 `magnet_links`，但 runner 仍是 dev-safe fixture，不具备
YTS_BZ provider type、真实/fixture gate、HTTP JSON parser 与失败分类。

## 目标

- 为 `ircs-magnet-service` 增加 YTS_BZ runner contract。
- 默认继续使用 fixture runner，不访问公网。
- 只有显式 gate 开启且 provider allowlist 命中 `YTS_BZ` 时，才允许真实 runner。
- 第一刀只支持 IMDb external id 查询。
- 用本地 fake HTTP server 或 mock client 单元测试验证 JSON parser、候选规范化和失败分类。

## 非目标

- 不访问真实 YTS_BZ、ThePirateBay 或其他公网 magnet provider。
- 不实现 Douban/RT metadata provider。
- 不新增 HTTPRoute，不修改 K8S manifest。
- 不修改 portal/admin/BFF route。
- 不修改公共索引或父任务，由主线程集成。

## 建议写入范围

```text
services/ircs-magnet-service/**
docs/reports/080-02806-magnet-yts-bz-runner-contract.md
docs/migrations/080-magnet-yts-bz-runner-contract.md
tasks/done/028-06-c-magnet-yts-bz-runner-contract.md
```

## 风险

- 真实 magnet provider 可能涉及公网内容源，必须默认关闭。
- provider JSON 字段可能漂移，parser 需要失败分类而不是伪成功。
- magnet URI、infoHash 和 source URL 不能污染已批准链接；测试必须使用 fixture unified video。

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

本轮不执行 K8S rollout、不运行公网 live smoke、不新增公共 HTTPRoute。

## 实施计划

- 在 `ircs-magnet-service` 内增加默认 no-network 的 runner gate。
- 增加 `YTS_BZ` + IMDb-only runner contract，并把非 IMDb 查询归类为不支持。
- 用本地 fake HTTP client / parser 单元测试覆盖 JSON 规范化、HTTP error、timeout 和 parse failure。
- 代码交付后回写本报告、迁移记录和 task spec 的验收结果。

## 实现结果

- 新增 `GatedMagnetProviderSearchRunner`，默认 `app.magnet.real-provider.enabled=false`，命中默认 fixture/no-network runner。
- 新增 `YtsBzMagnetProviderSearchRunner`，仅支持 `IMDB` query，使用 `/list_movies.json?query_term={imdb}&limit={limit}` contract。
- 新增 `MagnetUriUtils`，复用 V1 的 BTIH info hash 规范化语义，输出大写 40 位 hash 与标准 magnet URI。
- 新增 `MagnetProviderRunnerException`，把 `YTS_BZ_HTTP_ERROR`、`YTS_BZ_TIMEOUT`、`YTS_BZ_PARSE_FAILURE`、`YTS_BZ_UNSUPPORTED_QUERY` 作为 provider run 错误分类。
- `MagnetQueryService` 对 runner 分类异常只记录 failure type、request URL 和 HTTP status，不记录响应 body。

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

本轮测试只使用 fake HTTP client 和 parser fixture，未访问真实公网，未执行 K8S rollout，未 push image。
