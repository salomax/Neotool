import { describe, it, expect } from "vitest";
import { formatMetricValue } from "../formatMetricValue";

describe("formatMetricValue", () => {
  describe("invalid or non-finite value", () => {
    it("should return dash for null", () => {
      const result = formatMetricValue(null, "currency");
      expect(result).toEqual({ prefix: "", main: "-", suffix: "" });
    });

    it("should return dash for undefined", () => {
      const result = formatMetricValue(undefined, "number");
      expect(result).toEqual({ prefix: "", main: "-", suffix: "" });
    });

    it("should return dash for NaN", () => {
      const result = formatMetricValue(Number.NaN, "percentage");
      expect(result).toEqual({ prefix: "", main: "-", suffix: "" });
    });

    it("should return dash for Infinity", () => {
      const result = formatMetricValue(Number.POSITIVE_INFINITY, "currency");
      expect(result).toEqual({ prefix: "", main: "-", suffix: "" });
    });

    it("should return dash for non-number type", () => {
      const result = formatMetricValue("100" as any, "currency");
      expect(result).toEqual({ prefix: "", main: "-", suffix: "" });
    });
  });

  describe("currency type", () => {
    it("should format currency with symbol and number", () => {
      const result = formatMetricValue(1234.56, "currency");
      expect(result.prefix).toBeTruthy();
      expect(result.main).toBeTruthy();
      expect(result.suffix).toBe("");
    });

    it("should include totalizer for billions", () => {
      const result = formatMetricValue(1_500_000_000, "currency");
      expect(result.suffix).toBe("bi");
    });

    it("should use options (currency, locale, totalizerFormat)", () => {
      const result = formatMetricValue(1_000_000_000, "currency", {
        currency: "USD",
        locale: "en-US",
        totalizerFormat: "long",
      });
      expect(result.suffix).toBe("bilhões");
    });
  });

  describe("number type", () => {
    it("should format number without prefix", () => {
      const result = formatMetricValue(12345, "number");
      expect(result.prefix).toBe("");
      expect(result.main).toBeTruthy();
      expect(result.suffix).toBe("");
    });

    it("should include totalizer for millions", () => {
      const result = formatMetricValue(2_500_000, "number");
      expect(result.suffix).toBe("mi");
    });
  });

  describe("percentage type", () => {
    it("should format percentage with suffix %", () => {
      const result = formatMetricValue(0.156, "percentage");
      expect(result.prefix).toBe("");
      expect(result.main).toBeTruthy();
      expect(result.suffix).toBe("%");
    });

    it("should use locale for percentage", () => {
      const result = formatMetricValue(0.156, "percentage", { locale: "en-US" });
      expect(result.suffix).toBe("%");
      expect(result.main).toMatch(/\d/);
    });
  });

  describe("fallback for unknown valueType", () => {
    it("should return value as string when valueType hits default branch", () => {
      const result = formatMetricValue(99, "unknown" as any, {});
      expect(result).toEqual({ prefix: "", main: "99", suffix: "" });
    });
  });
});
