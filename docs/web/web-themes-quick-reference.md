# Theme System Quick Reference

A quick reference guide for using the Neotool theme system in your components.

## 🎨 Theme Modes

```typescript
type Mode = "light" | "dark";
```

## 🔧 Hooks & Context

### useThemeMode

```typescript
import { useThemeMode } from "@/styles/themes/AppThemeProvider";

const { mode, setMode, toggle } = useThemeMode();
// mode: "light" | "dark"
// setMode: (mode: Mode) => void
// toggle: () => void
```

### useTheme

```typescript
import { useTheme } from "@mui/material/styles";

const theme = useTheme();
// Access: theme.palette, theme.spacing, theme.typography, etc.
```

## 🎨 Common Theme Values

### Spacing

```typescript
// In sx prop
sx={{ padding: 2 }}  // theme.spacing(2) = 16px

// Direct access
theme.spacing(1)  // 8px
theme.spacing(2)  // 16px
theme.spacing(3)  // 24px

// Responsive
sx={{ padding: { xs: 1, md: 3 } }}
```

### Colors

```typescript
// In sx prop
sx={{ 
  bgcolor: "primary.main",
  color: "text.primary",
  borderColor: "divider",
}}

// Direct access
theme.palette.primary.main
theme.palette.text.primary
theme.palette.background.paper
```

### Typography

```typescript
// Variants
<Typography variant="h1">Heading</Typography>
<Typography variant="body1">Body text</Typography>

// In sx prop
sx={{ typography: "h4" }}

// Direct access
theme.typography.h1.fontSize  // 40px
theme.typography.body1.fontSize  // 16px
```

### Border Radius

```typescript
// In sx prop
sx={{ borderRadius: 2 }}  // theme.shape.borderRadius * 2

// Direct access
theme.shape.borderRadius  // 8px
```

## 📐 Design Tokens Reference

### Spacing Scale

| Token | Value | Usage |
|-------|-------|-------|
| `xs` | 4px | Tight spacing |
| `sm` | 8px | Small spacing |
| `md` | 12px | Medium spacing |
| `lg` | 16px | Large spacing |
| `xl` | 24px | Extra large spacing |

### Radius Scale

| Token | Value | Usage |
|-------|-------|-------|
| `sm` | 4px | Small elements |
| `md` | 8px | Default (most components) |
| `lg` | 12px | Large elements |
| `xl` | 16px | Extra large elements |

### Typography Scale

| Variant | Size | Usage |
|---------|------|-------|
| `h1` | 40px | Page titles |
| `h2` | 32px | Section titles |
| `h3` | 28px | Subsection titles |
| `h4` | 24px | Card titles |
| `h5` | 20px | Small headings |
| `h6` | 18px | Minor headings |
| `body1` | 16px | Body text |
| `body2` | 14px | Secondary text |

### Color Palette

#### Primary Colors
- `primary.main` - Main brand color
- `primary.contrastText` - Text on primary
- `secondary.main` - Secondary brand color
- `secondary.contrastText` - Text on secondary

#### Semantic Colors
- `success.main` - Success states
- `warning.main` - Warning states
- `error.main` - Error states
- `info.main` - Info states

#### Neutral Colors
- `background.default` - Page background
- `background.paper` - Card/surface background
- `text.primary` - Primary text
- `text.secondary` - Secondary text
- `divider` - Dividers and borders

## 💡 Common Patterns

### Themed Card

```typescript
<Box
  sx={{
    p: 3,
    borderRadius: 2,
    bgcolor: "background.paper",
    boxShadow: 2,
  }}
>
  Content
</Box>
```

### Responsive Spacing

```typescript
<Box
  sx={{
    padding: { xs: 1, sm: 2, md: 3 },
    gap: { xs: 1, md: 2 },
  }}
>
  Content
</Box>
```

### Typography with Theme Colors

```typescript
<Typography variant="h4" color="primary.main">
  Title
</Typography>
<Typography variant="body2" color="text.secondary">
  Description
</Typography>
```

### Custom Theme Properties

```typescript
const theme = useTheme();
const layoutTokens = (theme as any).custom?.layout;

// Access custom properties
const paperPadding = layoutTokens?.paper?.padding ?? 2;
const pageGap = layoutTokens?.pageLayout?.gap ?? 12;
```

## ✅ Best Practices Checklist

- [ ] Use `sx` prop instead of inline styles
- [ ] Use theme spacing values, not hardcoded pixels
- [ ] Use semantic color names (`primary.main`, not `#2563eb`)
- [ ] Use typography variants instead of custom font sizes
- [ ] Access custom properties safely with optional chaining
- [ ] Test components in both light and dark modes
- [ ] Use responsive spacing values when appropriate

## 🔗 Full Documentation

For complete documentation, see [Theme System Guide](./web-themes.md).

