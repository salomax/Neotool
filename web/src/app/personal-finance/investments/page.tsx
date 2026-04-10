"use client";

import React from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { useQuery } from "@apollo/client/react";
import { alpha, Box, Button, CircularProgress, Container, Divider, LinearProgress, Stack, Typography, useTheme } from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import TrendingUpIcon from "@mui/icons-material/TrendingUp";
import { Page } from "@/shared/components/ui/layout";
import { SidebarLayout } from "@/shared/components/ui/layout/SidebarLayout";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../i18n";
import { usePageTitle } from "@/shared/hooks/ui/usePageTitle";
import { QueryErrorState } from "@/shared/components/ui/feedback";
import { FinancialAccountSelector } from "../finance-transactions/account/FinancialAccountSelector";
import { FINANCIAL_ACCOUNTS } from "@/lib/graphql/operations/banking/queries";
import {
  FinancialAccountsQuery,
} from "@/lib/graphql/operations/banking/queries.generated";
import { FinancialAccountType } from "@/lib/graphql/types/__generated__/graphql";
import { CreateInvestmentAccountDrawer } from "./components/CreateInvestmentAccountDrawer";
import { AssetTypeIcon } from "@/shared/components/ui/icons/AssetTypeIcon";
import { PendingActionsDrawer } from "./PendingActionsDrawer";
import { Label } from "@/shared/components/ui";
import { InvestmentSummary } from "./components/InvestmentSummary";
import { InvestmentGrid } from "./components/grid/InvestmentGrid";

