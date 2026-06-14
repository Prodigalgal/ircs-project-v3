# V1 config environment map

Source:

```text
D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-config\01-secrets.yaml
D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-config\02-configmap.yaml
D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-config\06-backend-deployment.yaml
```

This document records key names only. Secret values must stay in Kubernetes
Secrets and must not be copied into docs.

## Shared ConfigMap keys

```text
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
```

## Shared Secret keys

```text
DB_PASSWORD
VALKEY_PASSWORD
RABBITMQ_PASSWORD
ELASTICSEARCH_PASSWORD
ADMIN_PASSWORD
KIBANA_PASSWORD
```

## V1 Deployment env names

```text
SPRING_DATASOURCE_URL        <- DB_URL
SPRING_DATASOURCE_PASSWORD   <- DB_PASSWORD
SPRING_DATA_REDIS_HOST       <- VALKEY_HOST
SPRING_DATA_REDIS_PASSWORD   <- VALKEY_PASSWORD
SPRING_RABBITMQ_HOST         <- RABBITMQ_HOST
SPRING_RABBITMQ_PASSWORD     <- RABBITMQ_PASSWORD
ELASTICSEARCH_URIS           <- ELASTICSEARCH_URIS
ELASTICSEARCH_USERNAME       <- literal elastic
ELASTICSEARCH_PASSWORD       <- ELASTICSEARCH_PASSWORD
OTEL_EXPORTER_OTLP_ENDPOINT  <- OTEL_ENDPOINT
SECURITY_ADMIN_PASSWORD      <- ADMIN_PASSWORD
APP_STORAGE_R2_BUCKET_NAME   <- R2_BUCKET_NAME
APP_STORAGE_R2_PUBLIC_DOMAIN <- R2_PUBLIC_DOMAIN
APP_MAIL_HOST                <- MAIL_HOST
APP_MAIL_PORT                <- MAIL_PORT
APP_MAIL_FROM                <- MAIL_FROM
```

## Dev isolation policy

V3 reuses the V1 key names, but dev values must point at dev dependencies.
Worker services must not consume queues from `ircs-system`.

```text
              +--------------------+
              | ircs-system        |
              | production runtime |
              +---------+----------+
                        ^
                        |
        no dev queue consumers, no dev HTTPRoute
                        |
              +---------+----------+
              | ircs-dev           |
              | dev services only  |
              +---------+----------+
                        |
       +----------------+----------------+
       |                |                |
       v                v                v
 postgres-svc     rabbitmq-svc      valkey-svc
 ircs-dev         ircs-dev          ircs-dev
```

Default dev config therefore rewrites internal hosts to `*.ircs-dev.svc.cluster.local`.
External side effects such as mail delivery and object storage writes must stay
disabled or use explicit dev credentials until that service has its own cutover
plan.
