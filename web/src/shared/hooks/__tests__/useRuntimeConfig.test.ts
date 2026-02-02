import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { getRuntimeConfig } from "@/shared/config/runtime-config";
import { useRuntimeConfig } from "../useRuntimeConfig";

vi.mock("@/shared/config/runtime-config", () => ({
  getRuntimeConfig: vi.fn(),
}));

describe("useRuntimeConfig", () => {
  const mockConfig = {
    env: "test",
    unleashProxyUrl: "https://unleash.example.com",
    unleashClientToken: "token",
    graphqlEndpoint: "https://api.example.com/graphql",
    googleClientId: "client-id",
  };

  beforeEach(() => {
    vi.mocked(getRuntimeConfig).mockReturnValue(mockConfig);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("should return runtime config from getRuntimeConfig", () => {
    const { result } = renderHook(() => useRuntimeConfig());

    expect(result.current).toEqual(mockConfig);
    expect(getRuntimeConfig).toHaveBeenCalled();
  });

  it("should memoize config (getRuntimeConfig called once per render)", () => {
    const { result, rerender } = renderHook(() => useRuntimeConfig());

    expect(getRuntimeConfig).toHaveBeenCalled();
    rerender();
    expect(getRuntimeConfig).toHaveBeenCalled();
    expect(result.current).toEqual(mockConfig);
  });
});
