# CVM NAV Bulk Sync — Implementation Spec

## Context & Motivation

The banking service syncs CVM fund NAVs from `financial_data.asset_daily_price` into
`banking.investment_asset_daily_price` to power portfolio recalculation (TWR).

The current implementation uses paginated GraphQL (`forEachFundDailyNav`, 250 rows/page) and
single-row upserts. This is slow and fragile at scale: the CVM catalog has ~65k funds, and a
5-year backfill inserts hundreds of thousands of rows.

B3 listed-asset prices already use a superior approach: a single streaming NDJSON HTTP response
(gzip-compressed) from `financialdata`, with batched upserts in `banking`. This spec describes
applying the same pattern to CVM NAVs.

---

## Reference Implementation (B3 / Listed Asset Prices)

Study these files before writing any code. The CVM implementation is a direct parallel.

### financialdata service (producer side)

| File | Role |
|---|---|
| `financialdata/.../rest/internal/ListedAssetDailyPriceExportLine.kt` | NDJSON line DTO |
| `financialdata/.../rest/internal/ListedAssetDailyPriceExportRepository.kt` | JDBC cursor-based streaming query |
| `financialdata/.../rest/internal/ListedAssetDailyPriceExportService.kt` | Writes NDJSON / gzip to output stream |
| `financialdata/.../rest/internal/ListedAssetDailyPriceExportController.kt` | `GET /internal/market-data/listed-prices/export` |

### banking service (consumer side)

| File | Role |
|---|---|
| `banking/.../client/investment/financialdata/ListedAssetPriceBulkSyncClient.kt` | Interface |
| `banking/.../client/investment/financialdata/HttpListedAssetPriceBulkSyncClient.kt` | HTTP streaming + NDJSON parsing |
| `banking/.../service/investment/MarketDataSyncService.kt` → `syncListedAssetPrices()` | Orchestration, batch flush, cursor persistence |

---

## What to Build

### 1. `financialdata` — Export endpoint for fund NAVs

#### 1a. `FundNavExportLine.kt`

New file at `financialdata/.../rest/internal/FundNavExportLine.kt`.

Mirror of `ListedAssetDailyPriceExportLine` with fund-specific fields:

```kotlin
@Serdeable
data class FundNavExportLine(
    val cursor: String,
    val cnpj: String,
    val date: LocalDate,
    val closePrice: BigDecimal,
    val updatedAt: Instant,
)
```

#### 1b. `FundNavExportRepository.kt`

New file at `financialdata/.../rest/internal/FundNavExportRepository.kt`.

JDBC cursor-based streaming query. Mirror `ListedAssetDailyPriceExportRepository` exactly,
replacing ticker logic with CNPJ logic.

Key points:
- `fetchSize` injected from `financialdata.market-data-export.fund-navs.fetch-size` (default `1000`)
- Filters: `source = 'CVM'`, `fund_cnpj IS NOT NULL`, `price_date BETWEEN from AND to`
- Optional `cnpjs` filter: `AND ia.fund_cnpj = ANY (?)` — uses `connection.createArrayOf("text", ...)`
- Optional `updatedSince`: `AND adp.created_at >= ?`
- Cursor keyset: `(price_date ASC, fund_cnpj ASC, id ASC)` — same structure as listed prices but with `cnpj` instead of `ticker`
- Cursor encoding: `CursorEncoder.encodeCompositeCursor(mapOf("date" to date.toString(), "cnpj" to cnpj), id = assetId)`

SQL structure (adapt from `ListedAssetDailyPriceExportRepository`):

```sql
SELECT
    ia.id::text,
    ia.fund_cnpj,
    adp.price_date,
    adp.close_price,
    adp.created_at
FROM financial_data.asset_daily_price adp
JOIN financial_data.investment_asset ia ON ia.id = adp.asset_id
WHERE ia.source = 'CVM'
  AND ia.fund_cnpj IS NOT NULL
  AND adp.price_date >= ?
  AND adp.price_date <= ?
  -- optional: AND ia.fund_cnpj = ANY (?)
  -- optional: AND adp.created_at >= ?
  -- optional cursor: AND (adp.price_date > ? OR (adp.price_date = ? AND (ia.fund_cnpj > ? OR (ia.fund_cnpj = ? AND ia.id::text > ?))))
ORDER BY adp.price_date ASC, ia.fund_cnpj ASC, ia.id ASC
```

Note: the cursor SQL already exists in `InvestmentFundRepositoryImpl.getDailyNavsForSync()` — copy it.

#### 1c. `FundNavExportService.kt`

New file at `financialdata/.../rest/internal/FundNavExportService.kt`.

