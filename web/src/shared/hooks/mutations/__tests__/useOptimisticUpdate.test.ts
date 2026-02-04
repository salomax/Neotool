import { describe, it, expect, vi, afterEach } from "vitest";
import { renderHook, act, waitFor, cleanup } from "@testing-library/react";
import { useOptimisticUpdate } from "../useOptimisticUpdate";


afterEach(() => {
  cleanup();
});

describe.sequential("useOptimisticUpdate", () => {
  it("should initialize with provided value", () => {
    const { result } = renderHook(() =>
      useOptimisticUpdate({ value: false })
    );

    expect(result.current.optimisticValue).toBe(false);
    expect(result.current.isUpdating).toBe(false);
  });

  it("should allow setting optimistic value directly when syncOnPropChange is false", async () => {
    const { result } = renderHook(() =>
      useOptimisticUpdate({ value: false, syncOnPropChange: false })
    );

    expect(result.current.optimisticValue).toBe(false);

    act(() => {
      result.current.setOptimisticValue(true);
    });

    await waitFor(() => {
      expect(result.current.optimisticValue).toBe(true);
    });
  });

  it("should set isUpdating via setIsUpdating", () => {
    const { result } = renderHook(() =>
      useOptimisticUpdate({ value: 0 })
    );

    act(() => {
      result.current.setIsUpdating(true);
    });

    expect(result.current.isUpdating).toBe(true);
  });

  it("should execute update: set optimistic value, call updateFn, clear updating", async () => {
    const updateFn = vi.fn().mockResolvedValue(undefined);

    const { result } = renderHook(() =>
      useOptimisticUpdate({ value: false })
    );

    let promise: Promise<void>;
    act(() => {
      promise = result.current.executeUpdate(true, updateFn);
    });

    await waitFor(() => {
      expect(result.current.optimisticValue).toBe(true);
    });

    await act(async () => {
      await promise;
    });

    expect(updateFn).toHaveBeenCalled();
    await waitFor(() => {
      expect(result.current.isUpdating).toBe(false);
    });
  });

  it("should revert optimistic value on updateFn error", async () => {
    const updateFn = vi.fn().mockRejectedValue(new Error("Failed"));

    const { result } = renderHook(() =>
      useOptimisticUpdate({ value: false })
    );

    await expect(
      act(async () => {
        await result.current.executeUpdate(true, updateFn);
      })
    ).rejects.toThrow("Failed");

    expect(result.current.optimisticValue).toBe(false);
    expect(result.current.isUpdating).toBe(false);
  });

  it("should not run executeUpdate when already updating", async () => {
    let resolveUpdate: () => void;
    const updateFn = vi.fn().mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          resolveUpdate = resolve;
        })
    );

    const { result } = renderHook(() =>
      useOptimisticUpdate({ value: false })
    );

    let firstPromise: Promise<void>;
    act(() => {
      firstPromise = result.current.executeUpdate(true, updateFn);
    });

    await waitFor(() => {
      expect(result.current.isUpdating).toBe(true);
    });

    const secondUpdateFn = vi.fn();
    let secondPromise: Promise<void>;
    act(() => {
      secondPromise = result.current.executeUpdate(false, secondUpdateFn);
    });

    expect(secondUpdateFn).not.toHaveBeenCalled();
    resolveUpdate!();

    await act(async () => {
      await firstPromise;
      await secondPromise;
    });
  });

  it("should sync optimistic value with prop when syncOnPropChange is true", () => {
    const { result, rerender } = renderHook(
      ({ value }) => useOptimisticUpdate({ value }),
      { initialProps: { value: false } }
    );

    expect(result.current.optimisticValue).toBe(false);

    rerender({ value: true });

    expect(result.current.optimisticValue).toBe(true);
  });
});
