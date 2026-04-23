# Dashboard — Period Summary v2 (com Savings Rate)

## Contexto

O `dashboardPeriodSummary` atual retorna apenas 5 campos:
`totalIncome`, `totalExpenses`, `net`, `closingBalance`, `openingBalance`.

O objetivo é expandir para um resumo completo do período com:
patrimônio, fluxo de caixa com médias mensais, taxa de poupança,
aportes em investimentos e rendimento da carteira.

O único filtro disponível é o range de tempo (`startYear/Month` → `endYear/Month`).
Performance é prioridade: toda a lógica deve resolver em **3 queries** ao banco.

---

## Novos campos do contrato

### GraphQL — `DashboardPeriodSummary` (schema.graphqls)

```graphql
type DashboardPeriodSummary {
  # ── Fluxo de caixa ──────────────────────────────────────────────────────
  totalIncome: Float!
  totalExpenses: Float!
  """net = totalIncome - totalExpenses"""
  net: Float!
  """Number of months in the selected range (used by frontend for averages)."""
  monthCount: Int!
  avgMonthlyIncome: Float!
  avgMonthlyExpenses: Float!
  """(net / totalIncome) × 100. Null when totalIncome = 0."""
  savingsRate: Float

  # ── Patrimônio ──────────────────────────────────────────────────────────
  """Closing cash balance of the last month (banking accounts, excl. transfers)."""
  closingCashBalance: Float!
  """Investment portfolio gross value at end of period."""
  closingPortfolioValue: Float!
  """closingCashBalance + closingPortfolioValue."""
  totalNetWorth: Float!

  # ── Investimentos ────────────────────────────────────────────────────────
  """SUM of DEPOSIT flow events in the period."""
  totalInvested: Float!
  avgMonthlyInvested: Float!
  """closingPortfolioValue - openingPortfolioValue - netContributions."""
  totalReturn: Float!
  """Investment portfolio gross value at start of period."""
  openingPortfolioValue: Float!
  """(totalReturn / openingPortfolioValue) × 100. Null when openingPortfolioValue = 0."""
  returnPct: Float

  # ── Campos mantidos para compatibilidade ────────────────────────────────
  """Deprecated alias for closingCashBalance. Keep for frontend compatibility."""
  closingBalance: Float!
  openingBalance: Float!
}
```

> **Nota sobre `openingBalance`**: atualmente representa o saldo bancário do início do
> período. Mantido para não quebrar o frontend existente. Renomear para
> `openingCashBalance` em refactor futuro.

---

## Fontes de dados e mapeamento

| Campo novo | Fonte | Como obter |
|---|---|---|
| `monthCount` | `monthly_balance_snapshots` | COUNT(DISTINCT year, month) dos snapshots retornados |
| `avgMonthlyIncome` | computed | `totalIncome / monthCount` |
| `avgMonthlyExpenses` | computed | `totalExpenses / monthCount` |
| `savingsRate` | computed | `net / totalIncome × 100` (null se income = 0) |
| `closingCashBalance` | `monthly_balance_snapshots` | SUM `closing_balance` do último mês |
| `closingPortfolioValue` | `investment_portfolio_quota` | SUM `total_portfolio_value` (PORTFOLIO dim.) latest ≤ endDate |
| `openingPortfolioValue` | `investment_portfolio_quota` | SUM `total_portfolio_value` (PORTFOLIO dim.) latest < startDate |
| `totalNetWorth` | computed | `closingCashBalance + closingPortfolioValue` |
| `totalInvested` | `investment_portfolio_flow_event` | SUM `gross_amount` WHERE `event_type = 'DEPOSIT'` |
| `avgMonthlyInvested` | computed | `totalInvested / monthCount` |
| `totalReturn` | computed | `closingPortfolioValue - openingPortfolioValue - netContributions` |
| `returnPct` | computed | `totalReturn / openingPortfolioValue × 100` (null se opening = 0) |

onde `netContributions = SUM(DEPOSIT) - SUM(WITHDRAWAL)` no período.

---

