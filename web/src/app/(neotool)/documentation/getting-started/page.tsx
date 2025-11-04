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
                primary="Java Development Kit (JDK)"
                secondary="Version 21 or higher (required for Kotlin backend)"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Docker"
                secondary="For running infrastructure services and integration tests"
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
            Installing Node.js with NVM
          </Typography>
          <Typography variant="body2" paragraph>
            We recommend using <strong>NVM (Node Version Manager)</strong> to install and manage
            Node.js versions. This allows you to easily switch between different Node versions for
            different projects.
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
            <code>{`# Install NVM (macOS/Linux)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.0/install.sh | bash

# Or using Homebrew (macOS)
brew install nvm

# After installation, reload your shell or run:
source ~/.zshrc  # or ~/.bashrc

# Install Node.js ${APP_CONFIG.nodeVersion}
nvm install ${APP_CONFIG.nodeVersion}

# Use the installed version
nvm use ${APP_CONFIG.nodeVersion}

# Set as default (optional)
nvm alias default ${APP_CONFIG.nodeVersion}`}</code>
          </Box>
          <Typography variant="body2" sx={{ mt: 1 }} color="text.secondary">
            Verify the installation by running <code>node --version</code> (should show v{APP_CONFIG.nodeVersion} or higher)
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Installing Java Development Kit (JDK) with SDKMAN
          </Typography>
          <Typography variant="body2" paragraph>
            For the Kotlin backend, you need JDK 21. We recommend using{" "}
            <strong>SDKMAN</strong> to manage JDK installations. SDKMAN allows you to easily
            install and switch between different JDK versions.
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
            <code>{`# Install SDKMAN
curl -s "https://get.sdkman.io" | bash

# Reload your shell
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install JDK 21 (we recommend Temurin)
sdk install java 21.0.2-tem

# Set as default
sdk default java 21.0.2-tem

# Verify installation
java -version`}</code>
          </Box>
          <Typography variant="body2" sx={{ mt: 1 }} color="text.secondary">
            Verify the installation by running <code>java -version</code> (should show version 21 or higher)
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Installing Docker with Colima (macOS)
          </Typography>
          <Typography variant="body2" paragraph>
            For macOS users, we recommend using <strong>Colima</strong> (Containers on Linux on Mac)
            as a lightweight alternative to Docker Desktop. Colima runs containers using Lima, a
            Linux virtual machine, without the overhead of Docker Desktop.
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
            <code>{`# Install Colima using Homebrew
brew install colima docker docker-compose

# Start Colima
colima start

# Verify Docker is working
docker ps

# To stop Colima when not needed
colima stop`}</code>
          </Box>
          <Typography variant="body2" sx={{ mt: 1 }} color="text.secondary">
            For Linux users, install Docker using your distribution's package manager. For Windows
            users, Docker Desktop is recommended.
          </Typography>

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

# Using Corepack (recommended)
corepack enable
corepack prepare pnpm@latest --activate

# Using standalone script
curl -fsSL https://get.pnpm.io/install.sh | sh -`}</code>
          </Box>
          <Typography variant="body2" sx={{ mt: 1 }} color="text.secondary">
            Verify the installation by running <code>pnpm --version</code>
          </Typography>
        </Paper>

        {/* Setup Options */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Setup Options
          </Typography>
          <Typography variant="body1" paragraph>
            Choose the setup method that best fits your situation:
          </Typography>

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Option 1: Starting a New Project
          </Typography>
          <Typography variant="body2" paragraph>
            If you're starting from scratch, clone the starter repository:
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
            <code>{`# Clone the repository
git clone ${REPO_CONFIG.githubUrl}.git
cd ${REPO_CONFIG.repoName}/web

# Install dependencies
npm install
# or
pnpm install`}</code>
          </Box>

          <Divider sx={{ my: 3 }} />

          <Typography variant="h6" sx={{ mb: 2 }}>
            Option 2: Integrating into an Existing Project
          </Typography>
          <Typography variant="body2" paragraph>
            If you already have a project and want to integrate {APP_CONFIG.name} into it, merge
            from remote to preserve commit history:
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
            <code>{`# Navigate to your existing project
cd /path/to/your/existing/project

# Add the starter as a remote
git remote add starter ${REPO_CONFIG.githubUrl}.git

# Fetch the starter repository
git fetch starter

# Merge the starter into your repo
git merge starter/main --allow-unrelated-histories

# Resolve any conflicts, then commit
git add .
git commit -m "Merge ${APP_CONFIG.name} starter boilerplate"`}</code>
          </Box>

          <Typography variant="body2" sx={{ mt: 2 }} color="text.secondary">
            For more detailed integration instructions, see the{" "}
            <Link href="/documentation/integration" style={{ color: "inherit" }}>
              Integration Guide
            </Link>
            .
          </Typography>
        </Paper>

        {/* Basic Setup */}
        <Paper sx={{ p: 4 }}>
          <Typography variant="h4" component="h2" gutterBottom>
            Basic Setup
          </Typography>
          <Typography variant="body1" paragraph>
            After installing dependencies, you need to configure environment variables and review
            project-specific settings.
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

          <Typography variant="h6" sx={{ mt: 3, mb: 2 }}>
            Post-Integration Steps (For Existing Projects)
          </Typography>
          <Typography variant="body2" paragraph>
            If you integrated {APP_CONFIG.name} into an existing project, you may need to:
          </Typography>
          <List>
            <ListItem>
              <ListItemText
                primary="Update Configuration Files"
                secondary="Review and merge .gitignore, package.json, and other config files"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Update Package Names"
                secondary="Update package.json and build.gradle.kts with your project name"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Review Documentation"
                secondary="Update README.md and other docs with your project-specific information"
              />
            </ListItem>
            <ListItem>
              <ListItemText
                primary="Check Port Conflicts"
                secondary="Ensure docker-compose ports don't conflict with existing services"
              />
            </ListItem>
          </List>
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

