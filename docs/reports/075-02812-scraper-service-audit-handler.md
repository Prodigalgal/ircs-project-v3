# 075 028-12 scraper-service handler audit 报告

状态：已完成

日期：2026-06-08

关联任务：

- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`
- `tasks/in-progress/028-12-075-scraper-service-audit-handler.md`

## 背景

`028-12` 正在铺开 V1 到 V3 微服务迁移后的 service/worker audit。HTTP Controller/BFF
missing 已清零，config、catalog、content、credential、identity、interaction、magnet、
storage、search、portal 和 task service handler audit 已完成。`ircs-scraper-service`
仍未接入 service handler audit，但它承载手工采集 session、SSE stream、raw video refetch
和 task-service collection task 执行入口，是采集链路可观察性闭环的一部分。

## 目标

- 为 `ircs-scraper-service` 注册 `ServiceRequestAuditFilter`。
- 默认写入 `request_audit_logs.request_source=ircs-scraper-service`。
- 使用本地 MockMvc + H2 测试证明 scraper handler 请求会写入 audit 表。
- 验证 query `token` / `password` 被脱敏。
- 验证 Authorization、Cookie、request body、response content 不进入 audit 表。
- 验证 actuator 噪音被跳过。
- 提供低风险 smoke 脚本，不执行 K8S rollout、不 push image、不触发真实抓取或 RabbitMQ publish。

## 非目标

- 不修改 `task-service`。
- 不修改全局索引、公共需求文档、K8S 环境文档或 028-12 父任务公共账本。
- 不执行 K8S rollout、不 push image。
- 不修改 scraper 抓取、映射、SSE、RabbitMQ publish、task execution 或 refetch 业务语义。
- 不新增真实业务 fixture，不触发 live 抓取、SSE stream、raw video refetch 或 queue publish。

## 方案

复用 shared `ServiceRequestAuditFilter` 和 `RequestAuditSanitizer`。在 scraper-service 内新增
`ScraperServiceAuditConfiguration`，通过 `app.service.audit.enabled` 控制启停，并使用
`app.service.audit.source` 或 `spring.application.name` 解析 request source，默认值为
`ircs-scraper-service`。

本地测试采用 standalone MockMvc 和 mock `ManualScraperService`。选用
`POST /api/v1/scraper/manual/init` 验证 handler audit，因为该 handler 只创建内存 session；
测试中 service 被 mock，不会创建真实 session、不会触发 SSE、不会触发抓取，也不会发布 RabbitMQ。

## smoke 策略

当前 scraper-service 没有真正只读的业务 endpoint：

- `POST /api/v1/scraper/manual/init` 会创建手工采集 session。
- `GET /api/v1/scraper/manual/stream/{sessionId}` 会消费 session 并启动异步抓取。
- `POST /internal/v1/scraper/raw-videos/{id}/refetch` 会触发 refetch 异步流程。
- `POST /internal/v1/scraper/task-executions` 可能触发数据源抓取或 direct item publish。

因此 smoke 脚本默认只做本地语法和说明输出，不自动访问 live service。推荐主线程若要 live，
rollout 后主线程选择 dev-safe 负向 handler 请求：调用
`GET /api/v1/scraper/manual/stream/{invalid-session}`，期望业务失败但仍验证 audit 写入。
该路径不创建 manual session，不触发 `/raw-videos/{id}/refetch`、`/task-executions` 或真实抓取。

## 影响范围

```text
services/ircs-scraper-service/build.gradle
services/ircs-scraper-service/src/main/java/com/prodigalgal/ircs/scraper/ScraperServiceAuditConfiguration.java
services/ircs-scraper-service/src/test/java/com/prodigalgal/ircs/scraper/ScraperServiceAuditFilterTest.java
scripts/smoke/scraper-service-audit-02812-smoke.ps1
docs/reports/075-02812-scraper-service-audit-handler.md
docs/migrations/075-scraper-service-audit-handler.md
tasks/in-progress/028-12-075-scraper-service-audit-handler.md
```

## 风险

- live 调用 scraper 业务 endpoint 可能产生 session、副作用或异步抓取，需要由主线程统一决定。
- audit 表写入依赖目标环境 `request_audit_logs.request_source` schema 已存在。
- service audit 只记录请求元数据，不记录 body，不能用于复原具体抓取 payload。

## 验收标准

```text
MARK 1/4 scraper-service handler request writes request_source
MARK 2/4 scraper-service audit redacts query token/password
MARK 3/4 scraper-service audit excludes Authorization Cookie body and response content
MARK 4/4 scraper-service audit filter skips actuator noise
```

## 测试边界

```text
.\gradlew.bat :services:ircs-scraper-service:test --rerun-tasks
powershell -NoProfile -Command "$null = [System.Management.Automation.PSParser]::Tokenize((Get-Content -Raw 'scripts/smoke/scraper-service-audit-02812-smoke.ps1'), [ref]$null)"
```

不执行 rollout，不运行 live smoke，不触发真实抓取或 RabbitMQ publish。

## 当前状态

已完成。

## 实现结果

- 新增 `ScraperServiceAuditConfiguration`，为 `ircs-scraper-service` 注册
  `ServiceRequestAuditFilter`。
- `services/ircs-scraper-service/build.gradle` 增加 H2 测试依赖。
- 新增 `ScraperServiceAuditFilterTest`，使用 standalone MockMvc、mock
  `ManualScraperService` 和 H2 audit table 验证 scraper handler audit。
- 新增 `scripts/smoke/scraper-service-audit-02812-smoke.ps1`。默认 dry-run，只输出
  dev-safe 边界；显式 `-ExecuteLive` 时调用 invalid stream 请求，不创建 scraper session，
  不触发 refetch/task-executions。

## 验证结果

本地测试：

```text
.\gradlew.bat :services:ircs-scraper-service:test --rerun-tasks
exit code: 0

