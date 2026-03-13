"use client";

import React, { useState } from "react";
import { useQuery } from "@apollo/client/react";
import { alpha, Box, Button, CircularProgress, Container, Stack, Typography, useTheme } from "@mui/material";
import AccountBalanceIcon from "@mui/icons-material/AccountBalance";
import AddIcon from "@mui/icons-material/Add";
import AddchartIcon from "@mui/icons-material/Addchart";
import PlaylistAddIcon from "@mui/icons-material/PlaylistAdd";
import TrendingUpIcon from "@mui/icons-material/TrendingUp";
import CurrencyBitcoinIcon from "@mui/icons-material/CurrencyBitcoin";
import ApartmentIcon from "@mui/icons-material/Apartment";
import { Page } from "@/shared/components/ui/layout";
import { SidebarLayout } from "@/shared/components/ui/layout/SidebarLayout";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../i18n";
import { usePageTitle } from "@/shared/hooks/ui/usePageTitle";
import { QueryErrorState } from "@/shared/components/ui/feedback";
import { FINANCIAL_ACCOUNTS } from "@/lib/graphql/operations/banking/queries";
import { FinancialAccount, FinancialAccountType } from "@/lib/graphql/types/__generated__/graphql";
import { CreateInvestmentAccountDrawer } from "./components/CreateInvestmentAccountDrawer";
import { InvestmentTable } from "./components/InvestmentTable";
import { RecordFixedIncomeDrawer } from "./components/RecordFixedIncomeDrawer";
import { FinancialAccountSelector } from "../finance-transactions/components/FinancialAccountSelector";

