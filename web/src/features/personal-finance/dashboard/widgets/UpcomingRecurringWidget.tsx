"use client";

import React from "react";
import { Box, Button, Chip, Stack, Typography } from "@mui/material";
import { useQuery } from "@apollo/client/react";
import { useRouter } from "next/navigation";
import { WidgetWrapper } from "../components/WidgetWrapper";
import { WidgetProps } from "../types";
import { getWidgetDefinition } from "../registry";
import { useDashboardStore } from "../store";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "@/app/personal-finance/i18n";
import { RECURRING_PAYMENTS } from "@/lib/graphql/operations/banking/recurringPaymentOperations";

type Row = {
  id: string;
  name: string;
  expectedAmount?: number | null;
  currencyCode: string;
  status: "ACTIVE" | "PAUSED";
  nextDueDate: string;
};

type QueryData = {
  recurringPayments: Row[];
};

function formatAmount(value?: number | null, currency = "BRL"): string {
  if (value == null) return "—";
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency }).format(value);
}

function daysUntil(dateString: string): number {
  const target = new Date(dateString);
  const now = new Date();
  const base = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const due = new Date(target.getFullYear(), target.getMonth(), target.getDate()).getTime();
  return Math.round((due - base) / (1000 * 60 * 60 * 24));
}

function urgencyColor(days: number): "error" | "warning" | "default" {
  if (days <= 3) return "error";
  if (days <= 7) return "warning";
  return "default";
}

export const UpcomingRecurringWidget: React.FC<WidgetProps> = ({ widget, dragHandleProps }) => {
  const router = useRouter();
  const { t } = useTranslation(personalFinanceTranslations);
  const { isEditMode, removeWidget } = useDashboardStore();
  const definition = getWidgetDefinition(widget.type);

  const { data, loading, error, refetch } = useQuery<QueryData>(RECURRING_PAYMENTS, {
    variables: { financialAccountId: null },
    fetchPolicy: "cache-and-network",
  });

  const items =
    (data?.recurringPayments ?? [])
      .filter((row) => row.status === "ACTIVE")
      .sort((a, b) => a.nextDueDate.localeCompare(b.nextDueDate))
      .slice(0, 6);

  return (
    <WidgetWrapper
      title={widget.title || (definition?.defaultTitleKey ? t(definition.defaultTitleKey) : "")}
      isEditMode={isEditMode}
      onRemove={() => removeWidget(widget.id)}
      onRefresh={() => void refetch()}
      dragHandleProps={dragHandleProps}
    >
      <Stack spacing={1.25} sx={{ px: 2, pb: 2 }}>
        <Box sx={{ display: "flex", justifyContent: "flex-end" }}>
          <Button size="small" onClick={() => router.push("/personal-finance/recurring-payments")}>
            {t("recurringPayments.pendingActions.viewAll")}
          </Button>
        </Box>

        {error ? (
          <Typography variant="body2" color="error">
            {error.message}
          </Typography>
        ) : null}
        {!error && loading && items.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            {t("actions.loading")}
          </Typography>
        ) : null}
        {!error && !loading && items.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            {t("recurringPayments.emptyDescription")}
          </Typography>
        ) : null}

        {items.map((row) => {
          const days = daysUntil(row.nextDueDate);
          return (
            <Box
              key={row.id}
              sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 1.5 }}
            >
              <Box sx={{ minWidth: 0 }}>
                <Typography variant="body2" noWrap>
                  {row.name}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {row.expectedAmount == null
                    ? t("recurringPayments.variableAmountBadge")
                    : formatAmount(row.expectedAmount, row.currencyCode)}
                </Typography>
              </Box>
              <Chip
                size="small"
                color={urgencyColor(days)}
                label={t("recurringPayments.daysUntilDue", { count: days })}
              />
            </Box>
          );
        })}
      </Stack>
    </WidgetWrapper>
  );
};
