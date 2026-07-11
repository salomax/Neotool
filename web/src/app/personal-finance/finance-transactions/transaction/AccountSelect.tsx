import { Autocomplete, Box, TextField } from '@mui/material';
import type { SxProps, Theme } from '@mui/material/styles';
import { memo } from 'react';
import type { FinancialAccount } from '@/lib/graphql/types/__generated__/graphql';
import { useTranslation } from '@/shared/i18n';
import { personalFinanceTranslations } from '../../i18n';
import { FinancialAccountOption } from './FinancialAccountOption';

type AccountSelectVariant = 'standard' | 'field';

interface AccountSelectProps {
  value: string;
  accounts: FinancialAccount[];
  isReadonly: boolean;
  onChange: (id: string) => void;
  error?: boolean;
  helperText?: string;
  inputBottomBorderSx: SxProps<Theme>;
  variant?: AccountSelectVariant;
  label?: string;
  placeholder?: string;
  allowEmpty?: boolean;
  searchable?: boolean;
  dataTestId?: string;
}

export const AccountSelect = memo(function AccountSelect({
  value,
  accounts,
  isReadonly,
  onChange,
  error,
  helperText,
  inputBottomBorderSx,
  variant = 'standard',
  label,
  placeholder,
  allowEmpty = false,
  searchable = true,
  dataTestId,
}: AccountSelectProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const isFieldVariant = variant === 'field';
  const selectedAccount = accounts.find((account) => account.id === value) ?? null;
  const standardBorderSx = inputBottomBorderSx as Record<string, SxProps<Theme> | undefined>;
  const standardInputSx = {
    '& .MuiAutocomplete-inputRoot': {
      alignItems: 'flex-end',
      pt: '0 !important',
      pb: '0 !important',
    },
    '& .MuiInputBase-root': {
      alignItems: 'flex-end',
    },
    '& .MuiAutocomplete-input': {
      fontSize: '0.875rem',
      p: '0 24px 5px 0 !important',
      minWidth: 0,
    },
    '& .MuiInputBase-input': {
      fontSize: '0.875rem',
    },
    '& .MuiAutocomplete-endAdornment': {
      right: 0,
      top: 'calc(50% - 2px)',
    },
    '& .MuiInput-root:before': standardBorderSx['&:before'],
    '& .MuiInput-root:hover:not(.Mui-disabled, .Mui-error):before':
      standardBorderSx['&:hover:not(.Mui-disabled, .Mui-error):before'],
    '& .MuiInput-root:after': standardBorderSx['&:after'],
  } as SxProps<Theme>;

  return (
    <Box data-testid={dataTestId} sx={{ width: '100%', minWidth: 0 }}>
      <Autocomplete
        fullWidth
        options={accounts}
        getOptionLabel={(option) => option.name}
        value={selectedAccount}
        onChange={(_, nextValue) => onChange(nextValue?.id ?? '')}
        disableClearable={!allowEmpty}
        clearOnEscape={allowEmpty}
        isOptionEqualToValue={(a, b) => a.id === b.id}
        disabled={isReadonly}
        readOnly={isReadonly}
        noOptionsText={t('financeTransactions.selector.noAccountsFound')}
        openOnFocus={searchable}
        sx={{ width: '100%', minWidth: 0 }}
        slotProps={{
          paper: {
            sx: {
              minWidth: isFieldVariant ? undefined : 280,
            },
          },
        }}
        renderOption={(props, option) => (
          <Box component="li" {...props} key={option.id} data-testid={`account-select-option-${option.id}`}>
            <FinancialAccountOption account={option} />
          </Box>
        )}
        renderInput={(params) => (
          <TextField
            {...params}
            label={label}
            placeholder={placeholder}
            hiddenLabel={!isFieldVariant}
            error={error}
            helperText={helperText}
            fullWidth
            variant={isFieldVariant ? 'outlined' : 'standard'}
            InputProps={{
              ...params.InputProps,
              readOnly: isReadonly,
              ...(isFieldVariant ? {} : { disableUnderline: true }),
              sx: isFieldVariant ? undefined : inputBottomBorderSx,
            }}
            sx={isFieldVariant ? undefined : standardInputSx}
          />
        )}
      />
    </Box>
  );
});
