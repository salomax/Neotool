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
import { CategoryPicker } from './category/CategoryPicker';
import { CategoryIcon } from './category/CategoryIcon';
import type { TransactionCategory } from '@/lib/graphql/types/__generated__/graphql';

/** Max length for category name in the chip; longer names show ellipsis. 20 is a good balance. */
const MAX_CATEGORY_CHIP_CHARS = 20;

export interface CategorySelectProps {
  value: string | null;
  onChange: (categoryId: string | null) => void;
  categories: TransactionCategory[];
  creditDebitType: 'CREDIT' | 'DEBIT' | 'TRANSFER';
  placeholder?: string;
}

export function CategorySelect({
  value,
  onChange,
  categories = [],
  creditDebitType,
  placeholder = 'Categoria...',
}: CategorySelectProps) {
  const theme = useTheme();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  const selectedCategory = value
    ? categories.find((c) => c.id === value)
    : null;

  const handleClick = (event: React.MouseEvent<HTMLElement>) => {
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
      <Box onClick={handleClick} sx={{ cursor: 'pointer', display: 'inline-block' }}>
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
              bgcolor: selectedCategory.color
                ? alpha(selectedCategory.color, 0.1)
                : 'action.selected',
              color: selectedCategory.color || 'text.primary',
              borderColor: selectedCategory.color
                ? alpha(selectedCategory.color, 0.3)
                : 'divider',
              borderWidth: 1,
              borderStyle: 'solid',
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
              borderStyle: 'dashed',
              color: 'text.secondary',
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
