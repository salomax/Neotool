---
title: Feature Questionnaire
type: template
category: feature-creation
status: current
version: 2.0.0
tags: [template, questionnaire, feature-discovery]
ai_optimized: true
search_keywords: [questionnaire, feature, discovery, requirements]
related:
  - 04-templates/feature-creation/feature-form.md
  - 04-templates/feature-creation/workflow.md
---

# Feature Questionnaire

> **Purpose**: Comprehensive questionnaire to discover all feature requirements before implementation.

## Basic Information

### Feature Overview
- **Feature Name**: [Name]
- **Feature Description**: [Brief description]
- **Business Value**: [Why is this feature needed?]

## User-Facing Questions

### Target Users
- [ ] Is this feature user-facing? (Yes/No)
  - If Yes: Who are the target users? [End users, Admins, Developers, etc.]
- [ ] Does this require a UI? (Yes/No)
  - If Yes: What type of UI? [Web, Mobile, Both, Admin Panel, etc.]

### User Interactions
- [ ] What actions can users perform? [List actions]
- [ ] What information do users need to see? [List data/views]
- [ ] Are there any user workflows? [Describe workflows]

## Technical Scope Questions

### Backend Requirements
- [ ] Does this feature require backend changes? (Yes/No)
  - If Yes:
    - [ ] New API endpoints needed? (Yes/No)
    - [ ] Database changes needed? (Yes/No)
    - [ ] Business logic changes needed? (Yes/No)
    - [ ] Integration with external services? (Yes/No)

### Frontend Requirements
- [ ] Does this feature require frontend changes? (Yes/No)
  - If Yes:
    - [ ] New pages needed? (Yes/No)
    - [ ] New components needed? (Yes/No)
    - [ ] Existing components need modification? (Yes/No)
    - [ ] New routes needed? (Yes/No)

### API Requirements
- [ ] Does this feature require GraphQL schema changes? (Yes/No)
  - If Yes:
    - [ ] New queries needed? (Yes/No)
    - [ ] New mutations needed? (Yes/No)
    - [ ] New subscriptions needed? (Yes/No)
    - [ ] Federation requirements? (Yes/No)

### Database Requirements
- [ ] Does this feature require database changes? (Yes/No)
  - If Yes:
    - [ ] New tables needed? (Yes/No)
    - [ ] New columns needed? (Yes/No)
    - [ ] New indexes needed? (Yes/No)
    - [ ] Data migrations needed? (Yes/No)

## Security & Access Control

### Authentication & Authorization
- [ ] Does this feature require authentication? (Yes/No)
- [ ] Does this feature require authorization? (Yes/No)
  - If Yes: What roles/permissions are needed? [List]
- [ ] Does this feature handle sensitive data? (Yes/No)
- [ ] Are there any security considerations? [Describe]

## Integration Requirements

### External Services
- [ ] Does this feature integrate with external services? (Yes/No)
  - If Yes: Which services? [List]
- [ ] Does this feature require new third-party dependencies? (Yes/No)

### Internal Services
- [ ] Does this feature interact with other services/modules? (Yes/No)
  - If Yes: Which services/modules? [List]
- [ ] Does this feature require GraphQL Federation? (Yes/No)

## Data & Domain Model

### Entities
- [ ] What entities are involved? [List entities]
- [ ] Are there new entities? (Yes/No)
- [ ] Are there entity relationships? (Yes/No)
  - If Yes: Describe relationships [List]

### Data Requirements
- [ ] What data needs to be stored? [Describe]
- [ ] What data needs to be retrieved? [Describe]
- [ ] Are there data validation requirements? (Yes/No)
- [ ] Are there data constraints? (Yes/No)

## Testing Requirements

### Test Coverage
- [ ] Unit tests required? (Yes/No)
- [ ] Integration tests required? (Yes/No)
- [ ] E2E tests required? (Yes/No)
- [ ] Performance tests required? (Yes/No)

### Test Scenarios
- [ ] What are the critical test scenarios? [List]

## Documentation Requirements

### Documentation Needs
- [ ] API documentation needed? (Yes/No)
- [ ] User documentation needed? (Yes/No)
- [ ] Developer documentation needed? (Yes/No)
- [ ] Migration guide needed? (Yes/No)

## Performance & Scalability

### Performance Considerations
- [ ] Are there performance requirements? (Yes/No)
  - If Yes: What are they? [Describe]
- [ ] Expected load/usage? [Describe]
- [ ] Caching requirements? (Yes/No)

## Timeline & Dependencies

### Dependencies
- [ ] Are there blocking dependencies? (Yes/No)
  - If Yes: What are they? [List]
- [ ] Are there related features? (Yes/No)
  - If Yes: Which features? [List]

### Timeline
- [ ] Target completion date: [Date]
- [ ] Priority: [High/Medium/Low]

## Additional Context

### Constraints
- [ ] Are there any technical constraints? [Describe]
- [ ] Are there any business constraints? [Describe]

### Assumptions
- [ ] What assumptions are we making? [List]

### Open Questions
- [ ] What questions need to be answered? [List]

## Next Steps

After completing this questionnaire:
1. ✅ Review answers for completeness
2. ⏭️ Fill out [Feature Form](./feature-form.md) with detailed requirements
3. ⏭️ Proceed to [Feature Development Workflow](../06-workflows/feature-development.md)

---

**Status**: [ ] Complete | [ ] In Progress | [ ] Needs Review

**Completed By**: [Name]
**Date**: [Date]

