'use client';

import React, { useState } from 'react';
import {
  Box,
  Chip,
  Stack,
  Typography,
  alpha,
  useTheme,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { CategoryPicker } from './CategoryPicker';
import { CategoryIcon } from './CategoryIcon';
import type { TransactionCategory } from '@/lib/graphql/types/__generated__/graphql';

/** Max length for category name in the chip; longer names show ellipsis. 20 is a good balance. */
const MAX_CATEGORY_CHIP_CHARS = 20;

export interface CategorySelectProps {
  value: string | null;
  onChange: (categoryId: string | null) => void;
  categories: TransactionCategory[];
  creditDebitType: 'CREDIT' | 'DEBIT' | 'TRANSFER';
  placeholder?: string;
  disabled?: boolean;
  helperText?: string;
}

export function CategorySelect({
  value,
  onChange,
  categories = [],
  creditDebitType,
  placeholder = 'Categoria...',
  disabled = false,
  helperText,
}: CategorySelectProps) {
  const theme = useTheme();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  const selectedCategory = value
    ? categories.find((c) => c.id === value)
    : null;

  const handleClick = (event: React.MouseEvent<HTMLElement>) => {
    if (disabled) return;
    setAnchorEl(event.currentTarget);
  };

  const handleClose = () => {
    setAnchorEl(null);
  };

  const handleSelect = (id: string | null) => {
    onChange(id);
    handleClose();
  };

  return (
    <>
      <Box
        data-testid="tx-category-select"
        data-disabled={disabled ? 'true' : undefined}
        aria-disabled={disabled || undefined}
        title={disabled && helperText ? helperText : undefined}
        onClick={handleClick}
        sx={{
          cursor: disabled ? 'not-allowed' : 'pointer',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'stretch',
          width: '100%',
          minWidth: 0,
          opacity: disabled ? 0.5 : 1,
        }}
      >
        <Box sx={{ display: 'flex', alignItems: 'center', width: '100%', minWidth: 0 }}>
        {selectedCategory ? (
          <Chip
            label={
              <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0 }}>
                <CategoryIcon
                  iconName={selectedCategory.icon}
                  color={selectedCategory.color ?? undefined}
                  size={20}
                  sx={{ flexShrink: 0 }}
                />
                <Typography
                  variant="body2"
                  title={selectedCategory.name}
                  sx={{
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                    maxWidth: `${MAX_CATEGORY_CHIP_CHARS}ch`,
                  }}
                >
                  {selectedCategory.name}
                </Typography>
              </Stack>
            }
            sx={{
              maxWidth: '100%',
              minWidth: 0,
              bgcolor: selectedCategory.color
                ? alpha(selectedCategory.color, 0.1)
                : 'action.selected',
              color: selectedCategory.color || 'text.primary',
              borderColor: selectedCategory.color
                ? alpha(selectedCategory.color, 0.3)
                : 'divider',
              borderWidth: 1,
              borderStyle: 'solid',
              '& .MuiChip-label': {
                minWidth: 0,
                maxWidth: '100%',
                overflow: 'hidden',
              },
              '&:hover': {
                bgcolor: selectedCategory.color
                  ? alpha(selectedCategory.color, 0.2)
                  : 'action.hover',
              },
            }}
            onDelete={handleClick}
            deleteIcon={<ExpandMoreIcon sx={{ color: 'inherit !important' }} />}
          />
        ) : (
          <Chip
            label={placeholder}
            variant="outlined"
            sx={{
              maxWidth: '100%',
              minWidth: 0,
              borderStyle: 'dashed',
              color: 'text.secondary',
              '& .MuiChip-label': {
                minWidth: 0,
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              },
              '&:hover': {
                borderColor: 'primary.main',
                color: 'primary.main',
                bgcolor: alpha(theme.palette.primary.main, 0.05),
              },
            }}
            onDelete={handleClick}
            deleteIcon={<ExpandMoreIcon />}
          />
        )}
        </Box>
        {disabled && helperText && (
          <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, lineHeight: 1.2 }}>
            {helperText}
          </Typography>
        )}
      </Box>

      <CategoryPicker
        value={value}
        onChange={handleSelect}
        creditDebitType={creditDebitType}
        anchorEl={anchorEl}
        onClose={handleClose}
      />
    </>
  );
}
