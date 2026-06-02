'use client';

import {
  alpha,
  Box,
  Chip,
  Collapse,
  Fade,
  Stack,
  useTheme,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../i18n';
import { CreditDebitType } from '@/lib/graphql/types/__generated__/graphql';
import { CategoryFilterSelection } from './summary/MonthSummary';
import { CategoryIcon } from './category/CategoryIcon';

interface FinanceTransactionsActiveFiltersProps {
  open: boolean;
  summaryCreditDebitTypeFilter: CreditDebitType | null;
  onClearCreditDebitTypeFilter: () => void;
  summaryTypeChipAccent: string;
  summaryCategoryFilterSelection: CategoryFilterSelection | null;
  summaryCategoryFilterMeta: { name?: string | null; icon?: string | null } | null;
  summaryCategoryChipAccent: string;
  summaryUncategorizedFilterActive: boolean;
  summaryCategoryFilterId: string | null;
  onClearCategoryFilter: () => void;
  summaryTagFilterValue: string | null;
  summaryTagFilterMeta: { tag?: string | null } | null;
  summaryTagChipAccent: string;
  onClearTagFilter: () => void;
  summaryTransferFilterActive: boolean;
  onClearTransferFilter: () => void;
  summaryTransferChipAccent: string;
}

export function FinanceTransactionsActiveFilters({
  open,
  summaryCreditDebitTypeFilter,
  onClearCreditDebitTypeFilter,
  summaryTypeChipAccent,
  summaryCategoryFilterSelection,
  summaryCategoryFilterMeta,
  summaryCategoryChipAccent,
  summaryUncategorizedFilterActive,
  summaryCategoryFilterId,
  onClearCategoryFilter,
  summaryTagFilterValue,
  summaryTagFilterMeta,
  summaryTagChipAccent,
  onClearTagFilter,
  summaryTransferFilterActive,
  onClearTransferFilter,
  summaryTransferChipAccent,
}: FinanceTransactionsActiveFiltersProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();

  return (
    <Collapse
      in={open}
      timeout={{ enter: 320, exit: 240 }}
      unmountOnExit
      easing={{
        enter: theme.transitions.easing.easeOut,
        exit: theme.transitions.easing.sharp,
      }}
      sx={{ width: '100%' }}
    >
      <Fade
        in={open}
        timeout={{ enter: 260, exit: 180 }}
        easing={{
          enter: theme.transitions.easing.easeOut,
          exit: theme.transitions.easing.sharp,
        }}
      >
        <Box sx={{ width: '100%', mb: 2, pt: 0.25 }}>
          <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ width: '100%' }}>
            {summaryCreditDebitTypeFilter && (
              <Chip
                icon={
                  summaryCreditDebitTypeFilter === CreditDebitType.Credit ? (
                    <ArrowUpwardIcon sx={{ fontSize: 18 }} />
                  ) : (
                    <ArrowDownwardIcon sx={{ fontSize: 18 }} />
                  )
                }
                label={
                  summaryCreditDebitTypeFilter === CreditDebitType.Credit
                    ? t('financeTransactions.monthSummary.filteringByCredit')
                    : t('financeTransactions.monthSummary.filteringByDebit')
                }
                onDelete={onClearCreditDebitTypeFilter}
                deleteIcon={
                  <CloseIcon fontSize="small" aria-label={t('financeTransactions.monthSummary.clearCreditDebitTypeFilter')} />
                }
                variant="outlined"
                sx={{
                  pl: 1.5,
                  pr: 1.25,
                  py: 0.75,
                  bgcolor: alpha(summaryTypeChipAccent, 0.12),
                  borderColor: alpha(summaryTypeChipAccent, 0.4),
                  color: summaryTypeChipAccent,
                  fontWeight: 600,
                  '& .MuiChip-icon': {
                    color: summaryTypeChipAccent,
                    ml: 0.25,
                    mr: 0.5,
                  },
                  '& .MuiChip-label': { px: 1.25 },
                  '& .MuiChip-deleteIcon': {
                    color: alpha(summaryTypeChipAccent, 0.75),
                    '&:hover': { color: summaryTypeChipAccent },
                  },
                }}
              />
            )}
            {summaryCategoryFilterSelection && (
              <Chip
                icon={<CategoryIcon iconName={summaryCategoryFilterMeta?.icon ?? null} color={summaryCategoryChipAccent} size={18} />}
                label={t('financeTransactions.monthSummary.filteringByCategory', {
                  name:
                    summaryCategoryFilterMeta?.name ??
                    (summaryUncategorizedFilterActive
                      ? t('financeTransactions.monthSummary.uncategorized')
                      : summaryCategoryFilterId),
                })}
                onDelete={onClearCategoryFilter}
                deleteIcon={<CloseIcon fontSize="small" aria-label={t('financeTransactions.monthSummary.clearCategoryFilter')} />}
                variant="outlined"
                sx={{
                  pl: 1.5,
                  pr: 1.25,
                  py: 0.75,
                  bgcolor: alpha(summaryCategoryChipAccent, 0.12),
                  borderColor: alpha(summaryCategoryChipAccent, 0.4),
                  color: summaryCategoryChipAccent,
                  fontWeight: 600,
                  '& .MuiChip-icon': {
                    color: summaryCategoryChipAccent,
                    ml: 0.25,
                    mr: 0.5,
                  },
                  '& .MuiChip-label': { px: 1.25 },
                  '& .MuiChip-deleteIcon': {
                    color: alpha(summaryCategoryChipAccent, 0.75),
                    '&:hover': { color: summaryCategoryChipAccent },
                  },
                }}
              />
            )}
            {summaryTagFilterValue && (
              <Chip
                label={t('financeTransactions.monthSummary.filteringByTag', {
                  name: summaryTagFilterMeta?.tag ?? summaryTagFilterValue,
                })}
                onDelete={onClearTagFilter}
                deleteIcon={<CloseIcon fontSize="small" aria-label={t('financeTransactions.monthSummary.clearTagFilter')} />}
                variant="outlined"
                sx={{
                  pl: 1.5,
                  pr: 1.25,
                  py: 0.75,
                  bgcolor: alpha(summaryTagChipAccent, 0.12),
                  borderColor: alpha(summaryTagChipAccent, 0.4),
                  color: summaryTagChipAccent,
                  fontWeight: 600,
                  '& .MuiChip-label': { px: 1.25 },
                  '& .MuiChip-deleteIcon': {
                    color: alpha(summaryTagChipAccent, 0.75),
                    '&:hover': { color: summaryTagChipAccent },
                  },
                }}
              />
            )}
            {summaryTransferFilterActive && (
              <Chip
                icon={<SwapHorizIcon sx={{ fontSize: 18 }} />}
                label={t('financeTransactions.monthSummary.filteringByTransfer')}
                onDelete={onClearTransferFilter}
                deleteIcon={
                  <CloseIcon fontSize="small" aria-label={t('financeTransactions.monthSummary.clearTransferFilter')} />
                }
                variant="outlined"
                sx={{
                  pl: 1.5,
                  pr: 1.25,
                  py: 0.75,
                  bgcolor: alpha(summaryTransferChipAccent, 0.12),
                  borderColor: alpha(summaryTransferChipAccent, 0.4),
                  color: summaryTransferChipAccent,
                  fontWeight: 600,
                  '& .MuiChip-icon': {
                    color: summaryTransferChipAccent,
                    ml: 0.25,
                    mr: 0.5,
                  },
                  '& .MuiChip-label': { px: 1.25 },
                  '& .MuiChip-deleteIcon': {
                    color: alpha(summaryTransferChipAccent, 0.75),
                    '&:hover': { color: summaryTransferChipAccent },
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
