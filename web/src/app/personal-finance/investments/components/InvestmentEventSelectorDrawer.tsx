'use client';

import * as React from 'react';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import { Box, IconButton, Skeleton, Tooltip, Typography, alpha, useMediaQuery, useTheme } from '@mui/material';
import { useQuery } from '@apollo/client/react';
import { INVESTMENT_TRANSACTIONS } from '@/lib/graphql/operations/banking/queries';
import {
  InvestmentSource,
  InvestmentTransactionDirection,
  InvestmentTransactionType,
} from '@/lib/graphql/types/__generated__/graphql';
import { CurrencyAmountParts } from '@/shared/components/ui/data-display';
import { EmptyState, QueryErrorState } from '@/shared/components/ui/feedback';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { i18n, useTranslation } from '@/shared/i18n';
import { CloseIcon } from '@/shared/ui/mui-imports';
import { personalFinanceTranslations } from '../../i18n';
import { HoldingDetailHeader } from './HoldingDetailHeader';
import { buildHoldingDetailHeaderModelFromRow, type TranslateFn } from './holdingDetailHeaderPresenter';
import { UpdateHoldingDrawer } from './update/UpdateHoldingDrawer';
import { getAssetGroupKey, type HoldingRow } from './table/investmentTableHelpers';
import type { UpdateHoldingEditTransaction } from './update/types';

export interface InvestmentEventSelectorDrawerProps {
  open: boolean;
  holding: HoldingRow | null;
  onClose: () => void;
  onRecorded: () => Promise<void>;
}

interface InvestmentTransactionsQueryResult {
  investmentTransactions: EventSelectorTransaction[];
}

interface InvestmentTransactionsQueryVariables {
  holdingId: string;
}

type EventSelectorTransaction = UpdateHoldingEditTransaction & {
  holdingId: string;
  financialAccountId: string;
  source: InvestmentSource;
  type: InvestmentTransactionType;
};

function parseDateTimestamp(value: unknown): number {
  if (!value) return Number.NEGATIVE_INFINITY;

  const rawValue = String(value);
  const dateOnlyMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(rawValue);

  if (dateOnlyMatch) {
    const [, year, month, day] = dateOnlyMatch;
    return new Date(Number(year), Number(month) - 1, Number(day)).getTime();
  }

  const timestamp = new Date(rawValue).getTime();
  return Number.isNaN(timestamp) ? Number.NEGATIVE_INFINITY : timestamp;
}

function formatEventDate(value: unknown, locale: string): string {
  if (!value) return '—';

  const rawValue = String(value);
  const dateOnlyMatch = /^(\d{4})-(\d{2})-(\d{2})$/.exec(rawValue);

  if (dateOnlyMatch) {
    const [, year, month, day] = dateOnlyMatch;
    const date = new Date(Number(year), Number(month) - 1, Number(day));
    return date.toLocaleDateString(locale, { dateStyle: 'medium' });
  }

  const date = new Date(rawValue);
  if (Number.isNaN(date.getTime())) return '—';
  return date.toLocaleDateString(locale, { dateStyle: 'medium' });
}

function getEventTypeLabel(type: string, t: TranslateFn): string {
  return t(`investments.transactionTypes.${type}`, {
    defaultValue: t(`investments.detailDrawer.transactionTypes.${type}`, { defaultValue: type }),
  });
}

function EventRowSkeleton() {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, px: { xs: 2, md: 3 }, py: 1.5 }}>
      <Skeleton variant="circular" width={8} height={8} />
      <Box sx={{ flex: 1 }}>
        <Skeleton variant="text" width="40%" />
        <Skeleton variant="text" width="25%" />
      </Box>
      <Skeleton variant="text" width={80} />
    </Box>
  );
}

interface EventSelectorRowProps {
  transaction: EventSelectorTransaction;
  locale: string;
  currencyCode: string;
  isFirst: boolean;
  onClick?: () => void;
  t: TranslateFn;
}

