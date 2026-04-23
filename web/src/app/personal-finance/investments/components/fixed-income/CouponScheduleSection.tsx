"use client";

import { Divider, Stack } from "@mui/material";
import { TimelineIcon } from "@/shared/ui/mui-imports";
import { CouponScheduleTimeline } from "./CouponScheduleTimeline";
import { InvestmentSectionLabel } from "../InvestmentSectionLabel";
import type { FixedIncomeHoldingDetailQuery } from "./api/queries.generated";

interface CouponScheduleSectionProps {
  title: string;
  entries: NonNullable<FixedIncomeHoldingDetailQuery["investmentHolding"]>["couponSchedule"];
  locale: string;
  currencyCode: string | null;
  onRegisterOverdue?: (
    entry: NonNullable<FixedIncomeHoldingDetailQuery["investmentHolding"]>["couponSchedule"][number],
  ) => void;
  onEditPayment?: (
    entry: NonNullable<FixedIncomeHoldingDetailQuery["investmentHolding"]>["couponSchedule"][number],
  ) => void;
  onDeletePayment?: (transactionId: string) => Promise<void>;
  deletingPayment?: boolean;
}

export function CouponScheduleSection({
  title,
  entries,
  locale,
  currencyCode,
  onRegisterOverdue,
  onEditPayment,
  onDeletePayment,
  deletingPayment,
}: CouponScheduleSectionProps) {
  if (entries.length === 0) return null;

  return (
    <>
      <InvestmentSectionLabel title={title} icon={<TimelineIcon sx={{ fontSize: 16 }} />} sx={{ mb: 1 }} />
      <Stack sx={{ mb: 3 }}>
        <CouponScheduleTimeline
          entries={entries}
          locale={locale}
          currencyCode={currencyCode}
          onRegisterOverdue={onRegisterOverdue}
          onEditPayment={onEditPayment}
          onDeletePayment={onDeletePayment}
          deletingPayment={deletingPayment}
        />
      </Stack>
    </>
  );
}
