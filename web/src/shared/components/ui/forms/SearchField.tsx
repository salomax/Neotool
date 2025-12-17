"use client";

import React, { useState, useEffect, useRef, useCallback, useMemo } from "react";
import { TextField, InputAdornment, IconButton, SxProps, Theme } from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import ClearIcon from "@mui/icons-material/Clear";
import { getTestIdProps } from '@/shared/utils/testid';

export interface SearchFieldProps {
  value: string;
  onChange: (_v: string) => void;
  onSearch?: (_v: string) => void; // debounced callback
  debounceMs?: number;
  placeholder?: string;
  autoFocusOnSlash?: boolean;
  autoFocus?: boolean;
  fullWidth?: boolean;
  name?: string;
  'data-testid'?: string;
}

// Extract border styling to constant to reduce duplication
const BORDER_WIDTH = "2px !important";
const SVG_STROKE_STYLES = {
  strokeWidth: BORDER_WIDTH,
  vectorEffect: "non-scaling-stroke" as const,
  strokeLinecap: "square" as const,
  strokeLinejoin: "miter" as const,
};

const searchFieldStyles: SxProps<Theme> = {
  "& .MuiOutlinedInput-root": {
    "& fieldset, &:hover fieldset, &.Mui-focused fieldset": {
      borderWidth: BORDER_WIDTH,
    },
    "& .MuiOutlinedInput-notchedOutline, &.Mui-focused .MuiOutlinedInput-notchedOutline": {
      borderWidth: BORDER_WIDTH,
      top: "-1px",
      "& path, & > path": SVG_STROKE_STYLES,
    },
  },
};

export function SearchField({
  value,
  onChange,
  onSearch,
  debounceMs = 300,
  placeholder = "Search…",
  autoFocusOnSlash = true,
  autoFocus = false,
  fullWidth = true,
  name,
  'data-testid': dataTestId,
}: SearchFieldProps) {
  const testIdProps = getTestIdProps('SearchField', name, dataTestId);
  
  const [inputValue, setInputValue] = useState(value);
  const inputRef = useRef<HTMLInputElement | null>(null);
  const onSearchRef = useRef(onSearch);

  // Keep onSearch ref up to date without causing effect re-runs
  useEffect(() => {
    onSearchRef.current = onSearch;
  }, [onSearch]);

  // Sync internal state with controlled value prop
  useEffect(() => {
    setInputValue(value);
  }, [value]);

  // Debounced search callback
  useEffect(() => {
    const timeoutId = setTimeout(() => {
      onSearchRef.current?.(inputValue);
    }, debounceMs);
    return () => clearTimeout(timeoutId);
  }, [inputValue, debounceMs]);

  // Auto-focus on mount
  useEffect(() => {
    if (autoFocus && inputRef.current) {
      // Use requestAnimationFrame for better timing than setTimeout(0)
      const frameId = requestAnimationFrame(() => {
        inputRef.current?.focus();
      });
      return () => cancelAnimationFrame(frameId);
    }
  }, [autoFocus]);

  // Keyboard shortcut handler (slash to focus)
  useEffect(() => {
    if (!autoFocusOnSlash) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "/" && document.activeElement !== inputRef.current) {
        e.preventDefault();
        inputRef.current?.focus();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [autoFocusOnSlash]);

  // Handle input change
  const handleChange = useCallback((e: React.ChangeEvent<HTMLInputElement>) => {
    const newValue = e.target.value;
    setInputValue(newValue);
    onChange(newValue);
  }, [onChange]);

  // Handle clear button click
  const handleClear = useCallback(() => {
    setInputValue("");
    onChange("");
    onSearchRef.current?.("");
  }, [onChange]);

  // Memoize input adornments
  const inputSlotProps = useMemo(() => ({
    startAdornment: (
      <InputAdornment position="start">
        <SearchIcon fontSize="small" />
      </InputAdornment>
    ),
    endAdornment: inputValue ? (
      <InputAdornment position="end">
        <IconButton
          size="small"
          onClick={handleClear}
          aria-label="Clear search"
        >
          <ClearIcon fontSize="small" />
        </IconButton>
      </InputAdornment>
    ) : null,
  }), [inputValue, handleClear]);

  return (
    <TextField
      inputRef={inputRef}
      value={inputValue}
      onChange={handleChange}
      placeholder={placeholder}
      fullWidth={fullWidth}
      {...testIdProps}
      sx={searchFieldStyles}
      slotProps={{ input: inputSlotProps }}
    />
  );
}
