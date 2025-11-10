"use client";

import * as React from "react";
import { ErrorBoundary } from "@/shared/components/ErrorBoundary";
import Providers from "../providers";

/**
 * Auth layout - Full screen layout without header and sidebar
 * Used for authentication pages (signin, signup, etc.)
 */
export default function AuthLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <ErrorBoundary>
      <Providers>
        {children}
      </Providers>
    </ErrorBoundary>
  );
}

