'use client';

import React from 'react';
import { Box, IconButton, Typography } from '@mui/material';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import { Chip } from '@/shared/components/ui/primitives/Chip';
import { PercentageGain, ValueOriginIndicator } from '@/shared/components/ui/data-display';
import { formatTableDate } from '@/shared/utils/date';
import { getHoldingValueOrigin } from '@/shared/utils/holdingValueOrigin';
import { HoldingIdentity, resolveHoldingIdentity } from '../HoldingIdentity';
import {
  computePrincipal,
  formatPercentageRate,
  formatQuantity,
  formatValueOrPlaceholder,
  type InvestmentTableTranslate,
} from './investmentTableRowHelpers';
import type { InvestmentGroupLayout } from './investmentTableLayout';
import type { HoldingRow } from './investmentTableHelpers';

const SECONDARY_ROW_SX = { mt: 0.25, minHeight: 16, display: 'flex', alignItems: 'center' } as const;

interface InvestmentHoldingRowProps {
  holding: HoldingRow;
  layout: InvestmentGroupLayout;
  locale: string;
  dividerColor: string;
  hoverColor: string;
  t: InvestmentTableTranslate;
  onOpenDetail: (holding: HoldingRow) => void;
  onOpenMaturityConfirm: (holding: HoldingRow) => void;
  onOpenActions: (anchorEl: HTMLElement, holding: HoldingRow) => void;
}

