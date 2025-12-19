"use client";

import React, { useState, useEffect, useRef, useCallback, useMemo } from "react";
import { TextField, InputAdornment, IconButton, SxProps, Theme, useTheme } from "@mui/material";
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
const BORDER_WIDTH_FOCUSED = "2px !important";
const BORDER_WIDTH_UNFOCUSED = "1px !important";
const SVG_STROKE_STYLES_FOCUSED = {
  strokeWidth: BORDER_WIDTH_FOCUSED,
  vectorEffect: "non-scaling-stroke" as const,
  strokeLinecap: "square" as const,
  strokeLinejoin: "miter" as const,
};
const SVG_STROKE_STYLES_UNFOCUSED = {
  strokeWidth: BORDER_WIDTH_UNFOCUSED,
  vectorEffect: "non-scaling-stroke" as const,
  strokeLinecap: "square" as const,
  strokeLinejoin: "miter" as const,
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
  const theme = useTheme();
  const testIdProps = getTestIdProps('SearchField', name, dataTestId);
  
  // Get border widths from theme tokens
  const borderWidthUnfocused = `${(theme as any).custom?.border?.default ?? 1}px`;
  const borderWidthFocused = `${(theme as any).custom?.border?.focused ?? 2}px`;
  
  const searchFieldStyles: SxProps<Theme> = {
    "& .MuiOutlinedInput-root": {
      // Default and hover states: use theme token for unfocused border
      "& fieldset, &:hover fieldset": {
        borderWidth: borderWidthUnfocused,
      },
      // Focused state: use theme token for focused border
      "&.Mui-focused fieldset": {
        borderWidth: borderWidthFocused,
      },
      // Default and hover notched outline: use theme token
      "& .MuiOutlinedInput-notchedOutline": {
        borderWidth: borderWidthUnfocused,
        top: "-1px",
        "& path, & > path": {
          strokeWidth: borderWidthUnfocused,
          vectorEffect: "non-scaling-stroke" as const,
          strokeLinecap: "square" as const,
          strokeLinejoin: "miter" as const,
        },
      },
      // Focused notched outline: use theme token
      "&.Mui-focused .MuiOutlinedInput-notchedOutline": {
        borderWidth: borderWidthFocused,
        top: "-1px",
        "& path, & > path": {
          strokeWidth: borderWidthFocused,
          vectorEffect: "non-scaling-stroke" as const,
          strokeLinecap: "square" as const,
          strokeLinejoin: "miter" as const,
        },
      },
    },
  };
  
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
