'use client';

import { Box, IconButton, Stack, Typography, Link } from '@mui/material';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import dayjs from 'dayjs';
import 'dayjs/locale/pt-br';
import { useTranslation, i18n } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';

interface MonthYearCarouselProps {
  selectedMonth: number; // 1-12
  selectedYear: number;
  onChange: (month: number, year: number) => void;
}

export function MonthYearCarousel({ selectedMonth, selectedYear, onChange }: MonthYearCarouselProps) {
  const { t } = useTranslation(personalFinanceTranslations);

  const handlePrevMonth = () => {
    if (selectedMonth === 1) {
      onChange(12, selectedYear - 1);
    } else {
      onChange(selectedMonth - 1, selectedYear);
    }
  };

  const handleNextMonth = () => {
    if (selectedMonth === 12) {
      onChange(1, selectedYear + 1);
    } else {
      onChange(selectedMonth + 1, selectedYear);
    }
  };

  const locale = i18n.language?.toLowerCase().startsWith('pt') ? 'pt-br' : 'en';
  const currentDate = dayjs().locale(locale).year(selectedYear).month(selectedMonth - 1);
  const now = dayjs();
  const isCurrentMonth = now.month() === (selectedMonth - 1) && now.year() === selectedYear;

  const handleReset = () => {
    onChange(now.month() + 1, now.year());
  };

  return (
    <Box>
      <Stack spacing={0} sx={{ width: 'fit-content', alignItems: 'stretch' }}>
        {!isCurrentMonth && (
          <Box sx={{ display: 'flex', justifyContent: 'center', width: '100%' }}>
            <Link
              component="button"
              variant="body2"
              onClick={handleReset}
              sx={{ textTransform: 'none' }}
              underline="hover"
            >
              {t('financeTransactions.backToCurrentMonth')}
            </Link>
          </Box>
        )}

        <Stack direction="row" alignItems="center" spacing={0.5}>
          <IconButton
            onClick={handlePrevMonth}
            size="small"
            aria-label={t('financeTransactions.monthYearCarousel.prevAriaLabel')}
            data-testid="btn-prev-month"
          >
            <ChevronLeftIcon fontSize="small" />
          </IconButton>
          <Typography
            variant="body1"
            fontWeight="bold"
            data-testid="month-year-carousel-current"
            sx={{
              textTransform: 'capitalize',
              minWidth: 140,
              textAlign: 'center'
            }}
          >
            {currentDate.format('MMMM YYYY')}
          </Typography>
          <IconButton
            onClick={handleNextMonth}
            size="small"
            aria-label={t('financeTransactions.monthYearCarousel.nextAriaLabel')}
            data-testid="btn-next-month"
          >
            <ChevronRightIcon fontSize="small" />
          </IconButton>
        </Stack>
      </Stack>
    </Box>
  );
}
