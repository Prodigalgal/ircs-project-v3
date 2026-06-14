# 001 notification-worker extraction

Status: extracted, dev deployed

## Target service

`services/ircs-notification-worker`

## V1 source files

```text
modules/system/consumer/MailConsumer.java
modules/system/model/dto/MailMessageDTO.java
modules/system/service/MailService.java
modules/system/service/impl/MailServiceImpl.java
src/main/resources/templates/mail/activation.html
src/main/resources/templates/mail/reset-password.html
```

## V3 files created

```text
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/notification/MailMessageDTO.java
services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/mail/MailConsumer.java
services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/mail/NotificationMailService.java
services/ircs-notification-worker/src/main/java/com/prodigalgal/ircs/notification/mail/SmtpNotificationMailService.java
services/ircs-notification-worker/src/main/resources/application.yaml
services/ircs-notification-worker/Dockerfile
deploy/k8s/dev/notification-worker-dev.yaml
scripts/k8s/sync-dev-secrets.ps1
scripts/docker/build-service-image.ps1
```

## Boundary decision

The V1 mail sender depends on `SystemConfigService`, `KeyPoolManager`, and
`MailCredentialHandler`. V3 notification-worker starts with environment-driven
SMTP settings so the service can run independently. Credential rotation should
be reintroduced through `ircs-credential-service`, not by importing the whole
V1 system module.

## Queue

```text
Exchange: x.notification
Queue:    q.notification.mail
Owner:    ircs-notification-worker
Payload:  MailMessageDTO
```

## Remaining work

- Add a lightweight RabbitMQ listener smoke test once testcontainers or a local
  RabbitMQ profile is selected.
- Update V1 publishers to use the shared contract package during cutover.

## Verification

```text
.\gradlew.bat build
  PASS

.\gradlew.bat :shared:ircs-messaging:test :services:ircs-notification-worker:build
  PASS

java -jar services/ircs-notification-worker/build/libs/ircs-notification-worker-0.1.0-SNAPSHOT.jar \
  --server.port=18080 \
  --spring.rabbitmq.listener.simple.auto-startup=false \
  --app.notification.mail.enabled=false

GET /actuator/health/liveness
  {"status":"UP"}

.\scripts\k8s\sync-dev-secrets.ps1
  PASS, ircs-dev-secrets contains 6 whitelisted keys

kubectl apply --dry-run=server -f deploy\k8s\dev\notification-worker-dev.yaml
  PASS

.\scripts\k8s\sync-image-pull-secret.ps1
  PASS, ircs-dev/harbor-secret created

.\scripts\docker\push-service-image-jib.ps1 -Service ircs-notification-worker -Tag dev -Architecture arm64 -Os linux
  PASS, pushed harbor.mnnu.eu.org/ircs/ircs-notification-worker:dev for linux/arm64

kubectl apply -f deploy\k8s\dev\rabbitmq-dev.yaml
  PASS, statefulset/rabbitmq Ready 1/1

kubectl apply -f deploy\k8s\dev\notification-worker-dev.yaml
  PASS, deployment/ircs-notification-worker Ready 1/1

GET /actuator/health/liveness via port-forward
  {"status":"UP"}

GET /internal/service-info via port-forward
  {"service":"ircs-notification-worker","status":"UP"}
```

## Environment mapping

The worker is aligned with V1 `ircs-config`:

```text
ConfigMap keys:
  DB_URL
  VALKEY_HOST
  RABBITMQ_HOST
  ELASTICSEARCH_URIS
  OTEL_ENDPOINT
  R2_BUCKET_NAME
  R2_PUBLIC_DOMAIN
  MAIL_HOST
  MAIL_PORT
  MAIL_FROM
  APP_MAIL_ENABLED

Secret keys:
  DB_PASSWORD
  VALKEY_PASSWORD
  RABBITMQ_PASSWORD
  ELASTICSEARCH_PASSWORD
  ADMIN_PASSWORD
  KIBANA_PASSWORD
```

The application also accepts the explicit V1 Deployment env names:

```text
SPRING_RABBITMQ_HOST
SPRING_RABBITMQ_PASSWORD
APP_MAIL_HOST
APP_MAIL_PORT
APP_MAIL_FROM
APP_MAIL_ENABLED
```

## RabbitMQ topology

`shared/ircs-messaging` now declares the V1-compatible RabbitMQ topology:

```text
Topic exchanges:
  x.ingest
  x.process
  x.storage
  x.notification
  x.interaction
  x.search
  x.dlx
  x.domain.events

Queues:
  all QueueTopic queues plus matching .dlq queues
```

The topology can be disabled per service:

```text
--ircs.messaging.rabbit-topology.enabled=false
```

## Dev deployment

The dev manifest targets `ircs-dev` only and creates no public HTTPRoute:

```text
deploy/k8s/dev/notification-worker-dev.yaml
```

The dev manifest is conflict-safe by default:

```text
RABBITMQ_HOST -> rabbitmq-svc.ircs-dev.svc.cluster.local
APP_MAIL_ENABLED -> false
HTTPRoute -> none
```

The dev worker is now deployed in `ircs-dev` against namespace-local RabbitMQ:

```text
rabbitmq-svc.ircs-dev.svc.cluster.local:5672
q.notification.mail consumers: 1
```

Before applying the workload, sync whitelisted V1 secret keys into `ircs-dev`:

```powershell
.\scripts\k8s\sync-dev-secrets.ps1
```

Do not apply the worker against `ircs-system` RabbitMQ.

## ARM64 image build

The Kubernetes node architecture is `linux/arm64`, while the local development
machine is x86. Use the Jib script by default because it does not require a
local Docker daemon and explicitly pushes an ARM64 image:

```powershell
.\scripts\docker\push-service-image-jib.ps1 -Service ircs-notification-worker -Tag dev -Architecture arm64 -Os linux
```

The old Docker path now uses `docker buildx --platform linux/arm64` by default,
but it still needs Docker Desktop to be running:

```powershell
.\scripts\docker\build-service-image.ps1 -Service ircs-notification-worker -Tag dev -Push
```

Build and push the dev image:

```powershell
.\scripts\docker\push-service-image-jib.ps1 -Service ircs-notification-worker -Tag dev -Architecture arm64 -Os linux
```

Then apply:

```powershell
kubectl apply -f deploy\k8s\dev\notification-worker-dev.yaml
```
