# 028-07-D avatar R2 live smoke task spec

状态：已完成

父任务：

- `tasks/in-progress/028-07-r2-live-watchdog-parity.md`
- `tasks/in-progress/028-v1-v3-behavior-parity-parent.md`

上游来源：

- `docs/reports/084-02807-avatar-r2-live-smoke.md`
- `docs/migrations/084-avatar-r2-live-smoke.md`
- `tasks/done/028-07-c-storage-service-r2-path-smoke.md`

## 背景

cover image R2 path smoke 已完成，但 avatar 的完整 live path 仍缺少端到端证据。V1 已验证
头像上传后可迁移到对象存储并回写可公开访问 URL；V3 需要证明 identity-service、RabbitMQ
和 storage-service consumer 协作后具备同等副作用。

## 目标

- 构造最小 member/avatar fixture。
- 通过 portal/identity 上传头像，获得本地 `/media/avatars/*` URL。
- 临时启用 storage-service R2 runtime，显式使用 V1 bucket `ircs`，禁用 watchdog。
- 等待 `q.storage.avatar_sync` 消费并验证 `members.avatar_url` 改为 R2 public URL。
- 验证 R2 object 存在、本地 avatar 文件被删除。
- cleanup R2 object、DB fixture、queue/DLQ 和临时 env。

## 非目标

- 不永久修改 dev ConfigMap。
- 不批量迁移历史头像。
- 不新增业务接口或 HTTPRoute。
- 不打印 secret。

## 输入

```text
V1 R2 credential imported into credential-service
ircs-dev-app-config R2_PUBLIC_DOMAIN
identity-service portal auth/avatar upload path
storage-service SYNC_AVATAR consumer
q.storage.avatar_sync
```

## 输出

```text
scripts/smoke/storage-avatar-r2-live-02807-smoke.ps1
live smoke evidence
docs/reports/084-02807-avatar-r2-live-smoke.md
docs/migrations/084-avatar-r2-live-smoke.md
```

## 影响范围

```text
scripts/smoke/storage-avatar-r2-live-02807-smoke.ps1
docs/reports/084-02807-avatar-r2-live-smoke.md
docs/migrations/084-avatar-r2-live-smoke.md
tasks/done/028-07-d-avatar-r2-live-smoke.md
tasks/in-progress/028-07-r2-live-watchdog-parity.md
docs/k8s/current-environment-2026-06-02.md
docs/index.md
tasks/index.md
```

## 依赖

- `ircs-dev` PostgreSQL、RabbitMQ、identity-service、storage-service 可用。
- credential-service 可租用 R2 credential。
- R2 bucket `ircs` 可写入并可 cleanup。

## 风险

- 临时启用 R2 runtime 会触发 storage-service rollout，必须恢复。
- queue 若有共享消息，smoke 不应 purge 非本轮消息。
- member fixture 必须独立 email/trace 并可清理。

## 验收标准

```text
MARK 1/7 avatar R2 credential/config resolves V1 bucket override
MARK 2/7 storage-service temporary R2 runtime is enabled without watchdog scan
MARK 3/7 portal avatar upload persists local media and publishes avatar sync
MARK 4/7 SYNC_AVATAR drains and member avatar_url becomes R2 public URL
MARK 5/7 R2 object exists and local avatar object is removed
MARK 6/7 R2 object, member fixture and queues are cleaned
MARK 7/7 dev env restored with no HTTPRoute/quota/port-forward residue
```

## 测试边界

```powershell
.\gradlew.bat :services:ircs-identity-service:test :services:ircs-storage-service:test --rerun-tasks
.\scripts\smoke\storage-avatar-r2-live-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -ConfirmAvatarR2Write
```

Live smoke 只能由主线程执行。

## 实施结果

- 已新增 `scripts/smoke/storage-avatar-r2-live-02807-smoke.ps1`。
- dry-run 已验证 R2 credential/config、bucket override、public domain 和
  `q.storage.avatar_sync` consumer，不执行 live 写入。
- live smoke 已验证 portal 注册/login、avatar upload、本地 URL、`SYNC_AVATAR` 消费、
  R2 URL 回写、R2 HEAD、本地文件清理、R2/member/queue cleanup 和 dev 环境恢复。

## 验收结果

```text
MARK 1/7 avatar R2 credential/config resolves V1 bucket override - PASS
MARK 2/7 storage-service temporary R2 runtime is enabled without watchdog scan - PASS
MARK 3/7 portal register/login fixture is active for avatar upload - PASS
MARK 4/7 portal/identity avatar upload returns local media URL and publishes SYNC_AVATAR - PASS
MARK 5/7 q.storage.avatar_sync drains and members.avatar_url becomes R2 public URL - PASS
MARK 6/7 R2 object exists and local avatar file is cleaned - PASS
MARK 7/7 R2 object/member/queue cleanup and dev env restore complete - PASS
```

## 测试结果

```powershell
.\gradlew.bat :services:ircs-identity-service:test :services:ircs-storage-service:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL`。

```powershell
.\scripts\smoke\storage-avatar-r2-live-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -ConfirmAvatarR2Write
```

结果：`SUCCESS/AVATAR_R2_LIVE_SMOKE_OK`。

## 后置检查

```text
APP_STORAGE_R2_ENABLED=false
temporary secret absent
member fixture residue=0
q.storage.avatar_sync messages=0
q.storage.avatar_sync.dlq messages=0
HTTPRoute absent
ResourceQuota unchanged: pods=28/30, services=24/26
```
