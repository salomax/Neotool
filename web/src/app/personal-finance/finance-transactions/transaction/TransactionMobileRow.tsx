'use client';

import { Box, IconButton, Stack, Tooltip, Typography, alpha, useTheme } from '@mui/material';
import AttachFileIcon from '@mui/icons-material/AttachFile';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import { memo, useCallback, useEffect, useRef, useState, type KeyboardEvent, type MouseEvent } from 'react';
import {
  CreditDebitType,
  TransactionType,
  type Transaction,
} from '@/lib/graphql/types/__generated__/graphql';
import { Amount, TransactionDirectionBadge } from '@/shared/components/ui/data-display';
import { LABEL_FONT_SIZE, LABEL_LINE_HEIGHT } from '@/shared/components/ui/primitives/Label';
import { useTranslation, i18n } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import {
  CategoryChip,
  type CategoryChipDecorationAlpha,
} from '../category/CategoryChip';
import { InstallmentProgress } from './InstallmentProgress';
import { TransactionAccountCell } from './TransactionAccountCell';
import { TransactionTagList } from './TransactionTagList';
import type { SharedTransactionRowProps } from './TransactionRow';

const CATEGORY_CHIP_DECORATION_ALPHA: CategoryChipDecorationAlpha = {
  filledBackground: 0.03,
  filledBorder: 0.12,
  filledHoverBackground: 0.12,
  placeholderBackground: 0.012,
  placeholderInsetBorder: 0.04,
  placeholderHoverBackground: 0.03,
};

interface TransactionMobileRowProps extends SharedTransactionRowProps {
  activeActionMenuTransactionId: string | null;
  activeActionMenuAnchorEl: HTMLElement | null;
  showDivider: boolean;
}

function shouldShowInstallments(transaction: Transaction) {
  const installmentNumber = transaction.installmentNumber ?? null;
  const totalInstallments = transaction.totalInstallments ?? null;
  const visible =
    transaction.creditDebitType === CreditDebitType.Debit &&
    typeof installmentNumber === 'number' &&
    typeof totalInstallments === 'number' &&
    totalInstallments > 1 &&
    installmentNumber >= 1;

  return {
    visible,
    installmentNumber,
    totalInstallments,
  };
}

