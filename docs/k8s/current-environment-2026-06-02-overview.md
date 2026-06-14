# K8S current environment snapshot - 2026-06-02

Updated on 2026-06-06 after adding low-profile Valkey, the metadata-worker
dispatcher slice, the TMDB provider-worker slice, the credential-service
read-only metadata API plus token-protected internal provider lease API, and
the catalog-service read-only dictionary API, the config-service read-only API,
the task-service read-only collection task API, the ingestion-worker video
ingest queue slice, the normalization-worker normalize queue slice, the
aggregation-worker scheduler slice, and the second dev Java CPU limit
compression pass, the magnet-service provider/cache read-only slice, the
identity-service auth/profile MVP slice, worker-only Service pruning for
dev Service quota, the scraper-service manual scrape MVP slice, the dev Pod
quota adjustment, the portal-service read composition MVP slice, the
interaction-service feedback/history MVP slice, and the ops-service
maintenance/dashboard MVP slice, the content raw/unified admin API slice, the
storage cover image admin API slice, the ops-service observability read API
slice, and the portal/admin frontend gateway plus formal portal-bff/admin-bff
split, the identity-service member admin API slice, the content people
dictionary admin API slice, the catalog admin read contract slice, the config
common timezones API slice, the content auxiliary admin API slice, the catalog
admin write contract slice, and the config/credential admin write contract
slice.

Context:

- Kubernetes context: `kubernetes-admin@kubernetes`
- API server: `https://213.35.105.106:6443`
- Node: `instance-20251229-0833`
- Node OS: `Ubuntu 24.04.3 LTS`
- Kubernetes version observed on node: `v1.35.0`

## Namespace map

```text
argocd                 GitOps control plane
cert-manager           certificate automation
cilium-secrets         Cilium secrets
default                empty/default cluster namespace
envoy-gateway-system   Envoy Gateway control and production gateway
game-proxy             hysteria proxy workload
ircs-dev               isolated development namespace
ircs-system            current IRCS runtime and infrastructure
kube-node-lease        Kubernetes system
kube-public            Kubernetes system
kube-system            Kubernetes system, Cilium, CoreDNS, Hubble
local-path-storage     local-path storage provisioner
metallb-system         MetalLB load balancer
monitoring             Prometheus, Grafana, Alertmanager, Tempo, OTEL
```

## High-level topology

```text
                           +-------------------------+
                           | production-gateway      |
                           | Envoy Gateway           |
                           | LB: 10.0.0.240          |
                           +-----------+-------------+
                                       |
                 +---------------------+----------------------+
                 |                     |                      |
                 v                     v                      v
        +----------------+    +----------------+     +----------------+
        | ircs-system    |    | monitoring     |     | argocd         |
        | ircs route     |    | grafana route  |     | argocd route   |
        +-------+--------+    +----------------+     +----------------+
                |
                v
   +---------------------------+
   | ircs-frontend-svc         |
   | -> ircs-backend-svc       |
   | -> postgres/valkey/rabbit |
   | -> elasticsearch/kibana   |
   +---------------------------+

   +---------------------------+
   | ircs-dev                  |
   | Telepresence manager      |
   | PostgreSQL + RabbitMQ     |
   | Valkey + Elasticsearch    |
   | extracted services        |
   | no public HTTPRoute       |
   +---------------------------+
```

## Helm releases

| Release | Namespace | Chart | App |
| --- | --- | --- | --- |
| `argocd` | `argocd` | `argo-cd-9.3.5` | `v3.2.6` |
| `cert-manager` | `cert-manager` | `cert-manager-v1.19.2` | `v1.19.2` |
| `cilium` | `kube-system` | `cilium-1.18.6` | `1.18.6` |
| `eg` | `envoy-gateway-system` | `gateway-helm-1.6.2` | `v1.6.2` |
| `prometheus-stack` | `monitoring` | `kube-prometheus-stack-81.2.2` | `v0.88.0` |
| `traffic-manager` | `ircs-dev` | `telepresence-oss-2.28.0` | `2.28.0` |

## IRCS runtime namespace

`ircs-system` currently contains:

```text
Deployments:
  huawai
  ircs-backend
  ircs-frontend
  kibana

StatefulSets:
  postgres
  valkey
  rabbitmq
  elasticsearch

Services:
  huawai-svc
  ircs-backend-svc
  ircs-frontend-svc
  kibana-svc
  postgres-svc
  valkey-svc
  rabbitmq-svc
  elasticsearch-svc

HTTPRoutes:
  huawai-route   -> huawai.sophia.fr.eu.org
  ircs-route     -> ircs.sophia.fr.eu.org
  kibana-route   -> kibana.sophia.fr.eu.org
```

## Development namespace

