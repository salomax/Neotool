# Job Spec: B3 COTAHIST — Equities, FIIs, ETFs, BDRs

> **Service**: `financialdata`
> **Goal**: Build the investment asset catalog and daily price history for all B3-traded instruments (stocks, FIIs, ETFs, BDRs, options) by parsing the B3 COTAHIST historical series files.
> **Effort**: Large
> **Run frequency**: Daily at 21:30 BRT (after B3 closes at 18:00 and publishes the daily file ~21:00)

---

## Context

B3 (Brasil, Bolsa, Balcão) publishes a free daily file called **COTAHIST** that contains end-of-day quote data for every instrument traded on the exchange. This is the canonical source for:
- Asset metadata: ticker, ISIN code, company name, asset type, lot size
- Daily OHLCV: open, high, low, close, volume, trades

The same file format covers all B3 asset classes: `ON`/`PN` shares, FIIs, ETFs, BDRs, options, futures, and debentures that trade on exchange.

**New tables needed** in `financial_data` schema:
- `investment_asset` — asset catalog (shared by all import jobs)
- `asset_daily_price` — daily OHLCV
- `import_job_control` — per-job last-run tracking (replaces per-indicator `load_control`)

---

## Data source

**Publisher**: B3 — `b3.com.br`
**Files**:
- **Daily** (D-1 available next day ~21:00): `COTAHIST_D{DDMMYYYY}.ZIP`
- **Annual** (previous year, for backfill): `COTAHIST_A{YYYY}.ZIP`
- **Download URL pattern**:
  ```
  https://bvmf.bmfbovespa.com.br/InstDados/SerHist/COTAHIST_D{DDMMYYYY}.ZIP
  https://bvmf.bmfbovespa.com.br/InstDados/SerHist/COTAHIST_A{YYYY}.ZIP
  ```
- **Auth**: None — fully public
- **Format**: Fixed-width text (ASCII, ISO-8859-1 encoding), one record per line

### COTAHIST record layout (key fields only)

The full specification is available at B3's website. Key fields used:

| Position | Length | Field | Notes |
|----------|--------|-------|-------|
| 1 | 2 | `TIPREG` | `01` = daily quote record (skip header/trailer) |
| 3 | 8 | `DATPRE` | Trade date `YYYYMMDD` |
| 11 | 12 | `CODNEG` | Ticker (right-padded with spaces) |
| 25 | 3 | `TPMERC` | Market type: `010`=spot, `020`=fractional, `070`=options call, `080`=options put |
| 28 | 12 | `NOMRES` | Abbreviated company/fund name |
| 40 | 10 | `ESPECI` | Asset specification (ON, PN, CI, ETF, DR1, etc.) |
| 50 | 3 | `PRAZOT` | Days to maturity (options) |
| 53 | 4 | `MODREF` | Currency code (`R$` for BRL) |
| 57 | 13 | `PREABE` | Open price × 100 (integer, no decimal separator) |
| 70 | 13 | `PREMAX` | High price × 100 |
| 83 | 13 | `PREMIN` | Low price × 100 |
| 109| 13 | `PREMED` | Average price × 100 |
| 122| 13 | `PREULT` | Close price × 100 |
| 170| 5  | `TOTNEG` | Number of trades |
| 175| 18 | `QUATOT` | Total shares traded |
| 193| 18 | `VOLTOT` | Total volume × 100 |
| 211| 12 | `CODISI` | ISIN code |
| 223| 3  | `DISMES` | Price factor (1, 10, 100, 1000) — B3 special quote formats |

### Asset type mapping

| `ESPECI` value | `TPMERC` | Our `AssetType` |
|----------------|----------|----------------|
| `ON`, `PN`, `PNA`, `PNB`, `UNT` | `010` | `STOCK` |
| `CI` (Certificado de Investimento) | `010` | `REAL_ESTATE_FUND` (FII) |
| `ETF` | `010` | `ETF` |
| `ETF-FI` | `010` | `ETF_FIXED_INCOME` |
| `DR1`, `DR2`, `DR3`, `BDR` | `010` | `BDR` |
| `*` | `070` | `OPTION_CALL` |
| `*` | `080` | `OPTION_PUT` |

---

## Database schema

### Migration: `V1_15__create_investment_asset_tables.sql`

