# 084 028-07 avatar R2 live smoke 报告

状态：已完成

日期：2026-06-08

## 背景

`028-07-C` 已证明 storage-service cover image path 可以临时启用 R2 runtime，写入 V1
bucket `ircs`，完成 URL/DB side effect 和 cleanup。`028-05-B` 已证明 `SYNC_AVATAR`
consumer 在 R2 active 单元路径中会上传本地头像、回写 R2 URL 并删除本地副本。

剩余缺口是完整 avatar 端到端 live path：portal/identity 上传头像后，identity-service
先写入本地 `/media/avatars/*` URL 并投递 `q.storage.avatar_sync`，storage-service consumer
再把本地对象上传到 R2、把 `members.avatar_url` 改成 public R2 URL，并删除本地对象。

## 目标

- 新增 avatar R2 live smoke。
- 通过 V3 portal/identity 上传最小头像 fixture，验证 `members.avatar_url` 先落到本地
  `/media/avatars/*`。
- 临时启用 storage-service R2 runtime，显式覆盖 V1 bucket `ircs`，禁用 watchdog。
- 验证 `q.storage.avatar_sync` 被消费，`members.avatar_url` 回写到 `img.mnnu.eu.org` R2 URL。
- 验证 R2 object 存在、本地 avatar 文件被清理，最后删除 R2 object、DB fixture 和临时 Secret。
- smoke 完成后恢复 `APP_STORAGE_R2_ENABLED=false` 与原始 ConfigMap `valueFrom`。

## 非目标

- 不永久启用 R2。
- 不扫描历史 avatar 或 cover 数据。
- 不新增 HTTPRoute、Service、Pod、PVC 或中间件。
- 不打印 R2 secret。
- 不改变 identity/storage 的业务代码，除非 live smoke 暴露真实 parity bug。

## 建议写入范围

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

live smoke 只能由主线程执行，并在 `finally` 中恢复 dev 环境。

## 风险

- avatar path 跨 identity-service、storage-service、RabbitMQ、PostgreSQL 和 R2，失败时必须区分
  queue 未消费、R2 上传失败、DB compare-and-set 失败和 cleanup 失败。
- storage-service R2 env 临时覆盖必须完全恢复，否则会影响 dev 后续测试。
- R2 object key 必须限定为本轮 fixture，cleanup 必须幂等。

## 实施结果

- 新增 `scripts/smoke/storage-avatar-r2-live-02807-smoke.ps1`。
- smoke 会从 credential-service 租用 V1 R2 credential，临时把 storage-service R2 runtime
  指向 V1 bucket `ircs`，并禁用 watchdog。
- 通过 portal-bff 注册/激活/login 后上传最小 PNG 头像，验证先得到本地
  `/media/avatars/*` URL。
- 等待 `q.storage.avatar_sync` 被 storage-service consumer 消费，验证
  `members.avatar_url` 回写为 `https://img.mnnu.eu.org/avatars/*`。
- 通过 R2 HEAD 验证对象存在，并确认 storage-service 本地 `/app/storage/avatars/*` 文件已被删除。
- cleanup 会删除 R2 object、测试 member、临时 Secret，并恢复 storage-service deployment
  到 `APP_STORAGE_R2_ENABLED=false` 和 ConfigMap `valueFrom`。

## 验收结果

```text
MARK 1/7 avatar R2 credential/config resolves V1 bucket override
MARK 2/7 storage-service temporary R2 runtime is enabled without watchdog scan
MARK 3/7 portal register/login fixture is active for avatar upload
MARK 4/7 portal/identity avatar upload returns local media URL and publishes SYNC_AVATAR
MARK 5/7 q.storage.avatar_sync drains and members.avatar_url becomes R2 public URL
MARK 6/7 R2 object exists and local avatar file is cleaned
MARK 7/7 R2 object/member/queue cleanup and dev env restore complete
```

live smoke 结果：

```text
status: SUCCESS
reason: AVATAR_R2_LIVE_SMOKE_OK
traceId: 02807-avatar-r2-live-20260608201503
memberId: 049c4d39-8c37-47ad-b2cc-0c9c7fa62d25
localAvatarUrl: /media/avatars/3220fc7838ccdab0a3a21b5d8498275c7abe6930abbb059e9fdb4939bba8c2fd.png
r2Url: https://img.mnnu.eu.org/avatars/3220fc7838ccdab0a3a21b5d8498275c7abe6930abbb059e9fdb4939bba8c2fd.png
storagePath: avatars/3220fc7838ccdab0a3a21b5d8498275c7abe6930abbb059e9fdb4939bba8c2fd.png
effectiveBucket: ircs
configMapBucket: ircs-dev
queueAfterCleanup: q.storage.avatar_sync messages=0 ready=0 unacked=0 consumers=1
dlqAfterCleanup: q.storage.avatar_sync.dlq messages=0 ready=0 unacked=0 consumers=0
HTTPRoute: none
ResourceQuota: pods=28/30, services=24/26
```

## 测试结果

```powershell
.\gradlew.bat :services:ircs-identity-service:test :services:ircs-storage-service:test --rerun-tasks
```

结果：`BUILD SUCCESSFUL`。

```powershell
.\scripts\smoke\storage-avatar-r2-live-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken
```

dry-run 结果：`SKIPPED/LIVE_AVATAR_R2_WRITE_NOT_CONFIRMED`，已验证 credential/config/queue
consumer，不写 R2。

```powershell
.\scripts\smoke\storage-avatar-r2-live-02807-smoke.ps1 -StartCredentialPortForward -UseKubernetesSecretToken -ConfirmAvatarR2Write
```

live 结果：`SUCCESS/AVATAR_R2_LIVE_SMOKE_OK`。

## 后置检查

```text
APP_STORAGE_R2_ENABLED=false
APP_STORAGE_R2_BUCKET_NAME valueFrom=ircs-dev-app-config.R2_BUCKET_NAME
APP_STORAGE_R2_PUBLIC_DOMAIN valueFrom=ircs-dev-app-config.R2_PUBLIC_DOMAIN
temporary secret ircs-r2-avatar-smoke-02807 absent
member fixture residue=0
q.storage.avatar_sync messages=0 ready=0 unacked=0 consumers=1
q.storage.avatar_sync.dlq messages=0 ready=0 unacked=0 consumers=0
HTTPRoute absent
ResourceQuota unchanged: pods=28/30, services=24/26
```

## 遗留边界

- `ircs-dev-app-config.R2_BUCKET_NAME` 仍为 `ircs-dev`，而 V1 live credential 对应 bucket
  为 `ircs`；本 smoke 使用显式 runtime override 规避。是否永久修正 dev ConfigMap bucket
  仍需单独决策。
