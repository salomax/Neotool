"use client";

import * as React from "react";
import dynamic from "next/dynamic";
import { AppShell } from "@/shared/ui/shell/AppShell";

// Lazy load AppShell to reduce initial bundle size
const AppShellLazy = dynamic(() => Promise.resolve({ default: AppShell }), {
  loading: () => {
    const { LoadingSpinner } = require("@/shared/components/ui/primitives/LoadingSpinner");
    return <LoadingSpinner message="Loading application..." />;
  }
});

/**
 * Main app layout with header and sidebar
 * Used for all authenticated pages within the (neotool) route group
 */
export default function NeotoolLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <AppShellLazy>{children}</AppShellLazy>;
}

