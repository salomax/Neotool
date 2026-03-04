'use client';

import React, { useState, useEffect } from 'react';
import { useQuery, useMutation } from '@apollo/client/react';
import { useForm, useWatch, FormProvider, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  Box,
  Typography,
  IconButton,
  Card,
  CardContent,
  Stack,
  Button,
  Avatar,
  Slide,
  useTheme,
  InputAdornment,
  CircularProgress,
  Autocomplete,
  TextField as MuiTextField,
  Divider
} from '@mui/material';
import {
  AccountBalance as BankIcon,
  CreditCard as CardIcon,
  ArrowBack as BackIcon,
  ReceiptLong as ReceiptIcon,
} from '@mui/icons-material';
import { FinancialAccountTypeSelection } from './FinancialAccountTypeSelection';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { FormTextField as TextField, SelectField, ToggleField, MoneyInput } from '@/shared/components/ui/forms/form';
import { ColorPicker } from '@/shared/components/ui/primitives/ColorPicker';
import { ISSUERS } from '@/lib/graphql/operations/banking/queries';
import { CREATE_FINANCIAL_ACCOUNT } from '@/lib/graphql/operations/banking/mutations';
import { useToast } from '@/shared/providers';
import { FormErrorBanner } from '@/shared/components/ui/forms/components/FormErrorBanner';
import { FINANCIAL_ACCOUNTS } from '@/lib/graphql/operations/banking/queries';
import { useTranslation, i18n } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { Issuer } from '@/lib/graphql/types/__generated__/graphql';

// --- Types & Schemas ---

const FinancialAccountType = {
  BANK: 'BANK',
  CREDIT: 'CREDIT', // Mapping 'Card' to 'CREDIT' for now based on context, or could be others
} as const;

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

const ColorPresets = [
  '#1976d2', // Blue
  '#2e7d32', // Green
  '#ed6c02', // Orange
  '#9c27b0', // Purple
  '#d32f2f', // Red
  '#0288d1', // Light Blue
  '#424242', // Grey
  '#000000', // Black
];

