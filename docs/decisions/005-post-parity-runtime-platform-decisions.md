# 005 Post-Parity Runtime Platform Decisions

状态：已拍板，分批实现

日期：2026-06-10

## 背景

V1 到 V3 的 HTTP route missing 已保持为 0，后续完成标准转向行为级 1:1 迁移。
本 ADR 固化 2026-06-10 过会确认的“非测试债务”决策：这些事项不是多跑 smoke 就能关闭，
而是需要独立实现、数据契约或运行时治理。

## 决策

| 编号 | 主题 | 结论 |
| --- | --- | --- |
| D01 | AsyncLogger / Disruptor | 全业务服务 `services:*` 启用，`platform/ircs-migrator` 暂不启用。 |
| D02 | search outbox | 补回 V1 持久 `search_sync_tasks` 模型；search-service 拥有 relay/reconciliation，Rabbit consumer 作为执行端。 |
| D03 | readiness start | 不恢复全服务统一 auto start；保留显式 gate，补 per-service readiness orchestrator。 |
| D04 | UUID | 全量切回 V1 `timeBasedEpoch` 生成策略；历史 UUID 不迁移、不重写。 |
| D05 | title 语义 | 切回 V1 `mainTitle` 语义，同时补 search variants 与 portal/admin display 兼容。 |
| D06 | Tika | 接回 `tika-parsers-standard-package`，只给需要文件解析的服务接入。 |
| D07 | LLM cleaning | 接入真实 runner，但默认关闭；dev/staging/prod 均可通过强 gate 按需启用。 |
| D08 | cache governance | 做全服务统一缓存治理层，但不强制所有缓存预热；按数据热度和一致性要求选择本地、Redis/Valkey、owner-service materialized cache 等多级缓存。 |
| D09 | V1 依赖 | 不按依赖名机械接回；每个微服务按 V1 行为差异逐项判断。 |
| D10 | runner governor | 继续补全 V1 runner，统一 production governor、dry-run 默认、audit；execute gate 保留且默认开启。 |
| D11 | admin bootstrap | 支持 K8S Secret / ENV bootstrap admin，优先 password hash，已有 admin 默认不覆盖。 |
| D12 | registration | 保留公开注册；默认强制邮箱验证，dev 可关闭；支持第三方登录/绑定，provider 默认关闭。 |
| D13 | provider identity | 同一用户可绑定多个 provider；`provider + providerSubject` 全局唯一；同邮箱不自动静默合并。 |
| D14 | token/session | access token + refresh token + server-side session/revocation 表。 |
| D15 | service auth | 已被 `docs/decisions/007-manual-mtls-removal.md` 修订：不保留手动应用层 mTLS；服务间回到集群内 HTTP，继续保留 internal service token、service identity 和 scopes；旧共享 admin/internal token 仍不兼容。 |
| D16 | RBAC/scope | 统一 RBAC / permission code；行为即权限；业务服务强制 data scope，BFF/UI 只前置裁剪。 |
| D17 | content visibility | 引入 `visibility`、`contentRating`、data scope policy。 |
| D18 | audit model | 审计升级为 security / behavior / system 三类；可信源在 DB，ES 只做搜索分析副本。 |
| D19 | view audit | 目前只做业务行为审计，不额外审计每次 view。 |
| D20 | recommendation | 暂不做推荐系统。 |
| D21 | search ranking | 先保持基础检索，不引入复杂权重或个性化。 |
| D22 | error model | 对外对内统一错误模型；portal/admin 也适配新错误模型。 |
| D23 | media storage | 保留本地文件 + R2 双轨；R2 public object 为 canonical，local 用作缓存/临时源/dev fallback。 |
| D24 | async audit coverage | worker/job audit 覆盖所有异步执行，不分级落地。 |
| D25 | audit retention | DB retention：security 180 天，system/behavior/worker/send history 90 天，request 30 天；ES 副本 30-60 天。 |
| D26 | trace propagation | 强制全链路传播 `traceId + correlationId`。 |
| D27 | deletion/archive | 分级删除；核心实体软删除，流水/审计/任务进统一归档表，可重建数据 retention purge。 |
| D28 | maintenance scheduler | 统一 scheduler 先放 `ops-service`；默认调用 owner service 执行，不默认创建 K8S Job。 |
| D29 | hot governance cache | gate 等热门治理数据采用 L1 service local + L2 Redis/Valkey + L3 DB 兜底；DB 是唯一真相。安全相关 gate 不缓存 allow，只缓存短 TTL blocked 判定并由版本化事件失效。 |
| D30 | multi-tier business cache | 大部分读多写少业务数据允许 L1/L2/L3 多级缓存以减少 IO；DB 仍是唯一真相，service 本地/物化缓存只能作为可失效、可重建的读模型。权限、gate、credential 等安全热数据必须短 TTL + 版本事件失效 + DB fallback。 |
| D31 | ops observability | worker/outbox/runner 运行态必须能定位到具体副本；workerId 不得只使用 `spring.application.name`，优先使用显式 ENV/Pod 名称，兜底 `application@host#pid`。关键 outbox relay 进入 `/actuator/health`，Micrometer 覆盖任务延迟、run duration、成功/失败计数和失败率分析所需标签。 |
| D32 | distributed lock policy | 所有可能跨副本冲突或需要限流的服务入口应按行为风险接入统一分布式锁抽象；singleton runner/seed/watchdog/cleanup 用 Redis mutex lease，源站/provider 抓取用时间片预占用，凭据/API 配额用令牌桶。DB 幂等、唯一约束、row lease 和消息 ack 仍是最终兜底，不用 Redis lock 机械包裹所有 Rabbit consumer。 |

