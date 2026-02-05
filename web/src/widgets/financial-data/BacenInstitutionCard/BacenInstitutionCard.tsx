"use client";

import * as React from "react";
import { Card } from "@/shared/components/ui/primitives/Card";
import CardContent from "@mui/material/CardContent";
import Typography from "@mui/material/Typography";
import Stack from "@mui/material/Stack";
import Box from "@mui/material/Box";
import Avatar from "@mui/material/Avatar";
import {
  CurrencyDisplay,
  TrendIndicator,
  StatusIndicator,
  FinancialMetricItem,
} from "@/shared/components/ui/data-display";
import AccountBalanceIcon from "@mui/icons-material/AccountBalance";
import LockOutlinedIcon from "@mui/icons-material/LockOutlined";
import { useTheme } from "@mui/material/styles";
import { useTranslation } from "@/shared/i18n";
import { financialDataTranslations } from "@/shared/i18n/domains/financial-data";
import { formatPercentage, getPercentageColor } from "@/shared/utils/percentage";
import { getCustomThemeValue, getDerivedFontSize } from "@/shared/utils/theme";
import {
  getInstitutionTypeConfig,
  BASILEIA_THRESHOLDS,
} from "@/entities/financial-institution";

export interface BacenInstitution {
  id: string;
  name: string;
  legalName?: string | null;
  tradeName?: string | null;
  logoAsset?: {
    publicUrl?: string | null;
  } | null;
  profit?: number | null;
  equity?: number | null;
  yoyEquityGrowth?: number | null;
  basileiaIndex?: number | null;
  institutionType?: "INDIVIDUAL_INSTITUTION" | "PRUDENTIAL_GROUP" | "FINANCIAL_GROUP" | null;
  bacenInstitutionType?: string | null;
  tipoConsolidadoBancario?: string | null;
  /** When present and isRestricted, financial metrics are not displayed */
  dataRestriction?: { isRestricted: boolean; reason?: string | null } | null;
}

export interface BacenInstitutionCardProps {
  /**
   * Institution data
   */
  institution: BacenInstitution;
  /**
   * Click handler - navigates to institution detail page
   */
  onClick?: () => void;
}


/**
 * Bacen Institution Card component.
 *
 * Displays institution information in a card format with:
 * - Logo (circular, with fallback)
 * - Institution name and type
 * - Financial metrics (profit, equity, YoY growth, Basileia index)
 * - Institution type badge in footer
 *
 * @example
 * ```tsx
 * <BacenInstitutionCard
 *   institution={institution}
 *   onClick={() => navigate(`/bacen-institutions/${institution.id}`)}
 * />
 * ```
 */

