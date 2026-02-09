import { describe, it, expect } from "vitest";
import { createAppTheme } from "../theme";

describe("Theme Tokens", () => {
  it("should have correct breakpoints", () => {
    const theme = createAppTheme("light");
    expect(theme.breakpoints.values).toEqual({
      xs: 0,
      sm: 600,
      md: 960,
      lg: 1280,
      xl: 1920,
    });
  });

  it("should have mobile layout tokens", () => {
    const theme = createAppTheme("light");
    expect(theme.custom.layout.mobile).toBeDefined();
    expect(theme.custom.layout.mobile.headerHeight).toBe(56);
    expect(theme.custom.layout.mobile.bottomNavHeight).toBe(64);
    expect(theme.custom.layout.mobile.safeAreaBottom).toBe(34);
    expect(theme.custom.layout.mobile.touchTarget).toBe(56);
  });
  
  it("should have correct dark mode tokens", () => {
    const theme = createAppTheme("dark");
    expect(theme.custom.layout.mobile).toBeDefined();
    expect(theme.custom.layout.mobile.headerHeight).toBe(56);
    expect(theme.custom.layout.mobile.bottomNavHeight).toBe(64);
  });

  it("should have correct z-index for bottomNav", () => {
    const theme = createAppTheme("light");
    expect(theme.zIndex.bottomNav).toBe(1101);
  });
});
