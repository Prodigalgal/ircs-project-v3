# 112 no Autowired single-constructor dev rollout

状态：完成

日期：2026-06-12

## 背景

本轮收敛两个运行期风险：

- 项目约束升级为生产代码禁止 `@Autowired`，Spring 组件不能依赖多构造器注入选择。
- `ircs-dev` 在全服务 `dev` 镜像刷新后，`ircs-content-service` live Deployment 仍缺 Redis/Valkey 环境注入，导致 `DefaultResolverPresetSeeder` 通过 distributed lock 访问 Redis 时回退到 `localhost:6379` 并 CrashLoop。

## 修复摘要

- 生产源码 `@Autowired` 清零。
- 生产 Spring stereotype class 多构造器风险清零。
- `SearchSyncOutboxWriter` 保持在 `shared:ircs-common` 作为跨服务 outbox 基础设施，各服务通过本服务配置类显式创建 bean，不把业务规则下沉到 common。
- `deploy/k8s/dev/content-service-dev.yaml` 已包含并已 apply Redis/Valkey 注入：
  - `SPRING_DATA_REDIS_HOST` from `ircs-dev-app-config.VALKEY_HOST`
  - `SPRING_DATA_REDIS_PORT=6379`
- `ircs-content-service` 重新 rollout 后恢复 `Ready 1/1`。

## 本地审计

```text
PROD_AUTOWIRED_COUNT=0
SPRING_MULTI_CONSTRUCTOR_RISK_COUNT=0
```

审计口径：

- `@Autowired`：`services`、`shared`、`platform` 生产 Java 源码，排除 `src/test` 和 `build`。
- 多构造器：带 `@Component` / `@Service` / `@Repository` / `@Controller` / `@RestController` / `@Configuration` 的生产 class，统计显式构造器数量。

## K8S rollout

```text
kubectl apply -f deploy\k8s\dev\content-service-dev.yaml
kubectl -n ircs-dev rollout status deployment/ircs-content-service --timeout=600s

deployment "ircs-content-service" successfully rolled out
ircs-content-service   1     1     20
ircs-content-service-57db84dfc6-hkjfl   1/1 Running restart=0
```

`ircs-dev` Deployment readiness：

```text
25/25 deployments Ready 1/1
pods non-ready summary: empty
quota used: pods=28, requests.cpu=890m/4, requests.memory=4544Mi/8Gi
```

## 运行中镜像 digest

完整审计文件：

```text
build/tmp/k8s-image-digest-audit-20260612144129.md
```

应用镜像摘要：

| workload | image digest |
| --- | --- |
| ircs-admin-bff | `sha256:4e80119902baef1cb59f50f5a53d05d42aac3e39b3ff8f26c99764f21d0c6854` |
| ircs-admin-frontend | `sha256:53443284309fd652c3dd0b062af603faa3fe95b54203b0195fd18f69ba5fadba` |
| ircs-aggregation-worker | `sha256:ee0b2561bf927ee6414547e29de2be05a746031e9fe84d0a4e47243b93935937` |
| ircs-catalog-service | `sha256:bc8ec3c3b0a72b2f8ffbbb97814839bb91d783a6f0c474153f5f538a8a58cd39` |
| ircs-config-service | `sha256:2546bbdbe97bcd068ab9182f944c2953e32fae236c7b20abe2041aa8caadc047` |
| ircs-content-service | `sha256:012e10f4e9b3aa3abd47c5ef09deb84efcd09fc0b62a10443cd6db37bebe9558` |
| ircs-credential-service | `sha256:634f635076842a4a0b4518ef1ea5ca0beaee8980e80544f15c5eebb3ff750e96` |
| ircs-identity-service | `sha256:130eb971cb4954efca86752d280fec393c2dc3f91dcab7462d29807ec27ec5df` |
| ircs-ingestion-worker | `sha256:c9293ffbd00a899f4c93f8dbf59bff5811aa43cc6fe213161c02d38ecea53797` |
| ircs-interaction-service | `sha256:2cd5b1086233d618ac0354be4a1c0f982a5c75793670acfcd14ca451c0492cb0` |
| ircs-magnet-service | `sha256:8adacd816cd0e71008924b554a9c444ace1878e6d1ed2197ff6edc2ec5ff1ad1` |
| ircs-metadata-worker | `sha256:d41ab01e030c25018a61dd6139e466c8fdd0bb12706dda8533dfc4720ce4fb81` |
| ircs-normalization-worker | `sha256:977ff97a9840a1c5f6e7ba9871d298c104d935dd7681bc7108c04ff49e907a85` |
| ircs-notification-worker | `sha256:4cff0405ff615adfd29fe9847518208a9320b0f3522afa0021ceaeef55ada6cc` |
| ircs-ops-service | `sha256:af35123f2cdb873c207cda0d1bf377e22a1c6ad24225964523de1b68694ff3f6` |
| ircs-portal-bff | `sha256:d051d3f2d2391de6a7ff2f01408ba4bbc79d63ac1f87931029e69c7e0d22fa52` |
| ircs-portal-frontend | `sha256:a411c3498cd2871093953b570616a6e89b3f0d1621308e175692dffd109b2751` |
| ircs-portal-service | `sha256:6d10a946fce61b6930806ee4958c8e51ccee7243c55af90f71a6521b99bc4dc2` |
| ircs-scraper-service | `sha256:d33617061c9e3deda010d2db1fba40ca1e6323da2bd113ca4bb19bf1b91e0c55` |
| ircs-search-service | `sha256:228bad735e35e0ac36d2303ac0bce67fe438a3598bbd4d460fd59f0ded5faefe` |
| ircs-storage-service | `sha256:a8ef03b280714e4f7ddbaad677c65aa189f57b41173412c19f0c02c18945c1ae` |
| ircs-task-service | `sha256:e5d8f0d50a921e6d0f5ade027c089b877669d177977596a815aac5f4701eefb0` |

## 轻量 health smoke

Service 访问 smoke：

```text
build/tmp/health-smoke-20260612144341.txt
```

结果：

```text
ircs-admin-bff              UP
ircs-catalog-service        UP
ircs-config-service         UP
ircs-content-service        UP
ircs-credential-service     UP
ircs-identity-service       UP
ircs-interaction-service    UP
ircs-magnet-service         UP
ircs-normalization-worker   UP
ircs-ops-service            UP
ircs-portal-bff             UP
ircs-portal-service         UP
ircs-scraper-service        UP
ircs-search-service         UP
ircs-storage-service        UP
ircs-task-service           UP
```

Worker Pod IP smoke：

```text
build/tmp/worker-health-smoke-20260612144405.txt
```

结果：

```text
ircs-aggregation-worker     UP
ircs-metadata-worker        UP
ircs-ingestion-worker       UP
ircs-notification-worker    UP
```

## 边界

- 本轮 health smoke 只验证 readiness，不替代后续统一业务行为测试。
- `build/tmp` 证据为本地运行证据；关键 digest 与结果已写入本报告，避免临时文件清理后丢失结论。
- Prometheus 指标查询、Redis lock contention 多副本测试、真实 mTLS `-Execute` rollout、外部 provider/SMTP/R2 生产级 smoke 仍按后续统一测试窗口推进。
