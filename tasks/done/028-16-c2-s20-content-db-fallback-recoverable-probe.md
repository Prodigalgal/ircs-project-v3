# 028-16-C2-S20 content DB fallback recoverable probe task spec

状态：完成

父任务：

- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`

上游来源：

- `docs/requirements/028-16-c2-s20-content-db-fallback-recoverable-probe.md`
- `tasks/done/028-16-c2-s17-real-live-rabbit-config-event-smoke.md`
- `tasks/done/028-16-c2-s19-config-service-change-publisher-live-rollout.md`

## 背景

C2-S17 的 Rabbit publish / deliver / ack 已在 C2-S19 后跑通，但 content-service live
deployment 存在 `APP_CONTENT_SCRAPER_BASE_URL` runtime override，导致 DB fallback read probe
不能直接观察临时 DB 值。运行时覆盖源高于 DB/default seed 是 V1/V3 共同语义，不能为了测试反转。

## 目标

- 为 C2-S17 smoke 增加显式、可恢复、短窗口的 content runtime override probe。
- 记录原 runtime override 值，临时移除后确认缺席，probe 完成后恢复并确认。
- 跑通或重新判定 C2-S17 content read/fallback probe。
- 回写 C2-S17、C2 父任务、索引和本任务的验证结果。

## 非目标

- 不改变 SystemConfig 业务读取优先级。
- 不改 metadata-worker/Douban、notification、storage、portal/admin/BFF。
- 不新增长期公开诊断 endpoint。
- 不处理 Rabbit outage / missed-message TTL fallback。

## 输入

- content key：`app.content.internal.scraper-base-url`
- live override：`APP_CONTENT_SCRAPER_BASE_URL`
- queue：`q.content.config_changed`
- smoke 脚本：`scripts/smoke/config-event-content-live-02816c2-s17-smoke.ps1`

## 输出

- 更新后的 C2-S17 smoke 脚本。
- 本 task spec 的命令、证据、恢复记录和残留风险。
- C2-S17 task spec 的 MARK 状态回写。

## 影响范围

包含：

- `scripts/smoke/config-event-content-live-02816c2-s17-smoke.ps1`
- `deploy/k8s/dev/content-service-dev.yaml` 中可恢复 probe env/说明的最小必要调整
- `docs/requirements/028-16-c2-s20-content-db-fallback-recoverable-probe.md`
- `tasks/done/028-16-c2-s20-content-db-fallback-recoverable-probe.md`
- `tasks/done/028-16-c2-s17-real-live-rabbit-config-event-smoke.md`
- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- `tasks/index.md`

不包含：

- metadata-worker/Douban、notification、storage、portal/admin/BFF。

## 依赖

- `kubectl` 可访问 `ircs-dev`。
- content-service/config-service/postgres/RabbitMQ dev live 可用。
- 允许短暂 patch content-service deployment env，并必须恢复。

## 风险

- live patch 期间 content-service 的 scraper URL 会受 DB 临时值影响；脚本必须把 patch 置于显式开关后，并在 finally 中恢复。
- 并行 worker 可能修改同一 deployment；脚本只操作目标 env，不替换完整 env 列表。
- 如果目标 env 使用 `valueFrom`，本轮脚本不做自动 patch，避免无法精确恢复。

## 验收标准

- 脚本语法检查通过。
- content-service focused tests 通过。
- live smoke 输出 runtime override 原值/恢复证据、Rabbit 证据、content read 证据。
- 临时 DB key/value 清理完成，port-forward 无残留。
- C2-S17 能 MARK 完成；如不能，阻塞项比当前 runtime override 更具体。

## 测试边界

脚本语法：

```text
powershell -NoProfile -Command "$tokens=$null;$errors=$null;[System.Management.Automation.Language.Parser]::ParseFile('scripts\smoke\config-event-content-live-02816c2-s17-smoke.ps1',[ref]$tokens,[ref]$errors) | Out-Null; if ($errors.Count) { $errors | Format-List; exit 1 }; 'PARSER_OK'"
```

focused tests：

```text
.\gradlew.bat --no-daemon :services:ircs-content-service:test --rerun-tasks
```

live smoke：

```text
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\config-event-content-live-02816c2-s17-smoke.ps1 -ConfirmLiveRabbit -TemporarilyRemoveContentRuntimeOverride -ProbeContentRead -CreateTemporaryRawVideoFixture
```

## 当前状态

2026-06-09：任务创建，准备在不破坏 runtime override 优先级的前提下扩展 C2-S17 smoke。

2026-06-09：任务完成。脚本语法检查 `PARSER_OK`，content-service 定向测试
`BUILD SUCCESSFUL`，live smoke 完成 `MARK 7/7`。原 runtime override 为
`APP_CONTENT_SCRAPER_BASE_URL=http://ircs-scraper-service.ircs-dev.svc.cluster.local:8080`，
已在 probe 后恢复；DB config 临时行、`C2-S17-PROBE-*` raw fixture、Rabbit queue messages 和
19087/19088/19089 port-forward 均无残留。报告：
`docs/reports/095-02816c2s20-content-db-fallback-recoverable-probe.md`。
