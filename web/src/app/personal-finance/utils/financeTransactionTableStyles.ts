import type { SxProps, Theme } from "@mui/material/styles";
import { FINANCE_TABLE_TYPOGRAPHY_SX } from "@/shared/components/ui/data-display/Table";

/** Same literal as legacy TransactionTable row grouping (do not diverge). */
export const FINANCE_TRANSACTION_DATA_ROW_CORNER_RADIUS_PX = 12;

const dataRowCornerRadius = `${FINANCE_TRANSACTION_DATA_ROW_CORNER_RADIUS_PX}px`;

export function getFinanceTransactionTableSx(minWidth = 650): SxProps<Theme> {
  return {
    minWidth,
    borderCollapse: "separate",
    borderSpacing: 0,
    ...FINANCE_TABLE_TYPOGRAPHY_SX,
  };
}

/** Per header cell: single bottom divider only; no fill or side grid (matches TransactionTable). */
export function financeTransactionHeadCellSx(theme: Theme): SxProps<Theme> {
  return {
    py: 1,
    borderTop: "none",
    borderLeft: "none",
    borderRight: "none",
    borderBottom: `1px solid ${theme.palette.divider}`,
    bgcolor: "transparent",
    backgroundColor: "transparent",
    // Head cells use variant="head" (.MuiTableCell-head); override theme defaults.
    "&.MuiTableCell-head": {
      bgcolor: "transparent",
      backgroundColor: "transparent",
    },
  };
}

/** TableHead: transparent section + all head cells (root + .MuiTableCell-head). */
export const financeTransactionTableHeadSx: SxProps<Theme> = {
  bgcolor: "transparent",
  backgroundColor: "transparent",
  "& .MuiTableCell-root": {
    bgcolor: "transparent",
    backgroundColor: "transparent",
  },
  "& .MuiTableCell-head": {
    bgcolor: "transparent",
    backgroundColor: "transparent",
  },
};

/** Single header row inside TableHead (TransactionTable had no row fill). */
export const financeTransactionTableHeadRowSx: SxProps<Theme> = {
  bgcolor: "transparent",
  backgroundColor: "transparent",
};

export const financeTransactionTableBodySx: SxProps<Theme> = {
  bgcolor: "transparent",
  backgroundColor: "transparent",
};

/** Date group: transparent; strip MUI default cell bottom border on the spacer row. */
export const financeTransactionDateGroupTableRowSx: SxProps<Theme> = {
  bgcolor: "transparent",
  backgroundColor: "transparent",
  borderBottom: "none",
  "& .MuiTableCell-root": {
    bgcolor: "transparent",
    backgroundColor: "transparent",
    borderBottom: "none",
    borderTop: "none",
  },
};

export function financeTransactionDateGroupTableCellSx(): SxProps<Theme> {
  return {
    pt: 4,
    pb: 1,
    bgcolor: "transparent",
    backgroundColor: "transparent",
    borderBottom: "none",
    borderTop: "none",
  };
}

/**
 * Data rows only: `background.paper` on cells + rounded ends per date group
 * (selectors match TransactionTable: first/last `.MuiTableCell-root` in the row).
 */
export function financeTransactionBodyRowSx(
  _theme: Theme,
  opts: { isFirst: boolean; isLast: boolean },
): SxProps<Theme> {
  const { isFirst, isLast } = opts;
  return {
    bgcolor: "transparent",
    "&:last-child td, &:last-child th": { border: 0 },
    "& .MuiTableCell-root": {
      bgcolor: "background.paper",
      borderColor: "transparent",
    },
    "& > .MuiTableCell-root:first-of-type": {
      borderTopLeftRadius: isFirst ? dataRowCornerRadius : 0,
      borderBottomLeftRadius: isLast ? dataRowCornerRadius : 0,
    },
    "& > .MuiTableCell-root:last-of-type": {
      borderTopRightRadius: isFirst ? dataRowCornerRadius : 0,
      borderBottomRightRadius: isLast ? dataRowCornerRadius : 0,
    },
  };
}
