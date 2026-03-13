# Job Spec: Tesouro Nacional — Tesouro Direto Prices

> **Service**: `financialdata`
> **Goal**: Import the Tesouro Direto bond catalog and daily prices/rates from the Tesouro Nacional public API so users can track their Tesouro Direto positions.
> **Effort**: Small
> **Run frequency**: Twice daily — 10:00 and 18:00 BRT (Tesouro updates intraday)
> **Depends on**: Migration `V1_15` (shared `investment_asset` and `asset_daily_price` tables from spec 02)

---

## Context

Tesouro Direto is the Brazilian government bond program, the most popular fixed income product among retail investors. Prices are published by the Tesouro Nacional (Treasury) directly on their website as a public JSON API — no authentication required.

Unlike B3 equities, Tesouro Direto bonds have a **maturity date in the name** (e.g. "Tesouro IPCA+ 2035") and a **unit price** (PU — preço unitário) rather than a per-share price. The PU is what goes into portfolio valuation.

---

## Data source

**Publisher**: Tesouro Nacional
**Auth**: None — fully public

### API 1: Current prices (all active bonds)
```
URL:    https://www.tesourodireto.com.br/json/br/com/b3/tesourodireto/tesouro-direto/prices-and-rates.json
Format: JSON
Update: Every ~30 minutes during market hours (09:30–18:00 BRT)
```

**Response shape** (simplified):
```json
{
  "response": {
    "TrsrBdTradgList": [
      {
        "TrsrBd": {
          "nm": "Tesouro IPCA+ 2035",
          "cd": 70,
          "mtrtyDt": "2035-05-15",
          "isinCd": "BRSTNCLTN3R7",
          "minRedVal": 30.00,
          "untrRedVal": 30.18,
          "FinIndxs": { "nm": "IPCA" },
          "anulInvstmtRate": 6.13,     // annual real rate (for IPCA+)
          "anulRedRate": 6.14,          // annual redemption rate
          "irsDate": "2025-03-11",
          "BuySell": "Sell",
          "TrsrBdPrice": {
            "buyVal": 3418.17,          // buy PU (user buys at this price)
            "sellVal": 3445.80,         // sell PU (user sells at this price)
            "buyAnnualRate": 6.17,
            "sellAnnualRate": 6.13,
            "pricDate": "2025-03-11"
          }
        }
      }
    ]
  }
}
```

### API 2: Historical prices (downloadable Excel/JSON)
```
URL:    https://www.tesourodireto.com.br/json/br/com/b3/tesourodireto/tesouro-direto/historic-prices.json
Format: JSON
Update: Daily
```

Used for backfill only.

---

## Asset naming and type mapping

Tesouro Direto bond names follow a fixed pattern:

| Bond name prefix | `assetType` | `indexer` |
|------------------|-------------|-----------|
| `Tesouro Selic` | `TREASURY` | `SELIC` |
| `Tesouro IPCA+` | `TREASURY` | `IPCA` |
| `Tesouro IPCA+ com Juros Semestrais` | `TREASURY` | `IPCA` |
| `Tesouro Prefixado` | `TREASURY` | `PRE_FIXADO` |
| `Tesouro Prefixado com Juros Semestrais` | `TREASURY` | `PRE_FIXADO` |
| `Tesouro RendA+` | `TREASURY` | `IPCA` |
| `Tesouro Educa+` | `TREASURY` | `IPCA` |

The ticker we generate: `TNFT{cd}` where `{cd}` is the Tesouro bond code (e.g. `TNFT70`).
Or alternatively use the ISIN code directly as the unique key.

---

## Architecture

```
TesouroDiretoScheduledJob
    └── TesouroDiretoImportService
            ├── TesouroDiretoApiClient    (@Client)
            ├── InvestmentAssetRepository (upsert by isin_code)
            └── AssetDailyPriceRepository (upsert PU as close_price)
```

---

## Activities

### Activity 1 — API client

**File to create**: `domain/asset/tesouro/TesouroDiretoApiClient.kt`

