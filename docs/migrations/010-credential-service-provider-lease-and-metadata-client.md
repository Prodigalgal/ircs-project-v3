# 010 credential-service provider lease and metadata client

Status: token-protected internal provider credential lease API deployed and
smoke-tested in `ircs-dev`; metadata-worker TMDB credential lookup now goes
through credential-service.

## Target services

```text
services/ircs-credential-service
services/ircs-metadata-worker
shared/ircs-contracts
```

## V3 files created or changed

```text
shared/ircs-contracts/src/main/java/com/prodigalgal/ircs/contracts/credential/ProviderCredentialLease.java
services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/InternalCredentialController.java
services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/CredentialInternalAccessProperties.java
services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/CredentialLeaseMapper.java
services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/CredentialRepository.java
services/ircs-credential-service/src/main/java/com/prodigalgal/ircs/credential/CredentialService.java
services/ircs-credential-service/src/test/java/com/prodigalgal/ircs/credential/CredentialLeaseMapperTest.java
services/ircs-credential-service/src/test/java/com/prodigalgal/ircs/credential/InternalCredentialControllerTest.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/credential/MetadataCredentialServiceProperties.java
services/ircs-metadata-worker/src/main/java/com/prodigalgal/ircs/metadata/provider/tmdb/TmdbCredentialRepository.java
services/ircs-metadata-worker/src/test/java/com/prodigalgal/ircs/metadata/provider/tmdb/TmdbCredentialRepositoryTest.java
services/ircs-metadata-worker/src/main/resources/application.yaml
deploy/k8s/dev/credential-service-dev.yaml
deploy/k8s/dev/metadata-worker-dev.yaml
```

## Boundary decision

`ircs-credential-service` now owns provider credential reads for trusted
internal callers:

```text
GET /internal/credentials/providers/{provider}/leases
  header: X-IRCS-INTERNAL-TOKEN
  query: requiredPayloadKey, limit
  returns: ProviderCredentialLease[]
  includes: scalar secretPayload values, priority, rate limit metadata
  excludes: write, rotation, audit, external/public routing
```

The public read-only metadata API from slice 009 remains redacted:

```text
GET /api/v1/credentials
GET /api/v1/credentials/{id}
```

`ircs-metadata-worker` no longer queries `sys_credentials` for TMDB keys. It
calls the internal credential-service lease endpoint and maps `api_key` values
into its existing TMDB credential pool. `sys_credentials` remains in PostgreSQL
as the shared migration bridge, but direct provider-secret reads are now behind
the credential-service boundary.

If credential-service is unavailable, metadata-worker maps the failure to a
retryable provider failure with code `CREDENTIAL_SERVICE_UNAVAILABLE`.

## Runtime diagram

```text
                               ircs-dev namespace

        +--------------------+           +-----------------------------+
        | RabbitMQ x.process |           | Valkey                      |
        | metadata.fetch.tmdb|           | cred:<credential-id> windows|
        +----------+---------+           +--------------+--------------+
                   |                                    ^
                   v                                    |
        +-----------------------------+                 |
        | q.fetch.metadata.tmdb       |                 |
        | consumer: metadata-worker   |                 |
        +--------------+--------------+                 |
                       |                                |
                       v                                |
        +----------------------------------------------+ |
        | ircs-metadata-worker                         | |
        | - parse MetadataSearchContext                | |
        | - lease TMDB credentials                     |-+
        | - call TMDB only when credentials exist      |
        | - publish EnrichmentResultMsg                |
        +----------+--------------------------+--------+
                   |                          |
                   | internal ClusterIP HTTP  | metadata result
                   v                          v
        +----------------------------+   +-----------------------------+
        | ircs-credential-service    |   | q.enrich.metadata.result    |
        | /internal/.../leases       |   | consumer: result collector  |
        | - read sys_credentials     |   +--------------+--------------+
        +-------------+--------------+                  |
                      |                                 v
                      v                  +-----------------------------+
        +----------------------------+   | result collector            |
        | PostgreSQL                 |   | - update raw_videos         |
        | sys_credentials            |   | - publish search.sync.raw   |
        | raw_videos                 |   +--------------+--------------+
        +----------------------------+                  |
                                                        v
                                         +-----------------------------+
                                         | search-service              |
                                         | Elasticsearch              |
                                         +-----------------------------+

        No HTTPRoute in ircs-dev.
        No new persistent middleware added in this slice.
```

## Dev resource stance

This is a development and integration-test slice. It keeps the current small
resource profile:

```text
credential-service: request 25m/128Mi, limit 500m/512Mi
metadata-worker:    request 25m/128Mi, limit 500m/512Mi
routes:             ClusterIP only, no HTTPRoute in ircs-dev
internal auth:      X-IRCS-INTERNAL-TOKEN from ircs-dev-secrets
```

Current `ircs-dev` quota usage after rollout:

```text
pods:            11 / 20
services:        12 / 20
configmaps:      3 / 30
secrets:         4 / 30
requests.cpu:    950m / 4
requests.memory: 2432Mi / 8Gi
limits.cpu:      5750m / 8
limits.memory:   6656Mi / 16Gi
```

## Verification

Local tests:

```text
.\gradlew.bat :services:ircs-credential-service:test
  PASS

.\gradlew.bat :services:ircs-metadata-worker:test
  PASS

.\gradlew.bat build
  PASS, BUILD SUCCESSFUL
```

Repository check:

