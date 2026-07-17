"use client";

import * as React from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Alert, Box, Button, Container, Typography } from "@mui/material";
import { useMutation } from "@apollo/client/react";
import { LoadingSpinner } from "@/shared/components/ui/primitives/LoadingSpinner";
import { Paper } from "@/shared/components/ui/layout/Paper";
import { Stack } from "@/shared/components/ui/layout/Stack";
import { useAuth, useToast } from "@/shared/providers";
import { useTranslation } from "react-i18next";
import {
  extractErrorCode,
  extractErrorFields,
  extractErrorMessage,
} from "@/shared/utils/error";
import {
  type AcceptInvitationMutation,
  type AcceptInvitationMutationVariables,
} from "@/lib/graphql/operations/auth/accountMembership.generated";
import { ACCEPT_INVITATION } from "@/lib/graphql/operations/auth/accountMembership";

type AcceptErrorKind = "wrong_user" | "generic";

function resolveAcceptErrorKind(error: unknown): AcceptErrorKind {
  if (
    extractErrorCode(error) === "INVITATION_INVALID" &&
    extractErrorFields(error).includes("actorUserId")
  ) {
    return "wrong_user";
  }
  return "generic";
}

export default function AcceptInvitationPage() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { t } = useTranslation("common");
  const toast = useToast();
  const { isAuthenticated, isLoading, signOut } = useAuth();
  const token = searchParams.get("token");
  const [status, setStatus] = React.useState<"idle" | "success" | "error">("idle");
  const [errorKind, setErrorKind] = React.useState<AcceptErrorKind>("generic");
  const [message, setMessage] = React.useState<string | null>(null);
  const [acceptedAccountName, setAcceptedAccountName] = React.useState<string | null>(null);
  const hasTriggered = React.useRef(false);

  const [acceptInvitationMutation] = useMutation<AcceptInvitationMutation, AcceptInvitationMutationVariables>(
    ACCEPT_INVITATION,
  );

  React.useEffect(() => {
    if (isLoading || hasTriggered.current) {
      return;
    }
    if (!token) {
      hasTriggered.current = true;
      setStatus("error");
      setErrorKind("generic");
      setMessage(t("profile.invitation.invalidToken"));
      return;
    }
    if (!isAuthenticated) {
      hasTriggered.current = true;
      router.replace(`/signin?next=${encodeURIComponent(`/invitations/accept?token=${token}`)}`);
      return;
    }

    hasTriggered.current = true;
    void (async () => {
      try {
        const response = await acceptInvitationMutation({ variables: { token } });
        const accountName = response.data?.acceptInvitation.accountName ?? null;
        setAcceptedAccountName(accountName);
        setStatus("success");
        toast.success(t("profile.invitation.acceptSuccess"));
      } catch (err) {
        const kind = resolveAcceptErrorKind(err);
        const errorMessage =
          kind === "wrong_user"
            ? t("profile.invitation.wrongAccountDescription")
            : extractErrorMessage(err, t("profile.invitation.acceptError"));
        setErrorKind(kind);
        setMessage(errorMessage);
        setStatus("error");
        toast.error(errorMessage);
      }
    })();
  }, [acceptInvitationMutation, isAuthenticated, isLoading, router, t, toast, token]);

  const handleSwitchAccount = () => {
    const acceptPath = token
      ? `/invitations/accept?token=${encodeURIComponent(token)}`
      : "/invitations/accept";
    signOut({
      redirectTo: `/signin?next=${encodeURIComponent(acceptPath)}`,
    });
  };

  if (isLoading || status === "idle") {
    return (
      <Container maxWidth="sm" sx={{ py: 8 }}>
        <LoadingSpinner />
      </Container>
    );
  }

  const errorTitle =
    errorKind === "wrong_user"
      ? t("profile.invitation.wrongAccountTitle")
      : t("profile.invitation.errorTitle");

  return (
    <Box
      sx={{
        minHeight: "100%",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        bgcolor: "background.default",
        py: { xs: 4, sm: 8 },
        px: { xs: 2, sm: 3 },
      }}
    >
      <Container maxWidth="sm">
        <Paper elevation={0} sx={{ p: { xs: 3, sm: 4 }, border: 1, borderColor: "divider" }}>
          <Stack gap={2}>
            <Typography variant="h4" component="h1" sx={{ fontWeight: 600 }}>
              {status === "success" ? t("profile.invitation.acceptedTitle") : errorTitle}
            </Typography>

            {status === "success" ? (
              <Alert severity="success">
                {acceptedAccountName
                  ? t("profile.invitation.acceptedDescription", { accountName: acceptedAccountName })
                  : t("profile.invitation.acceptSuccess")}
              </Alert>
            ) : (
              <Alert severity={errorKind === "wrong_user" ? "warning" : "error"}>
                {message || t("profile.invitation.acceptError")}
              </Alert>
            )}

            <Box sx={{ display: "flex", justifyContent: "flex-end", gap: 1, flexWrap: "wrap" }}>
              {status === "error" && errorKind === "wrong_user" ? (
                <Button variant="contained" onClick={handleSwitchAccount}>
                  {t("profile.invitation.switchAccount")}
                </Button>
              ) : null}
              <Button
                variant={status === "error" && errorKind === "wrong_user" ? "outlined" : "contained"}
                onClick={() => router.push("/")}
              >
                {t("profile.invitation.goHome")}
              </Button>
            </Box>
          </Stack>
        </Paper>
      </Container>
    </Box>
  );
}
