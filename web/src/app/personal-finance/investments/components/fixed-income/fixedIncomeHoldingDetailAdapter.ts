"use client";

import {
  AssetType,
  Indexer,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
} from "@/lib/graphql/types/__generated__/graphql";
import { getHoldingValueOrigin, type ValueOriginLabel } from "@/shared/utils/holdingValueOrigin";
import type { FixedIncomeHoldingDetailQuery } from "./api/queries.generated";

type Translator = (key: string, options?: Record<string, unknown>) => string;

type Holding = NonNullable<FixedIncomeHoldingDetailQuery["investmentHolding"]>;
type Transaction = FixedIncomeHoldingDetailQuery["investmentTransactions"][number];
type HoldingPerformance = NonNullable<FixedIncomeHoldingDetailQuery["holdingPerformance"]>;

const INDEXER_LABELS: Partial<Record<Indexer, string>> = {
  [Indexer.Cdi]: "CDI",
  [Indexer.Di]: "DI",
  [Indexer.Ipca]: "IPCA+",
  [Indexer.IgpM]: "IGP-M",
  [Indexer.IgpDi]: "IGP-DI",
  [Indexer.Inpc]: "INPC",
  [Indexer.Tr]: "TR",
  [Indexer.Selic]: "SELIC",
  [Indexer.PreFixado]: "Prefixado",
  [Indexer.Outros]: "Outro",
};

export interface FixedIncomeSummaryMetricViewModel {
  id: string;
  label: string;
  value: number | null;
  tone?: "auto" | "positive" | "negative" | "neutral";
}

export interface FixedIncomeTimelineItemViewModel {
  id: string;
  title: string;
  tradeDateLabel: string;
  settlementDateLabel: string | null;
  amount: number;
  netAmount: number | null;
  incomeTax: number | null;
  iof: number | null;
  direction: InvestmentTransactionDirection;
}

export interface FixedIncomeHoldingDetailViewModel {
  title: string;
  subtitle: string;
  issuerLabel: string;
  accountLabel: string;
  currencyCode: string;
  principal: number | null;
  currentCostBasis: number | null;
  grossValue: number | null;
  netValue: number | null;
  incomeTax: number | null;
  iof: number | null;
  grossGain: number | null;
  netGain: number | null;
  grossGainPercent: number | null;
  netGainPercent: number | null;
  indexerLabel: string;
  issueDateLabel: string;
  gracePeriodDateLabel: string;
  maturityDateLabel: string;
  taxExemptLabel: string;
  statusLabel: string | null;
  valueOrigin: ValueOriginLabel;
  summaryMetrics: FixedIncomeSummaryMetricViewModel[];
  timelineItems: FixedIncomeTimelineItemViewModel[];
  chartPoints: Array<{
    timestamp: string;
    isProjected: boolean;
    investedPrincipal: number | null;
    grossValue: number | null;
    netValue: number | null;
  }>;
  supportsProjection: boolean;
}

function getCurrentLocale(): string {
  return Intl.DateTimeFormat().resolvedOptions().locale || "pt-BR";
}

function formatDate(value: unknown, locale: string): string {
  if (!value) return "—";

  const raw = String(value);
  const localDateMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(raw);
  if (localDateMatch) {
    const [, year, month, day] = localDateMatch;
    const date = new Date(Number(year), Number(month) - 1, Number(day));
    return date.toLocaleDateString(locale);
  }

  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleDateString(locale);
}

function formatAssetType(assetType: AssetType, t: Translator): string {
  return t(`investments.assetTypes.${assetType}`);
}

function formatIndexer(holding: Holding, locale: string): string {
  if (!holding.indexer) return "—";

  if (holding.indexer === Indexer.PreFixado) {
    if (holding.fixedAnnualRate == null) return INDEXER_LABELS[Indexer.PreFixado] || "Prefixado";
    const annualRate = holding.fixedAnnualRate * 100;
    return `${annualRate.toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}% a.a.`;
  }

  const label = INDEXER_LABELS[holding.indexer] || holding.indexer;
  if (holding.indexerPercentage == null) return label;

  const percentValue = holding.indexerPercentage * 100;
  if (!Number.isFinite(percentValue)) return label;

  const formattedPercent = percentValue.toLocaleString(locale, {
    minimumFractionDigits: 1,
    maximumFractionDigits: 2,
  });
  return `${label} ${formattedPercent}%`;
}

function formatTransactionType(type: InvestmentTransactionType, t: Translator): string {
  return t(`investments.detailDrawer.transactionTypes.${type}`);
}

function buildIssuerLabel(holding: Holding): string {
  return (
    holding.financialAccount?.issuer?.name ??
    holding.financialAccount?.issuerName ??
    holding.asset?.issuerName ??
    "—"
  );
}

function buildAccountLabel(holding: Holding): string {
  return holding.financialAccount?.name ?? "—";
}

