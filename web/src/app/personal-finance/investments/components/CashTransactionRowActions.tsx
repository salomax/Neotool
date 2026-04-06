"use client";

import React from "react";
import { useApolloClient, useMutation } from "@apollo/client/react";
import {
  alpha,
  Box,
  IconButton,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Typography,
  useTheme,
} from "@mui/material";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import EditIcon from "@mui/icons-material/Edit";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import QueryStatsIcon from "@mui/icons-material/QueryStats";
import { DELETE_INVESTMENT_CASH_TRANSACTION } from "@/lib/graphql/operations/banking/investmentCashMutations";
import type {
  DeleteInvestmentCashTransactionMutation,
  DeleteInvestmentCashTransactionMutationVariables,
} from "@/lib/graphql/operations/banking/investmentCashMutations.generated";
import {
  INVESTMENT_CASH_BALANCE,
  INVESTMENT_CASH_TRANSACTIONS,
  INVESTMENT_PORTFOLIO_SUMMARY,
} from "@/lib/graphql/operations/banking/queries";
import { CreditDebitType, TransactionSource } from "@/lib/graphql/types/__generated__/graphql";
import { ConfirmationDialog } from "@/shared/components/ui/feedback";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { useTranslation } from "@/shared/i18n";
import { Label } from "@/shared/components/ui/primitives/Label";
import { useToast } from "@/shared/providers";
import { formatTableDate } from "@/shared/utils/date";
import { personalFinanceTranslations } from "../../i18n";
import type { CashTransactionRowNode } from "./cashTransactionRowTypes";
import { formatCashLedgerTransactionLabel } from "./cashTransactionLabels";
import { EditCashForm } from "./EditCashForm";
import { formatCurrency, getCurrentLocale } from "./table/investmentTableHelpers";

function isManualCashRowEditable(node: CashTransactionRowNode): boolean {
  return node.source === TransactionSource.Manual && node.investmentTransactionId == null;
}

export interface CashTransactionRowActionsProps {
  node: CashTransactionRowNode;
  listContextAccountId: string | null | undefined;
}

/**
 * MoreVert menu with view / edit / delete for a cash ledger row (manual rows only for edit/delete).
 */
