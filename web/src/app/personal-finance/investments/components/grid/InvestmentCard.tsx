"use client";

import React from "react";
import { Box, Button, Divider, IconButton, Typography, alpha, useTheme } from "@mui/material";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import OpenInNewOutlinedIcon from "@mui/icons-material/OpenInNewOutlined";
import { PercentageGain } from "@/shared/components/ui/data-display";
import { CurrencyAmountParts } from "@/shared/components/ui/data-display/CurrencyAmountParts";
import { MetricSparkline } from "@/shared/components/ui/data-display/MetricSparkline";
import { Logo } from "@/shared/components/ui/primitives";
import { InvestmentAssetIcon, type InvestmentIconKind } from "../InvestmentAssetIcon";
import type { TimeSeriesDataPoint } from "@/shared/types/charts";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../../i18n";

export interface InvestmentCardProps {
  cardTestId?: string;
  grossValueTestId?: string;
  title: string;
  assetCount?: number | null;
  grossValue: number;
  currency: string;
  percentageGain: number | null;
  iconKind: InvestmentIconKind;
  primaryActionLabel: string;
  secondaryActionLabel: string;
  chartData: TimeSeriesDataPoint[];
  /** When set, replaces the two primary/secondary footer buttons with one action (e.g. cash "Movimentar"). */
  singleActionLabel?: string;
  onSingleActionClick?: (event: React.MouseEvent) => void;
  /** Opens the class detail view; main card body becomes keyboard-activable (not the footer). */
  onOpenDetail?: () => void;
  /** CTA used by empty cards to open the corresponding record drawer. */
  onFirstInvestmentClick?: () => void;
}

