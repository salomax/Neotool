---
title: Web Frontend Structure
type: guide
category: frontend
status: current
version: 1.0.0
tags: [frontend, structure, organization, nextjs, best-practices]
related:
  - ARCHITECTURE_OVERVIEW.md
  - adr/0004-typescript-nextjs-frontend.md
  - web/web-components.md
  - web/web-themes.md
---

# Web Frontend Structure - Best Practices

This document defines the best practices for organizing the `web/src/` directory in the boilerplate project.

> **Important Note**: The `customers`, `products`, and `orders` examples found in the codebase are **reference examples only** and should be removed in production. This documentation uses generic domain names (`domain-a`, `domain-b`) to represent your actual business domains.

## Directory Structure Overview

```
web/src/
├── app/                          # Next.js App Router (pages & layouts)
│   ├── (neotool)/               # Route group (doesn't affect URL)
│   │   ├── dashboard/           # Dashboard pages
│   │   ├── examples/            # Example pages
│   │   ├── documentation/       # Documentation pages
│   │   └── design-system/       # Design system showcase
│   ├── api/                     # API routes
│   ├── layout.tsx               # Root layout
│   ├── page.tsx                 # Home page
│   ├── not-found.tsx            # 404 page
│   └── providers.tsx            # Global providers
├── lib/                         # External integrations & utilities
│   ├── api/                     # API clients & providers
│   ├── graphql/                 # GraphQL operations & client
│   │   ├── client.ts            # Apollo Client setup
│   │   ├── GraphQLProvider.tsx  # GraphQL provider
│   │   ├── operations/          # Domain-organized operations
│   │   │   ├── domain-a/        # Domain A operations (example)
│   │   │   └── domain-b/        # Domain B operations (example)
│   │   ├── fragments/           # Reusable fragments
│   │   └── types/               # Generated types
│   ├── hooks/                   # Domain-specific business logic hooks
│   │   └── domain-a/            # Domain A hooks (example)
│   ├── domain/                  # Domain-specific code
│   │   └── hooks/               # Future: domain hooks location
│   └── [other-integrations]/    # Other external services
├── shared/                      # Shared application code
│   ├── components/              # Reusable UI components
│   │   └── ui/                  # Functional component system
│   │       ├── primitives/      # Basic UI building blocks
│   │       ├── layout/          # Layout and structure components
│   │       ├── navigation/      # Navigation components
│   │       ├── data-display/    # Data visualization components
│   │       ├── forms/           # Form input components
│   │       ├── feedback/        # User feedback components
│   │       ├── patterns/        # Complex compositions
│   │       └── data-table/      # Data table components (legacy)
│   ├── config/                  # Application configuration
│   ├── hooks/                   # Reusable utility hooks
│   ├── i18n/                    # Internationalization
│   ├── providers/               # React context providers
│   ├── store/                   # State management
│   ├── types/                   # TypeScript type definitions
│   ├── ui/                      # UI-specific components
│   │   ├── brand/               # Brand components
│   │   ├── cards/               # Card components
│   │   ├── navigation/          # Navigation components
│   │   └── shell/               # Layout shell components
│   └── utils/                   # Utility functions
├── stories/                     # Storybook stories
├── styles/                      # Global styles & themes
└── types/                       # Global TypeScript types
```

## Core Principles

### 1. Separation of Concerns
- **`app/`**: Next.js routing and page components
- **`lib/`**: External service integrations
- **`shared/`**: Reusable application code
- **`stories/`**: Component documentation and examples

### 2. Functional Component Grouping
- **Primitives**: Basic UI building blocks (Button, Input, Icon, Badge)
- **Layout**: Structure components (Stack, Paper, Frame, Grid)
- **Navigation**: Navigation components (Tabs, Link)
- **Data Display**: Data visualization (DataTable, Chart)
- **Forms**: Form input components (TextField, SelectField, DatePicker)
- **Feedback**: User feedback (Toast, Dialog, Tooltip)
- **Patterns**: Complex compositions and reusable patterns

### 3. Feature-Based Organization
- Group related functionality together
- Keep examples organized by feature domain
- Maintain clear boundaries between features

### 4. Consistent Naming Conventions
- **Files**: kebab-case (`user-profile.tsx`)
- **Components**: PascalCase (`UserProfile`)
- **Hooks**: camelCase with `use` prefix (`useUserProfile`)
- **Types**: PascalCase (`UserProfileType`)

## Directory Guidelines