```sql
-- Shared asset catalog — populated by all import jobs
CREATE TABLE financial_data.investment_asset (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    ticker       TEXT        UNIQUE,           -- B3 ticker or CoinGecko id
    isin_code    TEXT        UNIQUE,
    name         TEXT        NOT NULL,
    asset_type   TEXT        NOT NULL,          -- STOCK, REAL_ESTATE_FUND, ETF, BDR, etc.
    exchange     TEXT,                          -- B3, NASDAQ, CRYPTO
    currency_code TEXT       NOT NULL DEFAULT 'BRL',
    lot_size     INT         NOT NULL DEFAULT 100, -- B3 standard lot
    price_factor INT         NOT NULL DEFAULT 1,   -- B3 special quote divisor
    issuer_name  TEXT,
    fund_cnpj    TEXT        UNIQUE,           -- for CVM funds
    is_active    BOOLEAN     NOT NULL DEFAULT TRUE,
    source       TEXT        NOT NULL,         -- B3_COTAHIST, CVM, TESOURO_NACIONAL, COINGECKO, MANUAL
    external_id  TEXT,                         -- provider-specific ID
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_investment_asset_type   ON financial_data.investment_asset(asset_type);
CREATE INDEX idx_investment_asset_active ON financial_data.investment_asset(is_active);
CREATE INDEX idx_investment_asset_name   ON financial_data.investment_asset USING gin(to_tsvector('portuguese', name));

-- Daily OHLCV prices — one row per (asset, date)
CREATE TABLE financial_data.asset_daily_price (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    asset_id    UUID        NOT NULL REFERENCES financial_data.investment_asset(id),
    price_date  DATE        NOT NULL,
    open_price  NUMERIC(18,8),
    high_price  NUMERIC(18,8),
    low_price   NUMERIC(18,8),
    close_price NUMERIC(18,8) NOT NULL,
    avg_price   NUMERIC(18,8),
    volume      NUMERIC(22,2),
    trades      INT,
    source      TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_asset_price UNIQUE (asset_id, price_date)
);

CREATE INDEX idx_asset_price_asset ON financial_data.asset_daily_price(asset_id);
CREATE INDEX idx_asset_price_date  ON financial_data.asset_daily_price(price_date DESC);

-- Per-job run control (replaces per-indicator load_control for new jobs)
CREATE TABLE financial_data.import_job_control (
    job_name        TEXT        PRIMARY KEY,
    last_data_date  DATE,                      -- last date of data successfully imported
    last_run_at     TIMESTAMPTZ,
    last_success_at TIMESTAMPTZ,
    status          TEXT        NOT NULL DEFAULT 'IDLE',   -- IDLE, RUNNING, FAILED
    error_message   TEXT,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

---

## Architecture

```
CotahistScheduledJob          (@Scheduled — triggers daily)
    └── CotahistImportService (orchestrates download → parse → upsert)
            ├── CotahistDownloader  (downloads ZIP, extracts TXT)
            ├── CotahistParser     (reads fixed-width lines → CotahistRecord)
            ├── AssetRepository    (upsert investment_asset)
            ├── AssetPriceRepository (upsert asset_daily_price)
            └── ImportJobControlRepository (tracks last_data_date)
```

---

## Activities

### Activity 1 — Database migration

Create `V1_15__create_investment_asset_tables.sql` as shown above.

---

### Activity 2 — Domain model

**File to create**: `domain/asset/InvestmentAsset.kt`

```kotlin
data class InvestmentAsset(
    val id: UUID?,
    val ticker: String?,
    val isinCode: String?,
    val name: String,
    val assetType: String,
    val exchange: String?,
    val currencyCode: String,
    val lotSize: Int,
    val priceFactor: Int,
    val issuerName: String?,
    val fundCnpj: String?,
    val isActive: Boolean,
    val source: String,
    val externalId: String?,
)

data class AssetDailyPrice(
    val id: UUID?,
    val assetId: UUID,
    val priceDate: LocalDate,
    val openPrice: BigDecimal?,
    val highPrice: BigDecimal?,
    val lowPrice: BigDecimal?,
    val closePrice: BigDecimal,
    val avgPrice: BigDecimal?,
    val volume: BigDecimal?,
    val trades: Int?,
    val source: String,
)
```

---

### Activity 3 — COTAHIST parser

**File to create**: `domain/asset/cotahist/CotahistParser.kt`

```kotlin
@Singleton
class CotahistParser {

