# Sprint: Widget "Evolução do Patrimônio" (Net Worth)

## Contexto e motivação

O dashboard já tem:
- **MonthSummaryKpiWidget** — snapshot pontual do período (saldo, receita, despesa, resultado)
- **CashFlowWidget** — receitas vs. despesas mês a mês (barras empilhadas + linha de `net`)
- **CategoryDonutWidget** — distribuição por categoria

O próximo widget de alto valor é a **evolução do patrimônio líquido bancário ao longo do tempo** — ou seja, ver como o saldo total das contas (checking, savings, etc.) evolui mês a mês. No futuro, esse chart pode ser enriquecido com a posição da carteira de investimentos (Silver track), compondo um patrimônio total real.

---

## Decisão de arquitetura: reutilizar `cashFlowSeries`

A query `cashFlowSeries` já faz um range-scan em `monthly_balance_snapshots` e devolve um ponto por mês. Cada snapshot já contém `closingBalance`. A mudança mais econômica é **adicionar `closingBalance` ao `CashFlowDataPoint`** existente — sem nova query, sem novo hook, sem nova tabela.

O `NetWorthWidget` reutiliza `useCashFlowSeries()` e lê o campo `closingBalance` de cada ponto.

**Trade-off aceito**: o `CashFlowWidget` recebe um campo extra que ignora — custo zero; ganho: zero DB query adicional no dashboard.

**Path para portfólio** (futuro): quando o serviço de investimentos expuser `portfolioNetWorthSeries`, o campo `portfolioValue` pode ser adicionado ao `CashFlowDataPoint` ou servido por um endpoint separado e combinado no front.

---

## Visão do chart

```
Evolução do Patrimônio
┌────────────────────────────────────────────────────────┐
│                                              ╭─────╮   │
│                                   ╭─────────╯     │   │
│                          ╭────────╯               │   │
│               ╭──────────╯                        │   │
│  ╭────────────╯                                   │   │
│ ─┼──────────────────────────────────────────────── │
│  Jan  Fev  Mar  Abr  Mai  Jun  Jul  Ago  Set  Out  │
└────────────────────────────────────────────────────────┘
  Área preenchida = saldo bancário acumulado (closingBalance)
  (futuro: área empilhada com portfólio de investimentos)
```

- **Tipo**: `AreaChart` (Recharts)  
- **Série principal**: `closingBalance` — área com gradiente, cor `primary`  
- **Série futura**: `portfolioValue` — área empilhada, cor `secondary`  
- **Tooltip**: mostra valor formatado em BRL por mês  
- **Zero line**: `ReferenceLine y={0}` para saldos negativos ficarem evidentes  
- **Eixo X**: mesmo padrão do CashFlowWidget (mês/ano abreviado)  
- **Eixo Y**: moeda BRL, mirrored, sem linha de eixo

---

## Mudanças necessárias

### 1. Backend — Kotlin

#### 1a. `domain/transaction/CashFlowDataPoint.kt`

Adicionar `closingBalance`:

```kotlin
data class CashFlowDataPoint(
    val year: Int,
    val month: Int,
    val totalIncome: Double,
    val totalExpenses: Double,
    val closingBalance: Double,          // ← NOVO
) {
    val net: Double get() = totalIncome - totalExpenses
}
```

#### 1b. `graphql/transaction/CashFlowDataPointDTO.kt`

```kotlin
data class CashFlowDataPointDTO(
    val year: Int,
    val month: Int,
    val totalIncome: Double,
    val totalExpenses: Double,
    val net: Double,
    val closingBalance: Double,          // ← NOVO
)
```

#### 1c. `graphql/transaction/TransactionResolver.kt` — método `cashFlowSeries`

No `.map { point -> CashFlowDataPointDTO(...) }`, adicionar:
```kotlin
closingBalance = point.closingBalance,
```

#### 1d. `service/transaction/DashboardAnalyticsService.kt` — `getCashFlowSeries`

No bloco `.map { (ym, rows) -> CashFlowDataPoint(...) }`, adicionar:
```kotlin
closingBalance = rows.sumOf { it.closingBalance }.toDouble(),
```

> `rows.sumOf { it.closingBalance }` soma os `closingBalance` de todas as contas financeiras
> no mesmo mês — correto para o modo agregado. Para o modo por conta (`findByRange`), haverá
> apenas uma linha por mês, então a soma é identidade.

#### 1e. `src/main/resources/graphql/schema.graphqls`

No tipo `CashFlowDataPoint`:
```graphql
type CashFlowDataPoint {
  year: Int!
  month: Int!
  totalIncome: Float!
  totalExpenses: Float!
  net: Float!
  closingBalance: Float!    # ← NOVO
}
```

