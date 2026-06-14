# 034 portal/admin 门户 dev 部署边界分析

状态：已完成

更新时间：2026-06-06

## 输入来源

- V1 portal：`D:\WorkSpace\Project\ircs\ircs-project-v1\huawai`
- V1 admin：`D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-frontend`
- V1 K8S 配置：`D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-config`
- V3 service inventory：`deploy/k8s/service-inventory.yaml`
- 当前 K8S 环境：`docs/k8s/current-environment-2026-06-02.md`

## 当前事实

部署前 `ircs-dev` ResourceQuota 使用量：

```text
pods:            23 / 24
services:        19 / 20
configmaps:       3 / 30
secrets:          4 / 30
requests.cpu:   795m / 4
requests.memory: 3968Mi / 8Gi
limits.cpu:      6700m / 8
limits.memory:   12672Mi / 16Gi
```

portal/admin 前端与两个 BFF 部署后当前使用量：

```text
pods:            28 / 30
services:        24 / 26
configmaps:       4 / 30
secrets:          4 / 30
requests.cpu:    880m / 4
requests.memory: 4480Mi / 8Gi
limits.cpu:      7650m / 8
limits.memory:   14208Mi / 16Gi
```

V1 前端镜像当前已经在同一 ARM 节点运行：

```text
huawai:        harbor.mnnu.eu.org/ircs/huawai:8904e3825088e27b80cafb506cee77e7828c0c65
ircs-frontend: harbor.mnnu.eu.org/ircs/ircs-frontend:5813b9fcfabe06a2d916e9238d08cc749b54c6b3
```

因此本轮 MVP 可以复用已验证 ARM 镜像，先完成部署与 V3 API gateway 验证；后续若需要
修改前端源码，再进入远端 ARM Docker 构建链路。

## 前端行为边界

### portal huawai

- 技术栈：Next.js standalone。
- 运行端口：3000。
- 浏览器侧 API 基础路径：
  - `/api/backend/api/portal`
  - `/api/backend/api/portal/auth`
  - `/api/backend/api/portal/profile`
  - `/api/backend/api/portal/interaction`
  - `/api/backend/api/portal/feedback`
- 服务端渲染 API 基础路径：
  - `BACKEND_URL + /api/portal`

结论：portal Pod 的服务端渲染设置
`BACKEND_URL=http://ircs-portal-bff.ircs-dev.svc.cluster.local:8080`，避免
Next.js SSR 回打 gateway 形成自引用链路；浏览器侧 `/api/backend/**` 仍由 gateway
统一进入 `ircs-portal-bff`。

### admin ircs-frontend

- 技术栈：Vite + React + BrowserRouter + Nginx。
- 运行端口：80。
- 路由默认从 `/` 开始，不能直接挂在 `/admin` 子路径下，否则登录跳转、静态资源和
  BrowserRouter 都需要改造。
- 浏览器侧 API 基础路径：`/api/v1`。

结论：admin 应通过 gateway 的独立端口 `8080` 作为根路径暴露，避免修改 V1 前端代码。

## 三 Pod 部署边界

```text
Deployment ircs-portal-frontend
  image: huawai V1 ARM image
  port: 3000
  service: ircs-portal-frontend:3000

Deployment ircs-admin-frontend
  image: ircs-frontend V1 ARM image
  port: 80
  service: ircs-admin-frontend:80

Deployment ircs-frontend-gateway
  image: nginx:1.25-alpine
  port: 80 portal, 8080 admin
  service: ircs-frontend-gateway:80,8080
  config: ConfigMap nginx.conf
```

低资源建议：

```text
portal frontend: request 25m/192Mi, limit 250m/512Mi
admin frontend:  request 10m/32Mi,  limit 100m/128Mi
gateway:         request 10m/32Mi,  limit 100m/128Mi
```

总新增：

```text
frontend requests.cpu:    +45m
frontend requests.memory: +256Mi
frontend limits.cpu:      +450m
frontend limits.memory:   +768Mi
frontend pods/services:   +3 / +3

BFF requests.cpu:         +40m
BFF requests.memory:      +256Mi
BFF limits.cpu:           +500m
BFF limits.memory:        +768Mi
BFF pods/services:        +2 / +2
configmaps:               +1
```

最终 ResourceQuota：

```text
pods:     30
services: 26
```

部署后实际：

```text
pods:     28 / 30
services: 24 / 26
```

## Gateway 与 BFF 路由策略

gateway 只负责 edge 代理，不继续承接业务 ownership：

portal 端口 `80`：

```text
/api/backend/* -> ircs-portal-bff
/api/portal/*  -> ircs-portal-bff
/media/*       -> ircs-portal-bff
/              -> ircs-portal-frontend
```

admin 端口 `8080`：

```text
/api/v1/* -> ircs-admin-bff
/media/*  -> ircs-admin-bff
/         -> ircs-admin-frontend
```

BFF ownership：

```text
ircs-frontend-gateway -> ircs-portal-bff -> V3 portal-side services
ircs-frontend-gateway -> ircs-admin-bff  -> V3 admin-side services
```

正式决策记录见 `docs/decisions/003-formal-portal-admin-bff-split.md`。

## Smoke 结果

```text
portal-bff /internal/service-info -> 200
admin-bff  /internal/service-info -> 200
portal via gateway /api/portal/metadata -> 200
portal via gateway /api/backend/api/portal/home -> 200
portal via gateway /api/backend/api/portal/auth/pow -> 200
admin via gateway /api/v1/dashboard/statistics -> 200
admin via gateway /api/v1/ops/traffic/status -> 200
admin via gateway /api/v1/auth/pow -> 403, recorded V3 identity-service gap
```

## 风险

- admin 登录依赖 `/api/v1/auth/**`，V3 尚未实现，部署后会真实暴露为缺口。
- admin 使用根路径 BrowserRouter，所以不能与 portal 共用同一个 URL path prefix。
- 复用 V1 admin 镜像时，其容器内 Nginx 仍包含旧的 V1 monolith API proxy；只要用户通过
  gateway 访问，该配置不会处理 `/api/v1` 请求。
- 若未来新增公网 HTTPRoute，需先收紧 `production-gateway` 的 allowedRoutes 或使用专用
  dev hostname，避免流量冲突。

## 决策

本轮采用 portal/admin/gateway 三前端 Pod 加 portal/admin 两个 BFF Pod、无 HTTPRoute、
低资源、ClusterIP/port-forward 验证方案。
部署目标不是证明接口已经全部迁移，而是建立可以持续发现和记录接口缺口的真实页面入口。
