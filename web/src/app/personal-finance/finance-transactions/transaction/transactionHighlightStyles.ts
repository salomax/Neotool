import { alpha } from '@mui/material';
import { keyframes, type Theme } from '@mui/material/styles';

const HIGHLIGHT_DURATION_MS = 4000;

/**
 * Row-level outline fade — one frame around the whole row.
 * Prefer outline over box-shadow: inset box-shadow on `display: table-row` is
 * unreliable across browsers and often paints nothing.
 */
const tableRowOutlineHighlightFade = keyframes`
  0% {
    outline-color: var(--tx-highlight-border-start);
  }
  100% {
    outline-color: transparent;
  }
`;

/** Cell background fade only — no border (avoids per-column highlight rings). */
const tableCellBgHighlightFade = keyframes`
  0% {
    background-color: var(--tx-highlight-bg-start);
  }
  100% {
    background-color: var(--tx-highlight-bg-end);
  }
`;

const mobileRowHighlightFade = keyframes`
  0% {
    background-color: var(--tx-highlight-bg-start);
    box-shadow: inset 0 0 0 1px var(--tx-highlight-border-start);
  }
  100% {
    background-color: var(--tx-highlight-bg-end);
    box-shadow: inset 0 0 0 1px transparent;
  }
`;

export function getTransactionTableHighlightSx(theme: Theme, isActive: boolean) {
  if (!isActive) return {};

  return {
    '--tx-highlight-bg-start': alpha(theme.palette.primary.main, 0.12),
    '--tx-highlight-bg-end': theme.palette.background.paper,
    '--tx-highlight-border-start': alpha(theme.palette.primary.main, 0.28),
    outline: '1px solid var(--tx-highlight-border-start)',
    outlineOffset: '-1px',
    animation: `${tableRowOutlineHighlightFade} ${HIGHLIGHT_DURATION_MS}ms ease-out forwards`,
    '& > .MuiTableCell-root': {
      animation: `${tableCellBgHighlightFade} ${HIGHLIGHT_DURATION_MS}ms ease-out forwards`,
    },
  };
}

export function getTransactionMobileHighlightSx(theme: Theme, isActive: boolean) {
  if (!isActive) return {};

  return {
    '--tx-highlight-bg-start': alpha(theme.palette.primary.main, 0.12),
    '--tx-highlight-bg-end': theme.palette.background.paper,
    '--tx-highlight-border-start': alpha(theme.palette.primary.main, 0.28),
    animation: `${mobileRowHighlightFade} ${HIGHLIGHT_DURATION_MS}ms ease-out forwards`,
  };
}

export { HIGHLIGHT_DURATION_MS };
