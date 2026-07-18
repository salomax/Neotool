import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';
import { toFormDateValue } from '../DatePickers';

describe('toFormDateValue', () => {
  it('returns null when the picker value is cleared', () => {
    expect(toFormDateValue(null)).toBeNull();
  });

  it('returns null for invalid Dayjs so RHF clears stale valid dates', () => {
    expect(toFormDateValue(dayjs('not-a-date'))).toBeNull();
    expect(toFormDateValue(dayjs(Number.NaN))).toBeNull();
  });

  it('returns local start-of-day Date for a valid Dayjs value', () => {
    const result = toFormDateValue(dayjs('2026-03-15T18:22:09'));
    expect(result).toBeInstanceOf(Date);
    expect(result?.getFullYear()).toBe(2026);
    expect(result?.getMonth()).toBe(2);
    expect(result?.getDate()).toBe(15);
    expect(result?.getHours()).toBe(0);
    expect(result?.getMinutes()).toBe(0);
  });
});
