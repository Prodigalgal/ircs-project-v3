# 028-12-075 scraper-service audit handler task spec

状态：已完成

日期：2026-06-08

## 背景

IRCS V3 正在做 V1 已验证功能到 V3 微服务的行为级 1:1 迁移。`028-12` 当前主任务为
service/worker audit 铺开，已完成多个 service handler audit。`ircs-scraper-service`
仍需要接入 handler audit，以便采集链路 HTTP 请求在 `request_audit_logs` 中带来源字段。

## 目标

- 在 `ircs-scraper-service` 注册 `ServiceRequestAuditFilter`。
- 默认 `request_source` 为 `ircs-scraper-service`。
- 增加 H2 测试依赖和本地 MockMvc/H2 测试。
- 覆盖 request source 写入、query token/password 脱敏、Authorization/Cookie/request body/response content 排除、actuator 跳过。
- 新增低风险 smoke 脚本，说明 scraper 没有真正只读 endpoint 时的安全边界。

## 非目标

- 不修改 `task-service`。
- 不修改 `docs/index.md`、`tasks/index.md`、`docs/requirements/028-v1-v3-behavior-parity-closeout.md`、
  `docs/k8s/current-environment-2026-06-02.md` 或
  `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`。
- 不 rollout、不 push image、不访问 live scraper-service。
- 不触发真实抓取、SSE stream、RabbitMQ publish、raw video refetch 或 task execution。

## 上游来源

- `docs/reports/075-02812-scraper-service-audit-handler.md`
- `docs/migrations/075-scraper-service-audit-handler.md`
- `tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`

## 输入

- `services/ircs-scraper-service` 当前 controller/service/test/build.gradle/application.yaml。
- 已完成的 task/search/portal service audit configuration、测试和 smoke 模式。
- shared `ServiceRequestAuditFilter` 与 `RequestAuditSanitizer`。

## 输出

- scraper-service audit configuration。
- scraper-service MockMvc/H2 audit 测试。
- scraper-service audit smoke 脚本。
- 本地报告、迁移记录和 task spec 状态回写。

## 影响范围

```text
services/ircs-scraper-service/**
scripts/smoke/scraper-service-audit-02812-smoke.ps1
docs/reports/075-02812-scraper-service-audit-handler.md
docs/migrations/075-scraper-service-audit-handler.md
tasks/in-progress/028-12-075-scraper-service-audit-handler.md
```

## 依赖

- shared `ServiceRequestAuditFilter` 已可用。
- H2 测试运行依赖 Gradle test classpath。
- 主线程统一处理公共账本、rollout 和 live smoke 决策。

## 风险

- scraper-service live endpoint 大多有副作用，不能像 task/search/portal 一样默认 live。
- `POST /api/v1/scraper/manual/init` 虽低风险，但仍会创建 session；本 worker 不执行 live。
- 若 shared audit schema 不存在，实际环境写入会失败；本地 H2 只验证 handler/filter 行为。

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

不运行 live smoke，不 rollout，不 push image。

## 关联文档

- `docs/reports/075-02812-scraper-service-audit-handler.md`
- `docs/migrations/075-scraper-service-audit-handler.md`

## 关联任务

- 父任务：`tasks/in-progress/028-12-service-audit-rollout-and-worker-job-audit.md`
- 子任务：当前文件

## 当前状态

已完成。

## 实现结果

- `services/ircs-scraper-service/src/main/java/com/prodigalgal/ircs/scraper/ScraperServiceAuditConfiguration.java`
  注册 `ServiceRequestAuditFilter`。
- `services/ircs-scraper-service/build.gradle` 增加 `com.h2database:h2` 测试依赖。
- `services/ircs-scraper-service/src/test/java/com/prodigalgal/ircs/scraper/ScraperServiceAuditFilterTest.java`
  覆盖 request source、query 脱敏、敏感 header/cookie/body/response 排除和 actuator 跳过。
- `scripts/smoke/scraper-service-audit-02812-smoke.ps1` 提供默认 dry-run 与显式 live 模式。

## 验证结果

```text
.\gradlew.bat :services:ircs-scraper-service:test --rerun-tasks
exit code: 0

ScraperServiceAuditFilterTest tests=2, failures=0, errors=0, skipped=0
ScraperMappingServiceTest tests=1, failures=0, errors=0, skipped=0
ManualScraperServiceTaskExecutionTest tests=1, failures=0, errors=0, skipped=0
```

```text
scripts/smoke/scraper-service-audit-02812-smoke.ps1 parser: syntax ok
.\scripts\smoke\scraper-service-audit-02812-smoke.ps1
MARK 1/1 scraper-service audit smoke is dev-safe dry-run
```

## MARK

```text
MARK 1/4 scraper-service handler request writes request_source
MARK 2/4 scraper-service audit redacts query token/password
MARK 3/4 scraper-service audit excludes Authorization Cookie body and response content
MARK 4/4 scraper-service audit filter skips actuator noise
```

## 遗留与交接

- 主线程已执行 K8S rollout、image push 和 live smoke。
- live smoke 选择 invalid stream 请求，不创建 manual session，不调用 refetch/task-executions，不触发真实抓取或
  RabbitMQ publish。

```text
imageID: harbor.mnnu.eu.org/ircs/ircs-scraper-service@sha256:d33617061c9e3deda010d2db1fba40ca1e6323da2bd113ca4bb19bf1b91e0c55
trace: 02812-scraper-audit-20260608175041

MARK 1/4 scraper-service pod is Ready
MARK 2/4 invalid stream request fails without scraper execution
MARK 3/4 audit row redacts query token/password
MARK 4/4 cleanup leaves no DB residue
```
