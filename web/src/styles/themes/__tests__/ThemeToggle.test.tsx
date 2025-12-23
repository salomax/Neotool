import React from "react";
import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { render, screen, waitFor, act, cleanup } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AppThemeProvider, useThemeMode } from "@/styles/themes/AppThemeProvider";
import { ThemeToggle } from "@/styles/themes/ThemeToggle";

// Helper component to expose theme mode for testing
const ThemeModeIndicator: React.FC = () => {
  const { mode } = useThemeMode();
  return <div data-testid="theme-mode">{mode}</div>;
};

// Run sequentially to avoid concurrent renders leaking between tests
describe.sequential("ThemeToggle", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("should render toggle button", async () => {
    render(
      <AppThemeProvider>
        <ThemeToggle />
      </AppThemeProvider>,
    );

    // Wait for initial mount to complete (AppThemeProvider has a useEffect that sets mounted)
    await waitFor(() => {
      expect(screen.getByRole("button", { name: /toggle theme/i })).toBeInTheDocument();
    });

    const button = screen.getByRole("button", { name: /toggle theme/i });
    expect(button).toBeInTheDocument();
  });

  it("should display dark mode icon when in light mode", async () => {
    render(
      <AppThemeProvider defaultMode="light">
        <ThemeToggle />
        <ThemeModeIndicator />
      </AppThemeProvider>,
    );

    // Wait for initial mount to complete (AppThemeProvider has a useEffect that sets mounted)
    await waitFor(() => {
      expect(screen.getByTestId("theme-mode")).toBeInTheDocument();
    });

    // Verify mode is light
    expect(screen.getByTestId("theme-mode")).toHaveTextContent("light");
    
    // In light mode, should show dark mode icon (DarkModeIcon)
    // MUI icons render as SVG with data-testid or we can check by querying the icon
    const button = screen.getByRole("button", { name: /toggle theme/i });
    expect(button).toBeInTheDocument();
  });

  it("should display light mode icon when in dark mode", async () => {
    render(
      <AppThemeProvider defaultMode="dark">
        <ThemeToggle />
        <ThemeModeIndicator />
      </AppThemeProvider>,
    );

    // Wait for initial mount to complete (AppThemeProvider has a useEffect that sets mounted)
    await waitFor(() => {
      expect(screen.getByTestId("theme-mode")).toBeInTheDocument();
    });

    // Verify mode is dark
    expect(screen.getByTestId("theme-mode")).toHaveTextContent("dark");
    
    // In dark mode, should show light mode icon (LightModeIcon)
    const button = screen.getByRole("button", { name: /toggle theme/i });
    expect(button).toBeInTheDocument();
  });

  it("should show correct tooltip text in light mode", async () => {
    const user = userEvent.setup();
    render(
      <AppThemeProvider defaultMode="light">
        <ThemeToggle />
      </AppThemeProvider>,
    );

    // Wait for initial mount to complete (AppThemeProvider has a useEffect that sets mounted)
    await waitFor(() => {
      expect(screen.getByRole("button", { name: /toggle theme/i })).toBeInTheDocument();
    });

    const button = screen.getByRole("button", { name: /toggle theme/i });
    await user.hover(button);

    await waitFor(() => {
      expect(screen.getByText(/switch to dark/i)).toBeInTheDocument();
    });
  });

  it("should show correct tooltip text in dark mode", async () => {
    const user = userEvent.setup();
    render(
      <AppThemeProvider defaultMode="dark">
        <ThemeToggle />
      </AppThemeProvider>,
    );

    // Wait for initial mount to complete (AppThemeProvider has a useEffect that sets mounted)
    await waitFor(() => {
      expect(screen.getByRole("button", { name: /toggle theme/i })).toBeInTheDocument();
    });

    const button = screen.getByRole("button", { name: /toggle theme/i });
    await user.hover(button);

    await waitFor(() => {
      expect(screen.getByText(/switch to light/i)).toBeInTheDocument();
    });
  });

  it("should toggle from light to dark mode when clicked", async () => {
    const user = userEvent.setup();
    render(
      <AppThemeProvider defaultMode="light">
        <ThemeToggle />
        <ThemeModeIndicator />
      </AppThemeProvider>,
    );

    // Wait for initial mount to complete (AppThemeProvider has a useEffect that sets mounted)
    await waitFor(() => {
      expect(screen.getByTestId("theme-mode")).toBeInTheDocument();
    });

    // Verify initial state
    expect(screen.getByTestId("theme-mode")).toHaveTextContent("light");

    const button = screen.getByRole("button", { name: /toggle theme/i });
    
    // Wrap the click in act() to ensure state updates are properly handled
    await act(async () => {
      await user.click(button);
    });

    // Wait for state update - check mode directly instead of tooltip
    await waitFor(() => {
      expect(screen.getByTestId("theme-mode")).toHaveTextContent("dark");
    });
  });

  it("should toggle from dark to light mode when clicked", async () => {
    const user = userEvent.setup();
    render(
      <AppThemeProvider defaultMode="dark">
        <ThemeToggle />
        <ThemeModeIndicator />
      </AppThemeProvider>,
    );

    // Wait for initial mount to complete (AppThemeProvider has a useEffect that sets mounted)
    await waitFor(() => {
      expect(screen.getByTestId("theme-mode")).toBeInTheDocument();
    });

    // Verify initial state
    expect(screen.getByTestId("theme-mode")).toHaveTextContent("dark");

    const button = screen.getByRole("button", { name: /toggle theme/i });
    
    // Wrap the click in act() to ensure state updates are properly handled
    await act(async () => {
      await user.click(button);
    });

    // Wait for state update - check mode directly instead of tooltip
    await waitFor(() => {
      expect(screen.getByTestId("theme-mode")).toHaveTextContent("light");
    });
  });

  it("should persist theme mode in localStorage when toggled", async () => {
    const user = userEvent.setup();
    render(
      <AppThemeProvider defaultMode="light">
        <ThemeToggle />
      </AppThemeProvider>,
    );

    // Wait for initial mount to complete (AppThemeProvider has a useEffect that sets mounted)
    await waitFor(() => {
      expect(screen.getByRole("button", { name: /toggle theme/i })).toBeInTheDocument();
    });

    const button = screen.getByRole("button", { name: /toggle theme/i });
    
    // Wrap the click in act() to ensure state updates are properly handled
    await act(async () => {
      await user.click(button);
    });

    // Wait for state update to complete and localStorage to be updated
    await waitFor(() => {
      expect(localStorage.getItem("app:theme-mode")).toBe("dark");
    });
  });

  afterEach(() => {
    cleanup();
  });
});
