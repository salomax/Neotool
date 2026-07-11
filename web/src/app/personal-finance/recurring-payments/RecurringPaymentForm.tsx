'use client';

import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  alpha,
  Box,
  Chip,
  Stack,
  Typography,
  useTheme,
} from '@mui/material';
import { useMutation, useQuery } from '@apollo/client/react';
import { FormProvider, useForm, Controller, useWatch } from 'react-hook-form';
import { useTranslation, i18n } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import { FINANCIAL_ACCOUNTS } from '@/lib/graphql/operations/banking/queries';
import { TransactionCategoriesDocument } from '@/lib/graphql/operations/banking/queries.generated';
import {
  CREATE_RECURRING_PAYMENT,
  CREATE_RECURRING_PAYMENT_FROM_TRANSACTION,
  UPDATE_RECURRING_PAYMENT,
} from '@/lib/graphql/operations/banking/recurringPaymentOperations';
import type { FinancialAccount, Transaction } from '@/lib/graphql/types/__generated__/graphql';
import { RecurringFrequency } from '@/lib/graphql/types/__generated__/graphql';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { FormStack } from '@/shared/components/ui/forms/components';
import { ErrorAlert } from '@/shared/components/ui/feedback';
import {
  DatePickerField,
  FormTextField,
  MoneyInput,
  NumberField,
  SelectField,
  TextField,
} from '@/shared/components/ui/forms/form';
import { CategoryPicker } from '@/app/personal-finance/finance-transactions/category/CategoryPicker';
import { CategoryIcon } from '@/app/personal-finance/finance-transactions/category/CategoryIcon';
import { AccountSelect } from '@/app/personal-finance/finance-transactions/transaction/AccountSelect';
import { MerchantSelect } from '@/app/personal-finance/finance-transactions/transaction/MerchantSelect';
import { formatDateOnly, isValidDate, parseDateOnly, toStartOfDayLocal } from '@/shared/utils/date/dateOnly';
import type { RecurringPaymentRow } from './recurringPaymentRowModel';

export type RecurringPaymentRecord = RecurringPaymentRow;

type Frequency = RecurringFrequency;

type FormValues = {
  financialAccountId: string;
  name: string;
  creditDebitType: 'CREDIT' | 'DEBIT';
  type: string;
  categoryId: string;
  merchantId: string;
  expectedAmount?: number | null;
  frequency: Frequency;
  dayOfMonth: number | '';
  intervalDays: number | '';
  dueDate: Date | null;
  remindDaysBefore: number | '';
  notes: string;
};

function emptyForm(): FormValues {
  return {
    financialAccountId: '',
    name: '',
    creditDebitType: 'DEBIT',
    type: '',
    categoryId: '',
    merchantId: '',
    expectedAmount: undefined,
    frequency: RecurringFrequency.Monthly,
    dayOfMonth: '',
    intervalDays: '',
    dueDate: toStartOfDayLocal(new Date()),
    remindDaysBefore: 10,
    notes: '',
  };
}

function toDateInput(value?: string | null): string {
  if (!value) return '';
  return String(value).slice(0, 10);
}

function formatFromTransactionDateLabel(date: string | undefined | null): string {
  if (!date) return '';
  const d = new Date(String(date));
  if (Number.isNaN(d.getTime())) return String(date);
  return new Intl.DateTimeFormat('pt-BR').format(d);
}

interface RecurringPaymentFormProps {
  open: boolean;
  onClose: () => void;
  onSaved: () => Promise<void> | void;
  recurring?: RecurringPaymentRecord | null;
  fromTransaction?: Transaction | null;
}

