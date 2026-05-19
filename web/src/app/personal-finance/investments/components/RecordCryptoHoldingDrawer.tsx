"use client";

import * as React from "react";
import { useApolloClient, useMutation } from "@apollo/client/react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Box, Divider, Typography } from "@mui/material";
import { FormProvider, useForm } from "react-hook-form";
import dayjs from "dayjs";
import { z } from "zod";
import { CREATE_INVESTMENT_ASSET, RECORD_INVESTMENT_TRANSACTION } from "@/lib/graphql/operations/banking/mutations";
import {
  INVESTMENT_CASH_BALANCE,
  INVESTMENT_CASH_TRANSACTIONS,
  INVESTMENT_HOLDINGS,
  INVESTMENT_PORTFOLIO_SUMMARY,
} from "@/lib/graphql/operations/banking/queries";
import {
  CreateInvestmentAssetMutation,
  CreateInvestmentAssetMutationVariables,
  RecordInvestmentTransactionMutation,
  RecordInvestmentTransactionMutationVariables,
} from "@/lib/graphql/operations/banking/mutations.generated";
import {
  AssetType,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
} from "@/lib/graphql/types/__generated__/graphql";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import { FormRowStack, FormStack } from "@/shared/components/ui/forms/components";
import { FormTextField, MoneyInput, NumberField } from "@/shared/components/ui/forms/form";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { i18n, useTranslation } from "@/shared/i18n";
import { getLocaleSeparators } from "@/shared/utils/locale";
import { useToast } from "@/shared/providers";
import { formatDateOnly } from "@/shared/utils/date/dateOnly";
import { coerceInvestmentSettlementDate, validateInvestmentSettlementDates } from "@/shared/utils/investment/settlementDatePolicy";
import { FINANCE_MOVING_WINDOW_YEARS, getFinanceMinAllowedDate } from "../../finance-transactions/constants/dateWindow";
import { personalFinanceTranslations } from "../../i18n";
import type { IssuerSelectionAccount } from "./IssuerSelection";
import { InvestmentRecordDrawer } from "./InvestmentRecordDrawer";
import { InvestmentTradeAndSettlementDateRow } from "./InvestmentTradeAndSettlementDateRow";
import { useSyncDefaultSettlementDate } from "../hooks/useSyncDefaultSettlementDate";
import type { CryptoGateResult } from "./transaction-editor/new-holding/newHoldingFlow.types";

export interface RecordCryptoHoldingDrawerProps {
  open: boolean;
  onClose: () => void;
  financialAccountId?: string | null;
  financialAccounts?: IssuerSelectionAccount[];
  onRecorded?: () => void;
  initialDirection?: InvestmentTransactionDirection;
  /** When true, renders only the form body (parent supplies [`InvestmentRecordDrawer`]). */
  embedded?: boolean;
  /** Required when `embedded` — custodian account id from parent drawer. */
  resolvedAccountId?: string | null;
  /** Two-step flow: step 2 hides identity fields and seeds them from step 1. */
  assetGate?: { mode: "full" } | { mode: "locked"; result: CryptoGateResult };
}

type RecordCryptoFormData = {
  name: string;
  ticker: string;
  network: string;
  tradeDate?: Date;
  settlementDate?: Date;
  quantity?: number;
  unitPrice?: number;
};

function buildDefaultValues(): RecordCryptoFormData {
  return {
    name: "",
    ticker: "",
    network: "",
    tradeDate: dayjs().startOf("day").toDate(),
    settlementDate: coerceInvestmentSettlementDate("crypto", dayjs().startOf("day").toDate(), undefined),
    quantity: undefined,
    unitPrice: undefined,
  };
}

