# Testing Signin Feature

This guide covers how to test the signin feature at different levels: Backend, Frontend, and Full Stack (E2E).

## Authentication Overview

The authentication system uses **JWT (JSON Web Tokens)** for secure token-based authentication:

- **Access Tokens**: Short-lived JWT tokens (default: 15 minutes) used for API authentication. These are stateless and contain user information in the token payload.
- **Refresh Tokens**: Long-lived JWT tokens (default: 7 days) stored in the database for token refresh and revocation support.

Token expiration times are configurable via:
- `application.yml`: `jwt.access-token-expiration-seconds` and `jwt.refresh-token-expiration-seconds`
- Environment variables: `JWT_ACCESS_TOKEN_EXPIRATION_SECONDS` and `JWT_REFRESH_TOKEN_EXPIRATION_SECONDS`

For more details, see `JwtConfig` in the security module.

## Prerequisites

### Backend
- Java 17+ installed
- PostgreSQL 15+ running
- Docker (optional, for containerized setup)

### Frontend
- Node.js 20+ installed
- pnpm installed

### E2E Tests
- Playwright installed (see installation below)

## 1. Backend Testing

### Setup

1. **Start PostgreSQL** (if not already running):
```bash
# Using Docker
docker run -d \
  --name neotool-postgres \
  -e POSTGRES_DB=neotool_db \
  -e POSTGRES_USER=neotool \
  -e POSTGRES_PASSWORD=neotool \
  -p 5432:5432 \
  postgres:15

# Or use your existing PostgreSQL instance
```

2. **Navigate to backend**:
```bash
cd service/kotlin
```

3. **Build the project**:
```bash
./gradlew build
```

4. **Run database migrations** (Flyway will run automatically on startup):
```bash
./gradlew run
```

The backend should start on `http://localhost:8080`

### Create a Test User

You need to create a test user in the database. You can do this via SQL:

```sql
-- Connect to PostgreSQL
psql -h localhost -U neotool -d neotool_db

-- Insert a test user (password hash for "TestPassword123!")
INSERT INTO security.users (id, email, display_name, password_hash)
VALUES (
  gen_random_uuid(),
  'test@example.com',
  'Test User',
  '$argon2id$v=19$m=65536,t=3,p=1$kQKxvgLa0MYT//uuYEj1uQ$xtcLrqYkJxblqaIyos9kmiWV6jFGS9rwk/hBmeajd4k'
);
```

**Better approach**: Create a simple script or use the GraphQL API after implementing a signup mutation.

### Test GraphQL API

#### Using GraphQL Playground or Postman

1. **Start the backend** (if not already running):
```bash
cd service/kotlin
./gradlew run
```

2. **Open GraphQL endpoint**: `http://localhost:8080/graphql`

3. **Test Sign In Mutation**:
```graphql
mutation SignIn {
  signIn(input: {
    email: "test@example.com"
    password: "TestPassword123!"
    rememberMe: false
  }) {
    token
    refreshToken
    user {
      id
      email
      displayName
    }
  }
}
```

**Expected Response**:
```json
{
  "data": {
    "signIn": {
      "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c",
      "refreshToken": null,
      "user": {
        "id": "user-uuid",
        "email": "test@example.com",
        "displayName": "Test User"
      }
    }
  }
}
```

**Note**: The `token` is a JWT (JSON Web Token) with three parts separated by dots (header.payload.signature). When `rememberMe: true`, a `refreshToken` (also a JWT) will be returned for long-term authentication.

4. **Test Invalid Credentials**:
```graphql
mutation SignInInvalid {
  signIn(input: {
    email: "test@example.com"
    password: "WrongPassword"
  }) {
    token
    user {
      id
    }
  }
}
```

**Expected Response** (error):
```json
{
  "errors": [
    {
      "message": "Invalid email or password"
    }
  ],
  "data": null
}
```

5. **Test Current User Query** (requires token):
```graphql
query CurrentUser {
  currentUser {
    id
    email
    displayName
  }
}
```

**Headers**:
```
Authorization: Bearer <token-from-signin>
```

#### Using cURL

```bash
# Sign In
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{
    "query": "mutation { signIn(input: { email: \"test@example.com\", password: \"TestPassword123!\" }) { token user { id email } } }"
  }'

# Current User (with token)
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your-token>" \
  -d '{
    "query": "query { currentUser { id email displayName } }"
  }'
```

### Backend Unit Tests

Run backend tests:
```bash
cd service/kotlin
./gradlew test
```

## 2. Frontend Testing

