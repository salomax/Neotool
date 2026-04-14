# Merchants — Feature Spec

## Context

Merchants are payees associated with transactions. They power categorization rules, top-spend analytics, and the transaction table display. The current state:

- `banking.merchants` table already exists with `account_id` (null = system, UUID = user-scoped)
- System merchants are seeded from CSV / enrichment pipeline
- User merchants can already be created via `createMerchant` mutation
- Logo is resolved via `banking.merchant_logo_cache` keyed by `domain` (fetched from the assets service)
- `MerchantSelect` component exists in the transaction form but fetches the entire list at once

What's missing: server-side search + pagination, logo.dev integration on creation, and in-picker merchant creation.

---

## Requirements

### R1 — Server-side search & pagination on `merchants` query

The `merchants` query currently returns a flat `[Merchant!]!` with no filtering. Replace it with a paginated, searchable version.

**GraphQL changes:**
```graphql
type MerchantConnection {
  edges: [MerchantEdge!]!
  pageInfo: PageInfo!
  totalCount: Int!
}

type MerchantEdge {
  node: Merchant!
  cursor: String!
}

# Replace current flat query:
merchants(
  search: String        # filter by name (word-boundary CONTAINS, case-insensitive)
  after: String         # cursor for forward pagination
  first: Int = 20       # page size, max 50
): MerchantConnection!
```

**Backend:**
- `MerchantRepository.searchForAccount(accountId, search, after, first)` — keyset pagination ordered by `name ASC, id ASC`
- Cursor = base64-encoded `"name:id"` pair
- Search uses the same word-boundary logic as `CategoryRuleService.containsWholeWord`
- `MerchantService.search(...)` delegates to repository
- `MerchantResolver.merchants(...)` updated with new args

**Frontend (`MerchantSelect`):**
- Replace `useLazyQuery(MERCHANTS)` with `useLazyQuery(MERCHANTS, { variables: { first: 20 } })`
- Debounce search input (300ms) → re-query with `search` variable
- Infinite scroll or "Load more" button using `fetchMore` with `after: pageInfo.endCursor`
- `cache-first` replaced with `network-only` on search change, `cache-first` on pagination

---

### R2 — User merchant creation in the picker

Users can create a custom merchant directly from `MerchantSelect`, the same way categories are created in `CategorySelect`.

**Fields:**
| Field | Required | Notes |
|---|---|---|
| Name | Yes | Max 100 chars |
| Website | No | e.g. `netflix.com` or `https://netflix.com` — strip protocol/path, keep domain |

**Logo resolution flow (logo.dev):**
1. User enters a website → strip to bare domain (e.g. `netflix.com`)
2. Show live logo preview using `https://img.logo.dev/{domain}?token={token}&size=40&format=png` while the user types (debounced 500ms)
3. On save → backend stores `domain` → async job fetches the logo via logo.dev, uploads to asset service, writes to `merchant_logo_cache`
4. If no website → no logo (show initials avatar)

**Backend changes:**
- `createMerchant` mutation already exists — add `website: String` input field (normalized to domain server-side)
- New `MerchantLogoHydrationService` (or extend existing job): given a domain, fetch `https://img.logo.dev/{domain}?token={token}`, upload to assets, cache in `merchant_logo_cache`
- logo.dev API token stored in app config (env var `LOGO_DEV_TOKEN`)

**Frontend (picker "create" view):**
- Trigger: "Criar estabelecimento" button at the bottom of the picker list (same pattern as CategoryPicker)
- View states: `browse` → `create` → `confirm`
- `browse`: search + list + "Criar estabelecimento" button
- `create`: name field + website field + logo preview chip + Save/Cancel
- `confirm`: "Estabelecimento criado" confirmation, auto-closes after 1.5s
- On save: `createMerchant` mutation → optimistic update selects the new merchant immediately

---

### R3 — Merchant type extended

Add `isSystem` visibility to the picker:

- System merchants: shown to all users, read-only (no delete/edit)
- User merchants: shown only to their account, can be edited or deleted

**`Merchant` type additions:**
```graphql
type Merchant {
  # existing fields ...
  isSystem: Boolean!   # already exists
  isOwned: Boolean!    # new — true if account_id = current account (user can edit/delete)
}
```

---

## Current State Summary

