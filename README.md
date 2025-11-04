<p align="center">
  <img src="./design/assets/logos/neotool-logo-blue.svg" alt="NeoTool Logo" width="220" />
</p>


# NeoTool — build enterprise solutions smarter and faster

![Kotlin](https://img.shields.io/badge/Kotlin-Micronaut-7F52FF?logo=kotlin)
![GraphQL](https://img.shields.io/badge/API-GraphQL-E10098?logo=graphql)
![React](https://img.shields.io/badge/Web-Next.js-000000?logo=nextdotjs)
![ReactNative](https://img.shields.io/badge/Mobile-React%20Native-61DAFB?logo=react)
![Docker](https://img.shields.io/badge/Infra-Docker%20Compose-2496ED?logo=docker)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

---

NeoTool is a **modular full‑stack boilerplate** designed to **accelerate new app development** while keeping **clean architecture and best practices** baked in from day one.

Think of it as a **foundation framework** that helps you spin up new services or apps (backend, frontend, infra, and design system), all wired together and ready to evolve.

 spec-driven development

---

## ✨ Overview

NeoTool brings together several layers under one monorepo:

| Layer | Description | Tech Stack |
|-------|--------------|-------------|
| **Backend (`service/`)** | Kotlin + Micronaut with GraphQL, modular architecture, reusable components, and testing setup. | Kotlin, Micronaut, GraphQL, Gradle |
| **Frontend (`web/`)** | React + Next.js web app scaffold ready to consume GraphQL APIs. | React, Next.js, TypeScript |
| **Mobile (`mobile/`)** | Expo + React Native setup for cross‑platform mobile apps. | React Native, Expo |
| **Infra (`infra/`)** | Docker Compose, Kubernetes (Kustomize), GitOps (ArgoCD), and observability stack. | Docker, K8s, Grafana, Prometheus, Loki |
| **Contracts (`contracts/`)** | GraphQL Federation + OpenAPI specs for schema standardization. | Apollo Federation, OpenAPI |
| **Design (`design/`)** | Brand assets, UI tokens, icons, and design guidelines. | Figma, Adobe, Tokens |
| **Docs (`docs/`)** | ADRs and developer documentation via Docusaurus. | Markdown, Docusaurus |

---

## 🧩 Architecture

```mermaid
graph TD
    subgraph Frontend
        Web[Next.js Web App]
        Mobile[React Native App]
    end

    subgraph Backend
        ServiceKotlin[Micronaut Service]
        Gateway[GraphQL Router]
        Common[Common Utilities]
    end

    subgraph Infra
        Docker[Docker Compose / K8s]
        GitOps[ArgoCD]
        Metrics[Prometheus + Grafana]
        Logs[Loki + Promtail]
    end

    Web --> Gateway
    Mobile --> Gateway
    Gateway --> ServiceKotlin
    ServiceKotlin --> DB[(Postgres)]
    Metrics --> Grafana
    Logs --> Grafana
```

```mermaid
---
title: Feature implementation
---
flowchart TD
    User("User") --> Prompt["Insert prompt describing feature to be implemented"]
    Prompt --> KB["Neotool reads the knowledge base"]
    KB --> Artifacts["Neotool creates the artifacts"]
    Artifacts --> CICD["CI/CD"]

    subgraph ArtifactTypes["Artifacts"]
      Docs["Feature Documentation<br/>(Functional & Non-functional docs)"] --> Gherkin
      FE["Front-end Artifacts<br/>(Web/Mobile)"]
      BE["Backend Artifacts<br/>(Services)"]
      DB[("Storage Layer (Database)")]
      E2E["E2E Tests"] --> Cypress["Cypress tests"]
    end

    subgraph CICDType["CICD"]
        Build --> Deploy
    end

    Artifacts --> Docs
    Artifacts --> FE
    Artifacts --> BE
    Artifacts --> DB
    Artifacts --> E2E
```

---

## Infrastructure

#TODO

---

## Frontend

#TODO

---

## Backend

#TODO

### APIs (GraphQL and REST)

### Sync services

#TODO

### Async services

#### Messaging

#TODO

#### Webhooks

#TODO

---

## Data storage layer

---

## 🧭 Roadmap

- [ ] Lint
- [ ] Add Security module (Auth, RBAC)
- [ ] Add feature flag service
- [ ] Enable K8s deploy via GitOps  
- [ ] AI-based documentation assistant 🤖

---

## 🤝 Contributing

Pull requests, issues, and ideas are super welcome!  
Just keep the structure clean and consistent with existing modules.
