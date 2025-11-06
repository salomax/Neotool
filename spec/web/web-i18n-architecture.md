# i18n Architecture

This document describes the scalable internationalization (i18n) architecture implemented in the web frontend, following enterprise best practices for maintainability and scalability.

## Architecture Overview

The i18n system is designed to keep domain-specific translations in their respective contexts. This approach ensures that each domain manages its own translations independently, preventing conflicts and improving maintainability. The system uses a simple but effective registry pattern that automatically handles domain registration and provides intelligent fallback to common translations.

## Directory Structure

```
web/src/
├── shared/i18n/
│   ├── config.ts                 # Main i18n configuration
│   ├── hooks/
│   │   └── useTranslation.ts     # Translation hook with overloading
│   ├── types.ts                  # TypeScript type definitions
│   ├── LanguageSwitcher.tsx      # Language switching component
│   ├── index.ts                  # Main exports
│   └── locales/
│       ├── en/
│       │   └── common.json       # Shared/common translations only
│       └── pt/
│           └── common.json       # Shared/common translations only
├── app/
│   ├── domain-a/
│   │   ├── page.tsx
│   │   └── i18n/                # Domain-specific translations
│   │       ├── locales/
│   │       │   ├── en.json
│   │       │   └── pt.json
│   │       └── index.ts
│   └── domain-b/
│       ├── page.tsx
│       └── i18n/                # Domain-specific translations
│           ├── locales/
│           │   ├── en.json
│           │   └── pt.json
│           └── index.ts
```

## Implementation

### Translation Hook

The i18n system provides a single `useTranslation` hook with method overloading that supports both single and multiple domains with automatic fallback to common translations:

```typescript
// Single domain with automatic fallback to common
import { useTranslation } from '@/shared/i18n';
import { domainTranslations } from './i18n';

export default function DomainPage() {
  const { t, tDomain, tCommon } = useTranslation(domainTranslations);
  
  return (
    <div>
      <Typography variant="h4">
        {t('title')} // "Domain Management" (domain-specific)
      </Typography>
      <Button>{t('actions.save')}</Button> // Automatically falls back to common
      <Button>{t('actions.cancel')}</Button> // Automatically falls back to common
    </div>
  );
}
```

### Multiple Domains Support

```typescript
// Multiple domains with smart fallback
import { useTranslation } from '@/shared/i18n';
import { domainATranslations } from '../domain-a/i18n';
import { domainBTranslations } from '../domain-b/i18n';

export default function DashboardPage() {
  const { t, getDomain, common } = useTranslation([
    domainATranslations,
    domainBTranslations
  ]);
  
  return (
    <div>
      <Button>{t('addItemA')}</Button> // From domain-a
      <Button>{t('addItemB')}</Button>  // From domain-b
      <Button>{t('actions.save')}</Button> // Falls back to common
    </div>
  );
}
```

### Automatic Domain Registration

The i18n system uses a simple but effective registry pattern that automatically manages domain translations:

#### Configuration (`shared/i18n/config.ts`)

```typescript
"use client";
import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import en from "./locales/en/common.json";
import pt from "./locales/pt/common.json";

// Initialize i18n with common translations only
if (!i18n.isInitialized) {
  i18n
    .use(initReactI18next)
    .init({
      resources: {
        en: { common: en },
        pt: { common: pt },
      },
      lng: "en",
      fallbackLng: "en",
      ns: ["common"],
      defaultNS: "common",
      interpolation: { escapeValue: false },
      initImmediate: false,
    })
    .catch((error) => {
      console.error("Failed to initialize i18n:", error);
    });
}

export default i18n;
```

#### Domain Translation Structure

Each domain exports a `DomainTranslations` object:

```typescript
// domain/i18n/index.ts
import en from './locales/en.json';
import pt from './locales/pt.json';
import { DomainTranslations } from '@/shared/i18n/types';

export const domainTranslations: DomainTranslations = {
  domain: 'domain-name',
  en,
  pt,
};
```

#### Automatic Registration

```typescript
// In any component - domain is automatically registered
const { t } = useTranslation(domainTranslations); // Auto-registers domain
```

#### Registry Benefits