    fun parse(lines: Sequence<String>): Sequence<CotahistRecord> = lines
        .filter { it.length >= 245 && it.substring(0, 2) == "01" }   // daily records only
        .map { parseLine(it) }

    private fun parseLine(line: String): CotahistRecord {
        val ticker      = line.substring(12, 24).trim()
        val marketType  = line.substring(24, 27).trim()
        val name        = line.substring(27, 39).trim()
        val spec        = line.substring(39, 49).trim()
        val dateStr     = line.substring(2, 10)              // YYYYMMDD
        val isinCode    = line.substring(230, 242).trim()
        val priceFactor = line.substring(242, 245).trim().toIntOrNull() ?: 1

        // Prices are integers × 100; apply priceFactor
        val openPrice  = parsePriceCents(line.substring(56, 69))  / priceFactor
        val highPrice  = parsePriceCents(line.substring(69, 82))  / priceFactor
        val lowPrice   = parsePriceCents(line.substring(82, 95))  / priceFactor
        val avgPrice   = parsePriceCents(line.substring(108, 121)) / priceFactor
        val closePrice = parsePriceCents(line.substring(121, 134)) / priceFactor
        val trades     = line.substring(169, 174).trim().toIntOrNull()
        val volume     = line.substring(192, 210).trim().toBigDecimalOrNull()
            ?.divide(BigDecimal(100))

        return CotahistRecord(
            tradeDate   = LocalDate.parse(dateStr, DateTimeFormatter.BASIC_ISO_DATE),
            ticker      = ticker,
            marketType  = marketType,
            name        = name,
            spec        = spec,
            isinCode    = isinCode.ifBlank { null },
            priceFactor = priceFactor,
            openPrice   = openPrice,
            highPrice   = highPrice,
            lowPrice    = lowPrice,
            avgPrice    = avgPrice,
            closePrice  = closePrice,
            trades      = trades,
            volume      = volume,
        )
    }

    private fun parsePriceCents(raw: String): BigDecimal =
        raw.trim().toBigDecimalOrNull()?.divide(BigDecimal(100)) ?: BigDecimal.ZERO
}

