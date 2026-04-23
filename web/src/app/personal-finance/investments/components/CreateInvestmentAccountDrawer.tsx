'use client';

import React, { useEffect } from 'react';
import { useMutation, useQuery } from '@apollo/client/react';
import { zodResolver } from '@hookform/resolvers/zod';
import { Box, Divider } from '@mui/material';
import { FormStack } from '@/shared/components/ui/forms/components';
import { FormProvider, useController, useForm, useFormState, useWatch } from 'react-hook-form';
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
import { ErrorAlert } from '@/shared/components/ui/feedback';
import { FormTextField as TextField, SelectField } from '@/shared/components/ui/forms/form';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { useToast } from '@/shared/providers';
import { extractErrorMessage } from '@/shared/utils/error';
import { personalFinanceTranslations } from '../../i18n';
import { IssuerSelect } from '../../finance-transactions/account/IssuerSelect';

interface CreateInvestmentAccountDrawerProps {
  open: boolean;
  onClose: () => void;
  onCreated?: (account: { id: string; name: string }) => void;
}

const InvestmentSubtypeValues = ['BROKERAGE', 'FIXED_INCOME', 'PENSION', 'CRYPTO', 'FGTS', 'INSS'] as const;
type InvestmentSubtype = (typeof InvestmentSubtypeValues)[number];

type CreateInvestmentAccountFormData = {
  subtype: InvestmentSubtype;
  issuerId?: string;
  issuerName?: string;
  name: string;
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

  const { setValue, reset, control, getValues } = methods;
  const { dirtyFields } = useFormState({ control });
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

  const issuerCategories = React.useMemo<IssuerCategory[]>(() => {
    if (watchedSubtype === 'CRYPTO') return [IssuerCategory.CryptoExchange];
    if (watchedSubtype === 'FGTS' || watchedSubtype === 'INSS') return [IssuerCategory.Bank];
    return [IssuerCategory.Bank, IssuerCategory.Brokerage];
  }, [watchedSubtype]);

  const { data: issuersData, loading: issuersLoading } = useQuery<{ issuers: Issuer[] }>(ISSUERS, {
    variables: { categories: issuerCategories },
    skip: !open,
  });

  const selectedIssuer = React.useMemo(() => {
    if (!watchedIssuerId || watchedIssuerId === 'custom') return undefined;
    return issuersData?.issuers?.find((issuer) => issuer.id === watchedIssuerId);
  }, [issuersData?.issuers, watchedIssuerId]);

  const investmentSubtypes = React.useMemo(
    () =>
      ((
        selectedIssuer?.compeCode === '104' || watchedSubtype === 'FGTS' || watchedSubtype === 'INSS'
          ? InvestmentSubtypeValues
          : (['BROKERAGE', 'FIXED_INCOME', 'PENSION', 'CRYPTO'] as const)
      ) satisfies readonly InvestmentSubtype[]).map((value) => ({
        value,
        label: t(`investments.createAccount.subtypes.${value}`),
      })),
    [selectedIssuer?.compeCode, t, watchedSubtype]
  );

  useEffect(() => {
    if (!open) return;

    const previousIssuerId = previousIssuerIdRef.current;
    previousIssuerIdRef.current = watchedIssuerId;

    if (!watchedIssuerId) {
      if (previousIssuerId) {
        setValue('issuerName', '', { shouldDirty: false, shouldValidate: false });
        const currentName = getValues('name');
        const nameIsDirty = !!dirtyFields.name;
        if (!nameIsDirty && currentName === lastAutoNameRef.current) {
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
        const nameIsDirty = !!dirtyFields.name;
        if (
          !nameIsDirty &&
          (currentName.trim() === '' || currentName === lastAutoNameRef.current)
        ) {
          setValue('name', '', { shouldDirty: false, shouldValidate: false });
          lastAutoNameRef.current = null;
        }
      }
      return;
    }

    if (!issuersData?.issuers) return;

    const selectedIssuerFromList = issuersData.issuers.find((issuer) => issuer.id === watchedIssuerId);
    if (!selectedIssuerFromList) {
      setValue('issuerId', undefined, { shouldDirty: false, shouldValidate: true });
      setValue('issuerName', '', { shouldDirty: false, shouldValidate: false });
      const currentName = getValues('name');
      const nameIsDirty = !!dirtyFields.name;
      if (
        !nameIsDirty &&
        (currentName.trim() === '' || currentName === lastAutoNameRef.current)
      ) {
        setValue('name', '', { shouldDirty: false, shouldValidate: false });
        lastAutoNameRef.current = null;
      }
      return;
    }

    const subtypeLabel = t(`investments.createAccount.subtypes.${watchedSubtype}`);
    const autoName = `${subtypeLabel} - ${selectedIssuerFromList.name}`;

    setValue('issuerName', selectedIssuerFromList.name, { shouldDirty: false, shouldValidate: false });

    const currentName = getValues('name');
    const nameIsDirty = !!dirtyFields.name;
    const shouldAutofillName =
      !nameIsDirty || currentName.trim() === '' || currentName === lastAutoNameRef.current;

    if (shouldAutofillName) {
      setValue('name', autoName, { shouldDirty: false, shouldValidate: false });
      lastAutoNameRef.current = autoName;
    }
  }, [dirtyFields.name, getValues, issuersData?.issuers, open, setValue, t, watchedIssuerId, watchedSubtype]);

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
    const type =
      data.subtype === 'FGTS' || data.subtype === 'INSS'
        ? ('BENEFIT' as unknown as FinancialAccountType)
        : FinancialAccountType.Investment;

    createAccount({
      variables: {
        input: {
          type,
          subtype: data.subtype as unknown as FinancialAccountSubtype,
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
        title={t('investments.createAccount.title')}
      />
      <Drawer.Body>
        <FormProvider {...methods}>
          <form id="create-investment-account-form" onSubmit={methods.handleSubmit(onSubmit)}>
            <FormStack>
              <ErrorAlert
                error={
                  createError ? extractErrorMessage(createError, t('errors.default', { ns: 'common' })) : null
                }
              />

              <IssuerSelect
                issuers={issuersData?.issuers}
                loading={issuersLoading}
                value={issuerIdValue}
                onChange={issuerIdField.onChange}
                label={t('investments.createAccount.fields.issuerLabel')}
                placeholder={t('investments.createAccount.fields.issuerPlaceholder')}
                noOptionsText={t('financeTransactions.createAccount.step2.fields.noIssuersFound')}
                customIssuerLabel={t('financeTransactions.createAccount.step2.fields.customIssuer')}
                error={!!issuerIdFieldState.error}
                helperText={issuerIdFieldState.error?.message}
                size="medium"
              />

              {watchedIssuerId === 'custom' && (
                <TextField
                  name="issuerName"
                  label={t('investments.createAccount.fields.customIssuerLabel')}
                  required
                  size="medium"
                />
              )}

              <Box data-testid="investment-subtype-select">
                <SelectField
                  name="subtype"
                  label={t('investments.createAccount.fields.subtypeLabel')}
                  options={investmentSubtypes}
                  size="medium"
                />
              </Box>

              <TextField
                name="name"
                label={t('investments.createAccount.fields.accountNameLabel')}
                placeholder={t('investments.createAccount.fields.accountNamePlaceholder')}
                inputProps={{ 'data-testid': 'investment-account-name-input' }}
                size="medium"
              />

              <Divider />

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

            </FormStack>
          </form>
        </FormProvider>
      </Drawer.Body>
    </Drawer>
  );
}
