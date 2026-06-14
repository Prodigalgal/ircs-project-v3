# 027-01 portal/admin 门户 dev 部署 MVP

状态：已完成

上游来源：

- `docs/requirements/027-portal-admin-frontend-deployment.md`
- `docs/reports/034-portal-admin-frontend-deployment-boundary-analysis.md`
- `docs/k8s/current-environment-2026-06-02.md`

## 背景

V1 `huawai` 和 `ircs-frontend` 是已经验证过的门户入口。V3 后端服务正在逐步关闭 V1
路由缺口，需要把两个门户部署到 `ircs-dev`，通过真实页面持续发现接口迁移缺口。

## 目标

部署三个独立 Pod：

```text
ircs-portal-frontend
ircs-admin-frontend
ircs-frontend-gateway
```

并完成低资源 quota 调整、ClusterIP smoke、port-forward 使用入口和文档回写。

## 非目标

- 不修改 V1 前端业务代码。
- 不实现 admin auth、members 或 message API 缺口。
- 不创建公网 HTTPRoute。
- 不接回 V1 monolith 兜底。
- 不新增数据库、中间件或 PVC。

## 输入

- V1 portal 目录：`D:\WorkSpace\Project\ircs\ircs-project-v1\huawai`
- V1 admin 目录：`D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-frontend`
- ARM 已验证镜像：
  - `harbor.mnnu.eu.org/ircs/huawai:8904e3825088e27b80cafb506cee77e7828c0c65`
  - `harbor.mnnu.eu.org/ircs/ircs-frontend:5813b9fcfabe06a2d916e9238d08cc749b54c6b3`
- V3 service inventory：`deploy/k8s/service-inventory.yaml`

## 输出

- `deploy/k8s/dev/resource-quota-dev.yaml` quota 小幅提高。
- `deploy/k8s/dev/frontend-portal-admin-dev.yaml` 三 Pod/三 Service/gateway ConfigMap 清单。
- 三个 Deployment Ready 1/1。
- portal/admin gateway smoke 结果。
- `docs/index.md`、`tasks/index.md`、`docs/k8s/current-environment-2026-06-02.md` 回写。

## 影响范围

包含：

- `deploy/k8s/dev`
- `docs/requirements`
- `docs/reports`
- `docs/k8s`
- `tasks/in-progress`
- `tasks/done`

不包含：

- `services/*` Java 代码。
- V1 前端源码。
- public HTTPRoute。
- `ircs-system` 生产样运行 namespace。

## 依赖

- `ircs-dev` 可访问 Harbor imagePullSecret `harbor-secret`。
- V3 backend service ClusterIP Ready。
- ResourceQuota 允许新增三 Pod/三 Service。

## 风险

- admin auth 仍是 V3 缺口，admin 登录 API smoke 不应标记为完成。
- portal SSR 和浏览器 API 都依赖 gateway 路由，gateway 配置错误会造成页面可见但 API 失败。
- 三个 Deployment 初次创建会消耗紧张的 Pod/Service quota，必须先调整 quota。

## 验收标准

- `kubectl -n ircs-dev get deploy ircs-portal-frontend ircs-admin-frontend ircs-frontend-gateway`
  均 Ready 1/1。
- `kubectl -n ircs-dev get svc ircs-portal-frontend ircs-admin-frontend ircs-frontend-gateway`
  均存在。
- `kubectl -n ircs-dev get httproute` 仍为空。
- gateway ClusterIP smoke：

```text
MARK 1/6 portal / -> HTTP 200 text/html
MARK 2/6 portal /api/portal/metadata -> HTTP 200
MARK 3/6 portal /api/backend/api/portal/home -> HTTP 200
MARK 4/6 admin /login -> HTTP 200 text/html
MARK 5/6 admin /api/v1/dashboard/statistics -> HTTP 200
MARK 6/6 admin /api/v1/auth/pow ->真实记录当前 V3 结果，不作为完成接口标记
```

- ResourceQuota 部署后预期约为 `pods=26/27`、`services=22/23`。

## 测试边界

K8S：

```text
kubectl apply --dry-run=server -f deploy/k8s/dev/resource-quota-dev.yaml
kubectl apply --dry-run=server -f deploy/k8s/dev/frontend-portal-admin-dev.yaml
kubectl apply -f deploy/k8s/dev/resource-quota-dev.yaml
kubectl apply -f deploy/k8s/dev/frontend-portal-admin-dev.yaml
kubectl -n ircs-dev rollout status deploy/ircs-portal-frontend --timeout=240s
kubectl -n ircs-dev rollout status deploy/ircs-admin-frontend --timeout=240s
kubectl -n ircs-dev rollout status deploy/ircs-frontend-gateway --timeout=240s
```

本地浏览器验证入口：

```text
kubectl -n ircs-dev port-forward svc/ircs-frontend-gateway 18080:80 18081:8080
portal: http://127.0.0.1:18080
admin:  http://127.0.0.1:18081/login
```

## 关联任务

- 被本任务临时挂起，后续已完成：`tasks/done/025-08-interaction-service-admin-message-api-mvp.md`
- 上游覆盖闭环：`tasks/in-progress/025-v1-route-coverage-closeout-parent.md`
- 后续扩展：`tasks/done/027-02-portal-admin-bff-split-mvp.md`

## 当前进度

- 已确认用户要求三 Pod，而不是一个 Pod 多容器。
- 已确认 V1 portal 为 `huawai`。
- 已确认 V1 admin 为 `ircs-frontend`。
- 已确认当前 V1 前端镜像已在 ARM 节点运行，可先复用。
- 已完成 portal/admin/gateway 三 Pod 部署和初步 smoke。
- 2026-06-06 用户要求正式拆分 `portal-bff` 和 `admin-bff`，后续转入
  `tasks/done/027-02-portal-admin-bff-split-mvp.md` 并已完成。
- 已完成正式 BFF 拆分后复验：
  - `ircs-portal-frontend` Ready 1/1。
  - `ircs-admin-frontend` Ready 1/1。
  - `ircs-frontend-gateway` Ready 1/1。
  - `ircs-dev` 无 HTTPRoute。
  - portal metadata/home/auth-pow 经 gateway + portal-bff 返回 200。
  - admin dashboard/ops 经 gateway + admin-bff 返回 200。
  - admin auth-pow 返回 403，按真实缺口转入 `025` 路由覆盖闭环。

## 完成结果

```text
frontends:
  ircs-portal-frontend 1/1
  ircs-admin-frontend 1/1
  ircs-frontend-gateway 1/1

services:
  ircs-portal-frontend ClusterIP
  ircs-admin-frontend ClusterIP
  ircs-frontend-gateway ClusterIP

quota after BFF split:
  pods 28/30
  services 24/26
```
