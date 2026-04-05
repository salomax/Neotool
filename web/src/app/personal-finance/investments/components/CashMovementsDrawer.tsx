"use client";

import React from "react";
import { useQuery } from "@apollo/client/react";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import CloseIcon from "@mui/icons-material/Close";
import {
  Box,
  Button,
  CircularProgress,
  IconButton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import { INVESTMENT_CASH_TRANSACTIONS } from "@/lib/graphql/operations/banking/queries";
import type {
  InvestmentCashTransactionsQuery,
  InvestmentCashTransactionsQueryVariables,
} from "@/lib/graphql/operations/banking/queries.generated";
import type { IssuerSelectionAccount } from "./IssuerSelection";
import { CreditDebitType } from "@/lib/graphql/types/__generated__/graphql";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import i18n from "i18next";
import { useTranslation } from "@/shared/i18n";
import { formatTableDate } from "@/shared/utils/date";
import { personalFinanceTranslations } from "../../i18n";
import { CashMovementRowActions } from "./CashMovementRowActions";
import { formatCashLedgerMovementLabel } from "./cashMovementLabels";
import { formatCurrency, getCurrentLocale } from "./investmentTableHelpers";
import { IssuerSelection } from "./IssuerSelection";
import { RecordCashForm } from "./RecordCashForm";

const PAGE_SIZE = 20;

export interface CashMovementsDrawerProps {
  open: boolean;
  onClose: () => void;
  financialAccountId: string | null;
  investmentAccounts: IssuerSelectionAccount[];
  bankAccounts: IssuerSelectionAccount[];
  listContextAccountId?: string | null;
}

type Step = "list" | "record";

export function CashMovementsDrawer({
  open,
  onClose,
  financialAccountId,
  investmentAccounts,
  bankAccounts,
  listContextAccountId,
}: CashMovementsDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const locale = getCurrentLocale();
  const drawerLocale = React.useMemo(() => i18n.language || Intl.DateTimeFormat().resolvedOptions().locale || "pt-BR", []);

  const [step, setStep] = React.useState<Step>("list");
  const [recordAccountId, setRecordAccountId] = React.useState<string | null>(null);
  const [formResetSignal, setFormResetSignal] = React.useState(0);

  React.useEffect(() => {
    if (!open) return;
    setStep("list");
    setRecordAccountId(financialAccountId);
  }, [open, financialAccountId]);

  const { data, loading, error, fetchMore } = useQuery<
    InvestmentCashTransactionsQuery,
    InvestmentCashTransactionsQueryVariables
  >(INVESTMENT_CASH_TRANSACTIONS, {
    variables: { financialAccountId: financialAccountId ?? undefined, first: PAGE_SIZE, after: undefined },
    skip: !open,
    errorPolicy: "all",
  });

  const edges = data?.investmentCashTransactions?.edges ?? [];
  const pageInfo = data?.investmentCashTransactions?.pageInfo;
  const hasNextPage = pageInfo?.hasNextPage ?? false;
  const endCursor = pageInfo?.endCursor;

  const loadMore = async () => {
    if (!endCursor || !data?.investmentCashTransactions) return;
    await fetchMore({
      variables: {
        financialAccountId: financialAccountId ?? undefined,
        first: PAGE_SIZE,
        after: endCursor,
      },
      updateQuery: (prev, { fetchMoreResult }) => {
        if (!fetchMoreResult?.investmentCashTransactions) return prev;
        return {
          investmentCashTransactions: {
            ...fetchMoreResult.investmentCashTransactions,
            edges: [
              ...(prev.investmentCashTransactions?.edges ?? []),
              ...fetchMoreResult.investmentCashTransactions.edges,
            ],
          },
        };
      },
    });
  };

  const goToRecord = () => {
    setFormResetSignal((n) => n + 1);
    setStep("record");
    if (!financialAccountId) {
      setRecordAccountId(null);
    }
  };

  const listTitle = t("investments.cash.movements.drawer.listTitle");
  const recordTitle = t("investments.cash.movements.drawer.recordTitle");

  return (
    <Drawer open={open} onClose={onClose} anchor="right" size="md">
      {step === "list" ? (
        <>
          <Drawer.Header title={listTitle} />
          <Drawer.Body>
            <Box sx={{ display: "flex", justifyContent: "flex-end", mb: 2 }}>
              <Button variant="contained" size="small" onClick={goToRecord} data-testid="cash-drawer-register-btn">
                {t("investments.cash.movements.drawer.register")}
              </Button>
            </Box>

            {loading && (
              <Box sx={{ py: 6, display: "flex", justifyContent: "center" }}>
                <CircularProgress size={24} />
              </Box>
            )}

            {!loading && error && (
              <Typography variant="body2" color="error">
                {t("investments.cash.movements.loadError")}
              </Typography>
            )}

            {!loading && !error && edges.length === 0 && (
              <Typography variant="body2" color="text.secondary">
                {t("investments.cash.movements.empty")}
              </Typography>
            )}

            {!loading && !error && edges.length > 0 && (
              <>
                <Table size="small" sx={{ tableLayout: "fixed" }}>
                  <TableHead>
                    <TableRow>
                      <TableCell sx={{ width: "20%" }}>{t("investments.cash.movements.columns.date")}</TableCell>
                      <TableCell sx={{ width: "32%" }}>{t("investments.cash.movements.columns.description")}</TableCell>
                      <TableCell sx={{ width: "20%" }}>{t("investments.cash.movements.columns.kind")}</TableCell>
                      <TableCell align="right" sx={{ width: "16%" }}>
                        {t("investments.cash.movements.columns.amount")}
                      </TableCell>
                      <TableCell align="right" sx={{ width: 48, p: 0.5 }} aria-hidden />
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {edges.map(({ node }) => {
                      const signedPrefix = node.creditDebitType === CreditDebitType.Credit ? "+" : "−";
                      const amountStr = formatCurrency(node.amount, node.currencyCode, locale);
                      return (
                        <TableRow key={node.id}>
                          <TableCell>
                            <Typography variant="body2" noWrap>
                              {formatTableDate(String(node.date), locale)}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Typography variant="body2" noWrap title={node.description}>
                              {node.description}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Typography variant="body2" noWrap>
                              {formatCashLedgerMovementLabel(node.type, node.creditDebitType, (k) => t(k as never))}
                            </Typography>
                          </TableCell>
                          <TableCell align="right">
                            <Typography variant="body2" noWrap sx={{ fontVariantNumeric: "tabular-nums" }}>
                              {signedPrefix}
                              {"\u00A0"}
                              {amountStr}
                            </Typography>
                          </TableCell>
                          <TableCell align="right" sx={{ width: 48, p: 0.5, verticalAlign: "middle" }}>
                            <CashMovementRowActions
                              node={node}
                              listContextAccountId={listContextAccountId ?? financialAccountId}
                            />
                          </TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
                {hasNextPage ? (
                  <Box sx={{ display: "flex", justifyContent: "center", mt: 2 }}>
                    <Button size="small" onClick={() => void loadMore()} data-testid="cash-drawer-load-more">
                      {t("investments.cash.movements.loadMore")}
                    </Button>
                  </Box>
                ) : null}
              </>
            )}
          </Drawer.Body>
        </>
      ) : (
        <>
          <Drawer.Header>
            <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", width: "100%" }}>
              <Box sx={{ display: "flex", alignItems: "center", gap: 1, minWidth: 0 }}>
                <IconButton size="small" onClick={() => setStep("list")} data-testid="cash-drawer-back-btn">
                  <ArrowBackIcon />
                </IconButton>
                <Typography
                  variant="h5"
                  component="h2"
                  fontWeight={400}
                  noWrap
                  sx={{ letterSpacing: "0.05em", lineHeight: 1.15, color: "action.active" }}
                >
                  {recordTitle.toLocaleUpperCase(drawerLocale)}
                </Typography>
              </Box>
              <IconButton size="small" onClick={onClose} data-testid="cash-drawer-close-btn">
                <CloseIcon />
              </IconButton>
            </Box>
          </Drawer.Header>
          <Drawer.Body>
            {!recordAccountId ? (
              <IssuerSelection
                financialAccounts={investmentAccounts}
                description={t("investments.cash.drawer.selectAccount.description")}
                emptyState={t("investments.cash.drawer.selectAccount.emptyState")}
                onSelect={(id) => setRecordAccountId(id)}
                dataTestIdPrefix="cash-drawer-select-account-"
              />
            ) : (
              <RecordCashForm
                key={recordAccountId}
                financialAccountId={recordAccountId}
                bankAccounts={bankAccounts}
                investmentAccounts={investmentAccounts}
                listContextAccountId={listContextAccountId}
                resetSignal={formResetSignal}
                formId="cash-drawer-record-form"
                onSuccess={() => setStep("list")}
                onCancel={() => setStep("list")}
                cancelText={t("actions.cancel")}
                submitText={t("investments.cash.actions.submit")}
                submittingText={t("investments.cash.actions.submitting")}
                cancelTestId="cash-drawer-record-cancel"
                submitTestId="cash-drawer-record-submit"
              />
            )}
          </Drawer.Body>
        </>
      )}
    </Drawer>
  );
}
