"use client";

import React from "react";
import { useApolloClient, useMutation, useQuery } from "@apollo/client/react";
import { Box, Button, Chip, Stack, Typography } from "@mui/material";
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
import { PendingActionSeverity, PendingActionStatus, PendingActionType } from "@/lib/graphql/types/__generated__/graphql";
import type { IssuerSelectionAccount } from "./components/IssuerSelection";
import { MaturityConfirmDrawer, type MaturityDrawerHolding } from "./MaturityConfirmDrawer";
import { CouponPaymentConfirmDrawer, type CouponDrawerHolding } from "./CouponPaymentConfirmDrawer";
import { INVESTMENT_HOLDINGS, INVESTMENT_PORTFOLIO_SUMMARY } from "@/lib/graphql/operations/banking/queries";

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

  const sorted = React.useMemo(() => {
    const list = data?.pendingActions ?? [];
    return [...list].sort(
      (a, b) =>
        SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity] ||
        String(a.createdAt).localeCompare(String(b.createdAt)),
    );
  }, [data?.pendingActions]);

  const titleFor = (type: PendingActionType): string => {
    switch (type) {
      case PendingActionType.InvestmentMatured:
        return t("investments.pendingActions.registerMaturity");
      case PendingActionType.CouponPaymentDue:
        return t("investments.pendingActions.registerCoupon");
      default:
        return type;
    }
  };

  const assetName = (row: (typeof sorted)[number]): string =>
    row.relatedHolding?.asset?.name ?? (row.payload as { assetName?: string })?.assetName ?? "—";

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
          {!error ? <LoadingState isLoading={loading && sorted.length === 0} minHeight={220} size={20} /> : null}
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
                            investmentAccounts.find((a) => a.id === related.financialAccountId) ?? null,
                          );
                          setCouponOpen(true);
                        }}
                      >
                        {t("investments.pendingActions.registerCoupon")}
                      </Button>
                    ) : null}
                    {row.type !== PendingActionType.InvestmentMatured && row.type !== PendingActionType.CouponPaymentDue ? (
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
        investmentAccounts={investmentAccounts}
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
