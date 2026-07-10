'use client';

import CloseIcon from '@mui/icons-material/Close';
import EventRepeatIcon from '@mui/icons-material/EventRepeat';
import ScheduleIcon from '@mui/icons-material/Schedule';
import { alpha, Box, Chip, Collapse, Fade, Stack, useTheme } from '@mui/material';
import { CategoryIcon } from '@/app/personal-finance/finance-transactions/category/CategoryIcon';
import type { RecurringFrequency } from '@/lib/graphql/types/__generated__/graphql';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import type { RecurringCategoryFilterSelection, RecurringDueBucket } from './recurringPaymentRowModel';

interface RecurringPaymentsActiveFiltersProps {
  open: boolean;
  categoryFilter: RecurringCategoryFilterSelection | null;
  categoryMeta: { name?: string | null; icon?: string | null; color?: string | null } | null;
  onClearCategoryFilter: () => void;
  frequencyFilter: RecurringFrequency | null;
  onClearFrequencyFilter: () => void;
  dueBucketFilter: RecurringDueBucket | null;
  onClearDueBucketFilter: () => void;
}

export function RecurringPaymentsActiveFilters({
  open,
  categoryFilter,
  categoryMeta,
  onClearCategoryFilter,
  frequencyFilter,
  onClearFrequencyFilter,
  dueBucketFilter,
  onClearDueBucketFilter,
}: RecurringPaymentsActiveFiltersProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();

  const categoryAccent = categoryMeta?.color ?? theme.palette.primary.main;
  const frequencyAccent = theme.palette.primary.main;
  const dueAccent = dueBucketFilter === 'overdue' ? theme.palette.error.main : theme.palette.warning.main;

  const dueBucketLabel =
    dueBucketFilter === 'overdue'
      ? t('recurringPayments.summary.overdue')
      : dueBucketFilter === 'next7Days'
        ? t('recurringPayments.summary.next7Days')
        : dueBucketFilter === 'nextMonth'
          ? t('recurringPayments.table.groups.nextMonth')
          : null;

  return (
    <Collapse
      in={open}
      timeout={{ enter: 320, exit: 240 }}
      unmountOnExit
      easing={{ enter: theme.transitions.easing.easeOut, exit: theme.transitions.easing.sharp }}
      sx={{ width: '100%' }}
    >
      <Fade
        in={open}
        timeout={{ enter: 260, exit: 180 }}
        easing={{ enter: theme.transitions.easing.easeOut, exit: theme.transitions.easing.sharp }}
      >
        <Box sx={{ width: '100%', mb: 2, pt: 0.25 }}>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ width: '100%' }}>
            {categoryFilter && (
              <Chip
                icon={<CategoryIcon iconName={categoryMeta?.icon ?? null} color={categoryAccent} size={18} />}
                label={t('recurringPayments.summary.filteringByCategory', {
                  name:
                    categoryMeta?.name ??
                    (categoryFilter.uncategorized
                      ? t('recurringPayments.uncategorized')
                      : categoryFilter.categoryId),
                })}
                onDelete={onClearCategoryFilter}
                deleteIcon={<CloseIcon fontSize="small" aria-label={t('recurringPayments.summary.clearCategoryFilter')} />}
                variant="outlined"
                sx={{
                  pl: 1.5,
                  pr: 1.25,
                  py: 0.75,
                  bgcolor: alpha(categoryAccent, 0.12),
                  borderColor: alpha(categoryAccent, 0.4),
                  color: categoryAccent,
                  fontWeight: 600,
                  '& .MuiChip-icon': { color: categoryAccent, ml: 0.25, mr: 0.5 },
                  '& .MuiChip-label': { px: 1.25 },
                  '& .MuiChip-deleteIcon': {
                    color: alpha(categoryAccent, 0.75),
                    '&:hover': { color: categoryAccent },
                  },
                }}
              />
            )}
            {frequencyFilter && (
              <Chip
                icon={<EventRepeatIcon sx={{ fontSize: 18 }} />}
                label={t('recurringPayments.summary.filteringByFrequency', {
                  name: t(`recurringPayments.frequency.${frequencyFilter}`),
                })}
                onDelete={onClearFrequencyFilter}
                deleteIcon={<CloseIcon fontSize="small" aria-label={t('recurringPayments.summary.clearFrequencyFilter')} />}
                variant="outlined"
                sx={{
                  pl: 1.5,
                  pr: 1.25,
                  py: 0.75,
                  bgcolor: alpha(frequencyAccent, 0.12),
                  borderColor: alpha(frequencyAccent, 0.4),
                  color: frequencyAccent,
                  fontWeight: 600,
                  '& .MuiChip-icon': { color: frequencyAccent, ml: 0.25, mr: 0.5 },
                  '& .MuiChip-label': { px: 1.25 },
                  '& .MuiChip-deleteIcon': {
                    color: alpha(frequencyAccent, 0.75),
                    '&:hover': { color: frequencyAccent },
                  },
                }}
              />
            )}
            {dueBucketFilter && dueBucketLabel && (
              <Chip
                icon={<ScheduleIcon sx={{ fontSize: 18 }} />}
                label={t('recurringPayments.summary.filteringByDueBucket', { name: dueBucketLabel })}
                onDelete={onClearDueBucketFilter}
                deleteIcon={<CloseIcon fontSize="small" aria-label={t('recurringPayments.summary.clearDueBucketFilter')} />}
                variant="outlined"
                sx={{
                  pl: 1.5,
                  pr: 1.25,
                  py: 0.75,
                  bgcolor: alpha(dueAccent, 0.12),
                  borderColor: alpha(dueAccent, 0.4),
                  color: dueAccent,
                  fontWeight: 600,
                  '& .MuiChip-icon': { color: dueAccent, ml: 0.25, mr: 0.5 },
                  '& .MuiChip-label': { px: 1.25 },
                  '& .MuiChip-deleteIcon': {
                    color: alpha(dueAccent, 0.75),
                    '&:hover': { color: dueAccent },
                  },
                }}
              />
            )}
          </Stack>
        </Box>
      </Fade>
    </Collapse>
  );
}
