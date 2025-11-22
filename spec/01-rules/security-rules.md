---
title: Security Rules
type: rule
category: security
status: current
version: 2.0.0
tags: [security, auth, authorization, rules]
ai_optimized: true
search_keywords: [security, authentication, authorization, jwt, rules]
related:
  - 00-core/architecture.md
---

# Security Rules

> **Purpose**: Security and authentication rules.

## Authentication Rules

### Rule: JWT Token Usage

**Rule**: Use JWT tokens for authentication (access tokens for API, refresh tokens for renewal).

**Rationale**: Stateless, scalable authentication.

### Rule: Token Expiration

**Rule**: Access tokens must be short-lived (default: 15 minutes), refresh tokens long-lived (default: 7 days).

**Rationale**: Security best practice.

## Authorization Rules

### Rule: Input Validation

**Rule**: Validate all inputs at API boundaries.

**Rationale**: Prevents injection attacks.

### Rule: Parameterized Queries

**Rule**: Always use parameterized queries (never string concatenation).

**Rationale**: Prevents SQL injection.

## Related Documentation

- [Architecture Overview](../00-core/architecture.md)

