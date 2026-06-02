"use client";

import {
  AssetType,
  Indexer,
  InvestmentSource,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
} from "@/lib/graphql/types/__generated__/graphql";
import { getHoldingValueOrigin, type ValueOriginLabel } from "@/shared/utils/holdingValueOrigin";
import type { FixedIncomeHoldingDetailQuery } from "./api/queries.generated";
import { investmentTransactionCalendarDayKey, isSameInvestmentEventAndSettlementDay } from "../timelineSettlementDay";
import { withPartialMonthFlags } from "../performance/withPartialMonthFlags";

type Translator = (key: string, options?: Record<string, unknown>) => string;

type Holding = NonNullable<FixedIncomeHoldingDetailQuery["investmentHolding"]>;
type Transaction = FixedIncomeHoldingDetailQuery["investmentTransactions"][number];
type HoldingPerformance = NonNullable<FixedIncomeHoldingDetailQuery["holdingPerformance"]>;
type InvestmentHoldingPerformance = FixedIncomeHoldingDetailQuery["investmentHoldingPerformance"];

export interface FixedIncomeSummaryMetricViewModel {
  id: string;
  label: string;
  value: number | null;
  tone?: "auto" | "positive" | "negative" | "neutral";
}

export interface FixedIncomeTimelineItemViewModel {
  id: string;
  title: string;
  transactionType: InvestmentTransactionType;
  isFirstApplication: boolean;
  tradeDateLabel: string;
  tradeDateDayKey: string | null;
  settlementDateLabel: string | null;
  amount: number;
  netAmount: number | null;
  incomeTax: number | null;
  iof: number | null;
  direction: InvestmentTransactionDirection;
  source: InvestmentSource | null;
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
  xirr: number | null;
  xirrSinceLabel: string | null;
  xirrSinceYear: string | null;
  totalReturnAbsolute: number | null;
  couponsReceivedNet: number;
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
    isPartialMonth?: boolean;
    investedPrincipal: number | null;
    grossValue: number | null;
    netValue: number | null;
  }>;
  supportsProjection: boolean;
}

function getCurrentLocale(): string {
  return Intl.DateTimeFormat().resolvedOptions().locale || "pt-BR";
}

function getNotAvailableLabel(t: Translator): string {
  return t("investments.detailDrawer.common.notAvailable");
}

function formatDate(value: unknown, locale: string, t: Translator): string {
  if (!value) return getNotAvailableLabel(t);

  const raw = String(value);
  const localDateMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(raw);
  if (localDateMatch) {
    const [, year, month, day] = localDateMatch;
    const date = new Date(Number(year), Number(month) - 1, Number(day));
    return date.toLocaleDateString(locale);
  }

  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return getNotAvailableLabel(t);
  return date.toLocaleDateString(locale);
}

function formatYear(value: unknown): string | null {
  if (!value) return null;
  const raw = String(value);
  const localDateMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(raw);
  if (localDateMatch) return localDateMatch[1] ?? null;
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return null;
  return String(date.getFullYear());
}

function formatAssetType(assetType: AssetType, t: Translator): string {
  return t(`investments.assetTypes.${assetType}`);
}

