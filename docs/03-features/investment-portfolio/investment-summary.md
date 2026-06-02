# Investment Summary

Componente de resumo do portfólio de investimentos, análogo ao `MonthSummary` do módulo de transações bancárias. Exibe as métricas consolidadas da carteira — valor total, rendimento, alocação por grupo e saldo de caixa — em uma superfície compacta e interativa posicionada acima da tabela de holdings.

---

## Contexto e referências

O `MonthSummary` é o padrão de referência:
- Valor principal em destaque (h3, 2.3rem)
- Linhas de métricas com ícone + label + valor (`SummaryMetricRow`)
- Breakdown com `LinearProgress` + drawer para lista completa
- Estados de loading (skeletons) e erro (retry)

O `InvestmentSummary` segue o mesmo padrão visual e arquitetural, adaptado para o domínio de investimentos, com uma query dedicada no backend — `investmentPortfolioSummary` — análoga ao `monthSummary`.

| Arquivo | Relevância |
|---------|-----------|
| [MonthSummary.tsx](../../web/src/app/personal-finance/finance-transactions/summary/MonthSummary.tsx) | Padrão visual e arquitetural a seguir |
| [MonthSummaryQueryService.kt](../../service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/transaction/MonthSummaryQueryService.kt) | Padrão de service: single account / aggregate paths |
| [InvestmentQueryService.kt](../../service/kotlin/banking/src/main/kotlin/io/github/salomax/neotool/banking/service/investment/InvestmentQueryService.kt) | Carregamento e valuation de holdings |
| [investmentTableHelpers.ts](../../web/src/app/personal-finance/investments/components/investmentTableHelpers.ts) | `AssetGroupKey`, `ASSET_GROUP_ORDER`, mapeamento AssetType → grupo |
| [InvestmentTable.tsx](../../web/src/app/personal-finance/investments/components/InvestmentTable.tsx) | Contexto de integração |
| [page.tsx](../../web/src/app/personal-finance/investments/page.tsx) | `selectedAccountId`, `investmentAccounts` |

---

## Posicionamento na página

```
┌─ Sidebar ──────────┐  ┌─ Main Content ─────────────────────────────────────┐
│                    │  │                                                     │
│ [XP Investimentos] │  │  ┌─────────────────────────────────────────────┐   │
│ [BTG Pactual     ] │  │  │  InvestmentSummary                          │   │
│ [Consolidado     ] │  │  └─────────────────────────────────────────────┘   │
│                    │  │                                                     │
│                    │  │  [busca]              [Registrar ▼]                │
│                    │  │                                                     │
│                    │  │  ┌─ AssetGroupCard: Caixa ──────────────────────┐  │
│                    │  │  ├─ AssetGroupCard: Renda Variável ─────────────┤  │
│                    │  │  └─ ...                                          │  │
└────────────────────┘  └─────────────────────────────────────────────────────┘
```

---

## Layout do componente

```
┌─────────────────────────────────────────────────────────────┐
│  Portfólio Total                                            │
│  R$ 1.250.432,50                          [estimado ⚠]     │  ← h3, 2.3rem
│                                                             │
│  ──────────────────────────────────────────────────────    │
│  📈  Rendimento         +R$ 87.320,00        +7,5%         │  ← SummaryMetricRow
│  💼  Total investido     R$ 1.162.000,00                   │  ← SummaryMetricRow
│  💵  Caixa disponível      R$ 12.112,50                    │  ← SummaryMetricRow
│  ──────────────────────────────────────────────────────    │
│                                                             │
│  Alocação                                                   │
│  ▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░   Renda Variável    45%  ↗ +5,2%  │  ← GroupProgressRow
│  ░░░░▓▓▓▓░░░░░░░░░░░░░░   Renda Fixa Banc.  28%  → +2,1%  │
│  ░░░░░░░░▓▓▓░░░░░░░░░░░   Tesouro Direto    18%  → +0,9%  │
│  ░░░░░░░░░░░▓▓░░░░░░░░░   Caixa             10%  —        │
│                            [Ver todos os grupos]           │
└─────────────────────────────────────────────────────────────┘
```

