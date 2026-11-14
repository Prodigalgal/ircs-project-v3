# 聚合优化实施与验证记录

**状态：聚合/规范化 3.4.5、元数据 3.4.6 均通过应用测试与双架构 Native 门禁并自动发布。生产已推进规范化和部分聚合状态；3.4.7 候选进一步减少依赖等待时的数据库写入与心跳往返，等待 GitHub Actions 验证。禁止本地应用编译。**

## 目标与边界

依据 [138 号复审方案](138-aggregation-service-optimization-plan.md)，优先阻断歧义候选造成的已有 unified 误合并，补足状态机与队列所有权验证，再做受控性能验证。保留现有 raw/unified 数据模型、稳定 unified ID、绑定关系、内部接口、搜索/门户/收藏/观看历史契约，以及 PENDING/BOUND 的外部含义；新增能力通过现有聚合 worker 与队列渐进接入。

本轮不运行全局聚合 reset，不自动修复或拆分历史作品，不迁移或删除生产绑定、unified、收藏、观看历史、magnet 或索引数据。历史修复须另行提供可审阅的逐条变更集、旧 ID 和用户引用处理策略、前置版本条件及撤销边界。

不把 2026-11-14 的抽样 JSON 当实时数据或标注真值，不提交原始 JSON、真实绑定标识或来源关系。回归使用人工构造记录，以及已经去除来源和业务标识的历史错误模式；这些用例不能替代人工标注抽样，不能据此声称生产准确率。

## 依据与夹具设计

- 138 号第 3 节和 `aggregation-sample-2026-11-14.json` 仅提供旧快照统计与定向坏例线索：总体计数为采样时点汇总，不能估计总体误合并率；异常簇明细也不是随机验收集。
- 匹配、图聚类、服务和 worker 测试覆盖 placeholder ID、体育事件区分、图桥接、候选歧义、锁竞争和队列结果；本轮回归以合成标题/字段和显式预期替换生产样本，不提交原始作品名称、生产 ID 或原始样本导出。
- 夹具使用 `SplittableRandom` 或等价 PRNG 固定 seed，生成的 ID 从 seed 派生；用独立 `workLabel` 表示真值，不从 matcher 分数自动生成标签。正例、负例、边界例显式人工标注并提交为小型 Java fixture，不提交生产 JSON。
- 固定 seed 负责可重现的批量排列、候选顺序与并发时序；真值来自人工标注。调参集与验收集按作品标签拆分，避免同一作品/别名跨集合泄漏。合成定向回归与随机分层评测分开报告。

合成用例至少覆盖：可信与拒绝 ID 并存；同名异作和同作别名；同系列不同季及未知/不适用季；缺元数据桥接和中心高分但成员冲突的三节点图；单 raw 命中多个 unified；批量计划新增图外候选或旧绑定；体育同日多赛及合集/单场；短剧缺演员、演员别名与名单不全；候选截断、年份/别名召回；实际锁目标变化、租约丢失与 queue CAS 失败。每个负例的 `workLabel` 不同，每个正例共享标签。

## 实施顺序

1. **匹配安全：** 统一外部 ID 有效性、标题/别名键和领域身份约束；区分 raw 绑定、unified 刷新与 unified 间合并。只允许最终计划中证据充分且互相兼容的 unified 合并，完整校验成员、受影响目标和输入版本。
2. **状态与队列：** 区分普通同版本重复消息、新版本合法刷新和显式强制重算。BOUND 同版本重复不得重复改字段或产生新副作用；新数据版本可以刷新既有绑定；强制重算必须能处理真实 BOUND raw。依赖未就绪和锁竞争按等待语义延期，不消耗失败额度；确认 complete/defer/fail 均受当前 claim 所有权约束。
3. **提交保护：** 外部候选搜索在事务外限时完成；写事务重读 raw 输入版本、绑定、规则版本和最终目标。目标集合变化时重建并按稳定顺序加锁，禁止对旧计划直接追加目标。DB 提交与下游发布分别可观测、可重试。
4. **纠错准备与性能：** 仅设计和验证局部纠错所需的证据与变更集；本轮不对历史数据执行修复。为候选数量、批次大小、HTTP 超时、事务和锁等待设置显式上限，并用有限 fixture 与实际查询计划检查成本随输入规模有界。

## 验收门槛

### 身份与合并

