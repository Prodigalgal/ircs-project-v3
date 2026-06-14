# IRCS V3 开发指南

状态：进行中

更新时间：2026-06-11

## 技术栈

- Java 25, Gradle 9.1 (`./gradlew`), Spring Boot 3.5.6, Spring AI 1.1.2
- Lombok 全局启用，禁止添加 `spring-boot-starter-logging`（使用 Log4j2）
- 镜像构建用 Jib，基础镜像 `eclipse-temurin:25-jre-alpine`，推送到 Harbor
- 默认 Jib target arch 是 `arm64`；本地 x86 构建需 `-PjibTargetArch=amd64`
- 所有子项目使用 `io.spring.dependency-management`，BOM 包含 AWS SDK、Resilience4j、OpenTelemetry

## 核心命令

```powershell
.\gradlew projects                    # 查看全部子项目
.\gradlew build                       # 全量构建
.\gradlew :services:ircs-identity-service:test   # 单服务测试
.\gradlew :shared:ircs-common:test                # 共享模块测试
```

## 测试

- JUnit 5 + Mockito inline agent（`-javaagent:mockito-core`），JVM 需要额外 agent 参数
- 服务层测试用 H2 内存数据库，不用真实 PostgreSQL
- smoke 测试在 `scripts/smoke/` 下，PowerShell 脚本，默认 dry-run，真实执行需 `-Execute` flag

## 镜像构建 & 部署

```powershell
.\gradlew :services:ircs-identity-service:jib -PjibToImage=harbor.mnnu.eu.org/ircs/ircs-identity-service:dev
```

- K8S 部署清单在 `deploy/k8s/`，模板在 `deploy/k8s/templates/app-deployment.yaml`
- 所有服务 8080 端口，ClusterIP 类型，健康检查走 `/actuator/health`

## Monorepo Structure

```text
shared/            稳定契约和跨切面 helpers（java-library 插件）
  ircs-contracts   零依赖契约模块
  ircs-common      共享工具：安全、审计、缓存、出站 HTTP、UUID、readiness gate
  ircs-messaging   RabbitMQ 消息抽象
  ircs-observability 可观测性配置

services/          业务服务（Spring Boot + Jib）
  -*-service       HTTP 域服务（identity/content/catalog/portal/...）
  -*-worker        Worker 服务（ingestion/normalization/aggregation/metadata/notification）
  -bff             BFF 聚合层（portal-bff/admin-bff）

platform/          基础设施工具
  ircs-migrator    Liquibase 数据库迁移
```

## Java Package Convention

- 根包：`com.prodigalgal.ircs`
- 服务包：`com.prodigalgal.ircs.<service-name>`（如 `ircs.identity`）
- 共享包：`com.prodigalgal.ircs.common.<domain>`（如 `security`、`audit`、`cache`、`outbound`、`readiness`）
- 每个服务是独立 Spring Boot application，有自己的 `*Application.java` 主类

## Architecture Notes

- V1 路由保持不变：`/api/v1/**`、`/api/portal/**`、`/media/**`
- 服务间通信：HTTP sync call 或 RabbitMQ queue，禁止 worker 间直接调用
- 数据库：Phase 0 共用 PostgreSQL，按服务分表；物理拆分待路由稳定后
- 共享模块用 `compileOnly` 声明可选依赖，消费方按需引入 runtime 实现
