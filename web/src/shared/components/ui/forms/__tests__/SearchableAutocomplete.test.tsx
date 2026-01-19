"use client";

import React, { act } from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, waitFor, cleanup, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import { SearchableAutocomplete } from "../SearchableAutocomplete";

vi.mock("@/shared/hooks/ui/useDebounce", () => ({
  useDebounce: (value: any) => value,
}));

vi.mock("@/shared/components/ui/feedback", () => ({
  ErrorAlert: () => null,
}));

type MockAutocompleteOption = { id: string; label: string };

vi.mock("@mui/icons-material/Search", () => ({
  default: () => null,
}));

vi.mock("@mui/material", async () => {
  const Box = ({ children, ...props }: any) => <div {...props}>{children}</div>;
  const CircularProgress = () => <div data-testid="circular-progress" />;
  const Chip = ({ label, ...props }: any) => <div {...props}>{label}</div>;
  const InputAdornment = ({ children, ...props }: any) => <span {...props}>{children}</span>;

  // Omit MUI/TextField-specific props so they are not spread onto the native <input>
  const TextField = ({
    inputProps = {},
    InputProps: _InputProps,
    InputLabelProps: _InputLabelProps,
    fullWidth: _fullWidth,
    size: _size,
    variant: _variant,
    sx: _sx,
    label: _label,
    helperText: _helperText,
    error: _error,
    ...rest
  }: any) => {
    return <input data-testid="search-input" {...rest} {...inputProps} />;
  };

  const Autocomplete = ({
    inputValue,
    onInputChange,
    open,
    onOpen,
    onClose,
    renderInput,
  }: any) => {
    React.useEffect(() => {
      (window as any).__mockAutocompleteOpen = open;
    }, [open]);

    const inputElement = renderInput({
      InputProps: {},
      inputProps: {
        value: inputValue ?? "",
        onFocus: () => onOpen?.(),
        onBlur: () => onClose?.(),
        onChange: (e: any) => onInputChange?.(e, e.target.value, "input"),
      },
      InputLabelProps: {},
      disabled: false,
      fullWidth: true,
      size: "medium",
      variant: "outlined",
    });

    return (
      <div data-testid="autocomplete">
        {inputElement}
      </div>
    );
  };

  return {
    Autocomplete,
    TextField,
    CircularProgress,
    Box,
    Chip,
    InputAdornment,
  };
});

beforeEach(() => {
  cleanup();
  delete (window as any).__mockAutocompleteOpen;
});

describe("SearchableAutocomplete", () => {
  it("triggers search on Enter and closes popup", async () => {
    const user = userEvent.setup();
    const onSearch = vi.fn();

    const { container } = render(
      <SearchableAutocomplete<
        MockAutocompleteOption,
        MockAutocompleteOption,
        { options: MockAutocompleteOption[] },
        { query: string }
      >
        useQuery={() => ({
          data: {
            options: [{ id: "1", label: "Alpha" }],
          },
          loading: false,
          error: undefined,
          refetch: vi.fn(),
        })}
        getQueryVariables={(query) => ({ query })}
        extractData={(data) => data?.options ?? []}
        transformOption={(item) => item}
        selectedItems={[]}
        onChange={() => {}}
        getOptionId={(o) => o.id}
        getOptionLabel={(o) => o.label}
        onSearch={onSearch}
        loadMode="eager"
      />
    );

    const input = screen.getByTestId("search-input") as HTMLInputElement;

    // Focus the input to trigger onOpen
    await act(async () => {
      await user.click(input);
    });

    // Wait for popup to open
    await waitFor(() => {
      expect((window as any).__mockAutocompleteOpen).toBe(true);
    });

    await act(async () => {
      await user.type(input, "alp");
    });

    await act(async () => {
      await user.keyboard("{Enter}");
    });

    await waitFor(() => {
      expect(onSearch).toHaveBeenCalledWith("alp");
      expect((window as any).__mockAutocompleteOpen).toBe(false);
    });
  });
});

