"use client";

import React from "react";
import CheckIcon from "@mui/icons-material/Check";
import { Box, Divider, Stack, Typography } from "@mui/material";
import { Link } from "@/shared/components/ui/navigation/Link";
import { Label } from "@/shared/components/ui/primitives/Label";
import { CurrencyAmountParts } from "@/shared/components/ui/data-display/CurrencyAmountParts";
import { useTranslation } from "@/shared/i18n";
import { getCurrentLocale } from "@/shared/utils/date";
import { personalFinanceTranslations } from "../../../i18n";
import { PendingActionType } from "@/lib/graphql/types/__generated__/graphql";
import { PendingActionTypeChip } from "./PendingActionTypeChip";
import {
  daysSinceCreated,
  pendingActionAgeLabelKey,
  pendingActionAmount,
  pendingActionDescription,
  pendingActionTitle,
  type PendingActionRowData,
} from "./pendingActionPresenters";

export interface PendingActionRowProps {
  row: PendingActionRowData;
  dismissLoading?: boolean;
  onPrimaryAction?: () => void;
  onDismiss: () => void;
  primaryActionLabel?: string;
  primaryActionTestId?: string;
}

export function PendingActionRow({
  row,
  dismissLoading = false,
  onPrimaryAction,
  onDismiss,
  primaryActionLabel,
  primaryActionTestId,
}: PendingActionRowProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const locale = getCurrentLocale();
  const type = row.type as PendingActionType;
  const days = daysSinceCreated(row.createdAt);
  const ageKey = pendingActionAgeLabelKey(days);
  const ageLabel =
    ageKey === "days"
      ? t("investments.pendingActions.age.days", { count: days })
      : t(`investments.pendingActions.age.${ageKey}`);
  const amount = pendingActionAmount(row);
  const showPrimary = Boolean(primaryActionLabel && onPrimaryAction);

  return (
    <Box data-testid="pending-action-row">
      <Stack spacing={1} sx={{ p: 1.5 }}>
        <Box sx={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", gap: 1 }}>
          <PendingActionTypeChip type={type} />
          <Label sx={{ mb: 0 }}>{ageLabel}</Label>
        </Box>

        <Box
          sx={{
            display: "flex",
            alignItems: "flex-start",
            justifyContent: "space-between",
            gap: 1,
          }}
        >
          <Typography
            variant="subtitle1"
            sx={{ fontWeight: 600, lineHeight: 1.3, flex: 1, minWidth: 0 }}
          >
            {pendingActionTitle(row)}
          </Typography>
          {amount ? (
            <CurrencyAmountParts
              value={amount.value}
              currency={amount.currency}
              variant="subtitle1"
              sx={{
                fontWeight: 600,
                lineHeight: 1.3,
                flexShrink: 0,
                textAlign: "right",
              }}
            />
          ) : null}
        </Box>

        <Typography variant="body2" color="text.secondary">
          {pendingActionDescription(row, t, locale)}
        </Typography>
      </Stack>

      <Divider />

      <Box
        sx={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 1,
          px: 1.5,
          py: 1,
        }}
      >
        {showPrimary ? (
          <Link
            component="button"
            type="button"
            variant="body2"
            underline="hover"
            name="pending-action-primary"
            data-testid={primaryActionTestId}
            onClick={onPrimaryAction}
            sx={{ cursor: "pointer", border: 0, background: "none", p: 0, font: "inherit" }}
          >
            {primaryActionLabel}
          </Link>
        ) : (
          <Box />
        )}

        <Link
          component="button"
          type="button"
          variant="body2"
          underline="hover"
          name="pending-action-dismiss"
          data-testid="pending-action-dismiss"
          onClick={dismissLoading ? undefined : onDismiss}
          aria-disabled={dismissLoading}
          sx={{
            display: "inline-flex",
            alignItems: "center",
            gap: 0.5,
            cursor: dismissLoading ? "default" : "pointer",
            border: 0,
            background: "none",
            p: 0,
            font: "inherit",
            color: "text.secondary",
            opacity: dismissLoading ? 0.6 : 1,
            pointerEvents: dismissLoading ? "none" : "auto",
          }}
        >
          <CheckIcon sx={{ fontSize: 16 }} />
          {t("investments.pendingActions.dismiss")}
        </Link>
      </Box>
    </Box>
  );
}