export const InvestmentHoldingRow = React.memo(function InvestmentHoldingRow({
  holding,
  layout,
  locale,
  dividerColor,
  hoverColor,
  t,
  onOpenDetail,
  onOpenMaturityConfirm,
  onOpenActions,
}: InvestmentHoldingRowProps) {
  const assetType = holding.asset?.assetType ?? null;
  const assetTypeLabel = assetType != null ? t(`investments.assetTypes.${assetType}`) : '—';
  const maturityLabel = holding.maturityDate ? formatTableDate(String(holding.maturityDate), locale) : '—';

  const principal = computePrincipal(holding);
  const gross = typeof holding.grossValue === 'number' ? holding.grossValue : null;
  const gain = gross != null && principal != null ? gross - principal : null;
  const gainPercent = gross != null && principal != null && principal > 0 ? (gross - principal) / principal : null;
  const valueOriginKey = getHoldingValueOrigin(holding);

  const isExpiredPending = Boolean(holding.isExpiredPending);
  const isExpired = Boolean(holding.isExpired);
  const showMaturityBadge = layout.isFixedIncomeGroup && (isExpiredPending || isExpired);
  const maturityBadgeLabel = isExpiredPending
    ? t('investments.table.maturityPendingBadge')
    : t('investments.table.expiredBadge');

  const indexerLabel = holding.indexer != null ? t(`investments.recordHolding.indexers.${holding.indexer}`) : null;
  const indexerRateLabel =
    typeof holding.indexerPercentage === 'number'
      ? formatPercentageRate(holding.indexerPercentage, locale)
      : typeof holding.fixedAnnualRate === 'number'
        ? formatPercentageRate(holding.fixedAnnualRate, locale)
        : null;
  const indexerDisplay = [indexerLabel, indexerRateLabel].filter(Boolean).join(' ');

  const identity = resolveHoldingIdentity(holding);

  return (
    <Box
      role="row"
      onClick={() => onOpenDetail(holding)}
      sx={{
        display: 'flex',
        alignItems: 'center',
        px: 2,
        py: 1.5,
        bgcolor: 'background.paper',
        borderTop: `1px solid ${dividerColor}`,
        cursor: 'pointer',
        '&:hover': { bgcolor: hoverColor },
      }}
    >
      <Box sx={{ ...layout.assetColSx, minWidth: 0 }}>
        <HoldingIdentity assetName={identity.assetName} issuerName={identity.issuerName} logoUrl={identity.logoUrl} size="lg" />
      </Box>

      {layout.isFixedIncomeGroup ? (
        <Box sx={{ ...layout.indexerColSx, minWidth: 0 }}>
          <Typography variant="body2" noWrap>
            {indexerDisplay || '—'}
          </Typography>
          <Box sx={SECONDARY_ROW_SX} />
        </Box>
      ) : (
        <Box sx={{ width: layout.colWidth, minWidth: 0 }}>
          <Typography variant="body2" noWrap>
            {assetTypeLabel}
          </Typography>
        </Box>
      )}

      {layout.isFundGroup ? null : layout.isVariableIncomeGroup ? (
        <>
          <Box sx={{ ...layout.maturityColSx, minWidth: 0 }}>
            <Typography variant="body2" noWrap>
              {formatQuantity(holding.quantity, locale)}
            </Typography>
            <Box sx={SECONDARY_ROW_SX} />
          </Box>

          <Box sx={{ width: layout.colWidth, minWidth: 0, textAlign: 'right' }}>
            <Typography variant="body2" noWrap>
              {formatValueOrPlaceholder(holding.averageCost, holding.asset?.currencyCode, locale)}
            </Typography>
            <Box sx={{ ...SECONDARY_ROW_SX, justifyContent: 'flex-end' }} />
          </Box>

          <Box sx={{ width: layout.colWidth, minWidth: 0, textAlign: 'right' }}>
            <Typography variant="body2" noWrap>
              —
            </Typography>
            <Box sx={{ ...SECONDARY_ROW_SX, justifyContent: 'flex-end' }} />
          </Box>
        </>
      ) : (
        <Box sx={{ ...layout.maturityColSx, minWidth: 0 }}>
          <Typography variant="body2" noWrap>
            {maturityLabel}
          </Typography>
          <Box sx={SECONDARY_ROW_SX}>
            {showMaturityBadge ? (
              <Chip
                onClick={(event) => {
                  event.stopPropagation();
                  onOpenMaturityConfirm(holding);
                }}
                label={
                  <Typography
                    variant="body2"
                    sx={{
                      fontSize: '0.75rem',
                      lineHeight: 1.2,
                      opacity: 0.75,
                    }}
                  >
                    {maturityBadgeLabel}
                  </Typography>
                }
                size="small"
                variant="outlined"
                color="default"
                clickable
                sx={{
                  height: 28,
                  bgcolor: 'action.selected',
                  borderColor: 'divider',
                  '& .MuiChip-label': { px: 1 },
                }}
              />
            ) : null}
          </Box>
        </Box>
      )}

      <Box sx={{ ...layout.gainColSx, minWidth: 0, textAlign: 'right' }}>
        <Typography variant="body2" noWrap>
          {formatValueOrPlaceholder(gain, holding.asset?.currencyCode, locale)}
        </Typography>
        <Box sx={{ ...SECONDARY_ROW_SX, justifyContent: 'flex-end' }}>
          {gainPercent != null ? <PercentageGain value={gainPercent} locale={locale} textSx={{ lineHeight: 1.2 }} /> : null}
        </Box>
      </Box>

      <Box sx={{ ...layout.grossValueColSx, minWidth: 0, textAlign: 'right' }}>
        <Box
          sx={{
            position: 'relative',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'flex-end',
            width: '100%',
            overflow: 'visible',
          }}
        >
          <Typography variant="body2" noWrap>
            {formatValueOrPlaceholder(gross, holding.asset?.currencyCode, locale)}
          </Typography>
          {gross != null ? (
            <ValueOriginIndicator
              origin={valueOriginKey}
              title={t(`investments.table.valueOrigin.labels.${valueOriginKey}`)}
              description={t(`investments.table.valueOrigin.descriptions.${valueOriginKey}`)}
              ariaLabel={t('investments.table.valueOrigin.ariaLabel')}
              offsetPx={18}
            />
          ) : null}
        </Box>
        <Box sx={{ ...SECONDARY_ROW_SX, justifyContent: 'flex-end' }} />
      </Box>

      <Box sx={{ width: layout.actionColWidth, flexShrink: 0, display: 'flex', justifyContent: 'flex-end' }}>
        <IconButton
          size="small"
          aria-label={t('investments.table.rowActions.ariaLabel')}
          onClick={(event) => {
            event.stopPropagation();
            onOpenActions(event.currentTarget, holding);
          }}
        >
          <MoreVertIcon fontSize="small" />
        </IconButton>
      </Box>
    </Box>
  );
});
