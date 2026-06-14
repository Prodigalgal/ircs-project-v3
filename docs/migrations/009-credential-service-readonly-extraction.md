# 009 credential-service readonly extraction

Status: credential-service read-only metadata API deployed and smoke-tested in
`ircs-dev`; provider lease extension is documented in slice 010.

## Target service

`services/ircs-credential-service`

## V1 source area

```text
modules/system credentials/key pool
sys_credentials
provider credential pool lookups
```

## V3 files created

```text
services/ircs-credential-service/src/main/resources/application.yaml
services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/CredentialController.java
services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/CredentialRecord.java
services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/CredentialRepository.java
services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/CredentialSanitizer.java
services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/CredentialService.java
services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/CredentialSummary.java
services/ircs-credential-service/src/test/java/com/prodigalgal/ircs/credential/CredentialControllerTest.java
services/ircs-credential-service/src/test/java/com/prodigalgal/ircs/credential/CredentialSanitizerTest.java
services/ircs-credential-service/src/test/java/com/prodigalgal/ircs/credential/CredentialServiceTest.java
deploy/k8s/dev/credential-service-dev.yaml
```

## Boundary decision

This slice gives `sys_credentials` a real service boundary without taking on
write or leasing semantics yet. The first API is intentionally read-only and
redacted:

```text
GET /api/v1/credentials
  filters: provider, enabled, limit
  returns: credential metadata, payloadKeys, fingerprintSuffix
  never returns: payload values, api keys, tokens

GET /api/v1/credentials/{id}
  returns the same redacted shape or 404
```

This original read-only slice kept metadata-worker provider traffic unchanged.
Slice 010 adds the internal provider lease API and moves metadata-worker TMDB
credential lookup from direct DB access to a credential-service client.

## Runtime diagram

```text
                         ircs-dev namespace

       +-----------------------------+
       | internal HTTP client         |
       | kubectl exec / future svc    |
       +---------------+-------------+
                       |
                       v
       +-----------------------------+
       | ircs-credential-service      |
       | /api/v1/credentials          |
       | - validate query params      |
       | - read sys_credentials       |
       | - redact payload values      |
       +---------------+-------------+
                       |
                       v
       +-----------------------------+
       | PostgreSQL                   |
       | sys_credentials              |
       +-----------------------------+

       No HTTPRoute in ircs-dev.
       No queue ownership in this slice.
```

## Dev deployment

```text
image:   harbor.mnnu.eu.org/ircs/ircs-credential-service:dev
arch:    linux/arm64
service: ClusterIP only
request: 25m/128Mi
limit:   500m/512Mi
routes:  none in ircs-dev
```

The service uses JDBC only. The initial skeleton dependency on JPA/Redis was
removed to keep the service smaller and avoid unused runtime dependencies.

## Verification

Local tests:

```text
.\gradlew.bat :services:ircs-credential-service:test
  PASS

.\gradlew.bat build
  PASS, BUILD SUCCESSFUL in 12s
```

Manifest validation:

```text
kubectl apply --dry-run=server -f deploy\k8s\dev\credential-service-dev.yaml
  PASS
```

Image build:

```text
.\scripts\docker\push-project-image-jib.ps1 -ProjectPath ':services:ircs-credential-service' -ImageName ircs-credential-service -Tag dev -Architecture arm64 -Os linux
  PASS, pushed harbor.mnnu.eu.org/ircs/ircs-credential-service:dev for linux/arm64
```

Deployment:

```text
kubectl apply -f deploy\k8s\dev\credential-service-dev.yaml
kubectl rollout status deployment/ircs-credential-service -n ircs-dev --timeout=240s
  PASS, deployment/ircs-credential-service Ready 1/1
```

Health smoke:

```text
kubectl exec -n ircs-dev deployment/ircs-credential-service -- \
  sh -c 'wget -qO- http://localhost:8080/actuator/health/readiness'

{"status":"UP"}
```

Redaction smoke:

```text
Inserted temporary sys_credentials row:
  id=de0a6fd9-f07d-4201-bf92-279b6c9f099d
  provider=TMDB
  name=codex-redaction-smoke
  payload contains api_key and note keys

GET /api/v1/credentials?provider=TMDB&enabled=true&limit=5
  response included payloadKeys ["api_key","note"]
  response included fingerprintSuffix "89abcdef"
  response did not include the fake payload value
```

Cleanup:

```text
Deleted temporary sys_credentials row.

GET /api/v1/credentials?provider=TMDB&enabled=true&limit=5
  []
```

Resource profile after rollout:

```text
pods:            11 / 20
services:        12 / 20
requests.cpu:    950m / 4
requests.memory: 2432Mi / 8Gi
limits.cpu:      5750m / 8
limits.memory:   6656Mi / 16Gi
HTTPRoute:       none in ircs-dev
queues:          no new consumers or messages
```

## Issue found during smoke

The first API smoke returned HTTP 500 because PostgreSQL could not infer the
type of nullable prepared parameters in this pattern:

```text
(:enabled is null or enabled = :enabled)
```

`CredentialRepository` now builds optional filters dynamically, so null filters
do not become untyped SQL parameters.

## Remaining work

- Add credential write APIs behind an internal/admin access model.
- Harden internal/admin access for both redacted metadata and lease APIs.
- Add audit events for credential reads/writes before exposing any write path.
