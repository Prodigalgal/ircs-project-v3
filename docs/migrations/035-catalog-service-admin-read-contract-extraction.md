# 035 catalog-service admin read contract extraction

状态：已完成

日期：2026-06-06

## 范围

本轮将 V1 admin catalog/dictionary 只读契约迁移到 `ircs-catalog-service`，覆盖
data-sources、standard dictionaries、raw dictionaries 和 raw language trace。
高风险 create/update/delete/fetch-sample 仍按后续独立任务处理。

## 实现

- 新增 `CatalogPage`、分页请求、read DTO 和过滤 query DTO。
- 补齐 `/api/v1/data-sources` Page/detail。
- 补齐 `standard-categories`、`standard-genres`、`standard-areas`、`standard-languages` 读契约。
- 补齐 `raw-categories`、`raw-genres`、`raw-areas`、`raw-languages` Page/detail。
- 补齐 `raw-languages/{id}/trace`。
- 保留 `/api/v1/catalog/**` summary API。

## Schema 修复

主 agent live schema 复核发现 dev PostgreSQL 已将旧 `videos` 表重命名为
`raw_videos`。本轮将 `traceRawLanguage` SQL 修正为：

```text
video_raw_languages -> raw_videos
```

并新增 repository 单元测试防止回退。

## 验证

```text
.\gradlew.bat :services:ircs-catalog-service:test -> PASS
.\gradlew.bat build -> PASS
image:  harbor.mnnu.eu.org/ircs/ircs-catalog-service@sha256:4d6a3536242ab5c6c1fbd68cf215358f607a3c2eafba0e4ffa228ce47364b625
rollout: ircs-catalog-service Ready 1/1
smoke:  admin-bff MARK 21/21
cleanup: data_sources/raw_videos/raw_languages residue 0/0/0
```

## 结果

`025-13` 完成，catalog admin read contract 共 `21/21` 条接口完成迁移和复现标记。
