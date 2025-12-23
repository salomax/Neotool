import React from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { render, screen, within, cleanup } from "@testing-library/react";
import { Breadcrumb, BreadcrumbItem, RouteConfig } from "../Breadcrumb";
import { AppThemeProvider } from "@/styles/themes/AppThemeProvider";

// Mock Next.js navigation
const mockPathname = vi.fn();
vi.mock("next/navigation", () => ({
  usePathname: () => mockPathname(),
}));

const renderBreadcrumb = (props?: React.ComponentProps<typeof Breadcrumb>) => {
  return render(
    <AppThemeProvider>
      <Breadcrumb {...props} />
    </AppThemeProvider>
  );
};

// Run sequentially to avoid concurrent renders leaking between tests
describe.sequential("Breadcrumb", () => {
  // Store original matchMedia before any tests run
  const originalMatchMedia = typeof window !== 'undefined' ? window.matchMedia : undefined;
  
  beforeEach(() => {
    mockPathname.mockReturnValue("/");
    // Always set up matchMedia mock before each test to ensure it's available
    Object.defineProperty(window, "matchMedia", {
      writable: true,
      configurable: true,
      value: vi.fn((query: string) => ({
        matches: false, // Default to desktop
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      })),
    });
  });

  afterEach(() => {
    cleanup();
    // Always restore matchMedia to ensure test isolation
    vi.restoreAllMocks();
    // Restore original matchMedia or ensure it's a function
    if (originalMatchMedia) {
      Object.defineProperty(window, "matchMedia", {
        writable: true,
        configurable: true,
        value: originalMatchMedia,
      });
    } else {
      // Ensure it's always a function, even if original didn't exist
      Object.defineProperty(window, "matchMedia", {
        writable: true,
        configurable: true,
        value: vi.fn((query: string) => ({
          matches: false,
          media: query,
          onchange: null,
          addListener: vi.fn(),
          removeListener: vi.fn(),
          addEventListener: vi.fn(),
          removeEventListener: vi.fn(),
          dispatchEvent: vi.fn(),
        })),
      });
    }
  });

  describe("Manual items", () => {
    it("renders manual breadcrumb items", () => {
      const items: BreadcrumbItem[] = [
        { label: "Home", href: "/" },
        { label: "Products", href: "/products" },
        { label: "Details" },
      ];

      const { container } = renderBreadcrumb({ items });

      const { getByText } = within(container);
      expect(getByText("Home")).toBeInTheDocument();
      expect(getByText("Products")).toBeInTheDocument();
      expect(getByText("Details")).toBeInTheDocument();
    });

    it("renders items with icons", () => {
      const items: BreadcrumbItem[] = [
        { label: "Home", href: "/", icon: <span data-testid="home-icon">🏠</span> },
        { label: "Settings" },
      ];

      renderBreadcrumb({ items });

      expect(screen.getByTestId("home-icon")).toBeInTheDocument();
    });

    it("handles disabled items", () => {
      const items: BreadcrumbItem[] = [
        { label: "Home", href: "/" },
        { label: "Disabled", href: "/disabled", disabled: true },
      ];

      renderBreadcrumb({ items });

      const disabledLink = screen.queryByRole("link", { name: "Disabled" });
      expect(disabledLink).not.toBeInTheDocument();
    });
  });

  describe("Auto-generation from pathname", () => {
    it("auto-generates breadcrumbs from pathname", () => {
      mockPathname.mockReturnValue("/products/electronics/123");

      const { container } = renderBreadcrumb({ autoGenerate: true });

      const { getByText } = within(container);
      expect(getByText("Home")).toBeInTheDocument();
      expect(getByText("Products")).toBeInTheDocument();
      expect(getByText("Electronics")).toBeInTheDocument();
      expect(getByText("123")).toBeInTheDocument();
    });

    it("handles root pathname", () => {
      mockPathname.mockReturnValue("/");

      renderBreadcrumb({ autoGenerate: true, showHome: true });

      expect(screen.getByText("Home")).toBeInTheDocument();
    });

    it("does not show home when showHome is false", () => {
      mockPathname.mockReturnValue("/products");

      const { container } = renderBreadcrumb({ autoGenerate: true, showHome: false });

      const { queryByText, getByText } = within(container);
      expect(queryByText("Home")).not.toBeInTheDocument();
      expect(getByText("Products")).toBeInTheDocument();
    });

    it("uses route config for labels", () => {
      mockPathname.mockReturnValue("/users/123");

      const routeConfig: RouteConfig[] = [
        { path: "/users", label: "Users" },
        { path: "/users/[id]", label: (params) => `User ${params.id}` },
      ];

      const { container } = renderBreadcrumb({ autoGenerate: true, routeConfig });

      const { getByText } = within(container);
      expect(getByText("Home")).toBeInTheDocument();
      expect(getByText("Users")).toBeInTheDocument();
      expect(getByText("User 123")).toBeInTheDocument();
    });

    it("handles custom home label and href", () => {
      mockPathname.mockReturnValue("/products");

      const { container } = renderBreadcrumb({
        autoGenerate: true,
        homeLabel: "Dashboard",
        homeHref: "/dashboard",
      });

      const { getByText, getByRole } = within(container);
      expect(getByText("Dashboard")).toBeInTheDocument();
      const homeLink = getByRole("link", { name: "Dashboard" });
      expect(homeLink).toHaveAttribute("href", "/dashboard");
    });
  });

  describe("Current page handling", () => {
    it("makes last item non-clickable by default", () => {
      const items: BreadcrumbItem[] = [
        { label: "Home", href: "/" },
        { label: "Products", href: "/products" },
        { label: "Current" },
      ];

      const { container } = renderBreadcrumb({ items });

      const { queryByRole, getByText, getByRole } = within(container);
      const currentLink = queryByRole("link", { name: "Current" });
      expect(currentLink).not.toBeInTheDocument();
      // Verify the current page text is displayed
      const currentElement = getByText("Current");
      expect(currentElement).toBeInTheDocument();
      // Verify there's an element with aria-current="page" on the page
      // (The Box wrapper around the text has the aria-current attribute)
      const navigationElement = getByRole("navigation", { name: "breadcrumb navigation" });
      // eslint-disable-next-line testing-library/no-node-access
      const elementWithAriaCurrent = navigationElement.querySelector('[aria-current="page"]');
      expect(elementWithAriaCurrent).toBeInTheDocument();
      expect(elementWithAriaCurrent).toHaveAttribute("aria-current", "page");
    });

    it("makes last item clickable when currentPageClickable is true", () => {
      const items: BreadcrumbItem[] = [
        { label: "Home", href: "/" },
        { label: "Current", href: "/current" },
      ];

      renderBreadcrumb({ items, currentPageClickable: true });

      const currentLink = screen.getByRole("link", { name: "Current" });
      expect(currentLink).toBeInTheDocument();
      expect(currentLink).toHaveAttribute("href", "/current");
    });
  });

  describe("Max items / truncation", () => {
    it("truncates items when exceeding maxItems", () => {
      mockPathname.mockReturnValue("/a/b/c/d/e/f/g");

      const { container } = renderBreadcrumb({ autoGenerate: true, maxItems: 3 });

      // Should show Home, and last 2 items
      const { getByText, getAllByRole } = within(container);
      expect(getByText("Home")).toBeInTheDocument();
      // The last items should be visible
      const items = getAllByRole("listitem");
      expect(items.length).toBeLessThanOrEqual(3);
    });

    it("does not truncate when items are within maxItems", () => {
      mockPathname.mockReturnValue("/a/b/c");

      const { container } = renderBreadcrumb({ autoGenerate: true, maxItems: 5 });

      const { getByText } = within(container);
      expect(getByText("Home")).toBeInTheDocument();
      expect(getByText("A")).toBeInTheDocument();
      expect(getByText("B")).toBeInTheDocument();
      expect(getByText("C")).toBeInTheDocument();
    });
  });

  describe("Responsive behavior", () => {
    it("collapses on mobile when responsive is true", () => {
      // Mock matchMedia to return true for mobile queries (MUI uses max-width:599.95px for sm breakpoint)
      const originalMatchMedia = window.matchMedia;
      const mockMatchMedia = vi.fn((query: string) => ({
        matches: query.includes("max-width") && (query.includes("599.95") || query.includes("600") || query.includes("599")),
        media: query,
        onchange: null,
        addListener: vi.fn(),
        removeListener: vi.fn(),
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
        dispatchEvent: vi.fn(),
      }));
      
      Object.defineProperty(window, "matchMedia", {
        writable: true,
        configurable: true,
        value: mockMatchMedia,
      });

      try {
        mockPathname.mockReturnValue("/a/b/c/d");

        const { container } = renderBreadcrumb({ autoGenerate: true, responsive: true });

        // On mobile, should show only last 2 items
        const { getAllByRole } = within(container);
        const items = getAllByRole("listitem");
        expect(items.length).toBeLessThanOrEqual(2);
      } finally {
        // Always restore original
        Object.defineProperty(window, "matchMedia", {
          writable: true,
          configurable: true,
          value: originalMatchMedia,
        });
      }
    });

    it("shows all items when responsive is false", () => {
      mockPathname.mockReturnValue("/a/b/c");

      const { container } = renderBreadcrumb({ autoGenerate: true, responsive: false });

      const { getByText } = within(container);
      expect(getByText("Home")).toBeInTheDocument();
      expect(getByText("A")).toBeInTheDocument();
      expect(getByText("B")).toBeInTheDocument();
      expect(getByText("C")).toBeInTheDocument();
    });
  });

  describe("Separator customization", () => {
    it("uses default separator", () => {
      const items: BreadcrumbItem[] = [
        { label: "Home", href: "/" },
        { label: "Products" },
      ];

      renderBreadcrumb({ items });

      // Should have separator (ChevronRight icon by default)
      const breadcrumbs = screen.getByLabelText("breadcrumb navigation");
      expect(breadcrumbs).toBeInTheDocument();
    });

    it("uses custom separator", () => {
      const items: BreadcrumbItem[] = [
        { label: "Home", href: "/" },
        { label: "Products" },
      ];

      const { container } = renderBreadcrumb({ items, separator: ">" });

      // Custom separator should be present
      const { getByText } = within(container);
      expect(getByText(">")).toBeInTheDocument();
    });

    it("uses custom renderSeparator", () => {
      const items: BreadcrumbItem[] = [
        { label: "Home", href: "/" },
        { label: "Products" },
      ];

      renderBreadcrumb({
        items,
        renderSeparator: () => <span data-testid="custom-separator">→</span>,
      });

      expect(screen.getByTestId("custom-separator")).toBeInTheDocument();
    });
  });

  describe("Variants", () => {
    it("applies default variant styles", () => {
      const items: BreadcrumbItem[] = [{ label: "Home", href: "/" }];

      renderBreadcrumb({ items, variant: "default" });
      const breadcrumbs = screen.getByLabelText("breadcrumb navigation");
      expect(breadcrumbs).toBeInTheDocument();
    });

    it("applies compact variant styles", () => {
      const items: BreadcrumbItem[] = [{ label: "Home", href: "/" }];

      renderBreadcrumb({ items, variant: "compact" });
      const breadcrumbs = screen.getByLabelText("breadcrumb navigation");
      expect(breadcrumbs).toBeInTheDocument();
    });

    it("applies minimal variant styles", () => {
      const items: BreadcrumbItem[] = [{ label: "Home", href: "/" }];

      renderBreadcrumb({ items, variant: "minimal" });
      const breadcrumbs = screen.getByLabelText("breadcrumb navigation");
      expect(breadcrumbs).toBeInTheDocument();
    });
  });

  describe("Sizes", () => {
    it("applies small size", () => {
      const items: BreadcrumbItem[] = [{ label: "Home", href: "/" }];

      const { container } = renderBreadcrumb({ items, size: "small" });
      const { getByText } = within(container);
      expect(getByText("Home")).toBeInTheDocument();
    });

    it("applies medium size", () => {
      const items: BreadcrumbItem[] = [{ label: "Home", href: "/" }];

      const { container } = renderBreadcrumb({ items, size: "medium" });
      const { getByText } = within(container);
      expect(getByText("Home")).toBeInTheDocument();
    });

    it("applies large size", () => {
      const items: BreadcrumbItem[] = [{ label: "Home", href: "/" }];

      const { container } = renderBreadcrumb({ items, size: "large" });
      const { getByText } = within(container);
      expect(getByText("Home")).toBeInTheDocument();
    });
  });

  describe("Custom rendering", () => {
    it("uses custom renderItem", () => {
      const items: BreadcrumbItem[] = [
        { label: "Home", href: "/" },
        { label: "Products" },
      ];

      renderBreadcrumb({
        items,
        renderItem: (item, index, isLast) => (
          <span data-testid={`custom-item-${index}`} data-last={isLast}>
            {item.label}
          </span>
        ),
      });

      expect(screen.getByTestId("custom-item-0")).toBeInTheDocument();
      expect(screen.getByTestId("custom-item-1")).toBeInTheDocument();
      expect(screen.getByTestId("custom-item-1")).toHaveAttribute("data-last", "true");
    });
  });

  describe("Accessibility", () => {
    it("has proper aria-label", () => {
      const items: BreadcrumbItem[] = [{ label: "Home", href: "/" }];

      renderBreadcrumb({ items });

      expect(screen.getByLabelText("breadcrumb navigation")).toBeInTheDocument();
    });

    it("marks current page with aria-current", () => {
      const items: BreadcrumbItem[] = [
        { label: "Home", href: "/" },
        { label: "Current" },
      ];

      const { container } = renderBreadcrumb({ items });

      // Verify the current page text is displayed
      const { getByText, getByRole } = within(container);
      const currentElement = getByText("Current");
      expect(currentElement).toBeInTheDocument();
      // Verify there's an element with aria-current="page" on the page
      // (The Box wrapper around the text has the aria-current attribute)
      const navigationElement = getByRole("navigation", { name: "breadcrumb navigation" });
      // eslint-disable-next-line testing-library/no-node-access
      const elementWithAriaCurrent = navigationElement.querySelector('[aria-current="page"]');
      expect(elementWithAriaCurrent).toBeInTheDocument();
      expect(elementWithAriaCurrent).toHaveAttribute("aria-current", "page");
    });
  });

  describe("Test IDs", () => {
    it("generates test ID from name prop", () => {
      const items: BreadcrumbItem[] = [{ label: "Home", href: "/" }];

      renderBreadcrumb({ items, name: "main-nav" });
      const breadcrumbs = screen.getByTestId("breadcrumb-main-nav");
      expect(breadcrumbs).toBeInTheDocument();
    });

    it("uses custom data-testid when provided", () => {
      const items: BreadcrumbItem[] = [{ label: "Home", href: "/" }];

      renderBreadcrumb({ items, "data-testid": "custom-id" });
      const breadcrumbs = screen.getByTestId("custom-id");
      expect(breadcrumbs).toBeInTheDocument();
    });
  });

  describe("Edge cases", () => {
    it("returns null when no items", () => {
      renderBreadcrumb({ items: [], autoGenerate: false });
      const breadcrumbs = screen.queryByLabelText("breadcrumb navigation");
      expect(breadcrumbs).not.toBeInTheDocument();
    });

    it("handles null pathname", () => {
      mockPathname.mockReturnValue(null);

      const { container } = renderBreadcrumb({ autoGenerate: true, showHome: true });

      const { getByText } = within(container);
      expect(getByText("Home")).toBeInTheDocument();
    });

    it("handles empty pathname", () => {
      mockPathname.mockReturnValue("");

      const { container } = renderBreadcrumb({ autoGenerate: true, showHome: true });

      const { getByText } = within(container);
      expect(getByText("Home")).toBeInTheDocument();
    });

    it("handles pathname with trailing slash", () => {
      mockPathname.mockReturnValue("/products/");

      const { container } = renderBreadcrumb({ autoGenerate: true });

      const { getByText } = within(container);
      expect(getByText("Home")).toBeInTheDocument();
      expect(getByText("Products")).toBeInTheDocument();
    });

    it("handles pathname with multiple consecutive slashes", () => {
      mockPathname.mockReturnValue("//products///electronics");

      const { container } = renderBreadcrumb({ autoGenerate: true });

      const { getByText } = within(container);
      expect(getByText("Home")).toBeInTheDocument();
      expect(getByText("Products")).toBeInTheDocument();
      expect(getByText("Electronics")).toBeInTheDocument();
    });

    it("handles special characters in pathname segments", () => {
      mockPathname.mockReturnValue("/products/my-product-123");

      const { container } = renderBreadcrumb({ autoGenerate: true });

      const { getByText } = within(container);
      expect(getByText("Home")).toBeInTheDocument();
      expect(getByText("Products")).toBeInTheDocument();
      expect(getByText("My Product 123")).toBeInTheDocument();
    });

    it("handles maxItems of 0 (no limit)", () => {
      mockPathname.mockReturnValue("/a/b/c/d/e/f");

      renderBreadcrumb({ autoGenerate: true, maxItems: 0 });

      // Should show all items
      expect(screen.getByText("Home")).toBeInTheDocument();
      expect(screen.getByText("A")).toBeInTheDocument();
      expect(screen.getByText("B")).toBeInTheDocument();
      expect(screen.getByText("C")).toBeInTheDocument();
      expect(screen.getByText("D")).toBeInTheDocument();
      expect(screen.getByText("E")).toBeInTheDocument();
      expect(screen.getByText("F")).toBeInTheDocument();
    });

    it("handles maxItems of 1", () => {
      mockPathname.mockReturnValue("/a/b/c");

      const { container } = renderBreadcrumb({ autoGenerate: true, maxItems: 1 });

      // Should show only last item (or home if only one item)
      // Scope query to container to avoid finding items from other tests
      const { getAllByRole } = within(container);
      const items = getAllByRole("listitem");
      expect(items.length).toBeLessThanOrEqual(2); // Home + last item
    });

    it("handles maxItems of 2", () => {
      mockPathname.mockReturnValue("/a/b/c/d");

      renderBreadcrumb({ autoGenerate: true, maxItems: 2 });

      // Should show home and last item
      expect(screen.getByText("Home")).toBeInTheDocument();
      const items = screen.getAllByRole("listitem");
      expect(items.length).toBeLessThanOrEqual(2);
    });
  });

  describe("Route config edge cases", () => {
    it("handles route config with catch-all routes", () => {
      mockPathname.mockReturnValue("/docs/getting-started/installation");

      const routeConfig: RouteConfig[] = [
        { path: "/docs", label: "Documentation" },
        { path: "/docs/[...slug]", label: (params) => params.slug?.replace(/-/g, " ") || "" },
      ];

      renderBreadcrumb({ autoGenerate: true, routeConfig });

      expect(screen.getByText("Home")).toBeInTheDocument();
      expect(screen.getByText("Documentation")).toBeInTheDocument();
    });

    it("handles route config with optional segments", () => {
      mockPathname.mockReturnValue("/users/123");

      const routeConfig: RouteConfig[] = [
        { path: "/users", label: "Users" },
        { path: "/users/[id]", label: (params) => `User #${params.id}` },
      ];

      renderBreadcrumb({ autoGenerate: true, routeConfig });

      expect(screen.getByText("Home")).toBeInTheDocument();
      expect(screen.getByText("Users")).toBeInTheDocument();
      expect(screen.getByText("User #123")).toBeInTheDocument();
    });

    it("falls back to default formatting when route config doesn't match", () => {
      mockPathname.mockReturnValue("/unknown/path");

      const routeConfig: RouteConfig[] = [
        { path: "/known", label: "Known" },
      ];

      renderBreadcrumb({ autoGenerate: true, routeConfig });

      expect(screen.getByText("Home")).toBeInTheDocument();
      expect(screen.getByText("Unknown")).toBeInTheDocument();
      expect(screen.getByText("Path")).toBeInTheDocument();
    });
  });

  describe("Integration scenarios", () => {
    it("works with manual items and custom home", () => {
      const items: BreadcrumbItem[] = [
        { label: "Dashboard", href: "/dashboard" },
        { label: "Settings", href: "/settings" },
        { label: "Profile" },
      ];

      renderBreadcrumb({
        items,
        homeLabel: "Dashboard",
        homeHref: "/dashboard",
        showHome: false, // Don't show home since first item is already dashboard
      });

      expect(screen.getByText("Dashboard")).toBeInTheDocument();
      expect(screen.getByText("Settings")).toBeInTheDocument();
      expect(screen.getByText("Profile")).toBeInTheDocument();
    });

    it("works with variant and size together", () => {
      const items: BreadcrumbItem[] = [
        { label: "Home", href: "/" },
        { label: "Products" },
      ];

      renderBreadcrumb({ items, variant: "compact", size: "small" });

      expect(screen.getByText("Home")).toBeInTheDocument();
      expect(screen.getByText("Products")).toBeInTheDocument();
    });

    it("works with responsive and maxItems together", () => {
      mockPathname.mockReturnValue("/a/b/c/d/e");

      // Mock desktop viewport
      Object.defineProperty(window, "matchMedia", {
        writable: true,
        value: vi.fn().mockImplementation((query) => ({
          matches: false, // Desktop
          media: query,
          onchange: null,
          addListener: vi.fn(),
          removeListener: vi.fn(),
          addEventListener: vi.fn(),
          removeEventListener: vi.fn(),
          dispatchEvent: vi.fn(),
        })),
      });

      renderBreadcrumb({ autoGenerate: true, maxItems: 3, responsive: true });

      // On desktop, should respect maxItems
      expect(screen.getByText("Home")).toBeInTheDocument();
      const items = screen.getAllByRole("listitem");
      expect(items.length).toBeLessThanOrEqual(3);
    });

    it("handles items with all properties set", () => {
      // Ensure matchMedia is set up for this specific test
      Object.defineProperty(window, "matchMedia", {
        writable: true,
        configurable: true,
        value: vi.fn((query: string) => ({
          matches: false,
          media: query,
          onchange: null,
          addListener: vi.fn(),
          removeListener: vi.fn(),
          addEventListener: vi.fn(),
          removeEventListener: vi.fn(),
          dispatchEvent: vi.fn(),
        })),
      });

      const items: BreadcrumbItem[] = [
        {
          label: "Home",
          href: "/",
          icon: <span data-testid="home-icon">🏠</span>,
          disabled: false,
        },
        {
          label: "Products",
          href: "/products",
          icon: <span data-testid="products-icon">📦</span>,
          disabled: false,
        },
        {
          label: "Current",
          icon: <span data-testid="current-icon">📍</span>,
        },
      ];

      renderBreadcrumb({ items });

      expect(screen.getByTestId("home-icon")).toBeInTheDocument();
      expect(screen.getByTestId("products-icon")).toBeInTheDocument();
      expect(screen.getByTestId("current-icon")).toBeInTheDocument();
      expect(screen.getByText("Home")).toBeInTheDocument();
      expect(screen.getByText("Products")).toBeInTheDocument();
      expect(screen.getByText("Current")).toBeInTheDocument();
    });
  });

  describe("Manual vs auto-generation priority", () => {
    it("uses manual items when both manual and autoGenerate are provided", () => {
      // Ensure matchMedia is set up for this specific test
      Object.defineProperty(window, "matchMedia", {
        writable: true,
        configurable: true,
        value: vi.fn((query: string) => ({
          matches: false,
          media: query,
          onchange: null,
          addListener: vi.fn(),
          removeListener: vi.fn(),
          addEventListener: vi.fn(),
          removeEventListener: vi.fn(),
          dispatchEvent: vi.fn(),
        })),
      });

      mockPathname.mockReturnValue("/auto/path");

      const items: BreadcrumbItem[] = [
        { label: "Manual", href: "/manual" },
        { label: "Item" },
      ];

      renderBreadcrumb({ items, autoGenerate: true });

      expect(screen.getByText("Manual")).toBeInTheDocument();
      expect(screen.getByText("Item")).toBeInTheDocument();
      expect(screen.queryByText("Auto")).not.toBeInTheDocument();
      expect(screen.queryByText("Path")).not.toBeInTheDocument();
    });

    it("uses auto-generation when manual items are not provided", () => {
      // Ensure matchMedia is set up for this specific test
      Object.defineProperty(window, "matchMedia", {
        writable: true,
        configurable: true,
        value: vi.fn((query: string) => ({
          matches: false,
          media: query,
          onchange: null,
          addListener: vi.fn(),
          removeListener: vi.fn(),
          addEventListener: vi.fn(),
          removeEventListener: vi.fn(),
          dispatchEvent: vi.fn(),
        })),
      });

      mockPathname.mockReturnValue("/auto/path");

      renderBreadcrumb({ autoGenerate: true });

      expect(screen.getByText("Home")).toBeInTheDocument();
      expect(screen.getByText("Auto")).toBeInTheDocument();
      expect(screen.getByText("Path")).toBeInTheDocument();
    });
  });
});
