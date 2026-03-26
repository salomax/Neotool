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
  Collapse,
} from '@mui/material';
import { Button } from '@/shared/components/ui/primitives/Button';
import { SimilarEditDialog } from './SimilarEditDialog';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import dayjs from 'dayjs';
import { useLazyQuery, useQuery, useMutation } from '@apollo/client/react';
import { FINANCIAL_ACCOUNTS, SIMILAR_TRANSACTION_EDIT_PREVIEW, TRANSACTION_CATEGORIES, TRANSACTION_TAGS } from '@/lib/graphql/operations/banking/queries';
import { APPLY_TRANSACTION_EDIT_TO_SIMILAR, CREATE_TRANSACTION, UPDATE_TRANSACTION } from '@/lib/graphql/operations/banking/mutations';
import { 
  FinancialAccount, 
  TransactionCategory, 
  CreditDebitType,
  TransactionType,
  CategoryApplicableTo,
  Transaction,
} from '@/lib/graphql/types/__generated__/graphql';
import { BaseMoneyInput } from '@/shared/components/ui/forms/form/MoneyInput';
import { TagsInput } from '@/shared/components/ui/forms/form/TagsInput';
import { CategorySelect } from '../category/CategorySelect';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { FINANCE_MOVING_WINDOW_YEARS, getFinanceMinAllowedDate } from '../constants/dateWindow';
import { AttachmentSection } from '../../components/AttachmentSection';

interface InlineTransactionFormProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  onRefresh?: () => void;
  selectedAccountId: string | null;
  initialValues?: Transaction;
  onExited?: () => void;
  onBusyStart?: () => void;
  onBusyEnd?: () => void;
}

type SimilarEditPreview = {
  similarCount: number;
  matchDescription: string;
  supportsFuturePropagation: boolean;
};

type PendingSimilarEditPrompt = {
  transactionId: string;
  similarCount: number;
  matchDescription: string;
  input: {
    description?: string;
    categoryId?: string | null;
    tags?: string[];
  };
};

type ApplySimilarEditResult = {
  applyTransactionEditToSimilar: {
    affectedCount: number;
    futureRuleCreated: boolean;
  };
};

function normalizeTags(values: string[]): string[] {
  return [...new Set(values.map((value) => value.trim()).filter(Boolean))].sort();
}

function shouldPromptForSimilarEdit(
  transaction: Transaction | undefined,
  nextDescription: string,
  nextCategoryId: string | null,
  nextTags: string[],
): boolean {
  if (!transaction) return false;
  const descriptionChanged = transaction.description !== nextDescription;
  const categoryChanged =
    (transaction.category?.id ?? null) !== nextCategoryId && nextCategoryId !== null;
  const tagsChanged =
    JSON.stringify(normalizeTags(transaction.tags ?? [])) !== JSON.stringify(normalizeTags(nextTags));
  return descriptionChanged || categoryChanged || tagsChanged;
}

function buildSimilarEditInput(
  transaction: Transaction,
  nextDescription: string,
  nextCategoryId: string | null,
  nextTags: string[],
): PendingSimilarEditPrompt['input'] {
  const input: PendingSimilarEditPrompt['input'] = {};
  if (transaction.description !== nextDescription) {
    input.description = nextDescription;
  }
  if ((transaction.category?.id ?? null) !== nextCategoryId && nextCategoryId !== null) {
    input.categoryId = nextCategoryId;
  }
  if (
    JSON.stringify(normalizeTags(transaction.tags ?? [])) !== JSON.stringify(normalizeTags(nextTags))
  ) {
    input.tags = nextTags;
  }
  return input;
}