---

## Decisões de design

| # | Questão | Decisão |
|---|---------|---------|
| 1 | Dados calculados no frontend ou backend? | **Backend** — query `investmentPortfolioSummary`, análoga ao `monthSummary`. Centraliza valuation e gain/loss onde as regras já existem. |
| 2 | Cache/materialização? | **Não** no V1 — holdings têm valuation dinâmica (preço de mercado muda constantemente). Calcular on-read. Reavaliar se performance for problema. |
| 3 | Dois caminhos (conta única / consolidado)? | **Sim** — idêntico ao padrão do `monthSummary`. |
| 4 | Rendimento bruto ou líquido? | **Bruto** no V1 (`grossGain`). Líquido requer acumulação cuidadosa por tipo de ativo (IR progressivo) — V2. |
| 5 | Grupo de cash no breakdown? | **Sim** — grupo especial `cash` alimentado pelo `cashBalance`, sem AssetType. |

---

## Contrato GraphQL

```graphql
type InvestmentPortfolioSummary {
  # Posição atual
  totalHoldingsCount: Int!
  totalCostBasis: Float!           # Σ (quantity × averageCost)
  totalGrossValue: Float!          # Σ grossValue dos holdings ativos
  totalNetValue: Float!            # Σ netValue (após IR/IOF provisionados)
  totalIncomeTax: Float!           # Σ incomeTax provisionado
  totalIof: Float!                 # Σ IOF provisionado

  # Rendimento
  grossGain: Float!                # totalGrossValue − totalCostBasis
  grossGainPercent: Float!         # grossGain / totalCostBasis
  netGain: Float!                  # totalNetValue − totalCostBasis
  netGainPercent: Float!

  # Caixa
  cashBalance: Float!              # investmentCashBalance
  totalPortfolioValue: Float!      # totalGrossValue + cashBalance

  # Breakdown por grupo de ativo
  byAssetGroup: [AssetGroupSummary!]!

  # Meta de valuation
  hasEstimatedValues: Boolean!     # true se algum holding é UNAVAILABLE ou ESTIMATED
  valuationReferenceDate: Date     # data de referência mais recente entre os holdings
  currencyCode: String!
}

type AssetGroupSummary {
  groupKey: String!                # variableIncome, bankFixedIncome, cash, etc.
  label: String!                   # Nome localizado do grupo
  count: Int!                      # Número de holdings (0 para cash)
  totalCostBasis: Float!
  totalGrossValue: Float!
  grossGainPercent: Float          # null quando costBasis = 0
  allocationPercent: Float!        # totalGrossValue / totalPortfolioValue
  isCash: Boolean!
}

# Adicionado em Query type:
investmentPortfolioSummary(financialAccountId: ID): InvestmentPortfolioSummary!
```

Atualizar em:
- `schema.graphqls` (banking service)
- `contracts/graphql/subgraphs/banking/schema.graphqls`
- supergraph

---

## Requisitos

### RF-S01 — Valor total em destaque
Exibir `totalPortfolioValue` como métrica primária (h3, ~2.3rem, fontWeight 700), no mesmo estilo do `closingBalance` do `MonthSummary`. Quando `hasEstimatedValues = true`, exibir chip "estimado" com ícone de aviso.

### RF-S02 — Linhas de métricas (SummaryMetricRow)
Três linhas seguindo o padrão `SummaryMetricRow`:
1. **Rendimento** — `grossGain` com sinal e cor (verde/vermelho) + `grossGainPercent` em badge
2. **Total investido** — `totalCostBasis`
3. **Caixa disponível** — `cashBalance`; vermelho quando negativo

### RF-S03 — Breakdown de alocação (GroupProgressRow)
Grupos com `count > 0` + grupo de caixa quando `cashBalance != 0`, ordenados por `allocationPercent` desc. Cada linha: ícone + label + `LinearProgress` + percentual + `gainPercent`. Máximo 5 inline; botão "Ver todos os grupos" abre drawer.

