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

## Playwright local

- Se `playwright.config.ts` usa `webServer`, não subir `pnpm run dev` manualmente em paralelo antes
  do teste; isso pode causar conflito de porta/processo e falhas artificiais de boot.
- Para reruns locais isolados, preferir explicitar ambiente e URL base em vez de depender do default:

```bash
PLAYWRIGHT_TEST_ENV=local \
PLAYWRIGHT_TEST_BASE_URL=http://localhost:3001 \
PORT=3001 \
pnpm --dir web exec playwright test tests/e2e/example.smoke.spec.ts --project=smoke
```

- Se um smoke falhar por `webServer` timeout ou conflito com `next dev` já aberto, diagnosticar o
  runner local primeiro; não assumir regressão do código sem separar essa causa operacional.
