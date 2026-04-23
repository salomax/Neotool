# Dashboard Sprint 1 — Backlog Detalhado para Implementação

## Objetivo

Substituir os 3 widgets mock da `/personal-finance` por widgets com dados reais, e corrigir o date range picker para refletir as opções decididas.

Resultado esperado ao final do Sprint 1:
- Date range com `currentMonth` como default e opções corretas
- Widget de KPI cards (Saldo, Receitas, Despesas, Líquido) com dados reais
- Widget de Fluxo de Caixa (bar chart ±0) com dados reais
- Widget de Categorias (donut de despesas + donut de receitas) com dados reais

---

## Contexto técnico antes de começar

### Stack
- **Framework:** Next.js (App Router), React, TypeScript, MUI v5
- **GraphQL client:** Apollo Client — usar `useQuery` de `@apollo/client/react`
- **Charts:** Recharts `^3.8.1`; componentes wrapper em `src/shared/components/ui/data-display/`
  - `BarChart` — wrapper completo com axes, series, reference lines, Cell coloring
  - `LineChart`, `AreaChart` — wrappers similares
  - **Não existe** `PieChart` / `DonutChart` wrapper — usar Recharts diretamente
- **Estado global:** Zustand store em `web/src/features/personal-finance/dashboard/store.ts`
- **i18n:** `useTranslation` de `@/shared/i18n`; arquivos em `web/src/app/personal-finance/i18n/locales/`
- **Widget pattern:** todo widget recebe `WidgetProps` e usa `WidgetWrapper` como container

### Arquivos-chave
```
web/src/features/personal-finance/dashboard/
  types.ts          ← DateRangeOption, WidgetProps, WidgetDefinition
  store.ts          ← useDashboardStore (Zustand)
  registry.tsx      ← AVAILABLE_WIDGETS[], getWidgetDefinition()
  components/
    DashboardControls.tsx   ← date range Select + edit mode
    WidgetWrapper.tsx       ← container padrão de todo widget
  widgets/
    NetWorthWidget.tsx          ← SUBSTITUIR (mock)
    TransactionHistoryWidget.tsx ← REMOVER do registry
    SpendingBreakdownWidget.tsx  ← SUBSTITUIR (mock)

web/src/lib/graphql/operations/banking/queries.generated.ts
  ← MonthSummaryDocument, MonthSummaryQuery, MonthSummaryQueryVariables

web/src/app/personal-finance/i18n/locales/pt.json  ← traduções PT
web/src/app/personal-finance/i18n/locales/en.json  ← traduções EN
```

### GraphQL — query disponível

A query `monthSummary` já existe no arquivo gerado. Variáveis opcionais — sem `financialAccountId` retorna visão consolidada de todas as contas.

```typescript
// Tipo da query (já gerado em queries.generated.ts)
type MonthSummaryQueryVariables = {
  financialAccountId?: string;   // omitir = visão consolidada
  year?: number;                  // omitir = mês atual
  month?: number;                 // omitir = mês atual
};

type MonthSummaryQuery = {
  monthSummary: {
    year: number;
    month: number;
    totalIncome: number;
    totalExpenses: number;
    closingBalance: number;
    openingBalance: number;
    transfersIn: number;
    transfersOut: number;
    projectedClosingBalance: number | null;
    isCurrentMonth: boolean;
    isFutureMonth: boolean;
    expensesByCategory: CategoryTotal[];
    incomeByCategory: CategoryTotal[];
    tagsSummary: TagTotal[];
  }
};

type CategoryTotal = {
  uncategorized: boolean;
  totalAmount: number;
  transactionCount: number;
  percentage: number;
  category: { id: string; name: string; icon: string | null; color: string | null } | null;
};
```

**Atenção:** a API é por mês — não existe query de range. Para widgets que precisam de múltiplos meses (Fluxo de Caixa), o widget deve chamar `useQuery` N vezes em paralelo. Ver Tarefa 5.

---

## Tarefas

---

### Tarefa 1 — Atualizar `DateRangeOption` e o store

**Arquivo:** `web/src/features/personal-finance/dashboard/types.ts`

