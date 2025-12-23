import React from "react";
import { describe, it, expect, afterEach } from "vitest";
import { render, screen, fireEvent, cleanup } from "@testing-library/react";
import { AppThemeProvider } from "@/styles/themes/AppThemeProvider";
import { ThemeControls } from "@/styles/themes/ThemeControls";

// Run sequentially to avoid concurrent renders leaking between tests
describe.sequential("ThemeControls", () => {
  it("should render light and dark mode toggle buttons", () => {
    render(
      <AppThemeProvider>
        <ThemeControls />
      </AppThemeProvider>,
    );

    const lightButton = screen.getByRole("button", { name: /light/i });
    const darkButton = screen.getByRole("button", { name: /dark/i });

    expect(lightButton).toBeInTheDocument();
    expect(darkButton).toBeInTheDocument();
  });

  it("should display light mode as selected by default", () => {
    render(
      <AppThemeProvider defaultMode="light">
        <ThemeControls />
      </AppThemeProvider>,
    );

    const lightButton = screen.getByRole("button", { name: /light/i });
    expect(lightButton).toHaveAttribute("aria-pressed", "true");
  });

  it("should display dark mode as selected when defaultMode is dark", () => {
    render(
      <AppThemeProvider defaultMode="dark">
        <ThemeControls />
      </AppThemeProvider>,
    );

    const darkButton = screen.getByRole("button", { name: /dark/i });
    expect(darkButton).toHaveAttribute("aria-pressed", "true");
  });

  it("should change to dark mode when dark button is clicked", () => {
    render(
      <AppThemeProvider defaultMode="light">
        <ThemeControls />
      </AppThemeProvider>,
    );

    const darkButton = screen.getByRole("button", { name: /dark/i });
    fireEvent.click(darkButton);

    expect(darkButton).toHaveAttribute("aria-pressed", "true");
    const lightButton = screen.getByRole("button", { name: /light/i });
    expect(lightButton).toHaveAttribute("aria-pressed", "false");
  });

  it("should change to light mode when light button is clicked", () => {
    render(
      <AppThemeProvider defaultMode="dark">
        <ThemeControls />
      </AppThemeProvider>,
    );

    const lightButton = screen.getByRole("button", { name: /light/i });
    fireEvent.click(lightButton);

    expect(lightButton).toHaveAttribute("aria-pressed", "true");
    const darkButton = screen.getByRole("button", { name: /dark/i });
    expect(darkButton).toHaveAttribute("aria-pressed", "false");
  });

  it("should persist theme mode in localStorage", () => {
    const { rerender } = render(
      <AppThemeProvider defaultMode="light">
        <ThemeControls />
      </AppThemeProvider>,
    );

    const darkButton = screen.getByRole("button", { name: /dark/i });
    fireEvent.click(darkButton);

    expect(localStorage.getItem("app:theme-mode")).toBe("dark");

    // Rerender to verify persistence
    rerender(
      <AppThemeProvider defaultMode="light">
        <ThemeControls />
      </AppThemeProvider>,
    );

    // After rerender, dark mode should be restored from localStorage
    const darkButtonAfterRerender = screen.getByRole("button", { name: /dark/i });
    expect(darkButtonAfterRerender).toHaveAttribute("aria-pressed", "true");
  });

  afterEach(() => {
    cleanup();
  });
});

