# V3 审计复核与修复候选

复核日期：2026-11-12。对应报告：`136-v3-full-code-audit-security-reliability-concurrency-performance.md`。

## 基线与验收边界

实际源码仓库是项目根 `ircs-project-v3/.git`，修复起点为 `b8501b1`。原报告读取了残留的 `backend/.git`，其分支和 origin/main 描述不代表当前 monorepo。原报告实际列出 4 个 P0、12 个 P1、15 个 P2，共 31 项，摘要计数有误。未修改原始审计。

3.1.0 云端 Java、Python 回归成功，但两个架构的 Native migrator 在 `TagDatabaseChange.setKeepTagOnRollback` 反射访问失败，尚未部署。该版本已占用；后续 3.1.1 两套前端、Python 检查通过，Java 新增 PoW 测试因 Mockito 重载桩不匹配而失败。测试桩已修复；代理池新增能力合并后按 minor 使用 3.2.0，不复用失败版本。全部编译与测试通过 GitHub Actions，本地只做静态检查、清单渲染和生产只读调查。

以下“已实现”不表示已经通过 CI 或部署。当前生产仍为原 V3 Native 镜像组，聚合服务为已单独修复的临时镜像。新版本只有完整云端验证通过后才自动晋级 GitOps。

## 逐项结果