- **Zero Configuration**: New domains work automatically
- **No Duplicates**: Internal registry prevents multiple registrations
- **Synchronous**: Domains are registered immediately when hook is called
- **Memory Efficient**: No re-registration on page reloads

## Benefits

### Scalability
- Each domain manages its own translations
- No conflicts between different domains
- Easy to add new domains without affecting existing ones

### Maintainability
- Domain-specific translations stay with their domain
- Clear separation of concerns
- Easy to find and update translations

### Performance
- Only load translations for the domains you need
- Smaller bundle sizes for specific pages
- Lazy loading support (future enhancement)

### Developer Experience
- Type-safe translation keys
- IntelliSense support
- Clear error messages for missing translations

## Adding New Domains

Adding new domains is incredibly simple with the automatic registration system:

1. **Create domain i18n structure:**
   ```
   app/domain-a/
   └── i18n/
       ├── locales/
       │   ├── en.json
       │   └── pt.json
       └── index.ts
   ```

2. **Add translations:**
   ```json
   // en.json
   {
     "title": "New Domain Management",
     "addItem": "Add Item",
     "editItem": "Edit Item"
   }
   ```

3. **Create domain translation object:**
   ```typescript
   // domain-a/i18n/index.ts
   import en from './locales/en.json';
   import pt from './locales/pt.json';
   import { DomainTranslations } from '@/shared/i18n/types';

   export const domainATranslations: DomainTranslations = {
     domain: 'domain-a',
     en,
     pt,
   };
   ```

4. **Use in components (automatic registration):**
   ```typescript
   // That's it! No configuration needed
   const { t } = useTranslation(domainATranslations);
   ```

The system handles everything automatically. No manual imports, no configuration updates, no bundle size concerns.

## Best Practices

1. **Keep shared translations minimal** - Only put truly shared UI elements in `common.json`
2. **Use descriptive keys** - `addItem` instead of `add`
3. **Group related translations** - Use nested objects for better organization (e.g., `actions.save`, `actions.cancel`)
4. **Use interpolation** - For dynamic content: `"deleteMessage": "Delete {{name}}?"`
5. **Consistent naming** - Follow the same pattern across all domains
6. **Type safety** - Always use the `DomainTranslations` interface for domain objects
7. **Documentation** - Keep documentation updated when adding new patterns

## Migration from Shared Approach

When migrating from the old shared approach:

1. Move domain-specific translations from `shared/i18n/locales/*/common.json` to domain-specific files
2. Update components to use `useTranslation(domainTranslations)` instead of `useTranslation('common')`
3. Update translation keys to remove the domain prefix (e.g., `domain.title` → `title`)
4. Create `DomainTranslations` objects for each domain
5. Test all language switching functionality

## Future Enhancements

- **Lazy loading** - Load domain translations on demand
- **Enhanced type safety** - Generate TypeScript types from translation files
- **Translation management** - Integration with translation management tools
- **Pluralization** - Advanced pluralization rules
- **Date/Number formatting** - Locale-specific formatting
- **Performance optimization** - Memoization improvements for large translation sets

## Testing

The i18n system has comprehensive test coverage with 59 tests across 5 test files:

- **`types.test.ts`** - TypeScript type validation
- **`config.test.ts`** - i18n configuration and initialization  
- **`useTranslation.test.tsx`** - Core translation hook functionality
- **`integration.test.tsx`** - End-to-end integration scenarios
- **`i18n-language-switcher.test.tsx`** - Language switching component

### Running Tests

```bash
# Run all i18n tests
npm test -- src/shared/i18n

# Run with coverage
npm test -- src/shared/i18n --coverage

# Run specific test file
npm test -- src/shared/i18n/__tests__/useTranslation.test.tsx
```

### Test Coverage

- **`useTranslation.ts`**: 100% coverage
- **`config.ts`**: 96.42% coverage  
- **`LanguageSwitcher.tsx`**: 100% coverage

## Related Documentation

- [Web Frontend Structure](./web-src-structure.md) - Overall frontend organization
- [GraphQL Operations](./web-graphql-operations.md) - API integration patterns
- [Architecture Decision Records](../adr/) - Technical decision documentation

---

*This i18n architecture follows enterprise best practices and is designed to scale with the platform while maintaining clear separation of concerns between domains.*
