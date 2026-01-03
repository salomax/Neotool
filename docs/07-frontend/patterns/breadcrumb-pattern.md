---
title: Breadcrumb Pattern
type: pattern
category: frontend
status: current
version: 1.0.0
tags: [breadcrumb, navigation, ui, frontend, accessibility]
ai_optimized: true
search_keywords: [breadcrumb, breadcrumbs, navigation, page hierarchy, navigation path, breadcrumb component]
related:
  - 04-patterns/frontend-patterns/shared-components-pattern.md
  - 04-patterns/frontend-patterns/management-pattern.md
---

# Breadcrumb Pattern

> **Purpose**: Standard pattern for implementing breadcrumb navigation to show page hierarchy and enable quick navigation to parent pages.

## Overview

The Breadcrumb Pattern provides a consistent way to display navigation hierarchy across the application. Breadcrumbs help users understand their location within the application structure and provide quick navigation to parent pages.

**Location**: `web/src/shared/components/ui/navigation/Breadcrumb.tsx`

## When to Use Breadcrumbs

Use breadcrumbs when:
- **Deep Navigation**: Users navigate through multiple levels of pages (3+ levels)
- **Hierarchical Structure**: The application has a clear parent-child page relationship
- **Context Awareness**: Users need to understand where they are in the application
- **Quick Navigation**: Users frequently need to navigate back to parent pages

**Don't use breadcrumbs when:**
- **Shallow Navigation**: Only 1-2 levels of navigation
- **Flat Structure**: No clear hierarchy between pages
- **Home Page**: User is on the root/home page
- **Alternative Flows**: Pages are siblings rather than hierarchical

## Basic Usage

### Auto-Generation from URL Path

The simplest usage automatically generates breadcrumbs from the current URL path:

```typescript
import { Breadcrumb } from "@/shared/components/ui/navigation";

function ProductDetailPage() {
  return (
    <div>
      <Breadcrumb />
      {/* Page content */}
    </div>
  );
}
```

**Example URLs and Generated Breadcrumbs:**
- `/products` → `Home > Products`
- `/products/electronics` → `Home > Products > Electronics`
- `/products/electronics/123` → `Home > Products > Electronics > 123`

### Manual Items

For custom navigation flows or when URL structure doesn't match the logical hierarchy, use manual items:

```typescript
import { Breadcrumb, BreadcrumbItem } from "@/shared/components/ui/navigation";

function PageB() {
  const items: BreadcrumbItem[] = [
    { label: "Page A", href: "/a" },
    { label: "Page B" }, // Current page (no href)
  ];

  return (
    <div>
      <Breadcrumb items={items} />
      {/* Page content */}
    </div>
  );
}
```

## Common Scenarios

### Scenario 1: Hierarchical URL Structure

When your URLs reflect the page hierarchy, auto-generation works perfectly:

```typescript
// URL: /products/electronics/laptops
<Breadcrumb />
// Displays: Home > Products > Electronics > Laptops
```

### Scenario 2: Alternative Navigation Flows

When pages are siblings but you want to show a logical flow, use manual items:

```typescript
// User can navigate: Page A → Page B OR Page A → Page C
// Both Page B and Page C are siblings, not children

// On Page B:
<Breadcrumb items={[
  { label: "Page A", href: "/a" },
  { label: "Page B" }
]} />

// On Page C:
<Breadcrumb items={[
  { label: "Page A", href: "/a" },
  { label: "Page C" }
]} />
```

### Scenario 3: Dynamic Routes with Custom Labels

Use route configuration to provide meaningful labels for dynamic routes:

```typescript
import { Breadcrumb, RouteConfig } from "@/shared/components/ui/navigation";

function UserProfilePage({ userId }: { userId: string }) {
  const routeConfig: RouteConfig[] = [
    { path: "/users", label: "Users" },
    { 
      path: "/users/[id]", 
      label: (params) => `User ${params.id}` 
    },
  ];

  return (
    <div>
      <Breadcrumb routeConfig={routeConfig} />
      {/* Page content */}
    </div>
  );
}
```

