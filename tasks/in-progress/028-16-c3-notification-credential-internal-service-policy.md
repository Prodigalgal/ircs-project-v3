# 028-16-C3 notification MAIL credential INTERNAL_SERVICE policy task spec

状态：实现完成，完整验收受范围外 send history H2 schema 失败阻塞

父任务：

- `tasks/in-progress/028-16-c3-shared-outbound-http-client-parity.md`

上游来源：

- `tasks/in-progress/028-16-c3-shared-outbound-http-client-parity.md`
- `tasks/done/028-16-c3-s4a-metadata-tmdb-credential-internal-service-policy.md`

## 背景

C3-S4a 已完成 shared `INTERNAL_SERVICE` policy，并将
`metadata-worker/TmdbCredentialRepository` 从直接 `RestClient` 迁到 shared outbound
contract。`notification-worker/CredentialServiceMailCredentialRepository` 仍直接使用
Spring `RestClient` 从固定 credential-service base URL 租用 `MAIL` credential，需要按同一
内部服务策略接入，避免内部 caller 继续绕过统一 URL shape、timeout、redirect never 和 retry
边界。

## 目标

- 将 notification-worker 的 MAIL credential lease caller 迁到 shared
  `OutboundHttpClient` + `OutboundHttpPolicy.internalService()`。
- 保留固定 credential-service base URL、`/internal/credentials/providers/MAIL/leases`
  path、`requiredPayloadKey=username`、`limit`、`X-IRCS-INTERNAL-TOKEN` header。
- 保留空 credential payload 过滤：必须同时存在 `username` 和 `password`。
- 保留 credential-service 失败转 `MailCredentialLeaseException`，不打印真实 secret payload。
- 使用 fake transport/fake resolver 做 no-real-network 测试，覆盖 invalid internal base URL
  fail-before-send、token/header、失败状态映射和空 payload 过滤中的至少两个。

## 非目标

- 不修改 notification send history 或 ops query。
- 不修改 storage strict download、normalization、BFF proxy。
- 不接入真实 credential-service，不触碰真实 credential secrets。
- 不实现 circuit breaker。
- 不更新 `docs/index.md`、`tasks/index.md`、`docs/reports/089` 或
  `docs/migrations/089`。

## 输入

```text
services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/mail/CredentialServiceMailCredentialRepository.java
services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/mail/NotificationMailProperties.java
services/ircs-notification-worker/src/main/resources/application.yaml
services/ircs-notification-worker/src/test/java/com/prodigalgal/ircs/notification/mail/CredentialServiceMailCredentialRepositoryTest.java
shared/ircs-common/src/main/java/com/prodigalgal/ircs/common/outbound/**
```

## 输出

```text
CredentialServiceMailCredentialRepository shared INTERNAL_SERVICE adapter
notification mail credential-service request timeout property
focused no-real-network tests
this task spec with implementation and verification notes
```

## 影响范围

- `ircs-notification-worker` MAIL credential lease repository 与配置属性。
- `shared:ircs-common` outbound policy 仅复用；除非测试暴露缺口，不新增 shared 行为。

不包含 notification send history、SMTP delivery、Rabbit listener、storage、normalization 或
BFF。

## 依赖

- `shared:ircs-common`
- `shared:ircs-contracts`
- notification-worker Jackson / Spring Boot service dependencies

## 风险

- 误用 `PUBLIC_FETCH` 会阻断 cluster DNS 或 localhost dev base URL；必须使用
  `INTERNAL_SERVICE`。
- 异常日志或断言不能包含真实 `username/password` payload。
- 迁移后要保留空 payload 过滤，避免缺少 password 的 lease 被发给 SMTP。

## 验收标准

```text
C3-notification MARK 1/5 MAIL credential repository uses shared INTERNAL_SERVICE policy
C3-notification MARK 2/5 fixed path/query/token header and request timeout are preserved
C3-notification MARK 3/5 invalid internal base URL fails before transport send
C3-notification MARK 4/5 empty or incomplete credential payload is filtered
C3-notification MARK 5/5 shared and notification-worker tests pass without real network
```

