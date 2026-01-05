---
title: Styling Pattern
type: pattern
category: frontend
status: current
version: 1.0.0
tags: [styling, themes, design-tokens, colors, css, mui, frontend]
ai_optimized: true
search_keywords: [styling, themes, design tokens, color tokens, theme tokens, MUI theme, sx prop, color palette, hardcoded colors]
related:
  - 00-overview/technology-stack.md
  - 04-patterns/frontend-patterns/shared-components-pattern.md
---

# Styling Pattern

> **Purpose**: Standard pattern for styling components using design tokens and theme system.

## Overview

The Styling Pattern ensures consistent styling across the application by using design tokens and the theme system instead of hardcoded values. All colors, spacing, typography, and other design values must come from the design token system.

## Design Tokens

Design tokens are defined in `web/src/styles/themes/tokens.ts` and provide a single source of truth for all design values.

### Token Categories

1. **Spacing**: `spacing.xs`, `spacing.sm`, `spacing.md`, `spacing.lg`, `spacing.xl`
2. **Radius**: `radius.sm`, `radius.md`, `radius.lg`, `radius.xl`, `radius.table`
3. **Typography**: Font families, sizes, weights
4. **Palette**: Colors for primary, secondary, success, warning, error, info, backgrounds, text, borders
5. **Layout**: Padding, gaps, flex properties

## Color Tokens Rule

**CRITICAL RULE**: All colors must be set as design tokens. Hardcoded color values (hex codes, rgba values, color names) are **FORBIDDEN** in component code.

### Using Color Tokens

#### Via MUI Theme

Use theme palette colors through the `sx` prop or `useTheme` hook:

```typescript
import { useTheme } from '@mui/material/styles';

// In component
const theme = useTheme();

// Using sx prop
<Box sx={{ backgroundColor: 'error.main', color: 'text.primary' }} />

// Using theme object
<Box sx={{ backgroundColor: theme.palette.error.main }} />
```

#### Custom Palette Tokens

For custom color tokens (like `errorLightBg`), access them through the theme's custom palette:

```typescript
<Box
  sx={{
    backgroundColor: (theme) => 
      (theme as any).custom?.palette?.errorLightBg || 'fallback-color',
  }}
/>
```

#### Available Color Tokens

**Semantic Colors** (via `theme.palette`):
- `primary.main` - Primary brand color
- `primary.contrastText` - Text color on primary background
- `secondary.main` - Secondary brand color
- `secondary.contrastText` - Text color on secondary background
- `success.main` - Success state color
- `warning.main` - Warning state color
- `error.main` - Error/destructive state color
- `info.main` - Informational state color

**Background Colors**:
- `background.default` - Default page background
- `background.paper` - Paper/card background

**Text Colors**:
- `text.primary` - Primary text color
- `text.secondary` - Secondary/muted text color

**Border Colors**:
- `divider` - Divider/border color

**Custom Palette Tokens** (via `theme.custom.palette`):
- `errorLightBg` - Light error background for warning icons/backgrounds
- `inputBorder` - Input border color
- `tabBorder` - Tab border color

### Examples

#### ✅ Correct: Using Theme Tokens

```typescript
// Using semantic color tokens
<Button sx={{ backgroundColor: 'error.main', color: 'error.contrastText' }} />

// Using custom palette token
<Box
  sx={{
    backgroundColor: (theme) => 
      (theme as any).custom?.palette?.errorLightBg,
  }}
/>

// Using text colors
<Typography sx={{ color: 'text.primary' }}>Text</Typography>
```

#### ❌ Incorrect: Hardcoded Colors

```typescript
// ❌ DON'T: Hardcoded hex colors
<Box sx={{ backgroundColor: '#dc2626' }} />

// ❌ DON'T: Hardcoded rgba
<Box sx={{ backgroundColor: 'rgba(220, 38, 38, 0.1)' }} />

// ❌ DON'T: Hardcoded color names
<Box sx={{ backgroundColor: 'red' }} />
```

## Spacing Tokens

Use theme spacing function instead of hardcoded pixel values:

```typescript
// ✅ Correct: Using theme spacing
<Box sx={{ padding: 2, gap: 3, margin: 4 }} />

// ❌ Incorrect: Hardcoded spacing
<Box sx={{ padding: '16px', gap: '24px' }} />
```

## Typography Tokens

Use theme typography variants:

```typescript
// ✅ Correct: Using typography variants
<Typography variant="h1">Heading</Typography>
<Typography variant="body1">Body text</Typography>

// ❌ Incorrect: Hardcoded font sizes
<Typography sx={{ fontSize: '24px' }}>Text</Typography>
```

## Border Radius Tokens

Use theme shape or custom radius tokens:

```typescript
// ✅ Correct: Using theme shape
<Box sx={{ borderRadius: 2 }} />

// ✅ Correct: Using custom radius token
<Box sx={{ borderRadius: (theme) => (theme as any).custom?.radius?.lg }} />

// ❌ Incorrect: Hardcoded radius
<Box sx={{ borderRadius: '8px' }} />
```

## Adding New Color Tokens

When you need a new color token:

1. **Add to Design Tokens** (`web/src/styles/themes/tokens.ts`):
   ```typescript
   palette: {
     // ... existing tokens
     errorLightBg: "rgba(220, 38, 38, 0.1)", // Light mode
     // ...
   }
   ```

2. **Add to Theme Custom Palette** (`web/src/styles/themes/theme.ts`):
   ```typescript
   custom: {
     palette: {
       // ... existing tokens
       errorLightBg: t.palette.errorLightBg,
     },
   }
   ```

3. **Use in Components**:
   ```typescript
   <Box
     sx={{
       backgroundColor: (theme) => 
         (theme as any).custom?.palette?.errorLightBg,
     }}
   />
   ```

## Benefits

1. **Consistency**: All components use the same color values
2. **Maintainability**: Change colors in one place (tokens file)
3. **Theme Support**: Automatic light/dark mode support
4. **Accessibility**: Centralized control over contrast ratios
5. **Design System Alignment**: Ensures UI matches design system

## Code Review Checklist

When reviewing styling code:

- [ ] No hardcoded color values (hex, rgba, color names)
- [ ] All colors use theme palette tokens
- [ ] Custom colors are added as design tokens
- [ ] Spacing uses theme spacing function
- [ ] Typography uses theme variants
- [ ] Border radius uses theme shape or custom tokens

## Related Patterns

- [Shared Components Pattern](./shared-components-pattern.md) - Component styling guidelines
- [Management Pattern](./management-pattern.md) - How pages/components are structured in management UI

## References

- Design Tokens: `web/src/styles/themes/tokens.ts`
- Theme Factory: `web/src/styles/themes/theme.ts`
- MUI Theme Documentation: https://mui.com/material-ui/customization/theming/

