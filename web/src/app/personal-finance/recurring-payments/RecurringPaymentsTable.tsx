'use client';

import CheckIcon from '@mui/icons-material/Check';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import EditIcon from '@mui/icons-material/Edit';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import PauseCircleOutlineIcon from '@mui/icons-material/PauseCircleOutline';
import PlayCircleOutlineIcon from '@mui/icons-material/PlayCircleOutline';
import {
  alpha,
  Box,
  Card,
  CardContent,
  Chip,
  Divider,
  IconButton,
  LinearProgress,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import type { Theme } from '@mui/material/styles';
import type { MouseEvent } from 'react';
import { Fragment, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { i18n, useTranslation } from '@/shared/i18n';
import { formatDateOnlyLocale } from '@/shared/utils/date/dateOnly';
import { personalFinanceTranslations } from '../i18n';
import { CategoryChip, type CategoryChipDecorationAlpha } from '@/app/personal-finance/finance-transactions/category/CategoryChip';
import {
  financeTransactionBodyRowSx,
  financeTransactionDateGroupTableCellSx,
  financeTransactionDateGroupTableRowSx,
  financeTransactionHeadCellSx,
  financeTransactionTableBodySx,
  financeTransactionTableHeadRowSx,
  financeTransactionTableHeadSx,
  getFinanceTransactionTableSx,
} from '@/app/personal-finance/utils/financeTransactionTableStyles';
import { FINANCE_TABLE_BODY_FONT_SIZE } from '@/shared/components/ui/data-display/Table';
import { Label } from '@/shared/components/ui/primitives/Label';
import type { RecurringPaymentRow, RecurringDueBucket } from './recurringPaymentRowModel';
import { groupRecurringPaymentsByDueBucket, isOverdue } from './recurringPaymentRowModel';
import { RecurringFrequency, RecurringPaymentStatus } from '@/lib/graphql/types/__generated__/graphql';
import { RecurringPaymentNameIcon } from './RecurringPaymentNameIcon';

const COL_SPAN = 7;

const RECURRING_CATEGORY_CHIP_DECORATION: CategoryChipDecorationAlpha = {
  filledBackground: 0.1,
  filledBorder: 0,
  filledHoverBackground: 0.14,
  placeholderBackground: 0.02,
  placeholderInsetBorder: 0.06,
  placeholderHoverBackground: 0.05,
};

function formatAmount(value: number | null | undefined, currency: string): string {
  if (value == null) return '—';
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency }).format(value);
}

const STATUS_CHIP_LABEL_SX = {
  fontSize: FINANCE_TABLE_BODY_FONT_SIZE,
  fontWeight: 400,
  overflow: 'hidden',
  textOverflow: 'ellipsis',
} as const;

function statusChipSx(accentColor: string, options?: { muted?: boolean }) {
  return {
    color: accentColor,
    bgcolor: alpha(accentColor, 0.1),
    border: 'none',
    boxShadow: 'none',
    '& .MuiChip-label': STATUS_CHIP_LABEL_SX,
    ...(options?.muted ? { opacity: 0.75 } : {}),
  };
}

type StatusChipConfig = {
  label: string;
  sx: object;
  /** Matches chip label color — use for due date text in the same row. */
  dueDateColor: string;
  dueDateOpacity?: number;
};

function statusChipProps(
  row: RecurringPaymentRow,
  theme: Theme,
  tStatus: (k: string) => string,
  overdueLabel: string,
): StatusChipConfig {
  const baseSx = {
    border: 'none',
    boxShadow: 'none',
    '& .MuiChip-label': STATUS_CHIP_LABEL_SX,
  };

  if (isOverdue(row)) {
    return {
      label: overdueLabel,
      dueDateColor: theme.palette.error.main,
      sx: { ...baseSx, ...statusChipSx(theme.palette.error.main) },
    };
  }
  if (row.status === RecurringPaymentStatus.Active) {
    return {
      label: tStatus('ACTIVE'),
      dueDateColor: theme.palette.success.main,
      sx: { ...baseSx, ...statusChipSx(theme.palette.success.main) },
    };
  }
  if (row.status === RecurringPaymentStatus.Paused) {
    return {
      label: tStatus('PAUSED'),
      dueDateColor: theme.palette.warning.main,
      sx: { ...baseSx, ...statusChipSx(theme.palette.warning.main) },
    };
  }
  return {
    label: String(row.status),
    dueDateColor: theme.palette.text.secondary,
    sx: {
      ...baseSx,
      color: 'text.secondary',
      bgcolor: 'action.selected',
    },
  };
}

