'use client';

import React from 'react';
import { AccountBalance as BankIcon } from '@mui/icons-material';
import {
  Autocomplete,
  Avatar,
  Box,
  CircularProgress,
  Typography,
} from '@mui/material';
import { TextField as ThemedTextField } from '@/shared/components/ui/forms/form/TextField';
import { Issuer } from '@/lib/graphql/types/__generated__/graphql';

type IssuerOption = {
  label: string;
  value: string;
  logo: string | null;
  compeCode: string | null;
};

export interface IssuerSelectProps {
  issuers?: Issuer[] | null;
  loading?: boolean;
  value?: string;
  onChange: (issuerId: string | undefined) => void;
  label: string;
  placeholder?: string;
  noOptionsText: string;
  customIssuerLabel: string;
  error?: boolean;
  helperText?: React.ReactNode;
  size?: 'small' | 'medium';
  fullWidth?: boolean;
}

export function IssuerSelect({
  issuers,
  loading,
  value,
  onChange,
  label,
  placeholder,
  noOptionsText,
  customIssuerLabel,
  error,
  helperText,
  size = 'medium',
  fullWidth = true,
}: IssuerSelectProps) {
  const options = React.useMemo<IssuerOption[]>(
    () => [
      ...(issuers ?? []).map((issuer) => ({
        label: issuer.name,
        value: issuer.id,
        logo: issuer.logoAsset?.publicUrl ?? null,
        compeCode: issuer.compeCode ?? null,
      })),
      {
        label: customIssuerLabel,
        value: 'custom',
        logo: null,
        compeCode: null,
      },
    ],
    [customIssuerLabel, issuers],
  );

  const selectedOption = React.useMemo(
    () => options.find((option) => option.value === value) || null,
    [options, value],
  );

  return (
    <Autocomplete<IssuerOption, false, false, false>
      options={options}
      loading={!!loading}
      value={selectedOption}
      onChange={(_, newValue) => onChange(newValue ? newValue.value : undefined)}
      isOptionEqualToValue={(option, selectedValue) => option.value === selectedValue.value}
      noOptionsText={noOptionsText}
      getOptionLabel={(option) => (option.compeCode ? `${option.label} (${option.compeCode})` : option.label || '')}
      renderOption={(props, option) => {
        const { key, ...otherProps } = props;
        return (
          <Box
            component="li"
            key={key}
            {...otherProps}
            sx={{ display: 'flex', alignItems: 'center', gap: 2, py: 1.5 }}
          >
            {option.logo ? (
              <Avatar src={option.logo} sx={{ width: 40, height: 40, borderRadius: 1.5 }} />
            ) : (
              <Avatar
                sx={{
                  width: 40,
                  height: 40,
                  borderRadius: 1.5,
                  bgcolor: 'action.disabledBackground',
                }}
              >
                <BankIcon sx={{ fontSize: 24 }} />
              </Avatar>
            )}
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
              <Typography variant="body1" fontWeight="600">
                {option.label}
              </Typography>
              {option.compeCode && (
                <Typography variant="body2" color="text.secondary">
                  ({option.compeCode})
                </Typography>
              )}
            </Box>
          </Box>
        );
      }}
      renderInput={(params) => (
        <ThemedTextField
          {...params}
          fullWidth={fullWidth}
          size={size}
          label={label}
          placeholder={placeholder}
          error={!!error}
          helperText={helperText}
          InputProps={{
            ...params.InputProps,
            startAdornment: selectedOption ? (
              <React.Fragment>
                {selectedOption.logo ? (
                  <Avatar
                    src={selectedOption.logo}
                    sx={{ width: 24, height: 24, borderRadius: 1, mr: 1 }}
                  />
                ) : (
                  <Avatar
                    sx={{
                      width: 24,
                      height: 24,
                      borderRadius: 1,
                      mr: 1,
                      bgcolor: 'action.disabledBackground',
                    }}
                  >
                    <BankIcon sx={{ fontSize: 16 }} />
                  </Avatar>
                )}
                {params.InputProps.startAdornment}
              </React.Fragment>
            ) : (
              params.InputProps.startAdornment
            ),
            endAdornment: (
              <React.Fragment>
                {loading ? <CircularProgress color="inherit" size={20} /> : null}
                {params.InputProps.endAdornment}
              </React.Fragment>
            ),
          }}
        />
      )}
    />
  );
}
