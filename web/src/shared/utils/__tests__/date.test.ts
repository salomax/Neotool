import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  formatDate,
  formatTableDate,
  formatDateTime,
  formatRelativeDate,
  getCurrentLocale,
} from '../date';

describe('date utilities', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe('formatDate', () => {
    it('should return "-" for null input', () => {
      expect(formatDate(null)).toBe('-');
    });

    it('should return "-" for undefined input', () => {
      expect(formatDate(undefined)).toBe('-');
    });

    it('should return "-" for invalid date string', () => {
      expect(formatDate('invalid-date')).toBe('-');
    });

    it('should return "-" for invalid Date object', () => {
      const invalidDate = new Date('invalid');
      expect(formatDate(invalidDate)).toBe('-');
    });

    it('should format date with default options', () => {
      const date = new Date('2024-01-15T10:30:00Z');
      const result = formatDate(date);
      expect(result).toBeTruthy();
      expect(result).not.toBe('-');
    });

    it('should format date string', () => {
      const dateString = '2024-01-15T10:30:00Z';
      const result = formatDate(dateString);
      expect(result).toBeTruthy();
      expect(result).not.toBe('-');
    });

    it('should format date with custom locale', () => {
      const date = new Date('2024-01-15T10:30:00Z');
      const result = formatDate(date, { locale: 'pt-BR' });
      expect(result).toBeTruthy();
    });

    it('should format date with custom timezone', () => {
      const date = new Date('2024-01-15T10:30:00Z');
      const result = formatDate(date, { timeZone: 'America/New_York' });
      expect(result).toBeTruthy();
    });

    it('should format date only when showTime is false', () => {
      const date = new Date('2024-01-15T10:30:00Z');
      const result = formatDate(date, { showDate: true, showTime: false });
      expect(result).toBeTruthy();
    });

    it('should format time only when showDate is false', () => {
      const date = new Date('2024-01-15T10:30:00Z');
      const result = formatDate(date, { showDate: false, showTime: true });
      expect(result).toBeTruthy();
    });

    it('should format date and time together', () => {
      const date = new Date('2024-01-15T10:30:00Z');
      const result = formatDate(date, { showDate: true, showTime: true });
      expect(result).toBeTruthy();
    });

    it('should include seconds when showSeconds is true', () => {
      const date = new Date('2024-01-15T10:30:45Z');
      const result = formatDate(date, {
        showTime: true,
        showSeconds: true,
      });
      expect(result).toBeTruthy();
    });

    it('should use 12-hour format when use24Hour is false', () => {
      const date = new Date('2024-01-15T14:30:00Z');
      const result = formatDate(date, {
        showTime: true,
        use24Hour: false,
      });
      expect(result).toBeTruthy();
    });

    it('should use custom dateStyle', () => {
      const date = new Date('2024-01-15T10:30:00Z');
      const result = formatDate(date, { dateStyle: 'full' });
      expect(result).toBeTruthy();
    });

    it('should return empty string when neither date nor time is shown', () => {
      const date = new Date('2024-01-15T10:30:00Z');
      const result = formatDate(date, { showDate: false, showTime: false });
      expect(result).toBe('');
    });

    it('should handle formatting errors gracefully', () => {
      const consoleSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
      const date = new Date('2024-01-15T10:30:00Z');
      
      // Mock Intl.DateTimeFormat to throw
      const originalIntl = global.Intl;
      global.Intl = {
        ...originalIntl,
        DateTimeFormat: vi.fn(() => {
          throw new Error('Format error');
        }),
      } as any;

      const result = formatDate(date);
      expect(result).toBe('-');
      expect(consoleSpy).toHaveBeenCalled();

      global.Intl = originalIntl;
      consoleSpy.mockRestore();
    });
  });

  describe('formatTableDate', () => {
    it('should format date for table display', () => {
      const date = new Date('2024-01-15T10:30:00Z');
      const result = formatTableDate(date);
      expect(result).toBeTruthy();
      expect(result).not.toBe('-');
    });

    it('should use default locale', () => {
      const date = new Date('2024-01-15T10:30:00Z');
      const result = formatTableDate(date);
      expect(result).toBeTruthy();
    });

    it('should use custom locale', () => {
      const date = new Date('2024-01-15T10:30:00Z');
      const result = formatTableDate(date, 'pt-BR');
      expect(result).toBeTruthy();
    });

    it('should return "-" for invalid date', () => {
      expect(formatTableDate('invalid')).toBe('-');
    });
  });

  describe('formatDateTime', () => {
    it('should format date with time', () => {
      const date = new Date('2024-01-15T10:30:00Z');
      const result = formatDateTime(date);
      expect(result).toBeTruthy();
      expect(result).not.toBe('-');
    });

    it('should use default locale and timezone', () => {
      const date = new Date('2024-01-15T10:30:00Z');
      const result = formatDateTime(date);
      expect(result).toBeTruthy();
    });

    it('should use custom locale and timezone', () => {
      const date = new Date('2024-01-15T10:30:00Z');
      const result = formatDateTime(date, 'pt-BR', 'America/Sao_Paulo');
      expect(result).toBeTruthy();
    });

    it('should return "-" for invalid date', () => {
      expect(formatDateTime('invalid')).toBe('-');
    });
  });

  describe('formatRelativeDate', () => {
    it('should return "-" for null input', () => {
      expect(formatRelativeDate(null)).toBe('-');
    });

    it('should return "-" for undefined input', () => {
      expect(formatRelativeDate(undefined)).toBe('-');
    });

    it('should return "-" for invalid date', () => {
      expect(formatRelativeDate('invalid')).toBe('-');
    });

    it('should format relative date in minutes', () => {
      const now = new Date('2024-01-15T10:30:00Z');
      vi.setSystemTime(now);
      const date = new Date('2024-01-15T10:29:00Z'); // 1 minute ago
      const result = formatRelativeDate(date);
      expect(result).toBeTruthy();
    });

    it('should format relative date in hours', () => {
      const now = new Date('2024-01-15T10:30:00Z');
      vi.setSystemTime(now);
      const date = new Date('2024-01-15T09:30:00Z'); // 1 hour ago
      const result = formatRelativeDate(date);
      expect(result).toBeTruthy();
    });

    it('should format relative date in days', () => {
      const now = new Date('2024-01-15T10:30:00Z');
      vi.setSystemTime(now);
      const date = new Date('2024-01-14T10:30:00Z'); // 1 day ago
      const result = formatRelativeDate(date);
      expect(result).toBeTruthy();
    });

    it('should handle future dates', () => {
      const now = new Date('2024-01-15T10:30:00Z');
      vi.setSystemTime(now);
      const date = new Date('2024-01-16T10:30:00Z'); // 1 day in future
      const result = formatRelativeDate(date);
      expect(result).toBeTruthy();
    });

    it('should use custom locale', () => {
      const now = new Date('2024-01-15T10:30:00Z');
      vi.setSystemTime(now);
      const date = new Date('2024-01-14T10:30:00Z');
      const result = formatRelativeDate(date, 'pt-BR');
      expect(result).toBeTruthy();
    });

    it('should fallback to formatTableDate on error', () => {
      const consoleSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
      const date = new Date('2024-01-15T10:30:00Z');
      
      // Mock Intl.RelativeTimeFormat to throw
      const originalIntl = global.Intl;
      global.Intl = {
        ...originalIntl,
        RelativeTimeFormat: vi.fn(() => {
          throw new Error('Format error');
        }),
      } as any;

      const result = formatRelativeDate(date);
      expect(result).toBeTruthy();
      expect(consoleSpy).toHaveBeenCalled();

      global.Intl = originalIntl;
      consoleSpy.mockRestore();
    });
  });

  describe('getCurrentLocale', () => {
    it('should return browser locale when window is available', () => {
      Object.defineProperty(window, 'navigator', {
        value: {
          language: 'pt-BR',
        },
        writable: true,
      });
      expect(getCurrentLocale()).toBe('pt-BR');
    });

    it('should return default locale when navigator.language is not available', () => {
      Object.defineProperty(window, 'navigator', {
        value: {},
        writable: true,
      });
      expect(getCurrentLocale()).toBe('en-US');
    });

    it('should return default locale when window is undefined', () => {
      const originalWindow = global.window;
      // @ts-ignore
      delete global.window;
      expect(getCurrentLocale()).toBe('en-US');
      global.window = originalWindow;
    });
  });
});

