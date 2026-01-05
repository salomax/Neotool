---
title: Getting Started with NeoTool
type: overview
category: getting-started
status: current
version: 3.0.0
tags: [quick-start, getting-started, setup, installation, prerequisites]
ai_optimized: true
search_keywords: [quick-start, getting-started, setup, installation, prerequisites, first-steps]
related:
  - 01-overview/README.md
  - 01-overview/architecture-at-a-glance.md
  - 08-workflows/feature-development.md
  - 93-reference/commands.md
last_updated: 2026-01-02
---

# Getting Started with NeoTool

> **Goal**: Get up and running with NeoTool in 15 minutes.

## Prerequisites

Before you begin, ensure you have these tools installed:

| Tool | Version | Purpose | Installation |
|------|---------|---------|--------------|
| **Node.js** | 20.x LTS | Frontend runtime | [nodejs.org](https://nodejs.org) or use `nvm` |
| **JDK** | 21+ | Backend runtime | [adoptium.net](https://adoptium.net) or use `sdkman` |
| **Docker** | Latest | Containers | [docker.com](https://docker.com) or `colima` (Mac/Linux) |
| **Git** | 2.x+ | Version control | [git-scm.com](https://git-scm.com) |

### Quick Install (macOS/Linux)

```bash
# Install package managers
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
curl -s "https://get.sdkman.io" | bash

# Install runtimes
nvm install 20 && nvm use 20 && nvm alias default 20
sdk install java 21-tem

# Install Docker alternative (no Docker Desktop needed)
brew install colima docker docker-compose
colima start

# Verify
node --version  # Should show v20.x.x
java --version  # Should show openjdk 21.x.x
docker --version
```

**See**: [Installation Reference](../93-reference/installation.md) for detailed platform-specific instructions.

---

## Choose Your Path

Select the option that matches your goal:

### Path A: Start a New Project
**Best for**: Creating a new application from scratch
- ✅ Clean slate with all NeoTool features
- ✅ Recommended project structure
- ⏱️ Time: 10 minutes

[👉 Jump to Path A](#path-a-start-a-new-project-1)

---

### Path B: Add to Existing Project
**Best for**: Integrating NeoTool into an existing codebase
- ✅ Cherry-pick features you need
- ✅ Gradual adoption
- ⏱️ Time: 20 minutes

[👉 Jump to Path B](#path-b-add-to-existing-project-1)

---

### Path C: Explore First
**Best for**: Learning and evaluation
- ✅ No commitment
- ✅ Read-only exploration
- ⏱️ Time: 5 minutes

[👉 Jump to Path C](#path-c-explore-first-1)

---

## Path A: Start a New Project

### 1. Clone the Repository

```bash
git clone https://github.com/salomax/neotool.git my-project
cd my-project
```

### 2. Customize Your Project

Edit `project.config.json`:

```json
{
  "name": "my-project",
  "displayName": "My Project",
  "description": "Your project description",
  "version": "1.0.0",
  "repository": "https://github.com/yourusername/my-project"
}
```

### 3. Install Dependencies

```bash
# Backend
cd service/kotlin
./gradlew build

# Frontend
cd ../../web
npm install  # or: pnpm install

# Return to root
cd ..
```

### 4. Configure Environment

Create `infra/docker/.env.local`:

```bash
# Copy template
cp infra/docker/.env.example infra/docker/.env.local

# Edit with your values
nano infra/docker/.env.local
```

**Minimal config**:
```env
APP_NAME=my-project
POSTGRES_USER=myproject
POSTGRES_PASSWORD=secure_password_here
POSTGRES_DB=myproject_db
```

### 5. Start the Stack

```bash
# Start infrastructure (PostgreSQL, Grafana, etc.)
cd infra/docker
docker-compose -f docker-compose.local.yml up -d

# Start backend (in new terminal)
cd service/kotlin
./gradlew run

# Start frontend (in new terminal)
cd web
npm run dev
```

### 6. Verify Installation

Open your browser to:
- **Frontend**: http://localhost:3000
- **GraphQL Playground**: http://localhost:4000/graphql
- **Grafana**: http://localhost:3001 (admin/admin)

✅ **Success**: You should see the NeoTool welcome page!

### Next Steps
- [Create your first feature](../08-workflows/feature-development.md)
- [Explore the architecture](./architecture-at-a-glance.md)
- [Review core principles](./core-principles.md)

---

## Path B: Add to Existing Project

### 1. Add NeoTool as Remote

```bash
cd /path/to/your/existing/project
git remote add neotool https://github.com/salomax/neotool.git
git fetch neotool
```

### 2. Cherry-Pick What You Need

**Option 1: Merge Entire Specification**
```bash
# Add docs/ directory
git checkout neotool/main -- docs/
git add docs/
git commit -m "Add NeoTool specification"
```

**Option 2: Merge Specific Features**
```bash
# Add specific modules
git checkout neotool/main -- docs/05-backend/patterns/
git checkout neotool/main -- docs/07-frontend/patterns/
git checkout neotool/main -- docs/91-templates/

git add docs/
git commit -m "Add NeoTool patterns and templates"
```

### 3. Adapt to Your Architecture

Update paths in documentation to match your structure:

```bash
# Find and replace in docs
find docs -type f -name "*.md" -exec sed -i '' \
  's|service/kotlin|backend|g' {} \;
find docs -type f -name "*.md" -exec sed -i '' \
  's|web/src|frontend/src|g' {} \;
```

### 4. Configure AI Integration

Add to your IDE/AI tool:

```json
// .cursorrules or .claude/config.json
{
  "specificationPath": "docs/",
  "manifestFile": "docs/manifest.md",
  "sddEnabled": true,
  "contextStrategy": "docs/08-workflows/spec-context-strategy.md"
}
```

### Next Steps
- [Align with NeoTool principles](./core-principles.md)
- [Adapt patterns to your stack](../05-backend/patterns/)
- [Create ADRs for deviations](../92-adr/README.md)

---

## Path C: Explore First

### 1. Browse the Repository

```bash
git clone https://github.com/salomax/neotool.git
cd neotool
```

### 2. Read Key Documents

```bash
# High-level overview
cat docs/01-overview/README.md

# Architecture
cat docs/01-overview/architecture-at-a-glance.md

# SDD philosophy
cat docs/01-overview/specification-driven-development.md

# Complete index
cat docs/manifest.md
```

### 3. Explore the Code

```bash
# Backend structure
tree service/kotlin/app/src -L 3

# Frontend structure
tree web/src -L 2

# Infrastructure
tree infra/ -L 2
```

### 4. Run in Read-Only Mode

```bash
# Just start PostgreSQL
cd infra/docker
docker-compose -f docker-compose.local.yml up postgres -d

# Explore GraphQL schema
cat contracts/graphql/subgraphs/app/schema.graphqls
```

### Next Steps
- [Understand the principles](./core-principles.md)
- [Review example features](../90-examples/)
- [Check ADRs for tech decisions](../92-adr/)

---

## Verification Checklist

After setup, verify everything works:

### Infrastructure
- [ ] Docker containers running (`docker ps`)
- [ ] PostgreSQL accessible (`docker exec -it postgres psql -U neotool`)
- [ ] Grafana dashboard loads (http://localhost:3001)

### Backend
- [ ] Kotlin service starts without errors
- [ ] GraphQL playground accessible (http://localhost:4000/graphql)
- [ ] Health endpoint responds (http://localhost:8080/health)
- [ ] Database migrations applied

### Frontend
- [ ] Next.js dev server running
- [ ] Homepage loads (http://localhost:3000)
- [ ] No console errors
- [ ] GraphQL client connects

### Development Tools
- [ ] Hot reload works (change a file, see update)
- [ ] Tests pass (`./gradlew test` and `npm test`)
- [ ] Linting passes (`./gradlew ktlintCheck` and `npm run lint`)

---

## Troubleshooting

### Port Already in Use
```bash
# Find process using port 3000 (or other)
lsof -i :3000

# Kill process
kill -9 <PID>
```

### Docker Issues
```bash
# Restart Docker (Colima)
colima stop && colima start

# Reset Docker state
docker-compose -f docker-compose.local.yml down -v
docker-compose -f docker-compose.local.yml up -d
```

### Database Connection Errors
```bash
# Check PostgreSQL is running
docker ps | grep postgres

# View logs
docker logs postgres

# Recreate database
docker-compose down -v
docker-compose up -d
```

### Node/Java Version Issues
```bash
# Use correct Node version
nvm use 20

# Use correct Java version
sdk use java 21-tem

# Verify
node --version && java --version
```

**See**: [Troubleshooting Guide](../93-reference/troubleshooting.md) for comprehensive solutions.

---

## Common Next Steps by Role

### Developer
1. [Create your first feature](../08-workflows/feature-development.md)
2. [Learn backend patterns](../05-backend/patterns/)
3. [Learn frontend patterns](../07-frontend/patterns/)
4. [Use code templates](../91-templates/code-templates/)

### Architect
1. [Review system architecture](../02-architecture/system-architecture.md)
2. [Understand core principles](./core-principles.md)
3. [Read ADRs](../92-adr/)
4. [Explore domain model](../04-domain/domain-model.md)

### Product Manager
1. [Understand feature specs](../03-features/)
2. [Review development workflow](../08-workflows/feature-development.md)
3. [Check validation checklists](../94-validation/)

### AI Assistant / LLM
1. [Read SDD guide](./specification-driven-development.md)
2. [Load specification manifest](../manifest.md)
3. [Follow context strategy](../08-workflows/spec-context-strategy.md)
4. [Use feature templates](../91-templates/feature-templates/)

---

## Learning Resources

### Essential Reading (1 hour)
1. [README](./README.md) - Project overview
2. [Architecture at a Glance](./architecture-at-a-glance.md) - System design
3. [SDD Guide](./specification-driven-development.md) - Development philosophy
4. [Feature Workflow](../08-workflows/feature-development.md) - How to build

### Deep Dive (4 hours)
1. [System Architecture](../02-architecture/system-architecture.md)
2. [Domain Model](../04-domain/domain-model.md)
3. [All Patterns](../05-backend/patterns/ + ../07-frontend/patterns/)
4. [All Standards](../05-backend/standards/ + ../06-contracts/)

### Examples & Templates (2 hours)
1. [CRUD Example](../90-examples/backend/crud-example/)
2. [Batch Workflow Example](../90-examples/backend/batch-workflows/)
3. [Code Templates](../91-templates/code-templates/)
4. [AI Prompts](../91-templates/ai-prompts/)

---

## Support & Community

- **Documentation Issues**: [GitHub Issues](https://github.com/salomax/neotool/issues)
- **Questions**: [GitHub Discussions](https://github.com/salomax/neotool/discussions)
- **Contributing**: See [CONTRIBUTING.md](../../CONTRIBUTING.md)

---

**Estimated Time to Productivity**:
- Basic setup: 15 minutes
- First feature: 2 hours
- Team proficiency: 1 week

*Welcome to Specification-Driven Development with NeoTool!*