#### Compilar e testar

```bash
cd service/kotlin
./gradlew :banking:testUnit
```

---

### 2. Frontend — TypeScript/React

#### 2a. `web/src/lib/graphql/operations/banking/queries.ts`

Adicionar `closingBalance` na seleção da query `CASH_FLOW_SERIES`:

```typescript
export const CASH_FLOW_SERIES = gql`
  query CashFlowSeries(
    $financialAccountId: ID
    $startYear: Int!
    $startMonth: Int!
    $endYear: Int!
    $endMonth: Int!
  ) {
    cashFlowSeries(
      financialAccountId: $financialAccountId
      startYear: $startYear
      startMonth: $startMonth
      endYear: $endYear
      endMonth: $endMonth
    ) {
      year
      month
      totalIncome
      totalExpenses
      net
      closingBalance    # ← NOVO
    }
  }
`;
```

#### 2b. `contracts/graphql/supergraph/supergraph.local.graphql`

No bloco do tipo `CashFlowDataPoint`, adicionar o novo campo com `@join__field`:

```graphql
type CashFlowDataPoint
  @join__type(graph: BANKING)
{
  year: Int! @join__field(graph: BANKING)
  month: Int! @join__field(graph: BANKING)
  totalIncome: Float! @join__field(graph: BANKING)
  totalExpenses: Float! @join__field(graph: BANKING)
  net: Float! @join__field(graph: BANKING)
  closingBalance: Float! @join__field(graph: BANKING)    # ← NOVO
}
```

#### 2c. Rodar codegen

```bash
cd web
npx graphql-codegen
```

Verificar que `CashFlowSeriesQuery` agora inclui `closingBalance` no tipo gerado.

#### 2d. i18n — `web/src/app/personal-finance/i18n/locales/pt.json`

Adicionar sob `dashboard.widgets`:

```json
"netWorth": {
  "bankingBalance": "Saldo Bancário",
  "portfolioValue": "Carteira",
  "noData": "Sem dados para o período selecionado."
}
```

E em `defaultTitle` da registry (hardcoded no arquivo): `"Evolução do Patrimônio"`

> Verificar se existe chave `defaultTitle` no namespace ou se é string direta no registry —
> o padrão atual usa string direta em `registry.tsx`.

#### 2e. `web/src/features/personal-finance/dashboard/widgets/NetWorthWidget.tsx` — NOVO

