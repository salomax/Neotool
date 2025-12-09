export { metadata } from "@/shared/seo/metadata";
import * as React from "react";
import dynamic from "next/dynamic";
import { Box } from "@mui/material";
import { ErrorBoundary } from "@/shared/components/ErrorBoundary";
import Providers from "./providers";

// Lazy load AppShell to reduce initial bundle size
const AppShell = dynamic(() => import("@/shared/ui/shell/AppShell").then(mod => ({ default: mod.AppShell })), {
  loading: () => {
    const { LoadingSpinner } = require("@/shared/components/ui/primitives/LoadingSpinner");
    return (
      <Box
        sx={{
          minHeight: "100vh",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <LoadingSpinner message="Loading application..." />
      </Box>
    );
  }
});

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body>
        <ErrorBoundary>
          <Providers>
            <AppShell>{children}</AppShell>
          </Providers>
        </ErrorBoundary>
      </body>
    </html>
  );
}
