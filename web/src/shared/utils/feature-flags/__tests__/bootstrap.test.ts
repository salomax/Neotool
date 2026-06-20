import { describe, expect, it, vi, beforeEach } from "vitest";
import { getBootstrapFeatureFlagsData } from "../bootstrap";

const mockGetFeatureFlags = vi.hoisted(() => vi.fn());
const mockGetAuthSessionContextFromCookies = vi.hoisted(() => vi.fn());

vi.mock("../server", () => ({
  getFeatureFlags: mockGetFeatureFlags,
}));

vi.mock("@/shared/utils/auth-session/server", () => ({
  getAuthSessionContextFromCookies: mockGetAuthSessionContextFromCookies,
}));

describe.sequential("getBootstrapFeatureFlagsData", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("returns immediate client-side bootstrap for authenticated sessions", async () => {
    mockGetAuthSessionContextFromCookies.mockResolvedValue({
      userId: "user-1",
      tenantId: "tenant-1",
      role: "admin",
    });

    const result = await getBootstrapFeatureFlagsData();

    expect(result).toEqual({
      flags: {},
      context: {
        environment: "production",
        userId: "user-1",
        tenantId: "tenant-1",
        role: "admin",
      },
      sessionContext: {
        userId: "user-1",
        tenantId: "tenant-1",
        role: "admin",
      },
    });
    expect(mockGetFeatureFlags).not.toHaveBeenCalled();
  });

  it("fetches bootstrap flags for anonymous sessions", async () => {
    mockGetAuthSessionContextFromCookies.mockResolvedValue(null);
    mockGetFeatureFlags.mockResolvedValue({
      "public-flag": true,
    });

    const result = await getBootstrapFeatureFlagsData();

    expect(result).toEqual({
      flags: {
        "public-flag": true,
      },
      context: {
        environment: "production",
      },
      sessionContext: null,
    });
    expect(mockGetFeatureFlags).toHaveBeenCalledWith({
      environment: "production",
    });
  });
});