```kotlin
@Client("https://www.tesourodireto.com.br")
interface TesouroDiretoApiClient {

    @Get("/json/br/com/b3/tesourodireto/tesouro-direto/prices-and-rates.json")
    fun getCurrentPrices(): TesouroDiretoResponse

    @Get("/json/br/com/b3/tesourodireto/tesouro-direto/historic-prices.json")
    fun getHistoricPrices(): TesouroDiretoHistoricResponse
}
```

---

### Activity 2 — Import service

**File to create**: `domain/asset/tesouro/TesouroDiretoImportService.kt`

```kotlin
@Singleton
class TesouroDiretoImportService(
    private val client: TesouroDiretoApiClient,
    private val assetRepository: InvestmentAssetRepository,
    private val priceRepository: AssetDailyPriceRepository,
    private val jobControl: ImportJobControlRepository,
    private val log: Logger = LoggerFactory.getLogger(TesouroDiretoImportService::class.java),
) {
    companion object {
        const val JOB_NAME = "tesouro-direto"
        const val SOURCE   = "TESOURO_NACIONAL"
        const val ISSUER   = "Tesouro Nacional"
    }

    fun importCurrent() {
        runCatching {
            val response = client.getCurrentPrices()
            val bonds    = response.response.trsrBdTradgList.map { it.trsrBd }
            val priceDate = LocalDate.now()

            bonds.forEach { bond ->
                // Upsert asset catalog entry
                val asset = assetRepository.upsertByIsinCode(
                    InvestmentAsset(
                        id           = null,
                        ticker       = "TNFT${bond.cd}",
                        isinCode     = bond.isinCd,
                        name         = bond.nm,
                        assetType    = "TREASURY",
                        exchange     = null,
                        currencyCode = "BRL",
                        lotSize      = 1,
                        priceFactor  = 1,
                        issuerName   = ISSUER,
                        fundCnpj     = null,
                        isActive     = true,
                        source       = SOURCE,
                        externalId   = bond.cd.toString(),
                    )
                )

                // Upsert today's buy price as close_price
                // Buy price = what the investor paid; use buyVal for portfolio valuation
                priceRepository.upsert(
                    AssetDailyPrice(
                        id          = null,
                        assetId     = asset.id!!,
                        priceDate   = priceDate,
                        openPrice   = null,
                        highPrice   = bond.trsrBdPrice.sellVal?.toBigDecimalOrNull(),
                        lowPrice    = null,
                        closePrice  = bond.trsrBdPrice.buyVal.toBigDecimal(),
                        avgPrice    = null,
                        volume      = null,
                        trades      = null,
                        source      = SOURCE,
                    )
                )
            }

            jobControl.markSuccess(JOB_NAME, priceDate)
            log.info("Tesouro Direto: imported ${bonds.size} bonds")
        }.onFailure {
            jobControl.markFailed(JOB_NAME, it.message)
            log.error("Tesouro Direto import failed", it)
        }
    }
}
```

---

### Activity 3 — Response DTOs

**File to create**: `domain/asset/tesouro/TesouroDiretoResponse.kt`

```kotlin
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy::class)
data class TesouroDiretoResponse(
    val response: TesouroDiretoResponseBody,
)

data class TesouroDiretoResponseBody(
    @JsonProperty("TrsrBdTradgList")
    val trsrBdTradgList: List<TrsrBdTradg>,
)

data class TrsrBdTradg(
    @JsonProperty("TrsrBd")
    val trsrBd: TrsrBd,
)

data class TrsrBd(
    val nm: String,           // "Tesouro IPCA+ 2035"
    val cd: Int,              // 70
    val mtrtyDt: String,      // "2035-05-15"
    val isinCd: String,
    @JsonProperty("TrsrBdPrice")
    val trsrBdPrice: TrsrBdPrice,
)

data class TrsrBdPrice(
    val buyVal: Double,        // PU para compra
    val sellVal: Double?,      // PU para venda (null when market is closed)
    val pricDate: String,      // "2025-03-11"
)
```

