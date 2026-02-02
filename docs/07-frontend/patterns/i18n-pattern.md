---
title: i18n (Internationalization) Pattern
type: pattern
category: frontend
status: current
version: 1.0.0
tags: [i18n, internationalization, localization, translations, multi-language, pattern]
ai_optimized: true
search_keywords: [i18n, translation, locale, useTranslation, domain, multi-language, internationalization]
related:
  - 04-patterns/frontend-patterns/shared-components-pattern.md
  - 07-frontend/patterns/graphql-query-pattern.md
  - 07-frontend/patterns/graphql-mutation-pattern.md
---

# i18n (Internationalization) Pattern

> **Purpose**: Standard pattern for implementing internationalization with domain-based translation structure that keeps translations co-located with features.

## Overview

This pattern provides a scalable approach to internationalization by organizing translations in domain-specific folders alongside the feature code. Each page or feature maintains its own `i18n` folder with locale-specific JSON files, promoting maintainability and making it easy to manage translations as features evolve.

## Core Principles

1. **Co-location**: Translations live next to the code that uses them
2. **Domain-based**: Each feature/page defines its own translation domain
3. **Type-safe**: TypeScript interfaces ensure proper usage
4. **Automatic registration**: Translations are registered automatically when used
5. **Fallback support**: Automatic fallback to common translations

## Pattern Structure

### Folder Structure

Every page or feature requiring translations should follow this structure:

```
web/src/app/(authentication)/forgot-password/
├── i18n/
│   ├── index.ts           # Translation domain export
│   └── locales/
│       ├── en.json        # English translations
│       └── pt.json        # Portuguese translations
└── page.tsx               # Component using translations
```

### Step 1: Create Translation Files

Create locale-specific JSON files in the `i18n/locales` folder:

**`i18n/locales/en.json`**
```json
{
  "title": "Forgot your password?",
  "subtitle": "Enter your email address and we'll send you a link to reset your password",
  "email": "Email",
  "sendResetLink": "Send reset link",
  "backToSignIn": "Back to sign in",
  "successMessage": "If an account with that email exists, a password reset link has been sent.",
  "errors": {
    "required": "This field is required",
    "invalidEmail": "Please enter a valid email address",
    "networkError": "Network error. Please try again.",
    "unknownError": "An unexpected error occurred. Please try again."
  }
}
```

**`i18n/locales/pt.json`**
```json
{
  "title": "Esqueceu sua senha?",
  "subtitle": "Digite seu endereço de e-mail e enviaremos um link para redefinir sua senha",
  "email": "E-mail",
  "sendResetLink": "Enviar link de redefinição",
  "backToSignIn": "Voltar para entrar",
  "successMessage": "Se uma conta com esse e-mail existir, um link de redefinição de senha foi enviado.",
  "errors": {
    "required": "Este campo é obrigatório",
    "invalidEmail": "Por favor, insira um endereço de e-mail válido",
    "networkError": "Erro de rede. Por favor, tente novamente.",
    "unknownError": "Ocorreu um erro inesperado. Por favor, tente novamente."
  }
}
```

### Step 2: Create Domain Export

Create an `index.ts` file that exports the translations as a domain:

**`i18n/index.ts`**
```typescript
import en from './locales/en.json';
import pt from './locales/pt.json';
import { DomainTranslations } from '@/shared/i18n/types';

export const forgotPasswordTranslations: DomainTranslations = {
  domain: 'forgot-password',
  en,
  pt,
};

export default forgotPasswordTranslations;
```

**Key elements:**
- Import all locale files
- Use the `DomainTranslations` type
- Define a unique `domain` name (typically matches the feature/page name)
- Export as a named constant with descriptive name

### Step 3: Use Translations in Component

Import and use the translations in your component:

**`page.tsx`**
```typescript
"use client";

import { useTranslation } from "@/shared/i18n/hooks/useTranslation";
import { forgotPasswordTranslations } from "./i18n";

export default function ForgotPasswordPage() {
  const { t } = useTranslation(forgotPasswordTranslations);

  return (
    <div>
      <Typography variant="h4">{t("title")}</Typography>
      <Typography variant="body1">{t("subtitle")}</Typography>
      {/* Access nested keys with dot notation */}
      <FormHelperText error>{t("errors.invalidEmail")}</FormHelperText>
    </div>
  );
}
```

