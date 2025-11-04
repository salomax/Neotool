"use client";

import * as React from "react";
import { TextField, InputAdornment, IconButton } from "@mui/material";
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
  fullWidth?: boolean;
  name?: string;
  'data-testid'?: string;
}

export const SearchField: React.FC<SearchFieldProps> = ({
  value,
  onChange,
  onSearch,
  debounceMs = 300,
  placeholder = "Search…",
  autoFocusOnSlash = true,
  fullWidth = true,
  name,
  'data-testid': dataTestId,
}) => {
  // Generate data-testid from component name and optional name prop
  const testIdProps = getTestIdProps('SearchField', name, dataTestId);
  
  const [t, setT] = React.useState(value);
  const ref = React.useRef<HTMLInputElement | null>(null);

  React.useEffect(() => setT(value), [value]);

  React.useEffect(() => {
    const id = setTimeout(() => onSearch?.(t), debounceMs);
    return () => clearTimeout(id);
  }, [t, debounceMs, onSearch]);

  React.useEffect(() => {
    if (!autoFocusOnSlash) return;
    const handler = (e: KeyboardEvent) => {
      if (e.key === "/" && document.activeElement !== ref.current) {
        e.preventDefault();
        ref.current?.focus();
      }
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [autoFocusOnSlash]);

  return (
    <TextField
      inputRef={ref}
      value={t}
      onChange={(e) => {
        setT(e.target.value);
        onChange(e.target.value);
      }}
      placeholder={placeholder}
      fullWidth={fullWidth}
      {...testIdProps}
      InputProps={{
        startAdornment: (
          <InputAdornment position="start">
            <SearchIcon fontSize="small" />
          </InputAdornment>
        ),
        endAdornment: t ? (
          <InputAdornment position="end">
            <IconButton
              size="small"
              onClick={() => {
                setT("");
                onChange("");
                onSearch?.("");
              }}
            >
              <ClearIcon fontSize="small" />
            </IconButton>
          </InputAdornment>
        ) : null,
      }}
    />
  );
};