export const TransactionMobileRow = memo(function TransactionMobileRow({
  transaction,
  account,
  isEditing,
  isEditFormOpen,
  isDeleting,
  updatingCategoryTransactionId,
  displayedCategory,
  selectedAccountId,
  activeActionMenuTransactionId,
  activeActionMenuAnchorEl,
  showDivider,
  callbacks,
}: TransactionMobileRowProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const descriptionRef = useRef<HTMLSpanElement | null>(null);
  const [isDescriptionMultiline, setIsDescriptionMultiline] = useState(false);

  const { visible: showInstallments, installmentNumber, totalInstallments } =
    shouldShowInstallments(transaction);
  const isBalanceAdjustment = transaction.type === TransactionType.BalanceAdjustment;
  const isBillPayment = transaction.type === TransactionType.BillPayment;
  const isActionMenuOpen =
    Boolean(activeActionMenuAnchorEl) && activeActionMenuTransactionId === transaction.id;
  const hasVisibleAccount = !selectedAccountId && Boolean(account);
  const shouldLeftAlignCategory = !hasVisibleAccount && isDescriptionMultiline;

  useEffect(() => {
    const element = descriptionRef.current;
    if (!element) return;

    const updateLineState = () => {
      const lineHeight = Number.parseFloat(window.getComputedStyle(element).lineHeight);
      if (!Number.isFinite(lineHeight) || lineHeight <= 0) {
        setIsDescriptionMultiline(false);
        return;
      }
      setIsDescriptionMultiline(element.scrollHeight > lineHeight * 1.5);
    };

    updateLineState();

    const observer =
      typeof ResizeObserver !== 'undefined' ? new ResizeObserver(() => updateLineState()) : null;
    observer?.observe(element);
    window.addEventListener('resize', updateLineState);

    return () => {
      observer?.disconnect();
      window.removeEventListener('resize', updateLineState);
    };
  }, [transaction.description]);

  const handleOpenActionMenu = useCallback(
    (element: HTMLElement) => {
      callbacks.onOpenActionMenu(element, transaction);
    },
    [callbacks, transaction],
  );

  const handleRowClick = useCallback(
    (event: MouseEvent<HTMLDivElement>) => {
      handleOpenActionMenu(event.currentTarget);
    },
    [handleOpenActionMenu],
  );

  const handleRowKeyDown = useCallback(
    (event: KeyboardEvent<HTMLDivElement>) => {
      if (event.key !== 'Enter' && event.key !== ' ') return;
      event.preventDefault();
      handleOpenActionMenu(event.currentTarget);
    },
    [handleOpenActionMenu],
  );

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
  }, [callbacks, transaction]);

  const handleNfceClick = useCallback(() => {
    callbacks.onOpenNfceDocument?.(transaction.id);
  }, [callbacks, transaction.id]);

  const stopPropagation = useCallback((event: MouseEvent<HTMLElement>) => {
    event.stopPropagation();
  }, []);

  return (
    <Box
      data-testid={`transaction-mobile-row-${transaction.id}`}
      role="button"
      tabIndex={0}
      onClick={handleRowClick}
      onKeyDown={handleRowKeyDown}
      aria-label={t('financeTransactions.transactionTable.rowActions.ariaLabel')}
      aria-haspopup="dialog"
      aria-expanded={isActionMenuOpen ? 'true' : undefined}
      sx={{
        bgcolor: 'background.paper',
        borderRadius: 0,
        px: 2,
        py: 1.5,
        cursor: 'pointer',
        borderBottom: showDivider ? '1px solid' : 'none',
        borderColor: 'divider',
        opacity: isDeleting ? 0 : isEditing && isEditFormOpen ? 0.5 : 1,
        pointerEvents: (isEditing && isEditFormOpen) || isDeleting ? 'none' : 'auto',
        filter: isDeleting || !(isEditing && isEditFormOpen) ? 'none' : 'grayscale(0.5)',
        transition: 'opacity 0.25s ease, filter 0.2s',
        '&:focus-visible': {
          outline: `2px solid ${theme.palette.primary.main}`,
          outlineOffset: 2,
        },
      }}
    >
      <Stack spacing={1} sx={{ minWidth: 0 }}>
        <Stack direction="row" spacing={1.25} alignItems="center" justifyContent="space-between">
          <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0, flex: 1 }}>
            <Box data-testid={`transaction-mobile-row-direction-${transaction.id}`} sx={{ flexShrink: 0 }}>
              <TransactionDirectionBadge
                creditDebitType={transaction.creditDebitType}
                transactionType={transaction.type}
                merchantLogoUrl={transaction.merchant?.logoAsset?.publicUrl}
                merchantName={transaction.merchant?.name}
                size={28}
              />
            </Box>
            <Typography
              ref={descriptionRef}
              variant="body2"
              title={transaction.description}
              sx={{
                fontSize: LABEL_FONT_SIZE,
                overflow: 'hidden',
                display: '-webkit-box',
                WebkitBoxOrient: 'vertical',
                WebkitLineClamp: 2,
                minWidth: 0,
                flex: 1,
                lineHeight: LABEL_LINE_HEIGHT,
              }}
            >
              {transaction.description}
            </Typography>
          </Stack>

          <Box data-testid={`transaction-mobile-row-amount-${transaction.id}`} sx={{ flexShrink: 0, pl: 1 }}>
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
              sx={{ textAlign: 'right', fontSize: LABEL_FONT_SIZE, lineHeight: LABEL_LINE_HEIGHT }}
            />
          </Box>
        </Stack>

        <Stack direction="row" spacing={1.25} alignItems="flex-start" justifyContent="space-between">
          <Box sx={{ minWidth: 0, flex: 1 }}>
            {hasVisibleAccount && account ? (
              <Box data-testid={`transaction-mobile-row-account-${transaction.id}`}>
                <TransactionAccountCell
                  account={account}
                  transaction={transaction}
                  fontSize={LABEL_FONT_SIZE}
                  lineHeight={LABEL_LINE_HEIGHT}
                />
              </Box>
            ) : shouldLeftAlignCategory ? (
              <Box sx={{ minWidth: 0, pt: 0.25 }}>
                {isBalanceAdjustment ? (
                  <Typography
                    variant="body2"
                    data-testid="transaction-mobile-row-balance-adjustment-no-category"
                    sx={{ fontSize: LABEL_FONT_SIZE, lineHeight: LABEL_LINE_HEIGHT, color: 'text.disabled' }}
                  >
                    —
                  </Typography>
                ) : (
                  <Tooltip
                    title={isBillPayment ? t('financeTransactions.category.disabledBillPayment') : ''}
                    disableHoverListener={!isBillPayment}
                  >
                    <Box component="span" sx={{ minWidth: 0 }}>
                      <CategoryChip
                        category={displayedCategory}
                        disabled={updatingCategoryTransactionId === transaction.id || isBillPayment}
                        loading={updatingCategoryTransactionId === transaction.id}
                        decorationAlpha={CATEGORY_CHIP_DECORATION_ALPHA}
                        labelFontSize={LABEL_FONT_SIZE}
                        placeholder={
                          <Typography
                            variant="body2"
                            sx={{ fontSize: LABEL_FONT_SIZE, lineHeight: LABEL_LINE_HEIGHT, opacity: 0.65 }}
                          >
                            {t('financeTransactions.category.label')}
                          </Typography>
                        }
                      />
                    </Box>
                  </Tooltip>
                )}
              </Box>
            ) : null}
          </Box>

          {!shouldLeftAlignCategory ? (
            <Box sx={{ minWidth: 0, maxWidth: '52%', display: 'flex', justifyContent: 'flex-end' }}>
              {isBalanceAdjustment ? (
                <Typography
                  variant="body2"
                  data-testid="transaction-mobile-row-balance-adjustment-no-category"
                  sx={{ fontSize: LABEL_FONT_SIZE, lineHeight: LABEL_LINE_HEIGHT, color: 'text.disabled' }}
                >
                  —
                </Typography>
              ) : (
                <Tooltip
                  title={isBillPayment ? t('financeTransactions.category.disabledBillPayment') : ''}
                  disableHoverListener={!isBillPayment}
                >
                  <Box component="span" sx={{ minWidth: 0 }}>
                    <CategoryChip
                      category={displayedCategory}
                      disabled={updatingCategoryTransactionId === transaction.id || isBillPayment}
                      loading={updatingCategoryTransactionId === transaction.id}
                      decorationAlpha={CATEGORY_CHIP_DECORATION_ALPHA}
                      labelFontSize={LABEL_FONT_SIZE}
                      placeholder={
                        <Typography
                          variant="body2"
                          sx={{ fontSize: LABEL_FONT_SIZE, lineHeight: LABEL_LINE_HEIGHT, opacity: 0.65 }}
                        >
                          {t('financeTransactions.category.label')}
                        </Typography>
                      }
                    />
                  </Box>
                </Tooltip>
              )}
            </Box>
          ) : null}
        </Stack>

        {(showInstallments || !isBalanceAdjustment || transaction.hasNfceDocument || !!transaction.attachments?.length) ? (
          <Stack direction="row" spacing={1.25} alignItems="flex-start" justifyContent="space-between">
            <Box sx={{ minWidth: 0, flex: 1 }}>
              {isBalanceAdjustment ? (
                <Typography
                  variant="body2"
                  data-testid="transaction-mobile-row-balance-adjustment-no-tags"
                  sx={{ fontSize: LABEL_FONT_SIZE, lineHeight: LABEL_LINE_HEIGHT, color: 'text.disabled' }}
                >
                  —
                </Typography>
              ) : (
                <TransactionTagList tags={transaction.tags ?? []} fontSize={LABEL_FONT_SIZE} />
              )}
            </Box>

            <Stack spacing={0.75} alignItems="flex-end" sx={{ flexShrink: 0, maxWidth: '44%' }}>
              {showInstallments ? (
                <Box sx={{ width: '100%', minWidth: 140 }}>
                  <InstallmentProgress
                    installmentNumber={installmentNumber!}
                    totalInstallments={totalInstallments!}
                    fontSize={LABEL_FONT_SIZE}
                    lineHeight={LABEL_LINE_HEIGHT}
                  />
                </Box>
              ) : null}

              <Stack direction="row" spacing={0.25} justifyContent="flex-end" alignItems="center">
                {transaction.hasNfceDocument && callbacks.onOpenNfceDocument ? (
                  <Tooltip title={t('financeTransactions.transactionTable.rowActions.viewNfce')}>
                    <IconButton
                      size="small"
                      sx={{
                        p: 0.25,
                        color: 'text.secondary',
                        '&:hover': {
                          bgcolor: alpha(theme.palette.primary.main, 0.08),
                          color: 'primary.main',
                        },
                      }}
                      onMouseDown={stopPropagation}
                      onClick={(event) => {
                        stopPropagation(event);
                        handleNfceClick();
                      }}
                      aria-label={t('financeTransactions.transactionTable.rowActions.viewNfce')}
                    >
                      <ReceiptLongOutlinedIcon sx={{ fontSize: 16 }} />
                    </IconButton>
                  </Tooltip>
                ) : null}
                {!!transaction.attachments?.length ? (
                  <IconButton
                    size="small"
                    sx={{
                      p: 0.25,
                      color: 'text.secondary',
                      '&:hover': {
                        bgcolor: alpha(theme.palette.primary.main, 0.08),
                        color: 'primary.main',
                      },
                    }}
                    onMouseDown={stopPropagation}
                    onClick={(event) => {
                      stopPropagation(event);
                      handleOpenAttachments();
                    }}
                    aria-label={t('financeTransactions.transactionTable.attachments.ariaLabel')}
                  >
                    <AttachFileIcon sx={{ fontSize: 16 }} />
                  </IconButton>
                ) : null}
              </Stack>
            </Stack>
          </Stack>
        ) : null}
      </Stack>
    </Box>
  );
});
