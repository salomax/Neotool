import type { Theme } from "@mui/material/styles";

export function getTableRadius(theme: Theme): number {
  const tableRadius = (theme as Theme & { custom?: { radius?: { table?: number | string } } }).custom?.radius?.table;
  if (typeof tableRadius === "number" && Number.isFinite(tableRadius)) return tableRadius;
  if (typeof tableRadius === "string") {
    const parsed = Number.parseFloat(tableRadius);
    if (Number.isFinite(parsed)) return parsed;
  }
  const fallback = theme.shape.borderRadius;
  if (typeof fallback === "number" && Number.isFinite(fallback)) return fallback;
  const parsedFallback = Number.parseFloat(String(fallback));
  return Number.isFinite(parsedFallback) ? parsedFallback : 0;
}
