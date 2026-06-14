# 038 parallel subagent next route slices

状态：已完成

更新时间：2026-06-06

## 背景

用户要求加速推进 V1 到 V3 微服务拆分，并把任务拆分给 subagent，每个 subagent 使用
`gpt-5.5` + `xhigh`。`025-11` 已完成 identity-service member admin API，本报告启动时
路由覆盖口径为：

```text
V1 routes: 203
V3 routes: 117
missing:   97
```

本报告汇总三个只读 subagent 的边界分析结果，并固定下一批并行 worker 切片，避免多个
agent 写同一模块导致冲突。

## 只读 subagent 结论

### content/admin

- `admin-bff` 已代理 `/api/v1/playlists`、`/api/v1/actors`、`/api/v1/directors`、
  `/api/v1/source-domains`、`/api/v1/resolvers` 到 `ircs-content-service`。
- `ircs-content-service` 当前真实覆盖 raw/unified 21 条接口，people、playlist、
  source-domain、resolver 仍缺真实 Controller。
- 推荐优先拆 people dictionary，actors/directors 共 14 条，写入范围可限制在
  `services/ircs-content-service/.../content/people/**`。

### catalog

- `ircs-catalog-service` 当前以 summary/read-only 为主，`/api/v1/catalog/**` 可用。
- `data-sources`、`raw-*`、`standard-*` 的 V1 admin 分页、详情和过滤契约仍不等价或未覆盖。
- 推荐先做 read contract，不碰高风险 create/update/delete/fetch-sample。

### ops/config

- ops read API 已完成，destructive DLQ、traffic reset 和 maintenance init 应延后。
- `GET /api/v1/common/timezones` 已由 `admin-bff` 代理到 config-service，但
  `ircs-config-service` 尚未实现。
- 推荐先做 common timezones read API，无 DB 写入、无外部调用，适合作为轻量并行 worker。

## 下一批并行 worker

```text
025-12 content-service people dictionary admin API
025-13 catalog-service admin read contract
025-14 config-service common timezones read API
```

并行规则：

- 三个 worker 均使用 `gpt-5.5` + `xhigh`。
- 每个 worker 必须先读 `AGENTS.md` 和对应 task spec。
- 每个 worker 只能写自己负责的服务模块、对应测试和必要的局部报告回写。
- 不得修改其他 worker 的文件，不得回滚主 agent 已完成的 `025-11`。
- 不新增 Pod、Service、HTTPRoute 或 PVC。
- worker 完成后主 agent 负责审查、整合、全仓 build、ARM64 image、rollout 和 live smoke。

## 完成结果

三个并行切片均已由主 agent 审查、整合、构建、rollout 和 live smoke：

```text
025-12 content-service people dictionary admin API: 14/14
025-13 catalog-service admin read contract:         21/21
025-14 config-service common timezones read API:    1/1
```

主 agent 整合时修正了 `ircs-catalog-service` trace SQL 与 dev schema 的不一致：
`raw_video_raw_languages` 应 join `raw_videos`，并补充 repository SQL guard 测试。

本轮完成后当前路由覆盖口径为：

```text
V1 routes: 203
V3 routes: 153
missing:   61
```

验证结果：

- `.\gradlew.bat :services:ircs-content-service:test` 通过。
- `.\gradlew.bat :services:ircs-catalog-service:test` 通过。
- `.\gradlew.bat :services:ircs-config-service:test` 通过。
- `.\gradlew.bat build` 通过。
- 三个服务 ARM64 镜像已推送并在 `ircs-dev` rollout 成功。
- `scripts/smoke/admin-bff-02512-02514-smoke.ps1` 通过 `admin-bff` 完成 `14/14`、`21/21`、`1/1` live smoke。
- 临时 actors/directors/data_sources/raw_videos/raw_languages 数据均已清理为 0。
- `ircs-dev` 仍无 HTTPRoute，ResourceQuota 仍为 `pods=28/30`、`services=24/26`。

## 高风险延后

```text
DELETE /api/v1/data-sources/{id}
POST   /api/v1/data-sources/fetch-sample
POST   /api/v1/ops/traffic/reset
POST   /api/v1/ops/dlq/{id}/retry
POST   /api/v1/ops/dlq/batch-retry
DELETE /api/v1/ops/dlq/{id}
POST   /api/v1/ops/dlq/batch-discard
POST   /api/v1/ops/maintenance/*/init
```

这些接口后续必须单独拆 task spec，并用隔离数据、feature flag、审计字段和二次确认策略控制。
