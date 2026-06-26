import { Chip, Stack, Tooltip, Typography, ButtonBase, alpha, useTheme } from '@mui/material';
import { memo } from 'react';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';

const TAG_COLORS = [
  '#F44336', '#E91E63', '#9C27B0', '#673AB7', '#3F51B5',
  '#2196F3', '#03A9F4', '#00BCD4', '#009688', '#4CAF50',
  '#8BC34A', '#CDDC39', '#FFEB3B', '#FFC107', '#FF9800',
  '#FF5722', '#795548', '#607D8B',
];

function stringToColor(value: string): string {
  let hash = 0;
  for (let i = 0; i < value.length; i++) {
    hash = value.charCodeAt(i) + ((hash << 5) - hash);
  }
  return TAG_COLORS[Math.abs(hash % TAG_COLORS.length)]!;
}

const TAG_BORDER_RADIUS = '.3rem';

interface TransactionTagListProps {
  tags: string[];
  fontSize?: string;
}

export const TransactionTagList = memo(function TransactionTagList({
  tags,
  fontSize = '0.7rem',
}: TransactionTagListProps) {
  const theme = useTheme();
  const { t } = useTranslation(personalFinanceTranslations);

  if (!tags.length) return null;

  return (
    <Stack direction="row" spacing={0.5} alignItems="center">
      {tags.slice(0, 2).map((tag, tagIndex) => {
        if (!tag) return null;
        const color = stringToColor(tag);
        return (
          <Chip
            key={`${tag}-${tagIndex}`}
            label={<Typography variant="body2" sx={{ fontSize }}>{tag}</Typography>}
            size="small"
            variant="filled"
            sx={{ bgcolor: alpha(color, 0.05), color, borderRadius: TAG_BORDER_RADIUS }}
          />
        );
      })}
      {tags.length > 2 && (
        <Tooltip
          title={
            <Stack spacing={0.5} sx={{ p: 0.5 }}>
              {tags.map((tag, tagIndex) =>
                tag ? (
                  <Typography key={`${tag}-${tagIndex}`} variant="caption" sx={{ color: 'inherit' }}>
                    • {tag}
                  </Typography>
                ) : null,
              )}
            </Stack>
          }
          arrow
        >
          <ButtonBase
            sx={{
              px: 0.5,
              borderRadius: TAG_BORDER_RADIUS,
              fontSize,
              fontWeight: 600,
              color: theme.palette.primary.main,
              '&:hover': { textDecoration: 'underline' },
              '&:focus-visible': {
                outline: `2px solid ${alpha(theme.palette.primary.main, 0.6)}`,
                outlineOffset: 2,
              },
            }}
            aria-label={t('financeTransactions.transactionTable.tags.moreAriaLabel', {
              count: tags.length - 2,
            })}
          >
            +{tags.length - 2}
          </ButtonBase>
        </Tooltip>
      )}
    </Stack>
  );
});
