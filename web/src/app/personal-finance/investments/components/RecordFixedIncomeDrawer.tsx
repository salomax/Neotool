"use client";

import React, { useEffect } from "react";
import { useMutation } from "@apollo/client/react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Avatar, Box, Divider, List, ListItemButton, ListItemIcon, ListItemText, Paper, Stack, Typography, alpha, useTheme } from "@mui/material";
import { FormProvider, useForm, useWatch } from "react-hook-form";
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
import {
  AssetType,
  FinancialAccount,
  Indexer,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
} from "@/lib/graphql/types/__generated__/graphql";
import { FormErrorBanner } from "@/shared/components/ui/forms/components/FormErrorBanner";
import { CheckboxField, DatePickerField, FormTextField as TextField, MoneyInput, PercentField, SelectField } from "@/shared/components/ui/forms/form";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { i18n, useTranslation } from "@/shared/i18n";
import { getLocaleSeparators } from "@/shared/utils/locale";
import { useToast } from "@/shared/providers";
import { personalFinanceTranslations } from "../../i18n";

interface RecordFixedIncomeDrawerProps {
  open: boolean;
  onClose: () => void;
  financialAccountId?: string | null;
  financialAccounts?: FinancialAccount[];
  onRecorded?: () => void;
}

const FIXED_INCOME_ASSET_TYPES: readonly AssetType[] = [
  AssetType.Cdb,
  AssetType.Rdb,
  AssetType.Lci,
  AssetType.Lca,
  AssetType.Lc,
  AssetType.Lf,
  AssetType.Lig,
  AssetType.Debentures,
  AssetType.Cri,
  AssetType.Cra,
  AssetType.Treasury,
] as const;

const FIXED_INCOME_INDEXERS: readonly Indexer[] = [
  Indexer.Cdi,
  Indexer.Di,
  Indexer.Ipca,
  Indexer.IgpM,
  Indexer.Selic,
  Indexer.PreFixado,
  Indexer.Outros,
] as const;

const TAX_EXEMPT_ASSET_TYPES: readonly AssetType[] = [AssetType.Lci, AssetType.Lca, AssetType.Cri, AssetType.Cra] as const;

type RecordFixedIncomeFormData = {
  assetName: string;
  assetType?: AssetType;
  issuerName: string;
  indexer?: Indexer;
  indexerPercentage?: number;
  preFixedRate?: number;
  grossValue: number;
  tradeDate?: Date;
  maturityDate?: Date;
  isTaxExempt: boolean;
};

