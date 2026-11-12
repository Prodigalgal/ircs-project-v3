# IRCS V3 Microservice Target

This repository is the target microservice workspace for IRCS.

The V1 backend remains the migration source. V3 is intentionally structured by
runtime ownership instead of the old package tree:

- HTTP domain services own public/admin API routes.
- Worker services own RabbitMQ queues and scheduled background work.
- Shared modules contain only stable contracts and cross-cutting helpers.
- K8S edge routing preserves `/api/v1/**`, `/api/portal/**`, and `/media/**`.

Start with:

```powershell
.\gradlew projects
.\gradlew build
```

Primary design documents:

- [Target boundaries](docs/architecture/001-target-microservice-boundaries.md)
- [Cutover plan](docs/architecture/002-cutover-plan.md)
- [Migrator extraction](docs/migration/003-migrator-extraction.md)

## V3 Native / JDK 25

本次升级保持 21 个 Java 服务和独立 migrator 的部署边界，使用 Spring Boot 4.1.2、Spring AI 2.0.2、Jackson 3、Gradle 9.9.1，Java 主版本固定为 25。HTTP、Rabbit 消费和任务执行器使用虚拟线程，并保留原有并发、预取和队列容量限制。

构建统一使用 [GitHub Actions](https://github.com/Prodigalgal/ircs-project-v3/actions/workflows/native-build.yml)。推送 `main` 或 `upgrade/**` 自动运行 Java/Python 回归、双架构 Native 构建和隔离 smoke；手动触发时可用 `build_scope` 选择 Java 模块。

通过检查后，镜像使用 `ghcr.io/prodigalgal/ircs-project-v3/<service>:sha-<完整提交SHA>`，包含 `linux/amd64` 和 `linux/arm64`。架构构建使用 GitHub 对应的原生 runner，GraalVM 固定到包含最新安全补丁的 JDK 25 LTS 镜像；无需本地编译，也无需新增 Docker Hub 密钥。

原 Docker Hub 工作流仍为手动入口，保留 `image_mode=jvm` 回退。自动验证不会更新 Kubernetes/GitOps 或生产镜像 tag。兼容性、采集消费可靠性修复、验证结果和回滚说明见 [升级记录](docs/migration/2026-11-12-native-jdk25.md)。
