'use client';

import React from 'react';
import { Box, alpha, useTheme } from '@mui/material';
import { useTranslation } from '@/shared/i18n';
import { Label } from '@/shared/components/ui/primitives/Label';
import { ValueOriginIndicator } from '@/shared/components/ui/data-display';
import { AssetTypeIcon, type AssetTypeIconKind } from '@/shared/components/ui/icons/AssetTypeIcon';
import { personalFinanceTranslations } from '../../../i18n';
import { InvestmentGroupSection } from '../InvestmentGroupSection';
import { InvestmentHoldingRow } from './InvestmentHoldingRow';
import { buildInvestmentGroupLayout } from './investmentTableLayout';
import type { HoldingRow, AssetGroupKey, InvestmentHoldingGroup } from './investmentTableHelpers';

const ICON_KIND_BY_GROUP: Record<AssetGroupKey, AssetTypeIconKind> = {
  bankFixedIncome: 'FIXED_INCOME',
  creditFixedIncome: 'FIXED_INCOME',
  variableIncome: 'STOCK',
  mutualFunds: 'FUND',
  pension: 'FUND',
  structured: 'STRUCTURED',
  others: 'OTHER',
};

interface InvestmentGroupTableProps {
  group: InvestmentHoldingGroup;
  expanded: boolean;
  locale: string;
  onToggleGroup: (groupKey: AssetGroupKey, expanded: boolean) => void;
  onOpenDetail: (holding: HoldingRow) => void;
  onOpenMaturityConfirm: (holding: HoldingRow) => void;
  onOpenActions: (anchorEl: HTMLElement, holding: HoldingRow) => void;
  onBuy?: (holding: HoldingRow) => void;
  onSell?: (holding: HoldingRow) => void;
  hideGroupHeader?: boolean;
}

