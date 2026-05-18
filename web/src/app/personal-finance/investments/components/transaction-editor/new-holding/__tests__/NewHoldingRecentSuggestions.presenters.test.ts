import { describe, expect, it } from "vitest";
import {
  recentEquityPrimaryFromTicker,
  recentFundPrimaryShort,
  recentSuggestionTileFromName,
} from "../NewHoldingRecentSuggestions";

describe("NewHoldingRecentSuggestions presenters", () => {
  it("strips trailing digits for equity primary", () => {
    expect(recentEquityPrimaryFromTicker("CMIG3")).toBe("CMIG");
    expect(recentEquityPrimaryFromTicker("PETR4")).toBe("PETR");
  });

  it("uses first word for fund primary short", () => {
    expect(recentFundPrimaryShort("Companhia Energética de Minas Gerais")).toBe("COMPANHIA");
  });

  it("builds compact tile from fund name", () => {
    expect(recentSuggestionTileFromName("XP Long Biased FIC FIM")).toMatch(/^XPL/);
  });
});
