'use client';

import { Box, Divider } from '@mui/material';
import { FinancialAccount, FinancialAccountType, CreditDebitType } from '@/lib/graphql/types/__generated__/graphql';
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
  onEditAccount: (account: FinancialAccount) => void;
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
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ p: 3, pb: 2 }}>
        <FinancialAccountSelector
          onNewAccount={accountSelector.onNewAccount}
          onEditAccount={accountSelector.onEditAccount}
          selectedAccountId={accountSelector.selectedAccountId}
          onSelectAccount={accountSelector.onSelect}
          accountTypes={ACCOUNT_SELECTOR_TYPES}
        />
        <Divider sx={{ my: 3 }} />
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
          />
        ) : (
          <BillSummaryHeader
            bill={billSummary.bill}
            loading={billSummary.loading}
            onPayClick={billSummary.onPayClick}
          />
        )}
        <Divider sx={{ my: 3 }} />
      </Box>
    </Box>
  );
}