### `app/` Directory (Next.js App Router)
```
app/
├── (neotool)/                 # Route group (doesn't affect URL)
│   ├── dashboard/             # Dashboard pages
│   ├── examples/              # Example pages
│   │   ├── customers/         # Customer examples
│   │   │   ├── i18n/         # Domain-specific translations
│   │   │   ├── components/   # Page-specific components
│   │   │   └── page.tsx
│   │   ├── products/         # Product examples
│   │   └── ...
│   ├── documentation/         # Documentation pages
│   └── design-system/         # Design system showcase
├── api/                       # API routes
│   └── sourcemaps/           # Source map API
├── layout.tsx                 # Root layout
├── page.tsx                   # Home page
├── not-found.tsx              # 404 page
└── providers.tsx              # Global providers
```

**Guidelines:**
- Use route groups `(name)` for organization without affecting URLs
- Keep page components focused on layout and data fetching
- Extract complex logic to custom hooks or services
- Use consistent naming for special files (`loading.tsx`, `error.tsx`)
- Domain-specific i18n should be co-located with pages in `[domain]/i18n/`

### `lib/` Directory (External Integrations)
```
lib/
├── api/                       # API clients
│   ├── AppQueryProvider.tsx   # Query client provider
│   └── [service]/             # Service-specific clients
├── graphql/                   # GraphQL operations
│   ├── client.ts              # Apollo Client setup
│   ├── operations/            # Domain-organized operations
│   │   ├── domain-a/          # Domain A operations (example)
│   │   │   ├── queries.ts     # Domain A queries
│   │   │   ├── mutations.ts   # Domain A mutations
│   │   │   └── index.ts       # Operations exports
│   │   └── domain-b/          # Domain B operations (example)
│   ├── fragments/             # Reusable fragments
│   └── types.ts               # GraphQL types
├── hooks/                     # Domain-specific business logic hooks
│   ├── domain-a/              # Domain A hooks (example)
│   │   ├── useDomainA.ts      # Domain A management
│   │   └── index.ts           # Hook exports
│   └── domain-b/              # Domain B hooks (example)
│       ├── useDomainB.ts      # Domain B management
│       └── index.ts           # Hook exports
└── [integration]/             # Other external services
    ├── client.ts              # Service client
    ├── types.ts               # Service types
    └── utils.ts               # Service utilities
```

**Guidelines:**
- One directory per external service
- Keep integration logic separate from business logic
- Use consistent file naming (`client.ts`, `types.ts`, `utils.ts`)
- Export everything through index files
- Domain hooks organized by domain in `lib/hooks/[domain]/`
- Domain hooks contain business logic and integrate with external services
- Keep domain hooks focused on single domain responsibility

### `shared/` Directory (Reusable Code)
```
shared/
├── components/                # Reusable UI components
│   └── ui/                    # Functional component system
│       ├── primitives/        # Basic building blocks
│       ├── layout/            # Layout and structure
│       ├── navigation/        # Navigation components
│       ├── data-display/      # Data visualization
│       ├── forms/             # Form components
│       ├── feedback/          # User feedback
│       ├── patterns/          # Complex compositions
│       └── data-table/        # Data table (legacy)
├── config/                    # Application configuration
│   ├── nav.config.ts          # Navigation configuration
│   └── [feature].config.ts    # Feature-specific config
├── hooks/                     # Reusable utility hooks
│   ├── useAutoSave.ts         # Auto-save functionality
│   ├── useDataTableQuery.ts   # Data table pagination
│   ├── useResponsive.ts       # Responsive breakpoints
│   └── useZodForm.ts          # Form validation
├── i18n/                      # Internationalization
│   ├── client.ts              # i18n client setup
│   ├── config.ts              # i18n configuration
│   └── locales/               # Translation files
├── providers/                 # React context providers
│   ├── index.ts               # Provider exports
│   └── [Provider].tsx         # Individual providers
├── store/                     # State management
│   ├── index.ts               # Store exports
│   └── [store].ts             # Individual stores
├── types/                     # TypeScript types
│   └── [feature].d.ts         # Feature-specific types
├── ui/                        # UI-specific components
│   ├── brand/                 # Brand components
│   ├── cards/                 # Card components
│   ├── navigation/            # Navigation components
│   └── shell/                 # Layout shell components
└── utils/                     # Utility functions
    ├── [feature]/             # Feature-specific utilities
    └── [utility].ts           # General utilities
```

**Guidelines:**
- Use atomic design principles for components
- Keep business logic in domain hooks (`lib/hooks/[domain]/`)
- Keep utility hooks in `shared/hooks/` (framework-agnostic)
- Organize utilities by feature when they grow large
- Use consistent naming patterns

## Component Organization

