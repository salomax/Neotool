import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, fireEvent, cleanup } from "@testing-library/react";
import { ThemeProvider, createTheme } from "@mui/material/styles";
import { StatusToggle } from "../StatusToggle";

const theme = createTheme();
const wrapper = ({ children }: { children: React.ReactNode }) => (
  <ThemeProvider theme={theme}>{children}</ThemeProvider>
);

describe.sequential("StatusToggle", () => {
  afterEach(() => cleanup());

  it("should render", () => {
    render(
      <StatusToggle value={false} onChange={vi.fn()} data-testid="st-1" />,
      { wrapper }
    );
    expect(screen.getByTestId("st-1")).toBeInTheDocument();
  });

  it("should render checked state when value is true", () => {
    render(
      <StatusToggle value={true} onChange={vi.fn()} data-testid="st-2" />,
      { wrapper }
    );
    const switchEl = screen.getByRole("switch");
    expect(switchEl).toBeChecked();
  });

  it("should call onChange when toggled", async () => {
    const onChange = vi.fn().mockResolvedValue(undefined);
    render(
      <StatusToggle value={false} onChange={onChange} data-testid="st-3" />,
      { wrapper }
    );
    const switchEl = screen.getByRole("switch");
    fireEvent.click(switchEl);
    expect(onChange).toHaveBeenCalledWith(true);
  });
});
