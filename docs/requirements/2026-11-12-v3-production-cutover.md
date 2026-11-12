# V3 单仓库、云端 Native CI/CD 与生产切换

## 用户最新授权

- 所有编译改在 GitHub Actions，停止本地编译。
- 沿用 `Prodigalgal/ircs-project-v3`，整合当前 Java、Python、管理端和门户源代码至单一仓库。
- 生产 Argo CD 继续监听 `Prodigalgal/k8s-gitops-config`，改用 V3，打通构建、镜像、GitOps 回写及同步链路，下架现有 V4 工作负载。
- 必须保留生产数据、PVC、数据库和必需运行配置；评估 V3/V4 schema 兼容性后直接对齐，禁止通过重建空库、清空队列或删除 PVC 完成切换。
- 用户后续明确不做旧 Git 备份和数据备份，直接对齐对接，需要扩容则先扩容。当前版本整合并验证后删除三个唯一旧仓库：`ircs-huawai-frontend`、`ircs-admin-frontend`、`ircs-backend`，不创建 Git mirror/bundle。
- 三节点 K8S 尽量均衡：结合已有 CPU/内存占用，分散业务和 worker，数据卷保持可用。
- 自有代码类和文件按队列、缓存、租约等职责命名，消除 Redis/Valkey/Rabbit 前缀；应用层依赖现有接口。命名调整不改变已有数据键、字段、旧 API 和消息契约。

## 范围与验收

1. 单仓库云端 Java、Python、前端回归及 amd64/arm64 Native/容器构建通过，使用固定提交标签。
2. GitHub Actions 对 GitOps 仓库使用专用最小写入权限，主分支仅在检查全部通过后回写镜像。
3. PVC 保留策略有效，数据迁移对现有 schema 和业务数据兼容，沿用现有数据库。
4. 处理现场 Valkey AOF 损坏：先扩容满卷，校验并保留有效命令，修复非法填充，不 FLUSH 或重建空数据目录。
5. V3 部署、真实进程/日志/探针、外部 API/页面、采集队列及 Hikari 状态有证据，V4 停止消费后退出生产入口。
6. Argo CD 状态与 GitOps desired state 一致，旧仓库在无引用且当前源代码已经整合后删除。

## 2026-11-12 只读现场

当前 kube context 为 `kubernetes-admin@sg-osaka-dualstack`，3 个 ARM64 节点，目标 namespace `ircs-prod`。Argo `ircs-v4-runtime` 指向 GitOps 的 `ircs-v4/runtime`；V4 API 和 Valkey 已经在 CrashLoopBackOff。PostgreSQL 数据库约 3277 MB，四个数据 PVC 均 Bound。Valkey 日志报告 `appendonly.aof.269.incr.aof` 格式损坏，按用户后续不备份的要求保留有效命令修复异常填充。以上是本次观察，不替代旧事故日志。

## Native 运行期配置边界

- 消费者 Bean 不再依赖构建期默认关闭的 `@ConditionalOnProperty`；监听容器通过运行期 `autoStartup` 控制。任务快照、watchdog、媒体请求和搜索对账的定时入口使用运行期布尔开关。
- 云端在真实 PostgreSQL、消息代理和键值存储上运行双架构 Native 检查，验证消费者关闭/开启、入库、播放列表消费和下游工作去重，全部通过才发布聚合标签。
- 发布方通过 `MessagePublisher` 接口依赖确认发布能力，具体传输与确认逻辑保留在 `ConfirmedMessagePublisher` 适配器。

## 首次生产 Native 启动检查补充

- Boot 4 的 RestClient 自动配置使用专用 `spring-boot-starter-restclient`，不能仅依赖 Web MVC starter。云端启动验证覆盖全部 21 个 Java 服务，包含真实数据库、消息代理、键值存储、带分词插件的搜索服务及启用 R2 客户端的初始化路径。
- 连接池和消息并发的环境变量使用 Spring 规范形式，例如 `SPRING_DATASOURCE_HIKARI_MAXIMUMPOOLSIZE`、`SPRING_DATASOURCE_HIKARI_CONNECTIONTIMEOUT`。不得把连接参数错误拆为 `connection.*`，导致绑定阶段调用连接 getter 提前启动并锁定 Hikari。Native 检查读取实际连接池上限，不能仅检查 YAML 数字。
- 死信人工重试使用手动确认，重发得到 broker confirm 后才 ACK 原消息；发送失败 NACK/requeue 原消息。人工重放重置本轮有限重试额度并记录次数，保留原 payload，禁止先删原消息再尝试发送。
- Native 资源清单包含搜索索引设置和目录国家/语言预设 JSON。云端使用空索引启动搜索服务，验证首次建索引路径，避免现有生产索引掩盖资源遗漏。剩余服务逐个检查，即使一个服务失败也收集后续启动结果，最终仍整体失败并阻止发布。
- JDK 25 的 Netty 共享内存清理路径需要 Native 编译参数 `-H:+SharedArenaSupport`。生产日志已发现缺少该能力引发的 `UnsupportedFeatureError`，随后出现键值连接响应错位和超时。双架构 CI 在 384 MiB 堆下反复读取 2 MiB 工作记录，断言 pending/inflight/DLQ 实际值，并检查启动、读写及优雅关闭的完整日志；禁止只凭 readiness 放行。参考 [Netty 17172](https://github.com/netty/netty/issues/17172) 和 [GraalVM FFM 支持](https://github.com/oracle/graal/issues/8113)。
- 网关 JWT 配置查询复用既有 L1 本地、L2 Valkey、L3 数据库加载组件；冷未命中的并发读取按 key 合并回源，回填使用 SET NX，避免覆盖写入方较新的共享值。L3 使用现有 DataSource，成功与异常路径均归还连接。环境变量优先级不变。
- JWT 撤销时间绕过 L1，三个网关副本直接读取 L2；事务提交后同步更新共享值，写入未确认不得报告成功。新写入该 key 的远端 TTL 最长 30 秒，发布后将生产既有该 key 的 TTL 收敛到相同上限；缓存不可用时在连接池预算内读取数据库，整个撤销来源不可用则返回 503，不能默认为 0 后放行。普通配置保留 L1 命中和本地空值缓存。增加按层、结果、安全/运行配置分类的低基数缓存指标，不以配置 key 或内容作为指标标签。

## 统一版本与不可变候选

- 本轮统一业务版本为 `3.1.0`，替代 Backend、管理端、门户和 Python 的历史占位版本。新增 Native 交付能力在 V3 架构内保持 API/数据兼容，使用 minor 版本；版本通过根 `VERSION`、NPM/lock、Backend build info、Python service version、WEB `/version.json` 以及 OCI 标签核对。
- 构建前先验证版本契约，并要求不可变 Git tag `v3.1.0` 指向本候选源码。镜像使用 `YYYYMMDD-v3.1.0-production-test.1-<sha12>` 及架构后缀，GitOps 仅引用全量检查后的多架构标签；已有镜像只复用，不覆盖，网络/权限错误不能视作不存在。后续候选改代码或配置必须占用新的三段式业务版本。
- Native migrator 成功后记录业务版本 Liquibase tag，同版本重复执行幂等，保留旧版本标记。迁移 hook 设置 600 秒截止时间和完成回收时间，不能将共享凭据或业务卷挂入临时 Job 的回收链。
