'use client';

import React from 'react';
import EditIcon from '@mui/icons-material/Edit';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import QueryStatsIcon from '@mui/icons-material/QueryStats';
import SellIcon from '@mui/icons-material/Sell';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import EventIcon from '@mui/icons-material/Event';
import TimelineIcon from '@mui/icons-material/Timeline';
import CardGiftcardIcon from '@mui/icons-material/CardGiftcard';
import AssignmentIcon from '@mui/icons-material/Assignment';
import { alpha, Divider, ListItemIcon, ListItemText, Menu, MenuItem, Typography } from '@mui/material';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../../i18n';
import {
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
  onEdit: (holding: HoldingRow) => Promise<void>;
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
}

export const InvestmentHoldingActionsMenu = React.memo(function InvestmentHoldingActionsMenu({
  anchorEl,
  holding,
  deleting,
  onClose,
  onViewDetails,
  onEdit,
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
}: InvestmentHoldingActionsMenuProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  const holdingGroup = holding?.asset?.assetType ? getAssetGroupKey(holding.asset.assetType) : null;
  const isFixedIncomeHolding = holdingGroup ? FIXED_INCOME_GROUP_KEYS.has(holdingGroup) : false;
  const showCorporateEvents = holdingGroup === 'variableIncome' || holdingGroup === 'mutualFunds' || holdingGroup === 'pension';
  const showExtendedCorporateEvents = holdingGroup === 'variableIncome';

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
              <ListItemIcon>
                <ShoppingCartIcon fontSize="small" />
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
              <ListItemIcon>
                <SellIcon fontSize="small" />
              </ListItemIcon>
              <ListItemText>{sellLabel}</ListItemText>
            </MenuItem>
          ) : null}
        </>
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
        <ListItemText>{t('investments.table.rowActions.viewDetails')}</ListItemText>
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
          await onEdit(holding);
        }}
      >
        <ListItemIcon>
          <EditIcon fontSize="small" />
        </ListItemIcon>
        <ListItemText>{t('investments.table.rowActions.edit')}</ListItemText>
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