export function BacenInstitutionCard({
  institution,
  onClick,
}: BacenInstitutionCardProps) {
  const { t } = useTranslation(financialDataTranslations);
  const theme = useTheme();
  const displayName = institution.tradeName || institution.name;
  const isRestricted = !!institution.dataRestriction?.isRestricted;

  // Memoize institution type configuration
  const typeConfig = React.useMemo(
    () =>
      getInstitutionTypeConfig(
        institution.institutionType,
        theme,
        t,
        institution.bacenInstitutionType || ""
      ),
    [institution.institutionType, institution.bacenInstitutionType, theme, t]
  );

  const Icon = typeConfig.icon;

  // Memoize Basileia index color
  const basileiaIndexColor = React.useMemo(
    () => {
      const percentage = institution.basileiaIndex != null ? institution.basileiaIndex * 100 : null;
      return getPercentageColor(percentage, BASILEIA_THRESHOLDS);
    },
    [institution.basileiaIndex]
  );

  // Handle keyboard navigation
  const handleKeyDown = React.useCallback(
    (event: React.KeyboardEvent) => {
      if (onClick && (event.key === "Enter" || event.key === " ")) {
        event.preventDefault();
        onClick();
      }
    },
    [onClick]
  );

  const hasLogo = !!institution.logoAsset?.publicUrl;

  // Extract theme tokens for cleaner code
  const borderWidth = getCustomThemeValue(theme, 'custom.border.default', 1);
  const avatarSize = theme.spacing(7); // 56px (7 * 8px)
  const iconSizeMedium = theme.typography.h5.fontSize; // 20px

  return (
    <Card
      onClick={onClick}
      hoverable
      onKeyDown={handleKeyDown}
      role={onClick ? "button" : undefined}
      tabIndex={onClick ? 0 : undefined}
      aria-label={onClick ? `${displayName}. ${t("messages.clickToViewDetails")}` : undefined}
      sx={{
        height: "100%",
        display: "flex",
        flexDirection: "column"
      }}
    >
      <CardContent sx={{ flex: 1, display: "flex", flexDirection: "column" }}>
        {/* Header: Logo and Name */}
        <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 3 }}>
          <Avatar
            src={hasLogo ? institution.logoAsset?.publicUrl || undefined : undefined}
            alt={displayName}
            sx={{
              width: avatarSize,
              height: avatarSize,
              borderRadius: 1.5, // Less rounded (12px)
              bgcolor: "background.default",
            }}
          >
            <AccountBalanceIcon sx={{ fontSize: 30, color: 'text.secondary' }} />
          </Avatar>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Stack direction="row" alignItems="center" spacing={1} sx={{ flexWrap: "wrap", gap: 0.5 }}>
              <Typography
                variant="h6"
                sx={{
                  fontWeight: 700,
                  fontSize: theme.typography.h6.fontSize, // 18px (1.125rem)
                  lineHeight: 1.2,
                  display: "-webkit-box",
                  WebkitLineClamp: 2,
                  WebkitBoxOrient: "vertical",
                  overflow: "hidden",
                  textOverflow: "ellipsis",
                }}
                title={displayName}
              >
                {displayName}
              </Typography>
              {isRestricted && (
                <Box
                  component="span"
                  sx={{
                    display: "inline-flex",
                    alignItems: "center",
                    color: "text.secondary",
                  }}
                  title={t("messages.dataRestricted")}
                  aria-label={t("messages.dataRestricted")}
                >
                  <LockOutlinedIcon sx={{ fontSize: 18 }} />
                </Box>
              )}
            </Stack>
          </Box>
        </Stack>

        {/* Financial Metrics Grid - hidden when data is restricted */}
        {!isRestricted && (
        <Box
          sx={{
            display: "grid",
            gridTemplateColumns: "repeat(2, 1fr)",
            gap: 2,
            mb: 1, // Reduced margin since we have footer
            mt: "auto", // Push to bottom of content area if needed
          }}
        >
          {/* Net Profit */}
          <FinancialMetricItem label={t("financialMetrics.netProfit")}>
            <CurrencyDisplay
              value={institution.profit}
              currency="BRL"
              locale="pt-BR"
              format="large"
              variant="body2"
              showColor={true}
            />
          </FinancialMetricItem>

          {/* Equity */}
          <FinancialMetricItem label={t("financialMetrics.equity")}>
            <CurrencyDisplay
              value={institution.equity}
              currency="BRL"
              locale="pt-BR"
              format="large"
              variant="body2"
              showColor={true}
            />
          </FinancialMetricItem>

          {/* YoY Growth */}
          <FinancialMetricItem label={t("financialMetrics.yoyGrowth")}>
            <TrendIndicator
              value={institution.yoyEquityGrowth}
              decimals={0}
              variant="body2"
            />
          </FinancialMetricItem>

          {/* Basel Index */}
          <FinancialMetricItem label={t("financialMetrics.baselIndex")}>
            <StatusIndicator
              color={institution.basileiaIndex ? basileiaIndexColor : 'none'}
              label={institution.basileiaIndex ? formatPercentage(institution.basileiaIndex, { decimals: 1 }) : '-'}
              ariaLabel={`Índice Basileia: ${formatPercentage(institution.basileiaIndex ?? 0, { decimals: 1 })}`}
              variant="body2"
            />
          </FinancialMetricItem>
        </Box>
        )}
        {isRestricted && (
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
            {t("messages.dataRestricted")}
          </Typography>
        )}
      </CardContent>

      {/* Footer: Institution Type */}
      <Box
        sx={{
          px: 2,
          py: 1.5,
          bgcolor: typeConfig.bgColor,
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          borderTop: `${borderWidth}px solid`,
          borderColor: "divider",
        }}
      >
        <Typography
          variant="caption"
          sx={{
            fontWeight: 700,
            color: typeConfig.color,
            textTransform: "uppercase",
            letterSpacing: "0.05em",
            fontSize: getDerivedFontSize(theme, 'body2', 11.2 / 14), // 11.2px from 14px base
          }}
        >
          {typeConfig.label}
        </Typography>
        <Icon
          sx={{
            color: typeConfig.color,
            fontSize: iconSizeMedium, // 20px from typography.h5
            opacity: 0.8, // Could be a token in the future
          }}
        />
      </Box>
    </Card>
  );
}

export default BacenInstitutionCard;
