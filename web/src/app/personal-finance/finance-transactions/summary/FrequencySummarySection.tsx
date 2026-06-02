import { Box, Divider, Stack, Typography } from '@mui/material';
import { memo } from 'react';
import { Label } from '@/shared/components/ui/primitives/Label';
import type { FrequencyTotalItem } from './types';
import { FrequencyProgressRow } from './FrequencyProgressRow';

interface FrequencySummarySectionProps {
  title: string;
  emptyText: string;
  items: FrequencyTotalItem[];
}

export const FrequencySummarySection = memo(function FrequencySummarySection({
  title,
  emptyText,
  items,
}: FrequencySummarySectionProps) {
  const topItems = items.slice(0, 5);

  return (
    <Box>
      <Label sx={{ mb: 1.5 }}>{title}</Label>

      {items.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          {emptyText}
        </Typography>
      ) : (
        <Stack spacing={1.5}>
          {topItems.map((item) => (
            <FrequencyProgressRow
              key={item.frequency}
              item={item}
              nameFontWeight={500}
              percentFontWeight={600}
            />
          ))}
        </Stack>
      )}
    </Box>
  );
});
