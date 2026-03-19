"use client";

import React from "react";
import { useQuery } from "@apollo/client/react";
import {
  alpha,
  Box,
  Button,
  CircularProgress,
  Container,
  Divider,
  Stack,
  Typography,
  useTheme,
} from "@mui/material";
import AccountBalanceIcon from "@mui/icons-material/AccountBalance";
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
import { FinancialAccountSelector } from "../finance-transactions/components/FinancialAccountSelector";
import { FINANCIAL_ACCOUNTS } from "@/lib/graphql/operations/banking/queries";
import { FinancialAccountsQuery } from "@/lib/graphql/operations/banking/queries.generated";
import { FinancialAccount, FinancialAccountType } from "@/lib/graphql/types/__generated__/graphql";
import { CreateInvestmentAccountDrawer } from "./components/CreateInvestmentAccountDrawer";
import { InvestmentTable } from "./components/InvestmentTable";
import { RecordFixedIncomeDrawer } from "./components/RecordFixedIncomeDrawer";
import { RecordFundHoldingDrawer } from "./components/RecordFundHoldingDrawer";

export default function PersonalFinanceInvestmentsPage() {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const [isCreateAccountOpen, setIsCreateAccountOpen] = React.useState(false);
  const [isRecordFixedIncomeOpen, setIsRecordFixedIncomeOpen] = React.useState(false);
  const [isRecordFundOpen, setIsRecordFundOpen] = React.useState(false);
  const [selectedAccountId, setSelectedAccountId] = React.useState<string | null>(null);

  usePageTitle(t("dashboard.actions.investmentsPortfolio.title"));

  const {
    data: accountsData,
    loading: accountsLoading,
    error: accountsError,
  } = useQuery<FinancialAccountsQuery>(FINANCIAL_ACCOUNTS, { errorPolicy: "all" });

  const investmentAccounts = React.useMemo<FinancialAccount[]>(
    () =>
      (accountsData?.financialAccounts ?? []).filter(
        (account) => account.type === FinancialAccountType.Investment,
      ) as FinancialAccount[],
    [accountsData?.financialAccounts],
  );

  const isEmpty = !accountsLoading && investmentAccounts.length === 0;

  const sidebar = (
    <Box sx={{ height: "100%", display: "flex", flexDirection: "column" }}>
      <Box sx={{ p: 3, pb: 2 }}>
        <FinancialAccountSelector
          onNewAccount={() => setIsCreateAccountOpen(true)}
          selectedAccountId={selectedAccountId}
          onSelectAccount={setSelectedAccountId}
          accountTypes={[FinancialAccountType.Investment]}
          allAccountsLabel={t("investments.sidebar.allBrokers")}
          consolidatedViewLabel={t("investments.sidebar.consolidatedView")}
          newAccountLabel={t("investments.emptyState.primaryAction")}
        />
        <Divider sx={{ my: 3 }} />
        <Box
          sx={{
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
                  startIcon={<AddchartIcon />}
                  sx={{ height: 48, minWidth: { xs: "100%", sm: 280 }, boxShadow: theme.shadows[4] }}
                  onClick={() => setIsCreateAccountOpen(true)}
                  data-testid="create-investment-account-empty-btn"
                >
                  {t("investments.emptyState.primaryAction")}
                </Button>
                <Button
                  variant="outlined"
                  size="large"
                  startIcon={<PlaylistAddIcon />}
                  sx={{ height: 48, minWidth: { xs: "100%", sm: 260 } }}
                  onClick={() => setIsRecordFixedIncomeOpen(true)}
                  data-testid="record-holding-empty-btn"
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
              <Box sx={{ display: "flex", justifyContent: "flex-end", mb: 2 }}>
                <Button
                  variant="contained"
                  startIcon={<PlaylistAddIcon />}
                  onClick={() => setIsRecordFixedIncomeOpen(true)}
                  data-testid="record-holding-top-btn"
                >
                  {t("investments.actions.recordHolding")}
                </Button>
              </Box>
              <InvestmentTable
                selectedAccountId={selectedAccountId}
                onRecordHolding={() => setIsRecordFixedIncomeOpen(true)}
                onRecordFund={() => setIsRecordFundOpen(true)}
              />
            </Container>
          </Page>
        </SidebarLayout>
      )}

      <CreateInvestmentAccountDrawer
        open={isCreateAccountOpen}
        onClose={() => setIsCreateAccountOpen(false)}
        onCreated={(account) => setSelectedAccountId(account.id)}
      />

      <RecordFixedIncomeDrawer
        open={isRecordFixedIncomeOpen}
        onClose={() => setIsRecordFixedIncomeOpen(false)}
        financialAccountId={selectedAccountId}
        financialAccounts={investmentAccounts}
      />

      <RecordFundHoldingDrawer
        open={isRecordFundOpen}
        onClose={() => setIsRecordFundOpen(false)}
        financialAccountId={selectedAccountId}
        financialAccounts={investmentAccounts}
      />
    </>
  );
}