| 条目 | 复核结论与处置 |
| --- | --- |
| P0-1 重复 Bean | 本地旧未跟踪文件残留属实；当前根仓库已跟踪源码只有一套能力实现，干净 Actions checkout 不存在六组重复定义。不通过 Bean override 掩盖问题。本地残留清理受自动审核阻断，未声称已经清理。 |
| P0-2 裸身份头 | 属实。Gateway 独占 Ed25519 私钥，对方法、目标路径/查询、身份和客户端 IP 签名；所有 Java backend API 入站验签，裸头无效。私钥只保存到现有 Secret，没有私钥文件或备份。 |
| P0-3 provider 失败重试 | 属实。原子复位可重试 FAILED，同时扣回上次终态计数；外部请求前捕获 provider attempt，完成时 CAS 隔离迟到结果。显式传递 attempt，不改变公共消息结构。生产只读确认历史 provider FAILED 309 条、raw enrichment FAILED 16 条，不批量重置永久失败。 |
| P0-4 封面轮询索引 | 属实，但当前规模为 43,010 行，并非报告推测的数十万。现行 SQL Seq Scan+Sort、2,289 buffers、13.08ms；新增匹配 CASE 优先级和时间排序的 partial index。尚未创建索引，不能声称性能已改善。 |
| P1-1 watchdog fail-open | 属实。租约缺失或服务失败时跳过本轮，移除本地伪租约。 |
| P1-2 长任务续租 | 属实。新增虚拟线程续租与期限检查，覆盖公共锁、维护 runner 和聚合 match 锁；分页/阶段及事务提交前检查所有权，丢锁中断并失败。协作取消不是数据库 fencing，聚合维护另用数据库锁。 |
| P1-3 task check-then-act | 属实。markQueued 增加 enabled 和原状态 CAS，以受影响行数决定是否派发；手工竞争返回 409，cron 竞争跳过。 |
| P1-4 多副本调度 | 属实。magnet、cover backfill、共享日志保留增加跨副本互斥；cron 由任务状态 CAS 保证同一任务只启动一次。 |
| P1-5 聚合 reset 并发 | 属实。维护事务使用独占 PostgreSQL advisory lock，正常聚合使用同 key 共享锁；相关删除/重置保持事务原子性。 |
| P1-6 CORS | 属实。改可配置精确 origin 白名单并关闭 ambient credentials。 |
| P1-7 backend 直连 | 属实。共享 servlet 边界保护 `/api/v1`、`/api/portal`，匿名请求也必须经 Gateway；媒体、内部令牌及探针路径保留原有用途。配套应用入站 NetworkPolicy。 |
| P1-8 internal fail-open | 属实，实际 10 处，原报告漏 aggregation。未配置 token 返回 503，已配置时继续检查令牌和 scope；旧 require-token=false 不再允许匿名。 |
| P1-9 XFF 限流 | 属实。Gateway 在显式可信代理边界解析地址，剥离外部转发和身份头并签入客户端 IP；identity 仅使用验签地址。 |
| P1-10 SSE query token | 属实。三处 SSE 改 fetch Authorization，保留命名事件、重连、Last-Event-ID、UTF-8 分块和取消；JWT/PAT 服务端不再接受 URL query 凭证。 |
| P1-11 normalization/enrich 索引 | 部分属实。补实际 PENDING watchdog 排序索引；没有报告所提 stale/enrich recovery SQL 读取路径，不添加无使用者索引。 |
| P1-12 孤儿迁移 | 属实，但旧 enrich 轮询已被工作队列替代。新注册迁移使用真实 aggregation runtime 排序索引；不盲目执行陈旧的整份 SQL。 |
| P2-1 路径变体 | 未证明 MVC 路由可利用，原报告也标为条件风险。统一 PoW/限流及 API 边界的 canonical 匹配并增加别名/歧义路径测试。 |
| P2-2 旧 PoW body | 属实。保留正常 scoped header 原子消费；缺 scope、服务端 nonce 和 captcha 的旧 body 方言明确拒绝，返回 pow.legacy.unsupported。当前两前端使用 header 协议。 |
| P2-3 缓存替换 | 属实。MetadataNameCache 合并/替换与 TTL 改单 Lua 原子执行，保留 key、序列化和空哨兵；没有 DEL 与填充之间的可见空窗。 |
| P2-4 Lua 限流解析 | 属实。null/异常结构改明确失败，不静默授予额度。 |
| P2-5 TMDB 无限等待 | 属实。增加累计等待上限及中断处理。 |
| P2-6 邮件限流本地化 | 属实。全局间隔及凭据滑动窗口改共享原子预留，保留突发容量、随机间隔及配额校验。 |
| P2-7 aggregation PROCESSING | 当前运行队列状态与旧 DB 恢复字段不同，不删除兼容状态与修复入口；没有证据证明该字段导致业务退化。 |
| P2-8 normalization retry time | 队列 dueAt 为实际重试时钟；数据库列仍保留观测/兼容意义，不删除或制造第二个调度源。 |
| P2-9 两处回收 | claim 内与 ops 回收确实并存，Lua 原子且有 owner/attempt 隔离；是防止 ops 不可用时停摆的恢复设计，保留。旧文档“单点回收”说法不成立。 |
| P2-10 门户 JSONB | 确有聚合扫描，公共 metadata 已有 5 分钟缓存。生产只读 genre 查询 41.5ms/68 项，试验改写 89.96ms，暂无改写收益证据，保留原查询和权限语义。 |
| P2-11 聚合长事务 | 属实，但 self-call 不会开启嵌套新事务。重算改逐 raw 短事务，消息交接位于返回之后，避免整批长期占用连接。 |
| P2-12 N+1 | 已消除批量删除关联 ID 的逐条查询；候选 external ID 按 100 输入批量召回，各输入仍各取 top20，去重后一次填充 metadata。保留逐对象维护门和不同搜索词回退。只读样例主表扫描 2→1、buffers 4918→2468，不作为吞吐基准。 |
| P2-13 lower(email) | 查询与普通索引不匹配属实；新增非唯一函数索引，不改变现有大小写唯一性契约。生产 members 当前为空。 |
| P2-14 DLQ trgm | 生产 failed_messages 精确为空，且实际 keyword 是五列 OR；缺少建立大 trgm 索引的收益证据，暂不加。历史 Rabbit/运行队列 DLQ 不等于这张表。 |
| P2-15 Hikari | 原疑点不成立：Spring 环境属性优先级有效；两个 worker deployment env 均 max=3，normalization 指标实测 max=3/min=0。metadata 惰性池暂无指标，不声称已验证其初始化值；CI 逐服务检查 max=2 的测试预算。 |

## 变更、兼容与发布

