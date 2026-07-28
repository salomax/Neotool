import { describe, it, expect } from 'vitest';
import { createTheme } from '@mui/material';
import { getTransactionTableHighlightSx } from '../transactionHighlightStyles';

const theme = createTheme();

function collectCssMentions(value: unknown, needles: string[], path = ''): string[] {
  if (value == null) return [];
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    const text = String(value);
    return needles.some((n) => text.includes(n)) ? [`${path}:${text}`] : [];
  }
  if (typeof value === 'object' && 'name' in (value as object) && 'styles' in (value as object)) {
    const styles = String((value as { styles?: string }).styles ?? '');
    return needles.some((n) => styles.includes(n)) ? [`${path}:keyframes`] : [];
  }
  if (Array.isArray(value)) {
    return value.flatMap((item, i) => collectCssMentions(item, needles, `${path}[${i}]`));
  }
  if (typeof value === 'object') {
    return Object.entries(value as Record<string, unknown>).flatMap(([key, child]) =>
      collectCssMentions(child, needles, path ? `${path}.${key}` : key),
    );
  }
  return [];
}

describe('getTransactionTableHighlightSx', () => {
  it('returns empty sx when inactive', () => {
    expect(getTransactionTableHighlightSx(theme, false)).toEqual({});
  });

  it('puts a single outline frame on the row, not inset borders on every cell', () => {
    const sx = getTransactionTableHighlightSx(theme, true) as Record<string, unknown>;

    // One bordered element: the row itself via outline (not tr box-shadow).
    expect(sx.outline).toEqual(expect.stringContaining('var(--tx-highlight-border-start)'));
    expect(sx.outlineOffset).toBe('-1px');
    expect(sx.animation).toBeTruthy();
    expect(collectCssMentions(sx.animation, ['outline-color']).length).toBeGreaterThan(0);

    // Must not use the old broad per-cell border selector.
    expect(sx).not.toHaveProperty('& .MuiTableCell-root');
    expect(sx).toHaveProperty('& > .MuiTableCell-root');

    const cellSx = sx['& > .MuiTableCell-root'];
    expect(cellSx).toBeTruthy();
    // Cells may fade background only — no inset border / box-shadow.
    expect(collectCssMentions(cellSx, ['box-shadow', 'boxShadow', 'outline'])).toEqual([]);
  });
});
