'use client';

import React from 'react';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import ManageSearchIcon from '@mui/icons-material/ManageSearch';
import EventIcon from '@mui/icons-material/Event';
import TimelineIcon from '@mui/icons-material/Timeline';
import CardGiftcardIcon from '@mui/icons-material/CardGiftcard';
import AssignmentIcon from '@mui/icons-material/Assignment';
import PercentIcon from '@mui/icons-material/Percent';
import PaymentsIcon from '@mui/icons-material/Payments';
import SavingsIcon from '@mui/icons-material/Savings';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import type { SvgIconComponent } from '@mui/icons-material';
import { alpha, Divider, ListItemIcon, ListItemText, Menu, MenuItem } from '@mui/material';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';
import {
  AMORTIZABLE_ASSET_TYPES,
  LENDABLE_ASSET_TYPES,
  getAssetGroupKey,
  type HoldingRow,
} from './investmentTableHelpers';

interface InvestmentHoldingActionsMenuProps {
  anchorEl: HTMLElement | null;
  holding: HoldingRow | null;
  deleting: boolean;
  onClose: () => void;
  onViewDetails: (holding: HoldingRow) => void;
  onRemove: (holding: HoldingRow) => void;
  onRecordProvento?: () => void;
  onRecordSplit?: () => void;
  onRecordBonus?: () => void;
  onRecordSubscription?: () => void;
  onRecordWithholdingTax?: () => void;
  onRecordAmortization?: () => void;
  onRecordStockLending?: () => void;
  onTransferCustody?: () => void;
}

interface InvestmentActionMenuItemConfig {
  id: string;
  label: string;
  icon: SvgIconComponent;
  iconColor?: string;
  destructive?: boolean;
  disabled?: boolean;
  onClick: () => void;
}

interface InvestmentActionMenuSectionProps {
  items: InvestmentActionMenuItemConfig[];
  showDivider?: boolean;
}

const InvestmentActionMenuSection = React.memo(function InvestmentActionMenuSection({
  items,
  showDivider = false,
}: InvestmentActionMenuSectionProps) {
  if (items.length === 0) return null;

  return (
    <>
      {showDivider ? <Divider /> : null}
      {items.map((item) => {
        const Icon = item.icon;
        return (
          <MenuItem
            key={item.id}
            disabled={item.disabled}
            onClick={item.onClick}
            sx={
              item.destructive
                ? {
                    color: 'error.main',
                    '&:hover': {
                      bgcolor: (theme) => alpha(theme.palette.error.main, 0.08),
                    },
                    '&.Mui-focusVisible': {
                      bgcolor: (theme) => alpha(theme.palette.error.main, 0.08),
                    },
                  }
                : undefined
            }
          >
            <ListItemIcon sx={item.destructive ? { color: 'inherit' } : item.iconColor ? { color: item.iconColor } : undefined}>
              <Icon fontSize="small" />
            </ListItemIcon>
            <ListItemText>{item.label}</ListItemText>
          </MenuItem>
        );
      })}
    </>
  );
});

