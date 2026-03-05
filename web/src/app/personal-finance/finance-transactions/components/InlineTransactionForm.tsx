import React, { useState, useEffect } from 'react';
import { 
  Box, 
  Stack, 
  TextField, 
  MenuItem, 
  Typography, 
  useTheme,
  alpha,
  Avatar,
  TableRow,
  TableCell,
  Select,
  IconButton,
  Tooltip,
  Paper
} from '@mui/material';
import { Button } from '@/shared/components/ui/primitives/Button';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import dayjs from 'dayjs';
import { useQuery, useMutation } from '@apollo/client/react';
import { FINANCIAL_ACCOUNTS, TRANSACTION_CATEGORIES, TRANSACTION_TAGS } from '@/lib/graphql/operations/banking/queries';
import { CREATE_TRANSACTION, UPDATE_TRANSACTION, DELETE_TRANSACTION } from '@/lib/graphql/operations/banking/mutations';
import { 
  FinancialAccount, 
  TransactionCategory, 
  CreditDebitType, 
  TransactionType,
  CategoryApplicableTo,
  Transaction
} from '@/lib/graphql/types/__generated__/graphql';
import { BaseMoneyInput } from '@/shared/components/ui/forms/form/MoneyInput';
import { TagsInput } from '@/shared/components/ui/forms/form/TagsInput';
import { CategorySelect } from './CategorySelect';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions
} from '@mui/material';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';

interface InlineTransactionFormProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  selectedAccountId: string | null;
  initialValues?: Transaction;
  onExited?: () => void;
  isDeleting?: boolean;
  onDeleteSuccess?: () => void;
}

