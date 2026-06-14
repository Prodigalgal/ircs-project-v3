# 028-20 post-parity runtime platform breakdown

状态：进行中，主实现已大幅收敛

归档原文：

- `docs/reports/archive/2026-06-13-doc-compaction/028-20-post-parity-runtime-platform-breakdown-full-log.md`

## 当前结论

028-20 是 V1/V3 行为级迁移后的生产运行时平台补齐任务。已完成的核心能力包括：

- async logger / Disruptor 全业务启用。
- persistent outbox、UUID timeBasedEpoch、V1 mainTitle 语义。
- 统一错误模型、RBAC/data scope、versioned config event、credential/cache governance。
- audit/archive/retention、ops maintenance scheduler、maintenance write gate。
- service identity/scopes 已完成；手动应用层 mTLS 已按用户决策撤销。
- Tika standard package 按服务接回，LLM maintenance runner、storage R2 cleanup/health、
  normalization shared outbound、worker/job observability 已落地。
- L1/L2/L3 多级热读缓存已覆盖 portal、catalog、search、magnet、credential 首批 owner。
- Redis distributed lock / lease / metrics / health 已落地到多服务冲突入口。
- no `@Autowired` 与单构造器风险已清零；Prometheus、Actuator health、shared Redis env 已在
  `ircs-dev` 真实环境验证。

## 剩余任务

1. `028-20-11`：按行为需要继续排查剩余 library follow-up；不按依赖名机械恢复。
2. `028-20-12`：在真实 K8S runner/lock contention 窗口补 worker/job、search outbox relay、
   distributed lock skipped/failed 事件样本。
3. `028-20-13`：继续把多级缓存扩展到剩余安全/业务热读 owner service。
4. `028-20-15`：补 K8S 多副本 Redis lock contention 真实在线测试；若真实 runner 超 TTL，
   再补 renew 或按 runner 拉长 TTL。
5. 统一测试只允许连接 `ircs-dev` 集群和真实外部依赖；禁止本地 smoke、mock/fake、造数验证。

## 当前风险

- 部分完成证据仍是 focused test 或 manifest dry-run，需要在统一 K8S 真实测试窗口补 live evidence。
- 手动 mTLS 已撤销，服务间安全目标态改为 service identity/scopes + 后续基础设施层 mTLS/网格评估。
- 多级缓存必须继续坚持 DB 为唯一真相，缓存事件不能为一致性阻断业务。

