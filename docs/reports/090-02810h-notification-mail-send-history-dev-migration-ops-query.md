# 090 028-10-H notification mail send history dev migration / ops query 报告

状态：已完成

日期：2026-06-09

## 背景

`028-10-G` 已完成 `notification_mail_send_history` 的 platform migrator 接入和 dry-run，
但 dev live DB 未落表，ops-service live endpoint 仍为 404。本轮执行真实 dev migration、
必要的 ops-service rollout，以及只读 smoke 验证。

## 目标

- 真实运行 dev migrator Job。
- 证明 dev DB 中 `notification_mail_send_history` 表存在。
- 验证 ops-service send history page 与 summary endpoint 可查询。
- 保持 SMTP 发送关闭，不触发真实外部发送。

## 执行记录

- 本地文档和 task spec 已先行创建：
  - `docs/requirements/028-10-h-notification-mail-send-history-dev-migration-ops-query.md`
  - `tasks/in-progress/028-10-h-notification-mail-send-history-dev-migration-ops-query.md`
- focused tests 已通过：

```powershell
.\gradlew.bat --no-daemon :platform:ircs-migrator:test :services:ircs-ops-service:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL in 20s`。

- 构建并推送 migrator ARM64 dev image：

```powershell
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':platform:ircs-migrator' -ImageName ircs-migrator -Tag dev -Architecture arm64 -Os linux
```

结果：

```text
image=harbor.mnnu.eu.org/ircs/ircs-migrator:dev
imageDigest=sha256:f6b8be42100136c204800cb6ad88c082a1aad56f04e10337c89ca22ca829c578
imageId=sha256:2000c1ba74845f872c71c33ccd9b681ae37c95bf4abb025d0cabf1e06e29a121
```

- 执行 dev migrator Job：

```powershell
kubectl -n ircs-dev apply -f .\deploy\k8s\dev\migrator-job-dev.yaml
kubectl -n ircs-dev wait --for=condition=complete job/ircs-migrator --timeout=180s
```

结果：

```text
job.batch/ircs-migrator created
job.batch/ircs-migrator condition met
succeeded=1
completionTime=2026-06-09T10:33:42Z
pod imageID=harbor.mnnu.eu.org/ircs/ircs-migrator@sha256:f6b8be42100136c204800cb6ad88c082a1aad56f04e10337c89ca22ca829c578
```

Liquibase 日志关键证据：

```text
Running Changeset: db/changelog/2026/07/add-notification-mail-send-history.sql::20260609-add-notification-mail-send-history::prodigalgal
ChangeSet ... ran successfully in 203ms
Liquibase: Update has been successful. Rows affected: 2
```

- 初次 smoke 证明 DB 已落表但 live ops-service 仍 404：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-send-history-02810g-smoke.ps1 -StartOpsPortForward -FailOnUnavailable
```

结果：`status=UNAVAILABLE`，`opsSendHistory.error=远程服务器返回错误: (404) 未找到。`

- 构建并推送 ops-service ARM64 dev image：

```powershell
.\scripts\docker\push-service-image-jib.ps1 -Service ircs-ops-service -Tag dev -Architecture arm64 -Os linux
```

结果：

```text
image=harbor.mnnu.eu.org/ircs/ircs-ops-service:dev
imageDigest=sha256:feca779a5bf0c8157a2a2fc8424aee12f266deb9df525ecc05da5f12e4f8b10c
imageId=sha256:3f7177d64abf721c93f20bd556b7f9eadda51c720402be2deb895f4f9746fe37
```

- rollout ops-service：

```powershell
kubectl -n ircs-dev rollout restart deployment/ircs-ops-service
kubectl -n ircs-dev rollout status deployment/ircs-ops-service --timeout=240s
```

结果：

```text
deployment.apps/ircs-ops-service restarted
deployment "ircs-ops-service" successfully rolled out
pod imageID=harbor.mnnu.eu.org/ircs/ircs-ops-service@sha256:feca779a5bf0c8157a2a2fc8424aee12f266deb9df525ecc05da5f12e4f8b10c
ready=1/1 updated=1
```

## 验证结果

DB 表与 changelog 证据：

```text
table=notification_mail_send_history
columnCount=13
businessIndexes=idx_notification_mail_send_history_correlation, idx_notification_mail_send_history_created, idx_notification_mail_send_history_status_mode_created, idx_notification_mail_send_history_template_created
primaryKey=notification_mail_send_history_pkey
recent24hCount=0
databasechangelog=20260609-add-notification-mail-send-history|prodigalgal|db/changelog/2026/07/add-notification-mail-send-history.sql|2026-06-09 10:33:37.127999
```

带 `028-10-H` 标识的 live smoke：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\smoke\notification-mail-send-history-02810g-smoke.ps1 -TaskId '028-10-H' -SliceName 'send-history-dev-migration-ops-query' -StartOpsPortForward -FailOnUnavailable
```

结果：

```text
MARK 1/4 notification-worker APP_MAIL_ENABLED checked and SMTP sending stays disabled
MARK 2/4 notification_mail_send_history table exists in dev DB
MARK 3/4 ops send history summary and page endpoints are available
MARK 4/4 SMTP was not sent and APP_MAIL_ENABLED was not changed
status=PASS
reason=SEND_HISTORY_DEV_SMOKE_OK
appMailEnabled=false
appMailSendHistoryEnabled=false(default)
sent=false
passwordPrinted=false
recipientPrinted=false
summaryStatus=OK
totalLast24h=0
sentLast24h=0
failedLast24h=0
skippedLast24h=0
pageTotalElements=0
```

## 资源与清理

- migrator Job 资源保持低配：`requests=25m/128Mi`，`limits=250m/512Mi`。
- ops-service live Deployment 保持低配：`requests=25m/128Mi`，`limits=250m/512Mi`。
- `ircs-ops-service` Service 仍为 `ClusterIP`，端口 `8080`。
- `ircs-dev` namespace 未新增 HTTPRoute，`kubectl -n ircs-dev get httproute` 返回
  `No resources found in ircs-dev namespace.`。
- migrator Job 已清理：

```powershell
kubectl -n ircs-dev delete job ircs-migrator
kubectl -n ircs-dev get job,pod -l app=ircs-migrator -o wide
```

结果：

```text
job.batch "ircs-migrator" deleted
No resources found in ircs-dev namespace.
```

- smoke 脚本启动的 port-forward 在 finally 中停止，未保留本地端口转发进程。

## 结论

028-10-H 已完成：dev DB schema、platform migrator Job、ops-service live endpoint 查询已经闭环。
本轮没有发送真实 SMTP，没有打开 `APP_MAIL_ENABLED`，没有新增公网入口。后续 SMTP staging
live preflight 可以在独立门禁下继续，但仍应保持 `AllowSend` 人工确认和低频单封策略。