### Setup

1. **Navigate to frontend**:
```bash
cd web
```

2. **Install dependencies** (if not already done):
```bash
pnpm install
```

3. **Generate GraphQL types**:
```bash
pnpm run codegen
```

4. **Start development server**:
```bash
pnpm run dev
```

The frontend should start on `http://localhost:3000`

### Manual Testing

1. **Open browser**: Navigate to `http://localhost:3000/signin`

2. **Test Successful Sign In**:
   - Enter email: `test@example.com`
   - Enter password: `TestPassword123!`
   - Click "Sign in"
   - Should redirect to home page (`/`)

3. **Test Invalid Credentials**:
   - Enter wrong email or password
   - Click "Sign in"
   - Should show error message

4. **Test Remember Me**:
   - Check "Keep me signed in"
   - Sign in successfully
   - Verify both `auth_token` (JWT access token) and `auth_refresh_token` (JWT refresh token) are stored in localStorage
   - Close browser and reopen
   - Should still be signed in (refresh token used to maintain session)
   - Access token expires after 15 minutes (default), but refresh token can be used to obtain a new access token

5. **Test Google OAuth Button**:
   - Click "Continue with Google"
   - Should show error message (placeholder - not yet implemented)

6. **Test Form Validation**:
   - Try submitting empty form - should show validation errors
   - Enter invalid email format - should show email validation error
   - Enter valid email but empty password - should show password required error

7. **Test Accessibility**:
   - Tab through form fields - should navigate in logical order
   - Use screen reader - should announce form labels and errors
   - Test keyboard navigation - all interactive elements should be accessible

8. **Test Links**:
   - Click "Forgot your password?" - should navigate to `/forgot-password`
   - Click "Sign up" - should navigate to `/signup`

### Frontend Unit Tests

The frontend uses **Vitest** and **React Testing Library** for unit testing.

#### Running Tests

```bash
# Run all tests
cd web
pnpm run test

# Run tests in watch mode
pnpm run test:watch

# Run tests with coverage
pnpm run test:coverage

# Run specific test file
pnpm run test SignInForm
```

#### Test Structure

Tests are located alongside components or in `__tests__` directories:
- Component tests: `src/shared/components/auth/__tests__/SignInForm.test.tsx`
- Page tests: `src/app/signin/__tests__/page.test.tsx`

#### What to Test

When testing the SignInForm component, focus on:

1. **Rendering**: Verify all form fields render correctly (email, password, remember me checkbox, buttons, links)
2. **Form Validation**: 
   - Email format validation
   - Required field validation
   - Error messages display correctly
3. **Form Submission**:
   - Calls `signIn` with correct parameters
   - Handles `rememberMe` checkbox state
   - Shows loading state during submission
   - Disables submit button while loading
4. **Error Handling**:
   - Displays error messages from API
   - Shows user-friendly error messages
5. **User Interactions**:
   - Password visibility toggle
   - Checkbox state changes
   - Link navigation

#### Testing Approach

1. **Mock Dependencies**: Mock `useAuth` and `useToast` hooks to isolate component logic
2. **Use Test IDs**: All form elements have `data-testid` attributes for reliable selection:
   - `textfield-email`
   - `textfield-password`
   - `checkbox-remember-me`
   - `button-signin`
   - `button-google-signin`
   - `signin-error`
3. **User Events**: Use `@testing-library/user-event` for realistic user interactions
4. **Async Handling**: Use `waitFor` for async operations and state updates

#### Example Test Cases

```typescript
// Example: Testing form validation
it('validates email field', async () => {
  const user = userEvent.setup();
  render(<SignInForm />);
  
  await user.type(screen.getByTestId('textfield-email'), 'invalid-email');
  await user.click(screen.getByTestId('button-signin'));
  
  await waitFor(() => {
    expect(screen.getByText(/invalid email/i)).toBeInTheDocument();
  });
});

// Example: Testing form submission
it('submits form with valid data', async () => {
  const mockSignIn = vi.fn().mockResolvedValue(undefined);
  // Mock useAuth to return mockSignIn
  
  const user = userEvent.setup();
  render(<SignInForm />);
  
  await user.type(screen.getByTestId('textfield-email'), 'test@example.com');
  await user.type(screen.getByTestId('textfield-password'), 'TestPassword123!');
  await user.click(screen.getByTestId('button-signin'));
  
  await waitFor(() => {
    expect(mockSignIn).toHaveBeenCalledWith('test@example.com', 'TestPassword123!', false);
  });
});
```

