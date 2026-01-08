"use client";

import * as React from "react";
import { Card } from "@/shared/components/ui/primitives/Card";
import CardContent from "@mui/material/CardContent";
import Typography from "@mui/material/Typography";
import Stack from "@mui/material/Stack";
import Box from "@mui/material/Box";
import Avatar from "@mui/material/Avatar";
import { formatLargeCurrency } from "@/shared/components/ui/forms/components/masks/br";
import TrendingUpIcon from "@mui/icons-material/TrendingUp";
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
 * Format Basileia index as percentage
 */
function formatBasileiaIndex(value: number | null | undefined): string {
  if (value == null) {
    return "0%";
  }

  return `${value.toFixed(1)}%`;
}

export interface BacenInstitution {
  id: string;
  name: string;
  legalName?: string | null;
  tradeName?: string | null;
  logoUrl?: string | null;
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
  const displayName = institution.tradeName || institution.name;
  
  // Format institutionType enum to readable label
  const getInstitutionTypeLabel = () => {
    if (!institution.institutionType) {
      return institution.bacenInstitutionType || "";
    }
    
    switch (institution.institutionType) {
      case "INDIVIDUAL_INSTITUTION":
        return t("institutionTypes.individualInstitution");
      case "PRUDENTIAL_GROUP":
        return t("institutionTypes.prudentialGroup");
      case "FINANCIAL_GROUP":
        return t("institutionTypes.financialGroup");
      default:
        return institution.bacenInstitutionType || "";
    }
  };
  
  const institutionTypeLabel = getInstitutionTypeLabel();

  return (
    <Card onClick={onClick} hoverable>
      <CardContent>
        {/* Header: Logo and Name */}
        <Stack direction="row" spacing={2} alignItems="center" sx={{ mb: 3 }}>
          <Avatar
            src={institution.logoUrl || undefined}
            alt={displayName}
            sx={{
              width: 56,
              height: 56,
              border: "1px solid",
              borderColor: "divider",
              bgcolor: "background.paper",
            }}
          >
            {displayName.charAt(0).toUpperCase()}
          </Avatar>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Typography
              variant="h6"
              sx={{
                fontWeight: 700,
                fontSize: "1.125rem",
                lineHeight: 1.2,
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
              }}
            >
              {displayName}
            </Typography>
            <Typography
              variant="caption"
              sx={{
                color: "text.secondary",
                fontWeight: 500,
                mt: 0.5,
                display: "block",
              }}
            >
              {institutionTypeLabel}
            </Typography>
          </Box>
        </Stack>

        {/* Financial Metrics Grid */}
        <Box
          sx={{
            display: "grid",
            gridTemplateColumns: "repeat(2, 1fr)",
            gap: 2,
            mb: 2,
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
              {formatLargeCurrency(
                institution.profit,
                "BRL",
                "pt-BR",
                {
                  billions: t("currency.billions"),
                  millions: t("currency.millions"),
                }
              )}
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
              {formatLargeCurrency(
                institution.equity,
                "BRL",
                "pt-BR",
                {
                  billions: t("currency.billions"),
                  millions: t("currency.millions"),
                }
              )}
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
              <TrendingUpIcon
                sx={{
                  fontSize: "1rem",
                  color: "success.main",
                }}
              />
              <Typography
                variant="body2"
                sx={{
                  fontWeight: 700,
                  fontSize: "0.875rem",
                  color: "success.main",
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
                  bgcolor: "success.main",
                  boxShadow: (theme) =>
                    `0 0 8px ${theme.palette.success.main}40`,
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
    </Card>
  );
}

export default BacenInstitutionCard;