export function InlineTransactionForm({ 
  isOpen, 
  onClose, 
  onSuccess, 
  selectedAccountId, 
  initialValues, 
  onExited,
  isDeleting = false,
  onDeleteSuccess
}: InlineTransactionFormProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const borderRadius = '.3rem';
  const [creditDebitType, setCreditDebitType] = useState<CreditDebitType>(initialValues?.creditDebitType || CreditDebitType.Debit);
  const [amount, setAmount] = useState<number | undefined>(initialValues?.amount ? Math.abs(initialValues.amount) : undefined);
  const [description, setDescription] = useState(initialValues?.description || '');
  const [date, setDate] = useState<dayjs.Dayjs | null>(initialValues?.date ? dayjs(initialValues.date as string) : dayjs());
  const [accountId, setAccountId] = useState<string>(initialValues?.financialAccountId || selectedAccountId || '');
  const [categoryId, setCategoryId] = useState<string>(initialValues?.category?.id || '');
  const [tags, setTags] = useState<string[]>(initialValues?.tags || []);
  
  const { data: accountsData } = useQuery<{ financialAccounts: FinancialAccount[] }>(FINANCIAL_ACCOUNTS);
  const { data: categoriesData } = useQuery<{ transactionCategories: TransactionCategory[] }>(TRANSACTION_CATEGORIES);
  const { data: tagsData } = useQuery<{ transactionTags: { tag: string }[] }>(TRANSACTION_TAGS);
  
  const [createTransaction, { loading: creating }] = useMutation(CREATE_TRANSACTION);
  const [updateTransaction, { loading: updating }] = useMutation(UPDATE_TRANSACTION);
  const [deleteTransaction, { loading: deleting }] = useMutation(DELETE_TRANSACTION);

  const [isDeleteDialogOpen, setIsDeleteDialogOpen] = useState(false);

  useEffect(() => {
    const accounts = accountsData?.financialAccounts;
    if (selectedAccountId) {
      setAccountId(selectedAccountId);
    } else if (accounts && accounts.length === 1 && !initialValues) {
      setAccountId(accounts[0]?.id ?? '');
    }
  }, [selectedAccountId, accountsData, initialValues]);

  // Reset form when opened or initialValues change
  useEffect(() => {
    if (isOpen) {
      if (initialValues) {
        setCreditDebitType(initialValues.creditDebitType);
        setAmount(Math.abs(initialValues.amount));
        setDescription(initialValues.description);
        setDate(dayjs(initialValues.date as string));
        setAccountId(initialValues.financialAccountId);
        setCategoryId(initialValues.category?.id || '');
        setTags(initialValues.tags || []);
      } else {
        if (!amount) setAmount(undefined);
        if (!description) setDescription('');
        if (!date) setDate(dayjs());
        if (!categoryId) setCategoryId('');
        if (!tags.length) setTags([]);
        
        const accounts = accountsData?.financialAccounts;
        if (!selectedAccountId && accounts && accounts.length === 1) {
             setAccountId(accounts[0]?.id ?? '');
        } else if (selectedAccountId) {
            setAccountId(selectedAccountId);
        }
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, initialValues, accountsData, selectedAccountId]);

  // Handle lifecycle for unmounting if needed
  useEffect(() => {
    if (!isOpen && onExited) {
      onExited();
    }
  }, [isOpen, onExited]);

  const handleSave = async () => {
    if (!amount || !description || !date || !accountId || !categoryId) return;

    const formattedAmount = Math.abs(amount);

    try {
      if (initialValues) {
        await updateTransaction({
          variables: {
            id: initialValues.id,
            input: {
              date: date.format('YYYY-MM-DD'),
              amount: formattedAmount,
              description,
              categoryId: categoryId || undefined,
              creditDebitType,
              tags: tags.length > 0 ? tags : undefined,
            }
          }
        });
      } else {
        await createTransaction({
          variables: {
            input: {
              date: date.format('YYYY-MM-DD'),
              amount: formattedAmount,
              description,
              financialAccountId: accountId,
              categoryId: categoryId || undefined,
              creditDebitType,
              type: TransactionType.OtherExpense,
              tags: tags.length > 0 ? tags : undefined,
            }
          }
        });
      }
      
      // Reset form if creating
      if (!initialValues) {
        setAmount(undefined);
        setDescription('');
        setDate(dayjs());
        setCategoryId('');
        setTags([]);
      }
      
      onSuccess();
      onClose();
    } catch (error) {
      console.error('Failed to save transaction:', error);
    }
  };

  const handleDelete = async () => {
    if (!initialValues) return;
    try {
      await deleteTransaction({
        variables: { id: initialValues.id }
      });
      if (onDeleteSuccess) {
        onDeleteSuccess();
      } else {
        onSuccess();
        onClose();
      }
    } catch (error) {
      console.error('Failed to delete transaction:', error);
    } finally {
      setIsDeleteDialogOpen(false);
    }
  };

  const categories = categoriesData?.transactionCategories || [];
  const accounts = accountsData?.financialAccounts || [];
  const filteredCategories = categories.filter(category => {
    if (creditDebitType === CreditDebitType.Credit) {
      return category.applicableTo === CategoryApplicableTo.Income || category.applicableTo === CategoryApplicableTo.Any;
    } else {
      return category.applicableTo === CategoryApplicableTo.Expense || category.applicableTo === CategoryApplicableTo.Any;
    }
  });

  const colSpan = selectedAccountId ? 7 : 8;

  const focusBorderSx = {
    borderBottom: '2px solid transparent',
    transition: 'border-color 0.2s',
    '&.Mui-focused': {
      borderBottom: `2px solid ${theme.palette.primary.main}`,
    }
  };

  // Render nothing if not open (though parent usually handles this)
  if (!isOpen) return null;

  return (
    <>
      <TableRow
        sx={{
          bgcolor: alpha(theme.palette.primary.main, 0.02),
          '& td': { 
            borderBottom: 'none',
            pt: 2,
            pb: 1,
            px: 1
          }
        }}
      >
        {/* 1. Type */}
        <TableCell padding="checkbox" sx={{ verticalAlign: 'top' }}>
          <Select
            value={creditDebitType}
            onChange={(e) => {
              setCreditDebitType(e.target.value as CreditDebitType);
              setCategoryId('');
            }}
            variant="standard"
            disableUnderline
            displayEmpty
            IconComponent={() => null}
            renderValue={(value) => {
              let icon = null;
              let color = theme.palette.info.main;
              
              if (value === CreditDebitType.Debit) {
                icon = <ArrowDownwardIcon fontSize="small" sx={{ color: 'error.main' }} />;
                color = theme.palette.error.main;
              } else if (value === CreditDebitType.Credit) {
                icon = <ArrowUpwardIcon fontSize="small" sx={{ color: 'success.main' }} />;
                color = theme.palette.success.main;
              } else {
                icon = <SwapHorizIcon fontSize="small" sx={{ color: 'info.main' }} />;
              }

              return (
                <Stack direction="row" sx={{ borderRadius: 1, overflow: 'hidden', border: 1, borderColor: 'divider' }}>
                  <Box 
                    sx={{ 
                      display: 'flex', 
                      alignItems: 'center', 
                      justifyContent: 'center',
                      width: 32,
                      height: 32,
                      bgcolor: alpha(color, 0.1),
                    }}
                  >
                    {icon}
                  </Box>
                  <Box 
                    sx={{ 
                      display: 'flex', 
                      alignItems: 'center', 
                      justifyContent: 'center',
                      width: 24,
                      bgcolor: 'background.paper',
                      borderLeft: 1,
                      borderColor: 'divider'
                    }}
                  >
                    <ExpandMoreIcon fontSize="small" color="action" />
                  </Box>
                </Stack>
              );
            }}
            sx={{ 
              minWidth: 'auto',
              '& .MuiSelect-select': { 
                p: 0,
                display: 'flex',
                bgcolor: 'transparent'
              } 
            }}
            MenuProps={{
              PaperProps: { sx: { width: 150 } }
            }}
          >
            <MenuItem value={CreditDebitType.Debit}>
              <Stack direction="row" spacing={1} alignItems="center">
                <ArrowDownwardIcon fontSize="small" color="error" />
                <Typography variant="body2">{t('financeTransactions.debit')}</Typography>
              </Stack>
            </MenuItem>
            <MenuItem value={CreditDebitType.Credit}>
              <Stack direction="row" spacing={1} alignItems="center">
                <ArrowUpwardIcon fontSize="small" color="success" />
                <Typography variant="body2">{t('financeTransactions.credit')}</Typography>
              </Stack>
            </MenuItem>
            <MenuItem value="TRANSFER">
              <Stack direction="row" spacing={1} alignItems="center">
                <SwapHorizIcon fontSize="small" color="info" />
                <Typography variant="body2">Transfer</Typography>
              </Stack>
            </MenuItem>
          </Select>
        </TableCell>

        {/* 2. Description */}
        <TableCell sx={{ verticalAlign: 'top' }}>
          <TextField
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder={t('financeTransactions.description')}
            variant="standard"
            fullWidth
            required
            autoFocus
            InputProps={{ 
              disableUnderline: true,
              sx: focusBorderSx
            }}
            sx={{ '& .MuiInputBase-input': { fontSize: '0.875rem' } }}
          />
        </TableCell>

        {/* 3. Category */}
        <TableCell sx={{ verticalAlign: 'top' }}>
          <CategorySelect
            value={categoryId}
            onChange={setCategoryId}
            categories={filteredCategories}
            placeholder={t('financeTransactions.category')}
          />
        </TableCell>

        {/* 4. Tags */}
        <TableCell sx={{ verticalAlign: 'top', minWidth: 150 }}>
          <TagsInput
            value={tags}
            onChange={setTags}
            options={tagsData?.transactionTags?.map(t => t.tag) || []}
            placeholder={t('financeTransactions.tags')}
            variant="standard"
            InputProps={{ 
              disableUnderline: true,
              sx: focusBorderSx
            }}
          />
        </TableCell>

        {/* 5. Issuer (Conditional) */}
        {!selectedAccountId && (
          <TableCell sx={{ verticalAlign: 'top' }}>
            <TextField
              select
              value={accountId}
              onChange={(e) => setAccountId(e.target.value)}
              variant="standard"
              fullWidth
              InputProps={{ 
                disableUnderline: true,
                sx: focusBorderSx
              }}
              sx={{ minWidth: 120 }}
            >
              {accounts.map((account) => (
                <MenuItem key={account.id} value={account.id}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Avatar 
                      src={account.issuer?.logoAsset?.publicUrl || undefined}
                      sx={{ 
                        width: 20, 
                        height: 20,
                        fontSize: '0.6rem',
                        bgcolor: account.issuer?.logoAsset?.publicUrl ? 'transparent' : (account.themeColor || theme.palette.grey[300]),
                      }}
                    >
                      {!account.issuer?.logoAsset?.publicUrl && account.name.charAt(0)}
                    </Avatar>
                    <Typography variant="body2" noWrap>{account.name}</Typography>
                  </Stack>
                </MenuItem>
              ))}
            </TextField>
          </TableCell>
        )}

        {/* 6. Amount */}
        <TableCell colSpan={2} sx={{ verticalAlign: 'top', minWidth: 100 }}>
          <BaseMoneyInput
            value={amount}
            onChange={(val) => setAmount(val as number | undefined)}
            variant="standard"
            fullWidth
            required
            InputProps={{ 
              disableUnderline: true,
              sx: { 
                fontSize: '0.875rem', 
                fontWeight: 'medium',
                ...focusBorderSx
              },
              onKeyDown: (e: any) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  if (!deleting && amount && description && date && accountId && categoryId) {
                    handleSave();
                  }
                }
              }
            }}
          />
        </TableCell>
      </TableRow>

      {/* Floating Action Bar Row */}
      <TableRow>
        <TableCell 
          colSpan={colSpan} 
          sx={{ 
            p: 0, 
            borderBottom: '1px solid rgba(224, 224, 224, 1)',
            bgcolor: alpha(theme.palette.primary.main, 0.02)
          }}
        >
          <Box sx={{ display: 'flex', justifyContent: 'flex-end', p: 1, pr: 2, alignItems: 'center', gap: 1 }}>
              <DatePicker
                value={date}
                onChange={(newValue) => setDate(newValue)}
                slotProps={{ 
                  textField: { 
                    hiddenLabel: true,
                    size: 'small', 
                    variant: 'standard',
                    InputProps: { 
                      disableUnderline: true,
                      sx: { 
                        flexDirection: 'row-reverse',
                        '& .MuiInputAdornment-root': {
                          ml: 0,
                          mr: 1
                        }
                      }
                    },
                    sx: { 
                      width: 140, 
                      m: 0,
                      mr: 2,
                      ...focusBorderSx,
                      '& .MuiInputBase-input': {
                        textAlign: 'left',
                        fontSize: '0.875rem',
                        p: 0.5
                      }
                    }
                  } 
                }}
              />
              
              {initialValues && (
                <Tooltip title={t('actions.delete')}>
                  <IconButton 
                    onClick={() => setIsDeleteDialogOpen(true)}
                    color="error"
                    size="small"
                  >
                    <DeleteOutlineIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              )}

              <Button 
                onClick={onClose} 
                color="inherit"
                size="small"
                sx={{ 
                  minWidth: 'auto',
                  bgcolor: alpha(theme.palette.text.primary, 0.05),
                  '&:hover': {
                    bgcolor: alpha(theme.palette.text.primary, 0.1),
                  }
                }}
              >
                {t('actions.cancel')}
              </Button>
              
              <Button 
                variant="contained" 
                onClick={handleSave}
                loading={creating || updating}
                size="small"
                disabled={deleting || !amount || !description || !date || !accountId || !categoryId}
                sx={{ boxShadow: theme.shadows[4] }}
              >
                {t('actions.save')}
              </Button>
          </Box>
        </TableCell>
      </TableRow>

      <Dialog
        open={isDeleteDialogOpen}
        onClose={() => setIsDeleteDialogOpen(false)}
      >
        <DialogTitle>{t('financeTransactions.deleteConfirmation.title')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('financeTransactions.deleteConfirmation.message')}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setIsDeleteDialogOpen(false)} color="inherit">
            {t('actions.cancel')}
          </Button>
          <Button onClick={handleDelete} color="error" autoFocus disabled={deleting}>
            {t('actions.delete')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