Adicionar `currentMonth` e `3y` ao tipo:

```typescript
// ANTES:
export type DateRangeOption = '90d' | '6m' | 'ytd' | '1y' | 'all' | 'custom';

// DEPOIS:
export type DateRangeOption = 'currentMonth' | '3m' | '6m' | '1y' | '3y' | 'all' | 'custom';
```

> Nota: `90d` e `ytd` são removidos. `90d` → `3m` (semântica mais clara). `ytd` (ano até hoje) é removido por não ser parte das opções decididas.

**Arquivo:** `web/src/features/personal-finance/dashboard/store.ts`

1. Mudar o default de `dateRange.option` de `'90d'` para `'currentMonth'`
2. Atualizar o `migrate` para cobrir os valores removidos:

```typescript
migrate: (persistedState: any) => {
  const option = persistedState?.state?.dateRange?.option;
  const legacyOptions = ['7d', '30d', '90d', 'ytd'];
  if (legacyOptions.includes(option)) {
    return {
      ...persistedState,
      state: {
        ...persistedState.state,
        dateRange: { ...persistedState.state.dateRange, option: 'currentMonth' },
      },
    };
  }
  return persistedState;
},
```

3. Incrementar `version` de `2` para `3` para forçar migration.

---

### Tarefa 2 — Atualizar i18n

**Arquivo:** `web/src/app/personal-finance/i18n/locales/pt.json`

Dentro de `dashboard.timeRange.options`, substituir as chaves para refletir o novo `DateRangeOption`:

```json
"options": {
  "currentMonth": "Mês atual",
  "3m": "Últimos 3 meses",
  "6m": "Últimos 6 meses",
  "1y": "Último ano",
  "3y": "Últimos 3 anos",
  "all": "Todo o período",
  "custom": "Personalizado"
}
```

**Arquivo:** `web/src/app/personal-finance/i18n/locales/en.json`

```json
"options": {
  "currentMonth": "Current month",
  "3m": "Last 3 months",
  "6m": "Last 6 months",
  "1y": "Last year",
  "3y": "Last 3 years",
  "all": "All time",
  "custom": "Custom"
}
```

---

### Tarefa 3 — Atualizar `DashboardControls` com as novas opções

**Arquivo:** `web/src/features/personal-finance/dashboard/components/DashboardControls.tsx`

1. Atualizar `timeRangeLabelByOption` para usar as novas chaves (remover `90d` e `ytd`, adicionar `currentMonth` e `3y`).

2. Atualizar o `<Select>` — substituir os `<MenuItem>` para bater com o novo `DateRangeOption`:

```tsx
<MenuItem value="currentMonth">{t('dashboard.timeRange.options.currentMonth')}</MenuItem>
<MenuItem value="3m">{t('dashboard.timeRange.options.3m')}</MenuItem>
<MenuItem value="6m">{t('dashboard.timeRange.options.6m')}</MenuItem>
<MenuItem value="1y">{t('dashboard.timeRange.options.1y')}</MenuItem>
<MenuItem value="3y">{t('dashboard.timeRange.options.3y')}</MenuItem>
<MenuItem value="all">{t('dashboard.timeRange.options.all')}</MenuItem>
```

---

### Tarefa 4 — Criar hook `useDashboardDateRange`

**Novo arquivo:** `web/src/features/personal-finance/dashboard/hooks/useDashboardDateRange.ts`

Este hook converte `DateRangeOption` em uma lista de `{ year, month }` para uso nas queries de `monthSummary`.

