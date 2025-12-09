"use client";

import React from "react";
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Container from '@mui/material/Container';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardActions from '@mui/material/CardActions';
import Button from '@mui/material/Button';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import CodeRoundedIcon from '@mui/icons-material/CodeRounded';
import PaletteRoundedIcon from '@mui/icons-material/PaletteRounded';
import BuildRoundedIcon from '@mui/icons-material/BuildRounded';
import SecurityRoundedIcon from '@mui/icons-material/SecurityRounded';
import SpeedRoundedIcon from '@mui/icons-material/SpeedRounded';
import AccessibilityRoundedIcon from '@mui/icons-material/AccessibilityRounded';
import RouteIcon from '@mui/icons-material/Route';
import StorageIcon from '@mui/icons-material/Storage';
import BugReportIcon from '@mui/icons-material/BugReport';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import Link from 'next/link';
import { useResponsive } from "@/shared/hooks/ui";

export default function DocumentationPage() {
  const { isMobile } = useResponsive();
  
  const getGridColumns = () => {
    return isMobile ? "1fr" : "repeat(2, 1fr)";
  };

  const sections = [
    {
      title: "Getting Started",
      description: "Quick start guide and installation instructions",
      icon: CodeRoundedIcon,
      href: "/documentation/getting-started",
      items: [
        "Installation",
        "Basic Setup",
        "Project Structure",
        "Configuration"
      ]
    },
    {
      title: "Themes",
      description: "Comprehensive guide to the theme system and design tokens",
      icon: PaletteRoundedIcon,
      href: "/documentation/themes",
      items: [
        "Design Tokens",
        "Color System",
        "Typography Scale",
        "Spacing System",
        "Theme Customization"
      ]
    },
    {
      title: "Components",
      description: "Complete component library with examples and props",
      icon: BuildRoundedIcon,
      href: "/documentation/components",
      items: [
        "Component Library Overview",
        "Form Components",
        "Layout Components",
        "Navigation Components",
        "Data Display"
      ]
    },
    {
      title: "Accessibility",
      description: "Guidelines and best practices for accessible design",
      icon: AccessibilityRoundedIcon,
      href: "/documentation/accessibility",
      items: [
        "WCAG Guidelines",
        "Keyboard Navigation",
        "Screen Reader Support",
        "Color Contrast",
        "Focus Management"
      ]
    },
    {
      title: "Performance",
      description: "Optimization tips and performance best practices",
      icon: SpeedRoundedIcon,
      href: "/documentation/performance",
      items: [
        "Bundle Size Optimization",
        "Lazy Loading",
        "Code Splitting",
        "Tree Shaking",
        "Caching Strategies"
      ]
    },
    {
      title: "Security",
      description: "Security considerations and best practices",
      icon: SecurityRoundedIcon,
      href: "/documentation/security",
      items: [
        "XSS Prevention",
        "CSRF Protection",
        "Content Security Policy",
        "Dependency Security",
        "Data Validation"
      ]
    },
    {
      title: "Routing",
      description: "Next.js App Router patterns and navigation",
      icon: RouteIcon,
      href: "/documentation/routing",
      items: [
        "App Router Basics",
        "Dynamic Routes",
        "Layouts & Templates",
        "Navigation Patterns",
        "Data Fetching"
      ]
    },
    {
      title: "State Management",
      description: "State management patterns and best practices",
      icon: StorageIcon,
      href: "/documentation/state-management",
      items: [
        "React State Patterns",
        "Custom Hooks",
        "Context API",
        "Server State",
        "Form State Management"
      ]
    },
    {
      title: "Testing",
      description: "Testing strategies and best practices",
      icon: BugReportIcon,
      href: "/documentation/testing",
      items: [
        "Testing Setup",
        "Unit Testing",
        "Integration Testing",
        "E2E Testing",
        "Test Best Practices"
      ]
    },
    {
      title: "Deployment",
      description: "Deployment strategies and CI/CD pipelines",
      icon: CloudUploadIcon,
      href: "/documentation/deployment",
      items: [
        "Build Configuration",
        "Environment Variables",
        "Docker Deployment",
        "CI/CD Setup",
        "Production Checklist"
      ]
    }
  ];

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Box sx={{ mb: 6 }}>
        <Typography variant="h3" component="h1" gutterBottom>
          Documentation
        </Typography>
        <Typography variant="h6" color="text.secondary" sx={{ maxWidth: 800 }}>
          Comprehensive guides, API references, and best practices to help you 
          build amazing applications with our design system.
        </Typography>
      </Box>

      <Box 
        sx={{ 
          display: "grid", 
          gridTemplateColumns: getGridColumns(),
          gap: 4 
        }}
      >
        {sections.map((section, index) => {
          const IconComponent = section.icon;
          return (
            <Card 
              key={index}
              component={Link}
              href={section.href}
              sx={{ 
                height: "100%",
                textDecoration: "none",
                display: "flex",
                flexDirection: "column",
                transition: "transform 0.2s ease-in-out, box-shadow 0.2s ease-in-out",
                "&:hover": {
                  transform: "translateY(-4px)",
                  boxShadow: 4
                }
              }}
            >
              <CardContent sx={{ p: 3, flexGrow: 1 }}>
                <Box sx={{ display: "flex", alignItems: "center", mb: 2 }}>
                  <IconComponent sx={{ fontSize: 32, color: "primary.main", mr: 2 }} />
                  <Typography variant="h5" component="h2">
                    {section.title}
                  </Typography>
                </Box>
                <Typography color="text.secondary" sx={{ mb: 3 }}>
                  {section.description}
                </Typography>
                <List dense>
                  {section.items.map((item, itemIndex) => (
                    <ListItem key={itemIndex} sx={{ px: 0 }}>
                      <ListItemIcon sx={{ minWidth: 32 }}>
                        <Box
                          sx={{
                            width: 6,
                            height: 6,
                            borderRadius: "50%",
                            bgcolor: "primary.main"
                          }}
                        />
                      </ListItemIcon>
                      <ListItemText 
                        primary={item}
                        primaryTypographyProps={{ variant: "body2" }}
                      />
                    </ListItem>
                  ))}
                </List>
              </CardContent>
              <CardActions sx={{ px: 3, pb: 2 }}>
                <Button 
                  size="small" 
                  endIcon={<ArrowForwardIcon />}
                  sx={{ ml: "auto" }}
                >
                  Read More
                </Button>
              </CardActions>
            </Card>
          );
        })}
      </Box>
    </Container>
  );
}
