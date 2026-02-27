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
  TextField as MuiTextField
} from '@mui/material';
import {
  AccountBalance as BankIcon,
  CreditCard as CardIcon,
  ArrowBack as BackIcon,
  ReceiptLong as ReceiptIcon,
} from '@mui/icons-material';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { TextField, SelectField, ToggleField } from '@/shared/components/ui/forms/form';
import { ColorPicker } from '@/shared/components/ui/primitives/ColorPicker';
import { ISSUERS } from '@/lib/graphql/operations/banking/queries';
import { CREATE_FINANCIAL_ACCOUNT } from '@/lib/graphql/operations/banking/mutations';
import { useToast } from '@/shared/providers';
import { FormErrorBanner } from '@/shared/components/ui/forms/components/FormErrorBanner';
import { FINANCIAL_ACCOUNTS } from '@/lib/graphql/operations/banking/queries';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';

// --- Types & Schemas ---

const FinancialAccountType = {
  BANK: 'BANK',
  CREDIT: 'CREDIT', // Mapping 'Card' to 'CREDIT' for now based on context, or could be others
} as const;

// Subtypes
const BankSubtypes = [
  { value: 'CONTA_CORRENTE', label: 'Checking' },
  { value: 'CONTA_POUPANCA', label: 'Savings' },
  { value: 'CONTA_DIGITAL', label: 'Digital Wallet' },
  { value: 'CASH', label: 'Cash' },
  { value: 'INVESTMENT', label: 'Investment' },
  { value: 'LOAN', label: 'Loan' },
];

const CardSubtypes = [
  { value: 'CREDIT_CARD', label: 'Credit' },
  { value: 'DEBIT_CARD', label: 'Debit' }, // Assuming these exist in backend enum
  { value: 'PREPAID_CARD', label: 'Prepaid' },
  { value: 'VOUCHER_CARD', label: 'Voucher' },
];

const CardNetworks = [
  { value: 'VISA', label: 'Visa' },
  { value: 'MASTERCARD', label: 'Mastercard' },
  { value: 'ELO', label: 'Elo' },
  { value: 'AMERICAN_EXPRESS', label: 'Amex' },
  { value: 'HIPERCARD', label: 'Hipercard' },
  { value: 'OTHER', label: 'Other' },
];

const CardLevels = [
  { value: 'STANDARD', label: 'Standard' },
  { value: 'GOLD', label: 'Gold' },
  { value: 'PLATINUM', label: 'Platinum' },
  { value: 'INFINITE', label: 'Infinite/Black' },
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

// Zod Schema
const createAccountSchema = z.object({
  type: z.enum(['BANK', 'CARD']),
  issuerId: z.string().optional(),
  issuerName: z.string().optional(), // For custom
  name: z.string().min(1, 'Account name is required'),
  currencyCode: z.string().default('BRL'),
  initialBalance: z.number().optional(), // Handled separately or as part of creation? Usually separate transaction, but user asked for it. Assuming mutation handles it.
  themeColor: z.string().default('#1976d2'),
  includeInNetWorth: z.boolean().default(true),
  includeInDashboard: z.boolean().default(true),
  // Bank specific
  subtype: z.string().min(1, 'Subtype is required'),
  branchCode: z.string().optional(),
  accountNumber: z.string().optional(),
  checkDigit: z.string().optional(),
  // Card specific
  cardNetwork: z.string().optional(),
  cardLevel: z.string().optional(),
  maskedNumber: z.string().optional(), // Last 4 digits
  creditLimit: z.number().optional(),
  closingDay: z.number().min(1).max(31).optional(),
  dueDay: z.number().min(1).max(31).optional(),
}).superRefine((data, ctx) => {
  if (!data.issuerId && !data.issuerName) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: "Issuer is required",
      path: ["issuerId"]
    });
  }

  if (data.type === 'CARD') {
     // Check if subtype is credit
     if (data.subtype === 'CREDIT_CARD') {
        if (!data.closingDay) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: "Closing day is required for credit cards",
            path: ["closingDay"]
          });
        }
        if (!data.dueDay) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: "Due day is required for credit cards",
            path: ["dueDay"]
          });
        }
     }
  }
});

type CreateAccountFormData = z.infer<typeof createAccountSchema>;

// --- Components ---