export function RecurringPaymentForm({ open, onClose, onSaved, recurring, fromTransaction }: RecurringPaymentFormProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const isEdit = Boolean(recurring?.id);
  const isFromTransaction = Boolean(fromTransaction?.id) && !isEdit;

  const methods = useForm<FormValues>({
    defaultValues: emptyForm(),
  });

  const { reset, handleSubmit, control, setValue } = methods;

  const [categoryPickerAnchorEl, setCategoryPickerAnchorEl] = useState<HTMLElement | null>(null);

  const { data: accountsData } = useQuery<{ financialAccounts: FinancialAccount[] }>(FINANCIAL_ACCOUNTS, {
    fetchPolicy: 'cache-first',
  });

  const { data: categoriesData } = useQuery(TransactionCategoriesDocument, { fetchPolicy: 'cache-first' });

  const accounts = useMemo(() => accountsData?.financialAccounts ?? [], [accountsData?.financialAccounts]);

  const creditDebitOptions = useMemo(
    () => [
      { value: 'DEBIT' as const, label: t('financeTransactions.debit') },
      { value: 'CREDIT' as const, label: t('financeTransactions.credit') },
    ],
    [t],
  );

  const frequencyOptions = useMemo(
    () => [
      { value: RecurringFrequency.Monthly, label: t('recurringPayments.frequency.MONTHLY') },
      { value: RecurringFrequency.Weekly, label: t('recurringPayments.frequency.WEEKLY') },
      { value: RecurringFrequency.Yearly, label: t('recurringPayments.frequency.YEARLY') },
      { value: RecurringFrequency.Custom, label: t('recurringPayments.frequency.CUSTOM') },
    ],
    [t],
  );

  const [createRecurring, { loading: creating, error: createError }] = useMutation(CREATE_RECURRING_PAYMENT);
  const [createFromTransaction, { loading: creatingFromTx, error: createFromTxError }] = useMutation(
    CREATE_RECURRING_PAYMENT_FROM_TRANSACTION,
  );
  const [updateRecurring, { loading: updating, error: updateError }] = useMutation(UPDATE_RECURRING_PAYMENT);
  const submitting = creating || creatingFromTx || updating;

  const mutationError = createError ?? createFromTxError ?? updateError;

  useEffect(() => {
    if (!open) return;
    if (recurring) {
      reset({
        financialAccountId: recurring.financialAccountId ?? '',
        name: recurring.name,
        creditDebitType: recurring.creditDebitType,
        type: recurring.type ?? '',
        categoryId: recurring.category?.id ?? '',
        merchantId: recurring.merchant?.id ?? '',
        expectedAmount: recurring.expectedAmount ?? undefined,
        frequency: recurring.frequency,
        dayOfMonth: recurring.dayOfMonth != null ? recurring.dayOfMonth : '',
        intervalDays: recurring.intervalDays != null ? recurring.intervalDays : '',
        dueDate: parseDateOnly(toDateInput(recurring.nextDueDate)) ?? toStartOfDayLocal(new Date()),
        remindDaysBefore: recurring.remindDaysBefore ?? 10,
        notes: recurring.notes ?? '',
      });
      return;
    }
    if (fromTransaction) {
      const txDate = String(fromTransaction.date ?? '');
      const dayPart = toDateInput(txDate).slice(-2).replace(/^0+/, '');
      const dom = Number(dayPart) || 1;
      reset({
        financialAccountId: fromTransaction.financialAccountId,
        name: fromTransaction.description,
        creditDebitType: fromTransaction.creditDebitType as 'CREDIT' | 'DEBIT',
        type: String(fromTransaction.type ?? ''),
        categoryId: fromTransaction.category?.id ?? '',
        merchantId: fromTransaction.merchant?.id ?? '',
        expectedAmount: fromTransaction.amount,
        frequency: RecurringFrequency.Monthly,
        dayOfMonth: Number.isFinite(dom) ? dom : 1,
        intervalDays: '',
        dueDate: parseDateOnly(toDateInput(txDate)) ?? toStartOfDayLocal(new Date()),
        remindDaysBefore: 10,
        notes: '',
      });
      return;
    }
    reset(emptyForm());
  }, [open, recurring, fromTransaction, reset]);

  const creditDebitWatch = useWatch({ control, name: 'creditDebitType', defaultValue: 'DEBIT' });
  const merchantIdVal = useWatch({ control, name: 'merchantId', defaultValue: '' });
  const categoryIdVal = useWatch({ control, name: 'categoryId', defaultValue: '' });
  const frequencyWatch = useWatch({ control, name: 'frequency', defaultValue: RecurringFrequency.Monthly });
  const nameWatch = useWatch({ control, name: 'name', defaultValue: '' });
  const dueDateWatch = useWatch({ control, name: 'dueDate' });

  const selectedCategory = useMemo(() => {
    const cats = categoriesData?.transactionCategories ?? [];
    return cats.find((c) => c.id === categoryIdVal) ?? null;
  }, [categoriesData?.transactionCategories, categoryIdVal]);

  const initialMerchantForSelect = useMemo(() => {
    if (!merchantIdVal) return null;
    if (recurring?.merchant?.id === merchantIdVal) {
      return {
        id: recurring.merchant.id,
        name: recurring.merchant.name,
        logoAsset: recurring.merchant.logoAsset ?? null,
      };
    }
    if (fromTransaction?.merchant?.id === merchantIdVal) {
      return {
        id: fromTransaction.merchant.id,
        name: fromTransaction.merchant.name,
        logoAsset: fromTransaction.merchant.logoAsset ?? null,
      };
    }
    return null;
  }, [recurring, fromTransaction, merchantIdVal]);

  const fromTxDateLabel = formatFromTransactionDateLabel(
    fromTransaction?.date != null ? String(fromTransaction.date) : undefined,
  );

  const onSubmit = async (state: FormValues) => {
    const parsedAmount =
      state.expectedAmount != null && Number.isFinite(Number(state.expectedAmount))
        ? Number(state.expectedAmount)
        : null;
    const parsedDay = typeof state.dayOfMonth === 'number' && Number.isFinite(state.dayOfMonth) ? state.dayOfMonth : null;
    const parsedInterval =
      typeof state.intervalDays === 'number' && Number.isFinite(state.intervalDays) ? state.intervalDays : null;
    const parsedRemind =
      typeof state.remindDaysBefore === 'number' && Number.isFinite(state.remindDaysBefore)
        ? state.remindDaysBefore
        : 10;

    if (!isValidDate(state.dueDate)) {
      return;
    }
    const firstDueDate = formatDateOnly(state.dueDate);

    const input = {
      financialAccountId: state.financialAccountId || null,
      name: state.name.trim(),
      creditDebitType: state.creditDebitType,
      type: state.type || null,
      categoryId: state.categoryId.trim() || null,
      merchantId: state.merchantId.trim() || null,
      expectedAmount: parsedAmount,
      currencyCode: 'BRL',
      frequency: state.frequency,
      dayOfMonth: parsedDay,
      intervalDays: parsedInterval,
      remindDaysBefore: Number.isFinite(parsedRemind) ? parsedRemind : 10,
      firstDueDate,
      notes: state.notes.trim() || null,
    };

    if (isEdit && recurring) {
      await updateRecurring({
        variables: {
          id: recurring.id,
          input: {
            financialAccountId: input.financialAccountId,
            name: input.name,
            creditDebitType: input.creditDebitType,
            expectedAmount: input.expectedAmount,
            frequency: input.frequency,
            dayOfMonth: input.dayOfMonth,
            intervalDays: input.intervalDays,
            remindDaysBefore: input.remindDaysBefore,
            nextDueDate: input.firstDueDate,
            notes: input.notes,
            categoryId: input.categoryId,
            merchantId: input.merchantId,
          },
        },
      });
    } else if (isFromTransaction && fromTransaction?.id) {
      await createFromTransaction({
        variables: {
          transactionId: fromTransaction.id,
          input,
        },
      });
    } else {
      await createRecurring({
        variables: { input },
      });
    }

    await onSaved();
    onClose();
  };

  const drawerTitle = isEdit ? t('recurringPayments.form.drawerTitleEdit') : t('recurringPayments.form.drawerTitleNew');

  const merchantSelectWrapperSx = {
    mb: 0,
    width: '100%',
    '& [data-testid="merchant-select-trigger"]': { width: '100%' },
    '& .MuiChip-root': {
      height: '56px !important',
      width: '100% !important',
      borderRadius: `${theme.shape.borderRadius}px !important`,
      justifyContent: 'flex-start',
    },
    '& .MuiChip-root:not(.MuiChip-outlined)': {
      bgcolor: 'transparent',
      '&:hover': { bgcolor: 'transparent' },
    },
    '& .MuiChip-label': { minWidth: 0, maxWidth: '100%', width: '100%', py: 0 },
  } as const;

  const dueDateOk = isValidDate(dueDateWatch as Date | null | undefined);

  return (
    <Drawer open={open} onClose={onClose} anchor="right" size="md" forceMobileTemporary>
      <Drawer.Header title={drawerTitle} />
      <Drawer.Body>
        <FormProvider {...methods}>
          <form id="recurring-payment-form" onSubmit={handleSubmit(onSubmit)}>
            <FormStack>
              {isFromTransaction ? (
                <Alert severity="info">
                  {t('recurringPayments.form.fromTransaction', { date: fromTxDateLabel })}
                </Alert>
              ) : null}

              <ErrorAlert error={mutationError} />

              <FormTextField
                name="name"
                label={t('recurringPayments.form.name')}
                required
                autoFocus
                size="medium"
                data-testid="recurring-payment-name"
              />

              <Controller
                name="financialAccountId"
                control={control}
                render={({ field }) => (
                  <AccountSelect
                    value={field.value}
                    accounts={accounts}
                    isReadonly={false}
                    onChange={field.onChange}
                    inputBottomBorderSx={{}}
                    variant="field"
                    label={t('recurringPayments.form.accountOptional')}
                    placeholder={t('recurringPayments.form.accountSearchPlaceholder')}
                    allowEmpty
                    searchable
                    dataTestId="recurring-payment-account-select"
                  />
                )}
              />

              <SelectField
                name="creditDebitType"
                label={t('recurringPayments.form.creditDebitType')}
                options={creditDebitOptions}
                disabled={isFromTransaction}
                size="medium"
              />

              <Box sx={{ mb: 0 }}>
                {selectedCategory ? (
                  <Chip
                    clickable
                    onClick={(e) => setCategoryPickerAnchorEl(e.currentTarget)}
                    label={
                      <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0 }}>
                        <CategoryIcon
                          iconName={selectedCategory.icon}
                          color={selectedCategory.color ?? undefined}
                          size={18}
                          sx={{ flexShrink: 0 }}
                        />
                        <Typography
                          variant="body2"
                          title={selectedCategory.name}
                          sx={{
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                            minWidth: 0,
                            maxWidth: '40ch',
                          }}
                        >
                          {selectedCategory.name}
                        </Typography>
                      </Stack>
                    }
                    variant="filled"
                    sx={{
                      height: 56,
                      width: '100%',
                      borderRadius: `${theme.shape.borderRadius}px`,
                      color: 'text.primary',
                      bgcolor: 'action.selected',
                      justifyContent: 'flex-start',
                      '&:hover': {
                        bgcolor: 'action.hover',
                      },
                      transition: 'background-color 0.15s ease',
                      '& .MuiChip-label': { minWidth: 0, maxWidth: '100%', width: '100%', py: 0 },
                    }}
                  />
                ) : (
                  <Chip
                    clickable
                    onClick={(e) => setCategoryPickerAnchorEl(e.currentTarget)}
                    label={
                      <Typography variant="body2" sx={{ opacity: 0.65 }}>
                        {t('recurringPayments.form.category')}
                      </Typography>
                    }
                    variant="outlined"
                    sx={{
                      height: 56,
                      width: '100%',
                      borderRadius: `${theme.shape.borderRadius}px`,
                      borderStyle: 'dashed',
                      color: 'text.secondary',
                      justifyContent: 'flex-start',
                      '&:hover': {
                        borderColor: 'primary.main',
                        color: 'primary.main',
                        bgcolor: alpha(theme.palette.primary.main, 0.05),
                      },
                      transition: 'background-color 0.15s ease',
                      '& .MuiChip-label': { minWidth: 0, maxWidth: '100%', width: '100%', py: 0 },
                    }}
                  />
                )}
              </Box>

              <CategoryPicker
                value={categoryIdVal || null}
                onChange={(id) => setValue('categoryId', id ?? '', { shouldDirty: true })}
                creditDebitType={creditDebitWatch}
                anchorEl={categoryPickerAnchorEl}
                onClose={() => setCategoryPickerAnchorEl(null)}
              />

              <Box sx={merchantSelectWrapperSx}>
                <Controller
                  name="merchantId"
                  control={control}
                  render={({ field }) => (
                    <MerchantSelect
                      value={field.value || null}
                      onChange={(id) => field.onChange(id ?? '')}
                      initialMerchant={initialMerchantForSelect}
                      placeholder={t('recurringPayments.form.merchant')}
                    />
                  )}
                />
              </Box>

              <MoneyInput
                name="expectedAmount"
                label={t('recurringPayments.form.expectedAmount')}
                currency="BRL"
                locale={i18n.language}
                helperText={t('recurringPayments.form.variableAmount')}
                size="medium"
              />

              <SelectField
                name="frequency"
                label={t('recurringPayments.form.frequency')}
                options={frequencyOptions}
                size="medium"
              />

              {frequencyWatch === RecurringFrequency.Monthly ? (
                <NumberField
                  name="dayOfMonth"
                  label={t('recurringPayments.form.dayOfMonth')}
                  min={1}
                  max={31}
                  allowNegative={false}
                  locale={i18n.language}
                  fractionDigits={0}
                  data-testid="recurring-payment-day-of-month"
                />
              ) : null}

              {frequencyWatch === RecurringFrequency.Custom ? (
                <NumberField
                  name="intervalDays"
                  label={t('recurringPayments.form.intervalDays')}
                  min={1}
                  allowNegative={false}
                  locale={i18n.language}
                  fractionDigits={0}
                  data-testid="recurring-payment-interval-days"
                />
              ) : null}

              <DatePickerField
                name="dueDate"
                label={t('recurringPayments.form.firstDueDate')}
                size="medium"
                data-testid="recurring-payment-due-date"
              />

              <NumberField
                name="remindDaysBefore"
                label={t('recurringPayments.form.remindDaysBefore')}
                min={0}
                max={60}
                allowNegative={false}
                locale={i18n.language}
                fractionDigits={0}
                data-testid="recurring-payment-remind-days"
              />

              <FormTextField
                name="notes"
                label={t('recurringPayments.form.notes')}
                fullWidth
                multiline
                minRows={2}
                size="medium"
                data-testid="recurring-payment-notes"
              />
            </FormStack>
          </form>
        </FormProvider>
      </Drawer.Body>
      <Drawer.Footer>
        <Drawer.ActionButtons
          cancelText={t('actions.cancel')}
          submitText={submitting ? t('financeTransactions.actions.saving') : t('actions.save')}
          onCancel={onClose}
          submitForm="recurring-payment-form"
          submitDisabled={!nameWatch?.trim() || !dueDateOk || submitting}
          submitting={submitting}
        />
      </Drawer.Footer>
    </Drawer>
  );
}
