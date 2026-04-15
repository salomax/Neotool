import { Box, MenuItem, Select, Stack, Typography } from '@mui/material';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import SwapHorizIcon from '@mui/icons-material/SwapHoriz';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { alpha } from '@mui/material';
import { memo } from 'react';
import { CreditDebitType } from '@/lib/graphql/types/__generated__/graphql';
import type { Theme } from '@mui/material/styles';

export type CreditDebitTypeExtended = CreditDebitType | 'TRANSFER_IN' | 'TRANSFER_OUT';

export function resolveTypeIconProps(type: CreditDebitTypeExtended): { icon: React.ReactNode; paletteColor: 'error' | 'success' | 'info' } {
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
}

export const TypeSelector = memo(function TypeSelector({ value, isReadonly, onChange, t }: TypeSelectorProps) {
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

  return (
    <Select
      value={value}
      onChange={(e) => onChange(e.target.value as CreditDebitTypeExtended)}
      SelectDisplayProps={{ 'data-testid': 'tx-credit-debit-type-select' } as React.HTMLAttributes<HTMLDivElement>}
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