const EventSelectorRow = React.memo(function EventSelectorRow({
  transaction,
  locale,
  currencyCode,
  isFirst,
  onClick,
  t,
}: EventSelectorRowProps) {
  const theme = useTheme();
  const isOutflow = transaction.direction === InvestmentTransactionDirection.Outflow;
  const directionColor = isOutflow ? 'error.main' : 'success.main';
  const directionLabel = isOutflow
    ? t('investments.detailDrawer.timeline.outflowLabel')
    : t('investments.detailDrawer.timeline.inflowLabel');
  const tradeDateLabel = formatEventDate(transaction.tradeDate, locale);
  const eventTypeLabel = getEventTypeLabel(String(transaction.type), t);
  const canEdit = Boolean(onClick);

  return (
    <Box
      component={canEdit ? 'button' : 'div'}
      type={canEdit ? 'button' : undefined}
      onClick={onClick}
      sx={{
        width: '100%',
        border: 'none',
        background: 'transparent',
        textAlign: 'left',
        display: 'grid',
        gridTemplateColumns: '10px minmax(0, 1fr) auto auto',
        alignItems: 'center',
        columnGap: 1.5,
        rowGap: 0.25,
        px: { xs: 2, md: 3 },
        py: 1.5,
        borderTop: isFirst ? 'none' : '1px solid',
        borderColor: 'divider',
        opacity: canEdit ? 1 : 0.45,
        cursor: canEdit ? 'pointer' : 'default',
        transition: 'background-color 150ms linear, opacity 150ms linear',
        '&:hover': canEdit
          ? {
              bgcolor: alpha(theme.palette.primary.main, 0.035),
              '& .event-selector-chevron': {
                opacity: 1,
                transform: 'translateX(0)',
              },
            }
          : undefined,
      }}
    >
      <Box
        sx={{
          width: 8,
          height: 8,
          borderRadius: 999,
          bgcolor: directionColor,
          gridColumn: 1,
          gridRow: '1 / span 2',
          alignSelf: 'center',
        }}
      />

      <Typography
        variant="body2"
        sx={{
          minWidth: 0,
          gridColumn: 2,
          gridRow: 1,
          color: 'text.secondary',
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}
      >
        <Box component="span" sx={{ color: directionColor, fontWeight: 700 }}>
          {directionLabel}
        </Box>
        {' · '}
        <Box component="span" sx={{ color: 'text.primary', fontWeight: 500 }}>
          {eventTypeLabel}
        </Box>
        {' · '}
        {tradeDateLabel}
      </Typography>

      <CurrencyAmountParts
        value={transaction.grossValue}
        currency={currencyCode}
        locale={locale}
        variant="body2"
        fontWeight={700}
        noWrap
        sx={{ gridColumn: 3, gridRow: '1 / span 2', color: directionColor, textAlign: 'right' }}
      />

      <Box
        sx={{
          gridColumn: 4,
          gridRow: '1 / span 2',
          display: 'flex',
          alignItems: 'center',
          color: 'text.secondary',
        }}
      >
        {canEdit ? (
          <ChevronRightIcon
            className="event-selector-chevron"
            sx={{
              fontSize: 20,
              opacity: { xs: 1, md: 0 },
              transform: 'translateX(-2px)',
              transition: 'opacity 120ms ease, transform 120ms ease',
            }}
          />
        ) : (
          <Tooltip title={t('investments.eventSelector.notEditable')} placement="left">
            <LockOutlinedIcon sx={{ fontSize: 18 }} />
          </Tooltip>
        )}
      </Box>
    </Box>
  );
});

export const InvestmentEventSelectorDrawer = React.memo(function InvestmentEventSelectorDrawer({
  open,
  holding,
  onClose,
  onRecorded,
}: InvestmentEventSelectorDrawerProps) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const { t } = useTranslation(personalFinanceTranslations);
  const locale = React.useMemo(() => i18n.language || Intl.DateTimeFormat().resolvedOptions().locale || 'pt-BR', []);

  const [editingTransaction, setEditingTransaction] = React.useState<EventSelectorTransaction | null>(null);

  React.useEffect(() => {
    if (!open) {
      setEditingTransaction(null);
    }
  }, [open]);

  React.useEffect(() => {
    setEditingTransaction(null);
  }, [holding?.id]);

  const { data, loading, error, refetch } = useQuery<InvestmentTransactionsQueryResult, InvestmentTransactionsQueryVariables>(
    INVESTMENT_TRANSACTIONS,
    {
      variables: { holdingId: holding?.id ?? '' },
      skip: !open || !holding?.id,
      fetchPolicy: 'cache-and-network',
      errorPolicy: 'all',
    },
  );

  const transactions = React.useMemo(() => {
    const rawTransactions = data?.investmentTransactions ?? [];

    return [...rawTransactions].sort((left, right) => {
      const timestampDelta = parseDateTimestamp(right.tradeDate) - parseDateTimestamp(left.tradeDate);
      if (timestampDelta !== 0) return timestampDelta;
      return right.id.localeCompare(left.id);
    });
  }, [data?.investmentTransactions]);

  const handleEditClose = React.useCallback(() => {
    setEditingTransaction(null);
  }, []);

  const handleEditRecorded = React.useCallback(async () => {
    setEditingTransaction(null);
    onClose();
    await onRecorded();
  }, [onClose, onRecorded]);

  const closeSelectorDrawer = React.useCallback(() => {
    setEditingTransaction(null);
    onClose();
  }, [onClose]);

  const hasTransactions = transactions.length > 0;
  const showSkeleton = loading && !hasTransactions;
  const showError = !loading && Boolean(error) && !hasTransactions;
  const showEmpty = !loading && !error && !hasTransactions;
  const currencyCode = holding?.asset.currencyCode ?? 'BRL';
  const groupTitle =
    holding?.asset?.assetType != null
      ? t(`investments.table.groups.${getAssetGroupKey(holding.asset.assetType)}`)
      : t('investments.eventSelector.title');
  const headerModel = React.useMemo(
    () => (holding ? buildHoldingDetailHeaderModelFromRow(holding, t, locale) : null),
    [holding, t, locale],
  );

  return (
    <>
      <Drawer
        open={open}
        onClose={closeSelectorDrawer}
        anchor="right"
      >
        <Drawer.Header>
          <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%', gap: 2 }}>
            <Typography variant="h6" noWrap sx={{ letterSpacing: '0.05em', lineHeight: 1.15, fontWeight: 400, color: 'action.active' }}>
              {groupTitle.toLocaleUpperCase(locale)}
            </Typography>

            <IconButton onClick={closeSelectorDrawer} size="small" aria-label={t('actions.close')}>
              <CloseIcon fontSize="small" />
            </IconButton>
          </Box>
        </Drawer.Header>

        <Drawer.Body sx={{ p: 0 }}>

          <Box sx={{ px: { xs: 2, md: 3 }, py: 2, borderBottom: '1px solid', borderColor: 'divider' }}>
            {headerModel ? (
              <HoldingDetailHeader
                model={headerModel}
                hideMetrics
              />
            ) : (
              <Typography variant="h6" fontWeight={700} sx={{ lineHeight: 1.2, wordBreak: 'break-word' }}>
                —
              </Typography>
            )}
          </Box>

          {showSkeleton ? (
            <Box>
              {Array.from({ length: 4 }).map((_, index) => (
                <EventRowSkeleton key={index} />
              ))}
            </Box>
          ) : null}

          {showError ? (
            <Box sx={{ py: 3, px: { xs: 2, md: 3 } }}>
              <QueryErrorState
                error={error}
                title={t('investments.table.error')}
                onRetry={() => {
                  void refetch();
                }}
              />
            </Box>
          ) : null}

          {showEmpty ? (
            <Box sx={{ py: 3, px: { xs: 2, md: 3 } }}>
              <EmptyState
                title={t('investments.eventSelector.empty.title')}
                description={t('investments.eventSelector.empty.description')}
              />
            </Box>
          ) : null}

          {!showSkeleton && !showError && hasTransactions ? (
            <Box>
              {transactions.map((transaction, index) => (
                <EventSelectorRow
                  key={transaction.id}
                  transaction={transaction}
                  locale={locale}
                  currencyCode={currencyCode}
                  isFirst={index === 0}
                  onClick={
                    transaction.source === InvestmentSource.Manual
                      ? () => {
                          setEditingTransaction(transaction);
                        }
                      : undefined
                  }
                  t={t}
                />
              ))}
            </Box>
          ) : null}
        </Drawer.Body>
      </Drawer>

      <UpdateHoldingDrawer
        holdingId={holding?.id ?? null}
        open={Boolean(editingTransaction && holding?.id)}
        onClose={handleEditClose}
        editTransaction={editingTransaction}
        onRecorded={handleEditRecorded}
      />
    </>
  );
});

export default InvestmentEventSelectorDrawer;
