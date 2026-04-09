"use client";

import * as React from "react";
import {
  alpha,
  Box,
  ButtonBase,
  Popover,
  Tooltip,
  Typography,
} from "@mui/material";
import { AssetTypeIcon, type AssetTypeIconKind } from "@/shared/components/ui/icons/AssetTypeIcon";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../i18n";

export type InvestmentTypeOption =
  | "fixed-income"
  | "equity"
  | "real-estate-fund"
  | "fund"
  | "crypto"
  | "cash";

interface InvestmentTypePickerProps {
  anchorEl: HTMLElement | null;
  open: boolean;
  onClose: () => void;
  onSelect: (type: InvestmentTypeOption) => void;
}

interface PickerOption {
  id: InvestmentTypeOption;
  label: string;
  iconKind: AssetTypeIconKind;
  disabled?: boolean;
}

export function InvestmentTypePicker({ anchorEl, open, onClose, onSelect }: InvestmentTypePickerProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  const options = React.useMemo<PickerOption[]>(
    () => [
      {
        id: "fixed-income",
        label: t("investments.picker.options.fixedIncome"),
        iconKind: "FIXED_INCOME",
      },
      {
        id: "equity",
        label: t("investments.picker.options.equity"),
        iconKind: "STOCK",
      },
      {
        id: "real-estate-fund",
        label: t("investments.picker.options.realEstateFund"),
        iconKind: "REAL_ESTATE_FUND",
      },
      {
        id: "fund",
        label: t("investments.picker.options.fund"),
        iconKind: "FUND",
      },
      {
        id: "crypto",
        label: t("investments.picker.options.crypto"),
        iconKind: "CRYPTO",
      },
      {
        id: "cash",
        label: t("investments.picker.options.cash"),
        iconKind: "OTHER",
      },
    ],
    [t],
  );

  return (
    <Popover
      open={open}
      anchorEl={anchorEl}
      onClose={onClose}
      anchorOrigin={{ vertical: "bottom", horizontal: "center" }}
      transformOrigin={{ vertical: "top", horizontal: "center" }}
      slotProps={{
        paper: {
          sx: {
            width: { xs: "calc(100vw - 24px)", sm: 520 },
            maxWidth: 520,
            borderRadius: 4,
            overflow: "hidden",
          },
        },
      }}
    >
      <Box
        sx={{
          width: "100%",
          px: { xs: 2.5, sm: 3 },
          pt: 2.5,
          pb: 3,
        }}
      >
        <Typography variant="h6" fontWeight={900} sx={{ mb: 2.5 }}>
          {t("investments.picker.title")}
        </Typography>

        <Box
          sx={{
            display: "grid",
            gridTemplateColumns: "1fr 1fr",
            gap: 2,
          }}
        >
        {options.map((option) => {
          const card = (
            <ButtonBase
              key={option.id}
              onClick={() => onSelect(option.id)}
              disabled={option.disabled}
              sx={(theme) => ({
                borderRadius: 3,
                border: `1px solid ${theme.palette.divider}`,
                px: 2,
                py: 2.5,
                minHeight: 120,
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                justifyContent: "center",
                gap: 1.5,
                transition: "background-color 150ms ease",
                opacity: option.disabled ? 0.55 : 1,
                "&:hover": {
                  bgcolor: alpha(theme.palette.primary.main, 0.06),
                },
              })}
            >
              <Box
                sx={(theme) => ({
                  width: 56,
                  height: 56,
                  borderRadius: 3,
                  bgcolor: alpha(theme.palette.primary.main, 0.10),
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                })}
              >
                <AssetTypeIcon kind={option.iconKind} sx={{ fontSize: 28, color: "primary.main" }} />
              </Box>
              <Typography variant="body2" fontWeight={700} textAlign="center">
                {option.label}
              </Typography>
            </ButtonBase>
          );

          if (!option.disabled) return card;
          return (
            <Tooltip key={option.id} title={t("investments.picker.options.comingSoon")}>
              <Box>{card}</Box>
            </Tooltip>
          );
        })}
        </Box>
      </Box>
    </Popover>
  );
}
