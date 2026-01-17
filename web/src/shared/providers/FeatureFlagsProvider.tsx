"use client";

import * as React from "react";
import { FlagProvider, useFlagsStatus, type IToggle, type IConfig } from "@unleash/proxy-client-react";
import { logger } from "@/shared/utils/logger";

type FeatureFlagsContextType = {
  isReady: boolean;
  flagsError: Error | null;
};

const FeatureFlagsContext = React.createContext<FeatureFlagsContextType | null>(null);

export const useFeatureFlagsContext = (): FeatureFlagsContextType => {
  const ctx = React.useContext(FeatureFlagsContext);
  if (!ctx) {
    throw new Error("useFeatureFlagsContext must be used within FeatureFlagsProvider");
  }
  return ctx;
};

type FeatureFlagsProviderProps = {
  children: React.ReactNode;
  bootstrap?: Record<string, boolean>; // Bootstrap data from SSR
};

export const FeatureFlagsProvider: React.FC<FeatureFlagsProviderProps> = ({
  children,
  bootstrap = {},
}) => {
  const [isReady, setIsReady] = React.useState(false);
  const [flagsError, setFlagsError] = React.useState<Error | null>(null);

  const unleashProxyUrl = process.env.NEXT_PUBLIC_UNLEASH_PROXY_URL;
  const unleashClientToken = process.env.NEXT_PUBLIC_UNLEASH_CLIENT_TOKEN;

  if (!unleashProxyUrl) {
    throw new Error("NEXT_PUBLIC_UNLEASH_PROXY_URL environment variable is required");
  }

  if (!unleashClientToken) {
    throw new Error("NEXT_PUBLIC_UNLEASH_CLIENT_TOKEN environment variable is required");
  }

  // Convert bootstrap Record<string, boolean> to IToggle[] format
  const bootstrapToggles: IToggle[] = Object.entries(bootstrap).map(([name, enabled]) => ({
    name,
    enabled,
    variant: { name: 'disabled', enabled: false }, // Default variant when not specified
    impressionData: false, // No impression data for bootstrap
  }));

  const config: IConfig = {
    url: unleashProxyUrl,
    clientKey: unleashClientToken,
    appName: "neotool-web",
    refreshInterval: 30, // Refresh every 30 seconds
    bootstrap: bootstrapToggles, // Use bootstrap data to avoid flicker
    environment: process.env.NEXT_PUBLIC_ENV || "production",
  };

  return (
    <FlagProvider config={config}>
      <FeatureFlagsStatusHandler
        onReady={() => setIsReady(true)}
        onError={(error) => setFlagsError(error)}
      />
      <FeatureFlagsContext.Provider value={{ isReady, flagsError }}>
        {children}
      </FeatureFlagsContext.Provider>
    </FlagProvider>
  );
};

// Internal component to handle flags status
const FeatureFlagsStatusHandler: React.FC<{
  onReady: () => void;
  onError: (error: Error) => void;
}> = ({ onReady, onError }) => {
  const { flagsReady, flagsError } = useFlagsStatus();

  React.useEffect(() => {
    if (flagsReady) {
      onReady();
    }
  }, [flagsReady, onReady]);

  React.useEffect(() => {
    if (flagsError) {
      onError(new Error(flagsError));
    }
  }, [flagsError, onError]);

  return null;
};
