# Dashboard /personal-finance — Design v1

## Contexto

A página `/personal-finance` é a home do módulo de finanças pessoais. Atualmente exibe uma dashboard com 3 widgets mock (Net Worth, Recent Transactions, Spending Breakdown) sobre infraestrutura já funcional (react-grid-layout, Zustand store com persist, drag & drop).

Este documento registra as decisões de design tomadas em sessão de benchmark e planejamento (Abril 2026) e o plano de implementação em sprints.

---

## Benchmark resumido

| App | Destaques relevantes |
|-----|---------------------|
| **Monarch Money** | Dashboard customizável (drag & drop), scrollable home com widgets; date range global afeta todos os charts; Reports separados por seção |
| **Copilot Money** | Design minimalista; tabs por seção; "Free to Spend" em destaque; Cash Flow comparativo mensal |
| **YNAB** | Foco em orçamento proativo; Spending Report com pie + bar chart; Targets com progress bars |
| **Gorila** | Referência BR para investimentos; rentabilidade, P&L, alocação por ativo, calendário dividendos; R$250bi+ consolidados |
| **Mobills** | Referência BR para controle pessoal; dashboard customizável; gráficos mensais e anuais de cash flow |

---

## Decisões de Design

### 1. Date Range (Filtro Global)

- **Default:** Mês atual (`currentMonth`)
- **Opções pré-definidas:**
  ```
  Mês atual | Últ. 3 meses | Últ. 6 meses | Últ. 12 meses | Este ano | 3 anos | Todo o período
  ```
- O date range selecionado é **global** — todos os widgets da home respondem a ele
- Para charts de tendência (ex: Fluxo de Caixa), o range controla quantos meses o eixo X exibe
- **Impacto no código:** `DateRangeOption` em `types.ts` precisa adicionar `currentMonth` e `3y`; default no store muda de `90d` para `currentMonth`

### 2. Layout

- **Monarch-style:** home scrollável única com widgets empilhados
- Sem tabs na home por enquanto — tabs podem ser introduzidas futuramente para seções pesadas (Investimentos, Relatórios)
- Drag & drop já está implementado
- Próxima evolução de customização: **show/hide por widget** (toggle nas settings do widget)

### 3. Widgets — Tier 1 (Sprint 1-2, dados disponíveis)

| Widget | Tipo visual | Fonte de dados |
|--------|-------------|----------------|
| **KPI Cards** — Saldo / Receitas / Despesas / Líquido | 4 cards em row | `MonthSummary` GraphQL |
| **Fluxo de Caixa** | Bar chart ±0 (verde acima / vermelho abaixo) | Array de `MonthSummary` |
| **Despesas por Categoria** | Donut chart | Transactions grouped by category |
| **Receitas por Categoria** | Donut chart | Transactions grouped by category |

### 4. Investimentos na Home — Tier 2 (Sprint 3)

O widget de investimentos deve responder rapidamente:
- Quanto tenho investido no total?
- Como está o balanceamento por classes de ativos?
- Qual o rendimento por classe?

**Decisão visual:** **Donut chart** (não Treemap) na home — mais compacto, leitura imediata.

```
[ Total investido ]  [ Maior classe % ]  [ Rendimento total ]
[       Donut — alocação por classe de ativo       ]
```

> O **Treemap interativo** (hierárquico: classes → ativos individuais) vai na **aba de Investimentos**, abaixo do grid existente.

### 5. Evolução Patrimonial — Tier 2 (Sprint 3)

- Area chart mostrando evolução do patrimônio ao longo do tempo
- Fonte: `MonthlyBalanceSnapshot` (banking) + posições de investimentos
- Requer agregação cross-domain — implementar após widgets Tier 1 estáveis

### 6. Itens Adiados para v2

- **Fatura do Cartão de Crédito** — domínio existe no backend, UI adiada
- **Merchants top gastos** — requer campo merchant bem populado
- **Projeções** — requer modelo preditivo ou configuração manual
- **Metas financeiras** — novo domínio

---

## Layout Visual Proposto (home)