## Estratégia de performance: 3 queries fixas

Independente do range (1 mês ou 5 anos), o custo é sempre **3 queries**:

### Query 1 — Snapshots (já existente)

Sem alteração. Retorna snapshots do range para calcular income, expenses,
openingBalance, closingCashBalance e monthCount.

```sql
-- já implementado em snapshotRepository.findByRangeForAccount / findByRange
SELECT * FROM banking.monthly_balance_snapshots
WHERE account_id = :accountId            -- ou financial_account_id = :financialAccountId
  AND (year, month) BETWEEN (:startYear, :startMonth) AND (:endYear, :endMonth)
```

### Query 2 — Portfolio value (opening + closing em 1 query) — **NOVA**

Precisa de novo método nativo no repositório de portfolio quota.
Traz em **uma só query** o valor mais recente para cada investment account
em dois pontos no tempo: início e fim do período.

```sql
-- opening: latest PORTFOLIO quota BEFORE the period
-- closing: latest PORTFOLIO quota AT OR BEFORE the period end
-- Uses DISTINCT ON for O(log N) index scan per account per snapshot

WITH accounts AS (
    SELECT id FROM banking.financial_accounts
    WHERE account_id = :accountId
      AND type = 'INVESTMENT'
      AND deleted_at IS NULL
),
opening AS (
    SELECT DISTINCT ON (q.financial_account_id)
           q.financial_account_id,
           q.total_portfolio_value AS opening_value
    FROM banking.investment_portfolio_quota q
    JOIN accounts a ON a.id = q.financial_account_id
    WHERE q.dimension_type = 'PORTFOLIO'
      AND q.dimension_key   = ''
      AND q.quota_date < make_date(:startYear, :startMonth, 1)
    ORDER BY q.financial_account_id, q.quota_date DESC
),
closing AS (
    SELECT DISTINCT ON (q.financial_account_id)
           q.financial_account_id,
           q.total_portfolio_value AS closing_value
    FROM banking.investment_portfolio_quota q
    JOIN accounts a ON a.id = q.financial_account_id
    WHERE q.dimension_type = 'PORTFOLIO'
      AND q.dimension_key   = ''
      AND q.quota_date <= make_date(:endYear, :endMonth, 1) + INTERVAL '1 month' - INTERVAL '1 day'
    ORDER BY q.financial_account_id, q.quota_date DESC
)
SELECT
    COALESCE(SUM(closing_value), 0) AS closing_portfolio_value,
    COALESCE(SUM(opening_value), 0) AS opening_portfolio_value
FROM closing
FULL OUTER JOIN opening USING (financial_account_id)
```

**Índice existente que garante a eficiência:**
`idx_portfolio_quota_dimension` em `(financial_account_id, dimension_type, dimension_key, quota_date DESC)` — o `DISTINCT ON` usa esse índice para index scan, não seq scan.

### Query 3 — Portfolio flow events (contribuições) — **NOVA**

Precisa de novo método nativo agregador no `PortfolioFlowEventRepository` ou
`PortfolioFlowEventEntityRepository`. Variante tenant-wide (sem `financialAccountId`).

```sql
SELECT
    COALESCE(SUM(gross_amount) FILTER (WHERE event_type = 'DEPOSIT'),    0) AS total_invested,
    COALESCE(SUM(gross_amount) FILTER (WHERE event_type = 'WITHDRAWAL'),  0) AS total_withdrawn
FROM banking.investment_portfolio_flow_event
WHERE financial_account_id IN (
    SELECT id FROM banking.financial_accounts
    WHERE account_id = :accountId
      AND type = 'INVESTMENT'
      AND deleted_at IS NULL
)
AND event_date >= make_date(:startYear, :startMonth, 1)
AND event_date <= make_date(:endYear, :endMonth, 1) + INTERVAL '1 month' - INTERVAL '1 day'
```

**Índice existente:**
`idx_portfolio_flow_account_date` em `(financial_account_id, event_date DESC)`.

---

## Arquivos a modificar / criar

### Backend

