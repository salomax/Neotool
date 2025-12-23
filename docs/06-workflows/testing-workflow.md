---
title: Testing Workflow
type: workflow
category: testing
status: current
version: 2.1.0
tags: [workflow, testing, process, spec-driven]
ai_optimized: true
search_keywords: [workflow, testing, spec-driven, coverage, test-driven]
related:
  - 05-standards/testing-standards/unit-test-standards.md
  - 04-patterns/backend-patterns/testing-pattern.md
  - 04-patterns/frontend-patterns/testing-pattern.md
  - 04-patterns/frontend-patterns/e2e-testing-pattern.md
  - 06-workflows/spec-context-strategy.md
---

# Testing Workflow

> **Purpose**: Spec-Driven testing workflow ensuring features meet specification requirements and quality standards.

## Overview

Testing in NeoTool is **Spec-Driven**, meaning tests are derived from:
1. **Feature Specification**: Gherkin scenarios from feature file
2. **Business Rules**: Requirements from memory file
3. **Patterns**: Testing patterns from `docs/04-patterns/`
4. **Standards**: Testing standards from `docs/05-standards/`

## Testing Philosophy

### Test-Driven Development (TDD)
- Write tests before or alongside implementation
- Use feature file scenarios as test cases
- Ensure tests validate business requirements

### Spec-Driven Testing
- Every Gherkin scenario → Test case
- Every business rule → Test validation
- Every edge case → Test coverage

## Testing Process

### Step 1: Test Planning

**Derive Tests from Specification**:

1. **Load Feature Context**:
   - Read feature file: `docs/03-features/<module>/<feature>/<feature>.feature`
   - Read memory file: `docs/03-features/<module>/<feature>/<feature>.memory.yml`
   - Extract all scenarios and business rules

2. **Map Scenarios to Tests**:
   - `@happy-path` → Unit + Integration + E2E tests
   - `@validation` → Unit + Integration tests
   - `@edge-case` → Unit + Integration tests
   - `@non-functional` → Performance/security tests

3. **Identify Test Types**:
   - **Unit Tests**: Business logic, validations, transformations
   - **Integration Tests**: Database, API, external services
   - **E2E Tests**: Complete user flows

### Step 2: Unit Testing

**Context**: Load testing patterns and standards

**Spec References**:
- [Testing Standards](../05-standards/testing-standards/unit-test-standards.md)
- [Backend Testing Pattern](../04-patterns/backend-patterns/testing-pattern.md)
- [Frontend Testing Pattern](../04-patterns/frontend-patterns/testing-pattern.md)

**Requirements**:
- **Coverage**: 90%+ line coverage, 85%+ branch coverage
- **Branches**: All conditional branches tested (if/when/switch/guard)
- **Business Logic**: All business rules tested
- **Validations**: All validation rules tested

**Implementation**:
1. Write unit tests for each service method
2. Test all business rules from memory file
3. Test all validation scenarios
4. Test edge cases and error conditions
5. Verify branch coverage

**Validation**:
```bash
# Backend
./gradlew test --tests "*Test" jacocoTestReport
# Check coverage: build/reports/jacoco/test/html/index.html

# Frontend
npm test -- --coverage
# Check coverage: coverage/index.html
```

### Step 3: Integration Testing

**Context**: Load integration testing patterns

**Spec References**:
- [Testing Standards](../05-standards/testing-standards/unit-test-standards.md)
- [Backend Testing Pattern](../04-patterns/backend-patterns/testing-pattern.md)

**Requirements**:
- **Coverage**: 80%+ line coverage, 75%+ branch coverage
- **Database**: All database interactions tested
- **API**: All GraphQL operations tested
- **External Services**: All integrations tested

**Implementation**:
1. Test database operations (CRUD, queries)
2. Test GraphQL resolvers end-to-end
3. Test service layer with real database
4. Test error handling and rollbacks
5. Use Testcontainers for database tests

**Validation**:
```bash
# Backend integration tests
./gradlew test --tests "*IntegrationTest"
```

### Step 4: E2E Testing

**Context**: Load E2E testing patterns

**Spec References**:
- [E2E Testing Pattern](../04-patterns/frontend-patterns/e2e-testing-pattern.md)

**Requirements**:
- **Coverage**: All Gherkin scenarios from feature file
- **User Flows**: Complete user journeys tested
- **Cross-Browser**: Test in multiple browsers (if applicable)

**Implementation**:
1. Map each Gherkin scenario to E2E test
2. Test complete user flows
3. Test error scenarios
4. Test edge cases
5. Use Playwright for browser automation