export function InvestmentCard({
  cardTestId,
  grossValueTestId,
  title,
  assetCount,
  grossValue,
  currency,
  percentageGain,
  iconKind,
  primaryActionLabel,
  secondaryActionLabel,
  chartData,
  singleActionLabel,
  onSingleActionClick,
  onOpenDetail,
  onFirstInvestmentClick,
}: InvestmentCardProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const customTheme = theme as any;
  const positiveColor = customTheme.custom?.palette?.currencyPositive || theme.palette.success.main;
  const negativeColor = customTheme.custom?.palette?.currencyNegative || theme.palette.error.main;
  const chartColor = (percentageGain ?? 0) < 0 ? negativeColor : positiveColor;
  const chartHistorical = React.useMemo(
    () => chartData.map((point) => Number(point.value)).filter((value) => Number.isFinite(value)),
    [chartData],
  );
  const hasMiniChart = chartHistorical.length >= 1;
  const isEmptyState = assetCount === 0 && !hasMiniChart;
  const showAssetCount = typeof assetCount === "number" && assetCount > 0;
  const emptyStateIconMask = "linear-gradient(135deg, rgba(0,0,0,0) 0%, rgba(0,0,0,1) 100%)";

  const handleMainClick = React.useCallback(
    (event: React.MouseEvent<HTMLDivElement>) => {
      if (!onOpenDetail) return;
      if ((event.target as HTMLElement).closest("button, a[href], input, textarea, select")) {
        return;
      }
      onOpenDetail();
    },
    [onOpenDetail],
  );

  const handleMainKeyDown = React.useCallback(
    (event: React.KeyboardEvent<HTMLDivElement>) => {
      if (!onOpenDetail) return;
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        onOpenDetail();
      }
    },
    [onOpenDetail],
  );

  return (
    <Box
      data-testid={cardTestId}
      sx={{
        "--investment-empty-icon-from": theme.palette.mode === "dark" ? theme.palette.grey[500] : theme.palette.grey[600],
        "--investment-empty-icon-to": theme.palette.mode === "dark" ? theme.palette.grey[300] : theme.palette.grey[400],
        "--investment-empty-icon-opacity": 0.7,
        border: `1px solid ${alpha(theme.palette.divider, 0.7)}`,
        borderRadius: 2,
        minHeight: 180,
        display: "flex",
        flexDirection: "column",
        bgcolor: "background.paper",
        overflow: "hidden",
        cursor: onOpenDetail ? "default" : "pointer",
        transition: "box-shadow 180ms ease, transform 180ms ease, border-color 180ms ease",
        "&:hover": {
          "--investment-empty-icon-from": theme.palette.primary.dark,
          "--investment-empty-icon-to": theme.palette.primary.light,
          "--investment-empty-icon-opacity": 1,
          boxShadow: `0 10px 24px ${alpha(theme.palette.primary.main, 0.22)}`,
          borderColor: alpha(theme.palette.primary.main, 0.45),
          transform: "translateY(-1px)",
          "& .investment-card-mini-chart": {
            opacity: 1,
            filter: `drop-shadow(0 6px 10px ${alpha(chartColor, 0.18)}) saturate(1.25) contrast(1.08)`,
          },
        },
      }}
    >
      <Box
        sx={{
          position: "relative",
          px: 2,
          pt: 2,
          pb: 0,
          flex: 1,
          display: "flex",
          flexDirection: "column",
          ...(onOpenDetail ? { cursor: "pointer" } : {}),
        }}
        role={onOpenDetail ? "button" : undefined}
        tabIndex={onOpenDetail ? 0 : undefined}
        onClick={onOpenDetail ? handleMainClick : undefined}
        onKeyDown={onOpenDetail ? handleMainKeyDown : undefined}
        aria-label={onOpenDetail ? t("investments.grid.openClassDetail", { title }) : undefined}
      >
        {isEmptyState ? (
          <Box sx={{ flex: 1, display: "flex", alignItems: "center", justifyContent: "center", textAlign: "center", pb: 1 }}>
            <Box sx={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 1 }}>
              <Box component="span" sx={{ display: "inline-grid", lineHeight: 0, opacity: "var(--investment-empty-icon-opacity)" }}>
                <InvestmentAssetIcon
                  kind={iconKind}
                  sx={{ gridArea: "1 / 1", fontSize: 44, pointerEvents: "none", color: "var(--investment-empty-icon-from)" }}
                />
                <InvestmentAssetIcon
                  kind={iconKind}
                  sx={{
                    gridArea: "1 / 1",
                    fontSize: 44,
                    pointerEvents: "none",
                    color: "var(--investment-empty-icon-to)",
                    WebkitMaskImage: emptyStateIconMask,
                    maskImage: emptyStateIconMask,
                  }}
                  aria-hidden
                />
              </Box>
              <Typography variant="subtitle2" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
                {title}
              </Typography>
              <Typography variant="caption" sx={{ color: "text.disabled", fontSize: "0.72rem" }}>
                {t("investments.grid.emptyDescription")}
              </Typography>
            </Box>
          </Box>
        ) : (
          <>
        <Box sx={{ display: "flex", flexDirection: "column", alignItems: "flex-start", minWidth: 0, zIndex: 1 }}>
          <Box sx={{ display: "flex", alignItems: "center", gap: 1, minWidth: 0 }}>
            <Logo
              size="sm"
              sx={{
                bgcolor: alpha(theme.palette.primary.main, 0.08),
                color: "primary.main",
              }}
            >
              <InvestmentAssetIcon kind={iconKind} sx={{ fontSize: 16 }} />
            </Logo>
            <Typography variant="subtitle2" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
              {title}
            </Typography>
          </Box>
          <Typography
            variant="caption"
            sx={{
              display: "block",
              mt: 0.2,
              ml: `calc(26px + ${theme.spacing(1)})`,
              color: "text.disabled",
              fontSize: "0.68rem",
              visibility: showAssetCount ? "visible" : "hidden",
            }}
            aria-hidden={!showAssetCount}
          >
            {showAssetCount ? t("investments.grid.assetCount", { count: assetCount }) : "\u00A0"}
          </Typography>
        </Box>
        <Box
          sx={{
            position: "absolute",
            top: 12,
            right: 12,
            color: "action.disabled",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
          }}
          aria-hidden
        >
          <OpenInNewOutlinedIcon fontSize="small" />
        </Box>

        {hasMiniChart ? (
          <Box
            className="investment-card-mini-chart"
            sx={{
              position: "absolute",
              left: -4,
              right: -4,
              top: 26,
              bottom: 0,
              opacity: 0.55,
              pointerEvents: "none",
              zIndex: 0,
              filter: `drop-shadow(0 6px 10px ${alpha(chartColor, 0.12)})`,
              transition: "opacity 180ms ease, filter 180ms ease",
            }}
            aria-hidden
          >
            <MetricSparkline
              historical={chartHistorical}
              color={chartColor}
              height="100%"
              isAnimationActive={false}
              curveType="linear"
            />
          </Box>
        ) : null}

        <Box sx={{ flex: 1, display: "flex", flexDirection: "column", justifyContent: "flex-end", pt: 4, position: "relative", zIndex: 1 }}>
          <Box sx={{ display: "flex", alignItems: "flex-end", justifyContent: "space-between", gap: 1, pb: 1 }}>
            <Box sx={{ display: "flex", flexDirection: "column", gap: 0.5, minWidth: 0 }}>
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{ textTransform: "uppercase", letterSpacing: "0.06em", fontSize: "0.62rem" }}
              >
                Total bruto
              </Typography>
              <CurrencyAmountParts
                value={grossValue}
                currency={currency}
                variant="h6"
                data-testid={grossValueTestId}
                sx={{ fontWeight: 700, lineHeight: 1.15 }}
              />
            </Box>
            <PercentageGain
              value={percentageGain}
              variant="body2"
              iconSize={14}
              sx={{ flexShrink: 0 }}
              textSx={{ fontWeight: 600 }}
            />
          </Box>
        </Box>
          </>
        )}
      </Box>

      <Box
        sx={{
          mt: 0,
          pt: 1,
          pb: 0.75,
          px: 2,
          borderTop: `1px solid ${alpha(theme.palette.divider, 0.6)}`,
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 1,
        }}
      >
        {isEmptyState ? (
          <Button
            size="small"
            variant="text"
            onClick={(e) => {
              e.stopPropagation();
              onFirstInvestmentClick?.();
            }}
            sx={{
              minWidth: 0,
              px: 1,
              flex: 1,
              height: "100%",
              borderRadius: 1,
              justifyContent: "center",
              textTransform: "none",
              fontSize: theme.typography.body1.fontSize,
              fontWeight: 500,
            }}
            aria-label={`Incluir primeiro investimento - ${primaryActionLabel}`}
          >
            {t("investments.grid.firstInvestmentCta")}
          </Button>
        ) : (
          <>
            <Box sx={{ display: "flex", alignItems: "stretch", gap: 1, flex: 1 }}>
              {singleActionLabel ? (
                <Button
                  size="small"
                  variant="text"
                  onClick={(e) => {
                    e.stopPropagation();
                    onSingleActionClick?.(e);
                  }}
                  sx={{
                    minWidth: 0,
                    px: 1,
                    flex: 1,
                    height: "100%",
                    borderRadius: 1,
                    justifyContent: "center",
                    textTransform: "none",
                    fontSize: theme.typography.body1.fontSize,
                    fontWeight: 500,
                  }}
                >
                  {singleActionLabel}
                </Button>
              ) : (
                <>
                  <Button
                    size="small"
                    variant="text"
                    sx={{
                      minWidth: 0,
                      px: 1,
                      flex: 1,
                      height: "100%",
                      borderRadius: 1,
                      justifyContent: "center",
                      textTransform: "none",
                      fontSize: theme.typography.body1.fontSize,
                      fontWeight: 500,
                    }}
                  >
                    {primaryActionLabel}
                  </Button>
                  <Divider orientation="vertical" flexItem />
                  <Button
                    size="small"
                    variant="text"
                    sx={{
                      minWidth: 0,
                      px: 1,
                      flex: 1,
                      height: "100%",
                      borderRadius: 1,
                      justifyContent: "center",
                      textTransform: "none",
                      fontSize: theme.typography.body1.fontSize,
                      fontWeight: 500,
                    }}
                  >
                    {secondaryActionLabel}
                  </Button>
                </>
              )}
            </Box>
            {!singleActionLabel ? (
              <>
                <Divider orientation="vertical" flexItem />
                <IconButton
                  size="small"
                  aria-label={`more actions ${title}`}
                  sx={{
                    borderRadius: 1,
                    alignSelf: "stretch",
                    px: 1,
                  }}
                >
                  <MoreVertIcon fontSize="small" />
                </IconButton>
              </>
            ) : null}
          </>
        )}
      </Box>
    </Box>
  );
}
