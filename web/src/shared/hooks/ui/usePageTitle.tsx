"use client";

import * as React from "react";

type PageTitleContextType = {
  title: string | null;
  setTitle: (title: string | null) => void;
};

const PageTitleContext = React.createContext<PageTitleContextType | null>(null);

/**
 * Hook to access and set the page title in the global header.
 * 
 * @param title - The page title to display, or null to clear it
 * 
 * @example
 * ```tsx
 * function MyPage() {
 *   usePageTitle("My Page Title");
 *   return <div>Content</div>;
 * }
 * ```
 * 
 * @throws {Error} If used outside of PageTitleProvider
 */
export function usePageTitle(title: string | null): void {
  const context = React.useContext(PageTitleContext);
  
  if (!context) {
    throw new Error("usePageTitle must be used within PageTitleProvider");
  }

  const { setTitle } = context;

  // Update title when it changes
  React.useEffect(() => {
    setTitle(title);
    // Cleanup: clear title when component unmounts
    return () => {
      setTitle(null);
    };
  }, [title, setTitle]);
}

/**
 * Hook to access the current page title (read-only).
 * Used by components that need to read the title without setting it.
 * 
 * @returns The current page title, or null if no title is set
 * 
 * @example
 * ```tsx
 * function Header() {
 *   const title = usePageTitleValue();
 *   return <h1>{title || "Default Title"}</h1>;
 * }
 * ```
 * 
 * @throws {Error} If used outside of PageTitleProvider
 */
export function usePageTitleValue(): string | null {
  const context = React.useContext(PageTitleContext);
  
  if (!context) {
    throw new Error("usePageTitleValue must be used within PageTitleProvider");
  }

  return context.title;
}

/**
 * Provider component for page title context.
 * Should be placed high in the component tree (e.g., in AppShell).
 * 
 * @param children - React children to wrap with the provider
 */
export function PageTitleProvider({ children }: { children: React.ReactNode }) {
  const [title, setTitle] = React.useState<string | null>(null);

  const value = React.useMemo(
    () => ({
      title,
      setTitle,
    }),
    [title, setTitle]
  );

  return (
    <PageTitleContext.Provider value={value}>
      {children}
    </PageTitleContext.Provider>
  );
}
