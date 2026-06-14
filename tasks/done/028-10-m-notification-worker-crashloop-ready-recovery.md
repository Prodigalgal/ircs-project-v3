# 028-10-M notification-worker CrashLoop Ready recovery

状态：已完成

父任务：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

## Compact Task Spec

目标：

- 修复 notification-worker CrashLoop：
  `CredentialServiceMailCredentialRepository: No default constructor found`。
- 让 `ircs-dev` 的 `ircs-notification-worker` 恢复 Ready。
- 保持 `028-10-L` SMTP one-shot 在本任务内不完成；后续已由 `028-10-L` 独立任务完成。

范围：

```text
services/ircs-notification-worker/**
deploy/k8s/dev/notification-worker*
scripts/smoke/notification*
docs/**
tasks/**
```

非目标：

- 不发送 SMTP。
- 不投递业务 mail queue。
- 不输出 secret。
- 不修改 metadata provider、config/content C2、storage、portal/admin/BFF。

## 实现结果

- `CredentialServiceMailCredentialRepository` 不再作为 `@Component` 注册，不再使用
  `@Autowired` 构造器。
- 新增 `NotificationMailCredentialConfiguration`，由 `@Bean` 显式创建 repository。
- focused test 覆盖 Spring context 中 repository bean 创建路径，避免再次依赖默认构造器。
- `ircs-dev` 删除临时残留 `APP_NOTIFICATION_LISTENER_ENABLED=true`，保留 `APP_MAIL_ENABLED`
  从 ConfigMap 读取且值为 `false`。

## 验证结果

通过：

```powershell
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test --rerun-tasks
```

结果：

```text
BUILD SUCCESSFUL in 19s
```

通过：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\docker\push-service-image-jib.ps1 -Service ircs-notification-worker -Tag dev -Architecture arm64 -Os linux
kubectl -n ircs-dev set env deployment/ircs-notification-worker APP_NOTIFICATION_LISTENER_ENABLED-
kubectl -n ircs-dev rollout status deployment/ircs-notification-worker --timeout=180s
```

结果：

```text
imageDigest=sha256:5271750ff0740e1f2aaf520642cb821fd845215f846bffbf8c0008571c99895e
deployment "ircs-notification-worker" successfully rolled out
ready=1/1 generation=19 observed=19
pod=ircs-notification-worker-7684c86d5c-7ptxd ready=True restartCount=0
imageID=harbor.mnnu.eu.org/ircs/ircs-notification-worker@sha256:5271750ff0740e1f2aaf520642cb821fd845215f846bffbf8c0008571c99895e
```

日志：

```text
Tomcat started on port 8080 (http) with context path '/'
Started IrcsNotificationWorkerApplication in 44.583 seconds
No CredentialServiceMailCredentialRepository / No default constructor / ERROR / Exception match in the 2m verification window
```

no-send / 恢复：

```text
APP_MAIL_ENABLED=false
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
