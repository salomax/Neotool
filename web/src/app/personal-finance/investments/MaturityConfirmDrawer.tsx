"use client";

import React from "react";
import { useMutation } from "@apollo/client/react";
import { Alert, Box, Divider } from "@mui/material";
import dayjs from "dayjs";
import { z } from "zod";
import { FormProvider, useForm, useWatch } from "react-hook-form";
import {
  CONFIRM_HOLDING_MATURITY,
} from "@/lib/graphql/operations/banking/pendingActionOperations";
import {
  ConfirmHoldingMaturityMutation,
  ConfirmHoldingMaturityMutationVariables,
} from "@/lib/graphql/operations/banking/pendingActionOperations.generated";
import type { FinancialAccountsQuery } from "@/lib/graphql/operations/banking/queries.generated";
import {
  FinancialAccountSubtype,
  FinancialAccountType,
  HoldingCloseReason,
} from "@/lib/graphql/types/__generated__/graphql";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { DatePickerField, MoneyInput, SelectField, ToggleField } from "@/shared/components/ui/forms/form";
import { FormRowStack, FormStack } from "@/shared/components/ui/forms/components";
import { useToast } from "@/shared/providers";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../i18n";
import { HoldingIdentity } from "./components/HoldingIdentity";

const step1Schema = z
  .object({
    grossValue: z.number().positive("Gross must be positive"),
    netValue: z.number().min(0),
    incomeTax: z.number().min(0).optional(),
    iof: z.number().min(0).optional(),
    maturityDate: z.date(),
    closeReason: z.nativeEnum(HoldingCloseReason),
  })
  .refine((d) => d.netValue <= d.grossValue, {
    message: "Net must be ≤ gross",
    path: ["netValue"],
  });

export type MaturityDrawerHolding = {
  id: string;
  maturityDate: string | null;
  grossValue: number | null;
  netValue: number | null;
  incomeTax: number | null;
  iof: number | null;
  issuerName?: string | null;
  issuerLogoUrl?: string | null;
  asset: { id: string; name: string };
};

type BankAccountRow = NonNullable<FinancialAccountsQuery["financialAccounts"]>[number];

interface MaturityConfirmDrawerProps {
  open: boolean;
  onClose: () => void;
  holding: MaturityDrawerHolding | null;
  bankAccounts: BankAccountRow[];
  onSuccess?: () => void;
}

const CASH_SUBTYPES = new Set<FinancialAccountSubtype>([
  FinancialAccountSubtype.ContaCorrente,
  FinancialAccountSubtype.ContaPoupanca,
  FinancialAccountSubtype.ContaDigital,
  FinancialAccountSubtype.ContaSalario,
]);

type MaturityConfirmFormData = {
  grossValue: number | undefined;
  netValue: number | undefined;
  incomeTax: number | undefined;
  iof: number | undefined;
  maturityDate: Date | null;
  closeReason: HoldingCloseReason;
  shouldCreditToAccount: boolean;
  bankAccountId: string;
  creditAmount: number | undefined;
  creditDate: Date | null;
};

