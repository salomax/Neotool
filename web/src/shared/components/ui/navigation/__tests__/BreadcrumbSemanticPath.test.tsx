
import React from "react";
import { render, screen, cleanup } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import Breadcrumb from "../Breadcrumb";
import { BreadcrumbLabelProvider, useBreadcrumbLabel } from "@/shared/hooks/ui/useBreadcrumbLabel";
import { ThemeProvider, createTheme } from "@mui/material";

// Mock usePathname
const mockPathname = vi.fn();
vi.mock("next/navigation", () => ({
  usePathname: () => mockPathname(),
  useSearchParams: () => new URLSearchParams(),
}));

// Setup wrapper
const theme = createTheme();
const AppWrapper = ({ children }: { children: React.ReactNode }) => (
  <ThemeProvider theme={theme}>
    <BreadcrumbLabelProvider>
      {children}
    </BreadcrumbLabelProvider>
  </ThemeProvider>
);

// Test component that uses the hook
const SemanticBreadcrumbPage = ({ segment, label }: { segment: string, label: string }) => {
  useBreadcrumbLabel(segment, label);
  return <Breadcrumb autoGenerate />;
};

describe("Breadcrumb Semantic Path Integration", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });
  
  afterEach(() => {
    cleanup();
  });

  it("should replace the exact segment with the label", () => {
    mockPathname.mockReturnValue("/institutions/bradesco.123");

    render(
      <AppWrapper>
        <SemanticBreadcrumbPage segment="bradesco.123" label="Correct Label" />
      </AppWrapper>
    );

    expect(screen.getByText("Correct Label")).toBeInTheDocument();
    expect(screen.queryByText("bradesco.123")).not.toBeInTheDocument();
  });

  it("should NOT replace the segment if the key matches only part of it (the bug reproduction)", () => {
    mockPathname.mockReturnValue("/institutions/bradesco.123");

    render(
      <AppWrapper>
        <SemanticBreadcrumbPage segment="123" label="Wrong Label" />
      </AppWrapper>
    );

    // If this fails, it means "Wrong Label" WAS found, which implies partial matching or ID extraction logic exists
    expect(screen.queryByText("Wrong Label")).not.toBeInTheDocument();
    
    // It should show the default formatted segment
    // Based on default logic: "bradesco.123" -> "Bradesco.123"
    // Use regex to be safe about casing
    expect(screen.getByText(/Bradesco\.123/i)).toBeInTheDocument();
  });
});
