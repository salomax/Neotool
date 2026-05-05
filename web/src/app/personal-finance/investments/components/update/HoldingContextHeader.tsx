'use client';

import * as React from 'react';
import { Avatar, Box, Typography } from '@mui/material';
import { InvestmentAssetIcon, getInvestmentIconKindFromAssetType } from '../InvestmentAssetIcon';
import { AssetTypeChip } from '../AssetTypeChip';
import type { UpdateHolding } from './types';

interface HoldingContextHeaderProps {
  holding: UpdateHolding;
}

export function HoldingContextHeader({ holding }: HoldingContextHeaderProps) {
  const iconKind = getInvestmentIconKindFromAssetType(holding.asset.assetType);
  const logoUrl = holding.asset.logoAsset?.publicUrl ?? null;

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2, minWidth: 0 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, minWidth: 0 }}>
        <Avatar
          src={logoUrl ?? undefined}
          sx={{ width: 34, height: 34, bgcolor: 'action.selected' }}
          aria-label={holding.asset.name}
        >
          <Box component="span" sx={{ display: 'inline-flex', color: 'text.secondary' }}>
            <InvestmentAssetIcon kind={iconKind} fontSize="small" />
          </Box>
        </Avatar>
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 600 }} noWrap>
            {holding.asset.name}
          </Typography>
          <Typography variant="body2" color="text.secondary" noWrap>
            {holding.asset.issuerName ?? '—'}
          </Typography>
        </Box>
      </Box>
      <AssetTypeChip assetType={holding.asset.assetType} size="small" />
    </Box>
  );
}