```typescript
'use client';

import React from 'react';
import ShowChartIcon from '@mui/icons-material/ShowChart';
import { Box, Skeleton, Typography, useTheme } from '@mui/material';
import { alpha } from '@mui/material/styles';
import {
  Area,
  AreaChart,
  CartesianGrid,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { getWidgetDefinition } from '../registry';
import { WidgetWrapper } from '../components/WidgetWrapper';
import { useDashboardStore } from '../store';
import { WidgetProps } from '../types';
import { useCashFlowSeries } from '../hooks/useDashboardAnalytics';
import { i18n, useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '@/app/personal-finance/i18n';

// ── helpers ──────────────────────────────────────────────────────────────────

function formatBrl(value: number) {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    maximumFractionDigits: 0,
  }).format(value);
}

function formatBrlFull(value: number) {
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}

type AxisTickProps = { x?: number | string; y?: number | string; payload?: { value?: number | string } };
type XAxisTickProps = { x?: number | string; y?: number | string; payload?: { value?: string; index?: number } };

// ── Widget ───────────────────────────────────────────────────────────────────

export const NetWorthWidget: React.FC<WidgetProps> = ({ widget, dragHandleProps }) => {
  const theme = useTheme();
  const { isEditMode, removeWidget } = useDashboardStore();
  const { series, loading, error, refetch } = useCashFlowSeries();
  const { t } = useTranslation(personalFinanceTranslations);
  const definition = getWidgetDefinition(widget.type);
  const gradientSeed = React.useId().replace(/:/g, '');
  const locale = i18n.resolvedLanguage ?? i18n.language;

  // Label formatter: "Jan/2025"
  const monthLabelFormatter = React.useMemo(() => {
    const fmt = new Intl.DateTimeFormat(locale, { month: 'short' });
    return (year: number, month: number) => {
      const raw = fmt.format(new Date(year, month - 1, 1)).replace(/\.$/, '');
      return `${raw.charAt(0).toLocaleUpperCase(locale) + raw.slice(1)}/${year}`;
    };
  }, [locale]);

  const chartData = React.useMemo(
    () =>
      series.map((point) => ({
        label: monthLabelFormatter(point.year, point.month),
        year: point.year,
        month: point.month,
        bankingBalance: Number.isFinite(point.closingBalance) ? point.closingBalance : null,
        // portfolioValue: 0,  // futuro: virá de outra query
      })),
    [monthLabelFormatter, series],
  );

  // Domain do eixo Y com padding de 10%
  const yDomain = React.useMemo((): readonly [number, number] => {
    let min = Infinity;
    let max = -Infinity;
    for (const row of chartData) {
      const v = row.bankingBalance;
      if (typeof v === 'number' && Number.isFinite(v)) {
        if (v < min) min = v;
        if (v > max) max = v;
      }
    }
    if (!Number.isFinite(min)) return [0, 1];
    const span = Math.max(max - min, 1);
    const pad = span * 0.1;
    return [min - pad, max + pad];
  }, [chartData]);

  const xAxisTick = React.useCallback(
    (props: XAxisTickProps) => {
      const x = Number(props.x ?? 0);
      const y = Number(props.y ?? 0);
      return (
        <text x={x} y={y} dy={16} textAnchor="middle" fill={theme.palette.text.secondary} fontSize={11}>
          {props.payload?.value ?? ''}
        </text>
      );
    },
    [theme.palette.text.secondary],
  );

  const yAxisTick = React.useCallback(
    (props: AxisTickProps) => {
      const x = Number(props.x ?? 0);
      const y = Number(props.y ?? 0);
      const v = Number(props.payload?.value ?? 0);
      const safe = Number.isFinite(v) ? v : 0;
      return (
        <text
          x={x}
          y={y}
          dy={4}
          textAnchor="start"
          fontSize={11}
          fill={safe < 0 ? theme.palette.error.main : theme.palette.text.secondary}
        >
          {formatBrl(safe)}
        </text>
      );
    },
    [theme.palette.error.main, theme.palette.text.secondary],
  );

  const bankingBalanceLabel = t('dashboard.widgets.netWorth.bankingBalance');

  return (
    <WidgetWrapper
      title={widget.title || definition?.defaultTitle || ''}
      icon={<ShowChartIcon />}
      isEditMode={isEditMode}
      onRemove={() => removeWidget(widget.id)}
      onRefresh={() => void refetch()}
      dragHandleProps={dragHandleProps}
    >
      <Box sx={{ p: 2, height: '100%', display: 'flex', flexDirection: 'column', boxSizing: 'border-box' }}>
        {loading ? (
          <Skeleton variant="rectangular" sx={{ borderRadius: 1, height: '100%' }} />
        ) : error ? (
          <Typography color="error" variant="body2">
            {error.message}
          </Typography>
        ) : chartData.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            {t('dashboard.widgets.netWorth.noData')}
          </Typography>
        ) : (
          <Box sx={{ width: '100%', flex: 1, minHeight: 0 }}>
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={chartData} margin={{ top: 8, right: 8, left: 8, bottom: 8 }}>
                <defs>
                  <linearGradient id={`nw-banking-${gradientSeed}`} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor={theme.palette.primary.main} stopOpacity={0.35} />
                    <stop offset="95%" stopColor={theme.palette.primary.main} stopOpacity={0.04} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke={theme.palette.divider} vertical={false} />
                <XAxis
                  dataKey="label"
                  tick={xAxisTick}
                  axisLine={{ stroke: theme.palette.divider }}
                  tickLine={{ stroke: theme.palette.divider }}
                  interval="preserveStartEnd"
                  minTickGap={28}
                  tickMargin={8}
                />
                <YAxis
                  mirror
                  domain={yDomain}
                  tick={yAxisTick}
                  padding={{ top: 12, bottom: 12 }}
                  axisLine={false}
                  tickLine={false}
                />
                <ReferenceLine y={0} stroke={theme.palette.divider} />
                <Tooltip
                  formatter={(value) => {
                    const n = typeof value === 'number' ? value : Number(value ?? 0);
                    return [formatBrlFull(Number.isFinite(n) ? n : 0), bankingBalanceLabel];
                  }}
                  contentStyle={{
                    backgroundColor: theme.palette.background.paper,
                    border: `1px solid ${theme.palette.divider}`,
                    borderRadius: 6,
                  }}
                  labelStyle={{ color: theme.palette.text.secondary, fontSize: 12 }}
                />
                <Area
                  type="monotone"
                  dataKey="bankingBalance"
                  name={bankingBalanceLabel}
                  stroke={alpha(theme.palette.primary.main, 0.9)}
                  strokeWidth={2}
                  fill={`url(#nw-banking-${gradientSeed})`}
                  dot={false}
                  activeDot={{ r: 4, fill: theme.palette.primary.main, strokeWidth: 0 }}
                  connectNulls={false}
                  isAnimationActive={false}
                />
              </AreaChart>
            </ResponsiveContainer>
          </Box>
        )}
      </Box>
    </WidgetWrapper>
  );
};
```

#### 2f. `web/src/features/personal-finance/dashboard/registry.tsx`

Adicionar import e entrada no array `AVAILABLE_WIDGETS`:

```typescript
import ShowChartIcon from '@mui/icons-material/ShowChart';
import { NetWorthWidget } from './widgets/NetWorthWidget';

