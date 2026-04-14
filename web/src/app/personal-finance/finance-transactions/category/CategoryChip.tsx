'use client';

import React from 'react';
import { Box, Chip, CircularProgress, Stack, Typography, alpha, useTheme } from '@mui/material';
import { personalFinanceTranslations } from '../../i18n';
import { useTranslation } from '@/shared/i18n';
import { CategoryIcon } from './CategoryIcon';
import { FINANCE_TABLE_BODY_FONT_SIZE } from '@/shared/components/ui/data-display/Table';

export interface CategoryChipCategory {
  name: string;
  icon?: string | null;
  color?: string | null;
}

export interface CategoryChipProps {
  category: CategoryChipCategory | null | undefined;
  disabled?: boolean;
  loading?: boolean;
  onClick?: (e: React.MouseEvent<HTMLDivElement>) => void;
  placeholder?: React.ReactNode;
  maxWidthCh?: number;
  decorationAlpha?: CategoryChipDecorationAlpha;
}

export type CategoryChipDecorationAlpha = Readonly<{
  filledBackground: number;
  filledBorder: number;
  filledHoverBackground: number;
  placeholderBackground: number;
  placeholderInsetBorder: number;
  placeholderHoverBackground: number;
}>;

const DEFAULT_DECORATION_ALPHA: CategoryChipDecorationAlpha = {
  filledBackground: 0.1,
  filledBorder: 0.3,
  filledHoverBackground: 0.2,
  placeholderBackground: 0.02,
  placeholderInsetBorder: 0.06,
  placeholderHoverBackground: 0.05,
};

export function CategoryChip({
  category,
  disabled = false,
  loading = false,
  onClick,
  placeholder,
  maxWidthCh = 22,
  decorationAlpha = DEFAULT_DECORATION_ALPHA,
}: CategoryChipProps) {
  const theme = useTheme();
  const { t } = useTranslation(personalFinanceTranslations);
  const labelFontSize = FINANCE_TABLE_BODY_FONT_SIZE;
  const isClickable = typeof onClick === 'function';

  if (!category && !placeholder) return null;

  return (
    <Box
      component="span"
      sx={{
        display: 'inline-block',
        maxWidth: '100%',
        '& .MuiChip-root': {
          maxWidth: '100%',
        },
      }}
    >
      {category ? (
        <Chip
          clickable={isClickable}
          disabled={disabled}
          onClick={onClick}
          icon={loading ? <CircularProgress size={14} /> : undefined}
          label={
            <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0 }}>
              <CategoryIcon iconName={category.icon} color={category.color ?? undefined} size={18} sx={{ flexShrink: 0 }} />
              <Typography
                variant="body2"
                title={category.name}
                sx={{
                  fontSize: labelFontSize,
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  minWidth: 0,
                  maxWidth: `${maxWidthCh}ch`,
                }}
              >
                {category.name}
              </Typography>
            </Stack>
          }
          variant="filled"
          sx={{
            height: 28,
            color: category.color || 'text.primary',
            bgcolor: category.color ? alpha(category.color, decorationAlpha.filledBackground) : 'action.selected',
            borderColor: category.color ? alpha(category.color, decorationAlpha.filledBorder) : 'divider',
            borderWidth: 1,
            borderStyle: 'solid',
            '&:hover': isClickable
              ? {
                  bgcolor: category.color
                    ? alpha(category.color, decorationAlpha.filledHoverBackground)
                    : 'action.hover',
                }
              : undefined,
            transition: 'background-color 0.15s ease',
            '& .MuiChip-label': { minWidth: 0, maxWidth: '100%' },
          }}
        />
      ) : (
        <Chip
          clickable={isClickable}
          disabled={disabled}
          onClick={onClick}
          icon={loading ? <CircularProgress size={14} /> : undefined}
          label={placeholder ?? (
            <Typography variant="body2" sx={{ fontSize: labelFontSize, opacity: 0.65 }}>
              {t('financeTransactions.category')}
            </Typography>
          )}
          variant="outlined"
          sx={{
            height: 28,
            borderStyle: 'dashed',
            color: 'text.secondary',
            bgcolor: alpha(theme.palette.text.primary, decorationAlpha.placeholderBackground),
            boxShadow: `inset 0 0 0 1px ${alpha(
              theme.palette.text.primary,
              decorationAlpha.placeholderInsetBorder,
            )}`,
            '&:hover': isClickable
              ? {
                  borderColor: 'primary.main',
                  color: 'primary.main',
                  bgcolor: alpha(theme.palette.primary.main, decorationAlpha.placeholderHoverBackground),
                }
              : undefined,
            transition: 'background-color 0.15s ease',
          }}
        />
      )}
    </Box>
  );
}
