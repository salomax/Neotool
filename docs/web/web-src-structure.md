# Web Frontend Structure - Best Practices

This document defines the best practices for organizing the `web/src/` directory in the NeoTool boilerplate project.

## 🏗️ **Directory Structure Overview**

```
web/src/
├── app/                          # Next.js App Router (pages & layouts)
│   ├── (framework)/             # Framework-specific routes
│   │   ├── dashboard/           # Dashboard pages
│   │   ├── design-system/       # Design system showcase
│   │   └── examples/            # Example implementations
│   ├── examples/                # Feature examples
│   │   ├── api/                 # API integration examples
│   │   ├── customers/           # Customer management examples
│   │   ├── dashboard/           # Dashboard examples
│   │   ├── database/            # Database examples
│   │   ├── events/              # Event handling examples
│   │   ├── graphql/             # GraphQL examples
│   │   └── products/            # Product management examples
│   ├── documentation/           # Documentation pages
│   ├── layout.tsx               # Root layout
│   ├── page.tsx                 # Home page
│   ├── not-found.tsx            # 404 page
│   └── providers.tsx            # Global providers
├── lib/                         # External integrations & utilities
│   ├── api/                     # API clients & providers
│   ├── graphql/                 # GraphQL operations & client
│   └── [other-integrations]/    # Other external services
├── shared/                      # Shared application code
│   ├── components/              # Reusable UI components
│   │   └── ui/                  # Atomic design system
│   │       ├── atoms/           # Basic building blocks
│   │       ├── molecules/       # Simple component combinations
│   │       ├── organisms/       # Complex component combinations
│   │       └── data-table/      # Specialized components
│   ├── config/                  # Application configuration
│   ├── hooks/                   # Custom React hooks
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

## 🎯 **Core Principles**

### 1. **Separation of Concerns**
- **`app/`**: Next.js routing and page components
- **`lib/`**: External service integrations
- **`shared/`**: Reusable application code
- **`stories/`**: Component documentation and examples

### 2. **Atomic Design System**
- **Atoms**: Basic UI elements (Button, Input, Icon)
- **Molecules**: Simple combinations (SearchField, FormField)
- **Organisms**: Complex components (DataTable, Navigation)
- **Templates**: Page layouts and structures

### 3. **Feature-Based Organization**
- Group related functionality together
- Keep examples organized by feature domain
- Maintain clear boundaries between features

### 4. **Consistent Naming Conventions**
- **Files**: kebab-case (`user-profile.tsx`)
- **Components**: PascalCase (`UserProfile`)
- **Hooks**: camelCase with `use` prefix (`useUserProfile`)
- **Types**: PascalCase (`UserProfileType`)

## 📁 **Directory Guidelines**

### **`app/` Directory (Next.js App Router)**
```
app/
├── (framework)/                 # Route groups for organization
│   ├── dashboard/              # Dashboard-related pages
│   ├── design-system/          # Design system showcase
│   └── examples/               # Example implementations
├── examples/                   # Feature examples
│   ├── [feature]/              # Individual feature examples
│   │   ├── page.tsx           # Main example page
│   │   ├── loading.tsx        # Loading state
│   │   └── error.tsx          # Error state
│   └── page.tsx               # Examples index
├── layout.tsx                 # Root layout
├── page.tsx                   # Home page
├── not-found.tsx              # 404 page
└── providers.tsx              # Global providers
```

**Guidelines:**
- Use route groups `()` for organization without affecting URLs
- Keep page components focused on layout and data fetching
- Extract complex logic to custom hooks or services
- Use consistent naming for special files (`loading.tsx`, `error.tsx`)

### **`lib/` Directory (External Integrations)**
```
lib/
├── api/                       # API clients
│   ├── AppQueryProvider.tsx   # Query client provider
│   └── [service]/             # Service-specific clients
├── graphql/                   # GraphQL operations
│   ├── client.ts              # Apollo Client setup
│   ├── operations/            # Domain-organized operations
│   ├── fragments/             # Reusable fragments
│   └── types.ts               # GraphQL types
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

