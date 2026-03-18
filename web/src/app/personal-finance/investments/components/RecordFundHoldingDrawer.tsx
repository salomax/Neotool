"use client";

import React, { useEffect } from "react";
import { useMutation } from "@apollo/client/react";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  Avatar,
  Box,
  Button,
  CircularProgress,
  Divider,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Paper,
  Stack,
  TextField,
  Typography,
  alpha,
  useTheme,
} from "@mui/material";
import { Controller, FormProvider, useForm } from "react-hook-form";
import dayjs from "dayjs";
import { z } from "zod";
import { CREATE_INVESTMENT_ASSET, RECORD_INVESTMENT_TRANSACTION } from "@/lib/graphql/operations/banking/mutations";
import { ALL_INVESTMENT_HOLDINGS, INVESTMENT_HOLDINGS } from "@/lib/graphql/operations/banking/queries";
import {
  CreateInvestmentAssetMutation,
  CreateInvestmentAssetMutationVariables,
  RecordInvestmentTransactionMutation,
  RecordInvestmentTransactionMutationVariables,
} from "@/lib/graphql/operations/banking/mutations.generated";
import { FinancialAccount, InvestmentTransactionDirection, InvestmentTransactionType } from "@/lib/graphql/types/__generated__/graphql";
import { FormErrorBanner } from "@/shared/components/ui/forms/components/FormErrorBanner";
import { DatePickerField, MoneyInput, NumberField } from "@/shared/components/ui/forms/form";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { i18n, useTranslation } from "@/shared/i18n";
import { getLocaleSeparators } from "@/shared/utils/locale";
import { useToast } from "@/shared/providers";
import { personalFinanceTranslations } from "../../i18n";
import { FundAutocomplete, FundAutocompleteValue } from "./FundAutocomplete";
import { mapCvmFundAssetType } from "@/lib/utils/mapCvmFundAssetType";

interface RecordFundHoldingDrawerProps {
  open: boolean;
  onClose: () => void;
  financialAccountId?: string | null;
  financialAccounts?: FinancialAccount[];
  onRecorded?: () => void;
}

type RecordFundFormData = {
  fund: FundAutocompleteValue | null;
  quantity?: number | string;
  unitPrice?: number;
  tradeDate?: Date;
};

