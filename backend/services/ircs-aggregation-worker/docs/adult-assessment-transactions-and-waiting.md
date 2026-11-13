# 成人内容判断：短事务、快照与持久等待

聚合在短数据库事务中完成原有绑定、管线和维护锁工作，提交后使用短只读 REPEATABLE READ 事务获取分类输入快照，再通过一次批量 HTTP 请求调用 content-safety。等待 Google、微批队列和额度时不持有 JDBC 连接、聚合 advisory lock 或匹配锁。

未完成远端判断的记录保留本地明确 ADULT 证据，其他记录写入 SUSPECT；adult_checked_at 保持空值，以便维护重扫恢复。INDEX 请求在操作范围内缓冲和去重。已由本地证据明确判为 ADULT 的统一记录，在原聚合事务成功提交后立即发布其已有 unified INDEX 请求，避免旧 SAFE 搜索文档因其他条目排队继续暴露；该请求从缓冲移除，其他未决请求继续等待。最终判断写回后可再次发布已明确受限记录的更新索引。其余 INDEX 在全部判断完成且最终短事务成功后发布。原有 DELETE 与封面任务提交顺序保留；publishSearch=false 不会在后续判断阶段变相发布。

快照包含统一记录摘要（含 version、locked_fields、分类输入、adult override）以及绑定/raw/source/封面域名/标准分类的摘要。最终写入前重新获取维护共享锁、检查 ClusterLeaseGuard，锁定统一记录和现有来源行，再以新 READ COMMITTED 语句重新比较摘要。新内容、解绑、删除或人工 adult 字段锁均使迟到结果失效；最终事务回滚，不写旧结果和索引。现有匹配锁只覆盖原始聚合事务。

## 等待与失败分开

Content-safety 沿用旧 DTO，在 model.available=false、model.raw.provider=GOOGLE_DECISIONS、raw.retryable=true 时给出 reason/retryAfterSeconds。

- QUEUE_PENDING / QUEUE_FULL / QUOTA_WAIT / COOLDOWN 使用本模块 DistributedAggregationWorkDeferrer 原子延期。单次等待 2 秒至 7 天，直接采用队列 ETA 或真实额度/冷却 Retry-After，不叠加普通指数退避；不 complete、不重新 submit、不记录业务失败审计。
- HTTP 错误、连接/JSON/不完整响应、实际供应商异常等走原 RuntimeWorkQueue.fail，保留普通指数退避、业务上限和可恢复 DLQ。未知/不可信 reason 不具有免失败预算资格。
- 低置信度等 retryable=false 的终态 SUSPECT 正常完成，留给人工复核。已有 ADULT/adultRestricted=true 结果不因辅助 Google unavailable/pending 转成等待；混批其他未决条目仍按原批次延期语义处理。

## 文本覆盖边界

aggregation 到 content-safety 的现有 payload 会对 description、title 等单字段截取至 512 字符，并省略 rawMetadata。Google 侧 max12000 是对**服务收到并拼接后的文本**再次设定的上限，不能恢复此前截掉的内容，也不代表判断了完整原始简介。这里保持原跨服务压缩契约；判断结论受已传输证据覆盖范围约束。

延期失败若表示旧 claim 已失效，不转而修改或完成新 claim。基础设施异常保留原 claim，由已有可见性恢复处理。

## 局部存储协议适配约束

为避免本功能修改 common 并触发所有消费者重建，局部 adapter 明确依赖**已跟踪的** shared/ircs-common/.../DistributedRuntimeWorkQueue 存储协议。它复用 app.runtime-work-queue.key-prefix（默认 ircs:work:v1）和 event-stream-maxlen；仅接受 aggregation.raw-video / raw_video:*。

Lua 在同一原子操作中校验 taskType/taskId/status/submissionId/ownerId/attempt、未过期 visibleAt 和 inflight score，然后移至 pending、清 owner/deadline、写 dueAt/reason，attemptCycleStart 仅增加 1 抵消本次等待；attempt 始终单调递增，不回退 fencing。此前普通失败预算保留。Worker 的业务上限也使用 attempt-attemptCycleStart；最终普通 fail 仍由共享队列原子判断。延期写入 assessment_wait_scheduled 事件，不计入业务失败指标。

未来修改 DistributedRuntimeWorkQueue key、字段或预算语义时，必须同步审查此 adapter 并运行模块真实 Valkey 合同回归。此耦合为本次范围内的明确例外，没有更改共享接口或 DTO。

## 验证边界

回归源码已覆盖 HTTP 兼容/缺项/故障、管线专门异常穿透、批量提交后调用、索引缓冲、低置信终态、worker 普通失败与等待分流，以及：

- AdultAssessmentTransactionIntegrationTest：IRCS_TEST_POSTGRES_URL / USER / PASSWORD；真实 PostgreSQL 单连接池、维护锁释放、阻塞远端期间编辑和维护、行锁等待后重新核对、内容/来源/绑定/人工锁/删除快照失效。
- DistributedAggregationWorkDeferrerIntegrationTest：IRCS_TEST_KEY_VALUE_PORT；使用已跟踪 DistributedRuntimeWorkQueue 创建和 claim 的真实 Valkey 任务，验证超过 5 轮等待、旧 owner/attempt/submission 隔离、不同 taskType 拒绝、普通 5 次失败仍进 DLQ 并可恢复。

按工作区约定，构建和应用测试只由 GitHub Actions 执行。本地仅做静态 AST 与 diff 检查，不能据此声称运行回归已通过。
