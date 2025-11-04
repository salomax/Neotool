# Custom Hooks Architecture - Best Practices

This document defines the best practices for creating and organizing custom React hooks in the web frontend.

## Directory Structure

```
web/src/
├── lib/
│   └── hooks/                  # Domain-specific business logic hooks
│       ├── customer/            # Customer domain hooks
│       │   ├── useCustomers.ts
│       │   └── index.ts
│       ├── product/             # Product domain hooks
│       │   ├── useProducts.ts
│       │   └── index.ts
│       └── order/               # Order domain hooks
│           ├── useOrders.ts
│           └── index.ts
├── shared/
│   └── hooks/                  # Reusable utility hooks
│       ├── useAutoSave.ts
│       ├── useDataTableQuery.ts
│       ├── useResponsive.ts
│       └── useZodForm.ts
└── app/
    └── [domain]/
        └── hooks/              # Page-specific hooks (when needed)
            └── usePageSpecific.ts
```

## Hook Categories

### 1. Shared Hooks (`shared/hooks/`)
**Purpose**: Reusable utility hooks that can be used across the entire application.

**Characteristics**:
- Framework-agnostic functionality
- No business logic dependencies
- Pure utility functions
- Can be used in any component

**Examples**:
- `useAutoSave` - Auto-save form data
- `useDataTableQuery` - Pagination and filtering
- `useResponsive` - Responsive breakpoints
- `useZodForm` - Form validation

### 2. Domain Hooks (`lib/hooks/[domain]/`)
**Purpose**: Business logic hooks that encapsulate domain-specific operations.

**Characteristics**:
- Contains business logic
- Integrates with external services (GraphQL, APIs)
- Domain-specific data management
- CRUD operations and state management

**Examples**:
- `useCustomers` - Customer management operations
- `useProducts` - Product catalog operations
- `useOrders` - Order processing operations

### 3. Page Hooks (`app/[domain]/hooks/`)
**Purpose**: Page-specific hooks that combine multiple domain hooks or add page-specific logic.

**Characteristics**:
- Page-specific business logic
- Combines multiple domain hooks
- UI-specific state management
- Should be used sparingly

## Hook Design Patterns

### 1. Data Management Hooks

```typescript
// lib/hooks/customer/useCustomers.ts
export type UseCustomersOptions = {
  initialSearchTerm?: string;
  initialStatusFilter?: string;
};

export type UseCustomersReturn = {
  // Data
  customers: Customer[];
  filteredCustomers: Customer[];
  
  // State management
  searchTerm: string;
  setSearchTerm: (term: string) => void;
  
  // CRUD operations
  createCustomer: (data: CustomerFormData) => Promise<void>;
  updateCustomer: (id: string, data: CustomerFormData) => Promise<void>;
  deleteCustomer: (id: string) => Promise<void>;
  
  // Loading states
  loading: boolean;
  createLoading: boolean;
  updateLoading: boolean;
  deleteLoading: boolean;
  
  // Error handling
  error: any;
  
  // Utilities
  refetch: () => void;
};

export function useCustomers(options: UseCustomersOptions = {}): UseCustomersReturn {
  // Implementation
}
```

### 2. Utility Hooks

```typescript
// shared/hooks/useAutoSave.ts
export function useAutoSave<T extends object>(
  values: T,
  onSave: (values: T) => Promise<void> | void,
  debounceMs = 800,
) {
  // Implementation
}
```

### 3. Form Hooks

```typescript
// shared/hooks/useZodForm.ts
export function useZodForm<TSchema extends ZodType>(
  schema: TSchema,
  props?: UseFormProps<TFieldValues>
) {
  return useForm<TFieldValues>({
    ...props,
    resolver: zodResolver(schema),
    mode: props?.mode ?? "onBlur",
    reValidateMode: props?.reValidateMode ?? "onChange",
  });
}
```

## Naming Conventions

### Hook Names
- **Pattern**: `use[FeatureName]` or `use[ActionName]`
- **Examples**: 
  - `useCustomers` - Domain data management
  - `useAutoSave` - Utility functionality
  - `useDataTableQuery` - Specific functionality

### Type Names
- **Options**: `Use[FeatureName]Options`
- **Return**: `Use[FeatureName]Return`
- **Data**: `[FeatureName]FormData`, `[FeatureName]Type`

### File Names
- **Pattern**: `use[FeatureName].ts`
- **Examples**: `useCustomers.ts`, `useAutoSave.ts`

## Hook Documentation Standards

