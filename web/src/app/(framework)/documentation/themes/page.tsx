"use client";

import React from "react";
import {
  Box,
  Typography,
  Container,
  Paper,
  Button,
  Stack,
  Divider,
  Chip,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import Link from "next/link";
import { useTheme } from "@mui/material/styles";
import { APP_CONFIG } from "@/shared/config/repo.constants";

export default function ThemesPage() {
  const theme = useTheme();

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Box sx={{ mb: 4 }}>
        <Button
          component={Link}
          href="/documentation"
          startIcon={<ArrowBackIcon />}
          sx={{ mb: 3 }}
        >
          Back to Documentation
        </Button>
        <Typography variant="h3" component="h1" gutterBottom>
          Themes & Design Tokens
        </Typography>
        <Typography variant="h6" color="text.secondary" sx={{ maxWidth: 800 }}>
          Comprehensive guide to the theme system, design tokens, and customization options.
        </Typography>
      </Box>

      <Stack spacing={4}>
        {/* Overview */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Overview
          </Typography>
          <Typography variant="body1" paragraph>
            The {APP_CONFIG.name} theme system is built on a <strong>design token architecture</strong> that
            separates design decisions from implementation. This approach provides:
          </Typography>
          <Stack spacing={2} sx={{ mt: 2 }}>
            <Chip label="Consistency - All components use the same design tokens" />
            <Chip label="Flexibility - Easy to customize colors, spacing, typography" />
            <Chip label="Maintainability - Change design values in one place" />
            <Chip label="Type Safety - Full TypeScript support with IntelliSense" />
            <Chip label="Theme Modes - Built-in support for light and dark modes" />
          </Stack>
        </Paper>

        {/* Architecture */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Architecture
          </Typography>
          <Typography variant="body1" paragraph>
            The theme system follows a layered architecture:
          </Typography>
          <Box
            component="pre"
            sx={{
              bgcolor: "background.default",
              p: 3,
              borderRadius: 1,
              overflow: "auto",
              mt: 2,
            }}
          >
            <code>{`┌─────────────────────────────────────┐
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
└─────────────────────────────────────┘`}</code>
          </Box>
        </Paper>

        {/* Design Tokens */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Design Tokens
          </Typography>
          <Typography variant="body1" paragraph>
            Design tokens are the foundation of the theme system. They define all design values
            in a structured, type-safe way.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Spacing Tokens
          </Typography>
          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fit, minmax(200px, 1fr))",
              gap: 2,
              mb: 3,
            }}
          >
            {[
              { key: "xs", value: "4px" },
              { key: "sm", value: "8px" },
              { key: "md", value: "12px" },
              { key: "lg", value: "16px" },
              { key: "xl", value: "24px" },
            ].map((token) => (
              <Box
                key={token.key}
                sx={{
                  p: 2,
                  borderRadius: 1,
                  bgcolor: "background.default",
                  border: 1,
                  borderColor: "divider",
                }}
              >
                <Typography variant="caption" color="text.secondary">
                  spacing.{token.key}
                </Typography>
                <Typography variant="body2" fontWeight="bold">
                  {token.value}
                </Typography>
              </Box>
            ))}
          </Box>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Color Palette
          </Typography>
          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))",
              gap: 2,
            }}
          >
            {[
              "primary",
              "secondary",
              "success",
              "warning",
              "error",
              "info",
            ].map((color) => (
              <Box key={color}>
                <Box
                  sx={{
                    width: "100%",
                    height: 80,
                    borderRadius: 1,
                    bgcolor: `${color}.main`,
                    mb: 1,
                    border: 1,
                    borderColor: "divider",
                  }}
                />
                <Typography variant="caption" color="text.secondary">
                  {color}
                </Typography>
              </Box>
            ))}
          </Box>
        </Paper>

        {/* Using Themes */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Using Themes in Components
          </Typography>
          <Typography variant="body1" paragraph>
            There are several ways to use theme values in your components:
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            1. Using the sx Prop (Recommended)
          </Typography>
          <Box
            component="pre"
            sx={{
              bgcolor: "background.default",
              p: 2,
              borderRadius: 1,
              overflow: "auto",
            }}
          >
            <code>{`<Box
  sx={{
    padding: 2,                    // theme.spacing(2) = 16px
    margin: { xs: 1, md: 3 },     // Responsive spacing
    backgroundColor: "primary.main",
    color: "primary.contrastText",
    borderRadius: 2,               // theme.shape.borderRadius * 2
    typography: "h4",              // Uses theme.typography.h4
  }}
>
  Content
</Box>`}</code>
          </Box>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            2. Using useTheme Hook
          </Typography>
          <Box
            component="pre"
            sx={{
              bgcolor: "background.default",
              p: 2,
              borderRadius: 1,
              overflow: "auto",
            }}
          >
            <code>{`import { useTheme } from "@mui/material/styles";

function MyComponent() {
  const theme = useTheme();
  const spacing = theme.spacing(2);           // "16px"
  const primaryColor = theme.palette.primary.main;
  
  return (
    <div style={{ padding: spacing, color: primaryColor }}>
      Content
    </div>
  );
}`}</code>
          </Box>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            3. Using Theme Mode
          </Typography>
          <Box
            component="pre"
            sx={{
              bgcolor: "background.default",
              p: 2,
              borderRadius: 1,
              overflow: "auto",
            }}
          >
            <code>{`import { useThemeMode } from "@/styles/themes/AppThemeProvider";

function MyComponent() {
  const { mode, setMode, toggle } = useThemeMode();
  
  return (
    <button onClick={toggle}>
      Current mode: {mode}
    </button>
  );
}`}</code>
          </Box>
        </Paper>

        {/* Customization */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Customizing Themes
          </Typography>
          <Typography variant="body1" paragraph>
            To customize the theme, edit the design tokens in{" "}
            <code>web/src/styles/themes/tokens.ts</code>:
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Modifying Design Tokens
          </Typography>
          <Box
            component="pre"
            sx={{
              bgcolor: "background.default",
              p: 2,
              borderRadius: 1,
              overflow: "auto",
            }}
          >
            <code>{`export const tokens: Record<Mode, DesignTokens> = {
  light: {
    palette: {
      primary: "#your-color",  // Your custom color
      // ... rest of palette
    },
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
};`}</code>
          </Box>
        </Paper>

        {/* Best Practices */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Best Practices
          </Typography>
          <Stack spacing={2} sx={{ mt: 2 }}>
            <Box>
              <Typography variant="h6" gutterBottom>
                ✅ Use Theme Values, Not Hardcoded Values
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Always use theme.spacing(), theme.palette, etc. instead of hardcoded pixel values
              </Typography>
            </Box>
            <Divider />
            <Box>
              <Typography variant="h6" gutterBottom>
                ✅ Use Semantic Color Names
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Use "primary.main", "text.primary" instead of specific hex colors
              </Typography>
            </Box>
            <Divider />
            <Box>
              <Typography variant="h6" gutterBottom>
                ✅ Prefer sx Prop for Styling
              </Typography>
              <Typography variant="body2" color="text.secondary">
                The sx prop is optimized for theme integration and provides better performance
              </Typography>
            </Box>
            <Divider />
            <Box>
              <Typography variant="h6" gutterBottom>
                ✅ Use Responsive Spacing
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Use responsive breakpoints: padding: {`{ xs: 1, md: 3 }`}
              </Typography>
            </Box>
          </Stack>
        </Paper>

        {/* Resources */}
        <Paper sx={{ p: 4, bgcolor: "primary.main", color: "primary.contrastText" }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Resources
          </Typography>
          <Typography variant="body1" paragraph>
            For more detailed information about the theme system:
          </Typography>
          <Stack spacing={1} sx={{ mt: 2 }}>
            <Link
              href="/design-system/components"
              style={{ color: "inherit", textDecoration: "underline" }}
            >
              View Component Documentation
            </Link>
            <Link
              href="https://mui.com/material-ui/customization/theming/"
              target="_blank"
              rel="noopener noreferrer"
              style={{ color: "inherit", textDecoration: "underline" }}
            >
              Material-UI Theme Documentation
            </Link>
          </Stack>
        </Paper>
      </Stack>
    </Container>
  );
}