**Example Mapping**:
```gherkin
# Feature file
Scenario: User signs in with valid credentials
  Given I am on the sign-in page
  When I enter valid email and password
  Then I should be redirected to dashboard

# E2E Test
test('User signs in with valid credentials', async ({ page }) => {
  await page.goto('/sign-in');
  await page.fill('[name="email"]', 'user@example.com');
  await page.fill('[name="password"]', 'password123');
  await page.click('button[type="submit"]');
  await expect(page).toHaveURL('/dashboard');
});
```

**Validation**:
```bash
# E2E tests
npm run test:e2e
```

### Step 5: Coverage Validation

**Requirements**:
- **Unit Tests**: 90%+ line, 85%+ branch
- **Integration Tests**: 80%+ line, 75%+ branch
- **All Branches**: Every conditional branch tested

**Validation**:
1. Run coverage reports
2. Check coverage thresholds
3. Identify untested branches
4. Add tests for missing coverage
5. Verify all branches tested

### Step 6: Test Execution

**Before Commit**:
1. Run all unit tests
2. Run all integration tests
3. Run E2E tests (if applicable)
4. Check coverage reports
5. Fix any failures

**CI/CD**:
- All tests run automatically
- Coverage thresholds enforced
- Test failures block merge

## Spec-Driven Test Checklist

### Feature File Coverage
- [ ] All `@happy-path` scenarios tested
- [ ] All `@validation` scenarios tested
- [ ] All `@edge-case` scenarios tested
- [ ] All `@non-functional` scenarios tested (if applicable)

### Business Rules Coverage
- [ ] All business rules from memory file tested
- [ ] All validation rules tested
- [ ] All authorization rules tested
- [ ] All edge cases tested

### Code Coverage
- [ ] Unit test coverage: 90%+ line, 85%+ branch
- [ ] Integration test coverage: 80%+ line, 75%+ branch
- [ ] All conditional branches tested
- [ ] All error paths tested

### Test Quality
- [ ] Tests follow testing patterns
- [ ] Tests are readable and maintainable
- [ ] Tests use proper test data
- [ ] Tests are isolated and independent

## Context Strategy for Testing

**For AI Assistants**:

1. **Load Feature Context**:
   - Feature file (Gherkin scenarios)
   - Memory file (business rules)

2. **Load Testing Specs**:
   - Testing standards
   - Testing patterns (backend/frontend/E2E)
   - Coverage requirements

3. **Map Scenarios to Tests**:
   - Create test plan from scenarios
   - Identify test types needed
   - Plan coverage strategy

See [Spec Context Strategy](./spec-context-strategy.md) for detailed guidance.

## Test Execution Commands

```bash
# Backend unit tests
./gradlew test

# Backend integration tests
./gradlew test --tests "*IntegrationTest"

# Backend coverage
./gradlew test jacocoTestReport

# Frontend unit tests (all tests)
pnpm test

# Frontend unit tests (specific file by pattern)
pnpm test usePageTitle                    # Matches any file containing "usePageTitle"
pnpm test "**/usePageTitle.test.tsx"      # Full path pattern
pnpm vitest run usePageTitle              # Direct Vitest command

# Frontend component tests
npm run test:component

# Frontend E2E tests
pnpm run test:e2e

# Frontend coverage
pnpm test -- --coverage

# Watch mode
pnpm test:watch
./gradlew test --continuous
```

### Running Specific Test Files

**Frontend (Vitest)**:
- Use file patterns without `--` separator: `pnpm test <pattern>`
- Patterns match any file path containing the pattern
- Examples:
  - `pnpm test usePageTitle` - Matches `src/shared/hooks/__tests__/ui/usePageTitle.test.tsx`
  - `pnpm test UserDrawer` - Matches any test file with "UserDrawer" in the path
  - `pnpm test "**/usePageTitle.test.tsx"` - Full path pattern with glob

**Note**: Using `pnpm run test -- <pattern>` does not work correctly. Pass the pattern directly without `--` separator.

**Backend (Gradle)**:
- Use `--tests` flag with class name pattern: `./gradlew test --tests "*TestClass"`
- Examples:
  - `./gradlew test --tests "*UserServiceTest"` - Matches any test class ending with "UserServiceTest"
  - `./gradlew test --tests "com.example.UserServiceTest"` - Full class name

## Related Documentation

- [Testing Standards](../05-standards/testing-standards/unit-test-standards.md) - Coverage requirements
- [Backend Testing Pattern](../04-patterns/backend-patterns/testing-pattern.md) - Backend test patterns
- [Frontend Testing Pattern](../04-patterns/frontend-patterns/testing-pattern.md) - Frontend test patterns
- [E2E Testing Pattern](../04-patterns/frontend-patterns/e2e-testing-pattern.md) - E2E test patterns
- [Spec Context Strategy](./spec-context-strategy.md) - Context optimization

