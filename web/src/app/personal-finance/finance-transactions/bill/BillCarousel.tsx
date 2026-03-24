'use client';

import { Box, IconButton, Stack, Typography, Chip, useTheme } from '@mui/material';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import CircleIcon from '@mui/icons-material/Circle';
import dayjs from 'dayjs';
import 'dayjs/locale/pt-br';
import { i18n, useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { BillStatus, CreditCardBill, getBillStatusColor, getBillStatusLabel } from './billUtils';

export type { BillStatus };
export type CreditCardBillItem = Pick<CreditCardBill, 'id' | 'closingDate' | 'dueDate' | 'totalAmount' | 'status'>;

interface BillCarouselProps {
  bills: CreditCardBillItem[];
  selectedBillId: string | null;
  onSelectBill: (billId: string) => void;
}

export function BillCarousel({ bills, selectedBillId, onSelectBill }: BillCarouselProps) {
  const theme = useTheme();
  const { t } = useTranslation(personalFinanceTranslations);

  if (!bills || bills.length === 0) {
    return null;
  }

  const openBill = bills.find((b) => b.status === 'OPEN') ?? bills[0];
  if (!openBill) return null;

  const effectiveSelectedId = selectedBillId ?? openBill.id;
  const currentIndex = bills.findIndex((b) => b.id === effectiveSelectedId);
  const safeIndex = currentIndex === -1 ? 0 : currentIndex;
  const currentBill = bills[safeIndex];

  if (!currentBill) return null;

  const isFirst = safeIndex === bills.length - 1; // bills are expected in closingDate DESC
  const isLast = safeIndex === 0;

  const handlePrev = () => {
    if (isFirst) return;
    const nextIndexBill = bills[safeIndex + 1];
    if (nextIndexBill) {
        onSelectBill(nextIndexBill.id);
    }
  };

  const handleNext = () => {
    if (isLast) return;
    const prevIndexBill = bills[safeIndex - 1];
    if (prevIndexBill) {
        onSelectBill(prevIndexBill.id);
    }
  };

  const locale = i18n.language?.toLowerCase().startsWith('pt') ? 'pt-br' : 'en';
  const closingDate = dayjs(currentBill.closingDate).locale(locale);
  const monthLabel = closingDate.format('MMM/YYYY');
  const closingDayLabel = closingDate.format('D MMM');

  const statusDotColor = (status: BillStatus) => getBillStatusColor(status, theme).main;

  return (
    <Box sx={{ width: 'fit-content' }}>
      <Stack direction="row" alignItems="center" spacing={2}>
        <Stack direction="row" alignItems="center" spacing={0.5}>
          <IconButton onClick={handlePrev} size="small" disabled={isFirst}>
            <ChevronLeftIcon fontSize="small" />
          </IconButton>
          <Box sx={{ minWidth: 200, textAlign: 'center' }}>
            <Typography variant="body1" fontWeight="bold" sx={{ textTransform: 'capitalize' }}>
              {t('financeTransactions.billCarousel.title', { month: monthLabel })}
            </Typography>

        <Chip 
          label={getBillStatusLabel(currentBill.status)}
          size="small"
          sx={{
            fontWeight: 400,
            fontSize: '.7rem',
            color: theme.palette.common.white,
            bgcolor: statusDotColor(currentBill.status),
          }}
        />
          </Box>
          <IconButton onClick={handleNext} size="small" disabled={isLast}>
            <ChevronRightIcon fontSize="small" />
          </IconButton>
        </Stack>

      </Stack>




    </Box>
  );
}
