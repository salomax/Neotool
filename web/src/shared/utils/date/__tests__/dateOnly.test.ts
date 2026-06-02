import { describe, expect, it } from 'vitest';
import {
  dateOnlyToMs,
  formatDateOnly,
  formatDateOnlyLocale,
  isValidDate,
  parseDateOnly,
  toStartOfDayLocal,
} from '../dateOnly';

describe('dateOnly utilities', () => {
  describe('parseDateOnly', () => {
    it('returns null for null and undefined', () => {
      expect(parseDateOnly(null)).toBeNull();
      expect(parseDateOnly(undefined)).toBeNull();
    });

    it('returns null for invalid input', () => {
      expect(parseDateOnly('')).toBeNull();
      expect(parseDateOnly('invalid')).toBeNull();
      expect(parseDateOnly('2026-02-31')).toBeNull();
    });

    it('parses ISO date-only input as local start of day', () => {
      const parsed = parseDateOnly('2026-03-15');
      expect(parsed).not.toBeNull();
      expect(parsed?.getFullYear()).toBe(2026);
      expect(parsed?.getMonth()).toBe(2);
      expect(parsed?.getDate()).toBe(15);
      expect(parsed?.getHours()).toBe(0);
      expect(parsed?.getMinutes()).toBe(0);
    });

    it('parses ISO datetime input and normalizes to local start of day', () => {
      const parsed = parseDateOnly('2026-03-15T21:45:10Z');
      expect(parsed).not.toBeNull();
      expect(parsed?.getHours()).toBe(0);
      expect(parsed?.getMinutes()).toBe(0);
      expect(parsed?.getSeconds()).toBe(0);
      expect(parsed?.getMilliseconds()).toBe(0);
    });

    it('parses datetime with timezone offset and normalizes to local start of day', () => {
      const parsed = parseDateOnly('2026-03-15T23:30:00-03:00');
      expect(parsed).not.toBeNull();
      expect(parsed?.getHours()).toBe(0);
      expect(parsed?.getMinutes()).toBe(0);
      expect(parsed?.getSeconds()).toBe(0);
      expect(parsed?.getMilliseconds()).toBe(0);
    });
  });

  describe('formatDateOnly', () => {
    it('formats a valid date as YYYY-MM-DD', () => {
      expect(formatDateOnly(new Date(2026, 2, 15, 18, 22, 9))).toBe('2026-03-15');
    });

    it('throws for invalid date', () => {
      expect(() => formatDateOnly(new Date('invalid'))).toThrowError(
        'formatDateOnly requires a valid Date',
      );
    });
  });

  describe('toStartOfDayLocal', () => {
    it('normalizes a date to local start of day', () => {
      const normalized = toStartOfDayLocal(new Date(2026, 2, 15, 18, 22, 9, 321));
      expect(normalized.getHours()).toBe(0);
      expect(normalized.getMinutes()).toBe(0);
      expect(normalized.getSeconds()).toBe(0);
      expect(normalized.getMilliseconds()).toBe(0);
    });
  });

  describe('isValidDate', () => {
    it('returns true for a valid Date and false otherwise', () => {
      expect(isValidDate(new Date())).toBe(true);
      expect(isValidDate(new Date('invalid'))).toBe(false);
      expect(isValidDate('2026-03-15')).toBe(false);
      expect(isValidDate(null)).toBe(false);
    });
  });

  describe('dateOnlyToMs', () => {
    it('returns local start-of-day ms for ISO date-only strings', () => {
      const expected = new Date(2026, 2, 15).getTime();
      expect(dateOnlyToMs('2026-03-15')).toBe(expected);
    });

    it('returns NaN for unparseable input', () => {
      expect(dateOnlyToMs('invalid')).toBeNaN();
      expect(dateOnlyToMs(null)).toBeNaN();
    });

    it('differs from Date.parse when UTC midnight falls on the previous local day', () => {
      const localMs = dateOnlyToMs('2026-03-15');
      const utcParsed = Date.parse('2026-03-15');
      expect(new Date(localMs).getDate()).toBe(15);
      if (utcParsed !== localMs) {
        expect(new Date(utcParsed).getDate()).not.toBe(15);
      }
    });
  });

  describe('formatDateOnlyLocale', () => {
    it('formats ISO date-only using the calendar day, not UTC midnight', () => {
      const formatted = formatDateOnlyLocale('2026-03-15', 'pt-BR');
      expect(formatted).toMatch(/15/);
      expect(formatted).not.toMatch(/14\/03\/2026|14 de mar/);
    });

    it('returns empty string for nullish input', () => {
      expect(formatDateOnlyLocale(null)).toBe('');
      expect(formatDateOnlyLocale(undefined)).toBe('');
    });
  });
});