export default function PersonalFinanceInvestmentsPage() {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const router = useRouter();
  const searchParams = useSearchParams();
  const benefitAccountType = "BENEFIT" as unknown as FinancialAccountType;
  const [isCreateAccountOpen, setIsCreateAccountOpen] = React.useState(false);
  const [selectedAccountId, setSelectedAccountId] = React.useState<string | null>(null);
  const [holdingSearchQuery, setHoldingSearchQuery] = React.useState("");
  const [summaryFetching, setSummaryFetching] = React.useState(false);

  usePageTitle(t("dashboard.actions.investmentsPortfolio.title"));

  const {
    data: accountsData,
    loading: accountsLoading,
    error: accountsError,
  } = useQuery<FinancialAccountsQuery>(FINANCIAL_ACCOUNTS, { errorPolicy: "all" });

  const investmentAccounts = React.useMemo(
    () =>
      (accountsData?.financialAccounts ?? []).filter(
        (account) =>
          account.type === FinancialAccountType.Investment || account.type === benefitAccountType,
      ),
    [accountsData?.financialAccounts, benefitAccountType],
  );

  const bankAccounts = React.useMemo(
    () => (accountsData?.financialAccounts ?? []).filter((a) => a.type === FinancialAccountType.Bank),
    [accountsData?.financialAccounts],
  );

  const pendingActionsOpen = searchParams.get("pendingActions") === "1";
  const closePendingActions = () => {
    const params = new URLSearchParams(searchParams.toString());
    params.delete("pendingActions");
    const qs = params.toString();
    router.replace(qs ? `/personal-finance/investments?${qs}` : "/personal-finance/investments");
  };

  const isEmpty = !accountsLoading && investmentAccounts.length === 0;

  React.useEffect(() => {
    if (isEmpty) setSummaryFetching(false);
  }, [isEmpty]);

  const sidebar = (
    <Box sx={{ height: "100%", display: "flex", flexDirection: "column" }}>
      <Box sx={{ p: 3, pb: 2 }}>
        <FinancialAccountSelector
          onNewAccount={() => setIsCreateAccountOpen(true)}
          selectedAccountId={selectedAccountId}
          onSelectAccount={setSelectedAccountId}
          accountTypes={[FinancialAccountType.Investment, benefitAccountType]}
          allAccountsLabel={t("investments.sidebar.allBrokers")}
          consolidatedViewLabel={t("investments.sidebar.consolidatedView")}
          newAccountLabel={t("investments.emptyState.primaryAction")}
        />
        <Box sx={{ position: "relative", my: 3 }}>
          <Divider />
          {summaryFetching ? (
            <Box
              aria-hidden
              sx={{
                position: "absolute",
                left: 0,
                right: 0,
                bottom: 0,
                height: 4,
                pointerEvents: "none",
                zIndex: 2,
              }}
            >
              <LinearProgress
                sx={{ height: "100%", transition: "opacity 150ms linear" }}
              />
            </Box>
          ) : null}
        </Box>
        {!isEmpty ? (
          <InvestmentSummary
            financialAccountId={selectedAccountId}
            onSummaryFetchingChange={setSummaryFetching}
          />
        ) : null}
        {!isEmpty ? <Divider sx={{ my: 3 }} /> : null}
      </Box>
    </Box>
  );

  const emptyState = (
    <SidebarLayout side="left" size="sm" fullHeight sidebar={sidebar}>
      <Page>
        <Container maxWidth="xl" sx={{ py: 4, flex: 1, display: "flex" }}>
          <Box sx={{ width: "100%", display: "flex", justifyContent: "center" }}>
            <Box
              sx={{
                width: "100%",
                maxWidth: 760,
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                textAlign: "center",
                py: { xs: 6, md: 8 },
              }}
            >
              <Box sx={{ position: "relative", mb: 4 }}>
                <Box
                  sx={{
                    width: 180,
                    height: 180,
                    borderRadius: "50%",
                    bgcolor: alpha(theme.palette.primary.main, 0.07),
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                  }}
                >
                  <Box
                    sx={{
                      width: 132,
                      height: 132,
                      borderRadius: "50%",
                      bgcolor: alpha(theme.palette.primary.main, 0.10),
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                    }}
                  >
                    <TrendingUpIcon sx={{ fontSize: 58, color: alpha(theme.palette.primary.main, 0.45) }} />
                  </Box>
                </Box>
              </Box>

              <Typography variant="h5" fontWeight={900} sx={{ mb: 1 }}>
                {t("investments.emptyState.title")}
              </Typography>
              <Typography variant="body1" color="text.secondary" sx={{ maxWidth: 640, lineHeight: 1.7, mb: 4 }}>
                {t("investments.emptyState.description")}
              </Typography>

              <Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ width: "100%", justifyContent: "center", mb: 4 }}>
                <Button
                  variant="contained"
                  size="large"
                  startIcon={<AddIcon />}
                  sx={{
                    height: 48,
                    minWidth: { xs: "100%", sm: 280 },
                    boxShadow: `0 10px 24px ${alpha(theme.palette.primary.main, 0.35)}`,
                    "&:hover": {
                      boxShadow: `0 12px 28px ${alpha(theme.palette.primary.main, 0.45)}`,
                    },
                    "&:active": {
                      boxShadow: `0 6px 16px ${alpha(theme.palette.primary.main, 0.55)}`,
                    },
                    "&.Mui-disabled": {
                      boxShadow: "none",
                    },
                  }}
                  onClick={() => setIsCreateAccountOpen(true)}
                  data-testid="create-investment-account-empty-btn"
                >
                  {t("investments.emptyState.primaryAction")}
                </Button>
              </Stack>

              <Box sx={{ width: "100%", maxWidth: 620, borderBottom: `1px solid ${alpha(theme.palette.divider, 0.7)}`, my: 3 }} />

              <Label sx={{ mb: 2 }}>
                {t("investments.trackableAssets.title")}
              </Label>

              <Stack
                direction="row"
                spacing={6}
                sx={{ justifyContent: "center", flexWrap: "wrap", rowGap: 3, color: "text.secondary" }}
              >
                {[
                  { icon: <AssetTypeIcon kind="FIXED_INCOME" sx={{ fontSize: 22 }} />, label: t("investments.trackableAssets.fixedIncome") },
                  { icon: <AssetTypeIcon kind="STOCK" sx={{ fontSize: 22 }} />, label: t("investments.trackableAssets.stocks") },
                  { icon: <AssetTypeIcon kind="FUND" sx={{ fontSize: 22 }} />, label: t("investments.trackableAssets.funds") },
                  { icon: <AssetTypeIcon kind="CRYPTO" sx={{ fontSize: 22 }} />, label: t("investments.trackableAssets.crypto") },
                  { icon: <AssetTypeIcon kind="REAL_ESTATE_FUND" sx={{ fontSize: 22 }} />, label: t("investments.trackableAssets.reits") },
                ].map((item) => (
                  <Box key={item.label} sx={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 0.75, minWidth: 96 }}>
                    <Box sx={{ opacity: 0.7 }}>{item.icon}</Box>
                    <Typography variant="caption" sx={{ opacity: 0.8 }}>
                      {item.label}
                    </Typography>
                  </Box>
                ))}
              </Stack>
            </Box>
          </Box>
        </Container>
      </Page>
    </SidebarLayout>
  );

  if (accountsLoading) {
    return (
      <SidebarLayout side="left" size="sm" fullHeight sidebar={sidebar}>
        <Page>
          <Container maxWidth="xl" sx={{ py: 4 }}>
            <Box sx={{ display: "flex", justifyContent: "center", py: 10 }}>
              <CircularProgress />
            </Box>
          </Container>
        </Page>
      </SidebarLayout>
    );
  }

  if (accountsError) {
    return (
      <SidebarLayout side="left" size="sm" fullHeight sidebar={sidebar}>
        <Page>
          <Container maxWidth="xl" sx={{ py: 4 }}>
            <QueryErrorState title={t("investments.table.error")} error={accountsError} />
          </Container>
        </Page>
      </SidebarLayout>
    );
  }

  return (
    <>
      {isEmpty ? (
        emptyState
      ) : (
        <SidebarLayout side="left" size="sm" fullHeight sidebar={sidebar}>
          <Page>
            <Container maxWidth="xl" sx={{ py: 4 }}>
              <InvestmentGrid
                selectedAccountId={selectedAccountId}
                bankAccounts={bankAccounts}
                investmentAccounts={investmentAccounts}
                searchQuery={holdingSearchQuery}
                onSelectSearchAsset={(assetName) => setHoldingSearchQuery(assetName)}
              />
            </Container>
          </Page>
        </SidebarLayout>
      )}

      <PendingActionsDrawer open={pendingActionsOpen} onClose={closePendingActions} bankAccounts={bankAccounts} />

      <CreateInvestmentAccountDrawer
        open={isCreateAccountOpen}
        onClose={() => setIsCreateAccountOpen(false)}
        onCreated={(account) => setSelectedAccountId(account.id)}
      />

    </>
  );
}
