"use client";

import React from "react";
import { useMutation } from "@apollo/client/react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Box, Divider, Typography } from "@mui/material";
import { FormProvider, useForm } from "react-hook-form";
import { z } from "zod";
import { CONFIRM_COUPON_PAYMENT } from "@/lib/graphql/operations/banking/pendingActionOperations";
import { UPDATE_INVESTMENT_TRANSACTION } from "@/lib/graphql/operations/banking/mutations";
import { InvestmentTransactionDirection, InvestmentTransactionType } from "@/lib/graphql/types/__generated__/graphql";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { DatePickerField, MoneyInput } from "@/shared/components/ui/forms/form";
import { FormRowStack, FormStack } from "@/shared/components/ui/forms/components";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import { formatDateOnly, parseDateOnly, toStartOfDayLocal } from "@/shared/utils/date/dateOnly";
import {
  FinancialAccountIdentity,
  type FinancialAccountIdentityAccount,
} from "@/shared/components/ui/data-display/FinancialAccountIdentity";
import { useToast } from "@/shared/providers";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../i18n";
import { FixedIncomeAssetIdentity } from "./components/HoldingIdentity";
import { SectionDivider } from "@/shared/components/ui";

type ConfirmCouponPaymentMutation = {
  confirmCouponPayment: {
    couponTransaction: { id: string };
  };
};

type ConfirmCouponPaymentMutationVariables = {
  input: {
    holdingId: string;
    tradeDate: string;
    grossValue: number;
    netValue: number;
    incomeTax: number;
    destinationAccountId: string;
    pendingActionId?: string;
  };
};

type UpdateInvestmentTransactionMutation = {
  updateInvestmentTransaction: { id: string };
};

type UpdateInvestmentTransactionMutationVariables = {
  id: string;
  input: {
    type: InvestmentTransactionType;
    direction: InvestmentTransactionDirection;
    tradeDate: string;
    grossValue: number;
    netValue?: number | null;
    incomeTax?: number | null;
    iof?: number | null;
  };
};

export type CouponDrawerHolding = {
  id: string;
  financialAccountId: string;
  quantity: number;
  averageCost: number;
  couponFrequency: string | null;
  issueDate: string | null;
  isTaxExempt: boolean;
  grossValue: number | null;
  /** Pre-computed by the backend: grossValue − principal for the current coupon period. */
  accruedInterest: number | null;
  couponDate: string | null;
  issuerName?: string | null;
  asset: { id: string; name: string; assetType: string | null };
};

/**
 * Brazilian IR regressive table for fixed income (Tabela Regressiva de IR).
 * Applied on each coupon based on the holding period from issueDate to payment date.
 * Tax-exempt assets (LCI, LCA, CRI, CRA, LIG) return 0.
 */
function brazilianIrRate(holdingDays: number): number {
  if (holdingDays <= 180) return 0.225;
  if (holdingDays <= 360) return 0.20;
  if (holdingDays <= 720) return 0.175;
  return 0.15;
}

function estimateCouponAmounts(holding: CouponDrawerHolding, couponDate: Date) {
  const estimatedGross = holding.accruedInterest ?? undefined;

  if (estimatedGross == null) {
    return { estimatedGross: undefined, estimatedNet: undefined, estimatedIncomeTax: undefined, irRate: null };
  }

  const isExempt = holding.isTaxExempt;

  let irRate = 0;
  if (!isExempt) {
    if (holding.issueDate) {
      const issueDate = parseDateOnly(holding.issueDate);
      const issueMs = issueDate?.getTime();
      if (issueMs == null) {
        irRate = 0.15;
      } else {
      const couponMs = couponDate.getTime();
      const holdingDays = Math.max(0, Math.round((couponMs - issueMs) / 86_400_000));
      irRate = brazilianIrRate(holdingDays);
      }
    } else {
      // No issueDate — conservatively assume the most favourable rate (longest period)
      irRate = 0.15;
    }
  }

  const estimatedNet = Number((estimatedGross * (1 - irRate)).toFixed(2));
  const estimatedIncomeTax = Number((estimatedGross - estimatedNet).toFixed(2));
  return { estimatedGross, estimatedNet, estimatedIncomeTax, irRate };
}

interface CouponPaymentConfirmDrawerProps {
  open: boolean;
  onClose: () => void;
  pendingActionId: string | null;
  holding: CouponDrawerHolding | null;
  destinationAccount: FinancialAccountIdentityAccount | null;
  editingTransaction?: {
    id: string;
    tradeDate: string;
    grossValue: number;
    netValue?: number | null;
    incomeTax?: number | null;
    iof?: number | null;
  } | null;
  onSuccess?: () => void;
}

type CouponPaymentFormData = {
  grossValue: number | undefined;
  netValue: number | undefined;
  incomeTax: number | undefined;
  tradeDate: Date | null;
};

const schema = z
  .object({
    grossValue: z.number().positive("Gross must be positive"),
    netValue: z.number().min(0),
    incomeTax: z.number().min(0),
    tradeDate: z.date(),
  })
  .refine((data) => data.netValue <= data.grossValue, {
    message: "Net must be ≤ gross",
    path: ["netValue"],
  });

