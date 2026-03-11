import React from 'react';
import {
  Box,
  Stack,
  Typography,
  Skeleton,
  Chip,
  Button,
  useTheme,
  alpha,
} from '@mui/material';
import { Label } from '@/shared/components/ui/primitives';
import { useTranslation } from 'react-i18next';
import { BillStatus, getBillStatusColor } from '../hooks/billUtils';
import { formatCurrency } from '@/shared/components/ui/forms/components/masks/br';
import { formatDate } from '@/shared/utils/date';

export type { BillStatus };

export interface BillSummaryData {
  totalAmount: number;
  closingDate: string; // ISO date string
  dueDate: string; // ISO date string
  status: BillStatus;
  creditLimit?: number | null;
}

interface BillSummaryHeaderProps {
  bill: BillSummaryData | undefined;
  loading: boolean;
  onPayClick?: () => void;
}

export const BillSummaryHeader: React.FC<BillSummaryHeaderProps> = ({
  bill,
  loading,
  onPayClick,
}) => {
  const theme = useTheme();
  const { t, i18n } = useTranslation('personal-finance');

  const totalAmount = bill?.totalAmount;
  const creditLimit = bill?.creditLimit ?? null;
  const availableCreditLimit =
    creditLimit != null && totalAmount != null ? Math.max(creditLimit - totalAmount, 0) : null;

  const isPaid = bill?.status === 'PAID';
  const isOverdue = bill?.status === 'OVERDUE';

  const getStatusColorMain = () => {
    if (!bill?.status) return theme.palette.grey[500];
    const color = getBillStatusColor(bill.status, theme);
    // Handle case where color is grey (no main property)
    return (color as any).main || theme.palette.grey[700];
  };

  const statusColorMain = getStatusColorMain();
    
  const statusLabel = bill?.status
    ? t(`financeTransactions.billSummary.billStatus.${bill.status}`)
    : '-';

  const renderSkeletonRow = () => (
    <Box>
      <Skeleton variant="text" width={100} sx={{ mb: 1 }} />
      <Skeleton variant="text" width={180} height={50} sx={{ mb: 2 }} />
      
      <Stack direction="row" spacing={4} mb={3}>
        <Box>
          <Skeleton variant="text" width={80} sx={{ mb: 0.5 }} />
          <Skeleton variant="text" width={120} />
        </Box>
        <Box>
          <Skeleton variant="text" width={80} sx={{ mb: 0.5 }} />
          <Skeleton variant="text" width={120} />
        </Box>
      </Stack>

      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={3}>
        <Skeleton variant="text" width={60} />
        <Skeleton variant="rectangular" width={80} height={24} sx={{ borderRadius: 1 }} />
      </Stack>

      <Skeleton variant="rectangular" height={48} width="100%" sx={{ borderRadius: 1 }} />
    </Box>
  );

  if (loading && !bill) {
    return renderSkeletonRow();
  }

  return (
    <Box
      sx={{
        borderRadius: 3,
        border: `1px solid ${theme.palette.divider}`,
        p: 3,
        bgcolor: theme.palette.background.paper,
        maxWidth: { xs: '100%', sm: 380 }, // Constrain width for better aesthetics
      }}
    >
      {/* Header - Bill Amount */}
      <Box sx={{ mb: 3 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 0.5 }}>
          <Label>
            {t('financeTransactions.billSummary.billValue')}
          </Label>

          {loading ? (
            <Skeleton variant="rectangular" width={80} height={24} />
          ) : (
            <Chip
              label={statusLabel}
              size="small"
              sx={{
                height: 24,
                fontWeight: 700,
                textTransform: 'uppercase',
                borderRadius: 1,
                color: statusColorMain,
                bgcolor: alpha(statusColorMain, 0.12),
                px: 0.5,
              }}
            />
          )}
        </Stack>
        <Typography 
          variant="h3" 
          component="div"
          sx={{ 
            fontWeight: 700, 
            color: theme.palette.text.primary,
            fontSize: '2.3rem'
          }}
        >
          {loading ? (
            <Skeleton width={180} />
          ) : totalAmount != null ? (
            new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
              .formatToParts(totalAmount)
              .map((part, index) => {
                const isMain = part.type === 'integer' || part.type === 'group';
                return (
                  <Box
                    key={index}
                    component="span"
                    sx={{
                      fontSize: isMain ? 'inherit' : '0.6em',
                      opacity: isMain ? 1 : 0.7, // Slightly fade the smaller parts for better visual hierarchy
                      mr: part.type === 'currency' ? 0.5 : 0, // Ensure a tiny bit of space after currency symbol if literal is missing or tight
                    }}
                  >
                    {part.value}
                  </Box>
                );
              })
          ) : (
            '-'
          )}
        </Typography>
      </Box>

      {/* Details Row - Due Date & Available Limit */}
      <Stack direction="row" spacing={2} sx={{ mb: 3 }}>
        <Box sx={{ flex: 1 }}>
          <Label>
            {t('financeTransactions.billSummary.closingDate')}
          </Label>
          <Typography variant="body1" sx={{ fontWeight: 500 }}>
            {loading ? (
              <Skeleton width={120} />
            ) : bill?.closingDate ? (
              formatDate(bill?.closingDate, { 
                locale: i18n.language === 'pt' ? 'pt-BR' : 'en-US', 
                dateStyle: 'short' 
              })
            ) : (
              '-'
            )}
          </Typography>
        </Box>

        <Box sx={{ flex: 1 }}>
          <Label>
            {t('financeTransactions.billSummary.dueDate')}
          </Label>
          <Typography variant="body1" sx={{ fontWeight: 500 }}>
            {loading ? (
              <Skeleton width={120} />
            ) : bill?.dueDate ? (
              formatDate(bill?.dueDate, { 
                locale: i18n.language === 'pt' ? 'pt-BR' : 'en-US', 
                dateStyle: 'short' 
              })
            ) : (
              '-'
            )}
          </Typography>
        </Box>        

      </Stack>
      <Stack direction="row" spacing={4} sx={{ mb: 3 }}>
        <Box sx={{ width: '100%' }}>
          <Stack direction="row" justifyContent="space-between" alignItems="baseline" sx={{ mb: 0.5 }}>
            <Label>
              {t('financeTransactions.billSummary.availableLimit')}
            </Label>
            <Typography variant="body1" sx={{ fontWeight: 500 }}>
              {loading ? (
                <Skeleton width={100} />
              ) : availableCreditLimit != null ? (
                formatCurrency(availableCreditLimit)
              ) : (
                '-'
              )}
            </Typography>
          </Stack>

          {!loading && availableCreditLimit != null && creditLimit != null && totalAmount != null && (
            <Box 
              sx={{ 
                height: 6, 
                borderRadius: 3, 
                display: 'flex', 
                overflow: 'hidden', 
                width: '100%', 
                bgcolor: theme.palette.grey[100] 
              }}
            >
              <Box 
                sx={{ 
                  flex: totalAmount, 
                  bgcolor: statusColorMain,
                  transition: 'flex 0.5s ease-in-out'
                }} 
              />
              <Box 
                sx={{ 
                  flex: availableCreditLimit, 
                  bgcolor: theme.palette.grey[300],
                  transition: 'flex 0.5s ease-in-out'
                }} 
              />
            </Box>
          )}
        </Box>
      </Stack>


      {/* Action Button */}
      {!isPaid && !loading && (
        <Button
          variant="contained"
          color="primary"
          fullWidth
          size="large"
          onClick={onPayClick}
          sx={{ 
            fontWeight: 700, 
            py: 1.5,
            fontSize: '1rem',
            boxShadow: theme.shadows[4]
          }}
        >
          {t('financeTransactions.billSummary.payBill')}
        </Button>
      )}

      {/* Overdue Message */}
      {isOverdue && !loading && (
        <Typography
          variant="caption"
          sx={{ 
            mt: 2, 
            display: 'block', 
            color: theme.palette.error.main, 
            textAlign: 'center',
            fontWeight: 600
          }}
        >
          {t('financeTransactions.billSummary.overdueMessage')}
        </Typography>
      )}
    </Box>
  );
};
