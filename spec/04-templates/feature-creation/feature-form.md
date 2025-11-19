---
title: Feature Creation Form
type: template
category: feature-creation
status: current
version: 2.0.0
tags: [template, feature-form, feature-creation, ai-optimized]
ai_optimized: true
search_keywords: [feature, form, creation, template, prompt]
related:
  - 04-templates/feature-creation/questionnaire.md
  - 04-templates/feature-creation/workflow.md
---

# Feature Creation Form

> **Purpose**: Simple, human-readable form for creating features. Fill this form and provide it to AI to generate complete feature implementation.

## How to Use

1. **Fill out this form** with your feature requirements
2. **Provide the completed form to AI** (Cursor, ChatGPT, etc.)
3. **AI generates** complete feature implementation following NeoTool patterns
4. **Review and validate** using checklists

## Feature Information

### Basic Details
- **Feature Name**: [e.g., "User Profile Management"]
- **Feature Description**: [Brief description of what the feature does]
- **Business Value**: [Why is this feature needed?]

### Scope
- [ ] Backend only
- [ ] Frontend only
- [ ] Full-stack (Backend + Frontend)
- [ ] Database changes required
- [ ] API changes required

## Backend Requirements

### Entity/Model
- **Entity Name**: [e.g., "UserProfile"]
- **Fields**: 
  - [Field name]: [Type] - [Description]
  - [Field name]: [Type] - [Description]
- **Relationships**: 
  - [Relationship description]
- **Module**: [app | security | assistant | other]

### Operations
- [ ] Create
- [ ] Read (single)
- [ ] Read (list/search)
- [ ] Update
- [ ] Delete
- [ ] Other: [specify]

### GraphQL Schema
- **Queries**: [List queries needed, e.g., "getUserProfile(id: ID!): UserProfile"]
- **Mutations**: [List mutations needed, e.g., "updateUserProfile(input: UserProfileInput!): UserProfile"]
- **Subscriptions**: [List subscriptions needed, if any]
- **Federation**: [ ] Yes - Entity is shared across services
- **Federation Key**: [If federated, specify key field]

### Business Logic
- **Validation Rules**: [List validation requirements]
- **Business Rules**: [List business logic requirements]
- **Error Handling**: [Special error cases to handle]

## Frontend Requirements

### Pages/Routes
- **Page Name**: [e.g., "User Profile Page"]
- **Route**: [e.g., "/profile"]
- **Layout**: [Default | Custom]
- **Authentication**: [ ] Required | [ ] Optional | [ ] Public

### Components
- **Components Needed**: 
  - [Component name]: [Description]
  - [Component name]: [Description]

### User Interactions
- **Actions**: [List user actions, e.g., "View profile", "Edit profile", "Save changes"]
- **Forms**: [List forms needed]
- **Data Display**: [How should data be displayed?]

### GraphQL Operations
- **Queries**: [List queries to use]
- **Mutations**: [List mutations to use]
- **Subscriptions**: [List subscriptions to use, if any]

## Database Requirements

### Schema
- **Schema Name**: [app | security | assistant | other]
- **Table Name**: [e.g., "user_profiles"]
- **Indexes**: [List indexes needed]
- **Constraints**: [List constraints, e.g., "unique email"]

### Migration
- **Migration Description**: [Brief description of migration]
- **Data Migration**: [ ] Yes | [ ] No
- **Rollback Strategy**: [How to rollback if needed]

## Security Requirements

### Authentication
- [ ] Required
- [ ] Optional
- [ ] Public

### Authorization
- [ ] Role-based access control needed
- **Roles/Permissions**: [List roles/permissions needed]

### Data Protection
- [ ] Sensitive data handling
- [ ] Encryption required
- **Security Considerations**: [List any security concerns]

## Testing Requirements

### Unit Tests
- [ ] Service layer tests
- [ ] Repository tests
- [ ] Resolver tests

### Integration Tests
- [ ] Database integration tests
- [ ] API integration tests

### E2E Tests
- [ ] User flow tests
- **Test Scenarios**: [List key scenarios to test]

## Documentation Requirements

- [ ] API documentation
- [ ] User documentation
- [ ] Developer documentation
- [ ] Migration guide

## Additional Context

### Constraints
- [List any technical or business constraints]

### Assumptions
- [List assumptions made]

### Dependencies
- [List dependencies on other features/services]

### Open Questions
- [List questions that need to be answered]

---

## Example: User Profile Feature

### Feature Information
- **Feature Name**: User Profile Management
- **Feature Description**: Allow users to view and edit their profile information
- **Business Value**: Users need to manage their account information

### Scope
- [x] Full-stack (Backend + Frontend)
- [x] Database changes required
- [x] API changes required

### Backend Requirements
- **Entity Name**: UserProfile
- **Fields**: 
  - id: UUID - Primary key
  - userId: UUID - Foreign key to User
  - firstName: String - User's first name
  - lastName: String - User's last name
  - bio: String? - Optional biography
  - avatarUrl: String? - Optional avatar URL
- **Module**: app
- **Operations**: [x] Create [x] Read [x] Update
- **GraphQL Queries**: getUserProfile(userId: ID!): UserProfile
- **GraphQL Mutations**: updateUserProfile(input: UserProfileInput!): UserProfile

### Frontend Requirements
- **Page Name**: User Profile Page
- **Route**: "/profile"
- **Components**: ProfileForm, ProfileDisplay
- **Actions**: View profile, Edit profile, Save changes

---

**Status**: [ ] Complete | [ ] In Progress | [ ] Needs Review

**Completed By**: [Name]
**Date**: [Date]

