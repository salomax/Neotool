'use client';

import React from 'react';
import EditIcon from '@mui/icons-material/Edit';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import QueryStatsIcon from '@mui/icons-material/QueryStats';
import { alpha, ListItemIcon, ListItemText, Menu, MenuItem } from '@mui/material';
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
}

export const InvestmentHoldingActionsMenu = React.memo(function InvestmentHoldingActionsMenu({
  anchorEl,
  holding,
  deleting,
  onClose,
  onViewDetails,
  onEdit,
  onRemove,
}: InvestmentHoldingActionsMenuProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  const isFixedIncomeHolding = holding?.asset?.assetType
    ? FIXED_INCOME_GROUP_KEYS.has(getAssetGroupKey(holding.asset.assetType))
    : false;

  return (
    <Menu
      open={Boolean(anchorEl)}
      anchorEl={anchorEl}
      onClose={onClose}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      transformOrigin={{ vertical: 'top', horizontal: 'right' }}
    >
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