```text
rg sys_credentials services\ircs-metadata-worker
  no TMDB credential lookup through sys_credentials
```

Manifest validation:

```text
kubectl apply --dry-run=server -f deploy\k8s\dev\credential-service-dev.yaml
  PASS

kubectl apply --dry-run=server -f deploy\k8s\dev\metadata-worker-dev.yaml
  PASS
```

ARM64 image push:

```text
credential-service:
  harbor.mnnu.eu.org/ircs/ircs-credential-service:dev
  imageDigest=sha256:6c28235e04e180e84a8839d99505fb2077261a349a9a2b1cc9e27c7065344071

metadata-worker:
  harbor.mnnu.eu.org/ircs/ircs-metadata-worker:dev
  imageDigest=sha256:607fdca363ff4588f6dbd4dc988197272ad4558e0bc6efd5a6c4da39955b1baf
```

Rollout:

```text
kubectl rollout restart deployment/ircs-credential-service -n ircs-dev
kubectl rollout status deployment/ircs-credential-service -n ircs-dev --timeout=240s
  PASS, successfully rolled out

kubectl rollout restart deployment/ircs-metadata-worker -n ircs-dev
kubectl rollout status deployment/ircs-metadata-worker -n ircs-dev --timeout=240s
  PASS, successfully rolled out
```

Credential lease smoke:

```text
Inserted temporary sys_credentials row:
  id=5fd2a514-7f72-433b-aa57-b0eb84999590
  provider=TMDB
  name=codex-lease-smoke
  payload contains api_key and note keys

GET /internal/credentials/providers/TMDB/leases?requiredPayloadKey=api_key&limit=5
  response included secretPayload.api_key for the temporary row

Deleted temporary sys_credentials row.

GET /internal/credentials/providers/TMDB/leases?requiredPayloadKey=api_key&limit=5
  []
```

Metadata-worker smoke with empty credential pool:

```text
Inserted temporary raw_videos row:
  id=1f463d41-83c4-4723-984c-2ec1fc084bdb
  title=IRCSCredentialLeaseWorkerSmoke
  enrichment_status=PENDING
  enrichment_retry_count=0
  aggregation_status=BOUND

Published MetadataSearchContext JSON to x.process/metadata.fetch.tmdb.

metadata-worker log:
  Retryable metadata provider failure. provider=TMDB,
  videoId=1f463d41-83c4-4723-984c-2ec1fc084bdb,
  code=POOL_EXHAUSTED

PostgreSQL after result collector:
  enrichment_status=FAILED
  enrichment_retry_count=1
  aggregation_status=BOUND
  tmdb_id is empty

Elasticsearch after metadata-worker -> search-service chain:
  GET /ircs_raw_video/_doc/1f463d41-83c4-4723-984c-2ec1fc084bdb
  found=true
  enrichmentStatus=FAILED
```

Cleanup:

```text
Published structured DELETE to x.search/search.sync.raw.
Deleted temporary raw_videos row.

PostgreSQL:
  count(raw_videos where id=1f463d41-83c4-4723-984c-2ec1fc084bdb) -> 0

Elasticsearch:
  GET /ircs_raw_video/_doc/1f463d41-83c4-4723-984c-2ec1fc084bdb
  found=false

RabbitMQ:
  q.fetch.metadata.tmdb      0 messages, 1 consumer
  q.enrich.metadata.result   0 messages, 1 consumer
  q.search.sync.raw          0 messages, 1 consumer
  related DLQs               0 messages
```

## Implementation notes

The credential-service repository uses `jsonb_exists(payload, :requiredPayloadKey)`
instead of PostgreSQL's `payload ? :requiredPayloadKey` operator. The operator
form conflicts with JDBC named/positional parameter parsing because `?` is also
a placeholder marker.

`CredentialLeaseMapper` returns scalar JSON values only. Nested JSON payloads
are ignored for the lease response until a provider needs a typed payload
contract.

## Remaining work

- Add NetworkPolicy around `/internal/credentials/**` before using real secrets
  outside this trusted dev namespace.
- Add a real dev TMDB credential only when controlled external provider traffic
  is desired.
- Enable dispatcher fan-out to TMDB after a credential exists and rate-limit
  smoke passes.
- Add credential write APIs, rotation, and audit events.
- Add Douban and Rotten Tomatoes provider workers as separate slices.

## 2026-06-03 internal token hardening addendum

The internal lease endpoint now rejects requests without
`X-IRCS-INTERNAL-TOKEN` or with a wrong token. The configured token is stored in
the existing `ircs-dev-secrets` key `INTERNAL_CREDENTIAL_TOKEN`; no new Secret
resource was added.

Verification:

```text
.\gradlew.bat :services:ircs-credential-service:test
  PASS

.\gradlew.bat :services:ircs-metadata-worker:test
  PASS

.\gradlew.bat build
  PASS

kubectl apply --dry-run=server -f deploy\k8s\dev\credential-service-dev.yaml
  PASS

kubectl apply --dry-run=server -f deploy\k8s\dev\metadata-worker-dev.yaml
  PASS

no token -> 401
wrong token -> 401
correct token -> []
```

Rollout images:

```text
credential-service imageDigest=sha256:e62362c354c954a71c370abe6c918a27d5eaf7777c94d040c213c13d7317c466
metadata-worker imageDigest=sha256:455af9d6206aac8e8de89b93a93f749bc3b20b8355a31d6e7d9246aa67ca794a
```