export function RecordFundHoldingDrawer({
  open,
  onClose,
  financialAccountId,
  financialAccounts = [],
  onRecorded,
}: RecordFundHoldingDrawerProps) {
  const theme = useTheme();
  const { t } = useTranslation(personalFinanceTranslations);
  const { success } = useToast();
  const locale = React.useMemo(() => i18n.language || Intl.DateTimeFormat().resolvedOptions().locale || "pt-BR", []);
  const { decimal: localeDecimal, group: localeGroup } = React.useMemo(
    () => getLocaleSeparators(locale),
    [locale],
  );
  const [resolvedFinancialAccountId, setResolvedFinancialAccountId] = React.useState<string | null>(null);
  const needsAccountSelection = !resolvedFinancialAccountId;

  const createSchema = React.useMemo(
    () =>
      z.object({
        fund: z
          .object({
            id: z.string(),
            name: z.string(),
            cnpj: z.string(),
            assetType: z.string(),
            isActive: z.boolean(),
          })
          .nullable()
          .refine((v) => v != null, { message: t("investments.recordFund.validation.fundRequired") }),
        quantity: z.preprocess(
          (value) => {
            if (value === "" || value === null || value === undefined) return undefined;
            const raw = String(value);
            let normalized: string;
            if (localeGroup) {
              const decimalIndex = raw.lastIndexOf(localeDecimal);
              if (decimalIndex >= 0 && raw.indexOf(localeGroup, decimalIndex + 1) !== -1) {
                return undefined;
              }
              normalized = raw.replace(new RegExp(`\\${localeGroup}`, "g"), "").replace(localeDecimal, ".");
            } else {
              normalized = raw.replace(localeDecimal, ".");
            }
            const parsed = Number(normalized);
            return Number.isNaN(parsed) ? undefined : parsed;
          },
          z
            .number({ required_error: t("investments.recordFund.validation.quantityRequired") })
            .positive(t("investments.recordFund.validation.quantityPositive")),
        ),
        unitPrice: z.preprocess(
          (value) => {
            if (value === "" || value === null || value === undefined) return undefined;
            const raw = String(value);
            let normalized: string;
            if (localeGroup) {
              const decimalIndex = raw.lastIndexOf(localeDecimal);
              if (decimalIndex >= 0 && raw.indexOf(localeGroup, decimalIndex + 1) !== -1) {
                return undefined;
              }
              normalized = raw.replace(new RegExp(`\\${localeGroup}`, "g"), "").replace(localeDecimal, ".");
            } else {
              normalized = raw.replace(localeDecimal, ".");
            }
            const parsed = Number(normalized);
            return Number.isNaN(parsed) ? undefined : parsed;
          },
          z
            .number({ required_error: t("investments.recordFund.validation.unitPriceRequired") })
            .positive(t("investments.recordFund.validation.unitPricePositive")),
        ),
        tradeDate: z.preprocess(
          (value) => (value === "" || value === null || value === undefined ? undefined : value),
          z.date({
            required_error: t("investments.recordFund.validation.tradeDateRequired"),
            invalid_type_error: t("investments.recordFund.validation.tradeDateRequired"),
          }),
        ),
      }),
    [t],
  );

  const methods = useForm<RecordFundFormData>({
    resolver: zodResolver(createSchema),
    defaultValues: {
      fund: null,
      quantity: undefined,
      unitPrice: undefined,
      tradeDate: undefined,
    },
  });

  const { reset } = methods;

  useEffect(() => {
    if (!open) {
      reset();
    }
  }, [open, reset]);

  useEffect(() => {
    if (!open) return;
    setResolvedFinancialAccountId(financialAccountId || null);
  }, [financialAccountId, open]);

  const [createAsset, { loading: creatingAsset, error: createAssetError }] = useMutation<
    CreateInvestmentAssetMutation,
    CreateInvestmentAssetMutationVariables
  >(CREATE_INVESTMENT_ASSET);

  const [recordTransaction, { loading: recordingTx, error: recordTxError }] = useMutation<
    RecordInvestmentTransactionMutation,
    RecordInvestmentTransactionMutationVariables
  >(RECORD_INVESTMENT_TRANSACTION, {
    refetchQueries: [
      { query: INVESTMENT_HOLDINGS, variables: { financialAccountId: resolvedFinancialAccountId || "" } },
      { query: ALL_INVESTMENT_HOLDINGS },
    ],
    onCompleted: () => {
      success(t("investments.recordFund.success"));
      onRecorded?.();
      onClose();
    },
  });

  const isSubmitting = creatingAsset || recordingTx;
  const mutationError = createAssetError || recordTxError;

  const onSubmit = async (data: RecordFundFormData) => {
    if (!resolvedFinancialAccountId) return;
    if (!data.fund) return;
    if (!data.tradeDate) return;
    if (!data.quantity || !data.unitPrice) return;

    const tradeDate = dayjs(data.tradeDate).format("YYYY-MM-DD");
    const quantity = Number(data.quantity);
    const unitPrice = data.unitPrice;
    const grossValue = quantity * unitPrice;

    const createdAsset = await createAsset({
      variables: {
        input: {
          name: data.fund.name,
          assetType: mapCvmFundAssetType(data.fund.assetType),
          issuerName: data.fund.cnpj,
          currencyCode: "BRL",
        },
      },
    });

    const assetId = createdAsset.data?.createInvestmentAsset.id;
    if (!assetId) return;

    await recordTransaction({
      variables: {
        input: {
          financialAccountId: resolvedFinancialAccountId,
          assetId,
          direction: InvestmentTransactionDirection.Inflow,
          type: InvestmentTransactionType.Purchase,
          quantity,
          unitPrice,
          grossValue,
          tradeDate,
          settlementDate: tradeDate,
        },
      },
    });
  };

  return (
    <Drawer open={open} onClose={onClose} anchor="right" size="md">
      <Drawer.Header title={t("investments.recordFund.title")} />
      <Drawer.Body>
        {needsAccountSelection ? (
          <Stack spacing={2}>
            <Typography variant="body2" color="text.secondary">
              {t("investments.recordFund.selectAccount.description")}
            </Typography>

            {financialAccounts.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                {t("investments.recordFund.selectAccount.emptyState")}
              </Typography>
            ) : (
              <Paper variant="outlined" sx={{ borderRadius: 2, overflow: "hidden" }}>
                <List disablePadding>
                  {financialAccounts.map((account) => (
                    <ListItemButton
                      key={account.id}
                      onClick={() => setResolvedFinancialAccountId(account.id)}
                      sx={{
                        py: 1.5,
                        "&:not(:last-of-type)": { borderBottom: `1px solid ${alpha(theme.palette.divider, 0.6)}` },
                      }}
                      data-testid={`record-fund-select-account-${account.id}`}
                    >
                      <ListItemIcon sx={{ minWidth: 44 }}>
                        <Avatar
                          src={account.issuer?.logoAsset?.publicUrl || undefined}
                          sx={{
                            bgcolor: account.issuer?.logoAsset?.publicUrl ? "transparent" : (account.themeColor || "primary.main"),
                            width: 32,
                            height: 32,
                            borderRadius: 2,
                          }}
                        >
                          {account.name?.slice(0, 1).toUpperCase()}
                        </Avatar>
                      </ListItemIcon>
                      <ListItemText
                        primary={
                          <Typography variant="body2" fontWeight={700} noWrap>
                            {account.name}
                          </Typography>
                        }
                        secondary={
                          <Typography variant="caption" color="text.secondary" noWrap>
                            {account.issuer?.name || account.issuerName}
                          </Typography>
                        }
                      />
                    </ListItemButton>
                  ))}
                </List>
              </Paper>
            )}
          </Stack>
        ) : (
          <FormProvider {...methods}>
            <form onSubmit={methods.handleSubmit(onSubmit)}>
              <Stack spacing={3}>
                {mutationError && <FormErrorBanner message={mutationError.message} />}

                <Typography variant="overline" color="text.secondary">
                  {t("investments.recordFund.sections.fund")}
                </Typography>

                <FundAutocomplete name="fund" data-testid="record-fund-autocomplete" />

                <Divider />

                <Typography variant="overline" color="text.secondary">
                  {t("investments.recordFund.sections.applicationDetails")}
                </Typography>

                <Controller
                  name="quantity"
                  control={methods.control}
                  render={({ field, fieldState }) => (
                    <TextField
                      {...field}
                      type="text"
                      inputMode="decimal"
                      label={t("investments.recordFund.fields.quantity")}
                      fullWidth
                      error={!!fieldState.error}
                      helperText={fieldState.error?.message}
                      data-testid="record-fund-quantity"
                      inputProps={{
                        style: { textAlign: "right" },
                      }}
                    />
                  )}
                />

                <MoneyInput
                  name="unitPrice"
                  label={t("investments.recordFund.fields.unitPrice")}
                  currency="BRL"
                  data-testid="record-fund-unit-price"
                />

                <DatePickerField
                  name="tradeDate"
                  label={t("investments.recordFund.fields.tradeDate")}
                  data-testid="record-fund-trade-date"
                />

                <Divider />

                <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 1 }}>
                  <Button type="button" variant="outlined" onClick={onClose} disabled={isSubmitting}>
                    {t("actions.cancel")}
                  </Button>
                  <Button
                    type="submit"
                    variant="contained"
                    disabled={isSubmitting || !resolvedFinancialAccountId}
                    startIcon={isSubmitting ? <CircularProgress size={20} color="inherit" /> : null}
                    sx={(theme) => ({
                      boxShadow: theme.custom?.shadow?.(2, theme.palette.primary.main) ?? theme.shadows[2],
                      "&:hover": {
                        boxShadow: theme.custom?.shadow?.(3, theme.palette.primary.main) ?? theme.shadows[4],
                      },
                      "&:active": {
                        boxShadow: theme.custom?.shadow?.(1, theme.palette.primary.main) ?? theme.shadows[1],
                      },
                      "&.Mui-disabled": { boxShadow: "none" },
                    })}
                  >
                    {isSubmitting ? t("investments.recordFund.actions.submitting") : t("investments.recordFund.actions.submit")}
                  </Button>
                </Box>
              </Stack>
            </form>
          </FormProvider>
        )}
      </Drawer.Body>
    </Drawer>
  );
}

