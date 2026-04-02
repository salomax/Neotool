"use client";

import React, { useEffect } from "react";
import { useMutation } from "@apollo/client/react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Avatar, Box, Divider, List, ListItemButton, ListItemIcon, ListItemText, Paper, Typography, alpha, useTheme } from "@mui/material";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import { FormProvider, useForm, useWatch } from "react-hook-form";
import dayjs from "dayjs";
import { z } from "zod";
import { CREATE_INVESTMENT_ASSET, DELETE_INVESTMENT_TRANSACTION, RECORD_INVESTMENT_TRANSACTION } from "@/lib/graphql/operations/banking/mutations";
import { INVESTMENT_HOLDINGS } from "@/lib/graphql/operations/banking/queries";
import {
  CreateInvestmentAssetMutation,
  CreateInvestmentAssetMutationVariables,
  DeleteInvestmentTransactionMutation,
  DeleteInvestmentTransactionMutationVariables,
  RecordInvestmentTransactionMutation,
  RecordInvestmentTransactionMutationVariables,
} from "@/lib/graphql/operations/banking/mutations.generated";
import {
  AssetType,
  FinancialAccount,
  Indexer,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
  InvestmentTransaction,
} from "@/lib/graphql/types/__generated__/graphql";
import { FormErrorBanner } from "@/shared/components/ui/forms/components/FormErrorBanner";
import { FormStack, FormRowStack } from "@/shared/components/ui/forms/components";
import { CheckboxField, DatePickerField, FormTextField as TextField, MoneyInput, NumberField, PercentField, SelectField } from "@/shared/components/ui/forms/form";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { i18n, useTranslation } from "@/shared/i18n";
import { getLocaleSeparators } from "@/shared/utils/locale";
import { useToast } from "@/shared/providers";
import { personalFinanceTranslations } from "../../i18n";
import { InvestmentRecordDrawer } from "./InvestmentRecordDrawer";

interface RecordFixedIncomeDrawerProps {
  open: boolean;
  onClose: () => void;
  financialAccountId?: string | null;
  financialAccounts?: FinancialAccount[];
  onRecorded?: () => void;
  editHolding?: {
    id: string;
    asset: {
      id: string;
      name: string;
      assetType: AssetType;
      issuerName: string | null;
    };
    indexer: Indexer | null;
    indexerPercentage: number | null;
    fixedAnnualRate: number | null;
    maturityDate: unknown | null;
    isTaxExempt: boolean;
  } | null;
  editTransaction?: InvestmentTransaction | null;
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
  fixedAnnualRate?: number;
  quantity?: number | string;
  grossValue: number;
  tradeDate?: Date;
  maturityDate?: Date;
  isTaxExempt: boolean;
};

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

