# 100 028-10-M notification-worker CrashLoop Ready recovery

## 结论

`ircs-dev` notification-worker CrashLoop 阻塞已解除。根因收敛为
`CredentialServiceMailCredentialRepository` 同时存在生产构造器和测试构造器，生产路径依赖
`@Autowired` 选择构造器；在启动/装配路径上容易退回默认构造器查找并出现
`No default constructor found`。本轮改为显式 `@Bean` 装配，repository 本身不再作为
component scan 目标，也不再使用 `@Autowired`。

`028-10-L` SMTP one-shot 在本任务内未执行，当时仍保留为后续任务；后续已由
`028-10-L` 独立任务完成。

## 改动

- `CredentialServiceMailCredentialRepository` 移除 `@Component` 和 `@Autowired` 构造器。
- 新增 `NotificationMailCredentialConfiguration`，显式构造带 `OutboundHttpClient` 的
  repository bean。
- 新增 focused Spring context test，验证无默认构造器、无 `@Autowired` 选择时仍能创建
  `CredentialServiceMailCredentialRepository` bean。
- live Deployment 删除临时残留 `APP_NOTIFICATION_LISTENER_ENABLED=true`，恢复默认不消费 mail queue。

## 验证

```powershell
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test --rerun-tasks
```

结果：

```text
BUILD SUCCESSFUL in 19s
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\docker\push-service-image-jib.ps1 -Service ircs-notification-worker -Tag dev -Architecture arm64 -Os linux
```

结果：

```text
Pushed harbor.mnnu.eu.org/ircs/ircs-notification-worker:dev for linux/arm64
imageDigest=sha256:5271750ff0740e1f2aaf520642cb821fd845215f846bffbf8c0008571c99895e
```

```powershell
kubectl -n ircs-dev set env deployment/ircs-notification-worker APP_NOTIFICATION_LISTENER_ENABLED-
kubectl -n ircs-dev rollout status deployment/ircs-notification-worker --timeout=180s
```

结果：

```text
deployment "ircs-notification-worker" successfully rolled out
ready=1/1 generation=19 observed=19
pod=ircs-notification-worker-7684c86d5c-7ptxd ready=True restartCount=0
imageID=harbor.mnnu.eu.org/ircs/ircs-notification-worker@sha256:5271750ff0740e1f2aaf520642cb821fd845215f846bffbf8c0008571c99895e
```

日志证据：

```text
Tomcat started on port 8080 (http) with context path '/'
Started IrcsNotificationWorkerApplication in 44.583 seconds
No CredentialServiceMailCredentialRepository / No default constructor / ERROR / Exception match in the 2m verification window
```

开关与 no-send 证据：

```text
APP_MAIL_ENABLED=false from ircs-dev-app-config
APP_NOTIFICATION_LISTENER_ENABLED absent from live Deployment env
028-10-M correlation send history count=0
recent 20 minutes notification_mail_send_history status=SENT count=0
SMTP sent=false
business mail queue published by this task=false
secret printed=false
```

## MARK

```text
MARK 1/6 PASS CrashLoop root cause identified without exposing secrets
MARK 2/6 PASS focused constructor/DI startup test added
MARK 3/6 PASS notification-worker tests pass locally
MARK 4/6 PASS ARM64 dev image pushed with digest recorded
MARK 5/6 PASS ircs-dev notification-worker Ready and logs no longer contain constructor error
MARK 6/6 PASS APP_MAIL_ENABLED/listener test switches restored; SMTP sent count remains 0
```