export function RecordFixedIncomeDrawer({
  open,
  onClose,
  financialAccountId,
  financialAccounts = [],
  onRecorded,
}: RecordFixedIncomeDrawerProps) {
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
      z
        .object({
          assetName: z.string().trim().min(1, t("investments.recordHolding.validation.assetNameRequired")),
          assetType: z.nativeEnum(AssetType, {
            required_error: t("investments.recordHolding.validation.assetTypeRequired"),
          }),
          issuerName: z.string().trim().min(1, t("investments.recordHolding.validation.issuerRequired")),
          indexer: z.nativeEnum(Indexer, {
            required_error: t("investments.recordHolding.validation.indexerRequired"),
          }),
          indexerPercentage: z.preprocess(
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
            z.number().positive().optional(),
          ),
          preFixedRate: z.preprocess(
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
            z.number().positive().optional(),
          ),
          grossValue: z.preprocess(
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
              .number({ required_error: t("investments.recordHolding.validation.grossValueRequired") })
              .positive(t("investments.recordHolding.validation.grossValuePositive")),
          ),
          tradeDate: z.preprocess(
            (value) => (value === "" || value === null || value === undefined ? undefined : value),
            z.date({
              required_error: t("investments.recordHolding.validation.tradeDateRequired"),
              invalid_type_error: t("investments.recordHolding.validation.tradeDateRequired"),
            }),
          ),
          maturityDate: z.preprocess(
            (value) => (value === "" || value === null || value === undefined ? undefined : value),
            z.date({
              required_error: t("investments.recordHolding.validation.maturityDateRequired"),
              invalid_type_error: t("investments.recordHolding.validation.maturityDateRequired"),
            }),
          ),
          isTaxExempt: z.boolean().default(false),
        })
        .superRefine((data, ctx) => {
          if (!FIXED_INCOME_ASSET_TYPES.includes(data.assetType)) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t("investments.recordHolding.validation.assetTypeRequired"),
              path: ["assetType"],
            });
          }

          if (!FIXED_INCOME_INDEXERS.includes(data.indexer)) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t("investments.recordHolding.validation.indexerRequired"),
              path: ["indexer"],
            });
          }

          if (data.indexer !== Indexer.PreFixado && !data.indexerPercentage) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t("investments.recordHolding.validation.indexerPercentageRequired"),
              path: ["indexerPercentage"],
            });
          }

          if (data.indexer === Indexer.PreFixado && !data.preFixedRate) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t("investments.recordHolding.validation.preFixedRateRequired"),
              path: ["preFixedRate"],
            });
          }

          if (data.maturityDate.getTime() <= data.tradeDate.getTime()) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t("investments.recordHolding.validation.maturityAfterTrade"),
              path: ["maturityDate"],
            });
          }
        }),
    [localeDecimal, localeGroup, t],
  );

  const methods = useForm<RecordFixedIncomeFormData>({
    resolver: zodResolver(createSchema),
    defaultValues: {
      assetName: "",
      assetType: undefined,
      issuerName: "",
      indexer: undefined,
      indexerPercentage: undefined,
      preFixedRate: undefined,
      grossValue: undefined,
      tradeDate: undefined,
      maturityDate: undefined,
      isTaxExempt: false,
    },
  });

  const { reset, setValue } = methods;
  const watchedAssetType = useWatch({ control: methods.control, name: "assetType" });
  const watchedIndexer = useWatch({ control: methods.control, name: "indexer" });
  const watchedTradeDate = useWatch({ control: methods.control, name: "tradeDate" });
  const isTreasury = watchedAssetType === AssetType.Treasury;
  const isPreFixed = watchedIndexer === Indexer.PreFixado;

  useEffect(() => {
    if (!open) {
      reset();
    }
  }, [open, reset]);

  useEffect(() => {
    if (!open) return;
    setResolvedFinancialAccountId(financialAccountId || null);
  }, [financialAccountId, open]);

  useEffect(() => {
    if (watchedAssetType === AssetType.Treasury) {
      setValue("issuerName", "Tesouro Nacional", { shouldValidate: true });
    }

    setValue("isTaxExempt", TAX_EXEMPT_ASSET_TYPES.includes(watchedAssetType as (typeof TAX_EXEMPT_ASSET_TYPES)[number]));
  }, [watchedAssetType, setValue]);

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
      success(t("investments.recordHolding.success"));
      onRecorded?.();
      onClose();
    },
  });

  const isSubmitting = creatingAsset || recordingTx;
  const mutationError = createAssetError || recordTxError;

  const assetTypeOptions = React.useMemo(
    () =>
      FIXED_INCOME_ASSET_TYPES.map((value) => ({
        value,
        label: t(`investments.recordHolding.assetTypes.${value}`),
      })),
    [t],
  );

  const indexerOptions = React.useMemo(
    () =>
      FIXED_INCOME_INDEXERS.map((value) => ({
        value,
        label: t(`investments.recordHolding.indexers.${value}`),
      })),
    [t],
  );

  const onSubmit = async (data: RecordFixedIncomeFormData) => {
    if (!resolvedFinancialAccountId) return;
    if (!data.tradeDate || !data.maturityDate) return;

    const tradeDate = dayjs(data.tradeDate).format("YYYY-MM-DD");
    const maturityDate = dayjs(data.maturityDate).format("YYYY-MM-DD");

    const createdAsset = await createAsset({
      variables: {
        input: {
          name: data.assetName,
          assetType: data.assetType!,
          issuerName: data.issuerName,
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
          quantity: 1,
          unitPrice: data.grossValue,
          grossValue: data.grossValue,
          tradeDate,
          settlementDate: tradeDate,
          indexer: data.indexer!,
          indexerPercentage: data.indexer !== Indexer.PreFixado ? data.indexerPercentage! / 100 : undefined,
          preFixedRate: data.indexer === Indexer.PreFixado ? data.preFixedRate! / 100 : undefined,
          issueDate: tradeDate,
          maturityDate,
          isTaxExempt: data.isTaxExempt,
        },
      },
    });
  };

  return (
    <Drawer open={open} onClose={onClose} anchor="right" size="md">
      <Drawer.Header title={t("investments.recordHolding.title")} />
      <Drawer.Body>
        {needsAccountSelection ? (
          <Stack spacing={2}>
            <Typography variant="body2" color="text.secondary">
              {t("investments.recordHolding.selectAccount.description")}
            </Typography>

            {financialAccounts.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                {t("investments.recordHolding.selectAccount.emptyState")}
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
                      data-testid={`record-select-account-${account.id}`}
                    >
                      <ListItemIcon sx={{ minWidth: 44 }}>
                        <Avatar
                          variant="rounded"
                          src={account.issuer?.logoAsset?.publicUrl || undefined}
                          slotProps={{ img: { style: { objectFit: "contain" } } }}
                          sx={{
                            bgcolor: account.issuer?.logoAsset?.publicUrl ? "transparent" : (account.themeColor || "primary.main"),
                            width: 32,
                            height: 32,
                            borderRadius: 1.5,
                          }}
                        >
                          {account.name?.slice(0, 1).toUpperCase()}
                        </Avatar>
                      </ListItemIcon>
                      <ListItemText
                        primary={
                          <Typography variant="body2" fontWeight={600} noWrap>
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
            <form id="record-fixed-income-form" onSubmit={methods.handleSubmit(onSubmit)}>
              <Stack spacing={2}>
                {mutationError && <FormErrorBanner message={mutationError.message} />}

                <Stack spacing={1}>
                  <Typography variant="overline" color="text.secondary" sx={{ mb: 0 }}>
                    {t("investments.recordHolding.sections.assetInfo")}
                  </Typography>

                  <TextField
                    name="assetName"
                    label={t("investments.recordHolding.fields.assetName")}
                    data-testid="record-asset-name"
                  />
                </Stack>

                <SelectField
                  name="assetType"
                  label={t("investments.recordHolding.fields.assetType")}
                  options={assetTypeOptions}
                  data-testid="record-asset-type"
                />

                <TextField
                  name="issuerName"
                  label={t("investments.recordHolding.fields.issuerName")}
                  InputProps={isTreasury ? { readOnly: true } : undefined}
                  data-testid="record-issuer-name"
                />

                <Divider />

                <Typography variant="overline" color="text.secondary" sx={{ mb: 0 }}>
                  {t("investments.recordHolding.sections.applicationDetails")}
                </Typography>

                <MoneyInput
                  name="grossValue"
                  label={t("investments.recordHolding.fields.grossValue")}
                  currency="BRL"
                  data-testid="record-gross-value"
                />

                <Stack direction={{ xs: "column", sm: "row" }} spacing={2} alignItems="flex-start">
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <SelectField
                      name="indexer"
                      label={t("investments.recordHolding.fields.indexer")}
                      options={indexerOptions}
                      data-testid="record-indexer"
                    />
                  </Box>

                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    {!isPreFixed ? (
                      <PercentField
                        name="indexerPercentage"
                        label={t("investments.recordHolding.fields.indexerPercentage")}
                        min={0}
                        max={1000}
                        step={0.01}
                        fractionDigits={2}
                        locale={locale}
                        data-testid="record-indexer-percentage"
                      />
                    ) : (
                      <PercentField
                        name="preFixedRate"
                        label={t("investments.recordHolding.fields.preFixedRate")}
                        min={0}
                        max={1000}
                        step={0.01}
                        fractionDigits={2}
                        locale={locale}
                        data-testid="record-pre-fixed-rate"
                      />
                    )}
                  </Box>
                </Stack>

                <Stack direction={{ xs: "column", sm: "row" }} spacing={2} alignItems="flex-start">
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <DatePickerField
                      name="tradeDate"
                      label={t("investments.recordHolding.fields.tradeDate")}
                      data-testid="record-trade-date"
                    />
                  </Box>

                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <DatePickerField
                      name="maturityDate"
                      label={t("investments.recordHolding.fields.maturityDate")}
                      minDate={watchedTradeDate}
                      data-testid="record-maturity-date"
                    />
                  </Box>
                </Stack>

                <CheckboxField
                  name="isTaxExempt"
                  label={t("investments.recordHolding.fields.isTaxExempt")}
                  data-testid="record-tax-exempt-checkbox"
                />

                <Divider />

                {!needsAccountSelection ? (
                  <Drawer.ActionButtons
                    cancelText={t("actions.cancel")}
                    submitText={t("investments.recordHolding.actions.submit")}
                    submittingText={t("investments.recordHolding.actions.submitting")}
                    onCancel={onClose}
                    submitForm="record-fixed-income-form"
                    cancelDisabled={isSubmitting}
                    submitDisabled={isSubmitting || !resolvedFinancialAccountId}
                    submitting={isSubmitting}
                  />
                ) : null}

              </Stack>
            </form>
          </FormProvider>
        )}
      </Drawer.Body>
    </Drawer>
  );
}
