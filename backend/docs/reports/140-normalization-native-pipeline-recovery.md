# 规范化与元数据 Native 运行阻断、聚合上游恢复

2026-11-14，规范化 3.4.5 与聚合 3.4.7 正常消费；元数据 3.4.8 完成状态修复候选待 GitHub Actions 验证。原规范化积压已清空，剩余补全/聚合个案按原因继续处理。

生产只读统计发现，7,937 条聚合 PENDING 中，7,174 条仍是 normalization=PENDING / enrichment=PENDING；666 条 READY/SUCCESS，71 条 READY/PENDING，26 条 READY/PERMANENT_FAILURE。Ops 队列指标同时记录 normalization DLQ=7,270、inflight=200。规范化日志持续出现 `NoClassDefFoundError: Could not initialize class com.github.houbb.opencc4j.util.ZhJpConverterUtil`，这会使错误逃出 RuntimeException 分支，留下等待超时回收的 claim。以上为当时快照，不能将队列数量与数据库记录逐条等同。

OpenCC 1.14.0 的 [DataMaps](https://github.com/houbb/opencc4j/blob/master/src/main/java/com/github/houbb/opencc4j/support/datamap/impl/DataMaps.java)、[OpenccDatas](https://github.com/houbb/opencc4j/blob/master/src/main/java/com/github/houbb/opencc4j/support/data/impl/OpenccDatas.java) 和 Segments 通过 `Instances.singleton(Class)` 创建字典和分词对象。共享 Native hints 已包含字典资源，但没有该组反射构造器。新增规范化模块专属 hints，避免扩大所有共享模块的镜像构建范围。当前生产错误的修复效果须以实际 Native 文本消费回归确认，不能仅据 JVM 测试或启动 HTTP 200 下结论。

`RawVideoTextNormalizer` 在启动阶段真实执行繁简、日文新字和 ASCII 大小写转换；字典/构造器支持不完整时启动失败，防止一个已就绪实例持续领取无法处理的业务任务。保持原有清洗、标题、别名、锁定字段和来源映射算法；没有降级跳过转换或吞掉底层错误。

Native runtime smoke 新增实际规范化队列任务：隔离 PostgreSQL 插入合成原始记录，在隔离 Valkey 提交该版本任务，等待真实 Native worker 持久化 READY 与清洗结果、确认 SUCCESS，并检查元数据补全及聚合下游队列。原始服务只启动成功而不处理任务不能通过本门禁。CI 会构建变化的 normalization 与 aggregation 两个模块，其余镜像沿用发布账本。

聚合补入队查询同步只选 READY、补全不再 PENDING、标题非空的聚合 PENDING 输入，避免 7,174 条上游未就绪记录占满人工补回批次，先推进已经满足条件的输入。等待中的已有任务仍沿用持久 defer，不删除或重置任何状态。

为停止无效重试，GitOps `e56ac38` 临时将 normalization 副本从 1 改为 0，并加 `normalization-native-repair-hold` 注解；aggregation audit hold 继续保持。未执行全量 reset、删除 DLQ 或清空数据。恢复须在候选门禁通过后移除对应 hold，先观察有界批次的 READY、下游交接、错误率和连接占用，再推进历史积压。回滚使用上一不可变镜像并重新保持消费暂停；原有业务引用与数据保留。

3.4.3 / [43578323293](https://github.com/Prodigalgal/ircs-project-v3/actions/runs/43578323293) 因本轮新增 hints 使用了当前 Spring 不支持的双字符串 `registerPattern` 调用而在编译阶段失败；已拆为单独资源注册。该候选没有进入 Native 构建或生产发布，后续使用新的 3.4.4 版本。

3.4.4 / [43578669133](https://github.com/Prodigalgal/ircs-project-v3/actions/runs/43578669133) 的 355 项应用测试零失败、零跳过，四个 Native 镜像构建成功，两个架构均完成了真实文本转换启动预热；规范化队列任务反复重试使功能门禁超时，自动发布未执行。继续检查发现落库 SQL 的 `:categoryCode is not null` 对空分类缺少参数类型；PostgreSQL 只读 PREPARE 实证会拒绝这种参数。3.4.5 将其显式转换为 text，兼容只有来源分类名称而无代码、分类字典缺项的输入，新增真实 PostgreSQL 小连接池及 Spring 事务代理集成用例。Native 队列门禁保留，并在首次持久失败时报告 lastError，避免只得到最终超时。生产消费 hold 保持，须在完整门禁通过后恢复并观察真实数据。

3.4.5 / [43580835042](https://github.com/Prodigalgal/ircs-project-v3/actions/runs/43580835042) 的 361 项应用测试、双架构 Native 构建和运行、manifest 与自动 GitOps 发布全部成功。镜像引用通过 19290eb 更新；d32beca 先恢复规范化、ff7c0c1 随后恢复聚合，各恢复原有 1 副本。原 7,174 条待规范化记录的首轮运行快照降到 6,984，证明转换与落库恢复，不能等同完整聚合成功。五个 PVC UID/卷名与之前验收记录逐项一致，绑定悬空引用 0。

补全阶段继续暴露旧 metadata Native 镜像的真实错误：无法创建 `ProviderCredentialLease`（缺少反射构造器/属性）。该类型为 Lombok JavaBean，未命中公共 hints 的后缀/record 发现规则。3.4.6 仅在 metadata 的凭据边界使用 `RegisterReflectionForBinding`，并在领取任务前用生产同型的数组 JSON 检查 UUID、时间、限额与 secretPayload 的完整绑定，不执行外部请求，不读取真实密钥。JVM 用例覆盖正常绑定和字段缺失拒绝启动；双架构 Native runtime 的启动门禁实际执行此检查。避免为这一模块的缺陷改动共享 hints 并重建所有服务。

GitOps 6a42eb7 临时将 metadata 从 1 副本改为 0，保留其队列和任务，停止无效重试消耗。规范化正常继续推进；聚合的依赖等待沿用持久 defer，不扣失败次数。50 条历史就绪输入在补入队前检查未有 PENDING/PROCESSING task，再通过现有内部鉴权接口有界入队；未全量 reset 或删除历史 DLQ。补全修复后的实测结果仍须记录。

3.4.6 / [43582998730](https://github.com/Prodigalgal/ircs-project-v3/actions/runs/43582998730) 的 31 套件、99 项 metadata 应用测试零失败/错误/跳过，双架构 Native 构建和启动时完整凭据数组绑定检查、manifest 与自动 GitOps 全部成功。只构建 metadata 镜像；6fce6a4 更新其引用，随后移除 hold 并恢复原 1 副本。恢复前规范化 PENDING 已降至 6,004，聚合 BOUND 从 43,920 增至 43,953；聚合池的等待和超时计数均为 0。下一阶段仍检查真实 provider 调用和 enrichment 状态，启动门禁不能代替这些运行结果。

后续确认原 7,174 条规范化 PENDING 全部成为 READY，无 FAILED。c507806 回收临时规范化副本至原 1；聚合保持 2 副本，每副本主池 3。管理脚本在数据库输入已就绪且至少 2 分钟未变化的条件下，原子补齐 636 条缺失聚合任务；已有 160 条任务不改动，未删除 DLQ 或重置已有 claim。另补齐 71 条缺失补全分发任务，保留已有 12 条；管理补入队有独立审计报告与提交事件，不冒充应用提交速率计数。

存量 83 条 enrichment=PENDING 中，71 条已有完成的持久 run（51 SUCCESS、20 PERMANENT_FAILURE），12 条 run 仍 PENDING、对应 provider runtime task 在 DLQ。分发器对前者直接报“无可分发 provider”导致状态无法收尾。3.4.8 在没有 pending provider 时按 pipeline -> raw 顺序短事务锁定运行与当前输入，复用现有最终状态判定，仅 CAS 更新仍 PENDING 且 hash 一致的状态/重试数，不重写标题、元数据、provider 计数或重复请求供应商。数据库提交后交接搜索和聚合；终态分发重试可补发下游交接。新增真实 Spring 代理/PG 的终态成功、无匹配、版本过期、尚未完成、失败回滚回归，以及分发单测。GitOps d7bebc2 暂停无效分发重试，队列完整保留；门禁通过后恢复。
