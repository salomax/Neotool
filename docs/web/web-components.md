# Web Components - Design System

This document provides comprehensive documentation for the shared components library in the web frontend, following functional grouping principles and industry best practices.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Functional Component Structure](#functional-component-structure)
- [Component Categories](#component-categories)
- [Usage Guidelines](#usage-guidelines)
- [Testing with data-testid](#testing-with-data-testid)
- [Development Standards](#development-standards)
- [Component Reference](#component-reference)

## Overview

The shared components library provides a comprehensive set of reusable UI components built on top of Material-UI, organized by functional purpose rather than complexity. This system ensures consistency, maintainability, and scalability across the entire application.

### Key Features

- **Functional Grouping**: Components organized by purpose (primitives, layout, navigation, etc.)
- **TypeScript First**: Full type safety and IntelliSense support
- **Material-UI Based**: Built on top of Material-UI for consistency
- **Storybook Integration**: Interactive documentation and testing
- **Accessibility**: WCAG compliant components
- **Themeable**: Consistent theming across all components
- **Testing**: Comprehensive test coverage

## Architecture

```
shared/components/
├── ui/                          # Functional component system
│   ├── primitives/              # Basic UI building blocks
│   ├── layout/                  # Layout and structure components
│   ├── navigation/              # Navigation components
│   ├── data-display/            # Data visualization components
│   ├── forms/                   # Form input components
│   ├── feedback/                # User feedback components
│   ├── patterns/                # Complex compositions
│   └── data-table/              # Data table components (legacy)
├── ErrorBoundary.tsx            # Error handling component
└── LazyWrapper.tsx              # Lazy loading wrapper
```

## Functional Component Structure

### Primitives
Basic UI building blocks that cannot be broken down further without losing their meaning.

**Location**: `shared/components/ui/primitives/`

**Components**:
- `Avatar` - User profile pictures
- `Badge` - Status indicators and labels
- `Button` - Interactive buttons with loading states
- `Chip` - Small interactive elements
- `ColorPicker` - Color selection interface
- `DateTimePicker` - Date and time selection
- `IconButton` - Icon-based buttons
- `ImageUpload` - File upload for images
- `LoadingSpinner` - Loading indicators
- `PageSkeleton` - Page loading placeholders
- `ProgressBar` - Progress indicators
- `Rating` - Star rating component
- `Select` - Dropdown selection
- `Slider` - Range input component
- `Switch` - Toggle switches
- `Skeleton` - Content loading placeholders
- `ErrorBoundary` - Error handling wrapper

**Form Primitives**:
- `AsyncAutocomplete` - Asynchronous autocomplete
- `AutocompleteField` - Controlled autocomplete
- `CheckboxField` - Checkbox inputs
- `CurrencyField` - Currency input with formatting
- `DatePickers` - Date selection components
- `FileUploader` - File upload component
- `Form` - Form container
- `FormField` - Generic form field wrapper
- `FormMessage` - Form validation messages
- `MaskedField` - Input with masking
- `NumberField` - Numeric input with formatting
- `PasswordField` - Password input
- `PercentField` - Percentage input
- `RadioGroupField` - Radio button groups
- `SelectField` - Dropdown selection
- `ToggleField` - Toggle switches
- `TextField` - Text input fields

### Layout
Components for structuring and organizing content.

**Location**: `shared/components/ui/layout/`

**Components**:
- `Stack` - Flexible layout container with consistent spacing
- `Paper` - Elevated surfaces and containers with shadows
- `Frame` - Bordered containers and content areas
- `Drawer` - Slide-out navigation panels
- `Inline` - Inline layout container
- `Cluster` - Flexible grid layout
- `Grid` - CSS Grid layout component
- `PageHeader` - Page header with title and actions
- `PageLayout` - Complete page layout wrapper
- `Cover` - Full-screen cover layouts
- `Reel` - Horizontal scrolling container

### Navigation
Components for application navigation and routing.

**Location**: `shared/components/ui/navigation/`

**Components**:
- `Tabs` - Tab navigation component
- `Link` - Navigation links

### Data Display
Components for displaying data and visualizations.

**Location**: `shared/components/ui/data-display/`

**Components**:
- `Chart` - Data visualization charts
- `DataTable` - Advanced data tables with features:
  - Pagination
  - Sorting
  - Filtering
  - Selection
  - Inline editing
  - Bulk actions
  - Export functionality
- `DataTableSkeleton` - Loading state for data tables

### Forms
Components for form input and validation.

**Location**: `shared/components/ui/forms/`

**Components**:
- `RichTextEditor` - Rich text editing
- `SearchField` - Search input with functionality
- `SearchFilters` - Advanced search and filtering
- `FormSkeleton` - Loading state for forms

### Feedback
Components for user feedback and notifications.

**Location**: `shared/components/ui/feedback/`

**Components**:
- `ToastProvider` - Toast notification system
- `Tooltip` - Contextual information
- `EmptyErrorState` - Empty and error states
- `ConfirmDialog` - Confirmation dialogs
- `ConfirmationDialog` - Enhanced confirmation dialogs

### Patterns
Complex component compositions and patterns.

**Location**: `shared/components/ui/patterns/`

**Components**:
- `actions` - Action button patterns and utilities

## Component Categories

### 1. Form Components

Form components provide a complete solution for building forms with validation, formatting, and user experience enhancements.

**Key Features**:
- Automatic validation integration
- Consistent error handling
- Input formatting (currency, dates, etc.)
- Accessibility compliance
- Brazilian-specific fields (CPF, CNPJ, CEP)

**Usage Example**:
```tsx
import { Form, TextField, CurrencyField, SelectField } from '@/shared/components/ui/forms';

<Form onSubmit={handleSubmit}>
  <TextField name="name" label="Name" required />
  <CurrencyField name="price" label="Price" />
  <SelectField name="category" label="Category" options={categories} />
</Form>
```

### 2. Data Display Components

Components for displaying and interacting with data.

**Key Features**:
- Responsive design
- Loading states
- Empty states
- Error handling
- Accessibility

**Usage Example**:
```tsx
import { DataTable, Badge, Avatar } from '@/shared/components/ui';

<DataTable
  columns={columns}
  data={data}
  loading={loading}
  onRowClick={handleRowClick}
/>
```

### 3. Navigation Components

Components for application navigation and routing.

**Key Features**:
- Consistent styling
- Active state management
- Accessibility
- Mobile responsiveness

### 4. Feedback Components

Components for providing user feedback and notifications.

**Key Features**:
- Toast notifications
- Loading indicators
- Error boundaries
- Progress indicators

**Usage Example**:
```tsx
import { useToast } from '@/shared/providers';

const { success, error, info } = useToast();

// Show notifications
success('Operation completed successfully!');
error('Something went wrong!');
info('Processing your request...');
```

## Usage Guidelines

### 1. Import Strategy

**Preferred**: Import from the main index file
```tsx
import { Button, TextField, DataTable } from '@/shared/components/ui';
```

**Alternative**: Import from specific categories
```tsx
import { Button } from '@/shared/components/ui/primitives';
import { ConfirmDialog } from '@/shared/components/ui/feedback';
import { DataTable } from '@/shared/components/ui/data-display';
```

### 2. Component Props

All components follow consistent prop patterns:

```tsx
interface ComponentProps {
  // Required props
  requiredProp: string;
  
  // Optional props with defaults
  optionalProp?: boolean;
  
  // Event handlers
  onClick?: (event: MouseEvent) => void;
  
  // Styling props
  className?: string;
  sx?: SxProps<Theme>;
  
  // Accessibility props
  'aria-label'?: string;
  'aria-describedby'?: string;
}
```

### 3. Loading States

Many components support loading states for better UX:

```tsx
<Button loading={isLoading} loadingText="Saving...">
  Save
</Button>

<DataTable loading={isLoading} data={data} />
```

### 4. Error Handling

Components include built-in error handling:

```tsx
<TextField
  name="email"
  error={!!errors.email}
  helperText={errors.email?.message}
/>
```

## Testing with data-testid

All framework components support automatic `data-testid` generation through an optional `name` prop, making testing more maintainable and predictable.

### Overview

The `name` prop pattern allows components to automatically generate `data-testid` attributes following a consistent naming convention:

- **Without name**: `data-testid="componentname"` (e.g., `data-testid="button"`)
- **With name**: `data-testid="componentname-name"` (e.g., `data-testid="button-submit"`)
- **Override support**: Can still use explicit `data-testid` when needed

### Basic Usage

```tsx
// Primitive components
<Paper name="search-filters">Content</Paper>
<Button name="add-customer">Add Customer</Button>
<IconButton name="edit-item"><EditIcon /></IconButton>
<Chip name="status-active">Active</Chip>

// Layout components
<Stack name="form-layout" gap={2}>
  <Inline name="button-group" gap={1}>
    <Button name="save">Save</Button>
    <Button name="cancel">Cancel</Button>
  </Inline>
</Stack>

// Navigation components
<Tabs name="main-navigation" tabs={tabs} />

// Data display components
<DataTable name="customers-table" data={data} columns={columns} />

// Form components
<SearchField name="product-search" value={search} onChange={setSearch} />

// Feedback components
<ConfirmDialog name="delete-confirmation" open={open} onClose={onClose} />
```

### Generated Test IDs

The following test IDs will be automatically generated:

```html
<!-- Without name prop -->
<div data-testid="paper">Content</div>
<button data-testid="button">Click me</button>

<!-- With name prop -->
<div data-testid="paper-search-filters">Content</div>
<button data-testid="button-add-customer">Add Customer</button>
<div data-testid="stack-form-layout">
  <div data-testid="inline-button-group">
    <button data-testid="button-save">Save</button>
    <button data-testid="button-cancel">Cancel</button>
  </div>
</div>
```

### Testing Examples

Use the generated test IDs in your tests:

```tsx
import { render, screen } from '@testing-library/react';
import { Button, Paper, Stack } from '@/shared/components/ui';

test('renders components with test IDs', () => {
  render(
    <Stack name="form-layout">
      <Paper name="form-container">
        <Button name="submit-button">Submit</Button>
      </Paper>
    </Stack>
  );
  
  // Test using generated test IDs
  expect(screen.getByTestId('stack-form-layout')).toBeInTheDocument();
  expect(screen.getByTestId('paper-form-container')).toBeInTheDocument();
  expect(screen.getByTestId('button-submit-button')).toBeInTheDocument();
});

test('handles user interactions', async () => {
  const handleClick = jest.fn();
  
  render(
    <Button name="action-button" onClick={handleClick}>
      Click me
    </Button>
  );
  
  await user.click(screen.getByTestId('button-action-button'));
  expect(handleClick).toHaveBeenCalled();
});
```

### Dynamic Test IDs

For components that render lists or dynamic content, you can generate unique test IDs:

```tsx
// In a data table
{customers.map(customer => (
  <IconButton
    key={customer.id}
    name={`edit-customer-${customer.id}`}
    onClick={() => handleEdit(customer)}
  >
    <EditIcon />
  </IconButton>
))}

// In status chips
<Chip
  name={`status-${status.toLowerCase()}`}
  label={status}
  color={getStatusColor(status)}
/>
```

### Override with Explicit data-testid

When you need custom test IDs, you can still use explicit `data-testid`:

```tsx
<Button 
  name="submit" 
  data-testid="custom-submit-button"
>
  Submit
</Button>
// Renders: <button data-testid="custom-submit-button">Submit</button>
```

### Best Practices

1. **Use descriptive names**: Choose names that clearly indicate the component's purpose
   ```tsx
   <Button name="save-changes">Save Changes</Button>  // ✅ Good
   <Button name="btn1">Save</Button>                   // ❌ Avoid
   ```

2. **Be consistent**: Use the same naming pattern across your application
   ```tsx
   <Button name="action-save">Save</Button>
   <Button name="action-cancel">Cancel</Button>
   <Button name="action-delete">Delete</Button>
   ```

3. **Use kebab-case**: Follow the established pattern for multi-word names
   ```tsx
   <Paper name="user-profile-card">Content</Paper>     // ✅ Good
   <Paper name="userProfileCard">Content</Paper>       // ❌ Avoid
   ```

4. **Test what matters**: Focus on testing user interactions and important UI states
   ```tsx
   // Test user actions
   expect(screen.getByTestId('button-submit')).toBeInTheDocument();
   await user.click(screen.getByTestId('button-submit'));
   
   // Test dynamic content
   expect(screen.getByTestId('chip-status-active')).toBeInTheDocument();
   ```

### Component Support

All framework components support the `name` prop pattern:

**Primitives**: Paper, Button, IconButton, Chip, Avatar, Badge, Link, Select, Tooltip, Switch, Slider, Stack, Inline, Frame

**Layout**: Stack, Paper, Frame, Drawer, Inline, Cluster, Grid, PageLayout, PageHeader, Cover, Reel

**Navigation**: Tabs, Link

**Data Display**: Chart, DataTable

**Forms**: SearchField, SearchFilters

**Feedback**: ToastProvider, Tooltip, EmptyErrorState, ConfirmDialog, ConfirmationDialog

## Development Standards

### 1. Component Structure

Each component should follow this structure:

```tsx
"use client";

import React from "react";
import { ComponentProps } from "@mui/material";

export interface CustomComponentProps extends ComponentProps {
  // Custom props
}

export const CustomComponent: React.FC<CustomComponentProps> = ({
  // Props destructuring
  ...rest
}) => {
  // Component logic
  
  return (
    // JSX
  );
};

export default CustomComponent;
```

### 2. Storybook Stories

Every component should have comprehensive Storybook stories:

```tsx
import type { Meta, StoryObj } from "@storybook/react";
import { CustomComponent } from "./CustomComponent";

const meta: Meta<typeof CustomComponent> = {
  title: "Components/Primitives/CustomComponent",
  component: CustomComponent,
  argTypes: {
    // Story controls
  },
};

export default meta;
type Story = StoryObj<typeof CustomComponent>;

export const Default: Story = {
  args: {
    // Default props
  },
};
```

### 3. Testing

Components should include comprehensive tests:

```tsx
import { render, screen } from '@testing-library/react';
import { CustomComponent } from './CustomComponent';

describe('CustomComponent', () => {
  it('renders correctly', () => {
    render(<CustomComponent />);
    expect(screen.getByRole('button')).toBeInTheDocument();
  });
});
```

### 4. TypeScript

- Use strict TypeScript configuration
- Export proper interfaces
- Use generic types where appropriate
- Provide JSDoc comments for complex props

### 5. Accessibility

- Use semantic HTML elements
- Include proper ARIA attributes
- Ensure keyboard navigation
- Test with screen readers
- Follow WCAG guidelines

## Component Reference

### Button Component

Enhanced button component with loading states and consistent styling.

**Props**:
- `loading?: boolean` - Shows loading spinner
- `loadingText?: string` - Custom loading text
- `loadingIconSize?: number` - Size of loading spinner
- All Material-UI Button props

**Usage**:
```tsx
<Button 
  variant="contained" 
  loading={isLoading}
  onClick={handleClick}
>
  Save
</Button>
```

### DataTable Component

Advanced data table with comprehensive features.

**Props**:
- `columns: ColumnDef[]` - Table column definitions
- `data: any[]` - Table data
- `loading?: boolean` - Loading state
- `pagination?: boolean` - Enable pagination
- `sorting?: boolean` - Enable sorting
- `filtering?: boolean` - Enable filtering
- `selection?: boolean` - Enable row selection

**Usage**:
```tsx
<DataTable
  columns={columns}
  data={customers}
  loading={loading}
  pagination
  sorting
  onRowClick={handleRowClick}
/>
```

### Form Components

Comprehensive form components with validation and formatting.

**Key Components**:
- `TextField` - Text input with validation
- `CurrencyField` - Currency input with formatting
- `DatePicker` - Date selection
- `SelectField` - Dropdown selection
- `CheckboxField` - Checkbox input

**Usage**:
```tsx
<Form onSubmit={handleSubmit}>
  <TextField 
    name="name" 
    label="Name" 
    required 
    error={!!errors.name}
    helperText={errors.name?.message}
  />
  <CurrencyField 
    name="price" 
    label="Price" 
    currency="BRL"
  />
</Form>
```

## Best Practices

### 1. Component Composition

Prefer composition over complex props:

```tsx
// Good
<Card>
  <CardHeader title="Title" />
  <CardContent>
    <TextField />
  </CardContent>
  <CardActions>
    <Button>Save</Button>
  </CardActions>
</Card>

// Avoid
<Card title="Title" content={<TextField />} actions={<Button>Save</Button>} />
```

### 2. Consistent Styling

Use the theme system for consistent styling:

```tsx
// Good
<Button sx={{ color: 'primary.main' }} />

// Avoid
<Button style={{ color: '#1976d2' }} />
```

### 3. Error Boundaries

Wrap components that might fail:

```tsx
<ErrorBoundary fallback={<ErrorFallback />}>
  <DataTable data={data} />
</ErrorBoundary>
```

### 4. Performance

Use React.memo for expensive components:

```tsx
export const ExpensiveComponent = React.memo<Props>(({ data }) => {
  // Component logic
});
```

## Migration Guide

### From Atomic Design to Functional Grouping

**Before** (Atomic Design):
```tsx
import { Button } from '@/shared/components/ui/atoms';
import { ConfirmDialog } from '@/shared/components/ui/molecules';
import { DataTable } from '@/shared/components/ui/organisms';
```

**After** (Functional Grouping):
```tsx
import { Button } from '@/shared/components/ui/primitives';
import { ConfirmDialog } from '@/shared/components/ui/feedback';
import { DataTable } from '@/shared/components/ui/data-display';
```

### Benefits of Functional Grouping

1. **Easier to Find**: Components are grouped by what they do, not their complexity
2. **Better Developer Experience**: More intuitive organization
3. **Industry Standard**: Follows patterns used by major companies
4. **Future-Proof**: Clear categories for adding new components
5. **Consistent with Storybook**: Code structure matches documentation

## Contributing

When adding new components:

1. Follow the functional grouping structure
2. Create comprehensive Storybook stories
3. Add TypeScript interfaces
4. Include accessibility features
5. Write tests
6. Update this documentation

## Resources

- [Material-UI Documentation](https://mui.com/)
- [Functional Component Organization](https://bradfrost.com/blog/post/atomic-web-design/)
- [Storybook Documentation](https://storybook.js.org/)
- [WCAG Guidelines](https://www.w3.org/WAI/WCAG21/quickref/)
