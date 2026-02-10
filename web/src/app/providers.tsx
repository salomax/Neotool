"use client";

import * as React from "react";
import { AppThemeProvider } from "@/styles/themes/AppThemeProvider";
import { GraphQLProvider } from "@/lib/graphql/GraphQLProvider";
import { AppQueryProvider, ToastProvider, AuthProvider, AuthorizationProvider, FeatureFlagsProvider } from "@/shared/providers";
import { BreadcrumbLabelProvider } from "@/shared/hooks/ui/useBreadcrumbLabel";
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
                <BreadcrumbLabelProvider>
                  <ToastProvider>
                    {children}
                  </ToastProvider>
                </BreadcrumbLabelProvider>
              </AuthorizationProvider>
            </AuthProvider>
          </FeatureFlagsProvider>
        </GraphQLProvider>
      </AppQueryProvider>
    </AppThemeProvider>
  );
}
