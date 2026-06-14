# 028-20-06 RBAC / permission code / data scope / visibility

Status: done

## Goal

Build a production-oriented RBAC foundation for V3 so `role` is no longer the only authorization signal. This slice intentionally performs a destructive token migration: pre-RBAC JWTs without required permission/scope/data-scope/content-visibility claims are invalid and must be reissued by logout/login or an explicit token rollover.

## Scope Completed

- Shared auth contract in `shared:ircs-common`:
  - `IrcsAuthClaims`
  - `IrcsAuthHeaders`
  - `IrcsPermissions`
  - `IrcsRequestPrincipal`
  - `IrcsJwtAuthDecoder`
  - `IrcsJwtRequestAuthenticator`
  - `IrcsJwtRuntimeConfigResolver`
- Identity JWTs now include deterministic claims:
  - `role`
  - `permissions`
  - `scopes`
  - `dataCategories`
  - `dataGenres`
  - `dataTags`
  - `contentVisibility`
- Tokens missing required RBAC claims are rejected. There is no role-only token compatibility path.
- `JwtAuthenticationFilter` grants authorities for role and permission codes.
- admin-bff now enforces `ROLE_ADMIN` plus route-level permission code:
  - member admin
  - content
  - catalog
  - task read/write/run
  - ops read/run
  - storage
  - config
  - credential
  - magnet read/write/run
  - scraper run
  - interaction moderation
- portal-bff now enforces member/admin permission codes for profile, interaction, and feedback owner APIs.
- BFF proxy strips inbound spoofable `X-Authenticated-*` / `X-IRCS-Auth-*` headers and only forwards trusted identity headers after local token validation.
- Public portal reads remain public; authenticated principals only expand visibility when they have `portal.read`.
- `unified_videos.content_visibility` migration added with `PUBLIC/MEMBER/ADMIN/DRAFT/HIDDEN` constraint and indexes.
- `unified_video_tags` migration added as a lightweight unified tag model.
- content-service unified admin API now reads/writes:
  - `contentVisibility`
  - `tags`
- portal-service now enforces:
  - content visibility
  - category data scope
  - genre data scope
  - tag data scope
- search-service unified index and portal search queries now enforce:
  - content visibility
  - category data scope
  - genre data scope
  - tag data scope

## Non-goals

- No physical user/role/permission tables in this slice.
- No mTLS service identity; that remains `028-20-10`.
- No recommendation system.
- No per-view audit expansion.
- No live K8S rollout in this slice; local build/test evidence only.

## Migration Notes

- Existing admin/member JWTs issued before this slice are intentionally invalid.
- Recommended rollout sequence:
  1. Apply migrator with `add-unified-video-content-visibility.sql` and `add-unified-video-tags.sql`.
  2. Roll out identity-service, admin-bff, portal-bff, content-service, portal-service, search-service.
  3. Reissue admin/member JWTs by logout/login.
  4. Trigger unified search reindex so existing ES documents receive `contentVisibility` and `tags`.
  5. Optionally bump `security.jwt.iat-floor` to force all old tokens stale.

## Verification

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :services:ircs-identity-service:test :services:ircs-interaction-service:test :services:ircs-admin-bff:test :services:ircs-portal-bff:test :services:ircs-portal-service:test :services:ircs-search-service:test :services:ircs-content-service:test
BUILD SUCCESSFUL
```

```text
.\gradlew.bat --no-daemon :shared:ircs-common:test :platform:ircs-migrator:build :services:ircs-content-service:test :services:ircs-portal-service:test :services:ircs-search-service:test :services:ircs-admin-bff:test :services:ircs-portal-bff:test
BUILD SUCCESSFUL
```

```text
.\gradlew.bat --no-daemon :shared:ircs-common:jar :platform:ircs-migrator:build :services:ircs-identity-service:bootJar :services:ircs-interaction-service:bootJar :services:ircs-admin-bff:bootJar :services:ircs-portal-bff:bootJar :services:ircs-portal-service:bootJar :services:ircs-search-service:bootJar :services:ircs-content-service:bootJar
BUILD SUCCESSFUL
```

## Residual Risk

- Direct service access still relies on namespace/network isolation until `028-20-10` mTLS service identity is implemented.
- Existing Elasticsearch indices need reindex after rollout to populate the new `contentVisibility` and `tags` fields.
- The lightweight tag model covers unified-video tags. If later V1 tag semantics require a dictionary, aliases, or hierarchical tags, that should be a follow-up content/catalog slice.

