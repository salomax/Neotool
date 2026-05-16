import React from 'react';
import {
  Box,
  Stack,
  Select,
  MenuItem,
  FormControl,
  IconButton,
  Tooltip,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import SavingsOutlinedIcon from '@mui/icons-material/SavingsOutlined';
import { useDashboardStore } from '../store';
import { DateRangeOption } from '../types';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '@/app/personal-finance/i18n';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import ShoppingCartOutlinedIcon from '@mui/icons-material/ShoppingCartOutlined';
import RepeatIcon from '@mui/icons-material/Repeat';
import { QuickActionCard } from './QuickActionCard';

interface DashboardControlsProps {
}

export const DashboardControls: React.FC<DashboardControlsProps> = () => {
  const { dateRange, setDateRange, resetLayout } = useDashboardStore();
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));

  const timeRangeLabelByOption = React.useMemo<Record<DateRangeOption, string>>(
    () => ({
      currentMonth: t('dashboard.timeRange.options.currentMonth'),
      '3m': t('dashboard.timeRange.options.3m'),
      '6m': t('dashboard.timeRange.options.6m'),
      '1y': t('dashboard.timeRange.options.1y'),
      '3y': t('dashboard.timeRange.options.3y'),
      all: t('dashboard.timeRange.options.all'),
      custom: t('dashboard.timeRange.options.custom'),
    }),
    [t]
  );

  return (
    <Box 
      sx={{ 
        display: 'flex', 
        justifyContent: 'space-between', 
        alignItems: isMobile ? 'stretch' : 'flex-end',
        mb: 2,
        flexDirection: isMobile ? 'column' : 'row',
        gap: 1.5
      }}
    >
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, width: isMobile ? '100%' : 'auto' }}>
        <Box sx={{ width: isMobile ? '100%' : 'auto' }}>
          <Stack direction={isMobile ? 'column' : 'row'} spacing={2} sx={{ width: '100%' }}>
            <QuickActionCard
              href="/personal-finance/finance-transactions"
              title={t('dashboard.actions.financeMovements.title')}
              description={t('dashboard.actions.financeMovements.description')}
              accentColor={theme.palette.primary.main}
              icon={<SwapHorizIcon sx={{ fontSize: 128 }} />}
              isMobile={isMobile}
            />

            <QuickActionCard
              href="/personal-finance/investments"
              title={t('dashboard.actions.investmentsPortfolio.title')}
              description={t('dashboard.actions.investmentsPortfolio.description')}
              accentColor={theme.palette.success.main}
              icon={<SavingsOutlinedIcon sx={{ fontSize: 128 }} />}
              isMobile={isMobile}
            />

            <QuickActionCard
              href="/personal-finance/prices-consumption"
              title={t('dashboard.actions.pricesConsumption.title')}
              description={t('dashboard.actions.pricesConsumption.description')}
              accentColor={theme.palette.info.main}
              icon={<ShoppingCartOutlinedIcon sx={{ fontSize: 128 }} />}
              isMobile={isMobile}
            />

            <QuickActionCard
              href="/personal-finance/recurring-payments"
              title={t('dashboard.actions.recurringPayments.title')}
              description={t('dashboard.actions.recurringPayments.description')}
              accentColor={theme.palette.warning.main}
              icon={<RepeatIcon sx={{ fontSize: 128 }} />}
              isMobile={isMobile}
            />
          </Stack>
        </Box>
      </Box>

      <Stack
        direction="row"
        spacing={1}
        alignItems="center"
        sx={{
          width: isMobile ? '100%' : 'auto',
          justifyContent: isMobile ? 'space-between' : 'flex-end',
          flexWrap: 'wrap',
        }}
      >
        <FormControl
          size="small"
          sx={{
            minWidth: 160,
            '& .MuiOutlinedInput-notchedOutline': { border: 0 },
          }}
        >
          <Select
            aria-label={t('dashboard.timeRange.ariaLabel')}
            value={dateRange.option}
            onChange={(e) => setDateRange(e.target.value as DateRangeOption)}
            displayEmpty
            renderValue={(value) => timeRangeLabelByOption[value as DateRangeOption] ?? String(value)}
            sx={{
              bgcolor: 'transparent',
              '& .MuiSelect-select': {
                py: 1,
              },
            }}
          >
            <MenuItem value="currentMonth">{t('dashboard.timeRange.options.currentMonth')}</MenuItem>
            <MenuItem value="3m">{t('dashboard.timeRange.options.3m')}</MenuItem>
            <MenuItem value="6m">{t('dashboard.timeRange.options.6m')}</MenuItem>
            <MenuItem value="1y">{t('dashboard.timeRange.options.1y')}</MenuItem>
            <MenuItem value="3y">{t('dashboard.timeRange.options.3y')}</MenuItem>
            <MenuItem value="all">{t('dashboard.timeRange.options.all')}</MenuItem>
          </Select>
        </FormControl>
        <Tooltip title={t('dashboard.controls.resetLayout')}>
          <IconButton aria-label={t('dashboard.controls.resetLayout')} size="small" onClick={resetLayout}>
            <RestartAltIcon fontSize="small" />
          </IconButton>
        </Tooltip>
      </Stack>
    </Box>
  );
};
