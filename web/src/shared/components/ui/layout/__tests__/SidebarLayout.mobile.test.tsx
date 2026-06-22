import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";

const mockIsMobile = vi.fn();

vi.mock("@mui/material/useMediaQuery", () => ({
  default: () => mockIsMobile(),
}));

vi.mock("../Container", () => ({
  Container: ({ children, sx, "data-testid": dataTestId }: any) => (
    <div data-testid={dataTestId || "container"} style={sx}>
      {children}
    </div>
  ),
}));

vi.mock("../Box", () => {
  const Box = React.forwardRef(
    ({ children, sx, style, "data-testid": dataTestId, ...props }: any, ref: any) => (
      <div ref={ref} data-testid={dataTestId || "box"} style={{ ...sx, ...style }} {...props}>
        {children}
      </div>
    ),
  );
  Box.displayName = "Box";
  return { Box };
});

vi.mock("../MobileSidebarSheet", () => ({
  MobileSidebarSheet: ({
    open,
    onClose,
    children,
  }: {
    open: boolean;
    onClose: () => void;
    children: React.ReactNode;
  }) =>
    open ? (
      <div data-testid="mobile-sidebar-sheet">
        <button type="button" onClick={onClose}>
          close
        </button>
        {children}
      </div>
    ) : null,
}));

import { SidebarLayout } from "../SidebarLayout";

describe.sequential("SidebarLayout mobile behavior", () => {
  const mockSidebar = <div data-testid="sidebar-content">Sidebar Content</div>;
  const mockChildren = <div data-testid="main-content">Main Content</div>;
  const mobileTrigger = <div data-testid="mobile-trigger-content">Trigger</div>;

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("does not render the fixed sidebar on mobile when mobileVariant='bottom-sheet'", () => {
    mockIsMobile.mockReturnValue(true);

    render(
      <SidebarLayout sidebar={mockSidebar} mobileVariant="bottom-sheet" mobileTrigger={mobileTrigger}>
        {mockChildren}
      </SidebarLayout>,
    );

    expect(screen.queryByTestId("sidebarlayout-sidebar")).not.toBeInTheDocument();
  });

  it("renders the mobile trigger above content on mobile", () => {
    mockIsMobile.mockReturnValue(true);

    render(
      <SidebarLayout sidebar={mockSidebar} mobileVariant="bottom-sheet" mobileTrigger={mobileTrigger}>
        {mockChildren}
      </SidebarLayout>,
    );

    expect(screen.getByTestId("sidebarlayout-mobile-trigger")).toBeInTheDocument();
    expect(screen.getByTestId("mobile-trigger-content")).toBeInTheDocument();
  });

  it("opens the mobile sheet with sidebar content when the trigger is clicked", () => {
    mockIsMobile.mockReturnValue(true);

    render(
      <SidebarLayout sidebar={mockSidebar} mobileVariant="bottom-sheet" mobileTrigger={mobileTrigger}>
        {mockChildren}
      </SidebarLayout>,
    );

    fireEvent.click(screen.getByTestId("sidebarlayout-mobile-trigger"));

    expect(screen.getByTestId("mobile-sidebar-sheet")).toBeInTheDocument();
    expect(screen.getByTestId("sidebar-content")).toBeInTheDocument();
  });

  it("preserves desktop behavior", () => {
    mockIsMobile.mockReturnValue(false);

    render(
      <SidebarLayout sidebar={mockSidebar} mobileVariant="bottom-sheet" mobileTrigger={mobileTrigger}>
        {mockChildren}
      </SidebarLayout>,
    );

    expect(screen.getByTestId("sidebarlayout-sidebar")).toBeInTheDocument();
    expect(screen.queryByTestId("sidebarlayout-mobile-trigger")).not.toBeInTheDocument();
    expect(screen.queryByTestId("mobile-sidebar-sheet")).not.toBeInTheDocument();
  });

  it("hides the sidebar on mobile with mobileVariant='hidden' and no trigger", () => {
    mockIsMobile.mockReturnValue(true);

    render(
      <SidebarLayout sidebar={mockSidebar} mobileVariant="hidden">
        {mockChildren}
      </SidebarLayout>,
    );

    expect(screen.queryByTestId("sidebarlayout-sidebar")).not.toBeInTheDocument();
    expect(screen.queryByTestId("sidebarlayout-mobile-trigger")).not.toBeInTheDocument();
    expect(screen.queryByTestId("mobile-sidebar-sheet")).not.toBeInTheDocument();
  });
});
