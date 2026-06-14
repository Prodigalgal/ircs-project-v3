# 057 provider live queue 02806 TMDB smoke 报告

状态：已完成 live queue success smoke

任务：

- `tasks/in-progress/028-06-real-provider-live-parity.md`

## 背景

`028-06` 第一刀已经完成 metadata provider enable gate 和 TMDB credential/upstream diagnostic，
但仍缺真实 RabbitMQ 投递后的 ack/DLQ、provider result、raw metadata 和 search sync 联动证据。
当前 `ircs-dev` 为低资源环境，metadata-worker 已部署为 ClusterIP worker，`HTTPRoute` 应保持无资源。

## 本刀目标

- 增加 TMDB provider live queue smoke，投递最小 `q.fetch.metadata.tmdb` task。
- 在投递前先区分 credential-service 不可用、无 enabled TMDB credential、credential 被 TMDB 拒绝、
  TMDB upstream 网络/HTTP 失败。
- 只有 credential 与 upstream diagnostic 成功后才进入 queue live smoke。
- 验证 `q.fetch.metadata.tmdb`、`q.enrich.metadata.result`、`q.search.sync.raw` 及对应 DLQ 在 smoke
  窗口内可消费且最终为空。
- 验证 raw metadata 侧至少落出 `tmdb_id`、`enrichment_status=SUCCESS`，并通过 ES raw document
  观察 search sync 副作用。

## 非目标

- 不新增或修改任何 `HTTPRoute`。
- 不修改 normalization、storage、audit 或 ops service 代码。
- 不实现 Douban/RT 真实 provider。
- 不替换 magnet provider runner。
- 不伪造 TMDB 成功；credential 或 upstream 不满足时只输出 `SKIPPED`/diagnostic。

## 实施方案

- 新增 `scripts/smoke/metadata-provider-live-queue-02806-smoke.ps1`。
- 脚本使用 `kubectl` 访问 `ircs-dev` 的 PostgreSQL、RabbitMQ management、credential-service 和
  Elasticsearch，不依赖公网 admin/BFF HTTP surface。
- 若 `APP_METADATA_TMDB_ENABLED=false`，脚本默认 `SKIPPED/TMDB_PROVIDER_DISABLED`；只有显式传入
  `-EnableTmdbProviderForSmoke` 时才临时设置 metadata-worker env，并在 `finally` 中恢复原值。
- `-SkipPreflightTmdbDiagnostic` 可跳过本地 TMDB preflight，只验证集群内 provider worker 的真实
  TMDB 调用、result 聚合和 search sync 副作用。
- 脚本投递成功样本 `The Matrix`/`1999`，使用临时 `raw_videos` 行承载 provider result，结束后清理 DB
  与 ES fixture。

## 风险

- 临时启用 TMDB provider 会触发 metadata-worker rollout，必须显式开关并恢复 env。
- 共享 RabbitMQ 队列如果已有消息，脚本默认跳过，避免清理其他 agent 的 live 消息。
- TMDB 外部网络、credential 限速和 search-service/ES 状态都可能导致 diagnostic failure；这些不能被记为代码成功。

## 验收标准

- credential-service 不可用、无 credential、credential 缺 `api_key` 时输出 `SKIPPED`。
- TMDB upstream 401/429/5xx/network failure 输出真实 failure 分类。
- provider disabled 时默认输出 `SKIPPED/TMDB_PROVIDER_DISABLED`。
- 显式允许临时启用且 credential/upstream 正常时，输出以下 MARK：
  - `MARK 1/6 credential lease and TMDB upstream diagnostic succeeded`
  - `MARK 2/6 metadata/search consumers are ready and HTTPRoute remains absent`
  - `MARK 3/6 TMDB provider queue task is published and q.fetch.metadata.tmdb drains`
  - `MARK 4/6 provider result updates raw metadata to SUCCESS with tmdb_id`
  - `MARK 5/6 raw search sync refreshes ES document with TMDB enrichment fields`
  - `MARK 6/6 provider/result/search queues and DLQs are empty; smoke fixture is cleaned`

## 验证计划

```powershell
powershell -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-live-queue-02806-smoke.ps1
powershell -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-live-queue-02806-smoke.ps1 -EnableTmdbProviderForSmoke
```

