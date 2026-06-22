import React from "react";
import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AppThemeProvider } from "@/styles/themes/AppThemeProvider";
import { BottomNavigationBar } from "../BottomNavigationBar";

const mockPush = vi.fn();
const mockPathname = vi.fn();
const mockUseFeatureFlagEnabled = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: mockPush,
  }),
  usePathname: () => mockPathname(),
}));

vi.mock("@mui/material/useMediaQuery", () => ({
  default: () => true,
}));

vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        "routes.home": "Home",
        "routes.dashboard": "Dashboard",
        "routes.transactions": "Transactions",
        "routes.portfolio": "Portfolio",
        "routes.recurringPayments": "Recurring",
        "routes.more": "More",
      };

      return translations[key] ?? key;
    },
  }),
}));

vi.mock("@/shared/hooks/useFeatureFlag", () => ({
  useFeatureFlagEnabled: (flagName: string) => mockUseFeatureFlagEnabled(flagName),
}));

function renderBottomNavigationBar(onMoreClick = vi.fn()) {
  return render(
    <AppThemeProvider>
      <BottomNavigationBar onMoreClick={onMoreClick} />
    </AppThemeProvider>,
  );
}

describe.sequential("BottomNavigationBar", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockPathname.mockReturnValue("/personal-finance");
    mockUseFeatureFlagEnabled.mockImplementation((flagName: string) => flagName === "personal-finance-dashboard-enabled");
  });

  it("renders the five personal-finance tabs when the feature flag is enabled", () => {
    renderBottomNavigationBar();

    expect(screen.getByRole("button", { name: "Dashboard" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Transactions" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Portfolio" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Recurring" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "More" })).toBeInTheDocument();
  });

  it("marks Dashboard as active when on /personal-finance", () => {
    mockPathname.mockReturnValue("/personal-finance");

    renderBottomNavigationBar();

    expect(screen.getByRole("button", { name: "Dashboard" })).toHaveAttribute("aria-current", "page");
  });

  it("renders only Home and Menu when the feature flag is disabled", () => {
    mockUseFeatureFlagEnabled.mockReturnValue(false);

    renderBottomNavigationBar();

    expect(screen.getByRole("button", { name: "Home" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "More" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Dashboard" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Transactions" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Portfolio" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Recurring" })).not.toBeInTheDocument();
  });

  it("marks the correct tab as active for nested transaction routes", () => {
    mockPathname.mockReturnValue("/personal-finance/finance-transactions/123");

    renderBottomNavigationBar();

    expect(screen.getByRole("button", { name: "Transactions" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("button", { name: "Dashboard" })).not.toHaveAttribute("aria-current");
  });

  it("marks the recurring tab as active for nested recurring routes", () => {
    mockPathname.mockReturnValue("/personal-finance/recurring-payments/series");

    renderBottomNavigationBar();

    expect(screen.getByRole("button", { name: "Recurring" })).toHaveAttribute("aria-current", "page");
  });

  it("navigates to the selected route when a route tab is tapped", async () => {
    const user = userEvent.setup();
    renderBottomNavigationBar();

    await user.click(screen.getByRole("button", { name: "Portfolio" }));

    expect(mockPush).toHaveBeenCalledWith("/personal-finance/investments");
  });

  it("fires onMoreClick when the More tab is tapped", async () => {
    const user = userEvent.setup();
    const onMoreClick = vi.fn();

    renderBottomNavigationBar(onMoreClick);

    await user.click(screen.getByRole("button", { name: "More" }));

    expect(onMoreClick).toHaveBeenCalledTimes(1);
  });
});