```typescript
import { useMemo } from 'react';
import { useDashboardStore } from '../store';
import { DateRangeOption } from '../types';

export interface MonthYear {
  year: number;
  month: number; // 1-12
}

/** Retorna os meses cobertos pelo date range selecionado, do mais antigo para o mais recente. */
export function useDashboardDateRange(): MonthYear[] {
  const { option } = useDashboardStore((s) => s.dateRange);
  return useMemo(() => buildMonthRange(option), [option]);
}

function buildMonthRange(option: DateRangeOption): MonthYear[] {
  const now = new Date();
  const currentYear = now.getFullYear();
  const currentMonth = now.getMonth() + 1; // getMonth() é 0-based

  if (option === 'currentMonth') {
    return [{ year: currentYear, month: currentMonth }];
  }

  const monthCount =
    option === '3m' ? 3 :
    option === '6m' ? 6 :
    option === '1y' ? 12 :
    option === '3y' ? 36 :
    option === 'all' ? 60 : // fallback: 5 anos
    1;

  const months: MonthYear[] = [];
  for (let i = monthCount - 1; i >= 0; i--) {
    const d = new Date(currentYear, currentMonth - 1 - i, 1);
    months.push({ year: d.getFullYear(), month: d.getMonth() + 1 });
  }
  return months;
}
```

---

### Tarefa 5 — Widget: `MonthSummaryKpiWidget` (KPI Cards)

**Novo arquivo:** `web/src/features/personal-finance/dashboard/widgets/MonthSummaryKpiWidget.tsx`

Exibe 4 KPI cards em linha: Saldo atual, Receitas, Despesas, Líquido (receitas - despesas).

Para `currentMonth`, faz 1 query. Para ranges maiores, agrega totais de todos os meses do range.

```typescript
import React from 'react';
import { useQuery } from '@apollo/client/react';
import { Box, Grid, Skeleton, Typography, alpha, useTheme } from '@mui/material';
import AccountBalanceIcon from '@mui/icons-material/AccountBalance';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import TrendingDownIcon from '@mui/icons-material/TrendingDown';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import { WidgetWrapper } from '../components/WidgetWrapper';
import { WidgetProps } from '../types';
import { useDashboardStore } from '../store';
import { useDashboardDateRange } from '../hooks/useDashboardDateRange';
import { MonthSummaryDocument, MonthSummaryQuery } from '@/lib/graphql/operations/banking/queries.generated';

const formatCurrency = (value: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);
```

**Lógica de dados:**

- Para `currentMonth` (1 mês): chamar `useQuery(MonthSummaryDocument)` sem variáveis → pega mês atual consolidado
- Para ranges com N meses: chamar N queries em paralelo usando um hook auxiliar `useMultiMonthSummaries` (ver abaixo)
- Agregar: `totalIncome = SUM(months[].totalIncome)`, `totalExpenses = SUM(months[].totalExpenses)`, `closingBalance = months[last].closingBalance`

**Hook auxiliar para múltiplos meses:**

```typescript
// web/src/features/personal-finance/dashboard/hooks/useMultiMonthSummaries.ts
import { useQuery } from '@apollo/client/react';
import { MonthSummaryDocument } from '@/lib/graphql/operations/banking/queries.generated';
import { MonthYear } from './useDashboardDateRange';

/**
 * Chama monthSummary para cada mês da lista e retorna os resultados.
 * ATENÇÃO: a lista de meses deve ser estável entre renders para não violar as rules of hooks.
 * Sempre passe a lista completa; o Apollo deduplica requests iguais via cache.
 */
export function useMultiMonthSummaries(months: MonthYear[]) {
  // Limitado a 60 chamadas (5 anos) — suficiente para todos os ranges
  const q0  = useQuery(MonthSummaryDocument, { variables: months[0]  ? { year: months[0].year,  month: months[0].month  } : undefined, skip: !months[0]  });
  const q1  = useQuery(MonthSummaryDocument, { variables: months[1]  ? { year: months[1].year,  month: months[1].month  } : undefined, skip: !months[1]  });
  // ... até q35 (3 anos = 36 meses máx no Sprint 1; para 'all' limitar a 60)
  // Para simplicidade de implementação, criar um loop até MAX_MONTHS = 36
  // e usar skip: index >= months.length

  // Retornar array de { data, loading, error } para cada posição
}
```

> **Alternativa mais simples:** usar `@apollo/client` `useQuery` dentro de um `useMemo` não é possível (rules of hooks). A abordagem correta é criar um componente filho por mês que faz sua própria query, e um componente pai que agrega os resultados via Context ou callback. Ou usar Apollo's `client.query()` imperativo dentro de um `useEffect`. Escolha a abordagem que preferir — o resultado deve ser um array de `MonthSummaryData`.

