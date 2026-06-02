import { describe, expect, it } from 'vitest';
import {
  addLocalWeekdayBusinessDays,
  defaultB3EquityOrReitSettlementFromTradeDate,
  isLocalWeekendDate,
} from '../businessDays';

describe('isLocalWeekendDate', () => {
  it('treats Saturday and Sunday as weekend', () => {
    expect(isLocalWeekendDate(new Date(2026, 4, 16))).toBe(true); // Sat May 16 2026
    expect(isLocalWeekendDate(new Date(2026, 4, 17))).toBe(true); // Sun
    expect(isLocalWeekendDate(new Date(2026, 4, 18))).toBe(false); // Mon
  });
});

describe('addLocalWeekdayBusinessDays', () => {
  it('Monday + 2 business days → Wednesday', () => {
    const trade = new Date(2026, 4, 11); // Mon May 11 2026
    const out = addLocalWeekdayBusinessDays(trade, 2);
    expect(out.getFullYear()).toBe(2026);
    expect(out.getMonth()).toBe(4);
    expect(out.getDate()).toBe(13);
  });

  it('Friday + 2 business days → Tuesday (skips weekend)', () => {
    const trade = new Date(2026, 4, 15); // Fri May 15 2026
    const out = addLocalWeekdayBusinessDays(trade, 2);
    expect(out.getFullYear()).toBe(2026);
    expect(out.getMonth()).toBe(4);
    expect(out.getDate()).toBe(19);
  });

  it('returns trade date start-of-day when count is 0', () => {
    const trade = new Date(2026, 4, 15, 15, 30, 0);
    const out = addLocalWeekdayBusinessDays(trade, 0);
    expect(out.getHours()).toBe(0);
    expect(out.getDate()).toBe(15);
  });
});

describe('defaultB3EquityOrReitSettlementFromTradeDate', () => {
  it('matches D+2 business days', () => {
    const trade = new Date(2026, 4, 14); // Thu May 14 2026
    expect(defaultB3EquityOrReitSettlementFromTradeDate(trade).getTime()).toBe(
      addLocalWeekdayBusinessDays(trade, 2).getTime(),
    );
  });
});
