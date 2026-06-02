"use client";

import * as React from "react";
import { alpha, Box, Button, Paper, Typography, useTheme } from "@mui/material";
import { useTranslation } from "@/shared/i18n";
import { useLogoBorderColor } from "@/shared/hooks/ui/useLogoBorderColor";
import { personalFinanceTranslations } from "../../../../i18n";
import { mapB3AssetType } from "@/lib/utils/mapB3AssetType";
import { formatCnpj } from "@/lib/utils/formatCnpj";
import type { NewHoldingEligibleClass, NewHoldingGateSelection } from "./newHoldingFlow.types";
import type { InvestmentIconKind } from "../../InvestmentAssetIcon";
import { getInvestmentIconKindFromAssetType, InvestmentAssetIcon } from "../../InvestmentAssetIcon";

/** Same as {@link FinancialAccountSelector} medium `IssuerAvatar` (not `small`). */
const AVATAR_SIZE_PX = 40;

export interface NewHoldingAssetIdentityProps {
  assetClass: NewHoldingEligibleClass;
  selection: NewHoldingGateSelection;
  onChangeAsset: () => void;
}

/** Maps step-1 gate output to primary / secondary labels (same lines as list rows / autocomplete). */
export function resolveNewHoldingGateIdentity(selection: NewHoldingGateSelection): {
  assetName: string;
  issuerName?: string | null;
} {
  switch (selection.kind) {
    case "fund_cvm": {
      const cnpj = selection.fund.cnpj?.trim();
      return {
        assetName: selection.fund.name,
        issuerName: cnpj ? formatCnpj(cnpj) : null,
      };
    }
    case "fund_manual": {
      const cnpj = selection.manualFundCnpj?.trim();
      return {
        assetName: selection.manualFundName.trim(),
        issuerName: cnpj || null,
      };
    }
    case "equity": {
      const ticker = selection.asset.ticker?.trim();
      return {
        assetName: ticker || selection.asset.name,
        issuerName: ticker ? selection.asset.name : null,
      };
    }
    case "crypto": {
      const ticker = selection.ticker.trim().toUpperCase();
      const subtitle = [selection.name.trim(), selection.network?.trim()].filter(Boolean).join(" · ");
      return {
        assetName: ticker,
        issuerName: subtitle || null,
      };
    }
  }
}

function selectionAssetLogoUrl(selection: NewHoldingGateSelection): string | undefined {
  if (selection.kind !== "equity") return undefined;
  const u = selection.asset.logoUrl?.trim();
  return u || undefined;
}

function selectionInvestmentIconKind(selection: NewHoldingGateSelection): InvestmentIconKind {
  switch (selection.kind) {
    case "fund_cvm":
      return getInvestmentIconKindFromAssetType(selection.fund.assetType);
    case "fund_manual":
      return getInvestmentIconKindFromAssetType(selection.manualAssetType);
    case "equity":
      return getInvestmentIconKindFromAssetType(mapB3AssetType(selection.asset.assetType));
    case "crypto":
      return "CRYPTO";
  }
}

function selectionLogoAlt(selection: NewHoldingGateSelection): string {
  switch (selection.kind) {
    case "fund_cvm":
      return selection.fund.name;
    case "fund_manual":
      return selection.manualFundName.trim();
    case "equity":
      return selection.asset.name;
    case "crypto":
      return selection.name.trim();
  }
}

/**
 * Rounded asset tile matching autocomplete: optional `img` when URL works, otherwise `InvestmentAssetIcon`.
 * Avoids MUI `Avatar`+`src` showing an empty `<img>` slot (hides icon until load/error).
 */
