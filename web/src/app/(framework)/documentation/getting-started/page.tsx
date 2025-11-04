"use client";

import React from "react";
import {
  Box,
  Typography,
  Container,
  Paper,
  Button,
  Divider,
  List,
  ListItem,
  ListItemText,
  Chip,
  Stack,
} from "@mui/material";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import Link from "next/link";
import { useResponsive } from "@/shared/hooks/useResponsive";
import { REPO_CONFIG, APP_CONFIG } from "@/shared/config/repo.constants";

export default function GettingStartedPage() {
  const { isMobile } = useResponsive();

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
          Getting Started
        </Typography>
        <Typography variant="h6" color="text.secondary" sx={{ maxWidth: 800 }}>
          Quick start guide to set up and run the {APP_CONFIG.name} frontend application.
        </Typography>
      </Box>

      <Stack spacing={4}>
        {/* Installation */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Installation
          </Typography>
          <Typography variant="body1" paragraph>
            Before you begin, ensure you have the following installed:
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="Node.js"
                secondary={`Version ${APP_CONFIG.nodeVersion} or higher (LTS recommended)`}
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="npm or pnpm"
                secondary="Package manager for managing dependencies"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Git"
                secondary="Version control system"
              />
            </ListItem>
          </List>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Installing pnpm
          </Typography>
          <Typography variant="body2" paragraph>
            While npm comes bundled with Node.js, we recommend using pnpm for faster and more
            efficient dependency management. Install pnpm using one of the following methods:
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
            <code>{`# Using npm
npm install -g pnpm

# Using Homebrew (macOS)
brew install pnpm

# Using Corepack
corepack enable
corepack prepare pnpm@latest --activate

# Using standalone script
curl -fsSL https://get.pnpm.io/install.sh | sh -`}</code>
          </Box>
          <Typography variant="body2" sx={{ mt: 1 }} color="text.secondary">
            Verify the installation by running <code>pnpm --version</code>
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Clone the Repository
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
            <code>git clone {REPO_CONFIG.githubUrl}.git</code>
            <br />
            <code>cd {REPO_CONFIG.repoName}/web</code>
          </Box>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Install Dependencies
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
            <code>npm install</code>
            <br />
            <code># or</code>
            <br />
            <code>pnpm install</code>
          </Box>
        </Paper>

        {/* Basic Setup */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Basic Setup
          </Typography>
          <Typography variant="body1" paragraph>
            After installing dependencies, you need to configure environment variables.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Environment Variables
          </Typography>
          <Typography variant="body2" paragraph>
            Create a <code>.env.local</code> file in the <code>web/</code> directory:
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
            <code>{`# API Configuration
NEXT_PUBLIC_API_URL=${APP_CONFIG.apiUrl}
NEXT_PUBLIC_GRAPHQL_URL=${APP_CONFIG.graphqlUrl}`}</code>
          </Box>
        </Paper>

        {/* Project Structure */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Project Structure
          </Typography>
          <Typography variant="body1" paragraph>
            Understanding the project structure will help you navigate and work with the codebase effectively.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Key Directories
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary={
                  <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                    <code>src/app/</code>
                    <Chip label="Next.js App Router" size="small" />
                  </Box>
                }
                secondary="Pages, layouts, and routing using Next.js App Router"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary={
                  <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                    <code>src/shared/</code>
                    <Chip label="Shared Code" size="small" />
                  </Box>
                }
                secondary="Reusable components, hooks, utilities, and configurations"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary={
                  <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                    <code>src/lib/</code>
                    <Chip label="Integrations" size="small" />
                  </Box>
                }
                secondary="External service integrations (GraphQL, API clients, domain hooks)"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary={
                  <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                    <code>src/styles/</code>
                    <Chip label="Theming" size="small" />
                  </Box>
                }
                secondary="Theme configuration, design tokens, and global styles"
              />
            </ListItem>
          </List>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Component Organization
          </Typography>
          <Typography variant="body2" paragraph>
            Components are organized by functional purpose:
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
            <code>{`shared/components/ui/
├── primitives/      # Basic building blocks
├── layout/          # Layout components
├── navigation/      # Navigation components
├── forms/           # Form components
├── data-display/    # Data visualization
└── feedback/        # User feedback components`}</code>
          </Box>
        </Paper>

        {/* Development Server */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Running the Development Server
          </Typography>
          <Typography variant="body1" paragraph>
            Start the development server to see your application in action.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Start Development Server
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
            <code>npm run dev</code>
            <br />
            <code># or</code>
            <br />
            <code>pnpm dev</code>
          </Box>

          <Typography variant="body2" sx={{ mt: 2 }}>
            Open{" "}
            <Link href={APP_CONFIG.devUrl} style={{ color: "inherit" }}>
              {APP_CONFIG.devUrl}
            </Link>{" "}
            in your browser to see the application.
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Available Scripts
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="npm run dev"
                secondary="Start development server"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="npm run build"
                secondary="Build for production"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="npm run start"
                secondary="Start production server"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="npm run lint"
                secondary="Run ESLint"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="npm run test"
                secondary="Run tests"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="npm run storybook"
                secondary="Start Storybook"
              />
            </ListItem>
          </List>
        </Paper>

        {/* Next Steps */}
        <Paper sx={{ p: 4, bgcolor: "primary.main", color: "primary.contrastText" }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Next Steps
          </Typography>
          <Typography variant="body1" paragraph>
            Now that you have the application running, explore these resources:
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary={
                  <Link
                    href="/documentation/themes"
                    style={{ color: "inherit", textDecoration: "underline" }}
                  >
                    Learn about Themes
                  </Link>
                }
                secondary="Understand the theme system and design tokens"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary={
                  <Link
                    href="/documentation/components"
                    style={{ color: "inherit", textDecoration: "underline" }}
                  >
                    Explore Components
                  </Link>
                }
                secondary="Browse the component library"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary={
                  <Link
                    href="/design-system/components"
                    style={{ color: "inherit", textDecoration: "underline" }}
                  >
                    Component Documentation
                  </Link>
                }
                secondary="View detailed component documentation"
              />
            </ListItem>
          </List>
        </Paper>
      </Stack>
    </Container>
  );
}