### JSDoc Comments
```typescript
/**
 * Custom hook for managing customer data and operations
 * 
 * This hook encapsulates all customer-related business logic including:
 * - CRUD operations (Create, Read, Update, Delete)
 * - Search and filtering functionality
 * - Dialog state management
 * - Loading states and error handling
 * 
 * @param options - Configuration options for the hook
 * @returns Object containing all customer management functionality
 * 
 * @example
 * ```tsx
 * function CustomerPage() {
 *   const {
 *     filteredCustomers,
 *     searchTerm,
 *     setSearchTerm,
 *     createCustomer,
 *     loading,
 *     error
 *   } = useCustomers();
 * 
 *   return (
 *     <div>
 *       <input 
 *         value={searchTerm} 
 *         onChange={(e) => setSearchTerm(e.target.value)} 
 *       />
 *       {filteredCustomers.map(customer => (
 *         <div key={customer.id}>{customer.name}</div>
 *       ))}
 *     </div>
 *   );
 * }
 * ```
 */
export function useCustomers(options: UseCustomersOptions = {}): UseCustomersReturn {
  // Implementation
}
```

## Best Practices

### 1. Single Responsibility
Each hook should have a single, well-defined responsibility.

```typescript
// ✅ Good: Single responsibility
export function useCustomers() {
  // Only customer-related logic
}

// ❌ Bad: Multiple responsibilities
export function useCustomersAndProducts() {
  // Customer logic + Product logic
}
```

### 2. Consistent API Design
Use consistent patterns for similar functionality across hooks.

```typescript
// Consistent CRUD pattern
export type UseEntityReturn = {
  // Data
  entities: Entity[];
  filteredEntities: Entity[];
  
  // State
  searchTerm: string;
  setSearchTerm: (term: string) => void;
  
  // CRUD
  createEntity: (data: EntityFormData) => Promise<void>;
  updateEntity: (id: string, data: EntityFormData) => Promise<void>;
  deleteEntity: (id: string) => Promise<void>;
  
  // Loading
  loading: boolean;
  createLoading: boolean;
  updateLoading: boolean;
  deleteLoading: boolean;
  
  // Error
  error: any;
  
  // Utilities
  refetch: () => void;
};
```

### 3. Error Handling
Always provide error handling and loading states.

```typescript
export function useCustomers() {
  const [createCustomerMutation, { loading: createLoading, error: createError }] = useCreateCustomerMutation();
  
  const createCustomer = useCallback(async (data: CustomerFormData) => {
    try {
      await createCustomerMutation({ variables: { input: data } });
      refetch();
    } catch (err) {
      console.error('Error creating customer:', err);
      throw err; // Re-throw for component handling
    }
  }, [createCustomerMutation, refetch]);
  
  return {
    createCustomer,
    createLoading,
    error: createError,
    // ... other properties
  };
}
```

### 4. Type Safety
Use TypeScript extensively for type safety.

```typescript
export type Customer = {
  id: string;
  name: string;
  email: string;
  status: 'ACTIVE' | 'INACTIVE' | 'PENDING';
  createdAt: string | null;
  updatedAt: string | null;
};

export type CustomerFormData = {
  name: string;
  email: string;
  status: string;
};

export type UseCustomersOptions = {
  initialSearchTerm?: string;
  initialStatusFilter?: string;
};
```

### 5. Memoization
Use `useMemo` and `useCallback` appropriately to prevent unnecessary re-renders.

```typescript
export function useCustomers() {
  // Memoize expensive computations
  const filteredCustomers = useMemo(() => {
    return customers.filter(customer => {
      const matchesSearch = customer.name.toLowerCase().includes(searchTerm.toLowerCase());
      const matchesStatus = statusFilter === "ALL" || customer.status === statusFilter;
      return matchesSearch && matchesStatus;
    });
  }, [customers, searchTerm, statusFilter]);
  
  // Memoize callback functions
  const createCustomer = useCallback(async (data: CustomerFormData) => {
    // Implementation
  }, [createCustomerMutation, refetch]);
  
  return {
    filteredCustomers,
    createCustomer,
    // ... other properties
  };
}
```

### 6. Separation of Concerns
Keep business logic in hooks, UI logic in components.

