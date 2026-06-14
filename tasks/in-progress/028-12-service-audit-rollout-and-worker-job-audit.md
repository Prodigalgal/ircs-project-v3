# 028-12 service audit rollout and worker job audit

状态：当前已知 service/worker audit matrix 实现闭环，统一真实测试仍需补证据窗口

归档原文：

- `docs/reports/archive/2026-06-13-doc-compaction/028-12-service-audit-rollout-and-worker-job-audit-full-log.md`

## 当前结论

028-12 的目标是补齐 HTTP service handler audit 与 worker/job audit，让门户、admin、ops
持续验证时能追踪请求、异步任务、runner 和消费者行为。

已完成：

- `ServiceRequestAuditFilter` 已铺到 catalog、content、credential、identity、interaction、
  magnet、storage、search、portal、task 等服务代表入口。
- 敏感字段处理已覆盖 Authorization、Cookie、query token、password/request body 等高风险边界。
- `worker_job_audit_events` schema、ops 查询 API、summary API 已落地。
- notification、metadata、normalization、storage、task、search、scraper/resource 等 worker/job
  audit hook 已有实现或 live/UTB 证据。
- ingestion-worker raw/playlist queue consumer worker-job audit 已补实现。

## 剩余任务

1. 统一 K8S 真实测试窗口重新采集各服务 audit 可见性证据。
2. 对真实 runner、队列 consumer、maintenance job 补 worker/job audit 失败样本和延迟指标样本。
3. 与 029 Rabbit task runtime、028-20 Micrometer 指标合并，避免重复建设查询面。
4. 后续验证只允许走 `ircs-dev` 真实集群；不再执行本地 smoke 或 fake queue fixture。

## 当前风险

- 历史文档中仍存在早期 `local smoke` / fixture cleanup 语句；现目标态已升级为真实 K8S 在线测试。
- 部分服务只有只读最小端点 audit 证据，复杂写路径和异步失败路径需要在统一测试阶段补足。

