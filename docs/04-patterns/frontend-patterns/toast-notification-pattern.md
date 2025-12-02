---
title: Toast Notification Pattern
type: pattern
category: frontend
status: current
version: 1.0.0
tags: [toast, notification, feedback, user-experience, pattern]
ai_optimized: true
search_keywords: [toast, notification, feedback, useToast, success, error, user-feedback]
related:
  - 04-patterns/frontend-patterns/component-pattern.md
  - 11-validation/feature-checklist.md
---

# Toast Notification Pattern

> **Purpose**: Standard pattern for providing user feedback through toast notifications for all user actions, especially mutations (create, update, delete).

## Overview

Toast notifications provide immediate, non-intrusive feedback to users about the success or failure of their actions. This pattern ensures consistent user experience across all features by requiring toast notifications for all mutations and important user actions.

## Core Requirements

### Required Usage

Every feature MUST provide toast notifications for:

1. **All mutations** (create, update, delete operations)
   - Success toast on successful completion
   - Error toast on failure

2. **Status changes** (enable/disable, activate/deactivate)
   - Success toast confirming the change
   - Error toast on failure

3. **Bulk operations** (when applicable)
   - Success toast with count of affected items
   - Error toast on failure

### Required Components

1. **ToastProvider** - Must be included in the app provider tree
2. **useToast hook** - Used in components to show notifications
3. **extractErrorMessage utility** - Used to extract user-friendly error messages

## Pattern Structure

### Setup: ToastProvider

The `ToastProvider` must be included in the app's provider tree:

```typescript
// app/providers.tsx
import { ToastProvider } from "@/shared/providers";

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <ToastProvider>
      {children}
    </ToastProvider>
  );
}
```

### Pattern: Using Toast in Components

```typescript
import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";
import { useTranslation } from "@/shared/i18n";

function MyComponent() {
  const toast = useToast();
  const { t } = useTranslation(myTranslations);

  const handleCreate = async (data: FormData) => {
    try {
      await createMutation(data);
      toast.success(t('toast.itemCreated', { name: data.name }));
      closeDialog();
    } catch (err) {
      const errorMessage = extractErrorMessage(
        err,
        t('toast.itemCreateError')
      );
      toast.error(errorMessage);
    }
  };

  const handleUpdate = async (id: string, data: FormData) => {
    try {
      await updateMutation(id, data);
      toast.success(t('toast.itemUpdated', { name: data.name }));
      closeDialog();
    } catch (err) {
      const errorMessage = extractErrorMessage(
        err,
        t('toast.itemUpdateError')
      );
      toast.error(errorMessage);
    }
  };

  const handleDelete = async (id: string, name: string) => {
    try {
      await deleteMutation(id);
      toast.success(t('toast.itemDeleted', { name }));
      setDeleteConfirm(null);
    } catch (err) {
      const errorMessage = extractErrorMessage(
        err,
        t('toast.itemDeleteError')
      );
      toast.error(errorMessage);
    }
  };

  const handleToggleStatus = async (id: string, enabled: boolean) => {
    try {
      if (enabled) {
        await enableMutation(id);
        toast.success(t('toast.itemEnabled'));
      } else {
        await disableMutation(id);
        toast.success(t('toast.itemDisabled'));
      }
      refetch();
    } catch (err) {
      const errorMessage = extractErrorMessage(
        err,
        enabled ? t('toast.itemEnableError') : t('toast.itemDisableError')
      );
      toast.error(errorMessage);
    }
  };
}
```

### Pattern: Toast API

The `useToast` hook provides the following methods:

```typescript
const toast = useToast();

// Success notification (default: 3000ms)
toast.success('Item created successfully');

// Error notification (default: 4000ms)
toast.error('Failed to create item');

// Info notification (default: 3000ms)
toast.info('Processing your request...');

// Warning notification (default: 3500ms)
toast.warning('This action cannot be undone');

// Custom severity with duration
toast.show('Custom message', 'success', 5000);
```

### Pattern: Error Message Extraction

Always use `extractErrorMessage` to extract user-friendly error messages:

```typescript
import { extractErrorMessage } from "@/shared/utils/error";

try {
  await mutation();
} catch (err) {
  // Extract and clean error message
  const errorMessage = extractErrorMessage(
    err,
    t('toast.defaultError') // Fallback message
  );
  toast.error(errorMessage);
}
```

### Pattern: i18n Translations

Toast messages MUST be internationalized. Add toast translations to your feature's i18n files:

```json
// i18n/locales/en.json
{
  "toast": {
    "itemCreated": "Item '{name}' created successfully",
    "itemUpdated": "Item '{name}' updated successfully",
    "itemDeleted": "Item '{name}' deleted successfully",
    "itemCreateError": "Failed to create item",
    "itemUpdateError": "Failed to update item",
    "itemDeleteError": "Failed to delete item",
    "itemEnabled": "Item enabled successfully",
    "itemDisabled": "Item disabled successfully",
    "itemEnableError": "Failed to enable item",
    "itemDisableError": "Failed to disable item"
  }
}
```