Exact mirror of `ListedAssetDailyPriceExportService`. Replace `exportRepository.stream(...)` with
`FundNavExportRepository.stream(...)`.

#### 1d. `FundNavExportController.kt`

New file at `financialdata/.../rest/internal/FundNavExportController.kt`.

Mirror `ListedAssetDailyPriceExportController` exactly:
- Route: `GET /internal/market-data/fund-navs/export`
- Same auth: service token, `PrincipalType.SERVICE`
- Same gzip negotiation via `Accept-Encoding: gzip`
- Same virtual thread + `PipedInputStream`/`PipedOutputStream` pattern
- Query params: `from: LocalDate`, `to: LocalDate`, `updatedSince: Instant?`, `cnpjs: List<String>?`, `after: String?`

---

### 2. `banking` — Bulk sync client

#### 2a. `FundNavBulkSyncClient.kt`

New file at `banking/.../client/investment/financialdata/FundNavBulkSyncClient.kt`.

Mirror `ListedAssetPriceBulkSyncClient`:

```kotlin
data class FundNavBulkSyncNode(
    val cursor: String,
    val cnpj: String,
    val date: LocalDate,
    val closePrice: BigDecimal,
    val updatedAt: Instant,
)

interface FundNavBulkSyncClient {
    fun streamFundDailyNavs(
        from: LocalDate,
        to: LocalDate,
        updatedSince: Instant? = null,
        cnpjs: Collection<String>? = null,
        after: String? = null,
        consumer: (FundNavBulkSyncNode) -> Unit,
    )
}
```

#### 2b. `HttpFundNavBulkSyncClient.kt`

New file at `banking/.../client/investment/financialdata/HttpFundNavBulkSyncClient.kt`.

Mirror `HttpListedAssetPriceBulkSyncClient` exactly:
- Inject `banking.financialdata-service.url` and `banking.financialdata-service.fund-navs-export-path`
- Same JWT auth via `ServiceTokenClient`
- Same `Accept: application/x-ndjson` + `Accept-Encoding: gzip` headers
- Same `HttpClient` with configurable connect/read timeouts:
  - `banking.market-data-sync.fund-navs.connect-timeout-seconds` (default `10`)
  - `banking.market-data-sync.fund-navs.read-timeout-seconds` (default `600`)
- Same `unwrapBody()` gzip handling
- Same NDJSON line parsing with `objectMapper.readValue(line, FundNavBulkSyncNode::class.java)`
- Build URI with `cnpjs` as repeated query params (same as `tickers` in the listed prices client)

#### 2c. `AssetRepository` — add `findAllWithFundCnpj()`

Add to `AssetRepository` interface:

```kotlin
fun findAllWithFundCnpj(): List<Asset>
```

Add to `AssetEntityRepository`:

```kotlin
fun findByFundCnpjIsNotNull(): List<AssetEntity>
```

Add to `DefaultAssetRepository`:

```kotlin
override fun findAllWithFundCnpj(): List<Asset> =
    entityRepository.findByFundCnpjIsNotNull().map(AssetMapper::toDomain)
```

---

### 3. `banking` — Wire into `MarketDataSyncService`

#### 3a. Config flag

In `application.yml`, add alongside the listed prices bulk flag:

```yaml
banking:
  market-data-sync:
    fund-navs:
      bulk-enabled: ${BANKING_MARKET_DATA_SYNC_FUND_NAVS_BULK_ENABLED:true}
      batch-size: ${BANKING_MARKET_DATA_SYNC_FUND_NAVS_BATCH_SIZE:1000}
      connect-timeout-seconds: 10
      read-timeout-seconds: 600
```

In `.env.local` for banking:

```
BANKING_MARKET_DATA_SYNC_FUND_NAVS_BULK_ENABLED=true
BANKING_MARKET_DATA_SYNC_FUND_NAVS_BATCH_SIZE=1000
```

#### 3b. `MarketDataSyncService` — inject new dependencies

Add to constructor:

```kotlin
private val fundNavBulkSyncClient: FundNavBulkSyncClient,
@param:Property(name = "banking.market-data-sync.fund-navs.bulk-enabled", defaultValue = "true")
private val fundNavBulkEnabled: Boolean,
@param:Property(name = "banking.market-data-sync.fund-navs.batch-size", defaultValue = "1000")
private val fundNavBatchSize: Int,
```

#### 3c. Replace `syncFundDailyNavs()`

Current implementation (GraphQL, single upsert per row):

