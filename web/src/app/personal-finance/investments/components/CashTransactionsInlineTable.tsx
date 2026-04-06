"use client";

import React from "react";
import { useApolloClient, useQuery } from "@apollo/client/react";
import { alpha, Avatar, Box, CircularProgress, Typography, useTheme } from "@mui/material";
import { INVESTMENT_CASH_TRANSACTIONS, INVESTMENT_TRANSACTIONS } from "@/lib/graphql/operations/banking/queries";
import type {
  InvestmentCashTransactionsQuery,
  InvestmentCashTransactionsQueryVariables,
  InvestmentTransactionsQuery,
  InvestmentTransactionsQueryVariables,
} from "@/lib/graphql/operations/banking/queries.generated";
import { CreditDebitType } from "@/lib/graphql/types/__generated__/graphql";
import { useTranslation } from "@/shared/i18n";
import { Label } from "@/shared/components/ui/primitives/Label";
import { formatTableDate } from "@/shared/utils/date";
import { personalFinanceTranslations } from "../../i18n";
import { CashTransactionRowActions } from "./CashTransactionRowActions";
import { HoldingIdentity, resolveHoldingIdentity } from "./HoldingIdentity";
import type { IssuerSelectionAccount } from "./IssuerSelection";
import { formatCurrency, getCurrentLocale, type HoldingRow } from "./table/investmentTableHelpers";

const PREVIEW_FIRST = 5;
const SECONDARY_ROW_SX = { mt: 0.25, minHeight: 16, display: "flex", alignItems: "center" } as const;

export type { CashTransactionRowNode } from "./cashTransactionRowTypes";

export interface CashTransactionsInlineTableProps {
  financialAccountId: string | null | undefined;
  investmentAccounts?: IssuerSelectionAccount[];
  holdings?: HoldingRow[];
  onSelectRelatedAsset?: (assetName: string) => void;
}