export function NewHoldingStepAssetAvatar({
  logoSrc,
  logoAlt,
  iconKind,
  /** When true (e.g. “Últimos selecionados”), skip tinted photo shadow. */
  disablePhotoShadow = false,
}: {
  logoSrc?: string;
  logoAlt: string;
  iconKind: InvestmentIconKind;
  disablePhotoShadow?: boolean;
}) {
  const theme = useTheme();
  const [photoFailed, setPhotoFailed] = React.useState(false);

  React.useEffect(() => {
    setPhotoFailed(false);
  }, [logoSrc]);

  const borderRadius = (theme as { custom?: { radius?: { lg?: number } } }).custom?.radius?.lg != null
    ? `${(theme as { custom: { radius: { lg: number } } }).custom.radius.lg}px`
    : "12px";
  const showPhoto = Boolean(logoSrc) && !photoFailed;
  const computedBorderColor = useLogoBorderColor(disablePhotoShadow ? undefined : showPhoto ? logoSrc : undefined);
  const boxShadow =
    disablePhotoShadow || !(logoSrc && computedBorderColor && showPhoto)
      ? "none"
      : `4px 4px 8px 3px ${alpha(computedBorderColor, 0.18)}`;

  return (
    <Box
      sx={{
        width: AVATAR_SIZE_PX,
        height: AVATAR_SIZE_PX,
        borderRadius,
        flexShrink: 0,
        alignSelf: "center",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        overflow: "hidden",
        bgcolor: showPhoto ? "transparent" : "action.hover",
        color: "text.secondary",
        border: "none",
        boxSizing: "border-box",
        boxShadow,
        transition: theme.transitions.create(["box-shadow", "border-color"], {
          duration: theme.transitions.duration.shortest,
        }),
      }}
    >
      {showPhoto ? (
        <Box
          component="img"
          src={logoSrc}
          alt={logoAlt}
          onError={() => setPhotoFailed(true)}
          sx={{ width: "100%", height: "100%", objectFit: "contain" }}
        />
      ) : (
        <InvestmentAssetIcon kind={iconKind} sx={{ fontSize: 25 }} />
      )}
    </Box>
  );
}

/**
 * Step-2 header: **asset** tile like autocomplete (`img` or `InvestmentAssetIcon`), text column, **Alterar**.
 * Container matches {@link FinancialAccountSelector} trigger: `Paper` outlined, `borderRadius: 2`, `p: 2`.
 * Primary / secondary labels use the same Typography as the selector (medium): `subtitle1` + `fontWeight="bold"`, then `body2` + `text.secondary`.
 */
export function NewHoldingAssetIdentity({ assetClass: _assetClass, selection, onChangeAsset }: NewHoldingAssetIdentityProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const { assetName, issuerName } = React.useMemo(() => resolveNewHoldingGateIdentity(selection), [selection]);
  const logoSrc = React.useMemo(() => selectionAssetLogoUrl(selection), [selection]);
  const iconKind = React.useMemo(() => selectionInvestmentIconKind(selection), [selection]);
  const logoAlt = React.useMemo(() => selectionLogoAlt(selection), [selection]);

  return (
    <Paper
      elevation={0}
      variant="outlined"
      sx={{
        p: 2,
        mb: 2,
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: 2,
        borderRadius: 2,
        bgcolor: "background.paper",
        borderColor: "divider",
        transition: (theme) => theme.transitions.create(["border-color", "background-color"], { duration: theme.transitions.duration.shortest }),
      }}
    >
      <Box sx={{ display: "flex", alignItems: "center", gap: 2, minWidth: 0, flex: "1 1 auto" }}>
        <NewHoldingStepAssetAvatar logoSrc={logoSrc} logoAlt={logoAlt} iconKind={iconKind} />
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="subtitle1" fontWeight="bold" noWrap>
            {assetName}
          </Typography>
          {issuerName ? (
            <Typography variant="body2" color="text.secondary" noWrap>
              {issuerName}
            </Typography>
          ) : null}
        </Box>
      </Box>
      <Button size="small" variant="text" onClick={onChangeAsset} sx={{ flexShrink: 0 }}>
        {t("investments.newHoldingFlow.changeAsset")}
      </Button>
    </Paper>
  );
}
