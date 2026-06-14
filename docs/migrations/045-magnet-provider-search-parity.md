# 045 magnet provider search parity

状态：已完成

日期：2026-06-07

## 范围

本记录承接 `028-03`，把 `POST /api/v1/magnets/search/unified/{id}` 从
`DEV_MVP_EXTERNAL_SEARCH_DISABLED` 的 `SKIPPED` job 推进到 dev-safe provider runner。
本切片恢复 V1 中 job、provider run、magnet link 的核心数据副作用，但默认不访问真实
公网 provider。

## 实现

- magnet-service 新增 `MagnetProviderSearchRunner` 抽象。
- 默认实现 `FixtureMagnetProviderSearchRunner` 只生成 dev-safe fixture 候选。
- `MagnetQueryService.triggerUnifiedSearch` 会读取 IMDb ID、enabled provider 和支持的
  external ID，然后创建 running/success job。
- `JdbcMagnetRepository` 补齐：
  - `magnet_search_jobs`
  - `magnet_provider_runs`
  - `magnet_links`
- 保留 unified video 不存在时的 `404`。
- 无 IMDb ID、无 enabled provider 或 provider 不支持 IMDb 时仍安全创建 `SKIPPED` job。

## 镜像与部署

```text
ircs-magnet-service@sha256:5fb8ae6bb463ab202c971d2ca8cffc1fb21491bf7d78de0510b2715ada1984e6

deployment/ircs-magnet-service Ready 1/1
deployment/ircs-admin-bff      Ready 1/1
```

未新增 Pod、Service、HTTPRoute、PVC 或中间件。

## 验证

```text
.\gradlew.bat :services:ircs-magnet-service:test --rerun-tasks
.\scripts\smoke\behavior-02802-02804-parity-smoke.ps1
```

Smoke 标记：

```text
MARK 3/8 POST /api/v1/magnets/search/unified/{id} creates non-skipped search job
MARK 4/8 provider fixture run writes magnet_provider_runs
MARK 5/8 provider fixture candidate upserts magnet_links
```

后置检查：

```text
kubectl -n ircs-dev get httproute -> No resources found
ResourceQuota: pods=28/30, services=24/26
```

## 剩余边界

- fixture runner 只证明 dev-safe 副作用，不代表真实 provider 搜索质量。
- 真实 provider 限速、重试、内容安全审核和 portal detail cache eviction 后续单独处理。
