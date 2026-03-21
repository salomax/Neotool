'use client';

import React, { useState, useEffect, useRef } from 'react';
import { useQuery, useMutation } from '@apollo/client/react';
import { useForm, FormProvider, useController, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import dayjs from 'dayjs';
import {
  Box,
  Typography,
  IconButton,
  Stack,
  InputAdornment,
  Divider,
} from '@mui/material';
import {
  ArrowBack as BackIcon,
} from '@mui/icons-material';
import { FinancialAccountTypeSelection } from './FinancialAccountTypeSelection';
import { CardNetworkIcon } from '@/shared/components/ui/icons/CardNetworkIcon';
import { IssuerSelect } from './IssuerSelect';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { FormTextField as TextField, SelectField, MoneyInput, DatePickerField } from '@/shared/components/ui/forms/form';
import { ISSUERS } from '@/lib/graphql/operations/banking/queries';
import { CREATE_FINANCIAL_ACCOUNT } from '@/lib/graphql/operations/banking/mutations';
import { useToast } from '@/shared/providers';
import { FormErrorBanner } from '@/shared/components/ui/forms/components/FormErrorBanner';
import { FINANCIAL_ACCOUNTS } from '@/lib/graphql/operations/banking/queries';
import { useTranslation, i18n } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { Issuer } from '@/lib/graphql/types/__generated__/graphql';
import { FINANCE_MOVING_WINDOW_YEARS, getFinanceMinAllowedDate } from '../constants/dateWindow';
import { extractErrorMessage } from '@/shared/utils/error';

// --- Types & Schemas ---

// Subtypes
const BankSubtypeValues = [
  'CONTA_CORRENTE',
  'CONTA_POUPANCA',
  'CONTA_SALARIO',
  'CONTA_DIGITAL',
  'CONTA_CONJUNTA',
];

const CardSubtypeValues = [
  'CREDIT_CARD',
  'CHARGE_CARD',
  'PREPAID_CARD',
  'VOUCHER_CARD',
];

const CardNetworkValues = [
  'VISA',
  'MASTERCARD',
  'ELO',
  'AMERICAN_EXPRESS',
  'HIPERCARD',
  'DINERS_CLUB',
  'BANDEIRA_PROPRIA',
  'OUTRAS',
];

const CardLevelValues = [
  'STANDARD',
  'GOLD',
  'PLATINUM',
  'INFINITE',
  'BLACK',
];

const Currencies = [
  { value: 'BRL', label: 'BRL' },
  { value: 'USD', label: 'USD' },
  { value: 'EUR', label: 'EUR' },
];

type CreateAccountFormData = {
  type: 'BANK' | 'CARD';
  issuerId?: string;
  issuerName?: string;
  name: string;
  currencyCode: string;
  initialBalance?: number;
  initialBalanceDate?: Date | null;
  themeColor: string;
  includeInNetWorth: boolean;
  includeInDashboard: boolean;
  subtype: string;
  branchCode?: string;
  accountNumber?: string;
  checkDigit?: string;
  cardNetwork?: string;
  cardLevel?: string;
  maskedNumber?: string;
  creditLimit?: number;
  closingDay?: number;
  dueDay?: number;
};

function buildDefaultCardAccountName(
  data: Pick<CreateAccountFormData, 'subtype' | 'maskedNumber'>,
  t: (key: string) => string,
): string {
  const subtypeLabel =
    data.subtype && data.subtype in CardSubtypeLabelKeyByValue
      ? t(CardSubtypeLabelKeyByValue[data.subtype as keyof typeof CardSubtypeLabelKeyByValue])
      : t('financeTransactions.createAccount.step2.defaults.creditCardType');
  const lastFourDigits = (data.maskedNumber ?? '').replace(/\D/g, '').slice(-4);
  return lastFourDigits ? `${subtypeLabel} • ${lastFourDigits}` : subtypeLabel;
}

const CardSubtypeLabelKeyByValue = {
  CREDIT_CARD: 'financeTransactions.createAccount.step2.enums.cardSubtype.CREDIT_CARD',
  CHARGE_CARD: 'financeTransactions.createAccount.step2.enums.cardSubtype.CHARGE_CARD',
  PREPAID_CARD: 'financeTransactions.createAccount.step2.enums.cardSubtype.PREPAID_CARD',
  VOUCHER_CARD: 'financeTransactions.createAccount.step2.enums.cardSubtype.VOUCHER_CARD',
} as const;

// --- Components ---

interface CreateFinancialAccountDrawerProps {
  open: boolean;
  onClose: () => void;
  initialType?: 'BANK' | 'CARD' | null;
  onBackFromPrefilledType?: () => void;
  /** Pre-fills the last 4 digits field when creating a card inline from import. */
  initialMaskedNumber?: string;
  /** Pre-fills the card closing day (1-31) when creating from bill import. */
  initialClosingDay?: number;
  /** Pre-fills the card due day (1-31) when creating from bill import. */
  initialDueDay?: number;
  /** Pre-select issuer when creating from flows that already locked issuer. */
  initialIssuerId?: string;
  /** Pre-fills custom issuer name when issuerId is not available. */
  initialIssuerName?: string;
  /** Called after successful creation with the new account's id and name. */
  onCreated?: (account: {
    id: string;
    name: string;
    issuerId?: string;
    issuerName?: string;
    closingDay?: number;
    dueDay?: number;
  }) => void;
}

export function CreateFinancialAccountDrawer({
  open,
  onClose,
  initialType,
  onBackFromPrefilledType,
  initialMaskedNumber,
  initialClosingDay,
  initialDueDay,
  initialIssuerId,
  initialIssuerName,
  onCreated,
}: CreateFinancialAccountDrawerProps) {
  const [step, setStep] = useState<1 | 2>(1);
  const [selectedType, setSelectedType] = useState<'BANK' | 'CARD' | null>(null);
  const { success } = useToast();
  const { t } = useTranslation(personalFinanceTranslations);
  const minAllowedDate = React.useMemo(() => getFinanceMinAllowedDate(), []);

  // Zod Schema
  const createAccountSchema = React.useMemo(
    () =>
      z
        .object({
          type: z.enum(['BANK', 'CARD']),
          issuerId: z.string().optional(),
          issuerName: z.string().optional(),
          name: z.string().default(''),
          currencyCode: z.string().default('BRL'),
          initialBalance: z.number().optional(),
          initialBalanceDate: z.date().nullable().optional(),
          themeColor: z.string().default('#1976d2'),
          includeInNetWorth: z.boolean().default(true),
          includeInDashboard: z.boolean().default(true),
          subtype: z.string().min(1, t('financeTransactions.createAccount.step2.validation.subtypeRequired')),
          branchCode: z.string().optional(),
          accountNumber: z.string().optional(),
          checkDigit: z.string().optional(),
          cardNetwork: z.string().optional(),
          cardLevel: z.string().optional(),
          maskedNumber: z.string().optional(),
          creditLimit: z.number().optional(),
          closingDay: z.preprocess(
            (val) => (val === '' || val === undefined || val === null ? undefined : Number(val)),
            z
              .number({ invalid_type_error: t('financeTransactions.createAccount.step2.validation.closingDayInvalid') })
              .min(1, t('financeTransactions.createAccount.step2.validation.closingDayInvalid'))
              .max(31, t('financeTransactions.createAccount.step2.validation.closingDayInvalid'))
              .optional(),
          ),
          dueDay: z.preprocess(
            (val) => (val === '' || val === undefined || val === null ? undefined : Number(val)),
            z
              .number({ invalid_type_error: t('financeTransactions.createAccount.step2.validation.dueDayInvalid') })
              .min(1, t('financeTransactions.createAccount.step2.validation.dueDayInvalid'))
              .max(31, t('financeTransactions.createAccount.step2.validation.dueDayInvalid'))
              .optional(),
          ),
        })
        .superRefine((data, ctx) => {
          const issuerName = data.issuerName?.trim();

          if (!data.issuerId && !issuerName) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t('financeTransactions.createAccount.step2.validation.issuerRequired'),
              path: ['issuerId'],
            });
            return;
          }

          if (data.issuerId === 'custom' && !issuerName) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t('financeTransactions.createAccount.step2.validation.issuerRequired'),
              path: ['issuerName'],
            });
          }

          if (data.initialBalance !== undefined) {
            if (data.initialBalance === 0) {
              ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: t('financeTransactions.createAccount.step2.validation.initialBalanceNonZero'),
                path: ['initialBalance'],
              });
            }
            if (!data.initialBalanceDate) {
              ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: t('financeTransactions.createAccount.step2.validation.initialBalanceDateRequired'),
                path: ['initialBalanceDate'],
              });
            } else if (dayjs(data.initialBalanceDate).isBefore(minAllowedDate, 'day')) {
              ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: t('financeTransactions.validation.dateTooOld', { years: FINANCE_MOVING_WINDOW_YEARS }),
                path: ['initialBalanceDate'],
              });
            }
          }
        }),
    [t, minAllowedDate],
  );

  // Option lists with translations
  const bankSubtypes = React.useMemo(() => BankSubtypeValues.map(v => ({
    value: v,
    label: t(`financeTransactions.createAccount.step2.enums.bankSubtype.${v}`)
  })), [t]);

  const cardSubtypes = React.useMemo(() => CardSubtypeValues.map(v => ({
    value: v,
    label: t(`financeTransactions.createAccount.step2.enums.cardSubtype.${v}`)
  })), [t]);

  const cardNetworks = React.useMemo(() => CardNetworkValues.map(v => ({
    value: v,
    label: t(`financeTransactions.createAccount.step2.enums.cardNetwork.${v}`),
    icon: <CardNetworkIcon network={v} size={20} />,
  })), [t]);

  const cardLevels = React.useMemo(() => CardLevelValues.map(v => ({
    value: v,
    label: t(`financeTransactions.createAccount.step2.enums.cardLevel.${v}`)
  })), [t]);

  // Queries
  const { data: issuersData, loading: issuersLoading } = useQuery<{ issuers: Issuer[] }>(ISSUERS, {
    skip: !open,
  });

  const [createAccount, { loading: creating, error: createError }] = useMutation<{
    createFinancialAccount: { id: string; name: string };
  }>(CREATE_FINANCIAL_ACCOUNT, {
    refetchQueries: [{ query: FINANCIAL_ACCOUNTS }],
  });
  const createErrorMessage = React.useMemo(
    () => (createError ? extractErrorMessage(createError, t('errors.default', { ns: 'common' })) : ''),
    [createError, t],
  );

  const methods = useForm<CreateAccountFormData>({
    mode: 'onChange',
    reValidateMode: 'onChange',
    resolver: zodResolver(createAccountSchema),
    defaultValues: {
      name: '',
      issuerId: undefined,
      issuerName: '',
      currencyCode: 'BRL',
      initialBalanceDate: null,
      themeColor: '#1976d2',
      includeInNetWorth: true,
      includeInDashboard: true,
    },
  });

  const { setValue, reset, control, formState } = methods;
  const { isValid } = formState;
  const watchedIssuerId = useWatch({ control, name: 'issuerId' });
  const watchedSubtype = useWatch({ control, name: 'subtype' });
  const watchedCurrencyCode = useWatch({ control, name: 'currencyCode' });
  const previousIssuerIdRef = useRef<string | undefined>(undefined);
  const { field: issuerIdField, fieldState: issuerIdFieldState } = useController({
    name: 'issuerId',
    control,
  });
  const issuerIdValue = typeof issuerIdField.value === 'string' ? issuerIdField.value : undefined;

  // Reset on close
  const handleTypeSelect = React.useCallback(
    (type: 'BANK' | 'CARD') => {
      setSelectedType(type);
      setValue('type', type, { shouldValidate: true });
      if (type === 'BANK') setValue('subtype', 'CONTA_CORRENTE', { shouldValidate: true });
      else setValue('subtype', 'CREDIT_CARD', { shouldValidate: true });

      setStep(2);
    },
    [setValue],
  );

  useEffect(() => {
    if (!open) {
      React.startTransition(() => {
        setStep(1);
        setSelectedType(null);
      });
      reset();
      return;
    }

    if (initialType) {
      React.startTransition(() => {
        handleTypeSelect(initialType);
      });
    }
  }, [handleTypeSelect, initialType, open, reset]);

  // Pre-fill inline-import defaults when opening card creation from bill association.
  useEffect(() => {
    if (!open) return;

    if (initialMaskedNumber) {
      setValue('maskedNumber', initialMaskedNumber, { shouldValidate: true });
    }
    if (typeof initialClosingDay === 'number') {
      setValue('closingDay', initialClosingDay, { shouldValidate: true });
    }
    if (typeof initialDueDay === 'number') {
      setValue('dueDay', initialDueDay, { shouldValidate: true });
    }
    if (initialIssuerId) {
      setValue('issuerId', initialIssuerId, { shouldValidate: true });
      setValue('issuerName', '');
    } else if (initialIssuerName) {
      setValue('issuerId', 'custom', { shouldValidate: true });
      setValue('issuerName', initialIssuerName, { shouldValidate: true });
    }
  }, [open, initialMaskedNumber, initialClosingDay, initialDueDay, initialIssuerId, initialIssuerName, setValue]);

  // Handle Issuer Selection
  useEffect(() => {
    if (!open) return;

    const previousIssuerId = previousIssuerIdRef.current;
    previousIssuerIdRef.current = watchedIssuerId;

    if (!watchedIssuerId) {
      if (previousIssuerId) {
        setValue('issuerName', '', { shouldDirty: false, shouldValidate: false });
      }
      return;
    }

    if (watchedIssuerId === 'custom') {
      if (previousIssuerId !== 'custom') {
        setValue('issuerName', '', { shouldDirty: false, shouldValidate: false });
      }
      return;
    }

    if (!issuersData?.issuers) return;

    const issuer = issuersData.issuers.find((i) => i.id === watchedIssuerId);
    if (!issuer) {
      setValue('issuerId', undefined, { shouldDirty: false, shouldValidate: true });
      setValue('issuerName', '', { shouldDirty: false, shouldValidate: false });
      return;
    }

    setValue('issuerName', issuer.name, { shouldDirty: false, shouldValidate: false });
  }, [issuersData?.issuers, open, setValue, watchedIssuerId]);

  const handleClose = () => {
    onClose();
  };

  const handleBack = () => {
    // When opened with a pre-selected type (e.g. inline from import), back = close drawer
    if (initialType) {
      onBackFromPrefilledType?.() ?? handleClose();
      return;
    }
    setStep(1);
    setSelectedType(null);
    reset();
  };

  const onSubmit = async (data: CreateAccountFormData) => {
    const issuerName = data.issuerName?.trim();
    const accountType =
      data.type === 'BANK'
        ? 'BANK'
        : data.subtype === 'CREDIT_CARD' || data.subtype === 'CHARGE_CARD'
          ? 'CREDIT'
          : 'PAYMENT';
    const resolvedName =
      data.type === 'CARD'
        ? (data.name?.trim() || buildDefaultCardAccountName(data, t))
        : (data.name?.trim() || undefined);

    // Transform data for mutation
    const input: any = {
      type: accountType,
      subtype: data.subtype,
      name: resolvedName,
      currencyCode: data.currencyCode,
      themeColor: data.themeColor,
      includeInNetWorth: data.includeInNetWorth,
      includeInDashboard: data.includeInDashboard,
      // Handle issuer
      ...(data.issuerId === 'custom' ? { issuerName } : { issuerId: data.issuerId }),
    };

    if (data.initialBalance !== undefined) {
      input.initialBalance = data.initialBalance;
      input.initialBalanceDate = data.initialBalanceDate ? dayjs(data.initialBalanceDate).format('YYYY-MM-DD') : undefined;
    }

    if (data.type === 'BANK') {
      input.bankData = {
        branchCode: data.branchCode,
        number: data.accountNumber,
        checkDigit: data.checkDigit,
      };
    } else {
      input.cardData = {
        cardNetwork: data.cardNetwork,
        cardLevel: data.cardLevel,
        maskedNumber: data.maskedNumber,
        creditLimit: data.creditLimit,
        closingDay: data.closingDay,
        dueDay: data.dueDay,
      };
    }

    const meta = {
      issuerId: data.issuerId === 'custom' ? undefined : data.issuerId,
      issuerName: data.issuerId === 'custom' ? issuerName : undefined,
      closingDay: data.closingDay,
      dueDay: data.dueDay,
    };

    try {
      const result = await createAccount({ variables: { input } });
      const created = result.data?.createFinancialAccount;
      if (!created) return;

      success(t('financeTransactions.createAccount.step2.success'));
      onCreated?.({
        id: created.id,
        name: created.name,
        issuerId: meta.issuerId,
        issuerName: meta.issuerName,
        closingDay: meta.closingDay,
        dueDay: meta.dueDay,
      });
      handleClose();
    } catch {
      return;
    }
  };

  const renderTypeSelection = () => (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 4, p: 4, alignItems: 'stretch', justifyContent: 'flex-start', height: '100%' }}>
      {/* Title Section */}
      <Box sx={{ gap: 1, display: 'flex', flexDirection: 'column' }}>
        <Typography variant="subtitle2" color="text.secondary">
          {t('financeTransactions.createAccount.step1.title')}
        </Typography>
      </Box>

      {/* Bento Style Grid for Options */}
      <FinancialAccountTypeSelection onSelect={handleTypeSelect} />
    </Box>
  );

  const renderForm = () => (
    <Box sx={{ p: 0 }}>
      <FormProvider {...methods}>
        <form id="create-financial-account-form" onSubmit={methods.handleSubmit(onSubmit)}>
          <Stack spacing={3}>
            {createError && <FormErrorBanner message={createErrorMessage} />}

            {/* Common Fields */}
            <IssuerSelect
              issuers={issuersData?.issuers}
              loading={issuersLoading}
              value={issuerIdValue}
              onChange={issuerIdField.onChange}
              label={t('financeTransactions.createAccount.step2.fields.issuer')}
              noOptionsText={t('financeTransactions.createAccount.step2.fields.noIssuersFound')}
              customIssuerLabel={t('financeTransactions.createAccount.step2.fields.customIssuer')}
              error={!!issuerIdFieldState.error}
              helperText={issuerIdFieldState.error?.message}
            />

            {watchedIssuerId === 'custom' && (
              <TextField name="issuerName" label={t('financeTransactions.createAccount.step2.fields.institutionName')} required />
            )}

            {/* Subtype Selection */}
            {selectedType === 'BANK' && (
              <SelectField name="subtype" label={t('financeTransactions.createAccount.step2.fields.subtype')} options={bankSubtypes} />
            )}
            {selectedType === 'CARD' && (
              <SelectField name="subtype" label={t('financeTransactions.createAccount.step2.fields.subtype')} options={cardSubtypes} />
            )}

            {watchedIssuerId === 'custom' && (
              <SelectField name="currencyCode" label={t('financeTransactions.createAccount.step2.fields.currency')} options={Currencies} />
            )}

            <TextField name="name" label={t('financeTransactions.createAccount.step2.fields.accountName')} />

            {/* Initial Balance — only for bank accounts */}
            {selectedType !== 'CARD' && (
              <Stack direction="row" spacing={2}>
                <MoneyInput
                  name="initialBalance"
                  label={t('financeTransactions.createAccount.step2.fields.initialBalance')}
                  currency={watchedCurrencyCode}
                  locale={i18n.language}
                />
                <DatePickerField
                  name="initialBalanceDate"
                  label={t('financeTransactions.createAccount.step2.fields.initialBalanceDate')}
                  minDate={minAllowedDate.toDate()}
                />
              </Stack>
            )}

            {/* Bank Specific Fields (excluding subtype) */}
            {selectedType === 'BANK' && (
              <Stack direction="row" spacing={2}>
                <TextField name="branchCode" label={t('financeTransactions.createAccount.step2.fields.branchCode')} sx={{ flex: 1 }} />
                <TextField name="accountNumber" label={t('financeTransactions.createAccount.step2.fields.accountNumber')} sx={{ flex: 2 }} />
                <TextField name="checkDigit" label={t('financeTransactions.createAccount.step2.fields.checkDigit')} sx={{ width: 80 }} />
              </Stack>
            )}

            {/* Card Specific Fields (excluding subtype) */}
            {selectedType === 'CARD' && (
              <>
                <SelectField name="cardNetwork" label={t('financeTransactions.createAccount.step2.fields.network')} options={cardNetworks} />
                
                {watchedSubtype === 'CREDIT_CARD' && (
                  <SelectField name="cardLevel" label={t('financeTransactions.createAccount.step2.fields.level')} options={cardLevels} />
                )}
                
                {watchedSubtype === 'CREDIT_CARD' && (
                  <>
                    <Stack direction="row" spacing={2}>
                      <TextField
                        name="maskedNumber"
                        label={t('financeTransactions.createAccount.step2.fields.maskedNumber')}
                        placeholder="1234"
                        InputProps={{ startAdornment: <InputAdornment position="start">••••</InputAdornment> }}
                        sx={{ flex: 1 }}
                      />
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <MoneyInput
                          name="creditLimit"
                          label={t('financeTransactions.createAccount.step2.fields.creditLimit')}
                          currency={watchedCurrencyCode}
                          locale={i18n.language}
                          fullWidth
                        />
                      </Box>
                    </Stack>
                    <Stack direction="row" spacing={2}>
                      <TextField 
                        name="closingDay" 
                        label={t('financeTransactions.createAccount.step2.fields.closingDay')} 
                        type="number" 
                        inputProps={{ min: 1, max: 31 }}
                      />
                      <TextField 
                        name="dueDay" 
                        label={t('financeTransactions.createAccount.step2.fields.dueDay')} 
                        type="number"
                        inputProps={{ min: 1, max: 31 }}
                      />
                    </Stack>
                  </>
                )}

                {watchedSubtype !== 'CREDIT_CARD' && (
                  <TextField
                    name="maskedNumber"
                    label={t('financeTransactions.createAccount.step2.fields.maskedNumber')}
                    placeholder="1234"
                    InputProps={{ startAdornment: <InputAdornment position="start">••••</InputAdornment> }}
                  />
                )}
              </>
            )}

            <Divider />  

            <Drawer.ActionButtons
              cancelText={t('financeTransactions.actions.cancel')}
              submitText={selectedType === 'BANK' ? t('financeTransactions.actions.addAccount') : t('financeTransactions.actions.addCard')}
              submittingText={t('financeTransactions.actions.saving')}
              onCancel={handleClose}
              submitForm="create-financial-account-form"
              cancelDisabled={creating}
              submitDisabled={creating || !isValid}
              submitting={creating}
            />

          </Stack>
        </form>
      </FormProvider>
    </Box>
  );

  return (
    <Drawer
      open={open}
      onClose={onClose}
      anchor="right"
      size="md"
    >
      <Drawer.Header
        title={step === 1 ? t('financeTransactions.createAccount.title') : (selectedType === 'BANK' ? t('financeTransactions.createAccount.step1.bankAccount.label') : t('financeTransactions.createAccount.step1.card.label'))}
        icon={step === 2 ? <IconButton onClick={handleBack} edge="start"><BackIcon /></IconButton> : null}
      />
      <Drawer.Body>
        {step === 1 ? renderTypeSelection() : renderForm()}
      </Drawer.Body>
    </Drawer>
  );
}