function bucketLabelKey(bucket: RecurringDueBucket): string {
  switch (bucket) {
    case 'overdue':
      return 'recurringPayments.table.groups.overdue';
    case 'next7Days':
      return 'recurringPayments.table.groups.next7Days';
    case 'nextMonth':
      return 'recurringPayments.table.groups.nextMonth';
    default:
      return 'recurringPayments.table.groups.nextMonth';
  }
}

export interface RecurringPaymentsTableProps {
  rows: RecurringPaymentRow[];
  loading: boolean;
  noDataMessage: string;
  onEdit: (row: RecurringPaymentRow) => void;
  onPauseResume: (row: RecurringPaymentRow) => void;
  onDelete: (row: RecurringPaymentRow) => void;
  onConfirmPayment: (row: RecurringPaymentRow) => void;
}

export function RecurringPaymentsTable({
  rows,
  loading,
  noDataMessage,
  onEdit,
  onPauseResume,
  onDelete,
  onConfirmPayment,
}: RecurringPaymentsTableProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const isXsCards = useMediaQuery(theme.breakpoints.down('sm'));

  const containerRef = useRef<HTMLDivElement | null>(null);
  const tableHeadRef = useRef<HTMLTableSectionElement | null>(null);
  const [progressTop, setProgressTop] = useState(0);

  const grouped = useMemo(() => groupRecurringPaymentsByDueBucket(rows), [rows]);

  const [menuAnchor, setMenuAnchor] = useState<null | HTMLElement>(null);
  const [menuRow, setMenuRow] = useState<RecurringPaymentRow | null>(null);
  const [hoveredRowId, setHoveredRowId] = useState<string | null>(null);

  const confirmPaymentLabel = t('recurringPayments.actions.confirmPayment');
  const rowActionsAriaLabel = t('recurringPayments.table.rowActions.ariaLabel');

  const openMenu = useCallback((e: MouseEvent<HTMLElement>, row: RecurringPaymentRow) => {
    setMenuAnchor(e.currentTarget);
    setMenuRow(row);
  }, []);

  const closeMenu = useCallback(() => {
    setMenuAnchor(null);
    setMenuRow(null);
  }, []);

  const tStatus = useCallback((s: string) => t(`recurringPayments.status.${s}`), [t]);
  const tFreq = useCallback((f: RecurringFrequency) => t(`recurringPayments.frequency.${f}`), [t]);

  const showHeaderProgress = loading;

  const updateProgressTop = useCallback(() => {
    const c = containerRef.current;
    const h = tableHeadRef.current;
    if (!c || !h) return;
    const cRect = c.getBoundingClientRect();
    const hRect = h.getBoundingClientRect();
    setProgressTop(Math.max(0, Math.round(hRect.bottom - cRect.top - 1)));
  }, []);

  useEffect(() => {
    updateProgressTop();
    window.addEventListener('resize', updateProgressTop);
    const c = containerRef.current;
    const h = tableHeadRef.current;
    const ro =
      typeof ResizeObserver !== 'undefined' && (c || h) ? new ResizeObserver(() => updateProgressTop()) : null;
    if (ro) {
      if (c) ro.observe(c);
      if (h) ro.observe(h);
    }
    return () => {
      window.removeEventListener('resize', updateProgressTop);
      ro?.disconnect();
    };
  }, [updateProgressTop]);

  useEffect(() => {
    const id = window.requestAnimationFrame(() => updateProgressTop());
    return () => window.cancelAnimationFrame(id);
  }, [rows.length, grouped.length, updateProgressTop]);

  const headCellSx = financeTransactionHeadCellSx(theme);
  const isEmpty = rows.length === 0 && !loading;

  if (isXsCards) {
    return (
      <Box ref={containerRef} sx={{ position: 'relative' }}>
        <Box
          sx={{
            position: 'absolute',
            left: 0,
            right: 0,
            top: 0,
            height: 4,
            pointerEvents: 'none',
            zIndex: 2,
          }}
        >
          <LinearProgress
            sx={{
              height: '100%',
              opacity: showHeaderProgress ? 1 : 0,
              transition: 'opacity 150ms linear',
            }}
          />
        </Box>
        <Stack spacing={1.5} sx={{ pt: showHeaderProgress ? 0.5 : 0 }}>
          {isEmpty ? (
            <Box sx={{ py: 4, textAlign: 'center' }}>
              <Typography color="text.secondary">{noDataMessage}</Typography>
            </Box>
          ) : null}
          {grouped.map((group) => (
            <Fragment key={`m-${group.bucket}`}>
              <Box sx={{ pt: 0.5 }}>
                <Label sx={{ mb: 0.5 }}>{t(bucketLabelKey(group.bucket))}</Label>
              </Box>
              {group.rows.map((row) => {
                const chip = statusChipProps(row, theme, tStatus, t('recurringPayments.statusOverdue'));
                return (
                  <Card key={row.id} variant="outlined">
                    <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                      <Stack spacing={1}>
                        <Stack direction="row" spacing={1.5} alignItems="flex-start">
                          <RecurringPaymentNameIcon row={row} size={40} />
                          <Box sx={{ flex: 1, minWidth: 0 }}>
                            <Typography fontWeight={600} noWrap>
                              {row.name}
                            </Typography>
                            {row.merchant?.name ? (
                              <Typography variant="caption" color="text.secondary" noWrap>
                                {row.merchant.name}
                              </Typography>
                            ) : null}
                          </Box>
                          <RecurringPaymentRowActionsCell
                            row={row}
                            hovered
                            confirmPaymentLabel={confirmPaymentLabel}
                            rowActionsAriaLabel={rowActionsAriaLabel}
                            onConfirmPayment={onConfirmPayment}
                            onOpenMenu={openMenu}
                          />
                        </Stack>
                        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap alignItems="center">
                          {row.category ? (
                            <CategoryChip
                              category={row.category}
                              maxWidthCh={28}
                              decorationAlpha={RECURRING_CATEGORY_CHIP_DECORATION}
                            />
                          ) : null}
                          <Chip size="small" label={tFreq(row.frequency)} variant="outlined" />
                          <Chip size="small" label={chip.label} sx={chip.sx} />
                        </Stack>
                        <Stack direction="row" justifyContent="space-between" alignItems="center">
                          <Typography
                            variant="body2"
                            sx={{
                              color: chip.dueDateColor,
                              ...(chip.dueDateOpacity != null ? { opacity: chip.dueDateOpacity } : {}),
                            }}
                          >
                            {formatDateOnlyLocale(row.nextDueDate, i18n.language)}
                          </Typography>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>
                            {row.expectedAmount == null
                              ? t('recurringPayments.variableAmountBadge')
                              : formatAmount(row.expectedAmount, row.currencyCode)}
                          </Typography>
                        </Stack>
                      </Stack>
                    </CardContent>
                  </Card>
                );
              })}
            </Fragment>
          ))}
        </Stack>
        <RowActionMenu
          anchorEl={menuAnchor}
          row={menuRow}
          onClose={closeMenu}
          onEdit={onEdit}
          onPauseResume={onPauseResume}
          onDelete={onDelete}
        />
      </Box>
    );
  }

  return (
    <TableContainer
      ref={containerRef}
      component={Paper}
      elevation={0}
      sx={{
        bgcolor: 'transparent',
        background: 'transparent !important',
        border: 'none',
        overflow: 'visible',
        position: 'relative',
      }}
    >
      <Box
        sx={{
          position: 'absolute',
          left: 0,
          right: 0,
          top: `${progressTop}px`,
          height: 4,
          pointerEvents: 'none',
          zIndex: 2,
        }}
      >
        <LinearProgress
          sx={{
            height: '100%',
            opacity: showHeaderProgress ? 1 : 0,
            transition: 'opacity 150ms linear',
          }}
        />
      </Box>

      <Table sx={getFinanceTransactionTableSx(720)} aria-label={t('recurringPayments.table.ariaLabel')}>
        <TableHead ref={tableHeadRef} sx={financeTransactionTableHeadSx}>
          <TableRow sx={financeTransactionTableHeadRowSx}>
            <TableCell sx={headCellSx}>
              <Label sx={{ mb: 0 }}>{t('recurringPayments.table.columns.name')}</Label>
            </TableCell>
            <TableCell sx={headCellSx}>
              <Label sx={{ mb: 0 }}>{t('recurringPayments.table.columns.category')}</Label>
            </TableCell>
            <TableCell sx={headCellSx}>
              <Label sx={{ mb: 0 }}>{t('recurringPayments.table.columns.frequency')}</Label>
            </TableCell>
            <TableCell sx={headCellSx}>
              <Label sx={{ mb: 0 }}>{t('recurringPayments.table.columns.due')}</Label>
            </TableCell>
            <TableCell sx={headCellSx}>
              <Label sx={{ mb: 0 }}>{t('recurringPayments.table.columns.status')}</Label>
            </TableCell>
            <TableCell align="right" sx={headCellSx}>
              <Label sx={{ mb: 0 }}>{t('recurringPayments.table.columns.amount')}</Label>
            </TableCell>
            <TableCell align="right" sx={{ px: 2, ...headCellSx }} />
          </TableRow>
        </TableHead>
        <TableBody sx={financeTransactionTableBodySx}>
          {isEmpty ? (
            <TableRow>
              <TableCell colSpan={COL_SPAN} sx={{ border: 'none', py: 4, textAlign: 'center' }}>
                <Typography color="text.secondary">{noDataMessage}</Typography>
              </TableCell>
            </TableRow>
          ) : null}
          {grouped.map((group) => (
            <Fragment key={`grp-${group.bucket}`}>
              <TableRow sx={financeTransactionDateGroupTableRowSx}>
                <TableCell colSpan={COL_SPAN} sx={financeTransactionDateGroupTableCellSx()}>
                  <Label sx={{ mb: 0 }}>{t(bucketLabelKey(group.bucket))}</Label>
                </TableCell>
              </TableRow>
              {group.rows.map((row, index) => {
                const chip = statusChipProps(row, theme, tStatus, t('recurringPayments.statusOverdue'));
                const isFirst = index === 0;
                const isLast = index === group.rows.length - 1;
                const rowSx = financeTransactionBodyRowSx(theme, { isFirst, isLast });
                return (
                  <TableRow
                    key={row.id}
                    hover
                    sx={rowSx}
                    onMouseEnter={() => setHoveredRowId(row.id)}
                    onMouseLeave={() => setHoveredRowId(null)}
                  >
                    <TableCell>
                      <Stack direction="row" spacing={1.25} alignItems="center">
                        <RecurringPaymentNameIcon row={row} size={36} />
                        <Box sx={{ minWidth: 0 }}>
                          <Typography fontWeight={600} noWrap>
                            {row.name}
                          </Typography>
                          {row.merchant?.name ? (
                            <Typography variant="caption" color="text.secondary" noWrap display="block">
                              {row.merchant.name}
                            </Typography>
                          ) : null}
                        </Box>
                      </Stack>
                    </TableCell>
                    <TableCell sx={{ minWidth: 148 }}>
                      {row.category ? (
                        <CategoryChip
                          category={row.category}
                          maxWidthCh={22}
                          decorationAlpha={RECURRING_CATEGORY_CHIP_DECORATION}
                        />
                      ) : (
                        <Typography variant="body2" color="text.secondary">
                          —
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{tFreq(row.frequency)}</Typography>
                    </TableCell>
                    <TableCell>
                      <Typography
                        variant="body2"
                        sx={{
                          color: chip.dueDateColor,
                          ...(chip.dueDateOpacity != null ? { opacity: chip.dueDateOpacity } : {}),
                        }}
                      >
                        {formatDateOnlyLocale(row.nextDueDate, i18n.language)}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip size="small" label={chip.label} sx={chip.sx} />
                    </TableCell>
                    <TableCell align="right">
                      <Typography variant="body2">
                        {row.expectedAmount == null
                          ? t('recurringPayments.variableAmountBadge')
                          : formatAmount(row.expectedAmount, row.currencyCode)}
                      </Typography>
                    </TableCell>
                    <TableCell align="right" sx={{ px: 2, width: 88 }}>
                      <RecurringPaymentRowActionsCell
                        row={row}
                        hovered={hoveredRowId === row.id}
                        confirmPaymentLabel={confirmPaymentLabel}
                        rowActionsAriaLabel={rowActionsAriaLabel}
                        onConfirmPayment={onConfirmPayment}
                        onOpenMenu={openMenu}
                      />
                    </TableCell>
                  </TableRow>
                );
              })}
            </Fragment>
          ))}
        </TableBody>
      </Table>

      <RowActionMenu
        anchorEl={menuAnchor}
        row={menuRow}
        onClose={closeMenu}
        onEdit={onEdit}
        onPauseResume={onPauseResume}
        onDelete={onDelete}
      />
    </TableContainer>
  );
}

function RecurringPaymentRowActionsCell({
  row,
  hovered,
  confirmPaymentLabel,
  rowActionsAriaLabel,
  onConfirmPayment,
  onOpenMenu,
}: {
  row: RecurringPaymentRow;
  hovered: boolean;
  confirmPaymentLabel: string;
  rowActionsAriaLabel: string;
  onConfirmPayment: (row: RecurringPaymentRow) => void;
  onOpenMenu: (e: MouseEvent<HTMLElement>, row: RecurringPaymentRow) => void;
}) {
  const theme = useTheme();
  const showConfirmPayment = row.status === RecurringPaymentStatus.Active;

  const confirmPaymentTileSx = {
    width: 32,
    height: 32,
    minWidth: 32,
    p: 0,
    borderRadius: '10px',
    color: alpha(theme.palette.success.dark, theme.palette.mode === 'dark' ? 0.9 : 0.75),
    bgcolor: 'transparent',
    '&:hover': {
      bgcolor: alpha(theme.palette.success.main, theme.palette.mode === 'dark' ? 0.26 : 0.16),
      color: theme.palette.success.main,
    },
    '&:focus-visible': {
      outline: `2px solid ${alpha(theme.palette.success.main, 0.45)}`,
      outlineOffset: 1,
    },
  };

  return (
    <Box
      sx={{
        ml: 'auto',
        display: 'flex',
        justifyContent: 'flex-end',
        alignItems: 'center',
        gap: 0.25,
      }}
    >
      {showConfirmPayment ? (
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            visibility: hovered ? 'visible' : 'hidden',
          }}
        >
          <Tooltip title={confirmPaymentLabel}>
            <IconButton
              size="small"
              aria-label={confirmPaymentLabel}
              onClick={(event) => {
                event.stopPropagation();
                onConfirmPayment(row);
              }}
              sx={confirmPaymentTileSx}
            >
              <CheckIcon sx={{ fontSize: 18 }} />
            </IconButton>
          </Tooltip>
        </Box>
      ) : null}
      <IconButton
        size="small"
        aria-label={rowActionsAriaLabel}
        onClick={(event) => {
          event.stopPropagation();
          onOpenMenu(event, row);
        }}
      >
        <MoreVertIcon fontSize="small" />
      </IconButton>
    </Box>
  );
}