```json
// i18n/locales/pt.json
{
  "toast": {
    "itemCreated": "Item '{name}' criado com sucesso",
    "itemUpdated": "Item '{name}' atualizado com sucesso",
    "itemDeleted": "Item '{name}' excluído com sucesso",
    "itemCreateError": "Falha ao criar item",
    "itemUpdateError": "Falha ao atualizar item",
    "itemDeleteError": "Falha ao excluir item",
    "itemEnabled": "Item habilitado com sucesso",
    "itemDisabled": "Item desabilitado com sucesso",
    "itemEnableError": "Falha ao habilitar item",
    "itemDisableError": "Falha ao desabilitar item"
  }
}
```

## Complete Example: CRUD Operations with Toasts

```typescript
"use client";

import { useToast } from "@/shared/providers";
import { extractErrorMessage } from "@/shared/utils/error";
import { useTranslation } from "@/shared/i18n";
import { myFeatureTranslations } from "./i18n";

function MyFeatureComponent() {
  const toast = useToast();
  const { t } = useTranslation(myFeatureTranslations);

  const {
    createItem,
    updateItem,
    deleteItem,
    loading,
    createLoading,
    updateLoading,
    deleteLoading,
  } = useMyFeature();

  // Create operation
  const handleCreate = async (data: FormData) => {
    try {
      await createItem(data);
      toast.success(t('toast.itemCreated', { name: data.name }));
      closeDialog();
    } catch (err) {
      console.error('Error creating item:', err);
      const errorMessage = extractErrorMessage(
        err,
        t('toast.itemCreateError')
      );
      toast.error(errorMessage);
    }
  };

  // Update operation
  const handleUpdate = async (id: string, data: FormData) => {
    try {
      await updateItem(id, data);
      toast.success(t('toast.itemUpdated', { name: data.name }));
      closeDialog();
    } catch (err) {
      console.error('Error updating item:', err);
      const errorMessage = extractErrorMessage(
        err,
        t('toast.itemUpdateError')
      );
      toast.error(errorMessage);
    }
  };

  // Delete operation
  const handleDelete = async (id: string, name: string) => {
    try {
      await deleteItem(id);
      toast.success(t('toast.itemDeleted', { name }));
      setDeleteConfirm(null);
    } catch (err) {
      console.error('Error deleting item:', err);
      const errorMessage = extractErrorMessage(
        err,
        t('toast.itemDeleteError')
      );
      toast.error(errorMessage);
    }
  };

  return (
    // Component JSX
  );
}
```

## Common Errors and How to Avoid Them

### ❌ Error: Missing Toast Notifications

```typescript
// ❌ Incorrect - No user feedback
const handleCreate = async (data: FormData) => {
  await createMutation(data);
  closeDialog();
};

// ✅ Correct - Toast notification on success
const handleCreate = async (data: FormData) => {
  try {
    await createMutation(data);
    toast.success(t('toast.itemCreated', { name: data.name }));
    closeDialog();
  } catch (err) {
    const errorMessage = extractErrorMessage(err, t('toast.itemCreateError'));
    toast.error(errorMessage);
  }
};
```

### ❌ Error: Not Using extractErrorMessage

```typescript
// ❌ Incorrect - Raw error message may contain technical details
catch (err) {
  toast.error(err.message);
}

// ✅ Correct - Extract and clean error message
catch (err) {
  const errorMessage = extractErrorMessage(err, t('toast.defaultError'));
  toast.error(errorMessage);
}
```

### ❌ Error: Missing i18n Support

```typescript
// ❌ Incorrect - Hardcoded English message
toast.success('Item created successfully');

// ✅ Correct - Internationalized message
toast.success(t('toast.itemCreated', { name: data.name }));
```

### ❌ Error: Only Showing Errors, Not Success

```typescript
// ❌ Incorrect - Only error feedback
const handleCreate = async (data: FormData) => {
  try {
    await createMutation(data);
    closeDialog();
  } catch (err) {
    toast.error('Failed to create');
  }
};

// ✅ Correct - Both success and error feedback
const handleCreate = async (data: FormData) => {
  try {
    await createMutation(data);
    toast.success(t('toast.itemCreated', { name: data.name }));
    closeDialog();
  } catch (err) {
    const errorMessage = extractErrorMessage(err, t('toast.itemCreateError'));
    toast.error(errorMessage);
  }
};
```

## Quick Reference Checklist

When implementing mutations, verify:

- [ ] `useToast` hook imported from `@/shared/providers`
- [ ] `extractErrorMessage` imported from `@/shared/utils/error`
- [ ] Success toast shown on successful mutation
- [ ] Error toast shown on failed mutation
- [ ] Error messages extracted using `extractErrorMessage`
- [ ] Toast messages internationalized (i18n)
- [ ] Toast messages include relevant context (item name, etc.)
- [ ] Console.error used for debugging (in addition to toast)
- [ ] Toast notifications for all mutation types (create, update, delete)
- [ ] Toast notifications for status changes (enable/disable)

## Related Documentation

- [Component Pattern](./component-pattern.md) - Component structure
- [Feature Checklist](../11-validation/feature-checklist.md) - Feature completion requirements
- [Error Handling Utilities](../../web/src/shared/utils/error.ts) - Error extraction utilities