| Arquivo | Mudança |
|---|---|
| `schema.graphqls` | Expandir `DashboardPeriodSummary` com os novos campos |
| `DashboardPeriodSummary.kt` | Adicionar novos campos ao data class |
| `DashboardPeriodSummaryDTO.kt` | Idem |
| `DashboardAnalyticsService.getDashboardPeriodSummary()` | Chamar as 3 queries e montar o objeto |
| `PortfolioQuotaRepository.kt` + `DefaultPortfolioQuotaRepository.kt` | Novo método `findOpeningAndClosingPortfolioValues(accountId, startYear, startMonth, endYear, endMonth): PortfolioValueWindow` |
| `PortfolioFlowEventRepository.kt` + implementação | Novo método `sumContributionsByTenant(accountId, startYear, startMonth, endYear, endMonth): ContributionTotals` |

### Novos tipos de suporte (domain)

```kotlin
// Resultado da Query 2
data class PortfolioValueWindow(
    val openingPortfolioValue: BigDecimal,
    val closingPortfolioValue: BigDecimal,
)

// Resultado da Query 3
data class ContributionTotals(
    val totalInvested: BigDecimal,
    val totalWithdrawn: BigDecimal,
) {
    val netContributions: BigDecimal get() = totalInvested - totalWithdrawn
}
```

### Frontend

| Arquivo | Mudança |
|---|---|
| `queries.ts` | Adicionar novos campos ao fragment/query `DASHBOARD_PERIOD_SUMMARY` |
| Componente do Period Summary widget | Exibir os 7 novos cartões conforme layout combinado |
| `queries.generated.ts` | Regenerar via codegen |

---

## Cálculos computados no Kotlin (sem query extra)

```kotlin
val monthCount        = snapshots.map { Pair(it.year, it.month) }.distinct().size
val avgMonthlyIncome  = if (monthCount > 0) totalIncome / monthCount.toBigDecimal() else BigDecimal.ZERO
val avgMonthlyExpenses= if (monthCount > 0) totalExpenses / monthCount.toBigDecimal() else BigDecimal.ZERO
val savingsRate       = if (totalIncome > BigDecimal.ZERO) net / totalIncome * 100.toBigDecimal() else null
val totalNetWorth     = closingCashBalance + closingPortfolioValue
val totalReturn       = closingPortfolioValue - openingPortfolioValue - contributions.netContributions
val returnPct         = if (openingPortfolioValue > BigDecimal.ZERO) totalReturn / openingPortfolioValue * 100.toBigDecimal() else null
val avgMonthlyInvested= if (monthCount > 0) contributions.totalInvested / monthCount.toBigDecimal() else BigDecimal.ZERO
```

---

## Critérios de aceitação

- [ ] Usuário com receitas=46k, despesas=20k no período vê `savingsRate ≈ 56.6%`
      e `net = 26k`.
- [ ] Usuário com carteira de investimentos de R$100k no final do período vê
      `totalNetWorth = closingCashBalance + 100k`.
- [ ] Sem dados de investimentos: `closingPortfolioValue = 0`, `totalReturn = 0`,
      `returnPct = null`.
- [ ] `monthCount` = 3 para um range de 3 meses com snapshots em todos os meses.
- [ ] `avgMonthlyIncome = totalIncome / monthCount` (sem arredondamento intermediário).
- [ ] Performance: nenhum request ao `dashboardPeriodSummary` dispara mais de 3 queries
      ao banco, independente do tamanho do range.
- [ ] Campos `closingBalance` e `openingBalance` mantidos com valores idênticos a
      `closingCashBalance` e `openingBalance` respectivamente (compatibilidade).

---

## O que NÃO muda

- Queries `cashFlowSeries`, `portfolioNetWorthSeries` e `categoryBreakdownForRange` — sem alteração.
- Filtro por `financialAccountId` ainda é suportado; neste caso as Queries 2 e 3
  filtram pelo `financialAccountId` específico em vez do tenant.
- `PortfolioFlowEventType` enum — sem alteração.
