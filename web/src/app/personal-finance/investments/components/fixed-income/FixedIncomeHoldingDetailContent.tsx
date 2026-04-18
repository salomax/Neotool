"use client";

import * as React from "react";
import {
  Box,
  Divider,
  Paper,
  Stack,
  Typography,
} from "@mui/material";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../../i18n";
import { TimeProgress } from "../TimeProgress";
import { EmptyState, QueryErrorState } from "@/shared/components/ui/feedback";
import { Skeleton } from "@/shared/components/ui/primitives/Skeleton";
import { buildFixedIncomeHoldingDetailViewModel } from "./fixedIncomeHoldingDetailAdapter";
import type { FixedIncomeHoldingDetailQuery } from "./api/queries.generated";
import { HoldingPerformanceChart } from "../performance/HoldingPerformanceChart";
import { TimelineEvents, timelineEventDomId } from "../TimelineEvents";
import { CouponScheduleSection } from "./CouponScheduleSection";

interface FixedIncomeHoldingDetailContentProps {
  holding: FixedIncomeHoldingDetailQuery["investmentHolding"] | null | undefined;
  transactions: FixedIncomeHoldingDetailQuery["investmentTransactions"] | undefined;
  performance: FixedIncomeHoldingDetailQuery["holdingPerformance"] | null | undefined;
  investmentHoldingPerformance: FixedIncomeHoldingDetailQuery["investmentHoldingPerformance"] | null | undefined;
  loading: boolean;
  error?: unknown;
  locale: string;
  onRetry?: () => void;
  onRegisterOverdueCoupon?: (
    entry: NonNullable<FixedIncomeHoldingDetailQuery["investmentHolding"]>["couponSchedule"][number],
  ) => void;
  onEditCouponPayment?: (
    entry: NonNullable<FixedIncomeHoldingDetailQuery["investmentHolding"]>["couponSchedule"][number],
  ) => void;
  onDeleteCouponPayment?: (transactionId: string) => Promise<void>;
  deletingCouponPayment?: boolean;
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
  investmentHoldingPerformance,
  loading,
  error,
  locale,
  onRetry,
  onRegisterOverdueCoupon,
  onEditCouponPayment,
  onDeleteCouponPayment,
  deletingCouponPayment,
}: FixedIncomeHoldingDetailContentProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  const viewModel = React.useMemo(() => {
    if (!holding) return null;
    return buildFixedIncomeHoldingDetailViewModel(
      holding,
      transactions ?? [],
      performance,
      investmentHoldingPerformance,
      t,
      locale,
    );
  }, [holding, investmentHoldingPerformance, locale, performance, t, transactions]);

  const inflowLabel = t("investments.detailDrawer.timeline.inflowLabel");
  const outflowLabel = t("investments.detailDrawer.timeline.outflowLabel");

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

  const currencyFormatter = new Intl.NumberFormat(locale, {
    style: "currency",
    currency: viewModel.currencyCode,
  });

  const firstTimelineEventIdByDayKey = new Map<string, string>();
  for (const item of viewModel.timelineItems) {
    const key = item.tradeDateDayKey;
    if (!key) continue;
    if (firstTimelineEventIdByDayKey.has(key)) continue;
    firstTimelineEventIdByDayKey.set(key, item.id);
  }

  const chartEvents = viewModel.timelineItems
    .filter((it) => Boolean(it.tradeDateDayKey) && !it.isFirstApplication)
    .map((it) => {
      const isOutflow = String(it.direction) === "OUTFLOW";
      const direction = isOutflow ? outflowLabel : inflowLabel;
      const amount = isOutflow ? (it.netAmount ?? it.amount) : it.amount;
      const formattedAmount = currencyFormatter.format(amount);
      return {
        id: it.id,
        timestamp: it.tradeDateDayKey as string,
        title: it.title,
        subtitle: `${direction} · ${formattedAmount}`,
      };
    });

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

      <Divider sx={{ my: 5 }} />

      <HoldingPerformanceChart
        points={viewModel.chartPoints}
        currencyCode={viewModel.currencyCode}
        locale={locale}
        resolution={performance?.resolution}
        supportsProjection={performance?.capabilities.supportsProjection ?? true}
        isTaxExempt={holding.isTaxExempt}
        title={t("investments.detailDrawer.chart.title")}
        emptyTitle={t("investments.detailDrawer.chart.emptyTitle")}
        emptyDescription={t("investments.detailDrawer.chart.emptyDescription")}
        grossLabel={t("investments.detailDrawer.chart.grossLabel")}
        netLabel={t("investments.detailDrawer.chart.netLabel")}
        projectionToggleLabel={t("investments.detailDrawer.chart.modes.projected")}
        events={chartEvents}
        onEventDayClick={(dayKey) => {
          const eventId = firstTimelineEventIdByDayKey.get(dayKey);
          if (!eventId) return;
          const el = document.getElementById(timelineEventDomId(eventId));
          if (!el) return;
          el.scrollIntoView({ behavior: "smooth", block: "start" });
          (el as HTMLElement).focus?.();
        }}
      />      

      <Divider sx={{ mb: 5 }} />

      { holding.couponSchedule?.length > 0 && 
        <>
          <CouponScheduleSection
            title={t("investments.detailDrawer.couponSchedule.sectionTitle")}
            entries={holding.couponSchedule}
            locale={locale}
            currencyCode={viewModel.currencyCode}
            onRegisterOverdue={onRegisterOverdueCoupon}
            onEditPayment={onEditCouponPayment}
            onDeletePayment={onDeleteCouponPayment}
            deletingPayment={deletingCouponPayment}
          />
          <Divider sx={{ mb: 5 }} />
        </>}
      
      <TimelineEvents
        title={t("investments.detailDrawer.timeline.title")}
        emptyTitle={t("investments.detailDrawer.timeline.emptyTitle")}
        emptyDescription={t("investments.detailDrawer.timeline.emptyDescription")}
        inflowLabel={inflowLabel}
        outflowLabel={outflowLabel}
        locale={locale}
        currencyCode={viewModel.currencyCode}
        items={viewModel.timelineItems}
        formatCurrency={(value) => currencyFormatter.format(value)}
        netAmountText={(formattedValue) => t("investments.detailDrawer.timeline.netAmount", { value: formattedValue })}
        incomeTaxText={(formattedValue) => t("investments.detailDrawer.timeline.incomeTax", { value: formattedValue })}
        iofText={(formattedValue) => t("investments.detailDrawer.timeline.iof", { value: formattedValue })}
        settlementDateText={(date) => t("investments.detailDrawer.timeline.settlementDate", { date })}
      />
    </Stack>
  );
}

export default FixedIncomeHoldingDetailContent;
