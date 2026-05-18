"use client";

import React from "react";
import { useApolloClient, useMutation, useQuery } from "@apollo/client/react";
import { Box, Button, Chip, Stack, Typography } from "@mui/material";
import { useRouter } from "next/navigation";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { EmptyState, LoadingState, QueryErrorState } from "@/shared/components/ui/feedback";
import { useAuth } from "@/shared/providers";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../i18n";
import {
  DismissPendingActionDocument,
  type DismissPendingActionMutation,
  type DismissPendingActionMutationVariables,
  PendingActionsDocument,
  type PendingActionsQuery,
  type PendingActionsQueryVariables,
  PendingActionsCountDocument,
} from "@/lib/graphql/operations/banking/pendingActionOperations.generated";
import { FinancialAccountType, PendingActionSeverity, PendingActionStatus, PendingActionType } from "@/lib/graphql/types/__generated__/graphql";
import type { IssuerSelectionAccount } from "./components/IssuerSelection";
import { MaturityConfirmDrawer, type MaturityDrawerHolding } from "./MaturityConfirmDrawer";
import { CouponPaymentConfirmDrawer, type CouponDrawerHolding } from "./CouponPaymentConfirmDrawer";
import { INVESTMENT_HOLDINGS, INVESTMENT_PORTFOLIO_SUMMARY } from "@/lib/graphql/operations/banking/queries";
import { FINANCIAL_ACCOUNTS } from "@/lib/graphql/operations/banking/queries";
import type { FinancialAccountsQuery } from "@/lib/graphql/operations/banking/queries.generated";

const SEVERITY_ORDER: Record<PendingActionSeverity, number> = {
  [PendingActionSeverity.ActionRequired]: 0,
  [PendingActionSeverity.Warning]: 1,
  [PendingActionSeverity.Info]: 2,
};

interface PendingActionsDrawerProps {
  open: boolean;
  onClose: () => void;
  investmentAccounts?: IssuerSelectionAccount[];
}

