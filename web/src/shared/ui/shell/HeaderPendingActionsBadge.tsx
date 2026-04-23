"use client";

import { useRouter } from "next/navigation";
import { useQuery } from "@apollo/client/react";
import Badge from "@mui/material/Badge";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import Tooltip from "@mui/material/Tooltip";
import NotificationsOutlinedIcon from "@mui/icons-material/NotificationsOutlined";
import { useTranslation } from "react-i18next";
import { PendingActionsCountDocument } from "@/lib/graphql/operations/banking/pendingActionOperations.generated";
import type { PendingActionsCountQuery } from "@/lib/graphql/operations/banking/pendingActionOperations.generated";
import { PendingActionSeverity, PendingActionStatus } from "@/lib/graphql/types/__generated__/graphql";
import { useAuth } from "@/shared/providers";

type BadgeColor = "error" | "warning" | "info" | "default";

function badgeColorFromActions(actions: PendingActionsCountQuery["pendingActions"]): BadgeColor {
  if (!actions.length) return "default";
  let hasWarning = false;
  for (const row of actions) {
    if (row.severity === PendingActionSeverity.ActionRequired) {
      return "error";
    }
    if (row.severity === PendingActionSeverity.Warning) {
      hasWarning = true;
    }
  }
  if (hasWarning) return "warning";
  return "info";
}

/** Pending actions for the authenticated workspace account (`current_account` in JWT); no client account id. */
export function HeaderPendingActionsBadge() {
  const { t } = useTranslation();
  const router = useRouter();
  const { isAuthenticated } = useAuth();

  const { data } = useQuery<PendingActionsCountQuery>(PendingActionsCountDocument, {
    variables: { status: PendingActionStatus.Pending },
    skip: !isAuthenticated,
    fetchPolicy: "cache-and-network",
    errorPolicy: "all",
  });

  const actions = data?.pendingActions ?? [];
  const total = actions.length;
  const badgeColor = badgeColorFromActions(actions);

  if (!isAuthenticated) {
    return null;
  }

  return (
    <Box sx={{ display: { xs: "none", md: "inline-flex" } }}>
      <Tooltip title={t("appHeader.pendingActions")}>
        <IconButton
          aria-label={t("appHeader.pendingActions")}
          onClick={() => router.push("/personal-finance/investments?pendingActions=1")}
          data-testid="header-pending-actions-button"
        >
          <Badge
            badgeContent={total}
            color={badgeColor}
            max={99}
            invisible={total === 0}
            data-testid="header-pending-actions-badge"
          >
            <NotificationsOutlinedIcon />
          </Badge>
        </IconButton>
      </Tooltip>
    </Box>
  );
}
