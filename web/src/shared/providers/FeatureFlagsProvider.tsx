"use client";

import * as React from "react";
import { FlagProvider, useFlagsStatus, type IToggle, type IConfig } from "@unleash/proxy-client-react";
import { getRuntimeConfig, validateConfig } from "@/shared/config/runtime-config";
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

  const handleReady = React.useCallback(() => {
    setIsReady(true);
  }, []);

  const handleError = React.useCallback((error: Error) => {
    setFlagsError(error);
  }, []);

  // Get configuration from runtime config (window.__RUNTIME_CONFIG__ or env vars)
  const runtimeConfig = getRuntimeConfig();
  const { valid, errors } = validateConfig(runtimeConfig);

  if (!valid) {
    logger.error("Feature flags configuration is invalid:", errors);
    throw new Error(`Feature flags configuration is invalid: ${errors.join(", ")}`);
  }

  // Convert bootstrap Record<string, boolean> to IToggle[] format
  // Memoized to prevent recalculation on every render
  const bootstrapToggles: IToggle[] = React.useMemo(
    () => Object.entries(bootstrap).map(([name, enabled]) => ({
      name,
      enabled,
      variant: { name: 'disabled', enabled: false }, // Default variant when not specified
      impressionData: false, // No impression data for bootstrap
    })),
    [bootstrap]
  );

  const config: IConfig = {
    url: runtimeConfig.unleashProxyUrl,
    clientKey: runtimeConfig.unleashClientToken,
    appName: "neotool-web",
    refreshInterval: 30, // Refresh every 30 seconds
    bootstrap: bootstrapToggles, // Use bootstrap data to avoid flicker
    environment: runtimeConfig.env,
  };

  return (
    <FlagProvider config={config}>
      <FeatureFlagsStatusHandler
        onReady={handleReady}
        onError={handleError}
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
