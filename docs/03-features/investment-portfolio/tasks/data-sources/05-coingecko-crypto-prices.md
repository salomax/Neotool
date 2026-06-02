# Job Spec: CoinGecko — Crypto Prices

> **Service**: `financialdata`
> **Goal**: Import a curated crypto asset catalog and daily prices (BRL) from CoinGecko's free public API.
> **Effort**: Small
> **Run frequency**: Daily at 08:00 BRT (crypto markets run 24/7; we import the prior day's close)
> **Depends on**: Migration `V1_15` (shared `investment_asset` and `asset_daily_price` tables from spec 02)

---

## Context

Crypto is a non-B3 asset class. CoinGecko provides the most comprehensive free API for crypto market data, covering 10,000+ assets with BRL pricing and no API key required for public endpoints (up to ~50 calls/min on the free tier).

For MVP, we import a **curated list** of the most relevant assets for Brazilian retail investors rather than the full 10,000+ catalog. The list can be expanded via config without code changes.

---

## Data source

**Publisher**: CoinGecko — `api.coingecko.com`
**Auth**: None required for free tier (API key optional for higher rate limits)
**Rate limit**: ~10–30 calls/min (free), 500/min (Pro)

### Endpoint 1: Market data for multiple assets
```
GET https://api.coingecko.com/api/v3/coins/markets
  ?vs_currency=brl
  &ids=bitcoin,ethereum,solana,...
  &order=market_cap_desc
  &per_page=250
  &page=1
  &price_change_percentage=24h
```

**Response** (per asset):
```json
{
  "id": "bitcoin",
  "symbol": "btc",
  "name": "Bitcoin",
  "current_price": 542300.00,
  "market_cap": 10741234567890,
  "total_volume": 87654321098,
  "high_24h": 548000.00,
  "low_24h": 535000.00,
  "price_change_percentage_24h": 1.23,
  "image": "https://assets.coingecko.com/coins/images/1/large/bitcoin.png",
  "last_updated": "2026-03-12T10:30:00.000Z"
}
```

### Endpoint 2: Historical daily prices (backfill)
```
GET https://api.coingecko.com/api/v3/coins/{id}/market_chart/range
  ?vs_currency=brl
  &from={unix_timestamp}
  &to={unix_timestamp}
```

**Response**:
```json
{
  "prices": [[1710000000000, 542300.00], ...],   // [timestamp_ms, price_brl]
  "total_volumes": [[1710000000000, 87654321098], ...]
}
```

---

## Curated asset list (initial)

Configurable via `application.yml` — no code change needed to add/remove assets.

```yaml
# application.yml
coingecko:
  assets:
    - id: bitcoin
      symbol: BTC
      name: Bitcoin
    - id: ethereum
      symbol: ETH
      name: Ethereum
    - id: solana
      symbol: SOL
      name: Solana
    - id: binancecoin
      symbol: BNB
      name: BNB
    - id: ripple
      symbol: XRP
      name: XRP
    - id: cardano
      symbol: ADA
      name: Cardano
    - id: avalanche-2
      symbol: AVAX
      name: Avalanche
    - id: chainlink
      symbol: LINK
      name: Chainlink
    - id: polkadot
      symbol: DOT
      name: Polkadot
    - id: usd-coin
      symbol: USDC
      name: USD Coin
    - id: tether
      symbol: USDT
      name: Tether
    - id: matic-network
      symbol: MATIC
      name: Polygon
    # Brazilian crypto platforms' native tokens:
    - id: mercado-bitcoin-token
      symbol: MBT
      name: Mercado Bitcoin
```

---

## Architecture

```
CoinGeckoScheduledJob
    └── CoinGeckoImportService
            ├── CoinGeckoApiClient     (@Client)
            ├── CoinGeckoAssetConfig   (@ConfigurationProperties — asset list from yml)
            ├── InvestmentAssetRepository (upsert by ticker)
            └── AssetDailyPriceRepository (upsert daily price)
```

---

## Activities

### Activity 1 — Configuration

**In `application.yml`**: add the `coingecko.assets` list shown above.

**File to create**: `domain/asset/coingecko/CoinGeckoAssetConfig.kt`

```kotlin
@ConfigurationProperties("coingecko")
data class CoinGeckoAssetConfig(
    val assets: List<CoinGeckoAssetEntry> = emptyList(),
    val apiKey: String? = null,   // optional Pro key
)

data class CoinGeckoAssetEntry(
    val id: String,       // CoinGecko coin id (e.g. "bitcoin")
    val symbol: String,   // ticker (e.g. "BTC")
    val name: String,
)
```

---

### Activity 2 — API client

**File to create**: `domain/asset/coingecko/CoinGeckoApiClient.kt`

```kotlin
@Client("https://api.coingecko.com")
interface CoinGeckoApiClient {

    @Get("/api/v3/coins/markets?vs_currency=brl&ids={ids}&order=market_cap_desc&per_page=250&page=1")
    fun getMarkets(@PathVariable ids: String): List<CoinGeckoMarketItem>

    @Get("/api/v3/coins/{id}/market_chart/range?vs_currency=brl&from={from}&to={to}")
    fun getHistoricalPrices(
        @PathVariable id: String,
        @PathVariable from: Long,   // Unix timestamp seconds
        @PathVariable to: Long,
    ): CoinGeckoHistoricResponse
}

data class CoinGeckoMarketItem(
    val id: String,
    val symbol: String,
    val name: String,
    @JsonProperty("current_price") val currentPrice: Double,
    @JsonProperty("high_24h") val high24h: Double?,
    @JsonProperty("low_24h") val low24h: Double?,
    @JsonProperty("total_volume") val totalVolume: Double?,
    @JsonProperty("last_updated") val lastUpdated: String,
)

data class CoinGeckoHistoricResponse(
    val prices: List<List<Double>>,         // [[timestamp_ms, price], ...]
    @JsonProperty("total_volumes") val volumes: List<List<Double>>,
)
```

---

### Activity 3 — Import service

**File to create**: `domain/asset/coingecko/CoinGeckoImportService.kt`

```kotlin
@Singleton
class CoinGeckoImportService(
    private val client: CoinGeckoApiClient,
    private val config: CoinGeckoAssetConfig,
    private val assetRepository: InvestmentAssetRepository,
    private val priceRepository: AssetDailyPriceRepository,
    private val jobControl: ImportJobControlRepository,
    private val log: Logger = LoggerFactory.getLogger(CoinGeckoImportService::class.java),
) {
    companion object {
        const val JOB_NAME = "coingecko"
        const val SOURCE   = "COINGECKO"
    }

    fun importLatest() {
        if (config.assets.isEmpty()) return

        // Fetch all configured assets in a single API call (comma-separated IDs)
        val ids = config.assets.joinToString(",") { it.id }

        runCatching {
            val markets   = client.getMarkets(ids)
            val priceDate = LocalDate.now()

            markets.forEach { item ->
                val configEntry = config.assets.find { it.id == item.id } ?: return@forEach

                val asset = assetRepository.upsertByTicker(
                    InvestmentAsset(
                        id           = null,
                        ticker       = configEntry.symbol.uppercase(),
                        isinCode     = null,
                        name         = item.name,
                        assetType    = "CRYPTO",
                        exchange     = "CRYPTO",
                        currencyCode = "BRL",
                        lotSize      = 1,
                        priceFactor  = 1,
                        issuerName   = null,
                        fundCnpj     = null,
                        isActive     = true,
                        source       = SOURCE,
                        externalId   = item.id,   // CoinGecko coin id
                    )
                )

                priceRepository.upsert(
                    AssetDailyPrice(
                        id          = null,
                        assetId     = asset.id!!,
                        priceDate   = priceDate,
                        openPrice   = null,
                        highPrice   = item.high24h?.toBigDecimal(),
                        lowPrice    = item.low24h?.toBigDecimal(),
                        closePrice  = item.currentPrice.toBigDecimal(),
                        avgPrice    = null,
                        volume      = item.totalVolume?.toBigDecimal(),
                        trades      = null,
                        source      = SOURCE,
                    )
                )
            }

            jobControl.markSuccess(JOB_NAME, priceDate)
            log.info("CoinGecko: imported ${markets.size} assets")
        }.onFailure {
            jobControl.markFailed(JOB_NAME, it.message)
            log.error("CoinGecko import failed", it)
        }
    }

    /**
     * Backfill historical prices for a single asset.
     * Called from management endpoint — rate-limit aware (1 req/asset).
     */
    fun backfillAsset(coinGeckoId: String, fromDate: LocalDate, toDate: LocalDate) {
        val asset = assetRepository.findByExternalId(SOURCE, coinGeckoId) ?: run {
            log.warn("CoinGecko backfill: asset $coinGeckoId not found in catalog — run importLatest first")
            return
        }

        val from = fromDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val to   = toDate.atStartOfDay().toEpochSecond(ZoneOffset.UTC)

        val historic = client.getHistoricalPrices(coinGeckoId, from, to)

        // Group by date (CoinGecko returns one point per day at midnight UTC)
        historic.prices.zip(historic.volumes).forEach { (pricePoint, volumePoint) ->
            val priceDate = Instant.ofEpochMilli(pricePoint[0].toLong())
                .atZone(ZoneOffset.UTC).toLocalDate()

            priceRepository.upsert(
                AssetDailyPrice(
                    id          = null,
                    assetId     = asset.id!!,
                    priceDate   = priceDate,
                    openPrice   = null,
                    highPrice   = null,
                    lowPrice    = null,
                    closePrice  = pricePoint[1].toBigDecimal(),
                    avgPrice    = null,
                    volume      = volumePoint[1].toBigDecimal(),
                    trades      = null,
                    source      = SOURCE,
                )
            )
        }
        log.info("CoinGecko backfill $coinGeckoId: ${historic.prices.size} points from $fromDate to $toDate")
    }
}
```

---

### Activity 4 — Scheduled job

**File to create**: `domain/asset/coingecko/CoinGeckoScheduledJob.kt`

```kotlin
@Singleton
class CoinGeckoScheduledJob(private val service: CoinGeckoImportService) {

    private val log = LoggerFactory.getLogger(CoinGeckoScheduledJob::class.java)

    // 08:00 BRT = 11:00 UTC
    // Crypto markets run 24/7 — we snapshot once per day
    @Scheduled(cron = "0 0 11 * * *")
    fun run() {
        log.info("CoinGeckoScheduledJob started")
        service.importLatest()
    }
}
```

---

### Activity 5 — Management endpoints

```kotlin
@Controller("/management/coingecko")
class CoinGeckoManagementController(private val service: CoinGeckoImportService) {

    @Post("/import/latest")
    fun importLatest() = service.importLatest()

    // Backfill one asset: POST /management/coingecko/backfill/bitcoin?from=2020-01-01&to=2024-12-31
    @Post("/backfill/{coinGeckoId}")
    fun backfill(
        @PathVariable coinGeckoId: String,
        @QueryValue from: String,
        @QueryValue to: String,
    ) = service.backfillAsset(
        coinGeckoId,
        LocalDate.parse(from),
        LocalDate.parse(to),
    )
}
```

---

## Rate limiting

The free CoinGecko API allows ~10–30 calls/min. Our design uses **one call per import run** (all configured assets in a single `getMarkets` request). The backfill endpoint calls once per asset — add a 2-second delay between assets if backfilling many:

```kotlin
config.assets.forEach { entry ->
    service.backfillAsset(entry.id, fromDate, toDate)
    Thread.sleep(2_000)   // 2s between calls — free tier safe
}
```

If a Pro API key is configured, this constraint can be relaxed.

## BRL pricing

All prices are fetched in BRL (`vs_currency=brl`) — no currency conversion needed. This is consistent with the rest of the portfolio.

## Decimal precision

Crypto prices vary wildly (BTC ~R$500,000; small altcoins ~R$0.0001). The `asset_daily_price.close_price NUMERIC(18,8)` column from spec 02 handles both extremes.

---

## Idempotency

- `upsertByTicker` — `ON CONFLICT (ticker) DO UPDATE`
- `priceRepository.upsert` — `ON CONFLICT (asset_id, price_date) DO UPDATE`
- Re-running the same day overwrites with the latest price (desirable — CoinGecko updates frequently)

## Backfill strategy

1. `POST /management/coingecko/import/latest` — seeds the catalog
2. `POST /management/coingecko/backfill/{id}?from=2020-01-01&to=2026-01-01` for each major asset
3. Respect rate limits: ~2s between calls on free tier

---

## File change summary

| File | Change |
|------|--------|
| `application.yml` | **Modify** — add `coingecko.assets` list |
| `domain/asset/coingecko/CoinGeckoAssetConfig.kt` | **Create** |
| `domain/asset/coingecko/CoinGeckoApiClient.kt` | **Create** |
| `domain/asset/coingecko/CoinGeckoImportService.kt` | **Create** |
| `domain/asset/coingecko/CoinGeckoScheduledJob.kt` | **Create** |
| `domain/asset/coingecko/CoinGeckoManagementController.kt` | **Create** |
| `repo/InvestmentAssetRepository.kt` | **Modify** — add `findByExternalId(source, externalId)` |

---

## Acceptance criteria

- [ ] All assets in the `coingecko.assets` config list are in `investment_asset` after `importLatest`
- [ ] Daily prices in BRL stored in `asset_daily_price`
- [ ] Adding a new asset to `application.yml` causes it to be imported on the next run (no code change)
- [ ] Single API call per daily import run (all assets batched into `ids=` param)
- [ ] Backfill endpoint works and respects rate limits
- [ ] `assetType = CRYPTO` and `exchange = CRYPTO` for all imported assets
- [ ] Re-running import does not create duplicate rows

## Out of scope

- Real-time WebSocket price streaming
- On-chain wallet tracking (DeFi)
- NFTs
- Staking yield tracking
- DEX token prices (only CoinGecko-listed assets)
- Full 10,000+ asset catalog (curated list only in MVP)
