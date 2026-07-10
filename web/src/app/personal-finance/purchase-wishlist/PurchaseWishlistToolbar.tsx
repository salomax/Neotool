'use client';

import AddIcon from '@mui/icons-material/Add';
import { Box, Button, Stack } from '@mui/material';
import { ExpandableSearchInput } from '@/shared/components/ui/forms/ExpandableSearchInput';

type PurchaseWishlistToolbarProps = {
  searchQuery: string;
  onSearchQueryChange: (value: string) => void;
  onCreate: () => void;
  createLabel: string;
  searchPlaceholder: string;
  clearSearchAriaLabel: string;
};

export function PurchaseWishlistToolbar({
  searchQuery,
  onSearchQueryChange,
  onCreate,
  createLabel,
  searchPlaceholder,
  clearSearchAriaLabel,
}: PurchaseWishlistToolbarProps) {
  return (
    <Stack
      direction={{ xs: 'column', md: 'row' }}
      spacing={2}
      sx={{ width: '100%', mb: 2, mt: { xs: 0, md: -1.5 }, minHeight: 50 }}
      alignItems={{ xs: 'stretch', md: 'flex-end' }}
      flexWrap="wrap"
      useFlexGap
    >
      <Box
        sx={{
          flex: 1,
          minWidth: 0,
        }}
      />
      <Box
        sx={{
          flex: '0 0 auto',
          display: 'flex',
          alignItems: 'flex-end',
          gap: 1.5,
          width: { xs: '100%', md: 'auto' },
          flexWrap: { xs: 'wrap', md: 'nowrap' },
        }}
      >
        <ExpandableSearchInput
          searchQuery={searchQuery}
          onSearchQueryChange={onSearchQueryChange}
          placeholder={searchPlaceholder}
          ariaClearLabel={clearSearchAriaLabel}
          collapsedSearchIconMarginLeft="20px"
        />
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={onCreate}
          sx={{ flexShrink: 0, minHeight: 44, width: { xs: '100%', sm: 'auto' } }}
        >
          {createLabel}
        </Button>
      </Box>
    </Stack>
  );
}
