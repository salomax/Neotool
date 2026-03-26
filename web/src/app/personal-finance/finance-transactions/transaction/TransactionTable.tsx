'use client';

import { 
  Table, 
  TableBody, 
  TableCell, 
  TableContainer, 
  TableHead, 
  TableRow, 
  Paper, 
  Chip, 
  Typography, 
  Box, 
  CircularProgress,
  LinearProgress,
  useTheme,
  IconButton,
  Stack,
  alpha,
  Avatar,
  Tooltip,
  Menu,
  MenuItem,
  Divider,
  ListItemIcon,
  ListItemText,
} from '@mui/material';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import EditIcon from '@mui/icons-material/Edit';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import { AccountBalance as BankIcon } from '@mui/icons-material';
import { CardNetworkIcon } from '@/shared/components/ui/icons/CardNetworkIcon';
import { Transaction, CreditDebitType, FinancialAccount, FinancialAccountSubtype, FinancialAccountType, Merchant, TransactionType } from '@/lib/graphql/types/__generated__/graphql';
import { useTranslation, i18n } from '@/shared/i18n';
import { useCallback, useEffect, useRef, useMemo, useState, Fragment, type MouseEvent } from 'react';
import { personalFinanceTranslations } from '../../i18n';
import { Label } from '@/shared/components/ui/primitives/Label';
import { ConfirmationDialog } from '@/shared/components/ui/feedback';
import AttachFileIcon from '@mui/icons-material/AttachFile';
import { FilePreviewDialog, type PreviewAttachment } from '../../components/FilePreviewDialog';

import { InlineTransactionForm } from './InlineTransactionForm';
import { CategoryIcon } from '../category/CategoryIcon';
import { CategoryPicker } from '../category/CategoryPicker';
import { useMutation } from '@apollo/client/react';
import { DELETE_TRANSACTION, REMOVE_TRANSACTION_ATTACHMENT, UPDATE_TRANSACTION } from '@/lib/graphql/operations/banking/mutations';
import { useToast } from '@/shared/providers';
import { extractErrorMessage } from '@/shared/utils/error';
import { FINANCE_TABLE_BODY_FONT_SIZE, FINANCE_TABLE_TYPOGRAPHY_SX } from '@/shared/components/ui/data-display/Table';
import { Amount } from '@/shared/components/ui/data-display';

interface TransactionTableProps {
  transactions: Transaction[];
  financialAccounts?: FinancialAccount[];
  loading: boolean;
  hasMore: boolean;
  onLoadMore: () => void;
  isCreating?: boolean;
  onCancelCreate?: () => void;
  onSuccessCreate?: () => void;
  selectedAccountId?: string | null;
  selectedMonth?: number;
  selectedYear?: number;
}

type DateParts = {
  year: number;
  month: number;
  day: number;
  dateKey: string;
  sortKey: number;
};

const TAG_COLORS = [
  '#F44336',
  '#E91E63',
  '#9C27B0',
  '#673AB7',
  '#3F51B5',
  '#2196F3',
  '#03A9F4',
  '#00BCD4',
  '#009688',
  '#4CAF50',
  '#8BC34A',
  '#CDDC39',
  '#FFEB3B',
  '#FFC107',
  '#FF9800',
  '#FF5722',
  '#795548',
  '#607D8B',
];

function stringToColor(value: string) {
  let hash = 0;
  for (let i = 0; i < value.length; i++) {
    hash = value.charCodeAt(i) + ((hash << 5) - hash);
  }
  const index = Math.abs(hash % TAG_COLORS.length);
  return TAG_COLORS[index]!;
}

