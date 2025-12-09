"use client";

import React from "react";
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Container from '@mui/material/Container';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardActions from '@mui/material/CardActions';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Divider from '@mui/material/Divider';
import PaletteRoundedIcon from '@mui/icons-material/PaletteRounded';
import ComponentIcon from '@mui/icons-material/Widgets';
import BookIcon from '@mui/icons-material/MenuBook';
import RocketLaunchIcon from '@mui/icons-material/RocketLaunch';
import CodeIcon from '@mui/icons-material/Code';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import { useResponsive } from "@/shared/hooks/ui";
import Link from "next/link";

export default function DesignSystemPage() {
  const { isMobile } = useResponsive();
  
  const getGridColumns = () => {
    if (isMobile) return "1fr";
    return "repeat(auto-fit, minmax(300px, 1fr))";
  };

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      {/* Header */}
      <Box sx={{ mb: 6 }}>
        <Typography variant="h3" component="h1" gutterBottom>
          Design System
        </Typography>
        <Typography variant="h6" color="text.secondary" sx={{ maxWidth: 800 }}>
          A comprehensive design system providing the foundation for building consistent, 
          accessible, and beautiful user interfaces across the Neotool platform.
        </Typography>
      </Box>

      {/* Getting Started */}
      <Card sx={{ mb: 4, bgcolor: "primary.main", color: "primary.contrastText" }}>
        <CardContent sx={{ p: 4 }}>
          <Box sx={{ display: "flex", alignItems: "center", mb: 2 }}>
            <RocketLaunchIcon sx={{ fontSize: 40, mr: 2 }} />
            <Typography variant="h4" component="h2">
              Getting Started
            </Typography>
          </Box>
          <Typography variant="body1" sx={{ mb: 3, opacity: 0.9 }}>
            New to the design system? Start here to learn how to use themes, components, and follow our design principles.
          </Typography>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <Button
              variant="contained"
              color="inherit"
              href="/design-system/components"
              component={Link}
              sx={{ bgcolor: "rgba(255,255,255,0.2)", "&:hover": { bgcolor: "rgba(255,255,255,0.3)" } }}
            >
              Browse Components
            </Button>
            <Button
              variant="outlined"
              color="inherit"
              href="http://localhost:6006"
              target="_blank"
              rel="noopener noreferrer"
              endIcon={<OpenInNewIcon />}
              sx={{ borderColor: "rgba(255,255,255,0.5)", "&:hover": { borderColor: "rgba(255,255,255,0.8)" } }}
            >
              Open Storybook
            </Button>
          </Stack>
        </CardContent>
      </Card>

      {/* Main Resources Grid */}
      <Box 
        sx={{ 
          display: "grid", 
          gridTemplateColumns: getGridColumns(),
          gap: 3,
          mb: 4
        }}
      >
        {/* Documentation */}
        <Card 
          component={Link}
          href="/documentation"
          sx={{ 
            height: "100%", 
            textDecoration: "none",
            transition: "transform 0.2s ease-in-out, box-shadow 0.2s ease-in-out",
            "&:hover": {
              transform: "translateY(-4px)",
              boxShadow: 4
            }
          }}
        >
          <CardContent sx={{ p: 3 }}>
            <Box sx={{ display: "flex", alignItems: "center", mb: 2 }}>
              <BookIcon sx={{ fontSize: 32, color: "primary.main", mr: 2 }} />
              <Typography variant="h5" component="h2">
                Documentation
              </Typography>
            </Box>
            <Typography color="text.secondary" sx={{ mb: 2 }}>
              Comprehensive guides, API references, and best practices for developers and designers.
            </Typography>
            <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
              <Chip label="Guides" size="small" />
              <Chip label="API Reference" size="small" />
              <Chip label="Examples" size="small" />
              <Chip label="Best Practices" size="small" />
            </Box>
          </CardContent>
          <CardActions sx={{ px: 3, pb: 2 }}>
            <Button size="small" endIcon={<OpenInNewIcon />}>
              View Docs
            </Button>
          </CardActions>
        </Card>

        {/* Components */}
        <Card 
          component={Link}
          href="/design-system/components"
          sx={{ 
            height: "100%", 
            textDecoration: "none",
            transition: "transform 0.2s ease-in-out, box-shadow 0.2s ease-in-out",
            "&:hover": {
              transform: "translateY(-4px)",
              boxShadow: 4
            }
          }}
        >
          <CardContent sx={{ p: 3 }}>
            <Box sx={{ display: "flex", alignItems: "center", mb: 2 }}>
              <ComponentIcon sx={{ fontSize: 32, color: "primary.main", mr: 2 }} />
              <Typography variant="h5" component="h2">
                Components
              </Typography>
            </Box>
            <Typography color="text.secondary" sx={{ mb: 2 }}>
              Explore our library of reusable UI components organized by category.
            </Typography>
            <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap" }}>
              <Chip label="Primitives" size="small" color="primary" />
              <Chip label="Forms" size="small" color="secondary" />
              <Chip label="Data Display" size="small" color="success" />
              <Chip label="Layout" size="small" color="info" />
            </Box>
          </CardContent>
          <CardActions sx={{ px: 3, pb: 2 }}>
            <Button size="small" endIcon={<OpenInNewIcon />}>
              Browse All Components
            </Button>
          </CardActions>
        </Card>

        {/* Themes & Design Tokens */}
        <Card 
          sx={{ 
            height: "100%",
            transition: "transform 0.2s ease-in-out, box-shadow 0.2s ease-in-out",
            "&:hover": {
              transform: "translateY(-4px)",
              boxShadow: 4
            }
          }}
        >
          <CardContent sx={{ p: 3 }}>
            <Box sx={{ display: "flex", alignItems: "center", mb: 2 }}>
              <PaletteRoundedIcon sx={{ fontSize: 32, color: "primary.main", mr: 2 }} />
              <Typography variant="h5" component="h2">
                Themes & Tokens
              </Typography>
            </Box>
            <Typography color="text.secondary" sx={{ mb: 2 }}>
              Design tokens, color system, typography, spacing, and theme customization guide.
            </Typography>
            <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap", mb: 2 }}>
              <Chip label="Colors" size="small" />
              <Chip label="Typography" size="small" />
              <Chip label="Spacing" size="small" />
              <Chip label="Themes" size="small" />
            </Box>
            <Typography variant="body2" color="text.secondary" sx={{ fontStyle: "italic", mt: 1 }}>
              See: <Box component="code" sx={{ bgcolor: "action.hover", px: 0.5, borderRadius: 0.5, fontSize: "0.875em" }}>docs/web/web-themes.md</Box> for complete documentation
            </Typography>
          </CardContent>
        </Card>
      </Box>

      <Divider sx={{ my: 4 }} />

      {/* Quick Links */}
      <Box sx={{ mb: 4 }}>
        <Typography variant="h5" component="h2" gutterBottom>
          Quick Links
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Fast access to commonly used resources and tools.
        </Typography>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={2} flexWrap="wrap">
          <Button
            variant="outlined"
            href="http://localhost:6006"
            target="_blank"
            rel="noopener noreferrer"
            endIcon={<OpenInNewIcon />}
          >
            Storybook
          </Button>
          <Button
            variant="outlined"
            href="/documentation"
            endIcon={<OpenInNewIcon />}
          >
            Theme Guide
          </Button>
          <Button
            variant="outlined"
            href="/documentation"
            endIcon={<OpenInNewIcon />}
          >
            Theme Quick Reference
          </Button>
          <Button
            variant="outlined"
            href="/documentation"
            endIcon={<OpenInNewIcon />}
          >
            Component Documentation
          </Button>
        </Stack>
      </Box>

      {/* Component Categories Preview */}
      <Box>
        <Typography variant="h5" component="h2" gutterBottom>
          Component Categories
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          Our components are organized by functional purpose for easy discovery.
        </Typography>
        <Box 
          sx={{ 
            display: "grid",
            gridTemplateColumns: { xs: "1fr", sm: "repeat(2, 1fr)", md: "repeat(4, 1fr)" },
            gap: 2
          }}
        >
          <Card variant="outlined">
            <CardContent>
              <Box sx={{ display: "flex", alignItems: "center", mb: 1 }}>
                <CodeIcon sx={{ fontSize: 20, color: "primary.main", mr: 1 }} />
                <Typography variant="subtitle1" fontWeight={600}>
                  Primitives
                </Typography>
              </Box>
              <Typography variant="body2" color="text.secondary">
                Basic building blocks like buttons, inputs, and icons
              </Typography>
            </CardContent>
          </Card>

          <Card variant="outlined">
            <CardContent>
              <Box sx={{ display: "flex", alignItems: "center", mb: 1 }}>
                <CodeIcon sx={{ fontSize: 20, color: "secondary.main", mr: 1 }} />
                <Typography variant="subtitle1" fontWeight={600}>
                  Layout
                </Typography>
              </Box>
              <Typography variant="body2" color="text.secondary">
                Components for structuring and organizing content
              </Typography>
            </CardContent>
          </Card>

          <Card variant="outlined">
            <CardContent>
              <Box sx={{ display: "flex", alignItems: "center", mb: 1 }}>
                <CodeIcon sx={{ fontSize: 20, color: "success.main", mr: 1 }} />
                <Typography variant="subtitle1" fontWeight={600}>
                  Forms
                </Typography>
              </Box>
              <Typography variant="body2" color="text.secondary">
                Form fields, validation, and form-related components
              </Typography>
            </CardContent>
          </Card>

          <Card variant="outlined">
            <CardContent>
              <Box sx={{ display: "flex", alignItems: "center", mb: 1 }}>
                <CodeIcon sx={{ fontSize: 20, color: "info.main", mr: 1 }} />
                <Typography variant="subtitle1" fontWeight={600}>
                  Data Display
                </Typography>
              </Box>
              <Typography variant="body2" color="text.secondary">
                Tables, charts, and data visualization components
              </Typography>
            </CardContent>
          </Card>
        </Box>
      </Box>
    </Container>
  );
}
