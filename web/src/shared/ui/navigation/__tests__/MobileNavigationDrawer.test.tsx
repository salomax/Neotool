import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AppThemeProvider } from "@/styles/themes/AppThemeProvider";
import { MobileNavigationDrawer } from "../MobileNavigationDrawer";

const mockPush = vi.fn();
const mockToggle = vi.fn();
const mockHasAny = vi.fn();
const mockSignOut = vi.fn();
const mockUseFeatureFlagEnabled = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: mockPush,
  }),
}));

vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        "routes.menu": "Menu",
        "routes.profile": "Profile",
        "routes.financialData": "Financial Data",
        "routes.investments": "Investments",
        "routes.pricesConsumption": "Prices & Consumption",
        "routes.settings": "Settings",
        "appHeader.theme.dark": "Dark Mode",
        "actions.enabled": "On",
        "actions.disabled": "Off",
        "appHeader.signOut": "Sign Out",
      };

      return translations[key] ?? key;
    },
  }),
}));

vi.mock("@/styles/themes/AppThemeProvider", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/styles/themes/AppThemeProvider")>();
  return {
    ...actual,
    useThemeMode: () => ({
      mode: "light",
      toggle: mockToggle,
    }),
  };
});

vi.mock("@/shared/providers", () => ({
  useAuthorization: () => ({
    hasAny: mockHasAny,
  }),
  useAuth: () => ({
    user: {
      id: "user-1",
      email: "user@example.com",
      displayName: "User Example",
      avatarUrl: null,
    },
    signOut: mockSignOut,
  }),
}));

vi.mock("@/shared/hooks/useFeatureFlag", () => ({
  useFeatureFlagEnabled: (flagName: string) => mockUseFeatureFlagEnabled(flagName),
}));

vi.mock("@/shared/components/ui/layout/Drawer", () => {
  function Drawer({ open, children }: { open: boolean; children: React.ReactNode }) {
    return open ? <div data-testid="mobile-navigation-drawer">{children}</div> : null;
  }

  function DrawerHeader({ title }: { title?: string }) {
    return <div>{title}</div>;
  }

  function DrawerBody({ children }: { children: React.ReactNode }) {
    return <div>{children}</div>;
  }

  Drawer.Header = DrawerHeader;
  Drawer.Body = DrawerBody;

  return { Drawer };
});

function renderMobileNavigationDrawer(onClose = vi.fn()) {
  return render(
    <AppThemeProvider>
      <MobileNavigationDrawer open onClose={onClose} />
    </AppThemeProvider>,
  );
}

describe.sequential("MobileNavigationDrawer", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockHasAny.mockReturnValue(true);
    mockUseFeatureFlagEnabled.mockImplementation((flagName: string) => {
      if (flagName === "personal-finance-dashboard-enabled") return true;
      if (flagName === "pricing-enabled") return true;
      return false;
    });
  });

  it("renders a profile link that navigates to /profile", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();

    renderMobileNavigationDrawer(onClose);

    await user.click(screen.getByRole("button", { name: /profile/i }));

    expect(mockPush).toHaveBeenCalledWith("/profile");
    expect(onClose).toHaveBeenCalled();
  });

  it("hides Settings when the user lacks authorization permissions", () => {
    mockHasAny.mockReturnValue(false);

    renderMobileNavigationDrawer();

    expect(screen.queryByRole("button", { name: /settings/i })).not.toBeInTheDocument();
  });

  it("hides Prices & Consumption when the pricing feature flag is disabled", () => {
    mockUseFeatureFlagEnabled.mockImplementation((flagName: string) => flagName === "personal-finance-dashboard-enabled");

    renderMobileNavigationDrawer();

    expect(screen.queryByRole("button", { name: /prices & consumption/i })).not.toBeInTheDocument();
  });

  it("calls signOut and closes the drawer when sign out is tapped", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();

    renderMobileNavigationDrawer(onClose);

    await user.click(screen.getByRole("button", { name: /sign out/i }));

    expect(onClose).toHaveBeenCalled();
    expect(mockSignOut).toHaveBeenCalledTimes(1);
  });
});