### RF-S04 — Loading
`Skeleton` para todos os valores. Layout não bloqueia.

### RF-S05 — Erro
Estado com ícone, mensagem e botão retry. Padrão do `MonthSummary`.

### RF-S06 — Conta única vs. consolidado
- `financialAccountId = null`: agrega todos os holdings de contas INVESTMENT/BENEFIT
- `financialAccountId != null`: apenas os holdings da conta selecionada

### RF-S07 — Interatividade com a tabela *(V2)*
Clicar em grupo no summary filtra/destaca o grupo na `InvestmentTable`. Props `onGroupClick` e `activeGroupFilter` opcionais, ignoradas no V1.

---

## Fora do escopo — V1

- Rendimento por período (mês, ano, desde início)
- Gráfico de pizza/donut de alocação
- Comparação com benchmark (CDI, IBOV)
- Rendimento líquido por grupo (IR progressivo por tipo de ativo)
- Performance histórica (série temporal)

---

## Backlog de implementação

### Backend

**[S-BE-01] `InvestmentPortfolioSummaryService.kt`**

Segue o padrão do `MonthSummaryQueryService` com dois caminhos:

```kotlin
@Service
class InvestmentPortfolioSummaryService(
    private val holdingRepository: InvestmentHoldingRepository,
    private val investmentQueryService: InvestmentQueryService,  // valuation de holdings
    private val transactionRepository: TransactionRepository,    // cashBalance
    private val financialAccountRepository: FinancialAccountRepository,
) {
    fun getSummary(
        financialAccountId: UUID?,
        accountId: UUID,
    ): InvestmentPortfolioSummaryDTO =
        if (financialAccountId != null)
            getSummaryForAccount(financialAccountId, accountId)
        else
            getAggregateSummary(accountId)

    // Conta única: carrega holdings + valuation + cash, agrega
    private fun getSummaryForAccount(
        financialAccountId: UUID,
        accountId: UUID,
    ): InvestmentPortfolioSummaryDTO { ... }

    // Consolidado: itera todas as contas INVESTMENT/BENEFIT do accountId
    // chama getSummaryForAccount para cada uma e soma os campos numéricos
    private fun getAggregateSummary(accountId: UUID): InvestmentPortfolioSummaryDTO { ... }
}
```

Lógica de agregação:
- Carregar holdings ativos via `holdingRepository.findActiveBy...`
- Reutilizar `InvestmentQueryService.toHoldingDTO` (ou método equivalente) para obter `grossValue` valorado
- Calcular `cashBalance` via `transactionRepository.sumLedgerBalance`
- Mapear `AssetType → AssetGroupKey` usando constante definida em `AssetGroupMapping` (mesma lógica do frontend `investmentTableHelpers.ts`)
- Agrupar e somar métricas por grupo
- Construir grupo especial `cash` com `isCash = true`

**[S-BE-02] `AssetGroupMapping.kt`**

Constante compartilhada mapeando `AssetType → AssetGroupKey` (enum ou string), replicando o `ASSET_TYPES_BY_GROUP` do frontend. Evita divergência entre backend e frontend nos grupos.

**[S-BE-03] `InvestmentPortfolioSummaryDTO.kt`**

DTO GraphQL espelhando o schema. Campos `Float` (Double em Kotlin) para compatibilidade GraphQL.

**[S-BE-04] Resolver em `InvestmentResolver.kt`**

```kotlin
fun investmentPortfolioSummary(
    financialAccountId: String?,
    principal: RequestPrincipal,
): InvestmentPortfolioSummaryDTO {
    val accountId = requireNotNull(principal.currentAccountId)
    return investmentPortfolioSummaryService.getSummary(
        financialAccountId = financialAccountId?.let(UUID::fromString),
        accountId = accountId,
    )
}
```

**[S-BE-05] Wiring em `BankingWiringFactory.kt`**

Data fetcher `investmentPortfolioSummary` com permissão `BANKING_INVESTMENT_READ`.

---

### Frontend

