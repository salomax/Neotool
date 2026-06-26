import { Box, ButtonBase, MenuItem, Select, Stack, Typography } from '@mui/material';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { alpha } from '@mui/material';
import { memo, type HTMLAttributes, type ReactNode } from 'react';
import { CreditDebitType } from '@/lib/graphql/types/__generated__/graphql';
import type { Theme } from '@mui/material/styles';

export type CreditDebitTypeExtended = CreditDebitType | 'TRANSFER_IN' | 'TRANSFER_OUT';

export function resolveTypeIconProps(type: CreditDebitTypeExtended): { icon: ReactNode; paletteColor: 'error' | 'success' | 'info' } {
  switch (type) {
    case CreditDebitType.Debit:
      return { icon: <ArrowDownwardIcon fontSize="small" sx={{ color: 'error.main' }} />, paletteColor: 'error' };
    case CreditDebitType.Credit:
      return { icon: <ArrowUpwardIcon fontSize="small" sx={{ color: 'success.main' }} />, paletteColor: 'success' };
    case 'TRANSFER_IN':
      return { icon: <ArrowForwardIcon fontSize="small" sx={{ color: 'info.main' }} />, paletteColor: 'info' };
    case 'TRANSFER_OUT':
      return { icon: <ArrowBackIcon fontSize="small" sx={{ color: 'info.main' }} />, paletteColor: 'info' };
    default:
      return { icon: <SwapHorizIcon fontSize="small" sx={{ color: 'info.main' }} />, paletteColor: 'info' };
  }
}

interface TypeSelectorProps {
  value: CreditDebitTypeExtended;
  isReadonly: boolean;
  onChange: (value: CreditDebitTypeExtended) => void;
  t: (key: string) => string;
  variant?: 'select' | 'carousel';
}

const TYPE_OPTIONS: Array<{
  value: CreditDebitTypeExtended;
  labelKey: 'financeTransactions.debit' | 'financeTransactions.credit' | 'financeTransactions.internalTransferIn' | 'financeTransactions.internalTransferOut';
}> = [
  { value: CreditDebitType.Credit, labelKey: 'financeTransactions.credit' },
  { value: CreditDebitType.Debit, labelKey: 'financeTransactions.debit' },
  { value: 'TRANSFER_OUT', labelKey: 'financeTransactions.internalTransferOut' },
  { value: 'TRANSFER_IN', labelKey: 'financeTransactions.internalTransferIn' },
];

function resolveCarouselLabel(
  option: (typeof TYPE_OPTIONS)[number],
  t: (key: string) => string,
): string {
  if (option.value === 'TRANSFER_OUT') return t('financeTransactions.typeShort.internalTransferOut');
  if (option.value === 'TRANSFER_IN') return t('financeTransactions.typeShort.internalTransferIn');
  return t(option.labelKey);
}

