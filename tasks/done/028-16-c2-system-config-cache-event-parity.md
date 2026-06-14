# 028-16-C2 SystemConfig cache/event parity

状态：已完成

归档原文：

- `docs/reports/archive/2026-06-13-doc-compaction/028-16-c2-system-config-cache-event-parity-full-log.md`

## 当前结论

C2 已完成 V1 SystemConfig 语义迁移与 V3 配置事件治理：

- V1 默认 seed 保留为最低优先级兜底。
- ENV、K8S Secret、K8S ConfigMap、configtree、启动参数、Spring Environment 注入等运行时来源
  高于 DB 和默认 seed。
- config-service 支持 runtime effective view、runtime source guard 和变更事件发布。
- content/config listener、Rabbit publish/deliver/ack、content DB fallback probe 已有真实链路证据。
- Rabbit outage / missed-message 的策略已拍板为 versioned event + TTL/refetch 兜底，不为一致性阻断业务。

## 维护边界

1. 新配置项必须先声明优先级和来源，不得让默认 seed 覆盖环境注入。
2. 服务热读可使用 L1/L2/L3 cache，但 DB 仍是最终兜底和唯一真相。
3. 配置变更需要发布 versioned event；消费者必须能通过 revision/eventId 去重和按需 refetch。
4. 后续验证只能走 `ircs-dev` 真实 Rabbit/config-service 链路；不再使用本地 smoke 作为完成证据。

