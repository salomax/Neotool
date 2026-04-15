import { Box, Button, Divider, Stack, Typography } from '@mui/material';
import { memo } from 'react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { Label } from '@/shared/components/ui/primitives/Label';
import type { TagTotalItem } from './types';
import { TagProgressRow } from './TagProgressRow';

interface TagSummarySectionProps {
  title: string;
  emptyText: string;
  items: TagTotalItem[];
  onSeeAll: () => void;
  onTagClick?: (tag: string) => void;
  activeTagFilter: string | null;
}

export const TagSummarySection = memo(function TagSummarySection({
  title,
  emptyText,
  items,
  onSeeAll,
  onTagClick,
  activeTagFilter,
}: TagSummarySectionProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const topItems = items.slice(0, 5);
  const canSeeAll = items.length > 5;

  return (
    <Box>
      <Divider sx={{ mb: 3 }} />
      <Box sx={{ display: 'flex', alignItems: 'baseline', justifyContent: 'space-between', gap: 2, mb: 1.5 }}>
        <Label sx={{ mb: 0 }}>{title}</Label>
        {canSeeAll && (
          <Button
            variant="text"
            size="small"
            onClick={onSeeAll}
            sx={{ p: 0, minWidth: 'auto', textTransform: 'none', fontWeight: 500 }}
          >
            {t('financeTransactions.monthSummary.seeAll')}
          </Button>
        )}
      </Box>

      {items.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          {emptyText}
        </Typography>
      ) : (
        <Stack spacing={1.25}>
          {topItems.map((item) => (
            <TagProgressRow
              key={item.tag}
              item={item}
              nameFontWeight={500}
              percentFontWeight={600}
              dimmed={Boolean(activeTagFilter && item.tag !== activeTagFilter)}
              onTagClick={onTagClick}
            />
          ))}
        </Stack>
      )}
    </Box>
  );
});
