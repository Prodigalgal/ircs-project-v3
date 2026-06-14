# K8S current environment conflict controls smoke evidence - 2026-06-02

来源：`current-environment-2026-06-02-conflict-controls.md` 拆分。

- Identity-service smoke passed:
  `ircs-identity-service` is Ready 1/1 and exposes only ClusterIP
  `10.97.88.143`. The ARM64 image is
  `harbor.mnnu.eu.org/ircs/ircs-identity-service:dev` with digest
  `sha256:51b2dbc5dffdd5affceed62762597f9e200ea494dd9f322ce52b5b85ee41d55c`.
  Smoke covered PoW challenge, register with email verification disabled,
  login, profile get, profile update, check-in, password change,
  forgot-password mail disabled, reset-password with a temporary Valkey token,
  relogin, BANNED login rejection, and temporary member cleanup.
  `activate`/`resend-code` are deferred to an email verification slice because
  dev currently sets `APP_IDENTITY_EMAIL_VERIFY_ENABLED=false` and
  `APP_MAIL_ENABLED=false`.
- Worker-only Service pruning passed:
  Services for `ircs-notification-worker`, `ircs-metadata-worker`,
  `ircs-ingestion-worker`, `ircs-normalization-worker`, and
  `ircs-aggregation-worker` were deleted and removed from dev manifests. All
  five Deployments remained Ready 1/1. RabbitMQ queue and DLQ message counts
  remained 0, and Service quota dropped from `20/20` to `15/20`.
- Scraper-service smoke passed:
  `POST /api/v1/scraper/manual/init` returned a session, and
  `GET /api/v1/scraper/manual/stream/{sessionId}` emitted `LOG`,
  `CARD(PUBLISHED)`, and `DONE`. The temporary direct item was published to
  `q.ingest.video`, consumed by `ircs-ingestion-worker`, and persisted as one
  `raw_videos` row with one playlist and one episode. Cleanup left temporary
  `raw_videos` and `data_sources` rows at 0, and all RabbitMQ queues and DLQs at
  0 messages. An initial DLQ was traced to the test script deleting the
  temporary `data_sources` row before ingestion consumed the message; after
  fixing cleanup ordering, the smoke passed without DLQ residue.
- Dev Pod quota adjustment passed:
  `deploy/k8s/dev/resource-quota-dev.yaml` was server dry-run validated and
  applied. `ircs-dev-quota` now has `pods=24`; CPU, memory, services, secrets
  and configmaps hard limits were kept unchanged.
- Portal-service smoke passed:
  `ircs-portal-service` is Ready 1/1 and exposes only ClusterIP
  `10.99.214.176`. The ARM64 image is
  `harbor.mnnu.eu.org/ircs/ircs-portal-service:dev` with digest
  `sha256:a2ca28ce3f71ad223257640c2c9396bb591c7ab022407717dc94ca60aa52a7cb`.
  `GET /api/portal/metadata` returned `categories`, `genres`, `areas`,
  `languages`, and `years`; `GET /api/portal/home` returned `spotlight`,
  `trending`, and `sections`.
- Interaction-service smoke passed:
  `ircs-interaction-service` is Ready 1/1 and exposes only ClusterIP
  `10.105.151.72`. The ARM64 image is
  `harbor.mnnu.eu.org/ircs/ircs-interaction-service:dev` with digest
  `sha256:540c97ad07c292e866a0f30523ff5b20c5f6bf5a6ae702db353c85f8fd019bd4`.
  Smoke inserted a temporary member and public message, verified
  `/api/portal/feedback/wall` did not leak `memberEmail`, and verified
  authenticated `/api/portal/interaction/history` plus
  `/api/portal/interaction/favorites` returned 200. Cleanup left temporary
  member/message rows at 0 and RabbitMQ queues plus DLQs at 0 messages.
