import { describe, expect, it } from 'vitest';
import {
  coerceInvestmentSettlementDate,
  defaultSettlementDateFromTrade,
  getInvestmentSettlementBounds,
  maxSettlementDateFromTrade,
} from '../settlementDatePolicy';

describe('defaultSettlementDateFromTrade', () => {
  it('uses D+0 for fixed income, fund, and crypto (start of local trade day)', () => {
    const trade = new Date(2026, 4, 14, 15, 30, 0);
    const want = new Date(2026, 4, 14, 0, 0, 0, 0);
    expect(defaultSettlementDateFromTrade('fixedIncome', trade).getTime()).toBe(want.getTime());
    expect(defaultSettlementDateFromTrade('fund', trade).getTime()).toBe(want.getTime());
    expect(defaultSettlementDateFromTrade('crypto', trade).getTime()).toBe(want.getTime());
  });

  it('uses D+2 weekdays for equity from Thursday → Monday', () => {
    const trade = new Date(2026, 4, 14);
    const out = defaultSettlementDateFromTrade('equity', trade);
    expect(out.getFullYear()).toBe(2026);
    expect(out.getMonth()).toBe(4);
    expect(out.getDate()).toBe(18);
  });
});

describe('maxSettlementDateFromTrade', () => {
  it('is trade date + 1 calendar year', () => {
    const trade = new Date(2024, 2, 10);
    const max = maxSettlementDateFromTrade(trade);
    expect(max.getFullYear()).toBe(2025);
    expect(max.getMonth()).toBe(2);
    expect(max.getDate()).toBe(10);
  });
});

describe('getInvestmentSettlementBounds', () => {
  it('uses trade date as min when after minAllowed', () => {
    const trade = new Date(2026, 5, 1);
    const minAllowed = new Date(2026, 0, 1);
    const { min, max } = getInvestmentSettlementBounds(trade, minAllowed);
    expect(min.getTime()).toBe(trade.getTime());
    expect(max.getTime()).toBe(maxSettlementDateFromTrade(trade).getTime());
  });
});

describe('coerceInvestmentSettlementDate', () => {
  it('clamps settlement beyond trade + 1 year', () => {
    const trade = new Date(2026, 0, 1);
    const tooLate = new Date(2028, 0, 1);
    const out = coerceInvestmentSettlementDate('fund', trade, tooLate);
    expect(out.getTime()).toBe(maxSettlementDateFromTrade(trade).getTime());
  });
});
