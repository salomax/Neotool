import React from "react";
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ThemeProvider, createTheme } from "@mui/material/styles";
import { Container } from "../Container";

const theme = createTheme();
const wrapper = ({ children }: { children: React.ReactNode }) => (
  <ThemeProvider theme={theme}>{children}</ThemeProvider>
);

describe("Container", () => {
  it("should render children", () => {
    render(
      <Container data-testid="container">
        <span>Content</span>
      </Container>,
      { wrapper }
    );
    expect(screen.getByTestId("container")).toBeInTheDocument();
    expect(screen.getByText("Content")).toBeInTheDocument();
  });

  it("should apply name for data-testid", () => {
    render(
      <Container name="main">
        <span>Content</span>
      </Container>,
      { wrapper }
    );
    expect(screen.getByTestId("container-main")).toBeInTheDocument();
  });

  it("should accept maxWidth prop", () => {
    render(
      <Container maxWidth="lg" data-testid="c1">
        Content
      </Container>,
      { wrapper }
    );
    expect(screen.getByTestId("c1")).toHaveClass("MuiContainer-maxWidthLg");
  });
});