type CreateAccountFormData = {
  type: 'BANK' | 'CARD';
  issuerId?: string;
  issuerName?: string;
  name: string;
  currencyCode: string;
  initialBalance?: number;
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

// --- Components ---

interface CreateFinancialAccountDrawerProps {
  open: boolean;
  onClose: () => void;
  initialType?: 'BANK' | 'CARD' | null;
}

export function CreateFinancialAccountDrawer({ open, onClose, initialType }: CreateFinancialAccountDrawerProps) {
  const [step, setStep] = useState<1 | 2>(1);
  const [selectedType, setSelectedType] = useState<'BANK' | 'CARD' | null>(null);
  const { success } = useToast();
  const theme = useTheme();
  const { t } = useTranslation(personalFinanceTranslations);

  // Zod Schema
  const createAccountSchema = React.useMemo(() => z.object({
    type: z.enum(['BANK', 'CARD']),
    issuerId: z.string().optional(),
    issuerName: z.string().optional(), // For custom
    name: z.string().min(1, t('financeTransactions.createAccount.step2.validation.accountNameRequired')),
    currencyCode: z.string().default('BRL'),
    initialBalance: z.number().optional(), 
    themeColor: z.string().default('#1976d2'),
    includeInNetWorth: z.boolean().default(true),
    includeInDashboard: z.boolean().default(true),
    // Bank specific
    subtype: z.string().min(1, t('financeTransactions.createAccount.step2.validation.subtypeRequired')),
    branchCode: z.string().optional(),
    accountNumber: z.string().optional(),
    checkDigit: z.string().optional(),
    // Card specific
    cardNetwork: z.string().optional(),
    cardLevel: z.string().optional(),
    maskedNumber: z.string().optional(), // Last 4 digits
    creditLimit: z.number().optional(),
    closingDay: z.preprocess(
      (val) => (val === '' || val === undefined || val === null ? undefined : Number(val)),
      z.number({ invalid_type_error: t('financeTransactions.createAccount.step2.validation.closingDayInvalid') })
        .min(1, t('financeTransactions.createAccount.step2.validation.closingDayInvalid'))
        .max(31, t('financeTransactions.createAccount.step2.validation.closingDayInvalid'))
        .optional()
    ),
    dueDay: z.preprocess(
      (val) => (val === '' || val === undefined || val === null ? undefined : Number(val)),
      z.number({ invalid_type_error: t('financeTransactions.createAccount.step2.validation.dueDayInvalid') })
        .min(1, t('financeTransactions.createAccount.step2.validation.dueDayInvalid'))
        .max(31, t('financeTransactions.createAccount.step2.validation.dueDayInvalid'))
        .optional()
    ),
  }).superRefine((data, ctx) => {
    if (!data.issuerId && !data.issuerName) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: t('financeTransactions.createAccount.step2.validation.issuerRequired'),
        path: ["issuerId"]
      });
    }
  
    if (data.type === 'CARD') {
       // Check if subtype is credit
       if (data.subtype === 'CREDIT_CARD') {
          if (!data.closingDay) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t('financeTransactions.createAccount.step2.validation.closingDayRequired'),
              path: ["closingDay"]
            });
          }
          if (!data.dueDay) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t('financeTransactions.createAccount.step2.validation.dueDayRequired'),
              path: ["dueDay"]
            });
          }
       }
    }
  }), [t]);

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
    label: t(`financeTransactions.createAccount.step2.enums.cardNetwork.${v}`)
  })), [t]);

  const cardLevels = React.useMemo(() => CardLevelValues.map(v => ({
    value: v,
    label: t(`financeTransactions.createAccount.step2.enums.cardLevel.${v}`)
  })), [t]);

  // Queries
  const { data: issuersData, loading: issuersLoading, error: issuersError } = useQuery<{ issuers: Issuer[] }>(ISSUERS, {
    skip: !open,
  });

  const [createAccount, { loading: creating, error: createError }] = useMutation(CREATE_FINANCIAL_ACCOUNT, {
    refetchQueries: [{ query: FINANCIAL_ACCOUNTS }],
    onCompleted: () => {
      success(t('financeTransactions.createAccount.step2.success'));
      handleClose();
    },
  });

  const methods = useForm<CreateAccountFormData>({
    resolver: zodResolver(createAccountSchema),
    defaultValues: {
      name: '',
      issuerId: undefined,
      issuerName: '',
      currencyCode: 'BRL',
      themeColor: '#1976d2',
      includeInNetWorth: true,
      includeInDashboard: true,
    },
  });

  const { watch, setValue, reset, control } = methods;
  const watchedIssuerId = watch('issuerId');
  const watchedSubtype = watch('subtype');
  const watchedCurrencyCode = watch('currencyCode');

  // Memoize options to prevent unnecessary re-renders
  const allIssuerOptions = React.useMemo(() => {
    const options = (issuersData?.issuers || []).map((i) => ({
      label: i.name,
      value: i.id,
      logo: i.logoAsset?.publicUrl,
      compe: i.compeCode,
    }));

    return [
      ...options,
      { label: t('financeTransactions.createAccount.step2.fields.customIssuer'), value: 'custom', logo: null, compe: null }
    ];
  }, [issuersData, t]);

  // Reset on close
  useEffect(() => {
    if (!open) {
      setStep(1);
      setSelectedType(null);
      reset();
    } else if (initialType) {
        // If opening with initialType
        handleTypeSelect(initialType);
    }
  }, [open, reset, initialType]);

  // Handle Issuer Selection
  useEffect(() => {
    if (watchedIssuerId && issuersData?.issuers) {
      if (watchedIssuerId === 'custom') {
        setValue('name', '');
        setValue('issuerName', '');
      } else {
        const issuer = issuersData.issuers.find((i) => i.id === watchedIssuerId);
        if (issuer) {
          const typeLabel = selectedType === 'BANK' 
            ? t('financeTransactions.createAccount.step1.bankAccount.label') 
            : t('financeTransactions.createAccount.step1.card.label');
          setValue('name', `${typeLabel} - ${issuer.name}`);
          setValue('issuerName', issuer.name);
        }
      }
    }
  }, [watchedIssuerId, issuersData, setValue, selectedType, t]);

  const handleClose = () => {
    onClose();
  };

  const handleTypeSelect = (type: 'BANK' | 'CARD') => {
    setSelectedType(type);
    setValue('type', type);
    // Set default subtype based on type
    if (type === 'BANK') setValue('subtype', 'CONTA_CORRENTE');
    else setValue('subtype', 'CREDIT_CARD');
    
    setStep(2);
  };

  const handleBack = () => {
    setStep(1);
    setSelectedType(null);
    reset(); // Reset form values
  };

  const onSubmit = (data: CreateAccountFormData) => {
    const accountType =
      data.type === 'BANK'
        ? 'BANK'
        : data.subtype === 'CREDIT_CARD' || data.subtype === 'CHARGE_CARD'
          ? 'CREDIT'
          : 'PAYMENT';

    // Transform data for mutation
    const input: any = {
      type: accountType,
      subtype: data.subtype,
      name: data.name,
      currencyCode: data.currencyCode,
      themeColor: data.themeColor,
      includeInNetWorth: data.includeInNetWorth,
      includeInDashboard: data.includeInDashboard,
      // Handle issuer
      ...(data.issuerId === 'custom' 
          ? { issuerName: data.issuerName } 
          : { issuerId: data.issuerId }),
    };

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

    createAccount({ variables: { input } });
  };

  const renderTypeSelection = () => (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 4, p: 4, alignItems: 'stretch', justifyContent: 'flex-start', height: '100%' }}>
      {/* Title Section */}
      <Box sx={{ gap: 1, display: 'flex', flexDirection: 'column' }}>
        <Typography variant="body1" color="text.secondary" sx={{ fontSize: '1.125rem' }}>
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
        <form onSubmit={methods.handleSubmit(onSubmit)}>
          <Stack spacing={3}>
            {createError && <FormErrorBanner message={createError.message} />}

            {/* Common Fields */}
              <Controller
                name="issuerId"
                control={control}
                render={({ field: { onChange, value }, fieldState: { error } }) => (
                  <Autocomplete
                    options={allIssuerOptions}
                    loading={issuersLoading}
                    value={allIssuerOptions.find(o => o.value === value) || null}
                    onChange={(_, newValue) => onChange(newValue ? newValue.value : null)}
                    isOptionEqualToValue={(option, value) => option.value === value.value}
                    noOptionsText={t('financeTransactions.createAccount.step2.fields.noIssuersFound')}
                    getOptionLabel={(option) => option.label || ''}
                    renderOption={(props, option: any) => {
                      const { key, ...otherProps } = props;
                      return (
                        <Box component="li" key={key} {...otherProps} sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 1.5 }}>
                          {option.logo ? (
                            <Avatar src={option.logo} sx={{ width: 40, height: 40, borderRadius: 1.5 }} />
                          ) : (
                            <Avatar sx={{ width: 40, height: 40, borderRadius: 1.5, bgcolor: 'action.disabledBackground' }}>
                              <BankIcon sx={{ fontSize: 24 }} />
                            </Avatar>
                          )}
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <Typography variant="body1" fontWeight="600">{option.label}</Typography>
                            {option.compe && (
                              <Typography variant="body2" color="text.secondary">({option.compe})</Typography>
                            )}
                          </Box>
                        </Box>
                      );
                    }}
                    renderInput={(params) => (
                      <MuiTextField
                        {...params}
                        label={t('financeTransactions.createAccount.step2.fields.issuer')}
                        error={!!error}
                        helperText={error?.message}
                        InputProps={{
                          ...params.InputProps,
                          endAdornment: (
                            <React.Fragment>
                              {issuersLoading ? <CircularProgress color="inherit" size={20} /> : null}
                              {params.InputProps.endAdornment}
                            </React.Fragment>
                          ),
                        }}
                      />
                    )}
                  />
                )}
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

            <TextField name="name" label={t('financeTransactions.createAccount.step2.fields.accountName')} required />

            {/* Initial Balance - Visual only for now as it's not in mutation */}
            <MoneyInput
              name="initialBalance"
              label={t('financeTransactions.createAccount.step2.fields.initialBalance')}
              currency={watchedCurrencyCode}
              locale={i18n.language}
            />

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
                
                <TextField 
                  name="maskedNumber" 
                  label={t('financeTransactions.createAccount.step2.fields.maskedNumber')} 
                  placeholder="1234"
                  InputProps={{ startAdornment: <InputAdornment position="start">••••</InputAdornment> }}
                />

                {watchedSubtype === 'CREDIT_CARD' && (
                  <>
                    <MoneyInput 
                      name="creditLimit" 
                      label={t('financeTransactions.createAccount.step2.fields.creditLimit')} 
                      currency={watchedCurrencyCode}
                      locale={i18n.language}
                    />
                    <Stack direction="row" spacing={2}>
                      <TextField 
                        name="closingDay" 
                        label={t('financeTransactions.createAccount.step2.fields.closingDay')} 
                        type="number" 
                        inputProps={{ min: 1, max: 31 }}
                        required
                      />
                      <TextField 
                        name="dueDay" 
                        label={t('financeTransactions.createAccount.step2.fields.dueDay')} 
                        type="number"
                        inputProps={{ min: 1, max: 31 }}
                        required
                      />
                    </Stack>
                  </>
                )}
              </>
            )}

            {/* <Stack direction="row" spacing={4}>
              <ToggleField name="includeInNetWorth" label={t('financeTransactions.createAccount.step2.fields.includeInNetWorth')} />
              <ToggleField name="includeInDashboard" label={t('financeTransactions.createAccount.step2.fields.includeInDashboard')} />
            </Stack> */}

            {/* <Box>
              <Typography variant="subtitle2" gutterBottom>{t('financeTransactions.createAccount.step2.fields.themeColor')}</Typography>
              <ColorPicker 
                value={watch('themeColor')} 
                onChange={(c) => setValue('themeColor', c)} 
                presets={ColorPresets}
              />
            </Box> */}
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
      {step === 2 && (
          <>
            <Divider sx={{ marginY: 3 }} />
            <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
              <Button 
                variant="contained" 
                onClick={methods.handleSubmit(onSubmit)}
                disabled={creating}
                startIcon={creating ? <CircularProgress size={20} color="inherit" /> : null}
              >
                {selectedType === 'BANK' ? t('financeTransactions.actions.addAccount') : t('financeTransactions.actions.addCard')}
              </Button>
            </Box>
          </>
      )}
          </Drawer.Body>
    </Drawer>
  );
}