export function CouponPaymentConfirmDrawer({
  open,
  onClose,
  pendingActionId,
  holding,
  destinationAccount,
  editingTransaction = null,
  onSuccess,
}: CouponPaymentConfirmDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const toast = useToast();
  const [confirmCoupon, { loading, error }] = useMutation<
    ConfirmCouponPaymentMutation,
    ConfirmCouponPaymentMutationVariables
  >(CONFIRM_COUPON_PAYMENT);
  const [updateInvestmentTransaction, { loading: updating, error: updateError }] = useMutation<
    UpdateInvestmentTransactionMutation,
    UpdateInvestmentTransactionMutationVariables
  >(UPDATE_INVESTMENT_TRANSACTION);

  const [hintIrRate, setHintIrRate] = React.useState<number | null>(null);

  const methods = useForm<CouponPaymentFormData>({
    resolver: zodResolver(schema),
    defaultValues: {
      grossValue: undefined,
      netValue: undefined,
      incomeTax: undefined,
      tradeDate: null,
    },
    mode: "onBlur",
    reValidateMode: "onChange",
  });

  React.useEffect(() => {
    if (!open || !holding) return;
    if (!holding.couponFrequency || holding.couponFrequency === "AT_MATURITY") {
      onClose();
      return;
    }
    if (editingTransaction) {
      setHintIrRate(null);
      methods.reset({
        grossValue: editingTransaction.grossValue,
        netValue: editingTransaction.netValue ?? undefined,
        incomeTax: editingTransaction.incomeTax ?? undefined,
        tradeDate: parseDateOnly(editingTransaction.tradeDate) ?? toStartOfDayLocal(new Date()),
      });
      return;
    }
    const couponDate = parseDateOnly(holding.couponDate) ?? toStartOfDayLocal(new Date());
    const { estimatedGross, estimatedNet, estimatedIncomeTax, irRate } = estimateCouponAmounts(holding, couponDate);
    setHintIrRate(irRate);
    methods.reset({
      grossValue: estimatedGross,
      netValue: estimatedNet,
      incomeTax: estimatedIncomeTax,
      tradeDate: couponDate,
    });
  }, [editingTransaction, holding, methods, onClose, open]);

  const submit = methods.handleSubmit(async (values) => {
    if (!holding || !values.tradeDate) return;
    try {
      if (editingTransaction) {
        await updateInvestmentTransaction({
          variables: {
              id: editingTransaction.id,
              input: {
                type: InvestmentTransactionType.InterestPayment,
                direction: InvestmentTransactionDirection.Outflow,
                tradeDate: formatDateOnly(values.tradeDate),
                grossValue: values.grossValue ?? 0,
                netValue: values.netValue ?? null,
                incomeTax: values.incomeTax ?? null,
              iof: editingTransaction.iof ?? null,
            },
          },
        });
        toast.success(t("investments.detailDrawer.couponSchedule.updateSuccess"));
        onSuccess?.();
        onClose();
        return;
      }
      await confirmCoupon({
        variables: {
          input: {
            holdingId: holding.id,
            tradeDate: formatDateOnly(values.tradeDate),
            grossValue: values.grossValue ?? 0,
            netValue: values.netValue ?? 0,
            incomeTax: values.incomeTax ?? 0,
            destinationAccountId: holding.financialAccountId,
            pendingActionId: pendingActionId || undefined,
          },
        },
      });
      toast.success(t("investments.pendingActions.registerCoupon"));
      onSuccess?.();
      onClose();
    } catch (mutationError) {
      toast.error(mutationError instanceof Error ? mutationError.message : "Request failed");
    }
  });

  if (!holding) return null;

  return (
    <Drawer open={open} onClose={onClose} anchor="right" size="md">
      <Drawer.Header title={t("investments.pendingActions.registerCoupon")} />
      <Drawer.Body>
        <FormProvider {...methods}>
          <form id="confirm-coupon-payment-form" onSubmit={submit}>
            <FormStack>
              <FixedIncomeAssetIdentity
                assetType={holding.asset.assetType}
                assetName={holding.asset.name}
                issuerName={holding.issuerName ?? undefined}
                size={36}
                sx={{ pb: 1 }}
                data-testid="coupon-drawer-asset-identity"
              />

              <ErrorAlert error={error} />
              <ErrorAlert error={updateError} />

              <SectionDivider
                label={t("investments.pendingActions.maturityDrawer.creditDestination")}
              />

              <FinancialAccountIdentity
                account={destinationAccount}
                data-testid="coupon-drawer-destination-account"
              />

              <SectionDivider
                label={t("investments.pendingActions.maturityDrawer.values")}
              />

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
                  {(() => {
                    const isExempt = holding.isTaxExempt;
                    if (isExempt) {
                      return (
                        <Typography variant="caption" color="success.main">
                          {t("investments.pendingActions.couponDrawer.taxExempt")}
                        </Typography>
                      );
                    }
                    if (hintIrRate !== null && hintIrRate > 0) {
                      return (
                        <Typography variant="caption" color="text.secondary">
                          {t("investments.pendingActions.couponDrawer.irRateHint", {
                            rate: `${(hintIrRate * 100).toFixed(1)}%`,
                          })}
                        </Typography>
                      );
                    }
                    return null;
                  })()}
                </Box>
                <Box sx={{ flex: 1, minWidth: 0 }}>
                  <DatePickerField
                    name="tradeDate"
                    label={t("investments.pendingActions.maturityDrawer.creditDate")}
                    size="medium"
                  />
                </Box>
              </FormRowStack>

              <Divider />

              <Drawer.ActionButtons
                cancelText={t("actions.cancel")}
                submitText={t("investments.pendingActions.registerCoupon")}
                onCancel={onClose}
                submitForm="confirm-coupon-payment-form"
                cancelDisabled={loading || updating}
                submitDisabled={loading || updating}
                submitting={loading || updating}
                cancelTestId="coupon-confirm-cancel-btn"
                submitTestId="coupon-confirm-submit-btn"
              />
            </FormStack>
          </form>
        </FormProvider>
      </Drawer.Body>
    </Drawer>
  );
}

export default CouponPaymentConfirmDrawer;