- Ops-service smoke passed:
  `ircs-ops-service` is Ready 1/1 and exposes only ClusterIP
  `10.102.243.27`. The ARM64 image is
  `harbor.mnnu.eu.org/ircs/ircs-ops-service:dev` with digest
  `sha256:c78353aee97fb3013b129bea99d94c1cc6558cbfaf7c4b7555b1ef6cc26aa7ac`.
  Smoke covered `GET /api/v1/dashboard/statistics`,
  `GET /api/v1/dashboard/analysis?days=14`,
  `GET /api/v1/dashboard/metrics`,
  `GET /api/v1/dashboard/source-quality`,
  `POST /api/v1/dashboard/refresh?days=14`,
  `GET /api/v1/ops/maintenance/active`, and
  `GET /api/v1/ops/maintenance/stream/{sessionId}` over ClusterIP. The final
  smoke returned 200/204 and expected `ERROR + DONE` SSE for an expired
  session. Cleanup checks left business tables at 0 rows for smoke anchors,
  RabbitMQ queues and DLQs at 0 messages, and no HTTPRoute in `ircs-dev`.
  During smoke, `/api/v1/dashboard/analysis` fixes were applied for PostgreSQL
  `Instant` parameter typing and `raw_category` schema drift
  (`source_name/source_code`). Hikari was capped at `maximum-pool-size=2` and
  `minimum-idle=0` to reduce dev PostgreSQL connection pressure.
- Ops-service observability read API smoke passed:
  Smoke covered `GET /api/v1/ops/request-audit`,
  `GET /api/v1/ops/request-audit/summary`, `GET /api/v1/ops/dlq`, and
  `GET /api/v1/ops/traffic/status` over ClusterIP with one interface-level
  `MARK` per completed and reproduced route, ending at `MARK 4/4`.
  Temporary `request_audit_logs`, `failed_messages`, and Valkey traffic smoke
  data were cleaned up; no HTTPRoute exists in `ircs-dev`, ResourceQuota remains
  `pods=23/24` and `services=19/20`, and RabbitMQ queues plus DLQs remained at
  0 messages.
- Content raw/unified admin API smoke passed:
  `ircs-content-service` is Ready 1/1 and exposes only ClusterIP
  `10.103.17.76`. The ARM64 image is
  `harbor.mnnu.eu.org/ircs/ircs-content-service:dev` with digest
  `sha256:e70bc1e9ca03b80f103775a2eb50ac8978af2af9b1fadb5f79e3cf06f839fb9a`.
  Smoke covered all 21 V1 raw/unified admin API routes, with one interface-level
  `MARK` per completed and reproduced route. The final smoke returned
  `MARK 21/21`, covered raw create/list/detail/update/delete/batch/sync-search/
  renormalize/reenrich/refetch and unified create/list/detail/update/delete/
  batch/sync-search/recalculate. Cleanup left smoke anchors at `0|0|0|0|0`,
  no HTTPRoute exists in `ircs-dev`, ResourceQuota remains `pods=23/24` and
  `services=19/20`, and PostgreSQL activity after Hikari compression was
  `1 active / 7 idle / 8 internal-null`.
- Dev Hikari compression passed:
- Storage cover image admin API smoke passed:
  `ircs-storage-service` is Ready 1/1 and exposes only ClusterIP
  `10.108.98.106`. The ARM64 image is
  `harbor.mnnu.eu.org/ircs/ircs-storage-service:dev` with digest
  `sha256:061d84a5f1a3f6d77efa71e37faab4bc326f4ab7b616d9ba9007ce2b143a73e4`.
  Smoke covered all 7 V1 cover image admin API routes with one interface-level
  `MARK` per completed and reproduced route:
  upload, list, detail, fetch, sync-r2 disabled behavior, retry, and delete.
  The final smoke returned `MARK 7/7`; smoke cleanup left
  `cover-smoke source_domains=0` and linked `cover_images=0`. No HTTPRoute
  exists in `ircs-dev`, and ResourceQuota remains `pods=23/24` and
  `services=19/20`.
- Dev Hikari compression passed:
  17 running datasource Deployments were patched and rolled out with
  `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=2`,
  `SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=0`, and
  `SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT=30000`; 18 datasource dev manifests,
  including the migrator Job, were synchronized and all dev YAML files passed
  `kubectl apply --dry-run=client`.
