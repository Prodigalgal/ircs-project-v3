# 089 V1 golden behavior inventory - MARK ledger

来源：`089-v1-golden-behavior-inventory.md` 拆分。

## 当前 MARK

```text
MARK 1/8 V1 source module inventory generated
MARK 2/8 V1 dependency-to-behavior mapping generated as first draft
MARK 3/8 normalization/datasource/storage/scraper behavior statuses assigned: partial
MARK 4/8 aggregation/content/search behavior statuses assigned: partial
MARK 5/8 identity/portal/interaction/task/system/magnet behavior statuses assigned: partial
MARK 6/8 all missing/partial/needs-evidence items link to tasks or backlog: partial
MARK 7/8 all improved-replacement items cite tests or smoke evidence: pending
MARK 8/8 docs/tasks indexes reference the inventory and remaining parity queue: partial, C1-S9/C2-S14/C3-S4a/A5 plus 028-07-E/028-10-D integrated 2026-06-09
A2 MARK 5/5 text/title first slice covered by normalization-worker unit tests: done 2026-06-08
A3 MARK 5/5 metadata result deep merge first slice covered by metadata-worker unit tests: done 2026-06-08
A1 MARK 4/4 canonical rawMetadata first slice covered by scraper-service unit tests: done 2026-06-08
A4 MARK 1/5 HanLP prewarm runner exists and is locally testable: done 2026-06-09
A4 MARK 2/5 description NER fallback extracts director/actor names from V1 keyword windows: done 2026-06-09
A4 MARK 3/5 explicit actor/director metadata remains higher priority than description NER: done 2026-06-09
A4 MARK 4/5 locked actors/directors fields suppress corresponding NER relation writes: done 2026-06-09
A4 MARK 5/5 normalization-worker tests pass and docs/tasks indexes updated: done 2026-06-09
A5 MARK 1/4 area composite token emits original raw token plus V1-compatible alias tokens: done 2026-06-09
A5 MARK 2/4 language composite/suffix token emits original raw token plus V1-compatible alias tokens: done 2026-06-09
A5 MARK 3/4 locked area/language fields suppress composite alias expansion: done 2026-06-09
A5 MARK 4/4 normalization-worker tests pass: done 2026-06-09
B1 MARK 1/5 title/year candidate recall works when external IDs are absent: done 2026-06-08
B1 MARK 2/5 external ID conflict blocks merge: done 2026-06-08
B1 MARK 3/5 season/version-sensitive fixtures do not merge incorrectly: done 2026-06-08
B1 MARK 4/5 similar title fixture binds to existing unified instead of creating duplicate: done 2026-06-08
B1 MARK 5/5 aggregation-worker tests pass and document remaining graph/merge gaps: done 2026-06-08
B2 MARK 1/6 matching result can expose root candidate plus duplicate victim candidates: done 2026-06-09
B2 MARK 2/6 victim raw bindings migrate to root with conflict-safe dedupe: done 2026-06-09
B2 MARK 3/6 member favorites/watch histories migrate victim -> root with conflict cleanup: done 2026-06-09
B2 MARK 4/6 victim unified rows and scoped relations are deleted without reverting raw to PENDING: done 2026-06-09
B2 MARK 5/6 aggregation publisher emits structured UNIFIED_VIDEO DELETE for victims: done 2026-06-09
B2 MARK 6/6 aggregation-worker/search-service tests pass and document B3 relation-union leftovers: done 2026-06-09
B3 MARK 1/5 victim actors/directors union into root before victim delete: done 2026-06-09
B3 MARK 2/5 victim genres/languages/areas union into root before victim delete: done 2026-06-09
B3 MARK 3/5 root duplicate relations dedupe with on conflict do nothing: done 2026-06-09
B3 MARK 4/5 B2 raw binding/favorite/history/victim delete behavior remains covered: done 2026-06-09
B3 MARK 5/5 aggregation-worker/search-service tests pass and docs record remaining raw-source pipeline gaps: done 2026-06-09
B4 MARK 1/5 victim scalar fields fill only root null/empty fields: done 2026-06-09
B4 MARK 2/5 root existing scalar fields are preserved: done 2026-06-09
B4 MARK 3/5 last_trend_at keeps the newest timestamp and metadata_status becomes DIRTY during merge: done 2026-06-09
B4 MARK 4/5 B2/B3 binding/interaction/relation/delete behavior remains covered: done 2026-06-09
B4 MARK 5/5 aggregation-worker/search-service tests pass and docs record remaining raw-source pipeline gaps: done 2026-06-09
B5 MARK 1/6 category vote writes unified_videos.category_id when category is unlocked: done 2026-06-09
B5 MARK 2/6 locked category preserves existing unified_videos.category_id: done 2026-06-09
B5 MARK 3/6 raw actors/directors rebuild unified relation when unlocked: done 2026-06-09
B5 MARK 4/6 raw genres/languages/areas rebuild unified standard relation when unlocked and ignore unmapped raw values: done 2026-06-09
B5 MARK 5/6 locked relation fields preserve existing unified relations: done 2026-06-09
B5 MARK 6/6 aggregation-worker/search-service tests pass and docs record remaining dynamic/cover/JGraphT gaps: done 2026-06-09
B6 MARK 1/7 title/year vote and score max update unlocked unified fields: done 2026-06-09
B6 MARK 2/7 published_at/total_episodes/duration/season choose first non-empty raw source with stable ordering: done 2026-06-09
B6 MARK 3/7 alias_title/description/subtitle choose longest raw value: done 2026-06-09
B6 MARK 4/7 remarks chooses latest updated raw value: done 2026-06-09
B6 MARK 5/7 external IDs fill only invalid unified values and ignore locked_fields: done 2026-06-09
B6 MARK 6/7 locked scalar fields preserve existing unified values: done 2026-06-09
B6 MARK 7/7 aggregation-worker/search-service tests pass and docs record remaining cover/JGraphT gaps: done 2026-06-09
B7 MARK 1/5 cover pipeline chooses first raw cover when unified cover is empty: done 2026-06-09
B7 MARK 2/5 managed LOCAL/R2 cover replaces EXTERNAL cover even when smaller: done 2026-06-09
B7 MARK 3/5 same managedness cover replaces only when file_size is greater than current * 1.1: done 2026-06-09
B7 MARK 4/5 coverImageUrl locked_fields does not block V1 CoverImagePipelineHandler parity: done 2026-06-09
B7 MARK 5/5 aggregation-worker/search-service tests pass and docs record remaining R2 promote/JGraphT gaps: done 2026-06-09
B8 MARK 1/5 storage queue topic and ownership for cover R2 promote are declared: done 2026-06-09
B8 MARK 2/5 aggregation-worker publishes promote command only for selected LOCAL/LOCAL_STORED cover: done 2026-06-09
B8 MARK 3/5 storage-service consumes promote command and schedules due upload only when R2 active: done 2026-06-09
B8 MARK 4/5 ineligible cover or inactive R2 does not mutate next_retry_time: done 2026-06-09
B8 MARK 5/5 local aggregation/storage/messaging tests pass and docs record remaining JGraphT/order/live gaps: done 2026-06-09
B9 MARK 1/6 aggregation-worker has service-scoped JGraphT dependency: done 2026-06-09
B9 MARK 2/6 raw/raw graph cluster uses edge 0.85 and consistency 0.90 with connected components: done 2026-06-09
B9 MARK 3/6 raw/raw cluster blocks season/version/external-id conflicts through shared matching and year bucket guard: done 2026-06-09
B9 MARK 4/6 aggregateBatch binds a multi-raw cluster to one unified and runs pipeline once after all binds: done 2026-06-09
B9 MARK 5/6 search index and cover R2 promote side effects remain emitted for clustered raw members: done 2026-06-09
B9 MARK 6/6 local aggregation-worker tests pass and docs record remaining context graph/runtime/live gaps: done 2026-06-09
B10 MARK 1/6 repository recalls context unified candidates for a raw batch by V1 douban/tmdb or title/year fallback: done 2026-06-09
B10 MARK 2/6 graph clusterer accepts raw and unified nodes and skips pure unified components: done 2026-06-09
B10 MARK 3/6 raw/unified edge uses threshold 0.85 and star consistency 0.90 like B9: done 2026-06-09
B10 MARK 4/6 multi-raw cluster with one context unified reuses that unified without creating a new one: done 2026-06-09
B10 MARK 5/6 cluster with multiple context unified IDs emits root/victim merge through existing B2-B4 path: done 2026-06-09
B10 MARK 6/6 local aggregation-worker/search-service tests pass and docs record remaining scoring/runtime/live gaps: done 2026-06-09
B11 MARK 1/5 raw candidates expose V1 standard categoryName from raw_category -> standard_category: done 2026-06-09
B11 MARK 2/5 unified candidates expose V1 standard categoryName from unified_videos -> standard_category: done 2026-06-09
B11 MARK 3/5 raw/raw graph edges do not cross concrete category buckets: done 2026-06-09
B11 MARK 4/5 raw/unified context graph edges do not cross category buckets, UNKNOWN only compares with UNKNOWN: done 2026-06-09
B11 MARK 5/5 aggregation-worker/search-service tests pass and docs record remaining scoring/live gaps: done 2026-06-09
B12 MARK 1/6 raw candidates expose actor/director/standard area names like V1 populateMetadata: done 2026-06-09
B12 MARK 2/6 unified candidates expose actor/director/standard area names like V1 populateMetadata: done 2026-06-09
B12 MARK 3/6 matching strategy uses V1 title 0.70 + metadata Jaccard 0.30 formula when metadata exists: done 2026-06-09
B12 MARK 4/6 matching strategy keeps V1 title * 0.95 fallback when either side metadata is empty: done 2026-06-09
B12 MARK 5/6 episode and area penalties can drop otherwise mergeable candidates below threshold: done 2026-06-09
B12 MARK 6/6 aggregation-worker/search-service tests pass and docs record remaining ES recall/runtime/live gaps: done 2026-06-09
B13 MARK 1/6 search-service exposes internal UUID-only context recall API: done 2026-06-09
B13 MARK 2/6 search candidate query matches V1 multi_match/year/limit semantics: done 2026-06-09
B13 MARK 3/6 aggregation-worker calls ES recall only for raw without valid douban/tmdb: done 2026-06-09
B13 MARK 4/6 ES recall IDs are loaded back from PostgreSQL before graph clustering: done 2026-06-09
B13 MARK 5/6 ES empty/error does not trigger SQL title fallback unless recall is explicitly disabled: done 2026-06-09
B13 MARK 6/6 search-service and aggregation-worker tests pass and docs record remaining runtime/live gaps: done 2026-06-09
B14 MARK 1/6 repository exposes V1 handler-grained pipeline rebuild methods: done 2026-06-09
B14 MARK 2/6 pipeline runtime executes Basic->ExternalId->Metadata->Dynamic->Category->Cover order: done 2026-06-09
B14 MARK 3/6 runtime catches one stage failure and continues later stages using JDBC nested savepoints: done 2026-06-09
B14 MARK 4/6 cover stage promote IDs are returned only when cover stage succeeds: done 2026-06-09
B14 MARK 5/6 AggregationService uses runtime for single raw and cluster binding side effects: done 2026-06-09
B14 MARK 6/6 aggregation-worker tests pass and docs record K8S live smoke as remaining gap: done 2026-06-09
B15 MARK 1/8 ircs-dev aggregation/search/storage consumers are ready and no HTTPRoute exists: done 2026-06-09
B15 MARK 2/8 multi-raw pipeline fixture is seeded and pre-cleaned from DB/ES/queues: done 2026-06-09
B15 MARK 3/8 scheduler binds fixture raws to one unified through aggregation-worker: done 2026-06-09
B15 MARK 4/8 runtime scalar and category outputs match V1 handler rules: done 2026-06-09
B15 MARK 5/8 runtime metadata relations match V1 union rules: done 2026-06-09
B15 MARK 6/8 cover stage selects managed cover and storage command is safely consumed/gated: done 2026-06-09
B15 MARK 7/8 raw/unified ES documents are refreshed and queues/DLQs stay empty: done 2026-06-09
B15 MARK 8/8 cleanup leaves no DB/ES/Rabbit/HTTPRoute/port-forward residue and resource quota is unchanged: done 2026-06-09
C3 MARK 1/6 outbound HTTP caller inventory completed: done 2026-06-08
C3 MARK 2/6 SSRF private/loopback/metadata-IP blocking contract test exists: done first slice 2026-06-08
C3 MARK 3/6 redirect/gzip/User-Agent/proxy/retry/circuit behavior documented or tested: done first slices, redirect/UA/retry/gzip/proxy tested 2026-06-08; circuit breaker first slice tested 2026-06-09
C3 MARK 4/6 first caller services use shared contract or explicit adapter: done first slices, catalog + magnet + scraper + metadata TMDB 2026-06-09
C3 MARK 5/6 service tests pass without real network: done first slices 2026-06-09
C3 MARK 6/6 docs/tasks indexes and 089 inventory updated: done first slices plus C3-S4a/S6/S7/S8/S9 2026-06-10
C3-S4a MARK 1/5 shared outbound policy can distinguish PUBLIC_FETCH, INTERNAL_SERVICE, BFF_PROXY: done 2026-06-09
C3-S4a MARK 2/5 INTERNAL_SERVICE validates scheme/host/userinfo without DNS SSRF blocking: done 2026-06-09
C3-S4a MARK 3/5 TmdbCredentialRepository uses shared INTERNAL_SERVICE and preserves token/header/path/query: done 2026-06-09
C3-S4a MARK 4/5 credential-service failure still maps to CREDENTIAL_SERVICE_UNAVAILABLE retryable: done 2026-06-09
C3-S4a MARK 5/5 shared and metadata-worker tests pass without real network: done 2026-06-09
C3-S6 MARK 1/6 shared outbound circuit breaker covers timeout, 5xx, IO/connection refused, open, half-open recovery: done 2026-06-09
C3-S6 MARK 2/6 circuit breaker has explicit error semantics for open circuit and remains fake-testable: done 2026-06-09
C3-S6 MARK 3/6 circuit breaker config supports ENV/K8S override and caller key override: done 2026-06-09
C3-S6 MARK 4/6 InternalContentClients uses shared INTERNAL_SERVICE without losing dynamic base URL/path/error mapping: done 2026-06-09
C3-S6 MARK 5/6 focused tests pass without real external network: done 2026-06-09
C3-S6 MARK 6/6 docs/tasks ledger updated with remaining caller adoption list: done 2026-06-09
C3-S7 MARK 1/6 ScraperTaskExecutionClient uses shared INTERNAL_SERVICE POST JSON caller: done 2026-06-09
C3-S7 MARK 2/6 POST body and 2xx response mapping preserve existing task execution contract: done 2026-06-09
C3-S7 MARK 3/6 4xx/5xx response mapping remains explicit and failure-compatible: done 2026-06-09
C3-S7 MARK 4/6 timeout/connection refused/open circuit fast fail are covered by tests: done 2026-06-09
C3-S7 MARK 5/6 focused task-service and shared tests pass without real network: done 2026-06-09
C3-S7 MARK 6/6 docs/tasks ledger updated with remaining adoption list and live smoke status: done 2026-06-09
C3-S8 MARK 1/6 RestClientAvatarStorageClient uses shared INTERNAL_SERVICE multipart POST: done 2026-06-10
C3-S8 MARK 2/6 multipart field filename content type and body are preserved: done 2026-06-10
C3-S8 MARK 3/6 2xx response mapping and empty response failure remain compatible: done 2026-06-10
C3-S8 MARK 4/6 timeout/5xx/IO/open circuit map to storage.sync.failed BAD_GATEWAY: done 2026-06-10
C3-S8 MARK 5/6 invalid internal base URL fails before transport send: done 2026-06-10
C3-S8 MARK 6/6 docs/tasks ledger updated and live/full tests deferred to unified testing: done 2026-06-10
C3-S9 MARK 1/7 BFF transport dispersal inventory completed: done 2026-06-10
C3-S9 MARK 2/7 shared outbound supports BFF_PROXY arbitrary method/body with streaming response: done 2026-06-10
C3-S9 MARK 3/7 admin-bff uses shared BFF_PROXY streaming adapter without losing route/query/header/status behavior: done 2026-06-10
C3-S9 MARK 4/7 portal-bff uses shared BFF_PROXY streaming adapter without losing route/query/header/status behavior: done 2026-06-10
C3-S9 MARK 5/7 caller key timeout circuit config support ENV/K8S override: done 2026-06-10
C3-S9 MARK 6/7 focused no-real-network tests pass or are recorded as deferred: done 2026-06-10
C3-S9 MARK 7/7 docs/tasks/089/093 ledgers updated with unified testing deferred: done 2026-06-10
D1 MARK 1/5 storage-service has tika-core only, no parser standard package: done 2026-06-09
D1 MARK 2/5 ImageSecurityValidator uses Tika.detect(byte[]) as MIME source: done 2026-06-09
D1 MARK 3/5 valid image fixture table passes for V1 allowlist MIME types: done 2026-06-09
D1 MARK 4/5 spoof/non-image/empty/oversize/filename fixtures are rejected: done 2026-06-09
D1 MARK 5/5 storage-service tests pass without live network: done 2026-06-09
D2 MARK 1/5 scraper-service has charset detector dependency scoped locally: done 2026-06-09
D2 MARK 2/5 list fetch detects GBK/GB18030 and UTF-8 fixtures without mojibake: done 2026-06-09
D2 MARK 3/5 list fetch parses items via Jackson streaming parser, not JsonPath.read: done 2026-06-09
D2 MARK 4/5 malformed/non-JSON list response boundary documented by tests: done 2026-06-09
D2 MARK 5/5 scraper/storage/shared tests pass without live network: done 2026-06-09
C2 MARK 2/7 sensitive defaults, MARK 3/7 prompt/mail validation, MARK 4/7 env-first runtime priority/effective source API, MARK 5/7 config/identity/interaction tests and MARK 6/7 config change event publisher + P0 consumers first slice: done 2026-06-08
C2 MARK 4/7 env/K8S/startup/configtree/Secret injection priority regression, APP_* env-name support, default-seed non-overwrite contract, config-service runtime source guard and P0 consumer YAML-default guard: done 2026-06-09
C2-S4 MARK 1/6 V1 SystemConfigKey current catalog diff documented: done 2026-06-09
C2-S4 MARK 2/6 SystemConfigDefaults contains all 133 V1 keys: done 2026-06-09
C2-S4 MARK 3/6 V1 required/sensitive keys remain protected and generated/redacted correctly: done 2026-06-09
C2-S4 MARK 4/6 existing env/K8S injection -> DB -> default seed priority tests still pass: done 2026-06-09
C2-S4 MARK 5/6 config-service tests pass: done 2026-06-09
C2-S4 MARK 6/6 docs/tasks indexes updated: done 2026-06-09
C2-S5 MARK 1/6 V1 StorageUrlHelper config facts documented: done 2026-06-09
C2-S5 MARK 2/6 search-service resolves R2 cover URL from runtime/DB/default config: done 2026-06-09
C2-S5 MARK 3/6 search-service resolves local managed cover URL with app.storage.public-path: done 2026-06-09
C2-S5 MARK 4/6 search-service config cache evicts on SystemConfigChangedEvent: done 2026-06-09
C2-S5 MARK 5/6 search-service tests pass without real Rabbit/ES: done 2026-06-09
C2-S5 MARK 6/6 docs/tasks indexes updated: done 2026-06-09
C2-S6 MARK 1/6 V1 interaction StorageUrlHelper usage facts documented: done 2026-06-09
C2-S6 MARK 2/6 interaction-service resolves R2 cover URL from runtime/DB/default config: done 2026-06-09
C2-S6 MARK 3/6 interaction-service resolves local managed cover URL with app.storage.public-path: done 2026-06-09
C2-S6 MARK 4/6 existing interaction config cache/event evict path covers cover URL keys: done 2026-06-09
C2-S6 MARK 5/6 interaction-service tests pass without real Rabbit: done 2026-06-09
C2-S6 MARK 6/6 docs/tasks indexes updated: done 2026-06-09
C2-S7 MARK 1/7 V1 portal StorageUrlHelper usage facts documented: done 2026-06-09
C2-S7 MARK 2/7 portal-service resolves R2 cover URL from runtime/DB/default config: done 2026-06-09
C2-S7 MARK 3/7 portal-service resolves local managed cover URL with app.storage.public-path: done 2026-06-09
C2-S7 MARK 4/7 portal-service config cache evicts on SystemConfigChangedEvent: done 2026-06-09
C2-S7 MARK 5/7 portal-service declares dedicated config event topology and K8S listener env: done 2026-06-09
C2-S7 MARK 6/7 portal-service tests and K8S dry-run pass without real Rabbit: done 2026-06-09
C2-S7 MARK 7/7 docs/tasks indexes updated: done 2026-06-09
C2-S8 MARK 1/8 V1 storage/avatar config usage facts documented: done 2026-06-09
C2-S8 MARK 2/8 storage-service has dedicated config cache/event listener: done 2026-06-09
C2-S8 MARK 3/8 CoverImageUrlResolver resolves R2/local cover URL from runtime/DB/default config: done 2026-06-09
C2-S8 MARK 4/8 AvatarStorageService uses runtime/DB/default avatar prefix and public path: done 2026-06-09
C2-S8 MARK 5/8 AvatarSyncService uses runtime/DB/default local public path and R2 public domain: done 2026-06-09
C2-S8 MARK 6/8 StorageR2Watchdog uses runtime/DB/default local public path: done 2026-06-09
C2-S8 MARK 7/8 storage-service tests and K8S dry-run pass without real Rabbit/R2: done 2026-06-09
C2-S8 MARK 8/8 docs/tasks indexes updated: done 2026-06-09
C2-S9 MARK 1/7 V1 normalization retry/watchdog SystemConfig facts documented: done 2026-06-09
C2-S9 MARK 2/7 normalization-worker has dedicated config cache/event listener: done 2026-06-09
C2-S9 MARK 3/7 RawVideoNormalizationService uses runtime/DB/default max retries with V1 default 5: done 2026-06-09
C2-S9 MARK 4/7 RawVideoNormalizationService uses runtime/DB/default backoff base seconds with V1 default 60: done 2026-06-09
C2-S9 MARK 5/7 NormalizationRetryWatchdog uses runtime/DB/default batch size and stale pending threshold: done 2026-06-09
C2-S9 MARK 6/7 normalization-worker tests and K8S dry-run pass without real Rabbit: done 2026-06-09
C2-S9 MARK 7/7 docs/tasks indexes updated: done 2026-06-09
C2-S10 MARK 1/6 V1 aggregation batch SystemConfig facts documented: done 2026-06-09
C2-S10 MARK 2/6 aggregation-worker has dedicated config cache/event listener: done 2026-06-09
C2-S10 MARK 3/6 AggregationScheduler uses runtime/DB/default batch size with V1 default 200: done 2026-06-09
C2-S10 MARK 4/6 dev K8S keeps low-resource APP_AGGREGATION_BATCH_SIZE override: done 2026-06-09
C2-S10 MARK 5/6 aggregation-worker tests and K8S dry-run pass without real Rabbit: done 2026-06-09
C2-S10 MARK 6/6 docs/tasks indexes updated: done 2026-06-09
C2-S11 MARK 1/8 V1 MAIL SystemConfig facts documented: done 2026-06-09
C2-S11 MARK 2/8 notification-worker has dedicated config cache/event listener: done 2026-06-09
C2-S11 MARK 3/8 mail enabled/from reads runtime/DB/default with V1 defaults: done 2026-06-09
C2-S11 MARK 4/8 SMTP connect options read runtime/DB/default with V1 defaults: done 2026-06-09
C2-S11 MARK 5/8 runtime env overrides DB and V1 defaults for dev safety: done 2026-06-09
C2-S11 MARK 6/8 config event evicts changed MAIL key cache: done 2026-06-09
C2-S11 MARK 7/8 notification-worker tests and K8S dry-run pass without real SMTP/Rabbit: done 2026-06-09
C2-S11 MARK 8/8 docs/tasks indexes updated: done 2026-06-09
C2-S12 MARK 1/8 V1 metadata provider SystemConfig facts documented: done 2026-06-09
C2-S12 MARK 2/8 metadata-worker has dedicated config cache/event listener: done 2026-06-09
C2-S12 MARK 3/8 dispatch provider fan-out reads runtime/DB/default provider enable flags: done 2026-06-09
C2-S12 MARK 4/8 provider worker execute gate reads the same runtime/DB/default provider enable flags: done 2026-06-09
C2-S12 MARK 5/8 pending registry TTL reads runtime/DB/default with V1/V3 aliases documented: done 2026-06-09
C2-S12 MARK 6/8 runtime env overrides DB/default for dev no-real-provider safety: done 2026-06-09
C2-S12 MARK 7/8 metadata-worker tests and K8S dry-run pass without real provider/Rabbit: done 2026-06-09
C2-S12 MARK 8/8 docs/tasks indexes updated: done 2026-06-09
C2-S13 MARK 1/7 V1 maintenance reindex actual config facts documented: done 2026-06-09
C2-S13 MARK 2/7 ops-service has dedicated config cache/event listener: done 2026-06-09
C2-S13 MARK 3/7 reindex dev limit reads runtime/DB/fallback per execution: done 2026-06-09
C2-S13 MARK 4/7 runtime env overrides DB and fallback, invalid values fallback safely: done 2026-06-09
C2-S13 MARK 5/7 dev K8S keeps low resource override and config listener enabled: done 2026-06-09
C2-S13 MARK 6/7 ops-service tests and K8S dry-run pass without real Rabbit: done 2026-06-09
C2-S13 MARK 7/7 docs/tasks indexes updated: done 2026-06-09
C2-S14 MARK 1/5 content-service scraper base URL consumer scan documented: done 2026-06-09
C2-S14 MARK 2/5 InternalContentClients reads runtime/DB/V3 fallback per refetch call: done 2026-06-09
C2-S14 MARK 3/5 q.content.config_changed and APP_CONTENT_CONFIG_LISTENER_ENABLED are declared: done 2026-06-09
C2-S14 MARK 4/5 content-service tests and manifest server dry-run pass: done 2026-06-09
C2-S14 MARK 5/5 docs/tasks inventory records remaining content startup-bound configs: done 2026-06-09
C2-S21 MARK 1/4 Rabbit outage / missed-message strategy decided as event evict + short TTL read-through: done 2026-06-09
C2-S21 MARK 2/4 content-service DB fallback cache refreshes after TTL without Rabbit event: done 2026-06-09
C2-S21 MARK 3/4 runtime override remains higher priority than DB cache/default: done 2026-06-09
C2-S21 MARK 4/4 content-service focused tests and docs/tasks/report updated; C2 parent closed: done 2026-06-09
C1-S1 MARK 5/5 catalog dictionary seed first slice covered by catalog-service unit tests: done 2026-06-08
C1-S2 MARK 5/5 data source preset/default_mapping first slice covered by catalog-service unit tests: done 2026-06-08
C1-S2b MARK 5/5 remote category sync first slice covered by catalog-service fake HTTP/repository tests: done 2026-06-08
C1-S2c MARK 1/6 V1 countries/languages full preset imported into catalog-service runtime owner seed: done 2026-06-10
C1-S2c MARK 2/6 default seed remains lowest priority and does not overwrite runtime/DB/user-edited rows: done 2026-06-10
C1-S2c MARK 3/6 cache warmup/TTL/evict first closure strategy recorded; normalization SystemConfig TTL fallback added: done 2026-06-10
C1-S2c MARK 4/6 resolver/resource live smoke prep script exists and is dry-run safe by default: done 2026-06-10, live deferred
C1-S2c MARK 5/6 focused catalog-service and normalization-worker tests pass: done 2026-06-10
C1-S2c MARK 6/6 C1 task, 089/093 ledgers and unified testing list updated: done 2026-06-10
C1-S3 MARK 1/4 notification-worker mail listener has explicit autoStartup gate: done 2026-06-09
C1-S3 MARK 2/4 application.yaml exposes APP_NOTIFICATION_LISTENER_ENABLED default false: done 2026-06-09
C1-S3 MARK 3/4 notification-worker tests pass without real Rabbit/SMTP: done 2026-06-09
C1-S3 MARK 4/4 C1/089/docs/tasks indexes updated: done 2026-06-09
C1-S4 MARK 1/5 V1 TaskInitializer facts documented in task spec: done 2026-06-09
C1-S4 MARK 2/5 task-service startup runner creates auto/full tasks for each data source: done 2026-06-09
C1-S4 MARK 3/5 default field values match V1 TaskInitializer: done 2026-06-09
C1-S4 MARK 4/5 seed is idempotent and does not overwrite existing named tasks: done 2026-06-09
C1-S4 MARK 5/5 cron scheduler gate defaults closed and task-service tests/docs indexes updated: done 2026-06-09
C1-S5 MARK 1/5 V1 ResolverInitializer default facts documented in task spec: done 2026-06-09
C1-S5 MARK 2/5 content-service startup runner creates missing default resolvers: done 2026-06-09
C1-S5 MARK 3/5 seed is idempotent and does not overwrite existing resolver rows: done 2026-06-09
C1-S5 MARK 4/5 disabled gate skips writes: done 2026-06-09
C1-S5 MARK 5/5 content-service tests and K8S dry-run pass, docs/tasks updated: done 2026-06-09
C1-S6 MARK 1/6 V1 CredentialInitializer env/file facts documented in task spec: done 2026-06-09
C1-S6 MARK 2/6 credential-service imports V1 env credentials with matching names/default fields: done 2026-06-09
C1-S6 MARK 3/6 file importer reads configured/default JSON and skips unsupported/invalid entries safely: done 2026-06-09
C1-S6 MARK 4/6 create-if-absent is fingerprint idempotent and does not overwrite existing credentials: done 2026-06-09
C1-S6 MARK 5/6 disabled gate/no-env/no-file paths perform no writes and logs do not include payload values: done 2026-06-09
C1-S6 MARK 6/6 credential-service tests and K8S dry-run pass, docs/tasks updated: done 2026-06-09
C1-S7 MARK 1/5 CredentialSummary payload redaction contract documented in task spec: done 2026-06-09
C1-S7 MARK 2/5 admin/public credential summaries return redacted metadata instead of secret values: done 2026-06-09
C1-S7 MARK 3/5 payloadKeys/fingerprintSuffix remain available for admin display and duplicate diagnostics: done 2026-06-09
C1-S7 MARK 4/5 internal lease still returns real secret payload under internal token path: done 2026-06-09
C1-S7 MARK 5/5 credential-service tests pass and docs/tasks updated: done 2026-06-09
C1-S8 MARK 1/5 V1 create/update/partialUpdate sync trigger facts documented in task spec: done 2026-06-09
C1-S8 MARK 2/5 catalog createDataSource triggers remote category sync after save: done 2026-06-09
C1-S8 MARK 3/5 catalog updateDataSource and patchDataSource trigger remote category sync after successful write: done 2026-06-09
C1-S8 MARK 4/5 sync failure is best-effort and does not fail data-source write: done 2026-06-09
C1-S8 MARK 5/5 catalog-service tests pass and docs/tasks updated: done 2026-06-09
C1-S9 MARK 1/5 task spec fixes one verifiable watchdog slice: done 2026-06-09
C1-S9 MARK 2/5 TaskWatchdog has default-off ConditionalOnProperty gate: done 2026-06-09
C1-S9 MARK 3/5 application.yaml and dev manifest expose APP_TASK_WATCHDOG_ENABLED=false: done 2026-06-09
C1-S9 MARK 4/5 task-service tests prove disabled default and enabled opt-in: done 2026-06-09
C1-S9 MARK 5/5 C1/089 record implementation result, tests and remaining boundary: done 2026-06-09
F2/B15 MARK 1/6 dirty lifecycle/reconciliation local behavior: done locally 2026-06-10; live reconciliation smoke deferred
F2/B15 MARK 2/6 search producer side effects: partial 2026-06-10; structured INDEX/DELETE and malformed reject covered, durable outbox/stats deferred
F2/B15 MARK 3/6 handler order/failure isolation edge cases: done locally 2026-06-10; pipeline failure keeps raw PENDING and unified DIRTY
F2/B15 MARK 4/6 context recall edge cases: unchanged from B13; SQL fallback guard remains covered by existing tests
F2/B15 MARK 5/6 focused local tests: done 2026-06-10; aggregation/search focused tests passed
F2/B15 MARK 6/6 docs/tasks ledgers: done 2026-06-10
```

