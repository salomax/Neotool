# TWR Debug Query — Specification

> **For AI Agents:** This is a complete, self-contained technical specification.
> Read every section before writing a single line of code.
> Do **not** add features, tests, or abstractions beyond what is specified here.

---

## 0. Context

**Working directory:** `service/kotlin/banking/`  
**Framework:** Micronaut (not Spring Boot) — `@Singleton`, `@MicronautTest`  
**API style:** GraphQL only. Follow the existing pattern: schema → DTO → resolver wiring.  
**Main package:** `io.github.salomax.neotool.banking`

**Why this query exists:** The portfolio TWR calculation sometimes produces unexpected results (e.g., −100%) after positions are added and removed. This debug query exposes every intermediate value of the `buildTimeWeightedIndexSeries` pipeline so the root cause can be identified without relying on log files or database inspection.

**Key files to read before starting:**

| File | Purpose |
|---|---|
| `src/main/resources/graphql/schema.graphqls` | Schema to extend |
| `graphql/investment/InvestmentResolver.kt` | Resolver to extend |
| `graphql/investment/InvestmentDTO.kt` | DTOs to extend |
| `service/investment/PortfolioPerformanceQueryService.kt` | Contains `buildTimeWeightedIndexSeries` and `buildScopedDimensionFlowAmounts` — the logic to expose |
| `service/investment/InvestmentPortfolioSummaryService.kt` | Shows how `getPortfolioPerformance` is called |
| `graphql/BankingWiringFactory.kt` | Where new query fetchers must be wired |

---

## 1. What to Build

A single new GraphQL query:

```graphql
portfolioTwrDebug(financialAccountId: ID, period: PerformancePeriod): PortfolioTwrDebug!
```

It runs the **exact same pipeline** as `investmentPortfolioSummary` → `getPortfolioPerformance`, but instead of returning only the final `twr` scalar, it returns every intermediate value: the quota series, the flow events considered, and the per-day TWR factor chain.

---

## 2. GraphQL Schema (additions to `schema.graphqls`)

Add the following types and the new query. Do **not** modify any existing type.

### 2.1 New types

```graphql
"""One row in the portfolio quota series fed into the TWR engine."""
type TwrQuotaPoint {
  date: Date!
  """Total portfolio value on this date (sum of all holdings mark-to-market)."""
  totalValue: Float!
}

"""One flow event considered by the TWR engine for this account and period."""
type TwrFlowEvent {
  date: Date!
  """DEPOSIT | WITHDRAWAL | DIVIDEND | INCOME_TAX | CORPORATE_ACTION"""
  eventType: String!
  grossAmount: Float!
  """Signed net amount used in the TWR formula: positive = inflow, negative = outflow."""
  canonicalNetFlow: Float!
}

"""One step in the TWR chain, corresponding to a quota point after the first."""
type TwrDailyStep {
  date: Date!
  previousValue: Float!
  currentValue: Float!
  """Sum of canonical net flows on this date (0 if none)."""
  netFlow: Float!
  """(currentValue - previousValue - netFlow) / previousValue"""
  dailyReturn: Float!
  """Running product: factor_t = factor_(t-1) * (1 + dailyReturn_t). Starts at 1.0."""
  cumulativeFactor: Float!
}

"""Full diagnostic payload for the TWR calculation of one account and period."""
type PortfolioTwrDebug {
  """ISO date of the first quota in the series (anchor point, factor = 1.0)."""
  startDate: Date
  """ISO date of the last quota in the series."""
  endDate: Date
  """Same value as investmentPortfolioSummary.performance.twr — null if series has fewer than 2 points."""
  twr: Float
  """The quota series (one entry per calendar day that has a stored quota)."""
  series: [TwrQuotaPoint!]!
  """Flow events in the window [startDate+1 .. endDate] used by the engine."""
  flowEvents: [TwrFlowEvent!]!
  """Per-day computation steps (series.size - 1 entries; empty if series < 2)."""
  dailySteps: [TwrDailyStep!]!
}
```

### 2.2 New query entry point

In the `type Query` block, add after the `investmentPortfolioSummary` entry:

```graphql
"""Debug view of the TWR calculation pipeline for a given account and period."""
portfolioTwrDebug(financialAccountId: ID, period: PerformancePeriod): PortfolioTwrDebug!
```

---

## 3. Kotlin DTOs (additions to `InvestmentDTO.kt`)

