# Tests - Frontend

## Stack

- Vitest para testes unitários de componentes, hooks e utilitários.
- Testing Library para render e interação.
- Playwright para E2E em `web/tests/e2e/`.
- React Native/Expo no mobile quando aplicável.

## Unit Tests com Vitest

```typescript
import { renderHook } from "@testing-library/react";
import { useTransactionSummary } from "./useTransactionSummary";

describe("useTransactionSummary", () => {
  it("calculates total correctly", () => {
    const { result } = renderHook(() => useTransactionSummary(mockTransactions));

    expect(result.current.total).toBe(150);
  });
});
```

## E2E com Playwright

Testes E2E ficam em `web/tests/e2e/`. Consulte também
`web/tests/e2e/CONVENTIONS.md` quando existir no workspace e os helpers em
`web/tests/e2e/helpers/`.

## Providers Frequentes

- Apollo: use `MockedProvider` quando o componente consome GraphQL.
- i18n: envolva com provider de mensagens usado pela feature.
- Toast/Auth/Preferences: use providers reais ou mocks já existentes nos helpers.

## Gotchas

- Não mocke Apollo Client manualmente se `MockedProvider` resolve o caso.
- Para componentes com i18n, o teste precisa prover mensagens da namespace usada.
- E2E deve usar locators estáveis e helpers existentes, não sleeps arbitrários.
- Atualize fixtures/mocks quando alterar generated GraphQL types.
