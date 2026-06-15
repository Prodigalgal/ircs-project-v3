# ircs-prod GitOps 运维手册

状态：当前执行口径

日期：2026-06-15

## 目标

`ircs-project-v3` 迁移到新的两节点 OCI K8S 集群，统一使用 `ircs-prod` namespace，
通过 Gitea Actions 构建 ARM64 镜像、推送到内部 registry，再由 Argo CD 管理部署。

本轮不保留旧环境兼容路径，不继续使用旧 Harbor、旧 `ircs-dev`、旧独立 GitOps 仓库。

## 基础设施

| 项 | 当前值 |
| --- | --- |
| Namespace | `ircs-prod` |
| Git repo | `https://gitea.mnnu.eu.org/admin/ircs-project-v3.git` |
| Argo CD repoURL | `http://gitea-http.gitea.svc.cluster.local:3000/admin/ircs-project-v3.git` |
| Registry | `registry.mnnu.eu.org/ircs` |
| Registry pull secret | `ircs-prod/registry-secret` |
| Runtime secret | `ircs-prod/ircs-prod-secrets` |
| Gateway | `gateway-system/mnnu-gateway` |
| External HTTP/HTTPS | `mnnu-gateway` NodePort `30080/30443` behind OCI NLB |
| TLS | Only at Gateway / infrastructure layer |

内部组件之间使用 HTTP、PostgreSQL、AMQP、Redis、Elasticsearch 原生协议，不在业务 Pod
内配置 TLS。公网 TLS 由 Envoy Gateway 统一终止。

## 域名和入口

| Host | 默认后端 | API 后端 |
| --- | --- | --- |
| `huawai.mnnu.eu.org` | `ircs-portal-frontend:3000` | `/api/backend`、`/api/portal`、`/media` -> `ircs-portal-bff:8080` |
| `admin.mnnu.eu.org` | `ircs-admin-frontend:80` | `/api/v1`、`/media` -> `ircs-admin-bff:8080` |

注意：`admin.mnnu.eu.org` 的默认路径必须指向后台前端，而不是直接指向 BFF。后台接口只通过
`/api/v1` 前缀进入 `ircs-admin-bff`。

## GitOps 目录

```text
deploy/
  argocd/
    ircs-prod-core-application.yaml
    ircs-prod-edge-application.yaml
    ircs-prod-migrator-application.yaml
  k8s/prod/
    core/
    edge-cutover/
    migration/
```

`core` 和 `edge-cutover` 由 Argo CD 自动同步。`migration` 保留为 Argo CD 管理对象，但不启用
automated sync，避免数据库迁移 Job 因镜像标签变化或 Job 不可变字段被重复触发。

## 镜像构建和标签回写

Gitea Actions workflow：`.gitea/workflows/build-and-push.yml`。

默认流程：

1. push 到 `main`。
2. Gitea runner 使用 `scripts/ci/jib-build-push-affected.sh` 计算 affected Java module。
3. Jib 构建 `linux/arm64` 镜像并推送到 `registry.mnnu.eu.org/ircs`。
4. `scripts/ci/update-prod-image-tags.sh` 根据 `build-image-manifest.txt` 修改
   `deploy/k8s/prod` 中对应镜像 tag。
5. Actions 以 `gitea-actions` 身份把生产清单 tag 更新 commit 回 `main`。
6. Argo CD 看到 Git revision 变化后自动同步 core/edge。

前端镜像当前由人工构建并推送：

| 镜像 | 来源 | 端口 |
| --- | --- | --- |
| `registry.mnnu.eu.org/ircs/huawai:prod-20260615` | V1 `huawai` Next.js 前台 | `3000` |
| `registry.mnnu.eu.org/ircs/ircs-frontend:prod-20260615` | V1 `ircs-frontend` 后台管理前端 | `80` |

2026-06-15 已推送镜像 digest：

- `huawai:prod-20260615` -> `sha256:f44dd3482d6ef3be632f090e61a70211a96d304d00e8c3abe92fccdf90298923`
- `ircs-frontend:prod-20260615` -> `sha256:06314fea13ad8d95dceaae4db02b4c0fddc4b48e6c78e43c0f967d8a80421216`

## 必需 Secrets

Gitea repo Actions secrets：

| Secret | 用途 |
| --- | --- |
| `REGISTRY_USERNAME` | 推送镜像到 registry |
| `REGISTRY_PASSWORD` | 推送镜像到 registry |
| `PUSH_USERNAME` | Actions 回写生产清单 |
| `PUSH_PASSWORD` | Actions 回写生产清单 |

K8S runtime secret：`ircs-prod/ircs-prod-secrets`。

当前统一账号口径为 `admin`，统一密码口径为约定的本地生产密码。数据库、RabbitMQ、
Elasticsearch、admin 初始化密码、内部 service token 等值在新环境中统一生成/写入，不读取旧
`ircs-dev` 或旧 Harbor secret。

## 部署顺序

1. 确认 `registry.mnnu.eu.org` 可登录，并存在 `ircs` 命名空间。
2. 创建 Gitea repo secrets。
3. 推送 `main` 到 Gitea，等待 Actions 完成镜像构建和 tag 回写。
4. 创建或更新 `ircs-prod/registry-secret` 和 `ircs-prod/ircs-prod-secrets`。
5. 在 Argo CD 创建 repo credential，URL 使用内部 HTTP Gitea 服务。
6. 应用 `deploy/argocd/ircs-prod-core-application.yaml`。
7. core 健康后，手动同步 `ircs-prod-migrator` 初始化数据库。
8. migrator 成功后，应用或同步 `ircs-prod-edge`。
9. Cloudflare DNS 创建 `huawai.mnnu.eu.org`、`admin.mnnu.eu.org` 到 OCI NLB 公网 IP。
10. Gateway TLS certificate 加入两个域名并等待 Ready。

## 验证命令

```powershell
kubectl -n argocd get app
kubectl -n ircs-prod get pods,svc,pvc,httproute
kubectl -n gateway-system get gateway,httproute,certificate,secret
```

公网验证：

```powershell
curl.exe -I https://huawai.mnnu.eu.org/
curl.exe -I https://admin.mnnu.eu.org/
curl.exe -I https://admin.mnnu.eu.org/api/v1/auth/pow?scope=admin.login
```

期望：

- `huawai.mnnu.eu.org/` 返回门户前端 HTML。
- `admin.mnnu.eu.org/` 返回后台管理前端 HTML。
- `admin.mnnu.eu.org/api/v1/...` 进入 `ircs-admin-bff`。
- `huawai.mnnu.eu.org/api/portal/...` 和 `/api/backend/...` 进入 `ircs-portal-bff`。

## 常见故障

| 现象 | 优先检查 |
| --- | --- |
| Argo CD 无法拉 repo | repo credential URL 是否等于内部 `gitea-http` 地址 |
| Pod `ImagePullBackOff` | `registry-secret` 是否是 `kubernetes.io/dockerconfigjson`，registry tag 是否存在 |
| Actions 构建成功但 Argo 不更新 | 是否产生了 `ci: update ircs image tags` commit |
| admin 域名打开的是接口响应 | `admin-route` 默认 backend 是否仍指向 `ircs-admin-frontend:80` |
| portal/admin API 500 | `APP_BFF_*_BASE_URL` 是否指向 `ircs-prod.svc.cluster.local` |
| TLS 证书不含新域名 | `gateway-system/mnnu-services-tls` 的 `dnsNames` 是否含 `huawai` 和 `admin` |