- Dev resource compression passed:
  all extracted dev services are Ready 1/1, `ircs-dev` CPU request dropped to
  `550m`, and node allocated CPU request dropped from `3997m/4000m` to
  `3522m/4000m`. `monitoring` pods remained Running/Ready; monitoring CPU
  request was already effectively 0, so no monitoring CPU reduction was needed
  for this scheduler issue.
- Portal/admin frontend and BFF rollout passed:
  `ircs-portal-frontend`, `ircs-admin-frontend`, `ircs-frontend-gateway`,
  `ircs-portal-bff`, and `ircs-admin-bff` are all Ready 1/1 and expose only
  ClusterIP services. The gateway routes portal API traffic through
  `ircs-portal-bff` and admin API traffic through `ircs-admin-bff`. Smoke
  covered BFF service-info, portal metadata/home/auth-pow, admin dashboard and
  admin ops traffic status. A 2026-06-06 recheck after `025-10` and `025-11`
  also confirmed admin `/api/v1/auth/pow` now returns 200 through
  `frontend-gateway -> admin-bff -> identity-service`. No HTTPRoute exists in
  `ircs-dev`.
- Interaction-service admin message API smoke passed:
  `ircs-interaction-service` is Ready 1/1 and exposes only ClusterIP
  `ircs-interaction-service`. The ARM64 image is
  `harbor.mnnu.eu.org/ircs/ircs-interaction-service:dev` with digest
  `sha256:6b272d19acf269781317b9c59028eb67220601b79f977bbd40efc50fc4cbccd0`.
  Smoke ran through `ircs-frontend-gateway -> ircs-admin-bff ->
  ircs-interaction-service` and covered `GET /api/v1/messages`,
  `POST /api/v1/messages/{id}/reply`,
  `PUT /api/v1/messages/{id}/visibility`, and
  `DELETE /api/v1/messages/{id}` with `MARK 4/4`. Temporary member/message
  cleanup left `remaining=0|0`; no HTTPRoute exists in `ircs-dev`, and
  ResourceQuota remains `pods=28/30` and `services=24/26`.
- Task-service runner/scheduler runtime contract smoke passed:
  `ircs-task-service`, `ircs-scraper-service`, and `ircs-admin-bff` are Ready
  1/1. The current ARM64 image digests are
  `ircs-task-service@sha256:677ee7eab5514d7e2e81ed9e8a0b4d390007bff50d8c7cc109fa43da294d38e6`,
  `ircs-scraper-service@sha256:271e249c2c477520595391572067ff3097a111e247169f06e8114ad44d60ff98`,
  and `ircs-admin-bff@sha256:3bbc9cde8b8486f5e1148bbb132aa690ea7c8dc95be2d5c796d98aaaa6a10edc`.
  Smoke ran through `admin-bff -> ircs-task-service -> ircs-scraper-service
  -> q.ingest.video -> ircs-ingestion-worker` and produced
  `COMPLETED|1|1|0`, `rawVideoCount=1`, and RabbitMQ queue/DLQ messages at 0.
  Temporary task/source/raw-video rows were cleaned up. No HTTPRoute exists in
  `ircs-dev`, and ResourceQuota remains `pods=28/30` and `services=24/26`.
- Identity-service admin auth API smoke passed:
  `ircs-identity-service` and `ircs-admin-bff` are Ready 1/1. The current
  identity-service ARM64 image digest is
  `sha256:cc5114036b97b5416a5298fe43d071c6519df5b3b8dd4673bc97a3d3997a931d`.
  Smoke ran through `admin-bff -> ircs-identity-service` and covered
  `GET /api/v1/auth/pow`, `POST /api/v1/auth/login`, and
  `POST /api/v1/auth/change-password` with `MARK 3/3`. Temporary admin
  username/password/iAT floor config values were restored; residue count was
  0. No HTTPRoute exists in `ircs-dev`, and ResourceQuota remains
  `pods=28/30` and `services=24/26`.
