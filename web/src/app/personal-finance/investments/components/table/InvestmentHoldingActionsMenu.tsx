'use client';

import React from 'react';
import HistoryIcon from '@mui/icons-material/History';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import QueryStatsIcon from '@mui/icons-material/QueryStats';
import AddIcon from '@mui/icons-material/Add';
import RemoveIcon from '@mui/icons-material/Remove';
import EventIcon from '@mui/icons-material/Event';
import TimelineIcon from '@mui/icons-material/Timeline';
import CardGiftcardIcon from '@mui/icons-material/CardGiftcard';
import AssignmentIcon from '@mui/icons-material/Assignment';
import PercentIcon from '@mui/icons-material/Percent';
import PaymentsIcon from '@mui/icons-material/Payments';
import SavingsIcon from '@mui/icons-material/Savings';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import { alpha, Divider, ListItemIcon, ListItemText, Menu, MenuItem, Typography } from '@mui/material';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';
import {
  AMORTIZABLE_ASSET_TYPES,
  LENDABLE_ASSET_TYPES,
  getAssetGroupKey,
  FIXED_INCOME_GROUP_KEYS,
  type HoldingRow,
} from './investmentTableHelpers';

interface InvestmentHoldingActionsMenuProps {
  anchorEl: HTMLElement | null;
  holding: HoldingRow | null;
  deleting: boolean;
  onClose: () => void;
  onViewDetails: (holding: HoldingRow) => void;
  onViewEvents: (holding: HoldingRow) => Promise<void>;
  onRemove: (holding: HoldingRow) => void;
  onBuy?: () => void;
  onSell?: () => void;
  buyLabel?: string;
  sellLabel?: string;
  showTransactionActions?: boolean;
  onRecordProvento?: () => void;
  onRecordSplit?: () => void;
  onRecordBonus?: () => void;
  onRecordSubscription?: () => void;
  onRecordWithholdingTax?: () => void;
  onRecordAmortization?: () => void;
  onRecordStockLending?: () => void;
  onTransferCustody?: () => void;
}

