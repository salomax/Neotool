# Tests — Frontend

## Stack

- **Vitest** — testes unitários de componentes e hooks
- **Playwright** — testes E2E
- **Mobile** — React Native com Expo (Vitest para unit)

## Unit tests com Vitest

```typescript
import { renderHook } from '@testing-library/react';
import { useTransactionSummary } from './useTransactionSummary';

describe('useTransactionSummary', () => {
  it('should calculate total correctly', () => {
    const { result } = renderHook(() => useTransactionSummary(mockTransactions));
    expect(result.current.total).toBe(150);
  });
});
```

## Gotchas

- Não mockar Apollo Client diretamente — usar `MockedProvider`
- Para componentes com i18n, envolver em `NextIntlClientProvider` nos testes
- Testes E2E ficam em `web/e2e/` — consultar `docs/07-frontend/patterns/` para padrões