- **安全**：新增公共验签边界、Gateway 签名、可信客户端地址；修复 CORS、PoW 和 10 处内部 fail-open。详见 `../architecture/gateway-identity-boundary.md`。旧 URL token 客户端及旧 PoW body 客户端须切到当前 Authorization/scoped-header 协议。
- **并发/可靠性**：provider attempt CAS、任务状态 CAS、跨副本调度、续租与丢锁处理、聚合数据库互斥和短事务。共享队列 key、消息路由和公共消息结构保持兼容。
- **数据/性能**：新增四个独立 `CREATE INDEX CONCURRENTLY` changeset，均 `runInTransaction:false`，所属业务版本 3.2.1；不删数据、不改变唯一性或已有迁移 checksum。批量读取保持每输入限制和权限维护门。
- **缓存/前端**：MetadataNameCache 原子替换与过期；SSE 不再向 URL 写凭证，重连重新读取当前 token，401 终止、组件卸载取消连接。现有 L1/L2/L3 配置缓存继续使用。
- **Native/发布**：补 TagDatabaseChange 公共反射 hints；统一业务版本 3.2.1，镜像保留日期、版本、用途和源码 SHA。Gateway 升级波次 -5，先签名后升级 backend 验签；网络、公钥引用与镜像在同一 GitOps commit 晋级。

验证入口：全量 Java 测试含真实 PostgreSQL provider retry 与真实 Valkey TTL/缓存/邮件限流；前端 Node SSE 回归、lint/build；两架构 Native migrator 迁移和重复运行、21 服务启动、非法直连/篡改/过期签名拒绝、真实 Gateway→Ops/identity 转发、低堆大队列读、实际入库及去重。以上新增回归执行状态待 CI 填入，不进行本地编译。

生产验收还必须核对 Argo revision、不可变镜像/运行时版本、数据库 tag 和索引有效性、保留数据与 PVC、实际消费/队列和连接池指标。HTTP 200 或 Argo Healthy 不能独自代表完成。

回滚使用上一组不可变 V3 镜像。新增索引可以保留，不需要回退业务数据。若回退到不签名 Gateway，必须先回退验签 backend；只回退 Gateway 会造成 401。私钥不分发到 backend，继续保存在现有 Secret。


## 3.2.0 可选代理池能力

用户追加 Oracle VLESS 和 CF 订阅接入，并授权补装缺少 VLESS 的节点。V3 公共 HTTP 客户端继续使用 HTTP 代理协议，两个私有 ClusterIP 网关负责 VLESS 协议转换；订阅 URL、UUID、REALITY 密钥材料仅进入专用 Secret，不写入仓库。Oracle 既有节点保留身份与服务；新 sydney-arm-01 使用 systemd 原生 Xray，保持 containerd/CNI。

`app.outbound.proxy-pool.enabled` 默认关闭；开启后 scraper 与 metadata PUBLIC_FETCH 在未显式自定义代理时轮询两个网关。GET/HEAD 只在既有重试额度内故障切换，故障网关进入有限冷却；全池失败明确报错，不静默直连。内部服务、Gateway 和 R2 请求保持原路径。

Config 的 PROXY 测试改真实有限时 HTTPS 探测，其他类别仍只进行参数接收但明确返回 NOT_TESTED/dry-run/success=false；管理端只对真实 boolean success 显示成功。运维部署、节点和真实连通性记录统一放在服务器管理工作区，最终以现场测试为准。


3.2.0 云端 Java 全模块编译通过，全量回归剩三项失败。复核发现 checkOwnership 与 close 重抛同一丢锁异常会触发 Java 自抑制错误，已改为独立包装并保留原 cause；另修正测试中过期假租约，并恢复集中虚拟线程工厂约束。修复候选递增为 3.2.1，旧版本不复用。代理网关已在生产集群独立部署，2026-11-13 09:00 北京时间实测 Oracle 7/7、CF 16/16 HTTPS 探测通过，两池均连通公网出口和 TMDB（无API key的401为预期认证响应）；应用 Native 候选尚未部署。