#### Testing the SignIn Page

Test the page component for:
- Rendering the signin form
- Redirecting authenticated users
- Loading states
- Layout and structure

#### Testing with Mocked GraphQL

Use Apollo Client's `MockedProvider` to mock GraphQL mutations:

```typescript
import { MockedProvider } from '@apollo/client/testing';
import { SIGN_IN } from '@/lib/graphql/operations/auth';

const mocks = [{
  request: {
    query: SIGN_IN,
    variables: { input: { email: 'test@example.com', password: 'TestPassword123!', rememberMe: false } }
  },
  result: {
    data: {
      signIn: {
        token: 'mock-jwt-token',
        refreshToken: null,
        user: { id: 'user-123', email: 'test@example.com', displayName: 'Test User' }
      }
    }
  }
}];

<MockedProvider mocks={mocks}>
  <SignInForm />
</MockedProvider>
```

#### Testing i18n

The signin page uses domain-specific i18n. Register translations in your test setup:

```typescript
import { signinTranslations } from '@/app/signin/i18n';
i18n.addResourceBundle('en', 'signin', signinTranslations.en, true, true);
```

#### Testing Accessibility

Use `jest-axe` to check for accessibility violations:

```typescript
import { axe } from 'jest-axe';

it('has no accessibility violations', async () => {
  const { container } = render(<SignInForm />);
  const results = await axe(container);
  expect(results).toHaveNoViolations();
});
```

Verify ARIA attributes are present:
- Form has `aria-label`
- Required fields have `aria-required="true"`
- Error messages have `role="alert"` and `aria-live="assertive"`

## 3. Full Stack E2E Testing

### Setup Playwright

1. **Install Playwright**:
```bash
cd web
pnpm add -D @playwright/test
```

2. **Install Playwright browsers**:
```bash
pnpm exec playwright install --with-deps
```

3. **Add test script to package.json** (if not already added):
```json
{
  "scripts": {
    "test:e2e": "playwright test",
    "test:e2e:ui": "playwright test --ui",
    "test:e2e:debug": "playwright test --debug"
  }
}
```

### Prepare Test Data

Before running E2E tests, you need to create a test user in the database:

```sql
-- Connect to PostgreSQL
psql -h localhost -U neotool -d neotool_db

-- Create test user with known password
-- Password: TestPassword123!
-- You'll need to generate the Argon2id hash using AuthenticationService.hashPassword()
-- For now, you can use a temporary approach or create a test setup script
```

**Better approach**: Create a test setup script that seeds test users.

### Run E2E Tests

1. **Ensure backend is running**:
```bash
cd service/kotlin
./gradlew run
```

2. **Ensure frontend is running** (in another terminal):
```bash
cd web
pnpm run dev
```

3. **Run E2E tests**:
```bash
cd web
pnpm run test:e2e
```

4. **Run E2E tests with UI** (interactive):
```bash
pnpm run test:e2e:ui
```

5. **Run E2E tests in debug mode**:
```bash
pnpm run test:e2e:debug
```

### E2E Test Scenarios

The E2E tests cover:
- ✅ Successful sign in
- ✅ Invalid credentials (wrong password)
- ✅ Invalid credentials (wrong email)
- ✅ Google OAuth button (placeholder)
- ✅ Remember me functionality
- ✅ Session persistence

### View Test Reports

After running tests, view the HTML report:
```bash
cd web
pnpm exec playwright show-report
```

## 4. Full Stack Integration Testing

### Manual Full Stack Test

1. **Start Backend**:
```bash
cd service/kotlin
./gradlew run
```

2. **Start Frontend** (in another terminal):
```bash
cd web
pnpm run dev
```

3. **Test Flow**:
   - Open `http://localhost:3000/signin`
   - Sign in with test credentials
   - Verify redirect to home
   - Check browser DevTools → Application → Local Storage
   - Verify `auth_token` (JWT access token) is stored
   - If "Remember Me" was checked, verify `auth_refresh_token` (JWT refresh token) is also stored
   - Verify token format: JWT tokens have 3 parts separated by dots (e.g., `eyJ...xxx.yyy.zzz`)
   - Refresh page - should remain signed in
   - Wait 15+ minutes (or configure shorter expiration) and verify access token expiration handling
   - Sign out (if implemented) or clear storage
   - Navigate to protected route - should redirect to signin

### Check Network Requests

