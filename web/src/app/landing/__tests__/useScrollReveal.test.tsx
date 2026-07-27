import React from "react";
import { act, renderHook } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ThemeProvider } from "@mui/material/styles";
import useMediaQuery from "@mui/material/useMediaQuery";
import { createAppTheme } from "@/styles/themes/theme";
import { useScrollReveal } from "../useScrollReveal";

vi.mock("@mui/material/useMediaQuery", () => ({
  default: vi.fn(),
}));

const mockUseMediaQuery = vi.mocked(useMediaQuery);

type MockObserverInstance = {
  observe: ReturnType<typeof vi.fn>;
  disconnect: ReturnType<typeof vi.fn>;
  trigger: (isIntersecting: boolean) => void;
};

function createWrapper() {
  const theme = createAppTheme("light");
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return <ThemeProvider theme={theme}>{children}</ThemeProvider>;
  };
}

describe.sequential("useScrollReveal", () => {
  let observer: MockObserverInstance | null = null;

  beforeEach(() => {
    mockUseMediaQuery.mockReturnValue(false);
    observer = null;

    global.IntersectionObserver = vi.fn((callback: (...args: unknown[]) => void) => {
      observer = {
        observe: vi.fn(),
        disconnect: vi.fn(),
        trigger: (isIntersecting: boolean) =>
          callback(
            [{ isIntersecting } as IntersectionObserverEntry],
            {} as IntersectionObserver,
          ),
      };

      return observer as unknown as IntersectionObserver;
    }) as unknown as typeof IntersectionObserver;
  });

  it("reveals after the target intersects and stays revealed", () => {
    const { result, rerender } = renderHook(() => useScrollReveal<HTMLDivElement>(), {
      wrapper: createWrapper(),
    });

    const element = document.createElement("div");
    act(() => {
      result.current.targetRef(element);
    });

    rerender();

    expect(result.current.isVisible).toBe(false);
    expect(observer?.observe).toHaveBeenCalledWith(element);

    act(() => {
      observer?.trigger(true);
    });

    expect(result.current.isVisible).toBe(true);

    act(() => {
      observer?.trigger(false);
    });

    expect(result.current.isVisible).toBe(true);

    act(() => {
      result.current.targetRef(null);
    });
  });

  it("starts visible when reduced motion is enabled", () => {
    mockUseMediaQuery.mockReturnValue(true);

    const { result } = renderHook(() => useScrollReveal<HTMLDivElement>(), {
      wrapper: createWrapper(),
    });

    expect(result.current.isVisible).toBe(true);
    expect(global.IntersectionObserver).not.toHaveBeenCalled();
  });
});
