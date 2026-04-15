"use client";

import React from "react";
import { useMutation } from "@apollo/client/react";
import { Alert, Box, Divider, Typography } from "@mui/material";
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
import { HoldingCloseReason } from "@/lib/graphql/types/__generated__/graphql";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { DatePickerField, MoneyInput, SelectField, ToggleField } from "@/shared/components/ui/forms/form";
import { FormRowStack, FormStack } from "@/shared/components/ui/forms/components";
import { FinancialAccountIdentity } from "@/shared/components/ui/data-display/FinancialAccountIdentity";
import { useToast } from "@/shared/providers";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../i18n";
import { FixedIncomeAssetIdentity } from "./components/HoldingIdentity";
import type { IssuerSelectionAccount } from "./components/IssuerSelection";

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
  financialAccountId: string;
  maturityDate: string | null;
  grossValue: number | null;
  netValue: number | null;
  incomeTax: number | null;
  iof: number | null;
  issuerName?: string | null;
  issuerLogoUrl?: string | null;
  asset: { id: string; name: string; assetType: string | null };
};

interface MaturityConfirmDrawerProps {
  open: boolean;
  onClose: () => void;
  holding: MaturityDrawerHolding | null;
  /** Used to show the custodian (“Corretora”) when crediting net value to the holding account. */
  investmentAccounts?: IssuerSelectionAccount[];
  onSuccess?: () => void;
}

type MaturityConfirmFormData = {
  grossValue: number | undefined;
  netValue: number | undefined;
  incomeTax: number | undefined;
  iof: number | undefined;
  maturityDate: Date | null;
  closeReason: HoldingCloseReason;
  shouldCreditToAccount: boolean;
  creditAmount: number | undefined;
  creditDate: Date | null;
};

export function MaturityConfirmDrawer({
  open,
  onClose,
  holding,
  investmentAccounts = [],
  onSuccess,
}: MaturityConfirmDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const toast = useToast();
  const [step1Error, setStep1Error] = React.useState<string | null>(null);

  const [confirmMaturity, { loading }] = useMutation<
    ConfirmHoldingMaturityMutation,
    ConfirmHoldingMaturityMutationVariables
  >(CONFIRM_HOLDING_MATURITY);

  const methods = useForm<MaturityConfirmFormData>({
    defaultValues: {
      grossValue: undefined,
      netValue: undefined,
      incomeTax: undefined,
      iof: undefined,
      maturityDate: null,
      closeReason: HoldingCloseReason.Matured,
      shouldCreditToAccount: false,
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
      creditAmount: n,
      creditDate: defaultMaturityDate,
    });
  }, [holding, methods, open]);

  const watchedShouldCreditToAccount = useWatch({ control: methods.control, name: "shouldCreditToAccount" });
  const watchedMaturityDate = useWatch({ control: methods.control, name: "maturityDate" });

  const creditCustodianAccount = React.useMemo(
    () =>
      holding ? investmentAccounts.find((a) => a.id === holding.financialAccountId) : undefined,
    [holding, investmentAccounts],
  );

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

    const creditAccountId = holding.financialAccountId;
    const cashMovement =
      values.shouldCreditToAccount && creditAccountId
        ? {
            financialAccountId: creditAccountId,
            amount: cashMovementAmount,
            date: cashMovementDate,
          }
        : undefined;

    if (values.shouldCreditToAccount && !creditAccountId) {
      toast.error(t("investments.pendingActions.maturityDrawer.missingHoldingAccount"));
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
            <FixedIncomeAssetIdentity
              assetType={holding.asset.assetType}
              assetName={holding.asset.name}
              issuerName={holding.issuerName ?? undefined}
              size={36}
              sx={{ pb: 1 }}
              data-testid="maturity-drawer-asset-identity"
            />
            {step1Error ? <Alert severity="error">{step1Error}</Alert> : null}

            <FormRowStack alignItems="flex-start">
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <MoneyInput
                  name="grossValue"
                  label={t("investments.pendingActions.maturityDrawer.grossValue")}
                  currency="BRL"
                  size="medium"
                />
              </Box>
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <MoneyInput
                  name="netValue"
                  label={t("investments.pendingActions.maturityDrawer.netValue")}
                  currency="BRL"
                  size="medium"
                />
              </Box>
            </FormRowStack>

            <FormRowStack alignItems="flex-start">
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <MoneyInput
                  name="incomeTax"
                  label={t("investments.pendingActions.maturityDrawer.incomeTax")}
                  currency="BRL"
                  size="medium"
                />
              </Box>
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <MoneyInput
                  name="iof"
                  label={t("investments.pendingActions.maturityDrawer.iof")}
                  currency="BRL"
                  size="medium"
                />
              </Box>
            </FormRowStack>

            <FormRowStack alignItems="flex-start">
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <DatePickerField
                  name="maturityDate"
                  label={t("investments.pendingActions.maturityDrawer.maturityDate")}
                  size="medium"
                />
              </Box>
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <SelectField
                  name="closeReason"
                  label={t("investments.pendingActions.maturityDrawer.closeReason")}
                  options={closeReasonOptions}
                  size="medium"
                />
              </Box>
            </FormRowStack>

            <ToggleField
              name="shouldCreditToAccount"
              label={t(
                "investments.pendingActions.maturityDrawer.creditNetValueToAccount",
                { defaultValue: "Creditar o valor liquido na conta?" },
              )}
              size="medium"
              data-testid="maturity-credit-toggle"
            />

            {watchedShouldCreditToAccount ? (
              <>
                <FormRowStack alignItems="flex-start">
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <MoneyInput
                      name="creditAmount"
                      label={t("investments.pendingActions.maturityDrawer.netValue")}
                      currency="BRL"
                      size="medium"
                    />
                  </Box>
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <DatePickerField
                      name="creditDate"
                      label={t("investments.pendingActions.maturityDrawer.creditDate")}
                      size="medium"
                      minDate={watchedMaturityDate || undefined}
                      maxDate={watchedMaturityDate ? dayjs(watchedMaturityDate).add(5, "year").toDate() : undefined}
                    />
                  </Box>
                </FormRowStack>
                <Box
                  sx={(theme) => ({
                    position: "relative",
                    border: `1px solid ${theme.palette.divider}`,
                    borderRadius: 1,
                    px: 1.5,
                    pt: 2,
                    pb: 1.5,
                  })}
                  data-testid="maturity-credit-destination-box"
                >
                  <Typography
                    component="span"
                    variant="caption"
                    sx={(theme) => ({
                      position: "absolute",
                      left: 10,
                      top: 0,
                      transform: "translateY(-50%)",
                      px: 0.75,
                      bgcolor: theme.palette.background.paper,
                      color: theme.palette.text.secondary,
                      lineHeight: 1.2,
                      pointerEvents: "none",
                    })}
                  >
                    {t("investments.pendingActions.maturityDrawer.creditDestination")}
                  </Typography>
                  <FinancialAccountIdentity
                    account={creditCustodianAccount}
                    justify="flex-start"
                    nameTitle={creditCustodianAccount?.name || holding.financialAccountId}
                    data-testid="maturity-credit-custodian-account"
                  />
                </Box>
              </>
            ) : null}
            <Divider sx={{ pb: 1 }}/>
            <Drawer.ActionButtons
              cancelText={t("actions.cancel")}
              submitText={t("investments.pendingActions.maturityDrawer.submitClose", { defaultValue: "Encerrar" })}
              onCancel={onClose}
              onSubmit={submit}
              cancelDisabled={loading}
              submitDisabled={loading || (watchedShouldCreditToAccount && !holding.financialAccountId)}
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
