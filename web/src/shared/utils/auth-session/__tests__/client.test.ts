import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  AUTH_SESSION_SYNC_TIMEOUT_MS,
  syncAuthSessionCookie,
} from "../client";

const mockLoggerDebug = vi.hoisted(() => vi.fn());

vi.mock("@/shared/utils/logger", () => ({
  logger: {
    debug: mockLoggerDebug,
  },
}));

describe.sequential("syncAuthSessionCookie", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });

  it("returns synced result when session cookie sync succeeds", async () => {
    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>;
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
    });

    const result = await syncAuthSessionCookie({
      user: { id: "user-1" },
      rememberMe: true,
    });

    expect(result).toEqual({
      ok: true,
      status: "synced",
      statusCode: 200,
    });
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/auth/session",
      expect.objectContaining({
        method: "POST",
        credentials: "include",
        body: JSON.stringify({ user: { id: "user-1" }, rememberMe: true }),
      })
    );
  });

  it("returns http_error result when session cookie sync returns non-ok response", async () => {
    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>;
    fetchMock.mockResolvedValue({
      ok: false,
      status: 503,
    });

    const result = await syncAuthSessionCookie({
      user: { id: "user-1" },
    });

    expect(result).toEqual({
      ok: false,
      status: "http_error",
      statusCode: 503,
    });
    expect(mockLoggerDebug).toHaveBeenCalledWith(
      "[AuthSession] Failed to sync auth session cookie",
      { status: 503 }
    );
  });

  it("returns timeout result when session cookie sync exceeds timeout", async () => {
    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>;
    fetchMock.mockImplementation((_input, init) =>
      new Promise((_, reject) => {
        init?.signal?.addEventListener("abort", () => {
          const error = new DOMException("The operation was aborted.", "AbortError");
          reject(error);
        });
      })
    );

    const promise = syncAuthSessionCookie({
      user: { id: "user-1" },
      timeoutMs: AUTH_SESSION_SYNC_TIMEOUT_MS,
    });

    await vi.advanceTimersByTimeAsync(AUTH_SESSION_SYNC_TIMEOUT_MS + 1);

    await expect(promise).resolves.toEqual({
      ok: false,
      status: "timeout",
    });
    expect(mockLoggerDebug).toHaveBeenCalledWith(
      "[AuthSession] Timed out while syncing auth session cookie",
      { timeoutMs: AUTH_SESSION_SYNC_TIMEOUT_MS }
    );
  });

  it("returns network_error result when session cookie sync request fails", async () => {
    const fetchMock = globalThis.fetch as ReturnType<typeof vi.fn>;
    const error = new Error("network down");
    fetchMock.mockRejectedValue(error);

    const result = await syncAuthSessionCookie({
      user: { id: "user-1" },
    });

    expect(result).toEqual({
      ok: false,
      status: "network_error",
      error,
    });
    expect(mockLoggerDebug).toHaveBeenCalledWith(
      "[AuthSession] Failed to sync auth session cookie",
      error
    );
  });
});
