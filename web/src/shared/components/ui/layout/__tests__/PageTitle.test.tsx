import React from "react";
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ThemeProvider, createTheme } from "@mui/material/styles";
import { PageTitle } from "../PageTitle";

const theme = createTheme();
const wrapper = ({ children }: { children: React.ReactNode }) => (
  <ThemeProvider theme={theme}>{children}</ThemeProvider>
);

describe("PageTitle", () => {
  it("should render title as h1", () => {
    render(<PageTitle>Settings</PageTitle>, { wrapper });
    const heading = screen.getByRole("heading", { level: 1, name: "Settings" });
    expect(heading).toBeInTheDocument();
  });

  it("should use name for data-testid", () => {
    render(<PageTitle name="settings">Settings</PageTitle>, { wrapper });
    expect(screen.getByTestId("pagetitle-settings")).toBeInTheDocument();
  });

  it("should use data-testid when provided", () => {
    render(
      <PageTitle data-testid="custom-title">Custom</PageTitle>,
      { wrapper }
    );
    expect(screen.getByTestId("custom-title")).toBeInTheDocument();
  });
});