**Layout dos KPI cards:**

```tsx
// 4 cards em row, responsivo: xs=6 (2 colunas), md=3 (4 colunas)
<Grid container spacing={2}>
  <Grid item xs={6} md={3}>
    <KpiCard label="Saldo atual" value={closingBalance} icon={<AccountBalanceIcon />} color="primary" />
  </Grid>
  <Grid item xs={6} md={3}>
    <KpiCard label="Receitas" value={totalIncome} icon={<TrendingUpIcon />} color="success" />
  </Grid>
  <Grid item xs={6} md={3}>
    <KpiCard label="Despesas" value={totalExpenses} icon={<TrendingDownIcon />} color="error" />
  </Grid>
  <Grid item xs={6} md={3}>
    <KpiCard label="Líquido" value={totalIncome - totalExpenses} icon={<SwapHorizIcon />}
      color={totalIncome - totalExpenses >= 0 ? "success" : "error"} />
  </Grid>
</Grid>
```

**`KpiCard` (componente interno):**
```tsx
// Sem Paper separado — usar Box com border e borderRadius
// Exibir: ícone colorido, label em caption, valor em h5/h6 bold
// Skeleton durante loading
```

**Layout no registry (tamanho):** `w: { xs: 12, md: 12 }, h: { xs: 2, md: 2 }` — full width, altura pequena.

---

### Tarefa 6 — Widget: `CashFlowWidget` (Bar chart ±0)

**Novo arquivo:** `web/src/features/personal-finance/dashboard/widgets/CashFlowWidget.tsx`

Exibe um bar chart com barras agrupadas (receita e despesa) por mês, ou barras únicas do líquido (receita - despesa) — **verde acima de 0, vermelho abaixo**.

**Decisão de implementação:** usar o **net mensal** (totalIncome - totalExpenses) como valor único por barra, com cor dinâmica via `<Cell>`. Mais legível no espaço de um widget.

**Usando o componente `BarChart` existente:**

```typescript
import { BarChart } from '@/shared/components/ui/data-display/BarChart';
```

O `BarChart` em `src/shared/components/ui/data-display/BarChart.tsx` suporta `referenceLines` (linha no y=0), `Cell` para colorir barras individualmente, e `axes` com `format: 'currency'`.

**Estrutura dos dados:**

```typescript
// Transformar o array de MonthSummary em:
const chartData = months.map((summary) => ({
  label: `${String(summary.month).padStart(2, '0')}/${summary.year}`,  // "04/2026"
  net: summary.totalIncome - summary.totalExpenses,
}));
```

**Props do `BarChart`:**

```tsx
<BarChart
  data={chartData}
  xKey="label"
  height={220}
  axes={[{
    id: 'left',
    side: 'left',
    format: 'currency',
    currency: 'BRL',
  }]}
  series={[{
    id: 'net',
    name: 'Líquido',
    dataKey: 'net',
    yAxisId: 'left',
  }]}
  referenceLines={[{ y: 0, yAxisId: 'left' }]}
  // Colorir barras via Cell: verde se net >= 0, vermelho se < 0
  // Ver o componente BarChart — ele aceita `colors` por série ou `Cell` internamente
  // Se não suportar Cell por item via props, usar a prop `colors` ou sobrescrever via children
  locale="pt-BR"
/>
```

> **Atenção:** verificar se o `BarChart` wrapper existente suporta coloração dinâmica por item (Cell). Se não suportar, usar `RechartsBarChart` diretamente de `recharts` com `<Cell fill={net >= 0 ? green : red} />`. O arquivo `BarChart.tsx` usa `Cell` internamente — inspecionar se expõe uma prop para isso ou se precisa de workaround.

**Estados:**
- `loading`: exibir `<Skeleton variant="rectangular" height={220} />`
- `empty` (nenhum mês com dados): exibir mensagem "Nenhum dado para o período"
- `error`: exibir mensagem de erro com botão retry

**Layout no registry:** `w: { xs: 12, md: 12 }, h: { xs: 4, md: 4 }` — full width.

---

### Tarefa 7 — Widget: `CategoryDonutWidget` (Donut de categorias)

