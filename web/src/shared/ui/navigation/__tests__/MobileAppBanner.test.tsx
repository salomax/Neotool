import React from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { AppThemeProvider } from "@/styles/themes/AppThemeProvider";
import { MobileAppBanner } from "../MobileAppBanner";

const mockIsMobile = vi.fn();
const mockUseFeatureFlagEnabled = vi.fn();

vi.mock("@mui/material/useMediaQuery", () => ({
  default: () => mockIsMobile(),
}));

vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        "mobileAppBanner.title": "For the best experience, get the app",
        "mobileAppBanner.subtitle": "Install Invistus on your phone for faster access.",
        "mobileAppBanner.appStore": "App Store",
        "mobileAppBanner.playStore": "Play Store",
        "mobileAppBanner.dismiss": "Dismiss app banner",
      };

      return translations[key] ?? key;
    },
  }),
}));

vi.mock("@/shared/hooks/useFeatureFlag", () => ({
  useFeatureFlagEnabled: (flagName: string) => mockUseFeatureFlagEnabled(flagName),
}));

vi.mock("next/image", () => ({
  // eslint-disable-next-line @next/next/no-img-element
  default: ({ alt, priority: _priority, ...props }: any) => <img alt={alt} {...props} />,
}));

function renderMobileAppBanner() {
  return render(
    <AppThemeProvider>
      <MobileAppBanner />
    </AppThemeProvider>,
  );
}

describe.sequential("MobileAppBanner", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    mockUseFeatureFlagEnabled.mockImplementation((flagName: string) => flagName === "mobile-app-message-show");
  });

  it("does not render on desktop", async () => {
    mockIsMobile.mockReturnValue(false);

    renderMobileAppBanner();

    await waitFor(() => {
      expect(screen.queryByTestId("mobile-app-banner")).not.toBeInTheDocument();
    });
  });

  it("renders on mobile", async () => {
    mockIsMobile.mockReturnValue(true);

    renderMobileAppBanner();

    expect(await screen.findByTestId("mobile-app-banner")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Play Store" })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "App Store" })).not.toBeInTheDocument();
  });

  it("closes the banner and persists the dismissed flag", async () => {
    const user = userEvent.setup();
    mockIsMobile.mockReturnValue(true);

    renderMobileAppBanner();

    await user.click(await screen.findByRole("button", { name: "Dismiss app banner" }));

    expect(localStorage.getItem("invistus:mobile-banner-dismissed")).toBe("true");
    await waitFor(() => {
      expect(screen.queryByTestId("mobile-app-banner")).not.toBeInTheDocument();
    });
  });

  it("does not render when already dismissed in localStorage", async () => {
    mockIsMobile.mockReturnValue(true);
    localStorage.setItem("invistus:mobile-banner-dismissed", "true");

    renderMobileAppBanner();

    await waitFor(() => {
      expect(screen.queryByTestId("mobile-app-banner")).not.toBeInTheDocument();
    });
  });

  it("does not render when the feature flag is disabled", async () => {
    mockIsMobile.mockReturnValue(true);
    mockUseFeatureFlagEnabled.mockReturnValue(false);

    renderMobileAppBanner();

    await waitFor(() => {
      expect(screen.queryByTestId("mobile-app-banner")).not.toBeInTheDocument();
    });
  });
});
