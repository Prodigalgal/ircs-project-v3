# 规范化 Native 运行阻断与聚合上游恢复

2026-11-14，3.4.5 候选，待 GitHub Actions 验证。本文记录代码改造和运行证据，不代表历史任务已经恢复。

生产只读统计发现，7,937 条聚合 PENDING 中，7,174 条仍是 normalization=PENDING / enrichment=PENDING；666 条 READY/SUCCESS，71 条 READY/PENDING，26 条 READY/PERMANENT_FAILURE。Ops 队列指标同时记录 normalization DLQ=7,270、inflight=200。规范化日志持续出现 `NoClassDefFoundError: Could not initialize class com.github.houbb.opencc4j.util.ZhJpConverterUtil`，这会使错误逃出 RuntimeException 分支，留下等待超时回收的 claim。以上为当时快照，不能将队列数量与数据库记录逐条等同。

OpenCC 1.14.0 的 [DataMaps](https://github.com/houbb/opencc4j/blob/master/src/main/java/com/github/houbb/opencc4j/support/datamap/impl/DataMaps.java)、[OpenccDatas](https://github.com/houbb/opencc4j/blob/master/src/main/java/com/github/houbb/opencc4j/support/data/impl/OpenccDatas.java) 和 Segments 通过 `Instances.singleton(Class)` 创建字典和分词对象。共享 Native hints 已包含字典资源，但没有该组反射构造器。新增规范化模块专属 hints，避免扩大所有共享模块的镜像构建范围。当前生产错误的修复效果须以实际 Native 文本消费回归确认，不能仅据 JVM 测试或启动 HTTP 200 下结论。

`RawVideoTextNormalizer` 在启动阶段真实执行繁简、日文新字和 ASCII 大小写转换；字典/构造器支持不完整时启动失败，防止一个已就绪实例持续领取无法处理的业务任务。保持原有清洗、标题、别名、锁定字段和来源映射算法；没有降级跳过转换或吞掉底层错误。

Native runtime smoke 新增实际规范化队列任务：隔离 PostgreSQL 插入合成原始记录，在隔离 Valkey 提交该版本任务，等待真实 Native worker 持久化 READY 与清洗结果、确认 SUCCESS，并检查元数据补全及聚合下游队列。原始服务只启动成功而不处理任务不能通过本门禁。CI 会构建变化的 normalization 与 aggregation 两个模块，其余镜像沿用发布账本。

聚合补入队查询同步只选 READY、补全不再 PENDING、标题非空的聚合 PENDING 输入，避免 7,174 条上游未就绪记录占满人工补回批次，先推进已经满足条件的输入。等待中的已有任务仍沿用持久 defer，不删除或重置任何状态。

为停止无效重试，GitOps `e56ac38` 临时将 normalization 副本从 1 改为 0，并加 `normalization-native-repair-hold` 注解；aggregation audit hold 继续保持。未执行全量 reset、删除 DLQ 或清空数据。恢复须在候选门禁通过后移除对应 hold，先观察有界批次的 READY、下游交接、错误率和连接占用，再推进历史积压。回滚使用上一不可变镜像并重新保持消费暂停；原有业务引用与数据保留。

3.4.3 / [43578323293](https://github.com/Prodigalgal/ircs-project-v3/actions/runs/43578323293) 因本轮新增 hints 使用了当前 Spring 不支持的双字符串 `registerPattern` 调用而在编译阶段失败；已拆为单独资源注册。该候选没有进入 Native 构建或生产发布，后续使用新的 3.4.4 版本。

3.4.4 / [43578669133](https://github.com/Prodigalgal/ircs-project-v3/actions/runs/43578669133) 的 355 项应用测试零失败、零跳过，四个 Native 镜像构建成功，两个架构均完成了真实文本转换启动预热；规范化队列任务反复重试使功能门禁超时，自动发布未执行。继续检查发现落库 SQL 的 `:categoryCode is not null` 对空分类缺少参数类型；PostgreSQL 只读 PREPARE 实证会拒绝这种参数。3.4.5 将其显式转换为 text，兼容只有来源分类名称而无代码、分类字典缺项的输入，新增真实 PostgreSQL 小连接池及 Spring 事务代理集成用例。Native 队列门禁保留，并在首次持久失败时报告 lastError，避免只得到最终超时。生产消费 hold 保持，须在完整门禁通过后恢复并观察真实数据。
