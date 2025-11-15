/**
 * Date formatting utilities using Intl.DateTimeFormat for consistent,
 * localized, and timezone-safe date presentation.
 */

export type DateFormatOptions = {
  /** The locale to use for formatting (e.g., 'en-US', 'pt-BR') */
  locale?: string;
  /** The timezone to use for formatting (e.g., 'UTC', 'America/New_York') */
  timeZone?: string;
  /** Whether to show the date component */
  showDate?: boolean;
  /** Whether to show the time component */
  showTime?: boolean;
  /** Whether to show seconds in time */
  showSeconds?: boolean;
  /** Whether to use 24-hour format */
  use24Hour?: boolean;
  /** Custom date style */
  dateStyle?: 'full' | 'long' | 'medium' | 'short';
  /** Custom time style */
  timeStyle?: 'full' | 'long' | 'medium' | 'short';
};

/**
 * Format a date string or Date object using Intl.DateTimeFormat
 * @param date - The date to format (string, Date, or null/undefined)
 * @param options - Formatting options
 * @returns Formatted date string or fallback text
 */
export function formatDate(
  date: string | Date | null | undefined,
  options: DateFormatOptions = {}
): string {
  if (!date) return '-';

  try {
    const {
      locale = 'en-US',
      timeZone = 'UTC',
      showDate = true,
      showTime = false,
      showSeconds = false,
      use24Hour = true,
      dateStyle = 'short',
    } = options;

    const dateObj = typeof date === 'string' ? new Date(date) : date;
    
    // Validate the date
    if (isNaN(dateObj.getTime())) {
      return '-';
    }

    // Build Intl.DateTimeFormat options
    const formatOptions: Intl.DateTimeFormatOptions = {
      timeZone,
    };

    if (showDate && showTime) {
      // Both date and time formatting - use separate styles
      formatOptions.dateStyle = dateStyle;
      formatOptions.timeStyle = 'short';
    } else if (showTime) {
      // Time-only formatting
      formatOptions.hour = '2-digit';
      formatOptions.minute = '2-digit';
      if (showSeconds) {
        formatOptions.second = '2-digit';
      }
      formatOptions.hour12 = !use24Hour;
    } else if (showDate) {
      // Date-only formatting
      formatOptions.dateStyle = dateStyle;
    } else {
      // Neither date nor time - return empty string
      return '';
    }

    const formatter = new Intl.DateTimeFormat(locale, formatOptions);
    return formatter.format(dateObj);
  } catch (error) {
    console.warn('Date formatting error:', error);
    return '-';
  }
}

/**
 * Format a date for display in tables (short format)
 * @param date - The date to format
 * @param locale - The locale to use
 * @returns Formatted date string
 */
export function formatTableDate(
  date: string | Date | null | undefined,
  locale: string = 'en-US'
): string {
  return formatDate(date, {
    locale,
    showDate: true,
    dateStyle: 'short',
    timeZone: 'UTC',
  });
}

/**
 * Format a date with time for display
 * @param date - The date to format
 * @param locale - The locale to use
 * @param timeZone - The timezone to use
 * @returns Formatted date and time string
 */
export function formatDateTime(
  date: string | Date | null | undefined,
  locale: string = 'en-US',
  timeZone: string = 'UTC'
): string {
  return formatDate(date, {
    locale,
    timeZone,
    showDate: true,
    showTime: true,
    showSeconds: false,
    use24Hour: true,
  });
}

/**
 * Format a date for relative display (e.g., "2 days ago")
 * @param date - The date to format
 * @param locale - The locale to use
 * @returns Relative date string
 */
export function formatRelativeDate(
  date: string | Date | null | undefined,
  locale: string = 'en-US'
): string {
  if (!date) return '-';

  try {
    const dateObj = typeof date === 'string' ? new Date(date) : date;
    
    if (isNaN(dateObj.getTime())) {
      return '-';
    }

    const formatter = new Intl.RelativeTimeFormat(locale, { numeric: 'auto' });
    const now = new Date();
    const diffInMs = dateObj.getTime() - now.getTime();
    const diffInDays = Math.round(diffInMs / (1000 * 60 * 60 * 24));

    if (Math.abs(diffInDays) < 1) {
      const diffInHours = Math.round(diffInMs / (1000 * 60 * 60));
      if (Math.abs(diffInHours) < 1) {
        const diffInMinutes = Math.round(diffInMs / (1000 * 60));
        return formatter.format(diffInMinutes, 'minute');
      }
      return formatter.format(diffInHours, 'hour');
    }

    return formatter.format(diffInDays, 'day');
  } catch (error) {
    console.warn('Relative date formatting error:', error);
    return formatTableDate(date, locale);
  }
}

/**
 * Get the current locale from the browser or i18n
 * @returns Current locale string
 */
export function getCurrentLocale(): string {
  if (typeof window !== 'undefined') {
    return window.navigator.language || 'en-US';
  }
  return 'en-US';
}
