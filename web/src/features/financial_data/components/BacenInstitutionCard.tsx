"use client";

import * as React from "react";
import { Card } from "@/shared/components/ui/primitives/Card";
import CardContent from "@mui/material/CardContent";
import Typography from "@mui/material/Typography";
import Stack from "@mui/material/Stack";
import Box from "@mui/material/Box";
import Avatar from "@mui/material/Avatar";
import { CurrencyDisplay } from "@/shared/components/ui/data-display";
import BusinessIcon from "@mui/icons-material/Business";
import LocationCityIcon from "@mui/icons-material/LocationCity";
import TrendingUpIcon from "@mui/icons-material/TrendingUp";
import TrendingDownIcon from "@mui/icons-material/TrendingDown";
import AccountBalanceIcon from "@mui/icons-material/AccountBalance";
import { useTheme, alpha } from "@mui/material/styles";
import { useTranslation } from "@/shared/i18n";
import { financialDataTranslations } from "../i18n";

/**
 * Format percentage with sign
 * Example: 0.12 -> "+12%"
 */
function formatPercentage(value: number | null | undefined): string {
  if (value == null) {
    return "0%";
  }

  const percentage = value * 100;
  const sign = percentage >= 0 ? "+" : "";
  return `${sign}${percentage.toFixed(0)}%`;
}

/**
 * Format Basileia index as percentage with color coding
 * < 11% Critical red
 * 11%-14% appropriate yellow  
 * >14% Good green
 */
function formatBasileiaIndex(value: number | null | undefined): string {
  if (value == null) {
    return "0%";
  }

  const percentage = value * 100;
  return `${percentage.toFixed(1)}%`;
}

/**
 * Get Basileia index color based on value
 */
