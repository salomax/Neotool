"use client";

import * as React from "react";
import CheckCircleOutlineIcon from "@mui/icons-material/CheckCircleOutline";
import AccessTimeIcon from "@mui/icons-material/AccessTime";
import WarningAmberIcon from "@mui/icons-material/WarningAmber";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import ExpandLessIcon from "@mui/icons-material/ExpandLess";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import EditIcon from "@mui/icons-material/Edit";
import MoreVertIcon from "@mui/icons-material/MoreVert";
import {
  alpha,
  Box,
  Chip,
  IconButton,
  Link,
  ListItemIcon,
  ListItemText,
  Menu,
  MenuItem,
  Stack,
  Typography,
  useTheme,
} from "@mui/material";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../../i18n";
import type { FixedIncomeHoldingDetailQuery } from "./api/queries.generated";
import { ConfirmationDialog } from "@/shared/components/ui/feedback";

type CouponScheduleEntry = NonNullable<FixedIncomeHoldingDetailQuery["investmentHolding"]>["couponSchedule"][number];

interface CouponScheduleTimelineProps {
  entries: CouponScheduleEntry[];
  locale?: string;
  currencyCode?: string | null;
  onRegisterOverdue?: (entry: CouponScheduleEntry) => void;
  onEditPayment?: (entry: CouponScheduleEntry) => void;
  onDeletePayment?: (transactionId: string) => Promise<void>;
  deletingPayment?: boolean;
}

function formatDate(value: unknown, locale: string): string {
  const raw = String(value);
  const ymd = raw.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (ymd) {
    const year = Number(ymd[1]);
    const month = Number(ymd[2]);
    const day = Number(ymd[3]);
    const localDate = new Date(year, month - 1, day);
    if (Number.isNaN(localDate.getTime())) return "—";
    return localDate.toLocaleDateString(locale);
  }

  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleDateString(locale);
}

function getSortKey(value: unknown): number {
  const raw = String(value);
  const ymd = raw.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (ymd) {
    return Number(`${ymd[1]}${ymd[2]}${ymd[3]}`);
  }
  const date = new Date(raw);
  return Number.isNaN(date.getTime()) ? 0 : date.getTime();
}