- 人工标注的所有负例在单条匹配、图聚类、批量最终计划和 victim merge 路径中均为 **0 次错误绑定或错误 unified 合并**；任何一个误合并即阻断。
- 所有固定正例按标注预期绑定；可信 ID 遇到脏字段仍可成功匹配，脏 ID 本身不能触发合并。测试必须断言最终受影响 unified 集合，不只断言分数或候选排序。
- 候选截断采用每条 raw 的独立上限，不得用全批次 LIMIT 丢弃输入候选；候选列表超限、锁 key 超限和版本变化均有显式结果及回归断言。
- 独立人工验收样本与定向回归集分开。若对总体自动绑定精度宣称 ≥98%，需报告单侧 95% 精确置信下限 ≥98%；在零错误假设下至少 149 个独立判断，簇内相关记录不得当独立样本。按来源/内容类型分层公布分母和区间；不以旧快照或 distinct-key 比率代替。

### 状态与失败恢复

- **普通重复：** 同 raw、同处理版本、BOUND 重复投递无重复绑定、合并、字段副作用或重复下游事件；当前 owner 完成消息，失去 owner 的旧 claim 不记成功。
- **合法刷新：** raw 新版本到达时保留既有绑定并刷新字段/流水线；即使已有绑定，也不创建重复 unified。输入版本未变化的重复与新版本刷新必须由不同断言区分。
- **强制重算：** 对真实 BOUND 状态执行，能完成所需重算并保持绑定；失败则标 dirty 且不谎报成功。不能只用 PENDING fixture 代表强制重算。
- **等待与 CAS：** 前置 normalization/enrichment 未就绪及 match lock busy 均延期或有等价、可验证的重新投递保证，失败尝试数不增加；依赖就绪/锁释放后可再次 claim 并完成。complete/defer/fail 的 CAS 拒绝、租约丢失、旧 owner 迟到提交均不得覆盖新 owner 或记成功审计。
- adult assessment wait、真实处理异常、最终不可重试失败分别验证 defer、重试和 DLQ 结果；等待应计入时长与告警，不与执行失败混算。

### 数据与性能

- 本轮测试不得触发 reset、历史拆簇或生产数据写入。victim merge 负例需断言收藏/观看历史引用未被误迁移或删除；历史修复在本轮仅要求输出预审计划，不执行。
- 每个 raw 候选数有固定上限（保留当前逐输入 top-20 语义，若实现调整须同步契约与测试）；processing chunk 与 match-key 数量有配置/代码上限。下游 HTTP 有连接与总超时，数据库事务不覆盖网络等待。
- 在相同 CI runner、固定输入规模和数据库 fixture 上记录查询数、最大候选数、事务时长及 claim→ack 时长；超出配置的硬上限或出现随 batch×candidate 无界增长即失败。生产 p95/p99 门槛在基线采集后另行定值，不从旧快照推算。

### GHA 与运行验证

- 聚合 worker 源码/测试变更应由 affected-plan 选择 `services:ircs-aggregation-worker:test` 及 `ircs-aggregation-worker` 镜像；Gradle 可编译其传递依赖，但不因此构建无关服务镜像或运行全仓应用测试。共享模块变更时仅按 affected-plan 依赖图扩大测试/镜像集合。
- 受影响模块测试必须通过；聚合代码触发的 native build 仅生成聚合 worker 的受影响镜像，并通过现有双架构 native runtime smoke。CI 计划 JSON、测试报告和镜像摘要留作证据；这只证明候选通过 CI，不表示已发布或生产验收。
- 生产恢复/部署另按 138 号第 8.3 节核对真实 revision、镜像摘要、配置、队列状态、绑定与下游引用；HTTP 200 或 Argo Healthy 不能单独作为完成条件。

## CI 选择规则核对

`.github/scripts/affected-plan.py` 按模块源文件计算服务消费者并把受影响模块加入测试矩阵；模块测试文件仅触发对应测试，不触发镜像构建。`backend/scripts/ci/native-matrix.py` 将 image 映射到模块测试任务；`.github/workflows/native-build.yml` 中 Java 测试任务按矩阵执行，受影响 Java 镜像再进入 native build 和 runtime smoke。3.4.2 实证只构建聚合 worker，3.4.5 选择聚合与规范化两个模块；前端和 Python 的元数据提升没有触发无关构建。shared/common、messaging、Gradle 或 native Docker recipe 的实际变更仍按依赖图扩大范围。

## 名称与报告交叉核对

138 号第 521 行关于部分测试仍使用 `new AggregationMatchingStrategy()` 无参构造器的描述已过时：当前可见聚合测试改由 `AggregationTestFixtures.matchingStrategy()` 构造，后续应由报告维护者修正；本计划不修改 138 号。

138 号引用的 `RedisRuntimeWorkQueue` 是本地 untracked 历史残留；`git ls-files` 确认当前受版本控制的实现是 `DistributedRuntimeWorkQueue`。聚合专属 defer/renew adapter 对齐该实现的 CAS 协议；本记录不覆盖用户的 138 号原文。

## 完成状态