```kotlin
// BEFORE — remove this
private fun syncFundDailyNavs(event: MarketDataJobSucceededEvent): LocalDate? {
    var maxSyncedDate: LocalDate? = null
    financialDataSyncClient.forEachFundDailyNav(
        from = event.fromDate,
        to = event.lastDataDate,
    ) { items ->
        items.forEach { item ->
            val asset = assetRepository.findByFundCnpj(item.cnpj) ?: return@forEach
            val assetId = asset.id ?: return@forEach
            assetDailyPriceRepository.upsert(
                InvestmentAssetDailyPrice(
                    assetId = assetId,
                    priceDate = item.date,
                    closePrice = item.closePrice,
                    source = MarketDataSyncJobName.CVM_NAV.jobName,
                ),
            )
            maxSyncedDate = maxDate(maxSyncedDate, item.date)
        }
    }
    return maxSyncedDate
}
```

New implementation (NDJSON bulk, batched upserts — mirror `syncListedAssetPrices`):

```kotlin
// AFTER
private fun syncFundDailyNavs(
    event: MarketDataJobSucceededEvent,
    existing: MarketDataSyncControl,
    syncStartedAt: Instant,
): SyncExecutionResult {
    if (!fundNavBulkEnabled) {
        return SyncExecutionResult(syncFundDailyNavsViaGraphQl(event))
    }

    val assetsByCnpj =
        assetRepository.findAllWithFundCnpj()
            .mapNotNull { asset ->
                val assetId = asset.id ?: return@mapNotNull null
                val cnpj = asset.fundCnpj?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                cnpj to assetId
            }.toMap()

    if (assetsByCnpj.isEmpty()) {
        logger.info { "Skipping fund NAV bulk sync because banking has no assets with fund_cnpj" }
        return SyncExecutionResult(lastSyncedDate = null, processedRows = 0)
    }

    val resumeCursor = existing.lastCursor.takeIf { existing.lastEventId == event.eventId }
    var maxSyncedDate: LocalDate? = null
    var processedRows = 0
    var lastCursor = resumeCursor
    val pendingUpserts = ArrayList<InvestmentAssetDailyPrice>(fundNavBatchSize.coerceAtLeast(1))

    fundNavBulkSyncClient.streamFundDailyNavs(
        from = event.fromDate,
        to = event.lastDataDate,
        updatedSince = null,
        cnpjs = assetsByCnpj.keys,
        after = resumeCursor,
    ) { item ->
        val assetId = assetsByCnpj[item.cnpj] ?: return@streamFundDailyNavs
        pendingUpserts +=
            InvestmentAssetDailyPrice(
                assetId = assetId,
                priceDate = item.date,
                closePrice = item.closePrice,
                source = MarketDataSyncJobName.CVM_NAV.jobName,
            )
        maxSyncedDate = maxDate(maxSyncedDate, item.date)
        processedRows += 1
        lastCursor = item.cursor

        if (pendingUpserts.size >= fundNavBatchSize.coerceAtLeast(1)) {
            flushFundNavBatch(
                batch = pendingUpserts,
                control = existing,
                event = event,
                syncStartedAt = syncStartedAt,
                lastCursor = lastCursor,
            )
        }
    }

    flushFundNavBatch(
        batch = pendingUpserts,
        control = existing,
        event = event,
        syncStartedAt = syncStartedAt,
        lastCursor = lastCursor,
    )

    logger.info {
        "Fund NAV bulk sync completed: eventId=${event.eventId} from=${event.fromDate} to=${event.lastDataDate} " +
            "cnpjs=${assetsByCnpj.size} rows=$processedRows resumed=${resumeCursor != null}"
    }
    return SyncExecutionResult(
        lastSyncedDate = maxSyncedDate,
        lastCursor = lastCursor,
        processedRows = processedRows,
    )
}

// Keep as fallback, mirror of syncListedAssetPricesViaGraphQl
private fun syncFundDailyNavsViaGraphQl(event: MarketDataJobSucceededEvent): LocalDate? {
    var maxSyncedDate: LocalDate? = null
    financialDataSyncClient.forEachFundDailyNav(
        from = event.fromDate,
        to = event.lastDataDate,
    ) { items ->
        items.forEach { item ->
            val asset = assetRepository.findByFundCnpj(item.cnpj) ?: return@forEach
            val assetId = asset.id ?: return@forEach
            assetDailyPriceRepository.upsert(
                InvestmentAssetDailyPrice(
                    assetId = assetId,
                    priceDate = item.date,
                    closePrice = item.closePrice,
                    source = MarketDataSyncJobName.CVM_NAV.jobName,
                ),
            )
            maxSyncedDate = maxDate(maxSyncedDate, item.date)
        }
    }
    return maxSyncedDate
}
```