export const InvestmentHoldingActionsMenu = React.memo(function InvestmentHoldingActionsMenu({
  anchorEl,
  holding,
  deleting,
  onClose,
  onViewDetails,
  onRemove,
  onRecordProvento,
  onRecordSplit,
  onRecordBonus,
  onRecordSubscription,
  onRecordWithholdingTax,
  onRecordAmortization,
  onRecordStockLending,
  onTransferCustody,
}: InvestmentHoldingActionsMenuProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  const holdingGroup = holding?.asset?.assetType ? getAssetGroupKey(holding.asset.assetType) : null;
  const showCorporateEvents = holdingGroup === 'variableIncome' || holdingGroup === 'mutualFunds' || holdingGroup === 'pension';
  const showExtendedCorporateEvents = holdingGroup === 'variableIncome';
  const showWithholdingTaxAction = holding?.asset?.assetType === 'MUTUAL_FUND';
  const showAmortizationAction = AMORTIZABLE_ASSET_TYPES.has(holding?.asset?.assetType ?? '');
  const showStockLendingAction = LENDABLE_ASSET_TYPES.has(holding?.asset?.assetType ?? '');
  const showTransferCustodyAction = (holding?.quantity ?? 0) > 0;
  const isDisabled = deleting || !holding;

  const withClose = React.useCallback(
    (action?: () => void) => () => {
      if (!holding || !action) return;
      onClose();
      action();
    },
    [holding, onClose],
  );

  const coreActions = React.useMemo<InvestmentActionMenuItemConfig[]>(() => {
    const items: InvestmentActionMenuItemConfig[] = [];

    items.push({
      id: 'view-current-position',
      label: t('investments.table.rowActions.viewCurrentPosition'),
      icon: ManageSearchIcon,
      disabled: isDisabled,
      onClick: () => {
        if (!holding) return;
        onClose();
        onViewDetails(holding);
      },
    });

    return items;
  }, [
    holding,
    isDisabled,
    onClose,
    onViewDetails,
    t,
  ]);

  const specificActions = React.useMemo<InvestmentActionMenuItemConfig[]>(() => {
    const items: InvestmentActionMenuItemConfig[] = [];

    if (showTransferCustodyAction && onTransferCustody) {
      items.push({
        id: 'transfer-custody',
        label: t('investments.table.rowActions.transferCustody'),
        icon: SwapHorizIcon,
        disabled: isDisabled,
        onClick: withClose(onTransferCustody),
      });
    }

    if (showWithholdingTaxAction && onRecordWithholdingTax) {
      items.push({
        id: 'record-withholding-tax',
        label: t('investments.table.rowActions.recordWithholdingTax'),
        icon: PercentIcon,
        disabled: isDisabled,
        onClick: withClose(onRecordWithholdingTax),
      });
    }

    if (showAmortizationAction && onRecordAmortization) {
      items.push({
        id: 'record-amortization',
        label: t('investments.table.rowActions.recordAmortization'),
        icon: PaymentsIcon,
        disabled: isDisabled,
        onClick: withClose(onRecordAmortization),
      });
    }

    if (showStockLendingAction && onRecordStockLending) {
      items.push({
        id: 'record-stock-lending',
        label: t('investments.table.rowActions.recordStockLending'),
        icon: SavingsIcon,
        disabled: isDisabled,
        onClick: withClose(onRecordStockLending),
      });
    }

    if (showCorporateEvents && onRecordProvento) {
      items.push({
        id: 'record-provento',
        label: t('investments.corporateEvents.provento'),
        icon: EventIcon,
        disabled: isDisabled,
        onClick: withClose(onRecordProvento),
      });
    }

    if (showExtendedCorporateEvents && onRecordSplit) {
      items.push({
        id: 'record-split',
        label: t('investments.corporateEvents.split'),
        icon: TimelineIcon,
        disabled: isDisabled,
        onClick: withClose(onRecordSplit),
      });
    }

    if (showExtendedCorporateEvents && onRecordBonus) {
      items.push({
        id: 'record-bonus',
        label: t('investments.corporateEvents.bonus'),
        icon: CardGiftcardIcon,
        disabled: isDisabled,
        onClick: withClose(onRecordBonus),
      });
    }

    if (showExtendedCorporateEvents && onRecordSubscription) {
      items.push({
        id: 'record-subscription',
        label: t('investments.corporateEvents.subscription'),
        icon: AssignmentIcon,
        disabled: isDisabled,
        onClick: withClose(onRecordSubscription),
      });
    }

    return items;
  }, [
    isDisabled,
    onRecordAmortization,
    onRecordBonus,
    onRecordProvento,
    onRecordSplit,
    onRecordStockLending,
    onRecordSubscription,
    onRecordWithholdingTax,
    onTransferCustody,
    showAmortizationAction,
    showCorporateEvents,
    showExtendedCorporateEvents,
    showStockLendingAction,
    showTransferCustodyAction,
    showWithholdingTaxAction,
    t,
    withClose,
  ]);

  const dangerActions = React.useMemo<InvestmentActionMenuItemConfig[]>(
    () => [
      {
        id: 'remove-holding',
        label: t('investments.table.rowActions.remove'),
        icon: DeleteOutlineIcon,
        destructive: true,
        disabled: isDisabled,
        onClick: () => {
          if (!holding) return;
          onClose();
          onRemove(holding);
        },
      },
    ],
    [holding, isDisabled, onClose, onRemove, t],
  );

  return (
    <Menu
      open={Boolean(anchorEl)}
      anchorEl={anchorEl}
      onClose={onClose}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      transformOrigin={{ vertical: 'top', horizontal: 'right' }}
    >
      <InvestmentActionMenuSection items={coreActions} />
      <InvestmentActionMenuSection items={specificActions} showDivider={coreActions.length > 0} />
      {dangerActions.length > 0 && (coreActions.length > 0 || specificActions.length > 0) ? <Divider /> : null}
      <InvestmentActionMenuSection items={dangerActions} />
    </Menu>
  );
});
