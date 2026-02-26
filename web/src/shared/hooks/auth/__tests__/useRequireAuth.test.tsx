import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { usePathname, useSearchParams } from "next/navigation";
import { useRequireAuth } from "../useRequireAuth";

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
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it("returns allowed false and redirects to signin with next on protected route when not authenticated", () => {
    vi.mocked(usePathname).mockReturnValue("/settings");

    const { result } = renderHook(() => useRequireAuth());

    expect(result.current.allowed).toBe(false);
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
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it("returns allowed true on protected route while loading", () => {
    vi.mocked(usePathname).mockReturnValue("/settings");
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: true,
    });

    const { result } = renderHook(() => useRequireAuth());

    expect(result.current.allowed).toBe(true);
    expect(mockReplace).not.toHaveBeenCalled();
  });
});