已完成以下代码改造，应用测试与只读评估见后文；真实消费吞吐和业务准确率仍需分别验证：

- 身份安全：单条和批量都复核完整拟合并集合，包含 victim 之间的冲突、候选缺失和正向证据；歧义不再任取最小 UUID，也不自动新建重复作品。自动合并既有作品默认关闭；普通 raw 绑定继续支持，冲突簇拆回独立绑定。
- 清洗与匹配：通用别名/副标题在清洗前后均过滤；provider 明确的占位及拒绝 ID 不参与召回、创建或流水线填充。发行年差异统一允许有效四位年份 ±1；未知年不是身份证明，完整簇校验阻止弱桥。未知季不等于 S1，可能增加待核验或碎片，未批量重绑旧数据。
- 图计算：每个图最多 5 个 raw；每 raw 的 context 召回最多 20，外层批次最多 100。固定大小、请求内 SimilaritySession 一次准备每个节点，边权复用；不新增跨请求无界缓存。
- 召回与缓存：四类 provider context 查询、空外部结果/空搜索结果回退、同请求标题年份去重、召回合并后只填充一次缓存元数据。最终 raw 从同一次行查询读取平面元数据，已锁目标直接读取新鲜平面字段并重新评分；关键写入复核不依赖 L2 旧值。元数据缓存失效移到事务提交后。
- 事务与网络：搜索召回、图计算和成人远端调用在数据库事务外。写入按小簇事务，30 秒预算、锁等待 2 秒，原始输入加锁重读、写前/提交前验证 claim 与 lease；成人反向 raw/source 锁采用 NOWAIT 退让。搜索使用专属共享 HttpClient，连接默认 2 秒、请求默认 3 秒，配置限制在 100 毫秒至 30 秒。
- 重算：显式作品刷新保持已有成员，一次运行完整流水线，不再每来源重复执行；重复 BOUND 不降级，不重跑整条流水线，仍保留成人待判断的续跑路径。
- 队列：依赖/竞争/源变化/快照变化/人工核验持久延期，不计供应商失败预算；默认依赖 30 秒、竞争 2 秒、核验 1 小时。严格 submission/owner/attempt/期限 CAS，整批未终结任务续期；事务内 ownership 检查不等待续期网络。
- 只读评估：内部鉴权保护的 `POST /internal/v1/aggregation/evaluate` 接收 1..100 个 UUID，去重后返回旧绑定、拟 root/victims、决策和原因。明确标注 `SNAPSHOT_ONLY`；没有批量图和提交授权，不执行写入、成人判断、索引或队列。

配置、DTO、并发和 Native HTTP 回归已经加入候选。CI 脚本静态单测、版本契约和 workflow lint 在本地通过；应用测试与 Native 验证仅在 GitHub Actions 执行。最新待验证候选为 aggregation 专属优化 3.4.7，其他已验收镜像继续复用，不因元数据提升重建无关组件。

未创建数据库迁移、未全量 reset、未自动拆修历史污染作品。尚无权威 ID/来源信任账本；真实误聚率、召回率、吞吐和连接占用变化均需标注样本与运行证据。回滚使用上一不可变镜像并保留 audit hold；切换 worker 前暂停消费，避免不同锁键格式滚动混跑。

## CI 证据