export function InlineTransactionForm({ 
  isOpen, 
  onClose, 
  onSuccess, 
  onRefresh,
  selectedAccountId, 
  initialValues, 
  onExited,
  onBusyStart,
  onBusyEnd
}: InlineTransactionFormProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const borderRadius = '.3rem';
  const minAllowedDate = React.useMemo(() => getFinanceMinAllowedDate(), []);
  const [creditDebitType, setCreditDebitType] = useState<CreditDebitType>(initialValues?.creditDebitType || CreditDebitType.Debit);
  const [amount, setAmount] = useState<number | undefined>(initialValues?.amount ? Math.abs(initialValues.amount) : undefined);
  const [description, setDescription] = useState(initialValues?.description || '');
  const [date, setDate] = useState<dayjs.Dayjs | null>(initialValues?.date ? dayjs(initialValues.date as string) : dayjs());
  const [accountId, setAccountId] = useState<string>(initialValues?.financialAccountId || selectedAccountId || '');
  const [categoryId, setCategoryId] = useState<string | null>(initialValues?.category?.id ?? null);
  const [tags, setTags] = useState<string[]>(initialValues?.tags || []);
  
  const { data: accountsData } = useQuery<{ financialAccounts: FinancialAccount[] }>(FINANCIAL_ACCOUNTS);
  const { data: categoriesData } = useQuery<{ transactionCategories: TransactionCategory[] }>(TRANSACTION_CATEGORIES);
  const { data: tagsData } = useQuery<{ transactionTags: { tag: string }[] }>(TRANSACTION_TAGS);
  
  const [createTransaction, { loading: creating }] = useMutation(CREATE_TRANSACTION);
  const [updateTransaction, { loading: updating }] = useMutation(UPDATE_TRANSACTION);
  const [loadSimilarEditPreview] = useLazyQuery<{ similarTransactionEditPreview: SimilarEditPreview }>(
    SIMILAR_TRANSACTION_EDIT_PREVIEW,
    { fetchPolicy: 'no-cache' },
  );
  const [applyTransactionEditToSimilar, { loading: applyingSimilarEdit }] =
    useMutation<ApplySimilarEditResult>(APPLY_TRANSACTION_EDIT_TO_SIMILAR);
  const [pendingSimilarEditPrompt, setPendingSimilarEditPrompt] = useState<PendingSimilarEditPrompt | null>(null);

  const isDateOutsideWindow = Boolean(date && date.isBefore(minAllowedDate, 'day'));

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
        setCategoryId(initialValues.category?.id ?? null);
        setTags(initialValues.tags || []);
      } else {
        if (!amount) setAmount(undefined);
        if (!description) setDescription('');
        if (!date) setDate(dayjs());
        if (!categoryId) setCategoryId(null);
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
    if (!amount || !description || !date || !accountId) return;
    if (isDateOutsideWindow) return;

    const formattedAmount = Math.abs(amount);
    const updateInput = {
      date: date.format('YYYY-MM-DD'),
      amount: formattedAmount,
      description,
      categoryId,
      creditDebitType,
      tags,
    };

    try {
      onBusyStart?.();
      if (initialValues) {
        const shouldPrompt = shouldPromptForSimilarEdit(initialValues, description, categoryId, tags);
        const previewResult =
          shouldPrompt
            ? await loadSimilarEditPreview({
                variables: { id: initialValues.id },
              })
            : undefined;

        await updateTransaction({
          variables: {
            id: initialValues.id,
            input: updateInput,
          }
        });

        const preview = previewResult?.data?.similarTransactionEditPreview;
        if (preview && preview.supportsFuturePropagation) {
          onRefresh?.();
          setPendingSimilarEditPrompt({
            transactionId: initialValues.id,
            similarCount: preview.similarCount,
            matchDescription: preview.matchDescription,
            input: buildSimilarEditInput(initialValues, description, categoryId, tags),
          });
          return;
        }
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
    } finally {
      onBusyEnd?.();
    }
  };

  const handleConfirmSimilarEdit = async (applyToExisting: boolean, applyToFuture: boolean) => {
    if (!pendingSimilarEditPrompt) return;

    try {
      onBusyStart?.();
      await applyTransactionEditToSimilar({
        variables: {
          id: pendingSimilarEditPrompt.transactionId,
          matchDescription: pendingSimilarEditPrompt.matchDescription,
          input: pendingSimilarEditPrompt.input,
          applyToFuture,
          applyToExisting,
        },
      });
      setPendingSimilarEditPrompt(null);
      onSuccess();
      onClose();
    } catch (error) {
      console.error('Failed to apply transaction edit to similar transactions:', error);
    } finally {
      onBusyEnd?.();
    }
  };

  const handleDismissSimilarEditPrompt = () => {
    setPendingSimilarEditPrompt(null);
    onSuccess();
    onClose();
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

  const inputBottomBorderSx = {
    borderBottom: `1px solid ${theme.palette.divider}`,
    transition: 'border-color 0.2s, border-bottom-width 0.2s',
    '&.Mui-focused': {
      borderBottom: `2px solid ${theme.palette.primary.main}`,
    },
  };

  const [shouldRender, setShouldRender] = useState(isOpen);

  useEffect(() => {
    if (isOpen) setShouldRender(true);
  }, [isOpen]);

  // Render nothing if not open (though parent usually handles this)
  if (!shouldRender) return null;

  const gridTemplateColumns = selectedAccountId
    ? '68px minmax(180px, 1.6fr) minmax(0, .8fr) minmax(auto, .8fr) minmax(auto, 0.8fr) 52px'
    : '68px minmax(180px, 1.6fr) minmax(0, .8fr) minmax(auto, .8fr) minmax(auto, 1.2fr) minmax(140px, 0.8fr) 52px';

  const amountGridColumn = selectedAccountId ? '5 / -1' : '6 / -1';

  return (
    <>
      <TableRow sx={{ position: 'relative', zIndex: 3, }}>
        <TableCell colSpan={colSpan} sx={{ p: 0, borderBottom: 'none', bgcolor: 'transparent' }}>
          <Collapse
            in={isOpen}
            timeout={250}
            appear
            onExited={() => {
              setShouldRender(false);
              onExited?.();
            }}
          >
            <Box
              data-testid="inline-transaction-form"
              sx={{
                borderRadius: 1,
                border: 1,
                borderColor: 'divider',
                boxShadow: `0 10px 26px ${alpha(theme.palette.primary.main, 0.2)}`,
                overflow: 'hidden',
                bgcolor: 'background.paper',
              }}
            >
              <Box
                sx={{
                  display: 'grid',
                  gridTemplateColumns,
                  columnGap: 2.5,
                  alignItems: 'end',
                  px: 1,
                  pt: 2,
                  pb: 1,
                  '& > *': { minWidth: 0 },
                }}
              >
              {/* 1. Type */}
              <Box sx={{ maxWidth: 'fit-content' }}>
                <Select
                  value={creditDebitType}
                  onChange={(e) => {
                    setCreditDebitType(e.target.value as CreditDebitType);
                    setCategoryId('');
                  }}
                  inputProps={{ 'data-testid': 'tx-credit-debit-type-select' }}
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
                      <Typography variant="body2">{t('financeTransactions.transfer')}</Typography>
                    </Stack>
                  </MenuItem>
                </Select>
              </Box>

              {/* 2. Description */}
              <Box sx={{ display: 'flex', alignItems: 'flex-end', width: '100%' }}>
                <TextField
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder={t('financeTransactions.description')}
                  variant="standard"
                  fullWidth
                  required
                  autoFocus
                  multiline
                  inputProps={{ 'data-testid': 'tx-description-input' }}
                  minRows={1}
                  maxRows={4}
                  InputProps={{ 
                    disableUnderline: true,
                    sx: inputBottomBorderSx
                  }}
                  sx={{
                    '& .MuiInputBase-input': { fontSize: '0.875rem' },
                    '& .MuiInputBase-inputMultiline': { padding: 0 },
                  }}
                />
              </Box>

              {/* 3. Category */}
              <Box
                sx={{
                  display: 'flex',
                  justifyContent: 'center',
                  alignItems: 'flex-end',
                  width: '100%',
                  minWidth: 0,
                  overflow: 'hidden',
                  '& .MuiChip-root': {
                    maxWidth: '100%',
                  },
                }}
              >
                <CategorySelect
                  value={categoryId}
                  onChange={setCategoryId}
                  categories={categories}
                  creditDebitType={creditDebitType}
                  placeholder={t('financeTransactions.category')}
                />
              </Box>

              {/* 4. Tags */}
              <Box sx={{ minWidth: 150, display: 'flex', alignItems: 'flex-end', width: '100%' }}>
                <TagsInput
                  value={tags}
                  onChange={setTags}
                  options={tagsData?.transactionTags?.map(t => t.tag) || []}
                  placeholder={t('financeTransactions.tags')}
                  variant="standard"
                  InputProps={{ 
                    disableUnderline: true,
                    sx: inputBottomBorderSx
                  }}
                />
              </Box>

              {/* 5. Issuer (Conditional) */}
              {!selectedAccountId && (
                <Box sx={{ display: 'flex', alignItems: 'flex-end', width: '100%' }}>
                  <TextField
                    select
                    value={accountId}
                    onChange={(e) => setAccountId(e.target.value)}
                    variant="standard"
                    fullWidth
                    InputProps={{ 
                      disableUnderline: true,
                      sx: {
                        borderBottom: `1px ${accountId ? 'solid' : 'dashed'} ${theme.palette.divider}`,
                        transition: 'border-color 0.2s, border-bottom-width 0.2s',
                        '&.Mui-focused': {
                          borderBottom: `2px solid ${theme.palette.primary.main}`,
                        },
                      }
                    }}
                    sx={{ minWidth: 120 }}
                  >
                    {accounts.map((account) => (
                      <MenuItem key={account.id} value={account.id}>
                        <Stack direction="row" spacing={1} alignItems="center">
                          <Avatar 
                            src={account.issuer?.logoAsset?.publicUrl || undefined}
                            sx={{ 
                              width: 24, 
                              height: 24,
                              fontSize: '0.6rem',
                              borderRadius: borderRadius,
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
                </Box>
              )}

              {/* 6. Amount */}
              <Box sx={{ gridColumn: amountGridColumn, minWidth: 100, textAlign: 'right', display: 'flex', alignItems: 'flex-end', justifyContent: 'flex-end', width: '100%' }}>
                <BaseMoneyInput
                  value={amount}
                  onChange={(val) => setAmount(val as number | undefined)}
                  variant="standard"
                  required
                  data-testid="tx-amount-input"
                  InputProps={{
                    disableUnderline: true,
                    sx: { 
                      fontSize: '0.875rem', 
                      fontWeight: 'medium',
                      maxWidth: 180,
                      ml: 'auto',
                      ...inputBottomBorderSx,
                      '& input': {
                        textAlign: 'right'
                      }
                    },
                    onKeyDown: (e: any) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        if (!(creating || updating) && amount && description && date && accountId) {
                          handleSave();
                        }
                      }
                    }
                  }}
                />
              </Box>
            </Box>

            <Box sx={{ display: 'flex', justifyContent: 'space-between', p: 1, px: 2, alignItems: 'center', bgcolor: theme.custom.palette.infoLightBg, borderTop: 1, borderColor: 'divider' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <DatePicker
                value={date}
                onChange={(newValue) => setDate(newValue)}
                minDate={minAllowedDate}
                slotProps={{
                  textField: {
                    hiddenLabel: true,
                    size: 'small',
                    variant: 'standard',
                    error: isDateOutsideWindow,
                    helperText: isDateOutsideWindow ? t('financeTransactions.validation.dateTooOld', { years: FINANCE_MOVING_WINDOW_YEARS }) : undefined,
                    inputProps: { 'data-testid': 'tx-date-input' },
                    InputProps: {
                      disableUnderline: true,
                      sx: { 
                        fontSize: '0.875rem',
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
                      ...focusBorderSx,
                      '& .MuiInputBase-input': {
                        textAlign: 'left',
                        fontSize: '0.875rem',
                        p: '4px 0 5px'
                      }
                    }
                  } 
                }}
              />
            </Box>

            {/* Attachments — only for existing transactions */}
            {initialValues && (
              <Box sx={{ flex: 1, mx: 2 }}>
                <AttachmentSection
                  transactionId={initialValues.id}
                  attachments={initialValues.attachments ?? []}
                  onChanged={onRefresh ?? onSuccess}
                  compact
                />
              </Box>
            )}

            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Button
                onClick={onClose}
                color="inherit"
                size="small"
                data-testid="tx-cancel-btn"
                sx={{
                  minWidth: 'auto',
                  bgcolor: alpha(theme.palette.text.primary, 0.0),
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
                data-testid="tx-save-btn"
                loading={creating || updating || applyingSimilarEdit}
                size="small"
                disabled={creating || updating || applyingSimilarEdit || !amount || !description || !date || !accountId || isDateOutsideWindow}
                sx={{ boxShadow: theme.shadows[4], borderRadius: 1 }}
              >
                {t('actions.save')}
              </Button>
            </Box>
          </Box>
          </Box>
          </Collapse>
        </TableCell>
      </TableRow>
      <SimilarEditDialog
        open={Boolean(pendingSimilarEditPrompt)}
        onClose={handleDismissSimilarEditPrompt}
        onConfirm={handleConfirmSimilarEdit}
        similarCount={pendingSimilarEditPrompt?.similarCount ?? 0}
        loading={applyingSimilarEdit}
      />
    </>
  );
}
