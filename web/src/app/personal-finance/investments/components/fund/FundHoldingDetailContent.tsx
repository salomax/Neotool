"use client";

import * as React from "react";
import { Box, Divider } from "@mui/material";
import { InvestmentSource } from "@/lib/graphql/types/__generated__/graphql";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../../i18n";
import { EmptyState, QueryErrorState } from "@/shared/components/ui/feedback";
import { Skeleton } from "@/shared/components/ui/primitives/Skeleton";
import { buildFundHoldingDetailViewModel } from "./fundHoldingDetailAdapter";
import type { FundHoldingDetailQuery } from "./api/queries.generated";
import { HoldingPerformanceChart } from "../performance/HoldingPerformanceChart";
import { DetailPerformancePeriodSelector } from "../performance/DetailPerformancePeriodSelector";
import type { DetailPerformancePeriod } from "../performance/detailPerformancePeriods";
import { TimelineEvents } from "../TimelineEvents";

interface FundHoldingDetailContentProps {
  holding: FundHoldingDetailQuery["investmentHolding"] | null | undefined;
  transactions: FundHoldingDetailQuery["investmentTransactions"] | undefined;
  performance: FundHoldingDetailQuery["holdingPerformance"] | null | undefined;
  investmentHoldingPerformance: FundHoldingDetailQuery["investmentHoldingPerformance"] | null | undefined;
  loading: boolean;
  error?: unknown;
  locale: string;
  selectedPeriod: DetailPerformancePeriod;
  onSelectPeriod: (period: DetailPerformancePeriod) => void;
  onRetry?: () => void;
  onDeleteTransaction?: (transactionId: string) => void;
}

export function FundHoldingDetailContent({
  holding,
  transactions,
  performance,
  investmentHoldingPerformance,
  loading,
  error,
  locale,
  selectedPeriod,
  onSelectPeriod,
  onRetry,
  onDeleteTransaction,
}: FundHoldingDetailContentProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  const viewModel = React.useMemo(() => {
    if (!holding) return null;
    return buildFundHoldingDetailViewModel(
      holding,
      transactions ?? [],
      performance,
      investmentHoldingPerformance,
      t,
      locale,
    );
  }, [holding, investmentHoldingPerformance, locale, performance, t, transactions]);

  const currencyFormatter = React.useMemo(() => {
    if (!viewModel?.currencyCode) return null;
    return new Intl.NumberFormat(locale, {
      style: "currency",
      currency: viewModel.currencyCode,
    });
  }, [locale, viewModel]);

  if (loading && !holding) {
    return (
      <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "1fr" }, gap: 2 }}>
        <Skeleton height={120} />
        <Skeleton height={320} />
        <Skeleton height={320} />
      </Box>
    );
  }

  if (error && !holding) {
    return (
      <QueryErrorState
        error={error}
        title={t("investments.detailDrawer.errorTitle")}
        onRetry={onRetry}
      />
    );
  }

  if (!holding || !viewModel) {
    return (
      <EmptyState title={t("shared.empty.title")} description={t("shared.empty.description")} />
    );
  }

  return (
    <Box sx={{ display: "grid", gridTemplateColumns: { xs: "1fr", md: "1fr" }, gap: 2 }}>
      <HoldingPerformanceChart
        points={viewModel.chartPoints}
        currencyCode={viewModel.currencyCode}
        locale={locale}
        resolution={performance?.resolution}
        supportsProjection={performance?.capabilities.supportsProjection ?? false}
        isTaxExempt={holding.isTaxExempt}
        title={t("investments.detailDrawer.chart.title")}
        headerAction={
          <DetailPerformancePeriodSelector
            value={selectedPeriod}
            onChange={onSelectPeriod}
          />
        }
        emptyTitle={t("investments.detailDrawer.chart.emptyTitle")}
        emptyDescription={t("investments.detailDrawer.chart.emptyDescription")}
        grossLabel={t("investments.detailDrawer.chart.grossLabel")}
        netLabel={t("investments.detailDrawer.chart.netLabel")}
        projectionToggleLabel={t("investments.detailDrawer.chart.modes.projected")}
      />

      <Divider sx={{ mb: 3 }} />

      <TimelineEvents
        title={t("investments.detailDrawer.timeline.title")}
        emptyTitle={t("investments.detailDrawer.timeline.emptyTitle")}
        emptyDescription={t("investments.detailDrawer.timeline.emptyDescription")}
        inflowLabel={t("investments.detailDrawer.timeline.inflowLabel")}
        outflowLabel={t("investments.detailDrawer.timeline.outflowLabel")}
        locale={locale}
        currencyCode={viewModel.currencyCode}
        items={viewModel.timelineItems.map((item) => ({
          ...item,
          source: item.source ?? undefined,
          isEditable: item.source === InvestmentSource.Manual,
        }))}
        formatCurrency={(value) => currencyFormatter?.format(value) ?? String(value)}
        netAmountText={(formattedValue) => t("investments.detailDrawer.timeline.netAmount", { value: formattedValue })}
        incomeTaxText={(formattedValue) => t("investments.detailDrawer.timeline.incomeTax", { value: formattedValue })}
        iofText={(formattedValue) => t("investments.detailDrawer.timeline.iof", { value: formattedValue })}
        settlementDateText={(date) => t("investments.detailDrawer.timeline.settlementDate", { date })}
        onDeleteTransaction={onDeleteTransaction}
        notEditableTooltip={t("investments.detailDrawer.timeline.notEditableTooltip")}
        actionsAriaLabel={t("investments.table.rowActions.ariaLabel")}
        deleteActionLabel={t("actions.delete")}
      />
    </Box>
  );
}

export default FundHoldingDetailContent;