export function buildFixedIncomeHoldingDetailViewModel(
  holding: Holding,
  transactions: FixedIncomeHoldingDetailQuery["investmentTransactions"],
  performance: HoldingPerformance | null | undefined,
  t: Translator,
  locale = getCurrentLocale(),
): FixedIncomeHoldingDetailViewModel {
  const currencyCode = holding.asset.currencyCode || "BRL";
  const summary = performance?.summary;
  const principal = typeof summary?.investedPrincipal === "number" ? summary.investedPrincipal : null;
  const currentCostBasis = typeof summary?.currentCostBasis === "number" ? summary.currentCostBasis : null;
  const grossValue = typeof summary?.grossValue === "number" ? summary.grossValue : null;
  const netValue = typeof summary?.netValue === "number" ? summary.netValue : null;
  const incomeTax = typeof summary?.incomeTax === "number" ? summary.incomeTax : null;
  const iof = typeof summary?.iof === "number" ? summary.iof : null;
  const grossGain = typeof summary?.grossGain === "number" ? summary.grossGain : null;
  const netGain = typeof summary?.netGain === "number" ? summary.netGain : null;
  const grossGainPercent = typeof summary?.grossGainPercent === "number" ? summary.grossGainPercent : null;
  const netGainPercent = typeof netGain === "number" && typeof principal === "number" && principal > 0 ? netGain / principal : null;
  const typeLabel = formatAssetType(holding.asset.assetType, t);
  const issuerLabel = buildIssuerLabel(holding);
  const accountLabel = buildAccountLabel(holding);

  const summaryMetrics: FixedIncomeSummaryMetricViewModel[] = [
    {
      id: "principal",
      label: t("investments.detailDrawer.metrics.principal"),
      value: principal,
      tone: "neutral",
    },
    {
      id: "currentCostBasis",
      label: t("investments.detailDrawer.metrics.currentCostBasis"),
      value: currentCostBasis,
      tone: "neutral",
    },
    {
      id: "gross",
      label: t("investments.detailDrawer.metrics.grossValue"),
      value: grossValue,
      tone: "neutral",
    },
    {
      id: "net",
      label: t("investments.detailDrawer.metrics.netValue"),
      value: netValue,
      tone: "neutral",
    },
    {
      id: "incomeTax",
      label: t("investments.detailDrawer.metrics.incomeTax"),
      value: incomeTax,
      tone: "negative",
    },
    {
      id: "iof",
      label: t("investments.detailDrawer.metrics.iof"),
      value: iof,
      tone: "negative",
    },
    {
      id: "grossGain",
      label: t("investments.detailDrawer.metrics.grossGain"),
      value: grossGain,
      tone: grossGain == null ? "neutral" : grossGain >= 0 ? "positive" : "negative",
    },
  ];

  const timelineItems = [...transactions]
    .sort((a, b) => new Date(String(b.tradeDate)).getTime() - new Date(String(a.tradeDate)).getTime())
    .map((transaction) => ({
      id: transaction.id,
      title: formatTransactionType(transaction.type, t),
      tradeDateLabel: formatDate(transaction.tradeDate, locale),
      settlementDateLabel: transaction.settlementDate ? formatDate(transaction.settlementDate, locale) : null,
      amount: transaction.grossValue,
      netAmount: transaction.netValue,
      incomeTax: transaction.incomeTax,
      iof: transaction.iof,
      direction: transaction.direction,
    }));

  const chartPoints = (performance?.points ?? []).map((point) => ({
    timestamp: String(point.timestamp),
    isProjected: point.isProjected,
    investedPrincipal: point.investedPrincipal,
    grossValue:
      holding.isTaxExempt && point.grossValue == null && typeof point.netValue === "number"
        ? point.netValue
        : point.grossValue,
    netValue: point.netValue,
  }));

  return {
    title: holding.asset.name,
    subtitle: typeLabel,
    issuerLabel,
    accountLabel,
    currencyCode,
    principal,
    currentCostBasis,
    grossValue,
    netValue,
    incomeTax,
    iof,
    grossGain,
    netGain,
    grossGainPercent,
    netGainPercent,
    indexerLabel: formatIndexer(holding, locale),
    issueDateLabel: formatDate(holding.issueDate, locale),
    gracePeriodDateLabel: formatDate(holding.gracePeriodDate, locale),
    maturityDateLabel: formatDate(holding.maturityDate, locale),
    taxExemptLabel: holding.isTaxExempt
      ? t("investments.detailDrawer.taxExempt.yes")
      : t("investments.detailDrawer.taxExempt.no"),
    statusLabel: holding.isExpired
      ? t("investments.detailDrawer.status.expired")
      : null,
    valueOrigin: getHoldingValueOrigin({
      valuationSource: performance?.valuationSource ?? holding.valuationSource,
      source: holding.source,
    }),
    summaryMetrics,
    timelineItems,
    chartPoints,
    supportsProjection: Boolean(performance?.capabilities.supportsProjection),
  };
}
