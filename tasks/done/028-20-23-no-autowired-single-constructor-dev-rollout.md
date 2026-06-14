# 028-20-23 no Autowired single-constructor dev rollout

状态：已完成

来源：`docs/reports/112-no-autowired-single-constructor-dev-rollout.md`

## 目标

收敛生产代码禁止 `@Autowired` 后的 Spring 注入风险，并确认全服务 `ircs-dev` 运行态恢复健康。

## 范围

- 生产源码 `@Autowired` 清零审计。
- Spring stereotype class 多构造器风险清零审计。
- `ircs-content-service` dev Deployment Redis/Valkey env apply 与 rollout 恢复。
- 运行中镜像 digest 审计。
- Java/BFF/worker readiness 轻量 smoke。

## 非目标

- 不执行真实业务链路统一测试。
- 不执行 Prometheus 指标查询或多副本 contention 测试。
- 不执行真实 mTLS `-Execute` rollout。

## 验收证据

```text
PROD_AUTOWIRED_COUNT=0
SPRING_MULTI_CONSTRUCTOR_RISK_COUNT=0
ircs-content-service Ready 1/1 restart=0
ircs-dev deployments Ready 25/25
health smoke: 16 service endpoints UP
worker smoke: 4 worker Pod IP endpoints UP
```

证据文件：

```text
build/tmp/k8s-image-digest-audit-20260612144129.md
build/tmp/health-smoke-20260612144341.txt
build/tmp/worker-health-smoke-20260612144405.txt
```

## 后续

- 统一测试窗口继续覆盖业务行为、真实 provider、SMTP/R2/LLM、Prometheus 指标和 Redis lock contention。
- `028-20-10` 真实 mTLS `-Execute` smoke 已由 `028-20-25` 完成。
