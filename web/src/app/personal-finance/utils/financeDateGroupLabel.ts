export type FinanceDateParts = {
  year: number;
  month: number;
  day: number;
  dateKey: string;
  sortKey: number;
};

export function parseFinanceDateKey(value: string): FinanceDateParts | null {
  const dateKey = (value ?? "").split("T")[0] ?? "";
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dateKey);
  if (!match) return null;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  if (!year || !month || !day) return null;
  return { year, month, day, dateKey, sortKey: year * 10000 + month * 100 + day };
}

/** Same semantics as finance TransactionTable date group headers (today / tomorrow / long). */
export function formatFinanceDateGroupLabel(
  dateKey: string,
  t: (key: string, options?: { date?: string }) => string,
  language: string,
): string {
  if (!dateKey) return "";

  const parts = dateKey.split("-").map(Number);
  if (parts.length < 3) return dateKey;

  const year = parts[0];
  const month = parts[1];
  const day = parts[2];
  if (year === undefined || month === undefined || day === undefined) return dateKey;

  const date = new Date(year, month - 1, day);

  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const tomorrow = new Date(today);
  tomorrow.setDate(tomorrow.getDate() + 1);

  const isToday = date.getTime() === today.getTime();
  const isTomorrow = date.getTime() === tomorrow.getTime();

  const dateHeaderFormatter = new Intl.DateTimeFormat(language, {
    weekday: "long",
    day: "numeric",
    month: "long",
  });
  const formattedDate = dateHeaderFormatter.format(date);

  if (isToday) {
    return t("financeTransactions.dateGroup.today", { date: formattedDate });
  }
  if (isTomorrow) {
    return t("financeTransactions.dateGroup.tomorrow", { date: formattedDate });
  }
  return formattedDate;
}