export const InvestmentGroupTable = React.memo(function InvestmentGroupTable({
  group,
  expanded,
  locale,
  onToggleGroup,
  onOpenDetail,
  onOpenMaturityConfirm,
  onOpenActions,
  onBuy,
  onSell,
  hideGroupHeader = false,
}: InvestmentGroupTableProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();

  const layout = React.useMemo(() => buildInvestmentGroupLayout(group.key), [group.key]);
  const dividerColor = React.useMemo(() => alpha(theme.palette.divider, 0.7), [theme.palette.divider]);
  const hoverColor = React.useMemo(() => alpha(theme.palette.primary.main, 0.035), [theme.palette.primary.main]);
  const tableRadius = (theme as any).custom?.radius?.table ?? theme.shape.borderRadius;

  const leadingIcon = React.useMemo(
    () => <AssetTypeIcon kind={ICON_KIND_BY_GROUP[group.key]} sx={{ fontSize: 16 }} />,
    [group.key],
  );

  const summaryAdornment = React.useMemo(() => {
    if (!group.valueOriginKey || group.total === '—') return null;

    return (
      <ValueOriginIndicator
        origin={group.valueOriginKey}
        title={t(`investments.table.valueOrigin.labels.${group.valueOriginKey}`)}
        description={t(`investments.table.valueOrigin.descriptions.${group.valueOriginKey}`)}
        ariaLabel={t('investments.table.valueOrigin.ariaLabel')}
        offsetPx={18}
      />
    );
  }, [group.total, group.valueOriginKey, t]);

  const buyLabel = React.useMemo(() => {
    if (group.key === 'variableIncome') return t('investments.transactions.buy');
    if (group.key === 'mutualFunds' || group.key === 'pension') return t('investments.transactions.apply');
    return undefined;
  }, [group.key, t]);

  const sellLabel = React.useMemo(() => {
    if (group.key === 'variableIncome') return t('investments.transactions.sell');
    if (group.key === 'mutualFunds' || group.key === 'pension') return t('investments.transactions.redeem');
    if (group.key === 'bankFixedIncome' || group.key === 'creditFixedIncome') return t('investments.transactions.redeem');
    return undefined;
  }, [group.key, t]);

  const headerCells = React.useMemo(() => {
    const cells = [
      {
        key: 'asset',
        sx: layout.assetColSx,
        content: <Label>{t('investments.table.columns.asset')}</Label>,
      },
      ...(layout.isFixedIncomeGroup
        ? [
            {
              key: 'indexer',
              sx: layout.indexerColSx,
              content: <Label>{t('investments.table.columns.indexer')}</Label>,
            },
          ]
        : layout.isFundGroup
          ? []
          : [
            {
              key: 'type',
              sx: { width: layout.colWidth },
              content: <Label>{t('investments.table.columns.type')}</Label>,
            },
            ]),
      ...(layout.isFundGroup
        ? [
            {
              key: 'gain',
              align: 'right' as const,
              sx: layout.gainColSx,
              content: <Label>{t('investments.table.columns.gain')}</Label>,
            },
            {
              key: 'grossValue',
              align: 'right' as const,
              sx: layout.grossValueColSx,
              content: <Label>{t('investments.table.columns.grossValue')}</Label>,
            },
          ]
        : [
            ...(layout.isVariableIncomeGroup
              ? [
                  {
                    key: 'quantity',
                    sx: layout.maturityColSx,
                    content: <Label>{t('investments.table.columns.quantity')}</Label>,
                  },
                  {
                    key: 'averagePrice',
                    align: 'right' as const,
                    sx: { width: layout.colWidth },
                    content: <Label>{t('investments.table.columns.averagePrice')}</Label>,
                  },
                ]
              : [
                  {
                    key: 'maturityDate',
                    sx: layout.maturityColSx,
                    content: <Label>{t('investments.table.columns.maturityDate')}</Label>,
                  },
                ]),
            {
              key: 'gain',
              align: 'right' as const,
              sx: layout.gainColSx,
              content: <Label>{t('investments.table.columns.gain')}</Label>,
            },
            {
              key: 'grossValue',
              align: 'right' as const,
              sx: layout.grossValueColSx,
              content: <Label>{t('investments.table.columns.grossValue')}</Label>,
            },
          ]),
    ];

    return cells;
  }, [layout, t]);

  if (hideGroupHeader) {
    return (
      <Box sx={{ borderRadius: `${tableRadius}px`, overflow: 'hidden' }}>
        <Box sx={{ display: 'flex', alignItems: 'center', px: 2, py: 0.75 }}>
          {headerCells.map((cell) => (
            <Box key={cell.key} sx={{ textAlign: cell.align ?? 'left', ...cell.sx }}>
              {cell.content}
            </Box>
          ))}
          <Box sx={{ width: layout.actionColWidth, flexShrink: 0 }} />
        </Box>
        {group.holdings.map((holding) => (
          <InvestmentHoldingRow
            key={holding.id}
            holding={holding}
            layout={layout}
            locale={locale}
            dividerColor={dividerColor}
            hoverColor={hoverColor}
            t={t}
            onOpenDetail={onOpenDetail}
            onOpenMaturityConfirm={onOpenMaturityConfirm}
            onOpenActions={onOpenActions}
            onBuy={buyLabel ? onBuy : undefined}
            onSell={sellLabel ? onSell : undefined}
            buyLabel={buyLabel}
            sellLabel={sellLabel}
          />
        ))}
      </Box>
    );
  }

  return (
    <InvestmentGroupSection
      expanded={expanded}
      onToggle={(isExpanded) => onToggleGroup(group.key, isExpanded)}
      title={group.title}
      summary={group.summary}
      leadingIcon={leadingIcon}
      summaryAdornment={summaryAdornment}
      gainPercent={group.gainPercent}
      allocationPercent={group.allocationPercent}
      headerCells={headerCells}
      actionColWidth={layout.actionColWidth}
      tableRadius={tableRadius}
    >
      {group.holdings.map((holding) => (
        <InvestmentHoldingRow
          key={holding.id}
          holding={holding}
          layout={layout}
          locale={locale}
          dividerColor={dividerColor}
          hoverColor={hoverColor}
          t={t}
          onOpenDetail={onOpenDetail}
          onOpenMaturityConfirm={onOpenMaturityConfirm}
          onOpenActions={onOpenActions}
          onBuy={buyLabel ? onBuy : undefined}
          onSell={sellLabel ? onSell : undefined}
          buyLabel={buyLabel}
          sellLabel={sellLabel}
        />
      ))}
    </InvestmentGroupSection>
  );
});
