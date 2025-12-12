"use client";

import React, { useMemo } from "react";
import {
  Autocomplete,
  TextField,
  CircularProgress,
  Box,
  Chip,
} from "@mui/material";
import { useDebouncedSearch } from "@/shared/hooks/search";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import type { QueryResult } from "@apollo/client";

/**
 * Props for SearchableAutocomplete component
 */
export interface SearchableAutocompleteProps<
  TOption extends { id: string },
  TSelected extends { id: string },
  TQueryData,
  TQueryVariables
> {
  /**
   * Query hook function (e.g., useGetUsersQuery) - component calls it internally
   */
  useQuery: (
    options?: {
      variables?: TQueryVariables;
      skip?: boolean;
      fetchPolicy?: "cache-first" | "network-only" | "cache-only" | "no-cache" | "standby";
      notifyOnNetworkStatusChange?: boolean;
    }
  ) => QueryResult<TQueryData, TQueryVariables>;

  /**
   * Factory function that returns query variables including search query
   */
  getQueryVariables: (searchQuery: string) => TQueryVariables;

  /**
   * Extract array of raw items from query data
   */
  extractData: (data: TQueryData | undefined) => Array<any>;

  /**
   * Transform raw item to option format
   */
  transformOption: (item: any) => TOption;

  /**
   * Selected items (already in option format)
   */
  selectedItems: TSelected[];

  /**
   * Value change handler
   */
  onChange: (selected: TSelected[]) => void;

  /**
   * Get unique ID from option
   */
  getOptionId: (option: TOption | TSelected) => string;

  /**
   * Get display label from option
   */
  getOptionLabel: (option: TOption | TSelected) => string;

  /**
   * Compare two options for equality
   */
  isOptionEqualToValue?: (option: TOption, value: TSelected) => boolean;

  /**
   * Standard Autocomplete props
   */
  multiple?: boolean;
  label?: string;
  placeholder?: string;
  disabled?: boolean;
  loading?: boolean;
  error?: Error | null;
  onRetry?: () => void;
  errorMessage?: string;
  /**
   * Form field error state (for react-hook-form integration)
   */
  fieldError?: boolean;
  /**
   * Form field helper text (for react-hook-form integration)
   */
  helperText?: string;

  /**
   * Custom rendering
   */
  renderOption?: (props: any, option: TOption) => React.ReactNode;
  renderTags?: (
    value: TSelected[],
    getTagProps: (params: { index: number }) => any
  ) => React.ReactNode;

  /**
   * Skip query when true
   */
  skip?: boolean;

  /**
   * Additional query options
   */
  fetchPolicy?: "cache-first" | "network-only" | "cache-only" | "no-cache" | "standby";
  notifyOnNetworkStatusChange?: boolean;

  /**
   * Debounce delay in milliseconds
   * @default 300
   */
  debounceMs?: number;
}

/**
 * SearchableAutocomplete - A reusable autocomplete component with server-side search
 * 
 * Features:
 * - Server-side search with GraphQL queries
 * - Debounced search input (300ms default)
 * - Merges selected items with search results (selected items always appear)
 * - Handles loading and error states
 * - Works as controlled component (standalone, parent handles form integration)
 * - Supports multiple selection
 * 
 * @example
 * ```tsx
 * <SearchableAutocomplete
 *   useQuery={useGetUsersQuery}
 *   getQueryVariables={(search) => ({ first: 100, query: search || undefined })}
 *   extractData={(data) => data?.users?.edges?.map(e => e.node) || []}
 *   transformOption={(user) => ({ id: user.id, label: user.displayName || user.email })}
 *   selectedItems={selectedUsers}
 *   onChange={setSelectedUsers}
 *   getOptionId={(opt) => opt.id}
 *   getOptionLabel={(opt) => opt.label}
 *   multiple
 *   label="Select Users"
 * />
 * ```
 */
export function SearchableAutocomplete<
  TOption extends { id: string },
  TSelected extends { id: string },
  TQueryData,
  TQueryVariables
