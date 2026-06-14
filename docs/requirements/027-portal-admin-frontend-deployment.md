# 027 portal/admin 门户 dev 部署需求

状态：已完成

更新时间：2026-06-06

## 背景

V1 的门户端 `huawai` 和后台端 `ircs-frontend` 已经是经过验证的前端入口。当前 V3
后端微服务已经完成 18/18 MVP rollout，并进入 V1 路由覆盖闭环阶段。为了后续通过真实
页面持续验证接口是否完整迁移，需要把 V1 的 portal 门户和 admin 门户部署到
`ircs-dev`，并让页面请求优先进入 V3 微服务。

开始部署前 `ircs-dev` 资源较紧：

```text
pods:     23 / 24
services: 19 / 20
```

用户明确要求不要把 `huawai`、admin 静态容器和统一入口代理放在一个 Pod 内，需要拆成
三个独立 Pod。随后用户明确要求正式拆分 `portal-bff` 和 `admin-bff`，因此当前完成态
包含五个独立 Pod：

```text
ircs-portal-frontend
ircs-admin-frontend
ircs-frontend-gateway
ircs-portal-bff
ircs-admin-bff
```

## 目标

- 在 `ircs-dev` 部署三个独立 Deployment/Pod：
  - `ircs-portal-frontend`：V1 `huawai` Next.js standalone 门户。
  - `ircs-admin-frontend`：V1 `ircs-frontend` Vite/Nginx 后台门户。
  - `ircs-frontend-gateway`：Nginx 统一入口代理。
- 正式拆出两个独立 BFF Deployment/Pod：
  - `ircs-portal-bff`：portal 前端契约和后续聚合边界。
  - `ircs-admin-bff`：admin 前端契约和后续聚合边界。
- 为前端和 BFF Pod 使用低资源 request/limit。
- 调整 `ircs-dev` ResourceQuota，使部署后仍保留 2 个 Pod 和 2 个 Service 缓冲。
- 不创建公网 HTTPRoute，默认通过 ClusterIP 和 `kubectl port-forward` 验证。
- gateway 将门户和后台 API 请求路由到对应 BFF，BFF 再分流到 V3 namespace-local 微服务。
- 将门户作为后续接口迁移验收入口，页面发现的 404/501/行为不一致必须回填到
  `025` 路由覆盖闭环任务。

## 非目标

- 不迁移或修复前端业务代码。
- 不把 admin auth、admin members 等尚未迁移接口直接接到 V1 单体。
- 不创建公网域名、HTTPRoute 或生产切流。
- 不新增数据库、中间件或 PVC。
- 不在本任务内完成所有 V1 缺口，只部署验证入口。

## 当前架构

```text
                 local browser
                      |
                      | kubectl port-forward svc/ircs-frontend-gateway
                      |   18080 -> gateway:80    portal
                      |   18081 -> gateway:8080  admin
                      v
        +-------------------------------------------+
        | ircs-frontend-gateway Pod                 |
        | nginx                                     |
        |                                           |
        | :80   /              -> portal frontend   |
        | :80   /api/backend/* -> portal-bff        |
        | :80   /api/portal/*  -> portal-bff        |
        | :8080 /              -> admin frontend    |
        | :8080 /api/v1/*      -> admin-bff         |
        | :8080 /media/*       -> admin-bff         |
        +-------------------+-----------------------+
                            |
             +--------------+--------------+----------------+
             |                             |                |
             v                             v                v
 +--------------------------+   +--------------------------+
 | ircs-portal-frontend Pod |   | ircs-admin-frontend Pod  |
 | V1 huawai Next.js        |   | V1 ircs-frontend Nginx   |
 | port 3000                |   | port 80                  |
 +--------------------------+   +--------------------------+
             |
             | SSR BACKEND_URL
             v
 +--------------------------+       +--------------------------+
 | ircs-portal-bff Pod      |       | ircs-admin-bff Pod       |
 | thin proxy + ownership   |       | thin proxy + ownership   |
 +------------+-------------+       +------------+-------------+
              \                             /
               \                           /
                v                         v
       +--------------------------------------------+
       | V3 namespace-local backend services         |
       | identity/content/catalog/task/ops/storage   |
       | portal/search/interaction/magnet/scraper    |
       +--------------------------------------------+

 V3 backend service routing stays inside ircs-dev:

 /api/portal/auth, /api/portal/profile -> ircs-identity-service
 /api/portal/interaction, /feedback    -> ircs-interaction-service
 /api/portal/search                    -> ircs-search-service
 /api/portal/*                         -> ircs-portal-service
 /api/v1/dashboard, /api/v1/ops        -> ircs-ops-service
 /api/v1/raw-videos, /unified-videos   -> ircs-content-service
 /api/v1/cover-images, /media          -> ircs-storage-service
 /api/v1/collection-tasks              -> ircs-task-service
 /api/v1/catalog, dictionary routes    -> ircs-catalog-service
 /api/v1/configs                       -> ircs-config-service
 /api/v1/credentials                   -> ircs-credential-service
 /api/v1/magnets, /magnet-providers    -> ircs-magnet-service
 /api/v1/scraper/manual                -> ircs-scraper-service
 /api/v1/messages                      -> ircs-interaction-service
/api/v1/auth, /api/v1/members         -> ircs-identity-service
```

