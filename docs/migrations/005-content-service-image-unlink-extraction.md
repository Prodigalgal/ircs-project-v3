# 005 content-service image unlink extraction

Status: image unlink consumer deployed and end-to-end smoke-tested in `ircs-dev`

## Target service

`services/ircs-content-service`

## V1 source files

```text
modules/content/consumer/ImageUnlinkConsumer.java
modules/content/repository/RawVideoRepository.java
modules/content/repository/UnifiedVideoMaintenanceRepository.java
modules/storage/service/impl/CoverImageServiceImpl.java
infrastructure/queue/QueueTopic.java
```

## V3 files created

```text
services/ircs-content-service/build.gradle
services/ircs-content-service/src/main/resources/application.yaml
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/image/ContentUuidMessageParser.java
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/image/CoverImageReferenceRepository.java
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/image/CoverImageUnlinkResult.java
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/image/CoverImageUnlinkService.java
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/image/CoverImageUnlinkedPublisher.java
services/ircs-content-service/src/main/java/com/prodigalgal/ircs/content/image/ImageUnlinkConsumer.java
services/ircs-content-service/src/test/java/com/prodigalgal/ircs/content/image/ContentUuidMessageParserTest.java
services/ircs-content-service/src/test/java/com/prodigalgal/ircs/content/image/CoverImageReferenceRepositoryTest.java
services/ircs-content-service/src/test/java/com/prodigalgal/ircs/content/image/CoverImageUnlinkServiceTest.java
deploy/k8s/dev/content-service-dev.yaml
```

## Boundary decision

This slice moves the content-side unlink worker out of the V1 monolith:

```text
RabbitMQ q.storage.image_unlink
  -> ircs-content-service
  -> shared PostgreSQL raw_videos/unified_videos reference cleanup
  -> publish image.event.unlinked
  -> ircs-storage-service final physical/metadata delete
```

The service now owns:

- legacy UUID message parsing for `q.storage.image_unlink`
- raw video cover image reference cleanup
- unified video cover image reference cleanup
- `image.event.unlinked` event publishing after unlink work
- invalid message rejection without requeue, so poison messages go to DLQ

The service does not yet own:

- content HTTP APIs
- full raw/unified video aggregate logic
- cover image upload/download
- `q.storage.image` or `q.storage.avatar_sync`

This keeps the migration narrow while completing the deletion chain started by
the storage-service slice.

## Queues

```text
Exchange: x.storage
Routing:  image.cmd.unlink
Queue:    q.storage.image_unlink
Owner:    ircs-content-service
Payload:  legacy UUID

Exchange: x.storage
Routing:  image.event.unlinked
Queue:    q.storage.image_delete
Owner:    ircs-storage-service
Payload:  legacy UUID
```

## Dev dependencies

PostgreSQL is namespace-local in dev:

```text
service:
  postgres-svc.ircs-dev.svc.cluster.local:5432
database:
  ircs
tables:
  raw_videos
  unified_videos
```

RabbitMQ is namespace-local in dev:

```text
service:
  rabbitmq-svc.ircs-dev.svc.cluster.local:5672
exchange:
  x.storage
queue:
  q.storage.image_unlink
```

The dev deployment enables queue consumption:

```text
APP_CONTENT_LISTENER_ENABLED=true
```

Do not point content-service at `postgres-svc` or `rabbitmq-svc` in
`ircs-system`.

## Verification

Local tests:

```text
.\gradlew.bat :services:ircs-content-service:test
  PASS
```

Root build:

```text
.\gradlew.bat build
  PASS, BUILD SUCCESSFUL in 8s
```

Image build:

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath :services:ircs-content-service -ImageName ircs-content-service -Tag dev -Architecture arm64 -Os linux
  PASS, pushed harbor.mnnu.eu.org/ircs/ircs-content-service:dev for linux/arm64
```

Deployment:

```text
kubectl apply -f deploy\k8s\dev\content-service-dev.yaml
kubectl rollout status deployment/ircs-content-service -n ircs-dev --timeout=240s
  PASS, deployment/ircs-content-service Ready 1/1
```

Startup checks:

```text
ircs-content-service:
  Ready 1/1
  Spring Boot started on port 8080
  RabbitMQ connected to rabbitmq-svc.ircs-dev.svc.cluster.local:5672

rabbitmqctl list_queues name messages consumers:
  q.storage.image_unlink      0 messages, 1 consumer
  q.storage.image_unlink.dlq  0 messages, 0 consumers after cleanup
  q.storage.image_delete      0 messages, 1 consumer
  q.storage.image_delete.dlq  0 messages, 0 consumers
```

End-to-end smoke:

```text
Inserted temporary rows:
  source_domains.id=275c209f-a0d9-4a52-88a0-2db7b5a4233e
  cover_images.id=307b726f-d59f-4eb1-bf29-9647bbba29c0
  raw_videos.id=001f90eb-7233-4358-a7fd-b7c03f6f5b0c
  unified_videos.id=11a9eba7-486e-447b-8e4e-65e274301304

Published legacy UUID to x.storage/image.cmd.unlink:
  Message published and routed successfully

PostgreSQL after content-service + storage-service chain:
  cover_images count for image id      -> 0
  raw_videos non-null cover reference   -> 0
  unified_videos non-null cover ref     -> 0
  raw_videos temporary row still exists -> 1
  unified temporary row still exists    -> 1

Temporary raw/unified/source_domain rows:
  deleted after smoke
```

Poison message behavior:

```text
Invalid payload:
  codex-bad-content-unlink-smoke

Result:
  rejected with AmqpRejectAndDontRequeueException
  moved to q.storage.image_unlink.dlq

After cleanup:
  q.storage.image_unlink      0 messages, 1 consumer
  q.storage.image_unlink.dlq  0 messages, 0 consumers
```

Final dev state:

```text
deployments:
  ircs-content-service Ready 1/1
  ircs-storage-service Ready 1/1

resource usage:
  pods:            8 / 20
  services:        9 / 20
  requests.cpu:    750m / 4
  requests.memory: 2Gi / 8Gi
  limits.cpu:      4500m / 8
  limits.memory:   5376Mi / 16Gi

No public HTTPRoute in ircs-dev.
```

## Remaining work

- Move content HTTP read/write APIs after deciding the first user-facing route
  cutover path.
- Split the content read model away from the shared database after catalog and
  identity ownership are clearer.
- Add a repeatable smoke script for the unlink/delete chain.
- Continue storage-service with `q.storage.image` image download processing or
  start the metadata-worker slice.
