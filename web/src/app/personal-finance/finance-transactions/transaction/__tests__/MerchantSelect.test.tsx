import { describe, expect, it } from "vitest";
import { normalizeMerchantWebsiteDomain } from "../MerchantSelect";

describe("normalizeMerchantWebsiteDomain", () => {
  it("normalizes protocol, www and path", () => {
    expect(normalizeMerchantWebsiteDomain("https://www.netflix.com/br/plans")).toBe("netflix.com");
  });

  it("accepts plain domains", () => {
    expect(normalizeMerchantWebsiteDomain("spotify.com")).toBe("spotify.com");
  });

  it("rejects invalid input", () => {
    expect(normalizeMerchantWebsiteDomain("notawebsite")).toBeNull();
    expect(normalizeMerchantWebsiteDomain("")).toBeNull();
  });
});