1. Open browser DevTools → Network tab
2. Sign in
3. Verify:
   - POST request to `/graphql` with signIn mutation
   - Response contains JWT `token` (access token) and optionally `refreshToken` (if rememberMe was true)
   - Token is a JWT format (three parts separated by dots)
   - Subsequent requests include `Authorization: Bearer <jwt-token>` header
   - Token can be decoded (but not modified) - check JWT structure at jwt.io

## 5. Troubleshooting

### Backend Issues

**Issue**: Database connection error
- **Solution**: Ensure PostgreSQL is running and credentials match `application.yml`

**Issue**: Migration fails
- **Solution**: Check Flyway logs, ensure database is clean or migrations are in order

**Issue**: GraphQL schema not found
- **Solution**: Ensure `schema.graphqls` is in `src/main/resources/graphql/`

### Frontend Issues

**Issue**: GraphQL types not generated
- **Solution**: Run `pnpm run codegen` and ensure backend is running

**Issue**: CORS errors
- **Solution**: Check backend CORS configuration in `application.yml`

**Issue**: Token not being sent
- **Solution**: Check Apollo Client auth link configuration in `web/src/lib/graphql/client.ts`

**Issue**: JWT token expired
- **Solution**: Access tokens expire after 15 minutes (default). Use refresh token to obtain new access token, or sign in again

**Issue**: Invalid JWT token format
- **Solution**: Ensure token is a valid JWT (three parts separated by dots). Check token in browser DevTools → Application → Local Storage

### E2E Test Issues

**Issue**: Tests fail with "page.goto: net::ERR_CONNECTION_REFUSED"
- **Solution**: Ensure frontend dev server is running on port 3000

**Issue**: Tests fail with authentication errors
- **Solution**: Ensure test user exists in database with correct password hash

**Issue**: Playwright browsers not installed
- **Solution**: Run `pnpm exec playwright install --with-deps`

## 6. Creating Test Users Programmatically

For easier testing, create a simple script to generate test users:

### Backend Test Script (Kotlin)

Create `service/kotlin/app/src/test/kotlin/TestUserSeeder.kt`:

```kotlin
// This can be run as a test or main function
// to create test users with proper password hashes
```

### Frontend Test Helper

You can also create a test API endpoint or use GraphQL mutations to create users for testing.

## 7. JWT Token Configuration

### Default Settings

- **Access Token Expiration**: 900 seconds (15 minutes)
- **Refresh Token Expiration**: 604800 seconds (7 days)

### Configuration Options

#### Via application.yml

```yaml
jwt:
  secret: ${JWT_SECRET:your-secret-key-min-32-chars}
  access-token-expiration-seconds: ${JWT_ACCESS_TOKEN_EXPIRATION_SECONDS:900}
  refresh-token-expiration-seconds: ${JWT_REFRESH_TOKEN_EXPIRATION_SECONDS:604800}
```

#### Via Environment Variables

```bash
export JWT_SECRET="your-strong-secret-key-minimum-32-characters-long"
export JWT_ACCESS_TOKEN_EXPIRATION_SECONDS=900
export JWT_REFRESH_TOKEN_EXPIRATION_SECONDS=604800
```

### Token Structure

JWT tokens consist of three parts:
1. **Header**: Algorithm and token type (e.g., `{"alg":"HS256","typ":"JWT"}`)
2. **Payload**: Claims including user ID, email, expiration, etc.
3. **Signature**: HMAC-SHA256 signature for token verification

Example token structure:
```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwidHlwZSI6ImFjY2VzcyIsImlhdCI6MTUxNjIzOTAyMn0.signature
```

You can decode and inspect JWT tokens at [jwt.io](https://jwt.io) (without the secret, you can only view the payload, not verify the signature).

### Testing Token Expiration

To test token expiration behavior:

1. **Configure shorter expiration for testing**:
   ```yaml
   jwt:
     access-token-expiration-seconds: 60  # 1 minute for testing
   ```

2. **Sign in and wait for expiration**:
   - Sign in successfully
   - Wait for token to expire
   - Make a request with expired token
   - Verify proper error handling or token refresh

3. **Test refresh token flow**:
   - Sign in with `rememberMe: true`
   - Wait for access token to expire
   - Use refresh token to obtain new access token (if refresh endpoint implemented)

## 8. Next Steps

- [x] Implement JWT-based authentication (completed)
- [ ] Implement signup mutation for easier test user creation
- [ ] Add token refresh mutation/endpoint
- [ ] Add more comprehensive error handling tests
- [ ] Implement Google OAuth for social signin
- [ ] Add rate limiting tests
- [ ] Add JWT token expiration tests
- [ ] Add password reset flow tests

