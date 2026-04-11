import type { HoldingPerformanceChartPoint } from "./HoldingPerformanceChart";

function parsePointDate(raw: string): Date {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(raw));
  if (m) {
    const y = Number(m[1]);
    const mo = Number(m[2]);
    const d = Number(m[3]);
    return new Date(y, mo - 1, d);
  }
  return new Date(raw);
}

/** Marks the last non-projected point when it falls in the current calendar month. */
export function withPartialMonthFlags(points: HoldingPerformanceChartPoint[]): HoldingPerformanceChartPoint[] {
  const out: HoldingPerformanceChartPoint[] = points.map((p) => ({
    ...p,
    isPartialMonth: false,
  }));
  let lastNonProjected = -1;
  for (let i = out.length - 1; i >= 0; i--) {
    const row = out[i];
    if (row && !row.isProjected) {
      lastNonProjected = i;
      break;
    }
  }
  if (lastNonProjected < 0) return out;
  const target = out[lastNonProjected];
  if (!target) return out;
  const now = new Date();
  const d = parsePointDate(target.timestamp);
  if (Number.isNaN(d.getTime())) return out;
  if (d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth()) {
    out[lastNonProjected] = { ...target, isPartialMonth: true };
  }
  return out;
}
