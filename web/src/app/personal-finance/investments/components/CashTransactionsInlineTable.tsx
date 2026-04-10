"use client";

import React, { Fragment } from "react";
import { useQuery } from "@apollo/client/react";
import {
  alpha,
  Avatar,
  Box,
  ButtonBase,
  CircularProgress,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  useTheme,
} from "@mui/material";
import { INVESTMENT_CASH_TRANSACTIONS } from "@/lib/graphql/operations/banking/queries";
import type {
  InvestmentCashTransactionsQuery,
  InvestmentCashTransactionsQueryVariables,
} from "@/lib/graphql/operations/banking/queries.generated";
import { CreditDebitType } from "@/lib/graphql/types/__generated__/graphql";
import { useTranslation, i18n } from "@/shared/i18n";
import { Label } from "@/shared/components/ui/primitives/Label";
import { FINANCE_TABLE_BODY_FONT_SIZE } from "@/shared/components/ui/data-display/Table";
import { personalFinanceTranslations } from "../../i18n";
import { formatFinanceDateGroupLabel, parseFinanceDateKey } from "../../utils/financeDateGroupLabel";
import {
  financeTransactionBodyRowSx,
  financeTransactionDateGroupTableCellSx,
  financeTransactionDateGroupTableRowSx,
  financeTransactionHeadCellSx,
  financeTransactionTableBodySx,
  financeTransactionTableHeadRowSx,
  financeTransactionTableHeadSx,
  getFinanceTransactionTableSx,
} from "../../utils/financeTransactionTableStyles";
import { CashTransactionRowActions } from "./CashTransactionRowActions";
import { HoldingIdentity, resolveHoldingIdentity } from "./HoldingIdentity";
import type { IssuerSelectionAccount } from "./IssuerSelection";
import { formatCurrency, getCurrentLocale } from "./table/investmentTableHelpers";

const PAGE_SIZE = 20;

export type { CashTransactionRowNode } from "./cashTransactionRowTypes";

export interface CashTransactionsInlineTableProps {
  financialAccountId: string | null | undefined;
  investmentAccounts?: IssuerSelectionAccount[];
  onSelectRelatedAsset?: (assetName: string) => void;
}

type CashEdge = NonNullable<InvestmentCashTransactionsQuery["investmentCashTransactions"]>["edges"][number];

