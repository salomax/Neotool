"use client";

import { useState, useEffect, useCallback } from "react";

/**
 * Options for useDebouncedSearch hook
 */
export interface UseDebouncedSearchOptions {
  /**
   * Initial search query value
   */
  initialValue?: string;
  /**
   * Debounce delay in milliseconds
   * @default 300
   */
  debounceMs?: number;
  /**
   * Callback to reset pagination when search changes
   */
  onSearchChange?: () => void;
  /**
   * External setter for search query (if you want to control searchQuery externally)
   */
  setSearchQuery?: (query: string) => void;
  /**
   * External search query value (if you want to control searchQuery externally)
   */
  searchQuery?: string;
}

/**
 * Return type for useDebouncedSearch hook
 */
export interface UseDebouncedSearchReturn {
  /**
   * Current input value (immediate updates for display)
   */
  inputValue: string;
  /**
   * Current search query (debounced, used for actual search)
   */
  searchQuery: string;
  /**
   * Handler for input changes (immediate update)
   */
  handleInputChange: (value: string) => void;
  /**
   * Handler for search (debounced, triggers actual search)
   */
  handleSearch: (value: string) => void;
  /**
   * Set search query directly (bypasses debounce)
   */
  setSearchQuery: (query: string) => void;
}

/**
 * Hook for managing search input with debounce.
 * 
 * Separates immediate input state (for display) from debounced search state (for queries).
 * This prevents UI lag while typing and reduces unnecessary API calls.
 * 
 * @param options - Configuration options
 * @returns Object with input value, search query, and handlers
 * 
 * @example
 * ```tsx
 * function SearchComponent() {
 *   const { inputValue, searchQuery, handleInputChange, handleSearch } = useDebouncedSearch({
 *     initialValue: "",
 *     debounceMs: 300,
 *     onSearchChange: () => goToFirstPage(),
 *   });
 * 
 *   return (
 *     <input
 *       value={inputValue}
 *       onChange={(e) => {
 *         handleInputChange(e.target.value);
 *         handleSearch(e.target.value);
 *       }}
 *     />
 *   );
 * }
 * ```
 */
export function useDebouncedSearch(
  options: UseDebouncedSearchOptions = {}
): UseDebouncedSearchReturn {
  const {
    initialValue = "",
    debounceMs = 300,
    onSearchChange,
    setSearchQuery: externalSetSearchQuery,
    searchQuery: externalSearchQuery,
  } = options;

  const [inputValue, setInputValue] = useState(initialValue);
  const [internalSearchQuery, setInternalSearchQuery] = useState(initialValue);
  
  // Use external searchQuery if provided, otherwise use internal
  const searchQuery = externalSearchQuery !== undefined ? externalSearchQuery : internalSearchQuery;
  const setSearchQuery = externalSetSearchQuery || setInternalSearchQuery;

  // Sync input value when searchQuery changes externally
  useEffect(() => {
    setInputValue(searchQuery);
  }, [searchQuery]);

  // Immediate input update (for display)
  const handleInputChange = useCallback((value: string) => {
    setInputValue(value);
  }, []);

  // Debounced search update (triggers actual search)
  useEffect(() => {
    const timeoutId = setTimeout(() => {
      if (inputValue !== searchQuery) {
        setSearchQuery(inputValue);
        // Reset to first page when search changes
        onSearchChange?.();
      }
    }, debounceMs);

    return () => clearTimeout(timeoutId);
  }, [inputValue, debounceMs, onSearchChange, searchQuery]);

  const handleSearch = useCallback(
    (value: string) => {
      setInputValue(value);
    },
    []
  );

  return {
    inputValue,
    searchQuery,
    handleInputChange,
    handleSearch,
    setSearchQuery,
  };
}

