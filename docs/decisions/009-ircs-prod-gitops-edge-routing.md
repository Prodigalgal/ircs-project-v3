# 009 ircs-prod GitOps and edge routing

状态：accepted

日期：2026-06-14

## 决策

新的 V3 生产命名空间使用 `ircs-prod`。

`ircs-system` 是旧生产命名空间，后续不再作为 V3 生产目标；它只作为现状盘点、
迁移对照、流量冲突排查和退役对象保留。

生产发布链路沿用 GitOps：

```text
developer push
  |
  v
Gitea repo
  |
  | build / image publish / config sync
  v
ArgoCD Application
  |
  v
K8S namespace: ircs-prod
```

生产公网入口沿用现有 edge：

```text
Cloudflare DNS
  huawai.sophia.fr.eu.org -> A 129.150.47.1
  |
  v
Oracle Cloud NLB
  80  -> nodePort 30971
  443 -> nodePort 30432
  |
  v
envoy-gateway-system
  Gateway: production-gateway
  Service: envoy-envoy-gateway-system-production-gateway-e5b8cf91
  LoadBalancer: 10.106.255.188 / 10.0.0.240
  |
  v
HTTPRoute in ircs-prod
  |
  v
ircs-prod frontend gateway / BFF / services
```

## 当前真实集群状态

2026-06-14 切流后核对结果：

- `ircs-prod` 已创建，并成为后续开发、测试和生产候选验证的唯一目标 namespace。
- `ircs-system` 当前仍存在，但不再承接 `huawai` / `ircs` 业务入口；只作为短期回滚参考和退役对象。
- `envoy-gateway-system/production-gateway` 已 Programmed，address 为 `10.0.0.240`。
- Envoy Gateway Service：
  `envoy-envoy-gateway-system-production-gateway-e5b8cf91`
  `LoadBalancer 10.106.255.188 10.0.0.240 80:30971/TCP,443:30432/TCP`。
- 当前 `huawai.sophia.fr.eu.org` 由新 route 承接：
  `ircs-prod/huawai-route -> ircs-frontend-gateway:80`。
- 当前 `ircs.sophia.fr.eu.org` 由新 route 承接：
  `ircs-prod/ircs-route -> ircs-frontend-gateway:8080`。
- 旧 `ircs-system/huawai-route` 和 `ircs-system/ircs-route` 已删除；`ircs-system/kibana-route`
  暂时保留。
- 旧 `ircs-app` ArgoCD Application 仍指向 `ircs-system`，但 automated self-heal 已关闭；
  当前 `OutOfSync/Missing` 是预期状态，用于防止旧 route 被自动补回。
- `ircs-prod-core` 与 `ircs-prod-edge` 均为 `Synced/Healthy`。

## 已落地仓库

- 源码仓库：`https://gitea.mnnu.eu.org/gitea-admin/ircs-project-v3.git`。
- GitOps 仓库：`https://gitea.mnnu.eu.org/gitea-admin/ircs-prod-config.git`。
- GitOps core path：`ircs-prod/core`，目标 namespace `ircs-prod`。
- GitOps edge path：`ircs-prod/edge-cutover`，只声明 `huawai-route` 和 `ircs-route`。
- ArgoCD Application 清单位于 GitOps 仓库 `apps/`：
  `ircs-prod-core-application.yaml` 自动同步 core；`ircs-prod-edge-application.yaml`
  不开启 automated，用于人工切流窗口。

## 迁移要求

1. 新生产清单不得继续写入 `ircs-system`。
2. 新 ArgoCD Application 的 destination namespace 必须是 `ircs-prod`。
3. `huawai.sophia.fr.eu.org` 的最终 HTTPRoute 必须迁移到 `ircs-prod`，并指向 V3
   frontend gateway 或明确的 production entry service。
4. 旧 `ircs-system` HTTPRoute 在切流后必须退役，避免同 hostname route 冲突。
5. 切流前必须固定镜像 digest、Secret/ConfigMap 来源、备份/回滚方案和 go/no-go 报告。
6. Cloudflare、Oracle NLB 和 Envoy Gateway 可以沿用，不在业务代码层实现 mTLS。
7. 旧生产数据不迁移到 `ircs-prod`；`ircs-prod` 按 V3 migrator 初始化后的干净库起步。

## 非目标

- 本决策已经由后续执行创建 `ircs-prod`，但不删除旧 `ircs-system`。
- 本决策已经由后续执行关闭旧 `ircs-app` automated self-heal，用于避免旧业务 route 抢回流量。
- 本决策不删除 `ircs-system` 资源。
- 本决策不改变 Cloudflare/NLB/Envoy Gateway 当前基础设施。
- 本决策不再要求旧生产数据迁移；干净起步由 `031-ircs-prod-gitops-data-migration` 跟踪。
