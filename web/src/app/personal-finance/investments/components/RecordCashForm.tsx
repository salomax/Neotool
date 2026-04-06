"use client";

import React from "react";
import { useApolloClient, useMutation } from "@apollo/client/react";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  Autocomplete,
  Avatar,
  Box,
  Checkbox,
  Divider,
  FormControlLabel,
  TextField,
  Typography,
} from "@mui/material";
import { useTheme } from "@mui/material/styles";
import dayjs from "dayjs";
import { Controller, FormProvider, useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { CREATE_INVESTMENT_CASH_TRANSACTION } from "@/lib/graphql/operations/banking/mutations";
import {
  INVESTMENT_CASH_BALANCE,
  INVESTMENT_CASH_TRANSACTIONS,
  INVESTMENT_PORTFOLIO_SUMMARY,
} from "@/lib/graphql/operations/banking/queries";
import type {
  CreateInvestmentCashTransactionMutation,
  CreateInvestmentCashTransactionMutationVariables,
} from "@/lib/graphql/operations/banking/mutations.generated";
import {
  FinancialAccountSubtype,
  FinancialAccountType,
  InvestmentCashTransactionType,
} from "@/lib/graphql/types/__generated__/graphql";
import type { IssuerSelectionAccount } from "./IssuerSelection";
import { FormErrorBanner } from "@/shared/components/ui/forms/components/FormErrorBanner";
import { FormRowStack, FormStack } from "@/shared/components/ui/forms/components";
import { DatePickerField, FormTextField, MoneyInput } from "@/shared/components/ui/forms/form";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { SegmentedControl } from "@/shared/components/ui/inputs/SegmentedControl";
import { i18n, useTranslation } from "@/shared/i18n";
import { useToast } from "@/shared/providers";
import { getLocaleSeparators } from "@/shared/utils/locale";
import { personalFinanceTranslations } from "../../i18n";

const PEER_BANK_SUBTYPES = new Set<FinancialAccountSubtype>([
  FinancialAccountSubtype.ContaCorrente,
  FinancialAccountSubtype.ContaPoupanca,
  FinancialAccountSubtype.ContaSalario,
  FinancialAccountSubtype.ContaDigital,
  FinancialAccountSubtype.ContaConjunta,
]);

function PeerAccountAvatar({
  account,
  size = 32,
}: {
  account: IssuerSelectionAccount;
  size?: number;
}) {
  const theme = useTheme();
  return (
    <Avatar
      variant="rounded"
      src={account.issuer?.logoAsset?.publicUrl || undefined}
      slotProps={{ img: { style: { objectFit: "contain" } } }}
      sx={{
        bgcolor: account.issuer?.logoAsset?.publicUrl ? "transparent" : (account.themeColor || theme.palette.primary.main),
        width: size,
        height: size,
        borderRadius: 1.5,
        flexShrink: 0,
      }}
    >
      {account.name?.slice(0, 1).toUpperCase()}
    </Avatar>
  );
}

export type RecordCashFormValues = {
  movementType: InvestmentCashTransactionType.Deposit | InvestmentCashTransactionType.Withdrawal;
  amount: number | undefined;
  date: Date;
  description: string;
  peerFinancialAccountId: string;
  registerAsBalanceAdjustment: boolean;
};

export interface RecordCashFormProps {
  financialAccountId: string;
  bankAccounts: IssuerSelectionAccount[];
  /** Investment/benefit custodian accounts; brokerage (corretora) peers are offered for transfers. */
  investmentAccounts?: IssuerSelectionAccount[];
  /** Matches portfolio / table filter; drives cache refetch after save. */
  listContextAccountId?: string | null;
  /** Increment when the parent drawer opens to clear the form. */
  resetSignal?: number;
  formId: string;
  onSuccess: () => void;
  onCancel?: () => void;
  cancelText: string;
  submitText: string;
  submittingText: string;
  cancelTestId?: string;
  submitTestId?: string;
}

export function RecordCashForm({
  financialAccountId,
  bankAccounts,
  investmentAccounts = [],
  listContextAccountId,
  resetSignal = 0,
  formId,
  onSuccess,
  onCancel,
  cancelText,
  submitText,
  submittingText,
  cancelTestId,
  submitTestId,
}: RecordCashFormProps) {
  const apolloClient = useApolloClient();
  const { t } = useTranslation(personalFinanceTranslations);
  const { success } = useToast();
  const locale = React.useMemo(() => i18n.language || Intl.DateTimeFormat().resolvedOptions().locale || "pt-BR", []);
  const { decimal: localeDecimal, group: localeGroup } = React.useMemo(() => getLocaleSeparators(locale), [locale]);

  const peerOptions = React.useMemo(() => {
    const bankPeers = bankAccounts.filter(
      (a) =>
        a.id !== financialAccountId &&
        a.type === FinancialAccountType.Bank &&
        PEER_BANK_SUBTYPES.has(a.subtype as FinancialAccountSubtype),
    );
    const brokeragePeers = investmentAccounts.filter(
      (a) =>
        a.id !== financialAccountId &&
        a.type === FinancialAccountType.Investment &&
        (a.subtype as FinancialAccountSubtype) === FinancialAccountSubtype.Brokerage,
    );
    const seen = new Set<string>();
    return [...bankPeers, ...brokeragePeers]
      .filter((a) => {
        if (seen.has(a.id)) return false;
        seen.add(a.id);
        return true;
      })
      .sort((a, b) => a.name.localeCompare(b.name, locale, { sensitivity: "base" }));
  }, [bankAccounts, financialAccountId, investmentAccounts, locale]);

  const schema = React.useMemo(
    () =>
      z
        .object({
          movementType: z.union([
            z.literal(InvestmentCashTransactionType.Deposit),
            z.literal(InvestmentCashTransactionType.Withdrawal),
          ]),
          amount: z.preprocess(
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
            z.number().optional(),
          ),
          date: z.date(),
          description: z.string().optional(),
          peerFinancialAccountId: z.string().optional(),
          registerAsBalanceAdjustment: z.boolean(),
        })
        .superRefine((data, ctx) => {
          const amt = data.amount;
          if (amt == null || !Number.isFinite(amt) || amt <= 0) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t("investments.cash.validation.amountPositive"),
              path: ["amount"],
            });
          }
          const dateDay = dayjs(data.date).startOf("day");
          const today = dayjs().startOf("day");
          if (dateDay.isAfter(today)) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t("investments.cash.validation.dateNotInFuture"),
              path: ["date"],
            });
          }
          if (data.registerAsBalanceAdjustment) {
            const d = data.description?.trim();
            if (!d) {
              ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: t("investments.cash.validation.descriptionRequiredAdjustment"),
                path: ["description"],
              });
            }
          }
        }),
    [localeDecimal, localeGroup, t],
  );

  const methods = useForm<RecordCashFormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      movementType: InvestmentCashTransactionType.Deposit,
      amount: undefined,
      date: new Date(),
      description: "",
      peerFinancialAccountId: "",
      registerAsBalanceAdjustment: false,
    },
  });

  const { handleSubmit, reset, control, setValue } = methods;
  const movementType = useWatch({ control, name: "movementType" });
  const registerAsBalanceAdjustment = useWatch({ control, name: "registerAsBalanceAdjustment" });

  React.useEffect(() => {
    if (registerAsBalanceAdjustment) {
      setValue("peerFinancialAccountId", "");
    }
  }, [registerAsBalanceAdjustment, setValue]);

  const [createCash, { loading: submitting, error: mutationError }] = useMutation<
    CreateInvestmentCashTransactionMutation,
    CreateInvestmentCashTransactionMutationVariables
  >(CREATE_INVESTMENT_CASH_TRANSACTION, {
    refetchQueries: [
      {
        query: INVESTMENT_CASH_BALANCE,
        variables: { financialAccountId: listContextAccountId ?? undefined },
      },
      {
        query: INVESTMENT_PORTFOLIO_SUMMARY,
        variables: { financialAccountId: listContextAccountId ?? undefined },
      },
      { query: INVESTMENT_PORTFOLIO_SUMMARY },
      {
        query: INVESTMENT_CASH_TRANSACTIONS,
        variables: { financialAccountId: listContextAccountId ?? undefined, first: 5, after: undefined },
      },
      {
        query: INVESTMENT_CASH_TRANSACTIONS,
        variables: { financialAccountId: listContextAccountId ?? undefined, first: 20, after: undefined },
      },
    ],
  });

  React.useEffect(() => {
    reset({
      movementType: InvestmentCashTransactionType.Deposit,
      amount: undefined,
      date: new Date(),
      description: "",
      peerFinancialAccountId: "",
      registerAsBalanceAdjustment: false,
    });
  }, [financialAccountId, resetSignal, reset]);

  const onSubmit = async (data: RecordCashFormValues) => {
    const peerId = data.peerFinancialAccountId?.trim();
    const input = {
      financialAccountId,
      type: data.movementType,
      amount: data.amount!,
      date: dayjs(data.date).format("YYYY-MM-DD"),
      description: data.description?.trim() || undefined,
      peerFinancialAccountId: !data.registerAsBalanceAdjustment && peerId ? peerId : undefined,
      registerAsBalanceAdjustment: data.registerAsBalanceAdjustment,
    };

    await createCash({ variables: { input } });
    await apolloClient.refetchQueries({ include: [INVESTMENT_PORTFOLIO_SUMMARY] });

    if (input.peerFinancialAccountId) {
      success(t("investments.cash.successPaired"));
    } else {
      success(t("investments.cash.success"));
    }
    onSuccess();
  };

  const typeOptions = React.useMemo(
    () => [
      { value: InvestmentCashTransactionType.Deposit, label: t("investments.cash.type.deposit") },
      { value: InvestmentCashTransactionType.Withdrawal, label: t("investments.cash.type.withdrawal") },
    ],
    [t],
  );

  return (
    <FormProvider {...methods}>
      <form id={formId} onSubmit={handleSubmit(onSubmit)}>
        <FormStack>
          {mutationError && <FormErrorBanner message={mutationError.message} />}

          <Typography variant="overline" color="text.secondary" sx={{ mb: 0 }}>
            {t("investments.cash.movementTypeLabel")}
          </Typography>
          <SegmentedControl
            fullWidth
            value={movementType}
            onChange={(v) =>
              setValue("movementType", v as InvestmentCashTransactionType.Deposit | InvestmentCashTransactionType.Withdrawal)
            }
            options={typeOptions}
            data-testid="record-cash-type"
          />

          <FormControlLabel
            control={
              <Controller
                name="registerAsBalanceAdjustment"
                control={control}
                render={({ field }) => (
                  <Checkbox
                    {...field}
                    checked={field.value}
                    data-testid="record-cash-balance-adjustment"
                  />
                )}
              />
            }
            label={t("investments.cash.registerAsBalanceAdjustment")}
          />

          <Divider sx={{ my: 1 }} />

          <FormRowStack alignItems="flex-start">
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <MoneyInput
                name="amount"
                label={t("investments.cash.amount")}
                currency="BRL"
                data-testid="record-cash-amount"
              />
            </Box>
            <Box sx={{ flex: 1, minWidth: 0 }}>
              <DatePickerField
                name="date"
                label={t("investments.cash.date")}
                maxDate={dayjs().startOf("day").toDate()}
                data-testid="record-cash-date"
              />
            </Box>
          </FormRowStack>

          {!registerAsBalanceAdjustment && peerOptions.length > 0 ? (
            <Controller
              name="peerFinancialAccountId"
              control={control}
              render={({ field }) => {
                const selected = peerOptions.find((a) => a.id === field.value) ?? null;
                return (
                  <Autocomplete<IssuerSelectionAccount, false, false, false>
                    options={peerOptions}
                    value={selected}
                    onChange={(_, newValue) => field.onChange(newValue?.id ?? "")}
                    onBlur={field.onBlur}
                    disabled={submitting}
                    getOptionLabel={(a) => a.name}
                    isOptionEqualToValue={(a, b) => a.id === b.id}
                    noOptionsText={t("investments.cash.peerAccountNoMatch")}
                    renderOption={(props, account) => {
                      const { key, ...optionProps } = props;
                      return (
                        <Box
                          component="li"
                          key={key}
                          {...optionProps}
                          sx={{
                            display: "flex",
                            alignItems: "center",
                            gap: 1.5,
                            py: 1,
                            px: 1.5,
                          }}
                        >
                          <PeerAccountAvatar account={account} />
                          <Box sx={{ minWidth: 0, flex: 1 }}>
                            <Typography variant="body2" fontWeight={600} noWrap>
                              {account.name}
                            </Typography>
                            <Typography variant="caption" color="text.secondary" noWrap>
                              {account.issuer?.name || account.issuerName || "\u00A0"}
                            </Typography>
                          </Box>
                        </Box>
                      );
                    }}
                    renderInput={(params) => (
                      <TextField
                        {...params}
                        label={t("investments.cash.peerAccount")}
                        data-testid="record-cash-peer-account"
                        inputProps={{
                          ...params.inputProps,
                          "aria-label": t("investments.cash.peerAccount"),
                        }}
                        InputProps={{
                          ...params.InputProps,
                          startAdornment: (
                            <>
                              {selected ? <PeerAccountAvatar account={selected} size={24} /> : null}
                              {params.InputProps.startAdornment}
                            </>
                          ),
                        }}
                      />
                    )}
                    slotProps={{
                      clearIndicator: { title: t("investments.cash.peerAccountNone") },
                    }}
                  />
                );
              }}
            />
          ) : null}

          <FormTextField
            name="description"
            label={
              registerAsBalanceAdjustment
                ? t("investments.cash.descriptionAdjustment")
                : t("investments.cash.description")
            }
            multiline
            minRows={2}
            data-testid="record-cash-description"
          />

          <Divider sx={{ py: 1 }} />

          <Drawer.ActionButtons
            cancelText={cancelText}
            submitText={submitText}
            submittingText={submittingText}
            onCancel={onCancel ?? (() => {})}
            submitForm={formId}
            cancelDisabled={submitting}
            submitDisabled={submitting}
            submitting={submitting}
            cancelTestId={cancelTestId}
            submitTestId={submitTestId}
          />
        </FormStack>
      </form>
    </FormProvider>
  );
}
