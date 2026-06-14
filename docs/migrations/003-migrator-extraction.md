# 003 migrator extraction

Status: deployed and verified in `ircs-dev`

## Target module

`platform/ircs-migrator`

## V1 source

V1 runs schema migration inside the `ircs-backend` Deployment init containers:

```text
D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-config\06-backend-deployment.yaml
  migration-extractor
  liquibase-unlocker
  liquibase-runner
```

The V1 changelog entrypoint is:

```text
D:\WorkSpace\Project\ircs\ircs-project-v1\ircs-backend\src\main\resources\db\changelog\db.changelog-master.yaml
```

## V3 files created

```text
platform/ircs-migrator/build.gradle
platform/ircs-migrator/src/main/java/com/prodigalgal/ircs/migrator/IrcsMigratorApplication.java
platform/ircs-migrator/src/main/resources/application.yaml
platform/ircs-migrator/src/main/resources/db/changelog/**
deploy/k8s/dev/migrator-job-dev.yaml
scripts/docker/push-project-image-jib.ps1
```

## Boundary decision

Schema migration is now a platform Job, not a service startup concern.

```text
build images
  -> run ircs-migrator Job
  -> wait for schema success
  -> rollout app services
```

`ircs-migrator` owns only Liquibase execution. It does not expose HTTP routes,
does not consume queues, and does not run continuously.

The V1 force-unlock behavior was not copied into the default V3 Job. A forced
Liquibase unlock should be an explicit maintenance action, not a normal deploy
side effect.

## Dev Job

```text
deploy/k8s/dev/migrator-job-dev.yaml
image: harbor.mnnu.eu.org/ircs/ircs-migrator:dev
resources:
  request 50m/128Mi
  limit   500m/512Mi
ttlSecondsAfterFinished: 300
```

The image is built with Jib for the ARM64 K8S node:

```powershell
.\scripts\docker\push-project-image-jib.ps1 `
  -ProjectPath ':platform:ircs-migrator' `
  -ImageName ircs-migrator `
  -Tag dev `
  -Architecture arm64 `
  -Os linux
```

## Search-service schema note

The V1 search outbox changelog creates `search_sync_tasks`, not
`search_outbox`.

```text
db/changelog/2026/02/add-search-outbox.sql
  CREATE TABLE search_sync_tasks
```

Use this table name when porting the search relay/reconciliation slice.

## Verification

```text
All changelog includes resolved.

.\gradlew.bat :platform:ircs-migrator:build
  PASS

kubectl apply --dry-run=server -f deploy\k8s\dev\migrator-job-dev.yaml
  PASS

.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':platform:ircs-migrator' -ImageName ircs-migrator -Tag dev -Architecture arm64 -Os linux
  PASS, pushed harbor.mnnu.eu.org/ircs/ircs-migrator:dev for linux/arm64

kubectl apply -f deploy\k8s\dev\migrator-job-dev.yaml
  PASS

kubectl wait --for=condition=complete job/ircs-migrator -n ircs-dev --timeout=300s
  PASS, job.batch/ircs-migrator condition met

Liquibase summary:
  Run:               122
  Previously run:     0
  Total change sets: 122
  Update command completed successfully

PostgreSQL checks:
  select count(*) from databasechangelog;
    122

  select count(*) from pg_tables where schemaname='public';
    45

  select extname from pg_extension order by extname;
    pg_trgm
    plpgsql

  select tablename from pg_tables where schemaname='public' and tablename like '%search%';
    magnet_search_jobs
    search_sync_tasks
```
