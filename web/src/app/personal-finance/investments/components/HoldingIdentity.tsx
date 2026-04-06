"use client";

import * as React from "react";
import { Box, Typography } from "@mui/material";
import { Logo, type LogoSize } from "@/shared/components/ui/primitives/Logo";

export interface HoldingIdentityProps {
  assetName: string;
  issuerName?: string | null;
  logoUrl?: string | null;
  size?: LogoSize;
  sx?: any;
}

interface HoldingIdentitySource {
  asset?: {
    name?: string | null;
    issuerName?: string | null;
    logoAsset?: { publicUrl?: string | null } | null;
  } | null;
  financialAccount?: {
    issuer?: {
      logoAsset?: { publicUrl?: string | null } | null;
    } | null;
  } | null;
}

export function resolveHoldingIdentity(source: HoldingIdentitySource) {
  return {
    assetName: source.asset?.name ?? "—",
    issuerName: source.asset?.issuerName ?? "—",
    logoUrl: source.asset?.logoAsset?.publicUrl ?? source.financialAccount?.issuer?.logoAsset?.publicUrl ?? undefined,
  };
}

export const HoldingIdentity: React.FC<HoldingIdentityProps> = ({ assetName, issuerName, logoUrl, size = 32, sx }) => {
  const fallback = (assetName ?? "—").slice(0, 1).toUpperCase();
  return (
    <Box sx={{ display: "flex", alignItems: "center", gap: 1.25, minWidth: 0, ...sx }}>
      <Logo src={logoUrl ?? undefined} alt={issuerName ?? assetName} size={size} sx={{ flexShrink: 0 }}>
        {fallback}
      </Logo>
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="body2" noWrap>
          {assetName}
        </Typography>
        {issuerName ? (
          <Typography variant="caption" color="text.secondary" noWrap sx={{ lineHeight: 1.2 }}>
            {issuerName}
          </Typography>
        ) : null}
      </Box>
    </Box>
  );
};

export default HoldingIdentity;
