"use client";

import * as React from "react";
import { Avatar, Box, Typography } from "@mui/material";
import { Label } from "@/shared/components/ui/primitives/Label";
import { CurrencyAmountParts, GainPercent } from "@/shared/components/ui/data-display";
import { FixedIncomeTypeAvatar } from "./FixedIncomeTypeAvatar";
import {
  presentHoldingDetailHeaderLabels,
  type HoldingDetailHeaderModel,
} from "./holdingDetailHeaderPresenter";

export type HoldingDetailHeaderVariant = "fixedIncome" | "fund" | "equity";

export interface HoldingDetailHeaderProps {
  model: HoldingDetailHeaderModel;
  hideMetrics?: boolean;
  trailingContent?: React.ReactNode;
}

export function HoldingDetailHeader({
  model,
  hideMetrics = false,
  trailingContent,
}: HoldingDetailHeaderProps) {
  const labels = React.useMemo(() => presentHoldingDetailHeaderLabels(model), [model]);
  const showHeaderMeta = Boolean(labels.metaLabel);
  const showMetrics = !hideMetrics;
  const hasTrailing = showMetrics || Boolean(trailingContent);

  return (
    <Box sx={{ display: "flex", alignItems: "flex-start", justifyContent: hasTrailing ? "space-between" : "flex-start", gap: 2, mb: 2 }}>
      <Box sx={{ display: "flex", alignItems: "flex-start", gap: 1.5, minWidth: 0, flex: "1 1 auto" }}>
        {model.variant === "fixedIncome" ? (
          <Box sx={{ mt: 0.25, flexShrink: 0 }}>
            <FixedIncomeTypeAvatar assetType={model.assetType ?? null} size={40} />
          </Box>
        ) : (
          <Avatar
            src={model.logoUrl}
            alt={model.assetName}
            variant="rounded"
            sx={{ width: 40, height: 40, borderRadius: ".5rem", flexShrink: 0, mt: 0.25 }}
          >
            {model.assetName.slice(0, 1).toUpperCase()}
          </Avatar>
        )}

        <Box sx={{ minWidth: 0, flex: "1 1 auto" }}>
          <Typography
            data-testid="investment-holding-detail-asset-name"
            variant="h6"
            fontWeight={700}
            sx={{
              minWidth: 0,
              overflow: "hidden",
              textOverflow: "ellipsis",
              display: "-webkit-box",
              WebkitLineClamp: 2,
              WebkitBoxOrient: "vertical",
              wordBreak: "break-word",
              lineHeight: 1.25,
            }}
          >
            {model.assetName}
          </Typography>
          {showHeaderMeta ? (
            <Typography
              data-testid="investment-holding-detail-header-meta"
              variant="body2"
              noWrap
              sx={{ mt: 0.25, minWidth: 0 }}
            >
              {labels.metaLabel}
            </Typography>
          ) : null}
          {labels.accountLabel ? (
            <Typography
              data-testid="investment-holding-detail-header-financial-account"
              variant="caption"
              noWrap
              color="text.secondary"
              sx={{ mt: 0.25, minWidth: 0, display: "block" }}
            >
              {labels.accountLabel}
            </Typography>
          ) : null}
        </Box>
      </Box>

      {hasTrailing ? (
        <Box sx={{ display: "flex", flexDirection: "column", alignItems: "flex-end", flex: "0 0 auto" }}>
          {showMetrics ? (
            <>
              <Label sx={{ mb: 0.25 }}>{model.metrics.totalValueLabel}</Label>
              <CurrencyAmountParts
                value={model.metrics.totalValue}
                currency={model.metrics.currencyCode}
                locale={model.metrics.locale}
                variant="h4"
                fontWeight={500}
                noWrap
                sx={{ lineHeight: 1.1, fontSize: "1.7rem", letterSpacing: -1 }}
              />
              {model.metrics.gainPercent != null ? (
                <Box sx={{ mt: 0.25, display: "flex", alignItems: "center", justifyContent: "flex-end" }}>
                  <GainPercent
                    value={model.metrics.gainPercent}
                    locale={model.metrics.locale}
                    iconSize={12}
                    textSx={{ fontWeight: 600 }}
                  />
                </Box>
              ) : (
                <Typography variant="caption" sx={{ mt: 0.25, visibility: "hidden" }}>
                  —
                </Typography>
              )}
            </>
          ) : null}
          {trailingContent}
        </Box>
      ) : null}
    </Box>
  );
}
