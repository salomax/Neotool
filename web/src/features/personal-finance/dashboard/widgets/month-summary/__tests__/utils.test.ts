import { describe, expect, it } from 'vitest';
import {
  formatPercent,
  splitVolumePercentages,
  toPercentageGainRatio,
} from '../utils';

describe('month-summary utils', () => {
  describe('formatPercent', () => {
    it('returns em dash for nullish and non-finite values', () => {
      expect(formatPercent(null)).toBe('—');
      expect(formatPercent(undefined)).toBe('—');
      expect(formatPercent(Number.NaN)).toBe('—');
      expect(formatPercent(Number.POSITIVE_INFINITY)).toBe('—');
    });

    it('formats numbers with one decimal place and percent suffix', () => {
      expect(formatPercent(12.34)).toBe('12,3%');
    });
  });

  describe('splitVolumePercentages', () => {
    it('returns zero percentages when total volume is zero', () => {
      expect(splitVolumePercentages(100, 50, 0)).toEqual({ incomePct: 0, expensesPct: 0 });
    });

    it('keeps rounded percentages summing to 100', () => {
      const result = splitVolumePercentages(1, 2, 3);
      expect(result).toEqual({ incomePct: 33, expensesPct: 67 });
      expect(result.incomePct + result.expensesPct).toBe(100);
    });
  });

  describe('toPercentageGainRatio', () => {
    it('returns null for nullish and non-finite values', () => {
      expect(toPercentageGainRatio(null)).toBeNull();
      expect(toPercentageGainRatio(undefined)).toBeNull();
      expect(toPercentageGainRatio(Number.NaN)).toBeNull();
      expect(toPercentageGainRatio(Number.POSITIVE_INFINITY)).toBeNull();
    });

    it('converts percentage points into ratio for PercentageGain', () => {
      expect(toPercentageGainRatio(0)).toBe(0);
      expect(toPercentageGainRatio(12.5)).toBe(0.125);
      expect(toPercentageGainRatio(-8)).toBe(-0.08);
    });
  });
});
