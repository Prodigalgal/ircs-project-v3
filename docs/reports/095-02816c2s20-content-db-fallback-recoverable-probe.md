# 095 028-16-C2-S20 content DB fallback recoverable probe 报告

状态：完成

日期：2026-06-09

关联任务：

- `tasks/done/028-16-c2-s20-content-db-fallback-recoverable-probe.md`
- `tasks/done/028-16-c2-s17-real-live-rabbit-config-event-smoke.md`
- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`

## 结论

C2-S20 已完成，并把 C2-S17 从 PARTIAL 推进到完整 `MARK 7/7`。

本轮没有改变 V1/V3 SystemConfig 读取优先级。content-service 仍遵循：

```text
运行时覆盖源 -> system_configs DB -> service fallback/default
```

live probe 使用显式开关短暂移除 content-service deployment 中的
`APP_CONTENT_SCRAPER_BASE_URL`，确认 runtime override 缺席后写入临时 DB 值并触发 refetch。
content-service 返回的 502 响应中包含临时 DB fallback base URL，证明 content read probe 已观察到
DB fallback 值。finally 中已恢复 runtime override、清理 DB config、删除临时 raw fixture，并回收
port-forward。

## 变更

- `scripts/smoke/config-event-content-live-02816c2-s17-smoke.ps1`
  - 新增 `-TemporarilyRemoveContentRuntimeOverride`。
  - 新增 `-CreateTemporaryRawVideoFixture`。
  - runtime override 原值只在显式开关下临时移除，并在 finally 中恢复。
  - live DB 没有 raw video 时，可创建 `C2-S17-PROBE-*` 临时 fixture，probe 后按 id/source marker 删除。
  - 对 `SKIPPED` read probe 不再误报 SUCCESS。
- `docs/requirements/028-16-c2-s20-content-db-fallback-recoverable-probe.md`
- `tasks/done/028-16-c2-s20-content-db-fallback-recoverable-probe.md`
- `tasks/done/028-16-c2-s17-real-live-rabbit-config-event-smoke.md`
- `tasks/in-progress/028-16-c2-system-config-cache-event-parity.md`
- `tasks/index.md`
- `docs/index.md`

未修改 metadata-worker/Douban、notification、storage、portal/admin/BFF；未新增长期公开诊断接口。

## 命令与证据

脚本语法：

```text
$tokens=$null; $errors=$null; [System.Management.Automation.Language.Parser]::ParseFile("scripts\smoke\config-event-content-live-02816c2-s17-smoke.ps1", [ref]$tokens, [ref]$errors) | Out-Null; if ($errors.Count) { $errors | Format-List; exit 1 }; "PARSER_OK"
PARSER_OK
```

content-service 定向测试：

```text
.\gradlew.bat --no-daemon :services:ircs-content-service:test --rerun-tasks
BUILD SUCCESSFUL
```

live smoke：

```text
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke\config-event-content-live-02816c2-s17-smoke.ps1 -ConfirmLiveRabbit -TemporarilyRemoveContentRuntimeOverride -ProbeContentRead -CreateTemporaryRawVideoFixture
MARK 1/7 kubectl can access target namespace
MARK 2/7 config-service and content-service deployments are ready
MARK 3/7 Rabbit content config queue is present with a consumer
MARK 4/7 original config-service value captured before write
MARK 5/7 config-service wrote temporary non-sensitive value
MARK 6/7 Rabbit publish and content consumer ack observed for temporary update
MARK 7/7 content-service read probe observed temporary DB fallback value
status=SUCCESS
reason=LIVE_CONFIG_EVENT_SMOKE_COMPLETED
```

runtime override 原值：

```text
APP_CONTENT_SCRAPER_BASE_URL=http://ircs-scraper-service.ircs-dev.svc.cluster.local:8080
sha256=f816fc8486d0b7487ac2b428923d48bc290a73bccdd3613879f0bb2d0d99c732
```

runtime override patch/restore：

```text
patch status=TEMPORARILY_REMOVED
after patch present=false

restore status=RESTORED
after restore APP_CONTENT_SCRAPER_BASE_URL=http://ircs-scraper-service.ircs-dev.svc.cluster.local:8080
```

temporary update Rabbit evidence：

```text
before publish=4 deliver=4 deliverGet=4 ack=4 messages=0 consumers=1
after  publish=5 deliver=5 deliverGet=5 ack=5 messages=0 consumers=1
publishDelta=1
deliverDelta=2
ackDelta=1
```

content read evidence：

```text
temporaryValue=http://content-config-live-smoke.invalid/20260609114151
temporaryWrite.effectiveSource=DB
rawVideoFixture.id=102ba5b3-850c-42c4-b8e2-f36821de85f9
httpStatus=502
temporaryValueObserved=true
bodyPreview contains:
http://content-config-live-smoke.invalid/20260609114151/internal/v1/scraper/raw-videos/102ba5b3-850c-42c4-b8e2-f36821de85f9/refetch
```

restore Rabbit evidence：

```text
before publish=5 deliver=5 deliverGet=5 ack=5 messages=0 consumers=1
after  publish=6 deliver=6 deliverGet=6 ack=6 messages=0 consumers=1
publishDelta=1
deliverDelta=2
ackDelta=1
verifiedOriginalState=true
```

## 清理与残留

DB 清理：

```text
target_rows=0
temp_rows=0
raw_fixture_rows=0
```

Rabbit 收尾：

```text
q.content.config_changed      0 0 0 1
q.content.config_changed.dlq  0 0 0 0
```

port-forward 残留：

```text
Get-CimInstance Win32_Process ... kubectl.exe port-forward ... 19087/19088/19089
无输出
```

## 遗留风险

- C2-S17 已完成；不再以 `CONTENT_READ_BLOCKED_BY_RUNTIME_OVERRIDE` 作为阻塞。
- C2 父任务仍需单独决策 Rabbit outage / missed-message 下是否增加 TTL fallback，避免事件丢失后长期读旧缓存。
- 可恢复 probe 需要显式 live 开关，不应作为常规 smoke 默认行为。