function RowActionMenu({
  anchorEl,
  row,
  onClose,
  onEdit,
  onPauseResume,
  onDelete,
}: {
  anchorEl: null | HTMLElement;
  row: RecurringPaymentRow | null;
  onClose: () => void;
  onEdit: (r: RecurringPaymentRow) => void;
  onPauseResume: (r: RecurringPaymentRow) => void;
  onDelete: (r: RecurringPaymentRow) => void;
}) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const open = Boolean(anchorEl) && Boolean(row);

  return (
    <Menu
      anchorEl={anchorEl}
      open={open}
      onClose={onClose}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      transformOrigin={{ vertical: 'top', horizontal: 'right' }}
    >
      {row ? (
        <>
          <MenuItem
            onClick={() => {
              onClose();
              onEdit(row);
            }}
          >
            <ListItemIcon>
              <EditIcon fontSize="small" />
            </ListItemIcon>
            <ListItemText>{t('recurringPayments.table.rowActions.edit')}</ListItemText>
          </MenuItem>

          <MenuItem
            onClick={() => {
              onClose();
              void onPauseResume(row);
            }}
          >
            <ListItemIcon>
              {row.status === RecurringPaymentStatus.Active ? (
                <PauseCircleOutlineIcon fontSize="small" />
              ) : (
                <PlayCircleOutlineIcon fontSize="small" />
              )}
            </ListItemIcon>
            <ListItemText>
              {row.status === RecurringPaymentStatus.Active
                ? t('recurringPayments.actions.pause')
                : t('recurringPayments.actions.resume')}
            </ListItemText>
          </MenuItem>

          <Divider />

          <MenuItem
            onClick={() => {
              onClose();
              onDelete(row);
            }}
            sx={{
              color: 'error.main',
              '&:hover': { bgcolor: alpha(theme.palette.error.main, 0.08) },
              '&.Mui-focusVisible': { bgcolor: alpha(theme.palette.error.main, 0.08) },
            }}
          >
            <ListItemIcon sx={{ color: 'inherit' }}>
              <DeleteOutlineIcon fontSize="small" />
            </ListItemIcon>
            <ListItemText>{t('recurringPayments.table.rowActions.remove')}</ListItemText>
          </MenuItem>
        </>
      ) : null}
    </Menu>
  );
}