export function RecordFixedIncomeDrawer({
  open,
  onClose,
  financialAccountId,
  financialAccounts = [],
  onRecorded,
  editHolding,
  editTransaction,
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
              // `PercentField` already parses locale-formatted user input into a number.
              // If we normalize numeric inputs as if they were locale strings, we can
              // incorrectly treat the decimal dot as a thousands separator in pt-BR.
              if (typeof value === "number") return value;
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
            z.number().positive(t("investments.recordHolding.validation.indexerPercentageRequired")).optional(),
          ),
          fixedAnnualRate: z.preprocess(
            (value) => {
              if (value === "" || value === null || value === undefined) return undefined;
              if (typeof value === "number") return value;
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
          quantity: z.preprocess(
            (value) => {
              if (value === "" || value === null || value === undefined) return undefined;
              if (typeof value === "number") return value;
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
            z.number().min(0, t("investments.recordHolding.validation.quantityNonNegative")).default(1),
          ),
          grossValue: z.preprocess(
            (value) => {
              if (value === "" || value === null || value === undefined) return undefined;
              if (typeof value === "number") return value;
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

          if (data.indexer === Indexer.PreFixado && !data.fixedAnnualRate) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t("investments.recordHolding.validation.fixedAnnualRateRequired"),
              path: ["fixedAnnualRate"],
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
      fixedAnnualRate: undefined,
      quantity: 1,
      grossValue: undefined,
      tradeDate: undefined,
      maturityDate: undefined,
      isTaxExempt: false,
    },
  });

  const { reset, setValue, getValues } = methods;
  const watchedAssetName = useWatch({ control: methods.control, name: "assetName" });
  const watchedAssetType = useWatch({ control: methods.control, name: "assetType" });
  const watchedIndexer = useWatch({ control: methods.control, name: "indexer" });
  const watchedTradeDate = useWatch({ control: methods.control, name: "tradeDate" });
  const watchedQuantity = useWatch({ control: methods.control, name: "quantity" });
  const watchedGrossValue = useWatch({ control: methods.control, name: "grossValue" });
  const isTreasury = watchedAssetType === AssetType.Treasury;
  const isPreFixed = watchedIndexer === Indexer.PreFixado;
const isEdit = Boolean(editHolding && editTransaction);
  const currencyFormatter = React.useMemo(() => new Intl.NumberFormat(locale, { style: "currency", currency: "BRL" }), [locale]);

  const assetTypeTokens = React.useMemo(
    () => FIXED_INCOME_ASSET_TYPES.map(String).sort((a, b) => b.length - a.length),
    [],
  );

  const computedUnitPrice = React.useMemo(() => {
    if (typeof watchedQuantity !== "number" || !Number.isFinite(watchedQuantity) || watchedQuantity <= 0) return null;
    if (typeof watchedGrossValue !== "number" || !Number.isFinite(watchedGrossValue) || watchedGrossValue <= 0) return null;
    if (watchedQuantity <= 1) return null;
    return watchedGrossValue / watchedQuantity;
  }, [watchedGrossValue, watchedQuantity]);

  const toDateValue = React.useCallback((value: unknown) => {
    if (!value) return undefined;
    if (value instanceof Date) return value;
    const d = new Date(String(value));
    return Number.isNaN(d.getTime()) ? undefined : d;
  }, []);

  useEffect(() => {
    if (!open) {
      reset();
    }
  }, [open, reset]);

  // resolvedFinancialAccountId will be controlled by InvestmentRecordDrawer via onResolvedChange

  useEffect(() => {
    if (!open) return;
    if (!watchedAssetName) return;

    const matchedToken = assetTypeTokens.find((token) => new RegExp(`\\b${escapeRegExp(token)}\\b`).test(watchedAssetName));
    if (!matchedToken) return;

    const nextAssetType = matchedToken as AssetType;
    if (getValues("assetType") === nextAssetType) return;

    setValue("assetType", nextAssetType, { shouldDirty: true, shouldValidate: true });
  }, [assetTypeTokens, getValues, open, setValue, watchedAssetName]);

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

  const [deleteInvestmentTransaction, { loading: deletingTx, error: deleteTxError }] = useMutation<
    DeleteInvestmentTransactionMutation,
    DeleteInvestmentTransactionMutationVariables
  >(DELETE_INVESTMENT_TRANSACTION);

  const [recordTransaction, { loading: recordingTx, error: recordTxError }] = useMutation<
    RecordInvestmentTransactionMutation,
    RecordInvestmentTransactionMutationVariables
  >(RECORD_INVESTMENT_TRANSACTION, {
    refetchQueries: [
      { query: INVESTMENT_HOLDINGS, variables: { financialAccountId: resolvedFinancialAccountId ?? null } },
      { query: INVESTMENT_HOLDINGS },
    ],
    onCompleted: () => {
      success(t("investments.recordHolding.success"));
      onRecorded?.();
      onClose();
    },
  });

  const isSubmitting = creatingAsset || deletingTx || recordingTx;
  const mutationError = createAssetError || deleteTxError || recordTxError;

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

    const quantity = typeof data.quantity === "number" && Number.isFinite(data.quantity) && data.quantity >= 0 ? data.quantity : 1;
    const unitPrice = quantity > 0 ? data.grossValue / quantity : data.grossValue;

    const assetId = isEdit ? editHolding!.asset.id : null;
    const resolvedAssetId =
      assetId ??
      (await (async () => {
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
        return createdAsset.data?.createInvestmentAsset.id ?? null;
      })());
    if (!resolvedAssetId) return;

    try {
      if (isEdit) {
        await deleteInvestmentTransaction({ variables: { id: editTransaction!.id } });
      }

      await recordTransaction({
        variables: {
          input: {
            financialAccountId: resolvedFinancialAccountId,
            assetId: resolvedAssetId,
            direction: isEdit ? editTransaction!.direction : InvestmentTransactionDirection.Inflow,
            type: isEdit ? editTransaction!.type : InvestmentTransactionType.Purchase,
            quantity,
            unitPrice,
            grossValue: data.grossValue,
            tradeDate,
            settlementDate: tradeDate,
            indexer: data.indexer!,
            indexerPercentage: data.indexer !== Indexer.PreFixado ? data.indexerPercentage! / 100 : undefined,
            fixedAnnualRate: data.indexer === Indexer.PreFixado ? data.fixedAnnualRate! / 100 : undefined,
            issueDate: tradeDate,
            maturityDate,
            isTaxExempt: data.isTaxExempt,
          },
        },
      });
    } catch {
      return;
    }
  };

  useEffect(() => {
    if (!open) return;
    if (!isEdit) return;

    reset(
      {
        assetName: editHolding!.asset.name ?? "",
        assetType: editHolding!.asset.assetType,
        issuerName: editHolding!.asset.issuerName ?? "",
        indexer: editHolding!.indexer ?? undefined,
        indexerPercentage:
          editHolding!.indexerPercentage != null && Number.isFinite(editHolding!.indexerPercentage)
            ? editHolding!.indexerPercentage * 100
            : undefined,
        fixedAnnualRate:
          editHolding!.fixedAnnualRate != null && Number.isFinite(editHolding!.fixedAnnualRate) ? editHolding!.fixedAnnualRate * 100 : undefined,
        quantity: editTransaction!.quantity ?? 1,
        grossValue: editTransaction!.grossValue,
        tradeDate: toDateValue(editTransaction!.tradeDate),
        maturityDate: toDateValue(editHolding!.maturityDate),
        isTaxExempt: editHolding!.isTaxExempt ?? false,
      },
      { keepDirty: false },
    );
  }, [editHolding, editTransaction, isEdit, open, reset, toDateValue]);

  return (
    <InvestmentRecordDrawer
      open={open}
      onClose={onClose}
      title={t("investments.recordHolding.title")}
      initialFinancialAccountId={financialAccountId || null}
      financialAccounts={financialAccounts}
      issuerSelection={{
        description: t("investments.recordHolding.selectAccount.description"),
        emptyState: t("investments.recordHolding.selectAccount.emptyState"),
        dataTestIdPrefix: "record-select-account-",
      }}
      onResolvedChange={setResolvedFinancialAccountId}
    >
      {({ resolvedFinancialAccountId }) => (
        <FormProvider {...methods}>
          <form id="record-fixed-income-form" onSubmit={methods.handleSubmit(onSubmit)}>
            <FormStack>
              {mutationError && <FormErrorBanner message={mutationError.message} />}

              <FormStack spacing={1}>
                <Typography variant="overline" color="text.secondary" sx={{ mb: 0 }}>
                  {t("investments.recordHolding.sections.assetInfo")}
                </Typography>

                <TextField
                  name="assetName"
                  label={t("investments.recordHolding.fields.assetName")}
                  data-testid="record-asset-name"
                />
              </FormStack>

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

              <Divider sx={{ pt: 2 }} />

              <Typography variant="overline" color="text.secondary" sx={{ mb: 0 }}>
                {t("investments.recordHolding.sections.applicationDetails")}
              </Typography>

              <FormRowStack alignItems="flex-start">
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <NumberField
                    name="quantity"
                    label={t("investments.recordHolding.fields.quantity")}
                    fullWidth
                    locale={locale}
                    min={0}
                    allowNegative={false}
                    data-testid="record-quantity"
                  />
                </Box>

                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <MoneyInput
                    name="grossValue"
                    label={t("investments.recordHolding.fields.grossValue")}
                    currency="BRL"
                    data-testid="record-gross-value"
                  />
                </Box>
              </FormRowStack>

              {computedUnitPrice != null && (
                <Paper
                  variant="outlined"
                  sx={{
                    px: 1.5,
                    py: 1,
                    borderRadius: 1,
                    display: "flex",
                    alignItems: "center",
                    gap: 1,
                    borderColor: alpha(theme.palette.info.main, 0.35),
                    bgcolor: alpha(theme.palette.info.main, 0.06),
                  }}
                  data-testid="record-unit-price-info"
                >
                  <InfoOutlinedIcon fontSize="small" sx={{ color: "info.main" }} />
                  <Typography variant="body2" color="text.secondary">
                    {t("investments.recordHolding.values.unitPriceInfo", { unitPrice: currencyFormatter.format(computedUnitPrice) })}
                  </Typography>
                </Paper>
              )}

              <FormRowStack alignItems="flex-start">
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
                      name="fixedAnnualRate"
                      label={t("investments.recordHolding.fields.fixedAnnualRate")}
                      min={0}
                      max={1000}
                      step={0.01}
                      fractionDigits={2}
                      locale={locale}
                      data-testid="record-pre-fixed-rate"
                    />
                  )}
                </Box>
              </FormRowStack>

              <FormRowStack alignItems="flex-start">
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
              </FormRowStack>

              <CheckboxField
                name="isTaxExempt"
                label={t("investments.recordHolding.fields.isTaxExempt")}
                data-testid="record-tax-exempt-checkbox"
              />

              <Divider />

              <Drawer.ActionButtons
                cancelText={t("actions.cancel")}
                submitText={t("investments.recordHolding.actions.submit")}
                submittingText={t("investments.recordHolding.actions.submitting")}
                onCancel={onClose}
                submitForm="record-fixed-income-form"
                cancelDisabled={isSubmitting}
                submitDisabled={isSubmitting || !resolvedFinancialAccountId}
                submitting={isSubmitting}
                cancelTestId="record-fixed-income-cancel-btn"
                submitTestId="record-fixed-income-submit-btn"
              />
            </FormStack>
          </form>
        </FormProvider>
      )}
    </InvestmentRecordDrawer>
  );
}
