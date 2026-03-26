import React from 'react';
import { alpha, Box, ButtonBase, Typography, useTheme } from '@mui/material';
import { 
  AccountBalance as BankIcon, 
  CreditCard as CardIcon,
  ArrowForwardIos as ArrowForwardIosIcon
} from '@mui/icons-material';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';

interface FinancialAccountTypeSelectionProps {
  onSelect: (type: 'BANK' | 'CARD') => void;
}

export function FinancialAccountTypeSelection({ onSelect }: FinancialAccountTypeSelectionProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const optionRadius = theme.custom?.radius?.optionCard ?? 2;
  const hoverAccent = theme.palette.primary.main;

  const getOptionCardSx = React.useCallback(
    () => ({
      width: '100%',
      minHeight: { xs: 176, md: 196 },
      p: 3,
      borderRadius: optionRadius,
      border: `1px solid ${alpha(theme.palette.divider, 0.7)}`,
      bgcolor: 'background.paper',
      display: 'flex',
      alignItems: 'stretch',
      justifyContent: 'space-between',
      gap: 2,
      position: 'relative',
      overflow: 'hidden',
      boxShadow: theme.shadows[1],
      transition: 'transform 0.2s ease-in-out, box-shadow 0.2s ease-in-out, border-color 0.2s ease-in-out',
      '&::before': {
        content: '""',
        position: 'absolute',
        inset: 0,
        background: `radial-gradient(850px circle at 80% 100%, ${alpha(hoverAccent, 0.12)}, transparent 55%)`,
        opacity: 0,
        transition: 'opacity 0.7s',
        pointerEvents: 'none',
        zIndex: 0,
      },
      '&::after': {
        content: '""',
        position: 'absolute',
        inset: 0,
        background: `radial-gradient(850px circle at 80% 100%, ${alpha(hoverAccent, 0.10)}, transparent 85%)`,
        opacity: 1,
        pointerEvents: 'none',
        zIndex: 0,
      },
      '& .option-content': {
        position: 'relative',
        zIndex: 1,
        display: 'flex',
        flexDirection: 'column',
        flex: '1 1 auto',
        minWidth: 0,
      },
      '& .option-arrow': {
        position: 'relative',
        zIndex: 1,
        alignSelf: 'center',
        transition: 'transform 0.2s ease-in-out, color 0.2s ease-in-out',
        color: alpha(theme.palette.text.primary, 0.35),
        flex: '0 0 auto',
      },
      '& .option-action': {
        transition: 'color 0.2s ease-in-out',
        color: 'text.secondary',
      },
      '& .option-bg-icon': {
        position: 'absolute',
        right: 14,
        bottom: -4,
        opacity: 0.3,
        transition: 'transform 0.7s, opacity 0.7s',
        color: alpha(theme.palette.text.primary, 0.12),
        transform: 'translate(0, 0) scale(1.1)',
        zIndex: 0,
        pointerEvents: 'none',
      },
      '&:hover': {
        transform: 'translateY(-2px)',
        boxShadow: theme.custom?.shadow?.(4, hoverAccent) ?? theme.shadows[4],
        borderColor: hoverAccent,
        '&::before': {
          opacity: 1,
        },
        '& .option-arrow': {
          transform: 'translateX(4px)',
          color: hoverAccent,
        },
        '& .option-action': {
          color: hoverAccent,
        },
        '& .option-bg-icon': {
          transform: 'translate(8px, 10px) scale(1.4)',
          opacity: 0.12,
          color: alpha(hoverAccent, 0.22),
        },
      },
      '&:active': {
        transform: 'translateY(-1px)',
      },
    }),
    [hoverAccent, optionRadius, theme]
  );

  return (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: { xs: '1fr', md: 'repeat(2, minmax(0, 360px))' },
        justifyContent: 'center',
        gap: 3,
        width: '100%',
        mx: 'auto',
      }}
    >
      <ButtonBase
        onClick={() => onSelect('BANK')}
        data-testid="account-type-BANK"
        sx={{
          borderRadius: optionRadius,
          textAlign: 'left',
          width: '100%',
          maxWidth: { md: 360 },
          cursor: 'pointer',
          '&:focus-visible .option-card': {
            outline: `2px solid ${alpha(hoverAccent, 0.55)}`,
            outlineOffset: 2,
          },
        }}
      >
        <Box className="option-card" sx={getOptionCardSx()}>
          <Box className="option-content" sx={{ minWidth: 0 }}>
            <Typography variant="subtitle1" fontWeight={700} noWrap>
              {t('financeTransactions.createAccount.step1.bankAccount.label')}
            </Typography>
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{
                lineHeight: 1.6,
                display: '-webkit-box',
                WebkitLineClamp: 3,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
              }}
            >
              {t('financeTransactions.createAccount.step1.bankAccount.subtitle')}
            </Typography>
            <Typography className="option-action" variant="body2" sx={{ mt: 'auto', pt: 1, fontWeight: 700 }} noWrap>
              {t('financeTransactions.createAccount.step1.action')}
            </Typography>
          </Box>
          <Box className="option-arrow">
            <ArrowForwardIosIcon sx={{ fontSize: 16 }} />
          </Box>
          <Box className="option-bg-icon">
            <BankIcon sx={{ fontSize: 128 }} />
          </Box>
        </Box>
      </ButtonBase>

      <ButtonBase
        onClick={() => onSelect('CARD')}
        data-testid="account-type-CARD"
        sx={{
          borderRadius: optionRadius,
          textAlign: 'left',
          width: '100%',
          maxWidth: { md: 360 },
          cursor: 'pointer',
          '&:focus-visible .option-card': {
            outline: `2px solid ${alpha(hoverAccent, 0.55)}`,
            outlineOffset: 2,
          },
        }}
      >
        <Box className="option-card" sx={getOptionCardSx()}>
          <Box className="option-content" sx={{ minWidth: 0 }}>
            <Typography variant="subtitle1" fontWeight={700} noWrap>
              {t('financeTransactions.createAccount.step1.card.label')}
            </Typography>
            <Typography
              variant="body2"
              color="text.secondary"
              sx={{
                lineHeight: 1.6,
                display: '-webkit-box',
                WebkitLineClamp: 3,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
              }}
            >
              {t('financeTransactions.createAccount.step1.card.subtitle')}
            </Typography>
            <Typography className="option-action" variant="body2" sx={{ mt: 'auto', pt: 1, fontWeight: 700 }} noWrap>
              {t('financeTransactions.createAccount.step1.action')}
            </Typography>
          </Box>
          <Box className="option-arrow">
            <ArrowForwardIosIcon sx={{ fontSize: 16 }} />
          </Box>
          <Box className="option-bg-icon">
            <CardIcon sx={{ fontSize: 128 }} />
          </Box>
        </Box>
      </ButtonBase>
    </Box>
  );
}
