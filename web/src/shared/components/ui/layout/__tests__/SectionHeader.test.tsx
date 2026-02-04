import React from "react";
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ThemeProvider, createTheme } from "@mui/material/styles";
import { SectionHeader } from "../SectionHeader";

const theme = createTheme();
const wrapper = ({ children }: { children: React.ReactNode }) => (
  <ThemeProvider theme={theme}>{children}</ThemeProvider>
);

describe("SectionHeader", () => {
  it("should render title string as h2", () => {
    render(<SectionHeader title="Section Name" />, { wrapper });
    expect(
      screen.getByRole("heading", { level: 2, name: "Section Name" })
    ).toBeInTheDocument();
  });

  it("should render icon when provided", () => {
    render(
      <SectionHeader title="Section" icon={<span data-testid="icon">Icon</span>} />,
      { wrapper }
    );
    expect(screen.getByTestId("icon")).toBeInTheDocument();
  });

  it("should render actions when provided", () => {
    render(
      <SectionHeader
        title="Section"
        actions={<button type="button">Action</button>}
      />,
      { wrapper }
    );
    expect(screen.getByRole("button", { name: "Action" })).toBeInTheDocument();
  });
});
