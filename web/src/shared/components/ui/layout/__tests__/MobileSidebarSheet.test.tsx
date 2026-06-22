import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { fireEvent, render, screen } from "@testing-library/react";
import { MobileSidebarSheet } from "../MobileSidebarSheet";

vi.mock("../Drawer", () => {
  function Drawer({
    open,
    onClose,
    children,
  }: {
    open: boolean;
    onClose: () => void;
    children: React.ReactNode;
  }) {
    if (!open) return null;

    return (
      <div data-testid="drawer-root">
        <button type="button" onClick={onClose} data-testid="drawer-backdrop">
          backdrop
        </button>
        {children}
      </div>
    );
  }

  function DrawerBody({
    children,
    sx,
  }: {
    children: React.ReactNode;
    sx?: Record<string, unknown>;
  }) {
    return (
      <div data-testid="drawer-body" data-sx={JSON.stringify(sx ?? {})}>
        {children}
      </div>
    );
  }

  Drawer.Body = DrawerBody;

  return { Drawer };
});

describe.sequential("MobileSidebarSheet", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders children when open=true", () => {
    render(
      <MobileSidebarSheet open onClose={vi.fn()}>
        <div>Sidebar content</div>
      </MobileSidebarSheet>,
    );

    expect(screen.getByText("Sidebar content")).toBeInTheDocument();
  });

  it("does not render when open=false", () => {
    render(
      <MobileSidebarSheet open={false} onClose={vi.fn()}>
        <div>Sidebar content</div>
      </MobileSidebarSheet>,
    );

    expect(screen.queryByText("Sidebar content")).not.toBeInTheDocument();
  });

  it("calls onClose when the backdrop is clicked", () => {
    const onClose = vi.fn();

    render(
      <MobileSidebarSheet open onClose={onClose}>
        <div>Sidebar content</div>
      </MobileSidebarSheet>,
    );

    fireEvent.click(screen.getByTestId("drawer-backdrop"));

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("renders the drag handle", () => {
    render(
      <MobileSidebarSheet open onClose={vi.fn()}>
        <div>Sidebar content</div>
      </MobileSidebarSheet>,
    );

    expect(screen.getByTestId("mobile-sidebar-sheet-handle")).toBeInTheDocument();
  });
});
