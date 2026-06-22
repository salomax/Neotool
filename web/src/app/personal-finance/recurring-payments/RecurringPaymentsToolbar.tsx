'use client';

import AddIcon from '@mui/icons-material/Add';
import { Box, Button, Stack } from '@mui/material';
import { ExpandableSearchInput } from '@/shared/components/ui/forms/ExpandableSearchInput';
import type { RecurringFrequency, RecurringPaymentStatus } from '@/lib/graphql/types/__generated__/graphql';
import { RecurringPaymentsMultiSelectFilter } from './RecurringPaymentsMultiSelectFilter';

export interface RecurringPaymentsToolbarProps {
  searchQuery: string;
  onSearchQueryChange: (q: string) => void;
  onNewRecurring: () => void;
  searchPlaceholder: string;
  newButtonLabel: string;
  clearSearchAriaLabel: string;
  statusFilter: RecurringPaymentStatus[];
  onStatusFilterChange: (next: RecurringPaymentStatus[]) => void;
  frequencyFilter: RecurringFrequency[];
  onFrequencyFilterChange: (next: RecurringFrequency[]) => void;
  statusOptions: { value: RecurringPaymentStatus; label: string }[];
  frequencyOptions: { value: RecurringFrequency; label: string }[];
  filtersAllLabel: string;
  filtersSelectedCount: (count: number) => string;
  filtersAriaStatus: string;
  filtersAriaFrequency: string;
  filtersStatusLabel: string;
  filtersFrequencyLabel: string;
}

export function RecurringPaymentsToolbar({
  searchQuery,
  onSearchQueryChange,
  onNewRecurring,
  searchPlaceholder,
  newButtonLabel,
  clearSearchAriaLabel,
  statusFilter,
  onStatusFilterChange,
  frequencyFilter,
  onFrequencyFilterChange,
  statusOptions,
  frequencyOptions,
  filtersAllLabel,
  filtersSelectedCount,
  filtersAriaStatus,
  filtersAriaFrequency,
  filtersStatusLabel,
  filtersFrequencyLabel,
}: RecurringPaymentsToolbarProps) {
  return (
    <Stack
      direction={{ xs: 'column', md: 'row' }}
      spacing={2}
      sx={{ width: '100%', mb: 2, mt: { xs: 0, md: -1.5 }, minHeight: 50 }}
      alignItems={{ xs: 'stretch', md: 'flex-end' }}
      flexWrap="wrap"
      useFlexGap
    >
      <Box
        sx={{
          flex: 1,
          minWidth: 0, // Allow filter chips to wrap on mobile instead of forcing horizontal overflow.
          display: 'flex',
          flexWrap: 'wrap',
          gap: 1.5,
          alignItems: 'flex-end',
        }}
      >
        <RecurringPaymentsMultiSelectFilter<RecurringPaymentStatus>
          fieldLabel={filtersStatusLabel}
          options={statusOptions}
          value={statusFilter}
          onChange={onStatusFilterChange}
          allLabel={filtersAllLabel}
          selectedCountLabel={filtersSelectedCount}
          ariaLabel={filtersAriaStatus}
        />
        <RecurringPaymentsMultiSelectFilter<RecurringFrequency>
          fieldLabel={filtersFrequencyLabel}
          options={frequencyOptions}
          value={frequencyFilter}
          onChange={onFrequencyFilterChange}
          allLabel={filtersAllLabel}
          selectedCountLabel={filtersSelectedCount}
          ariaLabel={filtersAriaFrequency}
        />
      </Box>
      <Box
        sx={{
          flex: '0 0 auto',
          display: 'flex',
          alignItems: 'flex-end',
          gap: 1.5,
          width: { xs: '100%', md: 'auto' },
          flexWrap: { xs: 'wrap', md: 'nowrap' },
        }}
      >
        <ExpandableSearchInput
          searchQuery={searchQuery}
          onSearchQueryChange={onSearchQueryChange}
          placeholder={searchPlaceholder}
          ariaClearLabel={clearSearchAriaLabel}
        />
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={onNewRecurring}
          sx={{
            flexShrink: 0,
            minHeight: 44,
            width: { xs: '100%', sm: 'auto' },
          }}
        >
          {newButtonLabel}
        </Button>
      </Box>
    </Stack>
  );
}