Add `flushFundNavBatch()` — exact mirror of `flushListedAssetPriceBatch()`:

```kotlin
private fun flushFundNavBatch(
    batch: MutableList<InvestmentAssetDailyPrice>,
    control: MarketDataSyncControl,
    event: MarketDataJobSucceededEvent,
    syncStartedAt: Instant,
    lastCursor: String?,
) {
    if (batch.isEmpty()) return
    assetDailyPriceRepository.upsertAll(batch)
    batch.clear()
    syncControlRepository.upsert(
        control.copy(
            sourceLastDataDate = maxDate(control.sourceLastDataDate, event.lastDataDate),
            sourceLastRunAt = maxInstant(control.sourceLastRunAt, event.lastRunAt),
            lastEventId = event.eventId,
            lastCursor = lastCursor,
            status = MarketDataSyncStatus.SYNCING,
            lastSyncStartedAt = syncStartedAt,
            lastError = null,
            updatedAt = Instant.now(),
        ),
    )
}
```

Update the `sync()` dispatch to pass the new parameters:

```kotlin
// In sync(), change:
MarketDataSyncJobName.CVM_NAV -> SyncExecutionResult(syncFundDailyNavs(event))
// To:
MarketDataSyncJobName.CVM_NAV -> syncFundDailyNavs(event, existing, syncStartedAt)
```

---

### 4. `financialdata` — `application.yml` config

Add the new export config property (same section as existing export config):

```yaml
financialdata:
  market-data-export:
    fund-navs:
      fetch-size: ${FINANCIALDATA_MARKET_DATA_EXPORT_FUND_NAVS_FETCH_SIZE:1000}
```

Add the banking-side path property in `banking/application.yml`:

```yaml
banking:
  financialdata-service:
    fund-navs-export-path: ${FINANCIALDATA_FUND_NAVS_EXPORT_PATH:/internal/market-data/fund-navs/export}
```

Add to `banking/.env.local`:

```
FINANCIALDATA_FUND_NAVS_EXPORT_PATH=/internal/market-data/fund-navs/export
```

---

## File Checklist

### New files

| Service | File |
|---|---|
| financialdata | `rest/internal/FundNavExportLine.kt` |
| financialdata | `rest/internal/FundNavExportRepository.kt` |
| financialdata | `rest/internal/FundNavExportService.kt` |
| financialdata | `rest/internal/FundNavExportController.kt` |
| banking | `client/investment/financialdata/FundNavBulkSyncClient.kt` |
| banking | `client/investment/financialdata/HttpFundNavBulkSyncClient.kt` |

### Modified files

| Service | File | Change |
|---|---|---|
| banking | `repo/investment/AssetRepository.kt` | add `findAllWithFundCnpj()` |
| banking | `repo/investment/AssetEntityRepository.kt` | add `findByFundCnpjIsNotNull()` |
| banking | `repo/investment/DefaultAssetRepository.kt` | implement `findAllWithFundCnpj()` |
| banking | `service/investment/MarketDataSyncService.kt` | inject client + flag, replace `syncFundDailyNavs`, add `flushFundNavBatch` |
| banking | `src/main/resources/application.yml` | add fund-navs config block |
| banking | `.env.local` | add bulk enabled/batch-size env vars |
| financialdata | `src/main/resources/application.yml` | add fund-navs fetch-size config |

---

## Key Design Decisions

**Why filter by CNPJs upfront?**
Banking only holds a small subset of the ~65k CVM funds. Sending known CNPJs as a query param
to the export endpoint means the SQL `ANY (?)` filter runs on the DB side, and only the
relevant rows travel over the wire. Same rationale as sending tickers for B3.

**Why keep the GraphQL fallback?**
`fundNavBulkEnabled=false` lets you disable the bulk path without redeploying, identical to
`listedPriceBulkEnabled` for B3. The fallback (`syncFundDailyNavsViaGraphQl`) is the current
code, unchanged.

**Why batched upserts instead of single-row upserts?**
`assetDailyPriceRepository.upsertAll()` already exists and is used by the B3 path. Flushing
every 1000 rows reduces round trips by ~1000x compared to the current per-row upsert.

**Cursor / resume on failure**
`lastCursor` is persisted to `banking.market_data_sync_control` on every batch flush (same as
B3). If the sync fails mid-stream, the next run with the same `eventId` resumes from
`lastCursor` instead of restarting from the beginning.

**No pagination**
The export endpoint is a single streaming response (server-cursor via JDBC `fetchSize`), not
paginated. The banking client reads line-by-line without buffering the entire response. This
avoids the N+1 HTTP round-trips of the GraphQL approach.