function formatIndexer(holding: Holding, locale: string, t: Translator): string {
  if (!holding.indexer) return getNotAvailableLabel(t);

  if (holding.indexer === Indexer.PreFixado) {
    if (holding.fixedAnnualRate == null) return t(`investments.detailDrawer.indexers.${Indexer.PreFixado}`);
    const annualRate = holding.fixedAnnualRate * 100;
    const formattedAnnualRate = annualRate.toLocaleString(locale, {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
    return t("investments.detailDrawer.indexers.fixedAnnualRate", {
      rate: formattedAnnualRate,
      defaultValue: `${formattedAnnualRate}% p.a.`,
    });
  }

  const fallbackLabel =
    holding.indexer === Indexer.Cdi
      ? "CDI"
      : holding.indexer === Indexer.Di
        ? "DI"
        : holding.indexer === Indexer.Ipca
          ? "IPCA+"
          : holding.indexer === Indexer.IgpM
            ? "IGP-M"
            : holding.indexer === Indexer.IgpDi
              ? "IGP-DI"
              : holding.indexer === Indexer.Inpc
                ? "INPC"
                : holding.indexer === Indexer.Tr
                  ? "TR"
                  : holding.indexer === Indexer.Selic
                    ? "SELIC"
                    : holding.indexer === Indexer.Outros
                      ? "Outro"
                      : String(holding.indexer);
  const label = t(`investments.detailDrawer.indexers.${holding.indexer}`, { defaultValue: fallbackLabel });
  if (holding.indexerPercentage == null) return label;

  const percentValue = holding.indexerPercentage * 100;
  if (!Number.isFinite(percentValue)) return label;

  const formattedPercent = percentValue.toLocaleString(locale, {
    minimumFractionDigits: 1,
    maximumFractionDigits: 2,
  });
  return t("investments.detailDrawer.indexers.withPercentage", {
    label,
    percentage: formattedPercent,
    defaultValue: `${label} ${formattedPercent}%`,
  });
}

function formatTransactionType(type: InvestmentTransactionType, t: Translator): string {
  return t(`investments.detailDrawer.transactionTypes.${type}`);
}

function buildIssuerLabel(holding: Holding, t: Translator): string {
  return (
    holding.financialAccount?.issuer?.name ??
    holding.financialAccount?.issuerName ??
    holding.asset?.issuerName ??
    getNotAvailableLabel(t)
  );
}

function buildAccountLabel(holding: Holding, t: Translator): string {
  return holding.financialAccount?.name ?? getNotAvailableLabel(t);
}

export function buildFixedIncomeHoldingDetailViewModel(
  holding: Holding,
  transactions: FixedIncomeHoldingDetailQuery["investmentTransactions"],
  performance: HoldingPerformance | null | undefined,
  investmentHoldingPerformance: InvestmentHoldingPerformance | null | undefined,
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
  const xirr = typeof investmentHoldingPerformance?.xirr === "number" ? investmentHoldingPerformance.xirr : null;
  const xirrSinceLabel = investmentHoldingPerformance?.xirrSince
    ? formatDate(investmentHoldingPerformance.xirrSince, locale, t)
    : null;
  const xirrSinceYear = investmentHoldingPerformance?.xirrSince ? formatYear(investmentHoldingPerformance.xirrSince) : null;
  const totalReturnAbsolute =
    typeof investmentHoldingPerformance?.totalReturnAbsolute === "number"
      ? investmentHoldingPerformance.totalReturnAbsolute
      : null;
  const couponsReceivedNet = transactions.reduce((acc, transaction) => {
    if (transaction.type !== InvestmentTransactionType.InterestPayment) return acc;
    if (transaction.direction !== InvestmentTransactionDirection.Inflow) return acc;
    const amount = typeof transaction.netValue === "number" ? transaction.netValue : transaction.grossValue;
    return acc + amount;
  }, 0);
  const typeLabel = formatAssetType(holding.asset.assetType, t);
  const issuerLabel = buildIssuerLabel(holding, t);
  const accountLabel = buildAccountLabel(holding, t);

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

  const sortedTimelineTransactions = [...transactions]
    .map((transaction, index) => ({ transaction, index }))
    .sort((a, b) => {
      const byDate = new Date(String(b.transaction.tradeDate)).getTime() - new Date(String(a.transaction.tradeDate)).getTime();
      if (byDate !== 0) return byDate;
      return b.index - a.index;
    })
    .map(({ transaction }) => transaction);

  const firstApplicationId =
    [...sortedTimelineTransactions]
      .filter((item) => item.type === InvestmentTransactionType.Purchase)
      .sort((a, b) => new Date(String(a.tradeDate)).getTime() - new Date(String(b.tradeDate)).getTime())[0]?.id ?? null;

  const mappedTimelineItems = sortedTimelineTransactions.map((transaction) => ({
      id: transaction.id,
      title: formatTransactionType(transaction.type, t),
      transactionType: transaction.type,
      isFirstApplication: transaction.id === firstApplicationId,
      tradeDateLabel: formatDate(transaction.tradeDate, locale, t),
      tradeDateDayKey: investmentTransactionCalendarDayKey(transaction.tradeDate),
      settlementDateLabel:
        transaction.settlementDate &&
        !isSameInvestmentEventAndSettlementDay(transaction.tradeDate, transaction.settlementDate)
          ? formatDate(transaction.settlementDate, locale, t)
          : null,
      amount: transaction.grossValue,
      netAmount: transaction.netValue,
      incomeTax: transaction.incomeTax,
      iof: transaction.iof,
      direction: transaction.direction,
      source: transaction.source ?? null,
    }));

  const couponTimeline = [...transactions]
    .filter(
      (transaction) =>
        transaction.type === InvestmentTransactionType.InterestPayment &&
        transaction.direction === InvestmentTransactionDirection.Inflow,
    )
    .map((transaction) => ({
      tradeDate: new Date(String(transaction.tradeDate)).getTime(),
      amount: typeof transaction.netValue === "number" ? transaction.netValue : transaction.grossValue,
    }))
    .sort((a, b) => a.tradeDate - b.tradeDate);
  let couponIndex = 0;
  let cumulativeCoupons = 0;
  const chartPoints = withPartialMonthFlags(
    (performance?.points ?? []).map((point) => {
      const pointTime = new Date(String(point.timestamp)).getTime();
      while (couponIndex < couponTimeline.length && couponTimeline[couponIndex]!.tradeDate <= pointTime) {
        cumulativeCoupons += couponTimeline[couponIndex]!.amount;
        couponIndex += 1;
      }
      const grossBase =
        holding.isTaxExempt && point.grossValue == null && typeof point.netValue === "number"
          ? point.netValue
          : point.grossValue;
      return {
        timestamp: String(point.timestamp),
        isProjected: point.isProjected,
        investedPrincipal: point.investedPrincipal,
        grossValue: grossBase == null ? grossBase : grossBase + cumulativeCoupons,
        netValue: point.netValue == null ? point.netValue : point.netValue + cumulativeCoupons,
      };
    }),
  );

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
    xirr,
    xirrSinceLabel,
    xirrSinceYear,
    totalReturnAbsolute,
    couponsReceivedNet,
    indexerLabel: formatIndexer(holding, locale, t),
    issueDateLabel: formatDate(holding.issueDate, locale, t),
    gracePeriodDateLabel: formatDate(holding.gracePeriodDate, locale, t),
    maturityDateLabel: formatDate(holding.maturityDate, locale, t),
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
    timelineItems: mappedTimelineItems,
    chartPoints,
    supportsProjection: Boolean(performance?.capabilities.supportsProjection),
  };
}