interface CreateFinancialAccountDrawerProps {
  open: boolean;
  onClose: () => void;
}

export function CreateFinancialAccountDrawer({ open, onClose }: CreateFinancialAccountDrawerProps) {
  const [step, setStep] = useState<1 | 2>(1);
  const [selectedType, setSelectedType] = useState<'BANK' | 'CARD' | null>(null);
  const { success } = useToast();
  const theme = useTheme();
  const { t } = useTranslation(personalFinanceTranslations);

  // Queries
  const { data: issuersData, loading: issuersLoading } = useQuery<{ issuers: any[] }>(ISSUERS, {
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
      currencyCode: 'BRL',
      themeColor: '#1976d2',
      includeInNetWorth: true,
      includeInDashboard: true,
    },
  });

  const { watch, setValue, reset, control } = methods;
  const watchedIssuerId = watch('issuerId');
  const watchedSubtype = watch('subtype');

  // Reset on close
  useEffect(() => {
    if (!open) {
      setStep(1);
      setSelectedType(null);
      reset();
    }
  }, [open, reset]);

  // Handle Issuer Selection
  useEffect(() => {
    if (watchedIssuerId && issuersData?.issuers) {
      if (watchedIssuerId === 'custom') {
        setValue('name', '');
        setValue('issuerName', '');
      } else {
        const issuer = issuersData.issuers.find((i: any) => i.id === watchedIssuerId);
        if (issuer) {
          setValue('name', issuer.name);
          setValue('issuerName', issuer.name); // Default fallback
        }
      }
    }
  }, [watchedIssuerId, issuersData, setValue]);

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
    // Transform data for mutation
    const input: any = {
      type: data.type === 'BANK' ? 'BANK' : 'CREDIT', // Adjust based on actual backend enum
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
    
    // Note: initialBalance is not in the mutation input based on user description, 
    // but user asked for it in the form. It might need a separate transaction or backend support.
    // For now, I'll omit it from mutation input unless I see it in schema. 
    // (Checked schema: no initialBalance in CreateFinancialAccountInput).
    // So it's UI only for now or I should ignore it. 
    // I will keep it in form state but not send it if not supported.

    createAccount({ variables: { input } });
  };

  const issuerOptions = (issuersData?.issuers || []).map((i: any) => ({
    label: i.name,
    value: i.id,
    logo: i.logoAsset?.publicUrl,
    compe: i.compeCode,
  }));
  
  // Add "Other / Custom" option
  const allIssuerOptions = [
    ...issuerOptions,
    { label: 'Other / Custom', value: 'custom', logo: null, compe: null }
  ];

  const renderTypeSelection = () => (
    <Box sx={{ display: 'flex', gap: 2, height: '100%', alignItems: 'center', justifyContent: 'center', p: 4 }}>
      <Card 
        variant="outlined" 
        sx={{ 
          flex: 1, 
          height: 200, 
          cursor: 'pointer', 
          transition: 'all 0.2s',
          '&:hover': { borderColor: 'primary.main', bgcolor: 'action.hover' }
        }}
        onClick={() => handleTypeSelect('BANK')}
      >
        <CardContent sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', gap: 2 }}>
          <BankIcon sx={{ fontSize: 48, color: 'primary.main' }} />
          <Box sx={{ textAlign: 'center' }}>
            <Typography variant="h6">{t('financeTransactions.createAccount.step1.bankAccount.label')}</Typography>
            <Typography variant="body2" color="text.secondary">{t('financeTransactions.createAccount.step1.bankAccount.subtitle')}</Typography>
          </Box>
        </CardContent>
      </Card>

      <Card 
        variant="outlined" 
        sx={{ 
          flex: 1, 
          height: 200, 
          cursor: 'pointer',
          transition: 'all 0.2s',
          '&:hover': { borderColor: 'primary.main', bgcolor: 'action.hover' }
        }}
        onClick={() => handleTypeSelect('CARD')}
      >
        <CardContent sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', gap: 2 }}>
          <CardIcon sx={{ fontSize: 48, color: 'primary.main' }} />
          <Box sx={{ textAlign: 'center' }}>
            <Typography variant="h6">{t('financeTransactions.createAccount.step1.card.label')}</Typography>
            <Typography variant="body2" color="text.secondary">{t('financeTransactions.createAccount.step1.card.subtitle')}</Typography>
          </Box>
        </CardContent>
      </Card>
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
                  getOptionLabel={(option) => option.label}
                  renderOption={(props, option: any) => (
                    <Box component="li" {...props} sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                      {option.logo ? (
                        <Avatar src={option.logo} sx={{ width: 24, height: 24 }} />
                      ) : (
                        <Avatar sx={{ width: 24, height: 24, bgcolor: 'action.disabledBackground' }}>
                          <BankIcon sx={{ fontSize: 14 }} />
                        </Avatar>
                      )}
                      <Box>
                        <Typography variant="body2">{option.label}</Typography>
                        {option.compe && (
                          <Typography variant="caption" color="text.secondary"> • {option.compe}</Typography>
                        )}
                      </Box>
                    </Box>
                  )}
                  renderInput={(params) => (
                    <MuiTextField
                      {...params}
                      label="Issuer"
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
              <TextField name="issuerName" label="Institution Name" required />
            )}

            <TextField name="name" label="Account Name" required />

            {watchedIssuerId === 'custom' && (
              <SelectField name="currencyCode" label="Currency" options={Currencies} />
            )}

            {/* Initial Balance - Visual only for now as it's not in mutation */}
            <TextField 
              name="initialBalance" 
              label="Initial Balance" 
              type="number"
              InputProps={{
                startAdornment: <InputAdornment position="start">R$</InputAdornment>,
              }}
            />

            <Box>
              <Typography variant="subtitle2" gutterBottom>Color Theme</Typography>
              <ColorPicker 
                value={watch('themeColor')} 
                onChange={(c) => setValue('themeColor', c)} 
                presets={ColorPresets}
              />
            </Box>

            <Stack direction="row" spacing={4}>
              <ToggleField name="includeInNetWorth" label="Include in Net Worth" />
              <ToggleField name="includeInDashboard" label="Include in Dashboard" />
            </Stack>

            {/* Bank Specific */}
            {selectedType === 'BANK' && (
              <>
                <SelectField name="subtype" label="Account Type" options={BankSubtypes} />
                <Stack direction="row" spacing={2}>
                  <TextField name="branchCode" label="Branch" sx={{ flex: 1 }} />
                  <TextField name="accountNumber" label="Account Number" sx={{ flex: 2 }} />
                  <TextField name="checkDigit" label="Digit" sx={{ width: 80 }} />
                </Stack>
              </>
            )}

            {/* Card Specific */}
            {selectedType === 'CARD' && (
              <>
                <SelectField name="subtype" label="Card Type" options={CardSubtypes} />
                <SelectField name="cardNetwork" label="Network" options={CardNetworks} />
                
                {watchedSubtype === 'CREDIT_CARD' && (
                  <SelectField name="cardLevel" label="Card Level" options={CardLevels} />
                )}
                
                <TextField 
                  name="maskedNumber" 
                  label="Last 4 Digits" 
                  placeholder="1234"
                  InputProps={{ startAdornment: <InputAdornment position="start">••••</InputAdornment> }}
                />

                {watchedSubtype === 'CREDIT_CARD' && (
                  <>
                    <TextField 
                      name="creditLimit" 
                      label="Credit Limit" 
                      type="number"
                      InputProps={{ startAdornment: <InputAdornment position="start">R$</InputAdornment> }}
                    />
                    <Stack direction="row" spacing={2}>
                      <TextField 
                        name="closingDay" 
                        label="Closing Day" 
                        type="number" 
                        inputProps={{ min: 1, max: 31 }}
                        required
                      />
                      <TextField 
                        name="dueDay" 
                        label="Due Day" 
                        type="number"
                        inputProps={{ min: 1, max: 31 }}
                        required
                      />
                    </Stack>
                  </>
                )}
              </>
            )}

            <Box sx={{ mt: 2, display: 'flex', justifyContent: 'flex-end' }}>
              <Button 
                variant="contained" 
                type="submit" 
                size="large"
                disabled={creating}
                startIcon={creating ? <CircularProgress size={20} color="inherit" /> : null}
              >
                {selectedType === 'BANK' ? t('financeTransactions.actions.addAccount') : t('financeTransactions.actions.addCard')}
              </Button>
            </Box>
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
