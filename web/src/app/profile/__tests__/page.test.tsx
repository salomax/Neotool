import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { cleanup, fireEvent, render, waitFor, within } from "@testing-library/react";
import { ThemeProvider } from "@mui/material/styles";
import ProfilePage from "../page";
import { createAppTheme } from "@/styles/themes/theme";
import { UPDATE_ACCOUNT } from "@/lib/graphql/operations/auth/mutations";

const mockSetUser = vi.fn();
const mockUpdateUserPreferences = vi.fn();
const mockUpdateBankingUserPreferences = vi.fn();
const mockToastError = vi.fn();

let mockUser: {
  id: string;
  email: string;
  displayName?: string | null;
  avatarUrl?: string | null;
  avatarProvider?: string | null;
  emailVerified?: boolean | null;
  plan?: string | null;
} | null = null;

const mockUseQuery = vi.fn();
const mockUseMutation = vi.fn();
const mockCacheModify = vi.fn();
const theme = createAppTheme("light");

vi.mock("@apollo/client", () => ({
  useApolloClient: () => ({
    cache: {
      modify: mockCacheModify,
    },
  }),
  gql: (strings: TemplateStringsArray) => strings.join(""),
}));

vi.mock("@apollo/client/react", () => ({
  useQuery: (...args: unknown[]) => mockUseQuery(...args),
  useMutation: (...args: unknown[]) => mockUseMutation(...args),
  useApolloClient: () => ({
    cache: {
      modify: mockCacheModify,
    },
  }),
}));

vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
  initReactI18next: {
    type: "3rdParty",
    init: () => {},
  },
}));

vi.mock("@/shared/hooks/ui/usePageTitle", () => ({
  usePageTitle: vi.fn(),
}));

vi.mock("@/shared/providers", () => ({
  useAuth: () => ({
    user: mockUser,
    setUser: mockSetUser,
  }),
  usePreferences: () => ({
    userPreferences: {
      language: "PORTUGUESE",
      theme: "SYSTEM",
      blurBalances: false,
      firstDayOfWeek: 1,
      keepMeConnected: false,
    },
    bankingUserPreferences: {
      ruleApplicationMode: "ALWAYS_PROMPT",
    },
    updateUserPreferences: mockUpdateUserPreferences,
    updateBankingUserPreferences: mockUpdateBankingUserPreferences,
    isLoading: false,
  }),
  useToast: () => ({
    error: mockToastError,
  }),
}));

describe("ProfilePage", () => {
  const renderPage = () =>
    render(
      <ThemeProvider theme={theme}>
        <ProfilePage />
      </ThemeProvider>,
    );

  beforeEach(() => {
    cleanup();
    vi.clearAllMocks();

    mockUser = {
      id: "user-1",
      email: "user@example.com",
      displayName: "User Name",
      avatarUrl: "http://cdn/avatar.png",
      avatarProvider: null,
      emailVerified: true,
      plan: "Free",
    };

    mockUseQuery.mockReturnValue({
      data: { myAccounts: [] },
      loading: false,
      refetch: vi.fn(),
    });

    mockUseMutation.mockImplementation(() => [vi.fn()]);
  });

  it("toggles keepMeConnected preference", async () => {
    const view = renderPage();

    const toggle = view.getByRole("switch");

    fireEvent.click(toggle);

    await waitFor(() => {
      expect(mockUpdateUserPreferences).toHaveBeenCalledWith({
        keepMeConnected: true,
      });
    });
  });

  it("allows owner to edit account name inline", async () => {
    const refetch = vi.fn();
    const updateAccountMutation = vi.fn().mockResolvedValue({
      data: {
        updateAccount: {
          id: "account-1",
          accountName: "Renamed Account",
          accountType: "PERSONAL",
          accountStatus: "ACTIVE",
          isOwner: true,
        },
      },
    });
    mockUseQuery.mockReturnValue({
      data: {
        myAccounts: [
          {
            id: "account-1",
            accountName: "Original Account",
            accountType: "PERSONAL",
            accountStatus: "ACTIVE",
            isOwner: true,
          },
        ],
      },
      loading: false,
      refetch,
    });

    mockUseMutation.mockImplementation((document: unknown) => {
      if (document === UPDATE_ACCOUNT) {
        return [updateAccountMutation];
      }
      return [vi.fn()];
    });

    const view = renderPage();

    fireEvent.click(view.getAllByRole("button", { name: "profile.accounts.editLabel" })[0]!);

    const input = view.getByDisplayValue("Original Account");
    fireEvent.change(input, { target: { value: "Renamed Account" } });
    fireEvent.keyDown(input, { key: "Enter", code: "Enter" });

    await waitFor(() => {
      expect(updateAccountMutation).toHaveBeenCalledWith({
        variables: {
          accountId: "account-1",
          input: { accountName: "Renamed Account" },
        },
      });
      expect(refetch).toHaveBeenCalled();
    });
  });

  it("hides avatar upload actions for oauth users", () => {
    mockUser = {
      ...(mockUser as NonNullable<typeof mockUser>),
      avatarProvider: "google",
    };

    const view = renderPage();
    const scoped = within(view.container);

    expect(scoped.queryAllByRole("button", { name: "profile.avatar.changePhoto" })).toHaveLength(0);
    expect(scoped.queryAllByText("profile.avatar.removePhoto")).toHaveLength(0);
  });
});
