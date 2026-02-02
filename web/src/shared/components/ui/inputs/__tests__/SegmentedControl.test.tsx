import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, fireEvent, cleanup, within } from "@testing-library/react";
import { ThemeProvider, createTheme } from "@mui/material/styles";
import { SegmentedControl } from "../SegmentedControl";

const theme = createTheme();
const wrapper = ({ children }: { children: React.ReactNode }) => (
  <ThemeProvider theme={theme}>{children}</ThemeProvider>
);

const options = [
  { value: "a", label: "Option A" },
  { value: "b", label: "Option B" },
  { value: "c", label: "Option C" },
];

describe("SegmentedControl", () => {
  afterEach(() => {
    cleanup();
  });

  it("should render all options", () => {
    const onChange = vi.fn();
    const { container } = render(
      <SegmentedControl
        options={options}
        value="a"
        onChange={onChange}
      />,
      { wrapper }
    );
    const { getByRole } = within(container);
    expect(getByRole("button", { name: "Option A" })).toBeInTheDocument();
    expect(getByRole("button", { name: "Option B" })).toBeInTheDocument();
    expect(getByRole("button", { name: "Option C" })).toBeInTheDocument();
  });

  it("should call onChange when option is clicked", () => {
    const onChange = vi.fn();
    const { container } = render(
      <SegmentedControl
        options={options}
        value="a"
        onChange={onChange}
      />,
      { wrapper }
    );
    const { getByRole } = within(container);
    fireEvent.click(getByRole("button", { name: "Option B" }));
    expect(onChange).toHaveBeenCalledWith("b");
  });

  it("should show selected state for current value", () => {
    const { container } = render(
      <SegmentedControl
        options={options}
        value="b"
        onChange={vi.fn()}
      />,
      { wrapper }
    );
    const { getByRole } = within(container);
    const optionB = getByRole("button", { name: "Option B" });
    expect(optionB).toHaveAttribute("aria-pressed", "true");
  });
});
