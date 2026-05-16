'use client';

import {
  Box,
  Divider,
  LinearProgress,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
  useTheme,
} from '@mui/material';
import {
  CategoryApplicableTo,
  Transaction,
  FinancialAccount,
} from '@/lib/graphql/types/__generated__/graphql';
import { useTranslation, i18n } from '@/shared/i18n';
import { useCallback, useEffect, useMemo, useRef, useState, Fragment, type MouseEvent } from 'react';
import { personalFinanceTranslations } from '../../i18n';
import { Label } from '@/shared/components/ui/primitives/Label';
import {
  financeTransactionDateGroupTableCellSx,
  financeTransactionDateGroupTableRowSx,
  financeTransactionHeadCellSx,
  financeTransactionTableBodySx,
  financeTransactionTableHeadRowSx,
  financeTransactionTableHeadSx,
  getFinanceTransactionTableSx,
} from '../../utils/financeTransactionTableStyles';
import { formatFinanceDateGroupLabel, parseFinanceDateKey } from '../../utils/financeDateGroupLabel';
import { InlineTransactionForm } from './InlineTransactionForm';
import { TransactionRow } from './TransactionRow';
import type { TransactionRowCallbacks } from './TransactionRow';
import { TransactionActionMenu } from './TransactionActionMenu';
import { TransactionDialogs } from './TransactionDialogs';
import { useBusyCounter } from './hooks/useBusyCounter';
import { useTransactionEdit } from './hooks/useTransactionEdit';
import { useTransactionDelete } from './hooks/useTransactionDelete';
import { useActionMenu, resolveRuleApplicableTo } from './hooks/useActionMenu';
import { useCategoryPicker } from './hooks/useCategoryPicker';
import { useSimilarEdit } from './hooks/useSimilarEdit';
import { useAttachmentPreview } from './hooks/useAttachmentPreview';
import { RecurringPaymentForm } from '@/app/personal-finance/recurring-payments/RecurringPaymentForm';

interface TransactionTableProps {
  transactions: Transaction[];
  financialAccounts?: FinancialAccount[];
  loading: boolean;
  hasMore: boolean;
  onLoadMore: () => void;
  isCreating?: boolean;
  onCancelCreate?: () => void;
  onSuccessCreate?: () => void;
  onOpenNfceDocument?: (transactionId: string) => void;
  selectedAccountId?: string | null;
  selectedMonth?: number;
  selectedYear?: number;
}

