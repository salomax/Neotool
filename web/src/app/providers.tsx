"use client";

import * as React from "react";
import { AppThemeProvider } from "@/styles/themes/AppThemeProvider";
import { AppQueryProvider } from "@/shared/providers";
import { GraphQLProvider } from "@/lib/graphql/GraphQLProvider";
import { ToastProvider, AuthProvider, AuthorizationProvider, FeatureFlagsProvider } from "@/shared/providers";
import "@/shared/i18n/config";

type ProvidersProps = {
  children: React.ReactNode;
  featureFlagsBootstrap?: Record<string, boolean>;
};

export default function Providers({ children, featureFlagsBootstrap }: ProvidersProps) {
  return (
    <AppThemeProvider>
      <AppQueryProvider>
        <GraphQLProvider>
          <FeatureFlagsProvider bootstrap={featureFlagsBootstrap}>
            <AuthProvider>
              <AuthorizationProvider>
                <ToastProvider>
                  {children}
                </ToastProvider>
              </AuthorizationProvider>
            </AuthProvider>
          </FeatureFlagsProvider>
        </GraphQLProvider>
      </AppQueryProvider>
    </AppThemeProvider>
  );
}