>({
  useQuery,
  getQueryVariables,
  extractData,
  transformOption,
  selectedItems,
  onChange,
  getOptionId,
  getOptionLabel,
  isOptionEqualToValue,
  multiple = false,
  label,
  placeholder,
  disabled = false,
  loading: externalLoading = false,
  error: externalError = null,
  onRetry,
  errorMessage,
  fieldError = false,
  helperText,
  renderOption,
  renderTags,
  skip = false,
  fetchPolicy = "network-only",
  notifyOnNetworkStatusChange = true,
  debounceMs = 300,
}: SearchableAutocompleteProps<TOption, TSelected, TQueryData, TQueryVariables>) {
  // Debounced search
  const { inputValue, searchQuery, handleInputChange } = useDebouncedSearch({
    initialValue: "",
    debounceMs,
  });

  // Query variables based on debounced search
  const queryVariables = useMemo(
    () => getQueryVariables(searchQuery),
    [searchQuery, getQueryVariables]
  );

  // Execute query
  const {
    data,
    loading: queryLoading,
    error: queryError,
    refetch,
  } = useQuery({
    variables: queryVariables,
    skip: skip,
    fetchPolicy,
    notifyOnNetworkStatusChange,
  });

  // Extract and transform data
  const rawData = useMemo(() => extractData(data), [data, extractData]);
  const transformedOptions = useMemo(
    () => rawData.map(transformOption),
    [rawData, transformOption]
  );

  // Merge selected items with search results
  // Selected items should always appear, even if not in search results
  // Priority: search results (full data) > selected items (may be placeholders)
  const allOptions = useMemo(() => {
    // Create a map of search results by ID (these have full data)
    const searchResultsMap = new Map<string, TOption>();
    for (const opt of transformedOptions) {
      searchResultsMap.set(getOptionId(opt), opt);
    }
    
    // Create a set of search result IDs for quick lookup
    const searchResultIds = new Set(searchResultsMap.keys());
    
    // Find selected items that are NOT in search results (need to add them)
    // These are items that were selected but don't match current search
    const missingSelected = selectedItems.filter((selected) =>
      !searchResultIds.has(getOptionId(selected))
    );
    
    // Combine: search results first (they have full data), then missing selected items
    // Search results take priority because they're added first to the map
    return [...transformedOptions, ...missingSelected];
  }, [transformedOptions, selectedItems, getOptionId]);

  // Loading state
  const loading = queryLoading || externalLoading;

  // Error state
  const error = queryError || externalError;
  const handleRetry = onRetry || (() => refetch());

  // Get current value (selected items from allOptions to ensure full data)
  // IMPORTANT: This hook must be called before any conditional returns to follow Rules of Hooks
  const currentValue = useMemo(() => {
    if (multiple) {
      // Map selected items to their corresponding items in allOptions
      // This ensures we use items with full data (from search) when available
      return selectedItems
        .map((selected) => {
          const found = allOptions.find(
            (opt) => getOptionId(opt) === getOptionId(selected)
          );
          return found || selected;
        })
        .filter((item) => {
          // Only include if it exists in allOptions
          return allOptions.some((opt) => getOptionId(opt) === getOptionId(item));
        }) as TSelected[];
    } else {
      return selectedItems.length > 0
        ? (allOptions.find(
            (opt) => getOptionId(opt) === getOptionId(selectedItems[0])
          ) as TSelected) || null
        : null;
    }
  }, [selectedItems, allOptions, getOptionId, multiple]);

  // Handle value change
  const handleChange = (
    _event: React.SyntheticEvent,
    newValue: (TOption | TSelected) | (TOption | TSelected)[]
  ) => {
    const values = multiple
      ? (newValue as (TOption | TSelected)[])
      : newValue
      ? [newValue as TOption | TSelected]
      : [];

    // Deduplicate by ID
    const uniqueValues = Array.from(
      new Map(values.map((item) => [getOptionId(item), item])).values()
    ) as TSelected[];

    onChange(uniqueValues);
  };

  // Default option equality check
  const defaultIsOptionEqualToValue = (
    option: TOption | TSelected,
    value: TSelected
  ) => {
    if (isOptionEqualToValue) {
      return isOptionEqualToValue(option as TOption, value);
    }
    return getOptionId(option) === getOptionId(value);
  };

  // Show loading state (conditional return AFTER all hooks)
  if (loading && !data) {
    return (
      <Box sx={{ display: "flex", justifyContent: "center", p: 2 }}>
        <CircularProgress size={24} />
      </Box>
    );
  }

  // Show error state (conditional return AFTER all hooks)
  if (error) {
    return (
      <ErrorAlert
        error={error}
        onRetry={handleRetry}
        fallbackMessage={errorMessage || "Failed to load options"}
      />
    );
  }

  return (
    <Autocomplete
      multiple={multiple}
      options={allOptions}
      value={currentValue}
      onChange={handleChange}
      inputValue={inputValue}
      onInputChange={(_event, newInputValue) => {
        handleInputChange(newInputValue);
      }}
      getOptionLabel={getOptionLabel}
      isOptionEqualToValue={defaultIsOptionEqualToValue}
      loading={loading}
      disabled={disabled}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          placeholder={placeholder}
          error={fieldError}
          helperText={helperText}
          fullWidth
          InputProps={{
            ...params.InputProps,
            endAdornment: (
              <>
                {loading ? <CircularProgress size={16} /> : null}
                {params.InputProps.endAdornment}
              </>
            ),
          }}
        />
      )}
      renderOption={renderOption}
      renderTags={
        renderTags ||
        ((value: TSelected[], getTagProps) =>
          value.map((option, index) => {
            const { key, ...tagProps } = getTagProps({ index });
            return (
              <Chip
                key={key || getOptionId(option)}
                variant="outlined"
                label={getOptionLabel(option)}
                {...tagProps}
              />
            );
          }))
      }
    />
  );
}



