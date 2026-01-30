import React from "react";
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ThemeProvider, createTheme } from "@mui/material/styles";
import { ManagementLayout } from "../ManagementLayout";

const theme = createTheme();
const wrapper = ({ children }: { children: React.ReactNode }) => (
  <ThemeProvider theme={theme}>{children}</ThemeProvider>
);

describe("ManagementLayout", () => {
  it("should render children", () => {
    render(
      <ManagementLayout error={null}>
        <span>Content</span>
      </ManagementLayout>,
      { wrapper }
    );
    expect(screen.getByText("Content")).toBeInTheDocument();
  });

  it("should render Header and Content slots", () => {
    render(
      <ManagementLayout error={null}>
        <ManagementLayout.Header>
          <span>Header content</span>
        </ManagementLayout.Header>
        <ManagementLayout.Content>
          <span>Main content</span>
        </ManagementLayout.Content>
      </ManagementLayout>,
      { wrapper }
    );
    expect(screen.getByText("Header content")).toBeInTheDocument();
    expect(screen.getByText("Main content")).toBeInTheDocument();
  });

  it("should render error when error is provided", () => {
    render(
      <ManagementLayout error={new Error("Failed to load")}>
        <span>Content</span>
      </ManagementLayout>,
      { wrapper }
    );
    expect(screen.getByText("Failed to load")).toBeInTheDocument();
  });
});
