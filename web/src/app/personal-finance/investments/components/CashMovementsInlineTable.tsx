"use client";

import React from "react";
import { useQuery } from "@apollo/client/react";
import { alpha, Box, CircularProgress, Typography, useTheme } from "@mui/material";
import { INVESTMENT_CASH_TRANSACTIONS } from "@/lib/graphql/operations/banking/queries";
import type {
  InvestmentCashTransactionsQuery,
  InvestmentCashTransactionsQueryVariables,
} from "@/lib/graphql/operations/banking/queries.generated";
import { CreditDebitType } from "@/lib/graphql/types/__generated__/graphql";
import { useTranslation } from "@/shared/i18n";
import { Label } from "@/shared/components/ui/primitives/Label";
import { formatTableDate } from "@/shared/utils/date";
import { personalFinanceTranslations } from "../../i18n";
import { CashMovementRowActions } from "./CashMovementRowActions";
import { formatCashLedgerMovementLabel } from "./cashMovementLabels";
import { formatCurrency, getCurrentLocale } from "./investmentTableHelpers";

const PREVIEW_FIRST = 5;

export type { CashMovementRowNode } from "./cashMovementRowTypes";

export interface CashMovementsInlineTableProps {
  financialAccountId: string | null | undefined;
}

export function CashMovementsInlineTable({ financialAccountId }: CashMovementsInlineTableProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const locale = getCurrentLocale();
  const theme = useTheme();
  const divider = alpha(theme.palette.divider, 0.7);

  const { data, loading, error } = useQuery<InvestmentCashTransactionsQuery, InvestmentCashTransactionsQueryVariables>(
    INVESTMENT_CASH_TRANSACTIONS,
    {
      variables: { financialAccountId: financialAccountId ?? undefined, first: PREVIEW_FIRST, after: undefined },
      errorPolicy: "all",
    },
  );

  const edges = data?.investmentCashTransactions?.edges ?? [];

  if (loading) {
    return (
      <Box sx={{ py: 3, display: "flex", justifyContent: "center" }}>
        <CircularProgress size={20} />
      </Box>
    );
  }

  if (error) {
    return (
      <Box sx={{ py: 2, px: 2 }}>
        <Typography variant="body2" color="error">
          {t("investments.cash.movements.loadError")}
        </Typography>
      </Box>
    );
  }

  if (edges.length === 0) {
    return (
      <Box sx={{ py: 2, px: 2 }}>
        <Typography variant="body2" color="text.secondary">
          {t("investments.cash.movements.empty")}
        </Typography>
      </Box>
    );
  }

  return (
    <Box>
      <Box sx={{ display: "flex", alignItems: "center", px: 2, py: 0.75 }}>
        <Box sx={{ width: "20%", minWidth: 0 }}>
          <Label>{t("investments.cash.movements.columns.date")}</Label>
        </Box>
        <Box sx={{ width: "34%", minWidth: 0 }}>
          <Label>{t("investments.cash.movements.columns.description")}</Label>
        </Box>
        <Box sx={{ width: "20%", minWidth: 0 }}>
          <Label>{t("investments.cash.movements.columns.kind")}</Label>
        </Box>
        <Box sx={{ width: "14%", minWidth: 0, textAlign: "right" }}>
          <Label>{t("investments.cash.movements.columns.amount")}</Label>
        </Box>
        <Box sx={{ width: 44, flexShrink: 0 }} />
      </Box>

      {edges.map(({ node }) => {
        const signedPrefix = node.creditDebitType === CreditDebitType.Credit ? "+" : "−";
        const amountStr = formatCurrency(node.amount, node.currencyCode, locale);
        const amountColor =
          node.creditDebitType === CreditDebitType.Credit ? theme.palette.success.main : theme.palette.error.main;

        return (
          <Box
            key={node.id}
            sx={{
              display: "flex",
              alignItems: "center",
              px: 2,
              py: 1.5,
              borderTop: `1px solid ${divider}`,
              bgcolor: "background.paper",
            }}
          >
            <Box sx={{ width: "20%", minWidth: 0 }}>
              <Typography variant="body2" noWrap>
                {formatTableDate(String(node.date), locale)}
              </Typography>
            </Box>
            <Box sx={{ width: "34%", minWidth: 0 }}>
              <Typography variant="body2" noWrap title={node.description}>
                {node.description}
              </Typography>
            </Box>
            <Box sx={{ width: "20%", minWidth: 0 }}>
              <Typography variant="body2" noWrap>
                {formatCashLedgerMovementLabel(node.type, node.creditDebitType, (k) => t(k as never))}
              </Typography>
            </Box>
            <Box sx={{ width: "14%", minWidth: 0, textAlign: "right" }}>
              <Typography variant="body2" noWrap sx={{ fontVariantNumeric: "tabular-nums", color: amountColor }}>
                {signedPrefix}
                {"\u00A0"}
                {amountStr}
              </Typography>
            </Box>
            <Box sx={{ width: 44, flexShrink: 0, display: "flex", justifyContent: "flex-end" }}>
              <CashMovementRowActions node={node} listContextAccountId={financialAccountId} />
            </Box>
          </Box>
        );
      })}
    </Box>
  );
}
