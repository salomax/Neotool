import { describe, it, expect } from "vitest";
import {
  BASILEIA_THRESHOLDS,
  IMMOBILIZATION_THRESHOLDS,
} from "../metrics";

describe("metrics constants", () => {
  describe("BASILEIA_THRESHOLDS", () => {
    it("should have critical and warning values", () => {
      expect(BASILEIA_THRESHOLDS.critical).toBe(11);
      expect(BASILEIA_THRESHOLDS.warning).toBe(14);
    });

    it("should be readonly", () => {
      expect(Object.keys(BASILEIA_THRESHOLDS)).toEqual([
        "critical",
        "warning",
      ]);
    });
  });

  describe("IMMOBILIZATION_THRESHOLDS", () => {
    it("should have good, warning, and critical values", () => {
      expect(IMMOBILIZATION_THRESHOLDS.good).toBe(40);
      expect(IMMOBILIZATION_THRESHOLDS.warning).toBe(50);
      expect(IMMOBILIZATION_THRESHOLDS.critical).toBe(100);
    });

    it("should be readonly", () => {
      expect(Object.keys(IMMOBILIZATION_THRESHOLDS)).toEqual([
        "good",
        "warning",
        "critical",
      ]);
    });
  });
});
