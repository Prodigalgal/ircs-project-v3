# V3 单仓库、云端 Native CI/CD 与生产切换

## 用户最新授权

- 所有编译改在 GitHub Actions，停止本地编译。
- 沿用 `Prodigalgal/ircs-project-v3`，整合当前 Java、Python、管理端和门户源代码至单一仓库。
- 生产 Argo CD 继续监听 `Prodigalgal/k8s-gitops-config`，改用 V3，打通构建、镜像、GitOps 回写及同步链路，下架现有 V4 工作负载。
- 必须保留生产数据、PVC、数据库和必需运行配置；先评估 V3/V4 schema 兼容性并备份，禁止通过重建空库、清空队列或删除 PVC 完成切换。
- 用户明确不要求旧 Git 备份。当前版本整合并验证后删除三个唯一旧仓库：`ircs-huawai-frontend`、`ircs-admin-frontend`、`ircs-backend`，不创建其 Git mirror/bundle。

## 范围与验收

1. 单仓库云端 Java、Python、前端回归及 amd64/arm64 Native/容器构建通过，使用固定提交标签。
2. GitHub Actions 对 GitOps 仓库使用专用最小写入权限，主分支仅在检查全部通过后回写镜像。
3. 数据库备份可读取、传输校验通过；PVC 保留策略有效，数据迁移对现有 schema 和业务数据兼容。
4. 处理现场 Valkey AOF 损坏：保留原始文件后检查恢复边界，尽可能完整恢复，不 FLUSH 或重建空数据目录。
5. V3 部署、真实进程/日志/探针、外部 API/页面、采集队列及 Hikari 状态有证据，V4 停止消费后退出生产入口。
6. Argo CD 状态与 GitOps desired state 一致，旧仓库在无引用且当前源代码已经整合后删除。

## 2026-11-12 只读现场

当前 kube context 为 `kubernetes-admin@sg-osaka-dualstack`，3 个 ARM64 节点，目标 namespace `ircs-prod`。Argo `ircs-v4-runtime` 指向 GitOps 的 `ircs-v4/runtime`；V4 API 和 Valkey 已经在 CrashLoopBackOff。PostgreSQL 数据库约 3277 MB，四个数据 PVC 均 Bound。Valkey 日志报告 `appendonly.aof.269.incr.aof` 格式损坏，必须先保留文件再恢复。以上是本次观察，不替代旧事故日志。