### **`shared/` Directory (Reusable Code)**
```
shared/
├── components/                # Reusable UI components
│   └── ui/                    # Atomic design system
│       ├── atoms/             # Basic building blocks
│       ├── molecules/         # Simple combinations
│       ├── organisms/         # Complex components
│       └── data-table/        # Specialized components
├── config/                    # Application configuration
│   ├── nav.config.ts          # Navigation configuration
│   └── [feature].config.ts    # Feature-specific config
├── hooks/                     # Custom React hooks
│   ├── use[Feature].ts        # Feature-specific hooks
│   └── use[Utility].ts        # Utility hooks
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
- Keep business logic in hooks and services
- Organize utilities by feature when they grow large
- Use consistent naming patterns

## 🧩 **Component Organization**

### **Atomic Design System**
```
shared/components/ui/
├── atoms/                     # Basic building blocks
│   ├── Button/                # Component directory
│   │   ├── Button.tsx         # Component implementation
│   │   ├── Button.stories.tsx # Storybook stories
│   │   ├── Button.test.tsx    # Unit tests
│   │   └── index.ts           # Component export
│   └── index.ts               # All atoms export
├── molecules/                 # Simple combinations
│   ├── SearchField/           # Component directory
│   │   ├── SearchField.tsx    # Component implementation
│   │   ├── SearchField.stories.tsx
│   │   ├── SearchField.test.tsx
│   │   └── index.ts
│   └── index.ts
├── organisms/                 # Complex components
│   ├── DataTable/             # Component directory
│   │   ├── DataTable.tsx      # Component implementation
│   │   ├── DataTable.stories.tsx
│   │   ├── DataTable.test.tsx
│   │   └── index.ts
│   └── index.ts
└── index.ts                   # All UI components export
```

**Guidelines:**
- One directory per component
- Include stories and tests alongside components
- Use index files for clean exports
- Follow atomic design principles strictly

## 📝 **File Naming Conventions**

### **Components**
- **Files**: `ComponentName.tsx`
- **Directories**: `ComponentName/`
- **Stories**: `ComponentName.stories.tsx`
- **Tests**: `ComponentName.test.tsx`

### **Hooks**
- **Files**: `useHookName.ts`
- **Custom hooks**: Always start with `use`

### **Utilities**
- **Files**: `utility-name.ts`
- **Directories**: `utility-name/`

### **Types**
- **Files**: `feature-name.d.ts` or `feature-name.types.ts`
- **Interfaces**: `FeatureNameInterface`
- **Types**: `FeatureNameType`

## 🔄 **Import/Export Patterns**

### **Index Files**
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

### **Component Exports**
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

## 🧪 **Testing Strategy**

### **Test Organization**
```
shared/components/ui/atoms/Button/
├── Button.tsx
├── Button.stories.tsx
├── Button.test.tsx
└── index.ts
```

### **Test Naming**
- **Unit tests**: `ComponentName.test.tsx`
- **Integration tests**: `ComponentName.integration.test.tsx`
- **E2E tests**: `feature-name.e2e.test.tsx`

## 📚 **Documentation Standards**

### **Component Documentation**
- Include JSDoc comments for props
- Provide usage examples in stories
- Document accessibility features
- Include design system guidelines

### **Storybook Stories**
- Use `.stories.tsx` extension
- Follow Storybook naming conventions
- Include all component variants
- Provide interactive examples

## 🌐 **Internationalization (i18n)**

The project uses a scalable i18n architecture with domain-specific translations:

```
shared/i18n/                    # Core i18n configuration
├── config.ts                   # Main i18n setup
├── hooks/useI18n.ts           # Custom hooks
└── locales/                   # Shared translations only
    ├── en/common.json
    └── pt/common.json

app/(framework)/examples/
├── customers/i18n/            # Domain-specific translations
│   ├── locales/
│   │   ├── en.json
│   │   └── pt.json
│   └── index.ts
└── products/i18n/             # Domain-specific translations
    ├── locales/
    │   ├── en.json
    │   └── pt.json
    └── index.ts
```

**Usage:**
```typescript
// Domain-specific translations
const { t } = useI18n('customers');
return <Typography>{t('title')}</Typography>;

// Shared translations
const { t } = useCommonI18n();
return <Button>{t('routes.home')}</Button>;
```

For detailed i18n architecture documentation, see [Web i18n Architecture](./web-i18n-architecture.md).

## 🚀 **Best Practices Summary**

1. **Consistent Structure**: Follow the established directory structure
2. **Atomic Design**: Organize components by complexity level
3. **Feature-Based**: Group related functionality together
4. **Clear Naming**: Use consistent naming conventions
5. **Proper Exports**: Use index files for clean imports
6. **Comprehensive Testing**: Include tests alongside components
7. **Good Documentation**: Document components and patterns
8. **Scalable i18n**: Use domain-specific translation architecture
9. **Separation of Concerns**: Keep different types of code separate

## 🔧 **Migration Guidelines**

When refactoring existing code:

1. **Move files** to appropriate directories
2. **Update imports** to use new paths
3. **Rename files** to follow conventions
4. **Add index files** for clean exports
5. **Update tests** to match new structure
6. **Update documentation** to reflect changes

---

*This structure follows enterprise best practices and scales well for large applications while maintaining developer productivity.*