### Functional Component System
```
shared/components/ui/
├── primitives/                # Basic building blocks
│   ├── Button/                # Component directory
│   │   ├── Button.tsx         # Component implementation
│   │   ├── Button.stories.tsx # Storybook stories
│   │   ├── Button.test.tsx    # Unit tests
│   │   └── index.ts           # Component export
│   ├── Avatar/
│   ├── Badge/
│   └── index.ts               # All primitives export
├── layout/                    # Layout components
│   ├── Stack/
│   ├── Paper/
│   ├── Frame/
│   └── index.ts
├── navigation/                # Navigation components
│   ├── Tabs/
│   ├── Link/
│   └── index.ts
├── data-display/              # Data visualization
│   ├── DataTable/
│   ├── Chart/
│   └── index.ts
├── forms/                     # Form components
│   ├── TextField/
│   ├── SelectField/
│   └── index.ts
├── feedback/                  # User feedback
│   ├── ToastProvider/
│   ├── ConfirmDialog/
│   └── index.ts
└── index.ts                   # All UI components export
```

**Guidelines:**
- Components organized by functional purpose, not complexity
- One directory per component (when needed)
- Include stories and tests alongside components
- Use index files for clean exports
- Follow functional grouping principles

## File Naming Conventions

### Components
- **Files**: `ComponentName.tsx`
- **Directories**: `ComponentName/`
- **Stories**: `ComponentName.stories.tsx`
- **Tests**: `ComponentName.test.tsx`

### Hooks
- **Files**: `useHookName.ts`
- **Custom hooks**: Always start with `use`

### Utilities
- **Files**: `utility-name.ts`
- **Directories**: `utility-name/`

### Types
- **Files**: `feature-name.d.ts` or `feature-name.types.ts`
- **Interfaces**: `FeatureNameInterface`
- **Types**: `FeatureNameType`

## Import/Export Patterns

### Index Files
```typescript
// atoms/index.ts
export { default as Button } from './Button';
export { default as Input } from './Input';
export { default as Icon } from './Icon';

// shared/components/ui/index.ts
export * from './atoms';
export * from './molecules';
export * from './organisms';
```

### Component Exports
```typescript
// Button/Button.tsx
export interface ButtonProps {
  variant?: 'primary' | 'secondary';
  size?: 'sm' | 'md' | 'lg';
}

export default function Button({ variant = 'primary', size = 'md', ...props }: ButtonProps) {
  // Component implementation
}

// Button/index.ts
export { default } from './Button';
export type { ButtonProps } from './Button';
```

## Testing Strategy

### Test Organization
```
shared/components/ui/atoms/Button/
├── Button.tsx
├── Button.stories.tsx
├── Button.test.tsx
└── index.ts
```

### Test Naming
- **Unit tests**: `ComponentName.test.tsx`
- **Integration tests**: `ComponentName.integration.test.tsx`
- **E2E tests**: `feature-name.e2e.test.tsx`

## Documentation Standards

### Component Documentation
- Include JSDoc comments for props
- Provide usage examples in stories
- Document accessibility features
- Include design system guidelines

### Storybook Stories
- Use `.stories.tsx` extension
- Follow Storybook naming conventions
- Include all component variants
- Provide interactive examples

## Internationalization (i18n)

The project uses a scalable i18n architecture with domain-specific translations:

```
shared/i18n/                    # Core i18n configuration
├── config.ts                   # Main i18n setup
├── hooks/useTranslation.ts     # Custom hooks
└── locales/                   # Shared translations only
    ├── en/common.json
    └── pt/common.json

app/
├── domain-a/i18n/             # Domain-specific translations
│   ├── locales/
│   │   ├── en.json
│   │   └── pt.json
│   └── index.ts
└── domain-b/i18n/             # Domain-specific translations
    ├── locales/
    │   ├── en.json
    │   └── pt.json
    └── index.ts
```

**Usage:**
```typescript
// Domain-specific translations
const { t } = useTranslation(domainTranslations);
return <Typography>{t('title')}</Typography>;

// Shared translations
const { tCommon } = useTranslation(domainTranslations);
return <Button>{tCommon('routes.home')}</Button>;
```

For detailed i18n architecture documentation, see [i18n Architecture](./web-i18n-architecture.md).

For custom hooks patterns and best practices, see [Custom Hooks Architecture](./web-custom-hooks.md).

## Best Practices Summary

1. **Consistent Structure**: Follow the established directory structure
2. **Functional Grouping**: Organize components by purpose, not complexity
3. **Feature-Based**: Group related functionality together
4. **Clear Naming**: Use consistent naming conventions
5. **Proper Exports**: Use index files for clean imports
6. **Comprehensive Testing**: Include tests alongside components
7. **Good Documentation**: Document components and patterns
8. **Scalable i18n**: Use domain-specific translation architecture
9. **Separation of Concerns**: Keep different types of code separate
10. **Route Groups**: Use Next.js route groups `(name)` for organization

## Migration Guidelines

When refactoring existing code:

1. **Move files** to appropriate directories
2. **Update imports** to use new paths
3. **Rename files** to follow conventions
4. **Add index files** for clean exports
5. **Update tests** to match new structure
6. **Update documentation** to reflect changes

---

*This structure follows enterprise best practices and scales well for large applications while maintaining developer productivity.*
