---
title: Theme System Guide
type: guide
category: frontend
status: current
version: 1.0.0
tags: [themes, design-tokens, styling, mui, frontend]
related:
  - web/web-themes-quick-reference.md
  - web/web-components.md
  - ARCHITECTURE_OVERVIEW.md
---

# Theme System Guide

A comprehensive guide to understanding and using the Neotool theme system. This document covers how themes work, what design tokens are available, how to customize themes, and how to use them in your components.

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Design Tokens](#design-tokens)
- [Theme Modes](#theme-modes)
- [Using Themes in Components](#using-themes-in-components)
- [Customizing Themes](#customizing-themes)
- [Best Practices](#best-practices)
- [Examples](#examples)

## Overview

The Neotool theme system is built on a **design token architecture** that separates design decisions from implementation. This approach provides:

- **Consistency**: All components use the same design tokens
- **Flexibility**: Easy to customize colors, spacing, typography, etc.
- **Maintainability**: Change design values in one place, see them everywhere
- **Type Safety**: Full TypeScript support with IntelliSense
- **Theme Modes**: Built-in support for light and dark modes

### Key Concepts

1. **Design Tokens**: Abstract design values (colors, spacing, typography) independent of any framework
2. **Theme Factory**: Creates Material-UI themes from design tokens
3. **Theme Provider**: Manages theme state and provides it to the application
4. **Theme Context**: React context for accessing and controlling theme mode

## Architecture

The theme system follows a layered architecture:

```
┌─────────────────────────────────────┐
│   Design Tokens (tokens.ts)         │  ← Framework-agnostic tokens
│   - spacing, radius, typography     │
│   - palette (light/dark)            │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│   Theme Factory (theme.ts)          │  ← Creates MUI theme from tokens
│   - Maps tokens to MUI ThemeOptions │
│   - Adds component overrides        │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│   Theme Provider (AppThemeProvider) │  ← Manages theme state
│   - Mode switching                  │
│   - LocalStorage persistence        │
│   - React context                   │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│   Components                        │  ← Use theme via hooks/context
│   - useTheme()                      │
│   - useThemeMode()                  │
│   - sx prop                         │
└─────────────────────────────────────┘
```

### File Structure

```
web/src/styles/themes/
├── tokens.ts              # Design tokens (independent from MUI)
├── theme.ts               # MUI theme factory
├── AppThemeProvider.tsx   # Theme provider with context
├── ThemeRegistry.tsx      # Next.js theme registry
├── ThemeToggle.tsx        # Theme toggle component
└── ThemeControls.tsx      # Advanced theme controls
```

## Design Tokens

Design tokens are the foundation of the theme system. They define all design values in a structured, type-safe way.

### Token Structure

```typescript
interface DesignTokens {
  spacing: {
    xs: number;    // 4px
    sm: number;    // 8px
    md: number;    // 12px
    lg: number;    // 16px
    xl: number;    // 24px
  };
  radius: {
    sm: number;    // 4px
    md: number;    // 8px
    lg: number;    // 12px
    xl: number;    // 16px
  };
  layout: {
    paper: {
      padding: number;
      flexShrink: number;
    };
    pageLayout: {
      padding: number;
      gap: number;
      fullHeight: boolean;
    };
  };
  typography: {
    fontFamily: string;
    h1: number;        // 40px
    h2: number;        // 32px
    h3: number;        // 28px
    h4: number;        // 24px
    h5: number;        // 20px
    h6: number;        // 18px
    body: number;      // 16px
    small: number;     // 14px
    monoFamily: string;
  };
  palette: {
    primary: string;
    primaryContrast: string;
    secondary: string;
    secondaryContrast: string;
    success: string;
    warning: string;
    error: string;
    info: string;
    bg: string;
    bgPaper: string;
    text: string;
    textMuted: string;
    divider: string;
  };
}
```

### Current Token Values

#### Light Mode

```typescript
{
  spacing: { xs: 4, sm: 8, md: 12, lg: 16, xl: 24 },
  radius: { sm: 4, md: 8, lg: 12, xl: 16 },
  palette: {
    primary: "#2563eb",          // Blue
    primaryContrast: "#ffffff",
    secondary: "#7c3aed",        // Purple
    secondaryContrast: "#ffffff",
    success: "#16a34a",          // Green
    warning: "#f59e0b",          // Amber
    error: "#dc2626",            // Red
    info: "#0284c7",             // Cyan
    bg: "#f8fafc",               // Light gray
    bgPaper: "#ffffff",          // White
    text: "#0f172a",             // Dark slate
    textMuted: "#475569",        // Medium slate
    divider: "#e2e8f0",          // Light gray
  },
  typography: {
    fontFamily: "'Inter', ui-sans-serif, system-ui, ...",
    monoFamily: "'JetBrains Mono', ui-monospace, ...",
    h1: 40,
    h2: 32,
    h3: 28,
    h4: 24,
    h5: 20,
    h6: 18,
    body: 16,
    small: 14,
  }
}
```

#### Dark Mode

```typescript
{
  spacing: { xs: 4, sm: 8, md: 12, lg: 16, xl: 24 },
  radius: { sm: 4, md: 8, lg: 12, xl: 16 },
  palette: {
    primary: "#60a5fa",          // Lighter blue
    primaryContrast: "#0b1220",
    secondary: "#c084fc",        // Lighter purple
    secondaryContrast: "#0b1220",
    success: "#22c55e",          // Lighter green
    warning: "#fbbf24",          // Lighter amber
    error: "#ef4444",            // Lighter red
    info: "#38bdf8",             // Lighter cyan
    bg: "#0b1220",               // Dark slate
    bgPaper: "#0f172a",          // Darker slate
    text: "#e5e7eb",             // Light gray
    textMuted: "#94a3b8",        // Medium gray
    divider: "#1f2937",          // Dark gray
  },
  // Typography same as light mode
}
```

## Theme Modes

The theme system supports two modes: `light` and `dark`. Users can switch between modes, and their preference is saved in localStorage.

### Mode Type

```typescript
type Mode = "light" | "dark";
```

### Using Theme Mode

```typescript
import { useThemeMode } from "@/styles/themes/AppThemeProvider";

function MyComponent() {
  const { mode, setMode, toggle } = useThemeMode();
  
  return (
    <button onClick={toggle}>
      Current mode: {mode}
    </button>
  );
}
```

### Theme Toggle Component

The `ThemeToggle` component provides a simple toggle button:

```typescript
import { ThemeToggle } from "@/styles/themes/ThemeToggle";

<ThemeToggle />
```

### Theme Controls Component

For more control, use `ThemeControls`:

```typescript
import { ThemeControls } from "@/styles/themes/ThemeControls";

<ThemeControls />
```

## Using Themes in Components

There are several ways to use theme values in your components:

### 1. Using MUI's `sx` Prop (Recommended)

The `sx` prop is the most common way to style components with theme values:

```typescript
import { Box } from "@mui/material";

<Box
  sx={{
    // Use theme spacing
    padding: 2,                    // theme.spacing(2) = 16px
    margin: { xs: 1, md: 3 },     // Responsive spacing
    
    // Use theme colors
    backgroundColor: "primary.main",
    color: "primary.contrastText",
    borderColor: "divider",
    
    // Use theme typography
    typography: "h4",              // Uses theme.typography.h4
    
    // Use theme radius
    borderRadius: 2,               // theme.shape.borderRadius * 2
    
    // Use palette directly
    bgcolor: "background.paper",
    color: "text.primary",
  }}
>
  Content
</Box>
```

### 2. Using `useTheme` Hook

For more complex logic or when you need theme values in JavaScript:

```typescript
import { useTheme } from "@mui/material/styles";

function MyComponent() {
  const theme = useTheme();
  
  // Access theme values
  const spacing = theme.spacing(2);           // "16px"
  const primaryColor = theme.palette.primary.main;
  const borderRadius = theme.shape.borderRadius;
  
  // Access custom theme properties
  const layoutTokens = (theme as any).custom?.layout;
  const paperPadding = layoutTokens?.paper?.padding;
  
  return (
    <div style={{ padding: spacing, borderRadius }}>
      Content
    </div>
  );
}
```

### 3. Using Typography Variants

Typography variants automatically use theme values:

```typescript
import { Typography } from "@mui/material";

<Typography variant="h1">Heading 1</Typography>
<Typography variant="h2">Heading 2</Typography>
<Typography variant="body1">Body text</Typography>
<Typography variant="body2" color="text.secondary">
  Secondary text
</Typography>
```

### 4. Using Design Tokens Directly

For components that don't use MUI, import tokens directly:

```typescript
import { tokens, Mode } from "@/styles/themes/tokens";

function MyCustomComponent({ mode }: { mode: Mode }) {
  const t = tokens[mode];
  
  return (
    <div style={{
      padding: `${t.spacing.md}px`,
      borderRadius: `${t.radius.lg}px`,
      backgroundColor: t.palette.bg,
      color: t.palette.text,
    }}>
      Content
    </div>
  );
}
```

### 5. Using Custom Theme Properties

Some components use custom theme properties:

```typescript
import { useTheme } from "@mui/material/styles";

function PageLayout() {
  const theme = useTheme();
  
  // Access custom layout tokens
  const layoutTokens = (theme as any).custom?.layout;
  const paperPadding = layoutTokens?.paper?.padding ?? 2;
  const pageGap = layoutTokens?.pageLayout?.gap ?? 12;
  
  // Use them in your component
  return (
    <div style={{ gap: `${pageGap * 8}px` }}>
      <Paper sx={{ p: paperPadding }}>Content</Paper>
    </div>
  );
}
```

## Customizing Themes

### Modifying Design Tokens

To change design values, edit `web/src/styles/themes/tokens.ts`:

```typescript
export const tokens: Record<Mode, DesignTokens> = {
  light: {
    // Change primary color
    palette: {
      primary: "#your-color",  // Your custom color
      // ... rest of palette
    },
    // Change spacing scale
    spacing: {
      xs: 4,
      sm: 8,
      md: 16,  // Changed from 12 to 16
      lg: 24,  // Changed from 16 to 24
      xl: 32,  // Changed from 24 to 32
    },
    // ... rest of tokens
  },
  dark: {
    // Same structure for dark mode
  },
};
```

### Extending MUI Theme

To add custom theme properties or override MUI components, edit `web/src/styles/themes/theme.ts`:

```typescript
export const createAppTheme = (mode: Mode) => {
  const t = tokens[mode];
  const options: ThemeOptions = {
    // ... existing options
    
    // Add custom component overrides
    components: {
      MuiButton: {
        defaultProps: {
          disableElevation: true,
          variant: "contained",
        },
        styleOverrides: {
          root: {
            borderRadius: t.radius.lg,
            // Add custom styles
            textTransform: "none",
          },
        },
      },
      // Add more component overrides
    },
    
    // Add custom theme properties
    custom: {
      layout: t.layout,
      // Add your custom properties here
      customProperty: {
        value: "something",
      },
    },
  };
  
  return createTheme(options);
};
```

### Adding New Design Tokens

To add new tokens:

1. **Update the `DesignTokens` interface**:

```typescript
export interface DesignTokens {
  // ... existing tokens
  
  // Add new token category
  shadows: {
    sm: string;
    md: string;
    lg: string;
  };
}
```

2. **Add values to token definitions**:

```typescript
export const tokens: Record<Mode, DesignTokens> = {
  light: {
    // ... existing tokens
    shadows: {
      sm: "0 1px 2px rgba(0,0,0,0.05)",
      md: "0 4px 6px rgba(0,0,0,0.1)",
      lg: "0 10px 15px rgba(0,0,0,0.15)",
    },
  },
  dark: {
    // ... same structure
    shadows: {
      sm: "0 1px 2px rgba(0,0,0,0.3)",
      md: "0 4px 6px rgba(0,0,0,0.4)",
      lg: "0 10px 15px rgba(0,0,0,0.5)",
    },
  },
};
```

3. **Use in theme factory** (if needed):

```typescript
export const createAppTheme = (mode: Mode) => {
  const t = tokens[mode];
  const options: ThemeOptions = {
    // ... existing options
    custom: {
      layout: t.layout,
      shadows: t.shadows,  // Add to custom theme properties
    },
  };
  return createTheme(options);
};
```

### Creating a New Theme Mode

To add a new theme mode (e.g., "auto" or "high-contrast"):

1. **Update the `Mode` type**:

```typescript
export type Mode = "light" | "dark" | "high-contrast";
```

2. **Add token values**:

```typescript
export const tokens: Record<Mode, DesignTokens> = {
  light: { /* ... */ },
  dark: { /* ... */ },
  "high-contrast": {
    // High contrast values
    palette: {
      primary: "#000000",
      primaryContrast: "#ffffff",
      // ... higher contrast values
    },
    // ... rest of tokens
  },
};
```

3. **Update theme provider logic** (if needed):

```typescript
// In AppThemeProvider.tsx
if (saved === "light" || saved === "dark" || saved === "high-contrast") {
  setMode(saved);
}
```

## Best Practices

### 1. Use Theme Values, Not Hardcoded Values

✅ **Good**:
```typescript
<Box sx={{ padding: 2, color: "primary.main" }}>
```

❌ **Bad**:
```typescript
<Box sx={{ padding: "16px", color: "#2563eb" }}>
```

### 2. Use Responsive Spacing

✅ **Good**:
```typescript
<Box sx={{ padding: { xs: 1, md: 3 } }}>
```

❌ **Bad**:
```typescript
<Box sx={{ padding: "24px" }}>
```

### 3. Use Semantic Color Names

✅ **Good**:
```typescript
<Box sx={{ bgcolor: "background.paper", color: "text.primary" }}>
```

❌ **Bad**:
```typescript
<Box sx={{ bgcolor: "#ffffff", color: "#000000" }}>
```

### 4. Access Custom Properties Safely

✅ **Good**:
```typescript
const layoutTokens = (theme as any).custom?.layout;
const padding = layoutTokens?.paper?.padding ?? 2;
```

❌ **Bad**:
```typescript
const padding = theme.custom.layout.paper.padding;  // May crash
```

### 5. Use Typography Variants

✅ **Good**:
```typescript
<Typography variant="h4">Title</Typography>
```

❌ **Bad**:
```typescript
<Typography sx={{ fontSize: 24, fontWeight: 600 }}>Title</Typography>
```

### 6. Prefer `sx` Prop for Styling

The `sx` prop is optimized for theme integration and provides better performance:

✅ **Good**:
```typescript
<Box sx={{ padding: 2, borderRadius: 1 }}>
```

❌ **Bad**:
```typescript
<Box style={{ padding: "16px", borderRadius: "8px" }}>
```

## Examples

### Example 1: Simple Themed Component

```typescript
import { Box, Typography, Button } from "@mui/material";

export function ThemedCard() {
  return (
    <Box
      sx={{
        p: 3,                           // theme.spacing(3) = 24px
        borderRadius: 2,                // theme.shape.borderRadius * 2
        bgcolor: "background.paper",
        boxShadow: 2,
      }}
    >
      <Typography variant="h5" color="primary.main">
        Title
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
        Description
      </Typography>
      <Button variant="contained" sx={{ mt: 2 }}>
        Action
      </Button>
    </Box>
  );
}
```

### Example 2: Using Theme Mode

```typescript
import { useThemeMode } from "@/styles/themes/AppThemeProvider";
import { Box, Switch, Typography } from "@mui/material";

export function ThemeSwitcher() {
  const { mode, toggle } = useThemeMode();
  
  return (
    <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
      <Typography>Light</Typography>
      <Switch checked={mode === "dark"} onChange={toggle} />
      <Typography>Dark</Typography>
    </Box>
  );
}
```

### Example 3: Custom Component Using Tokens

```typescript
import { tokens, Mode } from "@/styles/themes/tokens";

interface CustomCardProps {
  mode: Mode;
}

export function CustomCard({ mode }: CustomCardProps) {
  const t = tokens[mode];
  
  return (
    <div
      style={{
        padding: `${t.spacing.lg}px`,
        borderRadius: `${t.radius.lg}px`,
        backgroundColor: t.palette.bgPaper,
        color: t.palette.text,
        border: `1px solid ${t.palette.divider}`,
        fontFamily: t.typography.fontFamily,
      }}
    >
      <h2 style={{ fontSize: `${t.typography.h2}px` }}>Title</h2>
      <p style={{ fontSize: `${t.typography.body}px` }}>Content</p>
    </div>
  );
}
```

### Example 4: Using Custom Layout Tokens

```typescript
import { Paper } from "@/shared/components/ui/layout/Paper";
import { useTheme } from "@mui/material/styles";

export function LayoutExample() {
  const theme = useTheme();
  const layoutTokens = (theme as any).custom?.layout;
  
  return (
    <div style={{
      padding: theme.spacing(layoutTokens?.pageLayout?.padding ?? 4),
      gap: theme.spacing(layoutTokens?.pageLayout?.gap ?? 12),
    }}>
      <Paper>
        Content with automatic padding from theme
      </Paper>
    </div>
  );
}
```

### Example 5: Responsive Theming

```typescript
import { Box } from "@mui/material";

export function ResponsiveBox() {
  return (
    <Box
      sx={{
        // Responsive padding
        padding: { xs: 1, sm: 2, md: 3, lg: 4 },
        
        // Responsive typography
        typography: {
          xs: "body2",
          md: "body1",
          lg: "h6",
        },
        
        // Responsive colors
        bgcolor: {
          xs: "background.default",
          md: "background.paper",
        },
      }}
    >
      Responsive content
    </Box>
  );
}
```

## Summary

The Neotool theme system provides a comprehensive, type-safe way to manage design values across your application. By using design tokens, you can:

- ✅ Maintain consistency across components
- ✅ Easily customize colors, spacing, and typography
- ✅ Support multiple theme modes
- ✅ Ensure type safety with TypeScript
- ✅ Create responsive, accessible designs

Remember:
- Use `sx` prop for styling with theme values
- Access custom properties safely with optional chaining
- Follow the best practices for maintainable code
- Extend the theme system when needed, but keep it organized

For more information, see:
- [Theme System Quick Reference](./web-themes-quick-reference.md)
- [Web Components Documentation](./web-components.md)
- [MUI Theme Documentation](https://mui.com/material-ui/customization/theming/)

