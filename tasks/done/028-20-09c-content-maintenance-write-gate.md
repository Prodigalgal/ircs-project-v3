# 028-20-09C Content maintenance write gate

状态：done

父任务：`tasks/in-progress/028-20-post-parity-runtime-platform-breakdown.md`

来源：`028-20-09B` scoped gate registry 后续 owner service 接入。

## 目标

- content-service 首批接入 `MaintenanceWriteGateInspector`。
- raw/unified video 管理写入口在 DB 写入、search outbox、normalization/aggregation/refetch dispatch 前先检查 scoped gate。
- 支持 generic `content-service/video/*` 和 specific `content-service/raw-video|unified-video/<id>` 两级资源范围。

## 范围

- `services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/ContentMaintenanceGate.java`
- `RawVideoAdminService`
- `UnifiedVideoAdminService`
- focused tests

## 完成内容

- `ContentMaintenanceGate` 封装 content-service owner scope 判定。
- raw video `create/update/delete/batchDelete/reNormalize/reEnrich/reFetch` 接入 write gate。
- unified video `create/update/delete/batchDelete/recalculateMetadata` 接入 write gate。
- `content-service/video/*` 可统一阻断 raw/unified video 写；specific gate 可只阻断某个 raw/unified video。

## 验证结果

```powershell
.\gradlew.bat --no-daemon :services:ircs-content-service:test --tests "*ContentMaintenanceGateTest" --tests "*RawVideoAdminServiceTest" --tests "*UnifiedVideoAdminServiceTest"
.\gradlew.bat --no-daemon :services:ircs-content-service:bootJar
```

结果：全部通过。

备注：首次并行跑 content-service focused tests 与 bootJar 时，Windows/Gradle 增量编译出现 content-service class file 临时竞争；随后单独重跑 bootJar 通过。

## 遗留边界

- people/auxiliary/source-domain 写入口尚未接入。
- search/storage/credential 等其他 owner service 仍需逐项接入 WRITE/CONSUMER gate。
- worker consumer 的 `PAUSE_CONSUMERS` 接入仍后置。