## Advanced Usage

### Multiple Domains

If a component needs translations from multiple domains (e.g., common + feature-specific):

```typescript
import { useTranslation } from "@/shared/i18n/hooks/useTranslation";
import { myFeatureTranslations } from "./i18n";
import { commonTranslations } from "@/shared/i18n/common";

export default function MyComponent() {
  // Pass an array of domains
  const { t, getDomain, common } = useTranslation([
    myFeatureTranslations,
    commonTranslations
  ]);

  // t() automatically searches all domains in order, then falls back to common
  const title = t("title");

  // Access specific domain
  const featureDomain = getDomain("my-feature");
  const featureTitle = featureDomain?.t("title");

  // Access common translations explicitly
  const commonButton = common("button.save");

  return <div>{title}</div>;
}
```

### Translation Function Features

The `useTranslation` hook returns different APIs based on usage:

#### Single Domain (most common)

```typescript
const { t, tDomain, tCommon } = useTranslation(domainTranslations);

// t() - tries domain first, falls back to common
const title = t("title");

// tDomain() - only searches the domain, no fallback
const domainSpecific = tDomain("uniqueKey");

// tCommon() - only searches common translations
const commonLabel = tCommon("button.cancel");
```

#### Multiple Domains

```typescript
const { t, getDomain, common } = useTranslation([domain1, domain2]);

// t() - searches all domains in order, then common
const text = t("someKey");

// getDomain() - access specific domain
const domain1Hook = getDomain("domain-1");
const domainText = domain1Hook?.t("key");

// common() - access common translations
const commonText = common("commonKey");
```

### Interpolation

Support for variable interpolation (using react-i18next features):

**Translation file:**
```json
{
  "welcome": "Welcome, {{name}}!",
  "itemCount": "You have {{count}} item(s)"
}
```

**Usage:**
```typescript
const greeting = t("welcome", { name: "John" });
// Result: "Welcome, John!"

const count = t("itemCount", { count: 5 });
// Result: "You have 5 item(s)"
```

## Naming Conventions

### Domain Names

- Use kebab-case
- Should match the feature/page context
- Examples: `'forgot-password'`, `'user-profile'`, `'customer-management'`

### Translation Keys

- Use camelCase for simple keys
- Use dot notation for nested structures
- Group related translations logically

**Recommended structure:**
```json
{
  "title": "Page Title",
  "subtitle": "Page Subtitle",
  "labels": {
    "firstName": "First Name",
    "lastName": "Last Name"
  },
  "buttons": {
    "save": "Save",
    "cancel": "Cancel"
  },
  "errors": {
    "required": "Required field",
    "invalid": "Invalid value"
  },
  "toast": {
    "successMessage": "Operation successful",
    "errorMessage": "Operation failed"
  }
}
```

## Technical Details

### Type Safety

The pattern uses TypeScript interfaces to ensure type safety:

```typescript
export interface DomainTranslations {
  /** The domain name (e.g., 'customers', 'products', 'inventory') */
  domain: string;
  /** Dynamic language support - any number of languages */
  [language: string]: any;
}

export type TranslationFunction = (key: string, options?: any) => string;
```

### Automatic Registration

When you call `useTranslation()`, the hook automatically:
1. Registers the domain translations with i18next (if not already registered)
2. Prevents duplicate registrations using an internal registry
3. Adds resource bundles for each language

### Fallback Mechanism

The translation lookup follows this priority:
1. Primary domain (for single domain usage)
2. Additional domains in order (for multi-domain usage)
3. Common translations (always available as fallback)

If a key is not found in any domain, the key itself is returned.

## Common Use Cases

### Form Labels and Errors

```json
{
  "form": {
    "email": "Email Address",
    "password": "Password",
    "confirmPassword": "Confirm Password"
  },
  "validation": {
    "emailRequired": "Email is required",
    "passwordTooShort": "Password must be at least 8 characters",
    "passwordMismatch": "Passwords do not match"
  }
}
```

### Toast Messages

