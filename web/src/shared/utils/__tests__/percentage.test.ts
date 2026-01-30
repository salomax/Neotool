import { describe, it, expect } from "vitest";
import {
  formatPercentageWithSign,
  formatPercentage,
  getPercentageColor,
} from "../percentage";

describe("percentage utilities", () => {
  describe("formatPercentageWithSign", () => {
    it("should return 0% for null", () => {
      expect(formatPercentageWithSign(null)).toBe("0%");
    });

    it("should return 0% for undefined", () => {
      expect(formatPercentageWithSign(undefined)).toBe("0%");
    });

    it("should format positive value without + sign (showZeroSign only affects zero)", () => {
      expect(formatPercentageWithSign(0.12, { showZeroSign: true })).toBe("12%");
    });

    it("should format positive value without + by default", () => {
      expect(formatPercentageWithSign(0.12)).toBe("12%");
    });

    it("should format negative value with - sign", () => {
      expect(formatPercentageWithSign(-0.05)).toBe("-5%");
    });

    it("should use decimals option", () => {
      expect(formatPercentageWithSign(0.155, { decimals: 1 })).toBe("15.5%");
      expect(formatPercentageWithSign(-0.1234, { decimals: 2 })).toBe("-12.34%");
    });

    it("should format zero without sign by default", () => {
      expect(formatPercentageWithSign(0)).toBe("0%");
    });

    it("should format zero with + when showZeroSign", () => {
      expect(formatPercentageWithSign(0, { showZeroSign: true })).toBe("+0%");
    });
  });

  describe("formatPercentage", () => {
    it("should return 0% for null", () => {
      expect(formatPercentage(null)).toBe("0%");
    });

    it("should return 0% for undefined", () => {
      expect(formatPercentage(undefined)).toBe("0%");
    });

    it("should format value with default decimals", () => {
      expect(formatPercentage(0.12)).toBe("12.0%");
    });

    it("should use decimals option", () => {
      expect(formatPercentage(0.155, { decimals: 1 })).toBe("15.5%");
      expect(formatPercentage(0.1234, { decimals: 2 })).toBe("12.34%");
    });

    it("should format zero", () => {
      expect(formatPercentage(0)).toBe("0.0%");
    });
  });

  describe("getPercentageColor", () => {
    const thresholds = { critical: 11, warning: 14 };

    it("should return success.main for null", () => {
      expect(getPercentageColor(null, thresholds)).toBe("success.main");
    });

    it("should return success.main for undefined", () => {
      expect(getPercentageColor(undefined, thresholds)).toBe("success.main");
    });

    it("should return error.main when value below critical", () => {
      expect(getPercentageColor(8, thresholds)).toBe("error.main");
      expect(getPercentageColor(10, thresholds)).toBe("error.main");
    });

    it("should return warning.main when value between critical and warning", () => {
      expect(getPercentageColor(11, thresholds)).toBe("warning.main");
      expect(getPercentageColor(12, thresholds)).toBe("warning.main");
      expect(getPercentageColor(14, thresholds)).toBe("warning.main");
    });

    it("should return success.main when value above warning", () => {
      expect(getPercentageColor(15, thresholds)).toBe("success.main");
      expect(getPercentageColor(20, thresholds)).toBe("success.main");
    });
  });
});