**Novo arquivo:** `web/src/features/personal-finance/dashboard/widgets/CategoryDonutWidget.tsx`

Exibe dois donuts lado a lado: Despesas por Categoria (esquerda) e Receitas por Categoria (direita).

**Fonte de dados:**
- Para `currentMonth`: usar `expensesByCategory` e `incomeByCategory` do `monthSummary` (já incluídos na `MonthSummaryDocument`)
- Para ranges multi-mês: agregar somando `totalAmount` por `category.id` entre os meses

**Não existe wrapper de PieChart/Donut** — usar Recharts diretamente:

```tsx
import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts';
```

**Estrutura de dados para o Pie:**

```typescript
const expenseSlices = expensesByCategory.map((item) => ({
  name: item.uncategorized ? 'Sem categoria' : item.category!.name,
  value: item.totalAmount,
  color: item.category?.color ?? theme.palette.action.disabled,
}));
```

**Componente DonutChart interno (reutilizável):**

```tsx
interface DonutChartProps {
  data: Array<{ name: string; value: number; color: string }>;
  title: string;
  totalLabel: string;
  totalValue: number;
  loading?: boolean;
}
```

- `innerRadius={60}` / `outerRadius={90}` para o estilo donut
- Label central com o total (usando `customized` label ou elemento absoluto sobre o Pie)
- Tooltip com valor formatado em BRL
- Legend abaixo com nome + valor

**Layout widget:** dois donuts em `Grid container`:
- `xs=12 md=6` cada — em mobile ficam empilhados, em desktop lado a lado
- Altura total do widget: `h: { xs: 8, md: 5 }` no registry

**Empty state:** se `expensesByCategory` está vazio, exibir "Nenhuma despesa categorizada no período" (usar string de i18n já existente: `monthSummary.noCategorizedExpenses`).

---

### Tarefa 8 — Atualizar o Registry

**Arquivo:** `web/src/features/personal-finance/dashboard/registry.tsx`

1. Remover `TransactionHistoryWidget` do `AVAILABLE_WIDGETS` (widget irrelevante para o dashboard analítico)
2. Substituir `NetWorthWidget` por `MonthSummaryKpiWidget`
3. Substituir `SpendingBreakdownWidget` por `CategoryDonutWidget`
4. Adicionar `CashFlowWidget`

```typescript
export const AVAILABLE_WIDGETS: WidgetDefinition[] = [
  {
    type: 'month-summary-kpi',
    defaultTitle: 'Resumo do Período',
    description: 'Saldo, receitas, despesas e resultado líquido.',
    component: MonthSummaryKpiWidget,
    defaultSize: { w: { xs: 12, md: 12 }, h: { xs: 2, md: 2 } },
    icon: <AccountBalanceIcon />,
  },
  {
    type: 'cash-flow',
    defaultTitle: 'Fluxo de Caixa',
    description: 'Comparativo de receitas e despesas por mês.',
    component: CashFlowWidget,
    defaultSize: { w: { xs: 12, md: 12 }, h: { xs: 4, md: 4 } },
    icon: <BarChartIcon />,
  },
  {
    type: 'category-donut',
    defaultTitle: 'Categorias',
    description: 'Distribuição de despesas e receitas por categoria.',
    component: CategoryDonutWidget,
    defaultSize: { w: { xs: 12, md: 12 }, h: { xs: 8, md: 5 } },
    icon: <PieChartIcon />,
  },
];
```

---

### Tarefa 9 — Atualizar DEFAULT_WIDGETS e DEFAULT_LAYOUTS no store

**Arquivo:** `web/src/features/personal-finance/dashboard/store.ts`

Atualizar `DEFAULT_WIDGETS` e `DEFAULT_LAYOUTS` para usar os novos tipos:

```typescript
const DEFAULT_WIDGETS: WidgetInstance[] = [
  { id: 'month-summary-kpi-1', type: 'month-summary-kpi', isVisible: true },
  { id: 'cash-flow-1', type: 'cash-flow', isVisible: true },
  { id: 'category-donut-1', type: 'category-donut', isVisible: true },
];

const DEFAULT_LAYOUTS = {
  lg: [
    { i: 'month-summary-kpi-1', x: 0, y: 0,  w: 12, h: 2 },
    { i: 'cash-flow-1',         x: 0, y: 2,  w: 12, h: 4 },
    { i: 'category-donut-1',    x: 0, y: 6,  w: 12, h: 5 },
  ],
  md: [
    { i: 'month-summary-kpi-1', x: 0, y: 0,  w: 12, h: 2 },
    { i: 'cash-flow-1',         x: 0, y: 2,  w: 12, h: 4 },
    { i: 'category-donut-1',    x: 0, y: 6,  w: 12, h: 5 },
  ],
  sm: [
    { i: 'month-summary-kpi-1', x: 0, y: 0,  w: 12, h: 2 },
    { i: 'cash-flow-1',         x: 0, y: 2,  w: 12, h: 4 },
    { i: 'category-donut-1',    x: 0, y: 6,  w: 12, h: 8 },
  ],
  xs: [
    { i: 'month-summary-kpi-1', x: 0, y: 0,  w: 12, h: 3 },
    { i: 'cash-flow-1',         x: 0, y: 3,  w: 12, h: 4 },
    { i: 'category-donut-1',    x: 0, y: 7,  w: 12, h: 8 },
  ],
  xxs: [
    { i: 'month-summary-kpi-1', x: 0, y: 0,  w: 12, h: 3 },
    { i: 'cash-flow-1',         x: 0, y: 3,  w: 12, h: 4 },
    { i: 'category-donut-1',    x: 0, y: 7,  w: 12, h: 8 },
  ],
};
```

---

### Tarefa 10 — Deletar widgets obsoletos

Deletar os arquivos que não serão mais usados:
- `web/src/features/personal-finance/dashboard/widgets/NetWorthWidget.tsx`
- `web/src/features/personal-finance/dashboard/widgets/TransactionHistoryWidget.tsx`
- `web/src/features/personal-finance/dashboard/widgets/SpendingBreakdownWidget.tsx`

---

## Ordem de execução recomendada

```
1 → 2 → 3   (types + i18n + controls — sem dependências de dados)
4           (hook useDashboardDateRange — independente)
5           (KpiWidget — depende de 4)
6           (CashFlowWidget — depende de 4)
7           (CategoryDonutWidget — depende de 4)
8 → 9       (registry + store defaults — depende de 5, 6, 7)
10          (deletar obsoletos — só após 8 e 9)
```

---

## Checklist de validação

- [ ] Selecionando "Mês atual" → KPI cards mostram dados do mês corrente
- [ ] Selecionando "Últimos 3 meses" → Fluxo de Caixa exibe 3 barras
- [ ] Selecionando "Últimos 6 meses" → Fluxo de Caixa exibe 6 barras
- [ ] Barras positivas (receita > despesa) aparecem em verde
- [ ] Barras negativas (despesa > receita) aparecem em vermelho
- [ ] Donut de despesas mostra os slices por categoria com cores e labels
- [ ] Donut de receitas mostra os slices por categoria
- [ ] Sem dados no período → empty states corretos em cada widget
- [ ] Loading states (Skeleton) aparecem durante fetch
- [ ] Drag & drop ainda funciona após as mudanças no registry e store
- [ ] Reset layout restaura o novo DEFAULT_LAYOUT
- [ ] Estado persistido com opções antigas (90d, ytd) é migrado para currentMonth
- [ ] TypeScript compila sem erros

---

## Notas adicionais

**Sobre `toggleWidgetVisibility`:** o `store.ts` já tem essa função e `WidgetWrapper` tem o item "Hide Widget" no menu. Para o Sprint 1 não é necessário nenhum trabalho adicional aqui.

**Sobre `financialAccountId`:** todos os widgets do Sprint 1 usam visão consolidada (sem `financialAccountId`). Filtro por conta é feature futura.

**Sobre i18n nos widgets:** usar as strings existentes em `pt.json` `financeTransactions.monthSummary.*` para labels como "Receitas", "Despesas", "Saldo". Não duplicar strings.