ScraperServiceAuditFilterTest tests=2, failures=0, errors=0, skipped=0
ScraperMappingServiceTest tests=1, failures=0, errors=0, skipped=0
ManualScraperServiceTaskExecutionTest tests=1, failures=0, errors=0, skipped=0
scraper-service total tests=4, failures=0, errors=0, skipped=0
```

脚本语法：

```text
powershell -NoProfile -Command "$null = [System.Management.Automation.PSParser]::Tokenize((Get-Content -Raw 'scripts/smoke/scraper-service-audit-02812-smoke.ps1'), [ref]$null)"
syntax ok
```

dry-run smoke：

```text
.\scripts\smoke\scraper-service-audit-02812-smoke.ps1
MARK 1/1 scraper-service audit smoke is dev-safe dry-run
```

MARK：

```text
MARK 1/4 scraper-service handler request writes request_source
MARK 2/4 scraper-service audit redacts query token/password
MARK 3/4 scraper-service audit excludes Authorization Cookie body and response content
MARK 4/4 scraper-service audit filter skips actuator noise
```

## rollout/live 状态

主线程已完成 ARM64 image push、`ircs-dev` rollout 和 live smoke。

```text
imageID: harbor.mnnu.eu.org/ircs/ircs-scraper-service@sha256:d33617061c9e3deda010d2db1fba40ca1e6323da2bd113ca4bb19bf1b91e0c55
pod: ircs-scraper-service Ready 1/1
trace: 02812-scraper-audit-20260608175041
invalidSessionId: 97fd57bf-700a-4f99-b83a-08ac3cf236a5
```

live smoke 已改为调用 `GET /api/v1/scraper/manual/stream/{invalid-session}` 并期望失败，避免
`/manual/init` 创建 session，也不触发 `/refetch`、`/task-executions` 或真实抓取。

```text
MARK 1/4 scraper-service pod is Ready
MARK 2/4 invalid stream request fails without scraper execution
MARK 3/4 audit row redacts query token/password
MARK 4/4 cleanup leaves no DB residue
```

后置检查：

```text
request_audit_logs scraper-service trace residue=0
q.ingest.video/q.process.playlist_sync unchanged
HTTPRoute absent
ResourceQuota unchanged: pods 28/30, services 24/26
```