export function PendingActionsDrawer({ open, onClose, investmentAccounts = [] }: PendingActionsDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const router = useRouter();
  const client = useApolloClient();
  const { isAuthenticated } = useAuth();

  const {
    data,
    loading,
    error,
    refetch: refetchActions,
  } = useQuery<PendingActionsQuery, PendingActionsQueryVariables>(PendingActionsDocument, {
    variables: { status: PendingActionStatus.Pending },
    skip: !open || !isAuthenticated,
    fetchPolicy: "cache-and-network",
    errorPolicy: "all",
    notifyOnNetworkStatusChange: false,
  });

  const [dismissAction, { loading: dismissLoading }] = useMutation<
    DismissPendingActionMutation,
    DismissPendingActionMutationVariables
  >(DismissPendingActionDocument);

  const [maturityOpen, setMaturityOpen] = React.useState(false);
  const [maturityHolding, setMaturityHolding] = React.useState<MaturityDrawerHolding | null>(null);

  const [couponOpen, setCouponOpen] = React.useState(false);
  const [couponHolding, setCouponHolding] = React.useState<CouponDrawerHolding | null>(null);
  const [couponPendingActionId, setCouponPendingActionId] = React.useState<string | null>(null);
  const [couponDestinationAccount, setCouponDestinationAccount] = React.useState<IssuerSelectionAccount | null>(null);
  const { data: accountsData } = useQuery<FinancialAccountsQuery>(FINANCIAL_ACCOUNTS, {
    skip: !open || !isAuthenticated,
    fetchPolicy: "cache-and-network",
  });

  const sorted = React.useMemo(() => {
    const list = data?.pendingActions ?? [];
    return [...list].sort(
      (a, b) =>
        SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity] ||
        String(a.createdAt).localeCompare(String(b.createdAt)),
    );
  }, [data?.pendingActions]);

  const effectiveInvestmentAccounts = React.useMemo(() => {
    if (investmentAccounts.length > 0) return investmentAccounts;
    return (accountsData?.financialAccounts ?? []).filter(
      (account) =>
        account.type === FinancialAccountType.Investment ||
        account.type === FinancialAccountType.Benefit,
    );
  }, [accountsData?.financialAccounts, investmentAccounts]);

  const titleFor = (type: PendingActionType): string => {
    if (type === PendingActionType.InvestmentMatured) return t("investments.pendingActions.registerMaturity");
    if (type === PendingActionType.CouponPaymentDue) return t("investments.pendingActions.registerCoupon");
    if (type === PendingActionType.RecurringPaymentDue) return t("recurringPayments.pendingActions.paymentDue");
    return type;
  };

  const isRecurringDue = (rowType: PendingActionType): boolean =>
    rowType === PendingActionType.RecurringPaymentDue;

  const assetName = (row: (typeof sorted)[number]): string =>
    row.relatedHolding?.asset?.name ??
    (row.payload as { assetName?: string; recurringPaymentName?: string })?.assetName ??
    (row.payload as { recurringPaymentName?: string })?.recurringPaymentName ??
    "—";

  const refresh = React.useCallback(async () => {
    await refetchActions();
    await client.refetchQueries({
      include: [PendingActionsCountDocument, INVESTMENT_HOLDINGS, INVESTMENT_PORTFOLIO_SUMMARY],
    });
  }, [client, refetchActions]);

  const handleDismiss = async (id: string) => {
    await dismissAction({ variables: { id } });
    await refresh();
  };

  return (
    <>
      <Drawer open={open} onClose={onClose} anchor="right" size="md">
        <Drawer.Header title={t("investments.pendingActions.panelTitle")} />
        <Drawer.Body>
          {error ? <QueryErrorState error={error} onRetry={() => void refresh()} /> : null}
          {!error ? <LoadingState isLoading={loading && sorted.length === 0} minHeight={220} /> : null}
          {!error && !loading && sorted.length === 0 ? (
            <EmptyState
              title={t("investments.pendingActions.emptyTitle")}
              description={t("investments.pendingActions.emptyDescription")}
            />
          ) : null}
          {!error && sorted.length > 0 ? (
            <Stack spacing={1.5}>
              {sorted.map((row) => (
                <Box
                  key={row.id}
                  data-testid="pending-action-row"
                  sx={{
                    display: "flex",
                    flexWrap: "wrap",
                    alignItems: "center",
                    gap: 1,
                    justifyContent: "space-between",
                  }}
                >
                  <Box sx={{ display: "flex", alignItems: "center", gap: 1, minWidth: 0 }}>
                    <Chip size="small" label={row.severity} variant="outlined" />
                    <Typography variant="body2" noWrap sx={{ minWidth: 0 }}>
                      {titleFor(row.type as PendingActionType)} — {assetName(row)}
                    </Typography>
                    {isRecurringDue(row.type as PendingActionType) ? (
                      <Typography variant="caption" color="text.secondary" noWrap>
                        {(row.payload as { expectedAmount?: string | number | null })?.expectedAmount
                          ? ` · R$ ${(row.payload as { expectedAmount?: string | number }).expectedAmount}`
                          : ` · ${t("recurringPayments.variableAmountBadge")}`}
                      </Typography>
                    ) : null}
                  </Box>
                  <Box sx={{ display: "flex", gap: 1 }}>
                    {row.type === PendingActionType.InvestmentMatured && row.relatedHolding ? (
                      <Button
                        size="small"
                        variant="contained"
                        data-testid="pending-action-register-maturity"
                        onClick={() => {
                          const related = row.relatedHolding;
                          if (!related) return;
                          setMaturityHolding({
                            id: related.id,
                            financialAccountId: related.financialAccountId,
                            maturityDate: related.maturityDate ? String(related.maturityDate) : null,
                            grossValue: related.grossValue ?? null,
                            netValue: related.netValue ?? null,
                            incomeTax: related.incomeTax ?? null,
                            iof: related.iof ?? null,
                            issuerName: related.asset.issuerName ?? null,
                            issuerLogoUrl: related.asset.logoAsset?.publicUrl ?? null,
                            asset: { id: related.asset.id, name: related.asset.name, assetType: related.asset.assetType ?? null },
                          });
                          setMaturityOpen(true);
                        }}
                      >
                        {t("investments.pendingActions.registerMaturity")}
                      </Button>
                    ) : null}
                    {row.type === PendingActionType.CouponPaymentDue && row.relatedHolding ? (
                      <Button
                        size="small"
                        variant="contained"
                        data-testid="pending-action-register-coupon"
                        onClick={() => {
                          const related = row.relatedHolding;
                          if (!related) return;
                          setCouponHolding({
                            id: related.id,
                            financialAccountId: related.financialAccountId,
                            quantity: related.quantity,
                            averageCost: related.averageCost,
                            couponFrequency: related.couponFrequency ?? null,
                            issueDate: related.issueDate ? String(related.issueDate) : null,
                            isTaxExempt: related.isTaxExempt,
                            grossValue: related.grossValue ?? null,
                            accruedInterest: related.accruedInterest ?? null,
                            couponDate: null,
                            issuerName: related.asset.issuerName ?? null,
                            asset: { id: related.asset.id, name: related.asset.name, assetType: related.asset.assetType ?? null },
                          });
                          setCouponPendingActionId(row.id);
                          setCouponDestinationAccount(
                            effectiveInvestmentAccounts.find((a) => a.id === related.financialAccountId) ?? null,
                          );
                          setCouponOpen(true);
                        }}
                      >
                        {t("investments.pendingActions.registerCoupon")}
                      </Button>
                    ) : null}
                    {isRecurringDue(row.type as PendingActionType) ? (
                      <>
                        <Button
                          size="small"
                          variant="contained"
                          onClick={() => {
                            onClose();
                            router.push("/personal-finance/recurring-payments");
                          }}
                        >
                          {t("recurringPayments.pendingActions.viewAll")}
                        </Button>
                        <Button size="small" onClick={() => void handleDismiss(row.id)} disabled={dismissLoading}>
                          {t("recurringPayments.pendingActions.dismiss")}
                        </Button>
                      </>
                    ) : null}
                    {row.type !== PendingActionType.InvestmentMatured &&
                    row.type !== PendingActionType.CouponPaymentDue &&
                    !isRecurringDue(row.type as PendingActionType) ? (
                      <Button size="small" onClick={() => void handleDismiss(row.id)} disabled={dismissLoading}>
                        {t("investments.pendingActions.markSeen")}
                      </Button>
                    ) : null}
                  </Box>
                </Box>
              ))}
            </Stack>
          ) : null}
        </Drawer.Body>
        <Drawer.Footer>
          <Box sx={{ display: "flex", justifyContent: "flex-end" }}>
            <Drawer.CancelButton onClick={onClose}>{t("actions.close")}</Drawer.CancelButton>
          </Box>
        </Drawer.Footer>
      </Drawer>

      <MaturityConfirmDrawer
        open={maturityOpen}
        onClose={() => {
          setMaturityOpen(false);
          setMaturityHolding(null);
        }}
        holding={maturityHolding}
        investmentAccounts={effectiveInvestmentAccounts}
        onSuccess={() => {
          setMaturityOpen(false);
          setMaturityHolding(null);
          void refresh();
        }}
      />

      <CouponPaymentConfirmDrawer
        open={couponOpen}
        onClose={() => {
          setCouponOpen(false);
          setCouponHolding(null);
          setCouponPendingActionId(null);
          setCouponDestinationAccount(null);
        }}
        holding={couponHolding}
        pendingActionId={couponPendingActionId}
        destinationAccount={couponDestinationAccount}
        onSuccess={() => {
          setCouponOpen(false);
          setCouponHolding(null);
          setCouponPendingActionId(null);
          setCouponDestinationAccount(null);
          void refresh();
        }}
      />
    </>
  );
}