Add the following data classes at the bottom of `InvestmentDTO.kt`. Do not create a new file.

```kotlin
data class TwrQuotaPointDTO(
    val date: LocalDate,
    val totalValue: Double,
)

data class TwrFlowEventDTO(
    val date: LocalDate,
    val eventType: String,
    val grossAmount: Double,
    val canonicalNetFlow: Double,
)

data class TwrDailyStepDTO(
    val date: LocalDate,
    val previousValue: Double,
    val currentValue: Double,
    val netFlow: Double,
    val dailyReturn: Double,
    val cumulativeFactor: Double,
)

data class PortfolioTwrDebugDTO(
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val twr: Double?,
    val series: List<TwrQuotaPointDTO>,
    val flowEvents: List<TwrFlowEventDTO>,
    val dailySteps: List<TwrDailyStepDTO>,
)
```

---

## 4. Service Method (addition to `PortfolioPerformanceQueryService.kt`)

Add one new **public** method `getPortfolioTwrDebug`. It must reuse the existing private helpers — do **not** copy-paste their logic.

### 4.1 Method signature

```kotlin
fun getPortfolioTwrDebug(
    financialAccountId: UUID?,
    accountId: UUID,
    period: PerformancePeriodDTO,
): PortfolioTwrDebugDTO
```

### 4.2 Implementation rules

**When `financialAccountId` is null (consolidated view):**

Return immediately:
```kotlin
return PortfolioTwrDebugDTO(null, null, null, emptyList(), emptyList(), emptyList())
```

**When `financialAccountId` is provided:**

**Step 1 — Resolve window**

```kotlin
val window = resolvePerformanceWindow(
    financialAccountId = financialAccountId,
    dimensionType = PortfolioDimensionType.PORTFOLIO,
    dimensionKey = null,
    period = period,
) ?: return PortfolioTwrDebugDTO(null, null, null, emptyList(), emptyList(), emptyList())
```

**Step 2 — Build quota series DTO**

```kotlin
val seriesDTO = window.series.map { quota ->
    TwrQuotaPointDTO(
        date = quota.quotaDate,
        totalValue = quota.totalPortfolioValue.toDouble(),
    )
}
```

**Step 3 — Build flow amounts map** (same call as in `getScopedPortfolioPerformance`)

```kotlin
val flowAmountsByDate = buildScopedDimensionFlowAmounts(
    financialAccountId = financialAccountId,
    accountId = null,
    dimensionType = PortfolioDimensionType.PORTFOLIO,
    dimensionKey = null,
    from = window.start.quotaDate.plusDays(1),
    to = window.end.quotaDate,
)
```

**Step 4 — Build flow events DTO**

Fetch events directly from the repository (same date range and filter as `buildScopedDimensionFlowAmounts` uses internally):

```kotlin
val rawEvents = portfolioFlowEventRepository
    .findByAccountAndDateRange(financialAccountId, window.start.quotaDate.plusDays(1), window.end.quotaDate)
    .filter { it.sourceReferenceType != PORTFOLIO_EVENT_SOURCE_BANKING_TRANSACTION }
    .sortedBy { it.eventDate }

val flowEventsDTO = rawEvents.map { event ->
    TwrFlowEventDTO(
        date = event.eventDate,
        eventType = event.eventType.name,
        grossAmount = event.grossAmount.toDouble(),
        canonicalNetFlow = event.toCanonicalNetFlowAmount()?.toDouble() ?: 0.0,
    )
}
```

> `toCanonicalNetFlowAmount()` is a private extension on `InvestmentPortfolioFlowEvent` inside this class.
> Change its visibility from `private` to `internal` so it can be called here.

**Step 5 — Build daily steps**

Replay the same loop logic as `buildTimeWeightedIndexSeries` but collect every intermediate value.
Use **Double arithmetic** here (diagnostic view, not production calculation):

```kotlin
val dailySteps = mutableListOf<TwrDailyStepDTO>()

if (window.series.size >= 2) {
    var previousValue = window.series.first().totalPortfolioValue.toDouble()

    if (previousValue > 0.0) {
        var factor = 1.0

        window.series.drop(1).forEach { quota ->
            val currentValue = quota.totalPortfolioValue.toDouble()
            val netFlow = flowAmountsByDate[quota.quotaDate]?.toDouble() ?: 0.0

            val (dailyReturn, newFactor) = if (previousValue <= 0.0) {
                0.0 to factor
            } else {
                val r = (currentValue - previousValue - netFlow) / previousValue
                val f = factor * (1.0 + r)
                r to f
            }

            dailySteps += TwrDailyStepDTO(
                date = quota.quotaDate,
                previousValue = previousValue,
                currentValue = currentValue,
                netFlow = netFlow,
                dailyReturn = dailyReturn,
                cumulativeFactor = newFactor,
            )

            factor = newFactor
            previousValue = currentValue
        }
    }
}
```

