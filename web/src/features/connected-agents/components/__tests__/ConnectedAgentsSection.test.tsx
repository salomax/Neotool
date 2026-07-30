import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { ThemeProvider } from "@mui/material/styles";
import { createAppTheme } from "@/styles/themes/theme";
import { ConnectedAgentsSection } from "../ConnectedAgentsSection";
import { REVOKE_CONNECTED_AGENT } from "@/lib/graphql/operations/auth/connectedAgents";
import { ConnectedAgentActivityOutcome } from "@/lib/graphql/types/__generated__/graphql";

const mockUseQuery = vi.fn();
const mockUseMutation = vi.fn();
const mockExecuteMutation = vi.fn();
const mockIsMutationInFlight = vi.fn();
const mockToastSuccess = vi.fn();
const mockToastError = vi.fn();

const theme = createAppTheme("light");

vi.mock("@apollo/client/react", () => ({
  useQuery: (...args: unknown[]) => mockUseQuery(...args),
  useMutation: (...args: unknown[]) => mockUseMutation(...args),
}));

vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string, options?: Record<string, string>) => {
      if (options?.value) {
        return `${key}:${options.value}`;
      }
      if (options?.name) {
        return `${key}:${options.name}`;
      }
      return key;
    },
    i18n: {
      language: "en-US",
    },
  }),
  initReactI18next: {
    type: "3rdParty",
    init: () => {},
  },
}));

vi.mock("@/shared/providers", () => ({
  useToast: () => ({
    success: mockToastSuccess,
    error: mockToastError,
  }),
}));

vi.mock("@/shared/hooks/mutations", () => ({
  useMutationWithRefetch: (...args: unknown[]) => ({
    executeMutation: mockExecuteMutation,
    isMutationInFlight: mockIsMutationInFlight,
    __args: args,
  }),
}));

function renderSection() {
  return render(
    <ThemeProvider theme={theme}>
      <ConnectedAgentsSection accountId="account-1" />
    </ThemeProvider>,
  );
}

function buildConnectedAgent() {
  return {
    __typename: "ConnectedAgent" as const,
    connectionId: "connection-1",
    clientId: "client-1",
    clientName: "Planner Agent",
    connectedAt: "2026-07-30T10:00:00.000Z",
    lastGrantedAt: "2026-07-30T12:00:00.000Z",
    scopes: [
      {
        __typename: "ConnectedAgentScope" as const,
        name: "finance:transactions:read",
        description: "Read transactions",
      },
    ],
    recentActivity: [
      {
        __typename: "ConnectedAgentRecentActivity" as const,
        id: "activity-1",
        occurredAt: "2026-07-30T12:05:00.000Z",
        toolName: "finance.transactions.list",
        outcome: ConnectedAgentActivityOutcome.Success,
        errorCode: null,
        errorMessage: null,
        latencyMs: 42,
      },
    ],
  };
}

describe("ConnectedAgentsSection", () => {
  beforeEach(() => {
    cleanup();
    mockUseQuery.mockReset();
    mockUseMutation.mockReset();
    mockExecuteMutation.mockReset();
    mockIsMutationInFlight.mockReset();
    mockToastSuccess.mockReset();
    mockToastError.mockReset();

    mockUseQuery.mockReturnValue({
      data: { connectedAgents: [] },
      loading: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseMutation.mockImplementation(() => [vi.fn()]);
    mockExecuteMutation.mockResolvedValue({ data: { revokeConnectedAgent: { connectionId: "connection-1" } } });
    mockIsMutationInFlight.mockReturnValue(false);
  });

  afterEach(() => {
    cleanup();
  });

  it("renders the loading state", () => {
    mockUseQuery.mockReturnValueOnce({
      data: undefined,
      loading: true,
      error: undefined,
      refetch: vi.fn(),
    });

    const view = renderSection();

    expect(within(view.container).getByText("profile.connectedAgents.loading")).toBeInTheDocument();
  });

  it("renders the empty state", () => {
    const view = renderSection();

    expect(within(view.container).getByText("profile.connectedAgents.empty")).toBeInTheDocument();
  });

  it("renders the error state", () => {
    mockUseQuery.mockReturnValue({
      data: undefined,
      loading: false,
      error: new Error("boom"),
      refetch: vi.fn(),
    });

    const view = renderSection();

    expect(within(view.container).getByRole("alert")).toHaveTextContent("boom");
  });

  it("renders a populated table with recent activity", () => {
    mockUseQuery.mockReturnValue({
      data: { connectedAgents: [buildConnectedAgent()] },
      loading: false,
      error: undefined,
      refetch: vi.fn(),
    });

    const view = renderSection();

    expect(within(view.container).getByText("Planner Agent")).toBeInTheDocument();
    expect(within(view.container).getByText("client-1")).toBeInTheDocument();
    expect(within(view.container).getByText("finance:transactions:read")).toBeInTheDocument();
    expect(within(view.container).getByText("finance.transactions.list")).toBeInTheDocument();
    expect(within(view.container).getByText("profile.connectedAgents.activityOutcome.SUCCESS")).toBeInTheDocument();
    expect(within(view.container).getByTestId("revoke-connected-agent-connection-1")).toBeInTheDocument();
  });

  it("confirms revoke, executes the mutation, and shows a success toast", async () => {
    const refetch = vi.fn();
    const revokeMutation = vi.fn().mockResolvedValue({
      data: {
        revokeConnectedAgent: {
          connectionId: "connection-1",
          clientId: "client-1",
          accountId: "account-1",
          revokedAt: "2026-07-30T12:06:00.000Z",
        },
      },
    });

    mockUseQuery.mockReturnValue({
      data: { connectedAgents: [buildConnectedAgent()] },
      loading: false,
      error: undefined,
      refetch,
    });
    mockUseMutation.mockImplementation((document: unknown) => {
      if (document === REVOKE_CONNECTED_AGENT) {
        return [revokeMutation];
      }
      return [vi.fn()];
    });

    const view = renderSection();

    fireEvent.click(within(view.container).getByTestId("revoke-connected-agent-connection-1"));

    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText("profile.connectedAgents.revokeConfirmDescription:Planner Agent")).toBeInTheDocument();

    fireEvent.click(within(dialog).getAllByRole("button", { name: "profile.connectedAgents.revokeAction" })[0]!);

    await waitFor(() => {
      expect(mockExecuteMutation).toHaveBeenCalledWith(
        revokeMutation,
        { connectionId: "connection-1" },
        "revoke-connection-1",
      );
      expect(mockToastSuccess).toHaveBeenCalledWith("profile.connectedAgents.revokeSuccess");
    });
  });

  it("shows an error toast when revoke fails", async () => {
    const revokeMutation = vi.fn();
    mockUseQuery.mockReturnValue({
      data: { connectedAgents: [buildConnectedAgent()] },
      loading: false,
      error: undefined,
      refetch: vi.fn(),
    });
    mockUseMutation.mockImplementation((document: unknown) => {
      if (document === REVOKE_CONNECTED_AGENT) {
        return [revokeMutation];
      }
      return [vi.fn()];
    });
    mockExecuteMutation.mockRejectedValue(new Error("revoke failed"));

    const view = renderSection();

    fireEvent.click(within(view.container).getByTestId("revoke-connected-agent-connection-1"));
    fireEvent.click(
      within(screen.getByRole("dialog"))
        .getAllByRole("button", { name: "profile.connectedAgents.revokeAction" })[0]!,
    );

    await waitFor(() => {
      expect(mockToastError).toHaveBeenCalledWith("revoke failed");
    });
  });
});
