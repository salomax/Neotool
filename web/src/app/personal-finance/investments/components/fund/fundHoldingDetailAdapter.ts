"use client";

import {
  AssetType,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
} from "@/lib/graphql/types/__generated__/graphql";
import { getHoldingValueOrigin, type ValueOriginLabel } from "@/shared/utils/holdingValueOrigin";
import type { FundHoldingDetailQuery } from "./api/queries.generated";
import { isSameInvestmentEventAndSettlementDay } from "../timelineSettlementDay";

type Translator = (key: string, options?: Record<string, unknown>) => string;

type Holding = NonNullable<FundHoldingDetailQuery["investmentHolding"]>;
type Transaction = FundHoldingDetailQuery["investmentTransactions"][number];
type HoldingPerformance = NonNullable<FundHoldingDetailQuery["holdingPerformance"]>;
type InvestmentHoldingPerformance = FundHoldingDetailQuery["investmentHoldingPerformance"];
type PerformancePoint = HoldingPerformance["points"][number];

export interface FundSummaryMetricViewModel {
  id: string;
  label: string;
  value: number | null;
  tone?: "auto" | "positive" | "negative" | "neutral";
}

export interface FundTimelineItemViewModel {
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

export interface FundHoldingDetailViewModel {
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
  xirr: number | null;
  xirrSinceLabel: string | null;
  totalReturnAbsolute: number | null;
  dividendsReceived: number | null;
  valueOrigin: ValueOriginLabel;
  summaryMetrics: FundSummaryMetricViewModel[];
  timelineItems: FundTimelineItemViewModel[];
  chartPoints: Array<{
    timestamp: string;
    isProjected: boolean;
    investedPrincipal: number | null;
    grossValue: number | null;
    netValue: number | null;
  }>;
  supportsProjection: boolean;
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

function formatTransactionType(type: InvestmentTransactionType, t: Translator): string {
  return t(`investments.detailDrawer.transactionTypes.${type}`);
}

function buildIssuerLabel(holding: Holding): string {
  return holding.financialAccount?.issuer?.name ?? holding.asset?.issuerName ?? "—";
}

function buildAccountLabel(holding: Holding): string {
  return holding.financialAccount?.name ?? "—";
}

function costBasisFromHolding(holding: Holding): number | null {
  const q = holding.quantity;
  const ac = holding.averageCost;
  if (
    typeof q === "number" &&
    typeof ac === "number" &&
    Number.isFinite(q) &&
    Number.isFinite(ac) &&
    q > 0 &&
    ac >= 0
  ) {
    return q * ac;
  }
  return null;
}

/** Sum of purchase inflows when the performance summary (invested principal) is unavailable. */
function sumPurchaseInflowGross(transactions: FundHoldingDetailQuery["investmentTransactions"]): number | null {
  let sum = 0;
  let any = false;
  for (const tx of transactions) {
    if (
      tx.direction === InvestmentTransactionDirection.Inflow &&
      tx.type === InvestmentTransactionType.Purchase &&
      typeof tx.grossValue === "number" &&
      Number.isFinite(tx.grossValue)
    ) {
      sum += tx.grossValue;
      any = true;
    }
  }
  return any ? sum : null;
}

export function buildFundHoldingDetailViewModel(
  holding: Holding,
  transactions: FundHoldingDetailQuery["investmentTransactions"],
  performance: HoldingPerformance | null | undefined,
  investmentHoldingPerformance: InvestmentHoldingPerformance | null | undefined,
  t: Translator,
  locale: string,
): FundHoldingDetailViewModel {
  const currencyCode = holding.asset.currencyCode || "BRL";
  const summary = performance?.summary;

  let principal: number | null =
    typeof summary?.investedPrincipal === "number" ? summary.investedPrincipal : null;
  if (principal == null) {
    principal = costBasisFromHolding(holding);
  }
  if (principal == null) {
    principal = sumPurchaseInflowGross(transactions);
  }

  let currentCostBasis: number | null =
    typeof summary?.currentCostBasis === "number" ? summary.currentCostBasis : null;
  if (currentCostBasis == null) {
    currentCostBasis = principal;
  }

  let grossValue: number | null = typeof summary?.grossValue === "number" ? summary.grossValue : null;
  if (grossValue == null && typeof holding.grossValue === "number" && Number.isFinite(holding.grossValue)) {
    grossValue = holding.grossValue;
  }

  let netValue: number | null = typeof summary?.netValue === "number" ? summary.netValue : null;
  if (netValue == null && typeof holding.netValue === "number" && Number.isFinite(holding.netValue)) {
    netValue = holding.netValue;
  }
  if (netValue == null && grossValue != null) {
    netValue = grossValue;
  }

  let incomeTax: number | null = typeof summary?.incomeTax === "number" ? summary.incomeTax : null;
  if (incomeTax == null && typeof holding.incomeTax === "number" && Number.isFinite(holding.incomeTax)) {
    incomeTax = holding.incomeTax;
  }

  let iof: number | null = typeof summary?.iof === "number" ? summary.iof : null;
  if (iof == null && typeof holding.iof === "number" && Number.isFinite(holding.iof)) {
    iof = holding.iof;
  }

  let grossGain: number | null = typeof summary?.grossGain === "number" ? summary.grossGain : null;
  if (grossGain == null && grossValue != null && principal != null) {
    grossGain = grossValue - principal;
  }

  let netGain: number | null = typeof summary?.netGain === "number" ? summary.netGain : null;
  if (netGain == null && netValue != null && principal != null) {
    netGain = netValue - principal;
  }

  let grossGainPercent: number | null =
    typeof summary?.grossGainPercent === "number" ? summary.grossGainPercent : null;
  if (grossGainPercent == null && grossGain != null && principal != null && principal > 0) {
    grossGainPercent = grossGain / principal;
  }

  let netGainPercent: number | null = null;
  if (typeof netGain === "number" && typeof principal === "number" && principal > 0) {
    netGainPercent = netGain / principal;
  } else if (typeof investmentHoldingPerformance?.totalReturn === "number") {
    netGainPercent = investmentHoldingPerformance.totalReturn;
  }
  const xirr = typeof investmentHoldingPerformance?.xirr === "number" ? investmentHoldingPerformance.xirr : null;
  const xirrSinceLabel = investmentHoldingPerformance?.xirrSince
    ? formatDate(investmentHoldingPerformance.xirrSince, locale)
    : null;
  const totalReturnAbsolute =
    typeof investmentHoldingPerformance?.totalReturnAbsolute === "number"
      ? investmentHoldingPerformance.totalReturnAbsolute
      : null;
  const dividendsReceived =
    typeof investmentHoldingPerformance?.dividendsReceived === "number"
      ? investmentHoldingPerformance.dividendsReceived
      : null;

  const subtitle = formatAssetType(holding.asset.assetType, t);
  const issuerLabel = buildIssuerLabel(holding);
  const accountLabel = buildAccountLabel(holding);

  const summaryMetrics: FundSummaryMetricViewModel[] = [
    { id: "principal", label: t("investments.detailDrawer.metrics.principal"), value: principal, tone: "neutral" },
    { id: "currentCostBasis", label: t("investments.detailDrawer.metrics.currentCostBasis"), value: currentCostBasis, tone: "neutral" },
    { id: "gross", label: t("investments.detailDrawer.metrics.grossValue"), value: grossValue, tone: "neutral" },
    { id: "net", label: t("investments.detailDrawer.metrics.netValue"), value: netValue, tone: "neutral" },
    {
      id: "incomeTax",
      label: t("investments.detailDrawer.metrics.incomeTax"),
      value: incomeTax,
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
      settlementDateLabel:
        transaction.settlementDate &&
        !isSameInvestmentEventAndSettlementDay(transaction.tradeDate, transaction.settlementDate)
          ? formatDate(transaction.settlementDate, locale)
          : null,
      amount: transaction.grossValue,
      netAmount: transaction.netValue,
      incomeTax: typeof transaction.incomeTax === "number" ? transaction.incomeTax : null,
      iof: typeof transaction.iof === "number" ? transaction.iof : null,
      direction: transaction.direction,
    }));

  const chartPoints = (performance?.points ?? []).map((point: PerformancePoint) => ({
    timestamp: String(point.timestamp),
    isProjected: point.isProjected,
    investedPrincipal: point.investedPrincipal,
    grossValue: point.grossValue,
    netValue: point.netValue,
  }));

  return {
    title: holding.asset.name,
    subtitle,
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
    xirr,
    xirrSinceLabel,
    totalReturnAbsolute,
    dividendsReceived,
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