## 非目标

- 不把这些决策一次性实现完。
- 不因为“依赖一致”而全服务机械加 V1 历史依赖。
- 不把 production-like live smoke 结果写成本 ADR 的完成标准；每个实现切片单独验收。

## 实现顺序

优先级以行为影响、横切风险和独立可验证程度排序：

1. `028-20-01` AsyncLogger / Disruptor runtime parity。
2. `028-20-02` search persistent outbox relay/reconciliation。
3. `028-20-03` UUID timeBasedEpoch adoption。
4. `028-20-04` title mainTitle + search/display compatibility。
5. `028-20-05` unified error model + portal/admin adaptation。
6. `028-20-06` RBAC / permission / data scope / visibility。
7. `028-20-07` config/credential/cache versioned governance.
8. `028-20-08` audit/archive/retention governance.
9. `028-20-09` ops-service maintenance scheduler.
10. `028-20-10` mTLS service identity destructive migration.
11. `028-20-11` Tika/LLM/media/library behavior follow-up.
12. `028-20-12` worker/outbox ops observability enhancement.
13. `028-20-13` multi-tier business cache/read-model rollout.
14. `028-20-15` distributed lock manager multi-service rollout.

## 风险

- 多个决策会改写跨服务契约，必须避免在一个切片里同时改变错误模型、权限、title 和 search outbox。
- UUID 全量切换仅影响新增写入，历史数据混用 random/time-based 是预期状态。
- mTLS、RBAC/data scope 和统一错误模型会影响 BFF、portal/admin、服务间调用和测试夹具，应独立 staging；旧 service/admin token 失效是预期破坏性结果。
- `execute gate` 默认开启意味着管理员可真实执行危险 runner，必须依赖 RBAC、dry-run 可见性和 audit 做运行时治理。
- gate/cache 热路径必须避免让 Redis 成为事实源；Redis miss、坏值或不可用时必须回落 DB，且不能因为缓存层故障阻断业务判定。
- 多级缓存不能把 service-local 读模型提升为事实源；跨副本一致性依赖 versioned event、TTL 和 DB fallback，不为强一致性阻断业务读写。
- workerId 进入 DB lock、health detail 和 metrics tag 时要控制长度与标签基数；副本级标签只允许来自 Pod/进程粒度，不能拼接实体 ID、URL、payload 等高基数字段。
- 分布式锁不能替代数据层约束；Redis 不可用时，已启用锁的高风险 singleton/runner/限流入口应 fail closed 或 skipped，并暴露 health/audit/metrics 信号。长任务必须配置足够 TTL，后续如出现超过 TTL 的真实 runner 再补 lock renewal。

## 验收口径

每个子任务必须记录：

- 影响服务和数据契约。
- 默认开关和 ENV/K8S override。
- focused tests 或 build evidence。
- 是否需要 live/K8S/外部 smoke。
- docs/tasks 入账状态。
