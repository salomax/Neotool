'use client';

import React from 'react';
import { Chip } from '@mui/material';
import { alpha, useTheme, type Theme } from '@mui/material/styles';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { getAssetTypeIconKindFromAssetType, type AssetTypeIconKind } from '@/shared/components/ui/icons/AssetTypeIcon';
import { formatFundAssetType } from '@/lib/utils/mapCvmFundAssetType';

export interface AssetTypeChipProps {
  assetType: string | null | undefined;
  size?: 'small' | 'medium';
  variant?: 'outlined' | 'filled';
  sx?: Record<string, unknown>;
}

function getKindColor(kind: AssetTypeIconKind, palette: Theme['palette']): string {
  switch (kind) {
    case 'FIXED_INCOME':
      return palette.success.main;
    case 'FUND':
      return palette.secondary.main;
    case 'STOCK':
      return palette.primary.main;
    case 'REAL_ESTATE_FUND':
      return palette.warning.main;
    case 'CRYPTO':
      return palette.warning.dark;
    case 'OPTION':
      return palette.error.main;
    case 'DERIVATIVES':
      return palette.info.main;
    case 'STRUCTURED':
      return palette.info.dark;
    case 'PENSION':
      return palette.info.main;
    case 'OTHER':
    default:
      return palette.text.secondary as unknown as string;
  }
}

const FUND_LABEL_TYPES = new Set(['MUTUAL_FUND', 'FIXED_INCOME_FUND', 'EQUITY_FUND', 'MULTI_MARKET_FUND', 'FOREX_FUND', 'PGBL', 'VGBL']);

export function AssetTypeChip({ assetType, size = 'small', variant = 'outlined', sx }: AssetTypeChipProps) {
  const theme = useTheme();
  const { t } = useTranslation(personalFinanceTranslations);

  const kind = getAssetTypeIconKindFromAssetType(assetType);
  const color = getKindColor(kind, theme.palette);

  const label =
    assetType == null
      ? '—'
      : FUND_LABEL_TYPES.has(assetType)
      ? formatFundAssetType(assetType, t)
      : (t(`investments.assetTypes.${assetType}` as string) as string);

  return (
    <Chip
      size={size}
      variant={variant}
      label={label}
      sx={{
        bgcolor: alpha(color, 0.1),
        color,
        borderColor: alpha(color, 0.3),
        '& .MuiChip-label': { px: 1 },
        '&:hover': { bgcolor: alpha(color, 0.2) },
        ...sx,
      }}
    />
  );
}

export default AssetTypeChip;