`ircs-portal-frontend` 的服务端渲染 `BACKEND_URL` 直连
`ircs-portal-bff.ircs-dev.svc.cluster.local:8080`，避免 portal Pod 回打 gateway；
浏览器侧 `/api/backend/**` 仍通过 gateway 进入 `ircs-portal-bff`。

## BFF 边界

已按用户指令正式拆分独立 BFF 层，决策见：

```text
docs/decisions/003-formal-portal-admin-bff-split.md
```

```text
frontend-gateway = edge 反向代理，不做 BFF 职责
ircs-portal-bff  = portal 前端契约、代理和后续聚合边界
ircs-admin-bff   = admin 前端契约、代理和后续聚合边界
```

BFF 第一阶段以薄代理为主，不接回 V1 monolith，不伪造未迁移接口成功。

## 已知缺口

- V3 `ircs-identity-service` 的 `/api/v1/auth/**` 管理员登录与改密已由后续
  `025-10` 任务迁移完成。
- V3 `ircs-identity-service` 的 `/api/v1/members/**` 后台会员管理已由后续
  `025-11` 任务迁移完成。
- V3 `ircs-interaction-service` 的 `/api/v1/messages/**` 已由后续 `025-08`
  任务迁移完成。
- 其余 admin/portal 页面发现的缺口继续回填到 `025` 路由覆盖闭环。

## 验收标准

- `ircs-dev` 中三个前端 Deployment Ready 1/1。
- `ircs-dev` 中三个前端 Service 存在，且无 HTTPRoute。
- `ircs-dev` 中两个 BFF Deployment Ready 1/1。
- `ircs-dev` 中两个 BFF Service 存在，且无 HTTPRoute。
- ResourceQuota 调整后满足：
  - 部署前允许创建前端和 BFF Pod/Service。
  - 部署后至少保留 2 个 Pod 和 2 个 Service 缓冲。
- gateway 通过 ClusterIP 或 port-forward 验证：
  - portal 首页可返回 HTML。
  - admin 登录页可返回 HTML。
  - portal API 至少覆盖 metadata/home/auth pow 的 gateway 路由 smoke。
  - admin API 至少覆盖 dashboard/ops/content/storage 等已迁移路由 smoke。
  - admin auth 缺口按真实 HTTP 结果记录，不伪造完成。
- 文档、K8S 快照和任务状态回写完成。

## 完成结果

```text
deployments: ircs-portal-frontend, ircs-admin-frontend, ircs-frontend-gateway,
             ircs-portal-bff, ircs-admin-bff all Ready 1/1
services:    five ClusterIP services exist
httproute:   none in ircs-dev
quota:       pods 28/30, services 24/26
smoke:       portal metadata/home/auth-pow 200; admin dashboard/ops/auth-pow 200
known gap:   BFF 拆分层无遗留缺口；后续缺口按具体业务服务归属进入 025 闭环
```

## 复验 2026-06-06

本轮重新确认正式拆分状态：

```text
local tests: :services:ircs-portal-bff:test :services:ircs-admin-bff:test -> PASS
k8s:         ircs-portal-bff, ircs-admin-bff, ircs-frontend-gateway,
             ircs-portal-frontend, ircs-admin-frontend all Ready 1/1
quota:       pods 28/30, services 24/26
httproute:   none in ircs-dev
smoke:       portal-bff service-info 200; admin-bff service-info 200;
             portal metadata/home/auth-pow via gateway 200;
             admin dashboard/ops/auth-pow via gateway 200
```
