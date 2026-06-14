# 056 metadata provider parity gating 迁移记录

状态：完成第一刀

关联任务：

- `tasks/in-progress/028-06-real-provider-live-parity.md`

关联报告：

- `docs/reports/056-provider-parity-02806-first-slice.md`

## 迁移目标

将 metadata provider worker 的真实 provider live parity 第一刀收敛到 provider enable gate：
即使 queue 中出现 disabled provider task，也要输出可追踪分类并避免调用真实 provider。

## 行为变化

- 变更前：启用 `app.metadata.provider-workers.tmdb-enabled` 后，worker 会监听 TMDB/Douban/RT
  queue；缺 provider implementation 时输出 `PROVIDER_NOT_FOUND`。
- 变更后：worker 先检查 `app.metadata.providers.*` provider enable gate；disabled provider task
  输出 `PROVIDER_DISABLED` terminal failure，不调用 provider implementation。

## 兼容性

- TMDB enabled 时，现有 success、retryable credential failure、unsupported context 语义保持不变。
- dispatcher 的 fan-out gate 不变。
- 不新增公共 HTTP surface。

## 回滚策略

如发现上游依赖旧的 `PROVIDER_NOT_FOUND` 分类，可仅回退 metadata worker gate 判断与对应测试；
本次没有数据库结构迁移。

## 验证

- `.\gradlew.bat :services:ircs-metadata-worker:test`：通过。
- `.\gradlew.bat :services:ircs-magnet-service:test`：通过，确认相邻 magnet provider fixture 行为未受影响。
- `powershell -ExecutionPolicy Bypass -File scripts\smoke\metadata-provider-live-parity-02806-smoke.ps1`：
  通过 diagnostic 语义；当前本机默认端口没有 credential-service，输出
  `SKIPPED/CREDENTIAL_SERVICE_UNAVAILABLE`，未伪造 live 成功。

## 剩余迁移边界

- 需要 dev credential-service 和真实 TMDB credential 后，再补 queue ack/DLQ、raw metadata 和 search sync
  的 live 联动证据。
- Douban/RT 真实 provider 与 magnet 真实 provider runner 仍需独立迁移切片。