| Area | Status |
|---|---|
| `banking.merchants` table | ✅ exists |
| System merchants | ✅ seeded |
| `createMerchant` mutation | ✅ exists |
| Domain → logo cache | ✅ exists (`merchant_logo_cache`) |
| `merchants` query (flat) | ✅ exists |
| `MerchantSelect` component | ✅ exists (lazy-loaded) |
| Server-side search & pagination | ❌ missing |
| logo.dev integration | ❌ missing |
| In-picker creation | ❌ missing |
| Merchant edit / delete | ❌ missing |

---

## Backlog

### BK-M1 — Server-side search & pagination (backend)
**Scope:** Backend only  
- Add `MerchantRepository.searchForAccount(accountId, search?, after?, first)` with keyset pagination (`name ASC, id ASC`)
- Update `MerchantService.search(...)` 
- Update `MerchantResolver.merchants(...)` with new args
- Update GraphQL schema: `MerchantConnection`, `MerchantEdge`, `PageInfo` (reuse if already defined), new `merchants(search, after, first)` signature
- Unit tests: pagination cursor roundtrip, search word-boundary, empty result

### BK-M2 — Server-side search & pagination (frontend)
**Depends on:** BK-M1  
**Scope:** Frontend only  
- Update `MERCHANTS` GQL query with `search`, `after`, `first` args
- Update `MerchantSelect` to debounce search (300ms) and re-query
- Implement "load more" via `fetchMore` with `after: pageInfo.endCursor`
- Show result count or "showing X of Y" hint

### BK-M3 — logo.dev integration (backend)
**Scope:** Backend only  
- Add `LOGO_DEV_TOKEN` to app config
- Implement `MerchantLogoHydrationService.hydrateForDomain(domain)`:
  - Fetch `https://img.logo.dev/{domain}?token={token}&size=80&format=png`
  - Upload to assets service
  - Write to `merchant_logo_cache`
  - Skip if already cached and `fetched_at` < 30 days ago
- Wire into `createMerchant`: call hydration async after save (fire-and-forget, don't fail creation)
- Wire into existing sweeper or a new `@Scheduled` job to backfill logo-less merchants with a domain

### BK-M4 — In-picker merchant creation (frontend)
**Depends on:** BK-M3  
**Scope:** Frontend only  
- Add `create` and `confirm` view states to `MerchantSelect` (same pattern as `CategoryPicker`)
- `create` view: name input + website input + live logo preview (debounced 500ms, `https://img.logo.dev/{domain}?...`)
- Domain normalization util: strip `https://`, `www.`, trailing path → bare domain
- On save: `createMerchant` mutation → select the new merchant optimistically
- `confirm` view: success message, auto-dismiss 1.5s
- i18n keys: `merchantPicker.createMerchant`, `merchantPicker.namePlaceholder`, `merchantPicker.websitePlaceholder`, `merchantPicker.logoPreview`, `merchantPicker.created`

### BK-M5 — `createMerchant` mutation: add `website` field
**Depends on:** BK-M3  
**Scope:** Backend only  
- Add `website: String` to `CreateMerchantInput` in schema
- Normalize in service: strip protocol/path → bare domain, store as `domain`
- Validation: if provided, must resolve to a plausible domain (regex, max 200 chars)

### BK-M6 — Merchant edit & delete (backend + frontend)
**Scope:** Full stack  
- `updateMerchant(id: ID!, input: UpdateMerchantInput!)` mutation — name + website, user-owned only
- `deleteMerchant(id: ID!)` mutation — soft-delete; transactions retain `merchant_id` but picker hides it; rules with `merchant_id` get `merchant_id = null`
- Frontend: long-press or context menu on a user-owned merchant in the picker → edit / delete options
- System merchants: read-only, no edit/delete affordance

---

## Open Questions

1. **logo.dev token management** — free tier vs paid? Rate limits on hydration job backfill?
2. **Duplicate user merchants** — should creation be blocked if a merchant with the same normalized name already exists for the account? Or just warn?
3. **Merchant name display** — currently `display_name` and `merchant_name` are separate columns. Should the user-facing `name` field in the creation form map to `display_name` only, or both?
4. **Backfill scope for BK-M3** — hydrate logos for all existing system merchants with a domain on deploy, or lazily on first access?