- Identity-service member admin API smoke passed:
  `ircs-identity-service` and `ircs-admin-bff` are Ready 1/1. The current
  identity-service ARM64 image digest is
  `sha256:f8e44e063ce3ae32b132b53209bc96c22c0f14e1bed577af806870e528184d66`.
  Smoke ran through `admin-bff -> ircs-identity-service` and covered
  `GET /api/v1/members`, `GET /api/v1/members/{id}/favorites`,
  `GET /api/v1/members/{id}/history`, `PUT /api/v1/members/{id}`,
  `PUT /api/v1/members/{id}/status`, and
  `DELETE /api/v1/members/{id}` with `MARK 6/6`. Temporary admin
  username/password config values were restored; temporary member, favorite,
  history, and unified video data were cleaned up with
  `post_cleanup=0|0|0|0|0`. No HTTPRoute exists in `ircs-dev`, and
  ResourceQuota remains `pods=28/30` and `services=24/26`.
- Content/catalog/config post-MVP API smoke passed:
  `ircs-content-service`, `ircs-catalog-service`, `ircs-config-service`, and
  `ircs-admin-bff` are Ready 1/1. The current ARM64 image digests are
  `ircs-content-service@sha256:069bdf026e19c30dbbd653d784525d0b7ddccb49b1d1948d564706c6f6e2e8d1`,
  `ircs-catalog-service@sha256:4d6a3536242ab5c6c1fbd68cf215358f607a3c2eafba0e4ffa228ce47364b625`,
  and
  `ircs-config-service@sha256:eb25f939d7cc2b73a0b682623aa8fc12ef318226d5f74f300e3fa44f12165e98`.
  Smoke ran through `admin-bff` and covered content people admin API with
  `MARK 14/14`, catalog admin read contract with `MARK 21/21`, and common
  timezones with `MARK 1/1`. Temporary actors, directors, data_sources,
  raw_videos, and raw_languages data were cleaned up with residue count 0. No
  HTTPRoute exists in `ircs-dev`, and ResourceQuota remains `pods=28/30` and
  `services=24/26`.
- Content auxiliary admin API smoke passed:
  `ircs-content-service` and `ircs-admin-bff` are Ready 1/1. The current
  content-service ARM64 image digest is
  `sha256:de6af58c54ddf0ddcf5cbf73a1396f5a51bcfac084758bd30782cc4e42b2a233`.
  Smoke ran through `admin-bff` and covered playlists/source-domains/resolvers
  admin API with `MARK 14/14`. Temporary playlists, resolvers, source_domains,
  raw_videos, and data_sources data were cleaned up with residue count 0. No
  HTTPRoute exists in `ircs-dev`, and ResourceQuota remains `pods=28/30` and
  `services=24/26`.
- After content auxiliary admin API rollout, `ircs-dev` CPU request is
  `880m`, CPU limit is `7650m / 8`, Service quota usage is `24 / 26`, and Pod
  quota usage is `28 / 30`. The namespace still retains two buffer Pods and two
  buffer Services under the current low-resource profile.
- Catalog/config/credential admin write API smoke passed:
  `ircs-catalog-service`, `ircs-config-service`, `ircs-credential-service`, and
  `ircs-admin-bff` are Ready 1/1. The current ARM64 image digests are
  `ircs-catalog-service@sha256:bc8ec3c3b0a72b2f8ffbbb97814839bb91d783a6f0c474153f5f538a8a58cd39`,
  `ircs-config-service@sha256:3ce1e6be62dd11a6912a01715b007699b3f02ff8445c2383464545f7d2c9ddac`,
  and
  `ircs-credential-service@sha256:c1eaa572403be48308155a4edc8dcfca219b8008fb6f6f228e6d5352828d5b60`.
  Smoke ran through `admin-bff` and covered catalog admin write contract with
  `MARK 25/25`, plus config/credential admin write contract with `MARK 9/9`.
  Temporary data_sources, standard_*, raw_*, sys_credentials, and
  system_configs smoke data were cleaned up with residue count 0. No HTTPRoute
  exists in `ircs-dev`, and ResourceQuota remains `pods=28/30` and
  `services=24/26`.
- After catalog/config/credential admin write API rollout, `ircs-dev` CPU
  request is `880m`, CPU limit is `7650m / 8`, Service quota usage is
  `24 / 26`, and Pod quota usage is `28 / 30`. The namespace still retains two
  buffer Pods and two buffer Services under the current low-resource profile.

Image architecture:

