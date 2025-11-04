"use client";

import React from 'react';
import { TextField, InputAdornment, Box } from '@mui/material';
import { Search as SearchIcon, Refresh as RefreshIcon } from '@mui/icons-material';
import { Button } from '../primitives/Button';
import { getTestIdProps } from '@/shared/utils/testid';

export interface FilterOption {
  value: string;
  label: string;
}

export interface SearchFiltersProps {
  /** Search term value */
  searchTerm: string;
  /** Search term change handler */
  onSearchChange: (value: string) => void;
  /** Search placeholder text */
  searchPlaceholder?: string;
  /** Filter options */
  filters?: Array<{
    key: string;
    label: string;
    value: string;
    options: FilterOption[];
    onChange: (value: string) => void;
  }>;
  /** Refresh handler */
  onRefresh?: () => void;
  /** Whether refresh is loading */
  refreshLoading?: boolean;
  /** Additional actions */
  actions?: React.ReactNode;
  /** Gap between elements */
  gap?: number;
  /** Name for generating test ID */
  name?: string;
  /** Test identifier */
  'data-testid'?: string;
}

export function SearchFilters({
  searchTerm,
  onSearchChange,
  searchPlaceholder = "Search...",
  filters = [],
  onRefresh,
  refreshLoading = false,
  actions,
  gap = 2,
  name,
  'data-testid': dataTestId
}: SearchFiltersProps) {
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('SearchFilters', name, dataTestId);
  
  return (
    <Box
      sx={{ 
        display: "flex", 
        gap: gap, // Use the gap prop directly
        alignItems: "center", 
        justifyContent: "flex-start",
        flexWrap: "wrap"
      }}
      {...testIdProps}
    >
      {/* Search Input */}
      <TextField
        placeholder={searchPlaceholder}
        value={searchTerm}
        onChange={(e) => onSearchChange(e.target.value)}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <SearchIcon />
            </InputAdornment>
          ),
        }}
        sx={{ minWidth: 200 }}
      />

      {/* Dynamic Filters */}
      {filters.map((filter) => (
        <TextField
          key={filter.key}
          select
          label={filter.label}
          value={filter.value}
          onChange={(e) => filter.onChange(e.target.value)}
          SelectProps={{ native: true }}
          sx={{ minWidth: 120 }}
        >
          {filter.options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </TextField>
      ))}

      {/* Refresh Button */}
      {onRefresh && (
        <Button
          variant="outlined"
          startIcon={<RefreshIcon />}
          onClick={onRefresh}
          loading={refreshLoading}
        >
          Refresh
        </Button>
      )}

      {/* Additional Actions */}
      {actions}
    </Box>
  );
}

export default SearchFilters;
