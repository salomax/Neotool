import { describe, it, expect } from "vitest";
import {
  isRouteProtected,
  PROTECTED_ROUTE_PREFIXES,
} from "../auth-route-policy";

describe("auth-route-policy", () => {
  describe("PROTECTED_ROUTE_PREFIXES", () => {
    it("includes expected protected segments", () => {
      expect(PROTECTED_ROUTE_PREFIXES).toContain("/personal-finance");
      expect(PROTECTED_ROUTE_PREFIXES).toContain("/profile");
      expect(PROTECTED_ROUTE_PREFIXES).toContain("/settings");
    });
  });

  describe("isRouteProtected", () => {
    it("returns true for protected path exact match", () => {
      expect(isRouteProtected("/settings")).toBe(true);
      expect(isRouteProtected("/personal-finance")).toBe(true);
      expect(isRouteProtected("/profile")).toBe(true);
    });

    it("returns true for protected path with segment", () => {
      expect(isRouteProtected("/settings/authorization")).toBe(true);
      expect(isRouteProtected("/personal-finance/finance-transactions")).toBe(
        true
      );
      expect(isRouteProtected("/profile/security")).toBe(true);
    });

    it("returns false for public and anonymous-area routes", () => {
      expect(isRouteProtected("/")).toBe(false);
      expect(isRouteProtected("/signin")).toBe(false);
      expect(isRouteProtected("/signup")).toBe(false);
      expect(isRouteProtected("/forgot-password")).toBe(false);
      expect(isRouteProtected("/financial-data")).toBe(false);
      expect(isRouteProtected("/financial-data/bacen-institutions")).toBe(false);
      expect(isRouteProtected("/investments")).toBe(false);
      expect(isRouteProtected("/investments/fixed-income-comparison")).toBe(false);
    });

    it("returns false for null or empty pathname", () => {
      expect(isRouteProtected(null)).toBe(false);
      expect(isRouteProtected("")).toBe(false);
    });

    it("normalizes trailing slash", () => {
      expect(isRouteProtected("/settings/")).toBe(true);
      expect(isRouteProtected("/")).toBe(false);
    });
  });
});
