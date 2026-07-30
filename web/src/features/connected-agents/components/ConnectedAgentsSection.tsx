"use client";

import * as React from "react";
import {
  Alert,
  Box,
  Chip,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from "@mui/material";
import type { Theme } from "@mui/material/styles";
import { useTheme } from "@mui/material/styles";
import { useMutation, useQuery } from "@apollo/client/react";
import {
  ConnectedAgentsDocument,
  type ConnectedAgentsQuery,
  type ConnectedAgentsQueryVariables,
  type RevokeConnectedAgentMutation,
  type RevokeConnectedAgentMutationVariables,
} from "@/lib/graphql/operations/auth/connectedAgents.generated";
import { ConnectedAgentActivityOutcome } from "@/lib/graphql/types/__generated__/graphql";
import { CONNECTED_AGENTS, REVOKE_CONNECTED_AGENT } from "@/lib/graphql/operations/auth/connectedAgents";
import { useMutationWithRefetch } from "@/shared/hooks/mutations";
import { useToast } from "@/shared/providers";
import { useTranslation } from "react-i18next";
import { extractErrorMessage } from "@/shared/utils/error";
import { Label } from "@/shared/components/ui/primitives";
import { Paper } from "@/shared/components/ui/layout";
import { Button } from "@/shared/components/ui/primitives/Button";
import { ConfirmDialog } from "@/shared/components/ui/feedback/dialogs";
import {
  financeTransactionBodyRowSx,
  financeTransactionHeadCellSx,
  financeTransactionTableBodySx,
  financeTransactionTableHeadRowSx,
  financeTransactionTableHeadSx,
  getFinanceTransactionTableSx,
} from "@/app/personal-finance/utils/financeTransactionTableStyles";

type ConnectedAgentsSectionProps = {
  accountId: string;
  recentActivityFirst?: number;
};

type ConnectedAgentRow = ConnectedAgentsQuery["connectedAgents"][number];

const EMPTY_CONNECTED_AGENTS: ConnectedAgentsQuery["connectedAgents"] = [];

const connectedAgentHeadCellSx = (theme: Theme) => ({
  ...financeTransactionHeadCellSx(theme),
  px: 2,
});

const connectedAgentBodyCellSx = {
  px: 2,
  py: 2,
  verticalAlign: "top",
};

function formatDateTime(value: unknown, locale: string): string {
  return new Intl.DateTimeFormat(locale, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(String(value)));
}

function activityOutcomeColor(outcome: ConnectedAgentActivityOutcome): "success" | "error" | "warning" {
  switch (outcome) {
    case ConnectedAgentActivityOutcome.Success:
      return "success";
    case ConnectedAgentActivityOutcome.Error:
      return "error";
    case ConnectedAgentActivityOutcome.Rejected:
      return "warning";
  }
}

function ActivitySummary({
  agent,
  locale,
}: {
  agent: ConnectedAgentRow;
  locale: string;
}) {
  const { t } = useTranslation("common");
  const latestActivity = agent.recentActivity[0] ?? null;

  if (!latestActivity) {
    return (
      <Typography variant="body2" color="text.secondary">
        {t("profile.connectedAgents.noRecentActivity")}
      </Typography>
    );
  }

  return (
    <Stack gap={0.75}>
      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
        <Typography variant="body2">
          {latestActivity.toolName || t("profile.connectedAgents.unknownTool")}
        </Typography>
        <Chip
          size="small"
          color={activityOutcomeColor(latestActivity.outcome)}
          label={t(`profile.connectedAgents.activityOutcome.${latestActivity.outcome}`)}
          sx={{ borderRadius: 999, fontWeight: 400 }}
        />
      </Stack>
      <Typography variant="caption" color="text.secondary">
        {formatDateTime(latestActivity.occurredAt, locale)}
      </Typography>
      {latestActivity.errorMessage ? (
        <Typography variant="caption" color="text.secondary">
          {latestActivity.errorMessage}
        </Typography>
      ) : null}
    </Stack>
  );
}

export function ConnectedAgentsSection({
  accountId,
  recentActivityFirst = 3,
}: ConnectedAgentsSectionProps) {
  const { t, i18n } = useTranslation("common");
  const theme = useTheme();
  const toast = useToast();
  const locale = i18n.language || Intl.DateTimeFormat().resolvedOptions().locale || "en-US";
  const [agentToRevoke, setAgentToRevoke] = React.useState<ConnectedAgentRow | null>(null);

  const {
    data,
    loading,
    error,
    refetch,
  } = useQuery<ConnectedAgentsQuery, ConnectedAgentsQueryVariables>(ConnectedAgentsDocument, {
    variables: { accountId, recentActivityFirst },
    skip: !accountId,
  });

  const connectedAgents = data?.connectedAgents ?? EMPTY_CONNECTED_AGENTS;

  const [revokeConnectedAgentMutation] = useMutation<
    RevokeConnectedAgentMutation,
    RevokeConnectedAgentMutationVariables
  >(REVOKE_CONNECTED_AGENT);

  const { executeMutation, isMutationInFlight } = useMutationWithRefetch<
    unknown,
    ConnectedAgentsQueryVariables
  >({
    refetchQuery: ConnectedAgentsDocument,
    refetchVariables: { accountId, recentActivityFirst },
    onRefetch: () => {
      void refetch();
    },
    errorMessage: t("profile.connectedAgents.actionError"),
  });

  const handleRevoke = async (connectionId: string) => {
    try {
      await executeMutation(
        revokeConnectedAgentMutation,
        { connectionId },
        `revoke-${connectionId}`,
      );
      toast.success(t("profile.connectedAgents.revokeSuccess"));
      setAgentToRevoke(null);
    } catch (err) {
      toast.error(extractErrorMessage(err, t("profile.connectedAgents.actionError")));
    }
  };

  return (
    <Stack gap={1}>
      <Label sx={{ mb: 0, pl: 2, fontSize: (currentTheme) => currentTheme.typography.subtitle1.fontSize }}>
        {t("profile.connectedAgents.title")}
      </Label>
      <Paper
        elevation={0}
        variant="outlined"
        name="profile-connected-agents"
        data-testid="connected-agents-section"
        sx={{ p: 0, overflow: "hidden" }}
      >
        {loading ? (
          <Box sx={{ px: { xs: 2, sm: 3 }, py: 2.5 }}>
            <Typography variant="body2" color="text.secondary">
              {t("profile.connectedAgents.loading")}
            </Typography>
          </Box>
        ) : error ? (
          <Box sx={{ px: { xs: 2, sm: 3 }, py: 2.5 }}>
            <Alert severity="error">
              {extractErrorMessage(error, t("profile.connectedAgents.loadError"))}
            </Alert>
          </Box>
        ) : connectedAgents.length === 0 ? (
          <Box sx={{ px: { xs: 2, sm: 3 }, py: 2.5 }}>
            <Typography variant="body2" color="text.secondary">
              {t("profile.connectedAgents.empty")}
            </Typography>
          </Box>
        ) : (
          <TableContainer sx={{ overflowX: "auto" }}>
            <Table
              sx={getFinanceTransactionTableSx(920)}
              aria-label={t("profile.connectedAgents.table.ariaLabel")}
            >
              <TableHead sx={financeTransactionTableHeadSx}>
                <TableRow sx={financeTransactionTableHeadRowSx}>
                  <TableCell sx={connectedAgentHeadCellSx(theme)}>
                    <Label sx={{ mb: 0 }}>{t("profile.connectedAgents.table.agent")}</Label>
                  </TableCell>
                  <TableCell sx={connectedAgentHeadCellSx(theme)}>
                    <Label sx={{ mb: 0 }}>{t("profile.connectedAgents.table.access")}</Label>
                  </TableCell>
                  <TableCell sx={connectedAgentHeadCellSx(theme)}>
                    <Label sx={{ mb: 0 }}>{t("profile.connectedAgents.table.recentActivity")}</Label>
                  </TableCell>
                  <TableCell align="right" sx={connectedAgentHeadCellSx(theme)}>
                    <Label sx={{ mb: 0 }}>{t("profile.connectedAgents.table.actions")}</Label>
                  </TableCell>
                </TableRow>
              </TableHead>
              <TableBody sx={financeTransactionTableBodySx}>
                {connectedAgents.map((agent, index) => {
                  const rowBusy = isMutationInFlight(`revoke-${agent.connectionId}`);

                  return (
                    <TableRow
                      key={agent.connectionId}
                      data-testid={`connected-agent-row-${agent.connectionId}`}
                      sx={financeTransactionBodyRowSx(theme, {
                        isFirst: index === 0,
                        isLast: index === connectedAgents.length - 1,
                      })}
                    >
                      <TableCell sx={connectedAgentBodyCellSx}>
                        <Stack gap={0.5} sx={{ minWidth: 220 }}>
                          <Typography variant="body2">{agent.clientName}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {agent.clientId}
                          </Typography>
                        </Stack>
                      </TableCell>
                      <TableCell sx={connectedAgentBodyCellSx}>
                        <Stack gap={1}>
                          <Typography variant="body2" color="text.secondary">
                            {t("profile.connectedAgents.connectedAtLabel", {
                              value: formatDateTime(agent.connectedAt, locale),
                            })}
                          </Typography>
                          <Typography variant="body2" color="text.secondary">
                            {t("profile.connectedAgents.lastGrantedAtLabel", {
                              value: formatDateTime(agent.lastGrantedAt, locale),
                            })}
                          </Typography>
                          <Stack direction="row" spacing={0.75} flexWrap="wrap" useFlexGap>
                            {agent.scopes.map((scope) => (
                              <Tooltip key={`${agent.connectionId}-${scope.name}`} title={scope.description}>
                                <Chip
                                  label={scope.name}
                                  size="small"
                                  sx={{ borderRadius: 999, fontWeight: 400 }}
                                />
                              </Tooltip>
                            ))}
                          </Stack>
                        </Stack>
                      </TableCell>
                      <TableCell sx={connectedAgentBodyCellSx}>
                        <ActivitySummary agent={agent} locale={locale} />
                      </TableCell>
                      <TableCell align="right" sx={{ ...connectedAgentBodyCellSx, width: 176 }}>
                        <Button
                          variant="outlined"
                          color="error"
                          disabled={rowBusy}
                          data-testid={`revoke-connected-agent-${agent.connectionId}`}
                          onClick={() => setAgentToRevoke(agent)}
                        >
                          {t("profile.connectedAgents.revokeAction")}
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>

      <ConfirmDialog
        open={agentToRevoke !== null}
        title={t("profile.connectedAgents.revokeConfirmTitle")}
        description={t("profile.connectedAgents.revokeConfirmDescription", {
          name: agentToRevoke?.clientName ?? "",
        })}
        confirmText={t("profile.connectedAgents.revokeAction")}
        cancelText={t("actions.cancel")}
        confirmColor="error"
        loading={agentToRevoke !== null && isMutationInFlight(`revoke-${agentToRevoke.connectionId}`)}
        onConfirm={() => {
          if (agentToRevoke) {
            void handleRevoke(agentToRevoke.connectionId);
          }
        }}
        onCancel={() => setAgentToRevoke(null)}
        data-testid="revoke-connected-agent-confirm-dialog"
      />
    </Stack>
  );
}
