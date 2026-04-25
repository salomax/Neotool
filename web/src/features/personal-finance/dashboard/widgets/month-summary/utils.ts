const BRL_CURRENCY_FORMATTER = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const PERCENT_FORMATTER = new Intl.NumberFormat('pt-BR', {
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
});

export function formatCurrency(value: number) {
  return BRL_CURRENCY_FORMATTER.format(value);
}

export function formatPercent(value: number | null | undefined) {
  if (value == null || !Number.isFinite(value)) return '—';
  return `${PERCENT_FORMATTER.format(value)}%`;
}

export function splitVolumePercentages(absIncome: number, absExpenses: number, totalVolume: number) {
  if (totalVolume <= 0) return { incomePct: 0, expensesPct: 0 };
  const incomePct = Math.round((absIncome / totalVolume) * 100);
  const roundedExpensesPct = Math.round((absExpenses / totalVolume) * 100);
  const expensesPct = incomePct + roundedExpensesPct === 100 ? roundedExpensesPct : 100 - incomePct;
  return { incomePct, expensesPct };
}

export function toPercentageGainRatio(returnPct: number | null | undefined): number | null {
  if (returnPct == null || !Number.isFinite(returnPct)) return null;
  // DashboardPeriodSummary.returnPct is percentage points (e.g. 12.5 means 12.5%).
  // PercentageGain expects a ratio (e.g. 0.125).
  return returnPct / 100;
}
