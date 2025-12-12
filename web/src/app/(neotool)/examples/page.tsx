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
import { useResponsive } from "@/shared/hooks/ui";
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
