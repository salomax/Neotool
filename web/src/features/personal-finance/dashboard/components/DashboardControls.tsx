import React from 'react';
import {
  alpha,
  Box,
  Button,
  ButtonBase,
  Stack,
  Select,
  MenuItem,
  FormControl,
  Typography,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import DashboardCustomizeIcon from '@mui/icons-material/DashboardCustomize';
import CheckIcon from '@mui/icons-material/Check';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import { useDashboardStore } from '../store';
import { DateRangeOption } from '../types';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '@/app/personal-finance/i18n';
import Link from 'next/link';
import ArrowForwardIosIcon from '@mui/icons-material/ArrowForwardIos';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import ShoppingCartOutlinedIcon from '@mui/icons-material/ShoppingCartOutlined';

interface DashboardControlsProps {
  onAddWidget: () => void;
}

export const DashboardControls: React.FC<DashboardControlsProps> = ({ onAddWidget }) => {
  const { isEditMode, setEditMode, dateRange, setDateRange, resetLayout } = useDashboardStore();
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));

  const getQuickActionCardSx = React.useCallback(
    (accentColor: string) => ({
      width: '100%',
      p: 2,
      borderRadius: `${theme.custom.radius.button}px`,
      border: `1px solid ${alpha(theme.palette.divider, 0.7)}`,
      bgcolor: 'background.paper',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      gap: 2,
      position: 'relative',
      overflow: 'hidden',
      boxShadow: theme.shadows[1],
      transition: 'transform 0.2s ease-in-out, box-shadow 0.2s ease-in-out, border-color 0.2s ease-in-out',
      '&::after': {
        content: '""',
        position: 'absolute',
        inset: 0,
        background: `radial-gradient(850px circle at 80% 100%, ${alpha(accentColor, 0.12)}, transparent 55%)`,
        opacity: 1,
        transition: 'opacity 0.7s',
        pointerEvents: 'none',
        zIndex: 0,
      },
      '& .quick-action-content': {
        position: 'relative',
        zIndex: 1,
        minWidth: 0,
      },
      '& .quick-action-arrow': {
        position: 'relative',
        zIndex: 1,
        transition: 'transform 0.2s ease-in-out, color 0.2s ease-in-out',
      },
      '& .quick-action-bg-icon': {
        position: 'absolute',
        right: 14,
        bottom: -44,
        opacity: 0.3,
        transition: 'transform 0.7s, opacity 0.7s',
        color: alpha(accentColor, 0.22),
        transform: 'translate(0, 0) scale(1.1)',
        zIndex: 0,
        pointerEvents: 'none',
      },
      '&:hover': {
        transform: 'translateY(-2px)',
        boxShadow: theme.custom.shadow(4, accentColor),
        borderColor: accentColor,
        '& .quick-action-arrow': {
          transform: 'translateX(4px)',
          color: accentColor,
        },
        '& .quick-action-bg-icon': {
          transform: 'translate(8px, 10px) scale(1.4)',
          opacity: 0.12,
        },
      },
      '&:active': {
        transform: 'translateY(-1px)',
      },
    }),
    [theme]
  );

  const timeRangeLabelByOption = React.useMemo<Record<DateRangeOption, string>>(
    () => ({
      '90d': t('dashboard.timeRange.options.90d'),
      '6m': t('dashboard.timeRange.options.6m'),
      ytd: t('dashboard.timeRange.options.ytd'),
      '1y': t('dashboard.timeRange.options.1y'),
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
            <ButtonBase
              component={Link}
              href="/personal-finance/finance-transactions"
              sx={{
                flex: 1,
                minWidth: isMobile ? '100%' : 340,
                borderRadius: `${theme.custom.radius.button}px`,
                textAlign: 'left',
                '&:focus-visible .quick-action-card': {
                  outline: `2px solid ${alpha(theme.palette.primary.main, 0.5)}`,
                  outlineOffset: 2,
                },
              }}
            >
              <Box
                className="quick-action-card"
                sx={{
                  ...getQuickActionCardSx(theme.palette.primary.main),
                }}
              >
                <Box className="quick-action-content" sx={{ display: 'flex', alignItems: 'center', gap: 2, minWidth: 0 }}>

                  <Box sx={{ minWidth: 0 }}>
                    <Typography variant="subtitle1" fontWeight={700} noWrap>
                      {t('dashboard.actions.financeMovements.title')}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" noWrap>
                      {t('dashboard.actions.financeMovements.description')}
                    </Typography>
                  </Box>
                </Box>
                <Box className="quick-action-arrow" sx={{ color: alpha(theme.palette.text.primary, 0.35), flex: '0 0 auto' }}>
                  <ArrowForwardIosIcon sx={{ fontSize: 16 }} />
                </Box>
                <Box className="quick-action-bg-icon">
                  <SwapHorizIcon sx={{ fontSize: 128 }} />
                </Box>
              </Box>
            </ButtonBase>

            <ButtonBase
              component={Link}
              href="/personal-finance/investments"
              sx={{
                flex: 1,
                minWidth: isMobile ? '100%' : 340,
                borderRadius: `${theme.custom.radius.button}px`,
                textAlign: 'left',
                '&:focus-visible .quick-action-card': {
                  outline: `2px solid ${alpha(theme.palette.success.main, 0.5)}`,
                  outlineOffset: 2,
                },
              }}
            >
              <Box
                className="quick-action-card"
                sx={{
                  ...getQuickActionCardSx(theme.palette.success.main),
                }}
              >
                <Box className="quick-action-content" sx={{ display: 'flex', alignItems: 'center', gap: 2, minWidth: 0 }}>

                  <Box sx={{ minWidth: 0 }}>
                    <Typography variant="subtitle1" fontWeight={700} noWrap>
                      {t('dashboard.actions.investmentsPortfolio.title')}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" noWrap>
                      {t('dashboard.actions.investmentsPortfolio.description')}
                    </Typography>
                  </Box>
                </Box>
                <Box className="quick-action-arrow" sx={{ color: alpha(theme.palette.text.primary, 0.35), flex: '0 0 auto' }}>
                  <ArrowForwardIosIcon sx={{ fontSize: 16 }} />
                </Box>
                <Box className="quick-action-bg-icon">
                  <TrendingUpIcon sx={{ fontSize: 128 }} />
                </Box>
              </Box>
            </ButtonBase>

            <ButtonBase
              component={Link}
              href="/personal-finance/prices-consumption"
              sx={{
                flex: 1,
                minWidth: isMobile ? '100%' : 340,
                borderRadius: `${theme.custom.radius.button}px`,
                textAlign: 'left',
                '&:focus-visible .quick-action-card': {
                  outline: `2px solid ${alpha(theme.palette.info.main, 0.5)}`,
                  outlineOffset: 2,
                },
              }}
            >
              <Box
                className="quick-action-card"
                sx={{
                  ...getQuickActionCardSx(theme.palette.info.main),
                }}
              >
                <Box className="quick-action-content" sx={{ display: 'flex', alignItems: 'center', gap: 2, minWidth: 0 }}>
                  <Box sx={{ minWidth: 0 }}>
                    <Typography variant="subtitle1" fontWeight={700} noWrap>
                      {t('dashboard.actions.pricesConsumption.title')}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" noWrap>
                      {t('dashboard.actions.pricesConsumption.description')}
                    </Typography>
                  </Box>
                </Box>
                <Box className="quick-action-arrow" sx={{ color: alpha(theme.palette.text.primary, 0.35), flex: '0 0 auto' }}>
                  <ArrowForwardIosIcon sx={{ fontSize: 16 }} />
                </Box>
                <Box className="quick-action-bg-icon">
                  <ShoppingCartOutlinedIcon sx={{ fontSize: 128 }} />
                </Box>
              </Box>
            </ButtonBase>
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
            <MenuItem value="90d">{t('dashboard.timeRange.options.90d')}</MenuItem>
            <MenuItem value="6m">{t('dashboard.timeRange.options.6m')}</MenuItem>
            <MenuItem value="ytd">{t('dashboard.timeRange.options.ytd')}</MenuItem>
            <MenuItem value="1y">{t('dashboard.timeRange.options.1y')}</MenuItem>
            <MenuItem value="all">{t('dashboard.timeRange.options.all')}</MenuItem>
          </Select>
        </FormControl>
        {isEditMode ? (
          <>
            {/* <Button 
              startIcon={<AddIcon />} 
              variant="outlined" 
              onClick={onAddWidget}
              size="small"
            >
              Add Widget
            </Button> */}
            <Button 
              startIcon={<RestartAltIcon />} 
              color="error" 
              onClick={resetLayout}
              size="small"
              variant="outlined"
              sx={{ minHeight: 40 }}
            >
              {t('dashboard.controls.resetLayout')}
            </Button>
            <Button 
              startIcon={<CheckIcon />} 
              variant="contained" 
              color="primary"
              onClick={() => setEditMode(false)}
              size="small"
              sx={{ minHeight: 40 }}
            >
              {t('dashboard.controls.done')}
            </Button>
          </>
        ) : (
          <Button 
            startIcon={<DashboardCustomizeIcon />} 
            variant="text" 
            onClick={() => setEditMode(true)}
            size="small"
            sx={{ minHeight: 40 }}
          >
            {t('dashboard.controls.customize')}
          </Button>
        )}
      </Stack>
    </Box>
  );
};