```text
Kubernetes node architecture: linux/arm64
notification-worker image:   harbor.mnnu.eu.org/ircs/ircs-notification-worker:dev
notification-worker digest:  sha256:742fe58a97b29e57eeae201da03ec63e331d4759284582d6d2351654bb7df6d4
search-service image:        harbor.mnnu.eu.org/ircs/ircs-search-service:dev
storage-service image:       harbor.mnnu.eu.org/ircs/ircs-storage-service:dev
storage-service digest:      sha256:f73fd025c806bd09768b1336647b6806105896fbb3ece4ad19ca2ce689b26e02
content-service image:       harbor.mnnu.eu.org/ircs/ircs-content-service:dev
content-service digest:      sha256:de6af58c54ddf0ddcf5cbf73a1396f5a51bcfac084758bd30782cc4e42b2a233
metadata-worker image:       harbor.mnnu.eu.org/ircs/ircs-metadata-worker:dev
credential-service image:    harbor.mnnu.eu.org/ircs/ircs-credential-service:dev
credential-service digest:   sha256:c1eaa572403be48308155a4edc8dcfca219b8008fb6f6f228e6d5352828d5b60
catalog-service image:       harbor.mnnu.eu.org/ircs/ircs-catalog-service:dev
catalog-service digest:      sha256:bc8ec3c3b0a72b2f8ffbbb97814839bb91d783a6f0c474153f5f538a8a58cd39
config-service image:        harbor.mnnu.eu.org/ircs/ircs-config-service:dev
config-service digest:       sha256:3ce1e6be62dd11a6912a01715b007699b3f02ff8445c2383464545f7d2c9ddac
task-service image:          harbor.mnnu.eu.org/ircs/ircs-task-service:dev
task-service digest:         sha256:677ee7eab5514d7e2e81ed9e8a0b4d390007bff50d8c7cc109fa43da294d38e6
ingestion-worker image:      harbor.mnnu.eu.org/ircs/ircs-ingestion-worker:dev
ingestion-worker digest:     sha256:c9293ffbd00a899f4c93f8dbf59bff5811aa43cc6fe213161c02d38ecea53797
normalization-worker image:  harbor.mnnu.eu.org/ircs/ircs-normalization-worker:dev
normalization-worker digest: sha256:d0e35af46a0bd8aa0ca60e4db2c973a90824b7e84783cd2f5c6db501d0f043fe
aggregation-worker image:    harbor.mnnu.eu.org/ircs/ircs-aggregation-worker:dev
aggregation-worker digest:   sha256:6b397d462693101553323c826bc1e2c14d1a9a0a84e9b95206741cfd076fcf6d
magnet-service image:        harbor.mnnu.eu.org/ircs/ircs-magnet-service:dev
magnet-service digest:       sha256:5fb8ae6bb463ab202c971d2ca8cffc1fb21491bf7d78de0510b2715ada1984e6
identity-service image:      harbor.mnnu.eu.org/ircs/ircs-identity-service:dev
identity-service digest:     sha256:58381fc9c9acc25f82d0ac01a14d7e52623af5ca73e6bcce349e231a862f4456
scraper-service image:       harbor.mnnu.eu.org/ircs/ircs-scraper-service:dev
scraper-service digest:      sha256:271e249c2c477520595391572067ff3097a111e247169f06e8114ad44d60ff98
portal-service image:        harbor.mnnu.eu.org/ircs/ircs-portal-service:dev
portal-service digest:       sha256:a2ca28ce3f71ad223257640c2c9396bb591c7ab022407717dc94ca60aa52a7cb
interaction-service image:   harbor.mnnu.eu.org/ircs/ircs-interaction-service:dev
interaction-service digest:  sha256:a70f46ed265944ac06f180e68d8930268542c7c53519f624273b52b7cb296ab4
ops-service image:           harbor.mnnu.eu.org/ircs/ircs-ops-service:dev
ops-service digest:          sha256:77736bf364124fe8e582f71ce72cee7edf065c2812b16bbcfe1101963046253d
portal-bff image:            harbor.mnnu.eu.org/ircs/ircs-portal-bff:dev
portal-bff digest:           sha256:6ec945cf90eff8cbcb2bb83b808df8bd3d55e6083c265d776e614a220058d47e
admin-bff image:             harbor.mnnu.eu.org/ircs/ircs-admin-bff:dev
admin-bff digest:            sha256:3bbc9cde8b8486f5e1148bbb132aa690ea7c8dc95be2d5c796d98aaaa6a10edc
portal frontend image:       harbor.mnnu.eu.org/ircs/huawai:8904e3825088e27b80cafb506cee77e7828c0c65
admin frontend image:        harbor.mnnu.eu.org/ircs/ircs-frontend:5813b9fcfabe06a2d916e9238d08cc749b54c6b3
frontend gateway image:      nginx:1.25-alpine
migrator image:              harbor.mnnu.eu.org/ircs/ircs-migrator:dev
build path:                  Jib, linux/arm64
```

