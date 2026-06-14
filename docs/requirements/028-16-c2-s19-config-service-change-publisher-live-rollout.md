# 028-16-C2-S19 config-service change publisher live rollout 需求

状态：已完成

## 背景

C2-S18 已确认 content-service 监听端已经在 dev live 环境中真实上线：
`q.content.config_changed` 存在、`consumers=1`、DLQ 已声明。C2-S17 真实 Rabbit
config event smoke 的新阻塞点收敛到 config-service：live deployment 缺少
`SPRING_RABBITMQ_*` env，live 镜像内缺少 `SystemConfigChangePublisher.class`，因此通过
config-service 写入临时 SystemConfig 后没有 Rabbit publish / deliver / ack 证据。

本轮需要补齐并上线 config-service 的 SystemConfig change publisher，在 `ircs-dev` 中按低资源、
安全方式 rollout config-service，然后重跑 C2-S17 真实 Rabbit config event smoke。

## 目标

- 确认或补齐 config-service 在 SystemConfig 创建、更新、删除后按 V1 替代语义发布
  `config.system.changed` 事件。
- 确认默认 seed 仍只是最低优先级，不覆盖真实 ENV、K8S Secret、启动参数等运行时注入。
- 确认 dev manifest 为 config-service 注入 Rabbit 连接参数，保持单副本、ClusterIP、低
  request/limit，不新增 HTTPRoute 或扩大暴露面。
- 构建并推送 ARM/linux dev 镜像，rollout `deployment/ircs-config-service` 到 `ircs-dev`。
- 重跑 C2-S17 live smoke：写入临时 `app.content.internal.scraper-base-url`，观察 Rabbit
  publish / deliver / ack 或等价队列统计与消费侧日志证据，最后清理临时 DB key。
- 回写 docs/tasks，记录命令、镜像 digest、K8S 资源变化、Rabbit 证据、DB 清理证据、
  失败或遗留项。

## 范围

包含：

- `services/ircs-config-service/**`
- `deploy/k8s/dev/config-service-dev.yaml`
- `scripts/smoke/*config*`、`scripts/smoke/*rabbit*` 相关脚本
- `docs/**`
- `tasks/**`

不包含：

- content-service listener 代码
- storage-service、notification、Douban provider
- portal/admin/BFF
- 新增 HTTPRoute、Service 暴露或长期 Secret

## 方案

1. 先复核 config-service 本地 publisher、事件 payload 和 env-first/default seed 优先级测试。
2. 运行 config-service 相关单元测试与 K8S server dry-run。
3. 用既有 Jib 推送脚本构建 `linux/arm64` dev 镜像。
4. 仅 apply `deploy/k8s/dev/config-service-dev.yaml` 并 rollout config-service。
5. 采集 live pod env、镜像 digest、jar class、Rabbit 连接/日志证据。
6. 执行 C2-S17 smoke，确认临时值写入、Rabbit 消费和清理。

## 风险

- dev K8S API、Rabbit management API 或 port-forward 可能短时 timeout，需要低频重试并记录真实错误。
- Rabbit 队列 stats 是瞬时指标，需要结合队列统计和服务日志判断。
- content-service 存在运行时 override 时，DB fallback read 可能只能作为部分可观测项记录；
  publish / deliver / ack 是本轮核心验收。
- 如果 config-service 镜像推送或 rollout 失败，必须停止后续写入 smoke，并确认没有临时 DB
  key、port-forward 或临时 Secret 残留。

## 验收标准

- 本地 task spec 已建立父任务、子任务和测试边界。
- config-service 测试通过，或记录精确失败。
- K8S server dry-run 通过，manifest 不扩大服务暴露。
- dev live config-service 新 pod 使用包含 `SystemConfigChangePublisher.class` 的镜像，并有
  Rabbit env。
- C2-S17 smoke 观测到真实 Rabbit publish / deliver / ack，或记录新的阻塞点和清理证据。
- 临时 DB key 已恢复或删除，未发现本轮 port-forward、临时 Secret 或 config 临时值残留。

## 完成结果

2026-06-09：C2-S19 已完成。详见
`docs/reports/090-02816c2s19-config-service-publisher-live-rollout.md`。

- config-service dev 镜像已推送并 rollout，live digest 为
  `sha256:de6cc3f758ca62ff42bea22b422b2c570e488ac341da7380effdc9ce93472f05`。
- live pod 内已确认 `SystemConfigChangePublisher.class`。
- `SPRING_RABBITMQ_USERNAME` 已由错误的 `ircs` 修正为 Rabbit dev 实际用户 `admin`。
- C2-S17 重跑已观测到 temporary update 与 restore delete 的 Rabbit publish / deliver / ack。
- DB 临时值与本轮 port-forward 无残留。