export function CashTransactionsInlineTable({
  financialAccountId,
  investmentAccounts = [],
  onSelectRelatedAsset,
}: CashTransactionsInlineTableProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const locale = getCurrentLocale();
  const theme = useTheme();
  const showFinancialAccountColumn = !financialAccountId;
  const headCellSx = financeTransactionHeadCellSx(theme);
  const checkboxColSx = { width: 44, maxWidth: 44 };
  const assetColSx = { width: 120, maxWidth: 120 };
  const financialAccountColSx = { width: 140, maxWidth: 140 };
  const amountColSx = { width: 160, maxWidth: 160, px: 2 };
  const tableBodyLabelFontSize = FINANCE_TABLE_BODY_FONT_SIZE;
  const colSpan = showFinancialAccountColumn ? 6 : 5;

  const accountsById = React.useMemo(
    () => new Map(investmentAccounts.map((account) => [account.id, account])),
    [investmentAccounts],
  );

  const isFetchingMoreRef = React.useRef(false);
  const [loadingMore, setLoadingMore] = React.useState(false);

  const { data, loading, error, fetchMore } = useQuery<
    InvestmentCashTransactionsQuery,
    InvestmentCashTransactionsQueryVariables
  >(INVESTMENT_CASH_TRANSACTIONS, {
    variables: { financialAccountId: financialAccountId ?? undefined, first: PAGE_SIZE, after: undefined },
    errorPolicy: "all",
    notifyOnNetworkStatusChange: true,
  });

  const edges = React.useMemo(() => data?.investmentCashTransactions?.edges ?? [], [data?.investmentCashTransactions?.edges]);
  const pageInfo = data?.investmentCashTransactions?.pageInfo;
  const hasNextPage = pageInfo?.hasNextPage ?? false;
  const endCursor = pageInfo?.endCursor;

  const loadMore = React.useCallback(async () => {
    if (!hasNextPage || !endCursor || !data?.investmentCashTransactions) return;
    if (loading || isFetchingMoreRef.current) return;

    isFetchingMoreRef.current = true;
    setLoadingMore(true);
    await fetchMore({
      variables: {
        financialAccountId: financialAccountId ?? undefined,
        first: PAGE_SIZE,
        after: endCursor,
      },
      updateQuery: (prev, { fetchMoreResult }) => {
        if (!fetchMoreResult?.investmentCashTransactions) return prev;
        const previousEdges = prev.investmentCashTransactions?.edges ?? [];
        const incomingEdges = fetchMoreResult.investmentCashTransactions.edges ?? [];
        const seenIds = new Set<string>();
        const mergedEdges = [...previousEdges, ...incomingEdges].filter((edge) => {
          const id = edge?.node?.id;
          if (!id || seenIds.has(id)) return false;
          seenIds.add(id);
          return true;
        });

        return {
          investmentCashTransactions: {
            ...fetchMoreResult.investmentCashTransactions,
            edges: mergedEdges,
          },
        };
      },
    }).finally(() => {
      isFetchingMoreRef.current = false;
      setLoadingMore(false);
    });
  }, [data?.investmentCashTransactions, endCursor, fetchMore, financialAccountId, hasNextPage, loading]);

  const groupedByDate = React.useMemo(() => {
    const groups: { date: string; edges: CashEdge[] }[] = [];
    const sorted = [...edges].sort((a, b) => {
      const pa = parseFinanceDateKey(String(a.node.date));
      const pb = parseFinanceDateKey(String(b.node.date));
      return (pb?.sortKey ?? 0) - (pa?.sortKey ?? 0);
    });
    for (const edge of sorted) {
      const parts = parseFinanceDateKey(String(edge.node.date));
      const dateKey = parts?.dateKey ?? String(edge.node.date).split("T")[0] ?? "";
      const last = groups[groups.length - 1];
      if (last && last.date === dateKey) {
        last.edges.push(edge);
      } else {
        groups.push({ date: dateKey, edges: [edge] });
      }
    }
    return groups;
  }, [edges]);

  const linkRowHoverBg = React.useMemo(() => alpha(theme.palette.primary.main, 0.06), [theme.palette.primary.main]);
  const linkRowFocusBg = React.useMemo(() => alpha(theme.palette.primary.main, 0.08), [theme.palette.primary.main]);

  if (loading && !data?.investmentCashTransactions) {
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
    <TableContainer
      component={Paper}
      elevation={0}
      sx={{
        bgcolor: "transparent",
        background: "transparent !important",
        border: "none",
        overflow: "visible",
        position: "relative",
      }}
      data-testid="investment-cash-transactions-table"
    >
      <Table
        sx={{ ...getFinanceTransactionTableSx(650), tableLayout: "fixed" }}
        aria-label={t("financeTransactions.transactionTable.ariaLabel")}
      >
        <TableHead sx={financeTransactionTableHeadSx}>
          <TableRow sx={financeTransactionTableHeadRowSx}>
            <TableCell padding="checkbox" sx={{ pl: 2, ...checkboxColSx, ...headCellSx }} />
            <TableCell sx={headCellSx}>
              <Label>{t("investments.cash.movements.columns.description")}</Label>
            </TableCell>
            <TableCell sx={{ ...assetColSx, ...headCellSx }}>
              <Label>{t("investments.table.columns.asset")}</Label>
            </TableCell>
            {showFinancialAccountColumn ? (
              <TableCell align="right" sx={{ ...financialAccountColSx, ...headCellSx }}>
                <Label>{t("investments.cash.movements.columns.financialAccount")}</Label>
              </TableCell>
            ) : null}
            <TableCell align="right" sx={{ ...amountColSx, ...headCellSx }}>
              <Label>{t("investments.cash.movements.columns.amount")}</Label>
            </TableCell>
            <TableCell
              align="center"
              padding="none"
              sx={{
                width: 88,
                maxWidth: 88,
                px: 1,
                ...headCellSx,
              }}
            />
          </TableRow>
        </TableHead>
        <TableBody sx={financeTransactionTableBodySx}>
          {groupedByDate.map((group) => (
            <Fragment key={`cash-day-${group.date}`}>
              <TableRow sx={financeTransactionDateGroupTableRowSx}>
                <TableCell colSpan={colSpan} sx={financeTransactionDateGroupTableCellSx()}>
                  <Label>{formatFinanceDateGroupLabel(group.date, t, i18n.language)}</Label>
                </TableCell>
              </TableRow>
              {group.edges.map((edge, index) => {
                const { node } = edge;
                const account = accountsById.get(node.financialAccountId);
                const relatedIdentity =
                  node.investmentTransactionId != null && node.linkedInvestmentAsset != null
                    ? resolveHoldingIdentity({ asset: node.linkedInvestmentAsset })
                    : null;
                const signedPrefix = node.creditDebitType === CreditDebitType.Credit ? "+" : "−";
                const amountStr = formatCurrency(node.amount, node.currencyCode, locale);
                const amountColor =
                  node.creditDebitType === CreditDebitType.Credit ? theme.palette.success.main : theme.palette.error.main;
                const isFirst = index === 0;
                const isLast = index === group.edges.length - 1;

                return (
                  <TableRow
                    key={node.id}
                    sx={financeTransactionBodyRowSx(theme, { isFirst, isLast })}
                  >
                    <TableCell padding="checkbox" sx={{ pl: 2, ...checkboxColSx }} />
                    <TableCell component="th" scope="row">
                      <Typography
                        variant="body2"
                        noWrap
                        title={node.description}
                        sx={{ fontSize: tableBodyLabelFontSize, lineHeight: 1.25 }}
                      >
                        {node.description}
                      </Typography>
                    </TableCell>
                    <TableCell sx={assetColSx}>
                      {relatedIdentity ? (
                        <Box
                          onClick={() => onSelectRelatedAsset?.(relatedIdentity.assetName)}
                          role={onSelectRelatedAsset ? "button" : undefined}
                          tabIndex={onSelectRelatedAsset ? 0 : undefined}
                          aria-label={
                            onSelectRelatedAsset ? `Filtrar por ativo ${relatedIdentity.assetName}` : undefined
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
                                  display: "inline-flex",
                                  transition: "background-color 120ms ease, box-shadow 120ms ease",
                                  "&:hover": {
                                    bgcolor: alpha(theme.palette.primary.main, 0.08),
                                  },
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
                        <Typography variant="body2" color="text.secondary" sx={{ fontSize: tableBodyLabelFontSize }}>
                          —
                        </Typography>
                      )}
                    </TableCell>
                    {showFinancialAccountColumn ? (
                      <TableCell align="right" sx={financialAccountColSx}>
                        <Box sx={{ display: "flex", alignItems: "center", justifyContent: "flex-end", gap: 1, width: "100%" }}>
                          <Avatar
                            variant="rounded"
                            src={account?.issuer?.logoAsset?.publicUrl || undefined}
                            slotProps={{ img: { style: { objectFit: "contain" } } }}
                            sx={{
                              bgcolor: account?.issuer?.logoAsset?.publicUrl
                                ? "transparent"
                                : (account?.themeColor || "primary.main"),
                              width: 24,
                              height: 24,
                              borderRadius: 1,
                              flexShrink: 0,
                            }}
                          >
                            {(account?.name || "?").slice(0, 1).toUpperCase()}
                          </Avatar>
                          <Box sx={{ minWidth: 0 }}>
                            <Typography
                              variant="body2"
                              noWrap
                              title={account?.name || node.financialAccountId}
                              sx={{ fontSize: tableBodyLabelFontSize, lineHeight: 1.25 }}
                            >
                              {account?.name || "—"}
                            </Typography>
                            <Typography variant="caption" color="text.secondary" noWrap sx={{ lineHeight: 1.2 }}>
                              {account?.issuer?.name || account?.issuerName || "—"}
                            </Typography>
                          </Box>
                        </Box>
                      </TableCell>
                    ) : null}
                    <TableCell align="right" sx={amountColSx}>
                      <Typography
                        variant="body2"
                        sx={{
                          fontSize: tableBodyLabelFontSize,
                          lineHeight: 1.25,
                          fontVariantNumeric: "tabular-nums",
                          color: amountColor,
                          whiteSpace: "nowrap",
                        }}
                      >
                        {signedPrefix}
                        {"\u00A0"}
                        {amountStr}
                      </Typography>
                    </TableCell>
                    <TableCell align="center" padding="none" sx={{ width: 88, maxWidth: 88 }}>
                      <CashTransactionRowActions node={node} listContextAccountId={financialAccountId} />
                    </TableCell>
                  </TableRow>
                );
              })}
            </Fragment>
          ))}

          {hasNextPage ? (
            <TableRow>
              <TableCell colSpan={colSpan} sx={{ border: "none", p: 0, bgcolor: "transparent" }}>
                <ButtonBase
                  onClick={() => void loadMore()}
                  disabled={loadingMore}
                  data-testid="investment-cash-load-more-btn"
                  sx={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    gap: 1,
                    width: "100%",
                    textAlign: "center",
                    py: 1.25,
                    px: 2,
                    color: "primary.main",
                    bgcolor: "transparent",
                    backgroundColor: "transparent",
                    borderTop: `1px solid ${theme.palette.divider}`,
                    transition: "background-color 120ms ease-out",
                    "&:hover": {
                      bgcolor: linkRowHoverBg,
                    },
                    "&.Mui-focusVisible": {
                      bgcolor: linkRowFocusBg,
                    },
                    "&.Mui-disabled": {
                      color: "text.disabled",
                    },
                  }}
                >
                  {loadingMore ? <CircularProgress size={18} color="inherit" /> : null}
                  {t("investments.cash.movements.loadMore")}
                </ButtonBase>
              </TableCell>
            </TableRow>
          ) : null}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