**[S-UI-01] Query `INVESTMENT_PORTFOLIO_SUMMARY`**

```typescript
// queries.ts
export const INVESTMENT_PORTFOLIO_SUMMARY = gql`
  query InvestmentPortfolioSummary($financialAccountId: ID) {
    investmentPortfolioSummary(financialAccountId: $financialAccountId) {
      totalHoldingsCount
      totalCostBasis
      totalGrossValue
      totalNetValue
      totalIncomeTax
      totalIof
      grossGain
      grossGainPercent
      netGain
      netGainPercent
      cashBalance
      totalPortfolioValue
      hasEstimatedValues
      valuationReferenceDate
      currencyCode
      byAssetGroup {
        groupKey
        label
        count
        totalCostBasis
        totalGrossValue
        grossGainPercent
        allocationPercent
        isCash
      }
    }
  }
`
```

**[S-UI-02] `GroupProgressRow.tsx`**

Sub-componente análogo ao `CategoryProgressRow` do `MonthSummary`.

Props:
```typescript
interface GroupProgressRowProps {
  groupKey: string
  label: string
  count: number
  allocationPercent: number
  gainPercent?: number | null
  isCash: boolean
  active?: boolean
  onClick?: () => void
}
```

Layout: ícone do grupo (reutilizar lógica de `AssetTypeChip` ou criar `AssetGroupIcon`) + label + count + `LinearProgress` + percentual de alocação + gain colorido.

**[S-UI-03] `InvestmentSummary.tsx`**

```typescript
interface InvestmentSummaryProps {
  financialAccountId?: string | null
  // Não recebe data como prop — faz a própria query
  onGroupClick?: (groupKey: string) => void
  activeGroupFilter?: string | null
}
```

Estrutura interna:
- `useQuery(INVESTMENT_PORTFOLIO_SUMMARY, { variables: { financialAccountId } })`
- Seção 1: `totalPortfolioValue` em destaque + chip "estimado"
- Seção 2: três `SummaryMetricRow` — Rendimento, Total investido, Caixa
- `Divider`
- Seção 3: label "Alocação" + lista de `GroupProgressRow` (máx. 5)
  - Ordenados por `allocationPercent` desc
  - Botão "Ver todos os grupos" → abre drawer quando `byAssetGroup.length > 5`
- Drawer: lista completa de `GroupProgressRow`
- Loading: `Skeleton` para todos os valores
- Erro: estado com `onRetry`

**[S-UI-04] Integração em `page.tsx`**

- Renderizar `<InvestmentSummary financialAccountId={selectedAccountId} />` acima da `InvestmentTable`
- Passar `onGroupClick` e `activeGroupFilter` quando RF-S07 for implementado (V2)

**[S-UI-05] Codegen**

Rodar codegen após schema atualizado.

**[S-UI-06] Internacionalização**

```
investments.summary.totalPortfolio            — "Portfólio Total"
investments.summary.grossGain                 — "Rendimento"
investments.summary.totalInvested             — "Total investido"
investments.summary.cashBalance               — "Caixa disponível"
investments.summary.estimated                 — "estimado"
investments.summary.allocation                — "Alocação"
investments.summary.viewAllGroups             — "Ver todos os grupos"
investments.summary.drawer.title              — "Grupos de ativos"
investments.summary.error.title               — "Não foi possível calcular o resumo"
investments.summary.error.description         — "Verifique sua conexão e tente novamente."
```

---

## Em aberto

- **Rendimento líquido vs. bruto por grupo**: exibir bruto no V1. Líquido requer lógica de IR progressivo por tipo de ativo — V2.
- **Performance por período** ("este mês", "este ano", "desde o início"): requer nova query de performance histórica agregada — V2.
- **Gráfico de alocação**: pizza ou donut — V2 como seção opcional abaixo do breakdown.
- **Cache/materialização**: se a query `investmentPortfolioSummary` se provar lenta com carteiras grandes (100+ holdings), considerar snapshot materializado similar ao `MonthlyBalanceSnapshot` — avaliar após métricas de produção.
