"use client";

import * as React from "react";
import { Box, Typography } from "@mui/material";
import { useTheme } from "@mui/material/styles";
import { Logo, type LogoSize } from "@/shared/components/ui/primitives/Logo";
import { FixedIncomeTypeAvatar } from "./FixedIncomeTypeAvatar";

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
    ticker?: string | null;
    issuerName?: string | null;
    logoAsset?: { publicUrl?: string | null } | null;
  } | null;
  financialAccount?: {
    issuerName?: string | null;
    issuer?: {
      name?: string | null;
      logoAsset?: { publicUrl?: string | null } | null;
    } | null;
  } | null;
}

export function resolveHoldingIdentity(source: HoldingIdentitySource) {
  const assetName = source.asset?.ticker ?? source.asset?.name ?? "—";

  const custodianIssuerName =
    source.financialAccount?.issuer?.name ?? source.financialAccount?.issuerName ?? null;

  const secondaryLabel = source.asset?.ticker
    ? (source.asset?.name ?? source.asset?.issuerName ?? custodianIssuerName ?? null)
    : (source.asset?.issuerName ?? custodianIssuerName ?? null);

  const trimmedSecondary = secondaryLabel?.trim() || null;

  return {
    assetName,
    // Prefer a real secondary label; never render a placeholder hyphen as caption.
    issuerName: trimmedSecondary && trimmedSecondary !== "—" ? trimmedSecondary : null,
    logoUrl: source.asset?.logoAsset?.publicUrl ?? source.financialAccount?.issuer?.logoAsset?.publicUrl ?? undefined,
  };
}

export const HoldingIdentity: React.FC<HoldingIdentityProps> = ({ assetName, issuerName, logoUrl, size = "auto", sx }) => {
  const theme = useTheme();
  const logoBorderRadius = `${(theme as any).custom?.radius?.md ?? theme.shape.borderRadius}px`;
  const trimmedName = (assetName ?? "—").trim();
  const fallback = (trimmedName.slice(0, 1) || "—").toUpperCase();
  const resolvedLogoSrc =
    logoUrl != null && String(logoUrl).trim() !== "" ? String(logoUrl).trim() : undefined;
  return (
    <Box sx={{ display: "flex", alignItems: "flex-start", alignSelf: "flex-start", gap: 1.25, minWidth: 0, ...sx }}>
      <Logo
        src={resolvedLogoSrc}
        alt={issuerName ?? assetName}
        size={size}
        borderRadius={logoBorderRadius}
        sx={{ flexShrink: 0, alignSelf: "flex-start" }}
      >
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

export interface FixedIncomeAssetIdentityProps {
  assetType: string | null | undefined;
  assetName: string;
  issuerName?: string | null;
  size?: number | "auto";
  sx?: any;
  "data-testid"?: string;
}

export const FixedIncomeAssetIdentity: React.FC<FixedIncomeAssetIdentityProps> = ({
  assetType,
  assetName,
  issuerName,
  size = "auto",
  sx,
  "data-testid": dataTestId,
}) => {
  return (
    <Box sx={{ display: "flex", alignItems: "flex-start", gap: 1.25, minWidth: 0, ...sx }}>
      <FixedIncomeTypeAvatar assetType={assetType} size={size} data-testid={dataTestId} />
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="body2" noWrap>
          {assetName}
        </Typography>
        {issuerName ? (
          <Typography variant="caption" color="text.secondary" noWrap sx={{ lineHeight: 1.2, display: "block" }}>
            {issuerName}
          </Typography>
        ) : null}
      </Box>
    </Box>
  );
};

export default HoldingIdentity;
