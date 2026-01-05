# Validate Implementation Against Spec

You are a **QA Engineer** validating that an implementation meets the specification.

Your job is to:
1. Read the feature spec
2. Check each success criterion
3. Run tests
4. Verify implementation quality
5. Provide a validation report

## 1. Parse User Input

The user will provide a feature slug or spec file:

**Examples:**
```
/spec-validate user-profile-management
/spec-validate spec/features/user-profile-management.md
```

## 2. Read the Spec

Load the spec and extract:
- Success criteria
- Technical constraints
- Security requirements
- Integration points

## 3. Validation Checklist

Go through each success criterion and validate:

### For Each Criterion:

```
Validating: [Criterion text]

**Check:**
- [ ] Implementation exists
- [ ] Works as specified
- [ ] Tests pass
- [ ] Edge cases handled

**Result:** ✅ PASS / ❌ FAIL
**Evidence:** [How you verified]
**Notes:** [Any observations]
```

### Example Validation:

```
✅ Criterion 1: User can view profile

**Check:**
- [x] Implementation: ProfilePage.tsx exists
- [x] Works: Page loads and displays profile data
- [x] Tests: ProfilePage.test.tsx passes
- [x] Edge cases: Loading state, error state handled

**Result:** ✅ PASS
**Evidence:**
- Checked file: client/src/app/(authenticated)/profile/page.tsx
- Ran test: npm test ProfilePage.test.tsx
- Manual test: Loaded /profile in browser
```

## 4. Test Validation

Run and verify tests:

**Backend:**
```bash
# Unit tests
./gradlew :user:test

# Integration tests
./gradlew :user:integrationTest

# Coverage report
./gradlew :user:koverReport
```

**Frontend:**
```bash
# Unit/component tests
npm test -- profile

# E2E tests
npm run test:e2e -- profile

# Coverage
npm run test:coverage
```

**Report results (example format, replace with actual outputs):**
```
🧪 Test Results:

Backend:
✅ Unit tests: <pass/fail + count>
✅ Integration tests: <pass/fail + count>
✅ Coverage: <value> (target: <value>)

Frontend:
✅ Component tests: <pass/fail + count>
✅ E2E tests: <pass/fail + count>
✅ Coverage: <value> (target: <value>)

Overall: <summary>
```

## 5. Code Quality Checks

Verify code quality:

**Linting:**
```bash
# Backend
./gradlew ktlintCheck

# Frontend
npm run lint
```

**Type Safety:**
```bash
# Backend (compile)
./gradlew compileKotlin

# Frontend
npm run typecheck
```

**Report (fill with actual results):**
```
🔍 Code Quality:

✅ Linting: <status>
✅ Type errors: <status>
✅ Build: <status>
```

## 6. Pattern Compliance

Check if implementation follows codebase patterns:

```
🎨 Pattern Compliance:

**Backend:**
✅ Uses expected ID strategy (e.g., UUID v7)
✅ Follows Repository → Service → Resolver flow
✅ Error handling matches exemplars
✅ Audit/guardrails applied where required
✅ Authorization annotations in place if applicable

**Frontend:**
✅ Form/validation follows current pattern (e.g., React Hook Form + Zod)
✅ Data fetching/mutations follow current client pattern
✅ Loading/error states present
✅ Design system components used
✅ File structure matches feature conventions

**Tests:**
✅ Unit tests mock dependencies appropriately
✅ Integration tests use fixtures
✅ E2E tests follow existing pattern

Compliance: <summary/status>
```

## 7. Security Validation

Verify security requirements (adapt to the feature):

```
🔒 Security Checklist:

✅ Authorization: Required annotations/guards applied
✅ Input validation: All inputs validated
✅ SQL injection: Parameterized queries/ORM safe usage
✅ XSS prevention: Proper escaping in UI
✅ File upload (if any): Size/type validation
✅ Rate limiting (if specified): Implemented
✅ Audit logging: Required events logged

Security: <status>
```

## 8. Performance Validation

Check performance requirements (only if specified):

```
⚡ Performance:

✅ Database queries optimized (indexes where needed)
✅ N+1 avoided (DataLoader if applicable)
✅ Response time meets spec target (if defined)
✅ File uploads efficient (if applicable)
✅ Frontend bundle size reasonable

Performance: <status>
```

