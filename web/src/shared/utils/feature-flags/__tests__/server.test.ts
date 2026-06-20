import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  DEFAULT_UNLEASH_READY_TIMEOUT_MS,
  isUnleashClientReadyForBootstrap,
  waitForReady,
} from "../server";

function createClient(overrides: Partial<{
  isSynchronized: () => boolean;
  getFeatureToggleDefinitions: () => Array<{ name: string }>;
  once: (event: string, callback: () => void) => void;
  ready: boolean;
}> = {}) {
  const client = {
    isSynchronized: vi.fn(() => false),
    getFeatureToggleDefinitions: vi.fn(() => []),
    once: vi.fn(),
    ...overrides,
  };

  return client as unknown as Parameters<typeof waitForReady>[0];
}

describe.sequential("feature flag bootstrap readiness helper", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it("detects already synchronized clients as ready", () => {
    const client = createClient({
      isSynchronized: vi.fn(() => true),
    });

    expect(isUnleashClientReadyForBootstrap(client as never)).toBe(true);
  });

  it("detects preloaded toggle definitions as ready without attaching listeners", async () => {
    const client = createClient({
      getFeatureToggleDefinitions: vi.fn(() => [{ name: "flag-a" }]),
    });

    await expect(waitForReady(client as never)).resolves.toBe(true);
    expect(client.once).not.toHaveBeenCalled();
  });

  it("times out quickly when the client never becomes ready", async () => {
    const client = createClient();
    const promise = waitForReady(client as never, DEFAULT_UNLEASH_READY_TIMEOUT_MS);

    await vi.advanceTimersByTimeAsync(DEFAULT_UNLEASH_READY_TIMEOUT_MS + 1);

    await expect(promise).resolves.toBe(false);
    expect(client.once).toHaveBeenCalledWith("ready", expect.any(Function));
  });
});
