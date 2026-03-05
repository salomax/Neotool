# Banking — Import requests

## Happy-path execution order

### OFX/CSV import flow

1. **List Financial Accounts** — Get a `financial_account_id` and set it as `financial_account_id` in your environment (or use an existing account ID).
2. **Import OFX File** — Sends a small OFX fixture; returns an import job. The response `import.id` is saved to env as `import_job_id` (post-response script).
3. **Get Import Job** — Uses `import_job_id` from step 2. Poll for status or inspect `duplicateCandidates` when status is `REVIEW_REQUIRED`.
4. **List Import Jobs** — Optional; list all import jobs for `financial_account_id`.
5. **Resolve Import Duplicate** — When an import is in `REVIEW_REQUIRED`, call with `jobId` (e.g. `import_job_id`), `rowIndex`, and `resolution` (`KEEP_BOTH` | `USE_INCOMING` | `KEEP_EXISTING`).

**Import NFC-e QR** — Standalone; pass `financialAccountId` and a valid SEFAZ NFC-e URL. Does not set `import_job_id`.

### PDF bill import flow

1. **Submit PDF Bill Import** — Uploads a base64 PDF fixture and stores `submitPdfBillImport.id` as `pdf_import_job_id`.
2. **Get PDF Import Job** — Uses `pdf_import_job_id` to poll status and inspect `reviewPayload` once status becomes `AWAITING_REVIEW`.
3. **List PDF Import Jobs** — Optional history query, can be filtered by `financial_account_id`.
4. **Confirm PDF Bill Import** — Commits reviewed transactions for the selected card/account associations.

## Environment variables

- `jwt_token` — Bearer token (e.g. from Security > Sign In or Login as Admin).
- `router` — GraphQL endpoint (e.g. `http://localhost:4000` in localhost environment).
- `financial_account_id` — UUID of the financial account to import into (set after listing accounts).
- `import_job_id` — Set automatically by **Import OFX File** post-response script; used by **Get Import Job** and **Resolve Import Duplicate**.
- `pdf_import_job_id` — Set automatically by **Submit PDF Bill Import** post-response script; used by **Get PDF Import Job** and **Confirm PDF Bill Import**.
