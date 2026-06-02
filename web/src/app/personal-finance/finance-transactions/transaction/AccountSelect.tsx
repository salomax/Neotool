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
}

export const AccountSelect = memo(function AccountSelect({
  value,
  accounts,
  isReadonly,
  onChange,
  error,
  helperText,
  inputBottomBorderSx,
}: AccountSelectProps) {
  const theme = useTheme();

  return (
    <TextField
      select
      value={value}
      onChange={(e) => onChange(e.target.value)}
      variant="standard"
      fullWidth
      disabled={isReadonly}
      error={error}
      helperText={helperText}
      InputProps={{
        disableUnderline: true,
        readOnly: isReadonly,
        sx: inputBottomBorderSx,
      }}
      sx={{ minWidth: 120 }}
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