## 9. Manual Testing Guide

Provide manual testing steps:

```
📱 Manual Testing Checklist:

**Setup:**
1. Start infrastructure: `docker compose -f infra/docker/docker-compose.local.yml --profile database --profile gateway up -d`
2. Start web dev server: `cd web && pnpm dev`
3. Navigate to: http://localhost:3000/profile

**Happy Path:**
- [ ] Page loads with current profile data
- [ ] Can edit display name
- [ ] Can edit bio
- [ ] Can upload avatar
- [ ] Save button works
- [ ] Success notification shows
- [ ] Data persists after refresh

**Validation:**
- [ ] Empty name shows error
- [ ] Name > 100 chars shows error
- [ ] Bio > 500 chars shows error
- [ ] Invalid email format rejected
- [ ] Duplicate email rejected
- [ ] File > 5MB rejected

**Edge Cases:**
- [ ] Loading state shows while saving
- [ ] Error message if save fails
- [ ] Concurrent edits handled
- [ ] Refresh during edit asks to save

**Security:**
- [ ] Can only edit own profile
- [ ] Cannot edit other user's profile
- [ ] Admin can view (but not edit) any profile

Result: [User confirms manual tests]
```

## 10. Validation Report

Generate comprehensive report (replace placeholders with actual findings):

```
📊 VALIDATION REPORT
Feature: <feature name>
Spec: <spec path>
Date: <date>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

✅/❌ SUCCESS CRITERIA: <x>/<y> MET

1. <status> <criterion 1> — evidence/notes
2. <status> <criterion 2> — evidence/notes
...

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🧪 TESTS

Backend: <result/coverage>
Frontend: <result/coverage>
E2E: <result>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🔍 CODE QUALITY

Linting: <status>
Types/Build: <status>
Pattern compliance: <summary>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🔒 SECURITY

Key checks: <status/notes>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

⚡ PERFORMANCE (if applicable)

Status: <summary>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

📝 RECOMMENDATIONS

- <short actionable items or “None”>

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

OVERALL STATUS: <READY/NOT READY> (why)
```

## 11. Handle Failures

If validation fails:

```
❌ VALIDATION FAILED

Issues Found: 3

1. ❌ Email uniqueness not validated
   - Expected: Service validates email uniqueness
   - Actual: No validation found
   - Fix: Add validation in UserProfileService

2. ❌ Unit test coverage 75% (target: 90%)
   - Missing: Service validation tests
   - Fix: Add tests for validation logic

3. ❌ E2E test missing
   - Expected: Full flow E2E test
   - Actual: No E2E test found
   - Fix: Create profile.spec.ts

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

NEXT STEPS:

1. Fix the 3 issues above
2. Re-run validation: /spec-validate user-profile-management
3. Once all criteria met, proceed to code review

Would you like me to fix these issues? (yes/no)
```

If user says yes, implement the fixes.

## 12. Quick Validation Mode

For faster validation, support a quick mode:

```
/spec-validate user-profile-management --quick

🔍 Quick Validation:

✅ Success criteria: 7/7
✅ Tests: All passing
✅ Linting: Clean
✅ Types: No errors

Status: ✅ READY

(Use /spec-validate [slug] for detailed report)
```

## 13. CI/CD Integration Hint

Suggest automation:

```
💡 TIP: Automate This Validation

Add to your CI/CD pipeline:

# .github/workflows/validate-feature.yml
- name: Validate Feature
  run: |
    ./gradlew test koverReport
    npm test
    npm run test:e2e
    npm run lint
    npm run typecheck

This ensures validation runs automatically on PR.
```

---

## Usage Examples

**Full validation:**
```
/spec-validate user-profile-management
```

**Quick check:**
```
/spec-validate user-profile-management --quick
```

**Validate and fix issues:**
```
User: /spec-validate user-profile-management
Agent: [Finds 3 issues]
User: yes, fix them
Agent: [Fixes issues, re-validates]
Agent: ✅ All issues resolved, validation passing
```

---

**Remember:** Validation ensures implementation matches spec and meets quality standards.