**URL**: `/users/123`  
**Breadcrumb**: `Home > Users > User 123`

## API Reference

### BreadcrumbItem

```typescript
interface BreadcrumbItem {
  label: string;           // Display text
  href?: string;          // Optional link (if not provided, item is non-clickable)
  icon?: React.ReactNode; // Optional icon
  disabled?: boolean;     // Disable navigation
}
```

### RouteConfig

```typescript
interface RouteConfig {
  path: string;
  label: string | ((params: Record<string, string>) => string);
  icon?: React.ReactNode;
}
```

### BreadcrumbProps

```typescript
interface BreadcrumbProps {
  // Manual control
  items?: BreadcrumbItem[];
  
  // Auto-generation
  autoGenerate?: boolean;        // Default: true
  routeConfig?: RouteConfig[];
  homeLabel?: string;            // Default: "Home"
  homeHref?: string;             // Default: "/"
  homeIcon?: React.ReactNode;    // Default: HomeIcon
  
  // Behavior
  maxItems?: number;             // Default: 5
  showHome?: boolean;             // Default: true
  currentPageClickable?: boolean; // Default: false
  responsive?: boolean;           // Default: true
  
  // Customization
  separator?: React.ReactNode;    // Default: ChevronRightIcon
  variant?: "default" | "compact" | "minimal";
  size?: "small" | "medium" | "large";
  
  // Rendering
  renderItem?: (item: BreadcrumbItem, index: number, isLast: boolean) => React.ReactNode;
  renderSeparator?: () => React.ReactNode;
  
  // Styling
  className?: string;
  sx?: SxProps<Theme>;
  name?: string;
  "data-testid"?: string;
}
```

## Configuration Options

### Auto-Generation Behavior

```typescript
// Disable auto-generation
<Breadcrumb autoGenerate={false} items={customItems} />

// Custom home label and href
<Breadcrumb 
  homeLabel="Dashboard"
  homeHref="/dashboard"
  homeIcon={<DashboardIcon />}
/>

// Hide home item
<Breadcrumb showHome={false} />
```

### Truncation

When breadcrumbs exceed `maxItems`, the component automatically truncates while keeping the first (home) and last (current) items:

```typescript
// Limit to 3 visible items
<Breadcrumb maxItems={3} />
// URL: /a/b/c/d/e/f
// Displays: Home > ... > E > F
```

### Responsive Behavior

On mobile devices, breadcrumbs automatically collapse to show only the last 2 items:

```typescript
// Responsive (default)
<Breadcrumb responsive={true} />
// Desktop: Home > Products > Electronics > Laptops
// Mobile: Electronics > Laptops

// Always show all items
<Breadcrumb responsive={false} />
```

### Variants and Sizes

```typescript
// Compact variant with small size
<Breadcrumb variant="compact" size="small" />

// Minimal variant (uses "/" separator)
<Breadcrumb variant="minimal" />

// Large size
<Breadcrumb size="large" />
```

### Custom Rendering

```typescript
// Custom item rendering
<Breadcrumb
  renderItem={(item, index, isLast) => (
    <CustomItem 
      item={item} 
      isLast={isLast}
      index={index}
    />
  )}
/>

// Custom separator
<Breadcrumb
  renderSeparator={() => <span className="custom-separator">→</span>}
/>
```

## Best Practices

### 1. Use Auto-Generation When Possible

Prefer auto-generation when your URL structure matches the logical hierarchy:

```typescript
// ✅ Good: URL structure matches hierarchy
// /products/electronics/laptops
<Breadcrumb />

// ❌ Avoid: Manual items when auto-generation would work
<Breadcrumb items={[
  { label: "Home", href: "/" },
  { label: "Products", href: "/products" },
  { label: "Electronics", href: "/products/electronics" },
  { label: "Laptops" }
]} />
```

### 2. Use Manual Items for Alternative Flows

When pages are siblings but represent a logical flow:

