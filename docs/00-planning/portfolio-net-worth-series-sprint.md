# Sprint: `portfolioNetWorthSeries` — Patrimônio Total (Banking + Carteira)

## Contexto

O `NetWorthWidget` exibe hoje apenas o saldo bancário (`closingBalance` de `monthly_balance_snapshots`).
O objetivo deste sprint é adicionar o valor da carteira de investimentos mês a mês, compondo o
**patrimônio total real** no gráfico.

A query `portfolioPerformance` existente não é adequada (sem controle de range de datas, voltada para
TWR/benchmarks). O padrão correto é criar uma query dedicada simétrica às já existentes:

```
cashFlowSeries(startYear, startMonth, endYear, endMonth)         ← banking
portfolioNetWorthSeries(startYear, startMonth, endYear, endMonth) ← investments  ← NOVO
```

---

## Fonte de dados no backend

A tabela `investment_portfolio_quota` já armazena snapshots **diários** do valor de mercado da carteira:

| Coluna                | Tipo            | Descrição                                              |
|-----------------------|-----------------|--------------------------------------------------------|
| `financial_account_id`| UUID            | Conta de custódia                                      |
| `dimension_type`      | enum            | `PORTFOLIO` = consolidado, `ASSET_GROUP`, `GRID_CLASS` |
| `dimension_key`       | varchar(50)     | `""` (vazio) para `PORTFOLIO`                          |
| `quota_date`          | date            | Data do snapshot                                       |
| `quota_value`         | numeric(18,8)   | Valor da cota naquele dia                              |
| `total_quotas`        | numeric(18,8)   | Número de cotas                                        |
| `total_portfolio_value`| numeric(18,2)  | Valor total da carteira naquele dia ← **campo usado**  |
| `has_estimated_values`| boolean         | True quando algum ativo usou valuation estimado        |

Constraint única: `(financial_account_id, dimension_type, dimension_key, quota_date)`.

O repositório já tem:
- `findByAccountAndDateRange(accountId, dimensionType, dimensionKey, from, to)` → `List<Quota>` ASC
- `findLatestBefore(accountId, dimensionType, dimensionKey, date)` → `Quota?`

**Nenhuma migração necessária.**

---

## ⚠️ Ponto crítico: double-counting de cash

`monthly_balance_snapshots.closingBalance` agrega **todas** as contas financeiras do tenant,
incluindo contas de custódia (`FinancialAccountType.INVESTMENT`). O saldo caixa dessas contas
(dinheiro parado na corretora) já está contabilizado no `bankingClosingBalance`.

`investment_portfolio_quota.total_portfolio_value` pode ou não incluir esse mesmo caixa,
dependendo de como o sistema de cotas modela a posição de cash.

**A implementação deve verificar, antes de ir à produção, se `total_portfolio_value` inclui
o caixa da corretora.** Para isso:
- Buscar um mês com posição de caixa conhecida
- Comparar `total_portfolio_value` com `investmentCashBalance` para o mesmo período

Se incluir, a fórmula correta é:
```
netWorth = bankingClosingBalance + (total_portfolio_value − investmentCashLedgerBalance)
```
Se não incluir (somente posições):
```
netWorth = bankingClosingBalance + total_portfolio_value
```

O campo `isEstimated` deve ser propagado para o frontend para exibição de aviso visual.

---

## Lógica de seleção de ponto por mês

Para cada mês `(year, month)` no range:
- **Meses passados**: entrada com `quota_date` = último dia do mês com registro.
  Usar `MAX(quota_date) GROUP BY YEAR(quota_date), MONTH(quota_date)`.
- **Mês corrente**: último registro disponível (não necessariamente o último dia do calendário).

Meses sem nenhuma entrada (conta não existia ainda, ou sem movimentações):
omitir do resultado — o frontend faz `left join` por `(year, month)`.

---

## Mudanças necessárias

### 1. Backend — Kotlin

#### 1a. Novo domain class
`domain/portfolio/PortfolioMonthlyValue.kt`
```kotlin
data class PortfolioMonthlyValue(
    val year: Int,
    val month: Int,
    val grossValue: Double,
    val isEstimated: Boolean,
)
```