```typescript
// ✅ Good: Hook handles business logic
export function useCustomers() {
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [loading, setLoading] = useState(false);
  
  const createCustomer = useCallback(async (data: CustomerFormData) => {
    setLoading(true);
    try {
      const result = await createCustomerMutation({ variables: { input: data } });
      setCustomers(prev => [...prev, result.data.createCustomer]);
    } finally {
      setLoading(false);
    }
  }, [createCustomerMutation]);
  
  return { customers, createCustomer, loading };
}

// ✅ Good: Component handles UI logic
function CustomerPage() {
  const { customers, createCustomer, loading } = useCustomers();
  const [dialogOpen, setDialogOpen] = useState(false);
  
  const handleSubmit = (data: CustomerFormData) => {
    createCustomer(data);
    setDialogOpen(false);
  };
  
  return (
    <div>
      <Button onClick={() => setDialogOpen(true)}>Add Customer</Button>
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)}>
        {/* Form content */}
      </Dialog>
    </div>
  );
}
```

## Testing Hooks

### Unit Testing
```typescript
// useCustomers.test.ts
import { renderHook, act } from '@testing-library/react';
import { useCustomers } from './useCustomers';

describe('useCustomers', () => {
  it('should initialize with empty customers', () => {
    const { result } = renderHook(() => useCustomers());
    
    expect(result.current.customers).toEqual([]);
    expect(result.current.loading).toBe(false);
  });
  
  it('should filter customers by search term', () => {
    const { result } = renderHook(() => useCustomers());
    
    act(() => {
      result.current.setSearchTerm('john');
    });
    
    expect(result.current.searchTerm).toBe('john');
  });
});
```

### Integration Testing
```typescript
// CustomerPage.integration.test.tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { CustomerPage } from './CustomerPage';

describe('CustomerPage Integration', () => {
  it('should create a new customer', async () => {
    render(<CustomerPage />);
    
    fireEvent.click(screen.getByText('Add Customer'));
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'John Doe' } });
    fireEvent.change(screen.getByLabelText('Email'), { target: { value: 'john@example.com' } });
    fireEvent.click(screen.getByText('Create'));
    
    expect(await screen.findByText('John Doe')).toBeInTheDocument();
  });
});
```

## Migration Guidelines

### Moving Hooks Between Categories

1. **From `shared/` to `lib/hooks/[domain]/`**:
   - Hook contains business logic
   - Hook is domain-specific
   - Hook integrates with external services

2. **From `lib/hooks/[domain]/` to `shared/`**:
   - Hook is purely utility-based
   - Hook has no business logic
   - Hook can be used across domains

3. **Creating page-specific hooks**:
   - Only when combining multiple domain hooks
   - Only when adding page-specific logic
   - Consider if logic can be moved to domain hooks

### Refactoring Steps

1. **Identify the hook's purpose**
2. **Choose the appropriate directory**
3. **Move the hook file**
4. **Update all imports**
5. **Update documentation**
6. **Run tests to ensure functionality**

## Common Anti-Patterns

### ❌ Don't: Mix UI and Business Logic
```typescript
// Bad: UI state in business hook
export function useCustomers() {
  const [dialogOpen, setDialogOpen] = useState(false); // UI state
  const [customers, setCustomers] = useState<Customer[]>([]); // Business state
  
  return { customers, dialogOpen, setDialogOpen };
}
```

### ❌ Don't: Create God Hooks
```typescript
// Bad: Too many responsibilities
export function useEverything() {
  // Customer logic
  // Product logic
  // Order logic
  // UI logic
  // API logic
}
```

### ❌ Don't: Expose Internal Implementation
```typescript
// Bad: Exposing internal GraphQL hooks
export function useCustomers() {
  const { data, loading, error } = useGetCustomersQuery();
  
  return {
    data, // Internal implementation detail
    loading,
    error,
    customers: data?.customers || [],
  };
}
```

### ❌ Don't: Forget Error Boundaries
```typescript
// Bad: No error handling
export function useCustomers() {
  const createCustomer = async (data: CustomerFormData) => {
    await createCustomerMutation({ variables: { input: data } }); // No try/catch
  };
}
```

## Summary

Custom hooks are a powerful way to encapsulate and reuse logic in React applications. By following these patterns and guidelines:

1. **Organize hooks by purpose** (shared vs domain vs page-specific)
2. **Use consistent naming conventions**
3. **Document hooks thoroughly**
4. **Follow single responsibility principle**
5. **Implement proper error handling**
6. **Use TypeScript for type safety**
7. **Test hooks appropriately**

This architecture ensures maintainable, reusable, and well-tested code that scales with your application.

---

*This documentation follows enterprise best practices and provides a solid foundation for custom hook development in large-scale React applications.*
