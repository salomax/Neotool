# Asset Service Refactor Checklist

Step-by-step tasks to simplify storage keys, encode visibility per namespace, and clean up the GraphQL/domain surface so any contributor/LLM can implement safely.

## Goals
- Replace `{namespace}/{resourceType}/{resourceId}/{uuid}` keys with namespace-configured templates (e.g., `user-profiles/{ownerId}/{assetId}`, `public/institution-logo/{assetId}`) — no `resourceId` dependency.
- Make visibility explicit (`PUBLIC` vs `PRIVATE`) per namespace and drive URL generation/authorization from it.
- Keep signed URL TTL configurable; avoid persisting absolute CDN URLs (derive from configurable base).

## Phase 1: Config Model
1) Update `asset-config.yml` schema:
   - For each namespace add: `visibility: PUBLIC|PRIVATE`, `keyTemplate`, `uploadTtlSeconds` (per-namespace override; fallback to global).
   - Drop `resourceTypes` and any notion of retentionDays/maxAssetsPerResource.
2) `AssetConfigProperties`:
   - Parse new fields and expose `NamespaceConfig.visibility`, `NamespaceConfig.keyTemplate`, `NamespaceConfig.uploadTtlSeconds?`.
   - Remove `resourceTypes` from parsing/DTO.
   - Keep rate limit parsing unchanged.

## Phase 2: Domain & Persistence
1) `Asset.kt`:
   - Add `visibility: AssetVisibility` (new enum with `PUBLIC`, `PRIVATE`).
   - Remove `resourceType` from the domain if no longer needed; keep only if you still want semantic tagging (but decouple from key).
   - Add `uploadTtlSeconds` if you want to surface per-asset TTL in DTOs; otherwise just use per-request config.
   - Replace `generateStorageKey` to delegate to a new `StorageKeyFactory` using template + variables.
   - Clarify `generatePublicUrl` to take a configurable base; document that stored `publicUrl` is deprecated/non-authoritative.
2) `AssetEntity.kt` + DB migration:
   - Add `visibility` column (ENUM or VARCHAR) with default `PRIVATE`.
   - Drop `resource_type` column if the enum is removed; otherwise keep but note it’s metadata only.
   - Drop `public_url` if not needed; keep nullable until migration completes.
   - Add/update indexes: drop `resource_type` from composite indexes; consider `(owner_id, namespace)` and `(namespace, status)`.
3) `AssetResourceType.kt`:
   - Remove file if unused. If kept, note it’s informational only (not part of key/authorization).

## Phase 3: Storage Key Factory
1) Create `StorageKeyFactory`:
   - Input: namespace config, `ownerId`, `assetId` (UUID).
   - Supported placeholders: `{namespace}`, `{ownerId}`, `{assetId}`.
   - Validate required placeholders per namespace (e.g., user profiles require `{ownerId}`).
   - Provide `buildKey(namespaceConfig, ownerId, assetId)` -> `String`.
2) Wire into `AssetService.initiateUpload` to generate `storageKey` from the template instead of `Asset.generateStorageKey`.

## Phase 4: Services & Auth
1) `ValidationService`:
   - Remove `resourceType` from signature; validate mime/size purely by namespace.
2) `AssetService.initiateUpload`:
   - Fetch namespace config, derive TTL: `namespace.uploadTtlSeconds ?: storageProperties.uploadTtlSeconds`.
   - Generate storage key via `StorageKeyFactory`.
   - Persist `visibility` on the asset entity.
3) `AssetService.getAsset` / access control:
   - If `visibility == PUBLIC`, allow read without owner check (but still return `null` on missing).
   - If `visibility == PRIVATE`, require ownership (or hook for future group-based auth).
   - For private assets, generate a presigned download URL (`StorageClient.generatePresignedDownloadUrl`) instead of returning a permanent URL.
4) `AssetService.confirmUpload`:
   - Use the namespace-configured TTL where relevant.
   - Clear upload URL after confirm; set status READY as today.
5) URL generation:
   - Stop persisting absolute `publicUrl`; compute on the fly with `StorageClient.generatePublicUrl(storageKey)` when `visibility == PUBLIC`.
   - For `PRIVATE`, expose a `downloadUrl(ttlSeconds)` resolver that calls `generatePresignedDownloadUrl`.

## Phase 5: GraphQL & DTOs
1) `schema.graphqls`:
   - Update `Asset` type: add `visibility`; deprecate/remove `resourceType` if not needed; mark `publicUrl` deprecated or replace with `downloadUrl(ttlSeconds: Int): String`.
   - Update `CreateAssetUploadInput`: drop `resourceType`; keep `namespace`, `filename`, `mimeType`, `sizeBytes`, `idempotencyKey`.
