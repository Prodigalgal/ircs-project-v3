# 028-20-01 AsyncLogger / Disruptor runtime parity

状态：完成

父任务：`tasks/in-progress/028-20-post-parity-runtime-platform-breakdown.md`

## 目标

按已拍板决策对全业务服务启用 V1 风格 Log4j2 AsyncLogger / Disruptor。`platform/ircs-migrator`
保持当前同步日志，不纳入本切片。

## 影响范围

- `build.gradle`
- `shared/ircs-common/src/main/resources/log4j2-spring.xml`
- `docs/decisions/005-post-parity-runtime-platform-decisions.md`
- `tasks/in-progress/028-20-post-parity-runtime-platform-breakdown.md`
- `tasks/index.md`
- `docs/index.md`

## 验收标准

- `services:*` runtimeClasspath 包含 `com.lmax:disruptor:3.4.4`。
- 共享 logging config 包含 V1 关键 logger：
  - `com.prodigalgal.ircs`
  - `org.springframework`
  - `ingestion-tracer`
  - `org.apache.hc.client5.http.impl.io.DefaultHttpClientConnectionOperator`
- `includeLocation=false`。
- 代表 service 构建通过。
- 不新增 K8S HTTPRoute、Service 或运行时资源声明。

## 验证

已执行：

```powershell
.\gradlew.bat --no-daemon :services:ircs-config-service:dependencyInsight --configuration runtimeClasspath --dependency disruptor
.\gradlew.bat --no-daemon :services:ircs-config-service:bootJar :services:ircs-search-service:bootJar
jar tf shared\ircs-common\build\libs\ircs-common-0.1.0-SNAPSHOT.jar | Select-String "log4j2-spring.xml"
.\gradlew.bat --no-daemon :services:ircs-admin-bff:bootJar :services:ircs-portal-bff:bootJar :services:ircs-normalization-worker:bootJar :services:ircs-storage-service:bootJar
```

结果：

```text
LOG4J2_XML_OK
com.lmax:disruptor:3.4.4 present on services:ircs-config-service runtimeClasspath
shared ircs-common jar contains log4j2-spring.xml
config-service/search-service bootJar BUILD SUCCESSFUL
admin-bff/portal-bff/normalization-worker/storage-service bootJar BUILD SUCCESSFUL
```

## 结论

全业务服务通过 `serviceProjects` 统一获得 Disruptor runtime dependency，并通过
`shared:ircs-common` 获得 V1 风格 async logger 配置。`platform/ircs-migrator` 未接入本配置，
符合本轮决策。

## 后置

- K8S live rollout 后需要检查容器内 `IRCS_LOG_PATH` / `LOG_PATH` 默认落点和 ephemeral storage。
- 全链路 `traceId/correlationId` MDC 强制传播由后续 `028-20` trace 切片补齐。
