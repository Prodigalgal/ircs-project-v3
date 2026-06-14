# 057 metadata provider live queue smoke 迁移记录

状态：已完成 live queue success smoke

关联任务：

- `tasks/in-progress/028-06-real-provider-live-parity.md`

关联报告：

- `docs/reports/057-provider-live-queue-02806-tmdb-smoke.md`

## 迁移目标

补齐 `028-06` TMDB provider 的 live queue 诊断脚本，使真实 provider task 能在 dev 环境中可复现地
区分 credential、upstream、provider enable gate、queue ack/DLQ、raw metadata 和 search sync 结果。

## 行为变化

- 新增 smoke 脚本，不改变业务运行时代码。
- 默认不修改 `ircs-dev` metadata-worker env；provider disabled 时只输出 diagnostic skip。
- 传入 `-EnableTmdbProviderForSmoke` 时，脚本会临时设置 `APP_METADATA_TMDB_ENABLED=true` 并等待 rollout，
  结束后恢复原值。
- `-SkipPreflightTmdbDiagnostic` 用于跳过本地 TMDB preflight，直接验证集群内 provider worker 的
  真实 TMDB 调用、metadata result 和 search sync 副作用。

## 兼容性

- 不新增数据库迁移。
- 不新增 Kubernetes 资源和 `HTTPRoute`。
- 不修改 normalization、storage、shared、audit 或 ops 代码。
- 脚本默认不 purge 共享队列；只有显式传入清理开关时才清理本 smoke 相关队列。

## 回滚策略

删除 `scripts/smoke/metadata-provider-live-queue-02806-smoke.ps1` 以及本报告/任务回写即可；无数据库结构回滚。

## 验证边界

- 缺 credential 或 credential-service 不可用：预期 `SKIPPED`。
- provider disabled 且未显式允许临时 env patch：预期 `SKIPPED/TMDB_PROVIDER_DISABLED`。
- credential/upstream 可用且允许临时 env patch：预期 6 个 MARK 全部输出，且 DB/ES fixture 清理完成，
  `q.fetch.metadata.tmdb`、`q.enrich.metadata.result`、`q.search.sync.raw` 与对应 DLQ 均为空。

## 实施结果

- 新增 `scripts/smoke/metadata-provider-live-queue-02806-smoke.ps1`。
- 未修改 metadata worker 业务代码。
- 未修改 deployment YAML，未新增 Kubernetes 资源。
- 脚本通过运行时 `kubectl set env` 可选临时启用 TMDB provider，结束后恢复原 env。
- 脚本默认只在 credential/upstream diagnostic 成功后才投递 `q.fetch.metadata.tmdb`。

## 当前验证

- PowerShell parse：通过。
- 默认 diagnostic smoke：通过 SKIPPED 语义。
- 历史 dev 结果：`SKIPPED/TMDB_CREDENTIAL_WITHOUT_API_KEY`，credential-service HTTP 200，lease 数量 1，
  当时 lease payload 不含 `api_key`。
- V1 credential 导入后，`-SkipPreflightTmdbDiagnostic` live success 复跑通过：

```text
MARK 1/6 credential lease succeeded; TMDB upstream diagnostic skipped for direct queue verification
MARK 2/6 metadata/search consumers are ready and HTTPRoute remains absent
MARK 3/6 TMDB provider queue task is published and q.fetch.metadata.tmdb drains
MARK 4/6 provider result updates raw metadata to SUCCESS with tmdb_id
MARK 5/6 raw search sync refreshes ES document with TMDB enrichment fields
MARK 6/6 provider/result/search queues and DLQs are empty; smoke fixture is cleaned
```

- 后置检查：DB fixture residue=0；相关 queue/DLQ=0；`APP_METADATA_TMDB_ENABLED=false` 已恢复；
  `ircs-dev` 无 HTTPRoute；ResourceQuota 仍为 pods 28/30、services 24/26。
- `ircs-dev` HTTPRoute 只读检查：`No resources found in ircs-dev namespace`。

## 剩余迁移边界

- `028-06` 总任务后续迁移边界转向 Douban/RT 真实 provider task spec 与 magnet 真实 provider runner。
