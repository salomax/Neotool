"use client";

import React, { useState } from "react";
import { 
  Box, 
  Typography, 
  Container, 
  Card, 
  CardContent,
  Button,
  Chip,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  TextField,
  InputAdornment,
  Tabs,
  Tab,
  Badge,
  Avatar,
  Tooltip,
  IconButton,
  SearchIcon,
  FilterListIcon,
  CodeIcon,
  CategoryIcon,
  WidgetsIcon,
  ExtensionIcon,
  ViewModuleIcon,
  ViewListIcon,
  ViewComfyIcon,
  ViewListIconAlt,
  OpenInNewIcon,
  ContentCopyIcon,
  CloseIcon
} from "@/shared/ui/mui-imports";
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { useResponsive } from "@/shared/hooks/useResponsive";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { getAllComponentNames, getComponentDocs } from "./[component]/docs/registry";
import type { ComponentData } from "./[component]/docs/types";

// Component categories configuration
const categoryConfig = {
  primitives: {
    title: "Primitives",
    description: "Basic building blocks of our design system",
    icon: <CategoryIcon />,
    color: "primary" as const,
  },
  forms: {
    title: "Forms",
    description: "Form fields and input components",
    icon: <WidgetsIcon />,
    color: "secondary" as const,
  },
  "data-display": {
    title: "Data Display",
    description: "Tables, charts, and data visualization components",
    icon: <ExtensionIcon />,
    color: "success" as const,
  },
  layout: {
    title: "Layout",
    description: "Components for structuring and organizing content",
    icon: <ViewModuleIcon />,
    color: "info" as const,
  },
};

// Extract category from component's githubUrl path
function getCategoryFromGithubUrl(githubUrl: string): string {
  const match = githubUrl.match(/\/ui\/([^/]+)/);
  return match?.[1] ?? 'primitives';
}

// Get component categories dynamically from registry
function getComponentCategories() {
  const categoryMap: Record<string, Array<{ name: string; data: any }>> = {};
  const componentNames = getAllComponentNames();
  
  // Group components by category
  for (const name of componentNames) {
    const docs = getComponentDocs(name);
    if (!docs) continue;
    
    const category = getCategoryFromGithubUrl(docs.data.githubUrl);
    if (!categoryMap[category]) {
      categoryMap[category] = [];
    }
    categoryMap[category].push({ name, data: docs.data });
  }
  
  // Map to category config
  const categories: Record<string, any> = {};
  for (const [categoryKey, components] of Object.entries(categoryMap)) {
    const config = categoryConfig[categoryKey as keyof typeof categoryConfig];
    if (!config) continue; // Skip unknown categories
    
    categories[categoryKey] = {
      ...config,
      components: components.map(({ name, data }) => ({
        name: data.name,
        description: data.description,
        status: data.status,
        tests: data.tests || false,
      })),
    };
  }
  
  return categories;
}


