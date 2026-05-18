'use client';

import * as React from 'react';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import EditIcon from '@mui/icons-material/Edit';
import {
  Box,
  IconButton,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
  useTheme,
} from '@mui/material';
import type { Theme } from '@mui/material/styles';
import { CurrencyAmountParts } from '@/shared/components/ui/data-display';
import { EmptyState } from '@/shared/components/ui/feedback';
import { Label } from '@/shared/components/ui/primitives/Label';
import { useTranslation } from '@/shared/i18n';
import { formatTableDate } from '@/shared/utils/date';
import { personalFinanceTranslations } from '../../../i18n';
import {
  financeTransactionBodyRowSx,
  financeTransactionHeadCellSx,
  financeTransactionTableBodySx,
  financeTransactionTableHeadRowSx,
  financeTransactionTableHeadSx,
  getFinanceTransactionTableSx,
} from '../../../utils/financeTransactionTableStyles';

export interface MovimentacoesTransactionRow {
  id: string;
  direction: 'INFLOW' | 'OUTFLOW';
  tradeDate: unknown;
  quantity?: number | null;
  unitPrice?: number | null;
  grossValue: number;
  netValue?: number | null;
}

interface MovimentacoesTabProps {
  locale: string;
  currencyCode: string;
  buyLabel: string;
  sellLabel: string;
  transactions: MovimentacoesTransactionRow[];
  onEditTransaction: (transactionId: string) => void;
  onDeleteTransaction: (transactionId: string) => void;
}

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

/** Short locale-aware date; same as cash movements / holdings tables (`formatTableDate`). */
function formatTradeDate(value: unknown, locale: string): string {
  if (value == null || String(value).trim() === '') return '—';
  const formatted = formatTableDate(String(value), locale);
  return formatted === '-' ? '—' : formatted;
}

function formatQuantity(value: number | null | undefined, locale: string): string {
  if (typeof value !== 'number' || !Number.isFinite(value)) return '—';
  return new Intl.NumberFormat(locale, {
    minimumFractionDigits: 0,
    maximumFractionDigits: 4,
  }).format(value);
}

function headCellSx(theme: Theme) {
  return financeTransactionHeadCellSx(theme);
}

export function MovimentacoesTab({
  locale,
  currencyCode,
  buyLabel,
  sellLabel,
  transactions,
  onEditTransaction,
  onDeleteTransaction,
}: MovimentacoesTabProps) {
  const theme = useTheme();
  const { t } = useTranslation(personalFinanceTranslations);
  const hc = headCellSx(theme);

  const sortedTransactions = React.useMemo(
    () =>
      [...transactions].sort((left, right) => {
        const dateDelta = parseDateTimestamp(right.tradeDate) - parseDateTimestamp(left.tradeDate);
        if (dateDelta !== 0) return dateDelta;
        return right.id.localeCompare(left.id);
      }),
    [transactions],
  );

  const tableAriaLabel = t('investments.detailDrawer.transactions.tableAriaLabel');
  const editLabel = t('investments.detailDrawer.transactions.editTransaction');
  const deleteLabel = t('investments.detailDrawer.transactions.deleteTransaction');

  return (
    <Box>
      {sortedTransactions.length === 0 ? (
        <EmptyState
          title={t('investments.detailDrawer.transactions.emptyTitle')}
          description={t('investments.detailDrawer.transactions.emptyDescription')}
        />
      ) : (
        <TableContainer
          component={Paper}
          elevation={0}
          sx={{
            bgcolor: 'transparent',
            background: 'transparent !important',
            border: 'none',
            overflowX: 'auto',
          }}
          data-testid="holding-detail-transactions-table"
        >
          <Table
            size="small"
            sx={{ ...getFinanceTransactionTableSx(560), tableLayout: 'fixed' }}
            aria-label={tableAriaLabel}
          >
            <TableHead sx={financeTransactionTableHeadSx}>
              <TableRow sx={financeTransactionTableHeadRowSx}>
                <TableCell sx={{ ...hc, pl: 2, width: '22%' }}>
                  <Label>{t('investments.detailDrawer.transactions.columns.date')}</Label>
                </TableCell>
                <TableCell sx={{ ...hc, width: '18%' }}>
                  <Label>{t('investments.detailDrawer.transactions.columns.direction')}</Label>
                </TableCell>
                <TableCell align="right" sx={{ ...hc, width: '14%' }}>
                  <Label>{t('investments.detailDrawer.transactions.columns.quantity')}</Label>
                </TableCell>
                <TableCell align="right" sx={{ ...hc, width: '18%' }}>
                  <Label>{t('investments.detailDrawer.transactions.columns.unitPrice')}</Label>
                </TableCell>
                <TableCell align="right" sx={{ ...hc, width: '18%' }}>
                  <Label>{t('investments.detailDrawer.transactions.columns.value')}</Label>
                </TableCell>
                <TableCell
                  align="center"
                  padding="none"
                  sx={{
                    ...hc,
                    width: 88,
                    maxWidth: 88,
                    px: 1,
                  }}
                >
                  <Label>{t('investments.detailDrawer.transactions.columns.actions')}</Label>
                </TableCell>
              </TableRow>
            </TableHead>
            <TableBody sx={financeTransactionTableBodySx}>
              {sortedTransactions.map((transaction, index) => {
                const isFirst = index === 0;
                const isLast = index === sortedTransactions.length - 1;
                return (
                  <TableRow
                    key={transaction.id}
                    hover
                    onClick={() => onEditTransaction(transaction.id)}
                    sx={{
                      cursor: 'pointer',
                      ...financeTransactionBodyRowSx(theme, { isFirst, isLast }),
                    }}
                  >
                    <TableCell sx={{ pl: 2 }}>
                      <Typography variant="body2" component="span">
                        {formatTradeDate(transaction.tradeDate, locale)}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" component="span">
                        {transaction.direction === 'INFLOW' ? buyLabel : sellLabel}
                      </Typography>
                    </TableCell>
                    <TableCell align="right">
                      <Typography variant="body2" component="span" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                        {formatQuantity(transaction.quantity, locale)}
                      </Typography>
                    </TableCell>
                    <TableCell align="right">
                      <CurrencyAmountParts
                        value={transaction.unitPrice ?? null}
                        currency={currencyCode}
                        locale={locale}
                        variant="body2"
                      />
                    </TableCell>
                    <TableCell align="right">
                      <CurrencyAmountParts
                        value={transaction.netValue ?? transaction.grossValue}
                        currency={currencyCode}
                        locale={locale}
                        variant="body2"
                      />
                    </TableCell>
                    <TableCell align="center" padding="none" sx={{ px: 1 }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 0.25 }}>
                        <Tooltip title={editLabel}>
                          <IconButton
                            size="small"
                            aria-label={editLabel}
                            onClick={(event) => {
                              event.stopPropagation();
                              onEditTransaction(transaction.id);
                            }}
                          >
                            <EditIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title={deleteLabel}>
                          <IconButton
                            size="small"
                            color="error"
                            aria-label={deleteLabel}
                            onClick={(event) => {
                              event.stopPropagation();
                              onDeleteTransaction(transaction.id);
                            }}
                          >
                            <DeleteOutlineIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </Box>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Box>
  );
}
