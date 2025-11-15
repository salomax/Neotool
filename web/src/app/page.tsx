"use client";

import React from "react";
import { 
  Box, 
  Typography, 
  Container, 
  Card, 
  CardContent,
  DesignServicesRoundedIcon,
  CodeRoundedIcon,
  MenuBookRoundedIcon
} from "@/shared/ui/mui-imports";
import Link from "next/link";
import { useResponsive } from "@/shared/hooks/useResponsive";
import { Logo } from "@/shared/ui/brand";

export default function WelcomePage() {
  const { isMobile, isTablet } = useResponsive();
  
  const getGridColumns = () => {
    if (isMobile) return "1fr";
    if (isTablet) return "repeat(2, 1fr)";
    return "repeat(3, 1fr)";
  };

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Box sx={{ textAlign: "center", mb: 6 }}>
        <Box sx={{ mb: 3, display: 'flex', justifyContent: 'center' }}>
          <Logo variant="blue" size="xlarge" />
        </Box>
        <Typography variant="h2" component="h1" gutterBottom>
          Welcome to Neotool
        </Typography>
        <Typography variant="h5" color="text.secondary" sx={{ mx: "auto" }}>
          A comprehensive framework for building modern web applications
        </Typography>
      </Box>

      <Box 
        sx={{ 
          display: "grid", 
          gridTemplateColumns: getGridColumns(),
          gap: 4 
        }}
      >
        <Card 
          component={Link} 
          href="/design-system" 
          sx={{ 
            textDecoration: "none", 
            height: "100%",
            transition: "transform 0.2s ease-in-out",
            "&:hover": {
              transform: "translateY(-4px)",
              boxShadow: 4
            }
          }}
        >
          <CardContent sx={{ textAlign: "center", p: 4 }}>
            <DesignServicesRoundedIcon sx={{ fontSize: 48, color: "primary.main", mb: 2 }} />
            <Typography variant="h5" component="h2" gutterBottom>
              Design System
            </Typography>
            <Typography color="text.secondary">
              Explore our comprehensive design system with tokens, components, and guidelines
            </Typography>
          </CardContent>
        </Card>

        <Card 
          component={Link} 
          href="/examples" 
          sx={{ 
            textDecoration: "none", 
            height: "100%",
            transition: "transform 0.2s ease-in-out",
            "&:hover": {
              transform: "translateY(-4px)",
              boxShadow: 4
            }
          }}
        >
          <CardContent sx={{ textAlign: "center", p: 4 }}>
            <CodeRoundedIcon sx={{ fontSize: 48, color: "primary.main", mb: 2 }} />
            <Typography variant="h5" component="h2" gutterBottom>
              Examples
            </Typography>
            <Typography color="text.secondary">
              See real-world examples and implementations of our components
            </Typography>
          </CardContent>
        </Card>

        <Card 
          component={Link} 
          href="/documentation" 
          sx={{ 
            textDecoration: "none", 
            height: "100%",
            transition: "transform 0.2s ease-in-out",
            "&:hover": {
              transform: "translateY(-4px)",
              boxShadow: 4
            }
          }}
        >
          <CardContent sx={{ textAlign: "center", p: 4 }}>
            <MenuBookRoundedIcon sx={{ fontSize: 48, color: "primary.main", mb: 2 }} />
            <Typography variant="h5" component="h2" gutterBottom>
              Documentation
            </Typography>
            <Typography color="text.secondary">
              Comprehensive guides and API documentation for developers
            </Typography>
          </CardContent>
        </Card>
      </Box>
    </Container>
  );
}