export function CashTransactionRowActions({ node, listContextAccountId }: CashTransactionRowActionsProps) {
  const apolloClient = useApolloClient();
  const { t } = useTranslation(personalFinanceTranslations);
  const { success } = useToast();
  const locale = getCurrentLocale();
  const theme = useTheme();

  const [menuAnchor, setMenuAnchor] = React.useState<HTMLElement | null>(null);
  const [menuRow, setMenuRow] = React.useState<CashTransactionRowNode | null>(null);
  const [detailRow, setDetailRow] = React.useState<CashTransactionRowNode | null>(null);
  const [editRow, setEditRow] = React.useState<CashTransactionRowNode | null>(null);
  const [editResetSignal, setEditResetSignal] = React.useState(0);
  const [deleteRow, setDeleteRow] = React.useState<CashTransactionRowNode | null>(null);

  React.useEffect(() => {
    if (editRow) setEditResetSignal((n) => n + 1);
  }, [editRow]);

  const closeMenu = () => {
    setMenuAnchor(null);
    setMenuRow(null);
  };

  const [deleteCash, { loading: deleting }] = useMutation<
    DeleteInvestmentCashTransactionMutation,
    DeleteInvestmentCashTransactionMutationVariables
  >(DELETE_INVESTMENT_CASH_TRANSACTION, {
    refetchQueries: [
      {
        query: INVESTMENT_CASH_BALANCE,
        variables: { financialAccountId: listContextAccountId ?? undefined },
      },
      {
        query: INVESTMENT_PORTFOLIO_SUMMARY,
        variables: { financialAccountId: listContextAccountId ?? undefined },
      },
      { query: INVESTMENT_PORTFOLIO_SUMMARY },
      {
        query: INVESTMENT_CASH_TRANSACTIONS,
        variables: { financialAccountId: listContextAccountId ?? undefined, first: 5, after: undefined },
      },
      {
        query: INVESTMENT_CASH_TRANSACTIONS,
        variables: { financialAccountId: listContextAccountId ?? undefined, first: 20, after: undefined },
      },
    ],
  });

  const handleConfirmDelete = async () => {
    if (!deleteRow) return;
    await deleteCash({ variables: { id: deleteRow.id } });
    await apolloClient.refetchQueries({ include: [INVESTMENT_PORTFOLIO_SUMMARY] });
    success(t("investments.cash.movements.successDeleted"));
    setDeleteRow(null);
  };

  const openMenu = (e: React.MouseEvent<HTMLElement>) => {
    e.stopPropagation();
    setMenuAnchor(e.currentTarget);
    setMenuRow(node);
  };

  const editable = menuRow ? isManualCashRowEditable(menuRow) : false;

  return (
    <>
      <IconButton size="small" aria-label={t("investments.table.rowActions.ariaLabel")} onClick={openMenu}>
        <MoreVertIcon fontSize="small" />
      </IconButton>

      <Menu
        open={Boolean(menuAnchor)}
        anchorEl={menuAnchor}
        onClose={closeMenu}
        anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
        transformOrigin={{ vertical: "top", horizontal: "right" }}
      >
        <MenuItem
          disabled={deleting}
          onClick={() => {
            const row = menuRow;
            closeMenu();
            if (!row) return;
            setDetailRow(row);
          }}
        >
          <ListItemIcon>
            <QueryStatsIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t("investments.table.rowActions.viewDetails")}</ListItemText>
        </MenuItem>
        <MenuItem
          disabled={deleting || !editable}
          onClick={() => {
            const row = menuRow;
            closeMenu();
            if (!row || !isManualCashRowEditable(row)) return;
            setEditRow(row);
          }}
        >
          <ListItemIcon>
            <EditIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t("investments.table.rowActions.edit")}</ListItemText>
        </MenuItem>
        <MenuItem
          disabled={deleting || !editable}
          onClick={() => {
            const row = menuRow;
            closeMenu();
            if (!row || !isManualCashRowEditable(row)) return;
            setDeleteRow(row);
          }}
          sx={{
            color: "error.main",
            "&:hover": {
              bgcolor: alpha(theme.palette.error.main, 0.08),
            },
            "&.Mui-focusVisible": {
              bgcolor: alpha(theme.palette.error.main, 0.08),
            },
          }}
        >
          <ListItemIcon sx={{ color: "inherit" }}>
            <DeleteOutlineIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t("investments.table.rowActions.remove")}</ListItemText>
        </MenuItem>
      </Menu>

      <Drawer open={Boolean(detailRow)} onClose={() => setDetailRow(null)} anchor="right" size="sm">
        <Drawer.Header title={t("investments.cash.movements.detailTitle")} />
        <Drawer.Body>
          {detailRow ? (
            <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
              <Box>
                <Label>{t("investments.cash.movements.columns.date")}</Label>
                <Typography variant="body2">{formatTableDate(String(detailRow.date), locale)}</Typography>
              </Box>
              <Box>
                <Label>{t("investments.cash.movements.columns.kind")}</Label>
                <Typography variant="body2">
                  {formatCashLedgerTransactionLabel(detailRow.type, detailRow.creditDebitType, (k) => t(k as never))}
                </Typography>
              </Box>
              <Box>
                <Label>{t("investments.cash.amount")}</Label>
                <Typography variant="body2" sx={{ fontVariantNumeric: "tabular-nums" }}>
                  {detailRow.creditDebitType === CreditDebitType.Credit ? "+" : "−"}
                  {"\u00A0"}
                  {formatCurrency(detailRow.amount, detailRow.currencyCode, locale)}
                </Typography>
              </Box>
              <Box>
                <Label>{t("investments.cash.description")}</Label>
                <Typography variant="body2">{detailRow.description}</Typography>
              </Box>
            </Box>
          ) : null}
        </Drawer.Body>
      </Drawer>

      <Drawer open={Boolean(editRow)} onClose={() => setEditRow(null)} anchor="right" size="md">
        <Drawer.Header title={t("investments.cash.movements.editTitle")} />
        <Drawer.Body>
          {editRow ? (
            <EditCashForm
              key={editRow.id}
              transactionId={editRow.id}
              currencyCode={editRow.currencyCode}
              initialAmount={editRow.amount}
              initialDate={String(editRow.date)}
              initialDescription={editRow.description}
              listContextAccountId={listContextAccountId}
              resetSignal={editResetSignal}
              formId={`edit-cash-form-${editRow.id}`}
              onSuccess={() => setEditRow(null)}
              onCancel={() => setEditRow(null)}
              cancelText={t("actions.cancel")}
              submitText={t("investments.cash.actions.submit")}
              submittingText={t("investments.cash.actions.submitting")}
              cancelTestId="edit-cash-cancel-btn"
              submitTestId="edit-cash-submit-btn"
            />
          ) : null}
        </Drawer.Body>
      </Drawer>

      <ConfirmationDialog
        open={Boolean(deleteRow)}
        onClose={() => setDeleteRow(null)}
        onConfirm={handleConfirmDelete}
        title={t("investments.cash.movements.deleteConfirmation.title")}
        message={t("investments.cash.movements.deleteConfirmation.message")}
        cancelText={t("actions.cancel")}
        confirmText={t("actions.delete")}
        confirmColor="error"
        loading={deleting}
      />
    </>
  );
}
