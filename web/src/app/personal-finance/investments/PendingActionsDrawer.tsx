"use client";

import React from "react";
import { useApolloClient, useMutation, useQuery } from "@apollo/client/react";
import CheckIcon from "@mui/icons-material/Check";
import { Box, Divider, Stack } from "@mui/material";
import { useRouter } from "next/navigation";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { EmptyState, LoadingState, QueryErrorState } from "@/shared/components/ui/feedback";
import { useAuth, useToast } from "@/shared/providers";
import { useTranslation } from "@/shared/i18n";
import { extractErrorMessage } from "@/shared/utils/error";
import { personalFinanceTranslations } from "../i18n";
import { Button } from "@/shared/components/ui/primitives/Button";
import {
  DismissPendingActionDocument,
  type DismissPendingActionMutation,
  type DismissPendingActionMutationVariables,
  PendingActionsDocument,
  type PendingActionsQuery,
  type PendingActionsQueryVariables,
  PendingActionsCountDocument,
} from "@/lib/graphql/operations/banking/pendingActionOperations.generated";
import { CONFIRM_PENDING_ACTION as CONFIRM_PENDING_ACTION_DOC } from "@/lib/graphql/operations/banking/pendingActionOperations";
import { FinancialAccountType, PendingActionSeverity, PendingActionStatus, PendingActionType } from "@/lib/graphql/types/__generated__/graphql";
import type { IssuerSelectionAccount } from "./components/IssuerSelection";
import { MaturityConfirmDrawer, type MaturityDrawerHolding } from "./MaturityConfirmDrawer";
import { CouponPaymentConfirmDrawer, type CouponDrawerHolding } from "./CouponPaymentConfirmDrawer";
import { INVESTMENT_HOLDINGS, INVESTMENT_PORTFOLIO_SUMMARY } from "@/lib/graphql/operations/banking/queries";
import { FINANCIAL_ACCOUNTS } from "@/lib/graphql/operations/banking/queries";
import type { FinancialAccountsQuery } from "@/lib/graphql/operations/banking/queries.generated";
import { PendingActionRow } from "./components/pending-actions/PendingActionRow";
import type { PendingActionRowData } from "./components/pending-actions/pendingActionPresenters";

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
  const toast = useToast();

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
  const [confirmAction] = useMutation(CONFIRM_PENDING_ACTION_DOC);

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

  const refresh = React.useCallback(async () => {
    await refetchActions();
    await client.refetchQueries({
      include: [PendingActionsCountDocument, INVESTMENT_HOLDINGS, INVESTMENT_PORTFOLIO_SUMMARY],
    });
  }, [client, refetchActions]);

  const [bulkDismissLoading, setBulkDismissLoading] = React.useState(false);

  const handleDismiss = async (id: string) => {
    await dismissAction({ variables: { id } });
    await refresh();
  };

  const handleConfirm = async (id: string) => {
    try {
      await confirmAction({ variables: { id } });
      await refresh();
    } catch (error) {
      toast.error(extractErrorMessage(error, "Unable to confirm pending action"));
    }
  };

  const handleDismissAll = async () => {
    if (sorted.length === 0 || dismissLoading || bulkDismissLoading) return;
    const ids = sorted.map((row) => row.id);
    setBulkDismissLoading(true);
    try {
      await Promise.all(ids.map((id) => dismissAction({ variables: { id } })));
    } catch {
      // Partial failures are acceptable; finally still refreshes the list.
    } finally {
      setBulkDismissLoading(false);
      await refresh();
    }
  };

  const openMaturityDrawer = (row: PendingActionRowData) => {
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
  };

  const openCouponDrawer = (row: PendingActionRowData) => {
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
  };

  const primaryActionFor = (
    row: PendingActionRowData,
  ): {
    label?: string;
    testId?: string;
    onPrimaryAction?: () => void;
  } => {
    const type = row.type as PendingActionType;

    if (type === PendingActionType.InvestmentMatured && row.relatedHolding) {
      return {
        label: t("investments.pendingActions.registerMaturity"),
        testId: "pending-action-register-maturity",
        onPrimaryAction: () => openMaturityDrawer(row),
      };
    }

    if (type === PendingActionType.CouponPaymentDue && row.relatedHolding) {
      return {
        label: t("investments.pendingActions.registerCoupon"),
        testId: "pending-action-register-coupon",
        onPrimaryAction: () => openCouponDrawer(row),
      };
    }

    if (type === PendingActionType.RecurringPaymentDue) {
      return {
        label: t("recurringPayments.pendingActions.viewAll"),
        onPrimaryAction: () => {
          onClose();
          router.push("/personal-finance/recurring-payments");
        },
      };
    }

    const financialAccountId = (row.payload as { financialAccountId?: string })?.financialAccountId;
    if (type === PendingActionType.BankAccountNegativeBalance && financialAccountId) {
      return {
        label: t("investments.pendingActions.viewAccount"),
        onPrimaryAction: () => {
          onClose();
          router.push(`/personal-finance/finance-transactions?accountId=${financialAccountId}`);
        },
      };
    }

    if (type === PendingActionType.AgentWriteConfirmation) {
      return {
        label: t("actions.confirm"),
        testId: "pending-action-confirm",
        onPrimaryAction: () => void handleConfirm(row.id),
      };
    }

    return {};
  };

  return (
    <>
      <Drawer open={open} onClose={onClose} anchor="right" size="md">
        <Drawer.Header
          title={t("investments.pendingActions.panelTitle")}
          closeButtonAriaLabel={t("actions.close")}
        />
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
            <Stack divider={<Divider />} spacing={0}>
              {sorted.map((row) => {
                const primary = primaryActionFor(row);
                return (
                  <PendingActionRow
                    key={row.id}
                    row={row}
                    dismissLoading={dismissLoading || bulkDismissLoading}
                    onDismiss={() => void handleDismiss(row.id)}
                    primaryActionLabel={primary.label}
                    primaryActionTestId={primary.testId}
                    onPrimaryAction={primary.onPrimaryAction}
                  />
                );
              })}
            </Stack>
          ) : null}
        </Drawer.Body>
        <Drawer.Footer>
          <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2 }}>
            <Button
              type="button"
              variant="outlined"
              startIcon={<CheckIcon fontSize="small" />}
              disabled={sorted.length === 0 || dismissLoading}
              loading={bulkDismissLoading}
              onClick={() => void handleDismissAll()}
            >
              {t("investments.pendingActions.dismissAll")}
            </Button>
            <Box sx={{ display: "flex", justifyContent: "flex-end" }}>
              <Drawer.CancelButton onClick={onClose}>{t("actions.close")}</Drawer.CancelButton>
            </Box>
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