export function TransactionTable({
  transactions,
  financialAccounts,
  loading,
  hasMore,
  onLoadMore,
  isCreating = false,
  onCancelCreate,
  onSuccessCreate,
  onOpenNfceDocument,
  selectedAccountId = null,
  selectedMonth,
  selectedYear,
}: TransactionTableProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const loaderRef = useRef<HTMLDivElement>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const tableHeadRef = useRef<HTMLTableSectionElement | null>(null);
  const [progressTop, setProgressTop] = useState<number>(0);

  // ── State domains ────────────────────────────────────────────────────────────
  const { busyCount, beginBusy, endBusy } = useBusyCounter();

  // "Applied rules" drawer and "create rule" drawer are simple open/close state —
  // too lightweight to warrant their own hooks.
  const [appliedRulesTransaction, setAppliedRulesTransaction] = useState<Transaction | null>(null);
  const [createRuleDraft, setCreateRuleDraft] = useState<{
    pattern: string;
    applicableTo: CategoryApplicableTo;
  } | null>(null);
  const { editedTransaction, isEditFormOpen, openEdit, closeEdit, clearEdit } = useTransactionEdit();

  const deleteHook = useTransactionDelete({
    onDeleted: (tx) => {
      if (editedTransaction?.id === tx.id) {
        closeEdit();
        clearEdit();
      }
      onSuccessCreate?.();
    },
    beginBusy,
    endBusy,
  });

  const actionMenu = useActionMenu({
    transactions,
    onSuccess: onSuccessCreate,
    beginBusy,
    endBusy,
  });

  const categoryPicker = useCategoryPicker({
    transactions,
    onSuccess: onSuccessCreate,
    beginBusy,
    endBusy,
  });

  const similarEdit = useSimilarEdit({
    pendingSimilarEditPrompt: categoryPicker.pendingSimilarEditPrompt,
    setPendingSimilarEditPrompt: categoryPicker.setPendingSimilarEditPrompt,
    onSuccess: onSuccessCreate,
    beginBusy,
    endBusy,
  });

  const attachmentPreview = useAttachmentPreview({
    onSuccess: onSuccessCreate,
    beginBusy,
    endBusy,
  });
  const [recurringFormOpen, setRecurringFormOpen] = useState(false);
  const [recurringSourceTx, setRecurringSourceTx] = useState<Transaction | null>(null);

  // ── Derived ──────────────────────────────────────────────────────────────────
  const showHeaderProgress = loading || busyCount > 0;
  const colSpan = selectedAccountId ? 7 : 8;

  const accountsById = useMemo(() => {
    const map = new Map<string, FinancialAccount>();
    for (const account of financialAccounts ?? []) {
      map.set(account.id, account);
    }
    return map;
  }, [financialAccounts]);

  const groupedTransactions = useMemo(() => {
    const groups: { date: string; transactions: Transaction[] }[] = [];
    const sorted = transactions
      .map((transaction) => {
        const parts = parseFinanceDateKey(transaction.date as string);
        return {
          transaction,
          sortKey: parts?.sortKey ?? 0,
          dateKey: parts?.dateKey ?? (transaction.date as string),
        };
      })
      .sort((a, b) => b.sortKey - a.sortKey);

    for (const item of sorted) {
      const lastGroup = groups[groups.length - 1];
      if (lastGroup && lastGroup.date === item.dateKey) {
        lastGroup.transactions.push(item.transaction);
        continue;
      }
      groups.push({ date: item.dateKey, transactions: [item.transaction] });
    }
    return groups;
  }, [transactions]);

  const installmentSectionStartIndex = useMemo(() => {
    if (typeof selectedMonth !== 'number' || typeof selectedYear !== 'number') return null;
    if (selectedMonth < 1 || selectedMonth > 12) return null;
    const selectedMonthKey = selectedYear * 100 + selectedMonth;
    for (let i = 0; i < groupedTransactions.length; i++) {
      const parts = parseFinanceDateKey(groupedTransactions[i]?.date ?? '');
      if (!parts) continue;
      if (parts.year * 100 + parts.month < selectedMonthKey) {
        return i > 0 ? i : null;
      }
    }
    return null;
  }, [groupedTransactions, selectedMonth, selectedYear]);

  // ── Progress bar positioning ─────────────────────────────────────────────────
  const updateProgressTop = useCallback(() => {
    const c = containerRef.current;
    const h = tableHeadRef.current;
    if (!c || !h) return;
    const cRect = c.getBoundingClientRect();
    const hRect = h.getBoundingClientRect();
    setProgressTop(Math.max(0, Math.round(hRect.bottom - cRect.top - 1)));
  }, []);

  useEffect(() => {
    updateProgressTop();
    window.addEventListener('resize', updateProgressTop);
    const c = containerRef.current;
    const h = tableHeadRef.current;
    const ro =
      typeof ResizeObserver !== 'undefined' && (c || h)
        ? new ResizeObserver(() => updateProgressTop())
        : null;
    if (ro) {
      if (c) ro.observe(c);
      if (h) ro.observe(h);
    }
    return () => {
      window.removeEventListener('resize', updateProgressTop);
      ro?.disconnect();
    };
  }, [updateProgressTop]);

  useEffect(() => {
    const id = window.requestAnimationFrame(() => updateProgressTop());
    return () => window.cancelAnimationFrame(id);
  }, [selectedAccountId, updateProgressTop]);

  // ── Infinite scroll ──────────────────────────────────────────────────────────
  useEffect(() => {
    const currentLoader = loaderRef.current;
    if (!currentLoader) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting && hasMore && !loading) onLoadMore();
      },
      { threshold: 0, rootMargin: '200px 0px' },
    );
    observer.observe(currentLoader);
    return () => observer.disconnect();
  }, [hasMore, loading, onLoadMore]);

  // Clean up deletedTransactionId once Apollo removes the row
  useEffect(() => {
    if (!deleteHook.deletedTransactionId) return;
    if (!transactions.some((t) => t.id === deleteHook.deletedTransactionId)) {
      deleteHook.setDeletedTransactionId(null);
    }
  }, [transactions, deleteHook]);

  // ── Stable row callbacks (object identity stable via useMemo) ────────────────
  const rowCallbacks = useMemo<TransactionRowCallbacks>(
    () => ({
      onOpenCategoryPicker: (e: MouseEvent<HTMLElement>, tx: Transaction) =>
        categoryPicker.openPicker(e, tx),
      onOpenActionMenu: (el: HTMLElement, tx: Transaction) => actionMenu.open(el, tx),
      onOpenNfceDocument,
      onOpenAttachmentPreview: attachmentPreview.openPreview,
      onEditClose: closeEdit,
      onEditExited: clearEdit,
      onEditSuccess: async () => { await onSuccessCreate?.(); },
      onEditRefresh: () => { void onSuccessCreate?.(); },
      onBusyStart: beginBusy,
      onBusyEnd: endBusy,
    }),
    // onSuccessCreate is intentionally omitted from the stable ref — callers use void/await
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [categoryPicker.openPicker, actionMenu.open, onOpenNfceDocument, attachmentPreview.openPreview, closeEdit, clearEdit, beginBusy, endBusy],
  );

  const headCellSx = financeTransactionHeadCellSx(theme);

  return (
    <TableContainer
      ref={containerRef}
      component={Paper}
      elevation={0}
      data-testid="transaction-table"
      sx={{
        bgcolor: 'transparent',
        background: 'transparent !important',
        border: 'none',
        overflow: 'visible',
        position: 'relative',
      }}
    >
      {/* Sticky loading progress bar */}
      <Box
        sx={{
          position: 'absolute',
          left: 0,
          right: 0,
          top: `${progressTop}px`,
          height: 4,
          pointerEvents: 'none',
          zIndex: 2,
        }}
      >
        <LinearProgress
          sx={{
            height: '100%',
            opacity: showHeaderProgress ? 1 : 0,
            transition: 'opacity 150ms linear',
          }}
        />
      </Box>

      <Table
        sx={getFinanceTransactionTableSx(650)}
        aria-label={t('financeTransactions.transactionTable.ariaLabel')}
      >
        <TableHead ref={tableHeadRef} sx={financeTransactionTableHeadSx}>
          <TableRow sx={financeTransactionTableHeadRowSx}>
            <TableCell padding="checkbox" sx={{ pl: 2, ...headCellSx }} />
            <TableCell sx={headCellSx}>
              <Label>{t('financeTransactions.transactionTable.columns.description')}</Label>
            </TableCell>
            <TableCell sx={headCellSx}>
              <Label>{t('financeTransactions.transactionTable.columns.category')}</Label>
            </TableCell>
            <TableCell sx={headCellSx}>
              <Label>{t('financeTransactions.tags')}</Label>
            </TableCell>
            {!selectedAccountId && (
              <TableCell sx={headCellSx}>
                <Label>{t('financeTransactions.financialAccount')}</Label>
              </TableCell>
            )}
            <TableCell padding="none" sx={{ width: 32, ...headCellSx }} />
            <TableCell align="right" sx={headCellSx}>
              <Label>{t('financeTransactions.value')}</Label>
            </TableCell>
            <TableCell sx={{ px: 4, ...headCellSx }} />
          </TableRow>
        </TableHead>

        <TableBody sx={financeTransactionTableBodySx}>
          {onCancelCreate && onSuccessCreate && (
            <InlineTransactionForm
              isOpen={isCreating}
              onClose={onCancelCreate}
              onSuccess={onSuccessCreate}
              onRefresh={onSuccessCreate}
              selectedAccountId={selectedAccountId}
              selectedMonth={selectedMonth}
              selectedYear={selectedYear}
              onBusyStart={beginBusy}
              onBusyEnd={endBusy}
            />
          )}

          {groupedTransactions.map((group, groupIndex) => (
            <Fragment key={`day-group-${group.date}`}>
              {installmentSectionStartIndex === groupIndex && (
                <TableRow
                  key={`installment-section-header-${selectedYear}-${selectedMonth}`}
                  sx={{ bgcolor: 'transparent', borderBottom: 'none' }}
                >
                  <TableCell colSpan={colSpan} sx={{ pt: 4, pb: 1 }}>
                    <Stack spacing={1}>
                      <Label>{t('financeTransactions.installmentPurchasesDueThisMonthHeader')}</Label>
                      <Divider />
                    </Stack>
                  </TableCell>
                </TableRow>
              )}

              <TableRow key={`header-${group.date}`} sx={financeTransactionDateGroupTableRowSx}>
                <TableCell colSpan={colSpan} sx={financeTransactionDateGroupTableCellSx()}>
                  <Label>{formatFinanceDateGroupLabel(group.date, t, i18n.language)}</Label>
                </TableCell>
              </TableRow>

              {group.transactions.map((transaction, index) => {
                const account = !selectedAccountId ? accountsById.get(transaction.financialAccountId) : null;
                const displayedCategory = categoryPicker.forceNoCategoryByTransactionId[transaction.id]
                  ? null
                  : transaction.category;

                return (
                  <TransactionRow
                    key={transaction.id}
                    transaction={transaction}
                    account={account}
                    isFirst={index === 0}
                    isLast={index === group.transactions.length - 1}
                    isEditing={editedTransaction?.id === transaction.id}
                    isEditFormOpen={isEditFormOpen}
                    isDeleting={deleteHook.deletedTransactionId === transaction.id}
                    updatingCategoryTransactionId={categoryPicker.updatingCategoryTransactionId}
                    displayedCategory={displayedCategory}
                    selectedAccountId={selectedAccountId}
                    activeActionMenuTransactionId={actionMenu.menuTransaction?.id ?? null}
                    activeActionMenuAnchorEl={actionMenu.anchorEl}
                    callbacks={rowCallbacks}
                  />
                );
              })}
            </Fragment>
          ))}
        </TableBody>
      </Table>

      {/* Action menu (single instance, shared across all rows) */}
      <TransactionActionMenu
        anchorEl={actionMenu.anchorEl}
        transaction={actionMenu.menuTransaction}
        markingTransferTransactionId={actionMenu.markingTransferTransactionId}
        markingBillPaymentTransactionId={actionMenu.markingBillPaymentTransactionId}
        onClose={actionMenu.close}
        onEdit={(tx) => openEdit(tx)}
        onViewAppliedRules={(tx) => setAppliedRulesTransaction(tx)}
        onCreateRule={(tx) =>
          setCreateRuleDraft({ pattern: tx.description, applicableTo: resolveRuleApplicableTo(tx) })
        }
        recurringPaymentSuggestion={actionMenu.recurringPaymentSuggestion}
        onLinkRecurringPayment={actionMenu.handleLinkRecurringPayment}
        onCreateRecurringPayment={(tx) => {
          setRecurringSourceTx(tx);
          setRecurringFormOpen(true);
        }}
        onMarkAsTransfer={actionMenu.handleMarkAsInternalTransfer}
        onMarkAsBillPayment={actionMenu.handleMarkAsBillPayment}
        onDelete={(tx) => deleteHook.promptDelete(tx)}
      />

      {/* All dialogs/drawers */}
      <TransactionDialogs
        categoryPickerAnchorEl={categoryPicker.anchorEl}
        categoryPickerTransaction={categoryPicker.pickerTransaction}
        onCloseCategoryPicker={categoryPicker.close}
        onSelectCategory={categoryPicker.handleSelectCategory}
        appliedRulesTransaction={appliedRulesTransaction}
        onCloseAppliedRules={() => setAppliedRulesTransaction(null)}
        createRuleDraft={createRuleDraft}
        onCloseCreateRule={() => setCreateRuleDraft(null)}
        onSuccess={onSuccessCreate}
        transactionToRemove={deleteHook.transactionToRemove}
        removing={deleteHook.removing}
        onCancelDelete={deleteHook.cancelDelete}
        onConfirmDelete={deleteHook.confirmDelete}
        pendingSimilarEditPrompt={categoryPicker.pendingSimilarEditPrompt}
        applyingSimilarEdit={similarEdit.applyingSimilarEdit}
        onDismissSimilarEdit={similarEdit.handleDismissSimilarEditPrompt}
        onConfirmSimilarEdit={similarEdit.handleConfirmSimilarEdit}
        previewAttachments={attachmentPreview.previewAttachments}
        previewIndex={attachmentPreview.previewIndex}
        removingAttachmentId={attachmentPreview.removingAttachmentId}
        onRemoveAttachment={attachmentPreview.handleRemoveAttachment}
        onClosePreview={attachmentPreview.closePreview}
      />

      <RecurringPaymentForm
        open={recurringFormOpen}
        fromTransaction={recurringSourceTx}
        onClose={() => {
          setRecurringFormOpen(false);
          setRecurringSourceTx(null);
        }}
        onSaved={async () => {
          await onSuccessCreate?.();
        }}
      />

      {hasMore && (
        <Box ref={loaderRef} sx={{ display: 'flex', justifyContent: 'center', p: 2, minHeight: 50 }} />
      )}

      {!loading && transactions.length === 0 && (
        <Box sx={{ p: 4, textAlign: 'center' }}>
          <Typography color="text.secondary">
            {t('financeTransactions.transactionTable.emptyState')}
          </Typography>
        </Box>
      )}
    </TableContainer>
  );
}
