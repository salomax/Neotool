# Tests — Mobile

## Stack

- **Jest** (`jest-expo` preset) — unit and component tests.
- **React Native Testing Library** (`@testing-library/react-native`) — render and interaction.
- Config: `mobile/jest.config.js`, setup: `mobile/jest.setup.js`.
- Commands: `pnpm test`, `pnpm test:watch`, `pnpm test:coverage`.

## Component test

```tsx
import { render, screen, fireEvent } from '@testing-library/react-native';
import { LoginScreen } from '@/features/auth/screens/LoginScreen';

it('shows a validation error for an invalid email', async () => {
  render(<LoginScreen />, { wrapper: TestProviders });
  fireEvent.changeText(screen.getByLabelText('email'), 'not-an-email');
  fireEvent.press(screen.getByRole('button', { name: /entrar/i }));
  expect(await screen.findByText(/e-mail inválido/i)).toBeTruthy();
});
```

## Hook test

```tsx
import { renderHook } from '@testing-library/react-native';
import { usePortfolioSummary } from './usePortfolioSummary';

it('sums positions', () => {
  const { result } = renderHook(() => usePortfolioSummary(mockPositions));
  expect(result.current.total).toBe(150);
});
```

## Gotchas

- Wrap components under test with the app providers (Apollo `MockedProvider`, Paper theme, i18n).
  Prefer a shared `TestProviders` helper — do not mock Apollo Client directly; use `MockedProvider`.
- For i18n, initialize i18next in test mode or wrap in the app's `I18nProvider`; assert on visible
  copy or on keys, consistently.
- Query by role/label/text (accessibility-first) rather than by testID where possible — this also
  validates a11y labels.
- Mock native modules (SecureStore, LocalAuthentication, Notifications, Haptics) in `jest.setup.js`.
- Keep tests deterministic: fake timers for debounced search, and stub network status.

## What to cover

- Happy path + validation + server-error states for each form/flow.
- List rendering: loading (skeleton), empty, populated, and error.
- Guard behavior: unauthenticated redirect, authenticated access.
