'use client';

import * as React from 'react';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import EditIcon from '@mui/icons-material/Edit';
import EventNoteIcon from '@mui/icons-material/EventNote';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import { Box, IconButton, ListItemIcon, ListItemText, Menu, MenuItem, Stack, Tooltip, Typography, alpha, useTheme } from '@mui/material';
import type { SxProps, Theme } from '@mui/material/styles';
import { CurrencyAmountParts } from '@/shared/components/ui/data-display';
import { EmptyState } from '@/shared/components/ui/feedback';
import { Label } from '@/shared/components/ui/primitives/Label';
import { InvestmentSectionLabel } from './InvestmentSectionLabel';

const DEFAULT_TIMELINE_ICON = <EventNoteIcon sx={{ fontSize: 16 }} />;
const OUTFLOW_DIRECTION = 'OUTFLOW' as const;

type TimelineDirection = 'INFLOW' | 'OUTFLOW';

function joinTimelineParts(parts: Array<string | null | undefined>): string | null {
  const values = parts.filter((part): part is string => Boolean(part));
  return values.length > 0 ? values.join(' · ') : null;
}

export interface TimelineEventsItem {
  id: string;
  title: string;
  tradeDateLabel: string;
  settlementDateLabel?: string | null;
  amount: number;
  netAmount?: number | null;
  incomeTax?: number | null;
  iof?: number | null;
  direction: TimelineDirection;
  source?: 'MANUAL' | 'OPEN_FINANCE' | 'FILE_IMPORT';
  isEditable?: boolean;
}

export function timelineEventDomId(id: string): string {
  return `timeline-event-${id}`;
}

export interface TimelineEventsProps {
  title: string;
  emptyTitle: string;
  emptyDescription: string;
  icon?: React.ReactNode;
  inflowLabel: string;
  outflowLabel: string;
  locale: string;
  currencyCode: string;
  items: readonly TimelineEventsItem[];
  formatCurrency: (value: number) => string;
  netAmountText: (formattedValue: string) => string;
  incomeTaxText: (formattedValue: string) => string;
  iofText: (formattedValue: string) => string;
  /** When set, shown before tax/net lines when `settlementDateLabel` is present on an item. */
  settlementDateText?: (settlementDateLabel: string) => string;
  onEditTransaction?: (transactionId: string) => void;
  onDeleteTransaction?: (transactionId: string) => void;
  /** Tooltip shown on disabled menu items for non-manual transactions */
  notEditableTooltip?: string;
  actionsAriaLabel?: string;
  editActionLabel?: string;
  deleteActionLabel?: string;
  sx?: SxProps<Theme>;
}

interface TimelineEventCardProps {
  item: TimelineEventsItem;
  locale: string;
  currencyCode: string;
  formatCurrency: (value: number) => string;
  netAmountText: (formattedValue: string) => string;
  incomeTaxText: (formattedValue: string) => string;
  iofText: (formattedValue: string) => string;
  settlementDateText?: (settlementDateLabel: string) => string;
  inflowLabel: string;
  outflowLabel: string;
  alignRightOnDesktop: boolean;
  onEditTransaction?: (transactionId: string) => void;
  onDeleteTransaction?: (transactionId: string) => void;
  notEditableTooltip?: string;
  actionsAriaLabel?: string;
  editActionLabel?: string;
  deleteActionLabel?: string;
}

