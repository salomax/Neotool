'use client';

import React, { useEffect } from 'react';
import { useMutation, useQuery } from '@apollo/client/react';
import { zodResolver } from '@hookform/resolvers/zod';
import { AccountBalance as BankIcon } from '@mui/icons-material';
import {
  Autocomplete,
  Avatar,
  Box,
  CircularProgress,
  Stack,
  Typography,
} from '@mui/material';
import { FormProvider, useController, useForm, useWatch } from 'react-hook-form';
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
import { TextField as ThemedTextField } from '@/shared/components/ui/forms/form/TextField';
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
  compeCode: string | null;
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

  const { setValue, reset, control, getValues, getFieldState } = methods;
  const watchedSubtype = useWatch({ control, name: 'subtype' });
  const watchedIssuerId = useWatch({ control, name: 'issuerId' });
  const previousIssuerIdRef = React.useRef<string | undefined>(undefined);
  const lastAutoNameRef = React.useRef<string | null>(null);
  const { field: issuerIdField, fieldState: issuerIdFieldState } = useController({
    name: 'issuerId',
    control,
  });
  const issuerIdValue = typeof issuerIdField.value === 'string' ? issuerIdField.value : undefined;

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
      compeCode: issuer.compeCode || null,
    }));

    return [
      ...options,
      {
        label: t('financeTransactions.createAccount.step2.fields.customIssuer'),
        value: 'custom',
        logo: null,
        compeCode: null,
      },
    ];
  }, [issuersData?.issuers, t]);

  useEffect(() => {
    if (!open) return;

    const previousIssuerId = previousIssuerIdRef.current;
    previousIssuerIdRef.current = watchedIssuerId;

    if (!watchedIssuerId) {
      if (previousIssuerId) {
        setValue('issuerName', '', { shouldDirty: false, shouldValidate: false });
        const currentName = getValues('name');
        if (!getFieldState('name').isDirty && currentName === lastAutoNameRef.current) {
          setValue('name', '', { shouldDirty: false, shouldValidate: false });
          lastAutoNameRef.current = null;
        }
      }
      return;
    }

    if (watchedIssuerId === 'custom') {
      if (previousIssuerId !== 'custom') {
        setValue('issuerName', '', { shouldDirty: false, shouldValidate: true });
        const currentName = getValues('name');
        if (
          !getFieldState('name').isDirty &&
          (currentName.trim() === '' || currentName === lastAutoNameRef.current)
        ) {
          setValue('name', '', { shouldDirty: false, shouldValidate: false });
          lastAutoNameRef.current = null;
        }
      }
      return;
    }

    if (!issuersData?.issuers) return;

    const selectedIssuer = issuersData.issuers.find((issuer) => issuer.id === watchedIssuerId);
    if (!selectedIssuer) {
      setValue('issuerId', undefined, { shouldDirty: false, shouldValidate: true });
      setValue('issuerName', '', { shouldDirty: false, shouldValidate: false });
      const currentName = getValues('name');
      if (
        !getFieldState('name').isDirty &&
        (currentName.trim() === '' || currentName === lastAutoNameRef.current)
      ) {
        setValue('name', '', { shouldDirty: false, shouldValidate: false });
        lastAutoNameRef.current = null;
      }
      return;
    }

    const subtypeLabel = t(`investments.createAccount.subtypes.${watchedSubtype}`);
    const autoName = `${subtypeLabel} - ${selectedIssuer.name}`;

    setValue('issuerName', selectedIssuer.name, { shouldDirty: false, shouldValidate: false });

    const currentName = getValues('name');
    const nameIsDirty = getFieldState('name').isDirty;
    const shouldAutofillName =
      !nameIsDirty || currentName.trim() === '' || currentName === lastAutoNameRef.current;

    if (shouldAutofillName) {
      setValue('name', autoName, { shouldDirty: false, shouldValidate: false });
      lastAutoNameRef.current = autoName;
    }
  }, [getFieldState, getValues, issuersData?.issuers, open, setValue, t, watchedIssuerId, watchedSubtype]);

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
          <form id="create-investment-account-form" onSubmit={methods.handleSubmit(onSubmit)}>
            <Stack spacing={3}>
              {createError && <FormErrorBanner message={createError.message} />}

              <Box data-testid="investment-subtype-select">
                <SelectField
                  name="subtype"
                  label={t('investments.createAccount.fields.subtypeLabel')}
                  options={investmentSubtypes}
                />
              </Box>

              <Autocomplete<IssuerOption, false, false, false>
                options={allIssuerOptions}
                loading={issuersLoading}
                value={allIssuerOptions.find((option) => option.value === issuerIdValue) || null}
                onChange={(_, newValue) => issuerIdField.onChange(newValue ? newValue.value : undefined)}
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
                        {option.compeCode && (
                          <Typography variant="body2" color="text.secondary">
                            ({option.compeCode})
                          </Typography>
                        )}
                      </Box>
                    </Box>
                  );
                }}
                renderInput={(params) => (
                  <ThemedTextField
                    {...params}
                    label={t('investments.createAccount.fields.issuerLabel')}
                    placeholder={t('investments.createAccount.fields.issuerPlaceholder')}
                    fullWidth
                    size="medium"
                    error={!!issuerIdFieldState.error}
                    helperText={issuerIdFieldState.error?.message}
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
      </Drawer.Body>
      <Drawer.Footer>
        <Drawer.ActionButtons
          cancelText={t('actions.cancel')}
          submitText={t('investments.createAccount.actions.submit')}
          submittingText={t('investments.createAccount.actions.submitting')}
          onCancel={onClose}
          submitForm="create-investment-account-form"
          cancelDisabled={creating}
          submitDisabled={creating}
          submitting={creating}
        />
      </Drawer.Footer>
    </Drawer>
  );
}