`ircs-dev` currently contains Telepresence traffic-manager resources,
namespace-local PostgreSQL, RabbitMQ, Elasticsearch, low-profile Valkey, the
first extracted worker, the real search sync service, the storage image-delete
worker, the content image-unlink worker, the metadata result collector plus
dispatcher plus TMDB provider worker, the credential metadata/lease API, the
catalog read-only dictionary API, the config read-only API, the task
read-only collection task API, the ingestion video ingest worker, and the
normalization video normalize worker, the aggregation scheduler worker, the
magnet provider/cache read-only service, the identity auth/profile service, the
scraper manual scrape service, the portal read composition service, and the
interaction feedback/history service, the ops maintenance/dashboard service,
plus post-MVP content raw/unified admin API coverage, content people dictionary
admin API coverage, content playlists/source-domains/resolvers admin API
coverage, catalog admin read/write contract coverage, config common timezones
and admin write/test API coverage, credential admin write/template API
coverage, storage cover image admin API coverage, ops
request-audit/DLQ/traffic read API coverage, the V1 portal/admin frontend
entrypoints, the frontend gateway, and the formal portal/admin BFF layer:

```text
Deployments:
  traffic-manager
  ircs-aggregation-worker
  ircs-catalog-service
  ircs-config-service
  ircs-content-service
  ircs-credential-service
  ircs-admin-bff
  ircs-admin-frontend
  ircs-frontend-gateway
  ircs-identity-service
  ircs-ingestion-worker
  ircs-interaction-service
  ircs-magnet-service
  ircs-metadata-worker
  ircs-normalization-worker
  ircs-notification-worker
  ircs-ops-service
  ircs-portal-bff
  ircs-portal-frontend
  ircs-portal-service
  ircs-scraper-service
  ircs-search-service
  ircs-storage-service
  ircs-task-service
  valkey

StatefulSets:
  postgres
  rabbitmq
  elasticsearch

Services:
  traffic-manager
  agent-injector
  postgres-svc
  rabbitmq-svc
  elasticsearch-svc
  ircs-catalog-service
  ircs-config-service
  ircs-content-service
  ircs-credential-service
  ircs-admin-bff
  ircs-admin-frontend
  ircs-frontend-gateway
  ircs-identity-service
  ircs-interaction-service
  ircs-magnet-service
  ircs-ops-service
  ircs-portal-bff
  ircs-portal-frontend
  ircs-portal-service
  ircs-scraper-service
  ircs-search-service
  ircs-storage-service
  ircs-task-service
  valkey-svc

Secrets:
  ircs-dev-secrets
  harbor-secret

ConfigMaps:
  ircs-dev-app-config
  ircs-frontend-gateway-nginx

No public HTTPRoute.
PVC:
  postgres-data-postgres-0
  rabbitmq-data-rabbitmq-0
  elasticsearch-data-elasticsearch-0

Valkey uses no PVC in dev.
```

Current quota usage after catalog/config/credential admin write API rollout:

```text
pods:            28 / 30
services:        24 / 26
configmaps:      4 / 30
secrets:         4 / 30
requests.cpu:    880m / 4
requests.memory: 4480Mi / 8Gi
limits.cpu:      7650m / 8
limits.memory:   14208Mi / 16Gi
```

Current dev resource profile:

```text
postgres:       request 100m/256Mi, limit 500m/512Mi, pvc 2Gi
rabbitmq:       request 100m/256Mi, limit 500m/768Mi, pvc 1Gi
elasticsearch:  request 100m/1Gi,   limit 1000m/2Gi, heap 512m, pvc 5Gi
valkey:         request 50m/128Mi,  limit 250m/256Mi, maxmemory 96mb, no pvc
java services:  request 25m/128Mi,  limit 250m/512Mi
migrator job:   request 50m/128Mi,  limit 500m/512Mi
```

Datasource Java services use the dev Hikari pool profile:

```text
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=2
SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0
SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT=30000
```

Single-replica dev Java Deployments use `Recreate` to avoid a temporary surge
Pod on the small single-node cluster.

Labels and annotations:

```text
environment=dev
app.kubernetes.io/part-of=ircs
gateway-access=none
ircs.prodigalgal.com/no-public-route=true
ircs.prodigalgal.com/traffic-manager-scope=ircs-dev
```

Limits applied:

```text
ResourceQuota ircs-dev-quota:
  pods=30
  services=26
  configmaps=30
  secrets=30
  requests.cpu=4
  requests.memory=8Gi
  limits.cpu=8
  limits.memory=16Gi

LimitRange ircs-dev-default-limits:
  defaultRequest.cpu=100m
  defaultRequest.memory=256Mi
  default.cpu=500m
  default.memory=1Gi
```

