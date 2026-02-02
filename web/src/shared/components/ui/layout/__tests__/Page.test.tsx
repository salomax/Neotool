import React from "react";
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ThemeProvider, createTheme } from "@mui/material/styles";
import { Page } from "../Page";

const theme = createTheme();
const wrapper = ({ children }: { children: React.ReactNode }) => (
  <ThemeProvider theme={theme}>{children}</ThemeProvider>
);

describe("Page", () => {
  it("should render children", () => {
    render(
      <Page>
        <span>Page content</span>
      </Page>,
      { wrapper }
    );
    expect(screen.getByText("Page content")).toBeInTheDocument();
  });

  it("should forward ref", () => {
    const ref = React.createRef<HTMLDivElement>();
    render(
      <Page ref={ref}>
        <span>Content</span>
      </Page>,
      { wrapper }
    );
    expect(ref.current).toBeInstanceOf(HTMLDivElement);
  });
});
