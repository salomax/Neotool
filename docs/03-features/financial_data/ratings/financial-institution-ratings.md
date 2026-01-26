# Financial Institution Ratings Ingestion

**Type**: Backend + Workflow  
**Module**: financial_data  
**Status**: Draft

## Problem/Context

We need to store ratings for financial institutions (Fitch, S&P Global, Moody's, etc.) associated with `financial_data.financial_institution`, including change history, evidence of the data source, and support for multiple sources (API or scraper). The process must be idempotent and follow the design patterns of existing Go cronjobs in this module.

## Functional Requirements

1. **Institution association**
   - Each `financial_institution` can have `n` ratings.
   - Each rating belongs to an issuer (Fitch, S&P Global, Moody's).

2. **Minimum rating fields**
   - `name` (ex: National Short Term Rating)
   - `label` (ex: (AAA).br)
   - `rating_date` (ex: 2023-01-01)
   - `issuer`
   - `custom_attributes` (JSON, optional, issuer-specific)

3. **Source and evidence**
   - Store the source URL.
   - Store evidence (raw API JSON or rendered HTML without scripts).
   - Capture metadata: `fetched_at`, `http_status`, `content_hash`.

4. **Change history**
   - Maintain a history table for all rating changes.
   - Record updates when relevant fields change (label, rating_date, custom parameters).

5. **Idempotency**
   - Re-running the job with no changes should not create new records.
   - Change detection must use normalized rating content.

6. **Issuer-specific institution identifiers**
   - Each issuer uses its own external identifier (slug, id, etc.).
   - There must be a configurable mapping between `financial_institution` and issuer identifiers.
   - Example: Fitch -> slug `banco-do-brasil-sa-80089721`, S&P Global -> id `349026`.
   - That data is pre-loaded since it's very hard to figure out withtin the processing

7. **Per-institution configuration**
   - Ability to enable/disable ingestion per issuer for each institution.
   - Ability to override issuer identifiers per institution.

8. **Phase 1 (Fitch) technical requirements**
   - Endpoint: `https://api.fitchratings.com/` (GraphQL).
   - Request uses the `Entity` query with `slug` (ex: `banco-do-brasil-sa-80089721`).
   - Throttling: 1 request every 15 seconds (per issuer).
   - The Fitch slug must be stored per institution (issuer config) and used in the request payload.

## Data Model (high level)

### Core tables
- `financial_data.financial_institution_rating`
  - `id`
  - `financial_institution_id` (FK)
  - `issuer_id` (FK)
  - `name`
  - `label`
  - `rating_date`
  - `custom_params` (JSONB)
  - `source_evidence_id` (FK to evidence)
  - `content_hash` (hash of normalized rating)
  - `created_at`, `updated_at`

- `financial_data.financial_institution_rating_history`
  - `id`
  - `financial_institution_id` (FK)
  - `issuer_id` (FK)
  - `name`
  - `label`
  - `rating_date`
  - `custom_params` (JSONB)
  - `source_evidence_id` (FK to evidence)
  - `changed_at`


### Support tables
- `financial_data.rating_issuer`
  - `id`
  - `code` (unique, ex: FITCH)
  - `name` (display name)
  - `website`
  - `config` (JSONB for issuer-specific metadata, ex: throttling rules)
  - `logo_asset_id` (FK)
  - `enabled`
  - `created_at`, `updated_at`


- `financial_data.financial_institution_rating_issuer`
  - `id`
  - `rating_issuer_id` (FK)
  - `financial_institution_id` (FK)
  - `config` (JSONB for issuer-specific metadata, ex: slugs, ids or any other identification)

- `financial_data.rating_source_evidence`
  - `id`
  - `issuer_id`
  - `fetched_at`
  - `metadata` (request and responde metadata for throubleshooting, i.e.: url, content_type, http_status)
  - `raw_payload` TEXT
  - `content_hash`

## Implementation Strategy (high level)

### Architecture (following Go cronjob patterns)
Based on `workflow/golang/financial_data/bacen_ifdata`:

```
cmd/
└── cronjob/            # scheduled execution
└── standalone/         # standalone execution
internal/
├── domain/             # models, interfaces, validation
├── application/        # job orchestration
├── infrastructure/     # repositories, HTTP clients, evidence storage
├── processor/          # rating parsing/normalization
└── shared/             # utilities (hash, retry, rate-limit)
```

### Cronjob Flow
1. Load active configurations (`financial_data.financial_institution_rating_issuer` and `rating_issuer.config`).
2. For each enabled issuer:
   - Fetch the financial_institution_rating and its config
   - Fetch data via API or scraper (with rate limit and retry) using the financial_institution_rating config and issuer config.
   - Save evidence (raw payload + metadata) / if idempotent hash is different.
   - Normalize ratings into the internal model.
3. Compute `content_hash` per rating (canonical fields + normalized `custom_params`).
4. Upsert into `financial_institution_rating`:
   - If hash matches current, do nothing.
   - If changed, update and insert history.
5. Emit metrics and logs for the run.

### Idempotency
- Hash based on: `issuer + name + label + rating_date + custom_params_normalized`.
- Insert history only when `content_hash` changes.
- Optionally keep an evidence hash separately for audit.

### Issuer Patterns
- **Strategy/Factory pattern** to encapsulate issuer specifics:
  - `IssuerClient` (API or scraper)
  - `IssuerParser` (normalization)
  - `IssuerIdentifierResolver`
- Each issuer implementation remains isolated from core logic.

### Evidence and Data Origin
- Persist `source_url` and evidence:
  - API: raw JSON in `rating_source_evidence`.
  - Scraper: rendered HTML without scripts.
- Store large payloads in the asset service and keep `asset_id` in evidence.
- the rating name must be in the uppercase snake case


## Integration Points

- **Database**: `financial_data` schema
- **Kubernetes CronJob**: manifest similar to existing cronjobs
- **Config**: environment variables per issuer (URLs, tokens, timeouts)

## Decisions (Updated)

1. **Evidence storage**: Database (payloads are expected to be small).
2. **Initial issuer**: Fitch only (phase 1). Others later.
3. **Cronjob frequency**: Weekly.
4. **API exposure**: Yes, expose ratings in the Kotlin `financialdata` module by extending `BacenFinanceInstitution` with rating types.

## Seed Data (Phase 1)

Insert initial `rating_issuer` record for Fitch:

```
code: FITCH
name: Fitch
website: www.fitchratings.com
config:
  url_base: https://www.fitchratings.com/entity/
enabled: true
```

## Fitch Endpoint (example with banco do brasil)

"slug":"banco-do-brasil-sa-80089721"

```
curl 'https://api.fitchratings.com/' \
  -X POST \
  -H 'User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:147.0) Gecko/20100101 Firefox/147.0' \
  -H 'Accept: */*' \
  -H 'Accept-Language: en-US,en;q=0.9' \
  -H 'Referer: https://www.fitchratings.com/' \
  -H 'content-type: application/json' \
  -H 'Origin: https://www.fitchratings.com' \
  -H 'Connection: keep-alive' \
  -H 'Sec-Fetch-Dest: empty' \
  -H 'Sec-Fetch-Mode: cors' \
  -H 'Sec-Fetch-Site: same-site' \
  -H 'Priority: u=4' \
  --data-raw $'{"operationName":"Entity","variables":{"slug":"banco-do-brasil-sa-80089721"},"query":"query Entity($slug: String\041) {\\n  getEntity(slug: $slug) {\\n    slug\\n   name\\n    id\\n    ratings {\\n      id\\n      orangeDisplay\\n      correctionFlag\\n      ratingChangeDate\\n      ratingActionDescription\\n      ratingCode\\n      ratingAlertCode\\n      ratingEffectiveDate\\n      ratingTypeDescription\\n      ratingLocalValue\\n      ratingLocalActionDescription\\n      recoveryRatingValue\\n      }\\n    }\\n}\\n"}'
```

Response

````
{
  "data": {
    "getEntity": {
      "slug": "banco-do-brasil-sa-80089721",
      "name": "Banco do Brasil S.A.",
      "id": 80089721,
      "ratings": [
        {
          "id": "129697998",
          "orangeDisplay": false,
          "correctionFlag": false,
          "ratingChangeDate": "2025-12-12T17:40:16.000Z",
          "ratingActionDescription": "Affirmed",
          "ratingCode": "BB",
          "ratingAlertCode": "RO:Sta",
          "ratingEffectiveDate": "2025-12-12T15:02:52.000Z",
          "ratingTypeDescription": "Long Term Issuer Default Rating",
          "ratingLocalValue": "BB",
          "ratingLocalActionDescription": "Affirmed",
          "recoveryRatingValue": null
        },
        {
          "id": "129698003",
          "orangeDisplay": false,
          "correctionFlag": false,
          "ratingChangeDate": "2025-12-12T17:40:16.000Z",
          "ratingActionDescription": "Affirmed",
          "ratingCode": "B",
          "ratingAlertCode": null,
          "ratingEffectiveDate": "2025-12-12T15:02:52.000Z",
          "ratingTypeDescription": "Short Term Issuer Default Rating",
          "ratingLocalValue": "B",
          "ratingLocalActionDescription": "Affirmed",
          "recoveryRatingValue": null
        },
        {
          "id": "129698005",
          "orangeDisplay": false,
          "correctionFlag": false,
          "ratingChangeDate": "2025-12-12T17:40:16.000Z",
          "ratingActionDescription": "Affirmed",
          "ratingCode": "AAA(bra)",
          "ratingAlertCode": "RO:Sta",
          "ratingEffectiveDate": "2025-12-12T15:02:52.000Z",
          "ratingTypeDescription": "National Long Term Rating",
          "ratingLocalValue": "AAA(bra)",
          "ratingLocalActionDescription": "Affirmed",
          "recoveryRatingValue": null
        },
        {
          "id": "129698000",
          "orangeDisplay": false,
          "correctionFlag": false,
          "ratingChangeDate": "2025-12-12T17:40:16.000Z",
          "ratingActionDescription": "Affirmed",
          "ratingCode": "bb",
          "ratingAlertCode": null,
          "ratingEffectiveDate": "2025-12-12T15:02:52.000Z",
          "ratingTypeDescription": "Government Support Rating",
          "ratingLocalValue": "bb",
          "ratingLocalActionDescription": "Affirmed",
          "recoveryRatingValue": null
        },
        {
          "id": "129698001",
          "orangeDisplay": false,
          "correctionFlag": false,
          "ratingChangeDate": "2025-12-12T17:40:16.000Z",
          "ratingActionDescription": "Affirmed",
          "ratingCode": "B",
          "ratingAlertCode": null,
          "ratingEffectiveDate": "2025-12-12T15:02:52.000Z",
          "ratingTypeDescription": "Local Currency Short Term Issuer Default Rating",
          "ratingLocalValue": "B",
          "ratingLocalActionDescription": "Affirmed",
          "recoveryRatingValue": null
        },
        {
          "id": "129698002",
          "orangeDisplay": false,
          "correctionFlag": false,
          "ratingChangeDate": "2025-12-12T17:40:16.000Z",
          "ratingActionDescription": "Affirmed",
          "ratingCode": "BB",
          "ratingAlertCode": "RO:Sta",
          "ratingEffectiveDate": "2025-12-12T15:02:52.000Z",
          "ratingTypeDescription": "Local Currency Long Term Issuer Default Rating",
          "ratingLocalValue": "BB",
          "ratingLocalActionDescription": "Affirmed",
          "recoveryRatingValue": null
        },
        {
          "id": "129698007",
          "orangeDisplay": false,
          "correctionFlag": false,
          "ratingChangeDate": "2025-12-12T17:40:16.000Z",
          "ratingActionDescription": "Affirmed",
          "ratingCode": "bb",
          "ratingAlertCode": null,
          "ratingEffectiveDate": "2025-12-12T15:02:52.000Z",
          "ratingTypeDescription": "Viability Rating",
          "ratingLocalValue": "bb",
          "ratingLocalActionDescription": "Affirmed",
          "recoveryRatingValue": null
        },
        {
          "id": "129698004",
          "orangeDisplay": false,
          "correctionFlag": false,
          "ratingChangeDate": "2025-12-12T17:40:16.000Z",
          "ratingActionDescription": "Affirmed",
          "ratingCode": "F1+(bra)",
          "ratingAlertCode": null,
          "ratingEffectiveDate": "2025-12-12T15:02:52.000Z",
          "ratingTypeDescription": "National Short Term Rating",
          "ratingLocalValue": "F1+(bra)",
          "ratingLocalActionDescription": "Affirmed",
          "recoveryRatingValue": null
        },
        {
          "id": "122646310",
          "orangeDisplay": false,
          "correctionFlag": false,
          "ratingChangeDate": "2022-06-01T21:08:55.000Z",
          "ratingActionDescription": "Withdrawn",
          "ratingCode": "WD",
          "ratingAlertCode": null,
          "ratingEffectiveDate": "2022-06-01T20:26:55.000Z",
          "ratingTypeDescription": "Support Rating Floor",
          "ratingLocalValue": "RET",
          "ratingLocalActionDescription": "retirada",
          "recoveryRatingValue": null
        },
        {
          "id": "122646308",
          "orangeDisplay": false,
          "correctionFlag": false,
          "ratingChangeDate": "2022-06-01T21:08:54.000Z",
          "ratingActionDescription": "Withdrawn",
          "ratingCode": "WD",
          "ratingAlertCode": null,
          "ratingEffectiveDate": "2022-06-01T20:26:55.000Z",
          "ratingTypeDescription": "Support Rating",
          "ratingLocalValue": "RET",
          "ratingLocalActionDescription": "retirada",
          "recoveryRatingValue": null
        },
        {
          "id": "88378503",
          "orangeDisplay": false,
          "correctionFlag": false,
          "ratingChangeDate": "2012-01-25T14:20:00.000Z",
          "ratingActionDescription": "Withdrawn",
          "ratingCode": "WD",
          "ratingAlertCode": null,
          "ratingEffectiveDate": "2012-01-25T14:19:59.000Z",
          "ratingTypeDescription": "Individual Rating",
          "ratingLocalValue": "RET",
          "ratingLocalActionDescription": "retirada",
          "recoveryRatingValue": null
        }
      ]
    }
  }
}
```