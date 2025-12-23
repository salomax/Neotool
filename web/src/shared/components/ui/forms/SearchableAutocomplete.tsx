"use client";

import React, { useMemo, useState } from "react";
import {
  Autocomplete,
  TextField,
  CircularProgress,
  Box,
  Chip,
} from "@mui/material";
import { useDebounce } from "@/shared/hooks/ui/useDebounce";
import { ErrorAlert } from "@/shared/components/ui/feedback";

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
  // Using 'any' for the return type avoids tight coupling to a specific GraphQL client
  // while still enforcing the options shape at call sites.
  useQuery: (options?: {
    variables?: TQueryVariables;
    skip?: boolean;
    fetchPolicy?: "cache-first" | "network-only" | "cache-only" | "no-cache" | "standby";
    notifyOnNetworkStatusChange?: boolean;
  }) => {
    data?: TQueryData;
    loading: boolean;
    error?: Error;
    refetch: () => Promise<any>;
  };

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

  /**
   * TextField variant
   * @default "outlined"
   */
  variant?: "outlined" | "filled" | "standard";

  /**
   * Load mode for the query
   * - "lazy": Only run query when user searches (default)
   * - "eager": Run query on mount with empty search term, enable dropdown arrow
   * @default "lazy"
   */
  loadMode?: "lazy" | "eager";
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
  TSelected extends TOption,
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
  variant = "outlined",
  loadMode = "lazy",
}: SearchableAutocompleteProps<TOption, TSelected, TQueryData, TQueryVariables>) {
  // Local input value (what the user is typing)
  const [inputValue, setInputValue] = useState("");
  // Debounced value used for querying
  const debouncedSearch = useDebounce(inputValue, debounceMs);

  const trimmedSearch = debouncedSearch.trim();

  // Query variables based on debounced search
  const queryVariables = useMemo(
    () => getQueryVariables(trimmedSearch),
    [trimmedSearch, getQueryVariables]
  );

  // Determine if we should skip the query
  // In lazy mode: skip when search is empty
  // In eager mode: only skip when explicitly requested
  const shouldSkip = useMemo(() => {
    if (skip) return true;
    if (loadMode === "eager") return false;
    // lazy mode: skip when search is empty
    return trimmedSearch.length === 0;
  }, [skip, loadMode, trimmedSearch.length]);

  // Execute query
  const {
    data,
    loading: queryLoading,
    error: queryError,
    refetch,
  } = useQuery({
    variables: queryVariables,
    skip: shouldSkip,
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
  const allOptions: TOption[] = useMemo(() => {
    // Create a map of search results by ID (these have full data)
    // This also deduplicates any duplicate options from the query
    const searchResultsMap = new Map<string, TOption>();
    for (const opt of transformedOptions) {
      searchResultsMap.set(getOptionId(opt), opt);
    }
    
    // Create a set of search result IDs for quick lookup
    const searchResultIds = new Set(searchResultsMap.keys());
    
    // Find selected items that are NOT in search results (need to add them)
    // These are items that were selected but don't match current search
    const missingSelected = selectedItems.filter((selected) => {
      const id = getOptionId(selected);
      return !searchResultIds.has(id);
    });
    
    // Combine: deduplicated search results first (they have full data), then missing selected items
    // Search results take priority because they're added first to the map
    // Use Array.from(searchResultsMap.values()) to ensure deduplication
    return [...Array.from(searchResultsMap.values()), ...missingSelected];
  }, [transformedOptions, selectedItems, getOptionId]);

  // Loading state
  const loading = queryLoading || externalLoading;

  // Error state
  const error = queryError || externalError;
  const handleRetry = onRetry || (() => refetch());

  // Get current value (selected items from allOptions to ensure full data)
  // IMPORTANT: This hook must be called before any conditional returns to follow Rules of Hooks
  const currentValue: TOption | TOption[] | null = useMemo(() => {
    if (multiple) {
      // Map selected items to their corresponding items in allOptions
      // This ensures we use items with full data (from search) when available
      // Deduplicate by ID to prevent duplicate chips
      const valueMap = new Map<string, TSelected>();
      for (const selected of selectedItems) {
        const id = getOptionId(selected);
        if (!valueMap.has(id)) {
          const found = allOptions.find(
            (opt) => getOptionId(opt) === id
          );
          const item = (found || selected) as TSelected;
          // Only include if it exists in allOptions
          if (allOptions.some((opt) => getOptionId(opt) === getOptionId(item))) {
            valueMap.set(id, item);
          }
        }
      }
      return Array.from(valueMap.values());
    } else {
      if (selectedItems.length === 0) {
        return null;
      }
      const selected = selectedItems[0] as TSelected;
      const found = allOptions.find(
        (opt) => getOptionId(opt) === getOptionId(selected)
      );
      return found || selected;
    }
  }, [selectedItems, allOptions, getOptionId, multiple]);

  // Handle value change
  const handleChange = (
    _event: React.SyntheticEvent,
    newValue: TOption | TOption[] | null,
    reason: string
  ) => {
    // MULTIPLE MODE
    if (multiple) {
      // For removals/clear, trust MUI's newValue (it already reflects chips / clear button).
      if (reason === "removeOption" || reason === "clear") {
        const finalValues: TSelected[] = Array.isArray(newValue)
          ? (newValue as TSelected[])
          : [];

        // If nothing changed, do nothing
        if (finalValues.length === selectedItems.length) {
          return;
        }

        onChange(finalValues);

        // Clear the search when user clicks the clear button
        if (reason === "clear") {
          setInputValue("");
        }
        return;
      }

      // For selecting options, only ADD new items; clicking an already-selected
      // option should be a no-op (no toggle-off, no duplicates).
      const currentById = new Map(
        selectedItems.map((item) => [getOptionId(item), item as TSelected])
      );

      const incomingArray: TOption[] = Array.isArray(newValue)
        ? (newValue as TOption[])
        : newValue
        ? [newValue as TOption]
        : [];

      for (const option of incomingArray) {
        const id = getOptionId(option);
        if (!currentById.has(id)) {
          currentById.set(id, option as TSelected);
        }
      }

      const finalValues = Array.from(currentById.values());

      if (finalValues.length === selectedItems.length) {
        // No new items were added (e.g. re-selected an already-selected option)
        return;
      }

      onChange(finalValues);

      if (finalValues.length > 0 && reason === "selectOption") {
        setInputValue("");
      }
      return;
    }

    // SINGLE SELECTION MODE: behave like a normal select, but no-op if value didn't change.
    const nextValue = newValue ? (newValue as TOption) : null;
    const nextArray = nextValue ? [nextValue as TSelected] : [];

    const prevId =
      selectedItems.length > 0 && selectedItems[0]
        ? getOptionId(selectedItems[0] as TSelected)
        : null;
    const nextId =
      nextArray.length > 0 && nextArray[0]
        ? getOptionId(nextArray[0] as TSelected)
        : null;

    if (prevId === nextId) {
      return;
    }

    onChange(nextArray);
    if (nextArray.length > 0 && reason === "selectOption") {
      setInputValue("");
    }
  };

  // Default option equality check
  const defaultIsOptionEqualToValue = (option: TOption, value: TOption) => {
    if (isOptionEqualToValue) {
      return isOptionEqualToValue(option, value as unknown as TSelected);
    }
    return getOptionId(option) === getOptionId(value);
  };

  return (
    <Autocomplete
      multiple={multiple}
      options={allOptions}
      value={currentValue as any}
      onChange={handleChange}
      inputValue={inputValue}
      onInputChange={(_event, newInputValue, reason) => {
        // Preserve the current search text across option selection and other non-input changes.
        // We only update when the user is actively typing or explicitly clearing the field.
        if (reason === "input" || reason === "clear") {
          setInputValue(newInputValue);
        }
      }}
      autoHighlight
      // UX: show dropdown arrow in eager mode, hide in lazy mode
      popupIcon={loadMode === "eager" ? undefined : null}
      forcePopupIcon={loadMode === "eager"}
      getOptionLabel={getOptionLabel}
      isOptionEqualToValue={defaultIsOptionEqualToValue}
      loading={loading}
      disabled={disabled}
      renderInput={(params) => {
        // MUI Autocomplete's renderInput params don't include error/helperText
        // We handle error/helperText separately below
        const textFieldParams = params;
        
        // Only pass error prop when it's true, don't pass false
        const hasError = fieldError || !!error;
        const errorProp = hasError ? true : undefined;
        
        // Priority: field error message > query error message > helper text
        // Only pass helperText when it has a value
        const helperTextValue = fieldError && helperText
          ? helperText  // Field validation error takes priority
          : error 
          ? (errorMessage || "Failed to load options")  // Query error
          : helperText;  // Normal helper text
        
        return (
          <>
            <TextField
              {...textFieldParams}
              {...(label && { label })}
              placeholder={placeholder}
              {...(errorProp && { error: true })}
              {...(helperTextValue && { helperText: helperTextValue })}
              fullWidth
              variant={variant}
              InputProps={{
                ...params.InputProps,
                endAdornment: (
                  <>
                    {loading ? <CircularProgress size={16} /> : null}
                    {params.InputProps?.endAdornment}
                  </>
                ),
              }}
            />
          {error && (
            <Box sx={{ mt: 1 }}>
              <ErrorAlert
                error={error}
                onRetry={handleRetry}
                fallbackMessage={errorMessage || "Failed to load options"}
              />
            </Box>
          )}
        </>
        );
      }}
      renderOption={renderOption}
      renderTags={
        renderTags
          ? (value, getTagProps, _ownerState) =>
              renderTags(
                value as TSelected[],
                // Adapt MUI's getTagProps signature to the simpler one expected by callers
                ((params: { index: number }) => getTagProps(params)) as any
              )
          : (value, getTagProps) => {
              // Deduplicate by ID to ensure unique keys
              const seenIds = new Set<string>();
              return (value as TSelected[])
                .filter((option) => {
                  const id = getOptionId(option);
                  if (seenIds.has(id)) {
                    return false;
                  }
                  seenIds.add(id);
                  return true;
                })
                .map((option, index) => {
                  const { key: _key, ...tagProps } = getTagProps({ index });
                  return (
                    <Chip
                      key={getOptionId(option)}
                      variant="outlined"
                      color="primary"
                      label={getOptionLabel(option)}
                      {...tagProps}
                    />
                  );
                });
            }
      }
    />
  );
}