```
┌─────────────────────────────────────────────────────────┐
│  Quick Actions: [Movimentações] [Investimentos] [Preços] │  ← já existe
├───────────────────────────────────┬─────────────────────┤
│  Date range picker (global)       │  [Personalizar]      │
├──────────┬──────────┬─────────────┴──────────────────────┤
│  Saldo   │ Receitas │ Despesas  │   Líquido               │  ← KPI cards
├──────────┴──────────┴───────────┴────────────────────────┤
│  Fluxo de Caixa (Bar ±0, N meses conforme date range)    │  ← full width
├──────────────────────────┬──────────────────────────────┤
│  Despesas por Categoria  │  Receitas por Categoria       │
│       (Donut)            │       (Donut)                 │
├──────────────────────────┴──────────────────────────────┤
│  Visão de Investimentos                                   │
│  [ Total ]  [ Maior classe ]  [ Rendimento ]              │
│  [       Donut — alocação por classe       ]              │
├─────────────────────────────────────────────────────────┤
│  Evolução Patrimonial (Area chart)            ← fase 2  │
└─────────────────────────────────────────────────────────┘
```

---

## Estado Atual do Código

### Infraestrutura (já feita)

- **Store:** `web/src/features/personal-finance/dashboard/store.ts` — Zustand + persist; `addWidget`, `removeWidget`, `toggleWidgetVisibility`, `updateLayouts`, `isEditMode`
- **Grid:** react-grid-layout com breakpoints `lg / md / sm / xs / xxs`
- **Registry:** `registry.tsx` — `AVAILABLE_WIDGETS[]` extensível
- **Controls:** `DashboardControls.tsx` — date range picker + edit mode + quick action cards

### Widgets Mock (precisam de dados reais)

| Widget atual | Status | O que fazer |
|---|---|---|
| `NetWorthWidget` | Mock (hardcoded R$125k) | Substituir por KPI cards reais |
| `TransactionHistoryWidget` | Mock | Avaliar manter ou substituir por Fluxo de Caixa |
| `SpendingBreakdownWidget` | Mock (progress bars) | Substituir por Donut de categorias |

### Gaps a corrigir antes dos novos widgets

1. `DateRangeOption` não tem `currentMonth` nem `3y`
2. Default do store é `90d` — deve ser `currentMonth`
3. Date range está no store mas **não é passado para os widgets** — widgets precisam consumir `useDashboardStore().dateRange`

---

## Plano de Implementação

### Sprint 1 — Date range correto + KPI Cards reais

1. Adicionar `currentMonth` e `3y` ao tipo `DateRangeOption` e ao Select do `DashboardControls`
2. Atualizar i18n (pt.json + en.json) com os novos labels
3. Mudar default do store para `currentMonth`
4. Criar hook `useDashboardDateRange()` que converte `DateRangeOption` → `{ startDate, endDate }`
5. Implementar `MonthSummaryKpiWidget` — 4 KPI cards com dados reais via GraphQL `monthSummary`
6. Implementar `CashFlowWidget` — Bar chart usando array de `monthSummary` do período selecionado
7. Implementar `CategoryDonutWidget` — Donut expense + income com dados de categorias

### Sprint 2 — Investimentos na home

8. Implementar `InvestmentOverviewWidget` — 3 KPI cards + Donut por classe de ativo
9. Adicionar Treemap interativo na página `/personal-finance/investments` abaixo do grid

### Sprint 3 — Evolução Patrimonial

10. Implementar `PatrimonioEvolutionWidget` — Area chart usando `MonthlyBalanceSnapshot` + investimentos
11. Agregar saldo banking + valor de carteira por mês

---

## Dependências de Backend

| Widget | Query necessária | Status |
|--------|-----------------|--------|
| KPI Cards / Fluxo de Caixa | `monthSummary(financialAccountId, year, month)` | ✅ Existe |
| Category Donut | Query de categorias por período + conta | ⚠️ Verificar se existe |
| Investment Overview | `investmentPortfolioSummary` por classe | ⚠️ Verificar |
| Evolução Patrimonial | `monthlyBalanceSnapshots` por range | ⚠️ Verificar se exposto no GraphQL |

---

## Referências

- [MonthSummary design](./phase-2-monthly-summary-snapshots.md)
- `web/src/features/personal-finance/dashboard/store.ts`
- `web/src/features/personal-finance/dashboard/types.ts`
- `web/src/features/personal-finance/dashboard/registry.tsx`
