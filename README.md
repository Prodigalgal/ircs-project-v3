# IRCS V3

V3 单仓库，包含独立 Java 微服务、Python CPU 分类服务、管理端和门户。Java 主版本保持 25，Java 服务采用 GraalVM Native 与虚拟线程。

| 目录 | 内容 |
| --- | --- |
| backend | 21 个 Java 服务、独立 migrator、Python 服务、共享模块 |
| frontends/admin | React / MUI / Vite 管理端 |
| frontends/huawai | Next.js 门户 |
| .github/workflows | 回归、双架构构建和 GitOps 发布 |

构建在 [GitHub Actions](https://github.com/Prodigalgal/ircs-project-v3/actions/workflows/native-build.yml) 执行。`main`、`upgrade/**` 推送自动验证；产物使用 Docker Hub 的完整提交 SHA 标签。主分支全部检查通过后自动回写私有仓库 `Prodigalgal/k8s-gitops-config`，由 Argo CD 部署，数据基础设施与 PVC 保留。

版本、消费可靠性修复、兼容性影响、验证及切换记录见 [升级记录](backend/docs/migration/2026-11-12-native-jdk25.md)。前端 ESLint/TypeScript 使用最新 peer 兼容版本，新增 React Compiler 同步诊断保留为 warning。