export const InvestmentHoldingActionsMenu = React.memo(function InvestmentHoldingActionsMenu({
  anchorEl,
  holding,
  deleting,
  onClose,
  onViewDetails,
  onViewEvents,
  onRemove,
  onBuy,
  onSell,
  buyLabel,
  sellLabel,
  showTransactionActions = false,
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
  const isFixedIncomeHolding = holdingGroup ? FIXED_INCOME_GROUP_KEYS.has(holdingGroup) : false;
  const showCorporateEvents = holdingGroup === 'variableIncome' || holdingGroup === 'mutualFunds' || holdingGroup === 'pension';
  const showExtendedCorporateEvents = holdingGroup === 'variableIncome';
  const showWithholdingTaxAction = holding?.asset?.assetType === 'MUTUAL_FUND';
  const showAmortizationAction = AMORTIZABLE_ASSET_TYPES.has(holding?.asset?.assetType ?? '');
  const showStockLendingAction = LENDABLE_ASSET_TYPES.has(holding?.asset?.assetType ?? '');
  const showTransferCustodyAction = (holding?.quantity ?? 0) > 0;

  return (
    <Menu
      open={Boolean(anchorEl)}
      anchorEl={anchorEl}
      onClose={onClose}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      transformOrigin={{ vertical: 'top', horizontal: 'right' }}
    >
      {showTransactionActions ? (
        <>
          {buyLabel && onBuy ? (
            <MenuItem
              disabled={deleting || !holding}
              onClick={() => {
                if (!holding) return;
                onClose();
                onBuy();
              }}
            >
              <ListItemIcon sx={{ color: 'success.main' }}>
                <AddIcon fontSize="small" />
              </ListItemIcon>
              <ListItemText>{buyLabel}</ListItemText>
            </MenuItem>
          ) : null}

          {sellLabel && onSell ? (
            <MenuItem
              disabled={deleting || !holding}
              onClick={() => {
                if (!holding) return;
                onClose();
                onSell();
              }}
            >
              <ListItemIcon sx={{ color: 'error.main' }}>
                <RemoveIcon fontSize="small" />
              </ListItemIcon>
              <ListItemText>{sellLabel}</ListItemText>
            </MenuItem>
          ) : null}
        </>
      ) : null}

      {showWithholdingTaxAction ? (
        <MenuItem
          disabled={deleting || !holding || !onRecordWithholdingTax}
          onClick={() => {
            if (!holding || !onRecordWithholdingTax) return;
            onClose();
            onRecordWithholdingTax();
          }}
        >
          <ListItemIcon>
            <PercentIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t('investments.table.rowActions.recordWithholdingTax')}</ListItemText>
        </MenuItem>
      ) : null}

      {showAmortizationAction ? (
        <MenuItem
          disabled={deleting || !holding || !onRecordAmortization}
          onClick={() => {
            if (!holding || !onRecordAmortization) return;
            onClose();
            onRecordAmortization();
          }}
        >
          <ListItemIcon>
            <PaymentsIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t('investments.table.rowActions.recordAmortization')}</ListItemText>
        </MenuItem>
      ) : null}

      {showStockLendingAction ? (
        <MenuItem
          disabled={deleting || !holding || !onRecordStockLending}
          onClick={() => {
            if (!holding || !onRecordStockLending) return;
            onClose();
            onRecordStockLending();
          }}
        >
          <ListItemIcon>
            <SavingsIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t('investments.table.rowActions.recordStockLending')}</ListItemText>
        </MenuItem>
      ) : null}

      {showTransferCustodyAction ? (
        <MenuItem
          disabled={deleting || !holding || !onTransferCustody}
          onClick={() => {
            if (!holding || !onTransferCustody) return;
            onClose();
            onTransferCustody();
          }}
        >
          <ListItemIcon>
            <SwapHorizIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t('investments.table.rowActions.transferCustody')}</ListItemText>
        </MenuItem>
      ) : null}

      <MenuItem
        disabled={deleting || !holding}
        onClick={() => {
          if (!holding) return;
          onClose();
          onViewDetails(holding);
        }}
      >
        <ListItemIcon>
          <QueryStatsIcon fontSize="small" />
        </ListItemIcon>
        <ListItemText>{t('investments.table.rowActions.viewCurrentPosition')}</ListItemText>
      </MenuItem>

      {showCorporateEvents ? (
        <>
          <Divider />
          <Typography variant="overline" color="text.secondary" sx={{ px: 2, pt: 0.75, pb: 0.25, display: 'block' }}>
            {t('investments.corporateEvents.section')}
          </Typography>
          <MenuItem
            disabled={deleting || !holding || !onRecordProvento}
            onClick={() => {
              if (!holding || !onRecordProvento) return;
              onClose();
              onRecordProvento();
            }}
          >
            <ListItemIcon>
              <EventIcon fontSize="small" />
            </ListItemIcon>
            <ListItemText>{t('investments.corporateEvents.provento')}</ListItemText>
          </MenuItem>
          {showExtendedCorporateEvents ? (
            <MenuItem
              disabled={deleting || !holding || !onRecordSplit}
              onClick={() => {
                if (!holding || !onRecordSplit) return;
                onClose();
                onRecordSplit();
              }}
            >
              <ListItemIcon>
                <TimelineIcon fontSize="small" />
              </ListItemIcon>
              <ListItemText>{t('investments.corporateEvents.split')}</ListItemText>
            </MenuItem>
          ) : null}
          {showExtendedCorporateEvents ? (
            <MenuItem
              disabled={deleting || !holding || !onRecordBonus}
              onClick={() => {
                if (!holding || !onRecordBonus) return;
                onClose();
                onRecordBonus();
              }}
            >
              <ListItemIcon>
                <CardGiftcardIcon fontSize="small" />
              </ListItemIcon>
              <ListItemText>{t('investments.corporateEvents.bonus')}</ListItemText>
            </MenuItem>
          ) : null}
          {showExtendedCorporateEvents ? (
            <MenuItem
              disabled={deleting || !holding || !onRecordSubscription}
              onClick={() => {
                if (!holding || !onRecordSubscription) return;
                onClose();
                onRecordSubscription();
              }}
            >
              <ListItemIcon>
                <AssignmentIcon fontSize="small" />
              </ListItemIcon>
              <ListItemText>{t('investments.corporateEvents.subscription')}</ListItemText>
            </MenuItem>
          ) : null}
        </>
      ) : null}

      <MenuItem
        disabled={deleting || !holding}
        onClick={async () => {
          if (!holding) return;
          onClose();
          await onViewEvents(holding);
        }}
      >
        <ListItemIcon>
          <HistoryIcon fontSize="small" />
        </ListItemIcon>
        <ListItemText>{t('investments.table.rowActions.viewEvents')}</ListItemText>
      </MenuItem>

      <MenuItem
        disabled={deleting || !holding}
        onClick={() => {
          if (!holding) return;
          onClose();
          onRemove(holding);
        }}
        sx={(theme) => ({
          ...(isFixedIncomeHolding ? { borderTop: `1px solid ${alpha(theme.palette.divider, 0.7)}` } : {}),
          color: 'error.main',
          '&:hover': {
            bgcolor: alpha(theme.palette.error.main, 0.08),
          },
          '&.Mui-focusVisible': {
            bgcolor: alpha(theme.palette.error.main, 0.08),
          },
        })}
      >
        <ListItemIcon sx={{ color: 'inherit' }}>
          <DeleteOutlineIcon fontSize="small" />
        </ListItemIcon>
        <ListItemText>{t('investments.table.rowActions.remove')}</ListItemText>
      </MenuItem>
    </Menu>
  );
});
