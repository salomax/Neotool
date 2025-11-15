"use client";

import * as React from "react";
import { AppThemeProvider } from "@/styles/themes/AppThemeProvider";
import { AppQueryProvider } from "@/lib/api/AppQueryProvider";
import { GraphQLProvider } from "@/lib/graphql/GraphQLProvider";
import { ToastProvider, AuthProvider } from "@/shared/providers";
import "@/shared/i18n/config";

export default function Providers({ children }: { children: React.ReactNode }) {
  return (
    <AppThemeProvider>
      <AppQueryProvider>
        <GraphQLProvider>
          <AuthProvider>
            <ToastProvider>
              {children}
            </ToastProvider>
          </AuthProvider>
        </GraphQLProvider>
      </AppQueryProvider>
    </AppThemeProvider>
  );
}