data class CotahistRecord(
    val tradeDate: LocalDate,
    val ticker: String,
    val marketType: String,   // "010" spot, "020" fractional, "070" call, "080" put
    val name: String,
    val spec: String,         // "ON", "PN", "CI", "ETF", "DR1", etc.
    val isinCode: String?,
    val priceFactor: Int,
    val openPrice: BigDecimal,
    val highPrice: BigDecimal,
    val lowPrice: BigDecimal,
    val avgPrice: BigDecimal,
    val closePrice: BigDecimal,
    val trades: Int?,
    val volume: BigDecimal?,
)
```

Asset type resolution:
```kotlin
fun CotahistRecord.resolveAssetType(): String? = when {
    marketType == "070" -> "OPTION_CALL"
    marketType == "080" -> "OPTION_PUT"
    spec == "CI"        -> "REAL_ESTATE_FUND"
    spec == "ETF"       -> "ETF"
    spec == "ETF-FI"    -> "ETF_FIXED_INCOME"
    spec in setOf("DR1", "DR2", "DR3", "BDR") -> "BDR"
    spec in setOf("ON", "PN", "PNA", "PNB", "UNT", "ON NM", "PN NM") -> "STOCK"
    else -> null  // skip unknown types
}
```

---

### Activity 4 — Downloader

**File to create**: `domain/asset/cotahist/CotahistDownloader.kt`

```kotlin
@Singleton
class CotahistDownloader(
    private val httpClient: HttpClient,  // Micronaut low-level client for binary downloads
) {
    private val BASE = "https://bvmf.bmfbovespa.com.br/InstDados/SerHist"

    fun downloadDailyFile(date: LocalDate): File {
        val filename = "COTAHIST_D${date.format(DateTimeFormatter.ofPattern("ddMMyyyy"))}.ZIP"
        return downloadAndExtract("$BASE/$filename", filename)
    }

    fun downloadAnnualFile(year: Int): File {
        val filename = "COTAHIST_A$year.ZIP"
        return downloadAndExtract("$BASE/$filename", filename)
    }

    private fun downloadAndExtract(url: String, filename: String): File {
        val tmpZip = Files.createTempFile("cotahist", ".zip").toFile()
        // Download ZIP → extract TXT → return TXT file
        // File is ISO-8859-1 encoded
        ...
        return txtFile
    }
}
```

---

### Activity 5 — Import service

**File to create**: `domain/asset/cotahist/CotahistImportService.kt`

```kotlin
@Singleton
class CotahistImportService(
    private val downloader: CotahistDownloader,
    private val parser: CotahistParser,
    private val assetRepository: InvestmentAssetRepository,
    private val priceRepository: AssetDailyPriceRepository,
    private val jobControl: ImportJobControlRepository,
    private val log: Logger = LoggerFactory.getLogger(CotahistImportService::class.java),
) {
    companion object {
        const val JOB_NAME = "b3-cotahist"
        const val SOURCE   = "B3_COTAHIST"
    }

    fun importLatestDay() {
        val targetDate = resolveTargetDate()
        if (targetDate == null) {
            log.info("COTAHIST: already up to date")
            return
        }
        jobControl.setStatus(JOB_NAME, "RUNNING")
        runCatching {
            val file = downloader.downloadDailyFile(targetDate)
            importFile(file, targetDate)
            jobControl.markSuccess(JOB_NAME, targetDate)
        }.onFailure {
            jobControl.markFailed(JOB_NAME, it.message)
            log.error("COTAHIST import failed for $targetDate", it)
        }
    }

    fun backfillYear(year: Int) {
        val file = downloader.downloadAnnualFile(year)
        // Parse and import — uses upsert so safe to re-run
        importFile(file, referenceYear = year)
        log.info("COTAHIST backfill $year complete")
    }

    private fun importFile(file: File, targetDate: LocalDate? = null, referenceYear: Int? = null) {
        var assetCount = 0
        var priceCount = 0

        file.bufferedReader(Charsets.ISO_8859_1).useLines { lines ->
            parser.parse(lines)
                .filter { it.closePrice > BigDecimal.ZERO }    // skip zero-price records
                .forEach { record ->
                    val assetType = record.resolveAssetType() ?: return@forEach

                    // Upsert asset catalog entry
                    val asset = assetRepository.upsertByTicker(
                        InvestmentAsset(
                            id          = null,
                            ticker      = record.ticker,
                            isinCode    = record.isinCode,
                            name        = record.name,
                            assetType   = assetType,
                            exchange    = "B3",
                            currencyCode = "BRL",
                            lotSize     = 100,
                            priceFactor = record.priceFactor,
                            issuerName  = null,
                            fundCnpj    = null,
                            isActive    = true,
                            source      = SOURCE,
                            externalId  = record.isinCode,
                        )
                    )
                    assetCount++

                    // Upsert price
                    priceRepository.upsert(
                        AssetDailyPrice(
                            id          = null,
                            assetId     = asset.id!!,
                            priceDate   = record.tradeDate,
                            openPrice   = record.openPrice,
                            highPrice   = record.highPrice,
                            lowPrice    = record.lowPrice,
                            closePrice  = record.closePrice,
                            avgPrice    = record.avgPrice,
                            volume      = record.volume,
                            trades      = record.trades,
                            source      = SOURCE,
                        )
                    )
                    priceCount++
                }
        }

        log.info("COTAHIST import done: $assetCount assets, $priceCount prices")
    }

    private fun resolveTargetDate(): LocalDate? {
        val lastDate = jobControl.findLastDataDate(JOB_NAME)
        // B3 publishes D-1 data; target = last business day before today
        val target = previousBusinessDay(LocalDate.now())
        return if (lastDate == null || lastDate < target) target else null
    }
}
```

---

### Activity 6 — Scheduled job

**File to create**: `domain/asset/cotahist/CotahistScheduledJob.kt`

```kotlin
@Singleton
class CotahistScheduledJob(private val service: CotahistImportService) {

    private val log = LoggerFactory.getLogger(CotahistScheduledJob::class.java)

    // 21:30 BRT = 00:30 UTC next day
    @Scheduled(cron = "0 30 0 * * *")
    fun run() {
        log.info("CotahistScheduledJob started")
        service.importLatestDay()
        log.info("CotahistScheduledJob finished")
    }
}
```

---

### Activity 7 — Management endpoints

```kotlin
@Controller("/management/cotahist")
class CotahistManagementController(private val service: CotahistImportService) {

