# 028-16-C1-S7 credential summary redaction task spec

状态：已完成

父任务：

- `tasks/in-progress/028-16-c1-initialization-readiness-gate.md`
- `tasks/in-progress/028-16-c-dependency-initializer-tooling-parity.md`
- `tasks/in-progress/028-16-f-v1-golden-behavior-inventory.md`

上游来源：

- `docs/reports/089-v1-golden-behavior-inventory.md`
- `tasks/done/028-16-c1-s6-credential-runtime-initializer.md`
- V3 `services/ircs-credential-service`

## 背景

`028-16-C1-S6` 已让 credential-service 可从 ENV 和挂载 JSON 导入真实凭据。当前
`CredentialSanitizer.toSummary()` 仍把完整 `payload` 放入 public/admin
`CredentialSummary`，这会让 `GET/POST/PUT /api/v1/credentials` 回显 API key、邮箱授权码、
R2 secret 等真实 secret。internal lease 需要保留真实 secret 给可信内部服务，但 admin/public
summary 只能返回脱敏 metadata。

## 目标

- `CredentialSummary.payload` 不再包含原始 secret 值。
- 对每个 payload key 返回稳定脱敏 metadata，至少包含 `present=true` 和 `redacted=true`。
- `payloadKeys` 继续按 key 排序返回，保持 admin UI 可判断字段存在。
- internal lease API 继续返回真实 secret payload，不受 summary 脱敏影响。
- 测试覆盖 list/find/create/update summary 不回显 secret，lease 仍可取真实 secret。

## 非目标

- 不删除 `CredentialSummary.payload` 字段，避免破坏现有 admin 前端结构。
- 不改写凭据创建/更新请求格式。
- 不修改 internal credential token 逻辑。
- 不做真实 provider/MAIL/R2 live smoke。

## 影响范围

```text
services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/**
services/ircs-credential-service/src/test/java/com/prodigalgal/ircs/credential/**
docs/reports/089-v1-golden-behavior-inventory.md
docs/migrations/089-v1-golden-behavior-inventory.md
tasks/in-progress/028-16-c1-initialization-readiness-gate.md
tasks/index.md
docs/index.md
```

## 验收标准

```text
MARK 1/5 CredentialSummary payload redaction contract documented in this task: done 2026-06-09
MARK 2/5 admin/public credential summaries return redacted metadata instead of secret values: done 2026-06-09
MARK 3/5 payloadKeys/fingerprintSuffix remain available for admin display and duplicate diagnostics: done 2026-06-09
MARK 4/5 internal lease still returns real secret payload under internal token path: done 2026-06-09
MARK 5/5 credential-service tests pass and docs/tasks updated: done 2026-06-09
```

## 测试边界

```text
.\gradlew.bat --no-daemon :services:ircs-credential-service:test --rerun-tasks
```

## 当前状态

2026-06-09：已完成第一刀实现并归档。

实现结果：

- `CredentialSanitizer.toSummary()` 不再把原始 payload 值放入 `CredentialSummary.payload`。
- summary payload 现在按 key 返回稳定 metadata：`{"present": true, "redacted": true}`。
- `payloadKeys` 和 `fingerprintSuffix` 保持可用，admin UI 仍可展示字段存在性和指纹后缀。
- internal lease 仍通过 `CredentialLeaseMapper` 读取 `CredentialRecord.payloadJson()`，可信内部调用
  仍可获取真实 secret payload。

验证：

```text
.\gradlew.bat --no-daemon :services:ircs-credential-service:test --rerun-tasks
BUILD SUCCESSFUL

kubectl apply --dry-run=server -f deploy\k8s\dev\credential-service-dev.yaml
deployment.apps/ircs-credential-service configured (server dry run)
service/ircs-credential-service unchanged (server dry run)
```

后续未完成项：

- 未做真实 provider/MAIL/R2 live smoke。
- 未改 credential 创建/更新请求格式。
- 未改 internal token 机制。