```json
{
  "toast": {
    "createSuccess": "Item created successfully",
    "updateSuccess": "Item updated successfully",
    "deleteSuccess": "Item deleted successfully",
    "createError": "Failed to create item",
    "updateError": "Failed to update item",
    "deleteError": "Failed to delete item"
  }
}
```

### Dialog Content

```json
{
  "dialog": {
    "deleteTitle": "Confirm Deletion",
    "deleteMessage": "Are you sure you want to delete this item?",
    "confirmButton": "Delete",
    "cancelButton": "Cancel"
  }
}
```

### Table Headers

```json
{
  "table": {
    "columns": {
      "name": "Name",
      "email": "Email",
      "status": "Status",
      "createdAt": "Created At",
      "actions": "Actions"
    }
  }
}
```

## Best Practices

### ✅ DO

- Keep translations co-located with the feature code
- Use descriptive domain names that match the feature
- Organize translation keys logically with nested structures
- Provide all required languages for every key
- Use consistent key naming across similar features
- Extract hardcoded strings to translation files

### ❌ DON'T

- Don't hardcode strings directly in components
- Don't create a single massive translation file
- Don't use inconsistent key naming patterns
- Don't forget to add translations for new languages
- Don't duplicate translation keys across domains (use common instead)
- Don't use special characters in translation keys

## Migration Guide

### Converting Hardcoded Strings

**Before:**
```typescript
export default function MyPage() {
  return (
    <div>
      <h1>Welcome to My Page</h1>
      <p>This is a description</p>
      <button>Click Me</button>
    </div>
  );
}
```

**After:**
```typescript
// 1. Create i18n/locales/en.json
{
  "title": "Welcome to My Page",
  "description": "This is a description",
  "button": "Click Me"
}

// 2. Create i18n/locales/pt.json
{
  "title": "Bem-vindo à Minha Página",
  "description": "Esta é uma descrição",
  "button": "Clique em Mim"
}

// 3. Create i18n/index.ts
import en from './locales/en.json';
import pt from './locales/pt.json';
import { DomainTranslations } from '@/shared/i18n/types';

export const myPageTranslations: DomainTranslations = {
  domain: 'my-page',
  en,
  pt,
};

export default myPageTranslations;

// 4. Update component
import { useTranslation } from "@/shared/i18n/hooks/useTranslation";
import { myPageTranslations } from "./i18n";

export default function MyPage() {
  const { t } = useTranslation(myPageTranslations);

  return (
    <div>
      <h1>{t("title")}</h1>
      <p>{t("description")}</p>
      <button>{t("button")}</button>
    </div>
  );
}
```

## Checklist

When implementing i18n for a new feature:

- [ ] Create `i18n/locales/` folder structure
- [ ] Add `en.json` with all translation keys
- [ ] Add `pt.json` with all corresponding translations
- [ ] Create `i18n/index.ts` with domain export
- [ ] Import translations in component
- [ ] Use `useTranslation` hook
- [ ] Replace all hardcoded strings with `t()` calls
- [ ] Test language switching
- [ ] Verify all keys are present in all locales
- [ ] Check for missing translations in browser console

## Related Patterns

- **Toast Notification Pattern**: Toast messages should be internationalized
- **Form Validation Pattern**: Error messages should come from translations
- **GraphQL Patterns**: API error messages may need translation
- **Shared Components Pattern**: Reusable components should support i18n

## Examples in Codebase

Real-world implementations:
- [web/src/app/(authentication)/forgot-password](web/src/app/(authentication)/forgot-password) - Complete i18n setup example

## Troubleshooting

### Translation not appearing

1. Check that domain is registered: `console.log(i18n.hasResourceBundle('en', 'your-domain'))`
2. Verify key exists in JSON files
3. Check for typos in translation key
4. Ensure JSON files are valid (no trailing commas)

### Wrong language showing

1. Check language selector/switcher implementation
2. Verify `i18n.language` value
3. Check that all locale files have the same keys

### Missing translation warning

- Add the missing key to all locale files
- Check browser console for specific missing keys
- Use fallback to common translations if appropriate

## Future Enhancements

Potential improvements to consider:
- Type-safe translation keys using TypeScript template literals
- Translation validation in CI/CD pipeline
- Automated translation coverage reports
- Translation management tooling integration
- Pluralization support for complex cases
- Date/time/number formatting patterns