- 3.4.0 / `fdd6ea1` / [43573590114](https://github.com/Prodigalgal/ircs-project-v3/actions/runs/43573590114)：生产和测试代码编译通过，262 项应用测试中 4 项失败，Native 和发布被阻断。两项为旧召回契约/批次 mock 未同步；两项发现外部 ID 填充条件的 `NOT` 缺少完整括号，导致空值/无效值无法填充。已修复 SQL，并增加真实 PostgreSQL 的空值/占位值/拒绝值与 provider 隔离回归。
- CI 脚本静态单测 76 项、版本契约与 workflow lint 通过；这些测试不会启动本地应用或编译 Java。
- 3.4.1 / `2f3c160` / [43574037667](https://github.com/Prodigalgal/ircs-project-v3/actions/runs/43574037667)：29 套件、267 项应用测试，零失败/错误/跳过；26 项真实 PostgreSQL 事务用例、12 项真实 Valkey defer/renew 用例通过。amd64/arm64 Native 构建与两架构真实 HTTP runtime smoke、multiarch manifest 全通过。自动 GitOps 任务因依赖链存在跳过矩阵而被跳过，未按成功构建冒充已部署。
- 3.4.1 生产只读评估：固定 seed 各抽 100 条 BOUND/PENDING，耗时分别 3.609/2.344 秒。BOUND 为 93 兼容、7 身份冲突线索；PENDING 为 91 等待规范化/补全、8 旧绑定兼容、1 冲突线索。没有人工真值标签，不能把这些比例称作准确率或误聚率，也不能将接口评估耗时等同消费吞吐。前后 raw/unified/binding 为 51857/16272/45230，无变化，评估数据库会话无 idle-in-transaction；临时 Job/转发已清理。证据与可重跑脚本保存于服务器管理目录。

## 3.4.2 收尾

- 编排入口取消 `NEVER` 事务注解，用显式入口校验拒绝实际事务和空 synchronization 上下文，再按短 TransactionTemplate 写入。Spring 的空 synchronization 也可能绑定 JdbcTemplate 连接，单纯检查 `isActualTransactionActive=false` 不足以证明连接已释放。PostgreSQL 单连接池测试统一经过真实 Spring TransactionInterceptor/CGLIB 代理，覆盖准备查询、成人远端等待与重试。
- 已有绑定刷新对缺失季字段采用“缺少证据”处理，明确 S1/S2 冲突仍拒绝；新绑定及作品合并继续严格处理未知季，避免把未知直接当作 S1。只读冲突返回 provider/category/year/season/version/event 等具体维度，便于核验。
- 增量发布条件使用 `always()`、未取消、main、prepare 成功、有选中组件、manifest 成功的组合，处理上游未变模块矩阵跳过导致的传递跳过，同时保留全部成功门禁；语义依据 [GitHub needs 文档](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#jobsjob_idneeds)。静态回归与 3.4.2 实际自动 GitOps 发布均通过。
- 该轮候选独立占用 3.4.2；后续候选同样不复用版本、tag 或制品。

3.4.2 / `628b4f9` / [43576088924](https://github.com/Prodigalgal/ircs-project-v3/actions/runs/43576088924) 共 269 项应用测试零失败、零跳过，双架构 Native 构建和运行、manifest、自动 GitOps 发布全部通过。相同 seed 的 200 条生产只读评估中，100 条 BOUND 为 93 兼容、7 冲突线索（类别 3、赛事 3、TMDB ID 1），耗时 3.031 秒；100 条 PENDING 为 91 等待依赖、8 已有绑定兼容、1 赛事冲突，耗时 2.172 秒。评估前后业务行数不变，数据库会话 1 / idle-in-transaction 0；临时 Job 与转发已清理。两次评估的缓存、节点状态未受控，不能据耗时差计算优化百分比。

3.4.5 在 GitHub Actions 的 48 套件、361 项应用测试中零失败、零错误、零跳过，其中新增规范化 PostgreSQL 集成测试 6 项全部通过。现有聚合输入补入队查询在生产只读 EXPLAIN ANALYZE 下返回 100 行，执行约 1.227 毫秒；这是该查询单次耗时，不代表端到端消费吞吐。

[43580835042](https://github.com/Prodigalgal/ircs-project-v3/actions/runs/43580835042) 的四个 Native 镜像、双架构实际文本规范化/READY/SUCCESS/双下游交接、聚合只读 HTTP 门禁、多架构 manifest 与自动 GitOps 全部成功。生产恢复后首次快照中 normalization PENDING 从 7,174 降到 6,984（190 条进入 READY），数据卷身份及业务绑定保持。元数据补全暴露旧镜像的 Native 凭据反序列化问题，不能据规范化成功宣称完整聚合链路已恢复。

## 3.4.7 等待路径成本优化

生产恢复观察确认真实绑定开始推进，但依赖未就绪的任务仍会逐条开启写事务、锁 raw 行并重复写 PENDING；consumer presence 还在解析、处理前后及结果阶段重复执行远端心跳。在跨节点网络延迟下，这些往返使就绪记录的等待时间增加。

- 仅当输入快照已经为 PENDING 且依赖未就绪时，直接返回持久等待结果，不开启写事务、不锁行、不重写更新时间。依赖恰好就绪时，由其正常交接或已有持久 defer 再处理；该路径没有业务写入。PROCESSING 转回 PENDING、已有 BOUND 的成人续跑及所有实际绑定路径保留原校验。
- consumer presence 使用单调时钟和原子截止时间合并为每 15 秒至多一次；最小 presence TTL 为 visibility 加 1 分钟，持续消费及 claim 续期钩子仍会刷新。只合并存在状态心跳，不改变任务租约续期、CAS 或提交前所有权检查。
- 新增 25 个等待任务下的心跳调用上限、截止时间到期刷新、无写事务/锁/副作用的服务用例，以及真实 PostgreSQL 行 xmin/xmax/更新时间不变回归。原 PROCESSING 等待与 BOUND 续跑回归保留。

候选只修改 aggregation 模块；实际吞吐收益须以该候选通过门禁并发布后的运行状态确认，不能将调用数减少直接称作生产提速百分比。