export default function PersonalFinanceInvestmentsPage() {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const [createAccountOpen, setCreateAccountOpen] = useState(false);
  const [recordHoldingOpen, setRecordHoldingOpen] = useState(false);
  const [selectedInvestmentAccountId, setSelectedInvestmentAccountId] = useState<string | null>(null);

  usePageTitle(t("dashboard.actions.investmentsPortfolio.title"));

  const {
    data,
    loading,
    error: financialAccountsError,
    refetch: refetchFinancialAccounts,
  } = useQuery<{ financialAccounts: FinancialAccount[] }>(FINANCIAL_ACCOUNTS, {
    errorPolicy: "all",
  });

  const investmentAccounts = React.useMemo(
    () => (data?.financialAccounts || []).filter((account) => account.type === FinancialAccountType.Investment),
    [data?.financialAccounts],
  );

  const hasInvestmentAccounts = investmentAccounts.length > 0;

  React.useEffect(() => {
    if (
      selectedInvestmentAccountId &&
      !investmentAccounts.some((account) => account.id === selectedInvestmentAccountId)
    ) {
      setSelectedInvestmentAccountId(null);
    }
  }, [investmentAccounts, selectedInvestmentAccountId]);

  if (loading) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", alignItems: "center", height: "100vh" }}>
        <CircularProgress />
      </Box>
    );
  }

  if (financialAccountsError) {
    return (
      <Page>
        <Container maxWidth="md" sx={{ py: 6 }}>
          <QueryErrorState error={financialAccountsError} onRetry={() => refetchFinancialAccounts()} />
        </Container>
      </Page>
    );
  }

  return (
    <SidebarLayout
      side="left"
      size="sm"
      fullHeight
      sidebar={
        <Box sx={{ height: "100%", display: "flex", flexDirection: "column" }}>
          <Box sx={{ p: 3, pb: 2 }}>
            <FinancialAccountSelector
              onNewAccount={() => setCreateAccountOpen(true)}
              selectedAccountId={selectedInvestmentAccountId}
              onSelectAccount={setSelectedInvestmentAccountId}
              accountTypes={[FinancialAccountType.Investment]}
              allAccountsLabel={t("investments.sidebar.allBrokers")}
              consolidatedViewLabel={t("investments.sidebar.consolidatedView")}
              newAccountLabel={t("investments.emptyState.primaryAction")}
            />

            <Box
              sx={{
                mt: 2,
                p: 2,
                borderRadius: `${theme.custom.radius.button}px`,
                border: `1px solid ${alpha(theme.palette.divider, 0.7)}`,
                bgcolor: "background.paper",
                boxShadow: theme.shadows[1],
              }}
            >
              <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", mb: 1 }}>
                <Typography variant="caption" color="text.secondary" sx={{ letterSpacing: "0.12em" }}>
                  {t("investments.sidebar.summaryTitle")}
                </Typography>
                <Box
                  sx={{
                    width: 18,
                    height: 18,
                    borderRadius: "50%",
                    bgcolor: alpha(theme.palette.text.primary, 0.08),
                  }}
                />
              </Box>

              <Typography variant="caption" color="text.secondary">
                {t("investments.sidebar.totalLabel")}
              </Typography>
              <Typography variant="h6" fontWeight={900} sx={{ mb: 1 }}>
                R$ 0,00
              </Typography>

              <Stack spacing={1.25} sx={{ mt: 2 }}>
                {[
                  { label: t("investments.sidebar.metrics.contributions"), color: theme.palette.primary.main },
                  { label: t("investments.sidebar.metrics.returns"), color: theme.palette.success.main },
                ].map((item) => (
                  <Box key={item.label} sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 2 }}>
                    <Box sx={{ display: "flex", alignItems: "center", gap: 1.25, minWidth: 0 }}>
                      <Box
                        sx={{
                          width: 8,
                          height: 8,
                          borderRadius: "50%",
                          bgcolor: alpha(item.color, 0.9),
                          flex: "0 0 auto",
                        }}
                      />
                      <Typography variant="body2" color="text.secondary" noWrap>
                        {item.label}
                      </Typography>
                    </Box>
                    <Typography variant="body2" fontWeight={700} noWrap>
                      R$ 0,00 <Box component="span" sx={{ color: "text.secondary", fontWeight: 600 }}>(0%)</Box>
                    </Typography>
                  </Box>
                ))}
              </Stack>

              <Box sx={{ mt: 2 }}>
                <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
                  <Typography variant="caption" color="text.secondary" sx={{ letterSpacing: "0.12em" }}>
                    {t("investments.sidebar.allocation")}
                  </Typography>
                  <Typography variant="caption" color="text.secondary" fontWeight={700}>
                    0%
                  </Typography>
                </Box>
                <Box
                  sx={{
                    mt: 1,
                    height: 6,
                    borderRadius: 999,
                    bgcolor: alpha(theme.palette.primary.main, 0.12),
                    overflow: "hidden",
                  }}
                >
                  <Box sx={{ width: "0%", height: "100%", bgcolor: "primary.main" }} />
                </Box>
              </Box>
            </Box>
          </Box>
        </Box>
      }
    >
      <Page>
        {hasInvestmentAccounts ? (
          <Container maxWidth="xl" sx={{ py: 4 }}>
            <Stack direction="row" spacing={2} sx={{ width: "100%", mb: 2 }} justifyContent="flex-end" flexWrap="wrap">
              <Button
                sx={{
                  boxShadow: `0 10px 24px ${alpha(theme.palette.primary.main, 0.28)}`,
                  "&:hover": {
                    boxShadow: `0 12px 28px ${alpha(theme.palette.primary.main, 0.36)}`,
                  },
                }}
                variant="contained"
                startIcon={<PlaylistAddIcon />}
                onClick={() => setRecordHoldingOpen(true)}
                data-testid="record-holding-btn"
              >
                {t("investments.actions.recordHolding")}
              </Button>
            </Stack>

            <InvestmentTable
              selectedAccountId={selectedInvestmentAccountId}
              onRecordHolding={() => setRecordHoldingOpen(true)}
            />
          </Container>
        ) : (
          <Container maxWidth="xl" sx={{ py: 4, flex: 1, display: "flex" }}>
            <Box sx={{ width: "100%", display: "flex", justifyContent: "center" }}>
              <Box
                sx={{
                  width: "100%",
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
                    startIcon={<AddchartIcon />}
                    onClick={() => setCreateAccountOpen(true)}
                    data-testid="create-investment-account-btn"
                    sx={{ height: 48, minWidth: { xs: "100%", sm: 280 }, boxShadow: theme.shadows[4] }}
                  >
                    {t("investments.emptyState.primaryAction")}
                  </Button>
                  <Button
                    variant="outlined"
                    size="large"
                    startIcon={<PlaylistAddIcon />}
                    onClick={() => setCreateAccountOpen(true)}
                    sx={{ height: 48, minWidth: { xs: "100%", sm: 260 } }}
                  >
                    {t("investments.emptyState.secondaryAction")}
                  </Button>
                </Stack>

                <Box sx={{ width: "100%", maxWidth: 620, borderBottom: `1px solid ${alpha(theme.palette.divider, 0.7)}`, my: 3 }} />

                <Typography variant="overline" color="text.secondary" sx={{ letterSpacing: "0.18em", mb: 2 }}>
                  {t("investments.trackableAssets.title")}
                </Typography>

                <Stack
                  direction="row"
                  spacing={6}
                  sx={{ justifyContent: "center", flexWrap: "wrap", rowGap: 3, color: "text.secondary" }}
                >
                  {[
                    { icon: <CurrencyBitcoinIcon sx={{ fontSize: 22 }} />, label: t("investments.trackableAssets.crypto") },
                    { icon: <ApartmentIcon sx={{ fontSize: 22 }} />, label: t("investments.trackableAssets.reits") },
                    { icon: <TrendingUpIcon sx={{ fontSize: 22 }} />, label: t("investments.trackableAssets.stocks") },
                    { icon: <AccountBalanceIcon sx={{ fontSize: 22 }} />, label: t("investments.trackableAssets.fixedIncome") },
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
        )}

        <CreateInvestmentAccountDrawer
          open={createAccountOpen}
          onClose={() => setCreateAccountOpen(false)}
          onCreated={(account) => {
            setSelectedInvestmentAccountId(account.id);
            refetchFinancialAccounts();
          }}
        />

        <RecordFixedIncomeDrawer
          open={recordHoldingOpen}
          onClose={() => setRecordHoldingOpen(false)}
          financialAccountId={selectedInvestmentAccountId}
          financialAccounts={investmentAccounts}
          onRecorded={() => {
            setRecordHoldingOpen(false);
          }}
        />
      </Page>
    </SidebarLayout>
  );
}