export function CouponScheduleTimeline({
  entries,
  locale: localeProp,
  currencyCode,
  onRegisterOverdue,
  onEditPayment,
  onDeletePayment,
  deletingPayment = false,
}: CouponScheduleTimelineProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const theme = useTheme();
  const locale = React.useMemo(
    () => localeProp || Intl.DateTimeFormat().resolvedOptions().locale || "pt-BR",
    [localeProp],
  );
  const [showMore, setShowMore] = React.useState(false);
  const [menuAnchor, setMenuAnchor] = React.useState<HTMLElement | null>(null);
  const [menuEntry, setMenuEntry] = React.useState<CouponScheduleEntry | null>(null);
  const [deleteEntry, setDeleteEntry] = React.useState<CouponScheduleEntry | null>(null);
  const [deleting, setDeleting] = React.useState(false);
  const currencyFormatter = React.useMemo(() => {
    if (!currencyCode) return null;
    return new Intl.NumberFormat(locale, {
      style: "currency",
      currency: currencyCode,
    });
  }, [currencyCode, locale]);

  const sortedEntries = React.useMemo(
    () => [...entries].sort((a, b) => getSortKey(a.scheduledDate) - getSortKey(b.scheduledDate)),
    [entries],
  );

  const visibleEntries = React.useMemo(() => {
    if (showMore) return sortedEntries;
    const firstPendingIndex = sortedEntries.findIndex((entry) => !entry.matchedTransaction);
    if (firstPendingIndex === -1) return sortedEntries;
    return sortedEntries.slice(0, Math.max(1, firstPendingIndex + 1));
  }, [showMore, sortedEntries]);

  const hasMore = visibleEntries.length < sortedEntries.length;

  const renderRow = (entry: CouponScheduleEntry) => {
    const isPaid = Boolean(entry.matchedTransaction);
    const isOverdue = entry.isPast && !entry.matchedTransaction;
    const statusLabel = isPaid
      ? t("investments.detailDrawer.couponSchedule.statusPaid")
      : isOverdue
        ? t("investments.detailDrawer.couponSchedule.statusOverdue")
        : t("investments.detailDrawer.couponSchedule.statusExpected");
    const statusBaseColor = isPaid
      ? theme.palette.success.main
      : isOverdue
        ? theme.palette.warning.main
        : theme.palette.text.secondary;
    const statusIcon = isPaid ? <CheckCircleOutlineIcon fontSize="small" /> : isOverdue ? <WarningAmberIcon fontSize="small" /> : <AccessTimeIcon fontSize="small" />;
    const paidNetAmount = entry.matchedTransaction?.netValue ?? entry.matchedTransaction?.grossValue ?? null;

    return (
      <Box
        key={`${entry.scheduledDate}-${entry.periodStart}`}
        sx={{
          display: "grid",
          gridTemplateColumns: "1fr auto 1fr",
          alignItems: "center",
          gap: 1,
          py: 1,
          borderBottom: "1px solid",
          borderColor: "divider",
        }}
      >
        <Typography variant="body2">{formatDate(entry.scheduledDate, locale)}</Typography>
        <Box sx={{ display: "flex", justifyContent: "center", justifySelf: "center" }}>
          <Chip
            size="small"
            icon={statusIcon}
            label={statusLabel}
            sx={{
              color: statusBaseColor,
              bgcolor: alpha(statusBaseColor, 0.03),
              borderColor: alpha(statusBaseColor, 0.12),
              borderWidth: 1,
              borderStyle: "solid",
              minWidth: 128,
              "& .MuiChip-icon": {
                color: statusBaseColor,
              },
            }}
          />
        </Box>
        {entry.matchedTransaction ? (
          <Box sx={{ display: "flex", alignItems: "center", justifySelf: "end", gap: 0.5 }}>
            <Typography variant="body2">
              {paidNetAmount != null
                ? currencyFormatter?.format(paidNetAmount) ?? String(paidNetAmount)
                : "—"}
            </Typography>
            <IconButton
              size="small"
              aria-label={t("investments.table.rowActions.ariaLabel")}
              onClick={(e) => {
                e.stopPropagation();
                setMenuAnchor(e.currentTarget);
                setMenuEntry(entry);
              }}
              disabled={deletingPayment || deleting}
            >
              <MoreVertIcon fontSize="small" />
            </IconButton>
          </Box>
        ) : isOverdue ? (
          <Link
            component="button"
            type="button"
            underline="hover"
            variant="body2"
            onClick={() => onRegisterOverdue?.(entry)}
            sx={{ justifySelf: "end" }}
          >
            {t("investments.detailDrawer.couponSchedule.register")}
          </Link>
        ) : (
          <Typography variant="body2" color="text.secondary" sx={{ justifySelf: "end" }}>
            —
          </Typography>
        )}
      </Box>
    );
  };

  const closeMenu = () => {
    setMenuAnchor(null);
    setMenuEntry(null);
  };

  const confirmDelete = async () => {
    const txId = deleteEntry?.matchedTransaction?.id ?? null;
    if (!txId || !onDeletePayment) return;
    setDeleting(true);
    try {
      await onDeletePayment(txId);
      setDeleteEntry(null);
    } finally {
      setDeleting(false);
    }
  };

  return (
    <>
      <Stack spacing={1}>
        {visibleEntries.map(renderRow)}

        {hasMore || showMore ? (
          <Box sx={{ display: "flex", justifyContent: "center", width: "100%" }}>
            <Box
              component="button"
              type="button"
              onClick={() => setShowMore((current) => !current)}
              style={{ background: "none", border: 0, padding: 0, cursor: "pointer" }}
            >
              <Stack direction="row" spacing={0.5} alignItems="center">
                {showMore ? <ExpandLessIcon fontSize="small" /> : <ExpandMoreIcon fontSize="small" />}
                <Typography variant="body2">
                  {showMore
                    ? t("investments.detailDrawer.couponSchedule.showLess")
                    : t("investments.detailDrawer.couponSchedule.showMore")}
                </Typography>
              </Stack>
            </Box>
          </Box>
        ) : null}
      </Stack>

      <Menu
        open={Boolean(menuAnchor)}
        anchorEl={menuAnchor}
        onClose={closeMenu}
        anchorOrigin={{ vertical: "bottom", horizontal: "right" }}
        transformOrigin={{ vertical: "top", horizontal: "right" }}
      >
        <MenuItem
          disabled={deletingPayment || deleting || !menuEntry?.matchedTransaction}
          onClick={() => {
            const entry = menuEntry;
            closeMenu();
            if (!entry?.matchedTransaction) return;
            onEditPayment?.(entry);
          }}
        >
          <ListItemIcon>
            <EditIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t("investments.table.rowActions.edit")}</ListItemText>
        </MenuItem>
        <MenuItem
          disabled={deletingPayment || deleting || !menuEntry?.matchedTransaction}
          onClick={() => {
            const entry = menuEntry;
            closeMenu();
            if (!entry?.matchedTransaction) return;
            setDeleteEntry(entry);
          }}
          sx={{
            color: "error.main",
            "&:hover": {
              bgcolor: alpha(theme.palette.error.main, 0.08),
            },
            "&.Mui-focusVisible": {
              bgcolor: alpha(theme.palette.error.main, 0.08),
            },
          }}
        >
          <ListItemIcon sx={{ color: "inherit" }}>
            <DeleteOutlineIcon fontSize="small" />
          </ListItemIcon>
          <ListItemText>{t("investments.table.rowActions.remove")}</ListItemText>
        </MenuItem>
      </Menu>

      <ConfirmationDialog
        open={Boolean(deleteEntry)}
        onClose={() => setDeleteEntry(null)}
        onConfirm={confirmDelete}
        title={t("investments.detailDrawer.couponSchedule.deleteConfirmation.title")}
        message={t("investments.detailDrawer.couponSchedule.deleteConfirmation.message")}
        cancelText={t("actions.cancel")}
        confirmText={t("actions.delete")}
        confirmColor="error"
        loading={deletingPayment || deleting}
      />
    </>
  );
}

export default CouponScheduleTimeline;
