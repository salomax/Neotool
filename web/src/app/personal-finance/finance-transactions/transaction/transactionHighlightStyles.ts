import { alpha } from '@mui/material';
import { keyframes, type Theme } from '@mui/material/styles';

const HIGHLIGHT_DURATION_MS = 4000;

const tableRowHighlightFade = keyframes`
  0% {
    background-color: var(--tx-highlight-bg-start);
    box-shadow: inset 0 0 0 1px var(--tx-highlight-border-start);
  }
  100% {
    background-color: var(--tx-highlight-bg-end);
    box-shadow: inset 0 0 0 1px transparent;
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
    '& .MuiTableCell-root': {
      '--tx-highlight-bg-start': alpha(theme.palette.primary.main, 0.12),
      '--tx-highlight-bg-end': theme.palette.background.paper,
      '--tx-highlight-border-start': alpha(theme.palette.primary.main, 0.28),
      animation: `${tableRowHighlightFade} ${HIGHLIGHT_DURATION_MS}ms ease-out forwards`,
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
