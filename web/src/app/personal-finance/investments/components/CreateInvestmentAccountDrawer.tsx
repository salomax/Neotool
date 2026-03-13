'use client';

import React, { useEffect } from 'react';
import { useMutation, useQuery } from '@apollo/client/react';
import { zodResolver } from '@hookform/resolvers/zod';
import { AccountBalance as BankIcon } from '@mui/icons-material';
import {
  Autocomplete,
  Avatar,
  Box,
  Button,
  CircularProgress,
  Divider,
  Stack,
  TextField as MuiTextField,
  Typography,
} from '@mui/material';
import { Controller, FormProvider, useForm, useWatch } from 'react-hook-form';
import { z } from 'zod';
import { CREATE_FINANCIAL_ACCOUNT } from '@/lib/graphql/operations/banking/mutations';
import { FINANCIAL_ACCOUNTS, ISSUERS } from '@/lib/graphql/operations/banking/queries';
import {
  FinancialAccountSubtype,
  FinancialAccountType,
  Issuer,
  IssuerCategory,
} from '@/lib/graphql/types/__generated__/graphql';
import { useTranslation } from '@/shared/i18n';
import { FormErrorBanner } from '@/shared/components/ui/forms/components/FormErrorBanner';
import { FormTextField as TextField, SelectField } from '@/shared/components/ui/forms/form';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { useToast } from '@/shared/providers';
import { personalFinanceTranslations } from '../../i18n';

interface CreateInvestmentAccountDrawerProps {
  open: boolean;
  onClose: () => void;
  onCreated?: (account: { id: string; name: string }) => void;
}

const InvestmentSubtypeValues = ['BROKERAGE', 'FIXED_INCOME', 'PENSION', 'CRYPTO'] as const;
type InvestmentSubtype = (typeof InvestmentSubtypeValues)[number];

type CreateInvestmentAccountFormData = {
  subtype: InvestmentSubtype;
  issuerId?: string;
  issuerName?: string;
  name: string;
};

type IssuerOption = {
  label: string;
  value: string;
  logo: string | null;
  compe: string | null;
};

