import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";
import { DisableIfNoPermission } from "../DisableIfNoPermission";

const mockHas = vi.fn();
const mockHasAny = vi.fn();
const mockHasAll = vi.fn();
let mockLoading = false;

vi.mock("@/shared/providers/AuthorizationProvider", () => ({
  AuthorizationProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useAuthorization: () => ({
    has: mockHas,
    hasAny: mockHasAny,
    hasAll: mockHasAll,
    loading: mockLoading,
  }),
}));

describe.sequential("DisableIfNoPermission", () => {
  beforeEach(() => {
    mockHas.mockClear();
    mockHasAny.mockClear();
    mockHasAll.mockClear();
    mockLoading = false;
  });

  afterEach(() => {
    cleanup();
  });

  it("should disable child when loading", () => {
    mockLoading = true;
    render(
      <DisableIfNoPermission permission="test:permission">
        <button type="button">Click</button>
      </DisableIfNoPermission>
    );
    const button = screen.getByRole("button", { name: "Click" });
    expect(button).toBeDisabled();
  });

  it("should disable child when user lacks permission (single)", () => {
    mockHasAll.mockReturnValue(false);
    render(
      <DisableIfNoPermission permission="test:permission">
        <button type="button">Click</button>
      </DisableIfNoPermission>
    );
    const button = screen.getByRole("button", { name: "Click" });
    expect(button).toBeDisabled();
    expect(mockHasAll).toHaveBeenCalledWith(["test:permission"]);
  });

  it("should enable child when user has permission (single)", () => {
    mockHasAll.mockReturnValue(true);
    render(
      <DisableIfNoPermission permission="test:permission">
        <button type="button">Click</button>
      </DisableIfNoPermission>
    );
    const button = screen.getByRole("button", { name: "Click" });
    expect(button).not.toBeDisabled();
  });

  it("should use hasAll for array permission when anyOf is false", () => {
    mockHasAll.mockReturnValue(true);
    mockHasAny.mockReturnValue(false);
    render(
      <DisableIfNoPermission permission={["perm1", "perm2"]}>
        <button type="button">Array Action</button>
      </DisableIfNoPermission>
    );
    expect(mockHasAll).toHaveBeenCalledWith(["perm1", "perm2"]);
    expect(screen.getByRole("button", { name: "Array Action" })).not.toBeDisabled();
  });

  it("should use hasAny when anyOf is true", () => {
    mockHasAny.mockReturnValue(true);
    mockHasAll.mockReturnValue(false);
    render(
      <DisableIfNoPermission permission={["perm1", "perm2"]} anyOf>
        <button type="button">AnyOf Action</button>
      </DisableIfNoPermission>
    );
    expect(mockHasAny).toHaveBeenCalledWith(["perm1", "perm2"]);
    expect(screen.getByRole("button", { name: "AnyOf Action" })).not.toBeDisabled();
  });

  it("should keep child disabled when child already has disabled prop", () => {
    mockHasAll.mockReturnValue(true);
    render(
      <DisableIfNoPermission permission="test:permission">
        <button type="button" disabled>
          Click
        </button>
      </DisableIfNoPermission>
    );
    const button = screen.getByRole("button", { name: "Click" });
    expect(button).toBeDisabled();
  });
});
