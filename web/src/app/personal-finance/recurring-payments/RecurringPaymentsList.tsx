'use client';

import React, { useMemo, useState } from 'react';
import { useMutation, useQuery } from '@apollo/client/react';
import {
  Box,
  Container,
  Typography,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { ConfirmationDialog, LoadingState, QueryErrorState } from '@/shared/components/ui/feedback';
import { useDebounce } from '@/shared/hooks/ui';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import {
  CONFIRM_RECURRING_PAYMENT,
  DELETE_RECURRING_PAYMENT,
  PAUSE_RECURRING_PAYMENT,
  RECURRING_PAYMENTS,
  RESUME_RECURRING_PAYMENT,
} from '@/lib/graphql/operations/banking/recurringPaymentOperations';
import { RecurringFrequency, RecurringPaymentStatus } from '@/lib/graphql/types/__generated__/graphql';
import { formatDateOnly } from '@/shared/utils/date/dateOnly';
import { SidebarLayout } from '@/shared/components/ui/layout/SidebarLayout';
import { Page } from '@/shared/components/ui/layout';
import { useScopedToggleFilter } from '../finance-transactions/hooks/useScopedToggleFilter';
import { RecurringPaymentForm } from './RecurringPaymentForm';
import type { RecurringPaymentRecord } from './RecurringPaymentForm';
import { RecurringPaymentsActiveFilters } from './RecurringPaymentsActiveFilters';
import { RecurringPaymentsToolbar } from './RecurringPaymentsToolbar';
import { RecurringPaymentsSummaryPanel } from './RecurringPaymentsSummaryPanel';
import { RecurringPaymentsTable } from './RecurringPaymentsTable';
import { ConfirmPaymentDialog } from './ConfirmPaymentDialog';
import {
  computeRecurringSummaryMetrics,
  matchesRecurringCategoryFilter,
  matchesRecurringDueBucketFilter,
  matchesRecurringFrequencyFilter,
  type RecurringCategoryFilterSelection,
  type RecurringDueBucket,
} from './recurringPaymentRowModel';

type RecurringPaymentsData = { recurringPayments: RecurringPaymentRecord[] };

export function RecurringPaymentsList() {
  const { t } = useTranslation(personalFinanceTranslations);

  const [editing, setEditing] = useState<RecurringPaymentRecord | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [confirmPaymentRow, setConfirmPaymentRow] = useState<RecurringPaymentRecord | null>(null);
  const [rowToDelete, setRowToDelete] = useState<RecurringPaymentRecord | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const debouncedSearch = useDebounce(searchQuery.trim(), 300);
  const [statusFilter, setStatusFilter] = useState<RecurringPaymentStatus[]>([RecurringPaymentStatus.Active]);
  const [frequencyFilter, setFrequencyFilter] = useState<RecurringFrequency[]>([]);
  const summaryCategoryFilter = useScopedToggleFilter<RecurringCategoryFilterSelection>(
    'recurring-payments',
    (a, b) => a.categoryId === b.categoryId && a.uncategorized === b.uncategorized,
  );
  const summaryFrequencyFilter = useScopedToggleFilter<RecurringFrequency>('recurring-payments');
  const summaryDueBucketFilter = useScopedToggleFilter<RecurringDueBucket>('recurring-payments');

  const {
    data,
    loading,
    error,
    refetch,
  } = useQuery<RecurringPaymentsData>(RECURRING_PAYMENTS, {
    variables: { financialAccountId: null },
    fetchPolicy: 'cache-and-network',
  });

  const [pauseRecurring] = useMutation(PAUSE_RECURRING_PAYMENT);
  const [resumeRecurring] = useMutation(RESUME_RECURRING_PAYMENT);
  const [deleteRecurring, { loading: deleting }] = useMutation(DELETE_RECURRING_PAYMENT);
  const [confirmPayment] = useMutation(CONFIRM_RECURRING_PAYMENT);
  const [confirmingPayment, setConfirmingPayment] = useState(false);

  const rows = useMemo(() => data?.recurringPayments ?? [], [data?.recurringPayments]);

  const summaryRows = useMemo(() => {
    let list = rows;
    if (debouncedSearch) {
      const q = debouncedSearch.toLowerCase();
      list = list.filter((r) => r.name.toLowerCase().includes(q) || (r.merchant?.name?.toLowerCase().includes(q) ?? false));
    }
    if (statusFilter.length > 0) list = list.filter((r) => statusFilter.includes(r.status));
    if (frequencyFilter.length > 0) list = list.filter((r) => frequencyFilter.includes(r.frequency));
    return list;
  }, [rows, debouncedSearch, statusFilter, frequencyFilter]);

  const filteredRows = useMemo(
    () =>
      summaryRows.filter(
        (row) =>
          matchesRecurringCategoryFilter(row, summaryCategoryFilter.value) &&
          matchesRecurringFrequencyFilter(row, summaryFrequencyFilter.value) &&
          matchesRecurringDueBucketFilter(row, summaryDueBucketFilter.value),
      ),
    [summaryRows, summaryCategoryFilter.value, summaryDueBucketFilter.value, summaryFrequencyFilter.value],
  );

  const handleToggleStatus = async (row: RecurringPaymentRecord) => {
    if (row.status === RecurringPaymentStatus.Active) {
      await pauseRecurring({ variables: { id: row.id } });
    } else if (row.status === RecurringPaymentStatus.Paused) {
      await resumeRecurring({ variables: { id: row.id } });
    }
    await refetch();
  };

  const handleConfirmDelete = async () => {
    if (!rowToDelete) return;
    await deleteRecurring({ variables: { id: rowToDelete.id } });
    setRowToDelete(null);
    await refetch();
  };

  const handleConfirmPayment = async (row: RecurringPaymentRecord, paymentDate: Date) => {
    setConfirmingPayment(true);
    try {
      await confirmPayment({ variables: { id: row.id, paymentDate: formatDateOnly(paymentDate) } });
      await refetch();
      setConfirmPaymentRow(null);
    } finally {
      setConfirmingPayment(false);
    }
  };

  const statusOptions = useMemo(
    () =>
      [RecurringPaymentStatus.Active, RecurringPaymentStatus.Paused].map((value) => ({
        value,
        label:
          value === RecurringPaymentStatus.Active
            ? t('recurringPayments.status.ACTIVE')
            : t('recurringPayments.status.PAUSED'),
      })),
    [t],
  );

  const frequencyOptions = useMemo(
    () =>
      [RecurringFrequency.Monthly, RecurringFrequency.Weekly, RecurringFrequency.Yearly, RecurringFrequency.Custom].map(
        (value) => ({
          value,
          label:
            value === RecurringFrequency.Monthly
              ? t('recurringPayments.frequency.MONTHLY')
              : value === RecurringFrequency.Weekly
                ? t('recurringPayments.frequency.WEEKLY')
                : value === RecurringFrequency.Yearly
                  ? t('recurringPayments.frequency.YEARLY')
                  : t('recurringPayments.frequency.CUSTOM'),
        }),
      ),
    [t],
  );

  const noDataMessage =
    rows.length === 0
      ? t('recurringPayments.table.noRecurringActivity')
      : t('recurringPayments.noFilterResults');
  const metrics = useMemo(() => computeRecurringSummaryMetrics(summaryRows), [summaryRows]);
  const activeCount = summaryRows.filter((row) => row.status === RecurringPaymentStatus.Active).length;
  const summaryCategoryMeta = useMemo(() => {
    const selection = summaryCategoryFilter.value;
    if (!selection) return null;
    if (selection.uncategorized) {
      return {
        name: t('recurringPayments.uncategorized'),
        icon: null,
        color: null,
      };
    }

    const match = summaryRows.find((row) => row.category?.id === selection.categoryId)?.category;
    if (!match) return null;
    return {
      name: match.name,
      icon: match.icon,
      color: match.color,
    };
  }, [summaryCategoryFilter.value, summaryRows, t]);
  const summaryFiltersOpen = Boolean(
    summaryCategoryFilter.value || summaryFrequencyFilter.value || summaryDueBucketFilter.value,
  );
  const mobileTrigger = (
    <Box
      sx={{
        px: 3,
        py: 1.5,
        display: 'flex',
        alignItems: 'flex-start',
        justifyContent: 'space-between',
        gap: 2,
      }}
    >
      <Box sx={{ minWidth: 0, flex: 1 }}>
        <Typography variant="subtitle2" sx={{ fontWeight: 700, color: 'text.primary', lineHeight: 1.2 }}>
          {t('routes.recurringPayments')}
        </Typography>
        <Typography
          variant="body2"
          sx={{
            color: 'text.secondary',
            mt: 0.25,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
          }}
        >
          {t('recurringPayments.mobileTrigger.summary', {
            amount: new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(metrics.monthlyCommittedApprox),
            count: activeCount,
          })}
        </Typography>
      </Box>
      <ExpandMoreIcon sx={{ color: 'text.secondary', flexShrink: 0 }} />
    </Box>
  );

  const summaryPanel = (
    <Box sx={{ p: 3, pb: 2, height: '100%' }}>
      <RecurringPaymentsSummaryPanel
        rows={summaryRows}
        loading={loading && rows.length === 0}
        error={error}
        onRetry={() => void refetch()}
        onCategoryClick={summaryCategoryFilter.toggle}
        activeCategoryFilter={summaryCategoryFilter.value}
        onFrequencyClick={summaryFrequencyFilter.toggle}
        activeFrequencyFilter={summaryFrequencyFilter.value}
        onDueBucketClick={summaryDueBucketFilter.toggle}
        activeDueBucketFilter={summaryDueBucketFilter.value}
      />
    </Box>
  );

  const mainContent = (
    <Box>
      {error ? <QueryErrorState error={error} onRetry={() => void refetch()} /> : null}
      {!error ? <LoadingState isLoading={loading && rows.length === 0} minHeight={180} /> : null}

      {!error && !(loading && rows.length === 0) ? (
        <>
          <RecurringPaymentsToolbar
            searchQuery={searchQuery}
            onSearchQueryChange={setSearchQuery}
            onNewRecurring={() => setCreateOpen(true)}
            searchPlaceholder={t('recurringPayments.header.searchPlaceholder')}
            newButtonLabel={t('recurringPayments.newButton')}
            clearSearchAriaLabel={t('financeTransactions.transactionRules.actions.clearSearchAriaLabel')}
            statusFilter={statusFilter}
            onStatusFilterChange={setStatusFilter}
            frequencyFilter={frequencyFilter}
            onFrequencyFilterChange={setFrequencyFilter}
            statusOptions={statusOptions}
            frequencyOptions={frequencyOptions}
            filtersAllLabel={t('recurringPayments.filters.all')}
            filtersSelectedCount={(n) => t('recurringPayments.filters.selectedCount', { count: n })}
            filtersAriaStatus={t('recurringPayments.filters.ariaStatus')}
            filtersAriaFrequency={t('recurringPayments.filters.ariaFrequency')}
            filtersStatusLabel={t('recurringPayments.filters.status')}
            filtersFrequencyLabel={t('recurringPayments.filters.frequency')}
          />
          <RecurringPaymentsActiveFilters
            open={summaryFiltersOpen}
            categoryFilter={summaryCategoryFilter.value}
            categoryMeta={summaryCategoryMeta}
            onClearCategoryFilter={summaryCategoryFilter.clear}
            frequencyFilter={summaryFrequencyFilter.value}
            onClearFrequencyFilter={summaryFrequencyFilter.clear}
            dueBucketFilter={summaryDueBucketFilter.value}
            onClearDueBucketFilter={summaryDueBucketFilter.clear}
          />
          <RecurringPaymentsTable
            rows={filteredRows}
            loading={loading}
            noDataMessage={noDataMessage}
            onEdit={setEditing}
            onPauseResume={(r) => void handleToggleStatus(r)}
            onDelete={setRowToDelete}
            onConfirmPayment={(r) => setConfirmPaymentRow(r)}
          />
        </>
      ) : null}

      <RecurringPaymentForm
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onSaved={async () => {
          await refetch();
        }}
      />

      <RecurringPaymentForm
        open={Boolean(editing)}
        recurring={editing}
        onClose={() => setEditing(null)}
        onSaved={async () => {
          await refetch();
        }}
      />

      <ConfirmPaymentDialog
        row={confirmPaymentRow}
        loading={confirmingPayment}
        onClose={() => setConfirmPaymentRow(null)}
        onConfirm={(date) => void handleConfirmPayment(confirmPaymentRow!, date)}
      />

      <ConfirmationDialog
        open={Boolean(rowToDelete)}
        onClose={() => setRowToDelete(null)}
        onConfirm={() => void handleConfirmDelete()}
        title={t('recurringPayments.deleteConfirmation.title')}
        message={t('recurringPayments.deleteConfirmation.message')}
        cancelText={t('actions.cancel')}
        confirmText={t('recurringPayments.table.rowActions.remove')}
        confirmColor="error"
        loading={deleting}
      />
    </Box>
  );

  return (
    <SidebarLayout
      side="left"
      size="sm"
      fullHeight
      mobileVariant="bottom-sheet"
      mobileTrigger={mobileTrigger}
      sidebar={summaryPanel}
    >
      <Page>
        <Container
          maxWidth="xl"
          sx={{
            py: 4,
            minWidth: 0, // Let the recurring table and filters wrap within the handset viewport.
            overflowX: 'hidden',
          }}
        >
          {mainContent}
        </Container>
      </Page>
    </SidebarLayout>
  );
}