**Step 6 — Compute final TWR**

```kotlin
val twr = if (dailySteps.isEmpty()) null else dailySteps.last().cumulativeFactor - 1.0
```

**Step 7 — Return**

```kotlin
return PortfolioTwrDebugDTO(
    startDate = window.start.quotaDate,
    endDate = window.end.quotaDate,
    twr = twr,
    series = seriesDTO,
    flowEvents = flowEventsDTO,
    dailySteps = dailySteps,
)
```

---

## 5. Resolver (addition to `InvestmentResolver.kt`)

Add a new method `portfolioTwrDebug` following the same pattern as the existing `investmentPortfolioSummary` method.

```kotlin
fun portfolioTwrDebug(
    financialAccountId: UUID?,
    period: PerformancePeriodDTO?,
    env: DataFetchingEnvironment,
): PortfolioTwrDebugDTO {
    val accountId = accountIdFromEnv(env)
    return portfolioPerformanceQueryService.getPortfolioTwrDebug(
        financialAccountId = financialAccountId,
        accountId = accountId,
        period = period ?: PerformancePeriodDTO.ALL,
    )
}
```

---

## 6. Wiring (`BankingWiringFactory.kt`)

Register the new data fetcher. Find the block that wires `investmentPortfolioSummary` and add immediately after it, following the exact same pattern:

```kotlin
.dataFetcher("portfolioTwrDebug") { env ->
    investmentResolver.portfolioTwrDebug(
        financialAccountId = env.getArgumentOrNull<String>("financialAccountId")?.let(UUID::fromString),
        period = env.getArgumentOrNull<String>("period")?.let { PerformancePeriodDTO.valueOf(it) },
        env = env,
    )
}
```

---

## 7. Visibility change: `toCanonicalNetFlowAmount`

In `PortfolioPerformanceQueryService.kt`, find:

```kotlin
private fun InvestmentPortfolioFlowEvent.toCanonicalNetFlowAmount(): BigDecimal? =
```

Change `private` to `internal`. This is the only visibility change. All existing callers are inside the same class and are unaffected.

---

## 8. Files to Create

None. All changes go into existing files.

---

## 9. Files to Modify

| File | Change |
|---|---|
| `src/main/resources/graphql/schema.graphqls` | Add 4 new types + 1 query entry |
| `graphql/investment/InvestmentDTO.kt` | Add 4 new data classes at the bottom |
| `service/investment/PortfolioPerformanceQueryService.kt` | `private` → `internal` on `toCanonicalNetFlowAmount`; add `getPortfolioTwrDebug` |
| `graphql/investment/InvestmentResolver.kt` | Add `portfolioTwrDebug` method |
| `graphql/BankingWiringFactory.kt` | Register `portfolioTwrDebug` fetcher |

---

## 10. Acceptance Criteria

1. The service starts with no errors after the changes.
2. `portfolioTwrDebug(financialAccountId: "...", period: ALL)` returns a non-null payload.
3. `twr` in the debug response equals `investmentPortfolioSummary.performance.twr` for the same account and period (within ±0.000001 floating-point rounding).
4. `dailySteps.size == series.size - 1` when `series.size >= 2`.
5. For each step `i`: `dailySteps[i].cumulativeFactor ≈ (i == 0 ? 1.0 : dailySteps[i-1].cumulativeFactor) * (1 + dailySteps[i].dailyReturn)`.
6. When the portfolio has no quotas, all lists are empty and `twr` is null.
7. When `financialAccountId` is null, all lists are empty and `twr` is null.
8. The existing `investmentPortfolioSummary` query returns the same values as before (no regression).

---

## 11. Constraints

1. No new unit tests — this is a diagnostic tool.
2. No new files — all changes are additions to existing files.
3. No behaviour change to any existing query or service method.
4. No copy-paste of `buildTimeWeightedIndexSeries` logic — reuse existing helpers.
5. This query is a debugging tool only. Do not add it to any frontend GraphQL operation file.