默认命令用于安全 diagnostic；第二条只在确认 dev credential 可用且允许 metadata-worker 临时 rollout 时执行。

## 实现结果

- 已新增 `scripts/smoke/metadata-provider-live-queue-02806-smoke.ps1`。
- 脚本默认启动 RabbitMQ management、credential-service 和 Elasticsearch port-forward，不新增 `HTTPRoute`。
- 脚本先做 credential lease 与 TMDB search/detail upstream diagnostic。
- credential/upstream 未满足时输出 `SKIPPED` 或真实失败分类，不投递 queue。
- credential/upstream 满足后，脚本检查 metadata/search consumers、queue/DLQ 初始状态和 `HTTPRoute`。
- 若 `APP_METADATA_TMDB_ENABLED=false` 且未传 `-EnableTmdbProviderForSmoke`，脚本输出
  `SKIPPED/TMDB_PROVIDER_DISABLED`。
- Elasticsearch 本地 port-forward 默认使用 `39201`，避开 Windows 上 `19201` 被系统保留导致的
  bind forbidden。
- 若显式传入 `-EnableTmdbProviderForSmoke`，脚本临时设置 metadata-worker env，等待 rollout，并在
  `finally` 中恢复。
- 成功路径会插入临时 `raw_videos` fixture，向 `x.process`/`metadata.fetch.tmdb` 投递 JSON provider
  task，随后验证 raw metadata、ES raw document、queue/DLQ 和清理结果。

## 验证结果

- 通过 PowerShell 解析检查：

```powershell
$tokens=$null; $errors=$null; [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path 'scripts\smoke\metadata-provider-live-queue-02806-smoke.ps1'), [ref]$tokens, [ref]$errors)
```

- 通过默认 diagnostic：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-live-queue-02806-smoke.ps1
```

当前输出：

```json
{
  "task": "028-06",
  "slice": "metadata-provider-live-queue",
  "provider": "TMDB",
  "status": "SKIPPED",
  "reason": "TMDB_CREDENTIAL_WITHOUT_API_KEY",
  "namespace": "ircs-dev",
  "credentialBaseUrl": "http://127.0.0.1:18089",
  "tmdbBaseUrl": "https://api.themoviedb.org/3",
  "details": {
    "leaseHttpStatus": 200,
    "leaseCount": 1
  }
}
```

该结果说明 credential-service 可达，但当前 dev TMDB lease 不含 `api_key`；脚本未投递 provider queue，
未伪造 queue success。V1 credential 导入后已完成真实 `MARK 1/6` 到 `MARK 6/6` 复跑。

- 通过只读环境检查：`kubectl -n ircs-dev get httproute --no-headers` 输出
  `No resources found in ircs-dev namespace`。

## Live Success 复验

V1 TMDB credential 导入 `ircs-dev.sys_credentials` 后，执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-live-queue-02806-smoke.ps1 -SkipPreflightTmdbDiagnostic -WaitAttempts 45 -WaitSeconds 2 -PurgeSmokeQueuesOnCleanup
```

结果：

```text
MARK 1/6 credential lease succeeded; TMDB upstream diagnostic skipped for direct queue verification
MARK 2/6 metadata/search consumers are ready and HTTPRoute remains absent
MARK 3/6 TMDB provider queue task is published and q.fetch.metadata.tmdb drains
MARK 4/6 provider result updates raw metadata to SUCCESS with tmdb_id
MARK 5/6 raw search sync refreshes ES document with TMDB enrichment fields
MARK 6/6 provider/result/search queues and DLQs are empty; smoke fixture is cleaned
```

补充证据：

```text
raw metadata reached SUCCESS|603|tt0133093|1999|8.2
DB fixture residue: 0
q.fetch.metadata.tmdb/q.enrich.metadata.result/q.search.sync.raw and DLQ: 0 messages
APP_METADATA_TMDB_ENABLED restored to false
HTTPRoute: absent
ResourceQuota unchanged: pods 28/30, services 24/26
```

## 剩余项

- `028-06` 总任务仍保留 Douban/RT 真实 provider task spec 与 magnet 真实 provider runner。