// No array AVAILABLE_WIDGETS, após 'cash-flow':
{
  type: 'net-worth',
  defaultTitle: 'Evolução do Patrimônio',
  description: 'Saldo bancário acumulado mês a mês.',
  component: NetWorthWidget,
  defaultSize: {
    w: { xs: 12, md: 12 },
    h: { xs: 4, md: 4 },
  },
  icon: <ShowChartIcon />,
},
```

#### 2g. TypeScript check

```bash
cd web
npx tsc --noEmit
```

Deve retornar 0 erros.

---

## Ordem de implementação (para a AI Agent)

1. **Backend** (Kotlin):
   - [ ] `CashFlowDataPoint.kt` — adicionar `closingBalance: Double`
   - [ ] `CashFlowDataPointDTO.kt` — adicionar `closingBalance: Double`
   - [ ] `TransactionResolver.kt` — incluir `closingBalance = point.closingBalance` no map
   - [ ] `DashboardAnalyticsService.kt` — incluir `closingBalance = rows.sumOf { it.closingBalance }.toDouble()` no map
   - [ ] `schema.graphqls` — adicionar `closingBalance: Float!` ao tipo `CashFlowDataPoint`
   - [ ] Compilar: `./gradlew :banking:compileKotlin` (de `service/kotlin/`)

2. **Frontend** (TypeScript/React):
   - [ ] `contracts/graphql/supergraph/supergraph.local.graphql` — adicionar `closingBalance` ao tipo `CashFlowDataPoint`
   - [ ] `queries.ts` — adicionar `closingBalance` à seleção de `CASH_FLOW_SERIES`
   - [ ] Rodar codegen: `npx graphql-codegen` (de `web/`)
   - [ ] `pt.json` — adicionar chaves `dashboard.widgets.netWorth.*`
   - [ ] Criar `NetWorthWidget.tsx` (código acima)
   - [ ] `registry.tsx` — registrar o novo widget
   - [ ] `npx tsc --noEmit` — verificar 0 erros

---

## Extensão futura: portfólio de investimentos

Quando o Silver track expuser dados de carteira, a extensão natural é:

**Opção A** — Adicionar `portfolioValue: Float!` ao `CashFlowDataPoint` (mesmo padrão do `closingBalance`).

**Opção B** — Criar uma nova query `netWorthSeries` que já combina banking + investimentos no backend e devolve `bankingBalance`, `portfolioValue`, `totalNetWorth` por mês.

A Opção B é mais limpa se o dado de portfólio vier de um serviço diferente (ex: investments subgraph), pois a composição pode ser feita no gateway ou em um BFF. Nesse caso o `NetWorthWidget` passaria a usar `useNetWorthSeries()` em vez de `useCashFlowSeries()`, e os campos de income/expense seriam removidos do `AreaChart`.

No chart a evolução seria:
```
Área azul  = saldo bancário (closingBalance)
Área roxa  = carteira de investimentos (portfolioValue)
Linha bold = total combinado
```

---

## Arquivos afetados (resumo)

| Caminho | Mudança |
|---|---|
| `banking/src/main/kotlin/.../domain/transaction/CashFlowDataPoint.kt` | + campo `closingBalance` |
| `banking/src/main/kotlin/.../graphql/transaction/CashFlowDataPointDTO.kt` | + campo `closingBalance` |
| `banking/src/main/kotlin/.../graphql/transaction/TransactionResolver.kt` | + mapeamento de `closingBalance` |
| `banking/src/main/kotlin/.../service/transaction/DashboardAnalyticsService.kt` | + `closingBalance` no map de `getCashFlowSeries` |
| `banking/src/main/resources/graphql/schema.graphqls` | + `closingBalance: Float!` em `CashFlowDataPoint` |
| `contracts/graphql/supergraph/supergraph.local.graphql` | + `closingBalance` com `@join__field` |
| `web/src/lib/graphql/operations/banking/queries.ts` | + `closingBalance` na seleção |
| `web/src/lib/graphql/operations/banking/queries.generated.ts` | re-gerado pelo codegen |
| `web/src/app/personal-finance/i18n/locales/pt.json` | + chaves `dashboard.widgets.netWorth.*` |
| `web/src/features/personal-finance/dashboard/widgets/NetWorthWidget.tsx` | NOVO |
| `web/src/features/personal-finance/dashboard/registry.tsx` | + entrada `net-worth` |