export const TypeSelector = memo(function TypeSelector({
  value,
  isReadonly,
  onChange,
  t,
  variant = 'select',
}: TypeSelectorProps) {
  const { icon, paletteColor } = resolveTypeIconProps(value);

  const typeIconBox = (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: 32,
        height: 32,
        bgcolor: (theme: Theme) => alpha(theme.palette[paletteColor].main, 0.1),
      }}
    >
      {icon}
    </Box>
  );

  if (isReadonly) {
    return (
      <Stack
        direction="row"
        data-testid="tx-credit-debit-type-readonly"
        sx={{ borderRadius: 1, overflow: 'hidden', border: 1, borderColor: 'divider' }}
      >
        {typeIconBox}
      </Stack>
    );
  }

  if (variant === 'carousel') {
    return (
      <Box
        data-testid="tx-credit-debit-type-carousel"
        sx={{
          display: 'grid',
          gridTemplateColumns: 'repeat(4, minmax(0, 1fr))',
          gap: 0.5,
          width: '100%',
        }}
      >
        {TYPE_OPTIONS.map((option) => {
          const isSelected = option.value === value;
          const { icon: optionIcon, paletteColor: optionPaletteColor } = resolveTypeIconProps(option.value);

          return (
            <ButtonBase
              key={option.value}
              onClick={() => onChange(option.value)}
              data-testid={`tx-credit-debit-type-option-${option.value}`}
              sx={{
                minWidth: 0,
                width: '100%',
                borderRadius: 0.75,
                border: 1,
                borderColor: isSelected ? `${optionPaletteColor}.main` : 'divider',
                backgroundColor: isSelected
                  ? (theme: Theme) => alpha(theme.palette[optionPaletteColor].main, 0.1)
                  : 'background.paper',
                px: 0.375,
                py: 0.675,
                minHeight: 34,
                justifyContent: 'center',
                textAlign: 'center',
              }}
            >
              <Stack
                direction="row"
                spacing={0.375}
                alignItems="center"
                justifyContent="center"
                sx={{ width: '100%', minWidth: 0 }}
              >
                <Box
                  sx={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                    '& .MuiSvgIcon-root': {
                      fontSize: 14,
                      color: isSelected ? `${optionPaletteColor}.main` : `${optionPaletteColor}.main`,
                    },
                  }}
                >
                  {optionIcon}
                </Box>
                <Typography
                  variant="body2"
                  sx={{
                    color: isSelected ? `${optionPaletteColor}.main` : 'text.primary',
                    fontWeight: isSelected ? 600 : 500,
                    lineHeight: 1,
                    fontSize: '0.64rem',
                    minWidth: 0,
                    whiteSpace: 'nowrap',
                  }}
                >
                  {resolveCarouselLabel(option, t)}
                </Typography>
              </Stack>
            </ButtonBase>
          );
        })}
      </Box>
    );
  }

  return (
    <Select
      value={value}
      onChange={(e) => onChange(e.target.value as CreditDebitTypeExtended)}
      SelectDisplayProps={{ 'data-testid': 'tx-credit-debit-type-select' } as HTMLAttributes<HTMLDivElement>}
      variant="standard"
      disableUnderline
      displayEmpty
      IconComponent={() => null}
      renderValue={(v) => {
        const { icon: renderIcon, paletteColor: renderPaletteColor } = resolveTypeIconProps(v as CreditDebitTypeExtended);
        return (
          <Stack direction="row" sx={{ borderRadius: 1, overflow: 'hidden', border: 1, borderColor: 'divider' }}>
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: 32,
                height: 32,
                bgcolor: (theme: Theme) => alpha(theme.palette[renderPaletteColor].main, 0.1),
              }}
            >
              {renderIcon}
            </Box>
            <Box
              sx={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: 24,
                bgcolor: 'background.paper',
                borderLeft: 1,
                borderColor: 'divider',
              }}
            >
              <ExpandMoreIcon fontSize="small" color="action" />
            </Box>
          </Stack>
        );
      }}
      sx={{
        minWidth: 'auto',
        '& .MuiSelect-select': { p: 0, display: 'flex', bgcolor: 'transparent' },
      }}
      MenuProps={{ PaperProps: { sx: { width: 280 } } }}
    >
      <MenuItem value={CreditDebitType.Debit}>
        <Stack direction="row" spacing={1} alignItems="center">
          <ArrowDownwardIcon fontSize="small" color="error" />
          <Typography variant="body2">{t('financeTransactions.debit')}</Typography>
        </Stack>
      </MenuItem>
      <MenuItem value={CreditDebitType.Credit}>
        <Stack direction="row" spacing={1} alignItems="center">
          <ArrowUpwardIcon fontSize="small" color="success" />
          <Typography variant="body2">{t('financeTransactions.credit')}</Typography>
        </Stack>
      </MenuItem>
      <MenuItem value="TRANSFER_IN">
        <Stack direction="row" spacing={1} alignItems="center">
          <ArrowForwardIcon fontSize="small" color="info" />
          <Typography variant="body2">{t('financeTransactions.internalTransferIn')}</Typography>
        </Stack>
      </MenuItem>
      <MenuItem value="TRANSFER_OUT">
        <Stack direction="row" spacing={1} alignItems="center">
          <ArrowBackIcon fontSize="small" color="info" />
          <Typography variant="body2">{t('financeTransactions.internalTransferOut')}</Typography>
        </Stack>
      </MenuItem>
    </Select>
  );
});
