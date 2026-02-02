import { describe, it, expect } from "vitest";
import { getDerivedFontSize, getCustomThemeValue } from "../theme";
import type { Theme } from "@mui/material/styles";

describe("theme utilities", () => {
  describe("getDerivedFontSize", () => {
    it("should compute font size from base token and ratio", () => {
      const theme = {
        typography: {
          body2: { fontSize: 14 },
        },
      } as unknown as Theme;
      expect(getDerivedFontSize(theme, "body2", 10 / 14)).toBe("10px");
    });

    it("should handle string fontSize", () => {
      const theme = {
        typography: {
          body2: { fontSize: "14px" },
        },
      } as unknown as Theme;
      expect(getDerivedFontSize(theme, "body2", 1)).toBe("14px");
    });

    it("should use 14 as fallback when token missing", () => {
      const theme = { typography: {} } as unknown as Theme;
      expect(getDerivedFontSize(theme, "body2" as any, 1)).toBe("14px");
    });

    it("should use 14 when fontSize is invalid string", () => {
      const theme = {
        typography: {
          body2: { fontSize: "invalid" },
        },
      } as unknown as Theme;
      expect(getDerivedFontSize(theme, "body2", 1)).toBe("14px");
    });

    it("should apply ratio correctly", () => {
      const theme = {
        typography: {
          h6: { fontSize: 20 },
        },
      } as unknown as Theme;
      expect(getDerivedFontSize(theme, "h6", 0.5)).toBe("10px");
    });
  });

  describe("getCustomThemeValue", () => {
    it("should return value at path", () => {
      const theme = {
        custom: {
          border: { default: 2 },
        },
      } as any;
      expect(getCustomThemeValue(theme, "custom.border.default", 1)).toBe(2);
    });

    it("should return fallback when path does not exist", () => {
      const theme = {} as any;
      expect(getCustomThemeValue(theme, "custom.missing.path", "fallback")).toBe(
        "fallback"
      );
    });

    it("should return fallback when intermediate key is undefined", () => {
      const theme = { custom: {} } as any;
      expect(getCustomThemeValue(theme, "custom.border.default", 1)).toBe(1);
    });

    it("should return value when path has single segment", () => {
      const theme = { foo: "bar" } as any;
      expect(getCustomThemeValue(theme, "foo", "default")).toBe("bar");
    });

    it("should return fallback when final value is undefined", () => {
      const theme = { custom: { border: { default: undefined } } } as any;
      expect(getCustomThemeValue(theme, "custom.border.default", 99)).toBe(99);
    });
  });
});
