import { Avatar, MenuItem, Stack, TextField, Typography, useTheme } from '@mui/material';
import { memo } from 'react';
import type { FinancialAccount } from '@/lib/graphql/types/__generated__/graphql';

const AVATAR_BORDER_RADIUS = '.3rem';

interface AccountSelectProps {
  value: string;
  accounts: FinancialAccount[];
  isReadonly: boolean;
  onChange: (id: string) => void;
  error?: boolean;
  helperText?: string;
  inputBottomBorderSx: object;
  variant?: 'standard' | 'field';
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
}: AccountSelectProps) {
  const theme = useTheme();
  const isFieldVariant = variant === 'field';

  return (
    <TextField
      select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      variant={isFieldVariant ? 'outlined' : 'standard'}
      fullWidth
      disabled={isReadonly}
      error={error}
      helperText={helperText}
      InputProps={{
        disableUnderline: !isFieldVariant,
        readOnly: isReadonly,
        sx: isFieldVariant
          ? {
              '& .MuiOutlinedInput-notchedOutline': {
                borderColor: error ? 'error.main' : 'divider',
                borderWidth: 1,
              },
              '&:hover .MuiOutlinedInput-notchedOutline': {
                borderColor: error ? 'error.main' : 'divider',
              },
              '&.Mui-focused .MuiOutlinedInput-notchedOutline': {
                borderColor: error ? 'error.main' : 'divider',
                borderWidth: 1,
              },
            }
          : inputBottomBorderSx,
      }}
      sx={{
        minWidth: isFieldVariant ? 0 : 120,
        '& .MuiInputBase-root': {
          minHeight: isFieldVariant ? 40 : undefined,
          borderRadius: isFieldVariant ? 1 : undefined,
        },
      }}
    >
      {accounts.map((account) => (
        <MenuItem key={account.id} value={account.id}>
          <Stack direction="row" spacing={1} alignItems="center">
            <Avatar
              src={account.issuer?.logoAsset?.publicUrl || undefined}
              sx={{
                width: 24,
                height: 24,
                fontSize: '0.6rem',
                borderRadius: AVATAR_BORDER_RADIUS,
                bgcolor: account.issuer?.logoAsset?.publicUrl
                  ? 'transparent'
                  : account.themeColor || theme.palette.grey[300],
              }}
            >
              {!account.issuer?.logoAsset?.publicUrl && account.name.charAt(0)}
            </Avatar>
            <Typography variant="body2" noWrap>
              {account.name}
            </Typography>
          </Stack>
        </MenuItem>
      ))}
    </TextField>
  );
});
