# 004 storage-service extraction

Status: image physical deletion consumer deployed and smoke-tested in `ircs-dev`

## Target service

`services/ircs-storage-service`

## V1 source files

```text
modules/storage/consumer/ImageDeletionConsumer.java
modules/storage/consumer/ImageUnlinkConsumer.java
modules/storage/consumer/AvatarSyncConsumer.java
modules/storage/service/impl/CoverImageServiceImpl.java
modules/storage/model/entity/CoverImage.java
infrastructure/queue/QueueTopic.java
```

## V3 files created

```text
services/ircs-storage-service/build.gradle
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/IrcsStorageServiceApplication.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/CoverImageDeletionRepository.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/CoverImagePhysicalDeletionService.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/CoverImageRecord.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/CoverImageStorageType.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/ImageDeletionConsumer.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/LocalObjectStorage.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/R2ObjectStorage.java
services/ircs-storage-service/src/main/java/com/prodigalgal/ircs/storage/image/StorageUuidMessageParser.java
services/ircs-storage-service/src/main/resources/application.yaml
services/ircs-storage-service/src/test/java/com/prodigalgal/ircs/storage/image/CoverImagePhysicalDeletionServiceTest.java
services/ircs-storage-service/src/test/java/com/prodigalgal/ircs/storage/image/LocalObjectStorageTest.java
services/ircs-storage-service/src/test/java/com/prodigalgal/ircs/storage/image/StorageUuidMessageParserTest.java
deploy/k8s/dev/storage-service-dev.yaml
```

## Boundary decision

This first storage slice moves only the final physical deletion worker out of
the V1 monolith:

```text
RabbitMQ q.storage.image_delete
  -> ircs-storage-service
  -> shared PostgreSQL cover_images metadata during transition
  -> local object path or R2 object delete when enabled
```

The service now owns:

- legacy UUID message parsing for `q.storage.image_delete`
- cover image metadata lookup and delete through JDBC
- local object deletion guarded by a storage-root traversal check
- optional R2 object deletion behind configuration
- invalid message rejection without requeue, so poison messages go to DLQ

The service does not yet own:

- `q.storage.image`
- `q.storage.image_unlink`
- `q.storage.avatar_sync`
- upload/download HTTP endpoints
- producer-side unlink events

`q.storage.image_unlink` should move with the next content ownership slice,
because it detaches raw/unified video references before emitting
`image.event.unlinked`. Only after that event exists should
`ircs-storage-service` run final physical deletion.

The dev deployment keeps R2 disabled:

```text
APP_STORAGE_LISTENER_ENABLED=true
APP_STORAGE_R2_ENABLED=false
```

Local storage in dev uses `emptyDir` at `/app/storage` to avoid adding another
PVC while the environment is still only used for migration and smoke testing.

## Queues

```text
Exchange: x.storage
Routing:  image.event.unlinked
Queue:    q.storage.image_delete
Owner:    ircs-storage-service
Payload:  legacy UUID

Exchange: x.storage
Routing:  image.cmd.unlink
Queue:    q.storage.image_unlink
Owner:    content-service in next slice
Payload:  V1 unlink command

Exchange: x.storage
Routing:  image.process
Queue:    q.storage.image
Owner:    storage-service later, not this slice

Exchange: x.storage
Routing:  avatar.sync
Queue:    q.storage.avatar_sync
Owner:    storage-service later, not this slice
```

## Dev dependencies

PostgreSQL is namespace-local in dev:

```text
service:
  postgres-svc.ircs-dev.svc.cluster.local:5432
database:
  ircs
```

RabbitMQ is namespace-local in dev:

```text
service:
  rabbitmq-svc.ircs-dev.svc.cluster.local:5672
exchange:
  x.storage
queue:
  q.storage.image_delete
```

Do not point storage-service at `postgres-svc` or `rabbitmq-svc` in
`ircs-system`.

## Verification

Local tests:

```text
.\gradlew.bat :services:ircs-storage-service:test
  PASS
```

Root build:

```text
.\gradlew.bat build
  PASS, BUILD SUCCESSFUL in 10s
```

Image build:

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath :services:ircs-storage-service -ImageName ircs-storage-service -Tag dev -Architecture arm64 -Os linux
  PASS, pushed harbor.mnnu.eu.org/ircs/ircs-storage-service:dev for linux/arm64
```

Deployment:

```text
kubectl apply -f deploy\k8s\dev\storage-service-dev.yaml
kubectl rollout status deployment/ircs-storage-service -n ircs-dev --timeout=240s
  PASS, deployment/ircs-storage-service Ready 1/1
```

Startup checks:

```text
ircs-storage-service:
  Ready 1/1
  Spring Boot started on port 8080
  RabbitMQ connected to rabbitmq-svc.ircs-dev.svc.cluster.local:5672

rabbitmqctl list_queues name messages consumers:
  q.storage.image_delete      0 messages, 1 consumer
  q.storage.image_delete.dlq  0 messages, 0 consumers after cleanup
  q.storage.image             0 messages, 0 consumers
  q.storage.image_unlink      0 messages, 0 consumers
  q.storage.avatar_sync       0 messages, 0 consumers
```

Smoke deletion:

```text
Inserted temporary source_domains row and cover_images row:
  cover_images.id=91e45085-8c83-4781-b709-d7586d249e6d
  storage_type=EXTERNAL
  status=PENDING_DELETE

Published legacy UUID to x.storage/image.event.unlinked:
  Message published and routed successfully

PostgreSQL:
  select count(*) from cover_images where id='91e45085-8c83-4781-b709-d7586d249e6d'
  -> 0

Temporary source_domains row:
  deleted after smoke
```

Poison message behavior:

```text
Invalid payload:
  codex-bad-storage-smoke

Result:
  rejected with AmqpRejectAndDontRequeueException
  moved to q.storage.image_delete.dlq

After cleanup:
  q.storage.image_delete      0 messages, 1 consumer
  q.storage.image_delete.dlq  0 messages, 0 consumers
```

Final dev state:

```text
deployments:
  ircs-storage-service Ready 1/1

resource usage:
  pods:            7 / 20
  services:        8 / 20
  requests.cpu:    675m / 4
  requests.memory: 1920Mi / 8Gi
  limits.cpu:      4 / 8
  limits.memory:   4864Mi / 16Gi

No public HTTPRoute in ircs-dev.
```

## Remaining work

- `q.storage.image_unlink` moved with content-service in
  `005-content-service-image-unlink-extraction.md`.
- Add the image download/process worker for `q.storage.image` after ownership
  of source-domain and content tables is clearer.
- Add avatar sync/R2 worker after dev secrets and R2 behavior are intentionally
  tested.
- Add a repeatable smoke script for the deletion path to avoid manual
  RabbitMQ CLI quoting.
