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
  useTheme,
  IconButton,
  Stack,
  alpha,
  Avatar,
  Tooltip,
  Menu,
  MenuItem,
  ListItemIcon,
  ListItemText,
} from '@mui/material';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import EditIcon from '@mui/icons-material/Edit';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import { AccountBalance as BankIcon, CreditCard as CardIcon } from '@mui/icons-material';
import { Transaction, CreditDebitType, FinancialAccount, FinancialAccountSubtype, FinancialAccountType, TransactionType } from '@/lib/graphql/types/__generated__/graphql';
import { useTranslation, i18n } from '@/shared/i18n';
import { useEffect, useRef, useMemo, useState, Fragment } from 'react';
import { personalFinanceTranslations } from '../../i18n';
import { Label } from '@/shared/components/ui/primitives/Label';
import { ConfirmationDialog } from '@/shared/components/ui/feedback';

import { InlineTransactionForm } from './InlineTransactionForm';
import { CategoryIcon } from './category/CategoryIcon';
import { useMutation } from '@apollo/client/react';
import { DELETE_TRANSACTION } from '@/lib/graphql/operations/banking/mutations';

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
}

type DateParts = {
  year: number;
  month: number;
  day: number;
  dateKey: string;
  sortKey: number;
};

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
}: TransactionTableProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const loaderRef = useRef<HTMLDivElement>(null);
  const borderRadius = '.3rem';
  const [editedTransaction, setEditedTransaction] = useState<Transaction | null>(null);
  const [isEditFormOpen, setIsEditFormOpen] = useState(false);
  const [deletedTransactionId, setDeletedTransactionId] = useState<string | null>(null);
  const [actionMenuAnchorEl, setActionMenuAnchorEl] = useState<HTMLElement | null>(null);
  const [actionMenuTransaction, setActionMenuTransaction] = useState<Transaction | null>(null);
  const [transactionToRemove, setTransactionToRemove] = useState<Transaction | null>(null);

  const [deleteTransaction, { loading: removing }] = useMutation(DELETE_TRANSACTION);

  const closeActionMenu = () => {
    setActionMenuAnchorEl(null);
    setActionMenuTransaction(null);
  };

  const handleConfirmRemove = async () => {
    const tx = transactionToRemove;
    if (!tx) return;

    try {
      await deleteTransaction({ variables: { id: tx.id } });

      setTransactionToRemove(null);
      setDeletedTransactionId(tx.id);
      await new Promise((resolve) => setTimeout(resolve, 300));
      setDeletedTransactionId(null);

      if (editedTransaction?.id === tx.id) {
        setIsEditFormOpen(false);
        setEditedTransaction(null);
      }

      onSuccessCreate?.();
    } catch (error) {
      setTransactionToRemove(null);
    }
  };

  const stringToColor = (string: string) => {
    let hash = 0;
    for (let i = 0; i < string.length; i++) {
      hash = string.charCodeAt(i) + ((hash << 5) - hash);
    }
    const colors = [
      '#F44336', '#E91E63', '#9C27B0', '#673AB7', '#3F51B5', 
      '#2196F3', '#03A9F4', '#00BCD4', '#009688', '#4CAF50', 
      '#8BC34A', '#CDDC39', '#FFEB3B', '#FFC107', '#FF9800', 
      '#FF5722', '#795548', '#607D8B'
    ];
    const index = Math.abs(hash % colors.length);
    return colors[index];
  };

  const groupedTransactions = useMemo(() => {
    const groups: { date: string; transactions: Transaction[] }[] = [];
    [...transactions]
      .sort((a, b) => {
        const sortA = parseDateParts(a.date as string)?.sortKey ?? 0;
        const sortB = parseDateParts(b.date as string)?.sortKey ?? 0;
        return sortB - sortA;
      })
      .forEach((transaction) => {
        const dateKey = parseDateParts(transaction.date as string)?.dateKey ?? (transaction.date as string);
        const lastGroup = groups[groups.length - 1];
        if (lastGroup && lastGroup.date === dateKey) {
          lastGroup.transactions.push(transaction);
        } else {
          groups.push({ date: dateKey, transactions: [transaction] });
        }
      });

    return groups;
  }, [transactions]);

  const formatDateHeader = (dateString: string) => {
    if (!dateString) return '';
    
    // Create date object from YYYY-MM-DD string in local time to avoid timezone shifts
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
    
    const formattedDate = new Intl.DateTimeFormat(i18n.language, { 
      weekday: 'long', 
      day: 'numeric', 
      month: 'long' 
    }).format(date);

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

  const formatCurrency = (amount: number, currencyCode: string) => {
    return new Intl.NumberFormat(i18n.language, {
      style: 'currency',
      currency: currencyCode,
    }).format(amount);
  };

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

  const getTransactionTypeIcon = (creditDebitType: CreditDebitType, transactionType: TransactionType) => {
    let icon = null;
    let color = '';

    if (transactionType === TransactionType.Transfer) {
      icon = <SwapHorizIcon color="info" fontSize="small" />;
      color = theme.palette.info.main;
    } else {
      switch (creditDebitType) {
        case CreditDebitType.Credit:
          icon = <ArrowUpwardIcon color="success" fontSize="small" />;
          color = theme.palette.success.main;
          break;
        case CreditDebitType.Debit:
          icon = <ArrowDownwardIcon color="error" fontSize="small" />;
          color = theme.palette.error.main;
          break;
        default:
          return null;
      }
    }

    return (
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: 32,
          height: 32,
          borderRadius: '.3rem',
          bgcolor: alpha(color, 0.05),
        }}
      >
        {icon}
      </Box>
    );
  };

  const colSpan = selectedAccountId ? 6 : 7;

  return (
    <TableContainer component={Paper} elevation={0} sx={{ bgcolor: 'transparent', background: 'transparent !important', border: 'none', overflow: 'visible' }}>
      <Table sx={{ minWidth: 650, borderCollapse: 'separate', borderSpacing: '0' }} aria-label="transactions table">
        <TableHead>
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
            <TableCell align="right" sx={{ borderBottom: `1px solid ${theme.palette.divider}`, bgcolor: 'transparent' }}>
              <Label>{t('financeTransactions.value')}</Label>
            </TableCell>
            <TableCell align="center" sx={{ borderBottom: `1px solid ${theme.palette.divider}`, bgcolor: 'transparent' }}></TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {onCancelCreate && onSuccessCreate && (
            <InlineTransactionForm 
              isOpen={isCreating} 
              onClose={onCancelCreate} 
              onSuccess={onSuccessCreate}
              selectedAccountId={selectedAccountId}
            />
          )}
          {groupedTransactions.map((group) => (
            <Fragment key={`day-group-${group.date}`}>
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
                const installmentNumber = transaction.installmentNumber ?? null;
                const totalInstallments = transaction.totalInstallments ?? null;
                const showInstallments =
                  transaction.creditDebitType === CreditDebitType.Debit &&
                  typeof installmentNumber === 'number' &&
                  typeof totalInstallments === 'number' &&
                  totalInstallments > 1 &&
                  installmentNumber >= 1;
                
                return (
                  <Fragment key={transaction.id}>
                    <TableRow
                      sx={{ 
                        '&:last-child td, &:last-child th': { border: 0 },
                        opacity: isDeleting ? 0 : (isEditing && isEditFormOpen ? 0.5 : 1),
                        pointerEvents: (isEditing && isEditFormOpen) || isDeleting ? 'none' : 'auto',
                        filter: isDeleting ? 'none' : (isEditing && isEditFormOpen ? 'grayscale(0.5)' : 'none'),
                        transition: 'opacity 0.3s ease, filter 0.2s',
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
                        {getTransactionTypeIcon(transaction.creditDebitType, transaction.type)}
                      </TableCell>
                      <TableCell component="th" scope="row">
                        <Stack spacing={0.5}>
                          <Typography
                            variant="body2"
                            title={transaction.description}
                            sx={{
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
                        {transaction.category && (
                          <Chip 
                            label={
                              <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0 }}>
                                <CategoryIcon
                                  iconName={transaction.category.icon}
                                  color={transaction.category.color ?? undefined}
                                  size={20}
                                  sx={{ flexShrink: 0 }}
                                />
                                <Typography
                                  variant="body2"
                                  title={transaction.category.name}
                                  sx={{
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis',
                                    whiteSpace: 'nowrap',
                                    minWidth: 0,
                                    maxWidth: '22ch',
                                  }}
                                >
                                  {transaction.category.name}
                                </Typography>
                              </Stack>
                            }
                            variant="filled"
                            sx={{ 
                              color: transaction.category.color || 'text.primary',
                              bgcolor: transaction.category.color
                                ? alpha(transaction.category.color, 0.1)
                                : 'action.selected',
                              borderColor: transaction.category.color
                                ? alpha(transaction.category.color, 0.3)
                                : 'divider',
                              borderWidth: 1,
                              borderStyle: 'solid',
                              maxWidth: '100%',
                              '& .MuiChip-label': { minWidth: 0, maxWidth: '100%' },
                            }}
                          />
                        )}
                      </TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={0.5} alignItems="center">
                          {transaction.tags?.slice(0, 2).map((tag: string) => {
                            if (!tag) return null;
                            const color = stringToColor(tag || '');
                            return (
                              <Chip
                                key={tag}
                                label={<Typography variant="body2">{tag}</Typography>}
                                size="small"
                                variant="filled"
                                sx={{ 
                                  bgcolor: alpha(color || '', 0.05),
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
                                  {transaction.tags.map((tag: string) => (
                                    <Typography key={tag} variant="caption" sx={{ color: 'inherit' }}>
                                      • {tag}
                                    </Typography>
                                  ))}
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
                          {(() => {
                            const account = financialAccounts?.find(a => a.id === transaction.financialAccountId);
                            
                            if (!account) return null;

                            const issuer = account.issuer;
                            const logoUrl = issuer?.logoAsset?.publicUrl;
                            const last4 = isCardAccount(account) ? getCardLastFourDigits(account, transaction) : null;

                            return (
                              <Stack direction="row" spacing={1} alignItems="center">
                                <Avatar 
                                  src={logoUrl || undefined}
                                  sx={{ 
                                    width: 24, 
                                    height: 24,
                                    fontSize: '0.75rem',
                                    bgcolor: logoUrl ? 'transparent' : theme.palette.grey[300],
                                    borderRadius: borderRadius
                                  }}
                                >
                                  {!logoUrl && (account.type === FinancialAccountType.Bank ? <BankIcon sx={{ fontSize: '1rem' }} /> : <CardIcon sx={{ fontSize: '1rem' }} />)}
                                </Avatar>
                                <Stack spacing={0} sx={{ minWidth: 0 }}>
                                  <Typography variant="body2" sx={{ lineHeight: 1.25 }} noWrap>
                                    {account.name}
                                  </Typography>
                                  {last4 && (
                                    <Stack direction="row" spacing={0.5} alignItems="center">
                                      <CardIcon sx={{ fontSize: '0.9rem', color: 'text.secondary' }} />
                                      <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.2 }}>
                                        •••• {last4}
                                      </Typography>
                                    </Stack>
                                  )}
                                </Stack>
                              </Stack>
                            );
                          })()}
                        </TableCell>
                      )}
                      <TableCell align="right">
                        <Typography 
                          variant="body2" 
                          color={
                            transaction.type === TransactionType.Transfer
                              ? 'info.main'
                              : transaction.creditDebitType === CreditDebitType.Debit
                                ? 'error.main'
                                : 'success.main'
                          }
                          fontWeight="medium"
                          noWrap
                          sx={{ fontVariantNumeric: 'tabular-nums' }}
                        >
                          {formatCurrency(
                            transaction.creditDebitType === CreditDebitType.Debit 
                              ? -Math.abs(transaction.amount) 
                              : Math.abs(transaction.amount), 
                            transaction.currencyCode
                          )}
                        </Typography>
                      </TableCell>
                      <TableCell align="center">
                        <IconButton
                          size="small"
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
                          selectedAccountId={selectedAccountId}
                          initialValues={transaction}
                          onExited={() => setEditedTransaction(null)}
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
        >
          <ListItemIcon>
            <EditIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t('financeTransactions.transactionTable.rowActions.edit')}</ListItemText>
        </MenuItem>
        <MenuItem
          onClick={() => {
            const tx = actionMenuTransaction;
            closeActionMenu();
            if (!tx) return;
            setTransactionToRemove(tx);
          }}
          sx={{ color: 'error.main' }}
        >
          <ListItemIcon sx={{ color: 'inherit' }}>
            <DeleteOutlineIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t('financeTransactions.transactionTable.rowActions.remove')}</ListItemText>
        </MenuItem>
      </Menu>

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
      
      {/* Loading indicator / Sentinel for infinite scroll */}
      {(hasMore || loading) && (
        <Box 
          ref={loaderRef} 
          sx={{ display: 'flex', justifyContent: 'center', p: 2, minHeight: 50 }}
        >
          {loading && <CircularProgress size={24} />}
        </Box>
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
