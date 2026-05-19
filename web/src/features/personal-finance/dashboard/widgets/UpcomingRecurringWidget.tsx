"use client";

import React from "react";
import { Box, Divider, Link, Skeleton, Stack, Typography } from "@mui/material";
import { useQuery } from "@apollo/client/react";
import NextLink from "next/link";
import { WidgetWrapper } from "../components/WidgetWrapper";
import type { WidgetProps } from "../types";
import { getWidgetDefinition } from "../registry";
import { useDashboardStore } from "../store";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "@/app/personal-finance/i18n";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { Label } from "@/shared/components/ui/primitives/Label";
import { RecurringPaymentNameIcon } from "@/app/personal-finance/recurring-payments/RecurringPaymentNameIcon";
import { RECURRING_PAYMENTS } from "@/lib/graphql/operations/banking/recurringPaymentOperations";
import { RecurringPaymentStatus } from "@/lib/graphql/types/__generated__/graphql";

const NAME_ICON_SIZE = 28;

type Row = {
  id: string;
  name: string;
  creditDebitType: "CREDIT" | "DEBIT";
  type?: string | null;
  expectedAmount?: number | null;
  currencyCode: string;
  status: RecurringPaymentStatus;
  nextDueDate: string;
  merchant?: { id: string; name: string; logoAsset?: { publicUrl: string | null } | null } | null;
};

type QueryData = {
  recurringPayments: Row[];
};

const LIST_LIMIT = 10;

const ROW_GRID_SX = {
  display: "grid",
  gridTemplateColumns: "6px 3.25rem 1fr minmax(4.5rem, auto)",
  columnGap: 1,
  alignItems: "center",
  py: 1.25,
} as const;

function dueCaption(days: number, t: (key: string, options?: { count: number }) => string): string {
  if (days < 0) {
    const count = Math.abs(days);
    return count === 1
      ? t("recurringPayments.widget.overdueDay", { count })
      : t("recurringPayments.widget.overdueDays", { count });
  }
  if (days === 0) {
    return t("recurringPayments.widget.dueToday");
  }
  return days === 1
    ? t("recurringPayments.widget.dueInDay", { count: days })
    : t("recurringPayments.widget.dueInDays", { count: days });
}

const MONTHS_PT = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"];

function formatAmount(value: number, currency = "BRL"): string {
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency }).format(value);
}

function formatDate(dateString: string): string {
  const [, m, d] = dateString.split("-");
  return `${parseInt(d)}/${MONTHS_PT[parseInt(m) - 1]}`;
}

function daysUntil(dateString: string): number {
  const [y, m, d] = dateString.split("-").map(Number);
  const now = new Date();
  const base = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const due = new Date(y, m - 1, d).getTime();
  return Math.round((due - base) / (1000 * 60 * 60 * 24));
}

