# Job Spec: BACEN SGS — Reference Rates

> **Service**: `financialdata`
> **Goal**: Populate daily reference rate time series (CDI, SELIC, IPCA, IGP-M, TR) from the Brazilian Central Bank's public API so the fixed income simulator and portfolio yield calculations always have fresh data.
> **Effort**: Small
> **Run frequency**: Daily at 18:30 BRT (weekdays) — BACEN publishes the day's rates around 18:00

---

## Context

The `financialdata` service already has:
- `financial_data.economic_indicator` — master records (seeded in `V1_10`)
- `financial_data.economic_indicator_variation` — named series per indicator
- `financial_data.economic_indicator_value` — time series data points
- `financial_data.economic_indicator_load_control` — tracks last loaded date per series
- `IndicatorService` / `IndicatorValueService` — query this data for the fixed income simulator

This job fills `economic_indicator_value` by pulling from the BACEN SGS public API. **No new tables needed.** The missing piece is the scheduled ingestion job.

---

## Data source

**API**: BACEN SGS (Sistema Gerenciador de Séries Temporais)
**Base URL**: `https://api.bcb.gov.br/dados/serie/bcdata.sgs.{series}/dados`
**Auth**: None — fully public
**Format**: JSON array of `{ "data": "DD/MM/YYYY", "valor": "0.123" }`
**Rate limits**: No documented limit; ~1–2 req/s is safe

### Series to import

| Series code | Indicator | Variation | Frequency | Unit |
|-------------|-----------|-----------|-----------|------|
| `12` | CDI | Daily rate | Business days | % a.d. |
| `11` | SELIC | Daily rate | Business days | % a.d. |
| `433` | IPCA | Monthly rate | Monthly | % a.m. |
| `189` | IGP-M | Monthly rate | Monthly | % a.m. |
| `226` | TR | Monthly rate | Monthly | % a.m. |

**Example call** (last 30 data points for CDI):
```
GET https://api.bcb.gov.br/dados/serie/bcdata.sgs.12/dados/ultimos/30?formato=json
```

**Example call** (range, for backfill):
```
GET https://api.bcb.gov.br/dados/serie/bcdata.sgs.12/dados?dataInicial=01/01/2020&dataFinal=31/12/2020&formato=json
```

---

## Architecture

```
BacenSgsScheduledJob          (Micronaut @Scheduled — triggers daily)
    └── BacenSgsImportService (orchestrates series import + idempotency)
            ├── BacenSgsApiClient    (@Client — HTTP calls to BACEN API)
            └── IndicatorValueRepository  (existing — writes economic_indicator_value)
                LoadControlRepository      (existing — updates economic_indicator_load_control)
```

---

## Activities

### Activity 1 — Micronaut HTTP client

**File to create**: `domain/indicator/bacensgs/BacenSgsApiClient.kt`

```kotlin
@Client("https://api.bcb.gov.br")
interface BacenSgsApiClient {

    @Get("/dados/serie/bcdata.sgs.{series}/dados/ultimos/{count}?formato=json")
    fun getLatest(
        @PathVariable series: Int,
        @PathVariable count: Int = 5,
    ): List<BacenSgsDataPoint>

    @Get("/dados/serie/bcdata.sgs.{series}/dados?dataInicial={from}&dataFinal={to}&formato=json")
    fun getRange(
        @PathVariable series: Int,
        @PathVariable from: String,   // DD/MM/YYYY
        @PathVariable to: String,     // DD/MM/YYYY
    ): List<BacenSgsDataPoint>
}

data class BacenSgsDataPoint(
    val data: String,    // "DD/MM/YYYY"
    val valor: String,   // "0.04650" — percentage as string, may be empty for non-published days
)
```

### Activity 2 — Import service

**File to create**: `domain/indicator/bacensgs/BacenSgsImportService.kt`

```kotlin
@Singleton
class BacenSgsImportService(
    private val client: BacenSgsApiClient,
    private val indicatorValueRepository: IndicatorValueRepository,
    private val loadControlRepository: LoadControlRepository,
    private val log: Logger = LoggerFactory.getLogger(BacenSgsImportService::class.java),
) {

    companion object {
        // Maps SGS series code → (indicatorCode, variationCode) in our DB
        val SERIES_MAP = mapOf(
            12   to SeriesTarget("CDI",   "DAILY"),
            11   to SeriesTarget("SELIC", "DAILY"),
            433  to SeriesTarget("IPCA",  "MONTHLY"),
            189  to SeriesTarget("IGPM",  "MONTHLY"),
            226  to SeriesTarget("TR",    "MONTHLY"),
        )
    }

    fun importAll() {
        SERIES_MAP.entries.forEach { (seriesCode, target) ->
            runCatching { importSeries(seriesCode, target) }
                .onFailure { log.error("Failed to import SGS series $seriesCode", it) }
        }
    }

    private fun importSeries(seriesCode: Int, target: SeriesTarget) {
        val lastDate = loadControlRepository.findLastDataDate("bacen-sgs-$seriesCode")
        val points   = if (lastDate != null) {
            // Incremental: fetch from day after last known date
            val from = lastDate.plusDays(1).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            val to   = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            client.getRange(seriesCode, from, to)
        } else {
            // First run — backfill 5 years
            val from = LocalDate.now().minusYears(5).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            val to   = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            client.getRange(seriesCode, from, to)
        }

        val toInsert = points
            .filter { it.valor.isNotBlank() }
            .map { point ->
                EconomicIndicatorValue(
                    indicatorCode   = target.indicatorCode,
                    variationCode   = target.variationCode,
                    referenceDate   = LocalDate.parse(point.data, DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    value           = BigDecimal(point.valor),
                )
            }

        if (toInsert.isNotEmpty()) {
            // Upsert — ON CONFLICT (indicator_code, variation_code, reference_date) DO UPDATE
            indicatorValueRepository.upsertAll(toInsert)
            val latestDate = toInsert.maxOf { it.referenceDate }
            loadControlRepository.upsert("bacen-sgs-$seriesCode", latestDate)
            log.info("SGS $seriesCode: inserted/updated ${toInsert.size} values, latest=$latestDate")
        }
    }
}

data class SeriesTarget(val indicatorCode: String, val variationCode: String)
```