#### 1b. `schema.graphqls` — novo tipo e query

```graphql
"""Monthly portfolio gross value snapshot for a date range (dashboard use)."""
type PortfolioMonthlyValue {
  year: Int!
  month: Int!
  """Market value of all positions. Does NOT include investment account cash balance."""
  grossValue: Float!
  """True when at least one holding used an estimated valuation for this month."""
  isEstimated: Boolean!
}
```

Nova query no bloco de analytics (após `categoryBreakdownForRange`):
```graphql
"""
Monthly portfolio gross value series for the given date range.
One point per month (last available quota_date in the month).
Months with no quota data are omitted — use left-join by (year, month) on the client.
When financialAccountId is omitted, aggregates across all investment accounts.
"""
portfolioNetWorthSeries(
  financialAccountId: ID
  startYear: Int!
  startMonth: Int!
  endYear: Int!
  endMonth: Int!
): [PortfolioMonthlyValue!]!
```

#### 1c. DTO
`graphql/portfolio/PortfolioMonthlyValueDTO.kt`
```kotlin
data class PortfolioMonthlyValueDTO(
    val year: Int,
    val month: Int,
    val grossValue: Double,
    val isEstimated: Boolean,
)
```

#### 1d. `DashboardAnalyticsService.kt` — novo método

```kotlin
@Transactional(readOnly = true)
open fun getPortfolioNetWorthSeries(
    accountId: UUID,
    financialAccountId: UUID?,
    startYear: Int,
    startMonth: Int,
    endYear: Int,
    endMonth: Int,
): List<PortfolioMonthlyValue> {
    val from = LocalDate.of(startYear, startMonth, 1)
    val to   = LocalDate.of(endYear, endMonth, 1).plusMonths(1).minusDays(1)

    // financialAccountId=null → aggregate all INVESTMENT accounts for the tenant
    val rows = if (financialAccountId != null) {
        portfolioQuotaRepository.findByAccountAndDateRange(
            financialAccountId, PortfolioDimensionType.PORTFOLIO, "", from, to
        )
    } else {
        portfolioQuotaRepository.findByTenantAndDateRange(
            accountId, PortfolioDimensionType.PORTFOLIO, "", from, to
        )
        // NOTE: if findByTenantAndDateRange doesn't exist yet, add it (see §1e)
    }

    // For each (year, month), take the last available entry
    return rows
        .groupBy { Pair(it.quotaDate.year, it.quotaDate.monthValue) }
        .entries
        .sortedWith(compareBy({ it.key.first }, { it.key.second }))
        .map { (ym, monthRows) ->
            val last = monthRows.maxBy { it.quotaDate }
            PortfolioMonthlyValue(
                year       = ym.first,
                month      = ym.second,
                grossValue = last.totalPortfolioValue.toDouble(),
                isEstimated = last.hasEstimatedValues,
            )
        }
}
```

#### 1e. Repositório — query tenant-wide (se não existir)

Se `portfolioQuotaRepository` não tiver uma query que agrega por `accountId` (tenant) em vez de
`financialAccountId`, adicionar em `InvestmentPortfolioQuotaEntityRepository`:

```kotlin
@Query("""
    SELECT q FROM InvestmentPortfolioQuotaEntity q
    JOIN FinancialAccountEntity fa ON fa.id = q.financialAccountId
    WHERE fa.accountId = :accountId
      AND q.dimensionType = :dimensionType
      AND q.dimensionKey  = :dimensionKey
      AND q.quotaDate BETWEEN :from AND :to
    ORDER BY q.quotaDate ASC
""")
fun findByTenantAndDateRange(
    accountId: UUID,
    dimensionType: PortfolioDimensionType,
    dimensionKey: String,
    from: LocalDate,
    to: LocalDate,
): List<InvestmentPortfolioQuotaEntity>
```