const TimelineEventCard: React.FC<TimelineEventCardProps> = React.memo(function TimelineEventCard({
  item,
  locale,
  currencyCode,
  formatCurrency,
  netAmountText,
  incomeTaxText,
  iofText,
  settlementDateText,
  inflowLabel,
  outflowLabel,
  alignRightOnDesktop,
  onEditTransaction,
  onDeleteTransaction,
  notEditableTooltip,
  actionsAriaLabel,
  editActionLabel,
  deleteActionLabel,
}) {
  const theme = useTheme();
  const isOutflow = item.direction === OUTFLOW_DIRECTION;
  const directionLabel = isOutflow ? outflowLabel : inflowLabel;
  const directionColor = isOutflow ? 'error.main' : 'success.main';
  const amountValue = isOutflow ? (item.netAmount ?? item.amount) : item.amount;
  const hasDifferentSettlementDate = Boolean(
    item.settlementDateLabel &&
      item.settlementDateLabel.trim() &&
      item.settlementDateLabel.trim() !== item.tradeDateLabel.trim(),
  );
  const settlementPart =
    hasDifferentSettlementDate && settlementDateText && item.settlementDateLabel
      ? settlementDateText(item.settlementDateLabel)
      : null;
  const moneyParts = joinTimelineParts([
    !isOutflow && item.netAmount != null ? netAmountText(formatCurrency(item.netAmount)) : null,
    item.incomeTax != null ? incomeTaxText(formatCurrency(item.incomeTax)) : null,
    item.iof != null ? iofText(formatCurrency(item.iof)) : null,
  ]);
  const detailText = joinTimelineParts([settlementPart, moneyParts]);
  const textAlign = alignRightOnDesktop ? { xs: 'left', md: 'right' } : 'left';
  const [menuAnchor, setMenuAnchor] = React.useState<null | HTMLElement>(null);
  const [hovered, setHovered] = React.useState(false);
  const hasActions = Boolean(onEditTransaction || onDeleteTransaction);
  const isEditable = item.isEditable ?? item.source === 'MANUAL';
  const actionsVisible = hovered || Boolean(menuAnchor);
  const disabledTooltipTitle = !isEditable ? (notEditableTooltip ?? '') : '';
  const actionButtonAriaLabel = actionsAriaLabel ?? 'Transaction actions';
  const editLabel = editActionLabel ?? 'Edit';
  const deleteLabel = deleteActionLabel ?? 'Delete';

  const handleOpenMenu = (event: React.MouseEvent<HTMLElement>) => {
    event.stopPropagation();
    setMenuAnchor(event.currentTarget);
  };

  const handleCloseMenu = () => {
    setMenuAnchor(null);
  };

  return (
    <Box
      onMouseEnter={hasActions ? () => setHovered(true) : undefined}
      onMouseLeave={hasActions ? () => setHovered(false) : undefined}
      sx={{
        py: 1,
        minWidth: 0,
        width: '100%',
        position: 'relative',
        display: 'grid',
        gridTemplateColumns: 'minmax(0, 1fr) auto',
        columnGap: 2,
        alignItems: 'flex-start',
      }}
    >
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="caption" noWrap title={`${directionLabel} · ${item.tradeDateLabel}`} sx={{ minWidth: 0, width: '100%', textAlign, display: 'block' }}>
          <Box component="span" sx={{ fontWeight: 700, color: directionColor }}>
            {directionLabel}
          </Box>
          <Box component="span" sx={{ color: 'text.secondary' }}>
            {' '}
            · {item.tradeDateLabel}
          </Box>
        </Typography>
        <Label sx={{ mb: 0.25, textAlign }} title={item.title}>
          {item.title}
        </Label>
        {detailText ? (
          <Typography variant="caption" color="text.secondary" sx={{ mt: 0.25, display: 'block', textAlign }}>
            {detailText}
          </Typography>
        ) : null}
      </Box>

      <Stack direction="row" spacing={0.5} alignItems="flex-start" sx={{ minWidth: 0 }}>
        <CurrencyAmountParts
          value={amountValue}
          currency={currencyCode}
          locale={locale}
          variant="h6"
          fontWeight={500}
          noWrap
          sx={{ mt: 0.125, display: 'block', textAlign, lineHeight: 1.1, color: directionColor }}
        />

        {hasActions ? (
          <>
            <IconButton
              size="small"
              aria-label={actionButtonAriaLabel}
              onClick={handleOpenMenu}
              sx={{
                opacity: { xs: 1, md: actionsVisible ? 1 : 0.6 },
                pointerEvents: 'auto',
                transition: 'opacity 0.15s ease',
              }}
            >
              <MoreVertIcon fontSize="small" />
            </IconButton>

            <Menu
              anchorEl={menuAnchor}
              open={Boolean(menuAnchor)}
              onClose={handleCloseMenu}
              anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
              transformOrigin={{ vertical: 'top', horizontal: 'right' }}
            >
              <Tooltip
                title={disabledTooltipTitle}
                placement="left"
                disableHoverListener={isEditable}
                disableFocusListener={isEditable}
                disableTouchListener={isEditable}
              >
                <span>
                  <MenuItem
                    disabled={!isEditable || !onEditTransaction}
                    onClick={() => {
                      handleCloseMenu();
                      if (!onEditTransaction || !isEditable) return;
                      onEditTransaction(item.id);
                    }}
                  >
                    <ListItemIcon>
                      <EditIcon fontSize="small" />
                    </ListItemIcon>
                    <ListItemText>{editLabel}</ListItemText>
                  </MenuItem>
                </span>
              </Tooltip>

              <Tooltip
                title={disabledTooltipTitle}
                placement="left"
                disableHoverListener={isEditable}
                disableFocusListener={isEditable}
                disableTouchListener={isEditable}
              >
                <span>
                  <MenuItem
                    disabled={!isEditable || !onDeleteTransaction}
                    onClick={() => {
                      handleCloseMenu();
                      if (!onDeleteTransaction || !isEditable) return;
                      onDeleteTransaction(item.id);
                    }}
                    sx={{
                      color: 'error.main',
                      '&:hover': {
                        bgcolor: alpha(theme.palette.error.main, 0.08),
                      },
                      '&.Mui-focusVisible': {
                        bgcolor: alpha(theme.palette.error.main, 0.08),
                      },
                    }}
                  >
                    <ListItemIcon sx={{ color: 'inherit' }}>
                      <DeleteOutlineIcon fontSize="small" />
                    </ListItemIcon>
                    <ListItemText>{deleteLabel}</ListItemText>
                  </MenuItem>
                </span>
              </Tooltip>
            </Menu>
          </>
        ) : null}
      </Stack>
    </Box>
  );
});