```typescript
// ✅ Good: Shows logical flow even though pages are siblings
<Breadcrumb items={[
  { label: "Page A", href: "/a" },
  { label: "Page B" }
]} />
```

### 3. Keep Breadcrumbs Concise

Limit breadcrumb depth to 4-5 levels maximum:

```typescript
// ✅ Good: Reasonable depth
<Breadcrumb maxItems={5} />

// ❌ Avoid: Too many levels
<Breadcrumb maxItems={10} />
```

### 4. Use Meaningful Labels

Provide clear, user-friendly labels:

```typescript
// ✅ Good: Clear labels
const routeConfig: RouteConfig[] = [
  { path: "/users", label: "Users" },
  { path: "/users/[id]", label: (params) => `User ${params.id}` },
];

// ❌ Avoid: Technical labels
const routeConfig: RouteConfig[] = [
  { path: "/users", label: "users" },
  { path: "/users/[id]", label: (params) => params.id },
];
```

### 5. Don't Show Breadcrumbs on Home Page

Breadcrumbs are unnecessary on the root/home page:

```typescript
function HomePage() {
  const pathname = usePathname();
  
  return (
    <div>
      {pathname !== "/" && <Breadcrumb />}
      {/* Page content */}
    </div>
  );
}
```

### 6. Use Route Config for Dynamic Routes

Always provide route configuration for dynamic routes to show meaningful labels:

```typescript
// ✅ Good: Custom labels for dynamic routes
const routeConfig: RouteConfig[] = [
  { path: "/products/[id]", label: (params) => `Product ${params.id}` },
];

// ❌ Avoid: Auto-generated labels for dynamic routes
// Results in: Home > Products > 123 (not user-friendly)
```

## Accessibility

The Breadcrumb component includes built-in accessibility features:

- **ARIA Labels**: Uses `aria-label="breadcrumb navigation"`
- **Current Page**: Last item has `aria-current="page"`
- **Semantic HTML**: Uses proper `<nav>` element
- **Keyboard Navigation**: All links are keyboard accessible

## Examples

### Management Page with Breadcrumb

```typescript
import { Breadcrumb } from "@/shared/components/ui/navigation";
import { ManagementLayout } from "@/shared/components/management/ManagementLayout";

function UserManagementPage() {
  return (
    <ManagementLayout>
      <ManagementLayout.Header>
        <Breadcrumb />
        <UserSearch />
      </ManagementLayout.Header>
      <ManagementLayout.Content>
        <UserList />
      </ManagementLayout.Content>
    </ManagementLayout>
  );
}
```

### Detail Page with Custom Breadcrumb

```typescript
import { Breadcrumb, BreadcrumbItem } from "@/shared/components/ui/navigation";

function ProductDetailPage({ productId }: { productId: string }) {
  const { product } = useProduct(productId);
  
  const items: BreadcrumbItem[] = [
    { label: "Home", href: "/" },
    { label: "Products", href: "/products" },
    { label: product.category, href: `/products/${product.category}` },
    { label: product.name },
  ];

  return (
    <div>
      <Breadcrumb items={items} />
      <ProductDetails product={product} />
    </div>
  );
}
```

### Multi-Step Form with Breadcrumb

```typescript
import { Breadcrumb, BreadcrumbItem } from "@/shared/components/ui/navigation";

function MultiStepForm({ currentStep }: { currentStep: number }) {
  const steps = ["Personal Info", "Contact Details", "Review"];
  
  const items: BreadcrumbItem[] = [
    { label: "Home", href: "/" },
    { label: "Registration", href: "/register" },
    { label: steps[currentStep - 1] },
  ];

  return (
    <div>
      <Breadcrumb items={items} />
      <FormStep step={currentStep} />
    </div>
  );
}
```

## Related Documentation

- [Shared Components Pattern](./shared-components-pattern.md) - General component patterns
- [Management Pattern](./management-pattern.md) - Using breadcrumbs in management pages
- [Navigation Components](../../../web/src/shared/components/ui/navigation/) - Other navigation components