#### 1f. `TransactionResolver.kt` — novo método
```kotlin
fun portfolioNetWorthSeries(
    financialAccountId: String?,
    startYear: Int, startMonth: Int,
    endYear: Int,   endMonth: Int,
    principal: RequestPrincipal,
): List<PortfolioMonthlyValueDTO> {
    val accountId = requireNotNull(principal.currentAccountId)
    require(startMonth in 1..12); require(endMonth in 1..12)
    return dashboardAnalyticsService.getPortfolioNetWorthSeries(
        accountId          = accountId,
        financialAccountId = financialAccountId?.let(UUID::fromString),
        startYear = startYear, startMonth = startMonth,
        endYear   = endYear,   endMonth   = endMonth,
    ).map { PortfolioMonthlyValueDTO(it.year, it.month, it.grossValue, it.isEstimated) }
}
```

#### 1g. `BankingWiringFactory.kt`
Registrar `portfolioNetWorthSeries` com o mesmo padrão dos outros fetchers de analytics
(após `categoryBreakdownForRange`):
```kotlin
.dataFetcher("portfolioNetWorthSeries", createValidatedDataFetcher { env ->
    env.withPermission(BankingPermissions.BANKING_TRANSACTION_VIEW) { principal ->
        resolver.portfolioNetWorthSeries(
            financialAccountId = env.getArgument("financialAccountId"),
            startYear  = env.getArgument("startYear"),
            startMonth = env.getArgument("startMonth"),
            endYear    = env.getArgument("endYear"),
            endMonth   = env.getArgument("endMonth"),
            principal  = principal,
        )
    }
})
```

#### Compilar
```bash
cd service/kotlin && ./gradlew :banking:compileKotlin
```

---

### 2. Frontend — TypeScript/React

#### 2a. `contracts/graphql/supergraph/supergraph.local.graphql`

Adicionar o novo tipo (antes de `PortfolioPerformance`, seção de tipos BANKING):
```graphql
type PortfolioMonthlyValue
  @join__type(graph: BANKING)
{
  year: Int! @join__field(graph: BANKING)
  month: Int! @join__field(graph: BANKING)
  grossValue: Float! @join__field(graph: BANKING)
  isEstimated: Boolean! @join__field(graph: BANKING)
}
```

Adicionar a query (junto com as outras de analytics):
```graphql
portfolioNetWorthSeries(financialAccountId: ID, startYear: Int!, startMonth: Int!, endYear: Int!, endMonth: Int!): [PortfolioMonthlyValue!]! @join__field(graph: BANKING)
```

#### 2b. `web/src/lib/graphql/operations/banking/queries.ts`

```typescript
export const PORTFOLIO_NET_WORTH_SERIES = gql`
  query PortfolioNetWorthSeries(
    $financialAccountId: ID
    $startYear: Int!
    $startMonth: Int!
    $endYear: Int!
    $endMonth: Int!
  ) {
    portfolioNetWorthSeries(
      financialAccountId: $financialAccountId
      startYear: $startYear
      startMonth: $startMonth
      endYear: $endYear
      endMonth: $endMonth
    ) {
      year
      month
      grossValue
      isEstimated
    }
  }
`;
```

#### 2c. Codegen
```bash
cd web && npx graphql-codegen
```

#### 2d. `useDashboardAnalytics.ts` — novo hook

```typescript
export type PortfolioMonthlyValue = PortfolioNetWorthSeriesQuery['portfolioNetWorthSeries'][number];

export function usePortfolioValueSeries() {
  const range = useDashboardDateRangeParams();
  const { data, loading, error, refetch } = useQuery(PortfolioNetWorthSeriesDocument, {
    variables: range ?? { startYear: 0, startMonth: 1, endYear: 0, endMonth: 1 },
    skip: range === null,
    fetchPolicy: 'cache-and-network',
  });
  return {
    portfolioSeries: data?.portfolioNetWorthSeries ?? [],
    loading,
    error: error ?? null,
    refetch,
  };
}
```

#### 2e. `NetWorthWidget.tsx` — combinar as duas séries

Adicionar `usePortfolioValueSeries()` e combinar por `(year, month)`:

