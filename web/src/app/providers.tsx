"use client";

import * as React from "react";
import { LocalizationProvider } from '@mui/x-date-pickers';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import 'dayjs/locale/pt-br';
import { AppThemeProvider } from "@/styles/themes/AppThemeProvider";
import { GraphQLProvider } from "@/lib/graphql/GraphQLProvider";
import { AppQueryProvider, ToastProvider, AuthProvider, AuthorizationProvider, FeatureFlagsProvider, PreferencesProvider } from "@/shared/providers";
import { BreadcrumbLabelProvider } from "@/shared/hooks/ui/useBreadcrumbLabel";
import type { UnleashEvaluationContext } from "@/shared/types/unleash";
import "@/shared/i18n/config";

type ProvidersProps = {
  children: React.ReactNode;
  featureFlagsBootstrap?: Record<string, boolean>;
  featureFlagsContextBootstrap?: UnleashEvaluationContext;
};

export default function Providers({
  children,
  featureFlagsBootstrap,
  featureFlagsContextBootstrap,
}: ProvidersProps) {
  return (
    <AppThemeProvider>
      <LocalizationProvider dateAdapter={AdapterDayjs} adapterLocale="pt-br">
        <AppQueryProvider>
          <GraphQLProvider>
            <AuthProvider>
              <PreferencesProvider>
                <FeatureFlagsProvider
                  bootstrap={featureFlagsBootstrap}
                  contextBootstrap={featureFlagsContextBootstrap}
                >
                  <AuthorizationProvider>
                    <BreadcrumbLabelProvider>
                      <ToastProvider>
                        {children}
                      </ToastProvider>
                    </BreadcrumbLabelProvider>
                  </AuthorizationProvider>
                </FeatureFlagsProvider>
              </PreferencesProvider>
            </AuthProvider>
          </GraphQLProvider>
        </AppQueryProvider>
      </LocalizationProvider>
    </AppThemeProvider>
  );
}
