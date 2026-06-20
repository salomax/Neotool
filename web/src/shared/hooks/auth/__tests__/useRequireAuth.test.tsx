import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, renderHook, screen } from "@testing-library/react";
import { usePathname, useSearchParams } from "next/navigation";
import { RequireAuthGuard, useRequireAuth } from "../useRequireAuth";

type SearchParamsReturn = ReturnType<typeof useSearchParams>;
const makeSearchParams = (value = "") =>
  new URLSearchParams(value) as unknown as SearchParamsReturn;

const mockReplace = vi.fn();
vi.mock("next/navigation", () => ({
  usePathname: vi.fn(() => "/"),
  useSearchParams: vi.fn(() => makeSearchParams()),
  useRouter: () => ({ replace: mockReplace }),
}));

const mockUseAuth = vi.fn();
vi.mock("@/shared/providers", () => ({
  useAuth: () => mockUseAuth(),
}));

describe.sequential("useRequireAuth", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(usePathname).mockReturnValue("/");
    vi.mocked(useSearchParams).mockReturnValue(makeSearchParams());
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: false,
    });
  });

  it("returns allowed true on public route when not authenticated", () => {
    vi.mocked(usePathname).mockReturnValue("/signin");

    const { result } = renderHook(() => useRequireAuth());

    expect(result.current.allowed).toBe(true);
    expect(result.current.isPending).toBe(false);
    expect(result.current.isRedirecting).toBe(false);
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it("returns allowed false and redirects to signin with next on protected route when not authenticated", () => {
    vi.mocked(usePathname).mockReturnValue("/settings");

    const { result } = renderHook(() => useRequireAuth());

    expect(result.current.allowed).toBe(false);
    expect(result.current.isPending).toBe(false);
    expect(result.current.isRedirecting).toBe(true);
    expect(mockReplace).toHaveBeenCalledWith(
      "/signin?next=" + encodeURIComponent("/settings")
    );
  });

  it("includes search string in next when present", () => {
    vi.mocked(usePathname).mockReturnValue("/settings");
    vi.mocked(useSearchParams).mockReturnValue(makeSearchParams("tab=roles"));

    renderHook(() => useRequireAuth());

    expect(mockReplace).toHaveBeenCalledWith(
      "/signin?next=" + encodeURIComponent("/settings?tab=roles")
    );
  });

  it("returns allowed true on protected route when authenticated", () => {
    vi.mocked(usePathname).mockReturnValue("/settings");
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
    });

    const { result } = renderHook(() => useRequireAuth());

    expect(result.current.allowed).toBe(true);
    expect(result.current.isPending).toBe(false);
    expect(result.current.isRedirecting).toBe(false);
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it("returns pending state on protected route while loading", () => {
    vi.mocked(usePathname).mockReturnValue("/settings");
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: true,
    });

    const { result } = renderHook(() => useRequireAuth());

    expect(result.current.allowed).toBe(false);
    expect(result.current.isPending).toBe(true);
    expect(result.current.isRedirecting).toBe(false);
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it("renders loading state while auth is pending on a protected route", () => {
    vi.mocked(usePathname).mockReturnValue("/settings");
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: true,
    });

    render(
      <RequireAuthGuard>
        <div>protected content</div>
      </RequireAuthGuard>
    );

    expect(screen.getByTestId("auth-guard-loading")).toBeInTheDocument();
    expect(screen.queryByText("protected content")).not.toBeInTheDocument();
  });

  it("renders loading state while redirecting from a protected route", () => {
    vi.mocked(usePathname).mockReturnValue("/settings");

    render(
      <RequireAuthGuard>
        <div>protected content</div>
      </RequireAuthGuard>
    );

    expect(screen.getByTestId("auth-guard-loading")).toBeInTheDocument();
    expect(screen.queryByText("protected content")).not.toBeInTheDocument();
  });

  it("renders children when authenticated on a protected route", () => {
    vi.mocked(usePathname).mockReturnValue("/settings");
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
    });

    render(
      <RequireAuthGuard>
        <div>protected content</div>
      </RequireAuthGuard>
    );

    expect(screen.getByText("protected content")).toBeInTheDocument();
    expect(screen.queryByTestId("auth-guard-loading")).not.toBeInTheDocument();
  });
});
