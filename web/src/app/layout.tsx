export { metadata, viewport } from "@/shared/seo/metadata";
import * as React from "react";
import dynamic from "next/dynamic";
import { Box } from "@mui/material";
import { ErrorBoundary } from "@/shared/components/ErrorBoundary";
import { CloudflareAnalytics } from "@/shared/analytics/CloudflareAnalytics";
import { UmamiAnalytics } from "@/shared/analytics/UmamiAnalytics";
import { RuntimeConfigScript } from "@/shared/config/RuntimeConfigScript";
import { getBootstrapFeatureFlagsData } from "@/shared/utils/feature-flags/bootstrap";
import Providers from "./providers";

// Lazy load AppShell to reduce initial bundle size
const AppShell = dynamic(() => import("@/shared/ui/shell/AppShell").then(mod => ({ default: mod.AppShell })), {
  loading: () => {
    const { LoadingSpinner } = require("@/shared/components/ui/primitives/");
    return (
      <Box
        sx={{
          minHeight: "100vh",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <LoadingSpinner />
      </Box>
    );
  }
});

export default async function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const {
    flags: featureFlagsBootstrap,
    context: featureFlagsContextBootstrap,
  } = await getBootstrapFeatureFlagsData();

  return (
    <html lang="pt-BR">
      <head>
        <RuntimeConfigScript />
        <CloudflareAnalytics />
      </head>
      <body>
        <ErrorBoundary>
          <Providers
            featureFlagsBootstrap={featureFlagsBootstrap}
            featureFlagsContextBootstrap={featureFlagsContextBootstrap}
          >
            <UmamiAnalytics />
            <AppShell>{children}</AppShell>
          </Providers>
        </ErrorBoundary>
      </body>
    </html>
  );
}
