import { describe, it, expect, vi, afterEach } from "vitest";
import { renderHook, act, cleanup } from "@testing-library/react";
import { useMutationWithRefetch } from "../useMutationWithRefetch";


afterEach(() => {
  cleanup();
});

vi.mock("@/shared/utils/error", () => ({
  extractErrorMessage: (err: unknown, fallback: string) =>
    err instanceof Error ? err.message : fallback,
}));

describe.sequential("useMutationWithRefetch", () => {
  it("should return executeMutation and isMutationInFlight", () => {
    const { result } = renderHook(() => useMutationWithRefetch());

    expect(typeof result.current.executeMutation).toBe("function");
    expect(typeof result.current.isMutationInFlight).toBe("function");
    expect(result.current.isMutationInFlight("key1")).toBe(false);
  });

  it("should execute mutation and return result", async () => {
    const mutationFn = vi.fn().mockResolvedValue({ data: { id: "1" } });

    const { result } = renderHook(() => useMutationWithRefetch());

    let res: { data?: { id: string } } = {};
    await act(async () => {
      res = await result.current.executeMutation(
        mutationFn,
        { id: "1" },
        "key1"
      );
    });

    expect(mutationFn).toHaveBeenCalledWith({ variables: { id: "1" } });
    expect(res.data).toEqual({ id: "1" });
    expect(result.current.isMutationInFlight("key1")).toBe(false);
  });

  it("should report isMutationInFlight true while mutation is running", async () => {
    let resolveMutation: (value: { data?: unknown }) => void;
    const mutationPromise = new Promise<{ data?: unknown }>((resolve) => {
      resolveMutation = resolve;
    });
    const mutationFn = vi.fn().mockReturnValue(mutationPromise);

    const { result } = renderHook(() => useMutationWithRefetch());

    expect(result.current.isMutationInFlight("inflight-key")).toBe(false);

    let runPromise: Promise<{ data?: unknown }>;
    act(() => {
      runPromise = result.current.executeMutation(
        mutationFn,
        {},
        "inflight-key"
      );
    });

    expect(result.current.isMutationInFlight("inflight-key")).toBe(true);

    resolveMutation!({ data: {} });
    await act(async () => {
      await runPromise;
    });
    expect(result.current.isMutationInFlight("inflight-key")).toBe(false);
  });

  it("should pass refetchQueries when refetchQuery and refetchVariables provided", async () => {
    const mutationFn = vi.fn().mockResolvedValue({ data: {} });
    const refetchQuery = {} as any;
    const refetchVariables = { first: 10 };

    const { result } = renderHook(() =>
      useMutationWithRefetch({
        refetchQuery,
        refetchVariables,
      })
    );

    await act(async () => {
      await result.current.executeMutation(mutationFn, {}, "key1");
    });

    expect(mutationFn).toHaveBeenCalledWith({
      variables: {},
      refetchQueries: [
        {
          query: refetchQuery,
          variables: refetchVariables,
        },
      ],
    });
  });

  it("should call onRefetch after successful mutation", async () => {
    const onRefetch = vi.fn();
    const mutationFn = vi.fn().mockResolvedValue({ data: { id: "1" } });

    const { result } = renderHook(() =>
      useMutationWithRefetch({ onRefetch })
    );

    await result.current.executeMutation(mutationFn, {}, "key1");

    expect(onRefetch).toHaveBeenCalled();
  });

  it("should throw when mutation fails", async () => {
    const mutationFn = vi.fn().mockRejectedValue(new Error("Network error"));

    const { result } = renderHook(() =>
      useMutationWithRefetch({ errorMessage: "Custom error" })
    );

    await expect(
      result.current.executeMutation(mutationFn, {}, "key1")
    ).rejects.toThrow("Network error");
  });
});
