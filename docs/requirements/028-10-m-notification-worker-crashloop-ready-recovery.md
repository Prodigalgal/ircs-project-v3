# 028-10-M notification-worker CrashLoop Ready recovery

状态：已完成

父任务：

- `tasks/in-progress/028-10-production-governor-credential-pool-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

## 背景

`028-10-L` SMTP one-shot 在本任务开始时尚未完成。当前阻塞点不是 SMTP 发送逻辑，而是
`ircs-dev` 中 notification-worker 启动 CrashLoop：
`CredentialServiceMailCredentialRepository: No default constructor found`。

`028-10-J/K` retention cleanup 已完成；最新可用 notification-worker dev image digest 为
`sha256:79462463933647277e6069c61d0081e75ed241ccdff94f264d311d6ee164027b`。

## 目标

- 定位并修复 `CredentialServiceMailCredentialRepository` 构造器、DI、AOT 或反射导致的启动错误。
- 增加 focused test 覆盖 repository 绑定/启动路径，notification-worker test 全过。
- 构建并推送 ARM64 notification-worker dev image，rollout `ircs-dev`。
- 验证 deployment Ready、restart 稳定、日志无该构造器错误。
- 确认 `APP_MAIL_ENABLED` 等测试开关恢复为 ConfigMap/manifest 默认，不发送 SMTP。

## 非目标

- 不发送 SMTP。
- 不投递业务 mail queue。
- 不输出 secret。
- 不修改 metadata provider、config/content C2、storage、portal/admin/BFF。
- 不完成 `028-10-L` one-shot；该任务在本任务完成时继续保留未完成，后续已由
  `028-10-L` 独立任务完成。

## 影响范围

```text
services/ircs-notification-worker/**
deploy/k8s/dev/notification-worker*
scripts/smoke/notification*
docs/**
tasks/**
```

## 验收标准

```text
MARK 1/6 CrashLoop root cause identified without exposing secrets
MARK 2/6 focused constructor/DI startup test added or corrected
MARK 3/6 notification-worker tests pass locally
MARK 4/6 ARM64 dev image pushed with digest recorded
MARK 5/6 ircs-dev notification-worker Ready and logs no longer contain constructor error
MARK 6/6 APP_MAIL_ENABLED/listener test switches restored; SMTP sent count remains 0
```

## 测试方式

```powershell
.\gradlew.bat --no-daemon :services:ircs-notification-worker:test --rerun-tasks
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\docker\push-service-image-jib.ps1 -Service ircs-notification-worker -Tag dev -Architecture arm64 -Os linux
kubectl -n ircs-dev rollout status deployment/ircs-notification-worker --timeout=180s
```

## 当前状态

已完成。本轮只恢复 notification-worker Ready，并为后续重跑 `028-10-L` SMTP one-shot 清障；
未发送 SMTP，未投递业务 mail queue。

后续补充：`028-10-L` 已在 Ready 恢复后独立完成真实 SMTP one-shot。

结果：

```text
imageDigest=sha256:5271750ff0740e1f2aaf520642cb821fd845215f846bffbf8c0008571c99895e
ircs-dev ready=1/1 restartCount=0
APP_MAIL_ENABLED=false
APP_NOTIFICATION_LISTENER_ENABLED absent from live Deployment env
028-10-M correlation send history count=0
recent 20 minutes status=SENT count=0
```

报告：

- `docs/reports/100-02810m-notification-worker-crashloop-ready-recovery.md`
