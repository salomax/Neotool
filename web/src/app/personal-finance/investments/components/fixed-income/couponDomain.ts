export function estimateCouponGrossForPeriod(
  principal: number,
  fixedAnnualRate: number | null | undefined,
  periodStart: string | null,
  couponDate: string | null,
): number | null {
  if (principal <= 0 || !fixedAnnualRate || fixedAnnualRate <= 0) return null;
  if (!periodStart || !couponDate) return null;

  const startMs = new Date(periodStart).getTime();
  const endMs = new Date(couponDate).getTime();
  const calendarDays = Math.max(0, Math.round((endMs - startMs) / 86_400_000));
  if (calendarDays <= 0) return null;

  const approxBusinessDays = (calendarDays * 252) / 365;
  const grossAtDate = principal * Math.pow(1 + fixedAnnualRate, approxBusinessDays / 252);
  const interest = Number((grossAtDate - principal).toFixed(2));
  return interest > 0 ? interest : null;
}
