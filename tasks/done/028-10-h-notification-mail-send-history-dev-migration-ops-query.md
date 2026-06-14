# 028-10-H notification mail send history dev migration / ops query task spec

状态：已完成

父任务：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/requirements/028-10-h-notification-mail-send-history-dev-migration-ops-query.md`
- `docs/reports/090-02810h-notification-mail-send-history-dev-migration-ops-query.md`
- `tasks/done/028-10-e-notification-send-history-schema-ops-query.md`
- `tasks/done/028-10-g-send-history-migrator-dev-deploy-smoke.md`

## 背景

`028-10-E` 已实现 `notification_mail_send_history` schema contract 与 ops-service
只读查询。`028-10-G` 已把 schema 纳入 platform migrator 并完成 dry-run，但 dev DB
仍没有表，live ops-service endpoint 仍 404。本任务将 dry-run 推进到 dev 真实 migration
和 ops-service live 查询闭环。

## 目标

- 构建并推送 ARM 可运行 dev `ircs-migrator` image。
- 运行一次 `ircs-dev` migrator Job 并记录 job/pod/log 证据。
- 查询 dev DB，证明 `notification_mail_send_history` 表、字段和索引存在。
- 如 live ops-service 缺 endpoint，则构建、推送并 rollout `ircs-ops-service:dev`。
- 通过 port-forward 或等价 ClusterIP 访问验证 page 与 summary endpoint 不再 404。
- 证明本轮未发送 SMTP，且 `APP_MAIL_ENABLED` 保持 false 或未触发发送路径。

## 非目标

- 不发送真实 SMTP 邮件。
- 不投递 Rabbit mail queue。
- 不修改 config-service Rabbit publisher。
- 不修改 content-service listener。
- 不修改 Douban provider、storage-service、portal/admin/BFF。
- 不新增公网入口、HTTPRoute 或 namespace 污染。
- 不实现 send history retention。

## 输入

```text
platform/ircs-migrator/src/main/resources/db/changelog/**
platform/ircs-migrator/build.gradle
services/ircs-ops-service/**
deploy/k8s/dev/migrator-job-dev.yaml
deploy/k8s/dev/ops-service-dev.yaml
scripts/smoke/notification-mail-send-history-02810g-smoke.ps1
scripts/smoke/notification-mail-smtp-02810f-preflight.ps1
```

## 输出

```text
dev migrator image digest
dev migrator Job 状态与日志证据
dev DB table/index 查询证据
ops-service live endpoint smoke 证据
SMTP 未发送证据
docs/reports/090 回写
本 task spec 回写并完成后移动到 tasks/done/
```

## 影响范围

包含：

```text
platform/ircs-migrator/**
services/ircs-ops-service/**
deploy/k8s/dev/*migrator*
deploy/k8s/dev/*ops-service*
scripts/smoke/*notification*
scripts/smoke/*mail*
docs/requirements/028-10-h-notification-mail-send-history-dev-migration-ops-query.md
docs/reports/090-02810h-notification-mail-send-history-dev-migration-ops-query.md
tasks/in-progress/028-10-h-notification-mail-send-history-dev-migration-ops-query.md
```

不包含：

```text
services/ircs-config-service/**
services/ircs-content-service/**
services/ircs-storage-service/**
portal/admin/BFF
Douban provider
真实 SMTP
```

## 依赖

- 本机可构建 Gradle/Jib image。
- registry `harbor.mnnu.eu.org/ircs` 可推送 dev tag。
- `kubectl` 可访问 `ircs-dev` namespace。
- dev PostgreSQL `postgres-0` 可执行只读 SQL。
- ops-service 可通过 ClusterIP/port-forward 做只读 smoke。

## 风险

- registry 权限、网络或 ARM manifest 失败会阻塞 image rollout。
- migrator Job 可能因旧 Job 同名、资源不足或 DB lock 失败。
- ops-service rollout 可能因并行 worker 更新同一 deployment 而产生 live image 漂移。
- endpoint 只读查询如果表为空仍应返回 200，不能误判为空数据为失败。
- SMTP 未发送证据必须来自 smoke 输出、APP_MAIL_ENABLED 状态和未触发发送动作，而不是仅靠口头说明。

## 验收标准

```text
MARK 1/6 028-10-H docs/task spec created before live changes
MARK 2/6 migrator dev image built and pushed with digest
MARK 3/6 migrator Job completed in ircs-dev and was cleaned or intentionally retained
MARK 4/6 notification_mail_send_history exists in dev DB with expected indexes
MARK 5/6 ops-service send history page and summary endpoints return non-404 live responses
MARK 6/6 SMTP was not sent and APP_MAIL_ENABLED stayed false or no sending path was triggered
```

## 测试边界

必跑：

```powershell
.\gradlew.bat --no-daemon :platform:ircs-migrator:test :services:ircs-ops-service:test --rerun-tasks
kubectl -n ircs-dev apply -f .\deploy\k8s\dev\migrator-job-dev.yaml
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-send-history-02810g-smoke.ps1 -StartOpsPortForward -FailOnUnavailable
```

按需运行：

```powershell
.\gradlew.bat --no-daemon :platform:ircs-migrator:jib :services:ircs-ops-service:jib
kubectl -n ircs-dev rollout restart deployment/ircs-ops-service
kubectl -n ircs-dev rollout status deployment/ircs-ops-service --timeout=180s
```

不跑：

```text
真实 SMTP
AllowSend one-shot smoke
Rabbit mail queue 投递
destructive maintenance runner
```

## 关联文档

- `docs/requirements/028-10-h-notification-mail-send-history-dev-migration-ops-query.md`
- `docs/reports/090-02810h-notification-mail-send-history-dev-migration-ops-query.md`
- `tasks/done/028-10-e-notification-send-history-schema-ops-query.md`
- `tasks/done/028-10-g-send-history-migrator-dev-deploy-smoke.md`

## 关联任务

- 父任务：`tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- 本任务：`tasks/done/028-10-h-notification-mail-send-history-dev-migration-ops-query.md`

## 当前状态

已完成。dev migrator Job 已真实运行，dev DB 表已落库，ops-service 已 rollout 到包含
send history endpoint 的 dev image，live smoke 已通过。本轮未发送真实 SMTP。

## 实现结果

- `scripts/smoke/notification-mail-send-history-02810g-smoke.ps1` 增加可选参数：
  - `TaskId`
  - `SliceName`
- 默认值保持 `028-10-G`，兼容前序脚本；本轮 smoke 通过参数输出 `028-10-H` 证据。
- 未修改 migrator/ops-service 业务代码；本轮主要执行真实 image push、K8S Job、ops-service rollout
  和 live smoke。

## 验收结果

```text
MARK 1/6 PASS 028-10-H docs/task spec created before live changes
MARK 2/6 PASS migrator dev image built and pushed with digest
MARK 3/6 PASS migrator Job completed in ircs-dev and was cleaned
MARK 4/6 PASS notification_mail_send_history exists in dev DB with expected indexes
MARK 5/6 PASS ops-service send history page and summary endpoints return non-404 live responses
MARK 6/6 PASS SMTP was not sent and APP_MAIL_ENABLED stayed false
```

## 测试与操作记录

本地 focused tests：

```powershell
.\gradlew.bat --no-daemon :platform:ircs-migrator:test :services:ircs-ops-service:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL in 20s`。

image push：

```text
ircs-migrator:dev imageDigest=sha256:f6b8be42100136c204800cb6ad88c082a1aad56f04e10337c89ca22ca829c578
ircs-ops-service:dev imageDigest=sha256:feca779a5bf0c8157a2a2fc8424aee12f266deb9df525ecc05da5f12e4f8b10c
```

K8S 操作：

```powershell
kubectl -n ircs-dev apply -f .\deploy\k8s\dev\migrator-job-dev.yaml
kubectl -n ircs-dev wait --for=condition=complete job/ircs-migrator --timeout=180s
kubectl -n ircs-dev rollout restart deployment/ircs-ops-service
kubectl -n ircs-dev rollout status deployment/ircs-ops-service --timeout=240s
kubectl -n ircs-dev delete job ircs-migrator
```

live 证据：

```text
migrator job: succeeded=1, completionTime=2026-06-09T10:33:42Z
migrator pod imageID=harbor.mnnu.eu.org/ircs/ircs-migrator@sha256:f6b8be42100136c204800cb6ad88c082a1aad56f04e10337c89ca22ca829c578
ops-service pod imageID=harbor.mnnu.eu.org/ircs/ircs-ops-service@sha256:feca779a5bf0c8157a2a2fc8424aee12f266deb9df525ecc05da5f12e4f8b10c
ops-service Ready 1/1, ClusterIP, requests=25m/128Mi, limits=250m/512Mi
```

DB 证据：

```text
table=notification_mail_send_history
columnCount=13
businessIndexCount=4
recent24hCount=0
databasechangelog=20260609-add-notification-mail-send-history|prodigalgal|db/changelog/2026/07/add-notification-mail-send-history.sql|2026-06-09 10:33:37.127999
```

smoke：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-send-history-02810g-smoke.ps1 -TaskId '028-10-H' -SliceName 'send-history-dev-migration-ops-query' -StartOpsPortForward -FailOnUnavailable
```

结果：

```text
status=PASS
reason=SEND_HISTORY_DEV_SMOKE_OK
ops summary/page endpoints are available
summaryStatus=OK
pageTotalElements=0
totalLast24h=0
sent=false
APP_MAIL_ENABLED=false
passwordPrinted=false
recipientPrinted=false
```

## 清理结果

```text
kubectl -n ircs-dev get job,pod -l app=ircs-migrator -o wide
No resources found in ircs-dev namespace.
```

## 未执行项

```text
SKIPPED real SMTP: 本任务只验证 schema/ops 查询，未触发发送
SKIPPED Rabbit mail queue: 未投递 mail message
SKIPPED HTTPRoute: 未新增公网入口，ircs-dev 无 HTTPRoute
SKIPPED unrelated services: 未修改 config-service publisher、content-service listener、Douban、storage-service、portal/admin/BFF
```

## 剩余项

- send history retention 仍需独立任务定义和实现。
- SMTP staging one-shot live preflight 仍需独立门禁、`AllowSend` 人工确认、低频单封和安全收件人策略。