interface TimelineEventRowProps extends Omit<TimelineEventCardProps, 'alignRightOnDesktop'> {
  dividerColor?: string;
  hoverColor: string;
  isFirst: boolean;
}

const TimelineEventRow: React.FC<TimelineEventRowProps> = React.memo(function TimelineEventRow({
  item,
  locale,
  currencyCode,
  formatCurrency,
  netAmountText,
  incomeTaxText,
  iofText,
  settlementDateText,
  inflowLabel,
  outflowLabel,
  onEditTransaction,
  onDeleteTransaction,
  notEditableTooltip,
  actionsAriaLabel,
  editActionLabel,
  deleteActionLabel,
  dividerColor,
  hoverColor,
  isFirst,
}) {
  const isOutflow = item.direction === OUTFLOW_DIRECTION;

  return (
    <Box
      id={timelineEventDomId(item.id)}
      tabIndex={-1}
      sx={{
        display: 'grid',
        gridTemplateColumns: '16px minmax(0, 1fr)',
        columnGap: 1.5,
        alignItems: 'stretch',
        scrollMarginTop: 96,
        bgcolor: 'background.paper',
        borderTopWidth: isFirst ? 0 : 1,
        borderTopStyle: isFirst ? 'none' : 'solid',
        borderTopColor: dividerColor ?? 'divider',
        transition: 'background-color 150ms linear',
        '&:hover': { bgcolor: hoverColor },
      }}
    >
      <Box sx={{ position: 'relative', minHeight: '100%', gridColumn: 1 }}>
        <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'flex-start', pt: 1.6, height: '100%' }}>
          <Box
            sx={{
              width: 8,
              height: 8,
              borderRadius: 999,
              bgcolor: isOutflow ? 'error.main' : 'success.main',
              position: 'relative',
              zIndex: 2,
            }}
          />
        </Box>
      </Box>

      <Box sx={{ minWidth: 0, gridColumn: 2 }}>
        <TimelineEventCard
          item={item}
          locale={locale}
          currencyCode={currencyCode}
          formatCurrency={formatCurrency}
          netAmountText={netAmountText}
          incomeTaxText={incomeTaxText}
          iofText={iofText}
          settlementDateText={settlementDateText}
          inflowLabel={inflowLabel}
          outflowLabel={outflowLabel}
          alignRightOnDesktop={false}
          onEditTransaction={onEditTransaction}
          onDeleteTransaction={onDeleteTransaction}
          notEditableTooltip={notEditableTooltip}
          actionsAriaLabel={actionsAriaLabel}
          editActionLabel={editActionLabel}
          deleteActionLabel={deleteActionLabel}
        />
      </Box>
    </Box>
  );
});

export const TimelineEvents: React.FC<TimelineEventsProps> = ({
  title,
  emptyTitle,
  emptyDescription,
  icon,
  inflowLabel,
  outflowLabel,
  locale,
  currencyCode,
  items,
  formatCurrency,
  netAmountText,
  incomeTaxText,
  iofText,
  settlementDateText,
  onEditTransaction,
  onDeleteTransaction,
  notEditableTooltip,
  actionsAriaLabel,
  editActionLabel,
  deleteActionLabel,
  sx,
}) => {
  const theme = useTheme();
  const dividerColor = React.useMemo(() => alpha(theme.palette.divider, 0.7), [theme.palette.divider]);
  const hoverColor = React.useMemo(() => alpha(theme.palette.primary.main, 0.035), [theme.palette.primary.main]);

  if (items.length === 0) {
    return <EmptyState title={emptyTitle} description={emptyDescription} />;
  }

  return (
    <Box sx={sx}>
      <InvestmentSectionLabel title={title} icon={icon ?? DEFAULT_TIMELINE_ICON} sx={{ mb: 2 }} />

      <Box sx={{ position: 'relative' }}>
        <Box
          sx={{
            position: 'absolute',
            top: 0,
            bottom: 0,
            left: 8,
            width: 2,
            bgcolor: 'divider',
            transform: 'translateX(-1px)',
            pointerEvents: 'none',
            zIndex: 1,
          }}
        />

        <Stack spacing={0}>
          {items.map((item, index) => (
            <TimelineEventRow
              key={item.id}
              item={item}
              locale={locale}
              currencyCode={currencyCode}
              formatCurrency={formatCurrency}
              netAmountText={netAmountText}
              incomeTaxText={incomeTaxText}
              iofText={iofText}
              settlementDateText={settlementDateText}
              inflowLabel={inflowLabel}
              outflowLabel={outflowLabel}
              onEditTransaction={onEditTransaction}
              onDeleteTransaction={onDeleteTransaction}
              notEditableTooltip={notEditableTooltip}
              actionsAriaLabel={actionsAriaLabel}
              editActionLabel={editActionLabel}
              deleteActionLabel={deleteActionLabel}
              dividerColor={dividerColor}
              hoverColor={hoverColor}
              isFirst={index === 0}
            />
          ))}
        </Stack>
      </Box>
    </Box>
  );
};

export default TimelineEvents;
