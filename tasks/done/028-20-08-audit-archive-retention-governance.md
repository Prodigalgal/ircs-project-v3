# 028-20-08 audit / archive / retention governance

状态：done

父任务：`tasks/in-progress/028-20-post-parity-runtime-platform-breakdown.md`

来源：`docs/decisions/005-post-parity-runtime-platform-decisions.md`

## 完成内容

- `request_audit_logs`、`worker_job_audit_events`、`notification_mail_send_history` 已切到 `audit_class` 契约：`SECURITY` / `BEHAVIOR` / `SYSTEM`。
- `shared:ircs-common` 增加 `AuditClass`、`AuditClassifiers`、`AuditEsOutboxWriter`，request / worker audit 写入分类并 best-effort 写 `audit_es_replication_outbox`。
- `notification-worker` mail send history 写入 `SYSTEM` audit class 和 ES outbox；retention cleanup 从直接删除升级为先写 `audit_archive_entries` 再按 id 删除。
- `ops-service` 增加只读 audit governance summary：archive summary 与 ES outbox summary。
- 按破坏性迁移处理 schema：notification-worker 本地 schema 与 migrator 基础 changelog 均改为新目标态，不保留旧建表契约。

## 验证

```powershell
.\gradlew.bat --no-daemon :shared:ircs-common:test :platform:ircs-migrator:build :services:ircs-notification-worker:test :services:ircs-ops-service:test
.\gradlew.bat --no-daemon :shared:ircs-common:jar :platform:ircs-migrator:build :services:ircs-notification-worker:bootJar :services:ircs-ops-service:bootJar
.\gradlew.bat --no-daemon :services:ircs-config-service:test :services:ircs-credential-service:test :services:ircs-catalog-service:test :services:ircs-content-service:test :services:ircs-identity-service:test :services:ircs-interaction-service:test :services:ircs-magnet-service:test :services:ircs-portal-service:test :services:ircs-scraper-service:test :services:ircs-search-service:test :services:ircs-storage-service:test :services:ircs-task-service:test
```

结果：以上命令均通过。

## 遗留边界

- 本切片只落地 DB 可信源、ES replication outbox 和 ops 可观测入口；真实 Elasticsearch sink/relay 后续单独实现。
- 未执行 K8S live smoke；本轮验证范围为本地 focused tests、受影响服务 tests 和 affected bootJar。
- retention 的统一调度入口留给 `028-20-09` ops-service maintenance scheduler。