export default function ComponentsPage() {
  const { isMobile } = useResponsive();
  const router = useRouter();
  const [searchTerm, setSearchTerm] = useState("");
  const [selectedCategory, setSelectedCategory] = useState("all");
  const [viewMode, setViewMode] = useState<"grid" | "list">("grid");
  
  // Get component categories dynamically from registry
  const componentCategories = React.useMemo(() => getComponentCategories(), []);

  const filteredCategories = Object.entries(componentCategories)
    .filter(([key, category]) => {
      if (selectedCategory !== "all" && key !== selectedCategory) return false;
      
      if (!searchTerm.trim()) return true;
      
      const filteredComponents = category.components.filter((component: { name: string; description: string; status: string; tests?: boolean }) =>
        component.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
        component.description.toLowerCase().includes(searchTerm.toLowerCase())
      );
      
      return filteredComponents.length > 0;
    })
    .map(([key, category]) => {
      if (!searchTerm.trim()) return [key, category] as const;
      
      const filteredComponents = category.components.filter((component: { name: string; description: string; status: string; tests?: boolean }) =>
        component.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
        component.description.toLowerCase().includes(searchTerm.toLowerCase())
      );
      
      return [key, { ...category, components: filteredComponents }] as const;
    });

  const getStatusColor = (status: string) => {
    switch (status) {
      case "stable": return "success";
      case "beta": return "warning";
      case "deprecated": return "error";
      default: return "default";
    }
  };

  const getStatusLabel = (status: string) => {
    switch (status) {
      case "stable": return "Stable";
      case "beta": return "Beta";
      case "deprecated": return "Deprecated";
      default: return status;
    }
  };

  const handleViewComponent = (componentName: string) => {
    // Navigate to component detail page using Next.js routing
    router.push(`/design-system/components/${componentName.toLowerCase()}`);
  };

  return (
    <Container maxWidth="xl" sx={{ py: 4 }}>
      {/* Header */}
      <Box sx={{ mb: 6 }}>
        <Button
          component={Link}
          href="/design-system"
          startIcon={<ArrowBackIcon />}
          sx={{ mb: 3 }}
        >
          Back to Design System
        </Button>
        <Typography variant="h3" component="h1" gutterBottom>
          Components
        </Typography>
        <Typography variant="h6" color="text.secondary" sx={{ maxWidth: 800, mb: 4 }}>
          Explore our comprehensive collection of reusable UI components, organized by functional purpose.
        </Typography>
        
        {/* Search Results Counter */}
        {searchTerm && (
          <Box sx={{ mb: 2 }}>
            <Typography variant="body2" color="text.secondary">
              Found {filteredCategories.reduce((total, [, category]) => total + (category?.components?.length || 0), 0)} components matching &quot;{searchTerm}&quot;
            </Typography>
          </Box>
        )}

        {/* Search and Filters */}
        <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap", alignItems: "center", mb: 4 }}>
          <TextField
            placeholder="Search components..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon />
                </InputAdornment>
              ),
              endAdornment: searchTerm && (
                <InputAdornment position="end">
                  <IconButton
                    size="small"
                    onClick={() => setSearchTerm("")}
                    edge="end"
                  >
                    <CloseIcon />
                  </IconButton>
                </InputAdornment>
              ),
            }}
            sx={{ minWidth: 300 }}
          />
          
          <Button
            variant="outlined"
            startIcon={viewMode === "grid" ? <ViewListIconAlt /> : <ViewComfyIcon />}
            onClick={() => setViewMode(viewMode === "grid" ? "list" : "grid")}
          >
            {viewMode === "grid" ? "List View" : "Grid View"}
          </Button>
        </Box>

        {/* Category Tabs */}
        <Tabs
          value={selectedCategory}
          onChange={(_, value) => setSelectedCategory(value)}
          variant={isMobile ? "scrollable" : "standard"}
          scrollButtons="auto"
        >
          <Tab label="All Components" value="all" />
          {Object.entries(componentCategories).map(([key, category]) => (
            <Tab
              key={key}
              label={category.title}
              value={key}
              icon={category.icon}
              iconPosition="start"
            />
          ))}
        </Tabs>
      </Box>

      {/* Components Grid */}
      <Box sx={{ display: "flex", flexDirection: "column", gap: 4 }}>
        {filteredCategories.map(([categoryKey, category]) => (
          <Accordion key={categoryKey} defaultExpanded>
            <AccordionSummary expandIcon={<CodeIcon />}>
              <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
                <Box sx={{ color: `${category.color}.main` }}>
                  {category.icon}
                </Box>
                <Box>
                  <Typography variant="h6" component="h2">
                    {category.title}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {category.description}
                  </Typography>
                </Box>
                <Chip 
                  label={category.components.length} 
                  size="small" 
                  color={category.color}
                  sx={{ ml: "auto" }}
                />
              </Box>
            </AccordionSummary>
            <AccordionDetails>
              <Box sx={{ 
                display: viewMode === "grid" ? 'grid' : 'flex',
                flexDirection: viewMode === "list" ? 'column' : undefined,
                gridTemplateColumns: viewMode === "grid" ? { 
                  xs: '1fr', 
                  sm: 'repeat(2, 1fr)', 
                  md: 'repeat(3, 1fr)', 
                  lg: 'repeat(4, 1fr)' 
                } : undefined,
                gap: 3 
              }}>
                {category.components.map((component: any) => (
                  <Box key={component.name} sx={viewMode === "list" ? { width: "100%" } : {}}>
                    <Card 
                      onClick={() => handleViewComponent(component.name)}
                      sx={{ 
                        height: "100%", 
                        display: "flex", 
                        flexDirection: "column",
                        cursor: "pointer",
                        transition: "all 0.2s ease-in-out",
                        "&:hover": {
                          transform: "translateY(-2px)",
                          boxShadow: 4
                        }
                      }}
                    >
                      <CardContent sx={{ flexGrow: 1 }}>
                        <Box sx={{ display: "flex", alignItems: "flex-start", justifyContent: "space-between", mb: 2 }}>
                          <Typography variant="h6" component="h3" gutterBottom>
                            {component.name}
                          </Typography>
                          <Chip
                            label={getStatusLabel(component.status)}
                            size="small"
                            color={getStatusColor(component.status) as any}
                          />
                        </Box>
                        
                        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                          {component.description}
                        </Typography>

                        <Box sx={{ display: "flex", gap: 1, alignItems: "center" }}>
                          {component.tests && (
                            <Tooltip title="Has unit tests">
                              <Chip
                                icon={<CodeIcon />}
                                label="Tests"
                                size="small"
                                variant="outlined"
                                color="success"
                              />
                            </Tooltip>
                          )}
                        </Box>
                      </CardContent>
                    </Card>
                  </Box>
                ))}
              </Box>
            </AccordionDetails>
          </Accordion>
        ))}
      </Box>

          {/* Summary Stats */}
          <Box sx={{ mt: 6, p: 3, bgcolor: "background.paper", borderRadius: 2 }}>
            <Typography variant="h6" gutterBottom>
              Component Library Summary
            </Typography>
            <Box sx={{
              display: 'flex',
              flexWrap: 'wrap',
              gap: 3,
              justifyContent: 'center',
              alignItems: 'center'
            }}>
              {Object.entries(componentCategories).map(([key, category]) => (
                <Box key={key} sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                  <Typography variant="h4" color={`${category.color}.main`}>
                    {category.components.length}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {category.title}
                  </Typography>
                  {key !== Object.keys(componentCategories)[Object.keys(componentCategories).length - 1] && (
                    <Typography variant="body2" color="text.secondary" sx={{ mx: 1 }}>
                      •
                    </Typography>
                  )}
                </Box>
              ))}
            </Box>
          </Box>
    </Container>
  );
}