Risk to track:

- `production-gateway` currently allows HTTPRoutes from all namespaces.
- If someone creates an HTTPRoute in `ircs-dev` with a public hostname, it can attach to the production gateway.

Recommended hardening before adding dev HTTPRoutes:

```text
1. Label public route namespaces with gateway-access=public.
2. Change production-gateway allowedRoutes from All to a namespace selector.
3. Keep ircs-dev as gateway-access=none unless explicitly testing public routing.
4. Keep Telepresence intercepts in ircs-dev only.
5. Keep worker queue consumers on namespace-local RabbitMQ in dev.
```

Current dev scraper-service scope:

```text
owned:     POST /api/v1/scraper/manual/init, GET /api/v1/scraper/manual/stream/{sessionId}
publishes: q.ingest.video
database:  data_sources read-only migration bridge
session:   single-replica in-memory session map for MVP
smoke:     directItems -> SSE CARD(PUBLISHED) -> q.ingest.video -> raw_videos/playlists/episodes
deferred:  Valkey-backed sessions, admin auth, collection task lifecycle, real external data source smoke, trend sync
```

Current dev portal-service scope:

```text
owned:     GET /api/portal/metadata, GET /api/portal/home
database:  shared PostgreSQL read model as migration bridge
cache:     Valkey available through dev config
smoke:     metadata keys and home composition keys over ClusterIP
deferred:  full portal page set, cache warmers, edge routing, physical read-model split
```

Current dev interaction-service scope:

```text
owned:     GET /api/portal/feedback/wall, GET /api/portal/interaction/history, GET /api/portal/interaction/favorites,
           GET /api/v1/messages, POST /api/v1/messages/{id}/reply,
           PUT /api/v1/messages/{id}/visibility, DELETE /api/v1/messages/{id}
database:  messages, members, watch history/favorites tables as migration bridge
auth:      ROLE_MEMBER JWT for member-scoped routes
smoke:     public feedback filtering, authenticated history/favorites reads, and admin message 4/4 over gateway/admin-bff
current:   q.interaction.watch_progress consumer completed in 028-05-A
deferred:  admin auth integration, physical DB split
```

Current dev ops-service scope:

```text
owned:     GET /api/v1/dashboard/statistics, GET /api/v1/dashboard/analysis, GET /api/v1/dashboard/metrics,
           GET /api/v1/dashboard/source-quality, POST /api/v1/dashboard/refresh,
           GET /api/v1/ops/maintenance/active, GET /api/v1/ops/maintenance/stream/{sessionId},
           GET /api/v1/ops/request-audit, GET /api/v1/ops/request-audit/summary,
           GET /api/v1/ops/worker-job-audit, GET /api/v1/ops/worker-job-audit/summary,
           GET /api/v1/ops/dlq, GET /api/v1/ops/traffic/status
database:  shared PostgreSQL dashboard, request_audit_logs, worker_job_audit_events, and failed_messages read model as migration bridge
metrics:   namespace-local RabbitMQ queue properties and Valkey INFO
session:   single-replica in-memory maintenance session map for MVP
smoke:     dashboard read endpoints, expired maintenance stream, and observability read endpoints over ClusterIP
current:   BFF request audit collection completed in 028-01; worker/job audit ops query and notification live smoke completed in 028-12
deferred:  broader per-service/worker audit collection, DLQ retry/discard, traffic reset, destructive maintenance commands, physical DB split
```

