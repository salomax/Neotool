'use client';

import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import {
  Box,
  Checkbox,
  FormControl,
  ListItemText,
  MenuItem,
  Select,
  type SelectChangeEvent,
  Typography,
} from '@mui/material';

export interface RecurringPaymentsMultiSelectFilterProps<T extends string> {
  /** Shown before the colon (e.g. "Status"). */
  fieldLabel: string;
  options: { value: T; label: string }[];
  value: readonly T[];
  onChange: (next: T[]) => void;
  /** Summary when nothing is selected (e.g. "All"). */
  allLabel: string;
  selectedCountLabel: (count: number) => string;
  ariaLabel: string;
}

/** Keeps filter controls from resizing when the selected summary text changes. */
const FILTER_MIN_WIDTH_PX = 200;

/**
 * Pill-shaped multi-select control (label + primary summary + chevron), matching finance list filter prototypes.
 */
export function RecurringPaymentsMultiSelectFilter<T extends string>({
  fieldLabel,
  options,
  value,
  onChange,
  allLabel,
  selectedCountLabel,
  ariaLabel,
}: RecurringPaymentsMultiSelectFilterProps<T>) {
  const summaryLabel = (): string => {
    if (value.length === 0) return allLabel;
    if (value.length === 1) return options.find((o) => o.value === value[0])?.label ?? allLabel;
    return selectedCountLabel(value.length);
  };

  const handleChange = (event: SelectChangeEvent<T[]>) => {
    const raw = event.target.value;
    const next = (typeof raw === 'string' ? (raw.split(',') as T[]) : (raw as T[])) ?? [];
    onChange(next);
  };

  return (
    <FormControl
      size="small"
      variant="standard"
      sx={{
        minWidth: FILTER_MIN_WIDTH_PX,
        width: FILTER_MIN_WIDTH_PX,
        flexShrink: 0,
        '&::before, &::after': { display: 'none' },
      }}
    >
      <Select<T[]>
        multiple
        displayEmpty
        disableUnderline
        variant="standard"
        value={[...value]}
        onChange={handleChange}
        inputProps={{ 'aria-label': ariaLabel }}
        renderValue={() => (
          <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.5, maxWidth: '100%' }}>
            <Typography component="span" variant="body2" fontWeight={700} color="text.primary" sx={{ flexShrink: 0 }}>
              {fieldLabel}:
            </Typography>
            <Typography component="span" variant="body2" fontWeight={700} color="primary.main" noWrap sx={{ minWidth: 0 }}>
              {summaryLabel()}
            </Typography>
            <KeyboardArrowDownIcon sx={{ fontSize: 20, color: 'action.active', flexShrink: 0, ml: -0.25 }} />
          </Box>
        )}
        sx={{
          width: '100%',
          bgcolor: 'transparent',
          '&::before, &::after': { display: 'none' },
          '&:hover:not(.Mui-disabled)::before': { borderBottom: 'none' },
          '&.Mui-focused::after': { borderBottom: 'none' },
          '& .MuiSelect-select': {
            py: 0.75,
            pl: 0,
            pr: '0 !important',
            display: 'inline-flex',
            alignItems: 'center',
            width: '100%',
          },
          '& .MuiSelect-icon': {
            display: 'none',
          },
        }}
        MenuProps={{
          PaperProps: { sx: { maxHeight: 320, borderRadius: 2 } },
          slotProps: { list: { dense: true } },
        }}
      >
        {options.map((opt) => (
          <MenuItem key={opt.value} value={opt.value}>
            <Checkbox size="small" checked={value.includes(opt.value)} tabIndex={-1} sx={{ mr: 1, p: 0.5 }} />
            <ListItemText primary={opt.label} primaryTypographyProps={{ variant: 'body2' }} />
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  );
}
