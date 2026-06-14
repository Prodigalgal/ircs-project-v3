# 080 magnet YTS_BZ runner contract 迁移记录

状态：完成

日期：2026-06-08

## 迁移内容

为 `028-06` 增加 magnet provider 的第一条真实 runner contract：YTS_BZ + IMDb 查询。
该迁移只要求本地可测试的 runner gate、parser 和失败分类，不要求公网 live 成功。

## 范围

```text
services/ircs-magnet-service/**
docs/reports/080-02806-magnet-yts-bz-runner-contract.md
tasks/done/028-06-c-magnet-yts-bz-runner-contract.md
```

## 门禁

```text
APP_MAGNET_REAL_PROVIDER_ENABLED=false
APP_MAGNET_REAL_PROVIDER_ALLOWLIST=YTS_BZ
```

默认必须保持 no-network fixture 行为。真实 runner 只有在显式开启且 provider code/type
命中 allowlist 后才执行。

## 期望结果

```text
MARK 1/5 magnet real-provider gate defaults to fixture/no-network
MARK 2/5 YTS_BZ runner supports IMDb query only
MARK 3/5 YTS_BZ JSON response maps to normalized magnet candidates
MARK 4/5 HTTP error/timeout/parse failure are classified without leaking response body
MARK 5/5 magnet-service tests pass without public network access
```

## 回滚边界

如实现出现风险，可只回退新增 runner/gate/parser 代码，保留既有
`FixtureMagnetProviderSearchRunner` 和现有 magnet API 行为。

## 实施记录

- 2026-06-08：开始实现本地 runner contract，限定在 `ircs-magnet-service`、本迁移记录、
  080 报告和 028-06-C task spec 范围内。
- 本迁移不修改 K8S、不 rollout、不 push image。
- 2026-06-08：完成默认 no-network runner gate、YTS_BZ IMDb-only runner、JSON parser、
  候选规范化和失败分类。

## 验证记录

```text
MARK 1/5 magnet real-provider gate defaults to fixture/no-network：通过
MARK 2/5 YTS_BZ runner supports IMDb query only：通过
MARK 3/5 YTS_BZ JSON response maps to normalized magnet candidates：通过
MARK 4/5 HTTP error/timeout/parse failure are classified without leaking response body：通过
MARK 5/5 magnet-service tests pass without public network access：通过
```

测试命令：

```powershell
.\gradlew.bat :services:ircs-magnet-service:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL in 20s`。验证期间未访问真实公网。
