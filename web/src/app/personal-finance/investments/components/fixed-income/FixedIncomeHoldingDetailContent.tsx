"use client";

import * as React from "react";
import {
  Box,
  Divider,
  Paper,
  Stack,
} from "@mui/material";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../../i18n";
import EventNoteIcon from '@mui/icons-material/EventNote';
import { TimeProgress } from "../TimeProgress";
import { EmptyState, QueryErrorState } from "@/shared/components/ui/feedback";
import { Skeleton } from "@/shared/components/ui/primitives/Skeleton";
import { buildFixedIncomeHoldingDetailViewModel } from "./fixedIncomeHoldingDetailAdapter";
import type { FixedIncomeHoldingDetailQuery } from "./api/queries.generated";
import { HoldingPerformanceChart } from "../performance/HoldingPerformanceChart";
import { TimelineEvents } from "../TimelineEvents";

interface FixedIncomeHoldingDetailContentProps {
  holding: FixedIncomeHoldingDetailQuery["investmentHolding"] | null | undefined;
  transactions: FixedIncomeHoldingDetailQuery["investmentTransactions"] | undefined;
  performance: FixedIncomeHoldingDetailQuery["holdingPerformance"] | null | undefined;
  loading: boolean;
  error?: unknown;
  locale: string;
  onRetry?: () => void;
}

function LoadingSkeleton() {
  return (
    <Stack spacing={3}>
      <Paper variant="outlined" sx={{ p: 3 }}>
        <Stack direction="row" spacing={2} alignItems="center">
          <Skeleton variant="circular" width={48} height={48} />
          <Box sx={{ flex: 1 }}>
            <Skeleton variant="text" width="40%" height={32} />
            <Skeleton variant="text" width="24%" height={22} />
          </Box>
        </Stack>
      </Paper>
      <Skeleton variant="rectangular" height={240} sx={{ borderRadius: 1 }} />
    </Stack>
  );
}

export function FixedIncomeHoldingDetailContent({
  holding,
  transactions,
  performance,
  loading,
  error,
  locale,
  onRetry,
}: FixedIncomeHoldingDetailContentProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  const viewModel = React.useMemo(() => {
    if (!holding) return null;
    return buildFixedIncomeHoldingDetailViewModel(holding, transactions ?? [], performance, t, locale);
  }, [holding, locale, performance, t, transactions]);

  const currencyFormatter = React.useMemo(() => {
    if (!viewModel?.currencyCode) return null;
    return new Intl.NumberFormat(locale, {
      style: "currency",
      currency: viewModel.currencyCode,
    });
  }, [locale, viewModel]);

  if (loading && !holding) {
    return <LoadingSkeleton />;
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
      <EmptyState
        title={t("investments.detailDrawer.emptyTitle")}
        description={t("investments.detailDrawer.emptyDescription")}
      />
    );
  }

  const start = holding.issueDate ? new Date(String(holding.issueDate)) : null;
  const end = holding.maturityDate ? new Date(String(holding.maturityDate)) : null;
  const today = new Date();
  const percent =
    start && end && end.getTime() > start.getTime()
      ? Math.max(0, Math.min(1, (today.getTime() - start.getTime()) / (end.getTime() - start.getTime())))
      : null;

  function diffInMonths(from: Date | null, to: Date | null): number | null {
    if (!from || !to) return null;
    const y = to.getFullYear() - from.getFullYear();
    const m = to.getMonth() - from.getMonth();
    let months = y * 12 + m;
    if (to.getDate() < from.getDate()) months -= 1;
    return Math.max(0, months);
  }

  const monthsPassed = diffInMonths(start, today);
  const monthsLeft = diffInMonths(today, end);

  return (
    <Stack>
      <TimeProgress
        percent={percent}
        leftTitle={t("investments.detailDrawer.dates.elapsedTitle")}
        leftValue={monthsPassed != null ? t("investments.detailDrawer.dates.elapsedValue", { count: monthsPassed }) : "—"}
        rightTitle={t("investments.detailDrawer.dates.remainingTitle")}
        rightValue={monthsLeft != null ? t("investments.detailDrawer.dates.remainingValue", { count: monthsLeft }) : "—"}
        items={[
          { label: t("investments.detailDrawer.terms.issueDate"), value: viewModel.issueDateLabel },
          { label: t("investments.detailDrawer.terms.gracePeriodDate"), value: viewModel.gracePeriodDateLabel ?? "—" },
          { label: t("investments.detailDrawer.terms.maturityDate"), value: viewModel.maturityDateLabel },
        ]}
      />

      <Divider sx={{ my: 3 }} />

      <HoldingPerformanceChart
        points={viewModel.chartPoints}
        currencyCode={viewModel.currencyCode}
        locale={locale}
        isTaxExempt={holding.isTaxExempt}
        emptyTitle={t("investments.detailDrawer.chart.emptyTitle")}
        emptyDescription={t("investments.detailDrawer.chart.emptyDescription")}
        principalLabel={t("investments.detailDrawer.chart.principalLabel")}
        grossLabel={t("investments.detailDrawer.chart.grossLabel")}
        netLabel={t("investments.detailDrawer.chart.netLabel")}
        projectionToggleLabel={t("investments.detailDrawer.chart.modes.projected")}
      />      
      
      <Divider sx={{ mb: 3 }} />
      
      <TimelineEvents
        title={t("investments.detailDrawer.timeline.title")}
        emptyTitle={t("investments.detailDrawer.timeline.emptyTitle")}
        emptyDescription={t("investments.detailDrawer.timeline.emptyDescription")}
        icon={<EventNoteIcon sx={{ fontSize: 16, color: "primary.main" }} />}
        locale={locale}
        currencyCode={viewModel.currencyCode}
        items={viewModel.timelineItems}
        formatCurrency={(value) => currencyFormatter?.format(value) ?? String(value)}
        netAmountText={(formattedValue) => t("investments.detailDrawer.timeline.netAmount", { value: formattedValue })}
        incomeTaxText={(formattedValue) => t("investments.detailDrawer.timeline.incomeTax", { value: formattedValue })}
        iofText={(formattedValue) => t("investments.detailDrawer.timeline.iof", { value: formattedValue })}
      />
    </Stack>
  );
}

export default FixedIncomeHoldingDetailContent;
