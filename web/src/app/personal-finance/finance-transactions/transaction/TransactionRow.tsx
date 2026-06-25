'use client';

import type { FinancialAccountsQuery } from '@/lib/graphql/operations/banking/queries.generated';
import {
  IconButton,
  Stack,
  TableCell,
  TableRow,
  Tooltip,
  Typography,
  alpha,
  useTheme,
} from '@mui/material';
import AttachFileIcon from '@mui/icons-material/AttachFile';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import { Fragment, memo, useCallback, useMemo, type MouseEvent } from 'react';
import {
  CreditDebitType,
  TransactionType,
} from '@/lib/graphql/types/__generated__/graphql';
import type { Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { useTranslation, i18n } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { Amount, TransactionDirectionBadge } from '@/shared/components/ui/data-display';
import { FINANCE_TABLE_BODY_FONT_SIZE } from '@/shared/components/ui/data-display/Table';
import {
  financeTransactionBodyRowSx,
} from '../../utils/financeTransactionTableStyles';
import { CategoryChip, type CategoryChipDecorationAlpha } from '../category/CategoryChip';
import { InlineTransactionForm } from './InlineTransactionForm';
import { InstallmentProgress } from './InstallmentProgress';
import { TransactionTagList } from './TransactionTagList';
import { TransactionAccountCell } from './TransactionAccountCell';
import { TRANSACTION_ACTIONS_MENU_ID } from './TransactionActionMenu';
import type { PreviewAttachment } from '../../components/FilePreviewDialog';
import { getTransactionTableHighlightSx } from './transactionHighlightStyles';
import type { TransactionSuccessPayload } from './transactionFormShared';

const CATEGORY_CHIP_DECORATION_ALPHA: CategoryChipDecorationAlpha = {
  filledBackground: 0.03,
  filledBorder: 0.12,
  filledHoverBackground: 0.12,
  placeholderBackground: 0.012,
  placeholderInsetBorder: 0.04,
  placeholderHoverBackground: 0.03,
};

export interface TransactionRowCallbacks {
  onOpenCategoryPicker: (event: MouseEvent<HTMLElement>, transaction: Transaction) => void;
  onOpenActionMenu: (el: HTMLElement, transaction: Transaction) => void;
  onOpenNfceDocument?: (transactionId: string) => void;
  onOpenAttachmentPreview: (transactionId: string, attachments: PreviewAttachment[]) => void;
  onEditClose: () => void;
  onEditExited: () => void;
  onEditSuccess: (payload?: TransactionSuccessPayload) => void | Promise<void>;
  onEditRefresh: () => void;
  onBusyStart: () => void;
  onBusyEnd: () => void;
}

interface TransactionRowProps {
  transaction: Transaction;
  account: NonNullable<FinancialAccountsQuery['financialAccounts']>[number] | null | undefined;
  isFirst: boolean;
  isLast: boolean;
  isEditing: boolean;
  isEditFormOpen: boolean;
  isDeleting: boolean;
  updatingCategoryTransactionId: string | null;
  displayedCategory: Transaction['category'];
  selectedAccountId: string | null;
  activeActionMenuTransactionId: string | null;
  activeActionMenuAnchorEl: HTMLElement | null;
  isTemporarilyHighlighted?: boolean;
  callbacks: TransactionRowCallbacks;
}

export type SharedTransactionRowProps = Omit<
  TransactionRowProps,
  'isFirst' | 'isLast' | 'activeActionMenuTransactionId' | 'activeActionMenuAnchorEl'
>;

export const TransactionRow = memo(function TransactionRow({
  transaction,
  account,
  isFirst,
  isLast,
  isEditing,
  isEditFormOpen,
  isDeleting,
  updatingCategoryTransactionId,
  displayedCategory,
  selectedAccountId,
  activeActionMenuTransactionId,
  activeActionMenuAnchorEl,
  isTemporarilyHighlighted = false,
  callbacks,
}: TransactionRowProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();

  const rowSx = useMemo(
    () => financeTransactionBodyRowSx(theme, { isFirst, isLast }),
    [theme, isFirst, isLast],
  );

  const installmentNumber = transaction.installmentNumber ?? null;
  const totalInstallments = transaction.totalInstallments ?? null;
  const showInstallments =
    transaction.creditDebitType === CreditDebitType.Debit &&
    typeof installmentNumber === 'number' &&
    typeof totalInstallments === 'number' &&
    totalInstallments > 1 &&
    installmentNumber >= 1;
  const isBalanceAdjustment = transaction.type === TransactionType.BalanceAdjustment;
  const isBillPayment = transaction.type === TransactionType.BillPayment;
  const isActionMenuOpen =
    Boolean(activeActionMenuAnchorEl) && activeActionMenuTransactionId === transaction.id;

  const handleOpenAttachments = useCallback(() => {
    const list = (transaction.attachments ?? []).map((att) => ({
      id: att.id,
      assetId: att.assetId,
      filename: (att as any).originalFilename ?? att.filename ?? null,
      mimeType: att.mimeType ?? null,
      downloadUrl: att.downloadUrl ?? null,
    }));
    if (list.length > 0) {
      callbacks.onOpenAttachmentPreview(transaction.id, list);
    }
  }, [transaction.id, transaction.attachments, callbacks]);

  const handleOpenActionMenu = useCallback(
    (e: MouseEvent<HTMLButtonElement>) => {
      callbacks.onOpenActionMenu(e.currentTarget, transaction);
    },
    [transaction, callbacks],
  );

  const handleOpenCategoryPicker = useCallback(
    (e: MouseEvent<HTMLElement>) => {
      callbacks.onOpenCategoryPicker(e, transaction);
    },
    [transaction, callbacks],
  );

  const handleNfceClick = useCallback(() => {
    callbacks.onOpenNfceDocument?.(transaction.id);
  }, [transaction.id, callbacks]);

  return (
    <Fragment>
      <TableRow
        data-testid={`transaction-row-${transaction.id}`}
        tabIndex={-1}
        sx={{
          ...rowSx,
          ...getTransactionTableHighlightSx(theme, isTemporarilyHighlighted),
          opacity: isDeleting ? 0 : isEditing && isEditFormOpen ? 0.5 : 1,
          pointerEvents: (isEditing && isEditFormOpen) || isDeleting ? 'none' : 'auto',
          filter: isDeleting || !(isEditing && isEditFormOpen) ? 'none' : 'grayscale(0.5)',
          transition: 'opacity 0.25s ease, filter 0.2s',
          ...(isDeleting && {
            '& .MuiTableCell-root': { py: 0, height: 0, border: 'none' },
          }),
        }}
      >
        {/* Direction badge / merchant logo */}
        <TableCell padding="checkbox" sx={{ pl: 2 }}>
          <TransactionDirectionBadge
            creditDebitType={transaction.creditDebitType}
            transactionType={transaction.type}
            merchantLogoUrl={transaction.merchant?.logoAsset?.publicUrl}
            merchantName={transaction.merchant?.name}
          />
        </TableCell>

        {/* Description + installment progress */}
        <TableCell component="th" scope="row">
          <Stack spacing={0.5}>
            <Typography
              variant="body2"
              title={transaction.description}
              sx={{
                fontSize: FINANCE_TABLE_BODY_FONT_SIZE,
                display: '-webkit-box',
                WebkitBoxOrient: 'vertical',
                WebkitLineClamp: 2,
                maxWidth: 320,
                overflow: 'hidden',
              }}
            >
              {transaction.description}
            </Typography>
            {showInstallments && (
              <InstallmentProgress
                installmentNumber={installmentNumber!}
                totalInstallments={totalInstallments!}
              />
            )}
          </Stack>
        </TableCell>

        {/* Category */}
        <TableCell>
          {isBalanceAdjustment ? (
            <Typography
              variant="body2"
              data-testid="transaction-row-balance-adjustment-no-category"
              sx={{ fontSize: FINANCE_TABLE_BODY_FONT_SIZE, color: 'text.disabled' }}
            >
              —
            </Typography>
          ) : (
            <Tooltip
              title={isBillPayment ? t('financeTransactions.category.disabledBillPayment') : ''}
              disableHoverListener={!isBillPayment}
            >
              <span>
                <CategoryChip
                  category={displayedCategory}
                  disabled={updatingCategoryTransactionId === transaction.id || isBillPayment}
                  loading={updatingCategoryTransactionId === transaction.id}
                  onClick={isBillPayment ? undefined : handleOpenCategoryPicker}
                  decorationAlpha={CATEGORY_CHIP_DECORATION_ALPHA}
                  placeholder={
                    <Typography variant="body2" sx={{ fontSize: FINANCE_TABLE_BODY_FONT_SIZE, opacity: 0.65 }}>
                      {t('financeTransactions.category.label')}
                    </Typography>
                  }
                />
              </span>
            </Tooltip>
          )}
        </TableCell>

        {/* Tags */}
        <TableCell>
          {isBalanceAdjustment ? (
            <Typography
              variant="body2"
              data-testid="transaction-row-balance-adjustment-no-tags"
              sx={{ fontSize: FINANCE_TABLE_BODY_FONT_SIZE, color: 'text.disabled' }}
            >
              —
            </Typography>
          ) : (
            <TransactionTagList tags={transaction.tags ?? []} />
          )}
        </TableCell>

        {/* Account (only in "all accounts" view) */}
        {!selectedAccountId && (
          <TableCell>
            {account && (
              <TransactionAccountCell account={account} transaction={transaction} />
            )}
          </TableCell>
        )}

        {/* Inline action icons (NF-e, attachments) */}
        <TableCell align="center" padding="none" sx={{ width: 56, maxWidth: 56 }}>
          <Stack direction="row" spacing={0.25} justifyContent="center" alignItems="center">
            {transaction.hasNfceDocument && callbacks.onOpenNfceDocument && (
              <Tooltip title={t('financeTransactions.transactionTable.rowActions.viewNfce')}>
                <IconButton
                  size="small"
                  sx={{
                    p: 0.25,
                    color: 'text.secondary',
                    '&:hover': { bgcolor: alpha(theme.palette.primary.main, 0.08), color: 'primary.main' },
                  }}
                  onClick={handleNfceClick}
                  aria-label={t('financeTransactions.transactionTable.rowActions.viewNfce')}
                >
                  <ReceiptLongOutlinedIcon sx={{ fontSize: 16 }} />
                </IconButton>
              </Tooltip>
            )}
            {!!transaction.attachments?.length && (
              <IconButton
                size="small"
                sx={{
                  p: 0.25,
                  color: 'text.secondary',
                  '&:hover': { bgcolor: alpha(theme.palette.primary.main, 0.08), color: 'primary.main' },
                }}
                onClick={handleOpenAttachments}
                aria-label={t('financeTransactions.transactionTable.attachments.ariaLabel')}
              >
                <AttachFileIcon sx={{ fontSize: 16 }} />
              </IconButton>
            )}
          </Stack>
        </TableCell>

        {/* Amount */}
        <TableCell align="right">
          <Amount
            value={
              transaction.creditDebitType === CreditDebitType.Debit
                ? -Math.abs(transaction.amount)
                : Math.abs(transaction.amount)
            }
            currency={transaction.currencyCode}
            locale={i18n.language}
            tone={
              transaction.type === TransactionType.Transfer
                ? 'transfer'
                : transaction.creditDebitType === CreditDebitType.Debit
                  ? 'negative'
                  : 'positive'
            }
          />
        </TableCell>

        {/* Row actions menu trigger */}
        <TableCell align="center" padding="none" sx={{ width: 30, maxWidth: 30 }}>
          <IconButton
            size="small"
            sx={{ p: 0.5 }}
            onClick={handleOpenActionMenu}
            id={`transaction-actions-button-${transaction.id}`}
            aria-label={t('financeTransactions.transactionTable.rowActions.ariaLabel')}
            aria-haspopup="menu"
            aria-expanded={isActionMenuOpen ? 'true' : undefined}
            aria-controls={isActionMenuOpen ? TRANSACTION_ACTIONS_MENU_ID : undefined}
          >
            <MoreVertIcon fontSize="small" />
          </IconButton>
        </TableCell>
      </TableRow>

      {/* Inline edit form rendered below the row */}
      {isEditing && (
        <InlineTransactionForm
          isOpen={isEditFormOpen && !isDeleting}
          onClose={callbacks.onEditClose}
          onSuccess={async (payload) => { await callbacks.onEditSuccess(payload); }}
          onRefresh={() => { void callbacks.onEditRefresh(); }}
          selectedAccountId={selectedAccountId}
          initialValues={transaction}
          onExited={callbacks.onEditExited}
          onBusyStart={callbacks.onBusyStart}
          onBusyEnd={callbacks.onBusyEnd}
        />
      )}
    </Fragment>
  );
});
