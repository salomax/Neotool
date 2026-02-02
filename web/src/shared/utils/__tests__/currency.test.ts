import { describe, it, expect } from "vitest";
import { parseCurrencyValue, parseNumberValue } from "../currency";

describe("currency utilities", () => {
  describe("parseCurrencyValue", () => {
    it("should return zero with symbol and null totalizer for null", () => {
      const result = parseCurrencyValue(null);
      expect(result.symbol).toBeTruthy();
      expect(result.number).toBe("0");
      expect(result.totalizer).toBeNull();
    });

    it("should return zero with symbol and null totalizer for undefined", () => {
      const result = parseCurrencyValue(undefined);
      expect(result.symbol).toBeTruthy();
      expect(result.number).toBe("0");
      expect(result.totalizer).toBeNull();
    });

    it("should return zero with symbol and null totalizer for 0", () => {
      const result = parseCurrencyValue(0);
      expect(result.symbol).toBeTruthy();
      expect(result.number).toBe("0");
      expect(result.totalizer).toBeNull();
    });

    it("should format trillions with short totalizer", () => {
      const result = parseCurrencyValue(2_500_000_000_000, "BRL", "pt-BR", undefined, "short");
      expect(result.totalizer).toBe("tri");
      expect(result.number).toMatch(/2[.,]?5/);
    });

    it("should format trillions with long totalizer when labels provided", () => {
      const result = parseCurrencyValue(1_000_000_000_000, "BRL", "pt-BR", {
        billions: "bi",
        millions: "mi",
        trillions: "tri",
        trillionsLong: "trilhões",
      }, "long");
      expect(result.totalizer).toBe("trilhões");
    });

    it("should format billions with short totalizer", () => {
      const result = parseCurrencyValue(87_400_000_000);
      expect(result.totalizer).toBe("bi");
      expect(result.number).toMatch(/87[.,]?4/);
    });

    it("should format billions with long totalizer when labels provided", () => {
      const result = parseCurrencyValue(1_500_000_000, "BRL", "pt-BR", {
        billions: "bi",
        millions: "mi",
        billionsLong: "bilhões",
        millionsLong: "milhões",
      }, "long");
      expect(result.totalizer).toBe("bilhões");
    });

    it("should format millions with short totalizer", () => {
      const result = parseCurrencyValue(2_500_000);
      expect(result.totalizer).toBe("mi");
      expect(result.number).toMatch(/2[.,]?5/);
    });

    it("should format millions with long totalizer when labels provided", () => {
      const result = parseCurrencyValue(1_000_000, "BRL", "pt-BR", {
        billions: "bi",
        millions: "mi",
        millionsLong: "milhões",
      }, "long");
      expect(result.totalizer).toBe("milhões");
    });

    it("should format values under 1 million with number and null totalizer", () => {
      const result = parseCurrencyValue(1234.56);
      expect(result.totalizer).toBeNull();
      expect(result.number).toBeTruthy();
      expect(result.symbol).toBeTruthy();
    });

    it("should handle negative values for trillions", () => {
      const result = parseCurrencyValue(-1_500_000_000_000);
      expect(result.number).toContain("-");
      expect(result.totalizer).toBe("tri");
    });

    it("should handle negative values for billions", () => {
      const result = parseCurrencyValue(-50_000_000_000);
      expect(result.number).toContain("-");
      expect(result.totalizer).toBe("bi");
    });

    it("should handle negative values for millions", () => {
      const result = parseCurrencyValue(-2_000_000);
      expect(result.number).toContain("-");
      expect(result.totalizer).toBe("mi");
    });

    it("should use custom labels for short format", () => {
      const result = parseCurrencyValue(1_000_000_000, "BRL", "pt-BR", {
        billions: "B",
        millions: "M",
        trillions: "T",
      }, "short");
      expect(result.totalizer).toBe("B");
    });
  });

  describe("parseNumberValue", () => {
    it("should format trillions", () => {
      const result = parseNumberValue(1_000_000_000_000);
      expect(result.totalizer).toBe("tri");
      expect(result.number).toMatch(/1[.,]?0/);
    });

    it("should format trillions with long format", () => {
      const result = parseNumberValue(2_000_000_000_000, "pt-BR", undefined, "long");
      expect(result.totalizer).toBe("trilhões");
    });

    it("should format billions", () => {
      const result = parseNumberValue(87_400_000_000);
      expect(result.totalizer).toBe("bi");
    });

    it("should format billions with long format and custom labels", () => {
      const result = parseNumberValue(1_500_000_000, "pt-BR", {
        billionsLong: "bilhões",
        millionsLong: "milhões",
        trillionsLong: "trilhões",
      }, "long");
      expect(result.totalizer).toBe("bilhões");
    });

    it("should format millions", () => {
      const result = parseNumberValue(2_500_000);
      expect(result.totalizer).toBe("mi");
    });

    it("should return number and null totalizer for values under 1 million", () => {
      const result = parseNumberValue(12345);
      expect(result.totalizer).toBeNull();
      expect(result.number).toBeTruthy();
    });

    it("should handle negative values", () => {
      const result = parseNumberValue(-50_000_000_000);
      expect(result.number).toContain("-");
      expect(result.totalizer).toBe("bi");
    });

    it("should use custom labels for trillions", () => {
      const result = parseNumberValue(1_000_000_000_000, "pt-BR", {
        trillions: "T",
        trillionsLong: "trilhões",
      }, "short");
      expect(result.totalizer).toBe("T");
    });
  });
});