```typescript
const { portfolioSeries, loading: portfolioLoading } = usePortfolioValueSeries();

// Combinar por (year, month) — left join (banking é a série principal)
const chartData = React.useMemo(() => {
  const portfolioMap = new Map(
    portfolioSeries.map(p => [`${p.year}-${p.month}`, p])
  );
  return series.map(point => {
    const portfolio = portfolioMap.get(`${point.year}-${point.month}`);
    const banking = Number.isFinite(point.closingBalance) ? point.closingBalance : null;
    const portfolioValue = portfolio?.grossValue ?? null;
    return {
      label: monthLabelFormatter(point.year, point.month),
      year: point.year,
      month: point.month,
      bankingBalance: banking,
      portfolioValue,
      // total só quando ambos disponíveis
      totalNetWorth:
        banking !== null && portfolioValue !== null
          ? banking + portfolioValue
          : null,
      isEstimated: portfolio?.isEstimated ?? false,
    };
  });
}, [series, portfolioSeries, monthLabelFormatter]);
```

Chart: duas `<Area>` com `stackId="netWorth"` (stacked), plus `loading` combinado:
```typescript
const loading = seriesLoading || portfolioLoading;
```

Área banking — azul (`primary.main`), Área portfólio — roxa/laranja (`secondary.main` ou cor escolhida).

Adicionar aviso visual quando `isEstimated: true` (tooltip com asterisco ou opacity reduzida no ponto).

#### 2f. i18n — `pt.json` / `en.json`

```json
"netWorth": {
  "bankingBalance": "Saldo Bancário",
  "portfolioValue": "Carteira de Investimentos",
  "totalNetWorth": "Patrimônio Total",
  "estimatedNote": "Valor estimado",
  "noData": "Sem dados para o período selecionado."
}
```

#### 2g. TypeScript check
```bash
cd web && npx tsc --noEmit
```

---

## Ordem de implementação

1. **Backend**
   - [ ] `PortfolioMonthlyValue.kt` (domain)
   - [ ] `schema.graphqls` — tipo + query
   - [ ] `PortfolioMonthlyValueDTO.kt`
   - [ ] `DashboardAnalyticsService.kt` — `getPortfolioNetWorthSeries()`
   - [ ] `InvestmentPortfolioQuotaEntityRepository.kt` — `findByTenantAndDateRange` (se necessário)
   - [ ] `TransactionResolver.kt` — novo método
   - [ ] `BankingWiringFactory.kt` — registrar fetcher
   - [ ] `./gradlew :banking:compileKotlin`

2. **Frontend**
   - [ ] `supergraph.local.graphql` — tipo + query
   - [ ] `queries.ts` — `PORTFOLIO_NET_WORTH_SERIES`
   - [ ] `npx graphql-codegen`
   - [ ] `useDashboardAnalytics.ts` — `usePortfolioValueSeries()`
   - [ ] `NetWorthWidget.tsx` — stacked areas
   - [ ] `pt.json` / `en.json` — chaves atualizadas
   - [ ] `npx tsc --noEmit`

---

## Arquivos afetados

| Caminho | Mudança |
|---|---|
| `banking/domain/portfolio/PortfolioMonthlyValue.kt` | NOVO |
| `banking/graphql/portfolio/PortfolioMonthlyValueDTO.kt` | NOVO |
| `banking/resources/graphql/schema.graphqls` | + tipo + query |
| `banking/service/transaction/DashboardAnalyticsService.kt` | + método |
| `banking/repo/portfolio/InvestmentPortfolioQuotaEntityRepository.kt` | + query tenant (se necessário) |
| `banking/graphql/transaction/TransactionResolver.kt` | + método |
| `banking/graphql/config/BankingWiringFactory.kt` | + fetcher |
| `contracts/graphql/supergraph/supergraph.local.graphql` | + tipo + query |
| `web/src/lib/graphql/operations/banking/queries.ts` | + query document |
| `web/src/lib/graphql/operations/banking/queries.generated.ts` | re-gerado |
| `web/src/features/personal-finance/dashboard/hooks/useDashboardAnalytics.ts` | + hook |
| `web/src/features/personal-finance/dashboard/widgets/NetWorthWidget.tsx` | stacked areas |
| `web/src/app/personal-finance/i18n/locales/pt.json` | atualizar chaves netWorth |
| `web/src/app/personal-finance/i18n/locales/en.json` | atualizar chaves netWorth |