function getBasileiaIndexColor(value: number | null | undefined): string {
  if (value == null) {
    return "success.main"; // Default to green for null
  }

  const percentage = value * 100;
  if (percentage < 11) {
    return "error.main"; // Critical red
  } else if (percentage <= 14) {
    return "warning.main"; // Appropriate yellow
  } else {
    return "success.main"; // Good green
  }
}

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
  
  // Get configuration based on institution type
  const getTypeConfig = () => {
    const type = institution.institutionType;
    
    // Default fallback
    const defaultConfig = {
      label: institution.bacenInstitutionType || "",
      icon: BusinessIcon,
      color: theme.palette.text.secondary,
      bgColor: alpha(theme.palette.action.hover, 0.5),
    };

    if (!type) return defaultConfig;

    switch (type) {
      case "INDIVIDUAL_INSTITUTION":
        return {
          label: t("institutionTypes.individualInstitution"),
          icon: BusinessIcon,
          color: theme.palette.primary.main,
          bgColor: alpha(theme.palette.primary.main, 0.08),
        };
      case "PRUDENTIAL_GROUP":
        return {
          label: t("institutionTypes.prudentialGroup"),
          icon: LocationCityIcon,
          color: theme.palette.secondary.main,
          bgColor: alpha(theme.palette.secondary.main, 0.08),
        };
      case "FINANCIAL_GROUP":
        return {
          label: t("institutionTypes.financialGroup"),
          icon: LocationCityIcon,
          color: theme.palette.success.main,
          bgColor: alpha(theme.palette.success.main, 0.08),
        };
      default:
        return defaultConfig;
    }
  };

  const typeConfig = getTypeConfig();
  const Icon = typeConfig.icon;

  return (
    <Card 
      onClick={onClick} 
      hoverable 
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
            src={institution.logoAsset?.publicUrl || undefined}
            alt={displayName}
            sx={{
              width: 56,
              height: 56,
              border: "1px solid",
              borderColor: "divider",
              bgcolor: institution.logoAsset?.publicUrl 
                ? "background.paper" 
                : alpha(theme.palette.primary.main, 0.1),
            }}
          >
            {institution.logoAsset?.publicUrl ? (
              displayName.charAt(0).toUpperCase()
            ) : (
              <AccountBalanceIcon 
                sx={{ 
                  color: theme.palette.primary.main,
                  fontSize: 32,
                }} 
              />
            )}
          </Avatar>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography
              variant="h6"
              sx={{
                fontWeight: 700,
                fontSize: "1.125rem",
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
          </Box>
        </Stack>

        {/* Financial Metrics Grid */}
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
          <Box>
            <Typography
              variant="caption"
              sx={{
                fontSize: "0.625rem",
                fontWeight: 600,
                textTransform: "uppercase",
                letterSpacing: "0.05em",
                color: "text.secondary",
                display: "block",
                mb: 0.5,
              }}
            >
              {t("financialMetrics.netProfit")}
            </Typography>
            <Typography
              variant="body2"
              sx={{
                fontWeight: 700,
                fontSize: "0.875rem",
              }}
            >
              <CurrencyDisplay
              value={institution.profit}
              currency="BRL"
              locale="pt-BR"
              format="large"
              labels={{
                billions: t("currency.billions"),
                millions: t("currency.millions"),
              }}
              variant="body2"
              sx={{
                fontWeight: 700,
                fontSize: "0.875rem",
              }}
              showColor={true}
            />
            </Typography>
          </Box>

          {/* Equity */}
          <Box>
            <Typography
              variant="caption"
              sx={{
                fontSize: "0.625rem",
                fontWeight: 600,
                textTransform: "uppercase",
                letterSpacing: "0.05em",
                color: "text.secondary",
                display: "block",
                mb: 0.5,
              }}
            >
              {t("financialMetrics.equity")}
            </Typography>
            <Typography
              variant="body2"
              sx={{
                fontWeight: 700,
                fontSize: "0.875rem",
              }}
            >
              <CurrencyDisplay
              value={institution.equity}
              currency="BRL"
              locale="pt-BR"
              format="large"
              labels={{
                billions: t("currency.billions"),
                millions: t("currency.millions"),
              }}
              variant="body2"
              sx={{
                fontWeight: 700,
                fontSize: "0.875rem",
              }}
              showColor={true}
            />
            </Typography>
          </Box>

          {/* YoY Growth */}
          <Box>
            <Typography
              variant="caption"
              sx={{
                fontSize: "0.625rem",
                fontWeight: 600,
                textTransform: "uppercase",
                letterSpacing: "0.05em",
                color: "text.secondary",
                display: "block",
                mb: 0.5,
              }}
            >
              {t("financialMetrics.yoyGrowth")}
            </Typography>
            <Stack direction="row" spacing={0.5} alignItems="center">
              {institution.yoyEquityGrowth && institution.yoyEquityGrowth < 0 ? (
                <TrendingDownIcon
                  sx={{
                    fontSize: "1rem",
                    color: (theme) => theme.custom?.palette?.currencyNegative || "error.main",
                  }}
                />
              ) : (
                <TrendingUpIcon
                  sx={{
                    fontSize: "1rem",
                    color: (theme) => theme.custom?.palette?.currencyPositive || "success.main",
                  }}
                />
              )}
              <Typography
                variant="body2"
                sx={{
                  fontWeight: 700,
                  fontSize: "0.875rem",
                  color: institution.yoyEquityGrowth && institution.yoyEquityGrowth < 0 
                    ? (theme) => theme.custom?.palette?.currencyNegative || "error.main"
                    : (theme) => theme.custom?.palette?.currencyPositive || "success.main",
                }}
              >
                {formatPercentage(institution.yoyEquityGrowth)}
              </Typography>
            </Stack>
          </Box>

          {/* Basel Index */}
          <Box>
            <Typography
              variant="caption"
              sx={{
                fontSize: "0.625rem",
                fontWeight: 600,
                textTransform: "uppercase",
                letterSpacing: "0.05em",
                color: "text.secondary",
                display: "block",
                mb: 0.5,
              }}
            >
              {t("financialMetrics.baselIndex")}
            </Typography>
            <Stack direction="row" spacing={1} alignItems="center">
              <Box
                sx={{
                  width: 8,
                  height: 8,
                  borderRadius: "50%",
                  bgcolor: getBasileiaIndexColor(institution.basileiaIndex),
                  boxShadow: (theme) => {
                    const colorKey = getBasileiaIndexColor(institution.basileiaIndex);
                    const colorMap: Record<string, string> = {
                      "error.main": theme.palette.error.main,
                      "warning.main": theme.palette.warning.main,
                      "success.main": theme.palette.success.main
                    };
                    return `0 0 8px ${colorMap[colorKey]}40`;
                  },
                }}
              />
              <Typography
                variant="body2"
                sx={{
                  fontWeight: 700,
                  fontSize: "0.875rem",
                }}
              >
                {formatBasileiaIndex(institution.basileiaIndex)}
              </Typography>
            </Stack>
          </Box>
        </Box>
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
          borderTop: "1px solid",
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
            fontSize: "0.7rem",
          }}
        >
          {typeConfig.label}
        </Typography>
        <Icon
          sx={{
            color: typeConfig.color,
            fontSize: 20,
            opacity: 0.8,
          }}
        />
      </Box>
    </Card>
  );
}

export default BacenInstitutionCard;

