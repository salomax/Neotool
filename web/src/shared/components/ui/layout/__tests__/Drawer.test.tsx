import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { Drawer } from "../Drawer";
import { AppThemeProvider } from "@/styles/themes/AppThemeProvider";

const mockUseMediaQuery = vi.fn((_query?: unknown) => false);

vi.mock("@mui/material", async () => {
  const actual = await vi.importActual<typeof import("@mui/material")>("@mui/material");
  return {
    ...actual,
    useMediaQuery: (query: unknown) => mockUseMediaQuery(query),
  };
});

function renderDrawer(
  ui: React.ReactElement,
  options?: { mobile?: boolean },
) {
  mockUseMediaQuery.mockReturnValue(options?.mobile ?? false);
  return render(<AppThemeProvider>{ui}</AppThemeProvider>);
}

describe.sequential("Drawer mobile presentation", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseMediaQuery.mockReturnValue(false);
  });

  it("keeps default desktop layout when mobilePresentation is not set", () => {
    renderDrawer(
      <Drawer open onClose={vi.fn()} anchor="right" size="md">
        <Drawer.Header title="Settings" />
        <Drawer.Body>Body content</Drawer.Body>
        <Drawer.Footer>
          <Drawer.ActionButtons
            cancelText="Cancel"
            submitText="Save"
            onCancel={vi.fn()}
            onSubmit={vi.fn()}
          />
        </Drawer.Footer>
      </Drawer>,
    );

    expect(screen.getByText("SETTINGS")).toBeInTheDocument();
    expect(screen.getByText("Body content")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cancel" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save" })).toBeInTheDocument();
  });

  it("uses mobile-sheet layout mode on narrow viewports when mobilePresentation is enabled", () => {
    renderDrawer(
      <Drawer open onClose={vi.fn()} anchor="right" size="md" mobilePresentation="bottom-sheet-full">
        <Drawer.Header title="Import" />
        <Drawer.Body>Mobile body</Drawer.Body>
      </Drawer>,
      { mobile: true },
    );

    expect(screen.getByTestId("drawer-layout-root")).toHaveAttribute("data-layout-mode", "mobile-sheet");
    expect(screen.getByText("IMPORT")).toBeInTheDocument();
    expect(screen.getByText("Mobile body")).toBeInTheDocument();
  });

  it("keeps desktop anchor on wide viewports even with mobilePresentation enabled", () => {
    renderDrawer(
      <Drawer open onClose={vi.fn()} anchor="right" size="md" mobilePresentation="bottom-sheet-full">
        <Drawer.Header title="Import" />
        <Drawer.Body>Desktop body</Drawer.Body>
      </Drawer>,
      { mobile: false },
    );

    expect(screen.getByTestId("drawer-layout-root")).toHaveAttribute("data-layout-mode", "default");
  });

  it("stacks action buttons vertically in mobile-sheet layout mode", () => {
    renderDrawer(
      <Drawer open onClose={vi.fn()} anchor="right" size="md" mobilePresentation="bottom-sheet-full">
        <Drawer.Footer>
          <Drawer.ActionButtons
            cancelText="Cancel"
            submitText="Import"
            onCancel={vi.fn()}
            onSubmit={vi.fn()}
            data-testid="drawer-actions"
          />
        </Drawer.Footer>
      </Drawer>,
      { mobile: true },
    );

    const actions = screen.getByTestId("drawer-actions");
    expect(actions).toHaveStyle({ flexDirection: "column" });
  });
});
