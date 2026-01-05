---
title: <Feature Name>
type: feature-guide
module: <module>
feature_slug: <feature-slug>
status: draft | in-progress | review | complete
version: 1.0.0
tags: [<tag1>, <tag2>]
search_keywords: [<keyword1>, <keyword2>]
related:
  - <path-to-related-doc>
  - <path-to-pattern-doc>
---

# <Feature Name>

> **One-line summary**: Brief description of what this feature does and why it exists.

> **Note**: This is a template. Remove any sections that don't apply to your feature (e.g., Migration Guide if there's no migration). This guide should not contain code - only reference code files and locations.

## Overview

**Problem Context**: What problem does this feature solve? What pain point does it address?

**Goals**: What are the primary objectives of this feature?

**Target Users**: Who will use this feature? (e.g., end users, administrators, system integrators)

**Success Criteria**: How will we know this feature is successful?

## Functional Requirements

### Core Functionality

**FR-1: [Requirement Name]**
- **Description**: What this requirement does
- **Priority**: Critical | High | Medium | Low
- **Acceptance Criteria**:
  - Criterion 1
  - Criterion 2
  - Criterion 3

**FR-2: [Requirement Name]**
- **Description**: ...
- **Priority**: ...
- **Acceptance Criteria**: ...

### User Flows

**Flow 1: [Flow Name]**
- **Goal**: What the user wants to achieve
- **Steps**:
  1. Step description
  2. Step description
  3. Step description
- **Expected Outcome**: What should happen at the end
- **Error Handling**: What happens if something goes wrong

**Flow 2: [Flow Name]**
- ...

### Scenarios

#### Scenario: [Scenario Name]
- **Type**: Happy Path | Validation | Edge Case | Error Handling | Non-Functional
- **Goal**: What this scenario validates
- **Context**: Initial state or preconditions
- **Actions**: What happens (user actions, system events, etc.)
- **Expected Behavior**: What should happen
- **Validation Points**:
  - What to verify
  - What to check
- **Notes**: Any additional context or considerations

#### Scenario: [Scenario Name]
- ...

### Business Rules

**BR-1: [Rule Name]**
- **Description**: What the rule enforces
- **When**: When this rule applies
- **Then**: What should happen
- **Exceptions**: Any exceptions to this rule
- **Examples**:
  - Example 1
  - Example 2

**BR-2: [Rule Name]**
- ...

### Validations

**Field-Level Validations**:
- **Field Name**: Validation rules, error messages, format requirements
- **Field Name**: ...

**Business Rule Validations**:
- **Validation Name**: When it triggers, what it checks, error handling
- **Validation Name**: ...

**Access Control Validations**:
- **Permission**: Who can perform what actions
- **Permission**: ...

## Non-Functional Requirements

> **Note**: General performance, reliability, usability, and maintainability requirements follow architectural standards. See [Architecture Standards](../../05-standards/architecture-standards/) for details.

### Security

**Permissions to Create** (in security module):
- **Permission Name**: `resource:action` - Description of what this permission allows
- **Permission Name**: `resource:action` - Description

**Authorization Checks**:
- Where authorization checks are needed and which permissions are required

**Audit Events**:
- **Event Name**: Description of what should be audited
- **Event Name**: Description

### Observability

**Key Performance Indicators (KPIs)**:
- **KPI Name**: Description, target value, how to measure
- **KPI Name**: Description, target value, how to measure

**Metrics to Track**:
- **Metric Name**: Description, where it's measured (service, endpoint, etc.)
- **Metric Name**: Description

**Alerts**:
- **Alert Condition**: When to trigger, severity, notification channel
- **Alert Condition**: Description

## Definitions

### Domain Terms
- **Term 1**: Definition
- **Term 2**: Definition
- **Term 3**: Definition

### Data Models
- **Entity/Model Name**: Description, fields, relationships
- **Entity/Model Name**: ...

