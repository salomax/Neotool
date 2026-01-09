"use client";

import React from "react";
import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
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
  const React = (await import("react")) as typeof import("react");

  const Box = ({ children, ...props }: any) => <div {...props}>{children}</div>;
  const CircularProgress = () => <div data-testid="circular-progress" />;
  const Chip = ({ label, ...props }: any) => <div {...props}>{label}</div>;
  const InputAdornment = ({ children, ...props }: any) => <span {...props}>{children}</span>;

  const TextField = ({ inputProps, ...props }: any) => {
    return <input data-testid="search-input" {...props} {...inputProps} />;
  };

  const Autocomplete = ({
    options,
    inputValue,
    onInputChange,
    onChange,
    open,
    onOpen,
    onClose,
    renderInput,
    getOptionLabel,
  }: any) => {
    (window as any).__mockAutocompleteOpen = open;

    const highlightedIndexRef = React.useRef<number | null>(null);

    const internalOnKeyDown = (event: React.KeyboardEvent<HTMLInputElement>) => {
      if (event.key === "ArrowDown") {
        highlightedIndexRef.current = options.length > 0 ? 0 : null;
        return;
      }
      if (event.key === "Enter") {
        const idx = highlightedIndexRef.current;
        if (idx == null) return;
        const option = options[idx];
        onChange(event as any, option, "selectOption");
        onInputChange?.(event as any, getOptionLabel(option), "reset");
      }
    };

    const inputElement = renderInput({
      InputProps: {},
      inputProps: {
        value: inputValue ?? "",
        onFocus: () => onOpen?.(),
        onBlur: () => onClose?.(),
        onChange: (e: any) => onInputChange?.(e, e.target.value, "input"),
        onKeyDown: internalOnKeyDown,
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

describe("SearchableAutocomplete", () => {
  it("updates input with selected option label on keyboard Enter selection", async () => {
    const user = userEvent.setup();
    const onSearch = vi.fn();
    const onChange = vi.fn();

    render(
      <SearchableAutocomplete<
        MockAutocompleteOption,
        MockAutocompleteOption,
        { options: MockAutocompleteOption[] },
        { query: string }
      >
        useQuery={() => ({
          data: {
            options: [
              { id: "1", label: "Alpha" },
              { id: "2", label: "Beta" },
            ],
          },
          loading: false,
          error: undefined,
          refetch: vi.fn(),
        })}
        getQueryVariables={(query) => ({ query })}
        extractData={(data) => data?.options ?? []}
        transformOption={(item) => item}
        selectedItems={[]}
        onChange={onChange}
        getOptionId={(o) => o.id}
        getOptionLabel={(o) => o.label}
        onSearch={onSearch}
        loadMode="eager"
      />
    );

    const input = screen.getByTestId("search-input") as HTMLInputElement;
    await user.click(input);
    await user.type(input, "a");

    await user.keyboard("{ArrowDown}{Enter}");

    expect(input.value).toBe("Alpha");
  });

  it("triggers search on Enter without keyboard navigation and closes popup", async () => {
    const user = userEvent.setup();
    const onSearch = vi.fn();

    render(
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
    await user.click(input);
    await user.type(input, "alp");

    expect((window as any).__mockAutocompleteOpen).toBe(true);

    await user.keyboard("{Enter}");

    expect(onSearch).toHaveBeenCalledWith("alp");
    expect((window as any).__mockAutocompleteOpen).toBe(false);
  });
});