export function MaturityConfirmDrawer({
  open,
  onClose,
  holding,
  bankAccounts,
  onSuccess,
}: MaturityConfirmDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const toast = useToast();
  const [step1Error, setStep1Error] = React.useState<string | null>(null);

  const [confirmMaturity, { loading }] = useMutation<
    ConfirmHoldingMaturityMutation,
    ConfirmHoldingMaturityMutationVariables
  >(CONFIRM_HOLDING_MATURITY);

  const selectableBanks = React.useMemo(
    () =>
      bankAccounts.filter(
        (a) => a.type === FinancialAccountType.Bank && a.subtype != null && CASH_SUBTYPES.has(a.subtype),
      ),
    [bankAccounts],
  );

  const methods = useForm<MaturityConfirmFormData>({
    defaultValues: {
      grossValue: undefined,
      netValue: undefined,
      incomeTax: undefined,
      iof: undefined,
      maturityDate: null,
      closeReason: HoldingCloseReason.Matured,
      shouldCreditToAccount: false,
      bankAccountId: "",
      creditAmount: undefined,
      creditDate: null,
    },
    mode: "onBlur",
    reValidateMode: "onChange",
  });

  React.useEffect(() => {
    if (!open || !holding) return;
    setStep1Error(null);
    const g = holding.grossValue ?? 0;
    const n = holding.netValue ?? g;
    const defaultMaturityDate = holding.maturityDate ? dayjs(holding.maturityDate).toDate() : dayjs().toDate();
    methods.reset({
      grossValue: g,
      netValue: n,
      incomeTax: holding.incomeTax ?? undefined,
      iof: holding.iof ?? undefined,
      maturityDate: defaultMaturityDate,
      closeReason: HoldingCloseReason.Matured,
      shouldCreditToAccount: false,
      bankAccountId: selectableBanks[0]?.id ?? "",
      creditAmount: n,
      creditDate: defaultMaturityDate,
    });
  }, [holding, methods, open, selectableBanks]);

  const watchedBankAccountId = useWatch({ control: methods.control, name: "bankAccountId" });
  const watchedMaturityDate = useWatch({ control: methods.control, name: "maturityDate" });
  const watchedShouldCreditToAccount = useWatch({ control: methods.control, name: "shouldCreditToAccount" });

  const maturityDateStr = React.useMemo(() => {
    if (watchedMaturityDate) return dayjs(watchedMaturityDate).format("YYYY-MM-DD");
    if (holding?.maturityDate) return dayjs(holding.maturityDate).format("YYYY-MM-DD");
    return dayjs().format("YYYY-MM-DD");
  }, [holding?.maturityDate, watchedMaturityDate]);

  const closeReasonOptions = React.useMemo(
    () =>
      [
        HoldingCloseReason.Matured,
        HoldingCloseReason.RedeemedEarly,
        HoldingCloseReason.Transferred,
      ].map((value) => ({
        value,
        label: t(`investments.pendingActions.maturityDrawer.closeReasons.${value}`, { defaultValue: value }),
      })),
    [t],
  );

  const submit = async () => {
    if (!holding) return;
    const values = methods.getValues();
    const parsed = step1Schema.safeParse({
      grossValue: values.grossValue,
      netValue: values.netValue,
      incomeTax: values.incomeTax,
      iof: values.iof,
      maturityDate: values.maturityDate,
      closeReason: values.closeReason,
    });

    if (!parsed.success) {
      setStep1Error(parsed.error.issues[0]?.message || "Invalid");
      return;
    }

    const { grossValue, netValue, incomeTax, iof, closeReason, maturityDate } = parsed.data;
    const selectedMaturityDateStr = dayjs(maturityDate).format("YYYY-MM-DD");
    const cashMovementDate = values.creditDate ? dayjs(values.creditDate).format("YYYY-MM-DD") : selectedMaturityDateStr;
    const cashMovementAmount = values.creditAmount ?? 0;

    const cashMovement =
      values.shouldCreditToAccount && values.bankAccountId
        ? {
            financialAccountId: values.bankAccountId,
            amount: cashMovementAmount,
            date: cashMovementDate,
          }
        : undefined;

    if (values.shouldCreditToAccount && !values.bankAccountId) {
      toast.error(
        t("investments.pendingActions.maturityDrawer.selectBankAccount", {
          defaultValue: "Selecione a conta para creditar",
        }),
      );
      return;
    }

    if (values.shouldCreditToAccount && cashMovement) {
      if (!(cashMovementAmount > 0)) {
        toast.error(t("investments.pendingActions.maturityDrawer.amountMustBePositive"));
        return;
      }
    }

    try {
      await confirmMaturity({
        variables: {
          command: {
            holdingId: holding.id,
            maturityDate: selectedMaturityDateStr,
            grossValue,
            netValue,
            incomeTax,
            iof,
            closeReason,
            cashMovement,
          },
        },
      });
      toast.success(t("investments.pendingActions.registerMaturity"));
      onSuccess?.();
      onClose();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Request failed");
    }
  };

  if (!holding) return null;

  const title = t("investments.pendingActions.maturityDrawer.closePositionTitle");

  return (
    <Drawer open={open} onClose={onClose} anchor="right" size="md">
      <Drawer.Header title={title} />
      <Drawer.Body>
        <FormProvider {...methods}>
          <FormStack spacing={2} >
            <HoldingIdentity
              assetName={holding.asset.name}
              issuerName={holding.issuerName ?? undefined}
              logoUrl={holding.issuerLogoUrl ?? undefined}
              size={36}
              sx={{ pb: 1 }}
            />
            {step1Error ? <Alert severity="error">{step1Error}</Alert> : null}

            <FormRowStack alignItems="flex-start">
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <MoneyInput
                  name="grossValue"
                  label={t("investments.pendingActions.maturityDrawer.grossValue")}
                  currency="BRL"
                />
              </Box>
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <MoneyInput
                  name="netValue"
                  label={t("investments.pendingActions.maturityDrawer.netValue")}
                  currency="BRL"
                />
              </Box>
            </FormRowStack>

            <FormRowStack alignItems="flex-start">
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <MoneyInput
                  name="incomeTax"
                  label={t("investments.pendingActions.maturityDrawer.incomeTax")}
                  currency="BRL"
                />
              </Box>
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <MoneyInput
                  name="iof"
                  label={t("investments.pendingActions.maturityDrawer.iof")}
                  currency="BRL"
                />
              </Box>
            </FormRowStack>

            <FormRowStack alignItems="flex-start">
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <DatePickerField name="maturityDate" label={t("investments.pendingActions.maturityDrawer.maturityDate")} />
              </Box>
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <SelectField
                  name="closeReason"
                  label={t("investments.pendingActions.maturityDrawer.closeReason")}
                  options={closeReasonOptions}
                />
              </Box>
            </FormRowStack>

            <ToggleField
              name="shouldCreditToAccount"
              label={t(
                "investments.pendingActions.maturityDrawer.creditNetValueToAccount",
                { defaultValue: "Creditar o valor liquido na conta?" },
              )}
              data-testid="maturity-credit-toggle"
            />

            {watchedShouldCreditToAccount ? (
              <>
                <SelectField
                  name="bankAccountId"
                  label={t("investments.pendingActions.maturityDrawer.bankAccount")}
                  options={selectableBanks.map((a) => ({ value: a.id, label: a.name }))}
                />

                <FormRowStack alignItems="flex-start">
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <MoneyInput
                      name="creditAmount"
                      label={t("investments.pendingActions.maturityDrawer.netValue")}
                      currency="BRL"
                    />
                  </Box>
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <DatePickerField
                      name="creditDate"
                      label={t("investments.pendingActions.maturityDrawer.creditDate")}
                    />
                  </Box>
                </FormRowStack>
              </>
            ) : null}
            <Divider sx={{ pb: 1 }}/>
            <Drawer.ActionButtons
              cancelText={t("actions.cancel")}
              submitText={t("investments.pendingActions.maturityDrawer.submitClose", { defaultValue: "Encerrar" })}
              onCancel={onClose}
              onSubmit={submit}
              cancelDisabled={loading}
              submitDisabled={loading || (watchedShouldCreditToAccount && !watchedBankAccountId)}
              submitting={loading}
              cancelTestId="maturity-confirm-cancel-btn"
              submitTestId="maturity-confirm-submit-btn"
            />
          </FormStack>
        </FormProvider>
      </Drawer.Body>

    </Drawer>
  );
}