2) DTOs/mappers (`AssetDTO`, `CreateAssetUploadInput`, `ConfirmAssetUploadInput`) and resolvers:
   - Align fields with schema changes.
   - Ensure resolvers pass caller `actorId` as `ownerId` and apply visibility rules when resolving URLs.
3) Wiring (`AssetWiringFactory`):
   - Adjust data fetchers to surface `visibility`, the new URL fields, and to not expose `publicUrl` as authoritative.

## Phase 6: Storage & Config Defaults
1) `StorageClient`:
   - Ensure `generatePresignedUploadUrl` uses the namespace TTL override.
   - Add/confirm `generatePresignedDownloadUrl(ttlSeconds)` is used for private assets.
   - Keep `generatePublicUrl(storageKey)` for public assets; base URL must be configurable (do not hardcode).
2) `StorageConfig` / `StorageProperties`:
   - Add `uploadTtlSeconds` default and optional `downloadTtlSeconds`.
   - Add `publicBaseUrl` (CDN) as non-authoritative and use only at generation time.

## Phase 7: Migration Plan
1) Database migration:
   - Add `visibility` column with default `PRIVATE`.
   - Optionally drop `resource_type` and `public_url` columns if removing.
   - Update indexes accordingly.
2) Data backfill:
   - Set `visibility = PUBLIC` for namespaces intended to be public (e.g., `group-assets` logos if desired), else `PRIVATE`.
3) Storage key rollout:
   - Existing assets keep old keys; new uploads use templates. If you need uniformity, script a re-key/move with careful redirections.

## Phase 8: Testing
1) Unit tests:
   - `StorageKeyFactory` placeholder substitution/validation.
   - `ValidationService` without `resourceType`.
   - Visibility-based access rules in `AssetService.getAsset`.
2) Integration tests:
   - Upload/confirm/read for PUBLIC: returns stable CDN URL from `generatePublicUrl`.
   - Upload/confirm/read for PRIVATE: requires owner and returns presigned download URL; unauthorized access returns null/error.
   - Namespace TTL override honored for upload URL expiry.
3) Contract tests for GraphQL mutations/queries matching the new schema.

## Examples: Public vs Private Flows
- Config
  - `user-profiles`: `visibility: PRIVATE`, `keyTemplate: "user-profiles/{ownerId}/{assetId}"`, `uploadTtlSeconds: 900`
  - `institution-logos`: `visibility: PUBLIC`, `keyTemplate: "public/institution-logo/{assetId}"`, `uploadTtlSeconds: 900`
- Public upload (logo)
  - Mutation: `createAssetUpload(namespace: "institution-logos", filename: "logo.png", mimeType: "image/png", sizeBytes: 12345)`
  - Response: `uploadUrl` (PUT, expires in namespace TTL), `storageKey: public/institution-logo/<uuid>`, `visibility: PUBLIC`
  - Client uploads via `uploadUrl`, then calls `confirmAssetUpload(assetId)`.
  - Querying the asset returns `publicUrl` derived from `publicBaseUrl + storageKey`; no auth check needed.
- Private upload (user profile)
  - Mutation: `createAssetUpload(namespace: "user-profiles", filename: "avatar.jpg", mimeType: "image/jpeg", sizeBytes: 54321)`
  - Response: `uploadUrl` (PUT, expires in namespace TTL), `storageKey: user-profiles/<ownerId>/<uuid>`, `visibility: PRIVATE`
  - Client uploads, then `confirmAssetUpload(assetId)`.
  - Querying the asset:
    - Owner receives `downloadUrl(ttlSeconds: optional)` generated via `generatePresignedDownloadUrl` (GET, short-lived).
    - Non-owner receives `null`/authorization failure; no permanent public URL is exposed.

## Phase 9: Cleanup & Docs
1) Remove dead code/fields related to `resourceType` in storage keys and config.
2) Update developer docs and examples to show new key templates and visibility semantics.
3) Add a short “how to add a namespace” guide: required fields, template placeholders, visibility choice, TTL override.

## Notes & Decisions
- No `retentionDays` or `maxAssetsPerResource` fields.
- Signed URL TTL: support per-namespace override with global default.
- Do not treat stored `publicUrl` as authoritative; always derive from config + storage key. If CDN base changes, regenerate URLs on the fly.
