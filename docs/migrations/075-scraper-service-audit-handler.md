# 075 scraper-service handler audit 迁移记录

状态：已完成

日期：2026-06-08

## 迁移内容

为 `028-12` service handler audit 铺开 `ircs-scraper-service` 切片，计划接入
`ServiceRequestAuditFilter`，使 scraper HTTP handler 请求写入
`request_audit_logs.request_source=ircs-scraper-service`。

## 代码范围

```text
services/ircs-scraper-service/build.gradle
services/ircs-scraper-service/src/main/java/com/prodigalgal/ircs/scraper/ScraperServiceAuditConfiguration.java
services/ircs-scraper-service/src/test/java/com/prodigalgal/ircs/scraper/ScraperServiceAuditFilterTest.java
scripts/smoke/scraper-service-audit-02812-smoke.ps1
docs/reports/075-02812-scraper-service-audit-handler.md
tasks/in-progress/028-12-075-scraper-service-audit-handler.md
```

## 风险控制

- 只复用 shared audit filter，不改 scraper 业务 handler 语义。
- 本地测试使用 H2 audit table 和 mock `ManualScraperService`，不访问 PostgreSQL、RabbitMQ 或外部站点。
- 测试只断言 audit 元数据，不把 request body 或 response body 作为审计内容。
- smoke 脚本默认 dry-run；主线程 live smoke 已改用 invalid stream 请求，避免创建 scraper session、
  真实抓取或 RabbitMQ publish。

## 迁移结果

```text
MARK 1/4 scraper-service handler request writes request_source
MARK 2/4 scraper-service audit redacts query token/password
MARK 3/4 scraper-service audit excludes Authorization Cookie body and response content
MARK 4/4 scraper-service audit filter skips actuator noise
```

验证证据：

```text
.\gradlew.bat :services:ircs-scraper-service:test --rerun-tasks
exit code: 0

ScraperServiceAuditFilterTest tests=2, failures=0, errors=0, skipped=0
scraper-service total tests=4, failures=0, errors=0, skipped=0

scripts/smoke/scraper-service-audit-02812-smoke.ps1 parser: syntax ok
dry-run smoke: MARK 1/1 scraper-service audit smoke is dev-safe dry-run
```

主线程 rollout/live 结果：

```text
imageID: harbor.mnnu.eu.org/ircs/ircs-scraper-service@sha256:d33617061c9e3deda010d2db1fba40ca1e6323da2bd113ca4bb19bf1b91e0c55
pod: ircs-scraper-service Ready 1/1
trace: 02812-scraper-audit-20260608175041
invalidSessionId: 97fd57bf-700a-4f99-b83a-08ac3cf236a5

MARK 1/4 scraper-service pod is Ready
MARK 2/4 invalid stream request fails without scraper execution
MARK 3/4 audit row redacts query token/password
MARK 4/4 cleanup leaves no DB residue
```

## 当前状态

已完成。主线程已完成 ARM64 image push、`ircs-dev` rollout 和 live smoke；fixture cleanup
后 `request_audit_logs` residue 为 0，`q.ingest.video` / `q.process.playlist_sync` 未被污染，
`ircs-dev` 仍无 HTTPRoute，ResourceQuota 保持 `pods=28/30`、`services=24/26`。
