import { describe, expect, it, beforeEach } from "vitest";
import {
  pushRecentFundCvm,
  readRecentFundCvm,
  pushRecentCrypto,
  readRecentCrypto,
} from "../newHoldingFlowRecentStorage";

const fund = (id: string) => ({
  id,
  name: `Fund ${id}`,
  cnpj: "00000000000100",
  assetType: "MUTUAL_FUND",
  isActive: true,
});

describe("newHoldingFlowRecentStorage", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("keeps at most 5 fund CVM entries, most recent first, deduped by id", () => {
    for (let i = 1; i <= 6; i += 1) {
      pushRecentFundCvm(fund(String(i)));
    }
    const list = readRecentFundCvm();
    expect(list).toHaveLength(5);
    expect(list[0]?.id).toBe("6");
    expect(list.map((f) => f.id).join(",")).toBe("6,5,4,3,2");
  });

  it("moves duplicate fund to front", () => {
    pushRecentFundCvm(fund("a"));
    pushRecentFundCvm(fund("b"));
    pushRecentFundCvm({ ...fund("a"), name: "Renamed" });
    const list = readRecentFundCvm();
    expect(list).toHaveLength(2);
    expect(list[0]?.id).toBe("a");
    expect(list[0]?.name).toBe("Renamed");
  });

  it("stores crypto MRU", () => {
    pushRecentCrypto({ name: "Bitcoin", ticker: "btc", network: "" });
    pushRecentCrypto({ name: "Ether", ticker: "eth", network: "ERC-20" });
    const list = readRecentCrypto();
    expect(list).toHaveLength(2);
    expect(list[0]?.ticker).toBe("eth");
  });
});
