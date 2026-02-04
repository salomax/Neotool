import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { useFlag, useFlags } from "@unleash/proxy-client-react";
import { useFeatureFlagsContext } from "@/shared/providers/FeatureFlagsProvider";
import {
  useFeatureFlag,
  useFeatureFlagEnabled,
  useFeatureFlags,
  useFeatureFlagWithContext,
} from "../useFeatureFlag";

vi.mock("@unleash/proxy-client-react", () => ({
  useFlag: vi.fn(),
  useFlags: vi.fn(),
}));

vi.mock("@/shared/providers/FeatureFlagsProvider", () => ({
  useFeatureFlagsContext: vi.fn(),
}));

describe("useFeatureFlag", () => {
  beforeEach(() => {
    vi.mocked(useFeatureFlagsContext).mockReturnValue({
      isReady: true,
      flagsError: null,
    });
    vi.mocked(useFlag).mockReturnValue(true);
  });

  it("should return enabled true when isReady and flag is on", () => {
    vi.mocked(useFlag).mockReturnValue(true);
    const { result } = renderHook(() => useFeatureFlag("my-flag"));
    expect(result.current.enabled).toBe(true);
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("should return enabled false when isReady and flag is off", () => {
    vi.mocked(useFlag).mockReturnValue(false);
    const { result } = renderHook(() => useFeatureFlag("my-flag"));
    expect(result.current.enabled).toBe(false);
  });

  it("should return enabled false and loading true when not ready", () => {
    vi.mocked(useFeatureFlagsContext).mockReturnValue({
      isReady: false,
      flagsError: null,
    });
    const { result } = renderHook(() => useFeatureFlag("my-flag"));
    expect(result.current.enabled).toBe(false);
    expect(result.current.loading).toBe(true);
  });

  it("should return error from context", () => {
    vi.mocked(useFeatureFlagsContext).mockReturnValue({
      isReady: true,
      flagsError: new Error("Flags failed"),
    });
    const { result } = renderHook(() => useFeatureFlag("my-flag"));
    expect(result.current.error).toEqual(new Error("Flags failed"));
  });
});

describe("useFeatureFlagEnabled", () => {
  beforeEach(() => {
    vi.mocked(useFeatureFlagsContext).mockReturnValue({
      isReady: true,
      flagsError: null,
    });
  });

  it("should return boolean from useFeatureFlag enabled", () => {
    vi.mocked(useFlag).mockReturnValue(true);
    const { result } = renderHook(() => useFeatureFlagEnabled("my-flag"));
    expect(result.current).toBe(true);
  });

  it("should return false when flag is off", () => {
    vi.mocked(useFlag).mockReturnValue(false);
    const { result } = renderHook(() => useFeatureFlagEnabled("my-flag"));
    expect(result.current).toBe(false);
  });
});

describe("useFeatureFlags", () => {
  beforeEach(() => {
    vi.mocked(useFeatureFlagsContext).mockReturnValue({
      isReady: true,
      flagsError: null,
    });
  });

  it("should return empty object when not ready", () => {
    vi.mocked(useFeatureFlagsContext).mockReturnValue({
      isReady: false,
      flagsError: null,
    });
    const { result } = renderHook(() => useFeatureFlags());
    expect(result.current).toEqual({});
  });

  it("should convert IToggle[] to Record when flags is array", () => {
    vi.mocked(useFeatureFlagsContext).mockReturnValue({
      isReady: true,
      flagsError: null,
    });
    vi.mocked(useFlags).mockReturnValue([
      { name: "flag1", enabled: true },
      { name: "flag2", enabled: false },
    ] as any);
    const { result } = renderHook(() => useFeatureFlags());
    expect(result.current).toEqual({ flag1: true, flag2: false });
  });

  it("should return flags as-is when already a record", () => {
    const record = { myFlag: true };
    vi.mocked(useFlags).mockReturnValue(record as any);
    const { result } = renderHook(() => useFeatureFlags());
    expect(result.current).toEqual(record);
  });
});

describe("useFeatureFlagWithContext", () => {
  beforeEach(() => {
    vi.mocked(useFeatureFlagsContext).mockReturnValue({
      isReady: true,
      flagsError: null,
    });
    vi.mocked(useFlag).mockReturnValue(true);
  });

  it("should delegate to useFeatureFlag", () => {
    const { result } = renderHook(() =>
      useFeatureFlagWithContext("my-flag", { userId: "u1" })
    );
    expect(result.current.enabled).toBe(true);
  });
});