    @Post("/import/latest")
    fun importLatest() = service.importLatestDay()

    @Post("/backfill/{year}")
    fun backfill(@PathVariable year: Int) = service.backfillYear(year)
}
```

---

### Activity 8 — GraphQL exposure (new queries)

Add to `schema.graphqls`:

```graphql
type InvestmentAsset {
    id: ID!
    ticker: String
    isinCode: String
    name: String!
    assetType: String!
    exchange: String
    currencyCode: String!
    isActive: Boolean!
    source: String!
}

type AssetDailyPrice {
    id: ID!
    assetId: ID!
    priceDate: String!
    closePrice: Float!
    openPrice: Float
    highPrice: Float
    lowPrice: Float
    volume: Float
    source: String!
}

extend type Query {
    # Asset catalog search — used by banking service to resolve asset names
    searchInvestmentAssets(query: String!, assetTypes: [String!]): [InvestmentAsset!]!
    investmentAsset(id: ID!): InvestmentAsset
    investmentAssetByTicker(ticker: String!): InvestmentAsset

    # Latest price for a given asset
    latestAssetPrice(assetId: ID!): AssetDailyPrice
    # Price history for a range
    assetPriceHistory(assetId: ID!, from: String!, to: String!): [AssetDailyPrice!]!
}
```

---

## Idempotency

- `assetRepository.upsertByTicker` — `ON CONFLICT (ticker) DO UPDATE SET name, isin_code, price_factor, updated_at`
- `priceRepository.upsert` — `ON CONFLICT (asset_id, price_date) DO UPDATE SET close_price, ...`
- Re-running the job for the same day is fully safe

## File sizes

| File | Approx size |
|------|------------|
| Daily COTAHIST ZIP | ~15–20 MB compressed (~60 MB extracted) |
| Annual COTAHIST ZIP | ~150 MB compressed (~700 MB extracted) |

Process the file as a streaming sequence (never load into memory in full). The `parser.parse(lines: Sequence<String>)` design handles this.

## Backfill strategy

1. First deploy: call `POST /management/cotahist/backfill/{year}` for 2022, 2023, 2024, 2025
2. Then `POST /management/cotahist/import/latest` to catch up current year
3. Subsequent runs: scheduled job handles incrementally

---

## File change summary

| File | Change |
|------|--------|
| `db/migration/V1_15__create_investment_asset_tables.sql` | **Create** |
| `domain/asset/InvestmentAsset.kt` | **Create** |
| `domain/asset/cotahist/CotahistParser.kt` | **Create** |
| `domain/asset/cotahist/CotahistDownloader.kt` | **Create** |
| `domain/asset/cotahist/CotahistImportService.kt` | **Create** |
| `domain/asset/cotahist/CotahistScheduledJob.kt` | **Create** |
| `repo/InvestmentAssetRepository.kt` | **Create** |
| `repo/AssetDailyPriceRepository.kt` | **Create** |
| `repo/ImportJobControlRepository.kt` | **Create** |
| `graphql/schema.graphqls` | **Modify** — add asset + price queries |
| `graphql/resolver/InvestmentAssetResolver.kt` | **Create** |
| `domain/asset/cotahist/CotahistManagementController.kt` | **Create** |

---

## Acceptance criteria

- [ ] `investment_asset` table populated with all B3-traded tickers after backfill
- [ ] `asset_daily_price` table populated with OHLCV for all imported assets
- [ ] `searchInvestmentAssets` query returns results matching ticker or name
- [ ] `latestAssetPrice(assetId)` returns the most recent close price
- [ ] Re-running the job does not duplicate rows
- [ ] Parser correctly maps `ESPECI` → `assetType` for STOCK, REAL_ESTATE_FUND, ETF, BDR
- [ ] Options (market type 070/080) are imported as OPTION_CALL / OPTION_PUT
- [ ] File is streamed (not fully loaded into memory)
- [ ] Backfill endpoint works for a full year file
- [ ] `import_job_control` updated with `last_data_date` and status after each run

## Out of scope

- Real-time intraday prices (end-of-day is sufficient for portfolio tracking)
- Corporate events (dividends, splits) — separate job
- Fractional market (`TPMERC=020`) prices — can be imported but fractional has different lot rules
- Options Greeks or Black-Scholes pricing
