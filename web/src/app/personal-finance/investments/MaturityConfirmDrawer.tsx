"use client";

import React from "react";
import { useMutation } from "@apollo/client/react";
import { Alert, Avatar, Box, Typography } from "@mui/material";
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
import { DatePickerField, MoneyInput, SelectField } from "@/shared/components/ui/forms/form";
import { FormStack } from "@/shared/components/ui/forms/components/FormStack";
import { useToast } from "@/shared/providers";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../i18n";

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
  const [step, setStep] = React.useState<1 | 2>(1);
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
      bankAccountId: "",
      creditAmount: undefined,
      creditDate: null,
    },
    mode: "onBlur",
    reValidateMode: "onChange",
  });

  React.useEffect(() => {
    if (!open || !holding) return;
    setStep(1);
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
      bankAccountId: selectableBanks[0]?.id ?? "",
      creditAmount: n,
      creditDate: defaultMaturityDate,
    });
  }, [holding, methods, open, selectableBanks]);

  const watchedBankAccountId = useWatch({ control: methods.control, name: "bankAccountId" });
  const watchedMaturityDate = useWatch({ control: methods.control, name: "maturityDate" });

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

  const handleStep1Next = () => {
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
      const msg = parsed.error.issues[0]?.message || "Invalid";
      setStep1Error(msg);
      return;
    }
    setStep1Error(null);
    setStep(2);
  };

  const submit = async (withCash: boolean) => {
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
      setStep(1);
      setStep1Error(parsed.error.issues[0]?.message || "Invalid");
      return;
    }

    const { grossValue, netValue, incomeTax, iof, closeReason, maturityDate } = parsed.data;
    const selectedMaturityDateStr = dayjs(maturityDate).format("YYYY-MM-DD");
    const cashMovementDate = values.creditDate ? dayjs(values.creditDate).format("YYYY-MM-DD") : selectedMaturityDateStr;
    const cashMovementAmount = values.creditAmount ?? 0;

    const cashMovement =
      withCash && values.bankAccountId
        ? {
            financialAccountId: values.bankAccountId,
            amount: cashMovementAmount,
            date: cashMovementDate,
          }
        : undefined;

    if (withCash && cashMovement) {
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

  const title = t("investments.pendingActions.maturityDrawer.closePositionTitle", {
    defaultValue: "ENCERRAR POSIÇÃO",
  });

  return (
    <Drawer open={open} onClose={onClose} anchor="right" size="md">
      <Drawer.Header title={title} />
      <Drawer.Body>
        <FormProvider {...methods}>
          <FormStack spacing={2} sx={{ maxWidth: 520 }}>
            <Box sx={{ display: "flex", alignItems: "center", gap: 1.25, minWidth: 0 }}>
              <Avatar
                variant="rounded"
                src={holding.issuerLogoUrl ?? undefined}
                alt={holding.issuerName ?? undefined}
                sx={{ width: 36, height: 36 }}
                slotProps={{ img: { style: { objectFit: "contain" } } }}
              >
                {(holding.issuerName ?? holding.asset.name ?? "—").slice(0, 1).toUpperCase()}
              </Avatar>
              <Box sx={{ minWidth: 0 }}>
                <Typography variant="body2" noWrap>
                  {holding.asset.name}
                </Typography>
                {holding.issuerName ? (
                  <Typography variant="caption" color="text.secondary" noWrap>
                    {holding.issuerName}
                  </Typography>
                ) : null}
              </Box>
            </Box>

            {step === 1 ? (
              <>
                {step1Error ? <Alert severity="error">{step1Error}</Alert> : null}

                <MoneyInput
                  name="grossValue"
                  label={t("investments.pendingActions.maturityDrawer.grossValue")}
                  currency="BRL"
                />
                <MoneyInput
                  name="netValue"
                  label={t("investments.pendingActions.maturityDrawer.netValue")}
                  currency="BRL"
                />
                <MoneyInput
                  name="incomeTax"
                  label={t("investments.pendingActions.maturityDrawer.incomeTax")}
                  currency="BRL"
                />
                <MoneyInput
                  name="iof"
                  label={t("investments.pendingActions.maturityDrawer.iof")}
                  currency="BRL"
                />

                <DatePickerField
                  name="maturityDate"
                  label={t("investments.pendingActions.maturityDrawer.maturityDate", { defaultValue: "Data" })}
                />

                <SelectField
                  name="closeReason"
                  label={t("investments.pendingActions.maturityDrawer.closeReason")}
                  options={closeReasonOptions}
                />
              </>
            ) : (
              <>
                <Typography variant="body2">
                  {t("investments.pendingActions.maturityDrawer.step2Hint")}
                </Typography>

                <SelectField
                  name="bankAccountId"
                  label={t("investments.pendingActions.maturityDrawer.bankAccount")}
                  options={selectableBanks.map((a) => ({ value: a.id, label: a.name }))}
                />

                <MoneyInput
                  name="creditAmount"
                  label={t("investments.pendingActions.maturityDrawer.netValue")}
                  currency="BRL"
                />

                <DatePickerField
                  name="creditDate"
                  label={t("investments.pendingActions.maturityDrawer.creditDate")}
                />
              </>
            )}
          </FormStack>
        </FormProvider>
      </Drawer.Body>
      <Drawer.Footer>
        {step === 1 ? (
          <Drawer.ActionButtons
            cancelText={t("actions.cancel")}
            submitText={t("investments.pendingActions.maturityDrawer.confirmContinue")}
            onCancel={onClose}
            onSubmit={handleStep1Next}
            cancelDisabled={loading}
            submitDisabled={loading}
          />
        ) : (
          <>
            <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 2, flexWrap: "wrap" }}>
              <Drawer.CancelButton onClick={() => setStep(1)} disabled={loading}>
                {t("investments.pendingActions.maturityDrawer.back")}
              </Drawer.CancelButton>
              <Drawer.CancelButton onClick={() => submit(false)} disabled={loading}>
                {t("investments.pendingActions.maturityDrawer.skipCredit")}
              </Drawer.CancelButton>
              <Drawer.SubmitButton
                onClick={() => submit(true)}
                disabled={loading || !watchedBankAccountId}
                loading={loading}
              >
                {t("investments.pendingActions.maturityDrawer.submitClose", { defaultValue: "Encerrar" })}
              </Drawer.SubmitButton>
            </Box>
          </>
        )}
      </Drawer.Footer>
    </Drawer>
  );
}
