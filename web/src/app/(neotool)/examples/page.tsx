"use client";

import React from "react";
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Container from '@mui/material/Container';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import PeopleRoundedIcon from '@mui/icons-material/PeopleRounded';
import InventoryRoundedIcon from '@mui/icons-material/InventoryRounded';
import DashboardRoundedIcon from '@mui/icons-material/DashboardRounded';
import ApiRoundedIcon from '@mui/icons-material/ApiRounded';
import StorageRoundedIcon from '@mui/icons-material/StorageRounded';
import SchemaRoundedIcon from '@mui/icons-material/SchemaRounded';
import { useResponsive } from "@/shared/hooks/useResponsive";
import Link from "next/link";

export default function ExamplesPage() {
  const { isMobile, isTablet } = useResponsive();
  
  const getGridColumns = () => {
    if (isMobile) return "1fr";
    if (isTablet) return "repeat(2, 1fr)";
    return "repeat(3, 1fr)";
  };

  const examples = [
    {
      title: "Customer Management",
      description: "Complete CRUD operations for customer data with search, filtering, and validation",
      icon: PeopleRoundedIcon,
      href: "/examples/customers",
      technologies: ["React", "GraphQL", "PostgreSQL", "REST API"],
      features: ["List/Search", "Create", "Update", "Delete", "Validation"]
    },
    {
      title: "Product Catalog",
      description: "Full product management system with inventory tracking and pricing",
      icon: InventoryRoundedIcon,
      href: "/examples/products",
      technologies: ["React", "GraphQL", "PostgreSQL", "REST API"],
      features: ["List/Search", "Create", "Update", "Delete", "Inventory"]
    },
    {
      title: "Analytics Dashboard",
      description: "Real-time dashboard with charts and data visualization",
      icon: DashboardRoundedIcon,
      href: "/examples/dashboard",
      technologies: ["React", "Charts", "API", "Real-time"],
      features: ["Charts", "Metrics", "Real-time", "Responsive"]
    },
    {
      title: "API Integration",
      description: "GraphQL and REST API integration patterns and examples",
      icon: ApiRoundedIcon,
      href: "/examples/api",
      technologies: ["GraphQL", "REST", "TanStack Query", "TypeScript"],
      features: ["Queries", "Mutations", "Subscriptions", "Error Handling"]
    },
    {
      title: "Database Operations",
      description: "PostgreSQL operations and data modeling examples",
      icon: StorageRoundedIcon,
      href: "/examples/database",
      technologies: ["PostgreSQL", "Migrations", "Indexes", "Relations"],
      features: ["Schema", "Migrations", "Queries", "Performance"]
    },
    {
      title: "GraphQL Schema",
      description: "GraphQL schema design and federation examples",
      icon: SchemaRoundedIcon,
      href: "/examples/graphql",
      technologies: ["GraphQL", "Schema", "Federation", "Subgraphs"],
      features: ["Schema", "Types", "Resolvers", "Federation"]
    }
  ];

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Box sx={{ mb: 6 }}>
        <Typography variant="h3" component="h1" gutterBottom>
          Full-Stack Examples
        </Typography>
        <Typography variant="h6" color="text.secondary" sx={{ maxWidth: 800 }}>
          Comprehensive examples showcasing the complete solution stack: 
          React frontend, GraphQL API, PostgreSQL database, and real-world CRUD operations.
        </Typography>
      </Box>

      <Box 
        sx={{ 
          display: "grid", 
          gridTemplateColumns: getGridColumns(),
          gap: 4 
        }}
      >
        {examples.map((example, index) => {
          const IconComponent = example.icon;
          return (
            <Box key={index}>
              <Card 
                sx={{ 
                  height: "100%",
                  display: "flex",
                  flexDirection: "column",
                  transition: "transform 0.2s ease-in-out",
                  "&:hover": {
                    transform: "translateY(-4px)",
                    boxShadow: 4
                  }
                }}
              >
                <CardContent sx={{ flexGrow: 1, p: 3 }}>
                  <Box sx={{ display: "flex", alignItems: "center", mb: 2 }}>
                    <IconComponent sx={{ fontSize: 32, color: "primary.main", mr: 2 }} />
                    <Typography variant="h5" component="h2">
                      {example.title}
                    </Typography>
                  </Box>
                  <Typography color="text.secondary" sx={{ mb: 3, flexGrow: 1 }}>
                    {example.description}
                  </Typography>
                  
                  <Box sx={{ mb: 2 }}>
                    <Typography variant="subtitle2" gutterBottom>
                      Technologies:
                    </Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      {example.technologies.map((tech, techIndex) => (
                        <Chip 
                          key={techIndex}
                          label={tech} 
                          size="small" 
                          variant="outlined"
                          color="primary"
                        />
                      ))}
                    </Stack>
                  </Box>

                  <Box sx={{ mb: 3 }}>
                    <Typography variant="subtitle2" gutterBottom>
                      Features:
                    </Typography>
                    <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                      {example.features.map((feature, featureIndex) => (
                        <Chip 
                          key={featureIndex}
                          label={feature} 
                          size="small" 
                          variant="filled"
                          color="secondary"
                        />
                      ))}
                    </Stack>
                  </Box>

                  <Button 
                    component={Link} 
                    href={example.href}
                    variant="contained" 
                    fullWidth
                    sx={{ mt: "auto" }}
                  >
                    View Example
                  </Button>
                </CardContent>
              </Card>
            </Box>
          );
        })}
      </Box>
    </Container>
  );
}