export function CashTransactionsInlineTable({
  financialAccountId,
  investmentAccounts = [],
  holdings = [],
  onSelectRelatedAsset,
}: CashTransactionsInlineTableProps) {
  const apolloClient = useApolloClient();
  const { t } = useTranslation(personalFinanceTranslations);
  const locale = getCurrentLocale();
  const theme = useTheme();
  const divider = alpha(theme.palette.divider, 0.7);
  const showFinancialAccountColumn = !financialAccountId;

  const accountsById = React.useMemo(
    () => new Map(investmentAccounts.map((account) => [account.id, account])),
    [investmentAccounts],
  );

  const { data, loading, error } = useQuery<InvestmentCashTransactionsQuery, InvestmentCashTransactionsQueryVariables>(
    INVESTMENT_CASH_TRANSACTIONS,
    {
      variables: { financialAccountId: financialAccountId ?? undefined, first: PREVIEW_FIRST, after: undefined },
      errorPolicy: "all",
    },
  );

  const edges = React.useMemo(() => data?.investmentCashTransactions?.edges ?? [], [data?.investmentCashTransactions?.edges]);
  const [holdingByInvestmentTxId, setHoldingByInvestmentTxId] = React.useState<Map<string, HoldingRow>>(new Map());

  React.useEffect(() => {
    const relatedIds = new Set(
      edges.map((edge) => edge.node.investmentTransactionId).filter((id): id is string => Boolean(id)),
    );
    if (relatedIds.size === 0 || holdings.length === 0) {
      setHoldingByInvestmentTxId(new Map());
      return;
    }

    let cancelled = false;

    const resolveInvestmentRelations = async () => {
      const resolved = new Map<string, HoldingRow>();
      const pendingIds = new Set(relatedIds);

      for (const holding of holdings) {
        if (pendingIds.size === 0) break;

        try {
          const result = await apolloClient.query<InvestmentTransactionsQuery, InvestmentTransactionsQueryVariables>({
            query: INVESTMENT_TRANSACTIONS,
            variables: { holdingId: holding.id },
            fetchPolicy: "cache-first",
            errorPolicy: "all",
          });

          const transactions = result.data?.investmentTransactions ?? [];
          for (const transaction of transactions) {
            if (pendingIds.has(transaction.id)) {
              resolved.set(transaction.id, holding);
              pendingIds.delete(transaction.id);
              if (pendingIds.size === 0) break;
            }
          }
        } catch {
          // Best effort only: unresolved relations keep the column empty.
        }
      }

      if (!cancelled) setHoldingByInvestmentTxId(resolved);
    };

    void resolveInvestmentRelations();

    return () => {
      cancelled = true;
    };
  }, [apolloClient, edges, holdings]);

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
        <Box sx={{ width: showFinancialAccountColumn ? "14%" : "16%", minWidth: 0 }}>
          <Label>{t("investments.cash.movements.columns.date")}</Label>
        </Box>
        <Box sx={{ width: showFinancialAccountColumn ? "26%" : "34%", minWidth: 0 }}>
          <Label>{t("investments.cash.movements.columns.description")}</Label>
        </Box>
        <Box sx={{ width: showFinancialAccountColumn ? "24%" : "28%", minWidth: 0 }}>
          <Label>{t("investments.table.columns.asset")}</Label>
        </Box>
        {showFinancialAccountColumn ? (
          <Box sx={{ width: "18%", minWidth: 0 }}>
            <Label>{t("investments.cash.movements.columns.financialAccount")}</Label>
          </Box>
        ) : null}
        <Box sx={{ width: showFinancialAccountColumn ? "10%" : "12%", minWidth: 0, textAlign: "right" }}>
          <Label>{t("investments.cash.movements.columns.amount")}</Label>
        </Box>
        <Box sx={{ width: 88, minWidth: 88, maxWidth: 88, flexShrink: 0 }} />
      </Box>

      {edges.map(({ node }) => {
        const account = accountsById.get(node.financialAccountId);
        const relatedHolding =
          node.investmentTransactionId != null ? holdingByInvestmentTxId.get(node.investmentTransactionId) : undefined;
        const relatedIdentity = relatedHolding ? resolveHoldingIdentity(relatedHolding) : null;
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
            <Box sx={{ width: showFinancialAccountColumn ? "14%" : "16%", minWidth: 0 }}>
              <Typography variant="body2" noWrap>
                {formatTableDate(String(node.date), locale)}
              </Typography>
              <Box sx={SECONDARY_ROW_SX} />
            </Box>
            <Box sx={{ width: showFinancialAccountColumn ? "26%" : "34%", minWidth: 0 }}>
              <Typography variant="body2" noWrap title={node.description}>
                {node.description}
              </Typography>
              <Box sx={SECONDARY_ROW_SX} />
            </Box>
            <Box sx={{ width: showFinancialAccountColumn ? "24%" : "28%", minWidth: 0 }}>
              {relatedIdentity ? (
                <Box
                  onClick={() => onSelectRelatedAsset?.(relatedIdentity.assetName)}
                  role={onSelectRelatedAsset ? "button" : undefined}
                  tabIndex={onSelectRelatedAsset ? 0 : undefined}
                  aria-label={
                    onSelectRelatedAsset
                      ? `Filtrar por ativo ${relatedIdentity.assetName}`
                      : undefined
                  }
                  onKeyDown={(event) => {
                    if (!onSelectRelatedAsset) return;
                    if (event.key === "Enter" || event.key === " ") {
                      event.preventDefault();
                      onSelectRelatedAsset(relatedIdentity.assetName);
                    }
                  }}
                  sx={
                    onSelectRelatedAsset
                      ? {
                          cursor: "pointer",
                          borderRadius: 1,
                          px: 0.5,
                          py: 0.25,
                          transition: "background-color 120ms ease, transform 120ms ease, box-shadow 120ms ease",
                          "&:hover": {
                            bgcolor: alpha(theme.palette.primary.main, 0.08),
                            // transform: "translateY(-1px)",
                          },
                          // "&:active": {
                          //   transform: "translateY(0)",
                          // },
                          "&:focus-visible": {
                            outline: "none",
                            boxShadow: `0 0 0 2px ${alpha(theme.palette.primary.main, 0.35)}`,
                            bgcolor: alpha(theme.palette.primary.main, 0.1),
                          },
                        }
                      : undefined
                  }
                >
                  <HoldingIdentity
                    assetName={relatedIdentity.assetName}
                    issuerName={relatedIdentity.issuerName}
                    logoUrl={relatedIdentity.logoUrl}
                    size={24}
                  />
                </Box>
              ) : (
                <>
                  <Typography variant="body2" color="text.secondary">
                    —
                  </Typography>
                  <Box sx={SECONDARY_ROW_SX} />
                </>
              )}
            </Box>
            {showFinancialAccountColumn ? (
              <Box sx={{ width: "18%", minWidth: 0 }}>
                <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                <Avatar
                  variant="rounded"
                  src={account?.issuer?.logoAsset?.publicUrl || undefined}
                  slotProps={{ img: { style: { objectFit: "contain" } } }}
                  sx={{
                    bgcolor: account?.issuer?.logoAsset?.publicUrl ? "transparent" : (account?.themeColor || "primary.main"),
                    width: 24,
                    height: 24,
                    borderRadius: 1,
                    flexShrink: 0,
                  }}
                >
                  {(account?.name || "?").slice(0, 1).toUpperCase()}
                </Avatar>
                <Box sx={{ minWidth: 0 }}>
                  <Typography variant="body2" noWrap title={account?.name || node.financialAccountId}>
                    {account?.name || "—"}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" noWrap sx={{ lineHeight: 1.2 }}>
                    {account?.issuer?.name || account?.issuerName || "—"}
                  </Typography>
                </Box>
                </Box>
              </Box>
            ) : null}
            <Box sx={{ width: showFinancialAccountColumn ? "10%" : "12%", minWidth: 0, textAlign: "right" }}>
              <Typography variant="body2" noWrap sx={{ fontVariantNumeric: "tabular-nums", color: amountColor }}>
                {signedPrefix}
                {"\u00A0"}
                {amountStr}
              </Typography>
              <Box sx={{ ...SECONDARY_ROW_SX, justifyContent: "flex-end" }} />
            </Box>
            <Box
              sx={{
                width: 88,
                minWidth: 88,
                maxWidth: 88,
                flexShrink: 0,
                display: "flex",
                justifyContent: "flex-end",
              }}
            >
              <CashTransactionRowActions node={node} listContextAccountId={financialAccountId} />
            </Box>
          </Box>
        );
      })}
    </Box>
  );
}
