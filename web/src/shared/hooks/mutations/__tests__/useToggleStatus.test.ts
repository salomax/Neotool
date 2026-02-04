import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { useToggleStatus } from "../useToggleStatus";

const mockToast = {
  success: vi.fn(),
  error: vi.fn(),
};

vi.mock("@/shared/providers", () => ({
  useToast: () => mockToast,
}));

describe.sequential("useToggleStatus", () => {
  beforeEach(() => {
    mockToast.success.mockClear();
    mockToast.error.mockClear();
  });

  it("should call enableFn and show success when enabled is true", async () => {
    const enableFn = vi.fn().mockResolvedValue(undefined);
    const disableFn = vi.fn().mockResolvedValue(undefined);
    const t = vi.fn((key: string) => key);

    const { result } = renderHook(() =>
      useToggleStatus({
        enableFn,
        disableFn,
        enableSuccessMessage: "enabled",
        disableSuccessMessage: "disabled",
        enableErrorMessage: "enableError",
        disableErrorMessage: "disableError",
        t,
      })
    );

    await result.current("id-1", true);

    expect(enableFn).toHaveBeenCalledWith("id-1");
    expect(disableFn).not.toHaveBeenCalled();
    expect(mockToast.success).toHaveBeenCalledWith("enabled");
    expect(mockToast.error).not.toHaveBeenCalled();
  });

  it("should call disableFn and show success when enabled is false", async () => {
    const enableFn = vi.fn().mockResolvedValue(undefined);
    const disableFn = vi.fn().mockResolvedValue(undefined);
    const t = vi.fn((key: string) => key);

    const { result } = renderHook(() =>
      useToggleStatus({
        enableFn,
        disableFn,
        enableSuccessMessage: "enabled",
        disableSuccessMessage: "disabled",
        enableErrorMessage: "enableError",
        disableErrorMessage: "disableError",
        t,
      })
    );

    await result.current("id-2", false);

    expect(disableFn).toHaveBeenCalledTimes(1);
    expect(disableFn).toHaveBeenCalledWith("id-2");
    expect(enableFn).not.toHaveBeenCalled();
    expect(mockToast.success).toHaveBeenCalledWith("disabled");
    expect(mockToast.error).not.toHaveBeenCalled();
  });

  it("should show error toast when enableFn throws", async () => {
    const enableFn = vi.fn().mockRejectedValue(new Error("Enable failed"));
    const disableFn = vi.fn().mockResolvedValue(undefined);
    const t = vi.fn((key: string) => key);

    const { result } = renderHook(() =>
      useToggleStatus({
        enableFn,
        disableFn,
        enableSuccessMessage: "enabled",
        disableSuccessMessage: "disabled",
        enableErrorMessage: "enableError",
        disableErrorMessage: "disableError",
        t,
      })
    );

    await result.current("id-1", true);

    expect(mockToast.error).toHaveBeenCalled();
    expect(mockToast.success).not.toHaveBeenCalled();
  });

  it("should show error toast when disableFn throws", async () => {
    const enableFn = vi.fn().mockResolvedValue(undefined);
    const disableFn = vi.fn().mockRejectedValue(new Error("Disable failed"));
    const t = vi.fn((key: string) => key);

    const { result } = renderHook(() =>
      useToggleStatus({
        enableFn,
        disableFn,
        enableSuccessMessage: "enabled",
        disableSuccessMessage: "disabled",
        enableErrorMessage: "enableError",
        disableErrorMessage: "disableError",
        t,
      })
    );

    await result.current("id-1", false);

    expect(mockToast.error).toHaveBeenCalled();
    expect(mockToast.success).not.toHaveBeenCalled();
  });
});
