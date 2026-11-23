# V3 空库切换与旧数据清理

2026-11-23，生产 IRCS V3 空库重建。切换使用已验证的 4.1.1 Native 制品。验收点选在维护停止生产写入及应用恢复两个阶段。定时采集保持暂停。

## 数据边界

| 存储 | 清理范围 | 初始化方式 |
| --- | --- | --- |
| PostgreSQL | 旧 `ircs` 与上一代 `ircs_catalog_v3_20261123` 业务库，包括作品、来源、人员、会员、互动、历史任务和审计记录 | 新建 `ircs_catalog_v3_clean_20261123`，以 4.1.1 Liquibase 从空库迁移。仅初始化数据源、停用的定时定义、凭据、系统设置、解析器、磁链供应商和标准字典。任务初始状态为 IDLE，水位、游标和运行统计从空开始。 |
| Elasticsearch | 活动集群的 IRCS 内容与审计索引，以及退役实例的旧数据 | 活动集群按具体索引名称清理，服务启动时创建读投影。保留集群认证和运行所需的系统数据。退役卷写入暂停后清理数据，保留受保护的 PVC。 |
| R2 和应用本地文件 | IRCS 历史封面、头像及其本地副本 | 切换为新封面和头像前缀，按验证过的旧键范围批量删除并复列。保留桶、鉴权和公共域名。 |
| RabbitMQ、Valkey | 两代工作队列、索引任务、延迟重试、租约和读缓存 | 生产切换到新 RabbitMQ vhost，清理两代历史队列及 Valkey 任务数据。服务按原契约重新声明队列和缓存。 |

切换沿用已有数据库服务器、Kubernetes 节点、存储卷保护和代理出口。种子数据在 PostgreSQL Pod 内直接流式传输，保留配置引用的稳定 ID。凭据值和密码从运行环境读取，验收只报告结构、计数和状态。

## 切换顺序和门禁

1. 从已通过 PostgreSQL 迁移、Java 回归、双架构 Native 运行和镜像清单门禁的 4.1.1 镜像初始化新库。核对 4.1.1 迁移标签和待清理范围。
2. 使用 GitOps 将 IRCS 应用及入口缩到零，代理和数据平面保持可用。确认应用 Pod、旧库客户端连接和 RabbitMQ 消费者全部退出。
3. 在停写窗口流式初始化配置并校验采集计划。检查新库中所有业务及审计事实为空；索引、R2、旧本地卷和消息的清理各自核验受控范围。
4. 发布 GitOps 连接目标、已验证镜像、RabbitMQ vhost 和 R2 前缀。保持停写，确认新库、索引、对象和队列的零状态，随后删除两座旧业务库。
5. 恢复原有应用副本，检查 Argo CD、公开 API 和空目录。25 条定时采集计划保持停用。关闭默认任务播种、求片定时派发、目录补抓、榜单同步及自动磁链发现。只在 metadata-worker 停止 TMDB 快照和权威增量导入，让匹配及内容服务继续识别权威实体。回退应用制品时使用新库目标，后续启用采集后由系统重新建立数据。

[4.1.1 GitHub Actions](https://github.com/Prodigalgal/ircs-project-v3/actions/runs/45146888167) [索引删除接口](https://www.elastic.co/docs/api/doc/elasticsearch/operation/operation-indices-delete) [R2 S3 操作](https://developers.cloudflare.com/r2/api/s3/api/)

## 执行证据

- 4.1.1 通过 660 项 Java 验证及 12 个架构镜像和 Native 运行门禁。GitOps 引用对应的 6 个不可变组件镜像。新库记录 197 个 Liquibase changeset，生产应用角色 `ircs_catalog_v3` 通过读写权限和迁移锁校验。PostgreSQL 的服务探针连接系统库 `postgres`，应用连接目标为新目录库。 [镜像发布](https://github.com/Prodigalgal/ircs-project-v3/actions/runs/45157046515) [探针切换](https://github.com/Prodigalgal/k8s-gitops-config/commit/401f4425a87e37ed9507564bd6d3f1c489bfa9f7)
- 停写期间验证两个旧库客户端连接为 0。已删除旧库 `ircs` 和 `ircs_catalog_v3_20261123`。新库所有业务事实表为空，TMDB 的两个预置快照状态各为 0 行，水位和租约为空。初始化 25 个数据源及 25 条停用的定时计划、12 条凭据配置、5 个磁链供应商、4 个解析器和 587 项词典。三个动态采集开关已设为关闭。
- Elasticsearch 删除内容、原始来源和审计共 3 个 IRCS 索引，清理前审计索引计数为 19,259，清理后应用索引数为 0。活动 Valkey 同步清空 84,993 个键并持久化。退役搜索、退役缓存、退役封面和在用封面本地卷清理后各为 0 个业务目录项。清理用 Pod 已删除。
- 旧 RabbitMQ vhost 删除 68 个队列及其中 448 条消息。默认 vhost 清除 8,477 条历史消息，复查积压为 0。新 vhost 在恢复服务前队列为 0，随后由应用重新声明。
- R2 在前一轮清理 39,133 个旧封面，本轮再清除上一代目录的 4,562 个对象。整桶复列为 0，图像旧地址的公网抽查返回 404。新封面和头像使用不同的存储前缀供日后采集写入。
- 现有 Cloudflare 凭据执行限定前缀清理请求收到 401。维护窗口测试首页、旧详情均走公网源站返回 503，旧图返回 404。Worker 的 `cache.match` 和 `cache.put` 按 TTL 淘汰旧内容。 [Cloudflare Cache API](https://developers.cloudflare.com/workers/runtime-apis/cache/)
- GitOps 使用 `e1a7bef` 恢复服务部署，采集开关独立于副本数。 [恢复提交](https://github.com/Prodigalgal/k8s-gitops-config/commit/e1a7bef8f90a34962571a52aef341e64584b65c9)
- 服务恢复后，HuaWai、后台和首页 API 返回 200，首页各影片区合计 0 条。旧详情及旧 R2 对象抽查均返回 404，复列 R2 仍为 0。Elasticsearch 重新声明的两张内容索引均为 0 文档，新的审计索引开始记录本次启动后的操作。RabbitMQ 新 vhost 已声明 68 个队列，29 个消费者，积压 0。三个门户前端副本分别落在三个节点并就绪。
- 18:48（UTC+8）最终验收时，Argo CD 的 core 和 edge 均为 Synced / Healthy，26 个部署全部就绪。metadata-worker 启动 128 秒后复查作品、来源、封面、人物关系、会员、权威库存和补抓请求均为 0 条。25 条定时任务仍全部停用，最近执行时间为空。
