# 059 028-10 production governor / credential pool 第一刀报告

状态：已完成第一刀，`028-10` 总任务仍进行中

日期：2026-06-07

关联任务：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`

上游来源：

- `docs/reports/044-ops-maintenance-runner-parity-analysis.md`
- `docs/reports/054-mail-notification-disabled-failure-parity-analysis.md`

## 背景

`044` 已完成 ops maintenance `search-reindex-unified` dev-safe runner。该 runner 只按
`OPS_MAINTENANCE_REINDEX_DEV_LIMIT` 小批量投递 search sync 消息，不 hard reset ES，也不触发
raw reindex、trend sync、sanitize 或 aggregation reset 等高风险副作用。

`054` 已完成 mail disabled/failure 语义：disabled 时 ack/no-op，模板和 SMTP 失败时抛出异常交给
RabbitMQ retry/DLQ。但 V3 尚未接入 production SMTP credential pool、发送历史和真实 SMTP smoke。

本轮目标不是把生产任务直接打开，而是先固定 production governor 和 credential pool 的边界，并做一个
不会外呼、不会投递真实 SMTP、不会新增 HTTPRoute 的本地测试切片。

## 当前实现观察

- `MaintenanceRunnerService` 只支持 `search-reindex-unified`。
- `MaintenanceSessionService` 对 unsupported task 继续输出 dev guarded SSE，不执行真实 runner。
- `TrafficMonitorService` 只读/重置 `traffic:limit:*` Redis key，可作为后续 governor 观测面，但本轮不修改。
- `SmtpNotificationMailService` 仍使用 Spring `JavaMailSender` 配置，暂未从 credential-service 租约中选择 SMTP 账号。
- `CredentialProviderCatalog` 已支持 `MAIL` provider，必填 `username` 和 `password`。
- `CredentialService.leaseProviderCredentials()` 已支持 internal lease，但 service 层仍需固化 limit guard 和 MAIL pool 选择规则。

## Production Governor 边界

后续 production governor 至少需要四层门禁：

```text
environment gate
  -> 仅 dev-safe runner 可在 dev 执行；destructive runner 只能 staging/production-like

runner registry gate
  -> 每个 runner 显式登记 riskLevel、defaultLimit、maxLimit、supportsDryRun、cleanupPlan

traffic governor gate
  -> 读取全局/credential traffic slot，超过阈值时拒绝或延后 runner

session/audit gate
  -> session 可观察、可中止、可恢复；开始/完成/失败写入审计或运行历史
```

本轮不新增 runner registry，不改变现有 SSE contract，只把后续拆分边界写入迁移记录。

## SMTP Credential Pool 边界

SMTP pool 后续应分成四步：

```text
credential-service
  -> 只租 enabled MAIL credentials，secret 不写日志，limit 有 service-level cap

notification-worker adapter
  -> APP_NOTIFICATION_MAIL_ENABLED=false 时仍然 no-op；true 时才从 credential-service 获取 MAIL lease

send history
  -> 记录 requestId/messageId/to/template/status/providerCredentialId/failureCode，不记录 password

staging SMTP smoke
  -> 使用隔离账号、低频限额、人工确认；失败进入 retry/DLQ 分类，不把 skip 伪装成 success
```

本轮只做 credential-service service-level guard 和单元测试。

## 第一刀范围

包含：

- 移动并更新 `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`。
- 新增本报告和迁移记录。
- 在 `services/ircs-credential-service` 内固化 credential lease limit guard。
- 增加 MAIL credential pool 的本地单元测试。

不包含：

- 不修改 `services/ircs-ops-service` runner 实现。
- 不修改 `services/ircs-notification-worker` SMTP 发送路径。
- 不新增 HTTPRoute、controller route 或 BFF route。
- 不执行真实 SMTP、不投递 mail queue、不做 rollout。
- 不修改 `docs/index.md`、`tasks/index.md`、父任务或其他 028 子任务。

## 验收标准

```text
MARK 1/3 028-10 task moved to in-progress with task spec
MARK 2/3 production governor and SMTP credential pool boundary docs recorded
MARK 3/3 credential-service MAIL pool guard unit tests pass without SMTP
```

## 测试边界

必跑本地测试：

```powershell
.\gradlew.bat :services:ircs-credential-service:test --rerun-tasks
```

明确不执行：

```text
真实 SMTP
K8S rollout
HTTPRoute 变更
maintenance destructive runner
公网 provider smoke
```

## 当前状态

2026-06-07：第一刀已完成。production governor / SMTP credential pool 边界已记录，
credential-service 增加 service-level lease guard 和 MAIL pool 选择单元测试；未执行真实 SMTP、
未 rollout、未新增 HTTPRoute。

## 实现结果

- `CredentialService.leaseProviderCredentials()` 增加 service-level limit guard：
  - `limit > 100` 时 cap 到 `100`。
  - `limit <= 0` 时直接返回空列表，不查询 repository。
- 新增 `CredentialService.leaseMailCredentials(int limit)`：
  - provider 固定为 `MAIL`。
  - 只返回同时包含非空 `username` 和 `password` 的 lease。
  - 复用同一 limit guard。
- `CredentialServiceTest` 增加三条本地测试：
  - `capsProviderCredentialLeaseLimitAtServiceBoundary()`
  - `zeroProviderCredentialLeaseLimitSkipsRepository()`
  - `leasesMailCredentialsOnlyWhenUsernameAndPasswordExist()`

## 验证结果

通过：

```powershell
.\gradlew.bat :services:ircs-credential-service:test --rerun-tasks
BUILD SUCCESSFUL in 8s
```

测试证据：

```text
CredentialServiceTest tests=10, skipped=0, failures=0, errors=0
```

MARK：

```text
MARK 1/3 028-10 task moved to in-progress with task spec
MARK 2/3 production governor and SMTP credential pool boundary docs recorded
MARK 3/3 credential-service MAIL pool guard unit tests pass without SMTP
```

SKIPPED：

```text
SKIPPED real SMTP: 本轮禁止真实 SMTP，且第一刀只做 credential-service 单元测试
SKIPPED K8S rollout: 本轮不 rollout
SKIPPED HTTPRoute: 本轮不新增、不修改 HTTPRoute
SKIPPED destructive maintenance runner: 本轮不执行 raw reindex/hard reset/trend sync
```

## 剩余项

- production governor runner registry 与环境门禁实现。
- raw reindex hard reset、unified hard reset、trend sync 等高风险 runner 独立 task spec。
- notification-worker SMTP credential adapter。
- send history 表结构、写入语义和 retry/DLQ 对齐。
- staging-only SMTP smoke 和清理证据。