### Activity 3 — Scheduled job

**File to create**: `domain/indicator/bacensgs/BacenSgsScheduledJob.kt`

```kotlin
@Singleton
class BacenSgsScheduledJob(private val service: BacenSgsImportService) {

    private val log = LoggerFactory.getLogger(BacenSgsScheduledJob::class.java)

    // 18:30 BRT = 21:30 UTC (BRT = UTC-3)
    // Runs Mon–Fri; BACEN only publishes on business days
    @Scheduled(cron = "0 30 21 * * MON-FRI")
    fun run() {
        log.info("BacenSgsScheduledJob started")
        service.importAll()
        log.info("BacenSgsScheduledJob finished")
    }
}
```

### Activity 4 — Repository additions

The existing `IndicatorValueRepository` likely only has reads. Add:

**File to modify**: `repo/IndicatorValueRepository.kt` (or a new `IndicatorValueWriteRepository.kt`)

```kotlin
// Add to existing repository or create new one:

@Repository
interface IndicatorValueWriteRepository : JpaRepository<EconomicIndicatorValueEntity, UUID> {

    // Implemented in IndicatorValueWriteRepositoryImpl with native SQL:
    // INSERT INTO financial_data.economic_indicator_value (...)
    // ON CONFLICT (indicator_code, variation_code, reference_date) DO UPDATE SET value = EXCLUDED.value
    fun upsertAll(values: List<EconomicIndicatorValue>)
}
```

### Activity 5 — Database migration (if needed)

Check `V1_9__create_indicator_tables.sql` for the exact schema. If `economic_indicator_load_control` does not already have `last_data_date`, add it:

```sql
-- V1_14__add_last_data_date_to_load_control.sql (only if column is missing)
ALTER TABLE financial_data.economic_indicator_load_control
    ADD COLUMN IF NOT EXISTS last_data_date DATE;
```

Also add the upsert-friendly unique constraint on `economic_indicator_value` if not present:

```sql
ALTER TABLE financial_data.economic_indicator_value
    ADD CONSTRAINT uq_indicator_value UNIQUE (indicator_code, variation_code, reference_date)
    ON CONFLICT DO NOTHING;   -- safe to run idempotently
```

### Activity 6 — Manual backfill trigger

Add an admin GraphQL mutation (or Micronaut management endpoint) for triggering a backfill on demand:

```kotlin
// Option A: management endpoint (simpler, no auth needed in internal network)
@Controller("/management/bacen-sgs")
class BacenSgsManagementController(private val service: BacenSgsImportService) {

    @Post("/import")
    fun triggerImport(): HttpResponse<String> {
        service.importAll()
        return HttpResponse.ok("Import triggered")
    }
}
```

---

## Idempotency

- `upsertAll` uses `ON CONFLICT (indicator_code, variation_code, reference_date) DO UPDATE` — safe to re-run
- Re-running on the same day overwrites with the same value (no side effects)
- `load_control` tracks `last_data_date` so incremental fetches don't re-request already-loaded data

## Error handling

- Each series import wrapped in `runCatching` — one series failure does not abort others
- Errors logged with series code + full exception; no retry in v1 (next cron run will retry)
- If BACEN API returns empty for a non-business day, `filter { it.valor.isNotBlank() }` skips it

## Backfill strategy

- **First run** (no `last_data_date` in load_control): fetches 5 years of history
- **Subsequent runs**: incremental from `last_data_date + 1` to today
- Manual backfill: call the management endpoint; temporarily set `last_data_date = NULL` in load_control

---

## File change summary

| File | Change |
|------|--------|
| `domain/indicator/bacensgs/BacenSgsApiClient.kt` | **Create** |
| `domain/indicator/bacensgs/BacenSgsImportService.kt` | **Create** |
| `domain/indicator/bacensgs/BacenSgsScheduledJob.kt` | **Create** |
| `repo/IndicatorValueWriteRepository.kt` | **Create** (or modify existing) |
| `db/migration/V1_14__...sql` | **Create** (only if schema adjustments needed) |
| `graphql/resolver/BacenSgsManagementController.kt` | **Create** |

---

## Acceptance criteria

- [ ] CDI, SELIC, IPCA, IGP-M, TR daily/monthly values are populated in `economic_indicator_value`
- [ ] Re-running the job does not create duplicate rows
- [ ] Incremental run only fetches data after `last_data_date`
- [ ] Failed series import logs the error and continues other series
- [ ] First run (backfill) loads 5 years of history
- [ ] Fixed income simulator (`fixedIncomeComparison`) continues to work correctly with new data
- [ ] Management endpoint triggers a manual import

## Out of scope

- Real-time intraday rates (daily closing rate is sufficient for portfolio yield calc)
- PTAX (exchange rates) — separate job when offshore assets are added
- Expectation series (Focus report) — future feature
