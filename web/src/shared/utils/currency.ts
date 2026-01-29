/**
 * Currency parsing utilities for formatting large currency values
 */

export interface CurrencyParts {
  symbol: string;
  number: string;
  totalizer: string | null;
}

export interface CurrencyLabels {
  billions: string;
  millions: string;
  trillions?: string;
  billionsLong?: string;
  millionsLong?: string;
  trillionsLong?: string;
}

/**
 * Parses a currency value into its component parts (symbol, number, totalizer)
 * 
 * @param value - The numeric value to format
 * @param currency - ISO currency code (default: "BRL")
 * @param locale - Locale string (default: "pt-BR")
 * @param labels - Object with currency labels for short/long formats
 * @param totalizerFormat - Format for totalizer: "short" (bi/mi) or "long" (bilhões/milhões)
 * @returns Parsed currency parts
 */
export function parseCurrencyValue(
  value: number | null | undefined,
  currency: string = "BRL",
  locale: string = "pt-BR",
  labels?: CurrencyLabels,
  totalizerFormat: "short" | "long" = "short"
): CurrencyParts {
  if (value == null || value === 0) {
    const parts = new Intl.NumberFormat(locale, {
      style: "currency",
      currency,
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).formatToParts(1);
    const currencySymbol = parts.find((p) => p.type === "currency")?.value || "";
    return { symbol: currencySymbol, number: "0", totalizer: null };
  }

  const isNegative = value < 0;
  const absValue = Math.abs(value);
  const trillionsLabel = totalizerFormat === "long"
    ? (labels?.trillionsLong || "trilhões")
    : (labels?.trillions || "tri");
  const billionsLabel = totalizerFormat === "long" 
    ? (labels?.billionsLong || "bilhões")
    : (labels?.billions || "bi");
  const millionsLabel = totalizerFormat === "long"
    ? (labels?.millionsLong || "milhões")
    : (labels?.millions || "mi");

  const parts = new Intl.NumberFormat(locale, {
    style: "currency",
    currency,
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).formatToParts(1);
  const currencySymbol = parts.find((p) => p.type === "currency")?.value || "";

  const billions = absValue / 1_000_000_000;
  if (billions >= 1000) {
    // If billions >= 1000, convert to trillions
    const trillions = billions / 1000;
    // Format with one decimal place (e.g., 2.0)
    const numberStr = trillions.toLocaleString(locale, { 
      minimumFractionDigits: 1, 
      maximumFractionDigits: 1 
    });
    return { 
      symbol: currencySymbol, 
      number: isNegative ? `-${numberStr}` : numberStr, 
      totalizer: trillionsLabel 
    };
  }
  if (billions >= 1) {
    // Format with one decimal place (e.g., 87.4)
    const numberStr = billions.toLocaleString(locale, { 
      minimumFractionDigits: 1, 
      maximumFractionDigits: 1 
    });
    return { 
      symbol: currencySymbol, 
      number: isNegative ? `-${numberStr}` : numberStr, 
      totalizer: billionsLabel 
    };
  }

  const millions = absValue / 1_000_000;
  if (millions >= 1) {
    // Format with one decimal place
    const numberStr = millions.toLocaleString(locale, { 
      minimumFractionDigits: 1, 
      maximumFractionDigits: 1 
    });
    return { 
      symbol: currencySymbol, 
      number: isNegative ? `-${numberStr}` : numberStr, 
      totalizer: millionsLabel 
    };
  }

  // For values less than 1 million, extract number part from formatted currency
  const formattedParts = new Intl.NumberFormat(locale, {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).formatToParts(value);
  
  // Extract the numeric part (integer, group, decimal, fraction)
  const numberParts = formattedParts
    .filter(p => p.type === 'integer' || p.type === 'group' || p.type === 'decimal' || p.type === 'fraction')
    .map(p => p.value);
  const numberPart = numberParts.join('');
  
  return { 
    symbol: currencySymbol, 
    number: numberPart, 
    totalizer: null 
  };
}