export function RecordCryptoHoldingDrawer({
  open,
  onClose,
  financialAccountId,
  financialAccounts = [],
  onRecorded,
  initialDirection = InvestmentTransactionDirection.Inflow,
  embedded = false,
  resolvedAccountId = null,
  assetGate,
}: RecordCryptoHoldingDrawerProps) {
  const apolloClient = useApolloClient();
  const { t } = useTranslation(personalFinanceTranslations);
  const { success } = useToast();
  const locale = React.useMemo(() => i18n.language || Intl.DateTimeFormat().resolvedOptions().locale || "pt-BR", []);
  const { decimal: localeDecimal, group: localeGroup } = React.useMemo(() => getLocaleSeparators(locale), [locale]);
  const minAllowedDate = React.useMemo(() => getFinanceMinAllowedDate().toDate(), []);
  const [resolvedFinancialAccountId, setResolvedFinancialAccountId] = React.useState<string | null>(null);
  const isLockedGate = assetGate?.mode === "locked";

  const parseLocalizedNumber = React.useCallback(
    (value: unknown) => {
      if (value === "" || value === null || value === undefined) return undefined;
      if (typeof value === "number") return Number.isNaN(value) ? undefined : value;
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
    [localeDecimal, localeGroup],
  );

  const schema = React.useMemo(() => {
    const tradeDateField = z.preprocess(
      (value) => (value === "" || value === null || value === undefined ? undefined : value),
      z.date({ required_error: t("investments.updateHolding.validation.tradeDateRequired") }),
    );
    const settlementDateField = z.preprocess(
      (value) => (value === "" || value === null || value === undefined ? undefined : value),
      z.date({ required_error: t("investments.updateHolding.validation.settlementDateRequired") }),
    );
    const quantityField = z.preprocess(parseLocalizedNumber, z.number().positive());
    const unitPriceField = z.preprocess(parseLocalizedNumber, z.number().positive());
    const dateWindowRefine = <S extends z.ZodTypeAny>(base: S) =>
      base.superRefine((data: z.infer<S>, ctx) => {
        const td = "tradeDate" in data ? (data as { tradeDate?: Date }).tradeDate : undefined;
        if (td && dayjs(td).isBefore(dayjs(minAllowedDate), "day")) {
          ctx.addIssue({
            code: z.ZodIssueCode.custom,
            message: t("financeTransactions.validation.dateTooOld", { years: FINANCE_MOVING_WINDOW_YEARS }),
            path: ["tradeDate"],
          });
        }
        validateInvestmentSettlementDates(
          {
            tradeDate: td,
            settlementDate: "settlementDate" in data ? (data as { settlementDate?: Date }).settlementDate : undefined,
          },
          ctx,
          t,
        );
      });

    if (isLockedGate) {
      return dateWindowRefine(
        z.object({
          tradeDate: tradeDateField,
          settlementDate: settlementDateField,
          quantity: quantityField,
          unitPrice: unitPriceField,
        }),
      );
    }

    return dateWindowRefine(
      z.object({
        name: z.string().min(1, { message: t("investments.recordCrypto.validation.nameRequired") }),
        ticker: z.string().min(1, { message: t("investments.recordCrypto.validation.tickerRequired") }),
        network: z.string().optional(),
        tradeDate: tradeDateField,
        settlementDate: settlementDateField,
        quantity: quantityField,
        unitPrice: unitPriceField,
      }),
    );
  }, [isLockedGate, minAllowedDate, parseLocalizedNumber, t]);

  const methods = useForm<RecordCryptoFormData>({
    resolver: zodResolver(schema),
    defaultValues: buildDefaultValues(),
  });

  const { reset, handleSubmit, watch, setValue } = methods;
  const watchedTradeDate = watch("tradeDate");

  useSyncDefaultSettlementDate<RecordCryptoFormData>({
    enabled: open,
    assetKind: "crypto",
    tradeDate: watchedTradeDate,
    setValue,
  });

  React.useEffect(() => {
    if (!open) {
      reset(buildDefaultValues());
    }
  }, [open, reset]);

  React.useEffect(() => {
    if (!embedded) return;
    setResolvedFinancialAccountId(resolvedAccountId ?? null);
  }, [embedded, resolvedAccountId]);

  React.useEffect(() => {
    if (!open || !isLockedGate || !assetGate || assetGate.mode !== "locked") return;
    const r = assetGate.result;
    reset({
      name: r.name,
      ticker: r.ticker,
      network: r.network ?? "",
      tradeDate: dayjs().startOf("day").toDate(),
      settlementDate: coerceInvestmentSettlementDate("crypto", dayjs().startOf("day").toDate(), undefined),
      quantity: undefined,
      unitPrice: undefined,
    });
  }, [assetGate, isLockedGate, open, reset]);

  const [createAsset, { loading: creatingAsset, error: createAssetError }] = useMutation<
    CreateInvestmentAssetMutation,
    CreateInvestmentAssetMutationVariables
  >(CREATE_INVESTMENT_ASSET);

  const [recordTransaction, { loading: recordingTx, error: recordTxError }] = useMutation<
    RecordInvestmentTransactionMutation,
    RecordInvestmentTransactionMutationVariables
  >(RECORD_INVESTMENT_TRANSACTION);

  const completeRecording = React.useCallback(() => {
    success(t("investments.updateHolding.success"));
    onRecorded?.();
    onClose();
    void apolloClient
      .refetchQueries({
        include: [
          INVESTMENT_HOLDINGS,
          INVESTMENT_PORTFOLIO_SUMMARY,
          INVESTMENT_CASH_BALANCE,
          INVESTMENT_CASH_TRANSACTIONS,
        ],
      })
      .catch(() => undefined);
  }, [apolloClient, onClose, onRecorded, success, t]);

  const isSubmitting = creatingAsset || recordingTx;
  const mutationError = createAssetError || recordTxError;

  const onSubmit = async (data: RecordCryptoFormData) => {
    if (!resolvedFinancialAccountId) return;
    if (!data.tradeDate) return;
    if (!data.settlementDate) return;

    const identity =
      isLockedGate && assetGate?.mode === "locked"
        ? {
            name: assetGate.result.name.trim(),
            ticker: assetGate.result.ticker.trim(),
            network: assetGate.result.network?.trim() ?? "",
          }
        : {
            name: data.name.trim(),
            ticker: data.ticker.trim(),
            network: data.network?.trim() ?? "",
          };

    const quantity = data.quantity;
    const unitPrice = data.unitPrice;
    if (quantity == null || unitPrice == null) return;
    const grossValue = quantity * unitPrice;
    if (!Number.isFinite(grossValue) || grossValue <= 0) return;

    const tradeDate = formatDateOnly(data.tradeDate);
    const direction = initialDirection;

    try {
      const created = await createAsset({
        variables: {
          input: {
            name: identity.name,
            assetType: AssetType.Crypto,
            ticker: identity.ticker.toUpperCase(),
            currencyCode: "BRL",
            issuerName: identity.network || undefined,
          },
        },
      });
      const assetId = created.data?.createInvestmentAsset.id;
      if (!assetId) return;

      await recordTransaction({
        variables: {
          input: {
            financialAccountId: resolvedFinancialAccountId,
            assetId,
            direction,
            type:
              direction === InvestmentTransactionDirection.Outflow
                ? InvestmentTransactionType.Sale
                : InvestmentTransactionType.Purchase,
            tradeDate,
            settlementDate: formatDateOnly(coerceInvestmentSettlementDate("crypto", data.tradeDate, data.settlementDate)),
            quantity,
            unitPrice,
            grossValue,
          },
        },
      });

      completeRecording();
    } catch {
      return;
    }
  };

  const formId = "record-crypto-form";

  const cryptoFooterContent = (activeAccountId: string | null) => {
    const qty = methods.watch("quantity");
    const price = methods.watch("unitPrice");
    const gross =
      typeof qty === "number" && typeof price === "number" && Number.isFinite(qty * price) ? qty * price : null;
    const currencyFormatter = new Intl.NumberFormat(locale, { style: "currency", currency: "BRL" });

    return (
      <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
        <Box sx={{ display: "flex", justifyContent: "space-between", gap: 2 }}>
          <Typography variant="body2" color="text.secondary">
            {t("investments.updateHolding.summary.value")}
          </Typography>
          <Typography variant="body2" fontWeight={700}>
            {gross != null ? currencyFormatter.format(gross) : "—"}
          </Typography>
        </Box>
        <Divider />
        <Drawer.ActionButtons
          cancelText={t("actions.cancel")}
          submitText={t("investments.recordEquity.actions.submit")}
          submittingText={t("investments.recordEquity.actions.submitting")}
          onCancel={onClose}
          submitForm={formId}
          cancelDisabled={isSubmitting}
          submitDisabled={isSubmitting || !activeAccountId}
          submitting={isSubmitting}
        />
      </Box>
    );
  };

  const cryptoForm = (activeAccountId: string | null) => (
    <FormProvider {...methods}>
      <form id={formId} onSubmit={handleSubmit(onSubmit)}>
        <FormStack spacing={2}>
          <ErrorAlert error={mutationError} />

          {!isLockedGate ? (
            <>
              <Typography variant="overline" color="text.secondary">
                {t("investments.updateHolding.contextSection")}
              </Typography>
              <FormTextField name="name" label={t("investments.recordCrypto.fields.name")} size="medium" required />
              <FormTextField name="ticker" label={t("investments.recordCrypto.fields.ticker")} size="medium" required />
              <FormTextField name="network" label={t("investments.recordCrypto.fields.network")} size="medium" />

              <Divider />
            </>
          ) : null}

          <Typography variant="overline" color="text.secondary">
            {t("investments.updateHolding.sections.dates")}
          </Typography>
          <FormRowStack>
            <InvestmentTradeAndSettlementDateRow<RecordCryptoFormData>
              tradeDateLabel={t("investments.updateHolding.fields.tradeDate")}
              settlementDateLabel={t("investments.updateHolding.fields.settlementDate")}
              minDate={minAllowedDate}
              tradeMaxDate={dayjs().startOf("day").toDate()}
              size="medium"
            />
          </FormRowStack>

          <Typography variant="overline" color="text.secondary" sx={{ pt: 0.5 }}>
            {t("investments.updateHolding.sections.trade")}
          </Typography>
          <FormRowStack>
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <NumberField
                name="quantity"
                label={t("investments.updateHolding.fields.quantity")}
                locale={locale}
                size="medium"
                allowNegative={false}
                fractionDigits={8}
                fixedDecimalScale={false}
              />
            </Box>
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <MoneyInput
                name="unitPrice"
                label={t("investments.updateHolding.fields.unitPrice")}
                currency="BRL"
                size="medium"
              />
            </Box>
          </FormRowStack>

          {embedded ? cryptoFooterContent(activeAccountId) : null}
        </FormStack>
      </form>
    </FormProvider>
  );

  if (embedded) {
    return cryptoForm(resolvedAccountId ?? null);
  }

  return (
    <InvestmentRecordDrawer
      open={open}
      onClose={onClose}
      title={t("investments.grid.classes.crypto")}
      initialFinancialAccountId={financialAccountId || null}
      financialAccounts={financialAccounts}
      issuerSelection={{
        description: t("investments.recordEquity.selectAccount.description"),
        emptyState: t("investments.recordEquity.selectAccount.emptyState"),
        dataTestIdPrefix: "record-crypto-select-account-",
      }}
      onResolvedChange={setResolvedFinancialAccountId}
      footer={({ resolvedFinancialAccountId: accountId }) => cryptoFooterContent(accountId)}
    >
      {({ resolvedFinancialAccountId }) => cryptoForm(resolvedFinancialAccountId)}
    </InvestmentRecordDrawer>
  );
}
