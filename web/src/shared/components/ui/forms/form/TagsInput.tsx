import React, { useMemo } from 'react';
import {
  Autocomplete,
  TextField,
  Chip,
  Box,
  useTheme,
  alpha,
  Typography,
  TextFieldProps
} from '@mui/material';

interface TagsInputProps {
  value: string[];
  onChange: (newValue: string[]) => void;
  options: string[];
  placeholder?: string;
  label?: string;
  variant?: TextFieldProps['variant'];
  InputProps?: TextFieldProps['InputProps'];
}

// Generate a random color based on string content to be consistent
const stringToColor = (string: string) => {
  let hash = 0;
  for (let i = 0; i < string.length; i++) {
    hash = string.charCodeAt(i) + ((hash << 5) - hash);
  }
  
  const colors = [
    '#F44336', '#E91E63', '#9C27B0', '#673AB7', '#3F51B5', 
    '#2196F3', '#03A9F4', '#00BCD4', '#009688', '#4CAF50', 
    '#8BC34A', '#CDDC39', '#FFEB3B', '#FFC107', '#FF9800', 
    '#FF5722', '#795548', '#607D8B'
  ];
  
  const index = Math.abs(hash % colors.length);
  return colors[index];
};

export function TagsInput({ 
  value, 
  onChange, 
  options, 
  placeholder,
  label,
  variant = 'outlined',
  InputProps
}: TagsInputProps) {
  const theme = useTheme();

  return (
    <Autocomplete
      multiple
      freeSolo
      options={options}
      value={value}
      onChange={(_, newValue) => {
        onChange(newValue);
      }}
      renderTags={(value: readonly string[], getTagProps) =>
        value.map((option: string, index: number) => {
          const color = stringToColor(option);
          const { key, ...tagProps } = getTagProps({ index });
          return (
            <Chip
              key={key}
              variant="filled"
              label={<Typography variant="body2">{option}</Typography>}
              size="small"
              sx={{ 
                bgcolor: color ? alpha(color, 0.05) : undefined,
                color: color || undefined,
                borderRadius: '0.3rem',
                '& .MuiChip-deleteIcon': {
                  color: color ? alpha(color, 0.7) : undefined,
                  '&:hover': {
                    color: color || undefined,
                  }
                }
              }}
              {...tagProps}
            />
          );
        })
      }
      renderInput={(params) => (
        <TextField
          {...params}
          variant={variant}
          label={label}
          placeholder={value.length === 0 ? placeholder : ''}
          size="small"
          fullWidth
          InputProps={{
            ...params.InputProps,
            ...InputProps,
            sx: {
              ...(variant === 'outlined' ? { borderRadius: '0.3rem' } : {}),
              ...(InputProps?.sx || {})
            }
          }}
        />
      )}
      sx={{
        '& .MuiAutocomplete-tag': {
          margin: 0.5,
        },
      }}
    />
  );
}
