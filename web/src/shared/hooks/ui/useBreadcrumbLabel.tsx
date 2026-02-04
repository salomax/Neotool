"use client";

import * as React from "react";

type BreadcrumbLabelContextType = {
  labels: Record<string, string | null>;
  setLabel: (path: string, label: string | null | undefined) => void;
};

const BreadcrumbLabelContext = React.createContext<BreadcrumbLabelContextType | null>(null);

/**
 * Hook to set a custom label for a specific breadcrumb path segment.
 * 
 * @param path - The path segment to customize (e.g., "bacen-cod-inst")
 * @param label - The label to display. Pass null to show a loading state (skeleton).
 * 
 * @example
 * ```tsx
 * function MyPage() {
 *   useBreadcrumbLabel("bacen-cod-inst", "Institution Name");
 *   return <div>Content</div>;
 * }
 * ```
 * 
 * @throws {Error} If used outside of BreadcrumbLabelProvider
 */
export function useBreadcrumbLabel(path: string, label: string | null): void {
  const context = React.useContext(BreadcrumbLabelContext);
  
  if (!context) {
    throw new Error("useBreadcrumbLabel must be used within BreadcrumbLabelProvider");
  }

  const { setLabel } = context;

  // Update label when it changes
  React.useEffect(() => {
    setLabel(path, label);
    // Cleanup: clear label when component unmounts
    return () => {
      setLabel(path, undefined);
    };
  }, [path, label, setLabel]);
}

/**
 * Hook to access breadcrumb labels (read-only).
 * Used by components that need to read labels without setting them.
 * 
 * @returns The labels record
 * 
 * @throws {Error} If used outside of BreadcrumbLabelProvider
 */
export function useBreadcrumbLabels(): Record<string, string | null> {
  const context = React.useContext(BreadcrumbLabelContext);
  
  if (!context) {
    // Return empty record if used outside provider, to allow usage in components 
    // that might not be wrapped (e.g. tests or isolated usage)
    return {};
  }

  return context.labels;
}

/**
 * Provider component for breadcrumb label context.
 * Should be placed high in the component tree (e.g., in AppShell).
 * 
 * @param children - React children to wrap with the provider
 */
export function BreadcrumbLabelProvider({ children }: { children: React.ReactNode }) {
  const [labels, setLabels] = React.useState<Record<string, string | null>>({});

  const setLabel = React.useCallback((path: string, label: string | null | undefined) => {
    setLabels((prev) => {
      if (label === undefined) {
        const { [path]: _, ...rest } = prev;
        return rest;
      } else {
        return { ...prev, [path]: label };
      }
    });
  }, []);

  const value = React.useMemo(
    () => ({
      labels,
      setLabel,
    }),
    [labels, setLabel]
  );

  return (
    <BreadcrumbLabelContext.Provider value={value}>
      {children}
    </BreadcrumbLabelContext.Provider>
  );
}
