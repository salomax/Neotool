import dayjs from "dayjs";

export const FINANCE_MOVING_WINDOW_YEARS = 5;

export function getFinanceMinAllowedDate() {
  return dayjs().subtract(FINANCE_MOVING_WINDOW_YEARS, "year").startOf("day");
}