### API Contracts
- **Operation Name**: Description, inputs, outputs, errors
- **Operation Name**: ...

## Implementation Details

> **Note**: Architecture decisions follow [Architecture Decision Records](../../09-adr/). Feature-specific decisions should be documented in ADRs if they impact multiple features.

### Dependencies
- **Internal Dependencies**: Other features, services, or modules this depends on
- **External Dependencies**: External services, libraries, or APIs

### Data Requirements
- **Database Changes**: Tables, columns, indexes, migrations needed
- **Data Migration**: Any data migration requirements
- **Storage**: File storage, caching, or other storage needs

### Integration Points
- **Service Integrations**: Other services this integrates with
- **API Integrations**: External APIs used
- **Event Integrations**: Events published or consumed

## Testing Strategy

> **Note**: Testing standards follow [Testing Standards](../../05-standards/testing-standards/). This section identifies feature-specific test requirements.

**Test Coverage Requirements**:
- Unit tests: Scope and key test cases
- Integration tests: Scope and key test cases
- E2E tests: User journeys and critical scenarios to test

**Feature-Specific Test Scenarios**:
- Scenario 1: Description
- Scenario 2: Description

## Runbooks

### Deployment Runbook

**Infrastructure Requirements** (`/infra` folder):
- **Files to create/modify**: Description of infrastructure files needed
  - Example: `infra/docker/docker-compose.yml` - Service configuration
  - Example: `infra/router/router.yaml` - Router configuration
  - Example: `infra/observability/prometheus/rules.yml` - Prometheus rules
- **Configuration**: Environment variables, secrets, or config files needed
- **Dependencies**: Infrastructure dependencies (databases, queues, etc.)

**Pre-Deployment Checklist**:
1. Checklist item
2. Checklist item

**Deployment Steps**:
1. Step description
2. Step description

**Post-Deployment Verification**:
1. Verification step
2. Health check endpoints to verify

### Operational Runbook
**Monitoring**:
- What to monitor
- Key metrics to watch
- Alert thresholds

**Troubleshooting**:
- **Issue**: Common issue description
  - **Symptoms**: How to identify
  - **Root Cause**: Common causes
  - **Resolution**: How to fix
  - **Prevention**: How to prevent

**Maintenance**:
- Regular maintenance tasks
- Scheduled operations

### Rollback Runbook
**When to Rollback**: Conditions that trigger rollback

**Rollback Steps**:
1. Step description
2. Step description

**Post-Rollback**:
1. Verification
2. Next steps

## Code References

> **Note**: This guide does not contain code. Reference actual code files and locations.

**Key Files**:
- **Backend**: `service/kotlin/<module>/src/main/kotlin/...` - Description
- **Frontend**: `web/src/...` - Description
- **GraphQL Schema**: `contracts/graphql/subgraphs/<module>/schema.graphqls` - Description
- **Database Migrations**: `service/kotlin/<module>/src/main/resources/db/migration/` - Description

**API Contracts**:
- **Operation**: Reference to GraphQL schema or API documentation
- **Operation**: Reference

**Database Schema**:
- **Table/Entity**: Reference to migration file or entity file
- **Table/Entity**: Reference

## Examples

### Example 1: [Use Case Name]
**Context**: When this example applies

**Description**: What the example demonstrates

**Reference**: Link to code example or test file

### Example 2: [Use Case Name]
...

## Open Questions

- [ ] Question 1
- [ ] Question 2
- [ ] Question 3

## References

- **Related Features**: Links to related feature guides
- **Patterns**: Links to relevant patterns (`docs/05-backend/patterns/`)
- **Standards**: Links to relevant standards (`docs/05-backend/standards/`)
- **ADRs**: Links to relevant architecture decision records (`docs/92-adr/`)
- **Architecture**: Links to architecture documentation (`docs/01-overview/`, `docs/02-architecture/`)
- **External Docs**: Links to external documentation

## Changelog

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0 | YYYY-MM-DD | Author | Initial version |

