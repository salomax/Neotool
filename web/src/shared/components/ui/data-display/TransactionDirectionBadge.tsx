import React from 'react';
import { Box, alpha, useTheme } from '@mui/material';
import type { SxProps, Theme } from '@mui/material/styles';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { CreditDebitType, TransactionType } from '@/lib/graphql/types/__generated__/graphql';
import { Logo } from '@/shared/components/ui/primitives';

export type TransactionDirectionVisuals = {
  icon: React.ReactElement;
  color: string;
};

export function resolveTransactionDirectionVisuals(
  theme: Theme,
  creditDebitType: CreditDebitType,
  transactionType: TransactionType,
): TransactionDirectionVisuals | null {
  if (transactionType === TransactionType.Transfer) {
    return {
      icon:
        creditDebitType === CreditDebitType.Debit ? (
          <ArrowBackIcon fontSize="inherit" />
        ) : (
          <ArrowForwardIcon fontSize="inherit" />
        ),
      color: theme.palette.info.main,
    };
  }

  if (creditDebitType === CreditDebitType.Credit) {
    return { icon: <ArrowUpwardIcon fontSize="inherit" />, color: theme.palette.success.main };
  }

  if (creditDebitType === CreditDebitType.Debit) {
    return { icon: <ArrowDownwardIcon fontSize="inherit" />, color: theme.palette.error.main };
  }

  return null;
}

export interface TransactionDirectionBadgeProps {
  creditDebitType: CreditDebitType;
  transactionType: TransactionType;
  merchantLogoUrl?: string | null;
  merchantName?: string | null;
  size?: number;
  dimmed?: boolean;
  sx?: SxProps<Theme>;
}

export function TransactionDirectionBadge({
  creditDebitType,
  transactionType,
  merchantLogoUrl,
  merchantName,
  size = 32,
  dimmed = false,
  sx,
}: TransactionDirectionBadgeProps) {
  const theme = useTheme();
  const visuals = resolveTransactionDirectionVisuals(theme, creditDebitType, transactionType);
  if (!visuals) return null;

  const disabledFg = theme.palette.text.disabled;
  const fg = dimmed ? disabledFg : visuals.color;

  const overlaySize = Math.round(size * 0.56);
  const overlayOffset = Math.round(size * 0.25);
  const overlayFontSize = Math.round(size * 0.5);

  if (merchantLogoUrl) {
    return (
      <Box sx={{ position: 'relative', width: size, height: size, flexShrink: 0, ...sx }}>
        <Logo src={merchantLogoUrl} alt={merchantName ?? ''} size={size} />
        <Box
          sx={{
            position: 'absolute',
            bottom: -overlayOffset,
            right: -overlayOffset,
            width: overlaySize,
            height: overlaySize,
            borderRadius: '.3rem',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: dimmed
              ? alpha(disabledFg, 0.12)
              : `color-mix(in srgb, ${visuals.color} 5%, ${theme.palette.background.paper})`,
            color: fg,
            fontSize: overlayFontSize,
          }}
        >
          {visuals.icon}
        </Box>
      </Box>
    );
  }

  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: size,
        height: size,
        borderRadius: '.3rem',
        bgcolor: dimmed ? alpha(disabledFg, 0.12) : alpha(visuals.color, 0.08),
        color: fg,
        fontSize: Math.round(size * 0.625),
        flexShrink: 0,
        ...sx,
      }}
    >
      {visuals.icon}
    </Box>
  );
}