function parseDateParts(value: string): DateParts | null {
  const dateKey = (value ?? '').split('T')[0] ?? '';
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateKey);
  if (!match) return null;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  if (!year || !month || !day) return null;
  return { year, month, day, dateKey, sortKey: year * 10000 + month * 100 + day };
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
  selectedAccountId = null,
  selectedMonth,
  selectedYear,
}: TransactionTableProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const toast = useToast();
  const loaderRef = useRef<HTMLDivElement>(null);
  const transactionsRef = useRef<Transaction[]>(transactions);
  const borderRadius = '.3rem';
  const tableBodyLabelFontSize = FINANCE_TABLE_BODY_FONT_SIZE;
  const [editedTransaction, setEditedTransaction] = useState<Transaction | null>(null);
  const [isEditFormOpen, setIsEditFormOpen] = useState(false);
  const [deletedTransactionId, setDeletedTransactionId] = useState<string | null>(null);
  const [busyCount, setBusyCount] = useState(0);
  const beginBusy = useCallback(() => setBusyCount((c) => c + 1), []);
  const endBusy = useCallback(() => setBusyCount((c) => (c > 0 ? c - 1 : 0)), []);
  const [actionMenuAnchorEl, setActionMenuAnchorEl] = useState<HTMLElement | null>(null);
  const [actionMenuTransaction, setActionMenuTransaction] = useState<Transaction | null>(null);
  const [transactionToRemove, setTransactionToRemove] = useState<Transaction | null>(null);
  const [markingTransferTransactionId, setMarkingTransferTransactionId] = useState<string | null>(null);
  const [categoryPickerAnchorEl, setCategoryPickerAnchorEl] = useState<HTMLElement | null>(null);
  const [categoryPickerTransaction, setCategoryPickerTransaction] = useState<Transaction | null>(null);
  const [updatingCategoryTransactionId, setUpdatingCategoryTransactionId] = useState<string | null>(null);
  const [forceNoCategoryByTransactionId, setForceNoCategoryByTransactionId] = useState<Record<string, true>>({});
  const [previewAttachments, setPreviewAttachments] = useState<PreviewAttachment[] | null>(null);
  const [previewIndex, setPreviewIndex] = useState<number>(0);
  const [previewTransactionId, setPreviewTransactionId] = useState<string | null>(null);
  const [removingAttachmentId, setRemovingAttachmentId] = useState<string | null>(null);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const tableHeadRef = useRef<HTMLTableSectionElement | null>(null);
  const [progressTop, setProgressTop] = useState<number>(0);
  const showHeaderProgress = loading || busyCount > 0;

  const [deleteTransaction, { loading: removing }] = useMutation(DELETE_TRANSACTION);
  const [updateTransaction] = useMutation(UPDATE_TRANSACTION);
  const [removeTransactionAttachment] = useMutation(REMOVE_TRANSACTION_ATTACHMENT);

  useEffect(() => {
    transactionsRef.current = transactions;
  }, [transactions]);

  const updateProgressTop = useCallback(() => {
    const c = containerRef.current;
    const h = tableHeadRef.current;
    if (!c || !h) return;
    const cRect = c.getBoundingClientRect();
    const hRect = h.getBoundingClientRect();
    const top = Math.max(0, Math.round(hRect.bottom - cRect.top - 1));
    setProgressTop(top);
  }, []);

  useEffect(() => {
    updateProgressTop();
    window.addEventListener('resize', updateProgressTop);

    const c = containerRef.current;
    const h = tableHeadRef.current;
    const ResizeObserverImpl = typeof ResizeObserver === 'undefined' ? null : ResizeObserver;
    const ro =
      ResizeObserverImpl && (c || h)
        ? new ResizeObserverImpl(() => {
            updateProgressTop();
          })
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

  useEffect(() => {
    setForceNoCategoryByTransactionId((prev) => {
      const ids = Object.keys(prev);
      if (ids.length === 0) return prev;

      let changed = false;
      const next = { ...prev };
      for (const tx of transactions) {
        if (!next[tx.id]) continue;
        if (!tx.category) {
          delete next[tx.id];
          changed = true;
        }
      }
      return changed ? next : prev;
    });
  }, [transactions]);

  const closeActionMenu = useCallback(() => {
    setActionMenuAnchorEl(null);
    setActionMenuTransaction(null);
  }, []);

  useEffect(() => {
    if (!actionMenuTransaction) return;
    if (transactions.some((t) => t.id === actionMenuTransaction.id)) return;
    closeActionMenu();
  }, [actionMenuTransaction, closeActionMenu, transactions]);

  const handleConfirmRemove = async () => {
    const tx = transactionToRemove;
    if (!tx) return;

    try {
      beginBusy();
      await deleteTransaction({ variables: { id: tx.id } });

      setTransactionToRemove(null);
      setDeletedTransactionId(tx.id);

      if (editedTransaction?.id === tx.id) {
        setIsEditFormOpen(false);
        setEditedTransaction(null);
      }

      onSuccessCreate?.();
    } catch (error) {
      setTransactionToRemove(null);
      toast.error(extractErrorMessage(error, 'Não foi possível remover a transação'));
    } finally {
      endBusy();
    }
  };

  const handleMarkAsInternalTransfer = async () => {
    const tx = actionMenuTransaction;
    if (!tx) return;
    if (tx.type === TransactionType.Transfer) return;

    setMarkingTransferTransactionId(tx.id);
    try {
      beginBusy();
      await updateTransaction({
        variables: {
          id: tx.id,
          input: { type: TransactionType.Transfer },
        },
      });

      toast.success('Transação marcada como transferência interna');
      onSuccessCreate?.();
      closeActionMenu();
    } catch (err) {
      toast.error(extractErrorMessage(err, 'Não foi possível marcar como transferência interna'));
    } finally {
      setMarkingTransferTransactionId(null);
      endBusy();
    }
  };

  const closeCategoryPicker = useCallback(() => {
    setCategoryPickerAnchorEl(null);
    setCategoryPickerTransaction(null);
  }, []);

  const handleOpenCategoryPicker = (event: MouseEvent<HTMLElement>, transaction: Transaction) => {
    setCategoryPickerAnchorEl(event.currentTarget);
    const isForcedNoCategory = Boolean(forceNoCategoryByTransactionId[transaction.id]);
    if (!isForcedNoCategory) {
      setCategoryPickerTransaction(transaction);
      return;
    }

    setCategoryPickerTransaction({
      ...transaction,
      category: null,
    });
  };

  const handleSelectCategory = (categoryId: string | null) => {
    const tx = categoryPickerTransaction;
    closeCategoryPicker();
    if (!tx) return;

    const hadForcedNoCategory = Boolean(forceNoCategoryByTransactionId[tx.id]);
    if (categoryId === null) {
      setForceNoCategoryByTransactionId((prev) => ({ ...prev, [tx.id]: true }));
    } else if (hadForcedNoCategory) {
      setForceNoCategoryByTransactionId((prev) => {
        const next = { ...prev };
        delete next[tx.id];
        return next;
      });
    }

    setUpdatingCategoryTransactionId(tx.id);
    beginBusy();
    updateTransaction({
      variables: {
        id: tx.id,
        input: { categoryId },
      },
    })
      .then(() => {
        toast.success('Categoria atualizada');
        onSuccessCreate?.();

        if (categoryId === null) {
          window.setTimeout(() => {
            setForceNoCategoryByTransactionId((prev) => {
              if (!prev[tx.id]) return prev;
              const latest = transactionsRef.current.find((t) => t.id === tx.id);
              if (latest?.category) {
                const next = { ...prev };
                delete next[tx.id];
                return next;
              }
              return prev;
            });
          }, 2500);
        }
      })
      .catch((err) => {
        if (categoryId === null) {
          setForceNoCategoryByTransactionId((prev) => {
            if (!prev[tx.id]) return prev;
            const next = { ...prev };
            delete next[tx.id];
            return next;
          });
        }
        toast.error(extractErrorMessage(err, 'Não foi possível atualizar a categoria'));
      })
      .finally(() => {
        setUpdatingCategoryTransactionId(null);
        endBusy();
      });
  };

  const groupedTransactions = useMemo(() => {
    const groups: { date: string; transactions: Transaction[] }[] = [];
    const sorted = transactions
      .map((transaction) => {
        const parts = parseDateParts(transaction.date as string);
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
      const parts = parseDateParts(groupedTransactions[i]?.date ?? '');
      if (!parts) continue;
      const groupMonthKey = parts.year * 100 + parts.month;
      if (groupMonthKey < selectedMonthKey) {
        return i > 0 ? i : null;
      }
    }
    return null;
  }, [groupedTransactions, selectedMonth, selectedYear]);

  const dateHeaderFormatter = new Intl.DateTimeFormat(i18n.language, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  });

  const formatDateHeader = (dateString: string) => {
    if (!dateString) return '';
    
    const parts = dateString.split('-').map(Number);
    if (parts.length < 3) return dateString;
    
    const year = parts[0];
    const month = parts[1];
    const day = parts[2];
    
    if (year === undefined || month === undefined || day === undefined) return dateString;

    const date = new Date(year, month - 1, day);
    
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    
    const tomorrow = new Date(today);
    tomorrow.setDate(tomorrow.getDate() + 1);
    
    const isToday = date.getTime() === today.getTime();
    const isTomorrow = date.getTime() === tomorrow.getTime();
    
    const formattedDate = dateHeaderFormatter.format(date);

    if (isToday) {
      return t('financeTransactions.dateGroup.today', { date: formattedDate });
    }
    if (isTomorrow) {
      return t('financeTransactions.dateGroup.tomorrow', { date: formattedDate });
    }
    return formattedDate;
  };

  useEffect(() => {
    const currentLoader = loaderRef.current;
    if (!currentLoader) return;

    const observer = new IntersectionObserver(
      (entries) => {
        const entry = entries[0];
        if (entry?.isIntersecting && hasMore && !loading) {
          onLoadMore();
        }
      },
      { threshold: 0, rootMargin: '200px 0px' }
    );

    observer.observe(currentLoader);

    return () => {
      observer.disconnect();
    };
  }, [hasMore, loading, onLoadMore]);

  useEffect(() => {
    if (!deletedTransactionId) return;
    const stillPresent = transactions.some((t) => t.id === deletedTransactionId);
    if (!stillPresent) {
      setDeletedTransactionId(null);
    }
  }, [transactions, deletedTransactionId]);

 

  const getCardLastFourDigits = (account: FinancialAccount, transaction: Transaction) => {
    const raw = transaction.cardIdentifier || account.cardData?.maskedNumber || '';
    const digits = raw.replace(/\D/g, '');
    const last4 = digits.slice(-4);
    return last4.length === 4 ? last4 : null;
  };

  const isCardAccount = (account: FinancialAccount) => {
    if (account.cardData) return true;
    return (
      account.subtype === FinancialAccountSubtype.CreditCard ||
      account.subtype === FinancialAccountSubtype.ChargeCard ||
      account.subtype === FinancialAccountSubtype.PrepaidCard ||
      account.subtype === FinancialAccountSubtype.VoucherCard
    );
  };

  const getTypeIconProps = (creditDebitType: CreditDebitType, transactionType: TransactionType) => {
    if (transactionType === TransactionType.Transfer) {
      return { icon: <SwapHorizIcon fontSize="inherit" />, color: theme.palette.info.main };
    }
    if (creditDebitType === CreditDebitType.Credit) {
      return { icon: <ArrowUpwardIcon fontSize="inherit" />, color: theme.palette.success.main };
    }
    if (creditDebitType === CreditDebitType.Debit) {
      return { icon: <ArrowDownwardIcon fontSize="inherit" />, color: theme.palette.error.main };
    }
    return null;
  };

  const renderTransactionIcon = (
    creditDebitType: CreditDebitType,
    transactionType: TransactionType,
    merchant: Pick<Merchant, 'name' | 'logoAsset'> | null | undefined,
  ) => {
    const typeProps = getTypeIconProps(creditDebitType, transactionType);
    const logoUrl = merchant?.logoAsset?.publicUrl;

    if (logoUrl) {
      return (
        <Box sx={{ position: 'relative', width: 32, height: 32, flexShrink: 0 }}>
          <Avatar
            src={logoUrl}
            alt={merchant?.name ?? ''}
            variant="rounded"
            sx={{
              width: 32,
              height: 32,
              borderRadius: '.3rem',
            }}
          />
          {typeProps && (
            <Box
              sx={{
                position: 'absolute',
                bottom: -8,
                right: -8,
                width: 18,
                height: 18,
                border: 'none',
                borderRadius: borderRadius,
                display: 'flex',
                bgcolor: `color-mix(in srgb, ${typeProps.color} 5%, ${theme.palette.background.paper})`,
                alignItems: 'center',
                justifyContent: 'center',
                color: typeProps.color,
                fontSize: 16,
              }}
            >
              {typeProps.icon}
            </Box>
          )}
        </Box>
      );
    }

    if (!typeProps) return null;
    return (
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 32,
          height: 32,
          borderRadius: '.3rem',
          bgcolor: alpha(typeProps.color, 0.05),
          color: typeProps.color,
          fontSize: 20,
        }}
      >
        {typeProps.icon}
      </Box>
    );
  };

  const accountsById = useMemo(() => {
    const map = new Map<string, FinancialAccount>();
    for (const account of financialAccounts ?? []) {
      map.set(account.id, account);
    }
    return map;
  }, [financialAccounts]);

  const colSpan = selectedAccountId ? 7 : 8;

  return (
    <TableContainer
      ref={containerRef}
      component={Paper}
      elevation={0}
      sx={{
        bgcolor: 'transparent',
        background: 'transparent !important',
        border: 'none',
        overflow: 'visible',
        position: 'relative',
      }}
    >
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
      <Table sx={{ minWidth: 650, borderCollapse: 'separate', borderSpacing: '0', ...FINANCE_TABLE_TYPOGRAPHY_SX }} aria-label="transactions table">
        <TableHead ref={tableHeadRef}>
          <TableRow>
            <TableCell padding="checkbox" sx={{ pl: 2, borderBottom: `1px solid ${theme.palette.divider}`, bgcolor: 'transparent' }}></TableCell>
            <TableCell sx={{ borderBottom: `1px solid ${theme.palette.divider}`, bgcolor: 'transparent' }}>
              <Label>{t('financeTransactions.transactionTable.columns.description')}</Label>
            </TableCell>
            <TableCell sx={{ borderBottom: `1px solid ${theme.palette.divider}`, bgcolor: 'transparent' }}>
              <Label>{t('financeTransactions.transactionTable.columns.category')}</Label>
            </TableCell>
            <TableCell sx={{ borderBottom: `1px solid ${theme.palette.divider}`, bgcolor: 'transparent' }}>
              <Label>{t('financeTransactions.tags')}</Label>
            </TableCell>
            {!selectedAccountId && (
              <TableCell sx={{ borderBottom: `1px solid ${theme.palette.divider}`, bgcolor: 'transparent' }}>
                <Label>{t('financeTransactions.financialAccount')}</Label>
              </TableCell>
            )}
            <TableCell
              padding="none"
              sx={{
                width: 32,
                borderBottom: `1px solid ${theme.palette.divider}`,
                bgcolor: 'transparent',
              }}
            />
            <TableCell align="right" sx={{ borderBottom: `1px solid ${theme.palette.divider}`, bgcolor: 'transparent' }}>
              <Label>{t('financeTransactions.value')}</Label>
            </TableCell>
            <TableCell
              sx={{
                px: 4,
                borderBottom: `1px solid ${theme.palette.divider}`,
                bgcolor: 'transparent',
              }}
            />
          </TableRow>
        </TableHead>
        <TableBody>
          {onCancelCreate && onSuccessCreate && (
            <InlineTransactionForm 
              isOpen={isCreating} 
              onClose={onCancelCreate} 
              onSuccess={onSuccessCreate}
              onRefresh={onSuccessCreate}
              selectedAccountId={selectedAccountId}
              onBusyStart={beginBusy}
              onBusyEnd={endBusy}
            />
          )}
          {groupedTransactions.map((group, groupIndex) => (
              <Fragment key={`day-group-${group.date}`}>
                {installmentSectionStartIndex === groupIndex && (
                  <TableRow key={`installment-section-header-${selectedYear}-${selectedMonth}`} sx={{ bgcolor: 'transparent', borderBottom: 'none' }}>
                    <TableCell colSpan={colSpan} sx={{ pt: 4, pb: 1 }}>
                      <Stack spacing={1}>
                        <Label>{t('financeTransactions.installmentPurchasesDueThisMonthHeader')}</Label>
                        <Divider />
                      </Stack>
                    </TableCell>
                  </TableRow>
                )}
                <TableRow key={`header-${group.date}`} sx={{ bgcolor: 'transparent', borderBottom: 'none' }}>
                  <TableCell colSpan={colSpan} sx={{ pt: 2, pb: 1 }} >
                    <Label>
                      {formatDateHeader(group.date)}
                    </Label>
                  </TableCell>
                </TableRow>
                {group.transactions.map((transaction, index) => {
                const isEditing = editedTransaction?.id === transaction.id;
                const isDeleting = deletedTransactionId === transaction.id;
                const isFirst = index === 0;
                const isLast = index === group.transactions.length - 1;
                const account = !selectedAccountId ? accountsById.get(transaction.financialAccountId) : null;
                const installmentNumber = transaction.installmentNumber ?? null;
                const totalInstallments = transaction.totalInstallments ?? null;
                const showInstallments =
                  transaction.creditDebitType === CreditDebitType.Debit &&
                  typeof installmentNumber === 'number' &&
                  typeof totalInstallments === 'number' &&
                  totalInstallments > 1 &&
                  installmentNumber >= 1;
                const displayedCategory = forceNoCategoryByTransactionId[transaction.id]
                  ? null
                  : transaction.category;
                
                return (
                  <Fragment key={transaction.id}>
                    <TableRow
                      sx={{ 
                        '&:last-child td, &:last-child th': { border: 0 },
                        opacity: isDeleting ? 0 : (isEditing && isEditFormOpen ? 0.5 : 1),
                        pointerEvents: (isEditing && isEditFormOpen) || isDeleting ? 'none' : 'auto',
                        filter: isDeleting ? 'none' : (isEditing && isEditFormOpen ? 'grayscale(0.5)' : 'none'),
                        transition: 'opacity 0.25s ease, filter 0.2s',
                        ...(isDeleting && {
                          '& .MuiTableCell-root': {
                            py: 0,
                            height: 0,
                            border: 'none',
                          },
                        }),
                        '& .MuiTableCell-root': {
                          bgcolor: 'background.paper',
                          borderColor: 'transparent',
                        },
                        '& .MuiTableCell-root:first-child': {
                          borderTopLeftRadius: isFirst ? '12px' : 0,
                          borderBottomLeftRadius: isLast ? '12px' : 0,
                        },
                        '& .MuiTableCell-root:last-child': {
                          borderTopRightRadius: isFirst ? '12px' : 0,
                          borderBottomRightRadius: isLast ? '12px' : 0,
                        }
                      }}
                    >
                      <TableCell padding="checkbox" sx={{ pl: 2 }}>
                        {renderTransactionIcon(transaction.creditDebitType, transaction.type, transaction.merchant)}
                      </TableCell>
                      <TableCell component="th" scope="row">
                        <Stack spacing={0.5}>
                          <Typography
                            variant="body2"
                            title={transaction.description}
                            sx={{
                              fontSize: tableBodyLabelFontSize,
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
                            <Stack spacing={0.5} sx={{ maxWidth: 200 }}>
                              <Typography variant="caption" color="text.secondary">
                                {installmentNumber} of {totalInstallments}
                              </Typography>
                              <Box
                                role="progressbar"
                                aria-valuemin={1}
                                aria-valuemax={totalInstallments}
                                aria-valuenow={Math.min(Math.max(installmentNumber, 1), totalInstallments)}
                                sx={{
                                  display: 'flex',
                                  gap: '2px',
                                  height: 6,
                                }}
                              >
                                {Array.from({ length: totalInstallments }, (_, i) => {
                                  const idx = i + 1;
                                  const isCompleted = idx < installmentNumber;
                                  const isCurrent = idx === installmentNumber;

                                  const filledColor = theme.palette.primary.main;
                                  const bgColor = isCompleted
                                    ? alpha(filledColor, 0.55)
                                    : isCurrent
                                      ? filledColor
                                      : alpha(theme.palette.text.primary, 0.08);

                                  return (
                                    <Box
                                      key={idx}
                                      sx={{
                                        flex: 1,
                                        minWidth: 4,
                                        bgcolor: bgColor,
                                        borderRadius:
                                          idx === 1
                                            ? '6px 0 0 6px'
                                            : idx === totalInstallments
                                              ? '0 6px 6px 0'
                                              : 2,
                                      }}
                                    />
                                  );
                                })}
                              </Box>
                            </Stack>
                          )}
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <Box
                          component="span"
                          sx={{
                            display: 'inline-block',
                            maxWidth: '100%',
                            '& .MuiChip-root': {
                              maxWidth: '100%',
                            },
                          }}
                        >
                          {displayedCategory ? (
                            <Chip
                              clickable
                              disabled={updatingCategoryTransactionId === transaction.id}
                              onClick={(e) => handleOpenCategoryPicker(e, transaction)}
                              icon={
                                updatingCategoryTransactionId === transaction.id
                                  ? <CircularProgress size={14} />
                                  : undefined
                              }
                              label={
                                <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0 }}>
                                  <CategoryIcon
                                    iconName={displayedCategory.icon}
                                    color={displayedCategory.color ?? undefined}
                                    size={18}
                                    sx={{ flexShrink: 0 }}
                                  />
                                  <Typography
                                    variant="body2"
                                    title={displayedCategory.name}
                                    sx={{
                                      fontSize: tableBodyLabelFontSize,
                                      overflow: 'hidden',
                                      textOverflow: 'ellipsis',
                                      whiteSpace: 'nowrap',
                                      minWidth: 0,
                                      maxWidth: '22ch',
                                    }}
                                  >
                                    {displayedCategory.name}
                                  </Typography>
                                </Stack>
                              }
                              variant="filled"
                              sx={{
                                height: 28,
                                color: displayedCategory.color || 'text.primary',
                                bgcolor: displayedCategory.color
                                  ? alpha(displayedCategory.color, 0.1)
                                  : 'action.selected',
                                borderColor: displayedCategory.color
                                  ? alpha(displayedCategory.color, 0.3)
                                  : 'divider',
                                borderWidth: 1,
                                borderStyle: 'solid',
                                '&:hover': {
                                  bgcolor: displayedCategory.color
                                    ? alpha(displayedCategory.color, 0.2)
                                    : 'action.hover',
                                },
                                transition: 'background-color 0.15s ease',
                                '& .MuiChip-label': { minWidth: 0, maxWidth: '100%' },
                              }}
                            />
                          ) : (
                            <Chip
                              clickable
                              disabled={updatingCategoryTransactionId === transaction.id}
                              onClick={(e) => handleOpenCategoryPicker(e, transaction)}
                              icon={
                                updatingCategoryTransactionId === transaction.id
                                  ? <CircularProgress size={14} />
                                  : undefined
                              }
                              label={
                                <Typography variant="body2" sx={{ fontSize: tableBodyLabelFontSize, opacity: 0.65 }}>
                                  {t('financeTransactions.category')}
                                </Typography>
                              }
                              variant="outlined"
                              sx={{
                                height: 28,
                                borderStyle: 'dashed',
                                color: 'text.secondary',
                                bgcolor: alpha(theme.palette.text.primary, 0.02),
                                boxShadow: `inset 0 0 0 1px ${alpha(theme.palette.text.primary, 0.06)}`,
                                '&:hover': {
                                  borderColor: 'primary.main',
                                  color: 'primary.main',
                                  bgcolor: alpha(theme.palette.primary.main, 0.05),
                                },
                                transition: 'background-color 0.15s ease',
                              }}
                            />
                          )}
                        </Box>
                      </TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={0.5} alignItems="center">
                          {transaction.tags?.slice(0, 2).map((tag: string, tagIndex) => {
                            if (!tag) return null;
                            const color = stringToColor(tag);
                            return (
                              <Chip
                                key={`${tag}-${tagIndex}`}
                                label={<Typography variant="body2">{tag}</Typography>}
                                size="small"
                                variant="filled"
                                sx={{ 
                                  bgcolor: alpha(color, 0.05),
                                  color: color,
                                  borderRadius: borderRadius,
                                }}
                              />
                            );
                          })}
                          {transaction.tags?.length > 2 && (
                            <Tooltip 
                              title={
                                <Stack spacing={0.5} sx={{ p: 0.5 }}>
                                  {transaction.tags.map((tag: string, tagIndex) => {
                                    if (!tag) return null;
                                    return (
                                      <Typography key={`${tag}-${tagIndex}`} variant="caption" sx={{ color: 'inherit' }}>
                                        • {tag}
                                      </Typography>
                                    );
                                  })}
                                </Stack>
                              }
                              arrow
                            >
                              <Typography 
                                variant="caption" 
                                color="primary" 
                                sx={{ 
                                  cursor: 'pointer',
                                  fontSize: '0.7rem',
                                  fontWeight: 'medium',
                                  '&:hover': { textDecoration: 'underline' }
                                }}
                              >
                                +{transaction.tags.length - 2}
                              </Typography>
                            </Tooltip>
                          )}
                        </Stack>
                      </TableCell>
                      {!selectedAccountId && (
                        <TableCell>
                          {account && (() => {
                            const issuer = account.issuer;
                            const logoUrl = issuer?.logoAsset?.publicUrl;
                            const last4 = isCardAccount(account) ? getCardLastFourDigits(account, transaction) : null;

                            return (
                              <Stack direction="row" spacing={1} alignItems="center">
                                <Avatar 
                                  src={logoUrl || undefined}
                                  sx={{ 
                                    width: 26, 
                                    height: 26,
                                    fontSize: '0.75rem',
                                    bgcolor: logoUrl ? 'transparent' : theme.palette.grey[300],
                                    borderRadius: borderRadius
                                  }}
                                >
                                  {!logoUrl && (account.type === FinancialAccountType.Bank ? <BankIcon sx={{ fontSize: '1rem' }} /> : <CardNetworkIcon network={account.cardData?.cardNetwork} size={16} />)}
                                </Avatar>
                                <Stack spacing={0} sx={{ minWidth: 0 }}>
                                  <Typography variant="body2" sx={{ fontSize: tableBodyLabelFontSize, lineHeight: 1.25 }} noWrap>
                                    {account.name}
                                  </Typography>
                                  <Stack direction="row" spacing={0.5} alignItems="center">
                                    <CardNetworkIcon network={account.cardData?.cardNetwork} size={18} />
                                    {last4 && (
                                      <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
                                        •••• {last4}
                                      </Typography>
                                    )}
                                  </Stack>
                                </Stack>
                              </Stack>
                            );
                          })()}
                        </TableCell>
                      )}
                      <TableCell align="center" padding="none" sx={{ width: 32, maxWidth: 32 }}>
                        {!!transaction.attachments?.length && (
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
                            onClick={() => {
                              const list = (transaction.attachments ?? []).map((att) => ({
                                id: att.id,
                                assetId: att.assetId,
                                filename: (att as any).originalFilename ?? att.filename ?? null,
                                mimeType: att.mimeType ?? null,
                                downloadUrl: att.downloadUrl ?? null,
                              }));
                              if (list.length > 0) {
                                const firstWithUrlIndex = list.findIndex((a) => !!a.downloadUrl);
                                setRemovingAttachmentId(null);
                                setPreviewTransactionId(transaction.id);
                                setPreviewAttachments(list);
                                setPreviewIndex(firstWithUrlIndex >= 0 ? firstWithUrlIndex : 0);
                              }
                            }}
                            aria-label="Ver anexo"
                          >
                            <AttachFileIcon sx={{ fontSize: 16 }} />
                          </IconButton>
                        )}
                      </TableCell>
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
                      <TableCell
                        align="center"
                        padding="none"
                        sx={{
                          width: 30,
                          maxWidth: 30,
                        }}
                      >
                        <IconButton
                          size="small"
                          sx={{ p: 0.5 }}
                          onClick={(e) => {
                            setActionMenuAnchorEl(e.currentTarget);
                            setActionMenuTransaction(transaction);
                          }}
                          aria-label={t('financeTransactions.transactionTable.rowActions.ariaLabel')}
                        >
                          <MoreVertIcon fontSize="small" />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                    {isEditing && (
                        <InlineTransactionForm
                          isOpen={isEditFormOpen && !isDeleting}
                          onClose={() => setIsEditFormOpen(false)}
                          onSuccess={() => {
                            setIsEditFormOpen(false);
                            if (onSuccessCreate) onSuccessCreate();
                          }}
                          onRefresh={() => {
                            if (onSuccessCreate) onSuccessCreate();
                          }}
                          selectedAccountId={selectedAccountId}
                          initialValues={transaction}
                          onExited={() => setEditedTransaction(null)}
                          onBusyStart={beginBusy}
                          onBusyEnd={endBusy}
                        />
                      )}
                  </Fragment>
                );
              })}
              </Fragment>
            ))}
        </TableBody>
      </Table>

      <Menu
        open={Boolean(actionMenuAnchorEl)}
        anchorEl={actionMenuAnchorEl}
        onClose={closeActionMenu}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <MenuItem
          onClick={() => {
            const tx = actionMenuTransaction;
            closeActionMenu();
            if (!tx) return;
            setEditedTransaction(tx);
            setIsEditFormOpen(true);
          }}
          disabled={markingTransferTransactionId === actionMenuTransaction?.id}
        >
          <ListItemIcon>
            <EditIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t('financeTransactions.transactionTable.rowActions.edit')}</ListItemText>
        </MenuItem>
        {actionMenuTransaction?.type !== TransactionType.Transfer && (
          <MenuItem
            onClick={handleMarkAsInternalTransfer}
            disabled={markingTransferTransactionId === actionMenuTransaction?.id}
          >
            <ListItemIcon>
              {markingTransferTransactionId === actionMenuTransaction?.id ? (
                <CircularProgress size={18} />
              ) : (
                <SwapHorizIcon color="info" fontSize="small" />
              )}
            </ListItemIcon>
            <ListItemText>Marcar como transferência interna</ListItemText>
          </MenuItem>
        )}
        <Divider />
        <MenuItem
          onClick={() => {
            const tx = actionMenuTransaction;
            closeActionMenu();
            if (!tx) return;
            setTransactionToRemove(tx);
          }}
          sx={{
            color: 'error.main',
            '&:hover': {
              bgcolor: alpha(theme.palette.error.main, 0.08),
            },
            '&.Mui-focusVisible': {
              bgcolor: alpha(theme.palette.error.main, 0.08),
            },
          }}
          disabled={markingTransferTransactionId === actionMenuTransaction?.id}
        >
          <ListItemIcon sx={{ color: 'inherit' }}>
            <DeleteOutlineIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t('financeTransactions.transactionTable.rowActions.remove')}</ListItemText>
        </MenuItem>
      </Menu>

      <CategoryPicker
        value={categoryPickerTransaction?.category?.id ?? null}
        onChange={handleSelectCategory}
        creditDebitType={
          categoryPickerTransaction?.type === TransactionType.Transfer
            ? 'TRANSFER'
            : categoryPickerTransaction?.creditDebitType === CreditDebitType.Credit
              ? 'CREDIT'
              : 'DEBIT'
        }
        anchorEl={categoryPickerAnchorEl}
        onClose={closeCategoryPicker}
      />

      <ConfirmationDialog
        open={Boolean(transactionToRemove)}
        onClose={() => setTransactionToRemove(null)}
        onConfirm={handleConfirmRemove}
        title={t('financeTransactions.deleteConfirmation.title')}
        message={t('financeTransactions.deleteConfirmation.message')}
        cancelText={t('actions.cancel')}
        confirmText={t('actions.delete')}
        confirmColor="error"
        loading={removing}
      />
      
      {hasMore && (
        <Box 
          ref={loaderRef} 
          sx={{ display: 'flex', justifyContent: 'center', p: 2, minHeight: 50 }}
        />
      )}
      
      {!loading && transactions.length === 0 && (
        <Box sx={{ p: 4, textAlign: 'center' }}>
          <Typography color="text.secondary">
            {t('financeTransactions.transactionTable.emptyState')}
          </Typography>
        </Box>
      )}
      
      <FilePreviewDialog
        attachments={previewAttachments}
        initialIndex={previewIndex}
        removingAttachmentId={removingAttachmentId}
        onRemove={async (att) => {
          if (!previewTransactionId) return;
          setRemovingAttachmentId(att.id);
          try {
            beginBusy();
            await removeTransactionAttachment({ variables: { attachmentId: att.id } });
            setPreviewAttachments((prev) => {
              if (!prev) return prev;
              const next = prev.filter((a) => a.id !== att.id);
              return next.length > 0 ? next : null;
            });
            onSuccessCreate?.();
          } catch (err) {
            toast.error(extractErrorMessage(err, 'Não foi possível remover o anexo'));
          } finally {
            setRemovingAttachmentId(null);
            endBusy();
          }
        }}
        onClose={() => {
          setPreviewAttachments(null);
          setPreviewTransactionId(null);
          setRemovingAttachmentId(null);
        }}
      />
    </TableContainer>
  );
}
