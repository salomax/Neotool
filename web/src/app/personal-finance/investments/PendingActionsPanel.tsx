"use client";

import React from "react";
import { useApolloClient, useMutation, useQuery } from "@apollo/client/react";
import { Box, Button, Chip, Stack, Typography, alpha, useTheme } from "@mui/material";
import {
  GET_PENDING_ACTIONS,
  DISMISS_PENDING_ACTION,
} from "@/lib/graphql/operations/banking/pendingActionOperations";
import {
  PendingActionsQuery,
  PendingActionsQueryVariables,
  DismissPendingActionMutation,
  DismissPendingActionMutationVariables,
} from "@/lib/graphql/operations/banking/pendingActionOperations.generated";
import type { FinancialAccountsQuery } from "@/lib/graphql/operations/banking/queries.generated";
import { PendingActionSeverity, PendingActionStatus, PendingActionType } from "@/lib/graphql/types/__generated__/graphql";
import { useAuth } from "@/shared/providers";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../i18n";
import { MaturityConfirmDrawer, type MaturityDrawerHolding } from "./MaturityConfirmDrawer";
import { ALL_INVESTMENT_HOLDINGS } from "@/lib/graphql/operations/banking/queries";

const SEVERITY_ORDER: Record<PendingActionSeverity, number> = {
  [PendingActionSeverity.ActionRequired]: 0,
  [PendingActionSeverity.Warning]: 1,
  [PendingActionSeverity.Info]: 2,
};

type BankAccountRow = NonNullable<FinancialAccountsQuery["financialAccounts"]>[number];

interface PendingActionsPanelProps {
  bankAccounts: BankAccountRow[];
}

export function PendingActionsPanel({ bankAccounts }: PendingActionsPanelProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const client = useApolloClient();
  const { isAuthenticated } = useAuth();

  const { data, refetch } = useQuery<PendingActionsQuery, PendingActionsQueryVariables>(
    GET_PENDING_ACTIONS,
    {
      variables: { status: PendingActionStatus.Pending },
      skip: !isAuthenticated,
      fetchPolicy: "cache-and-network",
    },
  );

  const [dismissAction] = useMutation<DismissPendingActionMutation, DismissPendingActionMutationVariables>(
    DISMISS_PENDING_ACTION,
  );

  const [maturityOpen, setMaturityOpen] = React.useState(false);
  const [maturityHolding, setMaturityHolding] = React.useState<MaturityDrawerHolding | null>(null);

  const sorted = React.useMemo(() => {
    const list = data?.pendingActions ?? [];
    return [...list].sort(
      (a, b) => SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity] || String(a.createdAt).localeCompare(String(b.createdAt)),
    );
  }, [data?.pendingActions]);

  if (!isAuthenticated || sorted.length === 0) {
    return null;
  }

  const titleFor = (type: PendingActionType): string => {
    switch (type) {
      case PendingActionType.InvestmentMatured:
        return t("investments.pendingActions.registerMaturity");
      case PendingActionType.Informational:
        return "Informational";
      default:
        return type;
    }
  };

  const assetName = (row: (typeof sorted)[number]): string =>
    row.relatedHolding?.asset?.name ?? (row.payload as { assetName?: string })?.assetName ?? "—";

  return (
    <>
      <Box
        sx={{
          mb: 2,
          p: 2,
          borderRadius: `${theme.custom.radius.button}px`,
          border: `1px solid ${alpha(theme.palette.divider, 0.7)}`,
          bgcolor: "background.paper",
        }}
      >
        <Typography variant="subtitle2" sx={{ mb: 1.5, letterSpacing: "0.08em", color: "text.secondary" }}>
          {t("investments.pendingActions.panelTitle")}
        </Typography>
        <Stack spacing={1.5}>
          {sorted.map((row) => (
            <Box
              key={row.id}
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
                    onClick={() => {
                      setMaturityHolding(row.relatedHolding as MaturityDrawerHolding);
                      setMaturityOpen(true);
                    }}
                  >
                    {t("investments.pendingActions.registerMaturity")}
                  </Button>
                ) : null}
                {row.type !== PendingActionType.InvestmentMatured ? (
                  <Button
                    size="small"
                    onClick={async () => {
                      await dismissAction({ variables: { id: row.id as string } });
                      void refetch();
                    }}
                  >
                    {t("investments.pendingActions.markSeen")}
                  </Button>
                ) : null}
              </Box>
            </Box>
          ))}
        </Stack>
      </Box>

      <MaturityConfirmDrawer
        open={maturityOpen}
        onClose={() => {
          setMaturityOpen(false);
          setMaturityHolding(null);
        }}
        holding={maturityHolding}
        bankAccounts={bankAccounts}
        onSuccess={() => {
          void refetch();
          void client.refetchQueries({ include: [ALL_INVESTMENT_HOLDINGS] });
        }}
      />
    </>
  );
}