export const UpcomingRecurringWidget: React.FC<WidgetProps> = ({ widget, dragHandleProps }) => {
  const { t } = useTranslation(personalFinanceTranslations);
  const { isEditMode, removeWidget } = useDashboardStore();
  const definition = getWidgetDefinition(widget.type);

  const { data, loading, error, refetch } = useQuery<QueryData>(RECURRING_PAYMENTS, {
    variables: { financialAccountId: null },
    fetchPolicy: "cache-and-network",
  });

  const active = (data?.recurringPayments ?? [])
    .filter((r) => r.status === RecurringPaymentStatus.Active)
    .map((r) => ({ ...r, days: daysUntil(r.nextDueDate) }))
    .sort((a, b) => a.days - b.days);

  const items = active.slice(0, LIST_LIMIT);
  const isEmpty = !loading && !error && active.length === 0;

  const showSkeleton = loading && !data;

  return (
    <WidgetWrapper
      title={widget.title || (definition?.defaultTitleKey ? t(definition.defaultTitleKey) : "")}
      isEditMode={isEditMode}
      onRemove={() => removeWidget(widget.id)}
      onRefresh={() => void refetch()}
      dragHandleProps={dragHandleProps}
    >
      <Box sx={{ display: "flex", flexDirection: "column", height: "100%", minHeight: 0 }}>
        <Box sx={{ flex: 1, minHeight: 0, overflow: "auto" }}>
          {showSkeleton ? (
            <Stack sx={{ px: 2, pt: 1.5, py: 0.5 }} divider={<Divider />} spacing={0}>
              {Array.from({ length: LIST_LIMIT }, (_, i) => (
                <Box key={i} sx={ROW_GRID_SX}>
                  <Box />
                  <Skeleton variant="text" width="100%" height={14} />
                  <Box sx={{ display: "flex", alignItems: "flex-start", gap: 1, minWidth: 0 }}>
                    <Skeleton variant="circular" width={NAME_ICON_SIZE} height={NAME_ICON_SIZE} />
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                      <Skeleton variant="text" width="75%" height={18} />
                      <Skeleton variant="text" width="50%" height={14} sx={{ mt: 0.5 }} />
                    </Box>
                  </Box>
                  <Skeleton variant="text" width={72} height={18} sx={{ justifySelf: "end" }} />
                </Box>
              ))}
            </Stack>
          ) : error ? (
            <Box sx={{ px: 2, pt: 1.5 }}>
              <Typography variant="body2" color="error">
                {error.message}
              </Typography>
            </Box>
          ) : isEmpty ? (
            <Box sx={{ px: 2, pt: 1.5 }}>
              <Typography variant="body2" color="text.secondary">
                {t("recurringPayments.emptyDescription")}
              </Typography>
            </Box>
          ) : items.length > 0 ? (
            <Stack sx={{ px: 2, pt: 1.5, py: 0.5 }} divider={<Divider />} spacing={0}>
              {items.map((row) => {
                const isOverdue = row.days < 0;
                return (
                  <Box key={row.id} sx={ROW_GRID_SX}>
                    <Box sx={{ display: "flex", alignItems: "center", justifyContent: "center" }}>
                      {isOverdue ? (
                        <Box
                          component="span"
                          aria-hidden
                          sx={{ width: 6, height: 6, borderRadius: "50%", bgcolor: "error.main" }}
                        />
                      ) : null}
                    </Box>
                    <Label sx={{ mb: 0, ...(isOverdue && { color: "error.main" }) }}>
                      {formatDate(row.nextDueDate)}
                    </Label>
                    <Box sx={{ display: "flex", alignItems: "flex-start", gap: 1, minWidth: 0 }}>
                      <RecurringPaymentNameIcon row={row} size={NAME_ICON_SIZE} />
                      <Box sx={{ minWidth: 0, flex: 1 }}>
                        <Typography variant="body2" noWrap>
                          {row.name}
                        </Typography>
                        <Typography
                          variant="caption"
                          noWrap
                          display="block"
                          sx={{ color: isOverdue ? "error.main" : "text.secondary" }}
                        >
                          {dueCaption(row.days, t)}
                        </Typography>
                      </Box>
                    </Box>
                    <Typography
                      variant="body2"
                      fontWeight={600}
                      noWrap
                      sx={{ justifySelf: "end", ...(isOverdue && { color: "error.main" }) }}
                    >
                      {row.expectedAmount != null
                        ? formatAmount(row.expectedAmount, row.currencyCode)
                        : t("recurringPayments.variableAmountBadge")}
                    </Typography>
                  </Box>
                );
              })}
            </Stack>
          ) : null}
        </Box>

        <Drawer.Footer>
          <Box sx={{ display: "flex", justifyContent: "center" }}>
            <Link
              component={NextLink}
              href="/personal-finance/recurring-payments"
              variant="body2"
              underline="hover"
              sx={{ textTransform: "none", fontWeight: 500 }}
            >
              {t("recurringPayments.widget.viewAll")}
            </Link>
          </Box>
        </Drawer.Footer>
      </Box>
    </WidgetWrapper>
  );
};
