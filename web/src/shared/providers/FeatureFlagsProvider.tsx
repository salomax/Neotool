"use client";

import * as React from "react";
import {
  FlagProvider,
  useFlagsStatus,
  useUnleashContext,
  type IToggle,
  type IConfig,
} from "@unleash/proxy-client-react";
import { getRuntimeConfig, validateConfig } from "@/shared/config/runtime-config";
import { logger } from "@/shared/utils/logger";
import { useAuth } from "./AuthProvider";
import {
  buildAuthSessionContext,
  buildUnleashEvaluationContext,
} from "@/shared/utils/auth-session/shared";
import type { UnleashEvaluationContext } from "@/shared/types/unleash";

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
  contextBootstrap?: UnleashEvaluationContext;
};

export const FeatureFlagsProvider: React.FC<FeatureFlagsProviderProps> = ({
  children,
  bootstrap = {},
  contextBootstrap,
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
    () =>
      Object.entries(bootstrap).map(([name, enabled]) => ({
        name,
        enabled,
        variant: { name: "disabled", enabled: false }, // Default variant when not specified
        impressionData: false, // No impression data for bootstrap
      })),
    [bootstrap]
  );

  const initialContext = React.useMemo(
    () => contextBootstrap ?? buildUnleashEvaluationContext(null),
    [contextBootstrap]
  );

  const config: IConfig = React.useMemo(
    () => ({
      url: runtimeConfig.unleashProxyUrl,
      clientKey: runtimeConfig.unleashClientToken,
      appName: "neotool-web",
      refreshInterval: 30, // Refresh every 30 seconds
      bootstrap: bootstrapToggles, // Use bootstrap data to avoid flicker
      environment: runtimeConfig.env,
      context: mapUnleashContext(initialContext),
    }),
    [
      runtimeConfig.unleashProxyUrl,
      runtimeConfig.unleashClientToken,
      runtimeConfig.env,
      bootstrapToggles,
      initialContext,
    ]
  );

  return (
    <FlagProvider config={config}>
      <FeatureFlagsStatusHandler
        onReady={handleReady}
        onError={handleError}
      />
      <FeatureFlagsContextSync />
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

const FeatureFlagsContextSync: React.FC = () => {
  const { user, isLoading } = useAuth();
  const updateContext = useUnleashContext();
  const lastContextJsonRef = React.useRef<string | null>(null);

  const nextContext = React.useMemo(() => {
    if (isLoading) {
      return null;
    }

    const userContext = buildAuthSessionContext(user);
    return buildUnleashEvaluationContext(userContext);
  }, [isLoading, user]);

  React.useEffect(() => {
    if (!nextContext) {
      return;
    }

    const serializedContext = JSON.stringify(nextContext);
    if (serializedContext === lastContextJsonRef.current) {
      return;
    }

    lastContextJsonRef.current = serializedContext;
    void updateContext(mapUnleashContext(nextContext)).catch((error) => {
      logger.error("Failed to update feature flags context", error);
    });
  }, [nextContext, updateContext]);

  return null;
};

function mapUnleashContext(context: UnleashEvaluationContext): NonNullable<IConfig["context"]> {
  return {
    ...(context.userId ? { userId: context.userId } : {}),
    properties: {
      ...(context.tenantId ? { tenantId: context.tenantId } : {}),
      ...(context.role ? { role: context.role } : {}),
      ...(context.plan ? { plan: context.plan } : {}),
      ...(context.region ? { region: context.region } : {}),
      ...(context.environment ? { environment: context.environment } : {}),
    },
  };
}