## 测试边界

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-notification-worker:test --rerun-tasks
```

只使用 fake resolver/fake transport 和单元测试；不访问公网、不访问真实 credential-service、不打印
credential payload。

## 实现结果

- `CredentialServiceMailCredentialRepository` 已从 Spring `RestClient` 直接调用迁到 shared
  `OutboundHttpClient` + `OutboundHttpPolicy.internalService()`。
- 生产路径使用 `DefaultOutboundAddressResolver`、`OutboundUrlPolicy` 和
  `JdkOutboundTransport`，transport timeout 来自
  `app.notification.mail.credential-service.request-timeout`，默认 `10s`。
- 保留固定 credential-service base URL 拼接
  `/internal/credentials/providers/MAIL/leases?requiredPayloadKey=username&limit=...`。
- 保留 `X-IRCS-INTERNAL-TOKEN` header 注入；token 为空时不发送 header。
- 保留 `username` / `password` 双字段过滤，缺 password 或空 payload 不会返回给 SMTP。
- 非 2xx、IO、URL shape validation 和 interrupt 均映射为
  `MailCredentialLeaseException("Unable to lease MAIL credentials from credential-service")`；
  业务空凭据仍映射为 `MAIL credential is not available`。

## 验证结果

focused no-real-network 验证通过：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-notification-worker:test --tests "com.prodigalgal.ircs.notification.mail.CredentialServiceMailCredentialRepositoryTest" --rerun-tasks
BUILD SUCCESSFUL in 17s
```

覆盖点：

- invalid internal base URL `http://token@credential-service` 在 transport send 前失败。
- token/header、固定 path/query、`INTERNAL_SERVICE` policy type 和 request timeout。
- 503 失败状态经 shared retry 后映射为 `MailCredentialLeaseException`。
- 缺少 password 的 lease 被过滤，不触碰或打印真实 credential secrets。

完整验收命令已运行，但被本任务禁止修改范围内的 send history/schema 测试阻塞：

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-notification-worker:test --rerun-tasks
FAILED

JdbcMailSendHistoryWriterTest > writesFailedHistoryWithSensitiveFailureRedaction()
JdbcMailSendHistoryWriterTest > writesSentHistoryWithoutCredentialSecret()
NotificationMailSendHistorySchemaTest > schemaSupportsWriterColumnsAndOpsQueryIndexes()

org.h2.jdbc.JdbcSQLNonTransientException: Unknown data type: "TIMESTAMPTZ"
```

该失败来自 `notification_mail_send_history.sql` 在 H2 PostgreSQL mode 下不识别
`TIMESTAMPTZ`，属于本任务明确禁止触碰的 notification send history/ops query 范围；本切片未修改
send history/schema 代码。

## MARK

```text
C3-notification MARK 1/5 MAIL credential repository uses shared INTERNAL_SERVICE policy: done
C3-notification MARK 2/5 fixed path/query/token header and request timeout are preserved: done
C3-notification MARK 3/5 invalid internal base URL fails before transport send: done
C3-notification MARK 4/5 empty or incomplete credential payload is filtered: done
C3-notification MARK 5/5 shared and notification-worker tests pass without real network: blocked by forbidden send history H2 TIMESTAMPTZ failure; focused credential test passed
```

## 遗留问题

- task-service scraper execution caller、content-service internal scraper caller 和 identity-service
  avatar storage client 仍未接 `INTERNAL_SERVICE`。
- portal-bff 仍未接 `BFF_PROXY` target base URL validation。
- circuit breaker 仍未落代码。
- storage `IMAGE_DOWNLOAD_STRICT` shared adapter 仍未接入。
- notification-worker 全量测试当前被 send history H2 schema 兼容性问题阻塞，需由后续
  send history/schema 范围任务处理。

## 当前状态

本切片实现和 focused no-real-network 测试已完成；由于用户写入范围明确禁止修改
notification send history/ops query，完整 notification-worker 验收失败暂留在本任务记录中，待
主线程或后续专门任务处理后再移动到 `tasks/done/`。