export function CreateInvestmentAccountDrawer({
  open,
  onClose,
  onCreated,
}: CreateInvestmentAccountDrawerProps) {
  const { success } = useToast();
  const { t } = useTranslation(personalFinanceTranslations);

  const createAccountSchema = React.useMemo(
    () =>
      z
        .object({
          subtype: z.enum(InvestmentSubtypeValues, {
            required_error: t('investments.createAccount.validation.subtypeRequired'),
          }),
          issuerId: z.string().optional(),
          issuerName: z.string().optional(),
          name: z.string().default(''),
        })
        .superRefine((data, ctx) => {
          const issuerName = data.issuerName?.trim();

          if (!data.issuerId && !issuerName) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t('investments.createAccount.validation.issuerRequired'),
              path: ['issuerId'],
            });
            return;
          }

          if (data.issuerId === 'custom' && !issuerName) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t('investments.createAccount.validation.issuerRequired'),
              path: ['issuerName'],
            });
          }
        }),
    [t]
  );

  const methods = useForm<CreateInvestmentAccountFormData>({
    resolver: zodResolver(createAccountSchema),
    defaultValues: {
      subtype: 'BROKERAGE',
      name: '',
      issuerId: undefined,
      issuerName: '',
    },
  });

  const { setValue, reset, control } = methods;
  const watchedSubtype = useWatch({ control, name: 'subtype' });
  const watchedIssuerId = useWatch({ control, name: 'issuerId' });

  useEffect(() => {
    if (!open) {
      reset();
    }
  }, [open, reset]);

  const issuerCategories = React.useMemo<IssuerCategory[]>(
    () =>
      watchedSubtype === 'CRYPTO'
        ? [IssuerCategory.CryptoExchange]
        : [IssuerCategory.Bank, IssuerCategory.Brokerage],
    [watchedSubtype]
  );

  const { data: issuersData, loading: issuersLoading } = useQuery<{ issuers: Issuer[] }>(ISSUERS, {
    variables: { categories: issuerCategories },
    skip: !open,
  });

  const investmentSubtypes = React.useMemo(
    () =>
      InvestmentSubtypeValues.map((value) => ({
        value,
        label: t(`investments.createAccount.subtypes.${value}`),
      })),
    [t]
  );

  const allIssuerOptions = React.useMemo<IssuerOption[]>(() => {
    const options = (issuersData?.issuers || []).map((issuer) => ({
      label: issuer.name,
      value: issuer.id,
      logo: issuer.logoAsset?.publicUrl || null,
      compe: issuer.compeCode || null,
    }));

    return [
      ...options,
      {
        label: t('financeTransactions.createAccount.step2.fields.customIssuer'),
        value: 'custom',
        logo: null,
        compe: null,
      },
    ];
  }, [issuersData, t]);

  useEffect(() => {
    if (!watchedIssuerId || !issuersData?.issuers) {
      return;
    }

    if (watchedIssuerId === 'custom') {
      setValue('name', '');
      setValue('issuerName', '');
      return;
    }

    const selectedIssuer = issuersData.issuers.find((issuer) => issuer.id === watchedIssuerId);
    if (!selectedIssuer) {
      return;
    }

    const subtypeLabel = t(`investments.createAccount.subtypes.${watchedSubtype}`);
    setValue('name', `${subtypeLabel} - ${selectedIssuer.name}`);
    setValue('issuerName', selectedIssuer.name);
  }, [issuersData, setValue, t, watchedIssuerId, watchedSubtype]);

  const [createAccount, { loading: creating, error: createError }] = useMutation<{
    createFinancialAccount: { id: string; name: string };
  }>(CREATE_FINANCIAL_ACCOUNT, {
    refetchQueries: [{ query: FINANCIAL_ACCOUNTS }],
    onCompleted: (data) => {
      success(t('investments.createAccount.success'));
      onCreated?.({
        id: data.createFinancialAccount.id,
        name: data.createFinancialAccount.name,
      });
      onClose();
    },
  });

  const onSubmit = (data: CreateInvestmentAccountFormData) => {
    const name = data.name.trim();
    const issuerName = data.issuerName?.trim();

    createAccount({
      variables: {
        input: {
          type: FinancialAccountType.Investment,
          subtype: data.subtype as FinancialAccountSubtype,
          name: name || undefined,
          currencyCode: 'BRL',
          includeInNetWorth: true,
          includeInDashboard: true,
          ...(data.issuerId === 'custom' ? { issuerName } : { issuerId: data.issuerId }),
        },
      },
    });
  };

  return (
    <Drawer open={open} onClose={onClose} anchor="right" size="md">
      <Drawer.Header
        title={
          watchedSubtype
            ? t(`investments.createAccount.subtypes.${watchedSubtype}`)
            : t('investments.createAccount.title')
        }
      />
      <Drawer.Body>
        <FormProvider {...methods}>
          <form onSubmit={methods.handleSubmit(onSubmit)}>
            <Stack spacing={3}>
              {createError && <FormErrorBanner message={createError.message} />}

              <Box data-testid="investment-subtype-select">
                <SelectField
                  name="subtype"
                  label={t('investments.createAccount.fields.subtypeLabel')}
                  options={investmentSubtypes}
                />
              </Box>

              <Controller
                name="issuerId"
                control={control}
                render={({ field: { onChange, value }, fieldState: { error } }) => (
                  <Autocomplete<IssuerOption, false, false, false>
                    options={allIssuerOptions}
                    loading={issuersLoading}
                    value={allIssuerOptions.find((option) => option.value === value) || null}
                    onChange={(_, newValue) => onChange(newValue ? newValue.value : undefined)}
                    isOptionEqualToValue={(option, selectedValue) => option.value === selectedValue.value}
                    noOptionsText={t('financeTransactions.createAccount.step2.fields.noIssuersFound')}
                    getOptionLabel={(option) => option.label || ''}
                    renderOption={(props, option) => {
                      const { key, ...otherProps } = props;
                      return (
                        <Box
                          component="li"
                          key={key}
                          {...otherProps}
                          sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 1.5 }}
                        >
                          {option.logo ? (
                            <Avatar src={option.logo} sx={{ width: 40, height: 40, borderRadius: 1.5 }} />
                          ) : (
                            <Avatar
                              sx={{
                                width: 40,
                                height: 40,
                                borderRadius: 1.5,
                                bgcolor: 'action.disabledBackground',
                              }}
                            >
                              <BankIcon sx={{ fontSize: 24 }} />
                            </Avatar>
                          )}
                          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                            <Typography variant="body1" fontWeight="600">
                              {option.label}
                            </Typography>
                            {option.compe && (
                              <Typography variant="body2" color="text.secondary">
                                ({option.compe})
                              </Typography>
                            )}
                          </Box>
                        </Box>
                      );
                    }}
                    renderInput={(params) => (
                      <MuiTextField
                        {...params}
                        label={t('investments.createAccount.fields.issuerLabel')}
                        placeholder={t('investments.createAccount.fields.issuerPlaceholder')}
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
                <TextField
                  name="issuerName"
                  label={t('investments.createAccount.fields.customIssuerLabel')}
                  required
                />
              )}

              <TextField
                name="name"
                label={t('investments.createAccount.fields.accountNameLabel')}
                placeholder={t('investments.createAccount.fields.accountNamePlaceholder')}
                inputProps={{ 'data-testid': 'investment-account-name-input' }}
              />
            </Stack>
          </form>
        </FormProvider>

        <Divider sx={{ marginY: 3 }} />
        <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
          <Button
            variant="contained"
            onClick={methods.handleSubmit(onSubmit)}
            disabled={creating}
            startIcon={creating ? <CircularProgress size={20} color="inherit" /> : null}
          >
            {creating
              ? t('investments.createAccount.actions.submitting')
              : t('investments.createAccount.actions.submit')}
          </Button>
        </Box>
      </Drawer.Body>
    </Drawer>
  );
}
