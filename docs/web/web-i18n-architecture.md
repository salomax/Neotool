# Web Frontend i18n Architecture

This document describes the scalable internationalization (i18n) architecture implemented in the NeoTool web frontend, following enterprise best practices for maintainability and scalability.

## 🏗️ Architecture Overview

The i18n system is designed to keep domain-specific translations in their respective contexts. This approach ensures that each domain manages its own translations independently, preventing conflicts and improving maintainability.

## 📁 Directory Structure

```
web/src/
├── shared/i18n/
│   ├── config.ts                 # Main i18n configuration
│   ├── hooks/
│   │   └── useI18n.ts           # Custom hook for domain-specific i18n
│   ├── locales/
│   │   ├── en/
│   │   │   └── common.json       # Shared/common translations only
│   │   └── pt/
│   │       └── common.json       # Shared/common translations only
│   └── README.md                 # Implementation documentation
├── app/(framework)/examples/
│   ├── customers/
│   │   ├── page.tsx
│   │   └── i18n/                # Customer-specific translations
│   │       ├── locales/
│   │       │   ├── en.json
│   │       │   └── pt.json
│   │       └── index.ts
│   └── products/
│       ├── page.tsx
│       └── i18n/                # Product-specific translations
│           ├── locales/
│           │   ├── en.json
│           │   └── pt.json
│           └── index.ts
```

## 🚀 Implementation

### Translation Hook

The i18n system provides a single `useTranslation` hook with method overloading that supports both single and multiple domains with automatic fallback to common translations:

```typescript
// Single domain with automatic fallback to common
import { useTranslation } from '@/shared/i18n/hooks/useI18n';
import { customersTranslations } from './i18n';

export default function CustomersPage() {
  const { t } = useTranslation(customersTranslations);
  
  return (
    <div>
      <Typography variant="h4">
        {t('title')} // "Customer Management" (domain-specific)
      </Typography>
      <Button>{t('save')}</Button> // Automatically falls back to common
      <Button>{t('cancel')}</Button> // Automatically falls back to common
    </div>
  );
}
```

### Multiple Domains Support

```typescript
// Multiple domains with smart fallback
import { useTranslation } from '@/shared/i18n/hooks/useI18n';
import { customersTranslations } from '../customers/i18n';
import { productsTranslations } from '../products/i18n';

export default function DashboardPage() {
  const { t, getDomain, common } = useTranslation([
    customersTranslations,
    productsTranslations
  ]);
  
  return (
    <div>
      <Button>{t('addCustomer')}</Button> // From customers domain
      <Button>{t('addProduct')}</Button>  // From products domain
      <Button>{t('save')}</Button>        // Falls back to common
    </div>
  );
}
```

### Dynamic Registry System

The i18n system uses a **dynamic registry pattern** that automatically manages domain translations:

#### Registry Configuration (`shared/i18n/config.ts`)

```typescript
import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import { i18nRegistry } from "./registry";
import { registerMultipleDomains } from "./register-domain";

// Initialize with common translations only
i18n.use(initReactI18next).init({
  resources: {
    en: { common: en },
    pt: { common: pt },
  },
  lng: "en",
  fallbackLng: "en",
  ns: ["common"],
  defaultNS: "common",
  interpolation: { escapeValue: false },
}).then(() => {
  // Initialize registry
  i18nRegistry.initialize();
  
  // Auto-register known domains
  const knownDomains = ['customers', 'products', 'orders'];
  registerMultipleDomains(knownDomains);
});
```

#### Automatic Domain Registration

```typescript
// In any component - domain is automatically registered
const { t } = useI18n('customers'); // Auto-loads and registers 'customers' domain
```

#### Registry Benefits

- ✅ **Zero Configuration**: New domains work automatically
- ✅ **No Duplicates**: Registry prevents multiple registrations
- ✅ **Lazy Loading**: Only loads domains when needed
- ✅ **Memory Efficient**: No re-registration on page reloads

## 🎯 Benefits

### ✅ Scalability
- Each domain manages its own translations
- No conflicts between different domains
- Easy to add new domains without affecting existing ones

### ✅ Maintainability
- Domain-specific translations stay with their domain
- Clear separation of concerns
- Easy to find and update translations

### ✅ Performance
- Only load translations for the domains you need
- Smaller bundle sizes for specific pages
- Lazy loading support (future enhancement)

### ✅ Developer Experience
- Type-safe translation keys
- IntelliSense support
- Clear error messages for missing translations

## 📝 Adding New Domains

With the registry system, adding new domains is incredibly simple:

1. **Create domain i18n structure:**
   ```
   app/(framework)/examples/new-domain/
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

3. **Use in components (automatic registration):**
   ```typescript
   // That's it! No configuration needed
   const { t } = useTranslation(newDomainTranslations);
   ```

4. **Optional - Add to known domains for pre-loading:**
   ```typescript
   // In shared/i18n/config.ts
   const knownDomains = ['customers', 'products', 'orders', 'new-domain'];
   ```

**That's it!** The registry system handles everything automatically. No manual imports, no configuration updates, no bundle size concerns.

## 🔧 Best Practices

1. **Keep shared translations minimal** - Only put truly shared UI elements in `common.json`
2. **Use descriptive keys** - `addCustomer` instead of `add`
3. **Group related translations** - Use nested objects for better organization
4. **Use interpolation** - For dynamic content: `"deleteMessage": "Delete {{name}}?"`
5. **Consistent naming** - Follow the same pattern across all domains
6. **Documentation** - Keep documentation updated when adding new patterns

## 🚨 Migration from Shared Approach

When migrating from the old shared approach:

1. Move domain-specific translations from `shared/i18n/locales/*/common.json` to domain-specific files
2. Update components to use `useI18n('domainName')` instead of `useTranslation('common')`
3. Update translation keys to remove the domain prefix (e.g., `customers.title` → `title`)
4. Test all language switching functionality

## 🔮 Future Enhancements

- **Lazy loading** - Load domain translations on demand
- **Type safety** - Generate TypeScript types from translation files
- **Translation management** - Integration with translation management tools
- **Pluralization** - Advanced pluralization rules
- **Date/Number formatting** - Locale-specific formatting

## 📚 Related Documentation

- [Web Frontend Structure](./web-src-structure.md) - Overall frontend organization
- [GraphQL Operations](./web-graphql-operations.md) - API integration patterns
- [Architecture Decision Records](../adr/) - Technical decision documentation

---

*This i18n architecture follows enterprise best practices and is designed to scale with the NeoTool platform while maintaining clear separation of concerns between domains.*