---

### Activity 4 — Scheduled job

**File to create**: `domain/asset/tesouro/TesouroDiretoScheduledJob.kt`

```kotlin
@Singleton
class TesouroDiretoScheduledJob(private val service: TesouroDiretoImportService) {

    private val log = LoggerFactory.getLogger(TesouroDiretoScheduledJob::class.java)

    // 10:00 BRT = 13:00 UTC and 18:00 BRT = 21:00 UTC
    @Scheduled(cron = "0 0 13 * * MON-FRI")
    fun runMorning() {
        log.info("TesouroDiretoScheduledJob (morning) started")
        service.importCurrent()
    }

    @Scheduled(cron = "0 0 21 * * MON-FRI")
    fun runEvening() {
        log.info("TesouroDiretoScheduledJob (evening) started")
        service.importCurrent()
    }
}
```

---

### Activity 5 — Management endpoint

```kotlin
@Controller("/management/tesouro-direto")
class TesouroDiretoManagementController(private val service: TesouroDiretoImportService) {

    @Post("/import")
    fun importCurrent() = service.importCurrent()
}
```

---

## Special considerations

### Maturity date in bond name
The bond `nm` field contains the maturity year (e.g. "Tesouro IPCA+ 2035"). This means two bonds with the same type but different maturities are distinct assets — which is correct. The ISIN code is the unique identifier.

### Price when market is closed
The Tesouro website returns `null` or `0` for `sellVal` outside market hours. The import should still upsert the last known `buyVal` as `close_price`. On weekends, the daily job won't have new data but the last weekday's prices are already in the DB.

### PU vs. face value
Tesouro bonds have a **face value of R$1,000** at maturity, but trade at a market PU (e.g. R$3,418 for a long IPCA+ bond). Portfolio valuation should use `close_price` from `asset_daily_price` × holding `quantity`. Since quantity=1 for manually-entered bonds (per `record-first-holding.md`), `close_price` effectively IS the current market value.

### Rates
The `anulInvstmtRate` field (e.g. `6.13`) represents the current annual real yield for IPCA+ bonds, or the nominal annual rate for pre-fixed bonds. This should be stored as an additional column on `asset_daily_price` or in a dedicated `treasury_rate` table for yield display. For MVP, the PU price is sufficient.

---

## Idempotency

- `upsertByIsinCode` — `ON CONFLICT (isin_code) DO UPDATE`
- `priceRepository.upsert` — `ON CONFLICT (asset_id, price_date) DO UPDATE`
- Running twice on the same day just overwrites with the latest PU

---

## File change summary

| File | Change |
|------|--------|
| `domain/asset/tesouro/TesouroDiretoApiClient.kt` | **Create** |
| `domain/asset/tesouro/TesouroDiretoImportService.kt` | **Create** |
| `domain/asset/tesouro/TesouroDiretoScheduledJob.kt` | **Create** |
| `domain/asset/tesouro/TesouroDiretoResponse.kt` | **Create** |
| `domain/asset/tesouro/TesouroDiretoManagementController.kt` | **Create** |
| `repo/InvestmentAssetRepository.kt` | **Modify** — add `upsertByIsinCode` |

---

## Acceptance criteria

- [ ] All currently active Tesouro Direto bonds appear in `investment_asset` with `assetType = TREASURY`
- [ ] Each bond's PU (buy price) stored in `asset_daily_price.close_price` daily
- [ ] `upsertByIsinCode` works — re-running does not create duplicates
- [ ] `sellVal = null` case handled gracefully (skip or use buyVal as highPrice)
- [ ] Bond names correctly parsed to identify issuer = "Tesouro Nacional"
- [ ] Scheduler runs twice on business days; skips weekends (no new data)
- [ ] Management endpoint allows manual trigger

## Out of scope

- Intraday price streaming
- Yield curve analytics
- Rate history (only latest rate stored; full rate series is a future feature)
- Tesouro RendA+ / Educa+ special redemption rules
