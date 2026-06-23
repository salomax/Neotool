'use client';

import { Box, Divider } from '@mui/material';
import type { FinancialAccountsQuery } from '@/lib/graphql/operations/banking/queries.generated';
import { FinancialAccountType, CreditDebitType } from '@/lib/graphql/types/__generated__/graphql';
import { FinancialAccountSelector } from './account/FinancialAccountSelector';
import { MonthSummary, CategoryFilterSelection, MonthSummaryData } from './summary/MonthSummary';
import { BillSummaryHeader, BillSummaryData } from './bill/BillSummaryHeader';
import { DisplayMode } from './account/useAccountDisplayMode';

const ACCOUNT_SELECTOR_TYPES: FinancialAccountType[] = [
  FinancialAccountType.Bank,
  FinancialAccountType.Credit,
  FinancialAccountType.Payment,
];

interface AccountSelectorState {
  selectedAccountId: string | null;
  onSelect: (id: string | null) => void;
  onNewAccount: () => void;
  onEditAccount: (account: NonNullable<FinancialAccountsQuery['financialAccounts']>[number]) => void;
}

interface CalendarSummaryState {
  data: MonthSummaryData | undefined;
  loading: boolean;
  error?: unknown;
  onRetry: () => void;
  isConsolidated: boolean;
  categoryFilter: {
    value: CategoryFilterSelection | null;
    onToggle: (selection: CategoryFilterSelection) => void;
  };
  tagFilter: {
    value: string | null;
    onToggle: (tag: string) => void;
  };
  creditDebitFilter: {
    value: CreditDebitType | null;
    onToggle: (type: CreditDebitType) => void;
  };
  transferFilter: {
    value: boolean | null;
    onToggle: () => void;
  };
}

interface BillSummaryState {
  bill: BillSummaryData | undefined;
  loading: boolean;
  onPayClick: () => void;
}

interface FinanceTransactionsSidebarProps {
  accountSelector: AccountSelectorState;
  displayModeKind: DisplayMode['kind'];
  calendarSummary: CalendarSummaryState;
  billSummary: BillSummaryState;
}

export function FinanceTransactionsSidebar({
  accountSelector,
  displayModeKind,
  calendarSummary,
  billSummary,
}: FinanceTransactionsSidebarProps) {
  return (
    <Box sx={{ height: { xs: 'auto', md: '100%' }, display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ px: { xs: 0, md: 3 } }}>
        <Box sx={{ px: { xs: 3, md: 0 }, py: 3 }}>
          <FinancialAccountSelector
            onNewAccount={accountSelector.onNewAccount}
            onEditAccount={accountSelector.onEditAccount}
            selectedAccountId={accountSelector.selectedAccountId}
            onSelectAccount={accountSelector.onSelect}
            accountTypes={ACCOUNT_SELECTOR_TYPES}
          />
        </Box>
        <Divider />
        <Box sx={{ px: { xs: 3, md: 0 }, py: 3 }}>
          {displayModeKind === 'calendar' ? (
            <MonthSummary
              summary={calendarSummary.data}
              loading={calendarSummary.loading}
              error={calendarSummary.error}
              onRetry={calendarSummary.onRetry}
              isConsolidated={calendarSummary.isConsolidated}
              expensesByCategory={calendarSummary.data?.expensesByCategory ?? []}
              incomeByCategory={calendarSummary.data?.incomeByCategory ?? []}
              tagsSummary={calendarSummary.data?.tagsSummary ?? []}
              onCategoryClick={calendarSummary.categoryFilter.onToggle}
              activeCategoryFilter={calendarSummary.categoryFilter.value}
              onTagClick={calendarSummary.tagFilter.onToggle}
              activeTagFilter={calendarSummary.tagFilter.value}
              onCreditDebitTypeClick={calendarSummary.creditDebitFilter.onToggle}
              activeCreditDebitTypeFilter={calendarSummary.creditDebitFilter.value}
              onTransferClick={calendarSummary.transferFilter.onToggle}
              activeTransferFilter={calendarSummary.transferFilter.value === true}
            />
          ) : (
            <BillSummaryHeader
              bill={billSummary.bill}
              loading={billSummary.loading}
              onPayClick={billSummary.onPayClick}
            />
          )}
        </Box>
        <Divider />
      </Box>
    </Box>
  );
}
