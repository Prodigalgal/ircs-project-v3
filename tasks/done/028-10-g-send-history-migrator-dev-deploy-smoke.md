# 028-10-G send history migrator / dev deploy smoke task spec

状态：已完成

父任务：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `tasks/done/028-10-e-notification-send-history-schema-ops-query.md`
- `tasks/done/028-10-f-smtp-staging-live-preflight.md`
- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`

## 背景

`028-10-E` 已完成 `notification_mail_send_history` schema contract 和 ops-service 只读查询，
但该表尚未接入 dev 可执行的 `platform/ircs-migrator` Liquibase 路径。`028-10-F`
真实 preflight 证明 dev 环境仍缺表，ops endpoint 返回 `404 not found`，因此真实 SMTP smoke
被阻断。本切片只补 schema deploy path 与只读 dev smoke 证据，不发送 SMTP。

## 目标

- 将 `notification_mail_send_history` schema 纳入 `platform/ircs-migrator` master changelog，
  或证明已有等价 deploy path。
- 保持 notification-worker 的 schema contract 与 migrator changelog 字段、索引一致。
- 运行本地 migrator/schema tests。
- 在安全前提下执行 dev K8S migrator Job dry-run 或 server dry-run。
- 真实 dev smoke 只查询表存在性和 ops endpoint 可用性，不发送 SMTP。
- 确认 `APP_MAIL_ENABLED=false`，不打开真实 mail sending。

## 非目标

- 不发送真实 SMTP，不投递 mail queue。
- 不开启 `APP_MAIL_ENABLED` 或 `APP_NOTIFICATION_MAIL_ENABLED`。
- 不修改 Douban/Rabbit/R2/normalization。
- 不修改 `docs/index.md`、`tasks/index.md`、`docs/reports/089`、`docs/migrations/089`。
- 不处理 retention 删除策略。
- 不宣称 `SENT` 代表 inbox 投递成功。

## 输入

```text
tasks/done/028-10-e-notification-send-history-schema-ops-query.md
tasks/done/028-10-f-smtp-staging-live-preflight.md
tasks/in-progress/028-10-production-governor-credential-pool-parity.md
services/ircs-notification-worker/src/main/resources/db/schema/notification_mail_send_history.sql
platform/ircs-migrator/src/main/resources/db/changelog/**
deploy/k8s/dev/migrator-job-dev.yaml
scripts/smoke/notification-mail-smtp-02810f-preflight.ps1
```

## 输出

```text
platform/ircs-migrator Liquibase changelog include
migrator processResources / schema tests / dev dry-run 证据
只读 dev table / ops endpoint smoke 证据
本 task spec 的实现结果、验证结果和剩余风险回写
```

## 影响范围

包含：

```text
platform/ircs-migrator/src/main/resources/db/changelog/**
deploy/k8s/dev/*migrator*
services/ircs-notification-worker/src/main/resources/db/schema/**
scripts/smoke/*mail*
tasks/in-progress/028-10-g-send-history-migrator-dev-deploy-smoke.md
tasks/done/028-10-g-send-history-migrator-dev-deploy-smoke.md
```

不包含：

```text
Douban
Rabbit
R2
normalization
docs/index.md
tasks/index.md
docs/reports/089
docs/migrations/089
真实 SMTP
```

## 依赖

- 本地 Gradle/JDK 能运行 migrator resources 和 notification-worker/ops-service tests。
- `kubectl` 能访问 `ircs-dev`，且 dry-run 不会实际创建 Job。
- dev PostgreSQL 可通过既有 K8S 路径只读查询。
- ops endpoint smoke 若需要 port-forward，必须在 finally/清理步骤停止。

## 风险

- 仅本地加入 changelog 不等于 dev live DB 已迁移；需要 dry-run 或真实 Job 证据区分。
- ops endpoint `404` 可能来自 deployment 尚未包含 `028-10-E` 代码，而不只是缺表。
- `notification_mail_send_history` 查询只证明审计表/endpoint 存在，不证明真实 SMTP 投递。
- 若 dev migrator image 未重建推送，K8S Job 仍可能使用旧镜像。

## 验收标准

```text
MARK 1/5 028-10-G task spec created before code changes
MARK 2/5 notification_mail_send_history is included in platform/ircs-migrator deploy path
MARK 3/5 local migrator/schema tests pass without SMTP
MARK 4/5 dev K8S migrator dry-run or equivalent deploy evidence collected
MARK 5/5 dev smoke checks table/ops endpoint only and confirms no SMTP sending
```

## 测试边界

必跑：

```powershell
.\gradlew.bat --no-daemon :platform:ircs-migrator:processResources :services:ircs-notification-worker:test :services:ircs-ops-service:test --rerun-tasks
```

安全时运行：

```powershell
kubectl -n ircs-dev apply --dry-run=server -f .\deploy\k8s\dev\migrator-job-dev.yaml
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-smtp-02810f-preflight.ps1 -StartOpsPortForward
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-send-history-02810g-smoke.ps1 -StartOpsPortForward
```

不跑：

```text
真实 SMTP
AllowSend one-shot smoke
Rabbit mail queue 投递
K8S rollout
Douban/Rabbit/R2/normalization 测试
```

## 关联文档

- `tasks/done/028-10-e-notification-send-history-schema-ops-query.md`
- `tasks/done/028-10-f-smtp-staging-live-preflight.md`
- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`

## 关联任务

- 父任务：`tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- 本任务：`tasks/done/028-10-g-send-history-migrator-dev-deploy-smoke.md`

## 当前状态

已完成。本切片已将 `notification_mail_send_history` 纳入 `platform/ircs-migrator`
Liquibase master changelog，并完成本地 migrator/schema tests、K8S server dry-run 和只读
dev smoke。dev live 仍未落库，ops live endpoint 仍返回 404，因此本切片不执行 SMTP。

## 实现结果

- 新增 Liquibase SQL：
  - `platform/ircs-migrator/src/main/resources/db/changelog/2026/07/add-notification-mail-send-history.sql`
  - 字段和索引与 `services/ircs-notification-worker/src/main/resources/db/schema/notification_mail_send_history.sql`
    保持一致。
- 更新 master changelog：
  - `platform/ircs-migrator/src/main/resources/db/changelog/db.changelog-master.yaml`
  - 已 include `db/changelog/2026/07/add-notification-mail-send-history.sql`。
- 新增 migrator contract test：
  - `platform/ircs-migrator/src/test/java/com/prodigalgal/ircs/migrator/NotificationMailSendHistoryChangelogTest.java`
  - 验证 master include 存在，并验证 migrator SQL 去除 Liquibase header 后等于 notification-worker schema contract。
- 新增只读 dev smoke：
  - `scripts/smoke/notification-mail-send-history-02810g-smoke.ps1`
  - 只检查 `APP_MAIL_ENABLED`、dev DB 表存在性、ops summary/page endpoint。
  - 不发送 SMTP，不打印 password，不打印 recipient。

## 验收结果

```text
MARK 1/5 PASS 028-10-G task spec created before code changes
MARK 2/5 PASS notification_mail_send_history is included in platform/ircs-migrator deploy path
MARK 3/5 PASS local migrator/schema tests pass without SMTP
MARK 4/5 PASS dev K8S migrator server dry-run collected
MARK 5/5 PARTIAL dev smoke checked table/ops endpoint only and confirmed no SMTP sending; dev live table/ops endpoint still unavailable
```

## 测试结果

本地通过：

```powershell
.\gradlew.bat --no-daemon :platform:ircs-migrator:test :platform:ircs-migrator:processResources :services:ircs-notification-worker:test :services:ircs-ops-service:test --rerun-tasks
```

结果：

```text
BUILD SUCCESSFUL in 31s
platform:ircs-migrator NotificationMailSendHistoryChangelogTest: tests=2, failures=0, errors=0, skipped=0
services:ircs-notification-worker: tests=38, failures=0, errors=0, skipped=0
services:ircs-ops-service: tests=67, failures=0, errors=0, skipped=0
```

K8S server dry-run 通过：

```powershell
kubectl -n ircs-dev apply --dry-run=server -f .\deploy\k8s\dev\migrator-job-dev.yaml
```

结果：

```text
job.batch/ircs-migrator created (server dry run)
```

只读 dev smoke：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-send-history-02810g-smoke.ps1 -StartOpsPortForward
```

结果摘要：

```text
status=UNAVAILABLE
reason=SEND_HISTORY_DEV_SMOKE_INCOMPLETE
APP_MAIL_ENABLED=false
sent=false
notification_mail_send_history.present=false
opsSendHistory.available=false
opsSendHistory.error=远程服务器返回错误: (404) 未找到。
passwordPrinted=false
recipientPrinted=false
```

补充只读 SQL 证据：

```powershell
kubectl -n ircs-dev exec postgres-0 -- psql -U postgres -d ircs -tAc "select to_regclass('public.notification_mail_send_history');"
```

结果为空，表示 dev live DB 当前仍未落 `notification_mail_send_history`。

## 未执行项

```text
SKIPPED real SMTP: 本切片禁止发送，未传 AllowSend，未投递 Rabbit mail queue
SKIPPED APP_MAIL_ENABLED patch: dev configMap 当前 APP_MAIL_ENABLED=false，无需修改或恢复
SKIPPED K8S Job apply: 本切片只执行 server dry-run，未创建真实 migrator Job
SKIPPED ops-service rollout: dev ops-service 镜像未由本切片 rollout，因此 live endpoint 仍可能是旧代码
SKIPPED retention: send history retention 后续独立切片处理
```

## 剩余 SMTP staging 风险

- dev/staging 仍需构建并推送包含本 changelog 的 `ircs-migrator:dev` 镜像，然后执行真实 migrator Job，
  才能把 `notification_mail_send_history` 落到 dev DB。
- dev ops-service 仍需 rollout 到包含 `028-10-E` send history endpoint 的镜像；当前 live endpoint
  仍返回 404。
- notification-worker deployment 当前 `APP_MAIL_SEND_HISTORY_ENABLED=false(default)`；即使表落库，
  后续真实 smoke 前也必须显式打开 send history，并保持 `APP_MAIL_ENABLED=false` 作为常驻默认。
- SMTP provider 当前仍不是已证明的 staging-only provider；one-shot smoke 仍需安全测试收件人、
  低频一封、credential lease、send history 可查询和人工门禁全部满足后再执行。
