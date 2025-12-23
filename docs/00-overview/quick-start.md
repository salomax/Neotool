---
title: Quick Start Guide
type: overview
category: getting-started
status: current
version: 1.0.0
tags: [quick-start, getting-started, setup, installation]
ai_optimized: true
search_keywords: [quick-start, getting-started, setup, installation, prerequisites]
related:
  - 00-overview/project-overview.md
  - 00-overview/architecture-overview.md
  - 00-overview/technology-stack.md
---

# Quick Start Guide

> **Purpose**: Get NeoTool up and running quickly.

## Prerequisites

Before you begin, ensure you have the following installed:

- **Node.js** - Version 20.x or higher (LTS recommended)
- **JDK** - Version 21 or higher
- **nvm** (Node Version Manager) - For managing Node.js versions
- **sdkman** (SDK Manager) - For managing JDK and other SDKs
- **Git** - Version control system
- **Docker Engine** - For running infrastructure services (via Colima on Mac/Linux)
- **Colima** - For running Docker Engine on Mac/Linux without Docker Desktop

### Installation Instructions

#### macOS

```bash
# Install Homebrew (if not already installed)
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install Git
brew install git

# Install nvm (Node Version Manager)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.1/install.sh | bash

# Reload your shell configuration
source ~/.zshrc  # or ~/.bash_profile if using bash

# Install Node.js 20.x LTS using nvm
nvm install 20
nvm use 20
nvm alias default 20

# Install sdkman (SDK Manager)
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install JDK 21 using sdkman
sdk install java 21-tem

# Install Colima (Docker Engine alternative)
brew install colima docker docker-compose

# Start Colima
colima start

# Verify installations
node --version    # Should show v20.x.x
java --version    # Should show openjdk 21.x.x
git --version     # Should show git version
docker --version  # Should show Docker version
```

#### Linux

```bash
# Install Git
sudo apt update  # For Debian/Ubuntu
sudo apt install -y git
# OR for Fedora/RHEL
sudo dnf install -y git

# Install nvm (Node Version Manager)
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.1/install.sh | bash

# Reload your shell configuration
source ~/.bashrc  # or ~/.zshrc if using zsh

# Install Node.js 20.x LTS using nvm
nvm install 20
nvm use 20
nvm alias default 20

# Install sdkman (SDK Manager)
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Install JDK 21 using sdkman
sdk install java 21-tem

# Install Colima (Docker Engine alternative)
# For Debian/Ubuntu
sudo apt install -y colima docker.io docker-compose
# OR for Fedora/RHEL
sudo dnf install -y colima docker docker-compose

# Start Colima (provides Docker Engine)
colima start

# Verify installations
node --version    # Should show v20.x.x
java --version    # Should show openjdk 21.x.x
git --version     # Should show git version
docker --version  # Should show Docker version
```

## Setup Options

### Option 1: Starting a New Project

If you're starting from scratch, clone the starter repository:

```bash
# Clone the repository
git clone https://github.com/salomax/neotool.git
cd neotool

# Install dependencies for web frontend
cd web
npm install
# or
pnpm install

# Install dependencies for Kotlin backend
cd ../service/kotlin
./gradlew build

# Install dependencies for mobile (optional)
cd ../../mobile
npm install
```

### Option 2: Integrating into an Existing Project

If you already have a project and want to integrate NeoTool into it:

```bash
# Navigate to your existing project
cd /path/to/your/existing/project

# Add the starter as a remote
git remote add starter https://github.com/salomax/neotool.git

# Fetch the starter repository
git fetch starter

# Merge the starter into your repo
git merge starter/main --allow-unrelated-histories

# Resolve any conflicts, then commit
git add .
git commit -m "Merge NeoTool starter boilerplate"
```

## Customizing Your Project Name

After cloning or integrating the starter, customize the project name from "neotool" to your own project name.

**Quick Setup:**

1. **Check system requirements:**
   ```bash
   ./neotool --version
   ```
   This verifies that Node.js, Docker, and JVM are installed.

2. **Edit `project.config.json`** with your project details

## Configuration

### Environment Variables for Infrastructure

Create `.env.local` files in the `infra/` directory:

```plaintext
# --- Global ---
APP_NAME=neotool
APP_LOCALE=en-US
GRAPHQL_ENDPOINT=http://router:4000/graphql

# --- Database ---
POSTGRES_USER=neotool
POSTGRES_PASSWORD=neotool
POSTGRES_DB=neotool_db
POSTGRES_HOST=postgres
POSTGRES_PORT=5432

# --- Grafana ---
GF_SECURITY_ADMIN_USER=admin
GF_SECURITY_ADMIN_PASSWORD=admin

# --- AI ---
GEMINI_API_KEY=<enter Gemini key here>
```

### Environment Variables for Web

Create `.env.local` files in the `web/` directory with your API URLs.

## Running the Application

### Start Development Server

1. **Start infrastructure** (Docker Compose):
   ```bash
   cd infra/docker
   docker-compose -f docker-compose.local.yml up -d
   ```

2. **Start backend**:
   ```bash
   cd service/kotlin
   ./gradlew run
   ```

3. **Start frontend**:
   ```bash
   cd web
   npm run dev
   # or
   pnpm dev
   ```

### Access the Application

- **Web App**: http://localhost:3000
- **GraphQL Playground**: http://localhost:4000/graphql
- **Grafana**: http://localhost:3001 (admin/admin)

## Next Steps

- Review the [Architecture Overview](./architecture-overview.md)
- Check [Architecture Decision Records](../09-adr/) for technology choices
- Explore [Patterns](../04-patterns/) for implementation guidance
- See [Examples](../07-examples/) for working code
- Read [Feature Development Workflow](../06-workflows/feature-development.md)

## Troubleshooting

### Common Issues

1. **Port already in use**: Change ports in configuration files
2. **Docker not running**: Start Colima with `colima start`
3. **Database connection errors**: Check Docker containers are running
4. **Node version issues**: Use `nvm use 20` to switch to correct version

For more help, see [Troubleshooting Guide](../10-reference/troubleshooting.md).

## Related Documentation

- [Project Overview](./project-overview.md) - What is NeoTool?
- [Architecture Overview](./architecture-overview.md) - System architecture
- [Technology Stack](./technology-stack.md) - Technology choices
- [Project Structure](./project-structure.md) - Monorepo organization

---

*Get started building with NeoTool today!*

